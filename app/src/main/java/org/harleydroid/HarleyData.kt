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
import java.util.concurrent.CopyOnWriteArrayList

class HarleyData(private val mPrefs: SharedPreferences) {

	private var mRPM = 0
	private var mSpeed = 0
	private var mEngineTemp = 40
	private var mFuelGauge = 0
	private var mFuelLow = false
	private var mTurnSignals = 0
	private var mNeutral = false
	private var mClutch = false
	private var mGear = -1
	private var mCheckEngine = false
	private var mOdometer = 0
	private var mFuel = 0
	private var mVIN = "-----------------"
	private var mECMPN = "------------"
	private var mECMCalID = "------------"
	private var mECMSWLevel = 0
	private var mFuelAverage = -1
	private var mFuelInstant = 1
	private val mModuleDtc = DtcModule.entries.associateWith {
		CopyOnWriteArrayList<String>()
	}

	private var mResetOdometer = -1
	private var mResetFuel = -1
	private var mSavedOdometer = 0
	private var mSavedFuel = 0

	private val mDashboardListeners = CopyOnWriteArrayList<HarleyDataDashboardListener>()
	private val mDiagnosticsListeners = CopyOnWriteArrayList<HarleyDataDiagnosticsListener>()
	private val mRawListeners = CopyOnWriteArrayList<HarleyDataRawListener>()

	private var mHarleyDataThread: HarleyDataThread?

	init {
		mSavedOdometer = mPrefs.getInt("odometer", 0)
		mSavedFuel = mPrefs.getInt("fuel", 0)
		if (mSavedOdometer != 0 && mSavedFuel != 0)
			mFuelAverage = (1250 * mSavedFuel) / mSavedOdometer

		mHarleyDataThread = HarleyDataThread()
		mHarleyDataThread!!.start()
	}

	fun savePersistentData() {
		if (mResetOdometer >= 0) {
			mSavedOdometer = mSavedOdometer + mOdometer - mResetOdometer
			mResetOdometer = -1
		}
		if (mResetFuel >= 0) {
			mSavedFuel = mSavedFuel + mFuel - mResetFuel
			mResetFuel = -1
		}
		val editor = mPrefs.edit()
		editor.putInt("odometer", mSavedOdometer)
		editor.putInt("fuel", mSavedFuel)
		editor.commit()
	}

	fun destroy() {
		savePersistentData()
		mHarleyDataThread?.cancel()
		mHarleyDataThread = null
	}

	fun addHarleyDataDashboardListener(l: HarleyDataDashboardListener) {
		if (!mDashboardListeners.contains(l)) mDashboardListeners.add(l)
	}

	fun removeHarleyDataDashboardListener(l: HarleyDataDashboardListener) {
		mDashboardListeners.remove(l)
	}

	fun addHarleyDataDiagnosticsListener(l: HarleyDataDiagnosticsListener) {
		if (!mDiagnosticsListeners.contains(l)) mDiagnosticsListeners.add(l)
	}

	fun removeHarleyDataDiagnosticsListener(l: HarleyDataDiagnosticsListener) {
		mDiagnosticsListeners.remove(l)
	}

	fun addHarleyDataRawListener(l: HarleyDataRawListener) {
		if (!mRawListeners.contains(l)) mRawListeners.add(l)
	}

	fun removeHarleyDataRawListener(l: HarleyDataRawListener) {
		mRawListeners.remove(l)
	}

	fun getRPM(): Int = mRPM / 4

	fun setRPM(rpm: Int) {
		if (mRPM != rpm) {
			mRPM = rpm
			for (l in mDashboardListeners)
				l.onRPMChanged(mRPM / 4)
		}
	}

	fun getSpeedImperial(): Int = (mSpeed * 125) / (16 * 1609)

	fun getSpeedMetric(): Int = mSpeed / 128

