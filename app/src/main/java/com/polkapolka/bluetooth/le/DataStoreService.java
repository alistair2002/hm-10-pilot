package com.polkapolka.bluetooth.le;

import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.List;
import java.util.UUID;

/**
 * This code is inherited from the work of the the freeboard project
 * http://www.42.co.nz/freeboard/
 * and the HM10 bluetooth low energy module project
 * http://jnhuamao.cn/
 * Please acknowledge its origins if re-using it.  I add that any re-use is done so at your own risk etc..
 *
 * this class is a background task that maintains the latest data.  It also communicates this to the 
 * pebble.  The device controller becomes a view to this data.
 */
public class DataStoreService extends Service {
    private final static UUID PEBBLE_APP_UUID = UUID.fromString("424decad-838a-4ce8-be10-ad5ce75f551a");
    //public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    //public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    public final static String ACTION_DATA_UPDATE =
            "com.example.bluetooth.le.ACTION_DATA_UPDATE";

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    //private String mDeviceAddress;
    private String nmea_sentence;

	/* the following data relates to nmea data this class manages */
    private float nmea_rsa = 0; // rudder (starboard) angle
	private float nmea_hdm = 0; // heading magnetic
	private float nmea_sog = 0; // speed over ground
	private float nmea_hdw = 0; // heading by gps
	private int   nmea_bwr_what = 0; // what bearing wanted relative
	private int	  nmea_bwr_who = 0;  // relative to what sensor

    // instance of the bound bluetooth service
    private BluetoothLeService mBluetoothLeService;

    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX = null;
    private BluetoothGattCharacteristic characteristicRX = null;

    public class LocalBinder extends Binder {
        DataStoreService getService() {
            return DataStoreService.this;
        }
    }

    public boolean onUnbind(Intent intent) {

        unregisterReceiver(mGattUpdateReceiver);
        unregisterReceiver(pebbleDataReceiver);
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            }
        }

        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    @Override
    public IBinder onBind(Intent intent) {

        PebbleKit.registerReceivedDataHandler(this, pebbleDataReceiver );

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        return mBinder;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

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
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                characteristicTX = null;
                characteristicRX = null;
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private final PebbleKit.PebbleDataReceiver pebbleDataReceiver = new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID) {

        final Handler handler = new Handler();

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
                Log.i(TAG, "Received value=" + watch_event.getInteger(0) + " for key: 0");
            }

            PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
        }

    };

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }

    }
    private void displayData(String data) {
        nmea_sentence += data;
        if (!nmea_sentence.isEmpty()) {

			/* have we loaded a complete message */
            if (nmea_sentence.contains("\r\n")) {

                nmea_sentence = nmea_sentence.replace("$", "");
                nmea_sentence = nmea_sentence.replace("\r\n", "");

                String[] pairs = nmea_sentence.split(",");

                PebbleDictionary watch_data = new PebbleDictionary();

                for (String pair1 : pairs) {
                    String[] pair = pair1.split(":");

                    if (pair[0].equals("HDM")) {

                        Float wantedBoat = Float.parseFloat(pair[1]);
                        watch_data.addInt32(0, wantedBoat.intValue());
                        nmea_hdm = wantedBoat;

                    } else if (pair[0].equals("RSA")) {

                        Float value = Float.parseFloat(pair[1]);
                        watch_data.addInt32(4, value.intValue());
                        nmea_rsa = value;

                    } else if (pair[0].equals("SOG")) {

                        Float value = Float.parseFloat(pair[1]);
                        watch_data.addInt32(4, value.intValue());
                        nmea_sog = value;

                    } else if (pair[0].equals("HDW")) {

                        Float value = Float.parseFloat(pair[1]);
                        watch_data.addInt32(4, value.intValue());

                        nmea_hdw = value;

                    } else if (pair[0].equals("BWR")) { // bearing wanted rudder

                        String[] whatandwho = pair[1].split("\\.");
                        if ((null != whatandwho[0]) && (null != whatandwho[1])) {

                            nmea_bwr_what = Integer.parseInt(whatandwho[0]);
                            nmea_bwr_who = Integer.parseInt(whatandwho[1]);

                            watch_data.addInt32(1, nmea_bwr_what);
                            watch_data.addInt32(2, nmea_bwr_who);
                        }
                    }
                }
                nmea_sentence = "";

                if ((PebbleKit.isWatchConnected(getApplicationContext())) &&
                        (PebbleKit.areAppMessagesSupported(getApplicationContext()))) {

                    PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, watch_data);
                }

                final Intent intent = new Intent(ACTION_DATA_UPDATE);
                sendBroadcast(intent);
            }
        }
	}

    public void onFreeboardString(String freeboardSentence) {
        final byte[] tx = freeboardSentence.getBytes();
        if(mConnected && (null != characteristicTX) && (null != characteristicRX)) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX,true);
        }
    }

    public float getNmea_rsa() { return nmea_rsa; }
    public float getNmea_hdm() { return nmea_hdm; }
	public float getNmea_sog() { return nmea_sog; }
	public float getNmea_hdw() { return nmea_hdw; }
	public int getNmea_bwr_what() { return nmea_bwr_what; }
	public int getNmea_bwr_who() { return nmea_bwr_who; }

    public boolean isConnected() { return mConnected; }
}
