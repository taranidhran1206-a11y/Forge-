package com.forge.app.ui.gym

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.Toast
import com.forge.app.R
import com.forge.app.gym.GymCatalogue
import com.forge.app.gym.GymDatabase
import com.forge.app.gym.Routine
import com.forge.app.gym.RoutineItem
import com.forge.app.ui.kit.Kit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Make or change a routine: its name and exercises with sets, rep range and rest. */
object RoutineEditor {

    private data class Line(var exercise: String, var bodyPart: String, var sets: Int, var low: Int, var high: Int, var rest: Int)

    fun open(c: Context, scope: CoroutineScope, routineId: Long?) {
        scope.launch {
            val dao = GymDatabase.get(c).gymDao()
            val routine = routineId?.let { withContext(Dispatchers.IO) { dao.routine(it) } }
            val lines = routineId?.let { id ->
                withContext(Dispatchers.IO) { dao.routineItems(id) }.map { Line(it.exercise, it.bodyPart, it.sets, it.repsLow, it.repsHigh, it.restSec) }
            }?.toMutableList() ?: mutableListOf()
            build(c, scope, routine, lines)
        }
    }

    private fun build(c: Context, scope: CoroutineScope, routine: Routine?, lines: MutableList<Line>) {
        var dialog: Dialog? = null
        val (root, page) = Kit.sheet(c, "Routine", if (routine == null) "New routine" else "Edit routine") { dialog?.dismiss() }
        val name = Kit.input(c, "Name, e.g. Full body A").apply { setText(routine?.name ?: "") }
        page.addView(name)
        page.addView(Kit.label(c, "Exercises", bottom = 8))
        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)

        fun render() {
            list.removeAllViews()
            if (lines.isEmpty()) list.addView(Kit.sub(c, "Add the exercises you do on this day.").apply { setPadding(0, 0, 0, Kit.dp(c, 12)) })
            lines.forEachIndexed { i, line ->
                val card = Kit.card(c, bottom = 8, vertical = false).apply { gravity = Gravity.CENTER_VERTICAL }
                val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
                col.addView(Kit.row(c, line.exercise))
                col.addView(Kit.sub(c, "${line.sets} × ${line.low}–${line.high} reps · rest ${line.rest}s"))
                card.addView(col, Kit.weight())
                if (i > 0) card.addView(Kit.mono(c, "↑", Kit.FG2).apply {
                    setPadding(Kit.dp(c, 10), Kit.dp(c, 6), Kit.dp(c, 10), Kit.dp(c, 6))
                    setOnClickListener { lines.add(i - 1, lines.removeAt(i)); render() }
                })
                card.setOnClickListener { editLine(c, line, onRemove = { lines.remove(line); render() }) { render() } }
                list.addView(card)
            }
        }
        render()

