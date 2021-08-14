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
import static android.hardware.radio.V1_0.DeviceStateType.POWER_SAVE_MODE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import static java.util.Arrays.asList;

import android.annotation.IntDef;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.V1_5.IndicationFilter;
import android.net.ConnectivityManager;
import android.net.TetheringManager;
import android.os.BatteryManager;
import android.os.Message;
import android.test.suitebuilder.annotation.MediumTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Map;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DeviceStateMonitorTest extends TelephonyTest {
    private static final int INDICATION_FILTERS_MINIMUM = IndicationFilter.REGISTRATION_FAILURE;

    // All implemented indiation filters set so far
    // which is a subset of IndicationFilter.ALL
    private static final int INDICATION_FILTERS_ALL =
            IndicationFilter.SIGNAL_STRENGTH
            | IndicationFilter.FULL_NETWORK_STATE
            | IndicationFilter.DATA_CALL_DORMANCY_CHANGED
            | IndicationFilter.LINK_CAPACITY_ESTIMATE
            | IndicationFilter.PHYSICAL_CHANNEL_CONFIG
            | IndicationFilter.REGISTRATION_FAILURE
            | IndicationFilter.BARRING_INFO;

    // INDICATION_FILTERS_ALL but excludes Indication.SIGNAL_STRENGTH
    private static final int INDICATION_FILTERS_WHEN_TETHERING_ON =
            INDICATION_FILTERS_ALL & ~IndicationFilter.SIGNAL_STRENGTH;
    private static final int INDICATION_FILTERS_WHEN_CHARGING = INDICATION_FILTERS_ALL;
    private static final int INDICATION_FILTERS_WHEN_SCREEN_ON = INDICATION_FILTERS_ALL;

    /** @hide */
    @IntDef(prefix = {"STATE_TYPE_"}, value = {
        STATE_TYPE_RIL_CONNECTED,
        STATE_TYPE_SCREEN,
        STATE_TYPE_POWER_SAVE_MODE,
        STATE_TYPE_CHARGING,
        STATE_TYPE_TETHERING,
        STATE_TYPE_RADIO_AVAILABLE,
        STATE_TYPE_WIFI_CONNECTED,
        STATE_TYPE_ALWAYS_SIGNAL_STRENGTH_REPORTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface StateType {}

    // Keep the same value as correspoinding event
    // See state2Event() for detail
    private static final int STATE_TYPE_RIL_CONNECTED = 0;
    // EVENT_UPDATE_NODE_CHANGED is not here, it will be removed in aosp soon
    private static final int STATE_TYPE_SCREEN = 2;
    private static final int STATE_TYPE_POWER_SAVE_MODE = 3;
    private static final int STATE_TYPE_CHARGING = 4;
    private static final int STATE_TYPE_TETHERING = 5;
    private static final int STATE_TYPE_RADIO_AVAILABLE = 6;
    private static final int STATE_TYPE_WIFI_CONNECTED = 7;
    private static final int STATE_TYPE_ALWAYS_SIGNAL_STRENGTH_REPORTED = 8;

    /** @hide */
    @IntDef(prefix = {"STATE_"}, value = {
        STATE_OFF,
        STATE_ON
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface StateStatus {}

    private static final int STATE_OFF = 0;
    private static final int STATE_ON = 1;

    // The keys are the single IndicationFilter flags,
    // The values are the array of states, when one state turn on, the corresponding
    // IndicationFilter flag should NOT be turned off.
    private static final Map<Integer, int[]> INDICATION_FILTER_2_TRIGGERS = Map.of(
            IndicationFilter.SIGNAL_STRENGTH,             new int[] {
                STATE_TYPE_ALWAYS_SIGNAL_STRENGTH_REPORTED, STATE_TYPE_CHARGING, STATE_TYPE_SCREEN},
            IndicationFilter.FULL_NETWORK_STATE,          new int[] {
                STATE_TYPE_CHARGING, STATE_TYPE_SCREEN, STATE_TYPE_TETHERING},
            IndicationFilter.DATA_CALL_DORMANCY_CHANGED,  new int[] {
                STATE_TYPE_CHARGING, STATE_TYPE_SCREEN, STATE_TYPE_TETHERING},
            IndicationFilter.LINK_CAPACITY_ESTIMATE,      new int[] {
                STATE_TYPE_CHARGING, STATE_TYPE_SCREEN, STATE_TYPE_TETHERING},
            IndicationFilter.PHYSICAL_CHANNEL_CONFIG,     new int[] {
                STATE_TYPE_CHARGING, STATE_TYPE_SCREEN, STATE_TYPE_TETHERING}
    );

    @Mock
    UiModeManager mUiModeManager;
    private DeviceStateMonitor mDSM;
    // Given a stateType, return the event type that can change the state
    private int state2Event(@StateType int stateType) {
        // As long as we keep the same value, we can directly return the stateType
        return stateType;
    }

    private void updateState(@StateType int stateType, @StateStatus int stateValue) {
        final int event = state2Event(stateType);
        mDSM.obtainMessage(event, stateValue, 0 /* arg2, not used*/).sendToTarget();
        processAllMessages();
    }

    private void updateAllStatesToOff() {
        updateState(STATE_TYPE_RIL_CONNECTED, STATE_OFF);
        updateState(STATE_TYPE_SCREEN, STATE_OFF);
        updateState(STATE_TYPE_POWER_SAVE_MODE, STATE_OFF);
        updateState(STATE_TYPE_CHARGING, STATE_OFF);
        updateState(STATE_TYPE_TETHERING, STATE_OFF);
        updateState(STATE_TYPE_RADIO_AVAILABLE, STATE_OFF);
        updateState(STATE_TYPE_WIFI_CONNECTED, STATE_OFF);
        updateState(STATE_TYPE_ALWAYS_SIGNAL_STRENGTH_REPORTED, STATE_OFF);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mContextFixture.setSystemService(Context.UI_MODE_SERVICE, mUiModeManager);
        // We don't even need a mock executor, we just need to not throw.
        doReturn(null).when(mContextFixture.getTestDouble()).getMainExecutor();
        mDSM = new DeviceStateMonitor(mPhone);

        // Initialize with ALL states off
        updateAllStatesToOff();

        // eliminate the accumuted impact on Mockito.verify()
        reset(mSimulatedCommandsVerifier);
    }

    @After
    public void tearDown() throws Exception {
        mDSM = null;
        super.tearDown();
    }

    /**
     * Verify the behavior of CI.setUnsolResponseFilter().
     * Keeping other state unchanged, when one state change. setUnsolResponseFilter()
     * should be called with right IndicationFilter flag set.
     */
    @Test @MediumTest
    public void testSetUnsolResponseFilter_singleStateChange() {
        for (int indicationFilter : INDICATION_FILTER_2_TRIGGERS.keySet()) {
            for (int state : INDICATION_FILTER_2_TRIGGERS.get(indicationFilter)) {
                verifySetUnsolResponseFilter(state, indicationFilter);
            }
        }
    }

    private void verifySetUnsolResponseFilter(int state, int indicationFilter) {
        reset(mSimulatedCommandsVerifier);
        // In the beginning, all states are off

        // Turn on the state
        updateState(state, STATE_ON);

        // Keep other states off, then specified indication filter should NOT be turn off
        ArgumentCaptor<Integer> acIndicationFilter = ArgumentCaptor.forClass(Integer.class);
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                acIndicationFilter.capture(), nullable(Message.class));
        assertNotEquals((acIndicationFilter.getValue() & indicationFilter), 0);

        // Turn off the state again
        updateState(state, STATE_OFF);

        // Keep other states off, then no filter flag is on
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_MINIMUM), nullable(Message.class));
    }

    @Test
    public void testSetUnsolResponseFilter_noReduandantCall() {
        // initially all state off, turn screen on
        updateState(STATE_TYPE_SCREEN, STATE_ON);
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(anyInt(),
                nullable(Message.class));
        reset(mSimulatedCommandsVerifier);

        updateState(STATE_TYPE_CHARGING, STATE_ON);
        verify(mSimulatedCommandsVerifier, never()).setUnsolResponseFilter(anyInt(),
                nullable(Message.class));

        updateState(STATE_TYPE_POWER_SAVE_MODE, STATE_ON);
        verify(mSimulatedCommandsVerifier, never()).setUnsolResponseFilter(anyInt(),
                nullable(Message.class));
    }

    @Test
    public void testScreenOnOff() {
        // screen was off by default, turn it on now
        updateState(STATE_TYPE_SCREEN, STATE_ON);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_WHEN_SCREEN_ON), nullable(Message.class));

        // turn screen off
        updateState(STATE_TYPE_SCREEN, STATE_OFF);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_MINIMUM), nullable(Message.class));
    }

    @Test
    public void testTethering() {
        // Turn tethering on
        Intent intent = new Intent(TetheringManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>(asList("abc")));
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_WHEN_TETHERING_ON), nullable(Message.class));

        // Turn tethering off
        intent = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        intent.putExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, new ArrayList<>());
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_MINIMUM), nullable(Message.class));

        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), nullable(Message.class));
    }

    @Test
    public void testCharging() {
        // Charging
        Intent intent = new Intent(BatteryManager.ACTION_CHARGING);
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_WHEN_CHARGING), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(CHARGING_STATE),
                eq(true), nullable(Message.class));

        // Not charging
        intent = new Intent(BatteryManager.ACTION_DISCHARGING);
        mContext.sendBroadcast(intent);
        processAllMessages();

        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(
                eq(INDICATION_FILTERS_MINIMUM), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(LOW_DATA_EXPECTED),
                eq(true), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(CHARGING_STATE),
                eq(false), nullable(Message.class));
    }

    @Test
    public void testReset() {
        testResetFromEvent(DeviceStateMonitor.EVENT_RIL_CONNECTED);
        testResetFromEvent(DeviceStateMonitor.EVENT_RADIO_AVAILABLE);
    }

    private void testResetFromEvent(int event) {
        reset(mSimulatedCommandsVerifier);
        mDSM.obtainMessage(event).sendToTarget();
        processAllMessages();

        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(CHARGING_STATE),
                anyBoolean(), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(LOW_DATA_EXPECTED),
                anyBoolean(), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).sendDeviceState(eq(POWER_SAVE_MODE),
                anyBoolean(), nullable(Message.class));
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(anyInt(),
                nullable(Message.class));
    }

    @Test
    @MediumTest
    public void testComputeCellInfoMinInternal() {
        // by default, screen is off, charging is off and wifi is off
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());

        // keep screen off, but turn charging on
        updateState(STATE_TYPE_CHARGING, STATE_ON);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());

        // turn screen on, turn charging off and keep wifi off
        updateState(STATE_TYPE_SCREEN, STATE_ON);
        updateState(STATE_TYPE_CHARGING, STATE_OFF);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_SHORT_MS, mDSM.computeCellInfoMinInterval());

        // screen on, but on wifi
        updateState(STATE_TYPE_WIFI_CONNECTED, STATE_ON);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_LONG_MS, mDSM.computeCellInfoMinInterval());

        // screen on, charging
        updateState(STATE_TYPE_WIFI_CONNECTED, STATE_OFF);
        updateState(STATE_TYPE_CHARGING, STATE_OFF);
        assertEquals(
                DeviceStateMonitor.CELL_INFO_INTERVAL_SHORT_MS, mDSM.computeCellInfoMinInterval());
    }

    @Test
    public void testGetBarringInfo() {
        // At beginning, all states off. Now turn screen on
        updateState(STATE_TYPE_SCREEN, STATE_ON);

        ArgumentCaptor<Integer> acBarringInfo = ArgumentCaptor.forClass(Integer.class);
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(acBarringInfo.capture(),
                nullable(Message.class));
        assertNotEquals((acBarringInfo.getValue() & IndicationFilter.BARRING_INFO), 0);
        verify(mSimulatedCommandsVerifier).getBarringInfo(nullable(Message.class));

        reset(mSimulatedCommandsVerifier);

        // Turn screen off
        updateState(STATE_TYPE_SCREEN, STATE_OFF);
        verify(mSimulatedCommandsVerifier, never()).getBarringInfo(nullable(Message.class));
        verify(mSimulatedCommandsVerifier).setUnsolResponseFilter(acBarringInfo.capture(),
                nullable(Message.class));
        assertEquals((acBarringInfo.getValue() & IndicationFilter.BARRING_INFO), 0);

        reset(mSimulatedCommandsVerifier);

        // Turn tethering on, then screen on, getBarringInfo() should only be called once
        updateState(STATE_TYPE_TETHERING, STATE_ON);
        updateState(STATE_TYPE_SCREEN, STATE_ON);
        verify(mSimulatedCommandsVerifier).getBarringInfo(nullable(Message.class));
    }
}
