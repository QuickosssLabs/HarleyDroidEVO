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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language override (Preferences → Language).
 * Uses [AppCompatDelegate.setApplicationLocales] so activities recreate with the new resources.
 */
object AppLocale {
	const val PREF_KEY = "language"
	const val SYSTEM = "system"
	const val EN = "en"
	const val FR = "fr"

	@JvmStatic
	fun applyFromPrefs(prefs: SharedPreferences) {
		apply(prefs.getString(PREF_KEY, SYSTEM) ?: SYSTEM)
	}

	@JvmStatic
	fun apply(tag: String) {
		val locales = when (tag) {
			EN -> LocaleListCompat.forLanguageTags("en")
			FR -> LocaleListCompat.forLanguageTags("fr")
			else -> LocaleListCompat.getEmptyLocaleList()
		}
		AppCompatDelegate.setApplicationLocales(locales)
	}
}
