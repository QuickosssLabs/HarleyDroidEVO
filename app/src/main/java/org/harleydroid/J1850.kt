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

import android.util.Log
import java.util.Arrays

object J1850 {
	private const val D = false
	private val TAG = J1850::class.java.simpleName

	const val MAXBUF = 1024

	private var odolast = 0
	private var odoaccum = 0
	private var fuellast = 0
	private var fuelaccum = 0

	private val vin = ByteArray(17) { '-'.code.toByte() }
	private val ecmPN = ByteArray(12) { '-'.code.toByte() }
	private val ecmCalID = ByteArray(12) { '-'.code.toByte() }

	@JvmStatic
	fun resetCounters() {
		odolast = 0
		odoaccum = 0
		fuellast = 0
		fuelaccum = 0
		Arrays.fill(vin, '-'.code.toByte())
		Arrays.fill(ecmPN, '-'.code.toByte())
		Arrays.fill(ecmCalID, '-'.code.toByte())
	}

	@JvmStatic
	fun bytes_to_hex(`in`: ByteArray): ByteArray {
		val out = ByteArray(MAXBUF)
		var inidx = 0
		var outidx = 0

		while (inidx < `in`.size) {
			while (inidx < `in`.size && Character.digit(`in`[inidx].toInt().toChar(), 16) == -1)
				inidx++
			if (inidx >= `in`.size)
				break
			val digit0 = Character.digit(`in`[inidx++].toInt().toChar(), 16)

			while (inidx < `in`.size && Character.digit(`in`[inidx].toInt().toChar(), 16) == -1)
				inidx++
			if (inidx >= `in`.size)
				break
			val digit1 = Character.digit(`in`[inidx++].toInt().toChar(), 16)

			out[outidx++] = (digit0 * 16 + digit1).toByte()
		}
		val ret = ByteArray(outidx)
		System.arraycopy(out, 0, ret, 0, outidx)
		return ret
	}

	@JvmStatic
	fun crc(`in`: ByteArray): Byte {
		var crc: Byte = 0xff.toByte()

		for (i in `in`.indices) {
			var c = `in`[i]
			for (j in 0 until 8) {
				var poly: Byte = 0
				if ((0x80 and (crc.toInt() xor c.toInt())) != 0)
					poly = 0x1d
				crc = (((crc.toInt() shl 1) and 0xff) xor poly.toInt()).toByte()
				c = (c.toInt() shl 1).toByte()
			}
		}
		return crc
	}

