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

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
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

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
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
    private final static UUID PEBBLE_APP_UUID = UUID.fromString("424decad-838a-4ce8-be10-ad5ce75f551a");

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    //private TextView isSerial;
    //private TextView mDataField;
    //private String mDeviceName;
    private String mDeviceAddress;
	private String nmea_sentence;

    // to update the wanted value
    private int last_wanted_value;
    private int last_wanted_controller;

	//  private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
	private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

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
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
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
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    public void menuImageSelect(View v) {
        openOptionsMenu();
    }

    public void setConnectionState(int resourceId)
    {
        mConnectionState.setText(resourceId);
    }
    // private void clearUI() {
    //     mDataField.setText(R.string.no_data);
    // }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        //mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        android.app.FragmentManager fragmentManager = getFragmentManager();
        ViewPager mPager;
        PagerAdapter mPagerAdapter;

        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ArseSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        compassActivityFragment = (CompassActivityFragment) fragmentManager.findFragmentById(R.id.compassActivity);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        final Handler handler = new Handler();

        PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID) {

            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary watch_event) {


                if (null != watch_event.getInteger(0)) // OK it should be a constant but 0 is the key value
                {
                    final int key = watch_event.getInteger(0).intValue();
                    handler.post(new Runnable() {
                                     public void run() {
                            /* Update your UI here. */
                            switch (key)
                            {
                                case 1: // key up
                                    onFreeboardString("#BWR:-10\r\n");
                                    break;
                                case 2:
                                    onFreeboardString("#BWR:10\r\n");
                                    break;
                                default:
                            }
                        }
                    });
                    Log.i(getLocalClassName(), "Received value=" + watch_event.getInteger(0) + " for key: 0");
                }

                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
            }

        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
	}
	
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
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

	private void control_value_changed( int who, int what, String what_string ) {
		last_wanted_controller = who;
		last_wanted_value = what;

		switch (who){
			case 1: // rudder
				if (null != mCompassSlide) { mCompassSlide.setWanted(""); }
				if (null != mRudderSlide) { mRudderSlide.setWanted(what_string); }
				if (null != mCOGSlide) { mCOGSlide.setWanted(""); }
				break;
            case 2: // compass
                if (null != mCompassSlide) { mCompassSlide.setWanted(what_string); }
                if (null != mRudderSlide) { mRudderSlide.setWanted(""); }
                if (null != mCOGSlide) {  mCOGSlide.setWanted(""); }
                break;
			case 3: // cog
				if (null != mCompassSlide) { mCompassSlide.setWanted(""); }
				if (null != mRudderSlide) { mRudderSlide.setWanted(""); }
				if (null != mCOGSlide) { mCOGSlide.setWanted(what_string); }
				break;
			default:
				break;
		}
	}

    private void displayData(String data) {

        nmea_sentence += data;

        if (!nmea_sentence.isEmpty()) {

			if (nmea_sentence.contains("\r\n")) {

                boolean pebbled = PebbleKit.isWatchConnected(getApplicationContext());

                if (!PebbleKit.areAppMessagesSupported(getApplicationContext())) {
                    pebbled = false;
                }
				nmea_sentence = nmea_sentence.replace("$","");
				nmea_sentence = nmea_sentence.replace("\r\n","");
				
                String[] pairs = nmea_sentence.split(",");

                PebbleDictionary watch_data = new PebbleDictionary();

                for (String pair1 : pairs) {
                    String[] pair = pair1.split(":");

                    if (pair[0].equals("HDM")) {
                        Float wantedBoat = Float.parseFloat(pair[1]);

                        if (pebbled) {
                            watch_data.addInt32(0, wantedBoat.intValue());
                        }
                        if (null != compassActivityFragment) {
                            compassActivityFragment.setWantedBoat(wantedBoat);
                        }
                        if (null != mCompassSlide)
                        {
                            mCompassSlide.setBearing(wantedBoat.intValue());
                        }
                    } else if (pair[0].equals("RSA")) {
                        Float value = Float.parseFloat(pair[1]);
                        if (null != mRudderSlide)
                        {
                            if (pebbled) {
                                watch_data.addInt32(4, value.intValue());
                            }
                            mRudderSlide.setRudder(value.intValue());
                        }
					} else if (pair[0].equals("SOG")) {
                        Float value = Float.parseFloat(pair[1]);
                        if (null != mSOGSlide)
                        {
                            if (pebbled) {
                                Float value_ten = value * 10;
                                watch_data.addInt32(3, value_ten.intValue());
                            }
                            mSOGSlide.setSOG(value.intValue());
                        }
                    } else if (pair[0].equals("HDW")) {
                        Float value = Float.parseFloat(pair[1]);
                        if (null != mCOGSlide)
                        {
                            if (pebbled) {
                                watch_data.addInt32(5, value.intValue());
                            }
                            mCOGSlide.setCOG(value.intValue());
                        }
                    } else if (pair[0].equals("BWR")) { // bearing wanted rudder
                        String[] whatandwho = pair[1].split("\\.");
                        if (pebbled && (null != whatandwho[0]) && (null != whatandwho[1])) {

                            int what = Integer.parseInt(whatandwho[0]);
                            int who = Integer.parseInt(whatandwho[1]);

                            if ((who != last_wanted_controller) ||
                                (what != last_wanted_value)) {
                                control_value_changed( who, what, whatandwho[0] );
                            }

                            watch_data.addInt32(1, what);
                            watch_data.addInt32(2, who);
                        }
                    }

					
                }
				nmea_sentence = "";

                PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, watch_data);
			}
        }

    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();

        UUID HM_RX_TX = UUID.fromString(SampleGattAttributes.HM_RX_TX);

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();

            String LIST_NAME = "NAME";
            String LIST_UUID = "UUID";

            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            
            // If the service exists for HM 10 Serial, say so.
			//            if(SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") { isSerial.setText("HM-10 :-)"); } else {  isSerial.setText("Not HM-10 ;-("); }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

     		// get characteristic when UUID matches RX/TX UUID
    		 characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
    		 characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }
        
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onFreeboardString(String freeboardSentence) {
        final byte[] tx = freeboardSentence.getBytes();
        if(mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX,true);
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
