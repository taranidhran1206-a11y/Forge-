package com.forge.app.focus

import androidx.lifecycle.MutableLiveData

/**
 * In-process view of the focus session. FocusService owns the session and
 * publishes a snapshot every second; screens only observe it.
 */
object FocusState {

    data class Snapshot(
        val active: Boolean = false,
        val running: Boolean = false,
        val label: String = "",
        val totalSeconds: Int = 25 * 60,
        val remainingSeconds: Int = 25 * 60,
        val elapsedSeconds: Int = 0,
        val currentAwaySeconds: Int = 0
    )

    val live = MutableLiveData(Snapshot())

    /** XP awarded by a session that just finished; cleared by the screen that shows it. */
    val completedXp = MutableLiveData<Int?>(null)

    /** Set when another screen asks for a session; the Focus screen starts it and clears this. */
    @Volatile
    var pendingLabel: String? = null

    /** True while a Forge screen is visible. Set by MainActivity. */
    @Volatile
    var appInForeground: Boolean = false

    fun setIdleDuration(minutes: Int) {
        val current = live.value
        if (current != null && current.active) return
        live.value = Snapshot(totalSeconds = minutes * 60, remainingSeconds = minutes * 60)
    }
}
