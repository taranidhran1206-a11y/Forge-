package com.forge.app.ui.progress

import androidx.lifecycle.*
import com.forge.app.data.ForgeRepository
import com.forge.app.data.dao.CategoryTime
import com.forge.app.data.dao.XpBySource
import com.forge.app.data.model.DailyStats
import com.forge.app.data.model.RankSystem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProgressViewModel(private val repository: ForgeRepository) : ViewModel() {

    private val today = repository.getTodayDate()

    val totalXp: LiveData<Int> = repository.getTotalXp().asLiveData()

    val currentRank: LiveData<RankSystem.Rank> = totalXp.map { xp ->
        RankSystem.getRank(xp)
    }

    val rankProgress: LiveData<Float> = totalXp.map { xp ->
        RankSystem.getProgress(xp)
    }

    val xpToNextRank: LiveData<Int> = totalXp.map { xp ->
        RankSystem.getXpToNext(xp)
    }

    val globalRankPercent: LiveData<Int> = totalXp.map { xp ->
        RankSystem.getGlobalRankPercent(xp)
    }

    private val _weeklyStats = MutableLiveData<List<DailyStats>>(emptyList())
    val weeklyStats: LiveData<List<DailyStats>> = _weeklyStats

    val xpBySource: LiveData<List<XpBySource>> = repository.getXpBySource().asLiveData()

    val todayTimeByCategory: LiveData<List<CategoryTime>> =
        repository.getTotalMinutesByCategory(today).asLiveData()

    // Level up animation trigger
    private val _levelUpEvent = MutableLiveData<RankSystem.Rank?>(null)
    val levelUpEvent: LiveData<RankSystem.Rank?> = _levelUpEvent

    private var previousRank: RankSystem.Rank? = null

    init {
        loadWeeklyStats()
        // Monitor rank changes for level-up animation
        totalXp.observeForever { xp ->
            val newRank = RankSystem.getRank(xp)
            if (previousRank != null && previousRank != newRank && newRank.ordinal > (previousRank?.ordinal ?: 0)) {
                _levelUpEvent.value = newRank
            }
            previousRank = newRank
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val endDate = sdf.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            val startDate = sdf.format(cal.time)

            val stats = repository.getStatsForWeek(startDate, endDate)

            // Fill in missing days with empty stats
            val filledStats = mutableListOf<DailyStats>()
            cal.add(Calendar.DAY_OF_YEAR, -1) // reset
            val calStart = Calendar.getInstance()
            calStart.add(Calendar.DAY_OF_YEAR, -6)

            for (i in 0..6) {
                val date = sdf.format(calStart.time)
                val existing = stats.find { it.date == date }
                filledStats.add(existing ?: DailyStats(date = date))
                calStart.add(Calendar.DAY_OF_YEAR, 1)
            }

            _weeklyStats.value = filledStats
        }
    }

    fun clearLevelUp() {
        _levelUpEvent.value = null
    }

    fun refreshStats() {
        loadWeeklyStats()
    }
}
