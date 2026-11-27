package com.forge.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.forge.app.SplashActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Wake-up alarms you switch off with push-ups, squats or sums. Kept in prefs, scheduled with setAlarmClock. */
object AlarmStore {

    private const val PREFS = "forge_alarms"
    const val EXTRA_ID = "alarm_id"
    const val EXTRA_PREVIEW = "alarm_preview"
    private const val REQUEST_BASE = 7400

    data class Alarm(
        val id: Int,
        val hour: Int,
        val minute: Int,
        /** Calendar.DAY_OF_WEEK values; empty means ring once. */
        val days: Set<Int> = emptySet(),
        val enabled: Boolean = true,
        /** "pushups", "squats" or "math". */
        val challenge: String = "pushups",
        val reps: Int = 15,
        val label: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("id", id).put("hour", hour).put("minute", minute)
            .put("days", JSONArray(days.sorted())).put("enabled", enabled)
            .put("challenge", challenge).put("reps", reps).put("label", label)

        val challengeText: String
            get() = when (challenge) {
                "math" -> "3 sums"
                "squats" -> "$reps squats"
                else -> "$reps push-ups"
            }

        companion object {
            fun fromJson(o: JSONObject): Alarm {
                val d = o.optJSONArray("days") ?: JSONArray()
                return Alarm(
                    o.getInt("id"), o.getInt("hour"), o.getInt("minute"),
                    (0 until d.length()).map { d.getInt(it) }.toSet(),
                    o.optBoolean("enabled", true), o.optString("challenge", "pushups"),
                    o.optInt("reps", 15), o.optString("label", "")
                )
            }
        }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): List<Alarm> = try {
        val a = JSONArray(prefs(c).getString("alarms", "[]"))
        (0 until a.length()).map { Alarm.fromJson(a.getJSONObject(it)) }.sortedWith(compareBy({ it.hour }, { it.minute }))
    } catch (_: Exception) { emptyList() }

    fun get(c: Context, id: Int) = all(c).firstOrNull { it.id == id }

    private fun write(c: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs(c).edit().putString("alarms", a.toString()).apply()
    }

    fun newId(c: Context) = (all(c).maxOfOrNull { it.id } ?: 0) + 1

    fun save(c: Context, alarm: Alarm) {
        write(c, all(c).filter { it.id != alarm.id } + alarm)
        if (alarm.enabled) schedule(c, alarm) else cancel(c, alarm.id)
    }

    fun delete(c: Context, id: Int) {
        cancel(c, id)
        write(c, all(c).filter { it.id != id })
    }

    /** Next time this alarm rings, or null when it's off. */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long? {
        if (!alarm.enabled) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (alarm.days.isNotEmpty()) {
            var guard = 0
            while (cal.get(Calendar.DAY_OF_WEEK) !in alarm.days && guard++ < 8) cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** The soonest alarm that's on, with when it rings. */
    fun next(c: Context): Pair<Alarm, Long>? =
        all(c).mapNotNull { a -> nextTrigger(a)?.let { a to it } }.minByOrNull { it.second }

    private fun ringIntent(c: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        c, REQUEST_BASE + id,
        Intent(c, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context, alarm: Alarm) {
        val at = nextTrigger(alarm) ?: return cancel(c, alarm.id)
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            c, REQUEST_BASE + 99, Intent(c, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), ringIntent(c, alarm.id))
        } catch (_: SecurityException) {
            // Exact alarms switched off for Forge: still ring, a little less precisely.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ringIntent(c, alarm.id))
        }
    }

    fun cancel(c: Context, id: Int) {
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(ringIntent(c, id))
    }

    /** After a reboot, an update or a clock change. */
    fun rescheduleAll(c: Context) {
        all(c).forEach { if (it.enabled) schedule(c, it) else cancel(c, it.id) }
    }

    /** A one-off alarm turns itself off once it has rung; a repeating one books the next day. */
    fun afterRing(c: Context, id: Int) {
        val a = get(c, id) ?: return
        if (a.days.isEmpty()) save(c, a.copy(enabled = false)) else schedule(c, a)
    }

    fun canExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
}
