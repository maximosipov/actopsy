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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

// In order to start the service the application should run from the phone memory.
// This one wakes up every 24 hours and attempts to upload data (if upload is enabled)
// or just removes old files.
public class ServiceUpload extends Service implements OnSharedPreferenceChangeListener {

	private static final String TAG = "ActopsyUploadService";

	private long mDeleteAge; // in milliseconds

	ConnectivityManager mConnectivityManager;
	ConnectivityReceiver mConnectivityReceiver;
	AlarmManager mAlarmManager;
	AlarmReceiver mAlarmReceiver;

	@Override
	public void onCreate() {
		Log.i(TAG, "Create");

		// TODO: Are we actually running in this thread???
		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// android.os.Debug.waitForDebugger();
		long ts = System.currentTimeMillis();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int localStorage = Integer.valueOf(prefs.getString("editLocalStorage", "10")); 
		mDeleteAge = localStorage*ClassConsts.MILLIDAY;

		// Prepare for connectivity tracking
		mConnectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		mConnectivityReceiver = new ConnectivityReceiver();
		registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

		// Prepare for periodic activation
		mAlarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		mAlarmReceiver = new AlarmReceiver();
		Intent intent = new Intent(ClassConsts.UPLOAD_ALARM);
		PendingIntent pintent = PendingIntent.getBroadcast(this, 0, intent, 0);
		registerReceiver(mAlarmReceiver, new IntentFilter(ClassConsts.UPLOAD_ALARM));
		mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, ts + 1000, ClassConsts.UPLOAD_PERIOD, pintent);
	}

	private File[] getFiles() {
		File[] names = new File[0];
		File root = Environment.getExternalStorageDirectory();
		// TODO: Fix it to check and wait for storage
		try {
			if (root.canRead()){
				File folder = new File(root, ClassConsts.FILES_ROOT);
				if (folder.exists()) {
					// build list of zip files from sdcard
					names = folder.listFiles(new FilenameFilter() {
						public boolean accept(File dir, String name) {
							return name.toLowerCase().endsWith(".zip");
						}
					});
				}
			} else {
				Log.e(TAG, "SD card is not readable");
			}
		} catch (Exception e) {
			Log.e(TAG, "Could not open file: " + e.getMessage());
		}
		return names;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "Start");
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.registerOnSharedPreferenceChangeListener(this);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mConnectivityReceiver);
		unregisterReceiver(mAlarmReceiver);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.unregisterOnSharedPreferenceChangeListener(this);
		Log.i(TAG, "Destroy");
	}

	// Upload files if connectivity is available
	public class ConnectivityReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
			if (activeNetwork != null && activeNetwork.isConnected() && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
				// can upload
				Log.d(TAG, "WiFi On");
				File[] files = getFiles();
				if (files != null) {
					for (int i = 0; i < files.length; i++) {
						new UploaderTask().execute(files[i]);
					}
				}
			} else {
				// stop uploads
				Log.d(TAG, "WiFi Off");
			}
		}
	}

	// Remove old files
	public class AlarmReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Alarm");
			File[] files = getFiles();
			long now = System.currentTimeMillis();

			// Delete old files
			for (int i = 0; i < files.length; i ++) {
				long age = files[i].lastModified();
				if (age + mDeleteAge < now) {
					if (files[i].delete()) {
						Log.i(TAG, "Delete successful: " + files[i].getName());
					} else {
						Log.e(TAG, "Delete failed: " + files[i].getName());
					}
				}
			}
		}
	}

	private class UploaderTask extends AsyncTask<File, Void, Void> {
		@Override
		protected Void doInBackground(File... files) {
			try {
				for (int i = 0; i < files.length; i ++) {
					HttpClient httpclient = new DefaultHttpClient();
					HttpPost httppost = new HttpPost(ClassConsts.UPLOAD_URL);

					// Prepare HTTP request
					MultipartEntity entity = new MultipartEntity();
					entity.addPart( "MAX_FILE_SIZE", new StringBody(ClassConsts.UPLOAD_SIZE));
					entity.addPart( "FILE", new FileBody(files[i]));        	 
					httppost.setEntity( entity );

					// Execute HTTP request
					HttpResponse response = httpclient.execute(httppost);
					HttpEntity rsp = response.getEntity();
					if (rsp != null) {
						if (EntityUtils.toString(rsp) == "OK") {
							files[i].delete();
							Log.i(TAG, "Upload successful: " + files[i].getName());
						} else {
							Log.e(TAG, "Upload failed: " + EntityUtils.toString(rsp));
						}
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Could upload data: " + e.getMessage());
				e.printStackTrace();
			}

			return null;
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// Messages interface (unused)
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

	///////////////////////////////////////////////////////////////////////////
	// Shared preference change listener
	///////////////////////////////////////////////////////////////////////////
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("editLocalStorage")) {
			int localStorage = Integer.valueOf(sharedPreferences.getString(key, "10")); 
			if (localStorage > 0) {
				Log.i(TAG, "Changed delete age from " + mDeleteAge/ClassConsts.MILLIDAY + " to " + localStorage);
				mDeleteAge = localStorage*ClassConsts.MILLIDAY;
			}
		}
	}
}