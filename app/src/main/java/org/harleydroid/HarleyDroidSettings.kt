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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar

class HarleyDroidSettings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
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
        toolbar.post { ClickEffects.strip(toolbar) }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun startActivity(intent: android.content.Intent) {
        super.startActivity(intent)
        suppressPendingTransition(opening = true)
    }

    override fun finish() {
        super.finish()
        suppressPendingTransition(opening = false)
    }

    private fun suppressPendingTransition(opening: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                if (opening) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE,
                0,
                0
            )
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

    class SettingsFragment : PreferenceFragmentCompat(),
        Preference.OnPreferenceChangeListener {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            fillBluetoothTable(findPreference("bluetoothid"))
            // Only prefs without app:useSimpleSummaryProvider — setting summary
            // on those throws IllegalStateException.
            listOf("bluetoothid", "reconnectdelay", "wifi_host", "wifi_port", ConnectionTransport.PREF_KEY).forEach { key ->
                findPreference<Preference>(key)?.onPreferenceChangeListener = this
                updateSummary(key)
            }
            findPreference<ListPreference>(ConnectionTransport.PREF_KEY)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { pref, newValue ->
                    onPreferenceChange(pref, newValue)
                    // After the framework persists the new value
                    Handler(Looper.getMainLooper()).post { updateConnectionVisibility() }
                    true
                }
            findPreference<SwitchPreferenceCompat>("emulator")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, _ ->
                    Handler(Looper.getMainLooper()).post { updateConnectionVisibility() }
                    true
                }
            findPreference<ListPreference>(AppLocale.PREF_KEY)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    AppLocale.apply(newValue as String)
                    true
                }
            findPreference<ListPreference>(AppTheme.PREF_KEY)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    // Persist first, then recreate so the new accent theme applies.
                    preferenceManager.sharedPreferences
                        ?.edit()
                        ?.putString(AppTheme.PREF_KEY, newValue as String)
                        ?.commit()
                    activity?.recreate()
                    false
                }
            updateConnectionVisibility()
        }

        override fun onResume() {
            super.onResume()
            fillBluetoothTable(findPreference("bluetoothid"))
            updateSummary("bluetoothid")
            updateSummary("wifi_host")
            updateSummary("wifi_port")
            updateConnectionVisibility()
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
                    preference.summary = when (preference.key) {
                        "reconnectdelay" -> "$newValue ${getText(R.string.pref_seconds)}"
                        else -> newValue?.toString()
                    }
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
                    pref.summary = when (key) {
                        "reconnectdelay" -> "${pref.text} ${getText(R.string.pref_seconds)}"
                        "wifi_host" -> pref.text ?: ConnectionTransport.DEFAULT_WIFI_HOST
                        "wifi_port" -> pref.text ?: ConnectionTransport.DEFAULT_WIFI_PORT.toString()
                        else -> pref.text
                    }
                }
            }
        }

        /** Show BT device vs WiFi host/port; HDI only makes sense on Bluetooth. */
        private fun updateConnectionVisibility() {
            val prefs = preferenceManager.sharedPreferences ?: return
            val sim = HarleyDroid.isEmulatorMode(prefs)
            val wifi = !sim && ConnectionTransport.isWifi(prefs)
            findPreference<ListPreference>(ConnectionTransport.PREF_KEY)?.let { transport ->
                transport.isVisible = !sim
                transport.summaryProvider = null
                transport.summary = transport.entry
                    ?: getString(R.string.pref_connectiontransport_summary)
            }
            findPreference<Preference>("bluetoothid")?.isVisible = !sim && !wifi
            findPreference<Preference>("wifi_host")?.isVisible = wifi
            findPreference<Preference>("wifi_port")?.isVisible = wifi
            findPreference<ListPreference>("interfacetype")?.let { iface ->
                iface.isVisible = !sim
                iface.isEnabled = !wifi
                if (wifi && iface.value == "hdi") {
                    iface.value = "elm327"
                    prefs.edit().putString("interfacetype", "elm327").apply()
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
                    val adapter =
                        (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
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
