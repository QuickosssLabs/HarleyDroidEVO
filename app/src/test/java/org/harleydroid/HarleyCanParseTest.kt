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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarleyCanParseTest {

	private lateinit var data: HarleyData

	@Before
	fun setUp() {
		HarleyCan.resetCounters()
		data = HarleyData(MemPrefs())
	}

	@Test
	fun parsesSpacedSpeedFrame() {
		assertTrue(HarleyCan.parse("521 00 64 00 00 00 00 00".toByteArray(), data))
		assertEquals(100, data.getSpeedMetric())
	}

	@Test
	fun parsesHashFormatOdometer() {
		// km = 0x001388 = 5000 → setOdometer(5000*25)
		assertTrue(HarleyCan.parse("5C0#00000000001388".toByteArray(), data))
		assertEquals(5000, data.getOdometerMetric())
	}

	@Test
	fun parsesEngineTemp() {
		// byte5 = 80°C → metric display 80
		assertTrue(HarleyCan.parse("541 00 00 00 00 00 50 00 00".toByteArray(), data))
		assertEquals(80, data.getEngineTempMetric())
	}

	@Test
	fun parsesClutchBit() {
		assertTrue(HarleyCan.parse("550 01 00 00 00 00 00".toByteArray(), data))
		assertTrue(data.getClutch())
	}

	@Test
	fun estimatesGearFromRpmAndSpeed() {
		// 50 km/h, ~3rd gear: rpm ≈ 50 * 22 * 1.714 ≈ 1885
		assertTrue(HarleyCan.parse("550 00 00 00 00 00 00".toByteArray(), data))
		assertTrue(HarleyCan.parse("530 00 00 00 00 00 00 00 00".toByteArray(), data))
		assertTrue(HarleyCan.parse("521 00 32 00 00 00 00 00".toByteArray(), data))
		val rpm = 1885
		assertTrue(
			HarleyCan.parse(
				String.format("5C1 %02X %02X 00 00 00 00 00 00", (rpm * 4) shr 8, (rpm * 4) and 0xff)
					.toByteArray(),
				data
			)
		)
		assertEquals(3, data.getGear())
	}

	@Test
	fun blanksGearWhenNeutral() {
		assertTrue(HarleyCan.parse("521 00 32 00 00 00 00 00".toByteArray(), data))
		val rpm = 1885
		assertTrue(
			HarleyCan.parse(
				String.format("5C1 %02X %02X 00 00 00 00 00 00", (rpm * 4) shr 8, (rpm * 4) and 0xff)
					.toByteArray(),
				data
			)
		)
		assertTrue(HarleyCan.parse("530 00 00 81 00 00 00 00 00".toByteArray(), data))
		assertEquals(-1, data.getGear())
		assertTrue(data.getNeutral())
	}

	@Test
	fun blanksGearWhenClutch() {
		assertTrue(HarleyCan.parse("530 00 00 00 00 00 00 00 00".toByteArray(), data))
		assertTrue(HarleyCan.parse("521 00 40 00 00 00 00 00".toByteArray(), data))
		val rpm = 2200
		assertTrue(
			HarleyCan.parse(
				String.format("5C1 %02X %02X 00 00 00 00 00 00", (rpm * 4) shr 8, (rpm * 4) and 0xff)
					.toByteArray(),
				data
			)
		)
		assertTrue(HarleyCan.parse("550 01 00 00 00 00 00".toByteArray(), data))
		assertEquals(-1, data.getGear())
	}

	@Test
	fun parseFrameCompact() {
		val frame = HarleyCan.parseFrame("5210064000000000000")
		assertNotNull(frame)
		assertEquals(0x521, frame!!.first)
		assertEquals(0x64, frame.second[1].toInt() and 0xff)
	}

	/** Regression: absolute CAN odo + small fuel must not ArithmeticException (/0). */
	@Test
	fun absoluteOdoWithSmallFuelDoesNotCrashAverage() {
		assertTrue(HarleyCan.parse("5C0#0000000047F4".toByteArray(), data)) // 18420 km
		assertEquals(18420, data.getOdometerMetric())
		data.setFuel(1)
		assertEquals(-1, data.getFuelAverageMetric())
		assertEquals(-1, data.getFuelAverageImperial())
	}

	@Test
	fun absoluteOdoUsesTripForFuelAverage() {
		data.setOdometerAbsolute(18420 * 25)
		data.setFuel(0)
		data.setOdometerAbsolute(18421 * 25) // +1 km trip
		data.setFuel(13) // ~6.5 L/100km raw units
		assertEquals(650, data.getFuelAverageMetric())
		assertTrue(data.getFuelAverageImperial() > 0)
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
		override fun registerOnSharedPreferenceChangeListener(
			l: SharedPreferences.OnSharedPreferenceChangeListener?
		) {}
		override fun unregisterOnSharedPreferenceChangeListener(
			l: SharedPreferences.OnSharedPreferenceChangeListener?
		) {}
	}
}
