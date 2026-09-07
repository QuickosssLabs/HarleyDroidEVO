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

import android.content.Context
import java.util.Locale

/** Labels / formatting for log chart metrics (A2). */
object LogMetrics {

    /** Preferred chip order when present in a log. */
    val SELECTABLE: List<String> = listOf("RPM", "SPD", "ETP", "GER", "ODO", "FUL", "FGE")

    fun chipLabel(type: String, metric: Boolean): String = when (type) {
        "RPM" -> "RPM"
        "SPD" -> if (metric) "SPD km/h" else "SPD mph"
        "ETP" -> if (metric) "ETP °C" else "ETP °F"
        "GER" -> "GER"
        "ODO" -> if (metric) "ODO km" else "ODO mi"
        "FUL" -> if (metric) "FUL ml" else "FUL oz"
        "FGE" -> "FGE"
        else -> type
    }

    fun shortLabel(type: String): String = type

    fun formatValue(type: String, value: Float?): String {
        if (value == null) return "—"
        return when (type) {
            "RPM", "GER", "FGE", "CHK", "NTR", "CLU" ->
                "%.0f".format(Locale.US, value)
            "SPD", "ETP" ->
                if (value >= 100f) "%.0f".format(Locale.US, value)
                else "%.1f".format(Locale.US, value)
            "ODO" -> "%.2f".format(Locale.US, value / 100f)
            "FUL" -> "%.0f".format(Locale.US, value)
            else -> "%.1f".format(Locale.US, value)
        }
    }

    /** Trip distance from ODO series (log stores ×100). */
    fun tripDistance(series: FloatSeries?): Float? {
        val points = series?.points ?: return null
        if (points.size < 2) return null
        val delta = points.last().value - points.first().value
        if (delta < 0f) return null
        return delta / 100f
    }

    fun formatTrip(distance: Float?, metric: Boolean): String {
        if (distance == null) return "—"
        val unit = if (metric) "km" else "mi"
        return "%.1f %s".format(Locale.US, distance, unit)
    }

    fun isMetric(context: Context): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString("unit", "metric") == "metric"
    }
}
