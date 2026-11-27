package com.forge.app.ui.gym

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.forge.app.R
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.gym.BodyWeight
import com.forge.app.gym.GymCatalogue
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.GymSet
import com.forge.app.gym.Muscles
import com.forge.app.gym.Routine
import com.forge.app.gym.RoutineItem
import com.forge.app.gym.SetWithDate
import com.forge.app.gym.Strength
import com.forge.app.gym.Workout
import com.forge.app.gym.WorkoutSummary
import com.forge.app.ui.kit.Kit
import com.forge.app.ui.kit.Spark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gym log: routines, a workout screen with a row per set (previous, kg, reps, done),
 * rest timer, records, muscle recovery, body weight and history.
 */
class GymFragment : Fragment() {

    private val dao by lazy { GymDatabase.get(requireContext()).gymDao() }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var title: TextView
    private lateinit var idle: LinearLayout
    private lateinit var activeBox: LinearLayout

    // idle parts
    private lateinit var weekStats: List<TextView>
    private lateinit var routinesBox: LinearLayout
    private lateinit var recoveryBox: LinearLayout
    private lateinit var weightBox: LinearLayout
    private lateinit var bestsBox: LinearLayout
    private lateinit var historyBox: LinearLayout

    // active parts
    private lateinit var activeName: TextView
    private lateinit var activeSummary: TextView
    private lateinit var activeTime: TextView
    private lateinit var restCard: LinearLayout
    private lateinit var restTime: TextView
    private lateinit var exercisesBox: LinearLayout

    private var active: Workout? = null
    private var setsObservedFor = -1L
    private var sets: List<GymSet> = emptyList()
    private var structure = ""
    private var restFor: Map<String, Int> = emptyMap()
    private var previous: Map<String, List<GymSet>> = emptyMap()
    private var suggestion: Map<String, String> = emptyMap()
    private var routines: List<Routine> = emptyList()
    private var routineItems: List<RoutineItem> = emptyList()

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            updateRest()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val (root, page) = Kit.page(c)
        page.addView(Kit.label(c, "Gym log", bottom = 0))
        title = Kit.title(c, "Workouts").apply { id = R.id.tv_gym_title }
        page.addView(title)
        idle = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        activeBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        page.addView(idle)
        page.addView(activeBox)
        buildIdle(c)
        buildActive(c)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val c = requireContext()
        viewLifecycleOwner.lifecycleScope.launch { RoutineEditor.seedStarters(c) }

        dao.activeWorkout().asLiveData().observe(viewLifecycleOwner) { workout ->
            active = workout
            idle.visibility = if (workout == null) View.VISIBLE else View.GONE
            activeBox.visibility = if (workout == null) View.GONE else View.VISIBLE
            title.text = if (workout == null) "Workouts" else "Workout in progress"
            if (workout != null) {
                activeName.text = workout.name
                if (setsObservedFor != workout.id) {
                    setsObservedFor = workout.id
                    structure = ""
                    viewLifecycleOwner.lifecycleScope.launch { loadRestTimes(workout) }
                    dao.setsFor(workout.id).asLiveData().observe(viewLifecycleOwner) { list ->
                        if (active?.id == workout.id) onSets(list)
                    }
                }
            } else {
                setsObservedFor = -1L
                clearRest()
            }
        }
        dao.recentWorkouts(30).asLiveData().observe(viewLifecycleOwner) { renderHistory(it) }
        dao.bests(8).asLiveData().observe(viewLifecycleOwner) { renderBests(it) }
        dao.routines().asLiveData().observe(viewLifecycleOwner) { routines = it; renderRoutines() }
        dao.allRoutineItems().asLiveData().observe(viewLifecycleOwner) { routineItems = it; renderRoutines() }
        dao.setsSince(System.currentTimeMillis() - 8 * 86_400_000L).asLiveData().observe(viewLifecycleOwner) { renderRecovery(it) }
        dao.weights(60).asLiveData().observe(viewLifecycleOwner) { renderWeights(it) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        cancelRestAlarm(requireContext())
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        val left = restUntil(requireContext()) - System.currentTimeMillis()
        if (left > 1500) scheduleRestAlarm(requireContext(), left)
    }

    // ═════════════════════════ idle screen ═════════════════════════

