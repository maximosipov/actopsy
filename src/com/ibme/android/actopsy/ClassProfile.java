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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;

public class ClassProfile {

	private static final String TAG = "ActopsyProfile";

	public static final int LENGTH_V7 = 24*4;		// 15 minutes intervals (keep old value for convert)
	public static final int LENGTH = 24*60;		// 1 minutes intervals
	public static final long MILLIPERIOD = ClassConsts.MILLIDAY/LENGTH;

	public class Values {
		public long t;
		public float v;
		public Values(long time, float val) { t = time; v = val; }
	}

	private long mPeriodLimit;
	private double mPeriodSum;
	private long mPeriodNum;

	private Context mContext;

	public ClassProfile(Context context)
	{
		mContext = context;
	}

	public void init(long ts)
	{
		convert(ts);
		mPeriodLimit =  ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
		mPeriodSum = 0;
		mPeriodNum = 0;
	}

	public void fini()
	{
		mPeriodLimit =  MILLIPERIOD;
		mPeriodSum = 0;
		mPeriodNum = 0;
	}

	// Convert from preferences profiles
	public void convert(long ts)
	{
		SharedPreferences prefs = mContext.getSharedPreferences(
		        ClassConsts.PREFS_PRIVATE, Context.MODE_PRIVATE);
		if(prefs.getBoolean("updatedProfileV7", false))
			return;

		for (int day = 0; day < 7; day++) {
			// calculate day offset from today
			int offset = wd2offset(ts, day);
			// read in and recalculate
			long dayval = (ts/ClassConsts.MILLIDAY - offset)*ClassConsts.MILLIDAY;
			ArrayList<Values> jvals = new ArrayList<Values>();
			String profile = new String("profile-" + ClassConsts.DAYS[day]);
			SharedPreferences prof = mContext.getSharedPreferences(profile, Context.MODE_PRIVATE);
			for(int i=0; i<LENGTH_V7; i++)
			{
				Values val = new Values(0, 0);
				val.t = dayval + i*ClassConsts.MILLIDAY/LENGTH_V7;
				val.v = prof.getFloat(Integer.toString(i) + "_C", 0);
				jvals.add(val);
			}
			// write out
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
			String name = new String("profile-activity-" + fmt.format(new Date(dayval)) + ".json");
			File root = Environment.getExternalStorageDirectory();
			File folder = new File(root, ClassConsts.FILES_ROOT);
			File file = new File(folder, name);
			try {
				FileOutputStream stream = new FileOutputStream(file);
				writeVals(stream, jvals);
			} catch (IOException e) {
				new ClassEvents(TAG, "ERROR", "Couldn't write " + name);
			}
			new ClassEvents(TAG, "INFO", "Converted " + profile + " to " + name);
		}
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean("updatedProfileV7", true);
		editor.commit();
	}

	// Get profile values for a numbered week day
	public Values[] get(int day)
	{
		ArrayList<Values> vals = new ArrayList<Values>();
		long ts = System.currentTimeMillis();
		int offset = wd2offset(ts, day);
		long dayval = (ts/ClassConsts.MILLIDAY - offset)*ClassConsts.MILLIDAY;

		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		String name = new String("profile-activity-" + fmt.format(new Date(dayval)) + ".json");
		File root = Environment.getExternalStorageDirectory();
		File folder = new File(root, ClassConsts.FILES_ROOT);
		File file = new File(folder, name);
		try {
			FileInputStream stream = new FileInputStream(file);
			vals = readVals(stream);
		} catch (IOException e) {
			new ClassEvents(TAG, "ERROR", "Couldn't read " + name);
		}

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
				params.putFloat("val", (float)(mPeriodSum/mPeriodNum));
				new UpdaterTask().execute(params);
			}
			mPeriodSum = Math.abs(Math.sqrt(x*x + y*y + z*z) - ClassConsts.G);
			mPeriodNum = 1;
			mPeriodLimit = ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
		} else {
			mPeriodSum += Math.abs(Math.sqrt(x*x + y*y + z*z) - ClassConsts.G);
			mPeriodNum ++;
		}
	}

	private int wd2offset(long ts, int day)
	{
		int weekday = 0;
		int offset = 0;
		SimpleDateFormat fmt = new SimpleDateFormat("EEE");
		String today = new String(fmt.format(new Date(ts)));
		while (weekday < ClassConsts.DAYS.length && !ClassConsts.DAYS[weekday].equals(today))
			weekday++;

		if (day < weekday) {
			offset = 7 - weekday;
		} else {
			offset = day - weekday;
		}

		return offset;
	}

	private class UpdaterTask extends AsyncTask<Bundle, Void, Void> {
		@Override
		protected Void doInBackground(Bundle... params) {
			ArrayList<Values> vals = new ArrayList<Values>();
			long time = params[0].getLong("time");
			float val = params[0].getFloat("val");

			// Update profile data
			SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
			String name = new String("profile-activity-" + fmt.format(new Date(time)) + ".json");
			try {
				File root = Environment.getExternalStorageDirectory();
				File folder = new File(root, ClassConsts.FILES_ROOT);
				File file = new File(folder, name);
				try {
					FileInputStream istream = new FileInputStream(file);
					vals = readVals(istream);
				} catch (Exception e) {
					new ClassEvents(TAG, "ERROR", "Could not read profile " + name + ", resetting: " + e.getMessage());
				}
				FileOutputStream ostream = new FileOutputStream(file);
				vals.add(new Values(time, val));
				writeVals(ostream, vals);
			} catch (Exception e) {
				new ClassEvents(TAG, "ERROR", "Could not update profile " + name + " :" + e.getMessage());
			}

			return null;
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// JSON serialization support functions
	///////////////////////////////////////////////////////////////////////////
	private ArrayList<Values> readVals(InputStream in) throws IOException {
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		ArrayList<Values> vals = new ArrayList<Values>();
		reader.beginArray();
		while (reader.hasNext()) {
			Values v = gson.fromJson(reader, Values.class);
			vals.add(v);
		}
		reader.endArray();
		reader.close();
		return vals;
	}

	private void writeVals(OutputStream out, ArrayList<Values> vals) throws IOException {
		Gson gson = new Gson();
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
		writer.setIndent("  ");
		writer.beginArray();
		for (Values v : vals) {
			gson.toJson(v, Values.class, writer);
		}
		writer.endArray();
		writer.close();
	}
}
