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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry; 
import java.util.zip.ZipOutputStream; 

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

public class ClassAccelerometry {

	private static final String TAG = "ActopsyAccelerometry";
	private static final String DATEFMT = "yyyy-MM-dd HH:mm:ss.SSSZ";

	public class Values {
		long ts;
		float x;
		float y;
		float z;
	}

	private Context mContext;
	private BufferedWriter mWriter;
	private long mTsPrev;

	public ClassAccelerometry(Context context)
	{
		mContext = context;
	}

	// Open for write
	public void init(long ts)
	{
		// TODO: Fix it to check and wait for storage
		try {
			File root = Environment.getExternalStorageDirectory();
			if (root.canWrite()){
				// Create folders
				File folder = new File(root, ClassConsts.FILES_ROOT);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				// Initialise the main activity file
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
				File out = new File(folder, "activity-" + fmt.format(new Date(ts)) + ".csv");
				mWriter = new BufferedWriter(new FileWriter(out, true));
			} else {
				Log.e(TAG, "SD card is not writable");
			}
		} catch (Exception e) {
			Log.e(TAG, "Could not open file: " + e.getMessage());
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
		} catch (Exception e) {
			Log.e(TAG, "Could not close file: " + e.getMessage());
		}
	}

	void update(long ts, float x, float y, float z)
	{
		long today = ts/ClassConsts.MILLIDAY;
		long yesterday = mTsPrev/ClassConsts.MILLIDAY;
		if (today > yesterday) {
			fini();
			new ZipperTask().execute(yesterday);
			init(ts);
		}
		if (mWriter != null) {
			try {
				SimpleDateFormat fmt = new SimpleDateFormat(DATEFMT);
				String str = fmt.format(new Date(ts)) + 
						"," + Float.toString(x) + 
						"," + Float.toString(y) + 
						"," + Float.toString(z) + "\n";
				mWriter.write(str);
				// TODO: We may not need to flush that often
				mWriter.flush();
			} catch(Exception e) {
				Log.e(TAG, "Could not write to file: " + e.getMessage());
			}
		}
		mTsPrev = ts;
	}

	private class ZipperTask extends AsyncTask<Long, Void, Void> {
		@Override
		protected Void doInBackground(Long... y) {
			try {
				File root = Environment.getExternalStorageDirectory();
				if (root.canWrite()) {
					// Create folders
					File folder = new File(root, ClassConsts.FILES_ROOT);
					if (!folder.exists()) {
						folder.mkdirs();
					}
					// Compress files
					if (y.length >= 1) {
						SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
						File inf = new File(folder, "activity-" + fmt.format(new Date(y[0])) + ".csv");
						BufferedInputStream ins = new BufferedInputStream(new FileInputStream(inf));
						File outf = new File(folder, "activity-" + fmt.format(new Date(y[0])) + ".csv.zip");
						ZipOutputStream outs = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outf)));

						// Compress data
						ZipEntry entry = new ZipEntry(inf.getName());
						outs.putNextEntry(entry);
						byte data[] = new byte[ClassConsts.BUFFER_SIZE];
						int count;
				        while ((count = ins.read(data, 0, ClassConsts.BUFFER_SIZE)) != -1) { 
				        	outs.write(data, 0, count); 
				        } 
				        ins.close();
				        outs.close();
						// Remove uncompressed
				        inf.delete();
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Could not update activity: " + e.getMessage());
				e.printStackTrace();
			}

			return null;
		}
	}
}
