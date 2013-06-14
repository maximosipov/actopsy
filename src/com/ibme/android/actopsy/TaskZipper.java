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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry; 
import java.util.zip.ZipOutputStream; 

import android.os.AsyncTask;
import android.os.Environment;

public class TaskZipper extends AsyncTask<String, Void, Void> {

	private static final String TAG = "ActopsyTaskZipper";

	@Override
	protected Void doInBackground(String... files) {
		try {
			File root = Environment.getExternalStorageDirectory();
			if (root.canWrite()) {
				// Create folders
				File folder = new File(root, ClassConsts.FILES_ROOT);
				if (!folder.exists()) {
					folder.mkdirs();
				}
				// Compress files
				for (int i = 0; i < files.length; i++) {
					File inf = new File(folder, files[i]);
					BufferedInputStream ins = new BufferedInputStream(new FileInputStream(inf));
					File outf = new File(folder, files[i] + ".zip");
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
					new ClassEvents(TAG, "INFO", "Zipped " + outf.getName());
				}
			}
		} catch (Exception e) {
			new ClassEvents(TAG, "ERROR", "Zipper failed " + e.getMessage());
		}
		return null;
	}
}
