package com.forge.app

import com.forge.app.alarm.AlarmStore
import com.forge.app.alarm.RepCounter
import com.forge.app.critique.Verdict
import com.forge.app.fuel.Fuel
import com.forge.app.gym.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LogicTest {

    @Test
    fun repCounterNeedsAFullRep() {
        val c = RepCounter(RepCounter.Exercise.PUSHUPS)
        // Arms straight, down, back up: one rep (feed each angle a few times for the smoothing)
        listOf(170.0, 80.0, 170.0).forEach { a -> repeat(10) { c.feed(a) } }
        assertEquals(1, c.reps)
        // A half rep (only down to 120°) doesn't count
        listOf(120.0, 170.0).forEach { a -> repeat(10) { c.feed(a) } }
        assertEquals(1, c.reps)
        listOf(70.0, 175.0, 70.0, 175.0).forEach { a -> repeat(10) { c.feed(a) } }
        assertEquals(3, c.reps)
    }

    @Test
    fun repCounterWaitsForTheStartPosition() {
        val c = RepCounter(RepCounter.Exercise.SQUATS)
        // Starting already squatted and standing up isn't a rep
        listOf(90.0, 170.0).forEach { a -> repeat(10) { c.feed(a) } }
        assertEquals(0, c.reps)
        listOf(90.0, 170.0).forEach { a -> repeat(10) { c.feed(a) } }
        assertEquals(1, c.reps)
    }

    @Test
    fun angleAtJoint() {
        assertEquals(90.0, RepCounter.angleAt(0.0, 1.0, 0.0, 0.0, 1.0, 0.0), 0.001)
        assertEquals(180.0, RepCounter.angleAt(-1.0, 0.0, 0.0, 0.0, 1.0, 0.0), 0.001)
        assertEquals(45.0, RepCounter.angleAt(1.0, 1.0, 0.0, 0.0, 1.0, 0.0), 0.001)
    }

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int) = Calendar.getInstance().apply {
        set(year, month, day, h, m, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun alarmRingsNextMatchingDay() {
        // Friday 28 Nov 2025... use a fixed date: Wed 3 Dec 2025, 07:00
        val now = at(2025, Calendar.DECEMBER, 3, 7, 0)
        val weekdays = AlarmStore.Alarm(1, 6, 30, setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY))
        // 06:30 already passed today, so Thursday 06:30
        assertEquals(at(2025, Calendar.DECEMBER, 4, 6, 30), AlarmStore.nextTrigger(weekdays, now))
        val later = weekdays.copy(hour = 8, minute = 0)
        assertEquals(at(2025, Calendar.DECEMBER, 3, 8, 0), AlarmStore.nextTrigger(later, now))
        // Weekend-only from a Wednesday → Saturday
        val weekend = weekdays.copy(days = setOf(Calendar.SATURDAY, Calendar.SUNDAY))
        assertEquals(at(2025, Calendar.DECEMBER, 6, 6, 30), AlarmStore.nextTrigger(weekend, now))
        // Once, and off
        assertEquals(at(2025, Calendar.DECEMBER, 4, 6, 30), AlarmStore.nextTrigger(weekdays.copy(days = emptySet()), now))
        assertEquals(null, AlarmStore.nextTrigger(weekdays.copy(enabled = false), now))
    }

    @Test
    fun verdictScoreSkipsPartsThatDidNotApply() {
        val parts = listOf(
            Verdict.Part("labors", "Labors", 100, ""),
            Verdict.Part("focus", "Focus", 50, ""),
            Verdict.Part("training", "Training", null, "Rest day"),
            Verdict.Part("fuel", "Fuel", null, ""),
            Verdict.Part("mind", "Reflection", 0, "")
        )
        // (100*30 + 50*25 + 0*10) / 65 = 65.4
        assertEquals(65, Verdict.overall(parts))
        assertEquals(0, Verdict.overall(emptyList()))
    }

    @Test
    fun verdictRoundTrips() {
        val r = Verdict.Result("2025-12-03", 72, "Solid.", listOf("Trained"), listOf("Start earlier"),
            listOf(Verdict.Part("labors", "Labors", 80, "4 of 5"), Verdict.Part("fuel", "Fuel", null, "Nothing logged")), 5L)
        val back = Verdict.Result.fromJson(r.toJson())!!
        assertEquals(r, back)
    }

    @Test
    fun strengthMaths() {
        assertEquals(100.0, Strength.e1rm(100.0, 1), 0.5)
        assertTrue(Strength.e1rm(80.0, 8) > 95.0)
    }

    @Test
    fun fuelGoalsAreSensible() {
        val g = Fuel.suggest(Fuel.Profile(male = true, age = 20, heightCm = 175, weightKg = 70.0, activity = 2, aim = 1))
        assertTrue(g.kcal in 2600..3300)
        assertEquals(126, g.protein)
        val cut = Fuel.suggest(Fuel.Profile(male = false, age = 30, heightCm = 150, weightKg = 45.0, activity = 0, aim = -1))
        assertTrue(cut.kcal >= 1200)
    }
}
