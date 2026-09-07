//
// HarleyDroid: Harley Davidson J1850 Data Analyser for Android.
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

package org.harleydroid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import com.google.android.material.button.MaterialButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class HarleyDroidDiagnosticsView implements HarleyDataDiagnosticsListener, OnItemClickListener, OnClickListener
{
	private static final boolean D = false;
	private static final String TAG = HarleyDroidDiagnosticsView.class.getSimpleName();

	public static final int UPDATE_VIN = 1;
	public static final int UPDATE_ECMPN = 2;
	public static final int UPDATE_ECMCALID = 3;
	public static final int UPDATE_ECMSWLEVEL = 4;
	public static final int UPDATE_MODULE_DTC = 5;

	private Activity mActivity;
	private HarleyDroidDiagnosticsViewHandler mHandler;

	private MaterialButton mViewVIN;
	private TextView mViewECMPN;
	private TextView mViewECMCalID;
	private TextView mViewECMSWLevel;
	private ListView mViewEcmDtc;
	private ListView mViewAbsDtc;
	private ListView mViewTsmDtc;
	private int mInstalledLayoutRes = 0;
	private boolean mInstalledPortrait;

	public HarleyDroidDiagnosticsView(Activity activity) {
		mActivity = activity;
		mHandler = new HarleyDroidDiagnosticsViewHandler(this);
	}

	private void installContent(int layoutRes) {
		ViewGroup container = mActivity.findViewById(R.id.content_container);
		if (container != null) {
			container.removeAllViews();
			LayoutInflater.from(mActivity).inflate(layoutRes, container, true);
		} else {
			mActivity.setContentView(layoutRes);
		}
	}

	public void changeView(boolean portrait) {
		if (D) Log.d(TAG, "changeView portrait=" + portrait);

		int view = portrait ? R.layout.portrait_diag : R.layout.landscape_diag;

		ViewGroup container = mActivity.findViewById(R.id.content_container);
		boolean contentReady = container != null && container.getChildCount() > 0
				&& mInstalledLayoutRes == view
				&& mInstalledPortrait == portrait;
		if (contentReady) {
			return;
		}

		mInstalledLayoutRes = view;
		mInstalledPortrait = portrait;
		installContent(view);

		mViewVIN = (MaterialButton) mActivity.findViewById(R.id.vin_field);
		mViewECMPN = (TextView) mActivity.findViewById(R.id.ecmpn_field);
		mViewECMCalID = (TextView) mActivity.findViewById(R.id.ecmcalid_field);
		mViewECMSWLevel = (TextView) mActivity.findViewById(R.id.ecmswlevel_field);
		mViewEcmDtc = (ListView) mActivity.findViewById(R.id.ecmdtc_field);
		mViewAbsDtc = (ListView) mActivity.findViewById(R.id.absdtc_field);
		mViewTsmDtc = (ListView) mActivity.findViewById(R.id.tsmdtc_field);

		mViewVIN.setSoundEffectsEnabled(false);
		mViewVIN.setOnClickListener(this);
		bindDtcList(mViewEcmDtc);
		bindDtcList(mViewAbsDtc);
		bindDtcList(mViewTsmDtc);
	}

	private void bindDtcList(ListView list) {
		if (list == null) return;
		list.setSoundEffectsEnabled(false);
		list.setOnItemClickListener(this);
	}

	public void handleMessage(Message msg) {
		if (D) Log.d(TAG, "handleMessage " + msg.what);

		switch (msg.what) {
		case UPDATE_VIN:
			drawVIN(msg.getData().getString("vin"));
			break;
		case UPDATE_ECMPN:
			drawECMPN(msg.getData().getString("ecmpn"));
			break;
		case UPDATE_ECMCALID:
			drawECMCalID(msg.getData().getString("ecmcalid"));
			break;
		case UPDATE_ECMSWLEVEL:
			drawECMSWLevel(msg.arg1);
			break;
		case UPDATE_MODULE_DTC: {
			String name = msg.getData().getString("module");
			DtcModule module = null;
			if (name != null) {
				try {
					module = DtcModule.valueOf(name);
				} catch (IllegalArgumentException ignored) {
				}
			}
			drawModuleDtc(module, msg.getData().getStringArray("dtc"));
			break;
		}
		}
	}

	public void onVINChanged(String vin) {
		Message m = mHandler.obtainMessage(HarleyDroidDiagnosticsView.UPDATE_VIN);
		Bundle b = new Bundle();
		b.putString("vin", vin);
		m.setData(b);
		m.sendToTarget();
	}

	public void onECMPNChanged(String ecmPN) {
		Message m = mHandler.obtainMessage(HarleyDroidDiagnosticsView.UPDATE_ECMPN);
		Bundle b = new Bundle();
		b.putString("ecmpn", ecmPN);
		m.setData(b);
		m.sendToTarget();
	}

	public void onECMCalIDChanged(String ecmCalID) {
		Message m = mHandler.obtainMessage(HarleyDroidDiagnosticsView.UPDATE_ECMCALID);
		Bundle b = new Bundle();
		b.putString("ecmcalid", ecmCalID);
		m.setData(b);
		m.sendToTarget();
	}

	public void onECMSWLevelChanged(int ecmSWLevel) {
		mHandler.obtainMessage(HarleyDroidDiagnosticsView.UPDATE_ECMSWLEVEL, ecmSWLevel, -1).sendToTarget();
	}

	public void onModuleDtcChanged(DtcModule module, String[] dtc) {
		Message m = mHandler.obtainMessage(HarleyDroidDiagnosticsView.UPDATE_MODULE_DTC);
		Bundle b = new Bundle();
		b.putString("module", module.name());
		b.putStringArray("dtc", dtc);
		m.setData(b);
		m.sendToTarget();
	}

	public void drawAll(HarleyData hd) {
		if (hd != null) {
			drawVIN(hd.getVIN());
			drawECMPN(hd.getECMPN());
			drawECMCalID(hd.getECMCalID());
			drawECMSWLevel(hd.getECMSWLevel());
			for (DtcModule module : DtcModule.values())
				drawModuleDtc(module, hd.getDtc(module));
		} else {
			drawVIN("");
			drawECMPN("");
			drawECMCalID("");
			drawECMSWLevel(-1);
			for (DtcModule module : DtcModule.values())
				drawModuleDtc(module, null);
		}
	}

	public void drawVIN(String value) {
		if (mViewVIN != null)
			mViewVIN.setText(value);
	}

	public void drawECMPN(String value) {
		if (mViewECMPN != null)
			mViewECMPN.setText(value);
	}

	public void drawECMCalID(String value) {
		if (mViewECMCalID != null)
			mViewECMCalID.setText(value);
	}

	public void drawECMSWLevel(int value) {
		if (mViewECMSWLevel != null) {
			if (value == -1)
				mViewECMSWLevel.setText("");
			else
				mViewECMSWLevel.setText("0x" + Integer.toString(value, 16));
		}
	}

	public void drawModuleDtc(DtcModule module, String[] dtc) {
		if (module == null) return;
		ListView list = listFor(module);
		if (list == null) return;

		ArrayList<String> items = new ArrayList<String>();
		if (dtc != null) {
			for (int i = 0; i < dtc.length; i++)
				items.add(dtc[i]);
		}
		if (items.isEmpty())
			items.add(mActivity.getString(R.string.dtc_empty));
		list.setAdapter(new ArrayAdapter<String>(mActivity, R.layout.dtc_item, items));
	}

	private ListView listFor(DtcModule module) {
		switch (module) {
		case ECM: return mViewEcmDtc;
		case ABS: return mViewAbsDtc;
		case TSM: return mViewTsmDtc;
		default: return null;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View view, int pos, long id) {
		String dtc = ((TextView)view).getText().toString();
		if (dtc.equals(mActivity.getString(R.string.dtc_empty)))
			return;
		if (D) Log.i(TAG, "Clicked on [" + dtc + "]");
		Toast.makeText(
			mActivity.getApplicationContext(),
			DtcDescriptions.lookupOrUnknown(mActivity, dtc),
			Toast.LENGTH_LONG
		).show();
	}

	@Override
	public void onClick(View v) {
		CharSequence text = mViewVIN.getText();
		if (text.length() == 17)
			VINDecoder.show(mActivity, text);
	}

	static class HarleyDroidDiagnosticsViewHandler extends Handler {
		private final WeakReference<HarleyDroidDiagnosticsView> mHarleyDroidDiagnosticsView;

	    HarleyDroidDiagnosticsViewHandler(HarleyDroidDiagnosticsView harleyDroidDiagnosticsView) {
	        super(Looper.getMainLooper());
	        mHarleyDroidDiagnosticsView = new WeakReference<HarleyDroidDiagnosticsView>(harleyDroidDiagnosticsView);
	    }

		@Override
		public void handleMessage(Message msg) {
			HarleyDroidDiagnosticsView hddv = mHarleyDroidDiagnosticsView.get();
			if (hddv != null)
				hddv.handleMessage(msg);
		}
	}
}
