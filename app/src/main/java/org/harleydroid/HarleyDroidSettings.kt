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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar

class HarleyDroidSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val appBar = findViewById<View>(R.id.settings_app_bar)
        val content = findViewById<View>(R.id.settings_container)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = bars.left, bottom = bars.bottom, right = bars.right)
            windowInsets
        }
        ViewCompat.requestApplyInsets(appBar)
        ViewCompat.requestApplyInsets(content)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_name)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat(),
        Preference.OnPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            fillBluetoothTable(findPreference("bluetoothid"))
            // Only prefs without app:useSimpleSummaryProvider — setting summary
            // on those throws IllegalStateException.
            listOf("bluetoothid", "reconnectdelay").forEach { key ->
                findPreference<Preference>(key)?.onPreferenceChangeListener = this
                updateSummary(key)
            }
            findPreference<ListPreference>(AppLocale.PREF_KEY)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    AppLocale.apply(newValue as String)
                    true
                }
        }

        override fun onResume() {
            super.onResume()
            fillBluetoothTable(findPreference("bluetoothid"))
            updateSummary("bluetoothid")
        }

        override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
            when (preference) {
                is ListPreference -> {
                    // Clear provider if any, then show chosen entry label
                    preference.summaryProvider = null
                    val idx = preference.findIndexOfValue(newValue as String?)
                    preference.summary = if (idx >= 0) preference.entries[idx] else null
                }
                is EditTextPreference -> {
                    preference.summaryProvider = null
                    preference.summary = "$newValue ${getText(R.string.pref_seconds)}"
                }
            }
            return true
        }

        private fun updateSummary(key: String) {
            when (val pref = findPreference<Preference>(key)) {
                is ListPreference -> {
                    pref.summaryProvider = null
                    pref.summary = pref.entry
                }
                is EditTextPreference -> {
                    pref.summaryProvider = null
                    pref.summary = "${pref.text} ${getText(R.string.pref_seconds)}"
                }
            }
        }

        private fun fillBluetoothTable(btlist: ListPreference?) {
            btlist ?: return
            val devices = ArrayList<CharSequence>()
            val addresses = ArrayList<CharSequence>()
            val prefs = preferenceManager.sharedPreferences
            if (prefs != null && HarleyDroid.isEmulatorMode(prefs)) {
                for (i in 0..4) {
                    devices.add("$i:$i:$i:$i - Device $i")
                    addresses.add("$i:$i:$i:$i")
                }
            } else {
                try {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    val paired: Set<BluetoothDevice>? = adapter?.bondedDevices
                    paired?.forEach { dev ->
                        devices.add("${dev.address} - ${dev.name}")
                        addresses.add(dev.address)
                    }
                } catch (_: SecurityException) {
                }
            }
            if (devices.isEmpty()) {
                devices.add(getString(R.string.pref_bluetoothdev_none))
                addresses.add("")
            }
            btlist.entryValues = addresses.toTypedArray()
            btlist.entries = devices.toTypedArray()
        }
    }
}
