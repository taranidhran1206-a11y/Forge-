package com.forge.app.alarm

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Counts reps from joint angles. A rep is a full trip down past [downBelow] and back up
 * past [upAbove], so half reps and wobbles at the top don't count.
 */
class RepCounter(val exercise: Exercise) {

    enum class Exercise(val label: String, val downBelow: Double, val upAbove: Double, val tip: String) {
        PUSHUPS("push-ups", 95.0, 150.0, "Put the phone on the floor, side-on to you, about 2 m away, so your whole body is in view."),
        SQUATS("squats", 105.0, 160.0, "Stand the phone up facing you, about 2 to 3 m away, so you're in view head to feet.");

        companion object {
            fun of(key: String) = values().firstOrNull { it.name.equals(key, true) } ?: PUSHUPS
        }
    }

    enum class Phase { WAITING, UP, DOWN }

    var reps = 0
        private set
    var phase = Phase.WAITING
        private set
    /** The smoothed angle the counter last saw, for on-screen feedback. */
    var angle = 0.0
        private set

    private var smoothed = -1.0

    /** Feed one frame's angle (degrees). Returns true when this frame finished a rep. */
    fun feed(rawAngle: Double): Boolean {
        smoothed = if (smoothed < 0) rawAngle else smoothed * 0.6 + rawAngle * 0.4
        angle = smoothed
        when (phase) {
            Phase.WAITING -> if (smoothed > exercise.upAbove) phase = Phase.UP
            Phase.UP -> if (smoothed < exercise.downBelow) phase = Phase.DOWN
            Phase.DOWN -> if (smoothed > exercise.upAbove) {
                phase = Phase.UP
                reps++
                return true
            }
        }
        return false
    }

    /** Lost sight of the body: keep the count, but smoothing starts fresh. */
    fun lost() {
        smoothed = -1.0
    }

    companion object {
        /** Angle at b, in degrees (0..180), between the lines b→a and b→c. */
        fun angleAt(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Double {
            val r = Math.toDegrees(atan2(cy - by, cx - bx) - atan2(ay - by, ax - bx))
            var d = abs(r)
            if (d > 180) d = 360 - d
            return d
        }
    }
}
