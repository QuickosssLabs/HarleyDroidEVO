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

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.os.Bundle
import android.util.Log

class HarleyDroidGPS(context: Context) : LocationListener {

	companion object {
		private const val D = false
		private val TAG = HarleyDroidGPS::class.java.simpleName
		private const val TWO_MINUTES = 1000 * 60 * 2
	}

	private val mLocationManager =
		context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
	private var mCurrentBestLocation: Location? = null

	fun start() {
		if (D) Log.d(TAG, "start()")

		mCurrentBestLocation = null
		try {
			mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
		} catch (e: SecurityException) {
			Log.w(TAG, "GPS permission denied", e)
		} catch (e: IllegalArgumentException) {
			Log.w(TAG, "GPS provider unavailable", e)
		}
	}

	fun stop() {
		if (D) Log.d(TAG, "stop()")

		try {
			mLocationManager.removeUpdates(this)
		} catch (e: SecurityException) {
			Log.w(TAG, "GPS permission denied", e)
		}
	}

	fun getLocation(): String {
		if (D) Log.d(TAG, "getLocation() = $mCurrentBestLocation")

		val loc = mCurrentBestLocation ?: return ",,"

		return loc.longitude.toString() + "," +
			loc.latitude.toString() + "," +
			loc.altitude.toString()
	}

	protected fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
		if (currentBestLocation == null)
			return true

		val timeDelta = location.time - currentBestLocation.time
		val isSignificantlyNewer = timeDelta > TWO_MINUTES
		val isSignificantlyOlder = timeDelta < -TWO_MINUTES
		val isNewer = timeDelta > 0

		if (isSignificantlyNewer)
			return true
		else if (isSignificantlyOlder)
			return false

		val accuracyDelta = (location.accuracy - currentBestLocation.accuracy).toInt()
		val isLessAccurate = accuracyDelta > 0
		val isMoreAccurate = accuracyDelta < 0
		val isSignificantlyLessAccurate = accuracyDelta > 200

		val isFromSameProvider = isSameProvider(location.provider, currentBestLocation.provider)

		return when {
			isMoreAccurate -> true
			isNewer && !isLessAccurate -> true
			isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
			else -> false
		}
	}

	private fun isSameProvider(provider1: String?, provider2: String?): Boolean {
		if (provider1 == null)
			return provider2 == null
		return provider1 == provider2
	}

	override fun onLocationChanged(location: Location) {
		if (D) Log.d(TAG, "onLocationChanged($location)")

		if (mCurrentBestLocation == null || isBetterLocation(location, mCurrentBestLocation))
			mCurrentBestLocation = location
	}

	@Deprecated("Deprecated in API")
	override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
		if (D) Log.d(TAG, "onStatusChanged($status)")

		if (status == LocationProvider.OUT_OF_SERVICE)
			mCurrentBestLocation = null
	}

	override fun onProviderEnabled(provider: String) {
		if (D) Log.d(TAG, "onProviderEnabled()")
		mCurrentBestLocation = null
	}

	override fun onProviderDisabled(provider: String) {
		if (D) Log.d(TAG, "onProviderDisabled()")
		mCurrentBestLocation = null
	}
}
