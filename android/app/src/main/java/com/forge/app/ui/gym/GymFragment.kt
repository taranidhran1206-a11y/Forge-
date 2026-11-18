package com.forge.app.ui.gym

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.forge.app.R
import com.forge.app.data.ForgeDatabase
import com.forge.app.data.ForgeRepository
import com.forge.app.databinding.FragmentGymBinding
import com.forge.app.gym.GymCatalogue
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.GymSet
import com.forge.app.gym.Workout
import com.forge.app.gym.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Log workouts set by set; history, weekly count and best lifts. */
class GymFragment : Fragment() {

    private var _binding: FragmentGymBinding? = null
    private val binding get() = _binding!!

    private val dao by lazy { GymDatabase.get(requireContext()).gymDao() }
    private val handler = Handler(Looper.getMainLooper())
    private var active: Workout? = null
    private var setsObservedFor = -1L

    private val clock = object : Runnable {
        override fun run() {
            val workout = active ?: return
            val b = _binding ?: return
            val seconds = ((System.currentTimeMillis() - workout.startedAt) / 1000).toInt()
            b.tvActiveTime.text = if (seconds >= 3600) {
                String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
            } else String.format("%02d:%02d", seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGymBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartWorkout.setOnClickListener { startWorkout() }
        binding.btnAddExercise.setOnClickListener { pickExercise() }
        binding.btnFinishWorkout.setOnClickListener { finishWorkout() }
        binding.btnDiscardWorkout.setOnClickListener { confirmDiscard() }
        binding.tvGymCredit.setOnClickListener { showLicence() }

        dao.activeWorkout().asLiveData().observe(viewLifecycleOwner) { workout ->
            active = workout
            binding.llIdle.visibility = if (workout == null) View.VISIBLE else View.GONE
            binding.llActive.visibility = if (workout == null) View.GONE else View.VISIBLE
            binding.tvGymTitle.text = if (workout == null) "Workouts" else "Workout in progress"
            handler.removeCallbacks(clock)
            if (workout != null) {
                binding.tvActiveName.text = workout.name
                handler.post(clock)
                if (setsObservedFor != workout.id) {
                    setsObservedFor = workout.id
                    dao.setsFor(workout.id).asLiveData().observe(viewLifecycleOwner) { sets ->
                        if (active?.id == workout.id) renderSets(sets)
                    }
                }
            }
        }

        dao.recentWorkouts(30).asLiveData().observe(viewLifecycleOwner) { renderHistory(it) }
        dao.bests(6).asLiveData().observe(viewLifecycleOwner) { bests ->
            binding.llBests.removeAllViews()
            if (bests.isEmpty()) {
                binding.llBests.addView(subText("Finish a workout to see your best lifts here."))
            }
            bests.forEach { best ->
                binding.llBests.addView(twoColumnRow(best.exercise, "${kg(best.weightKg)} × ${best.reps}"))
            }
        }
    }

    // --- Actions ---

    private fun startWorkout() {
        val now = System.currentTimeMillis()
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now))
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        lifecycleScope.launch {
            if (dao.activeWorkoutNow() == null) {
                dao.insertWorkout(Workout(name = "$day workout", date = date, startedAt = now))
            }
        }
    }

