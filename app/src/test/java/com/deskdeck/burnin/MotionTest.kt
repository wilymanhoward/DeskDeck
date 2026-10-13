package com.deskdeck.burnin

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun `pixel shift stays within the amplitude and keeps moving around`() {
        val random = Random(42)
        var angle = 0.0
        repeat(500) {
            val step = PixelShift.next(angle, random)
            val advance = ((step.angleRad - angle) % (2 * PI) + 2 * PI) % (2 * PI)
            assertTrue("advance $advance", advance >= PI / 3 - 1e-9 && advance <= 5 * PI / 6 + 1e-9)
            val (x, y) = PixelShift.offset(step, amplitude = 6f)
            assertTrue(hypot(x, y) <= 6f + 1e-4f)
            assertTrue(hypot(x, y) >= 3f - 1e-4f)
            angle = step.angleRad
        }
    }

    @Test
    fun `drift steps toward the target without overshooting`() {
        val from = Drift.Point(0f, 0f)
        val target = Drift.Point(30f, 40f)
        val step = Drift.step(from, target, maxStep = 5f)
        assertEquals(3f, step.x, 1e-4f)
        assertEquals(4f, step.y, 1e-4f)
        assertEquals(target, Drift.step(Drift.Point(29f, 39f), target, maxStep = 5f))
    }

    @Test
    fun `drift points stay in bounds, even with no room`() {
        val random = Random(1)
        repeat(100) {
            val p = Drift.randomPoint(100f, 50f, random)
            assertTrue(p.x in 0f..100f && p.y in 0f..50f)
        }
        assertEquals(Drift.Point(0f, 0f), Drift.randomPoint(-10f, -10f, random))
        assertEquals(Drift.Point(100f, 0f), Drift.clamp(Drift.Point(150f, -5f), 100f, 50f))
    }

    @Test
    fun `rail opacity drifts between the minimum and full`() {
        val values = (0 until 100).map { RailOpacity.at(it * RailOpacity.PERIOD_MS / 100) }
        assertTrue(values.all { it in RailOpacity.MIN..1f })
        assertEquals(1f, RailOpacity.at(0), 1e-4f)
        assertEquals(RailOpacity.MIN, RailOpacity.at(RailOpacity.PERIOD_MS / 2), 1e-4f)
        // Slow: consecutive 5 s steps differ by well under 1%.
        assertTrue(abs(RailOpacity.at(5_000) - RailOpacity.at(10_000)) < 0.01f)
    }
}
