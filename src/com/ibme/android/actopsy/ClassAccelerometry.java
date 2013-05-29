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
			Log.i(TAG, "Day roll-over: " + new SimpleDateFormat("yyyy-MM-dd").format(new Date(mTsPrev)));
			new ZipperTask().execute(new Date(mTsPrev));
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

	private class ZipperTask extends AsyncTask<Date, Void, Void> {
		@Override
		protected Void doInBackground(Date... y) {
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
						File inf = new File(folder, "activity-" + fmt.format(y[0]) + ".csv");
						BufferedInputStream ins = new BufferedInputStream(new FileInputStream(inf));
						File outf = new File(folder, "activity-" + fmt.format(y[0]) + ".csv.zip");
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
						Log.i(TAG, "Created archive: " + outf.getName());
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Could not create archive: " + e.getMessage());
				e.printStackTrace();
			}

			return null;
		}
	}
}
