package com.deskdeck.burnin

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pixel shift: the whole UI moves to a new point on a loose orbit around its home position about
 * once a minute. Advancing the angle by 60–150° each time (rather than jumping randomly) spreads
 * wear evenly around the circle and never moves far in one step.
 */
object PixelShift {
    const val MOVE_DURATION_MS = 6_000
    const val INTERVAL_MS = 60_000L
    const val INTERVAL_JITTER_MS = 15_000L

    data class Step(val angleRad: Double, val radiusFraction: Double)

    fun next(previousAngleRad: Double, random: Random): Step {
        val advance = (60.0 + random.nextDouble() * 90.0) * PI / 180.0
        val angle = (previousAngleRad + advance) % (2 * PI)
        return Step(angle, 0.5 + random.nextDouble() * 0.5)
    }

    /** Offset in the same unit as [amplitude]; always within the amplitude circle. */
    fun offset(step: Step, amplitude: Float): Pair<Float, Float> {
        val r = amplitude * step.radiusFraction
        return (r * cos(step.angleRad)).toFloat() to (r * sin(step.angleRad)).toFloat()
    }
}

/** Slow random walk for the ambient and paused-clock views: small steps toward a random target. */
object Drift {
    data class Point(val x: Float, val y: Float)

    fun randomPoint(maxX: Float, maxY: Float, random: Random) =
        Point(random.nextFloat() * maxX.coerceAtLeast(0f), random.nextFloat() * maxY.coerceAtLeast(0f))

    /** Moves [from] toward [target] by at most [maxStep]; lands exactly on target when close. */
    fun step(from: Point, target: Point, maxStep: Float): Point {
        val dx = target.x - from.x
        val dy = target.y - from.y
        val distance = hypot(dx, dy)
        if (distance <= maxStep) return target
        return Point(from.x + dx / distance * maxStep, from.y + dy / distance * maxStep)
    }

    fun clamp(p: Point, maxX: Float, maxY: Float) =
        Point(p.x.coerceIn(0f, maxX.coerceAtLeast(0f)), p.y.coerceIn(0f, maxY.coerceAtLeast(0f)))
}

/** Sidebar icon opacity drifting between [MIN] and 1 over [PERIOD_MS], so icons don't sit at one level. */
object RailOpacity {
    const val MIN = 0.7f
    const val PERIOD_MS = 7 * 60_000L

    fun at(timeMs: Long): Float {
        val phase = (timeMs % PERIOD_MS).toDouble() / PERIOD_MS * 2 * PI
        return (MIN + (1f - MIN) * (0.5 + 0.5 * cos(phase))).toFloat()
    }
}
