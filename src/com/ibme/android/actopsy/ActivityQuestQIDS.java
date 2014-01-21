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

import com.ibme.android.actopsy.R;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ActivityQuestQIDS extends SherlockActivity implements OnSharedPreferenceChangeListener {

	private static final String TAG = "ActopsyQuest";

	private ClassQuestQIDS mQuest;

//	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	@Override
	public void onCreate(Bundle savedInstanceState) {
//		if (BuildConfig.DEBUG) {
//			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//	                 .detectAll()
//	                 .penaltyLog()
//	                 .build());
//		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.quest_qids);

		// Initialize preferences
		PreferenceManager.setDefaultValues(this, R.layout.preferences, false);

		// Start services
		Intent service = new Intent(this, ServiceCollect.class);
		this.startService(service);
		service = new Intent(this, ServiceUpload.class);
		this.startService(service);

		long ts = System.currentTimeMillis();
		mQuest = new ClassQuestQIDS(this);
		mQuest.init(ts);

		final int spinners[] = {
				R.id.spinnerQIDS1,
				R.id.spinnerQIDS2,
				R.id.spinnerQIDS3,
				R.id.spinnerQIDS4,
				R.id.spinnerQIDS5,
				R.id.spinnerQIDS6,
				R.id.spinnerQIDS7,
				R.id.spinnerQIDS8,
				R.id.spinnerQIDS9,
				R.id.spinnerQIDS10,
				R.id.spinnerQIDS11,
				R.id.spinnerQIDS12,
				R.id.spinnerQIDS13,
				R.id.spinnerQIDS14,
				R.id.spinnerQIDS15,
				R.id.spinnerQIDS16
			};

		loadSpinner(R.id.spinnerQIDS1, R.array.spinner_qids1_array);
		loadSpinner(R.id.spinnerQIDS2, R.array.spinner_qids2_array);
		loadSpinner(R.id.spinnerQIDS3, R.array.spinner_qids3_array);
		loadSpinner(R.id.spinnerQIDS4, R.array.spinner_qids4_array);
		loadSpinner(R.id.spinnerQIDS5, R.array.spinner_qids5_array);
		loadSpinner(R.id.spinnerQIDS6, R.array.spinner_qids6_array);
		loadSpinner(R.id.spinnerQIDS7, R.array.spinner_qids7_array);
		loadSpinner(R.id.spinnerQIDS8, R.array.spinner_qids8_array);
		loadSpinner(R.id.spinnerQIDS9, R.array.spinner_qids9_array);
		loadSpinner(R.id.spinnerQIDS10, R.array.spinner_qids10_array);
		loadSpinner(R.id.spinnerQIDS11, R.array.spinner_qids11_array);
		loadSpinner(R.id.spinnerQIDS12, R.array.spinner_qids12_array);
		loadSpinner(R.id.spinnerQIDS13, R.array.spinner_qids13_array);
		loadSpinner(R.id.spinnerQIDS14, R.array.spinner_qids14_array);
		loadSpinner(R.id.spinnerQIDS15, R.array.spinner_qids15_array);
		loadSpinner(R.id.spinnerQIDS16, R.array.spinner_qids16_array);

		// Handle submit button
        final Button submit = (Button) findViewById(R.id.buttonSubmit);
        submit.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	int vals[] = new int[16];
            	boolean complete = true;
            	for (int i=0; i<spinners.length; i++) {
            		final Spinner spinner = (Spinner) findViewById(spinners[i]);
            		vals[i] = spinner.getSelectedItemPosition();
            		if (vals[i] == 0 || vals[i] == Spinner.INVALID_POSITION) {
            			complete = false;
                    	Toast toast = Toast.makeText(getBaseContext(), "Please answer question " + Integer.toString(i+1), 1000);
                    	toast.show();
            		}
            	}

        		long ts = System.currentTimeMillis();
        		if (complete) {
	            	mQuest.update(ts, vals);
	            	Toast toast = Toast.makeText(getBaseContext(), "Thank you", 1000);
	            	toast.show();
	            	finish();
        		}
            }
        });
    }

	private void loadSpinner(int id, int array) {
		Spinner spinner;
		spinner = (Spinner) findViewById(id);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, array, R.layout.multiline_spinner_item);
		adapter.setDropDownViewResource(R.layout.multiline_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
			    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
					if (pos == 0) {
						((TextView) parent.getChildAt(0)).setTextColor(Color.RED);
					} else {
						((TextView) parent.getChildAt(0)).setTextColor(Color.GREEN);
					}
			    }
				@Override
				public void onNothingSelected(AdapterView<?> arg0) {
					// TODO Auto-generated method stub
					
				}
			}
		);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_actopsy, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menu_quest_qids) {
			return super.onOptionsItemSelected(item);
		} else if (itemId == R.id.menu_profile) {
			Intent profileActivity = new Intent(getBaseContext(), ActivityProfile.class);
			startActivity(profileActivity);
			return true;
        } else if (itemId == R.id.menu_quest_altman) {
            Intent questActivity = new Intent(getBaseContext(), ActivityQuestAltman.class);
            startActivity(questActivity);
            return true;
        } else if (itemId == R.id.menu_quest_gad) {
            Intent questActivity = new Intent(getBaseContext(), ActivityQuestGAD.class);
            startActivity(questActivity);
            return true;
		} else if (itemId == R.id.menu_settings) {
			Intent settingsActivity = new Intent(getBaseContext(), ActivitySettings.class);
			startActivity(settingsActivity);
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		if (mQuest != null) {
			mQuest.fini();
		}
		new ClassEvents(TAG, "INFO", "Destroyed");
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	}
}
