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
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object About {
    @JvmStatic
    fun about(activity: Activity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.about, null)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(R.string.about_ok, null)
            .setNeutralButton(R.string.about_license) { _, _ -> Eula.show(activity, true) }
            .setIcon(R.drawable.ic_launcher_harleydroid)
            .create()
        dialog.show()
        try {
            val pi = activity.packageManager.getPackageInfo(activity.packageName, 0)
            view.findViewById<TextView>(R.id.about_version).text = pi.versionName
        } catch (_: PackageManager.NameNotFoundException) {
        }
    }
}
