package com.forge.app.data.model

object RankSystem {

    // Paced for about 150 XP on a full day (labors are capped at 150, a reflection
    // adds 15): roughly two weeks to Gold and six months to Legend.
    enum class Rank(val title: String, val minXp: Int, val colorHex: String) {
        IRON("IRON", 0, "#78909C"),
        BRONZE("BRONZE", 300, "#A1887F"),
        SILVER("SILVER", 900, "#B0BEC5"),
        GOLD("GOLD", 2000, "#FFD54F"),
        PLATINUM("PLATINUM", 4000, "#80DEEA"),
        DIAMOND("DIAMOND", 7000, "#CE93D8"),
        TITAN("TITAN", 11000, "#448AFF"),
        MYTHIC("MYTHIC", 16000, "#FF5252"),
        APEX("APEX", 21000, "#FFD700"),
        LEGEND("LEGEND", 27000, "#E53935")
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

    fun getNextRankName(totalXp: Int): String {
        val current = getRank(totalXp)
        val next = getNextRank(current)
        return next?.title ?: "MAX"
    }
}
