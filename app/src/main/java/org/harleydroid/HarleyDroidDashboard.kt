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
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.MenuCompat

class HarleyDroidDashboard : HarleyDroid() {

    private lateinit var dashboardView: HarleyDroidDashboardView
    private var viewMode = HarleyDroidDashboardView.VIEW_GRAPHIC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupShell(R.string.dashboard_name)
        dashboardView = HarleyDroidDashboardView(this)
        dashboardView.changeView(
            viewMode,
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
            mUnitMetric
        )
    }

    override fun onStart() {
        super.onStart()
        viewMode = mPrefs.getInt("dashboardviewmode", HarleyDroidDashboardView.VIEW_GRAPHIC)
        val portrait = if (mOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        } else {
            mOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        dashboardView.changeView(viewMode, portrait, mUnitMetric)
        dashboardView.drawAll(mHD)
    }

    override fun onStop() {
        super.onStop()
        mPrefs.edit().putInt("dashboardviewmode", viewMode).apply()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (mOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            dashboardView.changeView(
                viewMode,
                newConfig.orientation == Configuration.ORIENTATION_PORTRAIT,
                mUnitMetric
            )
            dashboardView.drawAll(mHD)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        MenuCompat.setGroupDividerEnabled(menu, true)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.startstop_menu)?.isEnabled = mBluetoothID != null || isEmulatorMode()
        if (mService != null) {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_stop)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.disconnect_label)
        } else {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_play_clip)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.connect_label)
        }
        menu.findItem(R.id.mode_menu)?.setTitle(
            if (viewMode == HarleyDroidDashboardView.VIEW_GRAPHIC) R.string.mode_labelraw
            else R.string.mode_labelgr
        )
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.startstop_menu -> {
                if (mService == null) ensureConnectPermissions(true) else stopHDS()
                return true
            }
            R.id.mode_menu -> {
                viewMode = if (viewMode == HarleyDroidDashboardView.VIEW_GRAPHIC)
                    HarleyDroidDashboardView.VIEW_TEXT
                else HarleyDroidDashboardView.VIEW_GRAPHIC
                dashboardView.changeView(
                    viewMode,
                    resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                    mUnitMetric
                )
                dashboardView.drawAll(mHD)
                invalidateOptionsMenu()
                return true
            }
            R.id.diag_menu -> {
                startActivity(Intent(this, HarleyDroidDiagnostics::class.java))
                return true
            }
            R.id.preferences_menu -> {
                startActivity(Intent(this, HarleyDroidSettings::class.java))
                return true
            }
            R.id.reset_menu -> {
                if (mHD != null) mHD!!.resetCounters()
                else mPrefs.edit().putInt("odometer", 0).putInt("fuel", 0).apply()
                dashboardView.drawAll(mHD)
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

    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
        super.onServiceConnected(name, service)
        if (mService?.isPolling != true) mService?.startPoll()
        mHD?.addHarleyDataDashboardListener(dashboardView)
        dashboardView.drawAll(mHD)
        connectionViewModel.setState(ConnectionUiState.Polling)
    }

    override fun onServiceDisconnected(name: android.content.ComponentName?) {
        mHD?.removeHarleyDataDashboardListener(dashboardView)
        dashboardView.drawAll(null)
        super.onServiceDisconnected(name)
    }
}
