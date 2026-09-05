//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2010-2012 Stelian Pop <stelian@popies.net>
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

/** Diagnostic / vehicle bus selected in preferences. */
object BusProtocol {
	const val J1850 = "j1850"
	const val CAN = "can"

	@JvmStatic
	fun fromPrefs(prefs: SharedPreferences): String =
		prefs.getString("busprotocol", J1850) ?: J1850

	@JvmStatic
	fun isCan(protocol: String?): Boolean = CAN == protocol

	@JvmStatic
	fun isCan(prefs: SharedPreferences): Boolean = isCan(fromPrefs(prefs))
}
