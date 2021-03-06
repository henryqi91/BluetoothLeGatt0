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

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
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
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SeekBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    //UI
    private SeekBar speedBar,intensityBar;
    private TextView speedBarValue,intensityBarValue,
            mRollValue,mPitchValue,mTempValue;
    private Button ledSwitch;
    private int pressFlag = 0;
    //BLE
    private Handler mRollHandler,mPitchHandler,mTempHandler,mDtHandler,mWriteHandler;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

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
                clearUI();
            }  else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayRollData(intent.getStringExtra(BluetoothLeService.EXTRA_ROLL_DATA));
                displayPitchData(intent.getStringExtra(BluetoothLeService.EXTRA_PITCH_DATA));
                displayTempData(intent.getStringExtra(BluetoothLeService.EXTRA_TEMP_DATA));
            }
        }
    };

    private void clearUI() {
//        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mRollValue.setText(R.string.no_data);
        mPitchValue.setText(R.string.no_data);
        mTempValue.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_layout);

        //read
        mRollHandler = new Handler();
        mPitchHandler = new Handler();
        mTempHandler = new Handler();
        //notify
        mDtHandler = new Handler();
        //write
        mWriteHandler = new Handler();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        initUI();
        clearUI();

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        mDtHandler.removeCallbacks(mDtRunnable);
        mWriteHandler.postDelayed(mWriteRunnable, 200);

        mTempHandler.postDelayed(mTempRunnable, 200);
        mRollHandler.postDelayed(mRollRunnable,150);
        mPitchHandler.postDelayed(mPitchRunnable, 300);

    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop(){
        super.onStop();
        mDtHandler.postDelayed(mDtRunnable, 50);
        mWriteHandler.removeCallbacks(mWriteRunnable, 200);

        mTempHandler.removeCallbacks(mTempRunnable);
        mRollHandler.removeCallbacks(mRollRunnable);
        mPitchHandler.removeCallbacks(mPitchRunnable);
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
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
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
        }
        return super.onOptionsItemSelected(item);
    }

    private Runnable mRollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mBluetoothLeService.readRollCharacteristic();
                mRollHandler.postDelayed(this, 150);
                Log.d("mRollHandler", "Calling on DCA thread");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };
    private Runnable mPitchRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mBluetoothLeService.readPitchCharacteristic();
                mPitchHandler.postDelayed(this, 250);
                Log.d("mPitchHandler", "Calling on DCA thread");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };
    private Runnable mTempRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mBluetoothLeService.readTempCharacteristic();
                mTempHandler.postDelayed(this, 300);
                Log.d("mTempHandler", "Calling on DCA thread");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private Runnable mDtRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mBluetoothLeService.setDoubleTapCharacteristic(true);
                mDtHandler.postDelayed(this, 10);
                Log.d("mDtHandler", "Calling on DCA thread");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private Runnable mWriteRunnable = new Runnable() {
        @Override
        public void run() {
            try{
                mBluetoothLeService.writeCharacteristic(
                        intensityBar.getProgress()*pressFlag,speedBar.getProgress());
//                mBluetoothLeService.writeCharacteristic(intensityBar.getProgress());
                mWriteHandler.postDelayed(this, 200);
                Log.d("mWriteHandler", "Call on DCA thread");
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    };
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayRollData(String data) {

        if (data != null) {
            String toPrint = data.split("\n")[1];
            mRollValue.setText(toPrint);
        }
    }
    private void displayPitchData(String data) {
        if (data != null) {
            String toPrint = data.split("\n")[1];
            mPitchValue.setText(toPrint);
        }
    }
    private void displayTempData(String data) {
        if (data != null) {
            String toPrint = data.split("\n")[1];
            mTempValue.setText(toPrint);
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_TONOTIFY);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_SENT);
        return intentFilter;
    }

    private void initUI(){
        speedBar = (SeekBar) findViewById(R.id.speedBar);
        speedBarValue = (TextView) findViewById(R.id.speedCurrent);

        intensityBar = (SeekBar) findViewById(R.id.intensityBar);
        intensityBarValue = (TextView) findViewById(R.id.intensityCurr);

        mRollValue = (TextView) findViewById(R.id.rollBox);
        mPitchValue = (TextView) findViewById(R.id.pitchBox);
        mTempValue = (TextView) findViewById(R.id.tempBox);

        ledSwitch = (Button)findViewById(R.id.ledSwitch);
        ledSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pressFlag == 0){
                    pressFlag = 1;
                }else{
                    pressFlag = 0;
                }
            }
        });

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //speedBar
        speedBar.setProgress(-10);
        speedBarValue.setText("Current value: " + speedBar.getProgress());
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = -10;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                progress = progressValue - 10;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                speedBarValue.setText("Current value: " + progress);
            }
        });

        //intensity Bar
        intensityBarValue.setText("Current value: " + intensityBar.getProgress());
        intensityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                progress = progressValue;

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                intensityBarValue.setText("Current value: " + progress);
            }
        });
    }

}
