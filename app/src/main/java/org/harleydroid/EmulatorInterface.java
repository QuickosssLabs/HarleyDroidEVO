//
// HarleyDroid: Harley Davidson J1850 Data Analyser for Android.
//
// Copyright (C) 2010-2012 Stelian Pop <stelian@popies.net>
// Copyright (C) 2026 Quickosss - HarleyDroid EVO (CAN / HDLAN support)
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//

package org.harleydroid;

import android.util.Log;

public class EmulatorInterface implements J1850Interface
{
	private static final boolean D = false;
	private static final String TAG = EmulatorInterface.class.getSimpleName();

	private static final int MAX_ERRORS = 10;

	private HarleyDroidService mHarleyDroidService;
	private HarleyData mHD;
	private PollThread mPollThread = null;
	private SendThread mSendThread = null;
	private final String mBusProtocol;
	private final boolean mCanBus;

	public EmulatorInterface(HarleyDroidService harleyDroidService) {
		this(harleyDroidService, BusProtocol.J1850);
	}

	public EmulatorInterface(HarleyDroidService harleyDroidService, String busProtocol) {
		mHarleyDroidService = harleyDroidService;
		mBusProtocol = busProtocol != null ? busProtocol : BusProtocol.J1850;
		mCanBus = BusProtocol.isCan(mBusProtocol);
	}

	public void connect(HarleyData hd) {
		if (D) Log.d(TAG, "connect: " + hd + " bus=" + mBusProtocol);

		mHD = hd;
		mHarleyDroidService.connected();
	}

	public void disconnect() {
		if (D) Log.d(TAG, "disconnect");

		if (mPollThread != null) {
			mPollThread.cancel();
			mPollThread = null;
		}
		if (mSendThread != null) {
			mSendThread.cancel();
			mSendThread = null;
		}
	}

	public void startSend(String type[], String ta[], String sa[],
						  String command[], String expect[],
						  int timeout[], int delay) {
		if (mCanBus) {
			if (mPollThread == null)
				startPoll();
			return;
		}
		if (D) Log.d(TAG, "send: " + type + "-" + ta + "-" +
					 sa + "-" + command + "-" + expect);

		if (mPollThread != null) {
			mPollThread.cancel();
			mPollThread = null;
		}
		if (mSendThread != null)
			mSendThread.cancel();
		mSendThread = new SendThread(type, ta, sa, command, expect, timeout, delay);
		mSendThread.start();
	}

	public void setSendData(String type[], String ta[], String sa[],
							String command[], String expect[],
							int timeout[], int delay) {
		if (mCanBus)
			return;
		if (D) Log.d(TAG, "setSendData");

		if (mSendThread != null)
			mSendThread.setData(type, ta, sa, command, expect, timeout, delay);
	}

	public void startPoll() {
		if (D) Log.d(TAG, "startPoll");

		if (mPollThread != null)
			mPollThread.cancel();
		mPollThread = new PollThread();
		mPollThread.start();
	}

	/** Build ASCII hex J1850 line with trailing CRC (as ELM would deliver). */
	private static String j1850Frame(byte[] payload) {
		byte crc = (byte) ((~J1850.crc(payload)) & 0xff);
		StringBuilder sb = new StringBuilder(payload.length * 2 + 2);
		for (byte b : payload)
			sb.append(String.format("%02X", b & 0xff));
		sb.append(String.format("%02X", crc & 0xff));
		return sb.toString();
	}

	private class PollThread extends Thread {
		private boolean stop = false;

		public void run() {
			int odo = 0;
			int fuel = 0;
			int errors = 0;
			int tick = 0;

			setName("EmulatorInterface: PollThread");
			mHarleyDroidService.startedPoll();

			while (!stop) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
				}

				tick++;
				int kmh = 40 + (tick % 60);
				int tempC = 70 + (tick % 10);

