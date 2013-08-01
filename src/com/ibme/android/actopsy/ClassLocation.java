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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;

public class ClassLocation {

	private static final String TAG = "ActopsyLocation";

	private Context mContext;
	private File mFile;
	private BufferedWriter mWriter;
	private long mTsPrev;
	private float mSeedLat;
	private float mSeedLon;

	public ClassLocation(Context context)
	{
		mContext = context;
	}

	// Open for write
	public void init(long ts)
	{
		// Initialize coordinate randomization seeds with last known location
		SharedPreferences prefs = mContext.getSharedPreferences(
		        ClassConsts.PREFS_LOCATION, mContext.MODE_PRIVATE);
		mSeedLat = prefs.getFloat("seedLat", 0);
		mSeedLon = prefs.getFloat("seedLon", 0);
		if (mSeedLat == 0 && mSeedLon == 0) {
			LocationManager lm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
			Location l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
			mSeedLat = (float)l.getLatitude();
			mSeedLon = (float)l.getLongitude();
			SharedPreferences.Editor editor = prefs.edit();
			editor.putFloat("seedLat", mSeedLat);
			editor.putFloat("seedLon", mSeedLon);
			editor.commit();
		}

		// TODO: Fix it to check and wait for storage
		try {
			File root = Environment.getExternalStorageDirectory();
			if (root.canWrite()){
				// Create folders
				File folder = new File(root, ClassConsts.FILES_ROOT);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				// Initialise the main location file
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
				mFile = new File(folder, "location-" + fmt.format(new Date(ts)) + ".csv");
				mWriter = new BufferedWriter(new FileWriter(mFile, true));
			} else {
				new ClassEvents(TAG, "ERROR", "SD card not writable");
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Could not open file " + e.getMessage());
		}
		mTsPrev = ts;
	}

	public void fini()
	{
		try {
			if (mWriter != null) {
				mWriter.close();
				mWriter = null;
			}
			if (mFile != null) {
				mFile = null;
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Could not close file " + e.getMessage());
		}
	}

	void update(long ts, double lat, double lon)
	{
		lat = lat - mSeedLat;
		lon = lon - mSeedLon;
		long today = ts/ClassConsts.MILLIDAY;
		long yesterday = mTsPrev/ClassConsts.MILLIDAY;
		if (today > yesterday) {
			String f = mFile.getName();
			fini();
			new TaskZipper().execute(f);
			init(ts);
		}
		if (mWriter != null) {
			try {
				SimpleDateFormat fmt = new SimpleDateFormat(ClassConsts.DATEFMT);
				String str = fmt.format(new Date(ts)) + 
						"," + Double.toString(lat) +
						"," + Double.toString(lon) + "\n";
				mWriter.write(str);
				// TODO: We may not need to flush that often
				mWriter.flush();
			} catch(Exception e) {
				new ClassEvents(TAG, "ERROR", "Could not write file " + e.getMessage());
			}
		}
		mTsPrev = ts;
	}
}