	@JvmStatic
	fun parse(buffer: ByteArray, hd: HarleyData): Boolean {
		val `in` = bytes_to_hex(buffer)

		if (crc(`in`) != 0xc4.toByte()) {
			hd.setBadCRC(buffer)
			return false
		}

		var x = 0
		var y = 0
		if (`in`.size >= 4)
			x = ((`in`[0].toInt() shl 24) and 0xff000000.toInt()) or
				((`in`[1].toInt() shl 16) and 0x00ff0000) or
				((`in`[2].toInt() shl 8) and 0x0000ff00) or
				(`in`[3].toInt() and 0x000000ff)
		if (`in`.size >= 6)
			y = ((`in`[4].toInt() shl 8) and 0x0000ff00) or
				(`in`[5].toInt() and 0x000000ff)

		when {
			x == 0x281b1002 -> hd.setRPM(y)
			x == 0x48291002 -> hd.setSpeed(y)
			x == 0xa8491010.toInt() -> hd.setEngineTemp(`in`[4].toInt() and 0xff)
			x == 0xa83b1003.toInt() -> {
				if (`in`[4].toInt() != 0) {
					var gear = 0
					var v = `in`[4].toInt() and 0xff
					while ((v shr 1).also { v = it } != 0)
						gear++
					hd.setGear(gear)
				} else
					hd.setGear(-1)
			}
			x == 0x48da4039 && (`in`[4].toInt() and 0xfc) == 0 ->
				hd.setTurnSignals(`in`[4].toInt() and 0x03)
			(x and 0xffffff7f.toInt()) == 0xa8691006.toInt() -> {
				odolast = y - odolast
				if (odolast < 0)
					odolast += 65536
				odoaccum += odolast
				odolast = y
				hd.setOdometer(odoaccum)
			}
			(x and 0xffffff7f.toInt()) == 0xa883100a.toInt() -> {
				fuellast = y - fuellast
				if (fuellast < 0)
					fuellast += 65536
				fuelaccum += fuellast
				fuellast = y
				hd.setFuel(fuelaccum)
			}
			(x and 0xffffff7f.toInt()) == 0xa8836112.toInt() ->
				hd.setFuelGauge(`in`[4].toInt() and 0x0f, (`in`[3].toInt() and 0x80) != 0)
			(x and 0xffffff5d.toInt()) == 0x483b4000 -> {
				when (`in`[3].toInt() and 0xff) {
					0x20 -> hd.setNeutral(false)
					0xA0 -> hd.setNeutral(true)
				}
				hd.setClutch((`in`[3].toInt() and 0x80) != 0)
			}
			(x and 0xffffff7f.toInt()) == 0x68881003 ->
				hd.setCheckEngine((`in`[3].toInt() and 0x80) != 0)
			x == 0x0c10f13c -> {
				/* read block command */
			}
			x == 0x0cf1107c -> {
				when (`in`[4].toInt()) {
					0x01 -> {
						System.arraycopy(`in`, 5, ecmPN, 0, 6)
						hd.setECMPN(String(ecmPN).trim())
					}
					0x02 -> {
						System.arraycopy(`in`, 5, ecmPN, 6, 6)
						hd.setECMPN(String(ecmPN).trim())
					}
					0x03 -> {
						System.arraycopy(`in`, 5, ecmCalID, 0, 6)
						hd.setECMCalID(String(ecmCalID).trim())
					}
					0x04 -> {
						System.arraycopy(`in`, 5, ecmCalID, 6, 6)
						hd.setECMCalID(String(ecmCalID).trim())
					}
					0x0b -> hd.setECMSWLevel(`in`[5].toInt() and 0xff)
					0x0f -> {
						System.arraycopy(`in`, 5, vin, 0, 6)
						hd.setVIN(String(vin).trim())
					}
					0x10 -> {
						System.arraycopy(`in`, 5, vin, 6, 6)
						hd.setVIN(String(vin).trim())
					}
					0x11 -> {
						System.arraycopy(`in`, 5, vin, 12, 5)
						hd.setVIN(String(vin).trim())
					}
					else -> hd.setUnknown(buffer)
				}
			}
			(x and 0xff0fffff.toInt()) == 0x6c00f119 -> {
				if (D) Log.d(TAG, "DTC start")
			}
			(x and 0xffff0fff.toInt()) == 0x6cf10059 -> {
				if (`in`[4].toInt() != 0 || `in`[5].toInt() != 0) {
					var dtc = when ((`in`[4].toInt() and 0xc0) shr 6) {
						0 -> "P"
						1 -> "C"
						2 -> "B"
						3 -> "U"
						else -> ""
					}
					dtc += Integer.toString((`in`[4].toInt() and 0x30) shr 4, 16)
					dtc += Integer.toString(`in`[4].toInt() and 0x0f, 16)
					dtc += Integer.toString((`in`[5].toInt() and 0xf0) shr 4, 16)
					dtc += Integer.toString(`in`[5].toInt() and 0x0f, 16)
					dtc = dtc.uppercase()
					when (`in`[2].toInt()) {
						0x10 -> {
							if (D) Log.d(TAG, "historic DTC: $dtc")
							hd.addHistoricDTC(dtc)
						}
						0x40 -> {
							if (D) Log.d(TAG, "current DTC: $dtc")
							hd.addCurrentDTC(dtc)
						}
						else -> hd.setUnknown(buffer)
					}
				}
			}
			(x and 0xff0fffff.toInt()) == 0x6c00f114 -> {
				if (D) Log.d(TAG, "DTC clear request")
			}
			(x and 0xffff0fff.toInt()) == 0x6cf10054 -> {
				if (D) Log.d(TAG, "DTC clear reply")
			}
			else -> hd.setUnknown(buffer)
		}
		return true
	}
}
