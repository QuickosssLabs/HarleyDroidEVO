//
// HarleyDroid EVO: evolution of HarleyDroid (J1850 analyser for Android).
//
// Copyright (C) 2010-2012 Stelian Pop <stelian@popies.net>
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (maintenance / modernization)
//
// This program is free software under the GNU GPL v3 or later. See COPYING.
//
package org.harleydroid

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class J1850ParseTest {

    private lateinit var data: HarleyData

    @Before
    fun setUp() {
        J1850.resetCounters()
        data = HarleyData(MemPrefs())
    }

    @Test
    fun crcAcceptsValidFrame() {
        // RPM 1000: 28 1B 10 02 0F A0 + CRC
        val line = "28 1B 10 02 0F A0 D7".toByteArray()
        assertTrue(J1850.parse(line, data))
        assertEquals(1000, data.getRPM())
    }

    @Test
    fun parsesSpeed100Kmh() {
        // Speed 100 km/h: 48 29 10 02 32 00 + CRC 0x0f from README example
        val hex = J1850.bytes_to_hex("48 29 10 02 32 00".toByteArray())
        val withoutCrc = hex.copyOf(hex.size)
        val need = (J1850.crc(withoutCrc).toInt().inv() and 0xff).toByte()
        val withCrc = withoutCrc + need
        // Build ASCII hex with CRC for parse()
        val sb = StringBuilder()
        for (b in withCrc) sb.append(String.format("%02X", b.toInt() and 0xff))
        assertTrue(J1850.parse(sb.toString().toByteArray(), data))
        assertEquals(100, data.getSpeedMetric())
    }

    @Test
    fun parsesGear() {
        // gear raw nibble path: a8 3b 10 03 xx where xx bit shifts
        val payload = byteArrayOf(0xa8.toByte(), 0x3b, 0x10, 0x03, 0x08) // gear ~4
        val need = (J1850.crc(payload).toInt().inv() and 0xff).toByte()
        val frame = payload + need
        val sb = StringBuilder()
        for (b in frame) sb.append(String.format("%02X", b.toInt() and 0xff))
        assertTrue(J1850.parse(sb.toString().toByteArray(), data))
        assertEquals(3, data.getGear())
    }

    @Test
    fun parsesFuelGauge() {
        // A8 83 61 12 NN — bars 0–15; bit7 of byte3 = low fuel
        val payload = byteArrayOf(0xa8.toByte(), 0x83.toByte(), 0x61, 0x12, 0x0c)
        val need = (J1850.crc(payload).toInt().inv() and 0xff).toByte()
        val sb = StringBuilder()
        for (b in payload + need) sb.append(String.format("%02X", b.toInt() and 0xff))
        assertTrue(J1850.parse(sb.toString().toByteArray(), data))
        assertEquals(12, data.getFuelGauge())
        assertEquals(false, data.getFuelLow())

        val lowPayload = byteArrayOf(0xa8.toByte(), 0x83.toByte(), 0x61, 0x92.toByte(), 0x02)
        val lowNeed = (J1850.crc(lowPayload).toInt().inv() and 0xff).toByte()
        val lowSb = StringBuilder()
        for (b in lowPayload + lowNeed) lowSb.append(String.format("%02X", b.toInt() and 0xff))
        assertTrue(J1850.parse(lowSb.toString().toByteArray(), data))
        assertEquals(2, data.getFuelGauge())
        assertEquals(true, data.getFuelLow())
    }

    @Test
    fun parsesDtcByModule() {
        // ECM P0134
        assertTrue(parseFrame(byteArrayOf(0x6c, 0xf1.toByte(), 0x10, 0x59, 0x01, 0x34)))
        assertEquals(arrayOf("P0134").toList(), data.getDtc(DtcModule.ECM).toList())

        // ABS C1151
        assertTrue(parseFrame(byteArrayOf(0x6c, 0xf1.toByte(), 0x40, 0x59, 0x41, 0x51)))
        assertEquals(arrayOf("C1151").toList(), data.getDtc(DtcModule.ABS).toList())

        // TSM U1064 (was previously dropped as unknown)
        assertTrue(parseFrame(byteArrayOf(0x6c, 0xf1.toByte(), 0x60, 0x59, 0xd0.toByte(), 0x64)))
        assertEquals(arrayOf("U1064").toList(), data.getDtc(DtcModule.TSM).toList())
    }

    private fun parseFrame(payload: ByteArray): Boolean {
        val need = (J1850.crc(payload).toInt().inv() and 0xff).toByte()
        val sb = StringBuilder()
        for (b in payload + need) sb.append(String.format("%02X", b.toInt() and 0xff))
        return J1850.parse(sb.toString().toByteArray(), data)
    }

    private class MemPrefs : SharedPreferences {
        private val map = HashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, def: String?) = map[key] as String? ?: def
        override fun getStringSet(key: String?, def: MutableSet<String>?) =
            map[key] as MutableSet<String>? ?: def
        override fun getInt(key: String?, def: Int) = map[key] as Int? ?: def
        override fun getLong(key: String?, def: Long) = map[key] as Long? ?: def
        override fun getFloat(key: String?, def: Float) = map[key] as Float? ?: def
        override fun getBoolean(key: String?, def: Boolean) = map[key] as Boolean? ?: def
        override fun contains(key: String?) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, Any?>()
            override fun putString(k: String?, v: String?) = apply { pending[k!!] = v }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { pending[k!!] = v }
            override fun putInt(k: String?, v: Int) = apply { pending[k!!] = v }
            override fun putLong(k: String?, v: Long) = apply { pending[k!!] = v }
            override fun putFloat(k: String?, v: Float) = apply { pending[k!!] = v }
            override fun putBoolean(k: String?, v: Boolean) = apply { pending[k!!] = v }
            override fun remove(k: String?) = apply { pending[k!!] = this }
            override fun clear() = apply { pending.clear(); map.clear() }
            override fun commit(): Boolean {
                map.putAll(pending.filter { it.value !is SharedPreferences.Editor })
                pending.clear()
                return true
            }
            override fun apply() { commit() }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
