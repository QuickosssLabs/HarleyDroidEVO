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

interface J1850Interface {
	/** Connects to the J1850 bus. HarleyDroidService.connected() will be called upon completion. */
	fun connect(hd: HarleyData)

	/** Disconnects immediately from the J1850 bus. */
	fun disconnect()

	/** Starts polling. HarleyDroidService.startedPoll() will be called when polling begins. */
	fun startPoll()

	/** Starts sending commands. HarleyDroidService.startedSend() will be called when send begins. */
	fun startSend(
		type: Array<String>,
		ta: Array<String>,
		sa: Array<String>,
		command: Array<String>,
		expect: Array<String>,
		timeout: IntArray,
		delay: Int
	)

	/** Changes the data to be used in the send thread. */
	fun setSendData(
		type: Array<String>,
		ta: Array<String>,
		sa: Array<String>,
		command: Array<String>,
		expect: Array<String>,
		timeout: IntArray,
		delay: Int
	)
}
