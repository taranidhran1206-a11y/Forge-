package com.forge.app.ui.physique

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.forge.app.R
import com.forge.app.gym.GymCatalogue
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.Muscles
import com.forge.app.gym.Strength
import com.forge.app.ui.kit.Kit
import com.forge.app.util.Gemini
import com.forge.app.util.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Physique check: front/side/back photos over time, side by side, and Hercules' read on
 * what's changing. Photos stay on the phone; only an analysis sends two of them to Gemini.
 */
object PhysiqueUi {

    private val POSES = listOf("Front", "Side", "Back")

    data class Shot(val file: String, val date: String, val pose: String, val weightKg: Double?)
    data class Read(val date: String, val text: String)

    private fun dir(c: Context) = Photos.dir(c, "physique")
    private fun index(c: Context) = File(dir(c), "index.json")
    private fun readsFile(c: Context) = File(dir(c), "reads.json")

    fun shots(c: Context): List<Shot> = try {
        val a = JSONArray(index(c).readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Shot(o.getString("file"), o.getString("date"), o.getString("pose"), if (o.has("kg")) o.getDouble("kg") else null)
        }.filter { File(dir(c), it.file).exists() }
    } catch (_: Exception) { emptyList() }

    private fun saveShots(c: Context, list: List<Shot>) {
        val a = JSONArray()
        list.forEach { s -> a.put(JSONObject().put("file", s.file).put("date", s.date).put("pose", s.pose).apply { s.weightKg?.let { put("kg", it) } }) }
        index(c).writeText(a.toString())
    }

    fun reads(c: Context): List<Read> = try {
        val a = JSONArray(readsFile(c).readText())
        (0 until a.length()).map { Read(a.getJSONObject(it).getString("date"), a.getJSONObject(it).getString("text")) }
    } catch (_: Exception) { emptyList() }

    private fun saveRead(c: Context, r: Read) {
        val a = JSONArray()
        (listOf(r) + reads(c)).take(30).forEach { a.put(JSONObject().put("date", it.date).put("text", it.text)) }
        readsFile(c).writeText(a.toString())
    }

