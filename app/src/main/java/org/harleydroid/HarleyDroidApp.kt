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

import android.app.Application
import androidx.preference.PreferenceManager

class HarleyDroidApp : Application() {
	override fun onCreate() {
		super.onCreate()
		val prefs = PreferenceManager.getDefaultSharedPreferences(this)
		// Simulation is a session tool — always start in real (Bluetooth) mode
		if (prefs.getBoolean("emulator", false)) {
			prefs.edit().putBoolean("emulator", false).apply()
		}
		AppLocale.applyFromPrefs(prefs)
	}
}
