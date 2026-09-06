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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.R as MaterialR

/**
 * Accent color theme (Preferences → Color theme).
 * Surfaces stay dark; only the brand accent changes.
 */
object AppTheme {
	const val PREF_KEY = "colortheme"
	const val ORANGE = "orange"
	const val AMBER = "amber"
	const val CRIMSON = "crimson"
	const val TEAL = "teal"
	const val BLUE = "blue"
	const val GREEN = "green"
	const val SILVER = "silver"

	@JvmStatic
	fun currentId(prefs: SharedPreferences): String =
		prefs.getString(PREF_KEY, ORANGE) ?: ORANGE

	@JvmStatic
	fun currentId(context: Context): String =
		currentId(PreferenceManager.getDefaultSharedPreferences(context))

	@StyleRes
	@JvmStatic
	fun styleRes(themeId: String): Int = when (themeId) {
		AMBER -> R.style.Theme_HarleyDroid_Amber
		CRIMSON -> R.style.Theme_HarleyDroid_Crimson
		TEAL -> R.style.Theme_HarleyDroid_Teal
		BLUE -> R.style.Theme_HarleyDroid_Blue
		GREEN -> R.style.Theme_HarleyDroid_Green
		SILVER -> R.style.Theme_HarleyDroid_Silver
		else -> R.style.Theme_HarleyDroid
	}

	/** Call before [Activity.setContentView]. */
	@JvmStatic
	fun apply(activity: Activity) {
		activity.setTheme(styleRes(currentId(activity)))
	}

	@ColorInt
	@JvmStatic
	fun primary(context: Context): Int =
		resolveColor(context, MaterialR.attr.colorPrimary, 0xFFFF6B1E.toInt())

	@ColorInt
	@JvmStatic
	fun primaryContainer(context: Context): Int =
		resolveColor(context, MaterialR.attr.colorPrimaryContainer, 0xFF3D2318.toInt())

	@ColorInt
	@JvmStatic
	fun primarySoft(context: Context): Int =
		resolveColor(context, R.attr.hdColorAccentSoft, 0x26FF6B1E)

	@ColorInt
	@JvmStatic
	fun gaugeReadout(context: Context): Int =
		resolveColor(context, R.attr.hdColorGaugeReadout, 0xE8FF6B1E.toInt())

	@ColorInt
	private fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int): Int {
		val tv = TypedValue()
		return if (context.theme.resolveAttribute(attr, tv, true)) {
			if (tv.resourceId != 0) {
				ContextCompat.getColor(context, tv.resourceId)
			} else {
				tv.data
			}
		} else {
			fallback
		}
	}
}
