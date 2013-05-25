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

import com.ibme.android.actopsy.R;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class ReceiverActopsy extends BroadcastReceiver {

	private static final String TAG = "ActopsyReceiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Intent service;
		ComponentName comp;

		// data collection service
		service = new Intent(context, ServiceCollect.class);
		comp = context.startService(service);
		if (comp == null) {
			Log.e(TAG, "Could not start service " + comp.toString());
		} else {
			Log.i(TAG, "Started service");   
		}

		// data upload and housekeeping service
		service = new Intent(context, ServiceUpload.class);
		comp = context.startService(service);
		if (comp == null){
			Log.e(TAG, "Could not start service " + comp.toString());
		} else {
			Log.i(TAG, "Started service");   
		}
	}
}