    private fun activityOf(c: Context): Activity? {
        var x: Context? = c
        while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }
        return null
    }

    fun open(c: Context, scope: CoroutineScope) {
        var dialog: Dialog? = null
        val (root, page) = Kit.sheet(c, "Physique check", "Progress photos") { dialog?.dismiss() }
        val body = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(body)

        fun render() {
            body.removeAllViews()
            val all = shots(c)
            body.addView(Kit.primary(c, "Take today's photos") { addPhoto(c, scope, camera = true) { render() } })
            body.addView(Kit.secondary(c, "Add from gallery") { addPhoto(c, scope, camera = false) { render() } })
            body.addView(Kit.sub(c, "Same spot, same light, relaxed and flexed if you like. Front, side and back. Photos stay on this phone.")
                .apply { setPadding(0, 0, 0, Kit.dp(c, 16)) })

            if (all.isEmpty()) {
                body.addView(Kit.card(c).apply { addView(Kit.body(c, "No photos yet. Take a baseline today, then one every 2 to 4 weeks.")) })
                return
            }
            // Compare first and latest, same pose
            val dates = all.map { it.date }.distinct().sorted()
            if (dates.size >= 2) {
                body.addView(Kit.label(c, "First vs latest"))
                val pose = POSES.firstOrNull { p -> all.any { it.date == dates.first() && it.pose == p } && all.any { it.date == dates.last() && it.pose == p } }
                if (pose != null) {
                    val a = all.first { it.date == dates.first() && it.pose == pose }
                    val b = all.last { it.date == dates.last() && it.pose == pose }
                    val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 18)) }
                    row.addView(photoTile(c, a), LinearLayout.LayoutParams(0, Kit.dp(c, 260), 1f).apply { marginEnd = Kit.dp(c, 8) })
                    row.addView(photoTile(c, b), LinearLayout.LayoutParams(0, Kit.dp(c, 260), 1f))
                    body.addView(row)
                } else {
                    body.addView(Kit.sub(c, "Take the same pose as your first photos to compare them.").apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })
                }
            }

            body.addView(Kit.accent(c, if (dates.size >= 2) "Ask Hercules what's changed" else "Ask Hercules for a baseline read") {
                AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                    .setTitle("Send photos to Gemini?")
                    .setMessage("Hercules needs to see them. This sends your ${if (dates.size >= 2) "first and latest" else "latest"} front photo (or whichever pose you have) to Google's Gemini for this one read. Nothing else leaves your phone.")
                    .setPositiveButton("Send") { _, _ -> analyse(c, scope) { render() } }
                    .setNegativeButton("Cancel", null)
                    .show()
            })

            val rs = reads(c)
            if (rs.isNotEmpty()) {
                body.addView(Kit.label(c, "Hercules' reads"))
                rs.take(5).forEach { r ->
                    body.addView(Kit.card(c, bottom = 8).apply {
                        addView(Kit.label(c, pretty(r.date), bottom = 4))
                        addView(Kit.body(c, r.text))
                    })
                }
                body.addView(Kit.spacer(c, 10))
            }

            body.addView(Kit.label(c, "All photos"))
            dates.reversed().forEach { d ->
                val list = all.filter { it.date == d }
                body.addView(Kit.sub(c, pretty(d) + (list.firstNotNullOfOrNull { it.weightKg }?.let { " · ${Strength.kg(it)}" } ?: "")))
                val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = Kit.lp(top = Kit.dp(c, 6), bottom = Kit.dp(c, 14)) }
                list.forEach { s ->
                    row.addView(photoTile(c, s).apply {
                        setOnLongClickListener {
                            AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                                .setTitle("Delete this photo?")
                                .setPositiveButton("Delete") { _, _ ->
                                    File(dir(c), s.file).delete()
                                    saveShots(c, shots(c).filter { it.file != s.file })
                                    render()
                                }
                                .setNegativeButton("Keep", null)
                                .show()
                            true
                        }
                    }, LinearLayout.LayoutParams(0, Kit.dp(c, 160), 1f).apply { marginEnd = Kit.dp(c, 6) })
                }
                repeat(3 - list.size.coerceAtMost(3)) { row.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f)) }
                body.addView(row)
            }
        }
        render()
        dialog = Kit.showFull(c, root)
    }

    private fun pretty(date: String) = try {
        SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!)
    } catch (_: Exception) { date }

    private fun photoTile(c: Context, s: Shot): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xFF111111.toInt()); cornerRadius = Kit.dpf(c, 14f); setStroke(Kit.dp(c, 1), 0xFF1E1E1E.toInt()) }
            clipToOutline = true
        }
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "${s.pose} photo" }
        box.addView(img, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        box.addView(TextView(c).apply {
            text = "${s.pose} · ${s.date.substring(5)}"
            setTextColor(Kit.FG2)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, Kit.dp(c, 4), 0, Kit.dp(c, 4))
        })
        Thread {
            val bmp = Photos.load(File(dir(c), s.file), 720)
            img.post { if (bmp != null) img.setImageBitmap(bmp) }
        }.start()
        return box
    }

    private fun addPhoto(c: Context, scope: CoroutineScope, camera: Boolean, done: () -> Unit) {
        val activity = activityOf(c) ?: return
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle("Which pose?")
            .setItems(POSES.toTypedArray()) { _, which ->
                val pose = POSES[which]
                val now = Date()
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
                val name = "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)}_${pose.lowercase()}.jpg"
                val file = File(dir(c), name)
                val after = { ok: Boolean ->
                    if (ok) scope.launch {
                        val kg = withContext(Dispatchers.IO) { GymDatabase.get(c).gymDao().latestWeight()?.kg }
                        saveShots(c, shots(c) + Shot(name, date, pose, kg))
                        done()
                    } else file.delete()
                    Unit
                }
                if (camera) Photos.take(activity, file, after) else Photos.pick(activity, file, after)
            }
            .show()
    }

    private fun analyse(c: Context, scope: CoroutineScope, done: () -> Unit) {
        val all = shots(c)
        val dates = all.map { it.date }.distinct().sorted()
        if (dates.isEmpty()) return
        fun pick(d: String) = all.filter { it.date == d }.let { l -> l.firstOrNull { it.pose == "Front" } ?: l.first() }
        val first = pick(dates.first())
        val last = pick(dates.last())
        val wait = Toast.makeText(c, "Hercules is looking…", Toast.LENGTH_LONG)
        wait.show()
        scope.launch {
            try {
                val context = withContext(Dispatchers.IO) {
                    val dao = GymDatabase.get(c).gymDao()
                    val since = System.currentTimeMillis() - 28 * 86_400_000L
                    val sets = dao.setsSinceNow(since)
                    val groups = Muscles.recovery(sets, { GymCatalogue.find(c, it) }).filter { it.hardSets > 0 }
                        .sortedByDescending { it.hardSets }.joinToString(", ") { "${it.group} ${Strength.plain(it.hardSets)} sets" }
                    val workouts = sets.map { it.workoutId }.distinct().size
                    "Last 4 weeks of training: $workouts workouts; sets per muscle: ${groups.ifEmpty { "none logged" }}."
                }
                val parts = mutableListOf<Gemini.Part>()
                val single = first.file == last.file
                parts.add(Gemini.Part.Text(if (single) "Baseline photo (${first.pose}, ${first.date}${first.weightKg?.let { ", ${Strength.kg(it)}" } ?: ""}):"
                    else "Photo 1, earlier (${first.pose}, ${first.date}${first.weightKg?.let { ", ${Strength.kg(it)}" } ?: ""}):"))
                Photos.jpeg(File(dir(c), first.file))?.let { parts.add(Gemini.Part.Image(it)) }
                if (!single) {
                    parts.add(Gemini.Part.Text("Photo 2, latest (${last.pose}, ${last.date}${last.weightKg?.let { ", ${Strength.kg(it)}" } ?: ""}):"))
                    Photos.jpeg(File(dir(c), last.file))?.let { parts.add(Gemini.Part.Image(it)) }
                }
                parts.add(Gemini.Part.Text(context))
                val system = """
                    You are Hercules, a straight-talking but respectful strength coach in the Forge app.
                    You look at progress photos of the user, a young man training at the gym, and give a short physique read.
                    ${if (single) "Describe his starting point" else "Describe what visibly changed between the photos"}: muscle development by area
                    (shoulders, chest, arms, back, core, legs) and posture. Say which areas look like they're lagging compared with the
                    rest, and give 2-3 concrete training focuses for the next 4 weeks that fit his logged training.
                    Be honest and specific, never insulting, no body-fat guesses, no diet numbers, no medical claims.
                    If the photos are too dark, blurry or don't show a body, say what to fix instead.
                    Plain text, under 140 words, no markdown, no lists with symbols; short sentences.
                """.trimIndent()
                val text = Gemini.ask(c, system, parts, temperature = 0.5, maxTokens = 600).trim()
                saveRead(c, Read(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()), text))
                wait.cancel()
                done()
            } catch (e: Exception) {
                wait.cancel()
                Toast.makeText(c, "Couldn't get a read: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
