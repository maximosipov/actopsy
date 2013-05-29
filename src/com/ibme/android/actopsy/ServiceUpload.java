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

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.ArrayList;
import java.security.KeyStore;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;

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
	private String mUserID;
	private String mUserPass;
	private boolean mUpload;

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

		//android.os.Debug.waitForDebugger();
		long ts = System.currentTimeMillis();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int localStorage = Integer.valueOf(prefs.getString("editLocalStorage", "10")); 
		mDeleteAge = localStorage*ClassConsts.MILLIDAY;
		mUserID = prefs.getString("editUserID", "");
		mUserPass = prefs.getString("editUserPass", "");
		mUpload = prefs.getBoolean("checkboxShare", false);

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
				if (!mUpload || mUserID == null || mUserPass == null || mUserID.isEmpty() || mUserPass.isEmpty()) {
					Log.d(TAG, "Upload Off");
				} else {
					File[] files = getFiles();
					if (files != null) {
						for (int i = 0; i < files.length; i++) {
							new UploaderTask(context).execute(files[i]);
						}
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

	public class MyHttpClient extends DefaultHttpClient {

		final Context context;

		public MyHttpClient(Context context) {
			this.context = context;
		}

		@Override
		protected ClientConnectionManager createClientConnectionManager() {
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			registry.register(new Scheme("https", newSslSocketFactory(), 443));
			return new SingleClientConnManager(getParams(), registry);
		}

		private SSLSocketFactory newSslSocketFactory() {
			try {
				KeyStore trusted = KeyStore.getInstance("BKS");
				InputStream in = context.getResources().openRawResource(R.raw.ibmeweb7);
				try {
					trusted.load(in, "ez24get".toCharArray());
				} finally {
					in.close();
				}
				return new SSLSocketFactory(trusted);
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}
	}

	private class UploaderTask extends AsyncTask<File, Void, Void> {

		final Context context;

		UploaderTask(Context context) {
			this.context = context;
		}
		
		@Override
		protected Void doInBackground(File... files) {
			try {
				for (int i = 0; i < files.length; i ++) {
					CredentialsProvider cp = new BasicCredentialsProvider();
				    cp.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
				    		new UsernamePasswordCredentials(mUserID, mUserPass));
					MyHttpClient httpclient = new MyHttpClient(context);
				    httpclient.setCredentialsProvider(cp);
					HttpPost httppost = new HttpPost(ClassConsts.UPLOAD_URL);

					// Prepare HTTP request
					MultipartEntity entity = new MultipartEntity();
					entity.addPart( "MAX_FILE_SIZE", new StringBody(ClassConsts.UPLOAD_SIZE));
					entity.addPart( "USER", new StringBody(mUserID));
					entity.addPart( "PASS", new StringBody(mUserPass));
					entity.addPart( "FILE", new FileBody(files[i]));        	 
					httppost.setEntity( entity );

					// Execute HTTP request
					HttpResponse response = httpclient.execute(httppost);
					HttpEntity rsp = response.getEntity();
					if (rsp != null) {
						String str = EntityUtils.toString(rsp); 
						if (str.matches("^OK(?s).*")) {
							files[i].delete();
							Log.i(TAG, "Upload successful: " + files[i].getName());
						} else {
							Log.e(TAG, "Upload failed: " + str);
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
		} else if (key.equals("editUserID")) {
			Log.i(TAG, "Changed user ID");
			mUserID = sharedPreferences.getString("editUserID", "");

		} else if (key.equals("editUserPass")) {
			Log.i(TAG, "Changed password");
			mUserPass = sharedPreferences.getString("editUserPass", "");

		} else if (key.equals("checkboxShare")) {
			mUpload = sharedPreferences.getBoolean("checkboxShare", false);
			Log.i(TAG, "Changed share settings");
		}

	}
}