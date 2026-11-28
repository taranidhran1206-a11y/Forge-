package com.forge.app.gym

import android.content.Context
import org.json.JSONArray

/**
 * The exercise list shipped in assets/exercises.json (see assets/licenses/exercises.txt):
 * Free Exercise DB entries with photos and instructions, plus ExerciseDB names without them.
 */
object GymCatalogue {

    data class Exercise(
        val name: String,
        /** Muscle group: Chest, Back, Quads, … (see [Muscles.GROUPS]) */
        val group: String,
        val primary: List<String>,
        val secondary: List<String>,
        val equipment: String,
        val level: String,
        val category: String,
        /** Folder name in Free Exercise DB for the start/end photos; empty when there are none. */
        val imageId: String,
        val instructions: List<String>
    ) {
        /** Old name for the muscle group, kept so logged sets keep their body part. */
        val bodyPart: String get() = group
        val target: String get() = primary.firstOrNull() ?: group
        val hasPhotos: Boolean get() = imageId.isNotEmpty()
        val subtitle: String
            get() = listOf(group, equipment).filter { it.isNotBlank() }.joinToString(" · ")
    }

    @Volatile
    private var cache: List<Exercise>? = null
    @Volatile
    private var byName: Map<String, Exercise> = emptyMap()

    fun all(context: Context): List<Exercise> {
        cache?.let { return it }
        val list = try {
            val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Exercise(
                    name = o.optString("n"),
                    group = o.optString("g", "Other"),
                    primary = strings(o.optJSONArray("m")),
                    secondary = strings(o.optJSONArray("s")),
                    equipment = o.optString("e"),
                    level = o.optString("l"),
                    category = o.optString("c"),
                    imageId = o.optString("i"),
                    instructions = strings(o.optJSONArray("x"))
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        byName = list.associateBy { it.name.lowercase() }
        cache = list
        return list
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

    fun find(context: Context, name: String): Exercise? {
        all(context)
        return byName[name.lowercase()]
    }

    /** Matches every word of the query against name, muscles and equipment; photo entries first. */
    fun search(context: Context, query: String, group: String? = null, limit: Int = 80): List<Exercise> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        val phrase = words.joinToString(" ")
        return all(context).asSequence()
            .filter { group == null || it.group == group }
            .filter { e ->
                if (words.isEmpty()) true else {
                    val hay = "${e.name} ${e.group} ${e.primary.joinToString(" ")} ${e.equipment}".lowercase()
                    words.all { hay.contains(it) }
                }
            }
            .sortedWith(compareBy<Exercise>(
                { rank(it.name.lowercase(), phrase, words) },
                { if (it.hasPhotos) 0 else 1 },
                { it.name.length }
            ))
            .take(limit)
            .toList()
    }

    /** Lower is better: the exact name, then names starting with what was typed, then names holding every word. */
    private fun rank(name: String, phrase: String, words: List<String>): Int = when {
        words.isEmpty() -> 0
        name == phrase -> 0
        name.startsWith(phrase) -> 1
        name.contains(phrase) -> 2
        words.all { w -> name.split(' ', '-', '(', ')').any { it.startsWith(w) } } -> 3
        name.startsWith(words.first()) -> 4
        else -> 5
    }

    /** Other ways to train the same muscle: same equipment first, then easier/equal level. */
    fun variations(context: Context, of: Exercise, limit: Int = 12): List<Exercise> {
        val main = of.primary.firstOrNull()
        return all(context).asSequence()
            .filter { it.name != of.name && it.category != "stretching" }
            .filter { if (main != null) it.primary.firstOrNull() == main || it.group == of.group else it.group == of.group }
            .sortedWith(compareBy<Exercise>(
                { if (main != null && it.primary.firstOrNull() == main) 0 else 1 },
                { if (it.equipment == of.equipment) 0 else 1 },
                { if (it.hasPhotos) 0 else 1 },
                { it.name.length }
            ))
            .take(limit)
            .toList()
    }

    fun titleCase(name: String): String =
        name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
