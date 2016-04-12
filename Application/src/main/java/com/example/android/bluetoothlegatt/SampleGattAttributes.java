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

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();

    //measurement - read
    public static String HEART_RATE_MEASUREMENT =       "00002a37-0000-1000-8000-00805f9b34fb";
    public static String TEMP_MEASUREMENT =             "a32e5520-e477-11e2-a9e3-0002a5d5c51b";
    public static String PITCH_MEASUREMENT =            "cd20c480-e48b-11e2-840b-0002a5d5c51b";
    public static String ROLL_MEASUREMENT =             "01c50b60-e48c-11e2-a073-0002a5d5c51b";

    //double tap - notify
    public static String DT_SERVICE =                   "02366e80-cf3a-11e1-9ab4-0002a5d5c51b";
    public static String DOUBLE_TAP_CHARA =             "e23e78a0-cf4a-11e1-8ffc-0002a5d5c51b";
    //to write - write
    public static String SAMPLE_SERVICE =               "8263e608-cf3a-11e1-9ab4-0002a5d5c51b";
    public static String SAMPLE_CHARA =                 "340a1b80-cf4b-11e1-ac36-0002a5d5c51b";

    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        //required Characteristics
        attributes.put(TEMP_MEASUREMENT, "temperature measurement");
        attributes.put(PITCH_MEASUREMENT, "pitch measurement");
        attributes.put(ROLL_MEASUREMENT, "roll measurement");
        attributes.put(DOUBLE_TAP_CHARA, "double tap notification");
    }
}
