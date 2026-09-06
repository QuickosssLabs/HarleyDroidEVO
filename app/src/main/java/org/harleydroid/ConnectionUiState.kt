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

sealed class ConnectionUiState {
    /** Not linked to interface / simulation. */
    data object Idle : ConnectionUiState()
    /** Bluetooth / ELM handshake in progress. */
    data object Connecting : ConnectionUiState()
    /** Link up, waiting for live frames. */
    data object Connected : ConnectionUiState()
    /** Live capture, engine not running (RPM ≈ 0). */
    data object EngineOff : ConnectionUiState()
    /** Live capture, engine running. */
    data object Running : ConnectionUiState()
    /** Legacy alias used while starting poll before first RPM sample. */
    data object Polling : ConnectionUiState()
    data object Diagnostics : ConnectionUiState()
    data class Error(val status: Int) : ConnectionUiState()
    data object Reconnecting : ConnectionUiState()
}

object HarleyStatus {
    const val NONE = 0
    const val CONNECTING = 1
    const val CONNECTED = 2
    const val ERROR = 3
    const val ERRORAT = 4
    const val NODATA = 5
    const val TOOMANYERRORS = 6
    const val AUTORECON = 7
    const val CAN_DIAG_UNSUPPORTED = 8
}