        page.addView(Kit.secondary(c, "Add exercise") {
            ExerciseUi.pick(c, "Add to routine") { ex ->
                lines.add(Line(ex.name, ex.group, 3, 8, 12, 90))
                render()
            }
        })
        page.addView(Kit.primary(c, "Save routine") {
            val title = name.text.toString().trim()
            if (title.isEmpty()) { Toast.makeText(c, "Give the routine a name", Toast.LENGTH_SHORT).show(); return@primary }
            if (lines.isEmpty()) { Toast.makeText(c, "Add at least one exercise", Toast.LENGTH_SHORT).show(); return@primary }
            scope.launch {
                withContext(Dispatchers.IO) {
                    val dao = GymDatabase.get(c).gymDao()
                    val id = if (routine == null) {
                        dao.insertRoutine(Routine(name = title, sortOrder = dao.routinesNow().size))
                    } else {
                        dao.updateRoutine(routine.copy(name = title, updatedAt = System.currentTimeMillis()))
                        dao.deleteRoutineItems(routine.id)
                        routine.id
                    }
                    lines.forEachIndexed { i, l ->
                        dao.insertRoutineItem(RoutineItem(routineId = id, exercise = l.exercise, bodyPart = l.bodyPart,
                            sets = l.sets, repsLow = l.low, repsHigh = l.high, restSec = l.rest, sortOrder = i))
                    }
                }
                dialog?.dismiss()
            }
        })
        if (routine != null) {
            page.addView(Kit.quiet(c, "Delete routine") {
                AlertDialog.Builder(c, R.style.ForgeDialogTheme)
                    .setTitle("Delete ${routine.name}?")
                    .setMessage("Past workouts stay in your history.")
                    .setPositiveButton("Delete") { _, _ ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val dao = GymDatabase.get(c).gymDao()
                                dao.deleteRoutineItems(routine.id)
                                dao.deleteRoutine(routine.id)
                            }
                            dialog?.dismiss()
                        }
                    }
                    .setNegativeButton("Keep", null)
                    .show()
            })
        }
        dialog = Kit.showFull(c, root)
    }

    private fun editLine(c: Context, line: Line, onRemove: () -> Unit, onDone: () -> Unit) {
        val sets = Kit.input(c, "Sets", InputType.TYPE_CLASS_NUMBER).apply { setText(line.sets.toString()) }
        val low = Kit.input(c, "Fewest reps", InputType.TYPE_CLASS_NUMBER).apply { setText(line.low.toString()) }
        val high = Kit.input(c, "Most reps", InputType.TYPE_CLASS_NUMBER).apply { setText(line.high.toString()) }
        val rest = Kit.input(c, "Rest (seconds)", InputType.TYPE_CLASS_NUMBER).apply { setText(line.rest.toString()) }
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Kit.dp(c, 20), Kit.dp(c, 8), Kit.dp(c, 20), 0)
            addView(Kit.sub(c, "Sets"))
            addView(sets)
            addView(Kit.sub(c, "Rep range"))
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(low, Kit.weight())
            r.addView(Kit.spacer(c, 1).apply { layoutParams = LinearLayout.LayoutParams(Kit.dp(c, 10), 1) })
            r.addView(high, Kit.weight())
            addView(r)
            addView(Kit.sub(c, "Rest between sets"))
            addView(rest)
        }
        AlertDialog.Builder(c, R.style.ForgeDialogTheme)
            .setTitle(line.exercise)
            .setView(box)
            .setPositiveButton("Done") { _, _ ->
                line.sets = (sets.text.toString().toIntOrNull() ?: line.sets).coerceIn(1, 12)
                line.low = (low.text.toString().toIntOrNull() ?: line.low).coerceIn(1, 100)
                line.high = (high.text.toString().toIntOrNull() ?: line.high).coerceIn(line.low, 100)
                line.rest = (rest.text.toString().toIntOrNull() ?: line.rest).coerceIn(0, 900)
                onDone()
            }
            .setNeutralButton("Remove") { _, _ -> onRemove() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Adds the starter routines the first time the gym log opens. */
    suspend fun seedStarters(c: Context) = withContext(Dispatchers.IO) {
        val prefs = c.getSharedPreferences("forge_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("gym_routines_seeded", false)) return@withContext
        val dao = GymDatabase.get(c).gymDao()
        if (dao.routinesNow().isEmpty()) {
            com.forge.app.gym.StarterRoutines.ALL.forEachIndexed { order, (title, items) ->
                val id = dao.insertRoutine(Routine(name = title, sortOrder = order))
                items.forEachIndexed { i, it ->
                    val group = GymCatalogue.find(c, it.exercise)?.group ?: ""
                    dao.insertRoutineItem(RoutineItem(routineId = id, exercise = it.exercise, bodyPart = group,
                        sets = it.sets, repsLow = it.low, repsHigh = it.high, restSec = it.rest, sortOrder = i))
                }
            }
        }
        prefs.edit().putBoolean("gym_routines_seeded", true).apply()
    }
}