	fun setSpeed(speed: Int) {
		if (mSpeed != speed) {
			mSpeed = speed
			for (l in mDashboardListeners) {
				l.onSpeedImperialChanged((mSpeed * 125) / (16 * 1609))
				l.onSpeedMetricChanged(mSpeed / 128)
			}
		}
	}

	fun getEngineTempImperial(): Int = (mEngineTemp - 40) * 9 / 5 + 32

	fun getEngineTempMetric(): Int = mEngineTemp - 40

	fun setEngineTemp(engineTemp: Int) {
		if (mEngineTemp != engineTemp) {
			mEngineTemp = engineTemp
			for (l in mDashboardListeners) {
				l.onEngineTempImperialChanged((mEngineTemp - 40) * 9 / 5 + 32)
				l.onEngineTempMetricChanged(mEngineTemp - 40)
			}
		}
	}

	fun getFuelGauge(): Int = mFuelGauge

	fun getFuelLow(): Boolean = mFuelLow

	fun setFuelGauge(fuelGauge: Int, fuelLow: Boolean) {
		if (mFuelGauge != fuelGauge || mFuelLow != fuelLow) {
			mFuelGauge = fuelGauge
			mFuelLow = fuelLow
			for (l in mDashboardListeners)
				l.onFuelGaugeChanged(mFuelGauge, mFuelLow)
		}
	}

	fun getTurnSignals(): Int = mTurnSignals

	fun setTurnSignals(turnSignals: Int) {
		if (mTurnSignals != turnSignals) {
			mTurnSignals = turnSignals
			for (l in mDashboardListeners)
				l.onTurnSignalsChanged(mTurnSignals)
		}
	}

	fun getNeutral(): Boolean = mNeutral

	fun setNeutral(neutral: Boolean) {
		if (mNeutral != neutral) {
			mNeutral = neutral
			for (l in mDashboardListeners)
				l.onNeutralChanged(mNeutral)
		}
	}

	fun getClutch(): Boolean = mClutch

	fun setClutch(clutch: Boolean) {
		if (mClutch != clutch) {
			mClutch = clutch
			for (l in mDashboardListeners)
				l.onClutchChanged(mClutch)
		}
	}

	fun getGear(): Int = mGear

	fun setGear(gear: Int) {
		if (mGear != gear) {
			mGear = gear
			for (l in mDashboardListeners)
				l.onGearChanged(mGear)
		}
	}

	fun getCheckEngine(): Boolean = mCheckEngine

	fun setCheckEngine(checkEngine: Boolean) {
		if (mCheckEngine != checkEngine) {
			mCheckEngine = checkEngine
			for (l in mDashboardListeners)
				l.onCheckEngineChanged(mCheckEngine)
		}
	}

	fun getOdometerImperial(): Int {
		return if (mResetOdometer < 0)
			(mSavedOdometer * 40) / 1609
		else
			((mSavedOdometer + mOdometer - mResetOdometer) * 40) / 1609
	}

	fun getOdometerMetric(): Int {
		return if (mResetOdometer < 0)
			mSavedOdometer / 25
		else
			(mSavedOdometer + mOdometer - mResetOdometer) / 25
	}

	fun setOdometer(odometer: Int) {
		if (mResetOdometer < 0)
			mResetOdometer = odometer
		if (mOdometer != odometer) {
			mOdometer = odometer
			for (l in mDashboardListeners) {
				val o = mSavedOdometer + mOdometer - mResetOdometer
				l.onOdometerImperialChanged((o * 40) / 1609)
				l.onOdometerMetricChanged(o / 25)
			}
		}
	}

	/**
	 * Absolute odometer from CAN (bike total), not a session delta like J1850 pulses.
	 * Units match [setOdometer] (metric display = units / 25).
	 */
	fun setOdometerAbsolute(odometer: Int) {
		if (mResetOdometer < 0)
			mResetOdometer = 0
		if (mOdometer != odometer || mResetOdometer != 0) {
			mResetOdometer = 0
			mOdometer = odometer
			for (l in mDashboardListeners) {
				val o = mSavedOdometer + mOdometer
				l.onOdometerImperialChanged((o * 40) / 1609)
				l.onOdometerMetricChanged(o / 25)
			}
		}
	}