    private fun buildIdle(c: Context) {
        idle.addView(Kit.primary(c, "Start empty workout") { startWorkout(null) }.apply { id = R.id.btn_start_workout })
        val (stats, values) = Kit.statsCard(c, "0" to "This week", "0" to "Sets", "–" to "Last trained")
        weekStats = values
        idle.addView(stats)

        idle.addView(sectionHead(c, "Routines", "New routine") { RoutineEditor.open(c, viewLifecycleOwner.lifecycleScope, null) })
        routinesBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; layoutParams = Kit.lp(bottom = Kit.dp(c, 6)) }
        idle.addView(routinesBox)

        idle.addView(Kit.label(c, "Muscle recovery"))
        recoveryBox = Kit.card(c, bottom = 18)
        idle.addView(recoveryBox)

        idle.addView(Kit.label(c, "Body weight"))
        weightBox = Kit.card(c, bottom = 18)
        idle.addView(weightBox)

        idle.addView(Kit.label(c, "Best lifts"))
        bestsBox = Kit.card(c, bottom = 18).apply { setPadding(paddingLeft, Kit.dp(c, 8), paddingRight, Kit.dp(c, 8)) }
        idle.addView(bestsBox)

        val (library, _) = Kit.linkCard(c, "Exercise library", "${GymCatalogue.all(c).size} exercises with how-tos") {
            ExerciseUi.pick(c, "Library") { ex -> ExerciseUi.show(c, viewLifecycleOwner.lifecycleScope, ex.name) }
        }
        idle.addView(library)
        val (physique, _) = Kit.linkCard(c, "Physique check", "Progress photos and Hercules' read") {
            com.forge.app.ui.physique.PhysiqueUi.open(c, viewLifecycleOwner.lifecycleScope)
        }
        idle.addView(physique.apply { (layoutParams as LinearLayout.LayoutParams).bottomMargin = Kit.dp(c, 18) })

