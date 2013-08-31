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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;

public class ClassProfileAccelerometry {

	private static final String TAG = "ActopsyProfileAccelerometry";

	public static final int LENGTH_V7 = 24*4;		// 15 minutes intervals (keep old value for convert)
	public static final int LENGTH = 24*60;		// 1 minutes intervals
	public static final long MILLIPERIOD = ClassConsts.MILLIDAY/LENGTH;

	static public class Values {
		public long t;
		public float x;
		public float y;
		public float z;
		public Values(long time, float xv, float yv, float zv)
			{ t = time; x = xv; y = yv; z = zv; }
	}

	private long mPeriodLimit;
	private double mPeriodSumX, mPeriodSumY, mPeriodSumZ;
	private long mPeriodNum;

	private Context mContext;

	public ClassProfileAccelerometry(Context context)
	{
		mContext = context;
	}

	public void init(long ts)
	{
		// TODO: Fix it to check and wait for storage
		try {
			File root = mContext.getFilesDir();
			if (!root.exists()) {
				root.mkdirs();
			}
			if (root.canWrite()){
				File folder = new File(root, ClassConsts.CACHE_ROOT);
				if (!folder.exists()) {
					folder.mkdirs();
				}
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Could not open file " + e.getMessage());
		}
		mPeriodLimit =  ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
		mPeriodSumX = 0;
		mPeriodSumY = 0;
		mPeriodSumZ = 0;
		mPeriodNum = 0;
	}

	public void fini()
	{
		mPeriodLimit =  MILLIPERIOD;
		mPeriodSumX = 0;
		mPeriodSumY = 0;
		mPeriodSumZ = 0;
		mPeriodNum = 0;
	}

	// Convert from preferences profiles
	public void convert(long ts)
	{
		SharedPreferences prefs = mContext.getSharedPreferences(
		        ClassConsts.PREFS_PRIVATE, Context.MODE_PRIVATE);
		if(prefs.getBoolean("updatedProfileV7", false))
			return;

		long offset = TimeZone.getDefault().getRawOffset() + TimeZone.getDefault().getDSTSavings();
		for (int i=0; i<7; i++) {
			long daynum = ts-i*ClassConsts.MILLIDAY;
			ArrayList<Values> jvals = new ArrayList<Values>();
			// read in and convert
			SimpleDateFormat fmt = new SimpleDateFormat("EEE");
			String profile = new String("profile-" + new String(fmt.format(new Date(daynum))));
			SharedPreferences prof = mContext.getSharedPreferences(profile, Context.MODE_PRIVATE);
			long dayoff = (ts/ClassConsts.MILLIDAY - i) * ClassConsts.MILLIDAY;
			for(int j=0; j<LENGTH_V7; j++)
			{
				Values val = new Values(0, 0, 0, 0);
				val.t = dayoff + offset + j*ClassConsts.MILLIDAY/LENGTH_V7;
				val.x = (float) (prof.getFloat(Integer.toString(j) + "_C", 0) + ClassConsts.G);
				jvals.add(val);
			}
			// write out
			new ClassEvents(TAG, "INFO", "Converting " + profile);
			writeVals(daynum, jvals);
		}
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean("updatedProfileV7", true);
		editor.commit();
	}

	// Get profile values for a numbered week day
	public Values[] get(long ts)
	{
		ArrayList<Values> vals = new ArrayList<Values>();
		vals = readVals(ts);
		return vals.toArray(new Values[vals.size()]);
	}

	// Update acceleration value
	public void update(long ts, float x, float y, float z)
	{
		// Detect profile period roll-over and save (average) data
		if (ts > mPeriodLimit) {
			if (mPeriodNum > 0) {
				// Save averaged for all history profile data
				Bundle params = new Bundle();
				params.putLong("time", mPeriodLimit);
				params.putFloat("x", (float)(mPeriodSumX/mPeriodNum));
				params.putFloat("y", (float)(mPeriodSumY/mPeriodNum));
				params.putFloat("z", (float)(mPeriodSumZ/mPeriodNum));
				new UpdaterTask().execute(params);
			}
			mPeriodSumX = x;
			mPeriodSumY = y;
			mPeriodSumZ = z;
			mPeriodNum = 1;
			mPeriodLimit = ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
		} else {
			mPeriodSumX += Math.abs(x);
			mPeriodSumY += Math.abs(y);
			mPeriodSumZ += Math.abs(z);
			mPeriodNum ++;
		}
	}

	// Get profiles ready for upload
	public File[] getUploads()
	{
		File[] files = new File[0];
		File root = mContext.getFilesDir();
		File folder = new File(root, ClassConsts.CACHE_ROOT);
		long now = System.currentTimeMillis();

		// First, remove old files (yes - hack, I know)
		files = folder.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().matches(".*");
			}
		});
		for (int i = 0; i < files.length; i++) {
			long age = files[i].lastModified();
			// TODO: change 10 to proper consts definition
			if (age + 10*ClassConsts.MILLIDAY < now) {
				if (files[i].delete()) {
					new ClassEvents(TAG, "INFO", "Deleted " + files[i].getAbsolutePath());
				} else {
					new ClassEvents(TAG, "ERROR", "Delete failed " + files[i].getAbsolutePath());
				}
			}
		}

		// Now prepare list of not uploaded files and tag as uploaded
		ArrayList<File> uploads = new ArrayList<File>();
		files = folder.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().matches(".*json$");
			}
		});
		for (int i = 0; i < files.length; i++) {
			long age = files[i].lastModified();
			// At least 1 day old files
			if (age + 1*ClassConsts.MILLIDAY < now) {
				File uploaded = new File(files[i].getAbsolutePath() + ".uploaded");
				if (!uploaded.exists()) {
					try {
						uploaded.createNewFile();
						uploads.add(files[i]);
					} catch (IOException e) {
						new ClassEvents(TAG, "ERROR", "Couldn't create " + uploaded.getAbsolutePath());
					}
				}
			}
		}

		return uploads.toArray(new File[uploads.size()]);
	}


	private class UpdaterTask extends AsyncTask<Bundle, Void, Void> {
		@Override
		protected Void doInBackground(Bundle... params) {
			ArrayList<Values> vals = new ArrayList<Values>();
			long time = params[0].getLong("time");
			float x = params[0].getFloat("x");
			float y = params[0].getFloat("y");
			float z = params[0].getFloat("z");

			// Update profile data
			vals = readVals(time);
			vals.add(new Values(time, x, y, z));
			writeVals(time, vals);

			return null;
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// JSON serialization support functions
	///////////////////////////////////////////////////////////////////////////
	private ArrayList<Values> readVals(long ts) {
		ArrayList<Values> vals = new ArrayList<Values>();
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		String name = new String("profile-activity-" + fmt.format(new Date(ts)) + ".json");
		File root = mContext.getFilesDir();
		File folder = new File(root, ClassConsts.CACHE_ROOT);
		File file = new File(folder, name);
		try {
			FileInputStream stream = new FileInputStream(file);
			Gson gson = new Gson();
			JsonReader reader = new JsonReader(new InputStreamReader(stream, "UTF-8"));
			reader.beginArray();
			while (reader.hasNext()) {
				Values v = gson.fromJson(reader, Values.class);
				vals.add(v);
			}
			reader.endArray();
			reader.close();
		} catch (IOException e) {
			new ClassEvents(TAG, "ERROR", "Couldn't read " + name);
		}
		return vals;
	}

	private void writeVals(long ts, ArrayList<Values> vals) {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		String name = new String("profile-activity-" + fmt.format(new Date(ts)) + ".json");
		File root = mContext.getFilesDir();
		File folder = new File(root, ClassConsts.CACHE_ROOT);
		File file = new File(folder, name);
		try {
			FileOutputStream stream = new FileOutputStream(file);
			Gson gson = new Gson();
			JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream, "UTF-8"));
			writer.setIndent("  ");
			writer.beginArray();
			for (Values v : vals) {
				gson.toJson(v, Values.class, writer);
			}
			writer.endArray();
			writer.close();
		} catch (IOException e) {
			new ClassEvents(TAG, "ERROR", "Couldn't write " + name);
		}
	}
}
