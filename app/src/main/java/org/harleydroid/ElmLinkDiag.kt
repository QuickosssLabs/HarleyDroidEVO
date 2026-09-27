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

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

/** Runs [ElmLinkProbe] and shows a Material dialog with verdict + raw AT log. */
object ElmLinkDiag {

    private val executor = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun show(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.elm_diag_title)
            .setMessage(R.string.elm_diag_running)
            .setCancelable(false)
            .create()
        progress.show()

        executor.execute {
            // Let a previous capture release the ELM TCP/RFCOMM session.
            try {
                Thread.sleep(600)
            } catch (_: InterruptedException) {
            }
            val result = try {
                ElmLinkProbe.run(activity.applicationContext, prefs)
            } catch (e: Exception) {
                ElmLinkProbe.Result(
                    verdict = ElmLinkProbe.Verdict.LINK_FAIL,
                    endpoint = "?",
                    busProtocol = BusProtocol.fromPrefs(prefs),
                    elmBanner = null,
                    voltageText = null,
                    voltageVolts = null,
                    protocolDesc = null,
                    atmaLineCount = 0,
                    atmaSampleLines = emptyList(),
                    log = "Unexpected error: ${e.message}",
                )
            }
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                try {
                    progress.dismiss()
                } catch (_: Exception) {
                }
                showResult(activity, result)
            }
        }
    }

    private fun showResult(activity: Activity, result: ElmLinkProbe.Result) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_elm_link_diag, null)
        val verdictView = view.findViewById<TextView>(R.id.elm_diag_verdict)
        val summaryView = view.findViewById<TextView>(R.id.elm_diag_summary)
        val logView = view.findViewById<TextView>(R.id.elm_diag_log)

        verdictView.setText(verdictTitle(result.verdict))
        summaryView.text = buildSummary(activity, result)
        logView.text = result.log

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.elm_diag_title)
            .setView(view)
            .setPositiveButton(R.string.elm_diag_ok, null)
            .setNeutralButton(R.string.elm_diag_share) { _, _ ->
                share(activity, result)
            }
            .create()
        dialog.show()
    }

    private fun verdictTitle(v: ElmLinkProbe.Verdict): Int = when (v) {
        ElmLinkProbe.Verdict.EMULATOR -> R.string.elm_diag_verdict_emulator
        ElmLinkProbe.Verdict.NOT_CONFIGURED -> R.string.elm_diag_verdict_not_configured
        ElmLinkProbe.Verdict.LINK_FAIL -> R.string.elm_diag_verdict_link_fail
        ElmLinkProbe.Verdict.AT_FAIL -> R.string.elm_diag_verdict_at_fail
        ElmLinkProbe.Verdict.POWER_LOW -> R.string.elm_diag_verdict_power_low
        ElmLinkProbe.Verdict.BUS_SILENT -> R.string.elm_diag_verdict_bus_silent
        ElmLinkProbe.Verdict.BUS_NOISE -> R.string.elm_diag_verdict_bus_noise
        ElmLinkProbe.Verdict.BUS_OK -> R.string.elm_diag_verdict_bus_ok
    }

    private fun buildSummary(activity: Activity, r: ElmLinkProbe.Result): String {
        val busLabel = if (BusProtocol.isCan(r.busProtocol)) {
            activity.getString(R.string.elm_diag_bus_can)
        } else {
            activity.getString(R.string.elm_diag_bus_j1850)
        }
        val sb = StringBuilder()
        sb.append(activity.getString(R.string.elm_diag_endpoint, r.endpoint)).append('\n')
        sb.append(activity.getString(R.string.elm_diag_bus, busLabel)).append('\n')
        if (!r.elmBanner.isNullOrBlank()) {
            sb.append(activity.getString(R.string.elm_diag_banner, r.elmBanner)).append('\n')
        }
        val volt = r.voltageText ?: activity.getString(R.string.elm_diag_unknown)
        sb.append(activity.getString(R.string.elm_diag_voltage, volt)).append('\n')
        val proto = r.protocolDesc ?: activity.getString(R.string.elm_diag_unknown)
        sb.append(activity.getString(R.string.elm_diag_protocol, proto)).append('\n')
        sb.append(activity.getString(R.string.elm_diag_atma_count, r.atmaLineCount)).append('\n')
        sb.append('\n')
        sb.append(activity.getString(adviceRes(r.verdict)))
        return sb.toString()
    }

    private fun adviceRes(v: ElmLinkProbe.Verdict): Int = when (v) {
        ElmLinkProbe.Verdict.EMULATOR -> R.string.elm_diag_advice_emulator
        ElmLinkProbe.Verdict.NOT_CONFIGURED -> R.string.elm_diag_advice_not_configured
        ElmLinkProbe.Verdict.LINK_FAIL -> R.string.elm_diag_advice_link_fail
        ElmLinkProbe.Verdict.AT_FAIL -> R.string.elm_diag_advice_at_fail
        ElmLinkProbe.Verdict.POWER_LOW -> R.string.elm_diag_advice_power_low
        ElmLinkProbe.Verdict.BUS_SILENT -> R.string.elm_diag_advice_bus_silent
        ElmLinkProbe.Verdict.BUS_NOISE -> R.string.elm_diag_advice_bus_noise
        ElmLinkProbe.Verdict.BUS_OK -> R.string.elm_diag_advice_bus_ok
    }

    private fun share(activity: Activity, r: ElmLinkProbe.Result) {
        val text = buildString {
            append(activity.getString(verdictTitle(r.verdict))).append("\n\n")
            append(buildSummary(activity, r)).append("\n\n")
            append(r.log)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.elm_diag_title))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(
            Intent.createChooser(intent, activity.getString(R.string.elm_diag_share))
        )
    }
}
