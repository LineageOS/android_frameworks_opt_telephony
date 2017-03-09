/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.internal.telephony;

import static android.hardware.radio.V1_0.DeviceStateType.CHARGING_STATE;
import static android.hardware.radio.V1_0.DeviceStateType.LOW_DATA_EXPECTED;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static java.util.Arrays.asList;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.BatteryManager;
import android.os.HandlerThread;
import android.os.Message;
import android.support.test.filters.FlakyTest;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class DeviceStateMonitorTest extends TelephonyTest {

    private DeviceStateMonitor mDSM;

    private DeviceStateMonitorTestHandler mDeviceStateMonitorTestHandler;

    private class DeviceStateMonitorTestHandler extends HandlerThread {

        private DeviceStateMonitorTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDSM = new DeviceStateMonitor(mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDeviceStateMonitorTestHandler = new DeviceStateMonitorTestHandler(TAG);
        mDeviceStateMonitorTestHandler.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mDeviceStateMonitor = null;
        mDeviceStateMonitorTestHandler.quitSafely();
        super.tearDown();
    }

    @FlakyTest
    @Test
    @SmallTest
    public void testTethering() throws Exception {
        // Turn tethering on
        Intent intent = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>(asList("abc")));
        mContext.sendBroadcast(intent);

        waitForMs(100);

        verify(mSimulatedCommandsVerifier, times(1)).setUnsolResponseFilter(eq(6),
                any(Message.class));

        // Turn tethering off
        intent = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>());
        mContext.sendBroadcast(intent);
        waitForMs(100);

        verify(mSimulatedCommandsVerifier, times(1)).setUnsolResponseFilter(eq(0),
                any(Message.class));

        verify(mSimulatedCommandsVerifier, times(1)).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), any(Message.class));
    }

    @FlakyTest
    @Test
    @SmallTest
    public void testCharging() throws Exception {
        // Charging
        Intent intent = new Intent(BatteryManager.ACTION_CHARGING);
        mContext.sendBroadcast(intent);
        waitForMs(100);

        verify(mSimulatedCommandsVerifier, times(1)).sendDeviceState(eq(CHARGING_STATE),
                eq(true), any(Message.class));

        // Not charging
        intent = new Intent(BatteryManager.ACTION_DISCHARGING);
        mContext.sendBroadcast(intent);
        waitForMs(100);

        verify(mSimulatedCommandsVerifier, times(1)).setUnsolResponseFilter(eq(0),
                any(Message.class));

        verify(mSimulatedCommandsVerifier, times(1)).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), any(Message.class));

        verify(mSimulatedCommandsVerifier, times(1)).sendDeviceState(eq(CHARGING_STATE),
                eq(false), any(Message.class));
    }
}
