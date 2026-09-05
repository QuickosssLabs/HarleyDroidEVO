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

interface HarleyDataDashboardListener {
	fun onRPMChanged(rpm: Int)
	fun onSpeedImperialChanged(speed: Int)
	fun onSpeedMetricChanged(speed: Int)
	fun onEngineTempImperialChanged(engineTemp: Int)
	fun onEngineTempMetricChanged(engineTemp: Int)
	fun onFuelGaugeChanged(full: Int, low: Boolean)
	fun onTurnSignalsChanged(turnSignals: Int)
	fun onNeutralChanged(neutral: Boolean)
	fun onClutchChanged(clutch: Boolean)
	fun onGearChanged(gear: Int)
	fun onCheckEngineChanged(checkEngine: Boolean)
	fun onOdometerImperialChanged(odometer: Int)
	fun onOdometerMetricChanged(odometer: Int)
	fun onFuelImperialChanged(fuel: Int)
	fun onFuelMetricChanged(fuel: Int)
	fun onFuelAverageImperialChanged(fuel: Int)
	fun onFuelAverageMetricChanged(fuel: Int)
	fun onFuelInstantImperialChanged(fuel: Int)
	fun onFuelInstantMetricChanged(fuel: Int)
}
