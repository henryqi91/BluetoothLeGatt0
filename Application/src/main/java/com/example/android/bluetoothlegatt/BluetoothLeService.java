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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITETOSEND =
            "com.example.bluetooth.le.ACTION_DATAWRITETOSEND";
    public final static String ACTION_TONOTIFY =
            "com.example.bluetooth.le.ACTION_TONOTIFY";

    public final static String EXTRA_ROLL_DATA =
            "com.example.bluetooth.le.ROLL_DATA";
    public final static String EXTRA_PITCH_DATA =
            "com.example.bluetooth.le.PITCH_DATA";
    public final static String EXTRA_TEMP_DATA =
            "com.example.bluetooth.le.TEMP_DATA";
    public final static String EXTRA_TONOTIFY =
            "com.example.bluetooth.le.TONOTIFY";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    public final static UUID UUID_ROLL_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.ROLL_MEASUREMENT);
    public final static UUID UUID_PITCH_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.PITCH_MEASUREMENT);
    public final static UUID UUID_TEMP_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.TEMP_MEASUREMENT);

    public final static UUID UUID_SAMPLE_SERVICE =
            UUID.fromString(SampleGattAttributes.SAMPLE_SERVICE);
    public final static UUID UUID_DT_SERVICE =
            UUID.fromString(SampleGattAttributes.DT_SERVICE);
    public final static UUID UUID_SAMPLE_CHARA =
            UUID.fromString(SampleGattAttributes.SAMPLE_CHARA);
    public final static UUID UUID_DOUBLE_TAP_CHARA =
            UUID.fromString(SampleGattAttributes.DOUBLE_TAP_CHARA);
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            broadcastUpdate(ACTION_DATA_WRITETOSEND, characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic){
            broadcastUpdate(ACTION_DATA_AVAILABLE,characteristic);
        }

//        @Override
//        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
//            super.onDescriptorWrite( gatt,  descriptor,  status);
//            if(status == BluetoothGatt.GATT_SUCCESS){
//                if(gatt.getDevice().toString().equals("03:80:E1:00:34:08")){
//                    broadcastUpdate(ACTION_TONOTIFY,gatt.getService(
//                            UUID_DT_SERVICE).getCharacteristic(UUID_DOUBLE_TAP_CHARA));
//                }
//            }
//        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if(characteristic.getUuid().equals(UUID_DOUBLE_TAP_CHARA)){
            wakeUpScr();
            toNotify();
        }
        if (UUID_TEMP_MEASUREMENT.equals(characteristic.getUuid())) {
            final byte[] dataTemp = characteristic.getValue();
            if (dataTemp != null && dataTemp.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(dataTemp.length);
                short value = (short) (dataTemp[1]<<8 | dataTemp[0]&0xff);
                stringBuilder.append(String.format("%d.%d "+"\u2103", value/100,value%100));
                //celsius: \u2103; degree:\u00b0
                intent.putExtra(EXTRA_TEMP_DATA, new String(dataTemp) + "\n"
                        + stringBuilder.toString());
                sendBroadcast(intent);
            }
        }
        if(UUID_PITCH_MEASUREMENT.equals(characteristic.getUuid())) {
            final byte[] dataPitch = characteristic.getValue();
            if (dataPitch != null && dataPitch.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(dataPitch.length);
                short value = (short) (dataPitch[1] << 8 | dataPitch[0] & 0xff);
                stringBuilder.append(String.format("%d.%d " + "\u00b0", value / 100, value % 100));
                //celsius: \u2103; degree:\u00b0
                intent.putExtra(EXTRA_PITCH_DATA, new String(dataPitch) + "\n"
                        + stringBuilder.toString());
                sendBroadcast(intent);
            }
        }
            if(UUID_ROLL_MEASUREMENT.equals(characteristic.getUuid())){
                final byte[] dataRoll = characteristic.getValue();
                if (dataRoll != null && dataRoll.length > 0) {
                    final StringBuilder stringBuilder = new StringBuilder(dataRoll.length);
                    short value = (short) (dataRoll[1] << 8 | dataRoll[0] & 0xff);
//                    short newValue =(short) (value & 0xffff);
//                    stringBuilder.append(String.format("%d.%d " + "\u00b0", newValue / 100, newValue % 100));
                    stringBuilder.append(String.format("%d.%d " + "\u00b0", value / 100, value % 100));
                    //celsius: \u2103; degree:\u00b0
                    intent.putExtra(EXTRA_ROLL_DATA, new String(dataRoll) + "\n"
                            + stringBuilder.toString());
                    sendBroadcast(intent);
                }
            }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();


    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }


    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }


    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }


    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_PITCH_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }

        //roll measurement
    }

