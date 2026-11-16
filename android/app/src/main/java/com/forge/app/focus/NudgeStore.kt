package com.forge.app.focus

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Today's nudge tracker numbers. Kept in SharedPreferences so the service can
 * write them without a database migration. Resets when the date changes.
 */
object NudgeStore {

    private const val PREFS = "forge_nudge_tracker"
    private const val MAX_LOG = 8

    data class Day(
        val sessions: Int,
        val focusSeconds: Int,
        val awaySeconds: Int,
        val nudges: Int,
        val drifts: Int,
        val log: List<String>
    )

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun today(context: Context): Day {
        val p = prefs(context)
        if (p.getString("date", "") != todayKey()) {
            return Day(0, 0, 0, 0, 0, emptyList())
        }
        val log = mutableListOf<String>()
        try {
            val arr = JSONArray(p.getString("log", "[]"))
            for (i in 0 until arr.length()) log.add(arr.getString(i))
        } catch (_: Exception) {
        }
        return Day(
            sessions = p.getInt("sessions", 0),
            focusSeconds = p.getInt("focusSeconds", 0),
            awaySeconds = p.getInt("awaySeconds", 0),
            nudges = p.getInt("nudges", 0),
            drifts = p.getInt("drifts", 0),
            log = log
        )
    }

    @Synchronized
    fun add(
        context: Context,
        sessions: Int = 0,
        focusSeconds: Int = 0,
        awaySeconds: Int = 0,
        nudges: Int = 0,
        drifts: Int = 0,
        logLine: String? = null
    ) {
        val day = today(context)
        val log = day.log.toMutableList()
        if (logLine != null) {
            val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
            log.add(0, "$time  $logLine")
            while (log.size > MAX_LOG) log.removeAt(log.size - 1)
        }
        val arr = JSONArray()
        log.forEach { arr.put(it) }
        prefs(context).edit()
            .putString("date", todayKey())
            .putInt("sessions", day.sessions + sessions)
            .putInt("focusSeconds", day.focusSeconds + focusSeconds)
            .putInt("awaySeconds", day.awaySeconds + awaySeconds)
            .putInt("nudges", day.nudges + nudges)
            .putInt("drifts", day.drifts + drifts)
            .putString("log", arr.toString())
            .apply()
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m >= 60 -> "${m / 60}h ${m % 60}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