	fun getFuelImperial(): Int {
		return if (mResetFuel < 0)
			(mSavedFuel * 264) / 20000
		else
			((mSavedFuel + mFuel - mResetFuel) * 264) / 20000
	}

	fun getFuelMetric(): Int {
		return if (mResetFuel < 0)
			mSavedFuel / 20
		else
			(mSavedFuel + mFuel - mResetFuel) / 20
	}

	/** Session fuel in raw bus units (finer than [getFuelMetric]). */
	fun getFuelRawSession(): Int {
		return if (mResetFuel < 0)
			mSavedFuel
		else
			mSavedFuel + mFuel - mResetFuel
	}

	/** Session odometer in raw bus units (finer than [getOdometerMetric]). */
	fun getOdometerRawSession(): Int {
		return if (mResetOdometer < 0)
			mSavedOdometer
		else
			mSavedOdometer + mOdometer - mResetOdometer
	}

	fun setFuel(fuel: Int) {
		if (mResetFuel < 0)
			mResetFuel = fuel
		if (mFuel != fuel) {
			mFuel = fuel
			val f = mSavedFuel + mFuel - mResetFuel
			for (l in mDashboardListeners) {
				l.onFuelImperialChanged((f * 264) / 20000)
				l.onFuelMetricChanged(f / 20)
			}
			if (getOdometerMetric() != 0 && f != 0)
				setFuelAverage((50 * f) / getOdometerMetric())
			else
				setFuelAverage(-1)
		}
	}

	fun getFuelAverageImperial(): Int =
		if (mFuelAverage == -1) -1 else 2352146 / mFuelAverage

	fun getFuelAverageMetric(): Int = mFuelAverage

	private fun setFuelAverage(fuel: Int) {
		if (mFuelAverage != fuel) {
			mFuelAverage = fuel
			for (l in mDashboardListeners) {
				if (mFuelAverage == -1)
					l.onFuelAverageImperialChanged(-1)
				else
					l.onFuelAverageImperialChanged(2352146 / mFuelAverage)
				l.onFuelAverageMetricChanged(mFuelAverage)
			}
		}
	}

	fun getFuelInstantImperial(): Int =
		if (mFuelInstant == -1) -1 else 2352146 / mFuelInstant

	fun getFuelInstantMetric(): Int = mFuelInstant

	private fun setFuelInstant(fuel: Int) {
		if (mFuelInstant != fuel) {
			mFuelInstant = fuel
			for (l in mDashboardListeners) {
				if (mFuelInstant == -1)
					l.onFuelInstantImperialChanged(-1)
				else
					l.onFuelInstantImperialChanged(2352146 / mFuelInstant)
				l.onFuelInstantMetricChanged(mFuelInstant)
			}
		}
	}

	fun resetCounters() {
		mSavedOdometer = 0
		mSavedFuel = 0
		mResetOdometer = -1
		mResetFuel = -1
		for (l in mDashboardListeners) {
			l.onOdometerImperialChanged(0)
			l.onOdometerMetricChanged(0)
			l.onFuelImperialChanged(0)
			l.onFuelMetricChanged(0)
		}
	}

	fun getVIN(): String = mVIN

	fun setVIN(vin: String) {
		mVIN = vin
		for (l in mDiagnosticsListeners)
			l.onVINChanged(mVIN)
	}

	fun getECMPN(): String = mECMPN

	fun setECMPN(ecmPN: String) {
		mECMPN = ecmPN
		for (l in mDiagnosticsListeners)
			l.onECMPNChanged(mECMPN)
	}

	fun getECMCalID(): String = mECMCalID

	fun setECMCalID(ecmCalID: String) {
		mECMCalID = ecmCalID
		for (l in mDiagnosticsListeners)
			l.onECMCalIDChanged(mECMCalID)
	}

	fun getECMSWLevel(): Int = mECMSWLevel

