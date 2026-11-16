package com.forge.app.ui.today

import android.os.CountDownTimer
import androidx.lifecycle.*
import com.forge.app.data.ForgeRepository
import com.forge.app.data.model.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TodayViewModel(private val repository: ForgeRepository) : ViewModel() {

    private val today = repository.getTodayDate()

    val todayLabors: LiveData<List<Labor>> = repository.getLaborsForDate(today).asLiveData()

    val totalXp: LiveData<Int> = repository.getTotalXp().asLiveData()

    val currentRank: LiveData<RankSystem.Rank> = totalXp.map { xp ->
        RankSystem.getRank(xp)
    }

    val rankProgress: LiveData<Float> = totalXp.map { xp ->
        RankSystem.getProgress(xp)
    }

    val todayXp: LiveData<Int> = repository.getXpForDate(today).asLiveData()

    private val _streakCount = MutableLiveData(0)
    val streakCount: LiveData<Int> = _streakCount

    private val _activeTimeTrack = MutableLiveData<TimeTrack?>(null)
    val activeTimeTrack: LiveData<TimeTrack?> = _activeTimeTrack

    val todayTimeByCategory: LiveData<List<com.forge.app.data.dao.CategoryTime>> =
        repository.getTotalMinutesByCategory(today).asLiveData()

    // Day dots - which days of current week have activity
    private val _weekDayActivity = MutableLiveData<List<Boolean>>(listOf(false, false, false, false, false, false, false))
    val weekDayActivity: LiveData<List<Boolean>> = _weekDayActivity

    // XP toast event
    private val _xpToastEvent = MutableLiveData<XpEvent?>(null)
    val xpToastEvent: LiveData<XpEvent?> = _xpToastEvent

    // Rank-up event
    private val _rankUpEvent = MutableLiveData<RankSystem.Rank?>(null)
    val rankUpEvent: LiveData<RankSystem.Rank?> = _rankUpEvent

    // Elapsed time for active time track (updated every second)
    private val _trackElapsedSeconds = MutableLiveData(0L)
    val trackElapsedSeconds: LiveData<Long> = _trackElapsedSeconds

    private var trackTimer: CountDownTimer? = null

    init {
        loadStreak()
        loadActiveTimeTrack()
        loadWeekActivity()
    }

    private fun loadStreak() {
        viewModelScope.launch {
            _streakCount.value = repository.calculateStreak()
        }
    }

    private fun loadActiveTimeTrack() {
        viewModelScope.launch {
            val active = repository.getActiveTrack()
            _activeTimeTrack.value = active
            if (active != null) {
                startTrackTimer(active.startTime)
            }
        }
    }

    /** Activity for the current week, Monday first. */
    private fun loadWeekActivity() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val activity = mutableListOf<Boolean>()
            for (i in 0..6) {
                val stats = repository.getStats(sdf.format(cal.time))
                activity.add(stats != null && stats.laborsCompleted > 0)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            _weekDayActivity.value = activity
        }
    }

    fun toggleLabor(labor: Labor) {
        viewModelScope.launch {
            if (!labor.done) {
                val oldXp = repository.getTotalXpSync()
                val oldRank = RankSystem.getRank(oldXp)

                repository.completeLabor(labor)
                _xpToastEvent.value = XpEvent(
                    amount = labor.xpReward,
                    source = "labor",
                    description = labor.name,
                    timestamp = System.currentTimeMillis(),
                    date = today
                )

                // Check for rank up
                val newXp = repository.getTotalXpSync()
                val newRank = RankSystem.getRank(newXp)
                if (newRank != oldRank) {
                    _rankUpEvent.value = newRank
                }

                _streakCount.value = repository.calculateStreak()
                loadWeekActivity()
            } else {
                val updated = labor.copy(done = false)
                repository.updateLabor(updated)
            }
        }
    }

    fun clearXpToast() {
        _xpToastEvent.value = null
    }

    fun clearRankUp() {
        _rankUpEvent.value = null
    }

    fun addLabor(name: String, description: String) {
        viewModelScope.launch {
            val labor = Labor(
                name = name,
                emoji = "",
                description = description,
                time = "12:00",
                timeOfDay = "AFTERNOON",
                xpReward = 10,
                date = today,
                sortOrder = (todayLabors.value?.size ?: 0) + 1
            )
            repository.insertLabor(labor)
        }
    }

    fun editLabor(labor: Labor, newName: String, newDescription: String) {
        viewModelScope.launch {
            val updated = labor.copy(name = newName, description = newDescription)
            repository.updateLabor(updated)
        }
    }

    fun deleteLabor(labor: Labor) {
        viewModelScope.launch {
            repository.deleteLabor(labor)
        }
    }

    // --- Time tracking (Rize) ---

    fun startTimeTrack(category: String) {
        viewModelScope.launch {
            try {
                // Stop any active track first
                val active = repository.getActiveTrack()
                if (active != null) {
                    val now = System.currentTimeMillis()
                    val durationMin = ((now - active.startTime) / 60000).toInt()
                    repository.stopTrack(active.id, now, durationMin)
                }

                val startTime = System.currentTimeMillis()
                val track = TimeTrack(
                    category = category,
                    startTime = startTime,
                    endTime = null,
                    durationMinutes = 0,
                    date = today
                )
                val id = repository.insertTimeTrack(track)
                val newTrack = track.copy(id = id)
                _activeTimeTrack.value = newTrack
                startTrackTimer(newTrack.startTime)
            } catch (e: Exception) {
                // If DB insert fails, reset state
                _activeTimeTrack.value = null
            }
        }
    }

    fun stopTimeTrack() {
        viewModelScope.launch {
            val active = _activeTimeTrack.value ?: return@launch
            // Reset UI state immediately
            _activeTimeTrack.value = null
            trackTimer?.cancel()
            trackTimer = null
            _trackElapsedSeconds.value = 0L

            try {
                val now = System.currentTimeMillis()
                val durationMin = ((now - active.startTime) / 60000).toInt()
                repository.stopTrack(active.id, now, durationMin)

                // Award XP for time tracked
                if (durationMin >= 1) {
                    val xpAmount = (durationMin / 5).coerceAtLeast(1) * 2
                    repository.addXp(xpAmount, "time_track", "${active.category} - ${durationMin}min")
                    _xpToastEvent.value = XpEvent(
                        amount = xpAmount,
                        source = "time_track",
                        description = "${active.category} - ${durationMin}min",
                        timestamp = System.currentTimeMillis(),
                        date = today
                    )
                }
            } catch (_: Exception) {
                // DB error — UI already reset above
            }
        }
    }

    private fun startTrackTimer(startTime: Long) {
        trackTimer?.cancel()
        trackTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _trackElapsedSeconds.value = (System.currentTimeMillis() - startTime) / 1000
            }
            override fun onFinish() {}
        }.start()
    }

    override fun onCleared() {
        super.onCleared()
        trackTimer?.cancel()
    }
}
