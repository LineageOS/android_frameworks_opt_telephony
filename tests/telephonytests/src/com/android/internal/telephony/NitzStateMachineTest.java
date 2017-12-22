/*
 * Copyright 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class NitzStateMachineTest extends TelephonyTest {

    private static final TimeZone US_TIME_ZONE = TimeZone.getTimeZone("America/Los_Angeles");
    private static final String US_ISO_CODE = "us";

    @Mock
    private NitzStateMachine.DeviceState mDeviceState;

    private NitzStateMachine mNitzStateMachine;

    @Before
    public void setUp() throws Exception {
        logd("NitzStateMachineTest +Setup!");
        super.setUp("NitzStateMachineTest");

        // Configure device state
        when(mDeviceState.getIgnoreNitz()).thenReturn(false);
        when(mDeviceState.getNitzUpdateDiffMillis()).thenReturn(2000);
        when(mDeviceState.getNitzUpdateSpacingMillis()).thenReturn(1000 * 60 * 10);
        when(mDeviceState.elapsedRealtime()).thenReturn(123456789L);

        mNitzStateMachine = new NitzStateMachine(mPhone, mTimeServiceHelper, mDeviceState);

        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        // Confirm all mDeviceState side effects were verified. We don't care about retrievals of
        // device state.
        verify(mDeviceState, atLeast(0)).getIgnoreNitz();
        verify(mDeviceState, atLeast(0)).getNitzUpdateDiffMillis();
        verify(mDeviceState, atLeast(0)).getNitzUpdateSpacingMillis();
        verify(mDeviceState, atLeast(0)).elapsedRealtime();
        verify(mDeviceState, atLeast(0)).getNetworkCountryIsoForPhone();
        verifyNoMoreInteractions(mDeviceState);

        // Confirm all mTimeServiceHelper side effects were verified. We don't care about current
        // auto time / time zone state retrievals / listening.
        verify(mTimeServiceHelper, atLeast(0)).setListener(any());
        verify(mTimeServiceHelper, atLeast(0)).isTimeDetectionEnabled();
        verify(mTimeServiceHelper, atLeast(0)).isTimeZoneDetectionEnabled();
        verifyNoMoreInteractions(mTimeServiceHelper);

        super.tearDown();
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.setNetworkCountryIsoAvailable(true);

        // Create a simulated NITZ signal.
        TestNitzSignal usNitzSignal = createUsTestNitzSignal();

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.setTimeAndTimeZoneFromNitz(
                usNitzSignal.getNitzData(), usNitzSignal.getReceivedRealtimeMillis());

        // Check resulting state and side effects.
        long expectedAdjustedCurrentTimeMillis =
                usNitzSignal.getAdjustedCurrentTimeMillis(mDeviceState.elapsedRealtime());

        verifyTimeServiceTimeZoneWasSet(usNitzSignal.getTimeZoneId());
        verifyTimeServiceTimeWasSet(expectedAdjustedCurrentTimeMillis);

        assertTrue(mNitzStateMachine.getNitzUpdatedTime());
        assertEquals(usNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(usNitzSignal.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeZoneDisabled()
            throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.setNetworkCountryIsoAvailable(true);

        // Create a simulated NITZ signal.
        TestNitzSignal usNitzSignal = createUsTestNitzSignal();

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.setTimeAndTimeZoneFromNitz(
                usNitzSignal.getNitzData(), usNitzSignal.getReceivedRealtimeMillis());

        // Check resulting state and side effects.
        long expectedAdjustedCurrentTimeMillis =
                usNitzSignal.getAdjustedCurrentTimeMillis(mDeviceState.elapsedRealtime());

        verifyTimeServiceTimeZoneWasNotSet();
        verifyTimeServiceTimeWasSet(expectedAdjustedCurrentTimeMillis);

        assertTrue(mNitzStateMachine.getNitzUpdatedTime());
        assertEquals(usNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(usNitzSignal.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeDisabled() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.setNetworkCountryIsoAvailable(true);

        // Create a simulated NITZ signal.
        TestNitzSignal usNitzSignal = createUsTestNitzSignal();

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.setTimeAndTimeZoneFromNitz(
                usNitzSignal.getNitzData(), usNitzSignal.getReceivedRealtimeMillis());

        // Check resulting state and side effects.
        verifyTimeServiceTimeZoneWasSet(usNitzSignal.getTimeZoneId());
        verifyTimeServiceTimeWasNotSet();

        assertTrue(mNitzStateMachine.getNitzUpdatedTime());
        assertEquals(usNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(usNitzSignal.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeAndTimeZoneDisabled()
            throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.setNetworkCountryIsoAvailable(true);

        // Create a simulated NITZ signal.
        TestNitzSignal usNitzSignal = createUsTestNitzSignal();

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.setTimeAndTimeZoneFromNitz(
                usNitzSignal.getNitzData(), usNitzSignal.getReceivedRealtimeMillis());

        verifyTimeServiceTimeZoneWasNotSet();
        verifyTimeServiceTimeWasNotSet();

        assertTrue(mNitzStateMachine.getNitzUpdatedTime());
        assertEquals(usNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(usNitzSignal.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    private void verifyTimeServiceTimeZoneWasNotSet() {
        verify(mTimeServiceHelper, times(0)).setDeviceTimeZone(any(String.class));
    }

    private void verifyTimeServiceTimeZoneWasSet(String timeZoneId) {
        verify(mTimeServiceHelper, times(1)).setDeviceTimeZone(timeZoneId);
    }

    private void verifyTimeServiceTimeWasNotSet() {
        verify(mTimeServiceHelper, times(0)).setDeviceTime(anyLong());
    }

    private void verifyTimeServiceTimeWasSet(long expectedTimeMillis) {
        ArgumentCaptor<Long> timeServiceTimeCaptor = ArgumentCaptor.forClass(Long.TYPE);
        verify(mTimeServiceHelper, times(1)).setDeviceTime(timeServiceTimeCaptor.capture());
        assertEquals(expectedTimeMillis, (long) timeServiceTimeCaptor.getValue());
    }

    private void incrementSimulatedDeviceClock(int incMillis) {
        long currentElapsedRealtime = mDeviceState.elapsedRealtime();
        when(mDeviceState.elapsedRealtime()).thenReturn(currentElapsedRealtime + incMillis);
    }

    private static long createTime(TimeZone timeZone, int year, int monthOfYear, int dayOfMonth,
            int hourOfDay, int minuteOfHour, int secondOfMinute) {
        Calendar cal = new GregorianCalendar(timeZone);
        cal.clear();
        cal.set(year, monthOfYear - 1, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute);
        return cal.getTimeInMillis();
    }

    /**
     * Creates a TestNitzSignal for an arbitrary time with a US time zone.
     */
    private TestNitzSignal createUsTestNitzSignal() {
        long receivedRealtimeMillis = mDeviceState.elapsedRealtime();
        long nitzTimeMillis = createTime(US_TIME_ZONE, 2017, 1, 2, 12, 45, 25);
        return new TestNitzSignal(receivedRealtimeMillis, US_TIME_ZONE, nitzTimeMillis);
    }

    /**
     * A class to contain a simulated NITZ signal and test metadata.
     */
    private static class TestNitzSignal {

        private final long mReceivedRealtimeMillis;
        private final TimeZone mTimeZone;
        private final NitzData mNitzData;

        TestNitzSignal(long receivedRealtimeMillis, TimeZone timeZone, long nitzTimeMillis) {
            mReceivedRealtimeMillis = receivedRealtimeMillis;
            mTimeZone = timeZone;
            mNitzData = createValidNitzDataForTime(
                    timeZone, nitzTimeMillis, false /* includeEmulatorTimeZone */);
        }

        NitzData getNitzData() {
            return mNitzData;
        }

        long getReceivedRealtimeMillis() {
            return mReceivedRealtimeMillis;
        }

        long getCurrentTimeInMillis() {
            return mNitzData.getCurrentTimeInMillis();
        }

        String getTimeZoneId() {
            return mTimeZone.getID();
        }

        long getAdjustedCurrentTimeMillis(long currentElapsedRealtimeMillis) {
            long adjustmentMillis = currentElapsedRealtimeMillis - getReceivedRealtimeMillis();
            return getCurrentTimeInMillis() + adjustmentMillis;
        }

        private static NitzData createValidNitzDataForTime(TimeZone timeZone, long timeMillis,
                boolean includeEmulatorTimeZone) {
            int[] offsets = new int[2];
            timeZone.getOffset(timeMillis, false /* local */, offsets);
            int zoneOffsetMillis = offsets[0];
            int dstOffsetMillis = offsets[1];

            java.util.TimeZone emulatorTimeZone = null;
            if (includeEmulatorTimeZone) {
                emulatorTimeZone = java.util.TimeZone.getTimeZone(timeZone.getID());
            }
            return NitzData.createForTests(zoneOffsetMillis, dstOffsetMillis, timeMillis,
                    emulatorTimeZone);
        }
    }
}
