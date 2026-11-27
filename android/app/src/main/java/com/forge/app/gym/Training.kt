package com.forge.app.gym

import kotlin.math.roundToInt

/** Muscle groups, recovery and strength maths for the gym log. */
object Muscles {

    /** The groups shown in the recovery map, in body order. */
    val GROUPS = listOf(
        "Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms", "Traps",
        "Abs", "Lower back", "Glutes", "Quads", "Hamstrings", "Calves", "Hips"
    )

    /** Free Exercise DB and ExerciseDB muscle names → our groups (for secondary muscles too). */
    private val MAP = mapOf(
        "abdominals" to "Abs", "abs" to "Abs", "quadriceps" to "Quads", "quads" to "Quads",
        "shoulders" to "Shoulders", "delts" to "Shoulders", "chest" to "Chest", "pectorals" to "Chest",
        "serratus anterior" to "Chest", "hamstrings" to "Hamstrings", "triceps" to "Triceps",
        "biceps" to "Biceps", "lats" to "Back", "middle back" to "Back", "upper back" to "Back",
        "calves" to "Calves", "lower back" to "Lower back", "spine" to "Lower back",
        "forearms" to "Forearms", "glutes" to "Glutes", "traps" to "Traps",
        "adductors" to "Hips", "abductors" to "Hips", "neck" to "Traps", "levator scapulae" to "Traps"
    )

    fun group(muscle: String): String? = MAP[muscle.lowercase()]

    enum class State(val label: String) { FRESH("Ready"), RECOVERING("Recovering"), WORKED("Worked hard"), IDLE("Not trained this week") }

    data class Status(val group: String, val state: State, val hardSets: Double, val hoursSince: Double?)

    /**
     * Recovery from the last 7 days: primary muscles count a full set, secondary half.
     * A group is "worked hard" for ~48 h after 6+ sets, "recovering" for 72 h after any work.
     */
    fun recovery(sets: List<SetWithDate>, lookup: (String) -> GymCatalogue.Exercise?, now: Long = System.currentTimeMillis()): List<Status> {
        val load = HashMap<String, Double>()
        val last = HashMap<String, Long>()
        val recent = HashMap<String, Double>() // sets in the last 48 h
        for (s in sets) {
            if (s.setType == "warmup") continue
            val ex = lookup(s.exercise)
            val primary = ex?.primary?.mapNotNull { group(it) }?.ifEmpty { null }
                ?: listOfNotNull(ex?.group ?: s.bodyPart.ifBlank { null })
            val secondary = ex?.secondary?.mapNotNull { group(it) } ?: emptyList()
            fun add(g: String, w: Double) {
                load[g] = (load[g] ?: 0.0) + w
                if (s.startedAt > (last[g] ?: 0L)) last[g] = s.startedAt
                if (now - s.startedAt < 48 * 3_600_000L) recent[g] = (recent[g] ?: 0.0) + w
            }
            primary.distinct().forEach { add(it, 1.0) }
            secondary.distinct().filter { it !in primary }.forEach { add(it, 0.5) }
        }
        return GROUPS.map { g ->
            val since = last[g]?.let { (now - it) / 3_600_000.0 }
            val state = when {
                since == null -> State.IDLE
                since < 48 && (recent[g] ?: 0.0) >= 6 -> State.WORKED
                since < 72 -> State.RECOVERING
                else -> State.FRESH
            }
            Status(g, state, load[g] ?: 0.0, since)
        }
    }
}

object Strength {

