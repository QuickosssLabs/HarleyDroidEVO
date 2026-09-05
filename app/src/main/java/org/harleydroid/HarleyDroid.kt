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

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference

abstract class HarleyDroid : AppCompatActivity(), ServiceConnection, Eula.OnEulaAgreedTo {

    companion object {
        const val STATUS_NONE = HarleyStatus.NONE
        const val STATUS_CONNECTING = HarleyStatus.CONNECTING
        const val STATUS_CONNECTED = HarleyStatus.CONNECTED
        const val STATUS_ERROR = HarleyStatus.ERROR
        const val STATUS_ERRORAT = HarleyStatus.ERRORAT
        const val STATUS_NODATA = HarleyStatus.NODATA
        const val STATUS_TOOMANYERRORS = HarleyStatus.TOOMANYERRORS
        const val STATUS_AUTORECON = HarleyStatus.AUTORECON
        const val STATUS_CAN_DIAG_UNSUPPORTED = HarleyStatus.CAN_DIAG_UNSUPPORTED

        /** Runtime simulation mode (Preferences → Simulation). */
        @JvmStatic
        fun isEmulatorMode(prefs: SharedPreferences): Boolean =
            prefs.getBoolean("emulator", false)
    }

    protected lateinit var mPrefs: SharedPreferences
    private var mInterfaceType: String? = null
    private var mBusProtocol: String = BusProtocol.J1850
    private var mBluetoothAdapter: BluetoothAdapter? = null
    protected var mBluetoothID: String? = null
    private var mEmulator = false
    private var mWarnedHdiCan = false
    private var mAutoConnect = false
    private var mAutoReconnect = false
    private var mReconnectDelay = "30"
    private var mLogging = false
    private var mGPS = false
    private var mLogRaw = false
    private var mLogUnknown = false
    private var mScreenOn = false
    protected var mService: HarleyDroidService? = null
    protected var mUnitMetric = false
    protected var mOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    protected var mHD: HarleyData? = null
    protected lateinit var mHandler: Handler
    private lateinit var mToast: Toast
    private var mPermissionsReady = false
    private var pendingConnectAfterPerms = false
    protected lateinit var connectionViewModel: ConnectionViewModel
    protected var toolbar: MaterialToolbar? = null
    private var toolbarTitle: TextView? = null
    private var statusDot: View? = null

    protected fun isEmulatorMode(): Boolean =
        if (::mPrefs.isInitialized) isEmulatorMode(mPrefs) else false

    private val btEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            mToast.setText(R.string.toast_errorenablebluetooth)
            mToast.show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val needBt = !isEmulatorMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val btOk = if (needBt) {
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        if (!btOk && pendingConnectAfterPerms) {
            mToast.setText(R.string.toast_errorenablebluetooth)
            mToast.show()
            pendingConnectAfterPerms = false
            return@registerForActivityResult
        }
        mPermissionsReady = true
        if (!isEmulatorMode()) onBluetoothReady()
        if (pendingConnectAfterPerms) {
            pendingConnectAfterPerms = false
            startHDS()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectionViewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]
        mHandler = HarleyDroidHandler(this)
        mToast = Toast.makeText(this, "", Toast.LENGTH_LONG)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mEmulator = isEmulatorMode(mPrefs)
        mAutoConnect = true
        if (Eula.show(this, false)) onEulaAgreedTo()
    }

