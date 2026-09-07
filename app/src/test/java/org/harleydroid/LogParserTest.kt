//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software under the GNU GPL v3 or later. See COPYING.
//
package org.harleydroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

class LogParserTest {

    @Test
    fun parseLine_rpmWithGps() {
        val line = "20260101120000000,RPM,4200,1.0,2.0,3.0"
        val parsed = LogParser.parseLine(line)
        assertNotNull(parsed)
        assertEquals("RPM", parsed!!.type)
        assertEquals(4200f, parsed.numericValue!!, 0.01f)
        assertTrue(parsed.timeMs > 0L)
    }

    @Test
    fun parseLine_speedNoGps() {
        val line = "20260101120001000,SPD,98.5,,"
        val parsed = LogParser.parseLine(line)
        assertNotNull(parsed)
        assertEquals("SPD", parsed!!.type)
        assertEquals(98.5f, parsed.numericValue!!, 0.01f)
    }

    @Test
    fun parseLine_skipsDtcPayload() {
        val line = "20260101120002000,DTC,P0121,P0456,,,"
        val parsed = LogParser.parseLine(line)
        assertNotNull(parsed)
        assertEquals("DTC", parsed!!.type)
        assertNull(parsed.numericValue)
    }

    @Test
    fun parseLine_skipsRaw() {
        val line = "20260101120003000,RAW,281B1002,,"
        val parsed = LogParser.parseLine(line)
        assertNotNull(parsed)
        assertNull(parsed!!.numericValue)
    }

    @Test
    fun parseTimestamp_accepts14And17() {
        val a = LogParser.parseTimestamp("20260101120000")
        val b = LogParser.parseTimestamp("20260101120000000")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(a, b)
    }

    @Test
    fun downsample_keepsEndpoints() {
        val points = (0..99).map { LogPoint(it.toLong(), it.toFloat()) }
        val out = LogParser.downsample(points, 10)
        assertEquals(10, out.size)
        assertEquals(0f, out.first().value, 0f)
        assertEquals(99f, out.last().value, 0f)
    }

    @Test
    fun parse_gzipFileBuildsSeries() {
        val dir = createTempDir(prefix = "hd-log-test")
        val file = File(dir, "harley-test.log.gz")
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { w ->
            w.appendLine("20260101120000000,RPM,1000,,,")
            w.appendLine("20260101120001000,SPD,50,,,")
            w.appendLine("20260101120002000,RPM,2000,,,")
            w.appendLine("20260101120003000,DTC,P0121,,,")
            w.appendLine("20260101120004000,SPD,60,,,")
            w.appendLine("20260101120005000,ETP,95,,,")
            w.appendLine("20260101120006000,ODO,100,,,")
            w.appendLine("20260101120007000,ODO,250,,,")
            w.appendLine("20260101120008000,UNK,deadbeef,,,")
        }
        try {
            val parsed = LogParser.parse(file)
            assertEquals(2, parsed.series["RPM"]!!.points.size)
            assertEquals(2, parsed.series["SPD"]!!.points.size)
            assertEquals(1, parsed.series["ETP"]!!.points.size)
            assertEquals(2, parsed.series["ODO"]!!.points.size)
            assertEquals(1000f, parsed.series["RPM"]!!.min, 0.01f)
            assertEquals(2000f, parsed.series["RPM"]!!.max, 0.01f)
            assertEquals(1.5f, LogMetrics.tripDistance(parsed.series["ODO"])!!, 0.01f)
            val info = LogParser.summarize(file)
            assertTrue(info.hasRpm)
            assertTrue(info.hasSpeed)
            assertTrue("ETP" in info.presentTypes)
            assertTrue("ODO" in info.presentTypes)
            assertTrue(info.durationMs >= 0L)
        } finally {
            file.delete()
            dir.delete()
        }
    }

    @Test
    fun formatValue_odoUsesHundredths() {
        assertEquals("1.50", LogMetrics.formatValue("ODO", 150f))
        assertEquals("4200", LogMetrics.formatValue("RPM", 4200f))
    }
}
