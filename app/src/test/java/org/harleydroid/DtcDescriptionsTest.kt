//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software under the GNU GPL v3 or later. See COPYING.
//
package org.harleydroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcDescriptionsTest {

    @Test
    fun moduleAddressesMatchDiagnosticsTas() {
        assertEquals(0x10, DtcModule.ECM.address)
        assertEquals(0x40, DtcModule.ABS.address)
        assertEquals(0x60, DtcModule.TSM.address)
        assertEquals(DtcModule.ECM, DtcModule.fromAddress(0x10))
        assertEquals(DtcModule.ABS, DtcModule.fromAddress(0x40))
        assertEquals(DtcModule.TSM, DtcModule.fromAddress(0x60))
        assertEquals(null, DtcModule.fromAddress(0x20))
    }

    @Test
    fun clearExpectFormat() {
        for (module in DtcModule.entries) {
            val expect = String.format("6CF1%02X54", module.address)
            assertTrue(expect.startsWith("6CF1"))
            assertTrue(expect.endsWith("54"))
        }
    }
}
