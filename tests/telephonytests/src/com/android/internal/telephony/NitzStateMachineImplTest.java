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

import static com.android.internal.telephony.NitzStateMachineTestSupport.createTimeSignalFromNitzSignal;
import static com.android.internal.telephony.NitzStateMachineTestSupport.createUtcTime;
import static com.android.internal.telephony.NitzStateMachineTestSupport.zone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.icu.util.TimeZone;
import android.util.TimestampedValue;

import com.android.internal.telephony.NitzStateMachineTestSupport.FakeDeviceState;
import com.android.internal.telephony.NitzStateMachineTestSupport.Scenario;
import com.android.internal.telephony.TimeZoneLookupHelper.CountryResult;
import com.android.internal.telephony.TimeZoneLookupHelper.OffsetResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class NitzStateMachineImplTest extends TelephonyTest {

    // Values used to when initializing device state but where the value isn't important.
    private static final long ARBITRARY_SYSTEM_CLOCK_TIME = createUtcTime(1977, 1, 1, 12, 0, 0);
    private static final long ARBITRARY_REALTIME_MILLIS = 123456789L;
    private static final String ARBITRARY_TIME_ZONE_ID = "Europe/Paris";

    // A country with a single zone : the zone can be guessed from the country.
    // The UK uses UTC for part of the year so it is not good for detecting bogus NITZ signals.
    private static final Scenario UNITED_KINGDOM_SCENARIO = new Scenario.Builder()
            .setDeviceRealtimeMillis(ARBITRARY_REALTIME_MILLIS)
            .setTimeZone("Europe/London")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("gb")
            .buildFrozen();

    // A country that has multiple zones, but there is only one matching time zone at the time :
    // the zone cannot be guessed from the country alone, but can be guessed from the country +
    // NITZ. The US never uses UTC so it can be used for testing bogus NITZ signal handling.
    private static final Scenario UNIQUE_US_ZONE_SCENARIO = new Scenario.Builder()
            .setDeviceRealtimeMillis(ARBITRARY_REALTIME_MILLIS)
            .setTimeZone("America/Los_Angeles")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("us")
            .buildFrozen();

    // A country with a single zone: the zone can be guessed from the country alone. CZ never uses
    // UTC so it can be used for testing bogus NITZ signal handling.
    private static final Scenario CZECHIA_SCENARIO = new Scenario.Builder()
            .setDeviceRealtimeMillis(ARBITRARY_REALTIME_MILLIS)
            .setTimeZone("Europe/Prague")
            .setActualTimeUtc(2018, 1, 1, 12, 0, 0)
            .setCountryIso("cz")
            .buildFrozen();

    private FakeTimeServiceHelper mFakeTimeServiceHelper;
    private FakeDeviceState mFakeDeviceState;
    private TimeZoneLookupHelper mRealTimeZoneLookupHelper;

    private NitzStateMachineImpl mNitzStateMachine;

    @Before
    public void setUp() throws Exception {
        logd("NitzStateMachineTest +Setup!");
        super.setUp("NitzStateMachineTest");

        // In tests we use a fake impls for TimeServiceHelper and DeviceState.
        mFakeTimeServiceHelper = new FakeTimeServiceHelper();
        mFakeDeviceState = new FakeDeviceState();

        // In tests we use the real TimeZoneLookupHelper.
        mRealTimeZoneLookupHelper = new TimeZoneLookupHelper();
        mNitzStateMachine = new NitzStateMachineImpl(
                mPhone, mFakeTimeServiceHelper, mFakeDeviceState, mRealTimeZoneLookupHelper);

        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test_uniqueUsZone_Assumptions() {
        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // allZonesHaveSameOffset == false, so we shouldn't pick an arbitrary zone.
        CountryResult expectedCountryLookupResult = new CountryResult(
                "America/New_York",
                true /* multipleZonesInCountry */,
                false /* allZonesHaveSameOffset */,
                ARBITRARY_SYSTEM_CLOCK_TIME);
        CountryResult actualCountryLookupResult =
                mRealTimeZoneLookupHelper.lookupByCountry(
                        UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // isOnlyMatch == true, so the combination of country + NITZ should be enough.
        OffsetResult expectedLookupResult =
                new OffsetResult(zone("America/Los_Angeles"), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mRealTimeZoneLookupHelper.lookupByNitzCountry(
                UNIQUE_US_ZONE_SCENARIO.createNitzSignal().getValue(),
                UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }

    @Test
    public void test_unitedKingdom_Assumptions() {
        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // allZonesHaveSameOffset == true (not only that, there is only one zone), so we can pick
        // the zone knowing only the country.
        CountryResult expectedCountryLookupResult = new CountryResult(
                "Europe/London",
                false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */,
                ARBITRARY_SYSTEM_CLOCK_TIME);
        CountryResult actualCountryLookupResult =
                mRealTimeZoneLookupHelper.lookupByCountry(
                        UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        OffsetResult expectedLookupResult =
                new OffsetResult(zone("Europe/London"), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mRealTimeZoneLookupHelper.lookupByNitzCountry(
                UNITED_KINGDOM_SCENARIO.createNitzSignal().getValue(),
                UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }

    @Test
    public void test_uniqueUsZone_timeZoneEnabled_countryThenNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null);

        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country won't be enough for time zone detection.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal)
                // Country + NITZ is enough for both time + time zone detection.
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_unitedKingdom_timeZoneEnabled_countryThenNitz() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null);

        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone is enough to guess the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId())
                .nitzReceived(nitzSignal)
                // Country + NITZ is enough for both time + time zone detection.
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_uniqueUsZone_timeZoneDisabled_countryThenNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(false)
                .initializeTimeZoneSetting(null);

        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country is not enough to guess the time zone and time zone detection is disabled.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal)
                // Time zone detection is disabled, but time should be suggested from NITZ.
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_unitedKingdom_timeZoneDisabled_countryThenNitz() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(false)
                .initializeTimeZoneSetting(null);

        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // Country alone would be enough for time zone detection, but it's disabled.
                .verifyNothingWasSetAndReset()
                .nitzReceived(nitzSignal)
                // Time zone detection is disabled, but time should be suggested from NITZ.
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_uniqueUsZone_timeZoneEnabled_nitzThenCountry() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null);

        // Simulate receiving an NITZ signal.
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.nitzReceived(nitzSignal)
                // The NITZ alone isn't enough to detect a time zone.
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The NITZ + country is enough to detect the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_unitedKingdom_timeZoneEnabled_nitzThenCountry() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null);

        // Simulate receiving an NITZ signal.
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.nitzReceived(nitzSignal)
                // The NITZ alone isn't enough to detect a time zone.
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode());

        // The NITZ + country is enough to detect the time zone.
        script.verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_validCzNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

        // Simulate receiving an NITZ signal.
        script.nitzReceived(goodNitzSignal)
                // The NITZ alone isn't enough to detect a time zone.
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_validCzNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The NITZ country is enough to detect the time zone.
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(goodNitzSignal)
                // The time will be suggested from the NITZ signal.
                // The combination of NITZ + country will cause the time zone to be set.
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(goodNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_bogusCzNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal)
                // The NITZ alone isn't enough to detect a time zone, but there isn't enough
                // information to work out it is bogus.
                .verifyOnlyTimeWasSuggestedAndReset(createTimeSignalFromNitzSignal(badNitzSignal));

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .verifyOnlyTimeZoneWasSetAndReset(scenario.getTimeZoneId());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_bogusCzNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

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
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal)
                // The NITZ should be detected as bogus so only the time will be suggested.
                .verifyOnlyTimeWasSuggestedAndReset(createTimeSignalFromNitzSignal(badNitzSignal));

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_bogusUniqueUsNitzSignal_nitzReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

        // Create a corrupted NITZ signal, where the offset information has been lost.
        NitzData bogusNitzData = NitzData.createForTests(
                0 /* UTC! */, null /* dstOffsetMillis */,
                goodNitzSignal.getValue().getCurrentTimeInMillis(),
                null /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal)
                // The NITZ alone isn't enough to detect a time zone, but there isn't enough
                // information to work out its bogus.
                .verifyOnlyTimeWasSuggestedAndReset(createTimeSignalFromNitzSignal(badNitzSignal));

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate the country code becoming known.
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                // The country isn't enough to detect the time zone for US so we will leave the time
                // zone unset.
                .verifyNothingWasSetAndReset();

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_bogusUsUniqueNitzSignal_countryReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> goodNitzSignal = scenario.createNitzSignal();

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
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate receiving an NITZ signal.
        script.nitzReceived(badNitzSignal)
                // The NITZ should be detected as bogus so only the time will be suggested.
                .verifyOnlyTimeWasSuggestedAndReset(createTimeSignalFromNitzSignal(badNitzSignal));

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(badNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_emulatorNitzExtensionUsedForTimeZone() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        TimestampedValue<NitzData> originalNitzSignal = scenario.createNitzSignal();

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
        script.nitzReceived(emulatorNitzSignal)
                .verifyTimeSuggestedAndZoneSetAndReset(
                        createTimeSignalFromNitzSignal(emulatorNitzSignal), emulatorTimeZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(emulatorNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(emulatorTimeZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_emptyCountryStringUsTime_countryReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        String expectedZoneId = checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(scenario);

        // Nothing should be set. The country is not valid.
        script.countryReceived("").verifyNothingWasSetAndReset();

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertNull(mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Simulate receiving the NITZ signal.
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.nitzReceived(nitzSignal)
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), expectedZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(expectedZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_emptyCountryStringUsTime_nitzReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO;
        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(ARBITRARY_TIME_ZONE_ID);

        String expectedZoneId = checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(scenario);

        // Simulate receiving the NITZ signal.
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
        script.nitzReceived(nitzSignal)
                .verifyOnlyTimeWasSuggestedAndReset(scenario.createTimeSignal());

        // Check NitzStateMachine state.
        assertFalse(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // The time zone should be set (but the country is not valid so it's unlikely to be
        // correct).
        script.countryReceived("").verifyOnlyTimeZoneWasSetAndReset(expectedZoneId);

        // Check NitzStateMachine state.
        assertTrue(mNitzStateMachine.getNitzTimeZoneDetectionSuccessful());
        assertEquals(nitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(expectedZoneId, mNitzStateMachine.getSavedTimeZoneId());
    }

    @Test
    public void test_airplaneModeClearsState() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO.mutableCopy();
        long timeStepMillis = TimeUnit.HOURS.toMillis(3);

        Script script = new Script()
                .setRealtimeClockFromScenario(scenario)
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(null);

        // Pre-flight: Simulate a device receiving signals that allow it to detect time and time
        // zone.
        TimestampedValue<NitzData> preflightNitzSignal = scenario.createNitzSignal();
        script.nitzReceived(preflightNitzSignal)
                .countryReceived(scenario.getNetworkCountryIsoCode())
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), scenario.getTimeZoneId());

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
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());

        // Boarded flight: Airplane mode turned on / time zone detection still enabled.
        // The NitzStateMachineImpl must lose all state and stop having an opinion about time zone.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.setRealtimeClockFromScenario(scenario);

        script.toggleAirplaneMode(true);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Verify there's no time zone opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // During flight: Airplane mode turned off / time zone detection still enabled.
        // The NitzStateMachineImpl still must not have an opinion about time zone / hold any state.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.setRealtimeClockFromScenario(scenario);

        script.toggleAirplaneMode(false);

        // Verify there's still no opinion by toggling auto time zone off and on.
        script.toggleTimeZoneDetectionEnabled(false)
                .verifyNothingWasSetAndReset()
                .toggleTimeZoneDetectionEnabled(true)
                .verifyNothingWasSetAndReset();

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachine.getCachedNitzData());
        assertNull(mNitzStateMachine.getSavedTimeZoneId());

        // Post flight: Device has moved and receives new signals.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.setRealtimeClockFromScenario(scenario);

        // Simulate the movement to the destination.
        scenario.changeCountry(UNIQUE_US_ZONE_SCENARIO.getTimeZoneId(),
                UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode());

        // Simulate the device receiving NITZ signals again after the flight. Now the
        // NitzStateMachineImpl is opinionated again.
        TimestampedValue<NitzData> postFlightNitzSignal = scenario.createNitzSignal();
        script.countryReceived(scenario.getNetworkCountryIsoCode())
                .nitzReceived(postFlightNitzSignal)
                .verifyTimeSuggestedAndZoneSetAndReset(
                        scenario.createTimeSignal(), scenario.getTimeZoneId());

        // Check state that NitzStateMachine must expose.
        assertEquals(postFlightNitzSignal.getValue(), mNitzStateMachine.getCachedNitzData());
        assertEquals(scenario.getTimeZoneId(), mNitzStateMachine.getSavedTimeZoneId());
    }

    /**
     * Asserts a test scenario has the properties we expect for NITZ-only lookup. There are
     * usually multiple zones that will share the same UTC offset so we get a low quality / low
     * confidence answer, but the zone we find should at least have the correct offset.
     */
    private String checkNitzOnlyLookupIsAmbiguousAndReturnZoneId(Scenario scenario) {
        TimestampedValue<NitzData> nitzSignal = scenario.createNitzSignal();
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

        Script() {
            // Set initial fake device state.
            mFakeDeviceState.ignoreNitz = false;
            mFakeDeviceState.nitzUpdateDiffMillis = 2000;
            mFakeDeviceState.nitzUpdateSpacingMillis = 1000 * 60 * 10;

            mFakeDeviceState.networkCountryIsoForPhone = "";
        }

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
            mFakeTimeServiceHelper.currentTimeMillis = timeMillis;
            return this;
        }

        // Simulation methods.

        Script setRealtimeClockFromScenario(Scenario scenario) {
            long newRealTimeMillis = scenario.getDeviceRealTimeMillis();
            if (newRealTimeMillis < mFakeDeviceState.elapsedRealtime) {
                fail("elapsedRealtime clock shouldn't go backwards");
            }
            mFakeDeviceState.elapsedRealtime = newRealTimeMillis;
            return this;
        }

        Script countryReceived(String countryIsoCode) {
            mFakeDeviceState.networkCountryIsoForPhone = countryIsoCode;
            mNitzStateMachine.handleNetworkCountryCodeSet(true);
            return this;
        }

        Script nitzReceived(TimestampedValue<NitzData> nitzSignal) {
            mNitzStateMachine.handleNitzReceived(nitzSignal);
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

        Script verifyOnlyTimeWasSuggestedAndReset(TimestampedValue<Long> time) {
            verifyTimeZoneWasNotSet();
            verifyTimeWasSuggested(time);
            commitStateChanges();
            return this;
        }

        Script verifyTimeSuggestedAndZoneSetAndReset(
                TimestampedValue<Long> time, String timeZoneId) {
            verifyTimeZoneWasSet(timeZoneId);
            verifyTimeWasSuggested(time);
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

        private void verifyTimeWasSuggested(TimestampedValue<Long> time) {
            mFakeTimeServiceHelper.suggestedTime.assertChangeCount(1);
            mFakeTimeServiceHelper.suggestedTime.assertLatestEquals(time);
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

        public TimeServiceHelper.Listener listener;
        public boolean timeZoneDetectionEnabled;
        public long currentTimeMillis;

        // State we want to track.
        public TestState<String> deviceTimeZone = new TestState<>();
        public TestState<TimestampedValue<Long>> suggestedTime = new TestState<>();

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public long currentTimeMillis() {
            return currentTimeMillis;
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
        public void suggestDeviceTime(TimestampedValue<Long> deviceTime) {
            suggestedTime.set(deviceTime);
            // The fake time service just uses the latest suggestion.
            currentTimeMillis = deviceTime.getValue();
        }

        void commitState() {
            deviceTimeZone.commitLatest();
            suggestedTime.commitLatest();
        }
    }
}
