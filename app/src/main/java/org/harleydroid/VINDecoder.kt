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
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView

object VINDecoder {

	@JvmStatic
	fun show(activity: Activity, vin: CharSequence) {
		val li = LayoutInflater.from(activity)
		val view = li.inflate(R.layout.vin, null)
		val builder = AlertDialog.Builder(activity)
		builder.setView(view)
		builder.setPositiveButton(R.string.vin_ok, null)
		builder.setIcon(R.drawable.ic_launcher_harleydroid)
		val dialog = builder.create()
		dialog.show()

		var t = dialog.findViewById<TextView>(R.id.vin_destination)
		var r = activity.resources.getIdentifier("vin_destination_" + vin[0], "string", activity.packageName)
		if (r != 0) t.setText(r) else t.text = "?"

		t = dialog.findViewById(R.id.vin_class)
		r = activity.resources.getIdentifier("vin_class_" + vin[3], "string", activity.packageName)
		if (r != 0) t.setText(r) else t.text = "?"

		t = dialog.findViewById(R.id.vin_model)
		r = activity.resources.getIdentifier("vin_model_" + vin.subSequence(4, 6), "string", activity.packageName)
		if (r != 0)
			t.text = activity.getText(r).toString() + " (" + vin.subSequence(4, 6) + ")"
		else
			t.text = "? (" + vin.subSequence(4, 6) + ")"

		t = dialog.findViewById(R.id.vin_engine)
		r = activity.resources.getIdentifier("vin_engine_" + vin[6], "string", activity.packageName)
		if (r != 0) t.setText(r) else t.text = "?"

		t = dialog.findViewById(R.id.vin_date)
		r = activity.resources.getIdentifier("vin_date_" + vin[7], "string", activity.packageName)
		if (r != 0) t.setText(r) else t.text = "?"

		t = dialog.findViewById(R.id.vin_year)
		when {
			vin[9] in '1'..'9' -> t.text = "" + (2000 + (vin[9] - '0'))
			vin[9] in 'A'..'Z' -> t.text = "" + (2010 + (vin[9] - 'A'))
			else -> t.text = "?"
		}

		t = dialog.findViewById(R.id.vin_plant)
		r = activity.resources.getIdentifier("vin_plant_" + vin[10], "string", activity.packageName)
		if (r != 0) t.setText(r) else t.text = "?"

		t = dialog.findViewById(R.id.vin_serial)
		t.text = vin.subSequence(11, 17)
	}
}
