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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
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
import android.text.TextUtils;


public class TaskUploaderTC extends AsyncTask<File, Void, Void> {

	private static final String TAG = "ActopsyTaskUploaderTC";

	static public class Values {
		public String participant_id;
		public long response_date;
		public float X;
		public float Y;
		public float Z;
		public Values(String id, long time, float x, float y, float z)
			{ participant_id = id; response_date = time; X = x; Y = y; Z = z; }
	}

	final Context context;
	private String mUserID;
	private String mUserIDTC;
	private String mUserPassTC;
	private String mServerTC;

	TaskUploaderTC(Context context) {
		this.context = context;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		mUserID = prefs.getString("editUserID", "");
		mUserIDTC = prefs.getString("editUserIDTC", "");
		mUserPassTC = prefs.getString("editUserPassTC", "");
		mServerTC = prefs.getString("editServerTC", "");
	}

	public byte[] getHash(String password) {
		MessageDigest digest=null;
	    try {
	        digest = MessageDigest.getInstance("SHA-256");
	    } catch (NoSuchAlgorithmException e1) {
	        e1.printStackTrace();
	    }
	    digest.reset();
	    return digest.digest(password.getBytes());
	}

	static String bin2hex(byte[] data) {
	    return String.format("%0" + (data.length*2) + "x", new BigInteger(1, data));
	}

	@Override
	protected Void doInBackground(File... files) {
		try {
			// android.os.Debug.waitForDebugger();

			if (TextUtils.isEmpty(mUserID) || TextUtils.isEmpty(mUserIDTC) || TextUtils.isEmpty(mUserPassTC) || TextUtils.isEmpty(mServerTC)) {
				return null;
			}

			for (int i = 0; i < files.length; i ++) {
				String tcid = mUserID.substring(2);
				String hash = bin2hex(getHash(mUserIDTC + mUserPassTC));

				// TODO: I guess it may be optimized by pushing file directly instead of reading JSON in
				ArrayList<Values> vals = new ArrayList<Values>();
				FileInputStream stream = new FileInputStream(files[i]);
				Gson gson = new Gson();
				JsonReader reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
				reader.beginArray();
				while (reader.hasNext()) {
					Values v = gson.fromJson(reader, Values.class);
					vals.add(v);
				}
				reader.endArray();
				reader.close();
				String jvals = gson.toJson(vals);

			    DefaultHttpClient httpclient = new DefaultHttpClient();
				URL url = new URL(mServerTC + tcid + "/actigraphyresponses?apikey=" + tcid + "-" + hash);
			    HttpPut httpput = new HttpPut(url.toString());
			    StringEntity entity = new StringEntity(jvals);
			    entity.setContentType("application/json;charset=UTF-8");
			    entity.setContentEncoding(new BasicHeader(HTTP.CONTENT_TYPE,"application/json;charset=UTF-8"));
			    httpput.setEntity(entity); 

				HttpResponse response = httpclient.execute(httpput);
				if (response.getStatusLine().getStatusCode() == 200 || response.getStatusLine().getStatusCode() == 201) {
					files[i].delete();
					new ClassEvents(TAG, "INFO", "Uploaded " + files[i].getName());
				} else {
					HttpEntity rsp = response.getEntity();
					String str = EntityUtils.toString(rsp);
					new ClassEvents(TAG, "ERROR", "Upload TC error: " + str);
				}
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Upload TC failed " + e.getMessage());
		}

		return null;
	}
}
