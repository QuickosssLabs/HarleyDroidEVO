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

/**
 * J1850 diagnostic nodes queried for DTCs (TA in HarleyDroidDiagnostics).
 */
enum class DtcModule(val address: Int, val labelRes: Int) {
    ECM(0x10, R.string.dtc_module_ecm),
    ABS(0x40, R.string.dtc_module_abs),
    TSM(0x60, R.string.dtc_module_tsm);

    companion object {
        fun fromAddress(sa: Int): DtcModule? = entries.firstOrNull { it.address == (sa and 0xff) }
    }
}
