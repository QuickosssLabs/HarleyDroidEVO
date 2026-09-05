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

/**
 * Passive HDLAN / Harley CAN decoder (community maps — calibrate per model/year).
 *
 * Accepts ELM327 monitor lines with headers, e.g.:
 * - `521 00 64 00 00 00 00 00`
 * - `521#0064000000000000`
 * - `5210064000000000000` (ATS0)
 *
 * Values are converted into [HarleyData] raw units (same as J1850 path).
 *
 * Gear: HDLAN community maps only expose a **neutral** bit (0x530). Like the OEM
 * cluster, engaged gear is estimated from RPM / road speed using Cruise Drive
 * relative ratios (blank when clutch in, neutral, or speed/RPM too low).
 */
object HarleyCan {
	private const val D = false
	private val TAG = HarleyCan::class.java.simpleName

	/** Community / RealDash-style 11-bit IDs. */
	const val ID_SPEED = 0x521
	const val ID_ODOMETER = 0x5C0
	const val ID_ENGINE_TEMP = 0x541
	const val ID_SWITCHES = 0x550
	const val ID_STATUS = 0x530
	/** Tentative engine / RPM-related frame (best-effort). */
	const val ID_ENGINE = 0x5C1

	/** Harley Cruise Drive 6-speed internal ratios (6th = 1.0). */
	private val GEAR_RATIOS = doubleArrayOf(
		Double.NaN, // index 0 unused
		3.342, 2.302, 1.714, 1.392, 1.176, 1.000
	)

	/** Nominal rpm/kmh scale factor for 6th (adapts while riding). */
	private const val DEFAULT_K = 22.0
	private const val MIN_SPEED_KMH = 8
	private const val MIN_RPM = 900

	private var lastRpm = 0
	private var lastKmh = 0
	private var lastNeutral = false
	private var lastClutch = false
	private var scaleK = DEFAULT_K

	@JvmStatic
	fun resetCounters() {
		lastRpm = 0
		lastKmh = 0
		lastNeutral = false
		lastClutch = false
		scaleK = DEFAULT_K
	}

	/**
	 * @return true if the line was a plausible CAN frame (known or unknown).
	 *         false if the line could not be parsed as hex CAN at all.
	 */
	@JvmStatic
	fun parse(buffer: ByteArray, hd: HarleyData): Boolean {
		val line = String(buffer).trim()
		if (line.isEmpty()) return false
		// Ignore ELM chatter
		val upper = line.uppercase()
		if (upper.startsWith("SEARCHING") || upper == "OK" || upper.startsWith("ELM") ||
			upper.startsWith("AT") || upper == "?" || upper.startsWith("BUFFER") ||
			upper.startsWith("BUS") || upper.startsWith("STOPPED")
		) {
			return true
		}

		val parsed = parseFrame(line) ?: run {
			hd.setUnknown(buffer)
			return true
		}
		val (id, data) = parsed

		when (id) {
			ID_SPEED -> {
				if (data.size >= 2) {
					// RealDash: byte1 often speed; accept 16-bit little-endian fallback
					val kmh = (data[1].toInt() and 0xff).let { b1 ->
						if (data.size >= 3 && b1 == 0 && (data[2].toInt() and 0xff) != 0)
							data[2].toInt() and 0xff
						else b1
					}
					lastKmh = kmh
					hd.setSpeed(kmh * 128)
					updateEstimatedGear(hd)
				} else hd.setUnknown(buffer)
			}
			ID_ODOMETER -> {
				if (data.size >= 8) {
					// Bytes 5..7: 24-bit odometer (community map), treat as km → HarleyData units (/25)
					val km = ((data[5].toInt() and 0xff) shl 16) or
						((data[6].toInt() and 0xff) shl 8) or
						(data[7].toInt() and 0xff)
					hd.setOdometerAbsolute(km * 25)
				} else if (data.size >= 3) {
					val km = ((data[data.size - 3].toInt() and 0xff) shl 16) or
						((data[data.size - 2].toInt() and 0xff) shl 8) or
						(data[data.size - 1].toInt() and 0xff)
					hd.setOdometerAbsolute(km * 25)
				} else hd.setUnknown(buffer)
			}
			ID_ENGINE_TEMP -> {
				if (data.size >= 6) {
					// Byte 5: °C (community); HarleyData expects offset +40
					val celsius = data[5].toInt() and 0xff
					hd.setEngineTemp(celsius + 40)
				} else if (data.isNotEmpty()) {
					val celsius = data[data.size - 1].toInt() and 0xff
					hd.setEngineTemp(celsius + 40)
				} else hd.setUnknown(buffer)
			}
			ID_SWITCHES -> {
				if (data.isNotEmpty()) {
					val b0 = data[0].toInt() and 0xff
					lastClutch = (b0 and 0x01) != 0
					hd.setClutch(lastClutch)
					updateEstimatedGear(hd)
				} else hd.setUnknown(buffer)
			}
			ID_STATUS -> {
				if (data.size >= 3) {
					val neutralByte = data[2].toInt() and 0xff
					lastNeutral = (neutralByte and 0x81) != 0 || neutralByte == 0x81
					hd.setNeutral(lastNeutral)
					updateEstimatedGear(hd)
				} else hd.setUnknown(buffer)
			}
			ID_ENGINE -> {
				// Best-effort RPM: bytes 0..1 big-endian / 4 (OBD-like) when plausible
				if (data.size >= 2) {
					val raw = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
					val rpm = raw / 4
					if (rpm in 1..9000) {
						lastRpm = rpm
						hd.setRPM(rpm * 4)
						updateEstimatedGear(hd)
					} else if (data.size >= 4) {
						val raw2 = ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
						val rpm2 = raw2 / 4
						if (rpm2 in 1..9000) {
							lastRpm = rpm2
							hd.setRPM(rpm2 * 4)
							updateEstimatedGear(hd)
						} else hd.setUnknown(buffer)
					} else hd.setUnknown(buffer)
				} else hd.setUnknown(buffer)
			}
			else -> {
				if (D) Log.d(TAG, "unknown CAN id=0x${Integer.toHexString(id)}")
				hd.setUnknown(buffer)
			}
		}
		return true
	}

