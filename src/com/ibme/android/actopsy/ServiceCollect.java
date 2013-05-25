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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

// In order to start the service the application should run from the phone memory 
public class ServiceCollect extends Service implements SensorEventListener {

	// TODO: Make it a foreground service

	private static final String TAG = "ActopsyCollectService";

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;

	private ClassAccelerometry mAccelerometry;
	private ClassProfile mProfile;

	@Override
	public void onCreate() {
		Log.i(TAG, "Create");

		// TODO: Are we actually running in this thread???
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// android.os.Debug.waitForDebugger();

		long ts = System.currentTimeMillis();
		mAccelerometry = new ClassAccelerometry(this);
		mAccelerometry.init(ts);
		mProfile = new ClassProfile(this);
		mProfile.init(ts);

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "Start");
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		mSensorManager.unregisterListener(this);
		mAccelerometry.fini();
		mProfile.fini();
		Log.i(TAG, "Destroy");
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
		Log.i(TAG, "Bind");
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
		long ts = System.currentTimeMillis();
		float x = event.values[0];
		float y = event.values[1];
		float z = event.values[2];

		mAccelerometry.update(ts, x, y, z);
		mProfile.update(ts, x, y, z);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO: What's that?
	}
}