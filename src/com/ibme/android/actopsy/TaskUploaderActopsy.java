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

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Files;
import android.text.TextUtils;


public class TaskUploaderActopsy extends AsyncTask<File, Void, Void> {

	private static final String TAG = "ActopsyTaskUploaderActopsy";

	static public class Values {
		public long time;
		public float acc;
		public Values(long t, float l)
			{ time = t; acc = l; }
	}

	final Context context;
	private String mActopsyID;
	private String mActopsyPass;
	private String mActopsyServer;
	private String mActopsyPort;

	TaskUploaderActopsy(Context context) {
		this.context = context;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		mActopsyID = prefs.getString("editActopsyID", "");
		mActopsyPass = prefs.getString("editActopsyPass", "");
		mActopsyServer = prefs.getString("editServerActopsy", "");
		mActopsyPort = prefs.getString("editPortActopsy", "");
	}

	@Override
	protected Void doInBackground(File... files) {
		try {
			// android.os.Debug.waitForDebugger();

			if (TextUtils.isEmpty(mActopsyID) || TextUtils.isEmpty(mActopsyPass) || TextUtils.isEmpty(mActopsyServer) || TextUtils.isEmpty(mActopsyPort)) {
				return null;
			}

			for (int i = 0; i < files.length; i ++) {
				// TODO: I guess it may be optimized by pushing file directly instead of reading JSON in
				ArrayList<Values> vals = new ArrayList<Values>();
				FileInputStream stream = new FileInputStream(files[i]);
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
				StringBuilder sb = new StringBuilder();
				String jvals = "", line;
				while ((line = reader.readLine()) != null)
				{
				    sb.append(line);
				}
				jvals = sb.toString();

			    DefaultHttpClient httpclient = new DefaultHttpClient();
				URL url = new URL(mActopsyServer + "/api/db/" + mActopsyID + "/series?u=" + mActopsyID + "&p=" + mActopsyPass);
			    HttpPost httppost = new HttpPost(url.toString());
			    httppost.setEntity(new StringEntity(jvals));
			    httppost.setHeader("Accept", "application/json");
			    httppost.setHeader("Content-Type", "application/json; charset=UTF-8");

				HttpResponse response = httpclient.execute(httppost);
				if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 201) {
					files[i].delete();
					new ClassEvents(TAG, "INFO", "Uploaded to Actopsy " + files[i].getName());
				} else {
					HttpEntity rsp = response.getEntity();
					String str = EntityUtils.toString(rsp);
					new ClassEvents(TAG, "ERROR", "Upload Actopsy error: " + str);
				}
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Upload Actopsy failed " + e.getMessage());
		}

		return null;
	}
}
