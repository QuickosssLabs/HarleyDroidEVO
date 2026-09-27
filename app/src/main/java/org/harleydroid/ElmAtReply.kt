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

import java.util.Locale

/** Classifies ELM327 monitor / AT chatter so pollers do not treat it as bus CRC errors. */
object ElmAtReply {

    @JvmStatic
    fun isStatusLine(line: String?): Boolean {
        if (line.isNullOrBlank()) return true
        val u = line.trim().uppercase(Locale.US)
        if (u == ">" || u == "OK" || u == "?" || u == "ERROR") return true
        if (u == "STOPPED" || u == "NO DATA" || u == "UNABLE TO CONNECT") return true
        if (u.startsWith("SEARCHING") || u.startsWith("BUS INIT")) return true
        if (u.startsWith("ELM327") || u.startsWith("AT")) return true
        if (u.startsWith("BUFFER")) return true
        return false
    }

    /** Cheap clones often abort ATMA with this — restart monitoring rather than disconnect. */
    @JvmStatic
    fun needsAtmaRestart(line: String?): Boolean {
        if (line.isNullOrBlank()) return false
        val u = line.trim().uppercase(Locale.US)
        return u.startsWith("BUFFER") || u == "STOPPED"
    }
}
