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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.internal.telephony.util.TimeStampedValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class NitzStateMachineTest extends TelephonyTest {

    private static final String US_TIME_ZONE_ID = "America/Los_Angeles";
    private static final String US_ISO_CODE = "us";

    @Mock
    private NitzStateMachine.DeviceState mDeviceState;

    @Mock
    private TimeZoneLookupHelper mTimeZoneLookupHelper;

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
        when(mDeviceState.currentTimeMillis()).thenReturn(987654321L);

        mNitzStateMachine = new NitzStateMachine(
                mPhone, mTimeServiceHelper, mDeviceState, mTimeZoneLookupHelper);

        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        // Confirm all mDeviceState side effects were verified. We don't care about retrievals of
        // device state so we can use "at least 0" times to indicate they don't matter.
        verify(mDeviceState, atLeast(0)).getIgnoreNitz();
        verify(mDeviceState, atLeast(0)).getNitzUpdateDiffMillis();
        verify(mDeviceState, atLeast(0)).getNitzUpdateSpacingMillis();
        verify(mDeviceState, atLeast(0)).elapsedRealtime();
        verify(mDeviceState, atLeast(0)).currentTimeMillis();
        verify(mDeviceState, atLeast(0)).getNetworkCountryIsoForPhone();
        verifyNoMoreInteractions(mDeviceState);

        // Confirm all mTimeServiceHelper side effects were verified. We don't care about current
        // auto time / time zone state retrievals / listening so we can use "at least 0" times to
        // indicate they don't matter.
        verify(mTimeServiceHelper, atLeast(0)).setListener(any());
        verify(mTimeServiceHelper, atLeast(0)).isTimeDetectionEnabled();
        verify(mTimeServiceHelper, atLeast(0)).isTimeZoneDetectionEnabled();
        verify(mTimeServiceHelper, atLeast(0)).isTimeZoneSettingInitialized();
        verifyNoMoreInteractions(mTimeServiceHelper);

        super.tearDown();
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneSettingInitialized()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.handleNetworkCountryCodeSet(true);

        // Create a simulated NITZ signal.
        TestNitzSignal testNitzSignal = createTestNitzSignal();

        // Configure expected time zone lookup and the result.
        String testTimeZoneId = US_TIME_ZONE_ID;
        when(mTimeZoneLookupHelper.guessZoneIdByNitzCountry(
                testNitzSignal.getNitzData(), US_ISO_CODE)).thenReturn(testTimeZoneId);

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.handleNitzReceived(testNitzSignal.asTimeStampedValue());

        // Check resulting state and side effects.
        long expectedAdjustedCurrentTimeMillis =
                testNitzSignal.getAdjustedCurrentTimeMillis(mDeviceState.elapsedRealtime());

        verifyTimeServiceTimeZoneWasSet(testTimeZoneId);
        verifyTimeServiceTimeWasSet(expectedAdjustedCurrentTimeMillis);


        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(testTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeZoneDisabled()
            throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneSettingInitialized()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.handleNetworkCountryCodeSet(true);

        // Create a simulated NITZ signal.
        TestNitzSignal testNitzSignal = createTestNitzSignal();

        // Configure expected time zone lookup and the result.
        String testTimeZoneId = US_TIME_ZONE_ID;
        when(mTimeZoneLookupHelper.guessZoneIdByNitzCountry(
                testNitzSignal.getNitzData(), US_ISO_CODE)).thenReturn(testTimeZoneId);

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.handleNitzReceived(testNitzSignal.asTimeStampedValue());

        // Check resulting state and side effects.
        long expectedAdjustedCurrentTimeMillis =
                testNitzSignal.getAdjustedCurrentTimeMillis(mDeviceState.elapsedRealtime());

        verifyTimeServiceTimeZoneWasNotSet();
        verifyTimeServiceTimeWasSet(expectedAdjustedCurrentTimeMillis);

        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(testTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeDisabled() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneSettingInitialized()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.handleNetworkCountryCodeSet(true);

        // Create a simulated NITZ signal.
        TestNitzSignal testNitzSignal = createTestNitzSignal();

        // Configure expected time zone lookup and the result.
        String testTimeZoneId = US_TIME_ZONE_ID;
        when(mTimeZoneLookupHelper.guessZoneIdByNitzCountry(
                testNitzSignal.getNitzData(), US_ISO_CODE)).thenReturn(testTimeZoneId);

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.handleNitzReceived(testNitzSignal.asTimeStampedValue());

        // Check resulting state and side effects.
        verifyTimeServiceTimeZoneWasSet(testTimeZoneId);
        verifyTimeServiceTimeWasNotSet();

        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(testTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown_autoTimeAndTimeZoneDisabled()
            throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneSettingInitialized()).thenReturn(false);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.handleNetworkCountryCodeSet(true);

        // Create a simulated NITZ signal.
        TestNitzSignal testNitzSignal = createTestNitzSignal();

        // Configure expected time zone lookup and the result.
        String testTimeZoneId = US_TIME_ZONE_ID;
        when(mTimeZoneLookupHelper.guessZoneIdByNitzCountry(
                testNitzSignal.getNitzData(), US_ISO_CODE)).thenReturn(testTimeZoneId);

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.handleNitzReceived(testNitzSignal.asTimeStampedValue());

        // Check resulting state and side effects.
        verifyTimeServiceTimeZoneWasNotSet();
        verifyTimeServiceTimeWasNotSet();

        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(testTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_setTimeZoneFromNitz_countryInitiallyNotKnown() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(false);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneSettingInitialized()).thenReturn(false);

        // Simulate the country not being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn("");

        // Create a simulated NITZ signal.
        TestNitzSignal testNitzSignal = createTestNitzSignal();

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        // Simulate NITZ being received.
        mNitzStateMachine.handleNitzReceived(testNitzSignal.asTimeStampedValue());

        // Check resulting state and side effects.
        verifyTimeServiceTimeZoneWasNotSet();

        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        //
        // Simulate the country code becoming known.
        //

        // Configure expected time zone lookup and the result.
        String testTimeZoneId = US_TIME_ZONE_ID;
        when(mTimeZoneLookupHelper.guessZoneIdByNitzCountry(
                testNitzSignal.getNitzData(), US_ISO_CODE)).thenReturn(testTimeZoneId);

        // Simulate the country being known.
        when(mDeviceState.getNetworkCountryIsoForPhone()).thenReturn(US_ISO_CODE);
        mNitzStateMachine.handleNetworkCountryCodeSet(true);

        // Check resulting state and side effects.
        verifyTimeServiceTimeZoneWasSet(testTimeZoneId);

        // TODO(nfuller): The following line should probably be assertTrue but the logic under test
        // is probably buggy. Fix.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(testNitzSignal.getNitzData(), mNitzStateMachine.getCachedNitzData());
        assertEquals(testTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
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
        long currentTimeMillis = mDeviceState.currentTimeMillis();
        when(mDeviceState.elapsedRealtime()).thenReturn(currentTimeMillis + incMillis);
    }

    private static long createTime(TimeZone timeZone, int year, int monthOfYear, int dayOfMonth,
            int hourOfDay, int minuteOfHour, int secondOfMinute) {
        Calendar cal = new GregorianCalendar(timeZone);
        cal.clear();
        cal.set(year, monthOfYear - 1, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute);
        return cal.getTimeInMillis();
    }

    /**
     * Creates a TestNitzSignal containing an arbitrary current time and offset and with a
     * receivedRealtimeMillis from the current {@code mDeviceState.elapsedRealtime()}.
     */
    private TestNitzSignal createTestNitzSignal() {
        long receivedRealtimeMillis = mDeviceState.elapsedRealtime();
        // Create an arbitrary time.
        long timeMillis = createTime(TimeZone.getTimeZone("UTC"), 2017, 1, 2, 12, 45, 25);
        // Create arbitrary NITZ data.
        NitzData nitzData = NitzData.createForTests(
                1000 /* zoneOffsetMillis */,
                2000 /* dstOffsetMillis */,
                timeMillis,
                null /* emulatorHostTimeZone */);

        return new TestNitzSignal(nitzData, receivedRealtimeMillis);
    }

    /**
     * A class to contain a simulated NITZ signal and test metadata.
     */
    private static class TestNitzSignal {

        private final TimeStampedValue<NitzData> mNitzData;

        TestNitzSignal(NitzData nitzData, long receivedRealtimeMillis) {
            mNitzData = new TimeStampedValue<>(nitzData, receivedRealtimeMillis);
        }

        NitzData getNitzData() {
            return mNitzData.mValue;
        }

        long getReceivedRealtimeMillis() {
            return mNitzData.mElapsedRealtime;
        }

        long getCurrentTimeInMillis() {
            return getNitzData().getCurrentTimeInMillis();
        }

        long getAdjustedCurrentTimeMillis(long currentElapsedRealtimeMillis) {
            long adjustmentMillis = currentElapsedRealtimeMillis - getReceivedRealtimeMillis();
            return getCurrentTimeInMillis() + adjustmentMillis;
        }

        TimeStampedValue<NitzData> asTimeStampedValue() {
            return mNitzData;
        }
    }
}
