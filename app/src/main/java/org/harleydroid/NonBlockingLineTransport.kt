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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Shared reader-queue + AT line I/O for Bluetooth SPP and TCP ELM327 sockets.
 */
abstract class NonBlockingLineTransport : Thread(), ElmTransport {

    companion object {
        private const val D = false

        @JvmStatic
        fun myGetBytes(s: String, start: Int, end: Int): ByteArray {
            val result = ByteArray(end - start)
            for (i in start until end) {
                result[i - start] = s[i].code.toByte()
            }
            return result
        }

        @JvmStatic
        fun myGetBytes(s: String): ByteArray = myGetBytes(s, 0, s.length)
    }

    private var mIn: BufferedReader? = null
    private var mOut: OutputStream? = null
    private var queue: LinkedBlockingQueue<String>? = null

    protected fun startStreams(input: InputStream, output: OutputStream, threadName: String) {
        mIn = BufferedReader(InputStreamReader(input), 128)
        mOut = output
        queue = LinkedBlockingQueue()
        name = threadName
        start()
    }

    @Throws(TimeoutException::class)
    override fun readLine(timeout: Long): String {
        val line = try {
            queue?.poll(timeout, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            if (D) Log.e(tag(), "${System.currentTimeMillis()} readLine() interrupted: $e")
            null
        }
        if (line == null) {
            if (D) Log.d(tag(), "${System.currentTimeMillis()} readLine() timeout")
            throw TimeoutException(null as String?)
        }
        if (D) Log.d(tag(), "${System.currentTimeMillis()} readLine (${line.length}): $line")
        return line
    }

    @Throws(IOException::class)
    override fun writeLine(line: String) {
        val out = mOut ?: throw IOException("socket closed")
        val payload = "$line\r"
        if (D) Log.d(tag(), "${System.currentTimeMillis()} writeLine: $payload")
        out.write(myGetBytes(payload))
        out.flush()
    }

    @Throws(IOException::class, TimeoutException::class)
    override fun chat(send: String, expect: String, timeout: Long): String {
        val result = StringBuilder()
        writeLine(send)
        var remaining = timeout
        try {
            var start = System.currentTimeMillis()
            while (remaining > 0) {
                val line = readLine(remaining)
                val now = System.currentTimeMillis()
                remaining -= (now - start)
                start = now
                result.append(line).append('\n')
                if (line.contains(expect)) return result.toString()
            }
            throw TimeoutException(null as String?)
        } catch (e: TimeoutException) {
            throw TimeoutException(result.toString())
        }
    }

    override fun run() {
        try {
            while (true) {
                val line = mIn?.readLine()?.trim().orEmpty()
                if (line.isNotEmpty()) queue?.add(line)
            }
        } catch (e: IOException) {
            if (D) Log.e(tag(), "${System.currentTimeMillis()} read thread exception: $e")
        }
    }

    protected abstract fun tag(): String
}