    private fun finishWorkout() {
        val workout = active ?: return
        lifecycleScope.launch {
            val sets = dao.setsForNow(workout.id)
            if (sets.isEmpty()) {
                confirmDiscard()
                return@launch
            }
            dao.updateWorkout(workout.copy(endedAt = System.currentTimeMillis()))
            val ticked = tickGymLabor()
            val minutes = ((System.currentTimeMillis() - workout.startedAt) / 60000).toInt()
            val volume = sets.sumOf { it.weightKg * it.reps }
            val message = buildString {
                append("Workout saved · ${sets.size} sets · ${minutes} min · ${kg(volume)} lifted")
                if (ticked != null) append("\nTicked \"${ticked.first}\"" + if (ticked.second > 0) " · +${ticked.second} XP" else "")
            }
            context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
        }
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
                lifecycleScope.launch {
                    dao.deleteSetsFor(workout.id)
                    dao.deleteWorkout(workout.id)
                }
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun pickExercise() {
        val context = requireContext()
        val search = input("Search exercises, e.g. bench or chest", InputType.TYPE_CLASS_TEXT)
        val list = ListView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360))
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(search)
            addView(list)
        }
        var results: List<GymCatalogue.Exercise> = emptyList()
        var custom: String? = null
        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_2, android.R.id.text1, mutableListOf()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val title = view.findViewById<TextView>(android.R.id.text1)
                val sub = view.findViewById<TextView>(android.R.id.text2)
                title.setTextColor(Color.parseColor("#F5F5F5"))
                sub.setTextColor(Color.parseColor("#9E9E9E"))
                if (custom != null && position == 0) {
                    title.text = "Add \"$custom\""
                    sub.text = "Your own exercise"
                } else {
                    val e = results[position - if (custom != null) 1 else 0]
                    title.text = e.name
                    sub.text = e.subtitle
                }
                return view
            }
        }
        list.adapter = adapter

        fun refresh(query: String) {
            results = GymCatalogue.search(context, query)
            val q = query.trim()
            custom = if (q.length >= 2 && results.none { it.name.equals(q, true) }) GymCatalogue.titleCase(q) else null
            adapter.clear()
            adapter.addAll(List(results.size + if (custom != null) 1 else 0) { "" })
        }
        refresh("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh(s?.toString() ?: "")
        })

        val dialog = AlertDialog.Builder(context, R.style.ForgeDialogTheme)
            .setTitle("Add exercise")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val chosen = if (custom != null && position == 0) {
                GymCatalogue.Exercise(custom!!, "", "", "")
            } else results[position - if (custom != null) 1 else 0]
            dialog.dismiss()
            addSet(chosen.name, chosen.bodyPart)
        }
        dialog.show()
    }

    private fun addSet(exercise: String, bodyPart: String) {
        val workout = active ?: return
        val context = requireContext()
        lifecycleScope.launch {
            val last = dao.lastSet(exercise)
            val weight = input("Weight (kg)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val reps = input("Reps", InputType.TYPE_CLASS_NUMBER)
            if (last != null) {
                weight.setText(plain(last.weightKg))
                reps.setText(last.reps.toString())
            }
            val box = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(8), dp(20), 0)
                addView(weight)
                addView(reps.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10) })
                if (last != null) addView(subText("Last time: ${kg(last.weightKg)} × ${last.reps}"))
            }
            AlertDialog.Builder(context, R.style.ForgeDialogTheme)
                .setTitle(exercise)
                .setView(box)
                .setPositiveButton("Log set") { _, _ ->
                    val w = weight.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                    val r = reps.text.toString().toIntOrNull() ?: 0
                    if (r <= 0) {
                        Toast.makeText(context, "Enter the reps for this set", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        val best = dao.bestWeightBefore(exercise, workout.id)
                        dao.insertSet(GymSet(workoutId = workout.id, exercise = exercise, bodyPart = bodyPart, weightKg = w, reps = r))
                        if (best > 0 && w > best) {
                            Toast.makeText(context, "New best on $exercise: ${kg(w)}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // --- Rendering ---

    private fun renderSets(sets: List<GymSet>) {
        val b = _binding ?: return
        val volume = sets.sumOf { it.weightKg * it.reps }
        val exercises = sets.map { it.exercise }.distinct()
        b.tvActiveSummary.text = if (sets.isEmpty()) "Add your first exercise"
        else "${exercises.size} exercise${if (exercises.size == 1) "" else "s"} · ${sets.size} sets · ${kg(volume)}"

        b.llExercises.removeAllViews()
        exercises.forEach { name ->
            val its = sets.filter { it.exercise == name }
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                val pad = resources.getDimensionPixelSize(R.dimen.card_pad)
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            card.addView(TextView(requireContext()).apply {
                text = name
                setTextColor(Color.parseColor("#F5F5F5"))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_row))
                setTypeface(typeface, Typeface.BOLD)
            })
            its.forEachIndexed { index, set ->
                val row = twoColumnRow("Set ${index + 1}", "${kg(set.weightKg)} × ${set.reps}")
                row.setOnLongClickListener {
                    AlertDialog.Builder(requireContext(), R.style.ForgeDialogTheme)
                        .setTitle("Delete set ${index + 1}?")
                        .setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { dao.deleteSet(set.id) } }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                card.addView(row)
            }
            card.addView(TextView(requireContext()).apply {
                text = "Add set"
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#FF8A80"))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_body))
                setTypeface(typeface, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_chip_on)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.tap_min)
                ).apply { topMargin = dp(8) }
                setOnClickListener { addSet(name, its.first().bodyPart) }
            })
            b.llExercises.addView(card)
        }
    }

    private fun renderHistory(workouts: List<WorkoutSummary>) {
        val b = _binding ?: return
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val thisWeek = workouts.filter { it.startedAt >= weekStart }
        b.tvWeekWorkouts.text = thisWeek.size.toString()
        b.tvWeekSets.text = thisWeek.sumOf { it.sets }.toString()
        b.tvLastWorkout.text = workouts.firstOrNull()?.let { daysAgo(it.startedAt) } ?: "–"

        b.llHistory.removeAllViews()
        if (workouts.isEmpty()) {
            b.llHistory.addView(subText("No workouts yet. Start one when you get to the gym."))
            return
        }
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        workouts.take(10).forEach { w ->
            val minutes = (((w.endedAt ?: w.startedAt) - w.startedAt) / 60000).toInt()
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                val pad = resources.getDimensionPixelSize(R.dimen.card_pad)
                setPadding(pad, dp(12), pad, dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { showWorkout(w) }
            }
            row.addView(twoColumnRow(w.name, "$minutes min", bold = true))
            row.addView(subText("${dateFormat.format(Date(w.startedAt))} · ${w.sets} sets · ${kg(w.volume)}"))
            b.llHistory.addView(row)
        }
    }

    private fun showWorkout(w: WorkoutSummary) {
        lifecycleScope.launch {
            val sets = dao.setsForNow(w.id)
            val text = sets.map { it.exercise }.distinct().joinToString("\n\n") { name ->
                name + "\n" + sets.filter { it.exercise == name }
                    .joinToString("\n") { "  ${kg(it.weightKg)} × ${it.reps}" }
            }.ifEmpty { "No sets" }
            AlertDialog.Builder(requireContext(), R.style.ForgeDialogTheme)
                .setTitle(w.name)
                .setMessage(text)
                .setPositiveButton("Close", null)
                .setNeutralButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        dao.deleteSetsFor(w.id)
                        dao.deleteWorkout(w.id)
                    }
                }
                .show()
        }
    }

    private fun showLicence() {
        val text = try {
            requireContext().assets.open("licenses/exercises.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "Licence text missing"
        }
        AlertDialog.Builder(requireContext(), R.style.ForgeDialogTheme)
            .setTitle("Exercise list")
            .setMessage(text)
            .setPositiveButton("Close", null)
            .show()
    }

    // --- Helpers ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun plain(weight: Double): String =
        if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()

    private fun kg(weight: Double): String =
        if (weight >= 1000) String.format("%,d kg", weight.toInt()) else "${plain(Math.round(weight * 10) / 10.0)} kg"

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

    private fun input(hintText: String, type: Int) = EditText(requireContext()).apply {
        hint = hintText
        inputType = type
        setTextColor(Color.parseColor("#F5F5F5"))
        setHintTextColor(Color.parseColor("#9E9E9E"))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isSingleLine = true
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun subText(text: String) = TextView(requireContext()).apply {
        this.text = text
        setTextColor(Color.parseColor("#9E9E9E"))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun twoColumnRow(left: String, right: String, bold: Boolean = false) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(TextView(context).apply {
            text = left
            setTextColor(Color.parseColor("#F5F5F5"))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_body))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = right
            setTextColor(Color.parseColor("#C4C4C4"))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.t_small))
            typeface = Typeface.MONOSPACE
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(clock)
        setsObservedFor = -1L
        _binding = null
    }
}
