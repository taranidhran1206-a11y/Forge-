package com.forge.app.critique

import android.content.Context
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.focus.NudgeStore
import com.forge.app.fuel.Fuel
import com.forge.app.fuel.FuelDatabase
import com.forge.app.gym.GymDatabase
import com.forge.app.util.Gemini
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hercules' daily verdict. The score is worked out here from the day's real numbers, so it
 * is the same every time and needs no network; Gemini only writes the words around it.
 */
object Verdict {

    private const val PREFS = "forge_verdicts"
    private const val FOCUS_TARGET_MIN = 120

    /** One scored part of the day. A null score means it didn't apply today. */
    data class Part(val key: String, val name: String, val score: Int?, val detail: String)

    data class Day(
        val date: String,
        val parts: List<Part>,
        val facts: String,
        val missed: List<String>
    ) {
        val score: Int get() = overall(parts)
    }

    data class Result(
        val date: String,
        val score: Int,
        val verdict: String,
        val wins: List<String>,
        val fixes: List<String>,
        val parts: List<Part>,
        val createdAt: Long,
        val offline: Boolean = false
    ) {
        fun toJson(): String = JSONObject()
            .put("date", date).put("score", score).put("verdict", verdict)
            .put("wins", JSONArray(wins)).put("fixes", JSONArray(fixes))
            .put("parts", JSONArray().apply {
                parts.forEach { p -> put(JSONObject().put("key", p.key).put("name", p.name).put("detail", p.detail).apply { p.score?.let { put("score", it) } }) }
            })
            .put("createdAt", createdAt).put("offline", offline)
            .toString()

        companion object {
            fun fromJson(s: String): Result? = try {
                val o = JSONObject(s)
                val pa = o.optJSONArray("parts") ?: JSONArray()
                Result(
                    o.getString("date"), o.getInt("score"), o.optString("verdict"),
                    Verdict.strings(o.optJSONArray("wins")), Verdict.strings(o.optJSONArray("fixes")),
                    (0 until pa.length()).map { i ->
                        val p = pa.getJSONObject(i)
                        Part(p.getString("key"), p.getString("name"), if (p.has("score")) p.getInt("score") else null, p.optString("detail"))
                    },
                    o.optLong("createdAt"), o.optBoolean("offline")
                )
            } catch (_: Exception) { null }
        }
    }

    private val WEIGHTS = mapOf("labors" to 30, "focus" to 25, "training" to 20, "fuel" to 15, "mind" to 10)

