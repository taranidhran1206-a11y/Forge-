package com.forge.app.focus

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings and today's numbers for the distraction watch: which apps count as
 * distracting, when to nudge, and how long was spent in each one today.
 */
object Distraction {

    private const val PREFS = "forge_distraction"

    /** Popular time sinks. Games and apps the store labels social or video are added on top. */
    val DEFAULT_PACKAGES = setOf(
        "com.instagram.android", "com.instagram.barcelona",
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.google.android.youtube", "app.revanced.android.youtube",
        "com.facebook.katana", "com.facebook.lite",
        "com.twitter.android", "com.reddit.frontpage",
        "com.snapchat.android", "com.pinterest",
        "com.netflix.mediaclient", "com.disney.disneyplus", "in.startv.hotstar",
        "com.amazon.avod.thirdpartyclient", "tv.twitch.android.app",
        "com.discord", "com.tumblr", "com.vkontakte.android",
        "com.mobile.legends", "com.tencent.ig", "com.dts.freefireth",
        "com.supercell.clashofclans", "com.supercell.brawlstars", "com.roblox.client",
        "com.kiloo.subwaysurf", "com.king.candycrushsaga"
    )

    data class Today(
        val secondsByApp: Map<String, Int>,
        val nudges: Int
    ) {
        val totalSeconds: Int get() = secondsByApp.values.sum()
    }

    /** Pushed by the service so an open Focus screen can redraw. */
    val live = MutableLiveData<Long>(0L)

    /** Label of the app in front right now, when the watch can see it. */
    @Volatile
    var currentAppLabel: String? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // --- Settings ---

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }

    /** Minutes in a distracting app before the first nudge, outside a focus session. */
    fun thresholdMinutes(context: Context): Int = prefs(context).getInt("threshold_min", 3)

    fun setThresholdMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt("threshold_min", minutes).apply()
    }

    /** Test builds may lower the threshold to seconds. */
    fun thresholdSecondsOverride(context: Context): Int = prefs(context).getInt("threshold_sec_override", 0)

    fun setThresholdSecondsOverride(context: Context, seconds: Int) {
        prefs(context).edit().putInt("threshold_sec_override", seconds).apply()
    }

    fun snoozedUntil(context: Context): Long = prefs(context).getLong("snoozed_until", 0L)

    fun snooze(context: Context, minutes: Int) {
        prefs(context).edit().putLong("snoozed_until", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    /** null until the user picks their own list. */
    fun chosenApps(context: Context): Set<String>? {
        val p = prefs(context)
        if (!p.getBoolean("customized", false)) return null
        return p.getStringSet("apps", emptySet())?.toSet() ?: emptySet()
    }

    fun setChosenApps(context: Context, apps: Set<String>) {
        prefs(context).edit().putBoolean("customized", true).putStringSet("apps", apps).apply()
    }

    fun isDistracting(context: Context, pkg: String): Boolean {
        if (pkg == context.packageName) return false
        chosenApps(context)?.let { return pkg in it }
        if (pkg in DEFAULT_PACKAGES) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                if (info.category == ApplicationInfo.CATEGORY_GAME ||
                    info.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    info.category == ApplicationInfo.CATEGORY_VIDEO
                ) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    /** Apps with a launcher icon, for the picker, sorted by name. */
    fun launcherApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { it to label(context, it) }
            .sortedBy { it.second.lowercase() }
    }

    // --- Permissions ---

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // --- Today's numbers ---

    @Synchronized
    fun today(context: Context): Today {
        val p = prefs(context)
        if (p.getString("date", "") != todayKey()) return Today(emptyMap(), 0)
        val map = mutableMapOf<String, Int>()
        try {
            val json = JSONObject(p.getString("seconds", "{}") ?: "{}")
            json.keys().forEach { key -> map[key] = json.optInt(key, 0) }
        } catch (_: Exception) {
        }
        return Today(map, p.getInt("nudges", 0))
    }

    @Synchronized
    fun add(context: Context, seconds: Map<String, Int>, nudges: Int = 0) {
        if (seconds.isEmpty() && nudges == 0) return
        val day = today(context)
        val merged = day.secondsByApp.toMutableMap()
        seconds.forEach { (pkg, s) -> merged[pkg] = (merged[pkg] ?: 0) + s }
        val json = JSONObject()
        merged.forEach { (pkg, s) -> json.put(pkg, s) }
        prefs(context).edit()
            .putString("date", todayKey())
            .putString("seconds", json.toString())
            .putInt("nudges", day.nudges + nudges)
            .apply()
    }

    fun minutesText(seconds: Int): String {
        val m = seconds / 60
        return when {
            m >= 60 -> "${m / 60}h ${m % 60}m"
            m > 0 -> "${m}m"
            else -> "${seconds}s"
        }
    }
}
