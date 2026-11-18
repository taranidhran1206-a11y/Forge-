package com.forge.app.gym

import android.content.Context
import org.json.JSONArray

/** The exercise list shipped in assets/exercises.json (see assets/licenses/exercises.txt). */
object GymCatalogue {

    data class Exercise(val name: String, val bodyPart: String, val equipment: String, val target: String) {
        val subtitle: String
            get() = listOf(target, equipment).filter { it.isNotBlank() }.joinToString(" · ")
    }

    @Volatile
    private var cache: List<Exercise>? = null

    fun all(context: Context): List<Exercise> {
        cache?.let { return it }
        val list = try {
            val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Exercise(titleCase(o.optString("n")), o.optString("b"), o.optString("e"), o.optString("t"))
            }
        } catch (_: Exception) {
            emptyList()
        }
        cache = list
        return list
    }

    /** Matches every word of the query against name, muscle, body part and equipment. */
    fun search(context: Context, query: String, limit: Int = 60): List<Exercise> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        val all = all(context)
        if (words.isEmpty()) return all.take(limit)
        return all.asSequence()
            .filter { e ->
                val hay = "${e.name} ${e.target} ${e.bodyPart} ${e.equipment}".lowercase()
                words.all { hay.contains(it) }
            }
            .sortedBy { e -> if (e.name.lowercase().startsWith(words.first())) 0 else 1 }
            .take(limit)
            .toList()
    }

    fun titleCase(name: String): String =
        name.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
}