//    /*Custom Read/write*/
    public void readRollCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(
                UUID.fromString("42821a40-e477-11e2-82d0-0002a5d5c51b")); // Service for Temp,Roll,Pitch
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mRollChara = mCustomService.getCharacteristic(UUID_ROLL_MEASUREMENT);

        if(!mBluetoothGatt.readCharacteristic(mRollChara)){
            Log.w(TAG, "Failed to read Roll characteristic");
        }
    }

    public void readPitchCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(
                UUID.fromString("42821a40-e477-11e2-82d0-0002a5d5c51b")); // Service for Temp,Roll,Pitch
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mPitchChara = mCustomService.getCharacteristic(UUID_PITCH_MEASUREMENT);

        if(!mBluetoothGatt.readCharacteristic(mPitchChara)){
            Log.w(TAG, "Failed to read Pitch characteristic");
        }
    }

    public void readTempCharacteristic() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(
                UUID.fromString("42821a40-e477-11e2-82d0-0002a5d5c51b")); // Service for Temp,Roll,Pitch
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the read characteristic from the service*/
        BluetoothGattCharacteristic mTempChara = mCustomService.getCharacteristic(UUID_TEMP_MEASUREMENT);

        if(!mBluetoothGatt.readCharacteristic(mTempChara)){
            Log.w(TAG, "Failed to read Temperature characteristic");
        }
    }

    public void writeCharacteristic(int value) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        /*check if the service is available on the device*/
        BluetoothGattService mCustomService = mBluetoothGatt.getService(UUID_SAMPLE_SERVICE);
        if(mCustomService == null){
            Log.w(TAG, "Custom BLE Service not found");
            return;
        }
        /*get the write characteristic from the service*/
        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID_SAMPLE_SERVICE);

        if(mWriteCharacteristic == null){
            Log.w(TAG, "Custom BLE characteristic not found");
            return;
        }
        BluetoothGattCharacteristic mWriteChara = mCustomService.getCharacteristic(
                UUID_SAMPLE_CHARA);

        byte[] values = new byte[1];
        values[0] = (byte)(21 & 0xFF);
        mWriteChara.setValue(values);
        if(!mBluetoothGatt.writeCharacteristic(mWriteChara)){
            Log.w(TAG, "Failed to Write");
        }
    }

    public void setDoubleTapCharacteristic(boolean enabled){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        BluetoothGattService mCustomService = mBluetoothGatt.getService(
                UUID_DT_SERVICE);
        if(mCustomService == null){
            Log.w(TAG, "Double Tap Service not found");
            return;
        }
        BluetoothGattCharacteristic mDtChara = mCustomService.getCharacteristic(
                UUID_DOUBLE_TAP_CHARA);
        mBluetoothGatt.setCharacteristicNotification(mDtChara,enabled);

        if(UUID_DOUBLE_TAP_CHARA.equals(mDtChara.getUuid())){
            BluetoothGattDescriptor descriptor = mDtChara.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }

    }

    public void wakeUpScr(){
        PowerManager.WakeLock wl;
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "TAG");
        wl.acquire();
    }
    protected void toNotify(){
            Intent intent = new Intent(this, DeviceControlActivity.class);
            PendingIntent pIntent = PendingIntent.getActivity(
                    this, (int) System.currentTimeMillis(), intent, 0);

            Notification noti = new Notification.Builder(this)
                    .setContentTitle("Double Tap Detected!")
                    .setContentText("click to enter the app")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentIntent(pIntent)
                    .setAutoCancel(true).build();

            NotificationManager notiManager =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

            notiManager.notify(0,noti);
    }
}
