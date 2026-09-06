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
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object VINDecoder {

	@JvmStatic
	fun show(activity: Activity, vin: CharSequence) {
		val view = LayoutInflater.from(activity).inflate(R.layout.vin, null)
		MaterialAlertDialogBuilder(activity)
			.setView(view)
			.setPositiveButton(R.string.vin_ok, null)
			.setIcon(R.drawable.ic_launcher_harleydroid)
			.show()

		fun bind(id: Int, resName: String, fallback: CharSequence = "?") {
			val t = view.requireViewById<TextView>(id)
			val r = activity.resources.getIdentifier(resName, "string", activity.packageName)
			if (r != 0) t.setText(r) else t.text = fallback
		}

		bind(R.id.vin_destination, "vin_destination_" + vin[0])
		bind(R.id.vin_class, "vin_class_" + vin[3])

		val modelView = view.requireViewById<TextView>(R.id.vin_model)
		val modelCode = vin.subSequence(4, 6)
		val modelRes = activity.resources.getIdentifier(
			"vin_model_$modelCode",
			"string",
			activity.packageName
		)
		modelView.text = if (modelRes != 0) {
			activity.getText(modelRes).toString() + " ($modelCode)"
		} else {
			"? ($modelCode)"
		}

		bind(R.id.vin_engine, "vin_engine_" + vin[6])
		bind(R.id.vin_date, "vin_date_" + vin[7])

		val yearView = view.requireViewById<TextView>(R.id.vin_year)
		yearView.text = when (vin[9]) {
			in '1'..'9' -> "" + (2000 + (vin[9] - '0'))
			in 'A'..'Z' -> "" + (2010 + (vin[9] - 'A'))
			else -> "?"
		}

		bind(R.id.vin_plant, "vin_plant_" + vin[10])
		view.requireViewById<TextView>(R.id.vin_serial).text = vin.subSequence(11, 17)
	}
}
