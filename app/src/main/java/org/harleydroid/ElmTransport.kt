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

import java.io.IOException
import java.util.concurrent.TimeoutException

/** Line-oriented transport for ELM327 (Bluetooth SPP or TCP/WiFi). */
interface ElmTransport {
    @Throws(IOException::class)
    fun writeLine(line: String)

    @Throws(TimeoutException::class)
    fun readLine(timeout: Long): String

    @Throws(IOException::class, TimeoutException::class)
    fun chat(send: String, expect: String, timeout: Long): String

    fun close()
}
