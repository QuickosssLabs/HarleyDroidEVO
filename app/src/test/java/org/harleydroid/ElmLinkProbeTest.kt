package org.harleydroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElmLinkProbeTest {
    @Test
    fun parseVoltage_acceptsDotAndComma() {
        assertEquals(12.5, ElmLinkProbe.parseVoltage("12.5V")!!, 0.01)
        assertEquals(13.2, ElmLinkProbe.parseVoltage("13,2 V")!!, 0.01)
        assertEquals(11.0, ElmLinkProbe.parseVoltage("ATRV\n11.0V\n>")!!, 0.01)
    }

    @Test
    fun parseVoltage_rejectsGarbage() {
        assertNull(ElmLinkProbe.parseVoltage(null))
        assertNull(ElmLinkProbe.parseVoltage("OK"))
        assertNull(ElmLinkProbe.parseVoltage(""))
    }
}
