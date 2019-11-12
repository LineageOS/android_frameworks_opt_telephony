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

import static com.android.internal.telephony.NitzStateMachineTestSupport.ARBITRARY_SYSTEM_CLOCK_TIME;
import static com.android.internal.telephony.NitzStateMachineTestSupport.ARBITRARY_TIME_ZONE_ID;
import static com.android.internal.telephony.NitzStateMachineTestSupport.CZECHIA_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.NEW_ZEALAND_DEFAULT_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.NEW_ZEALAND_OTHER_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO2;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNITED_KINGDOM_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.US_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.NitzStateMachineTestSupport.createTimeSuggestionFromNitzSignal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.timedetector.PhoneTimeSuggestion;
import android.icu.util.TimeZone;
import android.util.TimestampedValue;

import com.android.internal.telephony.NitzStateMachineTestSupport.FakeDeviceState;
import com.android.internal.telephony.NitzStateMachineTestSupport.Scenario;
import com.android.internal.telephony.TimeZoneLookupHelper.OffsetResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class NitzStateMachineImplTest extends TelephonyTest {

    private FakeTimeServiceHelper mFakeTimeServiceHelper;
    private FakeDeviceState mFakeDeviceState;
    private TimeZoneLookupHelper mRealTimeZoneLookupHelper;

    private NitzStateMachineImpl mNitzStateMachine;

    @Before
    public void setUp() throws Exception {
        logd("NitzStateMachineImplTest +Setup!");
        super.setUp("NitzStateMachineImplTest");

        // In tests we use a fake impls for TimeServiceHelper and DeviceState.
        mFakeDeviceState = new FakeDeviceState();
        mFakeTimeServiceHelper = new FakeTimeServiceHelper(mFakeDeviceState);

        // In tests we use the real TimeZoneLookupHelper.
        mRealTimeZoneLookupHelper = new TimeZoneLookupHelper();
        mNitzStateMachine = new NitzStateMachineImpl(
                mPhone, mFakeTimeServiceHelper, mFakeDeviceState, mRealTimeZoneLookupHelper);

        logd("NitzStateMachineImplTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test_uniqueUsZone_timeZoneEnabled_countryThenNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country won't be enough for time zone detection.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal);

        // Country + NITZ is enough for both time + time zone detection.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedTimeSuggestion, scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_timeZoneUninitialized_countryOnly() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null /* uninitialized */);

        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone *should* be enough on an uninitialized device.
                .verifyOnlyTimeZoneWasSetAndReset(US_COUNTRY_DEFAULT_ZONE_ID);
    }

    @Test
    public void test_timeZoneInitialized_countryOnly() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone *should not* be enough on an initialized device.
                .verifyNothingWasSetAndReset();
    }

    @Test
    public void test_timeZoneUninitialized_nitzThenCountry() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;

        // Create a bad NITZ signal to send to the time zone detection code: Android always picks a
        // zone when there is one or more matching zones(regardless of whether the setting
        // setting is initialized), so we need to create a situation where no zones match to show it
        // still sets a time zone.
        TimestampedValue<NitzData> nitzSignal =
                CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Confirm there would be no match for this.
        OffsetResult result =
                mRealTimeZoneLookupHelper.lookupByNitzCountry(
                        nitzSignal.getValue(), scenario.getNetworkCountryIsoCode());
        assertNull(result);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null /* uninitialized */)
                .networkAvailable();

        script.nitzReceived(nitzSignal);
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The code will use the country default zone because the setting is uninitialized.
                .verifyOnlyTimeZoneWasSetAndReset(US_COUNTRY_DEFAULT_ZONE_ID);
    }

    @Test
    public void test_timeZoneInitialized_nitzThenCountry() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;

        // Create a bad NITZ signal to send to the time zone detection code: Android always picks a
        // zone when there is one or more matching zones(regardless of whether the setting
        // is initialized), so we need to create a situation where no zones match to show it will
        // not set the time zone.
        TimestampedValue<NitzData> nitzSignal =
                CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Confirm there would be no match for this.
        OffsetResult result =
                mRealTimeZoneLookupHelper.lookupByNitzCountry(
                        nitzSignal.getValue(), scenario.getNetworkCountryIsoCode());
        assertNull(result);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        script.nitzReceived(nitzSignal);
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The code will not set the zone because the setting is initialized.
                .verifyNothingWasSetAndReset();
    }

    @Test
    public void test_unitedKingdom_timeZoneEnabled_countryThenNitz() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone is enough to guess the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId())
                .nitzReceived(nitzSignal);

        // Country + NITZ is enough for both time + time zone detection.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedTimeSuggestion, scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_countryDefaultBoost_timeZoneEnabled_countryThenNitz() throws Exception {
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        // Demonstrate the defaultTimeZoneBoost behavior: we can get a zone only from the
        // countryIsoCode.
        {
            Scenario scenario = NEW_ZEALAND_DEFAULT_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            script.countryReceived(scenario.getNetworkCountryIsoCode())
                    // Even though there are multiple zones in the country, countryIsoCode is enough
                    // to guess the time zone because the default zone is boosted.
                    .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

            // Confirm what happens when NITZ doesn't conflict with the country-only result.
            script.nitzReceived(nitzSignal);

            // Country + NITZ is enough for both time + time zone detection.
            PhoneTimeSuggestion expectedTimeSuggestion =
                    createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
            script.verifyTimeSuggestedAndZoneSetAndReset(
                    expectedTimeSuggestion, scenario.getTimeZoneId());

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }

        // A valid NITZ signal for the non-default zone should still be correctly detected.
        {
            Scenario scenario = NEW_ZEALAND_OTHER_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            script.nitzReceived(nitzSignal)
                    // Time won't be set because the UTC signal will be the same.
                    .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }

        // Demonstrate what happens with a bogus NITZ for NZ: because the default zone is boosted
        // then we should return to the country default zone.
        {
            // A scenario that has a different offset than NZ.
            Scenario scenario = CZECHIA_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            String expectedTimeZoneId = NEW_ZEALAND_DEFAULT_SCENARIO.getTimeZoneId();
            script.nitzReceived(nitzSignal)
                    // Time won't be set because the UTC signal will be the same.
                    .verifyOnlyTimeZoneWasSetAndReset(expectedTimeZoneId);

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }
    }

    @Test
    public void test_noCountryDefaultBoost_timeZoneEnabled_countryThenNitz() throws Exception {
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        // Demonstrate the behavior without default country boost for a country with multiple zones:
        // we cannot get a zone only from the countryIsoCode.
        {
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            script.countryReceived(scenario.getNetworkCountryIsoCode())
                    // The country should no be enough to guess time zone.
                    .verifyNothingWasSetAndReset();

            // The NITZ signal + country iso code will be enough.
            script.nitzReceived(nitzSignal);

            // Country + NITZ is enough for both time + time zone detection.
            PhoneTimeSuggestion expectedTimeSuggestion =
                    createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
            script.verifyTimeSuggestedAndZoneSetAndReset(
                    expectedTimeSuggestion, scenario.getTimeZoneId());

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }

        // A valid NITZ signal for a different zone should also be correctly detected.
        {
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO2;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            script.nitzReceived(nitzSignal)
                    // Time won't be set because the UTC signal will be the same.
                    .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }

        // Demonstrate what happens with a bogus NITZ for US: because the default zone is not
        // boosted we should not change anything.
        {
            // A scenario that has a different offset than US.
            Scenario scenario = CZECHIA_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            script.nitzReceived(nitzSignal)
                    // Time won't be set because the UTC signal will be the same.
                    .verifyNothingWasSetAndReset();

            // Check NitzStateMachine state.
            assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
            assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        }
    }

    @Test
    public void test_uniqueUsZone_timeZoneDisabled_countryThenNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(false)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country is not enough to guess the time zone and time zone detection is disabled.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal);
        // Time zone detection is disabled, but time should be suggested from NITZ.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_unitedKingdom_timeZoneDisabled_countryThenNitz() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(false)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone would be enough for time zone detection, but it's disabled.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal);
        // Time zone detection is disabled, but time should be suggested from NITZ.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_uniqueUsZone_timeZoneEnabled_nitzThenCountry() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        // Simulate receiving an NITZ signal.
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(nitzSignal);

        // The NITZ alone isn't enough to detect a time zone.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The NITZ + country is enough to detect the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_unitedKingdom_timeZoneEnabled_nitzThenCountry() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        // Simulate receiving an NITZ signal.
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(nitzSignal);
        // The NITZ alone isn't enough to detect a time zone.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode());

        // The NITZ + country is enough to detect the time zone.
        script.verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_validCzNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(goodNitzSignal);

        // The NITZ alone isn't enough to detect a time zone.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), goodNitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_validCzNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The NITZ country is enough to detect the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(goodNitzSignal);

        // The time will be suggested from the NITZ signal.
        // The combination of NITZ + country will cause the time zone to be set.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), goodNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedTimeSuggestion, scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_bogusCzNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal);

        // The NITZ alone isn't enough to detect a time zone, but there isn't enough
        // information to work out it is bogus.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), badNitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_bogusCzNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The country is enough to detect the time zone for CZ.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal);

        // The NITZ should be detected as bogus so only the time will be suggested.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), badNitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_bogusUniqueUsNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal);
        // The NITZ alone isn't enough to detect a time zone, but there isn't enough
        // information to work out its bogus.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), badNitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The country isn't enough to detect the time zone for US so we will leave the time
                // zone unset.
                .verifyNothingWasSetAndReset();

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_bogusUsUniqueNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> goodNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The country isn't enough to detect the time zone for US so we will leave the time
                // zone unset.
                .verifyNothingWasSetAndReset();

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal);

        // The NITZ should be detected as bogus so only the time will be suggested.
        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), badNitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_emulatorNitzExtensionUsedForTimeZone() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        TimestampedValue<NitzData> originalNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create an NITZ signal with an explicit time zone (as can happen on emulators)
        NitzData originalNitzData = originalNitzSignal.getValue();
        // A time zone that is obviously not in the US, but it should not be questioned.
        String emulatorTimeZoneId = "Europe/London";
        NitzData emulatorNitzData = NitzData.createForTests(
                originalNitzData.getLocalOffsetMillis(),
                originalNitzData.getDstAdjustmentMillis(),
                originalNitzData.getCurrentTimeInMillis(),
                java.util.TimeZone.getTimeZone(emulatorTimeZoneId) /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> emulatorNitzSignal = new TimestampedValue<>(
                originalNitzSignal.getReferenceTimeMillis(), emulatorNitzData);

        // Simulate receiving the emulator NITZ signal.
        script.nitzReceived(emulatorNitzSignal);

        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), emulatorNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(expectedTimeSuggestion, emulatorTimeZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(emulatorNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_emptyCountryStringUsTime_countryReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        String expectedZoneId = checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(scenario);

        // Nothing should be set. The country is not valid.
        script.countryReceived("").verifyNothingWasSetAndReset();

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate receiving the NITZ signal.
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(nitzSignal);

        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(expectedTimeSuggestion, expectedZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_emptyCountryStringUsTime_nitzReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        String expectedZoneId = checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(scenario);

        // Simulate receiving the NITZ signal.
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(nitzSignal);

        PhoneTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), nitzSignal);
        script.verifyOnlyTimeWasSuggestedAndReset(expectedTimeSuggestion);

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // The time zone should be set (but the country is not valid so it's unlikely to be
        // correct).
        script.countryReceived("").verifyOnlyTimeZoneWasSetAndReset(expectedZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    @Test
    public void test_airplaneModeClearsState() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO.mutableCopy();
        int timeStepMillis = (int) TimeUnit.HOURS.toMillis(3);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID)
                .networkAvailable();

        // Pre-flight: Simulate a device receiving signals that allow it to detect time and time
        // zone.
        TimestampedValue<NitzData> preflightNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(preflightNitzSignal)
                .countryReceived(scenario.getNetworkCountryIsoCode());

        PhoneTimeSuggestion expectedPreFlightTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), preflightNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedPreFlightTimeSuggestion, scenario.getTimeZoneId());

        // Demonstrate the NitzStateMachineImpl is "opinionated" about time zone: toggling auto-time
        // zone on should cause it to set the last known time zone again.
        // Note: Historically Android telephony time detection hasn't retained an opinion about time
        // so only the time zone is set. Also, NitzStateMachine doesn't pay attention to whether
        // auto-time is enabled; it is left to the system server service to decide whether to act on
        // the time suggestion if the settings allow.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(preflightNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Boarded flight: Airplane mode turned on / time zone detection still enabled.
        // The NitzStateMachineImpl must lose all state and stop having an opinion about time zone.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        script.toggleAirplaneMode(true);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Verify there's no time zone opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // During flight: Airplane mode turned off / time zone detection still enabled.
        // The NitzStateMachineImpl still must not have an opinion about time zone / hold any state.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        script.toggleAirplaneMode(false);

        // Verify there's still no opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Post flight: Device has moved and receives new signals.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the movement to the destination.
        scenario.changeCountry(UNIQUE_US_ZONE_SCENARIO1.getTimeZoneId(),
                UNIQUE_US_ZONE_SCENARIO1.getNetworkCountryIsoCode());

        // Simulate the device receiving NITZ signals again after the flight. Now the
        // NitzStateMachineImpl is opinionated again.
        TimestampedValue<NitzData> postFlightNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .nitzReceived(postFlightNitzSignal);

        PhoneTimeSuggestion expectedPostFlightTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), postFlightNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(expectedPostFlightTimeSuggestion,
                        scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(postFlightNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    /**
     * Confirm losing the network / NITZ doesn't clear country state.
     */
    @Test
    public void test_handleNetworkUnavailableUs() throws Exception {
        // Use the US in this test: it requires country + NITZ to detect a time zone.
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1.mutableCopy();
        int timeStepMillis = (int) TimeUnit.HOURS.toMillis(3);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        // Simulate a device receiving signals that allow it to detect time and time zone.
        TimestampedValue<NitzData> initialNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.networkAvailable()
                .nitzReceived(initialNitzSignal)
                .countryReceived(scenario.getNetworkCountryIsoCode());

        PhoneTimeSuggestion expectedInitialTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), initialNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedInitialTimeSuggestion, scenario.getTimeZoneId());

        // Demonstrate the NitzStateMachineImpl is "opinionated" about time zone: toggling auto-time
        // zone on should cause it to set the last known time zone again.
        // Note: Historically Android telephony time detection hasn't retained an opinion about time
        // so only the time zone is set. Also, NitzStateMachine doesn't pay attention to whether
        // auto-time is enabled; it is left to the system server service to decide whether to act on
        // the time suggestion if the settings allow.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(initialNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate losing the network. The NitzStateMachineImpl must lose all NITZ state and stop
        // having an opinion about time zone.
        script.networkUnavailable()
                .verifyNothingWasSetAndReset();

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Verify there's no time zone opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the network becoming available again (but no NITZ yet).
        script.networkAvailable();

        // Verify there's still no opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the device receiving NITZ signals again now the network is available. Now the
        // NitzStateMachineImpl is opinionated again.
        TimestampedValue<NitzData> finalNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(finalNitzSignal);

        PhoneTimeSuggestion expectedFinalTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), finalNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(expectedFinalTimeSuggestion,
                        scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(finalNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    /**
     * Confirm losing the network / NITZ doesn't clear country state.
     */
    @Test
    public void test_handleNetworkUnavailableUk() throws Exception {
        // Use the UK in this test: it only requires country to detect a time zone.
        Scenario scenario = UNITED_KINGDOM_SCENARIO.mutableCopy();
        int timeStepMillis = (int) TimeUnit.HOURS.toMillis(3);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        // Simulate a device receiving signals that allow it to detect time and time zone.
        TimestampedValue<NitzData> initialNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.networkAvailable()
                .nitzReceived(initialNitzSignal)
                .countryReceived(scenario.getNetworkCountryIsoCode());

        PhoneTimeSuggestion expectedInitialTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), initialNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(
                expectedInitialTimeSuggestion, scenario.getTimeZoneId());

        // Demonstrate the NitzStateMachineImpl is "opinionated" about time zone: toggling auto-time
        // zone on should cause it to set the last known time zone again.
        // Note: Historically Android telephony time detection hasn't retained an opinion about time
        // so only the time zone is set. Also, NitzStateMachine doesn't pay attention to whether
        // auto-time is enabled; it is left to the system server service to decide whether to act on
        // the time suggestion if the settings allow.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(initialNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());

        // Simulate losing the network. The NitzStateMachineImpl must lose all NITZ state but should
        // retain country knowledge and so remain opinionated about time zone ID because the country
        // is sufficient to detect time zone in the UK.
        script.networkUnavailable()
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Verify there's a time zone opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the network becoming available again (but no NITZ yet).
        script.networkAvailable();

        // Verify there's still no opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the device receiving NITZ signals again now the network is available. Now the
        // NitzStateMachineImpl is opinionated again.
        TimestampedValue<NitzData> finalNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(finalNitzSignal);

        PhoneTimeSuggestion expectedFinalTimeSuggestion =
                createTimeSuggestionFromNitzSignal(mPhone.getPhoneId(), finalNitzSignal);
        script.verifyTimeSuggestedAndZoneSetAndReset(expectedFinalTimeSuggestion,
                        scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(finalNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
    }

    /**
     * Asserts a test scenario has the properties we expect for NITZ-only lookup. There are
     * usually multiple zones that will share the same UTC offset so we get a low quality / low
     * confidence answer, but the zone we find should at least have the correct offset.
     */
    private String checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(Scenario scenario) {
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        OffsetResult result = mRealTimeZoneLookupHelper.lookupByNitz(nitzSignal.getValue());
        String expectedZoneId = result.getTimeZone().getID();
        // All our scenarios should return multiple matches. The only cases where this wouldn't be
        // true are places that use offsets like XX:15, XX:30 and XX:45.
        assertFalse(result.getIsOnlyMatch());
        assertSameOffset(scenario.getActualTimeMillis(), expectedZoneId, scenario.getTimeZoneId());
        return expectedZoneId;
    }

    private static void assertSameOffset(long timeMillis, String zoneId1, String zoneId2) {
        assertEquals(TimeZone.getTimeZone(zoneId1).getOffset(timeMillis),
                TimeZone.getTimeZone(zoneId2).getOffset(timeMillis));
    }

    /**
     * A "fluent" helper class allowing reuse of logic for test state initialization, simulation of
     * events, and verification of device state changes with self-describing method names.
     */
    class Script {

        // Initialization methods for setting simulated device state.

        Script initializeTimeZoneDetectionEnabled(boolean enabled) {
            mFakeTimeServiceHelper.timeZoneDetectionEnabled = enabled;
            return this;
        }

        Script initializeTimeZoneSetting(String timeZoneId) {
            mFakeTimeServiceHelper.deviceTimeZone.init(timeZoneId);
            return this;
        }

        Script initializeSystemClock(long timeMillis) {
            mFakeDeviceState.currentTimeMillis = timeMillis;
            return this;
        }

        // Simulation methods.

        Script incrementTime(int timeIncrementMillis) {
            mFakeDeviceState.simulateTimeIncrement(timeIncrementMillis);
            return this;
        }

        Script countryReceived(String countryIsoCode) {
            mNitzStateMachine.handleCountryDetected(countryIsoCode);
            return this;
        }

        Script networkAvailable() {
            mNitzStateMachine.handleNetworkAvailable();
            return this;
        }

        Script nitzReceived(TimestampedValue<NitzData> nitzSignal) {
            mNitzStateMachine.handleNitzReceived(nitzSignal);
            return this;
        }

        Script networkUnavailable() {
            mNitzStateMachine.handleNetworkUnavailable();
            return this;
        }

        Script toggleAirplaneMode(boolean on) {
            mNitzStateMachine.handleAirplaneModeChanged(on);
            return this;
        }

        Script toggleTimeZoneDetectionEnabled(boolean on) {
            mFakeTimeServiceHelper.timeZoneDetectionEnabled = on;
            mFakeTimeServiceHelper.listener.onTimeZoneDetectionChange(on);
            return this;
        }

        // Verification methods.

        Script verifyNothingWasSetAndReset() {
            verifyTimeZoneWasNotSet();
            verifyTimeWasNotSuggested();
            commitStateChanges();
            return this;
        }

        Script verifyOnlyTimeZoneWasSetAndReset(String timeZoneId) {
            verifyTimeZoneWasSet(timeZoneId);
            verifyTimeWasNotSuggested();
            commitStateChanges();
            return this;
        }

        Script verifyOnlyTimeWasSuggestedAndReset(PhoneTimeSuggestion timeSuggestion) {
            verifyTimeZoneWasNotSet();
            verifyTimeWasSuggested(timeSuggestion);
            commitStateChanges();
            return this;
        }

        Script verifyTimeSuggestedAndZoneSetAndReset(
                PhoneTimeSuggestion timeSuggestion, String timeZoneId) {
            verifyTimeZoneWasSet(timeZoneId);
            verifyTimeWasSuggested(timeSuggestion);
            commitStateChanges();
            return this;
        }

        private void verifyTimeZoneWasNotSet() {
            mFakeTimeServiceHelper.deviceTimeZone.assertHasNotBeenSet();
        }

        private void verifyTimeWasNotSuggested() {
            mFakeTimeServiceHelper.suggestedTime.assertHasNotBeenSet();
        }

        private void verifyTimeZoneWasSet(String timeZoneId) {
            mFakeTimeServiceHelper.deviceTimeZone.assertHasBeenSet();
            mFakeTimeServiceHelper.deviceTimeZone.assertLatestEquals(timeZoneId);
        }

        private void verifyTimeWasSuggested(PhoneTimeSuggestion phoneTimeSuggestion) {
            mFakeTimeServiceHelper.suggestedTime.assertChangeCount(1);
            mFakeTimeServiceHelper.suggestedTime.assertLatestEquals(phoneTimeSuggestion);
        }

        private void commitStateChanges() {
            mFakeTimeServiceHelper.commitState();
        }
    }

    /** Some piece of state that tests want to track. */
    private static class TestState<T> {
        private T mInitialValue;
        private LinkedList<T> mValues = new LinkedList<>();

        void init(T value) {
            mValues.clear();
            mInitialValue = value;
        }

        void set(T value) {
            mValues.addFirst(value);
        }

        boolean hasBeenSet() {
            return mValues.size() > 0;
        }

        void assertHasNotBeenSet() {
            assertFalse(hasBeenSet());
        }

        void assertHasBeenSet() {
            assertTrue(hasBeenSet());
        }

        void commitLatest() {
            if (hasBeenSet()) {
                mInitialValue = mValues.getLast();
                mValues.clear();
            }
        }

        void assertLatestEquals(T expected) {
            assertEquals(expected, getLatest());
        }

        void assertChangeCount(int expectedCount) {
            assertEquals(expectedCount, mValues.size());
        }

        public T getLatest() {
            if (hasBeenSet()) {
                return mValues.getFirst();
            }
            return mInitialValue;
        }
    }

    private static class FakeTimeServiceHelper implements TimeServiceHelper {

        private final FakeDeviceState mFakeDeviceState;

        public TimeServiceHelper.Listener listener;
        public boolean timeZoneDetectionEnabled;

        // State we want to track.
        public TestState<String> deviceTimeZone = new TestState<>();
        public TestState<PhoneTimeSuggestion> suggestedTime = new TestState<>();

        FakeTimeServiceHelper(FakeDeviceState fakeDeviceState) {
            mFakeDeviceState = fakeDeviceState;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public boolean isTimeZoneSettingInitialized() {
            return deviceTimeZone.getLatest() != null;
        }

        @Override
        public boolean isTimeZoneDetectionEnabled() {
            return timeZoneDetectionEnabled;
        }

        @Override
        public void setDeviceTimeZone(String zoneId) {
            deviceTimeZone.set(zoneId);
        }

        @Override
        public void suggestDeviceTime(PhoneTimeSuggestion phoneTimeSuggestion) {
            suggestedTime.set(phoneTimeSuggestion);
            // The fake time service just uses the latest suggestion.
            mFakeDeviceState.currentTimeMillis = phoneTimeSuggestion.getUtcTime().getValue();
        }

        void commitState() {
            deviceTimeZone.commitLatest();
            suggestedTime.commitLatest();
        }
    }
}
