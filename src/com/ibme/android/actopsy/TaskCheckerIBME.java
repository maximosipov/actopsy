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
import java.io.InputStream;
import java.security.KeyStore;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;


public class TaskCheckerIBME extends AsyncTask<Void, Void, String> {

	private static final String TAG = "ActopsyTaskCheckerIBME";

	final Context context;
	private String mUserID;
	private String mUserPass;
	private String mServerRaw;

	TaskCheckerIBME(Context context) {
		this.context = context;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		mUserID = prefs.getString("editUserID", "");
		mUserPass = prefs.getString("editUserPass", "");
		mServerRaw = "https://ibme-web7.eng.ox.ac.uk/check.php";
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

	@Override
	protected String doInBackground(Void... nothing) {
		String result = "Unknown";
		try {
			// android.os.Debug.waitForDebugger();

			if (TextUtils.isEmpty(mUserID) || TextUtils.isEmpty(mUserPass) || TextUtils.isEmpty(mServerRaw)) {
				result = "User, password or server not set";
				new ClassEvents(TAG, "ERROR", result);
				return result;
			}

			CredentialsProvider cp = new BasicCredentialsProvider();
			cp.setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
					new UsernamePasswordCredentials(mUserID, mUserPass));
			MyHttpClient httpclient = new MyHttpClient(context);
			httpclient.setCredentialsProvider(cp);
			HttpPost httppost = new HttpPost(mServerRaw);

			// Prepare HTTP request
			MultipartEntity entity = new MultipartEntity();
			entity.addPart( "USER", new StringBody(mUserID));
			entity.addPart( "PASS", new StringBody(mUserPass));
			httppost.setEntity( entity );

			// Execute HTTP request
			HttpResponse response = httpclient.execute(httppost);
			HttpEntity rsp = response.getEntity();
			if (rsp != null) {
				String str = EntityUtils.toString(rsp);
				if (str.matches("^OK(?s).*")) {
					result = "OK";
					new ClassEvents(TAG, "INFO", result);
				} else {
					result = "Connection error: " + str;
					new ClassEvents(TAG, "ERROR", result);
				}
			}
			
		} catch (Exception e) {
			result = "Connection failed: " + e.getMessage();
			new ClassEvents(TAG, "ERROR", result);
		}

		return result;
	}

	protected void onPostExecute(String result){
    	Toast toast = Toast.makeText(context, result, 3000);
    	toast.show();
	}
}
