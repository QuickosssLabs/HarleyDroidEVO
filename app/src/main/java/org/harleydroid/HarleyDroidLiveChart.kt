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

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Live sliding-window chart (~60 s) fed by dashboard telemetry.
 */
class HarleyDroidLiveChart : HarleyDroid(), HarleyDataDashboardListener {

    companion object {
        private const val MAX_SERIES = 2
        private const val REFRESH_MS = 100L
        private val LIVE_TYPES = listOf("RPM", "SPD", "ETP", "GER", "ODO", "FUL", "FGE")
    }

    private lateinit var chart: LogChartView
    private lateinit var readout: TextView
    private lateinit var legend: TextView
    private lateinit var chips: ChipGroup
    private val buffer = LiveSeriesBuffer()
    private val selected = ArrayList<String>(MAX_SERIES)
    private val uiHandler = Handler(Looper.getMainLooper())
    private var refreshScheduled = false
    private var listening = false
    private var unitMetric = true
    private var chipsReady = false

    private val refreshRunnable = Runnable {
        refreshScheduled = false
        redrawChart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupShell(R.string.live_chart_name)
        unitMetric = mUnitMetric
        val container = findViewById<ViewGroup>(R.id.content_container)
        layoutInflater.inflate(R.layout.activity_live_chart, container, true)
        chart = findViewById(R.id.live_chart)
        readout = findViewById(R.id.live_readout)
        legend = findViewById(R.id.live_legend)
        chips = findViewById(R.id.live_chips)
        legend.text = getString(R.string.logs_chart_legend_hint)
        readout.text = getString(R.string.live_chart_waiting)
        selected.clear()
        selected.add("RPM")
        selected.add("SPD")
        ensureChips()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.live_chart_menu, menu)
        stripToolbarClickEffects()
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (mService != null) {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_stop)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.disconnect_label)
        } else {
            menu.findItem(R.id.startstop_menu)?.setIcon(R.drawable.ic_menu_play_clip)
            menu.findItem(R.id.startstop_menu)?.setTitle(R.string.connect_label)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.startstop_menu -> {
                if (mService == null) requestConnect() else stopHDS()
                return true
            }
            R.id.dash_menu -> {
                startActivity(
                    android.content.Intent(this, HarleyDroidDashboard::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
                return true
            }
            R.id.logs_menu -> {
                startActivity(android.content.Intent(this, HarleyDroidLogs::class.java))
                return true
            }
            R.id.preferences_menu -> {
                startActivity(android.content.Intent(this, HarleyDroidSettings::class.java))
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
        stopListening()
    }

    override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
        super.onServiceConnected(name, service)
        // Prefer existing poll; do not interrupt active diagnostics send.
        if (mService?.isPolling != true && mService?.isSending != true) {
            mService?.startPoll()
            connectionViewModel.setState(ConnectionUiState.Polling)
        }
        startListening()
        invalidateOptionsMenu()
    }

    override fun onServiceDisconnected(name: android.content.ComponentName?) {
        stopListening()
        readout.text = getString(R.string.live_chart_waiting)
        invalidateOptionsMenu()
        super.onServiceDisconnected(name)
    }

    override fun onResume() {
        super.onResume()
        unitMetric = mUnitMetric
        if (mHD != null) startListening()
    }

    override fun onPause() {
        stopListening()
        uiHandler.removeCallbacks(refreshRunnable)
        refreshScheduled = false
        super.onPause()
    }

    private fun startListening() {
        if (listening) return
        val hd = mHD ?: return
        hd.addHarleyDataDashboardListener(this)
        listening = true
        // Seed with current values so the chart is not empty until next bus tick.
        buffer.append("RPM", hd.getRPM().toFloat())
        if (unitMetric) buffer.append("SPD", hd.getSpeedMetric().toFloat())
        else buffer.append("SPD", hd.getSpeedImperial().toFloat())
        if (unitMetric) buffer.append("ETP", hd.getEngineTempMetric().toFloat())
        else buffer.append("ETP", hd.getEngineTempImperial().toFloat())
        scheduleRefresh()
    }

    private fun stopListening() {
        if (!listening) return
        mHD?.removeHarleyDataDashboardListener(this)
        listening = false
    }

    private fun ensureChips() {
        if (chipsReady) return
        chips.removeAllViews()
        for (type in LIVE_TYPES) {
            val chip = Chip(this).apply {
                text = LogMetrics.chipLabel(type, unitMetric)
                isCheckable = true
                isChecked = type in selected
                tag = type
                isSoundEffectsEnabled = false
                setEnsureMinTouchTargetSize(true)
                setOnCheckedChangeListener { button, checked ->
                    onChipToggled(button.tag as String, checked)
                }
            }
            chips.addView(chip)
        }
        chipsReady = true
        updateLegend()
    }

    private fun onChipToggled(type: String, isChecked: Boolean) {
        if (isChecked) {
            if (type in selected) return
            if (selected.size >= MAX_SERIES) {
                val removed = selected.removeAt(0)
                uncheckChip(removed)
            }
            selected.add(type)
        } else {
            selected.remove(type)
            if (selected.isEmpty()) {
                selected.add(type)
                checkChip(type)
                return
            }
        }
        updateLegend()
        redrawChart()
    }

    private fun uncheckChip(type: String) {
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as? Chip ?: continue
            if (chip.tag == type && chip.isChecked) {
                chip.setOnCheckedChangeListener(null)
                chip.isChecked = false
                chip.setOnCheckedChangeListener { button, checked ->
                    onChipToggled(button.tag as String, checked)
                }
            }
        }
    }

    private fun checkChip(type: String) {
        for (i in 0 until chips.childCount) {
            val chip = chips.getChildAt(i) as? Chip ?: continue
            if (chip.tag == type && !chip.isChecked) {
                chip.setOnCheckedChangeListener(null)
                chip.isChecked = true
                chip.setOnCheckedChangeListener { button, checked ->
                    onChipToggled(button.tag as String, checked)
                }
            }
        }
    }

    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        uiHandler.postDelayed(refreshRunnable, REFRESH_MS)
    }

    private fun redrawChart() {
        val now = System.currentTimeMillis()
        val leftType = selected.getOrNull(0)
        val rightType = selected.getOrNull(1)
        val left = leftType?.let { buffer.snapshot(it, now) }
        val right = rightType?.let { buffer.snapshot(it, now) }
        val start = buffer.windowStart(now)
        chart.setData(start, now, left, right)

        val leftTxt = if (leftType != null)
            "${LogMetrics.shortLabel(leftType)} ${LogMetrics.formatValue(leftType, left?.points?.lastOrNull()?.value)}"
        else "—"
        val rightTxt = if (rightType != null)
            "${LogMetrics.shortLabel(rightType)} ${LogMetrics.formatValue(rightType, right?.points?.lastOrNull()?.value)}"
        else null
        readout.text = if (rightTxt != null)
            getString(R.string.live_chart_readout_dual, leftTxt, rightTxt)
        else
            getString(R.string.live_chart_readout_single, leftTxt)
    }

    private fun updateLegend() {
        val leftType = selected.getOrNull(0)
        val rightType = selected.getOrNull(1)
        legend.text = when {
            leftType != null && rightType != null ->
                getString(
                    R.string.logs_chart_legend_dual,
                    LogMetrics.chipLabel(leftType, unitMetric),
                    LogMetrics.chipLabel(rightType, unitMetric)
                )
            leftType != null ->
                getString(
                    R.string.logs_chart_legend_single,
                    LogMetrics.chipLabel(leftType, unitMetric)
                )
            else -> getString(R.string.logs_chart_legend_hint)
        }
    }

    // --- HarleyDataDashboardListener ---

    override fun onRPMChanged(rpm: Int) {
        buffer.append("RPM", rpm.toFloat())
        scheduleRefresh()
    }

    override fun onSpeedImperialChanged(speed: Int) {
        if (!unitMetric) {
            buffer.append("SPD", speed.toFloat())
            scheduleRefresh()
        }
    }

    override fun onSpeedMetricChanged(speed: Int) {
        if (unitMetric) {
            buffer.append("SPD", speed.toFloat())
            scheduleRefresh()
        }
    }

    override fun onEngineTempImperialChanged(engineTemp: Int) {
        if (!unitMetric) {
            buffer.append("ETP", engineTemp.toFloat())
            scheduleRefresh()
        }
    }

    override fun onEngineTempMetricChanged(engineTemp: Int) {
        if (unitMetric) {
            buffer.append("ETP", engineTemp.toFloat())
            scheduleRefresh()
        }
    }

    override fun onFuelGaugeChanged(full: Int, low: Boolean) {
        buffer.append("FGE", if (low) 0f else full.toFloat())
        scheduleRefresh()
    }

    override fun onTurnSignalsChanged(turnSignals: Int) {}
    override fun onNeutralChanged(neutral: Boolean) {}
    override fun onClutchChanged(clutch: Boolean) {}

    override fun onGearChanged(gear: Int) {
        if (gear >= 1) {
            buffer.append("GER", gear.toFloat())
            scheduleRefresh()
        }
    }

    override fun onCheckEngineChanged(checkEngine: Boolean) {}

    override fun onOdometerImperialChanged(odometer: Int) {
        if (!unitMetric) {
            buffer.append("ODO", odometer.toFloat())
            scheduleRefresh()
        }
    }

    override fun onOdometerMetricChanged(odometer: Int) {
        if (unitMetric) {
            buffer.append("ODO", odometer.toFloat())
            scheduleRefresh()
        }
    }

    override fun onFuelImperialChanged(fuel: Int) {
        if (!unitMetric) {
            buffer.append("FUL", fuel.toFloat())
            scheduleRefresh()
        }
    }

    override fun onFuelMetricChanged(fuel: Int) {
        if (unitMetric) {
            buffer.append("FUL", fuel.toFloat())
            scheduleRefresh()
        }
    }

    override fun onFuelAverageImperialChanged(fuel: Int) {}
    override fun onFuelAverageMetricChanged(fuel: Int) {}
    override fun onFuelInstantImperialChanged(fuel: Int) {}
    override fun onFuelInstantMetricChanged(fuel: Int) {}
}
