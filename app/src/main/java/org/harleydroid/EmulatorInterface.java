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

/**
 * Generates coherent fake bus traffic for dashboard testing (no Bluetooth).
 * Scripted ride loop only (idle → accel → cruise → decel).
 */
public class EmulatorInterface implements J1850Interface
{
	private static final boolean D = false;
	private static final String TAG = EmulatorInterface.class.getSimpleName();

	private static final int MAX_ERRORS = 10;
	private static final int TICK_MS = 400;

	/** Cruise Drive relative ratios (6th = 1.0), same as {@link HarleyCan}. */
	private static final double[] GEAR_RATIOS = {
		Double.NaN, 3.342, 2.302, 1.714, 1.392, 1.176, 1.000
	};
	private static final double RPM_PER_KMH = 22.0;
	private static final int IDLE_RPM = 900;
	private static final int REDLINE_RPM = 5500;

	/** Full ride loop length in ticks (~90 s at 400 ms). */
	private static final int CYCLE_TICKS = 220;

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

	/** J1850 gear nibble: bit position encodes gear (1→0x02 … 6→0x40). */
	private static byte gearPayload(int gear) {
		if (gear < 1 || gear > 6)
			return 0;
		return (byte) (1 << gear);
	}

	private static int rpmFor(int kmh, int gear) {
		if (gear < 1 || gear > 6 || kmh <= 0)
			return IDLE_RPM;
		int rpm = (int) Math.round(kmh * RPM_PER_KMH * GEAR_RATIOS[gear]);
		if (rpm < IDLE_RPM) rpm = IDLE_RPM;
		if (rpm > REDLINE_RPM) rpm = REDLINE_RPM;
		return rpm;
	}

	/**
	 * Coherent ride state for one tick inside a repeating cycle.
	 * Phases: idle N → 1st…5th accel/shift → cruise → brief signals → soft decel.
	 */
	private static final class RideState {
		int kmh;
		int rpm;
		int gear;       // 1–5 engaged, -1 when blank
		boolean neutral;
		boolean clutch;
		int turnSignals; // 0 / 1=R / 2=L / 3=hazard
		boolean checkEngine;
		int fuelBars;    // 0–15
		boolean fuelLow;
		int tempC;
		int odoKm;
	}

