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

object DtcDescriptions {

    fun lookup(context: Context, code: String): String? {
        val codes = context.resources.getStringArray(R.array.dtc_codes)
        val strings = context.resources.getStringArray(R.array.dtc_strings)
        val n = minOf(codes.size, strings.size)
        for (i in 0 until n) {
            if (codes[i].equals(code, ignoreCase = true)) return strings[i]
        }
        return null
    }

    fun lookupOrUnknown(context: Context, code: String): String =
        lookup(context, code) ?: context.getString(R.string.dtc_unknown_desc_fmt, code)
}
