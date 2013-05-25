//
// Copyright (C) 2013 Maxim Osipov
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//

package com.ibme.android.actopsy;

import com.ibme.android.actopsy.R;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.LayoutParams;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.internal.widget.IcsAdapterView;
import com.actionbarsherlock.internal.widget.IcsAdapterView.OnItemSelectedListener;
import com.actionbarsherlock.internal.widget.IcsLinearLayout;
import com.actionbarsherlock.internal.widget.IcsSpinner;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityProfile extends SherlockActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = "Actopsy";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG, "Started");
		setContentView(R.layout.profile);

		// Initialize preferences
		PreferenceManager.setDefaultValues(this, R.layout.preferences, false);

		// Start services
		Intent service = new Intent(this, ServiceCollect.class);
		this.startService(service);
		service = new Intent(this, ServiceUpload.class);
		this.startService(service);

		// Initialize charts
		chartInit();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_actopsy, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_profile:
			return super.onOptionsItemSelected(item);
		case R.id.menu_settings:
			Intent settingsActivity = new Intent(getBaseContext(), ActivitySettings.class);
			startActivity(settingsActivity);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.registerOnSharedPreferenceChangeListener(this);
		doBindService();
	}

	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.unregisterOnSharedPreferenceChangeListener(this);
		doUnbindService();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// TODO: This option actually doesn't exist now, maybe remove?
		if (key.equals("checkboxService")) {
			if (sharedPreferences.getBoolean(key, true)) {
				doBindService();
			} else {
				doUnbindService();
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// Service interaction
	///////////////////////////////////////////////////////////////////////////
	boolean mBound;
	Messenger mService = null;

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			//			case ActopsyService.MSG_ACC_DATA:
			//				float x, y, z;
			//				long ts;
			//				Bundle b = msg.getData();
			//				x = b.getFloat("X");
			//				y = b.getFloat("Y");
			//				z = b.getFloat("Z");
			//				ts = b.getLong("TS");
			//				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	final Messenger mMessenger = new Messenger(new IncomingHandler());

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			try {
				Message msg = Message.obtain(null, ServiceCollect.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				Log.i(TAG, "Register client failed");
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
		}
	};

	void doBindService() {
		bindService(new Intent(ActivityProfile.this, ServiceCollect.class), mConnection, Context.BIND_AUTO_CREATE);
		mBound = true;
		Log.i(TAG, "Bind");
	}

	void doUnbindService() {
		if (mBound) {
			if (mService != null) {
				try {
					Message msg = Message.obtain(null, ServiceCollect.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) {
					Log.i(TAG, "Unregister client failed");
				}
			}

			// Detach our existing connection.
			unbindService(mConnection);
			mBound = false;
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// Activity profile plot
	///////////////////////////////////////////////////////////////////////////
	private GraphicalView[] mChartView;
	private XYMultipleSeriesRenderer[] mRenderer;
	private XYMultipleSeriesDataset[] mDataset;
	private XYSeriesRenderer[] mAvgRenderer;
	private TimeSeries[] mAvgSeries;
	private XYSeriesRenderer[] mCurrRenderer;
	private TimeSeries[] mCurrSeries;
	private XYSeriesRenderer[] mRefRenderer;
	private TimeSeries[] mRefSeries;
	private int weekday;

	private void chartInit() {
		LinearLayout[] layout = new LinearLayout[7];
		mChartView = new GraphicalView[7];
		mRenderer = new XYMultipleSeriesRenderer[7];
		mDataset = new XYMultipleSeriesDataset[7];
		mAvgRenderer = new XYSeriesRenderer[7];
		mAvgSeries = new TimeSeries[7];
		mCurrRenderer = new XYSeriesRenderer[7];
		mCurrSeries = new TimeSeries[7];
		mRefRenderer = new XYSeriesRenderer[7];
		mRefSeries = new TimeSeries[7];

		layout[0] = (LinearLayout) findViewById(R.id.linearLayoutActMON);
		layout[1] = (LinearLayout) findViewById(R.id.linearLayoutActTUE);
		layout[2] = (LinearLayout) findViewById(R.id.linearLayoutActWED);
		layout[3] = (LinearLayout) findViewById(R.id.linearLayoutActTHU);
		layout[4] = (LinearLayout) findViewById(R.id.linearLayoutActFRI);
		layout[5] = (LinearLayout) findViewById(R.id.linearLayoutActSAT);
		layout[6] = (LinearLayout) findViewById(R.id.linearLayoutActSUN);

		// find week day number
		long ts = System.currentTimeMillis();
		SimpleDateFormat fmt = new SimpleDateFormat("EEE");
		String today = new String(fmt.format(new Date(ts)));
		weekday = 0;
		while (weekday < ClassConsts.DAYS.length && !ClassConsts.DAYS[weekday].equals(today))
			weekday++;

		// initialize reference values
		float[] ref = chartGetRef();

		// initialise charts
		for (int i=0; i<7; i++) {
			mDataset[i] = new XYMultipleSeriesDataset();
			mRenderer[i] = new XYMultipleSeriesRenderer();
			mAvgRenderer[i] = new XYSeriesRenderer();
			mAvgSeries[i] = new TimeSeries(Integer.toString(i));
			mCurrRenderer[i] = new XYSeriesRenderer();
			mCurrSeries[i] = new TimeSeries(Integer.toString(i));
			mRefRenderer[i] = new XYSeriesRenderer();
			mRefSeries[i] = new TimeSeries(Integer.toString(i));

			ClassProfile.Values[] vals = new ClassProfile(this).get(i);
			for(int j=0; j<ClassProfile.LENGTH; j++)
			{
				mAvgSeries[i].add(j*ClassConsts.MILLIDAY/ClassProfile.LENGTH, vals[j].acc_avg);
				mCurrSeries[i].add(j*ClassConsts.MILLIDAY/ClassProfile.LENGTH, vals[j].acc_curr);
				mRefSeries[i].add(j*ClassConsts.MILLIDAY/ClassProfile.LENGTH, ref[j]);
			}
			mAvgRenderer[i].setColor(Color.TRANSPARENT);
			mAvgRenderer[i].setDisplayChartValues(false);
			mAvgRenderer[i].setFillBelowLine(true);
			mCurrRenderer[i].setDisplayChartValues(false);
			mCurrRenderer[i].setFillBelowLine(false);
			mRefRenderer[i].setColor(Color.TRANSPARENT);
			mRefRenderer[i].setDisplayChartValues(false);
			mRefRenderer[i].setFillBelowLine(true);
			// Highlight current day
			if (i == weekday) {
				mAvgRenderer[i].setFillBelowLineColor(Color.argb(0xd0, 0, 0x41, 0x67));
				mCurrRenderer[i].setColor(Color.argb(0xff, 0, 0x21, 0x47));
				mRefRenderer[i].setFillBelowLineColor(Color.argb(0x60, 0xf0, 0xe0, 0x00));
				mRenderer[i].setLabelsColor(Color.argb(0xff, 0, 0x21, 0x47));
				mRenderer[i].setXLabelsColor(Color.argb(0xff, 0, 0x21, 0x47));
			} else {
				mAvgRenderer[i].setFillBelowLineColor(Color.argb(0x60, 0, 0x41, 0x67));
				mCurrRenderer[i].setColor(Color.argb(0x80, 0, 0x21, 0x47));
				mRefRenderer[i].setFillBelowLineColor(Color.argb(0x20, 0xf0, 0xe0, 0x00));
				mRenderer[i].setLabelsColor(Color.argb(0x80, 0, 0x21, 0x47));
				mRenderer[i].setXLabelsColor(Color.argb(0x80, 0, 0x21, 0x47));
			}

			mRenderer[i].setChartTitleTextSize(getResources().getDimension(R.dimen.chart_font_large));
			mRenderer[i].setChartTitle(ClassConsts.DAYS[i]);
			mRenderer[i].setShowLabels(true);
			mRenderer[i].setLabelsTextSize(getResources().getDimension(R.dimen.chart_font_small));
			mRenderer[i].setYLabels(0);
			mRenderer[i].setYLabelsColor(0, Color.TRANSPARENT);
			mRenderer[i].setXLabels(0);
			mRenderer[i].setXAxisMin(0);
			mRenderer[i].setXAxisMax(ClassConsts.MILLIDAY);
			mRenderer[i].addXTextLabel(0, "00:00");
			mRenderer[i].addXTextLabel(ClassConsts.MILLIDAY/4, "6:00");
			mRenderer[i].addXTextLabel(ClassConsts.MILLIDAY/2, "12:00");
			mRenderer[i].addXTextLabel(ClassConsts.MILLIDAY/4*3, "18:00");
			mRenderer[i].addXTextLabel(ClassConsts.MILLIDAY, "24:00");
			mRenderer[i].setShowLegend(false);
			mRenderer[i].setShowAxes(false);
			mRenderer[i].setShowGrid(false);
			mRenderer[i].setZoomEnabled(false);
			mRenderer[i].setExternalZoomEnabled(false);
			mRenderer[i].setAntialiasing(true);
			mRenderer[i].setInScroll(true);
			mRenderer[i].setPanEnabled(false, false);
			mRenderer[i].setApplyBackgroundColor(true);
			mRenderer[i].setBackgroundColor(Color.WHITE);
			mRenderer[i].setMarginsColor(Color.WHITE);
			mRenderer[i].setPointSize(4);
			mRenderer[i].setMargins(new int[] {
					0, (int)getResources().getDimension(R.dimen.chart_margin),
					0, (int)getResources().getDimension(R.dimen.chart_margin) });

			mDataset[i].addSeries(mAvgSeries[i]);
			mDataset[i].addSeries(mCurrSeries[i]);
			mDataset[i].addSeries(mRefSeries[i]);
			mRenderer[i].addSeriesRenderer(mAvgRenderer[i]);
			mRenderer[i].addSeriesRenderer(mCurrRenderer[i]);
			mRenderer[i].addSeriesRenderer(mRefRenderer[i]);
			mChartView[i] = ChartFactory.getTimeChartView(this, mDataset[i], mRenderer[i], "HH:mm");
			layout[i].addView(mChartView[i]);
		}
	}
	
	float[] chartGetRef() {
		float[] ref = new float[ClassProfile.LENGTH];
		float max_level = 3;
		int i = 0;
		int j;
		// morning (0:00 - 6:00)
		for (; i < ClassProfile.LENGTH/24 * 6; i++) {
			ref[i] = 0;
		}
		// raise (6:00 - 8:00)
		j = i;
		for (; i < ClassProfile.LENGTH/24 * 9; i++) {
			// scale 6..8 to pi..2pi
			float arg = (float) ((float) ((i-j)/((float)(ClassProfile.LENGTH/24*3)) * Math.PI) + Math.PI);
			ref[i] = (float) ((Math.cos(arg)+1)*max_level/2);
		}
		// day (8:00 - 21:00)
		for (; i < ClassProfile.LENGTH/24 * 21; i++) {
			ref[i] = max_level;
		}
		// fall (21:00 - 23:00)
		j = i;
		for (; i < ClassProfile.LENGTH; i++) {
			// scale 21..23 to 0..pi
			float arg = (float) ((float) ((i-j)/((float)(ClassProfile.LENGTH/24*3)) * Math.PI));
			ref[i] = (float) ((Math.cos(arg)+1)*max_level/2);
		}
		return ref;
	}
}