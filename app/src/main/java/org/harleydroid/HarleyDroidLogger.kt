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

import android.content.Context
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPOutputStream

class HarleyDroidLogger(
    context: Context,
    private val mUnitMetric: Boolean,
    gps: Boolean,
    private val mLogRaw: Boolean,
    private val mLogUnknown: Boolean
) : HarleyDataDashboardListener, HarleyDataDiagnosticsListener, HarleyDataRawListener {

    companion object {
        private const val D = false
        private val TAG = HarleyDroidLogger::class.java.simpleName
        val TIMESTAMP_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).also {
            it.timeZone = TimeZone.getDefault()
        }

        fun myGetBytes(s: String): ByteArray {
            val result = ByteArray(s.length)
            for (i in s.indices) result[i] = s[i].code.toByte()
            return result
        }

        fun logDirectory(context: Context): File {
            return context.getExternalFilesDir(null) ?: context.filesDir
        }
    }

    private val mContext = context.applicationContext
    private var mGPS: HarleyDroidGPS? = if (gps) HarleyDroidGPS(context) else null
    private var mLog: BufferedOutputStream? = null

    fun start() {
        if (D) Log.d(TAG, "start()")
        mGPS?.start()
        try {
            val path = logDirectory(mContext)
            path.mkdirs()
            val logFile = File(path, "harley-${TIMESTAMP_FORMAT.format(Date())}.log.gz")
            mLog = BufferedOutputStream(GZIPOutputStream(FileOutputStream(logFile, false)))
        } catch (e: IOException) {
            Log.d(TAG, "Logfile open $e")
        }
    }

    fun write(header: String, data: ByteArray?) {
        val log = mLog ?: return
        try {
            log.write(myGetBytes(TIMESTAMP_FORMAT.format(Date())))
            log.write(','.code)
            log.write(myGetBytes(header))
            if (data != null) log.write(data)
            log.write(','.code)
            if (mGPS != null) {
                log.write(myGetBytes(mGPS!!.getLocation()))
            } else {
                log.write(','.code)
                log.write(','.code)
            }
            log.write('\n'.code)
        } catch (_: IOException) {
        }
    }

    fun write(header: String, data: String) = write(header, myGetBytes(data))
    fun write(data: String) = write(data, null as ByteArray?)

    fun stop() {
        if (D) Log.d(TAG, "stop()")
        mGPS?.stop()
        mGPS = null
        try {
            mLog?.close()
        } catch (_: IOException) {
        }
        mLog = null
    }

    override fun onRPMChanged(rpm: Int) = write("RPM,$rpm")
    override fun onSpeedImperialChanged(speed: Int) { if (!mUnitMetric) write("SPD,$speed") }
    override fun onSpeedMetricChanged(speed: Int) { if (mUnitMetric) write("SPD,$speed") }
    override fun onEngineTempImperialChanged(engineTemp: Int) { if (!mUnitMetric) write("ETP,$engineTemp") }
    override fun onEngineTempMetricChanged(engineTemp: Int) { if (mUnitMetric) write("ETP,$engineTemp") }
    override fun onFuelGaugeChanged(full: Int, low: Boolean) {
        if (low) write("FGE,EMPTY") else write("FGE,$full")
    }
    override fun onTurnSignalsChanged(turnSignals: Int) {
        when {
            turnSignals and 0x03 == 0x03 -> write("TRN,W")
            turnSignals and 0x01 == 0x01 -> write("TRN,R")
            turnSignals and 0x02 == 0x02 -> write("TRN,L")
            else -> write("TRN,")
        }
    }
    override fun onNeutralChanged(neutral: Boolean) = write("NTR,${if (neutral) "1" else "0"}")
    override fun onClutchChanged(clutch: Boolean) = write("CLU,${if (clutch) "1" else "0"}")
    override fun onGearChanged(gear: Int) = write("GER,$gear")
    override fun onCheckEngineChanged(checkEngine: Boolean) = write("CHK,${if (checkEngine) "1" else "0"}")
    override fun onOdometerImperialChanged(odometer: Int) { if (!mUnitMetric) write("ODO,$odometer") }
    override fun onOdometerMetricChanged(odometer: Int) { if (mUnitMetric) write("ODO,$odometer") }
    override fun onFuelImperialChanged(fuel: Int) { if (!mUnitMetric) write("FUL,$fuel") }
    override fun onFuelMetricChanged(fuel: Int) { if (mUnitMetric) write("FUL,$fuel") }
    override fun onFuelAverageImperialChanged(fuel: Int) {}
    override fun onFuelAverageMetricChanged(fuel: Int) {}
    override fun onFuelInstantImperialChanged(fuel: Int) {}
    override fun onFuelInstantMetricChanged(fuel: Int) {}
    override fun onVINChanged(vin: String) = write("VIN,$vin")
    override fun onECMPNChanged(ecmPN: String) = write("EPN,$ecmPN")
    override fun onECMCalIDChanged(ecmCalID: String) = write("ECI,$ecmCalID")
    override fun onECMSWLevelChanged(swLevel: Int) = write("ESL,$swLevel")
    override fun onHistoricDTCChanged(dtc: Array<String>) = write("DTH,", dtc.joinToString(","))
    override fun onCurrentDTCChanged(dtc: Array<String>) = write("DTC,", dtc.joinToString(","))
    override fun onBadCRCChanged(buffer: ByteArray) = write("CRC,", buffer)
    override fun onUnknownChanged(buffer: ByteArray) { if (mLogUnknown) write("UNK,", buffer) }
    override fun onRawChanged(buffer: ByteArray) { if (mLogRaw) write("RAW,", buffer) }
}
