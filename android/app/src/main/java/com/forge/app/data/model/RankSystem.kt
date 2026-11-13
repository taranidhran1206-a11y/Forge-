package com.forge.app.data.model

object RankSystem {

    enum class Rank(val title: String, val minXp: Int, val colorHex: String) {
        IRON("IRON", 0, "#78909C"),
        BRONZE("BRONZE", 200, "#A1887F"),
        SILVER("SILVER", 500, "#B0BEC5"),
        GOLD("GOLD", 1000, "#FFD54F"),
        PLATINUM("PLATINUM", 2000, "#80DEEA"),
        DIAMOND("DIAMOND", 4000, "#CE93D8"),
        TITAN("TITAN", 7500, "#FF5252"),
        LEGEND("LEGEND", 12000, "#E53935")
    }

    private val ranksDescending = Rank.values().sortedByDescending { it.minXp }

    fun getRank(totalXp: Int): Rank {
        return ranksDescending.firstOrNull { totalXp >= it.minXp } ?: Rank.IRON
    }

    fun getNextRank(current: Rank): Rank? {
        val ranks = Rank.values()
        val idx = ranks.indexOf(current)
        return if (idx < ranks.size - 1) ranks[idx + 1] else null
    }

    fun getProgress(totalXp: Int): Float {
        val current = getRank(totalXp)
        val next = getNextRank(current) ?: return 1.0f
        val range = next.minXp - current.minXp
        if (range <= 0) return 1.0f
        return ((totalXp - current.minXp).toFloat() / range).coerceIn(0f, 1f)
    }

    fun getXpToNext(totalXp: Int): Int {
        val current = getRank(totalXp)
        val next = getNextRank(current) ?: return 0
        return (next.minXp - totalXp).coerceAtLeast(0)
    }

    fun getGlobalRankPercent(totalXp: Int): Int {
        // Simulated global rank percentage based on XP thresholds
        return when {
            totalXp >= 12000 -> 99
            totalXp >= 7500 -> 95
            totalXp >= 4000 -> 88
            totalXp >= 2000 -> 75
            totalXp >= 1000 -> 60
            totalXp >= 500 -> 40
            totalXp >= 200 -> 25
            totalXp >= 50 -> 10
            else -> 5
        }
    }
}
