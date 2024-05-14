/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Looper;
import android.telephony.satellite.stub.ISatelliteListener;
import android.telephony.satellite.stub.NtnSignalStrength;
import android.telephony.satellite.stub.SatelliteModemState;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for DemoSimulator
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DemoSimulatorTest extends TelephonyTest {
    private static final String TAG = "DemoSimulatorTest";
    private static final long TEST_DEVICE_POINTING_ALIGNED_DURATION_MILLIS = 200L;
    private static final long TEST_DEVICE_POINTING_NOT_ALIGNED_DURATION_MILLIS = 300L;
    private static final String STATE_POWER_OFF = "PowerOffState";
    private static final String STATE_NOT_CONNECTED = "NotConnectedState";
    private static final String STATE_CONNECTED = "ConnectedState";

    private TestDemoSimulator mTestDemoSimulator;
    @Mock private ISatelliteListener mISatelliteListener;

    @Mock private SatelliteController mMockSatelliteController;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);

        when(mMockSatelliteController.isDemoModeEnabled()).thenReturn(true);
        when(mMockSatelliteController.getDemoPointingAlignedDurationMillis()).thenReturn(
                TEST_DEVICE_POINTING_ALIGNED_DURATION_MILLIS);
        when(mMockSatelliteController.getDemoPointingNotAlignedDurationMillis()).thenReturn(
                TEST_DEVICE_POINTING_NOT_ALIGNED_DURATION_MILLIS);

        mTestDemoSimulator = new TestDemoSimulator(mContext, Looper.myLooper(),
                mMockSatelliteController);
        mTestDemoSimulator.setSatelliteListener(mISatelliteListener);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testInitialState() {
        assertNotNull(mTestDemoSimulator);
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());
    }

    @Test
    public void testStateTransition() {
        // State transitions: POWER_OFF -> NOT_CONNECTED -> CONNECTED
        moveToConnectedState();

        // Device is not aligned with satellite. EVENT_DEVICE_NOT_ALIGNED timer should start
        mTestDemoSimulator.setDeviceAlignedWithSatellite(false);
        processAllMessages();
        assertTrue(mTestDemoSimulator.isDeviceNotAlignedTimerStarted());

        // After timeout, DemoSimulator should move to NOT_CONNECTED state.
        moveTimeForward(TEST_DEVICE_POINTING_NOT_ALIGNED_DURATION_MILLIS);
        processAllMessages();
        assertEquals(STATE_NOT_CONNECTED, mTestDemoSimulator.getCurrentStateName());

        // Satellite mode is OFF. DemoSimulator should move to POWER_OFF state.
        mTestDemoSimulator.onSatelliteModeOff();
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());
    }

    @Test
    public void testNotConnectedState_enter() throws Exception {
        clearInvocations(mISatelliteListener);

        // State transitions: POWER_OFF -> NOT_CONNECTED
        moveToNotConnectedState();

        verify(mISatelliteListener).onSatelliteModemStateChanged(
                SatelliteModemState.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        ArgumentCaptor<NtnSignalStrength> ntnSignalStrength = ArgumentCaptor.forClass(
                NtnSignalStrength.class);
        verify(mISatelliteListener).onNtnSignalStrengthChanged(ntnSignalStrength.capture());
        assertEquals(0, ntnSignalStrength.getValue().signalStrengthLevel);
    }

    @Test
    public void testNotConnectedState() {
        // State transitions: POWER_OFF -> NOT_CONNECTED
        moveToNotConnectedState();

        // Device is aligned with satellite. EVENT_DEVICE_ALIGNED timer should start.
        mTestDemoSimulator.setDeviceAlignedWithSatellite(true);
        processAllMessages();
        assertTrue(mTestDemoSimulator.isDeviceAlignedTimerStarted());

        // Device is not aligned with satellite. EVENT_DEVICE_ALIGNED messages should be removed.
        mTestDemoSimulator.setDeviceAlignedWithSatellite(false);
        processAllMessages();
        assertFalse(mTestDemoSimulator.isDeviceAlignedTimerStarted());
        assertEquals(STATE_NOT_CONNECTED, mTestDemoSimulator.getCurrentStateName());

        // Satellite mode is OFF. DemoSimulator should move to POWER_OFF state.
        mTestDemoSimulator.onSatelliteModeOff();
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());
    }

    @Test
    public void testConnectedState_enter() throws Exception {
        clearInvocations(mISatelliteListener);

        // State transitions: POWER_OFF -> NOT_CONNECTED -> CONNECTED
        moveToConnectedState();

        verify(mISatelliteListener).onSatelliteModemStateChanged(
                SatelliteModemState.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        verify(mISatelliteListener).onSatelliteModemStateChanged(
                SatelliteModemState.SATELLITE_MODEM_STATE_CONNECTED);
        ArgumentCaptor<NtnSignalStrength> ntnSignalStrength = ArgumentCaptor.forClass(
                NtnSignalStrength.class);
        verify(mISatelliteListener, times(2))
                .onNtnSignalStrengthChanged(ntnSignalStrength.capture());
        NtnSignalStrength ntnSignalStrengthOnConnected = ntnSignalStrength.getAllValues().get(1);
        assertEquals(2, ntnSignalStrengthOnConnected.signalStrengthLevel);
    }

    @Test
    public void testConnectedState() {
        // State transitions: POWER_OFF -> NOT_CONNECTED -> CONNECTED
        moveToConnectedState();

        // Device is not aligned with satellite. EVENT_DEVICE_NOT_ALIGNED timer should start
        mTestDemoSimulator.setDeviceAlignedWithSatellite(false);
        processAllMessages();
        assertTrue(mTestDemoSimulator.isDeviceNotAlignedTimerStarted());

        // Device is aligned with satellite before timeout.
        // EVENT_DEVICE_NOT_ALIGNED messages should be removed.
        mTestDemoSimulator.setDeviceAlignedWithSatellite(true);
        processAllMessages();
        assertFalse(mTestDemoSimulator.isDeviceNotAlignedTimerStarted());
        assertEquals(STATE_CONNECTED, mTestDemoSimulator.getCurrentStateName());

        // Satellite mode is off. DemoSimulator should move to POWER_OFF state
        mTestDemoSimulator.onSatelliteModeOff();
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());
    }

    private void moveToNotConnectedState() {
        // DemoSimulator will initially be in POWER_OFF state.
        assertNotNull(mTestDemoSimulator);
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());

        // Satellite mode is ON. DemoSimulator should move to NOT_CONNECTED state.
        mTestDemoSimulator.onSatelliteModeOn();
        processAllMessages();
        assertEquals(STATE_NOT_CONNECTED, mTestDemoSimulator.getCurrentStateName());
    }

    private void moveToConnectedState() {
        // DemoSimulator will initially be in POWER_OFF state.
        assertNotNull(mTestDemoSimulator);
        processAllMessages();
        assertEquals(STATE_POWER_OFF, mTestDemoSimulator.getCurrentStateName());

        // Satellite mode is ON. DemoSimulator should move to NOT_CONNECTED state.
        mTestDemoSimulator.onSatelliteModeOn();
        processAllMessages();
        assertEquals(STATE_NOT_CONNECTED, mTestDemoSimulator.getCurrentStateName());

        // Device is aligned with satellite. EVENT_DEVICE_ALIGNED timer should start.
        mTestDemoSimulator.setDeviceAlignedWithSatellite(true);
        processAllMessages();
        assertTrue(mTestDemoSimulator.isDeviceAlignedTimerStarted());

        // After timeout, DemoSimulator should move to CONNECTED state.
        moveTimeForward(TEST_DEVICE_POINTING_ALIGNED_DURATION_MILLIS);
        processAllMessages();
        assertEquals(STATE_CONNECTED, mTestDemoSimulator.getCurrentStateName());
    }

    private static class TestDemoSimulator extends DemoSimulator {

        TestDemoSimulator(@NonNull Context context, @NonNull Looper looper,
                @NonNull SatelliteController satelliteController) {
            super(context, looper, satelliteController);
        }

        String getCurrentStateName() {
            return getCurrentState().getName();
        }

        boolean isDeviceAlignedTimerStarted() {
            return hasMessages(EVENT_DEVICE_ALIGNED);
        }

        boolean isDeviceNotAlignedTimerStarted() {
            return hasMessages(EVENT_DEVICE_NOT_ALIGNED);
        }
    }
}
