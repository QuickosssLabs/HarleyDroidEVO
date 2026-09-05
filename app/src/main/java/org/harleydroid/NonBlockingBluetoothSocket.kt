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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class NonBlockingBluetoothSocket : Thread() {

    companion object {
        private const val D = false
        private val TAG = NonBlockingBluetoothSocket::class.java.simpleName
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CONNECT_TIMEOUT_MS = 15_000L

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

    private var mSock: BluetoothSocket? = null
    private var mIn: BufferedReader? = null
    private var mOut: OutputStream? = null
    private var queue: LinkedBlockingQueue<String>? = null

    @Throws(IOException::class)
    fun connect(device: BluetoothDevice) {
        if (D) Log.d(TAG, "${System.currentTimeMillis()} connect")

        try {
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        } catch (_: SecurityException) {
        }

        var sock: BluetoothSocket? = null
        try {
            sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
        } catch (e: Exception) {
            Log.w(TAG, "SPP UUID connect failed, falling back to reflection", e)
            try {
                sock?.close()
            } catch (_: IOException) {
            }
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                sock = m.invoke(device, 1) as BluetoothSocket
                sock.connect()
            } catch (e2: Exception) {
                Log.e(TAG, "createRfcommSocket fallback failed: $e2")
                throw IOException("Bluetooth connect failed", e2)
            }
        }

        mSock = sock
        mIn = BufferedReader(InputStreamReader(sock!!.inputStream), 128)
        mOut = sock.outputStream
        queue = LinkedBlockingQueue()
        start()
    }

    fun close() {
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

    @Throws(TimeoutException::class)
    fun readLine(timeout: Long): String {
        val line = try {
            queue?.poll(timeout, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            if (D) Log.e(TAG, "${System.currentTimeMillis()} readLine() interrupted: $e")
            null
        }
        if (line == null) {
            if (D) Log.d(TAG, "${System.currentTimeMillis()} readLine() timeout")
            throw TimeoutException(null as String?)
        }
        if (D) Log.d(TAG, "${System.currentTimeMillis()} readLine (${line.length}): $line")
        return line
    }

    @Throws(IOException::class)
    fun writeLine(line: String) {
        val out = mOut ?: throw IOException("socket closed")
        val payload = "$line\r"
        if (D) Log.d(TAG, "${System.currentTimeMillis()} writeLine: $payload")
        out.write(myGetBytes(payload))
        out.flush()
    }

    @Throws(IOException::class, TimeoutException::class)
    fun chat(send: String, expect: String, timeout: Long): String {
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
        name = "NonBlockingBluetoothSocket Thread"
        try {
            while (true) {
                val line = mIn?.readLine()?.trim().orEmpty()
                if (line.isNotEmpty()) queue?.add(line)
            }
        } catch (e: IOException) {
            if (D) Log.e(TAG, "${System.currentTimeMillis()} mReadThread exception: $e")
        }
    }
}
