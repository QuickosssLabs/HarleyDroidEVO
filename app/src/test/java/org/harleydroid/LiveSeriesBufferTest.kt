//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software under the GNU GPL v3 or later. See COPYING.
//
package org.harleydroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSeriesBufferTest {

    @Test
    fun dropsPointsOutsideWindow() {
        val buf = LiveSeriesBuffer(windowMs = 1_000L)
        buf.append("RPM", 1000f, timeMs = 0L)
        buf.append("RPM", 2000f, timeMs = 500L)
        buf.append("RPM", 3000f, timeMs = 1500L)
        val snap = buf.snapshot("RPM", nowMs = 1500L)!!
        assertEquals(2, snap.points.size)
        assertEquals(2000f, snap.points.first().value, 0f)
        assertEquals(3000f, snap.points.last().value, 0f)
    }

    @Test
    fun snapshotEmptyReturnsNull() {
        val buf = LiveSeriesBuffer()
        assertNull(buf.snapshot("SPD"))
    }

    @Test
    fun windowStartIsNowMinusWindow() {
        val buf = LiveSeriesBuffer(windowMs = 60_000L)
        assertEquals(40_000L, buf.windowStart(100_000L))
        assertTrue(buf.presentTypes().isEmpty())
        buf.append("ETP", 90f, 100_000L)
        assertTrue("ETP" in buf.presentTypes())
    }
}
