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

import android.content.SharedPreferences

/** How the phone reaches the ELM327 / HDI adapter. */
object ConnectionTransport {
    const val PREF_KEY = "connectiontransport"
    const val PREF_WIFI_HOST = "wifi_host"
    const val PREF_WIFI_PORT = "wifi_port"

    const val BLUETOOTH = "bluetooth"
    const val WIFI = "wifi"

    const val DEFAULT_WIFI_HOST = "192.168.0.10"
    const val DEFAULT_WIFI_PORT = 35000

    @JvmStatic
    fun fromPrefs(prefs: SharedPreferences): String =
        prefs.getString(PREF_KEY, BLUETOOTH)?.takeIf { it.isNotBlank() } ?: BLUETOOTH

    @JvmStatic
    fun isWifi(prefs: SharedPreferences): Boolean = fromPrefs(prefs) == WIFI

    @JvmStatic
    fun wifiHost(prefs: SharedPreferences): String =
        prefs.getString(PREF_WIFI_HOST, DEFAULT_WIFI_HOST)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_WIFI_HOST

    @JvmStatic
    fun wifiPort(prefs: SharedPreferences): Int {
        val raw = prefs.getString(PREF_WIFI_PORT, DEFAULT_WIFI_PORT.toString())
        return raw?.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_WIFI_PORT
    }
}