	private static RideState rideAt(int tick, int baseOdoKm) {
		RideState s = new RideState();
		int t = tick % CYCLE_TICKS;
		s.checkEngine = false;
		s.turnSignals = 0;
		s.clutch = false;
		s.neutral = false;
		s.gear = -1;

		// Fuel: start nearly full, slowly drain over the cycle (low near end)
		s.fuelBars = 14 - (t * 12) / CYCLE_TICKS;
		if (s.fuelBars < 1) s.fuelBars = 1;
		s.fuelLow = s.fuelBars <= 2;

		// Coolant: cold start → operating temp
		s.tempC = 45 + (t * 50) / 80;
		if (s.tempC > 95) s.tempC = 95;

		s.odoKm = baseOdoKm; // overwritten by PollThread with distance-based trip

		// --- timeline ---
		if (t < 8) {
			// Idle in neutral
			s.neutral = true;
			s.kmh = 0;
			s.rpm = 900 + (t % 3) * 20;
		} else if (t < 12) {
			// Clutch in, still N, blip throttle
			s.neutral = true;
			s.clutch = true;
			s.kmh = 0;
			s.rpm = 1200 + (t - 8) * 200;
		} else if (t < 36) {
			// 1st: pull away 0 → 28 km/h
			s.gear = 1;
			s.kmh = ((t - 12) * 28) / 24;
			s.rpm = rpmFor(Math.max(s.kmh, 5), 1);
		} else if (t < 40) {
			// Shift 1→2
			s.clutch = true;
			s.gear = -1;
			s.kmh = 28;
			s.rpm = 1600;
		} else if (t < 64) {
			// 2nd: 28 → 48
			s.gear = 2;
			s.kmh = 28 + ((t - 40) * 20) / 24;
			s.rpm = rpmFor(s.kmh, 2);
		} else if (t < 68) {
			s.clutch = true;
			s.gear = -1;
			s.kmh = 48;
			s.rpm = 1700;
		} else if (t < 92) {
			// 3rd: 48 → 70
			s.gear = 3;
			s.kmh = 48 + ((t - 68) * 22) / 24;
			s.rpm = rpmFor(s.kmh, 3);
		} else if (t < 96) {
			s.clutch = true;
			s.gear = -1;
			s.kmh = 70;
			s.rpm = 1800;
		} else if (t < 120) {
			// 4th: 70 → 95
			s.gear = 4;
			s.kmh = 70 + ((t - 96) * 25) / 24;
			s.rpm = rpmFor(s.kmh, 4);
		} else if (t < 124) {
			s.clutch = true;
			s.gear = -1;
			s.kmh = 95;
			s.rpm = 1900;
		} else if (t < 170) {
			// 5th cruise ~100–110 km/h with light variation
			s.gear = 5;
			int wobble = ((t / 3) % 5) - 2;
			s.kmh = 105 + wobble;
			s.rpm = rpmFor(s.kmh, 5);
		} else if (t < 190) {
			// Soft decel in 5th toward town speed
			s.gear = 5;
			s.kmh = 105 - ((t - 170) * 40) / 20;
			if (s.kmh < 40) s.kmh = 40;
			s.rpm = rpmFor(s.kmh, 5);
		} else if (t < 204) {
			// Down toward 2nd / clutch for stop
			s.clutch = true;
			s.gear = -1;
			s.kmh = 40 - ((t - 190) * 30) / 14;
			if (s.kmh < 5) s.kmh = 5;
			s.rpm = 1100;
		} else {
			// Stop, neutral
			s.neutral = true;
			s.kmh = 0;
			s.rpm = 900;
		}

		// Turn signals: left while rolling out of stop, right during cruise
		if (t >= 20 && t < 32) {
			// Blink left (~1.2 Hz with 400 ms ticks)
			s.turnSignals = (t % 2 == 0) ? 0x02 : 0;
		} else if (t >= 140 && t < 152) {
			s.turnSignals = (t % 2 == 0) ? 0x01 : 0;
		} else if (t >= 200 && t < 210) {
			// Hazard briefly while stopped (bike parking vibe) — every other tick
			s.turnSignals = (t % 2 == 0) ? 0x03 : 0;
		}

		return s;
	}

	private class PollThread extends Thread {
		private boolean stop = false;

		public void run() {
			int errors = 0;
			int tick = 0;
			final int baseOdoKm = 18420;
			/** Trip distance (km) — drives odometer + fuel at ~6.5 L/100km. */
			double tripKm = 0;
			double fuelAccum = 0;

			setName("EmulatorInterface: PollThread");
			mHarleyDroidService.startedPoll();

			while (!stop) {
				try {
					Thread.sleep(TICK_MS);
				} catch (InterruptedException e1) {
				}
				if (stop)
					break;

				tick++;
				RideState ride = rideAt(tick, baseOdoKm);
				double dKm = ride.kmh * TICK_MS / 3_600_000.0;
				tripKm += dKm;
				// 13 raw fuel units / km → average (50*13)/1 = 650 → 6.5 L/100km
				fuelAccum += 13.0 * dKm;
				int fuelPulse = Math.max(0, (int) Math.round(fuelAccum));
				// Session odo pulses: metric display units = raw/25 ≈ trip km
				int odoSession = Math.max(0, (int) Math.round(tripKm * 25));
				ride.odoKm = baseOdoKm + (int) tripKm;

				if (mCanBus) {
					boolean ok = emitCan(ride);
					if (ok)
						errors = 0;
					else
						++errors;
					// Odo display comes from absolute CAN frame (5C0); do not overwrite
					// with session pulses — that broke economy math and the odo reading.
					mHD.setFuel(fuelPulse);
					mHD.setFuelGauge(ride.fuelBars, ride.fuelLow);
					mHD.setTurnSignals(ride.turnSignals);
					mHD.setCheckEngine(ride.checkEngine);
					mHD.setNeutral(ride.neutral);
					mHD.setClutch(ride.clutch);
					if (ride.gear >= 1)
						mHD.setGear(ride.gear);
					else
						mHD.setGear(-1);
				} else {
					boolean ok = emitJ1850(ride, fuelPulse, odoSession);
					if (ok)
						errors = 0;
					else
						++errors;
				}

				if (errors > MAX_ERRORS) {
					mHarleyDroidService.disconnected(HarleyDroid.STATUS_TOOMANYERRORS);
					stop = true;
				}
			}
		}