    fun overall(parts: List<Part>): Int {
        val scored = parts.filter { it.score != null }
        val total = scored.sumOf { WEIGHTS[it.key] ?: 10 }
        if (total == 0) return 0
        return (scored.sumOf { it.score!! * (WEIGHTS[it.key] ?: 10) }.toDouble() / total).roundToInt()
    }

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dayStart(date: String): Long {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: Date()
        return Calendar.getInstance().apply { time = d; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }

    // ── storage ──

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saved(c: Context, date: String): Result? = prefs(c).getString(date, null)?.let { Result.fromJson(it) }

    fun save(c: Context, r: Result) {
        prefs(c).edit().putString(r.date, r.toJson()).apply()
    }

    /** Every saved verdict, newest first. */
    fun history(c: Context): List<Result> =
        prefs(c).all.values.mapNotNull { (it as? String)?.let(Result::fromJson) }.sortedByDescending { it.date }

    // ── gathering ──

    /** Reads the whole day from every part of Forge and scores it. */
    suspend fun gather(c: Context, date: String = today()): Day = withContext(Dispatchers.IO) {
        val app = c.applicationContext
        val repo = ForgeRepository(ForgeDatabase.getDatabase(app))
        val facts = StringBuilder()
        val parts = mutableListOf<Part>()

        // Labors
        val labors = repo.getLaborsForDateSync(date)
        val done = labors.filter { it.done }
        val missed = labors.filter { !it.done }.map { it.name }
        if (labors.isNotEmpty()) {
            val s = (done.size * 100.0 / labors.size).roundToInt()
            parts += Part("labors", "Labors", s, "${done.size} of ${labors.size} done")
            facts.append("Labors: ${done.size}/${labors.size} done.")
            if (done.isNotEmpty()) facts.append(" Done: ${done.joinToString { it.name }}.")
            if (missed.isNotEmpty()) facts.append(" Missed: ${missed.joinToString()}.")
            facts.append('\n')
        } else {
            parts += Part("labors", "Labors", null, "No labors set")
            facts.append("Labors: none set for the day.\n")
        }

        // Focus
        val tracked = repo.getTotalMinutesForDate(date)
        val nudge = if (date == today()) NudgeStore.today(app) else null
        val focusMin = maxOf(tracked, (nudge?.focusSeconds ?: 0) / 60)
        val focusScore = minOf(100, (focusMin * 100.0 / FOCUS_TARGET_MIN).roundToInt())
        parts += Part("focus", "Focus", focusScore, if (focusMin > 0) "${focusMin} min of deep work" else "No focus time")
        facts.append("Focus: $focusMin minutes (target $FOCUS_TARGET_MIN).")
        if (nudge != null && nudge.sessions > 0) {
            facts.append(" ${nudge.sessions} focus sessions, drifted to distracting apps ${nudge.drifts} times, ${nudge.awaySeconds / 60} min away.")
        }
        facts.append('\n')

        // Training
        val gym = GymDatabase.get(app).gymDao()
        val start = dayStart(date)
        val end = start + 24 * 3600_000L
        val today = gym.setsSinceNow(start - 2 * 24 * 3600_000L)
        val todaySets = today.filter { it.startedAt in start until end }
        val earlier = today.filter { it.startedAt < start }
        when {
            todaySets.isNotEmpty() -> {
                val exercises = todaySets.map { it.exercise }.distinct()
                val volume = todaySets.sumOf { it.weightKg * it.reps }.roundToInt()
                parts += Part("training", "Training", 100, "${todaySets.size} set${if (todaySets.size == 1) "" else "s"}, ${exercises.size} exercise${if (exercises.size == 1) "" else "s"}")
                facts.append("Training: trained today, ${todaySets.size} working sets across ${exercises.joinToString()}; volume ${volume} kg.\n")
            }
            earlier.isNotEmpty() -> {
                parts += Part("training", "Training", null, "Rest day")
                facts.append("Training: rest day (trained in the last two days).\n")
            }
            else -> {
                parts += Part("training", "Training", 25, "No workout in 3 days")
                facts.append("Training: no workout today or the two days before.\n")
            }
        }

        // Fuel
        val meals = FuelDatabase.get(app).dao().dayNow(date)
        if (meals.isNotEmpty() && Fuel.hasProfile(app)) {
            val g = Fuel.goals(app)
            val kcal = meals.sumOf { it.kcal }
            val protein = meals.sumOf { it.protein }.roundToInt()
            val pScore = minOf(1.0, protein.toDouble() / maxOf(1, g.protein))
            val kScore = maxOf(0.0, 1.0 - abs(kcal - g.kcal).toDouble() / maxOf(1, g.kcal))
            val s = ((pScore * 0.5 + kScore * 0.5) * 100).roundToInt()
            parts += Part("fuel", "Fuel", s, "$kcal / ${g.kcal} kcal, ${protein}g / ${g.protein}g protein")
            facts.append("Fuel: ${meals.size} meals logged, $kcal of ${g.kcal} kcal goal, ${protein} of ${g.protein} g protein. Foods: ${meals.joinToString { it.name }.take(300)}.\n")
        } else if (meals.isNotEmpty()) {
            val kcal = meals.sumOf { it.kcal }
            parts += Part("fuel", "Fuel", null, "$kcal kcal, no goal set")
            facts.append("Fuel: ${meals.size} meals, $kcal kcal, no goal set.\n")
        } else {
            parts += Part("fuel", "Fuel", null, "Nothing logged")
            facts.append("Fuel: no meals logged.\n")
        }

        // Mind
        val reflection = repo.getReflectionForDate(date)
        parts += Part("mind", "Reflection", if (reflection != null) 100 else 0, if (reflection != null) "Written" else "Not written")
        facts.append(if (reflection != null) "Reflection: \"${reflection.text.take(600)}\"\n" else "Reflection: not written.\n")

        app.getSharedPreferences("forge_alarms", Context.MODE_PRIVATE).getString("done_$date", null)?.let { w ->
            val (at, what) = w.split("|").let { it[0] to it.getOrElse(1) { "" } }
            facts.append("Woke with the Forge alarm at $at after $what.\n")
        }

        val streak = try { repo.calculateStreak() } catch (_: Exception) { 0 }
        facts.append("Streak: $streak days.\n")

        // Yesterday's fixes, so the verdict can check follow-through.
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(start - 12 * 3600_000L))
        saved(app, yesterday)?.let { y ->
            if (y.fixes.isNotEmpty()) facts.append("Yesterday's fixes to check: ${y.fixes.joinToString(" | ")}\n")
        }

        Day(date, parts, facts.toString(), missed)
    }