	fun setECMSWLevel(ecmSWLevel: Int) {
		mECMSWLevel = ecmSWLevel
		for (l in mDiagnosticsListeners)
			l.onECMSWLevelChanged(mECMSWLevel)
	}

	fun getDtc(module: DtcModule): Array<String> {
		val list = mModuleDtc[module] ?: return emptyArray()
		return Array(list.size) { i -> list[i] }
	}

	fun resetAllDtc() {
		for (module in DtcModule.entries) {
			mModuleDtc[module]?.clear()
			notifyModuleDtc(module)
		}
	}

	fun resetDtc(module: DtcModule) {
		mModuleDtc[module]?.clear()
		notifyModuleDtc(module)
	}

	fun addDtc(module: DtcModule, dtc: String) {
		val list = mModuleDtc[module] ?: return
		if (!list.contains(dtc)) list.add(dtc)
		notifyModuleDtc(module)
	}

	private fun notifyModuleDtc(module: DtcModule) {
		val dtclist = getDtc(module)
		for (l in mDiagnosticsListeners)
			l.onModuleDtcChanged(module, dtclist)
	}

	fun setBadCRC(buffer: ByteArray) {
		for (l in mRawListeners)
			l.onBadCRCChanged(buffer)
	}

	fun setUnknown(buffer: ByteArray) {
		for (l in mRawListeners)
			l.onUnknownChanged(buffer)
	}

	fun setRaw(buffer: ByteArray) {
		for (l in mRawListeners)
			l.onRawChanged(buffer)
	}

	override fun toString(): String {
		var ret = "RPM:" + mRPM / 4
		ret += " SPD:" + mSpeed / 128
		ret += " ETP:$mEngineTemp"
		ret += " FGE:$mFuelGauge"
		ret += " TRN:"
		ret += when {
			(mTurnSignals and 0x3) == 0x3 -> "W"
			(mTurnSignals and 0x1) != 0 -> "R"
			(mTurnSignals and 0x2) != 0 -> "L"
			else -> "x"
		}
		ret += " CLU/NTR:"
		ret += if (mNeutral) "N" else "x"
		ret += if (mClutch) "C" else "x"
		ret += if (mGear in 1..6) mGear.toString() else "x"
		ret += " CHK:$mCheckEngine"
		ret += " ODO:$mOdometer"
		ret += " FUL:$mFuel"
		return ret
	}

	private inner class HarleyDataThread : Thread() {
		private val MAX_ITEMS = 10
		private lateinit var fuelItems: IntArray
		private lateinit var odoItems: IntArray
		private var head = 0
		private var tail = 0
		@Volatile private var stop = false

		override fun run() {
			name = "HarleyDataThread"
			fuelItems = IntArray(MAX_ITEMS)
			odoItems = IntArray(MAX_ITEMS)
			head = 0
			tail = 1

			while (!stop) {
				// Use raw session units — getFuelMetric()/20 stays 0 for long stretches in sim
				fuelItems[head] = getFuelRawSession()
				odoItems[head] = getOdometerRawSession()
				val dFuel = fuelItems[head] - fuelItems[tail]
				val dOdo = odoItems[head] - odoItems[tail]
				// Average uses (50 * fuelRaw) / odoMetric where odoMetric = odoRaw/25
				// → instant = (50 * dFuelRaw) / (dOdoRaw/25) = (1250 * dFuelRaw) / dOdoRaw
				if (dFuel > 0 && dOdo > 0) {
					val inst = (1250 * dFuel) / dOdo
					if (inst in 1..4000)
						setFuelInstant(inst)
					else
						setFuelInstant(-1)
				} else {
					setFuelInstant(-1)
				}
				head++
				if (head >= MAX_ITEMS)
					head = 0
				tail++
				if (tail >= MAX_ITEMS)
					tail = 0
				try {
					sleep(1000)
				} catch (_: InterruptedException) {
				}
			}
		}

		fun cancel() {
			stop = true
		}
	}
}
