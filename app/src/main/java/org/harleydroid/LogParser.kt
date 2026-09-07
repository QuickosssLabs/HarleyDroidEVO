//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// See COPYING for the full licence text.
//
package org.harleydroid

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class LogPoint(val timeMs: Long, val value: Float)

data class FloatSeries(
    val type: String,
    val points: List<LogPoint>,
    val min: Float,
    val max: Float
)

data class ParsedLog(
    val startMs: Long,
    val endMs: Long,
    val series: Map<String, FloatSeries>,
    val lineCount: Int
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

data class LogFileInfo(
    val file: File,
    val startMs: Long,
    val endMs: Long,
    val sizeBytes: Long,
    val presentTypes: Set<String>,
    val numericSamples: Int
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val hasRpm: Boolean get() = "RPM" in presentTypes
    val hasSpeed: Boolean get() = "SPD" in presentTypes
}

object LogParser {

    /** Types available on the replay chart (A2). */
    val CHART_TYPES: Set<String> = LogMetrics.SELECTABLE.toSet()

    /** All numeric telemetry types we may parse into series. */
    private val NUMERIC_TYPES = setOf(
        "RPM", "SPD", "ETP", "GER", "NTR", "CLU", "CHK", "ODO", "FUL", "FGE", "ESL"
    )

    private const val MAX_DISPLAY_POINTS = 2500

    private val timestampFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).also {
            it.isLenient = false
            it.timeZone = TimeZone.getDefault()
        }
    }

    fun listLogFiles(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".log.gz") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun summarize(file: File): LogFileInfo {
        var startMs = 0L
        var endMs = 0L
        val present = LinkedHashSet<String>()
        var samples = 0
        openReader(file).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parsed = parseLine(line!!) ?: continue
                if (startMs == 0L) startMs = parsed.timeMs
                endMs = parsed.timeMs
                if (parsed.type in CHART_TYPES && parsed.numericValue != null) {
                    present.add(parsed.type)
                    samples++
                }
            }
        }
        if (startMs == 0L) {
            val mod = file.lastModified()
            startMs = mod
            endMs = mod
        }
        return LogFileInfo(
            file = file,
            startMs = startMs,
            endMs = endMs,
            sizeBytes = file.length(),
            presentTypes = present,
            numericSamples = samples
        )
    }

    fun parse(file: File, types: Set<String> = CHART_TYPES): ParsedLog {
        val builders = types.associateWith { ArrayList<LogPoint>() }
        var startMs = 0L
        var endMs = 0L
        var lines = 0
        openReader(file).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lines++
                val parsed = parseLine(line!!) ?: continue
                if (startMs == 0L) startMs = parsed.timeMs
                endMs = parsed.timeMs
                val value = parsed.numericValue ?: continue
                builders[parsed.type]?.add(LogPoint(parsed.timeMs, value))
            }
        }
        if (startMs == 0L) {
            val mod = file.lastModified()
            startMs = mod
            endMs = mod
        }
        val series = builders.mapNotNull { (type, points) ->
            if (points.isEmpty()) return@mapNotNull null
            val downsampled = downsample(points, MAX_DISPLAY_POINTS)
            var min = Float.POSITIVE_INFINITY
            var max = Float.NEGATIVE_INFINITY
            for (p in downsampled) {
                if (p.value < min) min = p.value
                if (p.value > max) max = p.value
            }
            if (min == max) {
                min -= 1f
                max += 1f
            }
            type to FloatSeries(type, downsampled, min, max)
        }.toMap()
        return ParsedLog(startMs, endMs, series, lines)
    }

    /** Visible for unit tests. */
    internal fun parseLine(line: String): ParsedLine? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val firstComma = trimmed.indexOf(',')
        if (firstComma <= 0) return null
        val tsRaw = trimmed.substring(0, firstComma)
        val timeMs = parseTimestamp(tsRaw) ?: return null
        val rest = trimmed.substring(firstComma + 1)
        val secondComma = rest.indexOf(',')
        if (secondComma <= 0) return null
        val type = rest.substring(0, secondComma)
        val payload = rest.substring(secondComma + 1)

        if (type == "DTC" || type == "DTH") {
            return ParsedLine(timeMs, type, null)
        }
        if (type !in NUMERIC_TYPES && type !in CHART_TYPES) {
            return ParsedLine(timeMs, type, null)
        }

        // payload: value[,lon,lat,alt] — value may be EMPTY for FGE
        val valueToken = payload.substringBefore(',')
        if (valueToken.isEmpty() || valueToken.equals("EMPTY", ignoreCase = true)) {
            return ParsedLine(timeMs, type, if (type == "FGE") 0f else null)
        }
        val number = valueToken.toFloatOrNull() ?: return ParsedLine(timeMs, type, null)
        return ParsedLine(timeMs, type, number)
    }

    internal fun parseTimestamp(raw: String): Long? {
        if (raw.length < 14) return null
        val normalized = when {
            raw.length >= 17 -> raw.substring(0, 17)
            raw.length == 14 -> raw + "000"
            else -> raw.padEnd(17, '0').substring(0, 17)
        }
        return try {
            timestampFormat.get()!!.parse(normalized)?.time
        } catch (_: Exception) {
            null
        }
    }

    internal fun downsample(points: List<LogPoint>, maxPoints: Int): List<LogPoint> {
        if (points.size <= maxPoints || maxPoints < 3) return points
        val out = ArrayList<LogPoint>(maxPoints)
        val last = points.size - 1
        out.add(points[0])
        val inner = maxPoints - 2
        for (i in 1..inner) {
            val idx = (i.toLong() * last / (inner + 1)).toInt().coerceIn(1, last - 1)
            out.add(points[idx])
        }
        out.add(points[last])
        return out
    }

    private fun openReader(file: File): BufferedReader {
        val input = FileInputStream(file)
        val stream = if (file.name.endsWith(".gz", ignoreCase = true)) {
            GZIPInputStream(input)
        } else {
            input
        }
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 16 * 1024)
    }

    data class ParsedLine(
        val timeMs: Long,
        val type: String,
        val numericValue: Float?
    )
}