    // ── judging ──

    private const val SYSTEM = """You are Hercules, the performance coach inside the Forge discipline app, giving the user their end-of-day verdict.
You get the day's real numbers and a score Forge already worked out. Do not change the score.
Be direct and specific: name the actual labors, minutes, sets and foods. Push the user, never insult them, never moralise.
If yesterday's fixes are listed, say plainly whether today shows they were followed.
Fixes must be concrete actions for tomorrow, each under 14 words, starting with a verb.
If the day isn't over yet, judge what's done so far and say what can still be done today; don't treat meals or labors that can still happen as missed.
Talk about food only as fuel for training and recovery. Never shame eating, never call eating little "starving", and never suggest eating less.
Never give medical advice, body-fat guesses or extreme diet targets.
Reply with JSON only: {"verdict": "one or two sentences, under 40 words", "wins": ["..."], "fixes": ["..."]}
Give 1-3 wins (only real ones; if there are none, an empty list) and 2-3 fixes."""

    suspend fun judge(c: Context, day: Day): Result {
        val now = System.currentTimeMillis()
        return try {
            val now = java.util.Calendar.getInstance()
            val over = day.date != today() || now.get(java.util.Calendar.HOUR_OF_DAY) >= 20
            val clock = if (day.date == today()) SimpleDateFormat("h:mm a", Locale.US).format(Date()) else "end of day"
            val prompt = "Date: ${day.date}\nTime now: $clock (${if (over) "the day is basically over" else "the day isn't over yet"})\n" +
                "Forge score: ${day.score}/100\nPart scores: " +
                day.parts.joinToString { "${it.name} ${it.score?.toString() ?: "n/a"}" } + "\n\n" + day.facts
            val reply = Gemini.ask(c, SYSTEM, listOf(Gemini.Part.Text(prompt)), json = true, maxTokens = 2000, temperature = 0.5)
            val o = JSONObject(Gemini.jsonOf(reply))
            Result(
                day.date, day.score, o.optString("verdict").trim().ifBlank { fallbackLine(day) },
                strings(o.optJSONArray("wins")).take(3), strings(o.optJSONArray("fixes")).take(3),
                day.parts, now
            )
        } catch (e: Exception) {
            android.util.Log.e("ForgeVerdict", "Hercules couldn't judge the day: ${e.message}")
            Result(day.date, day.score, fallbackLine(day), emptyList(), fallbackFixes(day), day.parts, now, offline = true)
        }.also { save(c, it) }
    }

    private fun fallbackLine(day: Day): String = when {
        day.score >= 85 -> "A strong day. Do it again tomorrow."
        day.score >= 65 -> "Solid, with room left on the table."
        day.score >= 40 -> "Half a day's work. Tomorrow needs more."
        else -> "This one slipped. Reset and start early tomorrow."
    }

    private fun fallbackFixes(day: Day): List<String> {
        val out = mutableListOf<String>()
        if (day.missed.isNotEmpty()) out += "Do ${day.missed.first()} first thing tomorrow."
        day.parts.firstOrNull { it.key == "focus" && (it.score ?: 100) < 60 }?.let { out += "Book one 50-minute focus block before noon." }
        day.parts.firstOrNull { it.key == "mind" && it.score == 0 }?.let { out += "Write tonight's reflection before bed." }
        day.parts.firstOrNull { it.key == "training" && it.score == 25 }?.let { out += "Get a workout in, even a short one." }
        return out.take(3).ifEmpty { listOf("Set tomorrow's labors tonight.") }
    }

    internal fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() }
}
