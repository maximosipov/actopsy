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

public class ClassConsts {
	public static final String FILES_ROOT = "/Android/data/com.ibme.android.actopsy/files/";
	public static final String PREFS_SUMMARY = "profile-summary";
	public static final String PREFS_STATS = "profile-stats";
	public static final String CACHE_ROOT = "/Android/data/com.ibme.android.actopsy/cache/";
	public static final String CACHE_FILES = "files.cache";

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