        idle.addView(Kit.label(c, "History"))
        historyBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        idle.addView(historyBox)
        idle.addView(credit(c))
    }

    private fun sectionHead(c: Context, label: String, action: String, onAction: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = Kit.lp(bottom = Kit.dp(c, 8))
        addView(Kit.label(c, label, bottom = 0), Kit.weight())
        addView(TextView(c).apply {
            text = action
            setTextColor(Kit.RED_TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(Kit.dp(c, 12), Kit.dp(c, 4), 0, Kit.dp(c, 4))
            setOnClickListener { onAction() }
        })
    }

    private fun credit(c: Context) = Kit.quiet(c, "Exercises: Free Exercise DB (public domain) and ExerciseDB (MIT)") {
        val text = try { c.assets.open("licenses/exercises.txt").bufferedReader().use { it.readText() } } catch (_: Exception) { "Licence text missing" }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme).setTitle("Exercise list").setMessage(text).setPositiveButton("Close", null).show()
    }.apply { id = R.id.tv_gym_credit; setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small)) }

    private fun renderRoutines() {
        val c = context ?: return
        if (!::routinesBox.isInitialized) return
        routinesBox.removeAllViews()
        if (routines.isEmpty()) {
            routinesBox.addView(Kit.sub(c, "Make a routine for each gym day, then start it in one tap.").apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })
            return
        }
        routines.forEach { r ->
            val items = routineItems.filter { it.routineId == r.id }
            val card = Kit.card(c, bottom = 8, vertical = false).apply {
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { RoutineEditor.open(c, viewLifecycleOwner.lifecycleScope, r.id) }
            }
            val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(Kit.row(c, r.name))
            val groups = items.mapNotNull { GymCatalogue.find(c, it.exercise)?.group ?: it.bodyPart.ifBlank { null } }.distinct().take(4)
            col.addView(Kit.sub(c, "${items.size} exercise${if (items.size == 1) "" else "s"}" +
                if (groups.isNotEmpty()) " · ${groups.joinToString(", ")}" else ""))
            card.addView(col, Kit.weight())
            card.addView(TextView(c).apply {
                text = "Start"
                gravity = Gravity.CENTER
                setTextColor(Kit.RED_TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_chip_on)
                setPadding(Kit.dp(c, 16), Kit.dp(c, 8), Kit.dp(c, 16), Kit.dp(c, 8))
                setOnClickListener { startWorkout(r) }
            })
            routinesBox.addView(card)
        }
    }

    private fun renderRecovery(list: List<SetWithDate>) {
        val c = context ?: return
        recoveryBox.removeAllViews()
        val status = Muscles.recovery(list, { GymCatalogue.find(c, it) })
        if (list.isEmpty()) {
            recoveryBox.addView(Kit.sub(c, "Log a workout and this shows which muscles are fresh, recovering or worked hard."))
            return
        }
        status.chunked(2).forEach { pair ->
            val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Kit.dp(c, 5), 0, Kit.dp(c, 5)) }
            pair.forEach { s ->
                val color = when (s.state) {
                    Muscles.State.WORKED -> Kit.RED
                    Muscles.State.RECOVERING -> Kit.AMBER
                    Muscles.State.FRESH -> Kit.GREEN
                    Muscles.State.IDLE -> Kit.FG3
                }
                val cell = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                cell.addView(Kit.dot(c, color))
                val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                col.addView(Kit.body(c, s.group).apply { setTypeface(typeface, Typeface.BOLD) })
                val sub = when (s.state) {
                    Muscles.State.IDLE -> "Not this week"
                    else -> "${s.state.label} · ${Strength.plain(s.hardSets)} sets"
                }
                col.addView(Kit.sub(c, sub))
                cell.addView(col)
                row.addView(cell, Kit.weight())
            }
            if (pair.size == 1) row.addView(View(c), Kit.weight())
            recoveryBox.addView(row)
        }
        recoveryBox.addView(Kit.sub(c, "Last 7 days. Red: trained hard in the last 2 days. Amber: still recovering. Green: ready.", Kit.FG3)
            .apply { setPadding(0, Kit.dp(c, 8), 0, 0) })
    }

    private fun renderWeights(list: List<BodyWeight>) {
        val c = context ?: return
        weightBox.removeAllViews()
        val top = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val latest = list.firstOrNull()
        val (col, _) = Kit.stat(c, latest?.let { Strength.kg(it.kg) } ?: "–", "Latest")
        top.addView(col, Kit.weight())
        if (list.size >= 2) {
            val monthAgo = System.currentTimeMillis() - 30 * 86_400_000L
            val ref = list.lastOrNull { it.timestamp >= monthAgo } ?: list.last()
            val diff = (latest!!.kg - ref.kg)
            top.addView(Kit.mono(c, (if (diff >= 0) "+" else "−") + Strength.kg(kotlin.math.abs(diff)) + " / 30 days"))
        }
        weightBox.addView(top)
        if (list.size >= 2) {
            weightBox.addView(Spark(c, list.take(30).reversed().map { it.kg })
                .apply { layoutParams = Kit.lp(h = Kit.dp(c, 48), top = Kit.dp(c, 8)) })
        }
        weightBox.addView(Kit.accent(c, "Log weight") { logWeight(latest) }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = Kit.dp(c, 10); bottomMargin = 0 }
        })
    }

    private fun logWeight(latest: BodyWeight?) {
        val c = requireContext()
        val field = Kit.input(c, "Weight (kg)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        latest?.let { field.setText(Strength.plain(it.kg)) }
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0); addView(field) }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle("Today's weight")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val kg = field.text.toString().replace(',', '.').toDoubleOrNull()
                if (kg == null || kg < 20 || kg > 400) { Toast.makeText(c, "Enter your weight in kg", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao.deleteWeightOn(date)
                    dao.insertWeight(BodyWeight(date = date, kg = kg))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderBests(bests: List<com.forge.app.gym.ExerciseBest>) {
        val c = context ?: return
        bestsBox.removeAllViews()
        if (bests.isEmpty()) {
            bestsBox.addView(Kit.sub(c, "Finish a workout to see your best lifts here.").apply { setPadding(0, Kit.dp(c, 6), 0, Kit.dp(c, 6)) })
        }
        bests.forEach { b ->
            val row = Kit.twoColumn(c, b.exercise, "${Strength.kg(b.weightKg)} × ${b.reps}")
            row.setOnClickListener { ExerciseUi.show(c, viewLifecycleOwner.lifecycleScope, b.exercise) }
            bestsBox.addView(row)
        }
    }

    private fun renderHistory(workouts: List<WorkoutSummary>) {
        val c = context ?: return
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val thisWeek = workouts.filter { it.startedAt >= weekStart }
        weekStats[0].text = thisWeek.size.toString()
        weekStats[1].text = thisWeek.sumOf { it.sets }.toString()
        weekStats[2].text = workouts.firstOrNull()?.let { daysAgo(it.startedAt) } ?: "–"

        historyBox.removeAllViews()
        if (workouts.isEmpty()) {
            historyBox.addView(Kit.sub(c, "No workouts yet. Start one when you get to the gym.").apply { setPadding(0, Kit.dp(c, 6), 0, Kit.dp(c, 6)) })
            return
        }
        val fmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        workouts.take(12).forEach { w ->
            val minutes = (((w.endedAt ?: w.startedAt) - w.startedAt) / 60000).toInt()
            val card = Kit.card(c, bottom = 8).apply {
                setPadding(paddingLeft, Kit.dp(c, 12), paddingRight, Kit.dp(c, 12))
                setOnClickListener { showWorkout(w) }
            }
            card.addView(Kit.twoColumn(c, w.name, "$minutes min", bold = true))
            card.addView(Kit.sub(c, "${fmt.format(Date(w.startedAt))} · ${w.sets} sets · ${Strength.kg(w.volume)}"))
            historyBox.addView(card)
        }
    }

    private fun showWorkout(w: WorkoutSummary) {
        val c = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { dao.setsForNow(w.id) }.filter { it.done == 1 }
            val text = list.map { it.exercise }.distinct().joinToString("\n\n") { name ->
                name + "\n" + list.filter { it.exercise == name }
                    .joinToString("\n") { "  ${typeTag(it.setType)}${Strength.kg(it.weightKg)} × ${it.reps}" }
            }.ifEmpty { "No sets" }
            AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                .setTitle(w.name)
                .setMessage(text)
                .setPositiveButton("Close", null)
                .setNeutralButton("Delete") { _, _ ->
                    AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                        .setTitle("Delete this workout?")
                        .setPositiveButton("Delete") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.deleteSetsFor(w.id); dao.deleteWorkout(w.id) }
                        }
                        .setNegativeButton("Keep", null)
                        .show()
                }
                .show()
        }
    }

    // ═════════════════════════ workout screen ═════════════════════════

    private fun buildActive(c: Context) {
        val head = Kit.card(c, background = R.drawable.bg_card_next, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        activeName = Kit.row(c, "").apply { id = R.id.tv_active_name }
        activeSummary = Kit.sub(c, "").apply { id = R.id.tv_active_summary }
        col.addView(activeName)
        col.addView(activeSummary)
        head.addView(col, Kit.weight())
        activeTime = Kit.num(c, "00:00").apply { id = R.id.tv_active_time; typeface = Typeface.MONOSPACE }
        head.addView(activeTime)
        activeBox.addView(head)

        restCard = Kit.card(c, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        val restCol = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        restCol.addView(Kit.label(c, "Rest", bottom = 0))
        restTime = Kit.num(c, "0:00", Kit.GREEN).apply { typeface = Typeface.MONOSPACE }
        restCol.addView(restTime)
        restCard.addView(restCol, Kit.weight())
        restCard.addView(Kit.chip(c, "−15s", false) { bumpRest(-15) })
        restCard.addView(Kit.chip(c, "+15s", false) { bumpRest(15) })
        restCard.addView(Kit.chip(c, "Skip", true) { clearRest() }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = 0 })
        activeBox.addView(restCard)

        exercisesBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; id = R.id.ll_exercises }
        activeBox.addView(exercisesBox)
        activeBox.addView(Kit.secondary(c, "Add exercise") { addExercise() }.apply { id = R.id.btn_add_exercise })
        activeBox.addView(Kit.primary(c, "Finish workout") { finishWorkout() }.apply { id = R.id.btn_finish_workout })
        activeBox.addView(Kit.quiet(c, "Discard workout") { confirmDiscard() }.apply { id = R.id.btn_discard_workout })
    }

    private fun startWorkout(routine: Routine?) {
        val c = requireContext()
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (dao.activeWorkoutNow() != null) return@launch
            val id = dao.insertWorkout(Workout(name = routine?.name ?: "$day workout", date = date, startedAt = now, routineId = routine?.id))
            if (routine != null) {
                dao.routineItems(routine.id).forEachIndexed { i, item ->
                    val last = dao.lastSession(item.exercise, id)
                    val ex = GymCatalogue.find(c, item.exercise)
                    val next = Strength.suggestion(last, item.repsLow, item.repsHigh, ex?.equipment ?: "")
                    repeat(item.sets) { s ->
                        val prev = last.filter { it.setType != "warmup" }.getOrNull(s)
                        dao.insertSet(GymSet(
                            workoutId = id, exercise = item.exercise, bodyPart = ex?.group ?: item.bodyPart,
                            weightKg = next?.first ?: prev?.weightKg ?: 0.0,
                            reps = next?.second ?: prev?.reps ?: item.repsLow,
                            done = 0, position = i
                        ))
                    }
                }
            }
        }
    }

    private suspend fun loadRestTimes(workout: Workout) {
        val rid = workout.routineId ?: run { restFor = emptyMap(); return }
        restFor = withContext(Dispatchers.IO) { dao.routineItems(rid) }.associate { it.exercise to it.restSec }
    }

    private fun onSets(list: List<GymSet>) {
        sets = list
        val done = list.filter { it.done == 1 }
        val names = list.map { it.exercise }.distinct()
        val volume = done.sumOf { it.weightKg * it.reps }
        activeSummary.text = if (list.isEmpty()) "Add your first exercise"
        else "${names.size} exercise${if (names.size == 1) "" else "s"} · ${done.size}/${list.size} sets · ${Strength.kg(volume)}"
        val key = list.joinToString("|") { "${it.id}:${it.done}:${it.setType}:${it.exercise}" }
        if (key == structure) return
        structure = key
        val workout = active ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val c = context ?: return@launch
            val prev = HashMap<String, List<GymSet>>()
            val sugg = HashMap<String, String>()
            withContext(Dispatchers.IO) {
                names.forEach { n ->
                    val last = dao.lastSession(n, workout.id)
                    prev[n] = last
                    val item = routineItems.firstOrNull { it.routineId == workout.routineId && it.exercise == n }
                    val ex = GymCatalogue.find(c, n)
                    Strength.suggestion(last, item?.repsLow ?: 8, item?.repsHigh ?: 12, ex?.equipment ?: "")?.let { (w, r) ->
                        sugg[n] = if (w > 0) "Try ${Strength.kg(w)} × $r" else "Try $r reps"
                    }
                }
            }
            previous = prev
            suggestion = sugg
            renderExercises()
        }
    }

    private fun renderExercises() {
        val c = context ?: return
        exercisesBox.removeAllViews()
        val byExercise = sets.groupBy { it.exercise }
        byExercise.keys.sortedBy { n -> byExercise[n]!!.minOf { it.position } }.forEach { name ->
            exercisesBox.addView(exerciseCard(c, name, byExercise[name]!!))
        }
    }

    private fun exerciseCard(c: Context, name: String, list: List<GymSet>): View {
        val card = Kit.card(c, bottom = 10)
        val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(Kit.row(c, name).apply {
            setOnClickListener { ExerciseUi.show(c, viewLifecycleOwner.lifecycleScope, name) }
        }, Kit.weight())
        head.addView(TextView(c).apply {
            text = "•••"
            setTextColor(Kit.FG2)
            setPadding(Kit.dp(c, 12), Kit.dp(c, 4), 0, Kit.dp(c, 4))
            contentDescription = "More for $name"
            setOnClickListener { exerciseMenu(name) }
        })
        card.addView(head)
        val last = previous[name].orEmpty()
        val hint = buildString {
            if (last.isNotEmpty()) append("Last time: " + last.filter { it.setType != "warmup" }.take(4).joinToString(", ") { "${Strength.plain(it.weightKg)}×${it.reps}" })
            suggestion[name]?.let { if (isNotEmpty()) append(" · "); append(it) }
            if (isEmpty()) append("First time logging this one")
        }
        card.addView(Kit.sub(c, hint).apply { setPadding(0, Kit.dp(c, 2), 0, Kit.dp(c, 8)) })

        card.addView(setHeader(c))
        var number = 0
        val work = last.filter { it.setType != "warmup" }
        list.forEach { s ->
            val label = if (s.setType == "warmup") "W" else { number++; if (s.setType == "drop") "D" else if (s.setType == "failure") "F" else "$number" }
            val prev = if (s.setType == "warmup") null else work.getOrNull(number - 1)
            card.addView(setRow(c, s, label, prev))
        }
        card.addView(Kit.accent(c, "Add set") { addSet(name, list.last()) }.apply {
            (layoutParams as LinearLayout.LayoutParams).apply { topMargin = Kit.dp(c, 8); bottomMargin = 0 }
        })
        return card
    }

    private fun setHeader(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, 0, 0, Kit.dp(c, 4))
        fun h(t: String, w: Int, weight: Float = 0f, center: Boolean = true) = addView(
            Kit.label(c, t, bottom = 0).apply { if (center) gravity = Gravity.CENTER },
            if (weight > 0) Kit.weight(weight) else LinearLayout.LayoutParams(Kit.dp(c, w), ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        h("Set", 34)
        h("Previous", 0, 1f, center = false)
        h("Kg", 72)
        h("Reps", 60)
        h("", 44)
    }

    private fun cell(c: Context, value: String, hint: String, decimal: Boolean) = EditText(c).apply {
        setText(value)
        this.hint = hint
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_NUMBER
        imeOptions = EditorInfo.IME_ACTION_DONE
        gravity = Gravity.CENTER
        isSingleLine = true
        typeface = Typeface.MONOSPACE
        setTextColor(Kit.FG)
        setHintTextColor(Kit.FG3)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_body))
        setBackgroundResource(R.drawable.bg_chip)
        setPadding(Kit.dp(c, 4), Kit.dp(c, 8), Kit.dp(c, 4), Kit.dp(c, 8))
        setSelectAllOnFocus(true)
    }

    private fun setRow(c: Context, s: GymSet, label: String, prev: GymSet?): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Kit.dp(c, 3), 0, Kit.dp(c, 3))
            if (s.done == 1) setBackgroundColor(0x1400E676)
        }
        val typeColor = when (s.setType) { "warmup" -> Kit.AMBER; "drop" -> Kit.BLUE; "failure" -> Kit.RED_TEXT; else -> Kit.FG }
        row.addView(Kit.mono(c, label, typeColor).apply {
            gravity = Gravity.CENTER
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, Kit.dp(c, 10), 0, Kit.dp(c, 10))
            contentDescription = "Set type, tap to change"
            setOnClickListener { cycleType(s) }
        }, LinearLayout.LayoutParams(Kit.dp(c, 34), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(Kit.mono(c, prev?.let { "${Strength.plain(it.weightKg)}×${it.reps}" } ?: "—", Kit.FG2).apply {
            setPadding(Kit.dp(c, 6), 0, 0, 0)
            setOnClickListener {
                if (prev != null && s.done == 0) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.updateSet(s.copy(weightKg = prev.weightKg, reps = prev.reps)) }
                    structure = ""
                }
            }
        }, Kit.weight())
        val kg = cell(c, if (s.weightKg > 0 || s.done == 1) Strength.plain(s.weightKg) else "", "kg", true).apply { id = R.id.et_set_kg }
        val reps = cell(c, if (s.reps > 0) s.reps.toString() else "", "reps", false).apply { id = R.id.et_set_reps }
        row.addView(kg, LinearLayout.LayoutParams(Kit.dp(c, 66), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Kit.dp(c, 6) })
        row.addView(reps, LinearLayout.LayoutParams(Kit.dp(c, 54), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Kit.dp(c, 6) })
        val save = {
            val w = kg.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val r = reps.text.toString().toIntOrNull() ?: 0
            if (w != s.weightKg || r != s.reps) {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.updateSet(s.copy(weightKg = w, reps = r)) }
            }
        }
        kg.setOnFocusChangeListener { _, has -> if (!has) save() }
        reps.setOnFocusChangeListener { _, has -> if (!has) save() }
        row.addView(ImageView(c).apply {
            id = R.id.btn_set_done
            setImageResource(if (s.done == 1) R.drawable.check_circle_done else R.drawable.check_circle_empty)
            contentDescription = if (s.done == 1) "Done" else "Mark set done"
            setPadding(Kit.dp(c, 8), Kit.dp(c, 8), Kit.dp(c, 8), Kit.dp(c, 8))
            setOnClickListener {
                val w = kg.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                val r = reps.text.toString().toIntOrNull() ?: 0
                toggleDone(s, w, r)
            }
        }, LinearLayout.LayoutParams(Kit.dp(c, 44), Kit.dp(c, 44)))
        row.setOnLongClickListener {
            AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                .setTitle("Delete this set?")
                .setPositiveButton("Delete") { _, _ -> viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.deleteSet(s.id) } }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }
        return row
    }

    private fun toggleDone(s: GymSet, weight: Double, reps: Int) {
        val c = requireContext()
        if (s.done == 1) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.updateSet(s.copy(done = 0, weightKg = weight, reps = reps)) }
            return
        }
        if (reps <= 0) { Toast.makeText(c, "Enter the reps for this set", Toast.LENGTH_SHORT).show(); return }
        val workout = active ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val best = withContext(Dispatchers.IO) {
                val b = dao.bestWeightBefore(s.exercise, workout.id)
                dao.updateSet(s.copy(done = 1, weightKg = weight, reps = reps, timestamp = System.currentTimeMillis()))
                b
            }
            if (best > 0 && weight > best && s.setType != "warmup") {
                Toast.makeText(c, "New best on ${s.exercise}: ${Strength.kg(weight)}", Toast.LENGTH_LONG).show()
            }
            if (s.setType != "warmup") startRest(restFor[s.exercise] ?: 90)
        }
    }

    private fun cycleType(s: GymSet) {
        val next = when (s.setType) { "normal" -> "warmup"; "warmup" -> "drop"; "drop" -> "failure"; else -> "normal" }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.updateSet(s.copy(setType = next)) }
        Toast.makeText(requireContext(), when (next) {
            "warmup" -> "Warm-up set (W)"; "drop" -> "Drop set (D)"; "failure" -> "Set to failure (F)"; else -> "Working set"
        }, Toast.LENGTH_SHORT).show()
    }

    private fun addSet(name: String, after: GymSet) {
        val workout = active ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            dao.insertSet(GymSet(workoutId = workout.id, exercise = name, bodyPart = after.bodyPart,
                weightKg = after.weightKg, reps = after.reps, done = 0, position = after.position))
        }
    }

    private fun addExercise() {
        val c = requireContext()
        val workout = active ?: return
        ExerciseUi.pick(c, "Add exercise") { ex ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val last = dao.lastSession(ex.name, workout.id).filter { it.setType != "warmup" }
                val pos = dao.maxPosition(workout.id) + 1
                val first = last.firstOrNull()
                repeat(maxOf(1, minOf(last.size, 4))) { i ->
                    val p = last.getOrNull(i) ?: first
                    dao.insertSet(GymSet(workoutId = workout.id, exercise = ex.name, bodyPart = ex.group,
                        weightKg = p?.weightKg ?: 0.0, reps = p?.reps ?: 0, done = 0, position = pos))
                }
            }
        }
    }

    private fun exerciseMenu(name: String) {
        val c = requireContext()
        val workout = active ?: return
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle(name)
            .setItems(arrayOf("How to do it", "Swap for a variation", "Remove from workout")) { _, which ->
                when (which) {
                    0 -> ExerciseUi.show(c, viewLifecycleOwner.lifecycleScope, name)
                    1 -> {
                        val ex = GymCatalogue.find(c, name)
                        val vars = if (ex != null) GymCatalogue.variations(c, ex, 15) else emptyList()
                        ExerciseUi.pick(c, "Swap $name", vars, "Same muscle") { to ->
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.renameExercise(workout.id, name, to.name, to.group) }
                        }
                    }
                    2 -> viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { dao.deleteExerciseFrom(workout.id, name) }
                }
            }
            .show()
    }

    private fun finishWorkout() {
        val workout = active ?: return
        val c = requireContext()
        activity?.currentFocus?.clearFocus()
        viewLifecycleOwner.lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) { dao.setsForNow(workout.id) }
            val done = all.filter { it.done == 1 }
            if (done.isEmpty()) { confirmDiscard(); return@launch }
            val open = all.size - done.size
            if (open > 0) {
                AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                    .setTitle("Finish with $open set${if (open == 1) "" else "s"} not done?")
                    .setMessage("Sets you didn't tick are dropped.")
                    .setPositiveButton("Finish") { _, _ -> viewLifecycleOwner.lifecycleScope.launch { saveWorkout(workout, done) } }
                    .setNegativeButton("Keep going", null)
                    .show()
            } else saveWorkout(workout, done)
        }
    }

    private suspend fun saveWorkout(workout: Workout, done: List<GymSet>) {
        val c = context ?: return
        val prs = withContext(Dispatchers.IO) {
            val history = done.map { it.exercise }.distinct().associateWith { dao.historyOf(it) }
            dao.deletePlannedSets(workout.id)
            dao.updateWorkout(workout.copy(endedAt = System.currentTimeMillis()))
            Strength.prs(done, history)
        }
        clearRest()
        val ticked = tickGymLabor()
        val minutes = ((System.currentTimeMillis() - workout.startedAt) / 60000).toInt()
        val volume = done.sumOf { it.weightKg * it.reps }
        val message = buildString {
            append("$minutes min · ${done.size} sets · ${Strength.kg(volume)} lifted")
            if (prs.isNotEmpty()) {
                append("\n\nNew records\n")
                prs.forEach { append("• ${it.exercise}: ${it.kind.lowercase()} ${it.value}\n") }
            }
            if (ticked != null) append("\nTicked \"${ticked.first}\"" + if (ticked.second > 0) " · +${ticked.second} XP" else "")
        }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle(if (prs.isNotEmpty()) "Workout saved. New records!" else "Workout saved")
            .setMessage(message.trim())
            .setPositiveButton("Nice", null)
            .show()
    }

    /** Ticks today's gym or exercise labor, if there is one still open. Returns its name and XP. */
    private suspend fun tickGymLabor(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        try {
            val repository = ForgeRepository(ForgeDatabase.getDatabase(requireContext().applicationContext))
            val match = Regex("gym|exercise|workout|lift|train|strength", RegexOption.IGNORE_CASE)
            val labor = repository.getLaborsForDateSync(repository.getTodayDate())
                .firstOrNull { !it.done && match.containsMatchIn(it.name) } ?: return@withContext null
            labor.name to repository.completeLabor(labor)
        } catch (_: Exception) {
            null
        }
    }

    private fun confirmDiscard() {
        val workout = active ?: return
        AlertDialog.Builder(requireContext(), R.style.ForgeDialogTheme)
            .setTitle("Discard workout")
            .setMessage("Remove this workout and its sets?")
            .setPositiveButton("Discard") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    dao.deleteSetsFor(workout.id)
                    dao.deleteWorkout(workout.id)
                }
                clearRest()
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    // ═════════════════════════ clock and rest timer ═════════════════════════

    private fun updateClock() {
        val workout = active ?: return
        val seconds = ((System.currentTimeMillis() - workout.startedAt) / 1000).toInt()
        activeTime.text = if (seconds >= 3600) String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        else String.format("%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return
        setRestUntil(requireContext(), System.currentTimeMillis() + seconds * 1000L)
        updateRest()
    }

    private fun bumpRest(delta: Int) {
        val c = requireContext()
        val until = restUntil(c)
        if (until <= System.currentTimeMillis()) return
        setRestUntil(c, maxOf(System.currentTimeMillis() + 1000, until + delta * 1000L))
        updateRest()
    }

    private fun clearRest() {
        val c = context ?: return
        setRestUntil(c, 0)
        if (::restCard.isInitialized) restCard.visibility = View.GONE
    }

    private fun updateRest() {
        val c = context ?: return
        val until = restUntil(c)
        if (until == 0L) { restCard.visibility = View.GONE; return }
        val left = ((until - System.currentTimeMillis()) / 1000).toInt()
        if (left <= 0) {
            setRestUntil(c, 0)
            restCard.visibility = View.GONE
            buzz(c)
            Toast.makeText(c, "Rest's over. Next set!", Toast.LENGTH_SHORT).show()
            return
        }
        restCard.visibility = View.VISIBLE
        restTime.text = String.format("%d:%02d", left / 60, left % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(tick)
        setsObservedFor = -1L
        structure = ""
    }

    // ═════════════════════════ helpers ═════════════════════════

    private fun typeTag(t: String) = when (t) { "warmup" -> "W  "; "drop" -> "D  "; "failure" -> "F  "; else -> "" }

    private fun daysAgo(time: Long): String {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            time >= start -> "Today"
            time >= start - 86_400_000L -> "1 day"
            else -> "${((start - time) / 86_400_000L) + 1} days"
        }
    }

    companion object {
        private const val PREF_REST = "gym_rest_until"

        fun restUntil(c: Context): Long = c.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE).getLong(PREF_REST, 0L)

        fun setRestUntil(c: Context, at: Long) {
            c.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE).edit().putLong(PREF_REST, at).apply()
        }

        fun buzz(c: Context) {
            try {
                val v = c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
                else @Suppress("DEPRECATION") v.vibrate(500)
            } catch (_: Exception) {
            }
        }

        private fun restIntent(c: Context) = PendingIntent.getBroadcast(
            c, 7301, Intent(c, RestOverReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun scheduleRestAlarm(c: Context, inMs: Long) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + inMs, restIntent(c))
        }

        fun cancelRestAlarm(c: Context) {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(restIntent(c))
        }
    }
}
