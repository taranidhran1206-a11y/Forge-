package com.forge.app.data

import com.forge.app.data.dao.*
import com.forge.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class ForgeRepository(private val database: ForgeDatabase) {

    // DAOs
    private val laborDao = database.laborDao()
    private val xpDao = database.xpDao()
    private val reflectionDao = database.reflectionDao()
    private val timeTrackDao = database.timeTrackDao()
    private val coachDao = database.coachDao()
    private val planDao = database.planDao()
    private val dailyStatsDao = database.dailyStatsDao()

    // ── Date helper ──────────────────────────────────────────────

    fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    // ── Labors ───────────────────────────────────────────────────

    fun getLaborsForDate(date: String): Flow<List<Labor>> = laborDao.getLaborsForDate(date)

    suspend fun insertLabor(labor: Labor): Long = laborDao.insert(labor)

    suspend fun updateLabor(labor: Labor) = laborDao.update(labor)

    suspend fun deleteLabor(labor: Labor) = laborDao.delete(labor)

    suspend fun toggleLaborDone(id: Long, done: Boolean) = laborDao.toggleDone(id, done)

    suspend fun getCompletedLaborCount(date: String): Int = laborDao.getCompletedCount(date)

    suspend fun getTotalLaborCount(date: String): Int = laborDao.getTotalCount(date)

    suspend fun completeLabor(labor: Labor) {
        laborDao.toggleDone(labor.id, true)
        addXp(labor.xpReward, "labor", "Completed: ${labor.name}")
        updateDailyLaborStats()
    }

    // ── XP ───────────────────────────────────────────────────────

    fun getTotalXp(): Flow<Int> = xpDao.getTotalXp()

    fun getTotalXpSync(): Int = xpDao.getTotalXpSync()

    fun getXpForDate(date: String): Flow<Int> = xpDao.getXpForDate(date)

    fun getRecentXpEvents(limit: Int = 20): Flow<List<XpEvent>> = xpDao.getRecentEvents(limit)

    fun getXpBySource(): Flow<List<XpBySource>> = xpDao.getXpBySource()

    suspend fun addXp(amount: Int, source: String, description: String) {
        val today = getTodayDate()
        xpDao.insert(
            XpEvent(
                amount = amount,
                source = source,
                description = description,
                date = today
            )
        )
        // Update daily stats totalXp
        val stats = dailyStatsDao.getStats(today) ?: DailyStats(date = today)
        dailyStatsDao.upsert(stats.copy(totalXp = stats.totalXp + amount))
    }

    // ── Reflections ──────────────────────────────────────────────

    fun getReflections(): Flow<List<Reflection>> = reflectionDao.getReflections()

    suspend fun getReflectionForDate(date: String): Reflection? = reflectionDao.getReflectionForDate(date)

    suspend fun insertReflection(reflection: Reflection): Long {
        return reflectionDao.insert(reflection)
    }

    fun getReflectionCount(): Flow<Int> = reflectionDao.getCount()

    // ── Time Tracking ────────────────────────────────────────────

    fun getTracksForDate(date: String): Flow<List<TimeTrack>> = timeTrackDao.getTracksForDate(date)

    suspend fun getActiveTrack(): TimeTrack? = timeTrackDao.getActiveTrack()

    suspend fun insertTimeTrack(track: TimeTrack): Long = timeTrackDao.insert(track)

    suspend fun startTracking(category: String): Long {
        // Stop any active track first
        val active = timeTrackDao.getActiveTrack()
        if (active != null) {
            stopTracking(active.id)
        }
        val now = System.currentTimeMillis()
        return timeTrackDao.insert(
            TimeTrack(
                category = category,
                startTime = now,
                date = getTodayDate()
            )
        )
    }

    suspend fun stopTrack(trackId: Long, endTime: Long, durationMinutes: Int) {
        timeTrackDao.stopTrack(trackId, endTime, durationMinutes)
    }

    suspend fun stopTracking(trackId: Long) {
        val now = System.currentTimeMillis()
        val track = timeTrackDao.getActiveTrack() ?: return
        if (track.id != trackId) return
        val durationMs = now - track.startTime
        val durationMinutes = (durationMs / 60000).toInt()
        timeTrackDao.stopTrack(trackId, now, durationMinutes)
    }

    suspend fun getTotalMinutesForDate(date: String): Int = timeTrackDao.getTotalMinutesForDate(date)

    fun getTotalMinutesByCategory(date: String): Flow<List<CategoryTime>> =
        timeTrackDao.getTotalMinutesByCategory(date)

    // ── Coach ────────────────────────────────────────────────────

    fun getCoachMessages(): Flow<List<CoachMessage>> = coachDao.getMessages()
    fun getMessages(): Flow<List<CoachMessage>> = coachDao.getMessages()

    suspend fun insertCoachMessage(message: CoachMessage) = coachDao.insert(message)

    suspend fun clearCoachMessages() = coachDao.clearAll()

    // ── Plan ─────────────────────────────────────────────────────

    fun getPlanEntries(): Flow<List<PlanEntry>> = planDao.getEntries()

    suspend fun insertPlanEntry(entry: PlanEntry) = planDao.insert(entry)

    fun getPlanEntriesForDate(date: String): Flow<List<PlanEntry>> = planDao.getEntriesForDate(date)
    fun getEntriesForDate(date: String): Flow<List<PlanEntry>> = planDao.getEntriesForDate(date)

    // ── Daily Stats ──────────────────────────────────────────────

    suspend fun getDailyStats(date: String): DailyStats? = dailyStatsDao.getStats(date)
    suspend fun getStats(date: String): DailyStats? = dailyStatsDao.getStats(date)

    suspend fun upsertDailyStats(stats: DailyStats) = dailyStatsDao.upsert(stats)
    suspend fun upsertStats(stats: DailyStats) = dailyStatsDao.upsert(stats)

    suspend fun getStatsForWeek(startDate: String, endDate: String): List<DailyStats> =
        dailyStatsDao.getStatsForWeek(startDate, endDate)

    suspend fun getStreakDays(): List<String> = dailyStatsDao.getStreakDays()

    suspend fun markBriefDone() {
        val today = getTodayDate()
        val stats = dailyStatsDao.getStats(today) ?: DailyStats(date = today)
        dailyStatsDao.upsert(stats.copy(briefDone = true))
        addXp(5, "audit", "Daily briefing completed")
    }

    suspend fun markAuditDone() {
        val today = getTodayDate()
        val stats = dailyStatsDao.getStats(today) ?: DailyStats(date = today)
        dailyStatsDao.upsert(stats.copy(auditDone = true))
        addXp(10, "audit", "Daily audit completed")
    }

    // ── Streak ───────────────────────────────────────────────────

    suspend fun calculateStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        var streak = 0

        while (true) {
            val dateStr = sdf.format(calendar.time)
            val stats = dailyStatsDao.getStats(dateStr)
            if (stats != null && stats.laborsCompleted > 0) {
                streak++
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                if (streak == 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    val yesterdayStr = sdf.format(calendar.time)
                    val yesterdayStats = dailyStatsDao.getStats(yesterdayStr)
                    if (yesterdayStats != null && yesterdayStats.laborsCompleted > 0) {
                        streak++
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        while (true) {
                            val d = sdf.format(calendar.time)
                            val s = dailyStatsDao.getStats(d)
                            if (s != null && s.laborsCompleted > 0) {
                                streak++
                                calendar.add(Calendar.DAY_OF_YEAR, -1)
                            } else break
                        }
                    }
                }
                break
            }
        }

        val today = getTodayDate()
        val stats = dailyStatsDao.getStats(today) ?: DailyStats(date = today)
        dailyStatsDao.upsert(stats.copy(streakCount = streak))

        return streak
    }

    // ── Sync helpers (for widgets/services, run on IO thread) ──

    fun getLaborsForDateSync(date: String): List<Labor> = laborDao.getLaborsForDateSync(date)

    // ── Internal helpers ─────────────────────────────────────────

    private suspend fun updateDailyLaborStats() {
        val today = getTodayDate()
        val completed = laborDao.getCompletedCount(today)
        val total = laborDao.getTotalCount(today)
        val stats = dailyStatsDao.getStats(today) ?: DailyStats(date = today)
        dailyStatsDao.upsert(stats.copy(laborsCompleted = completed, laborsTotal = total))
    }
}
