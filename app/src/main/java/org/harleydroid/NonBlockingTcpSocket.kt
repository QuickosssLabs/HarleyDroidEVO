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

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** TCP client for WiFi ELM327 dongles (typical port 35000). */
class NonBlockingTcpSocket : NonBlockingLineTransport() {

    companion object {
        private const val D = false
        private val TAG = NonBlockingTcpSocket::class.java.simpleName
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val SO_TIMEOUT_MS = 0 // blocking reads handled by queue poll timeouts
    }

    private var mSock: Socket? = null

    @Throws(IOException::class)
    fun connect(host: String, port: Int) {
        if (D) Log.d(TAG, "${System.currentTimeMillis()} connect $host:$port")
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            sock.soTimeout = SO_TIMEOUT_MS
            sock.tcpNoDelay = true
        } catch (e: Exception) {
            try {
                sock.close()
            } catch (_: IOException) {
            }
            throw IOException("TCP connect failed to $host:$port", e)
        }
        mSock = sock
        startStreams(sock.getInputStream(), sock.getOutputStream(), "NonBlockingTcpSocket Thread")
    }

    override fun close() {
        if (D) Log.d(TAG, "${System.currentTimeMillis()} close()")
        val sock = mSock
        mSock = null
        if (sock != null) {
            try {
                sock.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() failed", e)
            }
        }
    }

    override fun tag(): String = TAG
}
