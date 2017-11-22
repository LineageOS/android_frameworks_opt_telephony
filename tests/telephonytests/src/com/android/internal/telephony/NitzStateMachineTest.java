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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Mock
    private NitzStateMachine.DeviceState mDeviceState;

    private NitzStateMachine mNitzStateMachine;

    @Before
    public void setUp() throws Exception {
        logd("NitzStateMachineTest +Setup!");
        super.setUp("NitzStateMachineTest");

        // Configure device state
        when(mDeviceState.getIgnoreNitz()).thenReturn(false);
        when(mDeviceState.getIgnoreNitzForTests()).thenReturn(false);
        when(mDeviceState.getNitzUpdateDiffMillis()).thenReturn(2000);
        when(mDeviceState.getNitzUpdateSpacingMillis()).thenReturn(1000 * 60 * 10);
        when(mDeviceState.elapsedRealtime()).thenReturn(123456789L);

        NitzStateMachine real = new NitzStateMachine(mPhone, mTimeServiceHelper, mDeviceState);
        mNitzStateMachine = spy(real);

        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mNitzStateMachine = null;
        super.tearDown();
    }

    @Test
    public void test_setTimeAndTimeZoneFromNitz_countryKnown() throws Exception {
        // Simulate device settings.
        when(mTimeServiceHelper.isTimeDetectionEnabled()).thenReturn(true);
        when(mTimeServiceHelper.isTimeZoneDetectionEnabled()).thenReturn(true);

        // Simulate the country being known.
        when(mNitzStateMachine.getNetworkCountryIsoForPhone()).thenReturn("us");
        mNitzStateMachine.setNetworkCountryIsoAvailable(true);

        // Simulate NITZ being received.
        long nitzReceivedRealtimeMillis = mDeviceState.elapsedRealtime();
        TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");
        long nitzTimeMillis = createTime(timeZone, 2017, 1, 2, 12, 45, 25);
        NitzData nitzData = createValidNitzDataForTime(timeZone, nitzTimeMillis,
                false /* includeEmulatorTimeZone */);

        // Simulate the elapsedRealtime() value incrementing with the passage of time.
        incrementSimulatedDeviceClock(1000);

        mNitzStateMachine.setTimeAndTimeZoneFromNitz(nitzData, nitzReceivedRealtimeMillis);

        // Check resulting state and side effects.
        assertEquals(nitzData, mNitzStateMachine.getCachedNitzData());
        verify(mTimeServiceHelper, times(1)).setDeviceTimeZone(timeZone.getID());

        long timeServiceTimeMillis = verifyTimeServiceTimeWasSet();
        assertEquals(nitzTimeMillis + (mDeviceState.elapsedRealtime() - nitzReceivedRealtimeMillis),
                timeServiceTimeMillis);
        assertEquals(timeServiceTimeMillis, verifyNitzTimePropertyWasSet());

        assertTrue(mNitzStateMachine.getNitzUpdatedTime());
        assertEquals(timeZone.getID(), mNitzStateMachine.getSavedTimeZoneId());
    }

    private long verifyTimeServiceTimeWasSet() {
        ArgumentCaptor<Long> timeServiceTimeCaptor = ArgumentCaptor.forClass(Long.TYPE);
        verify(mTimeServiceHelper, times(1)).setDeviceTime(timeServiceTimeCaptor.capture());
        return timeServiceTimeCaptor.getValue();
    }

    private long verifyNitzTimePropertyWasSet() {
        ArgumentCaptor<Long> propertyCaptor = ArgumentCaptor.forClass(Long.TYPE);
        verify(mDeviceState, times(1)).setNitzTimeProperty(propertyCaptor.capture());
        return propertyCaptor.getValue();
    }

    private void incrementSimulatedDeviceClock(int incMillis) {
        long currentElapsedRealtime = mDeviceState.elapsedRealtime();
        when(mDeviceState.elapsedRealtime()).thenReturn(currentElapsedRealtime + incMillis);
    }

    private NitzData createValidNitzDataForTime(TimeZone timeZone, long timeMillis,
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

    private long createTime(TimeZone timeZone, int year, int monthOfYear, int dayOfMonth,
            int hourOfDay, int minuteOfHour,
            int secondOfMinute) {
        Calendar cal = new GregorianCalendar(timeZone);
        cal.clear();
        cal.set(year, monthOfYear - 1, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute);
        return cal.getTimeInMillis();
    }
}