		private boolean emitCan(RideState ride) {
			int rpmRaw = ride.rpm * 4;
			String[] lines = new String[] {
				String.format("521 00 %02X 00 00 00 00 00", ride.kmh & 0xff),
				String.format("5C0#00000000%06X", ride.odoKm & 0xffffff),
				String.format("541 00 00 00 00 00 %02X 00 00", ride.tempC & 0xff),
				ride.clutch ? "550 01 00 00 00 00 00" : "550 00 00 00 00 00 00",
				ride.neutral ? "530 00 00 81 00 00 00 00 00" : "530 00 00 00 00 00 00 00 00",
				String.format("5C1 %02X %02X 00 00 00 00 00 00",
					(rpmRaw >> 8) & 0xff, rpmRaw & 0xff)
			};
			boolean anyOk = false;
			for (String line : lines) {
				byte[] bytes = line.getBytes();
				mHD.setRaw(bytes);
				if (HarleyCan.parse(bytes, mHD))
					anyOk = true;
			}
			return anyOk;
		}

		private boolean emitJ1850(RideState ride, int fuelPulse, int odoSession) {
			int rpmRaw = ride.rpm * 4;
			int speedRaw = ride.kmh * 128;

			mHD.setOdometer(odoSession);
			mHD.setFuel(fuelPulse);
			// Turn / MIL also via frames below; setters keep UI in sync if CRC path lags
			mHD.setTurnSignals(ride.turnSignals);
			mHD.setCheckEngine(ride.checkEngine);

			// 0xA0 = neutral (+ clutch bit), 0x80 = clutch only, 0x20 = in gear
			final byte neutralClutch;
			if (ride.neutral)
				neutralClutch = (byte) 0xA0;
			else if (ride.clutch)
				neutralClutch = (byte) 0x80;
			else
				neutralClutch = 0x20;

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
					(byte) 0xA8, 0x49, 0x10, 0x10, (byte) (ride.tempC + 40)
				}),
				j1850Frame(new byte[] {
					(byte) 0xA8, 0x3B, 0x10, 0x03, gearPayload(ride.gear)
				}),
				j1850Frame(new byte[] {
					(byte) 0xA8, (byte) 0x83, 0x61,
					(byte) (ride.fuelLow ? 0x92 : 0x12),
					(byte) (ride.fuelBars & 0x0f)
				}),
				j1850Frame(new byte[] {
					0x48, 0x3B, 0x40, neutralClutch
				}),
				j1850Frame(new byte[] {
					0x48, (byte) 0xDA, 0x40, 0x39, (byte) (ride.turnSignals & 0x03)
				}),
				j1850Frame(new byte[] {
					0x68, (byte) 0x88, 0x10, (byte) (ride.checkEngine ? 0x83 : 0x03)
				}),
			};

			boolean anyOk = false;
			for (String line : lines) {
				byte[] bytes = line.getBytes();
				mHD.setRaw(bytes);
				if (J1850.parse(bytes, mHD))
					anyOk = true;
			}
			// Force ride state — J1850 clutch/neutral nibble cannot express
			// "clutch in + not neutral" cleanly with the known 0x20/0xA0 values.
			mHD.setNeutral(ride.neutral);
			mHD.setClutch(ride.clutch);
			if (ride.gear >= 1)
				mHD.setGear(ride.gear);
			else if (ride.neutral || ride.clutch)
				mHD.setGear(-1);
			return anyOk;
		}

		public void cancel() {
			stop = true;
			interrupt();
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

					String line;
					if ("40".equalsIgnoreCase(mTA[i])) {
						line = j1850Frame(new byte[]{0x6c, (byte)0xf1, 0x40, 0x59, 0x41, 0x51});
					} else if ("60".equalsIgnoreCase(mTA[i])) {
						line = j1850Frame(new byte[]{0x6c, (byte)0xf1, 0x60, 0x59, (byte)0xd0, 0x64});
					} else if (mCommand[i] != null && mCommand[i].startsWith("19")) {
						line = j1850Frame(new byte[]{0x6c, (byte)0xf1, 0x10, 0x59, 0x01, 0x34});
					} else {
						line = j1850Frame(new byte[]{0x6c, (byte)0xf1, 0x10, 0x59, 0x01, 0x34});
					}

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