	/**
	 * Estimate engaged gear from RPM/speed (OEM cluster behaviour).
	 * Exposed for unit tests.
	 */
	@JvmStatic
	fun updateEstimatedGear(hd: HarleyData) {
		if (lastNeutral) {
			hd.setGear(-1)
			return
		}
		if (lastClutch || lastKmh < MIN_SPEED_KMH || lastRpm < MIN_RPM) {
			hd.setGear(-1)
			return
		}

		val r = lastRpm.toDouble() / lastKmh.toDouble()
		var bestGear = -1
		var bestErr = Double.MAX_VALUE
		for (g in 1..6) {
			val expected = scaleK * GEAR_RATIOS[g]
			val err = kotlin.math.abs(r - expected) / expected
			if (err < bestErr) {
				bestErr = err
				bestGear = g
			}
		}
		// Reject ambiguous matches (clutch slip, wrong final drive, etc.)
		if (bestGear < 1 || bestErr > 0.22) {
			hd.setGear(-1)
			return
		}
		hd.setGear(bestGear)
		// Slow adaptation of overall scale to bike gearing / tyre size
		val observedK = r / GEAR_RATIOS[bestGear]
		if (observedK in 12.0..45.0) {
			scaleK = 0.92 * scaleK + 0.08 * observedK
		}
	}

	/**
	 * Parse an ELM-style CAN monitor line into (11-bit id, payload bytes).
	 */
	@JvmStatic
	fun parseFrame(line: String): Pair<Int, ByteArray>? {
		val cleaned = line.trim().uppercase()
			.replace("\r", "")
			.replace("\n", "")
		if (cleaned.isEmpty()) return null

		val hash = cleaned.indexOf('#')
		if (hash > 0) {
			val idStr = cleaned.substring(0, hash).replace(" ", "")
			val dataStr = cleaned.substring(hash + 1).replace(" ", "")
			val id = idStr.toIntOrNull(16) ?: return null
			val data = hexToBytes(dataStr) ?: return null
			return id to data
		}

		// Spaced: "521 00 64 ..." or compact "5210064..."
		val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
		if (tokens.size >= 2 && tokens.all { it.matches(Regex("[0-9A-F]+")) }) {
			val id = tokens[0].toIntOrNull(16) ?: return null
			val data = ByteArray(tokens.size - 1)
			for (i in 1 until tokens.size) {
				val v = tokens[i].toIntOrNull(16) ?: return null
				if (tokens[i].length > 2) {
					// Token might be multi-byte blob — fall through to compact
					return parseCompact(cleaned)
				}
				data[i - 1] = (v and 0xff).toByte()
			}
			return id to data
		}

		return parseCompact(cleaned)
	}

	private fun parseCompact(cleaned: String): Pair<Int, ByteArray>? {
		val hex = cleaned.replace(" ", "")
		if (!hex.matches(Regex("[0-9A-F]+")) || hex.length < 5) return null
		// 11-bit ID as 3 hex digits, rest payload
		val idLen = when {
			hex.length >= 3 && hex.substring(0, 3).toIntOrNull(16) != null -> 3
			else -> return null
		}
		val id = hex.substring(0, idLen).toInt(16)
		val dataHex = hex.substring(idLen)
		if (dataHex.length % 2 != 0) return null
		val data = hexToBytes(dataHex) ?: return null
		return id to data
	}

	private fun hexToBytes(hex: String): ByteArray? {
		if (hex.isEmpty() || hex.length % 2 != 0) return null
		val out = ByteArray(hex.length / 2)
		for (i in out.indices) {
			val v = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
			out[i] = (v and 0xff).toByte()
		}
		return out
	}
}
