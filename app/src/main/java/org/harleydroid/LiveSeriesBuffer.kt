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

import java.util.ArrayDeque

/**
 * Sliding-window buffer for live chart samples (A3).
 */
class LiveSeriesBuffer(
    private val windowMs: Long = 60_000L,
    private val maxPointsPerSeries: Int = 1200
) {
    private val series = LinkedHashMap<String, ArrayDeque<LogPoint>>()
    private val lock = Any()

    fun append(type: String, value: Float, timeMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val q = series.getOrPut(type) { ArrayDeque() }
            q.addLast(LogPoint(timeMs, value))
            trim(q, timeMs)
        }
    }

    fun presentTypes(): Set<String> = synchronized(lock) {
        series.filter { it.value.isNotEmpty() }.keys
    }

    fun snapshot(type: String, nowMs: Long = System.currentTimeMillis()): FloatSeries? {
        synchronized(lock) {
            val q = series[type] ?: return null
            trim(q, nowMs)
            if (q.isEmpty()) return null
            val points = q.toList()
            var min = Float.POSITIVE_INFINITY
            var max = Float.NEGATIVE_INFINITY
            for (p in points) {
                if (p.value < min) min = p.value
                if (p.value > max) max = p.value
            }
            if (min == max) {
                min -= 1f
                max += 1f
            }
            return FloatSeries(type, points, min, max)
        }
    }

    fun windowStart(nowMs: Long = System.currentTimeMillis()): Long = nowMs - windowMs

    fun clear() = synchronized(lock) { series.clear() }

    private fun trim(q: ArrayDeque<LogPoint>, nowMs: Long) {
        val cutoff = nowMs - windowMs
        while (q.isNotEmpty() && q.first().timeMs < cutoff) q.removeFirst()
        while (q.size > maxPointsPerSeries) q.removeFirst()
    }
}
