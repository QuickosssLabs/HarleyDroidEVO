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

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.UUID

class NonBlockingBluetoothSocket : NonBlockingLineTransport() {

    companion object {
        private const val D = false
        private val TAG = NonBlockingBluetoothSocket::class.java.simpleName
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var mSock: BluetoothSocket? = null

    @Throws(IOException::class)
    fun connect(context: Context, device: BluetoothDevice) {
        if (D) Log.d(TAG, "${System.currentTimeMillis()} connect")

        try {
            val adapter =
                (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.cancelDiscovery()
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
        startStreams(sock!!.inputStream, sock.outputStream, "NonBlockingBluetoothSocket Thread")
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
