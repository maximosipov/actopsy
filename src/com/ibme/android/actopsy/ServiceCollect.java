//
// Copyright (C) 2013, Maxim Osipov
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
//  - Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//  - Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//  - Neither the name of the University of Oxford nor the names of its
//    contributors may be used to endorse or promote products derived from this
//    software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
// IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
// BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
// LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
// OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
// OF THE POSSIBILITY OF SUCH DAMAGE.
//

package com.ibme.android.actopsy;

import com.ibme.android.actopsy.R;

import java.util.ArrayList;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

// In order to start the service the application should run from the phone memory 
public class ServiceCollect extends Service implements
	SensorEventListener, LocationListener, OnSharedPreferenceChangeListener {

	// TODO: Make it a foreground service

	private static final String TAG = "ActopsyCollectService";

	private SensorManager mSensorManager;
	private Sensor mSensorAccelerometer;
	private Sensor mSensorLight;
	private LocationManager mLocationManager;

	private ClassAccelerometry mAccelerometry;
	private ClassLight mLight;
	private ClassLocation mLocation;
	private ClassProfile mProfile;

	@Override
	public void onCreate() {
		// TODO: Are we actually running in this thread???
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// android.os.Debug.waitForDebugger();

		// Values should match android definitions
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int sdelay = Integer.valueOf(prefs.getString("listSamplingRate", "3"));
		new ClassEvents(TAG, "INFO", "Sampling rate " + sdelay);
		boolean loc = prefs.getBoolean("checkboxLocation", false);

		long ts = System.currentTimeMillis();
		mAccelerometry = new ClassAccelerometry(this);
		mAccelerometry.init(ts);
		mLight = new ClassLight(this);
		mLight.init(ts);
		mProfile = new ClassProfile(this);
		mProfile.init(ts);

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensorAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
		mSensorManager.registerListener(this, mSensorAccelerometer, sdelay);
		mSensorManager.registerListener(this, mSensorLight, sdelay);

		mLocation = new ClassLocation(this);
		mLocation.init(ts);
		if (loc) {
			mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
			mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		new ClassEvents(TAG, "INFO", "Started");

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		if (mLocationManager != null) {
			mLocationManager.removeUpdates(this);
		}
		mLocation.fini();
		mSensorManager.unregisterListener(this);
		mSensorManager.unregisterListener(this);
		mLight.fini();
		mAccelerometry.fini();
		mProfile.fini();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.unregisterOnSharedPreferenceChangeListener(this);

		new ClassEvents(TAG, "INFO", "Destroyed");
	}

	///////////////////////////////////////////////////////////////////////////
	// Messages interface
	///////////////////////////////////////////////////////////////////////////
	public static final int MSG_REGISTER_CLIENT = 1;
	public static final int MSG_UNREGISTER_CLIENT = 2;

	private ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	@Override
	public IBinder onBind(Intent intent) {
		return mMessenger.getBinder();
	}

	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

//	private void sendRefreshProgress(String status)
//	{
//		for (int i=mClients.size()-1; i>=0; i--) {
//			try {
//				Message m = Message.obtain(null, MSG_REFRESH_PROGRESS, 0, 0);
//				Bundle b = new Bundle();
//				b.putString("status", status);
//				m.setData(b);
//				mClients.get(i).send(m);
//			} catch (RemoteException e) {
//				mClients.remove(i);
//			}
//		}
//	}

	///////////////////////////////////////////////////////////////////////////
	// Sensors interface
	///////////////////////////////////////////////////////////////////////////
	@Override
	public void onSensorChanged(SensorEvent event) {
		// event.timestamp has a different meaning on different android versions
		long ts = System.currentTimeMillis();

		int type = event.sensor.getType();
		if (type == Sensor.TYPE_ACCELEROMETER) {
			float x = event.values[0];
			float y = event.values[1];
			float z = event.values[2];
			mAccelerometry.update(ts, x, y, z);
			mProfile.update(ts, x, y, z);
		} else if (type == Sensor.TYPE_LIGHT) {
			float lux = event.values[0];
			mLight.update(ts, lux);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO: What's that?
		new ClassEvents(TAG, "INFO", "Accuracy changed");
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals("listSamplingRate")) {
			String sdelay = prefs.getString(key, "3");
			new ClassEvents(TAG, "INFO", "Sampling rate " + sdelay);
			mSensorManager.unregisterListener(this);
			mSensorManager.unregisterListener(this);
			mSensorManager.registerListener(this, mSensorAccelerometer, Integer.valueOf(sdelay));
			mSensorManager.registerListener(this, mSensorLight, Integer.valueOf(sdelay));
		} else if (key.equals("checkboxLocation")) {
			boolean loc = prefs.getBoolean("checkboxLocation", false);
			if (loc) {
				mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
				mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
			} else {
				if (mLocationManager != null) {
					mLocationManager.removeUpdates(this);
					mLocation.fini();
				}
			}
		}
	}

	@Override
	public void onLocationChanged(Location loc) {
		long ts = loc.getTime();
		double lat = loc.getLatitude();
		double lon = loc.getLongitude();
		if (mLocation != null) {
			mLocation.update(ts, lat, lon);
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
	}
}