				if (mCanBus) {
					int odoKm = 12000 + tick;
					// Cycle gears 1–6 via RPM/speed (same estimation as HarleyCan)
					int gear = 1 + (tick / 8) % 6;
					double[] ratios = { 0, 3.342, 2.302, 1.714, 1.392, 1.176, 1.000 };
					int rpm = (int) Math.round(kmh * 22.0 * ratios[gear]);
					if (rpm < 900) rpm = 900;
					boolean neutral = (tick % 20 == 0);
					boolean clutch = (tick % 15 == 0);

					String[] lines = new String[] {
						String.format("521 00 %02X 00 00 00 00 00", kmh),
						String.format("5C0#00000000%06X", odoKm & 0xffffff),
						String.format("541 00 00 00 00 00 %02X 00 00", tempC),
						clutch ? "550 01 00 00 00 00 00" : "550 00 00 00 00 00 00",
						neutral ? "530 00 00 81 00 00 00 00 00" : "530 00 00 00 00 00 00 00 00",
						String.format("5C1 %02X %02X 00 00 00 00 00 00",
							((rpm * 4) >> 8) & 0xff, (rpm * 4) & 0xff)
					};
					for (String line : lines) {
						byte[] bytes = line.getBytes();
						mHD.setRaw(bytes);
						if (HarleyCan.parse(bytes, mHD))
							errors = 0;
						else
							++errors;
					}
				} else {
					int rpm = 900 + (tick % 50) * 20;
					int rpmRaw = rpm * 4;
					int speedRaw = kmh * 128;

					mHD.setOdometer(odo);
					odo += 150;
					mHD.setFuel(fuel);
					fuel += 50;
					if (tick % 20 == 0) {
						mHD.setCheckEngine(true);
						mHD.setTurnSignals(0);
					} else {
						mHD.setCheckEngine(false);
						mHD.setTurnSignals(3);
					}

					// Valid J1850 frames with CRC (same IDs as live bus)
					String[] lines = new String[] {
						j1850Frame(new byte[] {
							0x28, 0x1B, 0x10, 0x02,
							(byte) ((rpmRaw >> 8) & 0xff), (byte) (rpmRaw & 0xff)
						}),
						j1850Frame(new byte[] {
							0x48, 0x29, 0x10, 0x02,
							(byte) ((speedRaw >> 8) & 0xff), (byte) (speedRaw & 0xff)
						}),
						j1850Frame(new byte[] {
							(byte) 0xA8, 0x49, 0x10, 0x10, (byte) (tempC + 40)
						}),
						j1850Frame(new byte[] {
							(byte) 0xA8, 0x3B, 0x10, 0x03, 0x08
						}),
						(tick % 4 == 0)
							? j1850Frame(new byte[] { 0x48, 0x3B, 0x40, (byte) 0xA0 })
							: j1850Frame(new byte[] { 0x48, 0x3B, 0x40, 0x20 })
					};
					for (String line : lines) {
						byte[] bytes = line.getBytes();
						mHD.setRaw(bytes);
						if (J1850.parse(bytes, mHD))
							errors = 0;
						else
							++errors;
					}
				}

				if (errors > MAX_ERRORS) {
					mHarleyDroidService.disconnected(HarleyDroid.STATUS_TOOMANYERRORS);
					stop = true;
				}
			}
		}

		public void cancel() {
			stop = true;
		}
	}

	private class SendThread extends Thread {
		private boolean stop = false;
		private boolean newData = false;
		private String mType[], mTA[], mSA[], mCommand[], mExpect[];
		private int mTimeout[];
		private String mNewType[], mNewTA[], mNewSA[], mNewCommand[], mNewExpect[];
		private int mNewTimeout[];
		private int mDelay, mNewDelay;

		public SendThread(String type[], String ta[], String sa[], String command[], String expect[], int timeout[], int delay) {
			setName("EmulatorInterface: SendThread");
			mType = type;
			mTA = ta;
			mSA = sa;
			mCommand = command;
			mExpect = expect;
			mTimeout = timeout;
			mDelay = delay;
		}

		public void setData(String type[], String ta[], String sa[], String command[], String expect[], int timeout[], int delay) {
			synchronized (this) {
				mNewType = type;
				mNewTA = ta;
				mNewSA = sa;
				mNewCommand = command;
				mNewExpect = expect;
				mNewTimeout = timeout;
				mNewDelay = delay;
				newData = true;
			}
		}

		public void run() {

			mHarleyDroidService.startedSend();

			while (!stop) {

				synchronized (this) {
					if (newData) {
						mType = mNewType;
						mTA = mNewTA;
						mSA = mNewSA;
						mCommand = mNewCommand;
						mExpect = mNewExpect;
						mTimeout = mNewTimeout;
						mDelay = mNewDelay;
						newData = false;
					}
				}

				for (int i = 0; !stop && i < mCommand.length; i++) {

					byte[] data = new byte[3 + mCommand[i].length() / 2];
					data[0] = (byte)Integer.parseInt(mType[i], 16);
					data[1] = (byte)Integer.parseInt(mTA[i], 16);
					data[2] = (byte)Integer.parseInt(mSA[i], 16);
					for (int j = 0; j < mCommand[i].length() / 2; j++)
						data[j + 3] = (byte)Integer.parseInt(mCommand[i].substring(2 * j, 2 * j + 2), 16);

					String command = mCommand[i] + String.format("%02X", ((int)~J1850.crc(data)) & 0xff);

					if (D) Log.d(TAG, "send: " + mType[i] + "-" + mTA[i] + "-" +
							 mSA[i] + "-" + command + "-" + mExpect[i]);

					String line = "6CF1105901341167";

					byte[] bytes = line.getBytes();
					mHD.setRaw(bytes);
					J1850.parse(bytes, mHD);

					try {
						Thread.sleep(mTimeout[i]);
					} catch (InterruptedException e) {
					}
				}

				try {
					Thread.sleep(mDelay);
				} catch (InterruptedException e) {
				}
			}
		}

		public void cancel() {
			stop = true;
		}
	}
}
