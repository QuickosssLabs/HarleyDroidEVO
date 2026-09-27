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

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/**
 * Standalone ELM327 link probe (TCP or Bluetooth): AT handshake, voltage,
 * protocol, and a short ATMA listen — without using HarleyDroidService.
 */
object ElmLinkProbe {

    enum class Verdict {
        EMULATOR,
        NOT_CONFIGURED,
        LINK_FAIL,
        AT_FAIL,
        POWER_LOW,
        BUS_SILENT,
        BUS_NOISE,
        BUS_OK,
    }

    data class Result(
        val verdict: Verdict,
        val endpoint: String,
        val busProtocol: String,
        val elmBanner: String?,
        val voltageText: String?,
        val voltageVolts: Double?,
        val protocolDesc: String?,
        val atmaLineCount: Int,
        val atmaSampleLines: List<String>,
        val log: String,
    )

    private const val AT_TIMEOUT_MS = 3_000L
    private const val ATZ_TIMEOUT_MS = 6_000L
    private const val ATMA_LISTEN_MS = 5_000L
    private const val DRAIN_MS = 200L

    private val VOLTAGE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*V", Pattern.CASE_INSENSITIVE)

    @JvmStatic
    fun run(context: Context, prefs: SharedPreferences): Result {
        if (HarleyDroid.isEmulatorMode(prefs)) {
            return Result(
                verdict = Verdict.EMULATOR,
                endpoint = "emulator",
                busProtocol = BusProtocol.fromPrefs(prefs),
                elmBanner = null,
                voltageText = null,
                voltageVolts = null,
                protocolDesc = null,
                atmaLineCount = 0,
                atmaSampleLines = emptyList(),
                log = "Simulation mode is on — disable it in Preferences to probe a real ELM327.",
            )
        }

        val useWifi = ConnectionTransport.isWifi(prefs)
        val bus = BusProtocol.fromPrefs(prefs)
        val log = StringBuilder()

        fun line(msg: String) {
            log.append(msg).append('\n')
        }

        val endpoint: String
        val sock: ElmTransport = try {
            if (useWifi) {
                val host = ConnectionTransport.wifiHost(prefs)
                val port = ConnectionTransport.wifiPort(prefs)
                endpoint = "$host:$port (WiFi TCP)"
                line("Connecting to $endpoint …")
                NonBlockingTcpSocket().also { it.connect(host, port) }
            } else {
                val id = prefs.getString("bluetoothid", null)?.takeIf { it.isNotBlank() }
                if (id == null) {
                    return Result(
                        verdict = Verdict.NOT_CONFIGURED,
                        endpoint = "bluetooth?",
                        busProtocol = bus,
                        elmBanner = null,
                        voltageText = null,
                        voltageVolts = null,
                        protocolDesc = null,
                        atmaLineCount = 0,
                        atmaSampleLines = emptyList(),
                        log = "No Bluetooth device selected in Preferences.",
                    )
                }
                endpoint = "$id (Bluetooth SPP)"
                line("Connecting to $endpoint …")
                val adapter = (context.applicationContext
                    .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    ?: throw IOException("Bluetooth adapter unavailable")
                val device = adapter.getRemoteDevice(id)
                NonBlockingBluetoothSocket().also { it.connect(context, device) }
            }
        } catch (e: Exception) {
            line("CONNECT FAILED: ${e.message}")
            if (useWifi) {
                line("Tip: stay on the ELM WiFi AP and turn mobile data off.")
            }
            return Result(
                verdict = Verdict.LINK_FAIL,
                endpoint = if (useWifi) {
                    "${ConnectionTransport.wifiHost(prefs)}:${ConnectionTransport.wifiPort(prefs)}"
                } else {
                    "bluetooth"
                },
                busProtocol = bus,
                elmBanner = null,
                voltageText = null,
                voltageVolts = null,
                protocolDesc = null,
                atmaLineCount = 0,
                atmaSampleLines = emptyList(),
                log = log.toString().trimEnd(),
            )
        }

        try {
            line("OK — socket open")
            drain(sock)

            // Wake / warm start
            try {
                sock.writeLine("AT")
                Thread.sleep(400)
                drain(sock)
            } catch (_: Exception) {
            }

            val banner = expectContains(sock, "ATWS", "ELM327", ATZ_TIMEOUT_MS, log)
            if (banner == null) {
                return Result(
                    verdict = Verdict.AT_FAIL,
                    endpoint = endpoint,
                    busProtocol = bus,
                    elmBanner = null,
                    voltageText = null,
                    voltageVolts = null,
                    protocolDesc = null,
                    atmaLineCount = 0,
                    atmaSampleLines = emptyList(),
                    log = log.toString().trimEnd(),
                )
            }

            chatOk(sock, "ATE1", log)
            chatOk(sock, "ATH1", log)
            chatOk(sock, "ATAL", log)

            val voltageRaw = collect(sock, "ATRV", AT_TIMEOUT_MS, log)
            val voltageVolts = parseVoltage(voltageRaw)
            val voltageText = voltageRaw.lines().map { it.trim() }.firstOrNull {
                VOLTAGE.matcher(it).find()
            } ?: voltageRaw.trim().takeIf { it.isNotEmpty() }

            val sp = if (BusProtocol.isCan(bus)) "ATSP6" else "ATSP2"
            line("Bus preference: $bus → $sp")
            chatOk(sock, sp, log)

            val protocolDesc = collect(sock, "ATDP", AT_TIMEOUT_MS, log)
                .lines().map { it.trim() }.filter { it.isNotEmpty() && it != ">" }
                .joinToString(" | ")
                .ifBlank { null }

            line("Listening ATMA for ${ATMA_LISTEN_MS / 1000}s …")
            try {
                sock.writeLine("ATMA")
            } catch (e: IOException) {
                line("ATMA write failed: ${e.message}")
            }

            val samples = ArrayList<String>()
            var ignoredStatusLines = 0
            val deadline = System.currentTimeMillis() + ATMA_LISTEN_MS
            while (System.currentTimeMillis() < deadline) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                try {
                    val l = sock.readLine(remaining.coerceAtMost(1_000L)).trim()
                    if (l.isEmpty() || l == ">") continue
                    if (isElmStatusLine(l)) {
                        ignoredStatusLines++
                        if (ignoredStatusLines <= 6) line("  « $l »")
                        continue
                    }
                    samples.add(l)
                    if (samples.size <= 12) line("  « $l »")
                } catch (_: TimeoutException) {
                    // keep listening until deadline
                }
            }

            try {
                sock.writeLine("")
                Thread.sleep(200)
                drain(sock)
            } catch (_: Exception) {
            }

            line("ATMA lines: ${samples.size}")
            if (ignoredStatusLines > 0) {
                line("ELM/status lines ignored: $ignoredStatusLines")
            }

            val frameLike = samples.count { looksLikeBusFrame(it) }
            val finalVerdict = when {
                voltageVolts != null && voltageVolts < 9.0 -> Verdict.POWER_LOW
                frameLike > 0 -> Verdict.BUS_OK
                samples.isEmpty() -> Verdict.BUS_SILENT
                else -> Verdict.BUS_NOISE
            }

            return Result(
                verdict = finalVerdict,
                endpoint = endpoint,
                busProtocol = bus,
                elmBanner = banner.lines().map { it.trim() }.firstOrNull {
                    it.contains("ELM327", ignoreCase = true)
                } ?: banner.trim().take(80),
                voltageText = voltageText,
                voltageVolts = voltageVolts,
                protocolDesc = protocolDesc,
                atmaLineCount = samples.size,
                atmaSampleLines = samples.take(20),
                log = log.toString().trimEnd(),
            )
        } finally {
            try {
                sock.close()
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun parseVoltage(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val m = VOLTAGE.matcher(raw)
        if (!m.find()) return null
        return m.group(1)?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun isElmStatusLine(line: String): Boolean {
        return ElmAtReply.isStatusLine(line)
    }

    private fun looksLikeBusFrame(line: String): Boolean {
        val cleaned = line.replace(" ", "").replace("#", "")
        if (cleaned.length < 4) return false
        if (isElmStatusLine(line)) return false
        return cleaned.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun drain(sock: ElmTransport) {
        try {
            while (true) {
                sock.readLine(DRAIN_MS)
            }
        } catch (_: TimeoutException) {
        }
    }

    private fun expectContains(
        sock: ElmTransport,
        cmd: String,
        expect: String,
        timeout: Long,
        log: StringBuilder,
    ): String? {
        log.append("> ").append(cmd).append('\n')
        return try {
            val reply = sock.chat(cmd, expect, timeout)
            reply.lines().forEach { if (it.isNotBlank()) log.append("  « ").append(it.trim()).append(" »\n") }
            reply
        } catch (e: TimeoutException) {
            val partial = e.message.orEmpty()
            if (partial.isNotBlank()) {
                partial.lines().forEach {
                    if (it.isNotBlank()) log.append("  « ").append(it.trim()).append(" »\n")
                }
            }
            log.append("TIMEOUT waiting for \"").append(expect).append("\"\n")
            if (partial.contains(expect, ignoreCase = true)) partial else null
        } catch (e: IOException) {
            log.append("I/O: ").append(e.message).append('\n')
            null
        }
    }

    private fun chatOk(sock: ElmTransport, cmd: String, log: StringBuilder) {
        expectContains(sock, cmd, "OK", AT_TIMEOUT_MS, log)
    }

    private fun collect(
        sock: ElmTransport,
        cmd: String,
        timeout: Long,
        log: StringBuilder,
    ): String {
        log.append("> ").append(cmd).append('\n')
        return try {
            sock.writeLine(cmd)
            val result = StringBuilder()
            var remaining = timeout
            var start = System.currentTimeMillis()
            // Keep reading until the overall budget elapses (no single-token expect).
            while (remaining > 0) {
                try {
                    val line = sock.readLine(remaining.coerceAtMost(800L)).trim()
                    val now = System.currentTimeMillis()
                    remaining -= (now - start)
                    start = now
                    if (line.isEmpty() || line == ">") continue
                    result.append(line).append('\n')
                    log.append("  « ").append(line).append(" »\n")
                    // Quiet period after first reply — typical ELM AT response is short.
                    if (result.isNotEmpty()) {
                        try {
                            while (remaining > 0) {
                                val extra = sock.readLine(250L).trim()
                                val n = System.currentTimeMillis()
                                remaining -= (n - start)
                                start = n
                                if (extra.isEmpty() || extra == ">") continue
                                result.append(extra).append('\n')
                                log.append("  « ").append(extra).append(" »\n")
                            }
                        } catch (_: TimeoutException) {
                        }
                        break
                    }
                } catch (_: TimeoutException) {
                    val now = System.currentTimeMillis()
                    remaining -= (now - start)
                    start = now
                }
            }
            if (result.isEmpty()) log.append("  (no reply)\n")
            result.toString()
        } catch (e: IOException) {
            log.append("I/O: ").append(e.message).append('\n')
            ""
        }
    }
}
