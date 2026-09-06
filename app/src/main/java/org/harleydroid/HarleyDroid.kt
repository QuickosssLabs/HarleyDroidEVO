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
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
    private var mUseWifi = false
    private var mWifiHost: String = ConnectionTransport.DEFAULT_WIFI_HOST
    private var mWifiPort: Int = ConnectionTransport.DEFAULT_WIFI_PORT
    private var mEmulator = false
    private var mWarnedHdiCan = false
    private var mWarnedHdiWifi = false
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
    private var toolbarAccent: View? = null
    private var statusDot: View? = null
    private var statusChip: View? = null
    private var statusLabel: android.widget.TextView? = null
    private var simBadge: TextView? = null
    private var appliedThemeId: String? = null

    protected fun isEmulatorMode(): Boolean =
        if (::mPrefs.isInitialized) isEmulatorMode(mPrefs) else false

    /** True when Simulation is on, or a Bluetooth / WiFi endpoint is configured. */
    protected fun isConnectReady(): Boolean =
        isEmulatorMode() ||
            (mUseWifi && mWifiHost.isNotBlank()) ||
            (!mUseWifi && !mBluetoothID.isNullOrBlank())

    private fun needsBluetooth(): Boolean {
        if (isEmulatorMode()) return false
        return if (::mPrefs.isInitialized) !ConnectionTransport.isWifi(mPrefs) else !mUseWifi
    }

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
        val needBt = needsBluetooth() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
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
        if (needsBluetooth()) onBluetoothReady()
        if (pendingConnectAfterPerms) {
            pendingConnectAfterPerms = false
            startHDS()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppTheme.apply(this)
        super.onCreate(savedInstanceState)
        connectionViewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]
        mHandler = HarleyDroidHandler(this)
        mToast = Toast.makeText(this, "", Toast.LENGTH_LONG)
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mEmulator = isEmulatorMode(mPrefs)
        mAutoConnect = true
        appliedThemeId = AppTheme.currentId(mPrefs)
        if (Eula.show(this, false)) onEulaAgreedTo()
    }

    protected fun setupShell(titleRes: Int) {
        setContentView(R.layout.activity_shell)
        enterImmersiveMode()
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbar_title)
        toolbarAccent = findViewById(R.id.toolbar_accent)
        statusDot = findViewById(R.id.status_dot)
        statusChip = findViewById(R.id.status_chip)
        statusLabel = findViewById(R.id.status_label)
        simBadge = findViewById(R.id.sim_badge)
        simBadge?.isSoundEffectsEnabled = false
        statusChip?.isSoundEffectsEnabled = false
        simBadge?.setOnClickListener {
            startActivity(Intent(this, HarleyDroidSettings::class.java))
        }
        statusChip?.setOnClickListener { onStatusChipClicked() }
        applyEdgeToEdgeInsets(
            findViewById(R.id.app_bar),
            findViewById(R.id.content_container)
        )
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbarTitle?.setText(titleRes)
        updateToolbarTitleVisibility()
        updateSimulationBadge()
        toolbar?.post { ClickEffects.strip(toolbar) }
        connectionViewModel.uiState.observe(this) { state ->
            updateStatusDot(state)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToolbarTitleVisibility(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
    }

    /** Portrait: hide app name so status / SIM badges stay readable. */
    private fun updateToolbarTitleVisibility(
        portrait: Boolean = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    ) {
        toolbarTitle?.visibility = if (portrait) View.GONE else View.VISIBLE
        // Bring status chip closer to the logo when the title is hidden.
        statusChip?.let { chip ->
            val lp = chip.layoutParams
            if (lp is android.view.ViewGroup.MarginLayoutParams) {
                lp.marginStart = resources.getDimensionPixelSize(
                    if (portrait) R.dimen.space_8 else R.dimen.space_12
                )
                chip.layoutParams = lp
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        // Do not re-strip the whole toolbar tree here — that ran on every status tick
        // and made taps feel laggy (users re-tap → several system clicks).
        return super.onPrepareOptionsMenu(menu)
    }

    /** Call once after inflating an options menu (not on every prepare). */
    protected fun stripToolbarClickEffects() {
        toolbar?.post { ClickEffects.strip(toolbar) }
    }

    /**
     * Drop HarleyData UI listeners before unbind. onServiceDisconnected is only for
     * crashed services — without this, each Settings/Diag round-trip stacked listeners.
     */
    protected open fun detachHarleyDataListeners() {}

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        suppressPendingTransition(opening = true)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(intent, options)
        suppressPendingTransition(opening = true)
    }

    override fun finish() {
        super.finish()
        suppressPendingTransition(opening = false)
    }

    /** Instant screen changes (no slide) without using the deprecated transition API on 34+. */
    private fun suppressPendingTransition(opening: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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

    /** Status pill: guide setup or explain how to connect — never silent. */
    private fun onStatusChipClicked() {
        when (connectionViewModel.uiState.value) {
            is ConnectionUiState.Idle,
            is ConnectionUiState.Error -> {
                if (!isConnectReady()) {
                    openSettingsForSetup()
                } else {
                    snack(R.string.snack_tap_play_to_connect)
                }
            }
            else -> {
                // Connected / busy — chip is informational only
            }
        }
    }

    protected fun openSettingsForSetup() {
        startActivity(Intent(this, HarleyDroidSettings::class.java))
    }

    /**
     * Connect entry point from ▶. Always gives feedback: either starts connect
     * or explains that a Bluetooth / WiFi interface or Simulation must be configured.
     */
    protected fun requestConnect() {
        if (!isConnectReady()) {
            snackSetupNeeded()
            return
        }
        ensureConnectPermissions(true)
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
        val chip = statusChip ?: return
        val dot = statusDot ?: return
        val needsSetup = state is ConnectionUiState.Idle && !isConnectReady()
        val (colorRes, labelRes) = when {
            needsSetup ->
                R.color.hd_status_busy to R.string.status_setup_needed
            state is ConnectionUiState.Idle ->
                R.color.hd_status_idle to R.string.status_idle
            state is ConnectionUiState.Connecting ->
                R.color.hd_status_busy to R.string.status_connecting
            state is ConnectionUiState.Connected ->
                R.color.hd_status_link to R.string.status_connected
            state is ConnectionUiState.Polling ->
                R.color.hd_status_link to R.string.status_polling
            state is ConnectionUiState.EngineOff ->
                R.color.hd_status_busy to R.string.status_engine_off
            state is ConnectionUiState.Running ->
                R.color.hd_status_ok to R.string.status_running
            state is ConnectionUiState.Diagnostics ->
                R.color.hd_status_link to R.string.status_diagnostics
            state is ConnectionUiState.Reconnecting ->
                R.color.hd_status_busy to R.string.status_reconnect
            state is ConnectionUiState.Error ->
                R.color.hd_status_error to R.string.status_error
            else ->
                R.color.hd_status_idle to R.string.status_idle
        }
        val label = when (state) {
            is ConnectionUiState.Error -> statusMessage(state.status)
            else -> getString(labelRes)
        }
        chip.visibility = View.VISIBLE
        statusLabel?.text = label
        chip.contentDescription = when {
            needsSetup -> getString(R.string.status_setup_needed_desc)
            state is ConnectionUiState.Idle || state is ConnectionUiState.Error ->
                getString(R.string.status_idle_desc)
            else -> label
        }
        dot.contentDescription = label
        dot.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun updateSimulationBadge() {
        val sim = isEmulatorMode()
        simBadge?.visibility = if (sim) View.VISIBLE else View.GONE
        toolbarAccent?.setBackgroundColor(
            if (sim) ContextCompat.getColor(this, R.color.hd_status_busy)
            else AppTheme.primary(this)
        )
    }

    private fun statusMessage(status: Int): String = when (status) {
        STATUS_ERRORAT -> getString(R.string.status_error_at)
        STATUS_NODATA -> getString(R.string.status_nodata)
        STATUS_TOOMANYERRORS -> getString(R.string.status_too_many_errors)
        else -> getString(R.string.status_error)
    }

    override fun onEulaAgreedTo() {
        // Permissions deferred until connect / GPS logging
        mPermissionsReady = !needsBluetooth() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        if (mPermissionsReady && needsBluetooth()) onBluetoothReady()
    }

    private fun onBluetoothReady() {
        if (!needsBluetooth()) return
        mBluetoothAdapter =
            (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
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
        if (needsBluetooth() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (!isEmulatorMode() && mLogging && mGPS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
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
            if (needsBluetooth()) onBluetoothReady()
            if (thenConnect) startHDS()
        }
    }

    override fun onStart() {
        super.onStart()
        val themeId = AppTheme.currentId(mPrefs)
        if (appliedThemeId != null && appliedThemeId != themeId) {
            recreate()
            return
        }
        appliedThemeId = themeId
        mEmulator = isEmulatorMode(mPrefs)
        mInterfaceType = mPrefs.getString("interfacetype", null)
        mBusProtocol = BusProtocol.fromPrefs(mPrefs)
        mUseWifi = ConnectionTransport.isWifi(mPrefs)
        mWifiHost = ConnectionTransport.wifiHost(mPrefs)
        mWifiPort = ConnectionTransport.wifiPort(mPrefs)
        mBluetoothID = mPrefs.getString("bluetoothid", null)?.takeIf { it.isNotBlank() }
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
        updateSimulationBadge()
        invalidateOptionsMenu()
        connectionViewModel.uiState.value?.let { updateStatusDot(it) }
        bindService(Intent(this, HarleyDroidService::class.java), this, 0)
        if (mAutoConnect && isConnectReady() && mService == null) {
            mAutoConnect = false
            ensureConnectPermissions(true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && toolbar != null) enterImmersiveMode()
    }

    override fun onStop() {
        detachHarleyDataListeners()
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
        if (mUseWifi && mInterfaceType == "hdi" && !mWarnedHdiWifi) {
            mWarnedHdiWifi = true
            snack(R.string.toast_hdi_wifi_forced_elm)
        }
        if (!isEmulatorMode()) {
            if (!isConnectReady()) {
                snackSetupNeeded()
                return
            }
            if (mUseWifi) {
                mService?.setInterfaceType(
                    mInterfaceType,
                    mBusProtocol,
                    null,
                    true,
                    mWifiHost,
                    mWifiPort
                )
            } else {
                val deviceId = mBluetoothID ?: return
                val adapter = mBluetoothAdapter ?: return
                try {
                    mService?.setInterfaceType(
                        mInterfaceType,
                        mBusProtocol,
                        adapter.getRemoteDevice(deviceId)
                    )
                } catch (e: SecurityException) {
                    mToast.setText(R.string.toast_errorenablebluetooth)
                    mToast.show()
                    return
                }
            }
        } else {
            mService?.setInterfaceType(mInterfaceType, mBusProtocol, null)
        }
        mService?.setLogging(mLogging, mUnitMetric, mGPS, mLogRaw, mLogUnknown)
        mService?.setAutoReconnect(mAutoReconnect, mReconnectDelay.toInt())
        invalidateOptionsMenu()
        stripToolbarClickEffects()
        if (mScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        detachHarleyDataListeners()
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
        // Menu icons only depend on mService bound — not on every status snack.
    }

    protected fun snack(res: Int) {
        val root = findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(root, res, Snackbar.LENGTH_SHORT)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .show()
    }

    protected fun snack(msg: String) {
        val root = findViewById<android.view.View>(android.R.id.content)
        Snackbar.make(root, msg, Snackbar.LENGTH_SHORT)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .show()
    }

    protected fun snackSetupNeeded() {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, R.string.snack_need_device, Snackbar.LENGTH_LONG)
            .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
            .setAction(R.string.snack_open_settings) { openSettingsForSetup() }
            .show()
    }

    fun startHDS() {
        if (mService == null) {
            startService(Intent(this, HarleyDroidService::class.java))
            bindService(Intent(this, HarleyDroidService::class.java), this, 0)
        }
    }

    fun stopHDS() {
        detachHarleyDataListeners()
        mService?.disconnect()
        mService = null
        connectionViewModel.setState(ConnectionUiState.Idle)
        invalidateOptionsMenu()
    }

    class HarleyDroidHandler(activity: HarleyDroid) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(activity)
        override fun handleMessage(msg: Message) {
            ref.get()?.handleMessage(msg)
        }
    }
}
