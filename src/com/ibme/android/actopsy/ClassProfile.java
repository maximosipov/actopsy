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

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

///////////////////////////////////////////////////////////////////////////
// Activity profiles are stored in shared preferences with the following
// structure:
//
// profile-summary - summary of profile data
//  - fb_updated: Date of the last update of Facebook data
//
// profile-DayOfWeek - array of values with PROFILE_LENGTH intervals
//  - x_S - average activity level in xth interval
//  - x_N - number of days, contributed to xth interval
//  - x_C - last recorded value of xth interval
//  - x_FB - number of Facebook status update in xth interval
//
// profile-stats - array of values for Statistics screen
//  - x_L - average level of activity in xth interval
// 
///////////////////////////////////////////////////////////////////////////

public class ClassProfile {

	private static final String TAG = "ActopsyProfile";

	public static final int LENGTH = 24*4;		// 15 minutes intervals
	public static final long MILLIPERIOD = ClassConsts.MILLIDAY/LENGTH;

	public class Values {
		public float acc_avg;
		public float acc_curr;
		public int fb_updates;
	}

	private float[] mProfile;
	private long mPeriodLimit;
	private double mPeriodSum;
	private long mPeriodNum;
	private int mPeriodIdx;

	private Context mContext;

	public ClassProfile(Context context)
	{
		mContext = context;
	}

	public void init(long ts)
	{
		mProfile = new float[LENGTH];
		for (int i = 0; i < mProfile.length; i++) {
			mProfile[i] = 0;
		}
		mPeriodLimit =  ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
		mPeriodIdx = ts2idx(ts);
		mPeriodSum = 0;
		mPeriodNum = 0;
	}

	public void fini()
	{
		mPeriodLimit =  MILLIPERIOD;
		mPeriodIdx = 0;
		mPeriodSum = 0;
		mPeriodNum = 0;
	}

	// Get profile values for a numbered week day
	public Values[] get(int day)
	{
		Values[] vals = new Values[LENGTH];
		String profile = new String("profile-" + ClassConsts.DAYS[day]);
		SharedPreferences prefs = mContext.getSharedPreferences(profile, Context.MODE_PRIVATE);
		for(int i=0; i<LENGTH; i++)
		{
			vals[i] = new Values();
			vals[i].acc_avg = prefs.getFloat(Integer.toString(i) + "_S", 0);  
			vals[i].acc_curr = prefs.getFloat(Integer.toString(i) + "_C", 0);
			vals[i].fb_updates = prefs.getInt(Integer.toString(i) + "_FB", 0);
		}

		return vals;
	}

	// Update acceleration value
	public void update(long ts, float x, float y, float z)
	{
		// Detect profile period roll-over and save (average) data
		if (ts > mPeriodLimit) {
			if (mPeriodNum > 0) {
				mProfile[mPeriodIdx] = (float)(mPeriodSum/mPeriodNum);
				// Save averaged for all history profile data
				SimpleDateFormat fmt = new SimpleDateFormat("EEE");
				String profile = new String("profile-" + fmt.format(new Date(mPeriodLimit-1)));				
				Bundle params = new Bundle();
				params.putString("file", profile);
				params.putInt("index", mPeriodIdx);
				params.putFloatArray("data", mProfile);
				new UpdaterTask().execute(params);
			}
			mPeriodSum = Math.abs(Math.sqrt(x*x + y*y + z*z) - ClassConsts.G);
			mPeriodNum = 1;
			mPeriodLimit = ((long)ts/MILLIPERIOD)*MILLIPERIOD + MILLIPERIOD;
			mPeriodIdx = ts2idx(ts);
		} else {
			mPeriodSum += Math.abs(Math.sqrt(x*x + y*y + z*z) - ClassConsts.G);
			mPeriodNum ++;
		}
	}

	// Update Facebook activity
	public void update_fb(long ts)
	{
		SimpleDateFormat fmt = new SimpleDateFormat("EEE");
		String profile = new String("profile-" + fmt.format(new Date(ts)));				
		Bundle params = new Bundle();
		params.putString("file", profile);
		params.putLong("fb_ts", ts);
		new UpdaterTask().execute(params);
	}

	private void clear()
	{
		SharedPreferences prefs = mContext.getSharedPreferences(ClassConsts.PREFS_SUMMARY, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.commit();

		for (int i = 0; i < ClassConsts.DAYS.length; i++) {
			prefs = mContext.getSharedPreferences("profile-" + ClassConsts.DAYS[i], Context.MODE_PRIVATE);
			editor = prefs.edit();
			editor.clear();
			editor.commit();
		}
	}
	
	private int ts2idx(long ts)
	{
		return (int)((ts - ((long)(ts/ClassConsts.MILLIDAY))*ClassConsts.MILLIDAY)/MILLIPERIOD);
	}

	private class UpdaterTask extends AsyncTask<Bundle, Void, Void> {
		@Override
		protected Void doInBackground(Bundle... params) {
			int n;
			float f;

			try {
				String file = params[0].getString("file");
				int index = params[0].getInt("index");
				float[] profile = params[0].getFloatArray("data");
				long fb_ts = params[0].getLong("fb_ts");

				// Update profile data
				SharedPreferences prefs = mContext.getSharedPreferences(file, Context.MODE_PRIVATE);
				SharedPreferences.Editor editor = prefs.edit();
				if (profile != null) {
					// update activity profile
					n = prefs.getInt(Integer.toString(index) + "_N", 0);
					f = prefs.getFloat(Integer.toString(index) + "_S", 0);
					f = f*n/(n+1) + profile[index]/(n+1);
					n++;
					editor.putInt(Integer.toString(index) + "_N", n);
					editor.putFloat(Integer.toString(index) + "_S", f);
					editor.putFloat(Integer.toString(index) + "_C", profile[index]);
				} else if (fb_ts > 0) {
					// update Facebook profile
					n = prefs.getInt(Integer.toString(ts2idx(fb_ts)) + "_FB", 0);
					n++;
					editor.putInt(Integer.toString(ts2idx(fb_ts)) + "_FB", n);

					SharedPreferences.Editor editor_s = mContext.getSharedPreferences(ClassConsts.PREFS_SUMMARY, Context.MODE_PRIVATE).edit();
					editor_s.putLong("fb_updated", fb_ts);
					editor_s.commit();
				}
				editor.commit();

				Log.i(TAG, "Updated profile " + file);
			} catch (Exception e) {
				Log.e(TAG, "Could not update profile: " + e.getMessage());
				e.printStackTrace();
			}

			return null;
		}
	}
}
