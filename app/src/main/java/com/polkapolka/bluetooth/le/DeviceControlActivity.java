/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.polkapolka.bluetooth.le;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends FragmentActivity
        implements ScreenSlideCompass.OnFreeboardStringSend,
        ScreenSlideCOG.OnFreeboardStringSend,
        ScreenSlideRudder.OnFreeboardStringSend,
        ScreenSlideSOG.OnFreeboardStringSend {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final String PrefStoredFile =  "PilotBLEFile";
    private static final String PrefStoredDevice = "PilotBLEDevice";
    private String mDeviceAddress;

	//  private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private DataStoreService mDataStoreService;

	private ScreenSlideCompass      mCompassSlide;
    private ScreenSlideRudder       mRudderSlide;
    private ScreenSlideCOG          mCOGSlide;
    private ScreenSlideSOG          mSOGSlide;
    private CompassActivityFragment compassActivityFragment;

    private TextView mConnectionState;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (mBluetoothLeService.initialize()) {
                // Automatically connects to the device upon successful start-up initialization.
                mBluetoothLeService.connect(mDeviceAddress);

            } else {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }

    };

    private final ServiceConnection mDataStore = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mDataStoreService = ((DataStoreService.LocalBinder) service) .getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mDataStoreService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            }
        }
    };

    private final BroadcastReceiver dataServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            control_value_changed();
        }
    };

    public void menuImageSelect(View v) {
        openOptionsMenu();
    }

    private void setConnectionState(int resourceId)
    {
        mConnectionState.setText(resourceId);
    }
    // private void clearUI() {
    //     mDataField.setText(R.string.no_data);
    // }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.root_activity_view);

        SharedPreferences settings = getSharedPreferences(PrefStoredFile, 0);

        final Intent intent = getIntent();
        //mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        ViewPager mPager;
        PagerAdapter mPagerAdapter;

        if (null == mDeviceAddress) {
            Log.d( TAG, "** onCreate null device stored is :" +
                    settings.getString(PrefStoredDevice, null ));
            mDeviceAddress = settings.getString(PrefStoredDevice, null );
        } else {
            Log.d( TAG, "** onCreate non null :" + mDeviceAddress +
            " stored " + settings.getString(PrefStoredDevice, null ));

            SharedPreferences.Editor editor = settings.edit();
            editor.putString(PrefStoredDevice, mDeviceAddress);

            editor.apply();
        }

        Log.d(TAG, "create mDevice " + mDeviceAddress);

        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ArseSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        compassActivityFragment = (CompassActivityFragment) fragmentManager.findFragmentById(R.id.compassActivity);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Intent dataServiceIntent = new Intent(this, DataStoreService.class);
        bindService(dataServiceIntent, mDataStore, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ((null != mDataStoreService) && mDataStoreService.isConnected()) {
            updateConnectionState(R.string.connected);
        } else {
            updateConnectionState(R.string.disconnected);
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        registerReceiver(dataServiceReceiver, dataServiceFilter());

        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
	}
	
    @Override
    protected void onPause() {
        unregisterReceiver(mGattUpdateReceiver);
        unregisterReceiver(dataServiceReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mDataStore);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (( null != mDataStoreService) &&
            (mDataStoreService.isConnected())) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_rescan).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_rescan).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_rescan:
                Intent wakeScan = new Intent (this, DeviceScanActivity.class);
                startActivity( wakeScan );
                return true;
            case R.id.bubble_off:
                onFreeboardString("#OUT:0\r\n");
                return true;
            case R.id.bubble_compass:
                onFreeboardString("#OUT:1\r\n");
                return true;
            case R.id.bubble_cog:
                onFreeboardString("#OUT:2\r\n");
                return true;
            case R.id.bubble_amp:
                onFreeboardString("#OUT:3\r\n");
            case R.id.pid_off:
                onFreeboardString("#OFF:0\r\n");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setConnectionState(resourceId);
            }
        });
    }

	private void control_value_changed( ) {

        if (null != mDataStoreService) {

            compassActivityFragment.setWantedBoat(mDataStoreService.getNmea_hdm());
            if (null != mCompassSlide)
                mCompassSlide.setBearing(Math.round(mDataStoreService.getNmea_hdm()));
            if (null != mRudderSlide)
                mRudderSlide.setRudder(Math.round(mDataStoreService.getNmea_rsa()));
            if (null != mSOGSlide)
                mSOGSlide.setSOG(Math.round(mDataStoreService.getNmea_sog()));
            if (null != mCOGSlide)
                mCOGSlide.setCOG(Math.round(mDataStoreService.getNmea_hdw()));

            String what_string = String.valueOf(mDataStoreService.getNmea_bwr_what());

            switch (mDataStoreService.getNmea_bwr_who()) {
            case 1: // rudder
                if (null != mCompassSlide) {
                    mCompassSlide.setWanted("");
                }
                if (null != mRudderSlide) {
                    mRudderSlide.setWanted(what_string);
                }
                if (null != mCOGSlide) {
                    mCOGSlide.setWanted("");
                }
                break;
            case 2: // compass
                if (null != mCompassSlide) {
                    mCompassSlide.setWanted(what_string);
                }
                if (null != mRudderSlide) {
                    mRudderSlide.setWanted("");
                }
                if (null != mCOGSlide) {
                    mCOGSlide.setWanted("");
                }
                break;
            case 3: // cog
                if (null != mCompassSlide) {
                    mCompassSlide.setWanted("");
                }
                if (null != mRudderSlide) {
                    mRudderSlide.setWanted("");
                }
                if (null != mCOGSlide) {
                    mCOGSlide.setWanted(what_string);
                }
                break;
            default:
                break;
        }}
	}



    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    private static IntentFilter dataServiceFilter() {
    final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DataStoreService.ACTION_DATA_UPDATE);
        return intentFilter;
    }

    @Override
    public void onFreeboardString(String freeboardSentence) {
        if (null != mDataStoreService) {
            mDataStoreService.onFreeboardString(freeboardSentence);
        }
    }

    /**
     * A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private class ArseSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ArseSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return mCompassSlide = new ScreenSlideCompass();
                case 1:
                    return mRudderSlide = new ScreenSlideRudder();
                case 2:
                    return mCOGSlide = new ScreenSlideCOG();
                default:
                    return mSOGSlide = new ScreenSlideSOG();
            }
        }

        @Override
        public int getCount() {
            return 4; /* this should be a dimention thing not a magic number */
        }
    }

}