    protected fun setupShell(titleRes: Int) {
        setContentView(R.layout.activity_shell)
        enterImmersiveMode()
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbar_title)
        statusDot = findViewById(R.id.status_dot)
        applyEdgeToEdgeInsets(
            findViewById(R.id.app_bar),
            findViewById(R.id.content_container)
        )
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarTitle?.setText(titleRes)
        connectionViewModel.uiState.observe(this) { state ->
            updateStatusDot(state)
        }
    }

    /** Fullscreen dashboard: hide system bars; swipe from edge to reveal briefly. */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Pad only for display cutout / gesture edges — system bars are hidden. */
    protected fun applyEdgeToEdgeInsets(appBar: View, content: View) {
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, windowInsets ->
            val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = cutout.left, top = cutout.top, right = cutout.right)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = cutout.left, bottom = cutout.bottom, right = cutout.right)
            windowInsets
        }
        ViewCompat.requestApplyInsets(appBar)
        ViewCompat.requestApplyInsets(content)
    }

    private fun updateStatusDot(state: ConnectionUiState) {
        val dot = statusDot ?: return
        val (colorRes, labelRes, show) = when (state) {
            is ConnectionUiState.Idle ->
                Triple(R.color.hd_status_idle, R.string.status_idle, false)
            is ConnectionUiState.Connecting ->
                Triple(R.color.hd_status_busy, R.string.status_connecting, true)
            is ConnectionUiState.Connected ->
                Triple(R.color.hd_status_ok, R.string.status_connected, true)
            is ConnectionUiState.Polling ->
                Triple(R.color.hd_status_ok, R.string.status_polling, true)
            is ConnectionUiState.Diagnostics ->
                Triple(R.color.hd_status_ok, R.string.status_diagnostics, true)
            is ConnectionUiState.Reconnecting ->
                Triple(R.color.hd_status_busy, R.string.status_reconnect, true)
            is ConnectionUiState.Error ->
                Triple(R.color.hd_status_error, R.string.status_error, true)
        }
        if (!show) {
            dot.visibility = View.GONE
            return
        }
        dot.visibility = View.VISIBLE
        dot.contentDescription = when (state) {
            is ConnectionUiState.Error -> statusMessage(state.status)
            else -> getString(labelRes)
        }
        dot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun statusMessage(status: Int): String = when (status) {
        STATUS_ERRORAT -> getString(R.string.status_error_at)
        STATUS_NODATA -> getString(R.string.status_nodata)
        STATUS_TOOMANYERRORS -> getString(R.string.status_too_many_errors)
        else -> getString(R.string.status_error)
    }

    override fun onEulaAgreedTo() {
        // Permissions deferred until connect / GPS logging
        mPermissionsReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (mPermissionsReady) onBluetoothReady()
    }

    private fun onBluetoothReady() {
        if (isEmulatorMode()) return
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null) {
            mToast.setText(R.string.toast_nobluetooth)
            mToast.show()
            return
        }
        try {
            if (mBluetoothAdapter?.isEnabled == false) {
                btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } catch (_: SecurityException) {
            mToast.setText(R.string.toast_errorenablebluetooth)
            mToast.show()
        }
    }

    fun ensureConnectPermissions(thenConnect: Boolean = true) {
        val needed = mutableListOf<String>()
        if (!isEmulatorMode()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED
                ) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED
                ) needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (mLogging && mGPS) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            pendingConnectAfterPerms = thenConnect
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            mPermissionsReady = true
            if (!isEmulatorMode()) onBluetoothReady()
            if (thenConnect) startHDS()
        }
    }

    override fun onStart() {
        super.onStart()
        mEmulator = isEmulatorMode(mPrefs)
        mInterfaceType = mPrefs.getString("interfacetype", null)
        mBusProtocol = BusProtocol.fromPrefs(mPrefs)
        mBluetoothID = mPrefs.getString("bluetoothid", null)
        mAutoConnect = mAutoConnect && mPrefs.getBoolean("autoconnect", false)
        mAutoReconnect = mPrefs.getBoolean("autoreconnect", false)
        mReconnectDelay = mPrefs.getString("reconnectdelay", "30") ?: "30"
        mOrientation = when (mPrefs.getString("orientation", "auto")) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "reversePortrait" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "reverseLandscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        requestedOrientation = mOrientation
        mLogging = mPrefs.getBoolean("logging", false)
        mGPS = mPrefs.getBoolean("gps", false)
        mLogRaw = mPrefs.getBoolean("lograw", false)
        mLogUnknown = mPrefs.getBoolean("logunknown", false)
        mScreenOn = mPrefs.getBoolean("screenon", false)
        mUnitMetric = mPrefs.getString("unit", "metric") == "metric"
        invalidateOptionsMenu()
        bindService(Intent(this, HarleyDroidService::class.java), this, 0)
        if (mAutoConnect && (mBluetoothID != null || mEmulator) && mService == null) {
            mAutoConnect = false
            ensureConnectPermissions(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && toolbar != null) enterImmersiveMode()
    }

    override fun onStop() {
        super.onStop()
        try {
            unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        mService = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mService = (service as HarleyDroidService.HarleyDroidServiceBinder).service
        mService?.setHandler(mHandler)
        mHD = mService?.harleyData
        if (BusProtocol.isCan(mBusProtocol) && mInterfaceType == "hdi" && !mWarnedHdiCan) {
            mWarnedHdiCan = true
            snack(R.string.toast_hdi_can_forced_elm)
        }
        if (!isEmulatorMode()) {
            val adapter = mBluetoothAdapter ?: return
            if (mBluetoothID == null) return
            try {
                mService?.setInterfaceType(
                    mInterfaceType,
                    mBusProtocol,
                    adapter.getRemoteDevice(mBluetoothID)
                )
            } catch (e: SecurityException) {
                mToast.setText(R.string.toast_errorenablebluetooth)
                mToast.show()
                return
            }
        } else {
            mService?.setInterfaceType(mInterfaceType, mBusProtocol, null)
        }
        mService?.setLogging(mLogging, mUnitMetric, mGPS, mLogRaw, mLogUnknown)
        mService?.setAutoReconnect(mAutoReconnect, mReconnectDelay.toInt())
        invalidateOptionsMenu()
        if (mScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        try {
            unbindService(this)
        } catch (_: IllegalArgumentException) {
        }
        mService = null
        mHD = null
        bindService(Intent(this, HarleyDroidService::class.java), this, 0)
        invalidateOptionsMenu()
        if (mScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    open fun handleMessage(msg: Message) {
        when (msg.what) {
            STATUS_CONNECTING -> {
                connectionViewModel.setState(ConnectionUiState.Connecting)
                snack(R.string.toast_connecting)
            }
            STATUS_ERROR -> {
                connectionViewModel.setState(ConnectionUiState.Error(STATUS_ERROR))
                snack(R.string.toast_errorconnecting)
            }
            STATUS_ERRORAT -> {
                connectionViewModel.setState(ConnectionUiState.Error(STATUS_ERRORAT))
                snack(R.string.toast_errorat)
            }
            STATUS_CONNECTED -> {
                connectionViewModel.setState(ConnectionUiState.Connected)
                snack(R.string.toast_connected)
            }
            STATUS_NODATA -> {
                connectionViewModel.setState(ConnectionUiState.Error(STATUS_NODATA))
                snack(R.string.toast_nodata)
            }
            STATUS_TOOMANYERRORS -> {
                connectionViewModel.setState(ConnectionUiState.Error(STATUS_TOOMANYERRORS))
                snack(R.string.toast_toomanyerrors)
            }
            STATUS_AUTORECON -> {
                connectionViewModel.setState(ConnectionUiState.Reconnecting)
                snack(getString(R.string.toast_autorecon, mReconnectDelay))
            }
            STATUS_CAN_DIAG_UNSUPPORTED -> {
                snack(R.string.toast_can_diag_unsupported)
            }
            STATUS_NONE -> connectionViewModel.setState(ConnectionUiState.Idle)
        }
        invalidateOptionsMenu()
    }

    protected fun snack(res: Int) {
        val root = findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(root, res, Snackbar.LENGTH_SHORT).show()
    }

    protected fun snack(msg: String) {
        val root = findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT).show()
    }

    fun startHDS() {
        if (mService == null) {
            startService(Intent(this, HarleyDroidService::class.java))
            bindService(Intent(this, HarleyDroidService::class.java), this, 0)
        }
    }

    fun stopHDS() {
        mService?.disconnect()
        mService = null
        connectionViewModel.setState(ConnectionUiState.Idle)
    }

    class HarleyDroidHandler(activity: HarleyDroid) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            ref.get()?.handleMessage(msg)
        }
    }
}
