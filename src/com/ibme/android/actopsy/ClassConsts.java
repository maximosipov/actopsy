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

public class ClassConsts {
	public static final String FILES_ROOT = "/Android/data/com.ibme.android.actopsy/files/";
	public static final String PREFS_SUMMARY = "profile-summary";
	public static final String PREFS_STATS = "profile-stats";
	public static final String PREFS_LOCATION = "location-prefs";
	public static final String DATEFMT = "yyyy-MM-dd HH:mm:ss.SSSZ";

	public static final long MILLIDAY = 24*60*60*1000;		// Day in milliseconds
	public static final long MILLIHOUR = 60*60*1000;

	public static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

	public static final double G = 9.81;
	
	public static final long LOC_TIME = 1*60*1000;			// 1 minute granularity
	public static final float LOC_DIST = 0;					// don't care about distance

	public static final String UPLOAD_ALARM = "com.ibme.android.actopsy.UploadService.AlarmReceiver";
	public static final long UPLOAD_PERIOD = 24*60*60*1000L;	// 24 hours
	public static final String UPLOAD_URL = "https://ibme-web7.eng.ox.ac.uk/upload.php";
	public static final String UPLOAD_SIZE = "10000000"; // 10MB

	public static final int BUFFER_SIZE = 8192;
}
