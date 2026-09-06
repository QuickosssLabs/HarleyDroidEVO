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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ConnectionViewModel : ViewModel() {
    private val _uiState = MutableLiveData<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: LiveData<ConnectionUiState> = _uiState

    fun setState(state: ConnectionUiState) {
        _uiState.value = state
    }

    /**
     * Refine live status from RPM while capturing.
     * Below ~400 RPM → engine off / ignition; otherwise running.
     * Safe from any thread (emulator poll uses a background thread).
     */
    fun reportEngineRpm(rpm: Int) {
        when (_uiState.value) {
            is ConnectionUiState.Polling,
            is ConnectionUiState.Connected,
            is ConnectionUiState.EngineOff,
            is ConnectionUiState.Running -> {
                val next = if (rpm < 400)
                    ConnectionUiState.EngineOff
                else
                    ConnectionUiState.Running
                if (_uiState.value != next)
                    _uiState.postValue(next)
            }
            else -> Unit
        }
    }
}