    /** Estimated one-rep max (Epley); a single is its own max. */
    fun e1rm(weightKg: Double, reps: Int): Double =
        if (reps <= 0 || weightKg <= 0) 0.0 else if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)

    fun plain(weight: Double): String {
        val r = (weight * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }

    fun kg(weight: Double): String =
        if (weight >= 1000) String.format("%,d kg", weight.toInt()) else "${plain(weight)} kg"

    /** The smallest sensible jump for this kind of exercise. */
    fun step(equipment: String): Double = when {
        equipment.contains("dumbbell") -> 1.0
        equipment.contains("barbell") || equipment.contains("smith") -> 2.5
        equipment.contains("body") -> 0.0
        else -> 2.5
    }

    /**
     * Double progression: once every working set last time reached the top of the rep
     * range, add one step of weight; otherwise aim for one more rep at the same weight.
     */
    fun suggestion(last: List<GymSet>, repsLow: Int, repsHigh: Int, equipment: String): Pair<Double, Int>? {
        val work = last.filter { it.setType != "warmup" && it.reps > 0 }
        if (work.isEmpty()) return null
        val top = work.maxByOrNull { it.weightKg } ?: return null
        val atTop = work.filter { it.weightKg == top.weightKg }
        val step = step(equipment)
        return if (step > 0 && atTop.all { it.reps >= repsHigh }) {
            (top.weightKg + step) to repsLow
        } else {
            top.weightKg to (atTop.minOf { it.reps } + 1).coerceAtMost(repsHigh)
        }
    }

    data class Pr(val exercise: String, val kind: String, val value: String)

    /** New records in [done] compared with everything before it. */
    fun prs(done: List<GymSet>, history: Map<String, List<SetWithDate>>): List<Pr> {
        val out = mutableListOf<Pr>()
        done.filter { it.setType != "warmup" }.groupBy { it.exercise }.forEach { (name, sets) ->
            val before = history[name].orEmpty().filter { it.workoutId != sets.first().workoutId }
            if (before.isEmpty()) return@forEach
            val bestW = sets.maxOf { it.weightKg }
            val prevW = before.maxOf { it.weightKg }
            if (bestW > prevW && bestW > 0) out.add(Pr(name, "Heaviest", kg(bestW)))
            val bestE = sets.maxOf { e1rm(it.weightKg, it.reps) }
            val prevE = before.maxOf { e1rm(it.weightKg, it.reps) }
            if (bestE > prevE + 0.05 && bestW <= prevW && bestE > 0) out.add(Pr(name, "Best e1RM", kg(bestE)))
            val vol = sets.sumOf { it.weightKg * it.reps }
            val prevVol = before.groupBy { it.workoutId }.values.maxOf { s -> s.sumOf { it.weightKg * it.reps } }
            if (vol > prevVol && vol > 0 && bestE <= prevE + 0.05 && bestW <= prevW) out.add(Pr(name, "Most volume", kg(vol)))
        }
        return out
    }
}

/** Starter routines; the full-body one uses the machines a beginner learns first. */
object StarterRoutines {
    data class Item(val exercise: String, val sets: Int, val low: Int, val high: Int, val rest: Int = 90)

    val ALL: List<Pair<String, List<Item>>> = listOf(
        "Full body (machines)" to listOf(
            Item("Leg Press", 3, 10, 12, 120),
            Item("Leg Extensions", 3, 10, 15),
            Item("Smith Machine Incline Bench Press", 3, 8, 12, 120),
            Item("Wide-Grip Lat Pulldown", 3, 8, 12),
            Item("Seated Cable Rows", 3, 10, 12),
            Item("Seated Dumbbell Press", 3, 8, 12),
            Item("Dumbbell Bicep Curl", 2, 10, 15, 60)
        ),
        "Push" to listOf(
            Item("Barbell Bench Press - Medium Grip", 4, 6, 10, 150),
            Item("Incline Dumbbell Press", 3, 8, 12, 120),
            Item("Seated Dumbbell Press", 3, 8, 12),
            Item("Side Lateral Raise", 3, 12, 15, 60),
            Item("Triceps Pushdown", 3, 10, 15, 60)
        ),
        "Pull" to listOf(
            Item("Wide-Grip Lat Pulldown", 4, 8, 12),
            Item("Seated Cable Rows", 3, 8, 12),
            Item("Face Pull", 3, 12, 15, 60),
            Item("Dumbbell Bicep Curl", 3, 10, 12, 60),
            Item("Hammer Curls", 2, 10, 12, 60)
        ),
        "Legs" to listOf(
            Item("Barbell Squat", 4, 6, 10, 150),
            Item("Romanian Deadlift", 3, 8, 12, 120),
            Item("Leg Press", 3, 10, 12, 120),
            Item("Lying Leg Curls", 3, 10, 15),
            Item("Standing Calf Raises", 3, 12, 20, 60)
        )
    )
}
