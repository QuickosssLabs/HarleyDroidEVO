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
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class HarleyDroidLogChart : AppCompatActivity(), LogChartView.ScrubListener {

    companion object {
        const val EXTRA_PATH = "log_path"
        private const val MAX_SERIES = 2
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var chart: LogChartView
    private lateinit var readout: TextView
    private lateinit var legend: TextView
    private lateinit var chips: ChipGroup
    private lateinit var loading: View
    private var sessionStartMs = 0L
    private var summaryText: String = ""
    private var parsed: ParsedLog? = null
    private var unitMetric = true
    /** Selected types in axis order (left solid, right dashed). */
    private val selected = ArrayList<String>(MAX_SERIES)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_chart)
        unitMetric = LogMetrics.isMetric(this)

        val appBar = findViewById<View>(R.id.chart_app_bar)
        val content = findViewById<View>(R.id.chart_content)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, bottom = bars.bottom, right = bars.right)
            insets
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, R.string.logs_open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val file = File(path)
        if (!file.isFile) {
            Toast.makeText(this, R.string.logs_open_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title =
            file.name.removePrefix("harley-").removeSuffix(".log.gz")
        toolbar.post { ClickEffects.strip(toolbar) }

        chart = findViewById(R.id.log_chart)
        readout = findViewById(R.id.chart_readout)
        legend = findViewById(R.id.chart_legend)
        chips = findViewById(R.id.chart_chips)
        loading = findViewById(R.id.chart_loading)
        chart.scrubListener = this

        legend.text = getString(R.string.logs_chart_legend_hint)
        readout.text = getString(R.string.logs_chart_hint)
        loading.visibility = View.VISIBLE

        executor.execute {
            val result = try {
                LogParser.parse(file, LogParser.CHART_TYPES)
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                loading.visibility = View.GONE
                if (result == null || result.series.isEmpty()) {
                    readout.text = getString(R.string.logs_chart_empty)
                    return@runOnUiThread
                }
                parsed = result
                sessionStartMs = result.startMs
                setupChips(result)
                applySelection()
            }
        }
    }

    private fun setupChips(log: ParsedLog) {
        chips.removeAllViews()
        selected.clear()
        val available = LogMetrics.SELECTABLE.filter { log.series.containsKey(it) }
        // Default: RPM + SPD when present, else first two available
        val defaults = buildList {
            if ("RPM" in available) add("RPM")
            if ("SPD" in available) add("SPD")
            for (t in available) {
                if (size >= MAX_SERIES) break
                if (t !in this) add(t)
            }
        }
        selected.addAll(defaults.take(MAX_SERIES))

        for (type in available) {
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
    }

    private fun onChipToggled(type: String, isChecked: Boolean) {
        if (isChecked) {
            if (type in selected) return
            if (selected.size >= MAX_SERIES) {
                // Drop oldest selection and uncheck its chip
                val removed = selected.removeAt(0)
                uncheckChip(removed)
            }
            selected.add(type)
        } else {
            selected.remove(type)
            // Keep at least one series if possible
            if (selected.isEmpty()) {
                val fallback = parsed?.series?.keys?.firstOrNull()
                if (fallback != null) {
                    selected.add(fallback)
                    checkChip(fallback)
                    applySelection()
                    return
                }
            }
        }
        applySelection()
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

    private fun applySelection() {
        val log = parsed ?: return
        val leftType = selected.getOrNull(0)
        val rightType = selected.getOrNull(1)
        val left = leftType?.let { log.series[it] }
        val right = rightType?.let { log.series[it] }
        chart.setData(log.startMs, log.endMs, left, right)

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

        summaryText = buildSummary(log)
        readout.text = summaryText
    }

    private fun buildSummary(log: ParsedLog): String {
        val rpmMax = log.series["RPM"]?.max?.let { LogMetrics.formatValue("RPM", it) } ?: "—"
        val spdMax = log.series["SPD"]?.max?.let { LogMetrics.formatValue("SPD", it) } ?: "—"
        val etpMax = log.series["ETP"]?.max?.let { LogMetrics.formatValue("ETP", it) } ?: "—"
        val trip = LogMetrics.formatTrip(LogMetrics.tripDistance(log.series["ODO"]), unitMetric)
        return getString(
            R.string.logs_chart_summary_full,
            formatDuration(log.durationMs),
            rpmMax,
            spdMax,
            etpMax,
            trip
        )
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onScrub(timeMs: Long, leftValue: Float?, rightValue: Float?) {
        val elapsed = formatDuration((timeMs - sessionStartMs).coerceAtLeast(0L))
        val leftType = selected.getOrNull(0)
        val rightType = selected.getOrNull(1)
        val leftTxt = if (leftType != null)
            "${LogMetrics.shortLabel(leftType)} ${LogMetrics.formatValue(leftType, leftValue)}"
        else "—"
        val rightTxt = if (rightType != null)
            "${LogMetrics.shortLabel(rightType)} ${LogMetrics.formatValue(rightType, rightValue)}"
        else null
        readout.text = if (rightTxt != null)
            getString(R.string.logs_chart_scrub_dual, elapsed, leftTxt, rightTxt)
        else
            getString(R.string.logs_chart_scrub_single, elapsed, leftTxt)
    }

    override fun onScrubEnded() {
        if (summaryText.isNotEmpty()) readout.text = summaryText
    }

    private fun formatDuration(ms: Long): String {
        val sec = (ms / 1000L).coerceAtLeast(0L)
        val m = sec / 60
        val s = sec % 60
        return "%d:%02d".format(Locale.US, m, s)
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
