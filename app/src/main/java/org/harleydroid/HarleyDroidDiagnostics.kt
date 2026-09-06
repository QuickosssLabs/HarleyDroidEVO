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

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem

class HarleyDroidDiagnostics : HarleyDroid() {

    private lateinit var diagnosticsView: HarleyDroidDiagnosticsView

    companion object {
        private const val COMMAND_TIMEOUT = 500
        private const val GET_DTC_TIMEOUT = 2000
        private const val CLEAR_DTC_TIMEOUT = 500
        private const val COMMAND_DELAY = 10000
        private const val CLEAR_DTC_DELAY = 2000
    }

    private val types = arrayOf(
        "0C", "0C", "0C", "0C", "0C", "0C", "0C", "0C", "6C", "6C", "6C"
    )
    private val tas = arrayOf(
        "10", "10", "10", "10", "10", "10", "10", "10", "10", "40", "60"
    )
    private val sas = arrayOf(
        "F1", "F1", "F1", "F1", "F1", "F1", "F1", "F1", "F1", "F1", "F1"
    )
    private val commands = arrayOf(
        "3C01", "3C02", "3C03", "3C04", "3C0B", "3C0F", "3C10", "3C11",
        "1952FF00", "1952FF00", "1952FF00"
    )
    private val expects = arrayOf(
        "0CF1107C01", "0CF1107C02", "0CF1107C03", "0CF1107C04",
        "0CF1107C0B", "0CF1107C0F", "0CF1107C10", "0CF1107C11",
        "6CF11059", "6CF14059", "6CF16059"
    )
    private val timeouts = intArrayOf(
        COMMAND_TIMEOUT, COMMAND_TIMEOUT, COMMAND_TIMEOUT, COMMAND_TIMEOUT,
        COMMAND_TIMEOUT, COMMAND_TIMEOUT, COMMAND_TIMEOUT, COMMAND_TIMEOUT,
        GET_DTC_TIMEOUT, GET_DTC_TIMEOUT, GET_DTC_TIMEOUT
    )

    private val restartTask = Runnable {
        mHD?.resetHistoricDTC()
        mService?.setSendData(types, tas, sas, commands, expects, timeouts, COMMAND_DELAY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupShell(R.string.diagnostics_name)
        diagnosticsView = HarleyDroidDiagnosticsView(this)
        // Inflate once in onStart (same pattern as dashboard)
    }

    override fun onStart() {
        super.onStart()
        diagnosticsView.changeView(resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        diagnosticsView.drawAll(mHD)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            diagnosticsView.changeView(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
            diagnosticsView.drawAll(mHD)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.diagnostics_menu, menu)
        stripToolbarClickEffects()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Keep ▶ always tappable so first-time users get guidance instead of a dead button.
        if (mService != null) {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_stop)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.disconnect_label)
            menu.findItem(R.id.cleardtc_menu)?.isEnabled = !BusProtocol.isCan(mPrefs)
        } else {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_play_clip)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.connect_label)
            menu.findItem(R.id.cleardtc_menu)?.isEnabled = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.startstop_menu -> {
                if (mService == null) requestConnect() else stopHDS()
                return true
            }
            R.id.cleardtc_menu -> {
                if (BusProtocol.isCan(mPrefs)) {
                    snack(R.string.diag_can_unsupported)
                    return true
                }
                mHD?.resetHistoricDTC()
                clearDTC()
                return true
            }
            R.id.dash_menu -> {
                startActivity(
                    Intent(this, HarleyDroidDashboard::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
                return true
            }
            R.id.preferences_menu -> {
                startActivity(Intent(this, HarleyDroidSettings::class.java))
                return true
            }
            R.id.export_logs_menu -> {
                LogExporter.shareLatestLog(this)
                return true
            }
            R.id.about_menu -> {
                About.about(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun detachHarleyDataListeners() {
        mHD?.removeHarleyDataDiagnosticsListener(diagnosticsView)
    }

    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
        super.onServiceConnected(name, service)
        if (BusProtocol.isCan(mPrefs)) {
            snack(R.string.diag_can_unsupported)
            if (mService?.isPolling != true && mService?.isSending != true) {
                mService?.startPoll()
            }
            connectionViewModel.setState(ConnectionUiState.Polling)
        } else if (mService?.isSending != true) {
            mService?.startSend(types, tas, sas, commands, expects, timeouts, COMMAND_DELAY)
            connectionViewModel.setState(ConnectionUiState.Diagnostics)
        }
        mHD?.addHarleyDataDiagnosticsListener(diagnosticsView)
        diagnosticsView.drawAll(mHD)
    }

    override fun onServiceDisconnected(name: android.content.ComponentName?) {
        detachHarleyDataListeners()
        diagnosticsView.drawAll(null)
        super.onServiceDisconnected(name)
    }

    private fun clearDTC() {
        val cTypes = arrayOf("6C", "6C", "6C")
        val cTas = arrayOf("10", "40", "60")
        val cSas = arrayOf("F1", "F1", "F1")
        val cCommands = arrayOf("14", "14", "14")
        val cExpects = arrayOf("6CF11054", "6CF14054", "6CF16054")
        val cTimeout = intArrayOf(CLEAR_DTC_TIMEOUT, CLEAR_DTC_TIMEOUT, CLEAR_DTC_TIMEOUT)
        mService?.setSendData(cTypes, cTas, cSas, cCommands, cExpects, cTimeout, COMMAND_DELAY)
        Handler(Looper.getMainLooper()).postDelayed(restartTask, CLEAR_DTC_DELAY.toLong())
    }
}
