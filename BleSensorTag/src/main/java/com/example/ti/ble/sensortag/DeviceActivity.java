/**************************************************************************************************
  Filename:       DeviceActivity.java
  Revised:        $Date: Wed Apr 22 13:01:34 2015 +0200$
  Revision:       $Revision: 599e5650a33a4a142d060c959561f9e9b0d88146$

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI ('TI Devices').
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

 * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
 * any redistribution and use are licensed by TI for use only with TI Devices.
 * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

 * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
 * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TI'S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI'S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.sensortag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
// import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;


import com.example.ti.ble.btsig.profiles.DeviceInformationServiceProfile;
import com.example.ti.ble.common.BluetoothLeService;
import com.example.ti.ble.common.GattInfo;
import com.example.ti.ble.common.GenericBluetoothProfile;
import com.example.ti.ble.common.HelpView;
import com.example.ti.ble.ti.profiles.TIOADProfile;


@SuppressLint("InflateParams") public class DeviceActivity extends ViewPagerActivity {
	// Log
	// private static String TAG = "DeviceActivity";

	// Activity
	public static final String EXTRA_DEVICE = "EXTRA_DEVICE";
	private static final int PREF_ACT_REQ = 0;
	private static final int FWUPDATE_ACT_REQ = 1;

	private DeviceView mDeviceView = null;

	// BLE
	private BluetoothLeService mBtLeService = null;
	private BluetoothDevice mBluetoothDevice = null;
	private BluetoothGatt mBtGatt = null;
	private List<BluetoothGattService> mServiceList = null;
	private boolean mServicesRdy = false;
	private boolean mIsReceiving = false;

	// SensorTagGatt
	private BluetoothGattService mOadService = null;
	private BluetoothGattService mConnControlService = null;
	private boolean mIsSensorTag2;
	private String mFwRev;
	public ProgressDialog progressDialog;

	//GUI
	private List<GenericBluetoothProfile> mProfiles;

	public DeviceActivity() {
		mResourceFragmentPager = R.layout.fragment_pager;
		mResourceIdPager = R.id.pager;
		mFwRev = new String("1.5"); // Assuming all SensorTags are up to date until actual FW revision is read
	}

	public static DeviceActivity getInstance() {
		return (DeviceActivity) mThis;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();

		// BLE
		mBtLeService = BluetoothLeService.getInstance();
		mBluetoothDevice = intent.getParcelableExtra(EXTRA_DEVICE);
		mServiceList = new ArrayList<BluetoothGattService>();

		mIsSensorTag2 = false;
		// Determine type of SensorTagGatt
		String deviceName = mBluetoothDevice.getName();
		if ((deviceName.equals("SensorTag2")) ||(deviceName.equals("CC2650 SensorTag"))) {
			mIsSensorTag2 = true;
		}
		else mIsSensorTag2 = false;

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		// Log.i(TAG, "Preferences for: " + deviceName);

		// GUI
		mDeviceView = new DeviceView();
		mSectionsPagerAdapter.addSection(mDeviceView, "Sensors");
		HelpView hw = new HelpView();
		hw.setParameters("help_device.html", R.layout.fragment_help, R.id.webpage);
		mSectionsPagerAdapter.addSection(hw, "Help");
		mProfiles = new ArrayList<GenericBluetoothProfile>();
		progressDialog = new ProgressDialog(DeviceActivity.this);
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setIndeterminate(true);
		progressDialog.setTitle("Discovering Services");
        progressDialog.setMessage("");
		progressDialog.setMax(100);
        progressDialog.setProgress(0);
        progressDialog.show();

        // GATT database
		Resources res = getResources();
		XmlResourceParser xpp = res.getXml(R.xml.gatt_uuid);
		new GattInfo(xpp);

	}


	public boolean onCreateOptionsMenu(Menu menu) {
		this.optionsMenu = menu;
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.device_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}


	public boolean isEnabledByPrefs(String prefName) {
		String preferenceKeyString = "pref_"
				+ prefName;

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mBtLeService);

		Boolean defaultValue = true;
		return prefs.getBoolean(preferenceKeyString, defaultValue);
	}

	protected void onPause() {
		// Log.d(TAG, "onPause");
		super.onPause();
	}
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter fi = new IntentFilter();
		fi.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		fi.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
		fi.addAction(BluetoothLeService.ACTION_DATA_WRITE);
		fi.addAction(BluetoothLeService.ACTION_DATA_READ);
		fi.addAction(DeviceInformationServiceProfile.ACTION_FW_REV_UPDATED);
        fi.addAction(TIOADProfile.ACTION_PREPARE_FOR_OAD);
		return fi;
	}


	boolean isSensorTag2() {
		return mIsSensorTag2;
	}

	String firmwareRevision() {
		return mFwRev;
	}
	BluetoothGattService getOadService() {
		return mOadService;
	}

	BluetoothGattService getConnControlService() {
		return mConnControlService;
	}


	private void setBusy(boolean b) {
		mDeviceView.setBusy(b);
	}
	// Activity result handling
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		default:
			break;
		}
	}

	private void setError(String txt) {
		setBusy(false);
		Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
	}

	private void setStatus(String txt) {
		Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
	}


    class firmwareUpdateStart extends AsyncTask<String, Integer, Void> {
        ProgressDialog pd;
        Context con;

        public firmwareUpdateStart(ProgressDialog p,Context c) {
            this.pd = p;
            this.con = c;
        }

        @Override
        protected void onPreExecute() {
            this.pd = new ProgressDialog(DeviceActivity.this);
            this.pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            this.pd.setIndeterminate(false);
            this.pd.setTitle("Starting firmware update");
            this.pd.setMessage("");
            this.pd.setMax(mProfiles.size());
            this.pd.show();
            super.onPreExecute();
        }
        @Override
        protected Void doInBackground(String... params) {
            Integer ii = 1;
            for (GenericBluetoothProfile p : mProfiles) {

                p.disableService();
                p.deConfigureService();
                publishProgress(ii);
                ii = ii + 1;
            }

            if (isSensorTag2()) {
                final Intent i = new Intent(this.con, FwUpdateActivity_CC26xx.class);
                startActivityForResult(i, FWUPDATE_ACT_REQ);
            }
            else {
                final Intent i = new Intent(this.con, FwUpdateActivity.class);
                startActivityForResult(i, FWUPDATE_ACT_REQ);
            }
            return null;
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            this.pd.setProgress(values[0]);
        }
        @Override
        protected void onPostExecute(Void result) {
            this.pd.dismiss();
            super.onPostExecute(result);
        }

    }
}
