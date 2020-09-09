/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.internal.telephony.nitz;

import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET;

import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.ARBITRARY_SYSTEM_CLOCK_TIME;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNITED_KINGDOM_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.createEmptyTimeSuggestion;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.createEmptyTimeZoneSuggestion;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.createTimeSuggestionFromNitzSignal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.timedetector.TelephonyTimeSuggestion;
import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.os.TimestampedValue;

import com.android.internal.telephony.IndentingPrintWriter;
import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nitz.NitzStateMachineImpl.NitzSignalInputFilterPredicate;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.FakeDeviceState;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class NitzStateMachineImplTest extends TelephonyTest {

    private static final int SLOT_INDEX = 99999;
    private static final TelephonyTimeZoneSuggestion EMPTY_TIME_ZONE_SUGGESTION =
            createEmptyTimeZoneSuggestion(SLOT_INDEX);
    private static final TelephonyTimeSuggestion EMPTY_TIME_SUGGESTION =
            createEmptyTimeSuggestion(SLOT_INDEX);

    private FakeTimeServiceHelper mFakeTimeServiceHelper;
    private FakeDeviceState mFakeDeviceState;
    private TimeZoneSuggesterImpl mRealTimeZoneSuggester;

    private NitzStateMachineImpl mNitzStateMachineImpl;


    @Before
    public void setUp() throws Exception {
        TelephonyTest.logd("NitzStateMachineImplTest +Setup!");
        super.setUp("NitzStateMachineImplTest");

        // In tests we use a fake impls for NewTimeServiceHelper and DeviceState.
        mFakeDeviceState = new FakeDeviceState();
        mFakeTimeServiceHelper = new FakeTimeServiceHelper(mFakeDeviceState);

        // In tests we disable NITZ signal input filtering. The real NITZ signal filter is tested
        // independently. This makes constructing test data simpler: we can be sure the signals
        // won't be filtered for reasons like rate-limiting.
        NitzSignalInputFilterPredicate mFakeNitzSignalInputFilter = (oldSignal, newSignal) -> true;

        // In tests a real TimeZoneSuggesterImpl is used with the real TimeZoneLookupHelper and real
        // country time zone data. A fake device state is used (which allows tests to fake the
        // system clock / user settings). The tests can perform the expected lookups and confirm the
        // state machine takes the correct action. Picking real examples from the past is easier
        // than inventing countries / scenarios and configuring fakes.
        TimeZoneLookupHelper timeZoneLookupHelper = new TimeZoneLookupHelper();
        mRealTimeZoneSuggester = new TimeZoneSuggesterImpl(mFakeDeviceState, timeZoneLookupHelper);

        mNitzStateMachineImpl = new NitzStateMachineImpl(
                SLOT_INDEX, mFakeNitzSignalInputFilter, mRealTimeZoneSuggester,
                mFakeTimeServiceHelper);

        TelephonyTest.logd("NewNitzStateMachineImplTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test_countryThenNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        String networkCountryIsoCode = scenario.getNetworkCountryIsoCode();
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Capture expected results from the real suggester and confirm we can tell the difference
        // between them.
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion1 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, networkCountryIsoCode, null /* nitzSignal */);
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion2 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, networkCountryIsoCode, nitzSignal);
        assertNotNull(expectedTimeZoneSuggestion2);
        assertNotEquals(expectedTimeZoneSuggestion1, expectedTimeZoneSuggestion2);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate country being known.
        script.countryReceived(networkCountryIsoCode);

        script.verifyOnlyTimeZoneWasSuggestedAndReset(expectedTimeZoneSuggestion1);

        // Check NitzStateMachine exposed state.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // Simulate NITZ being received and verify the behavior.
        script.nitzReceived(nitzSignal);

        TelephonyTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, nitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedTimeSuggestion, expectedTimeZoneSuggestion2);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    @Test
    public void test_nitzThenCountry() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        String networkCountryIsoCode = scenario.getNetworkCountryIsoCode();

        // Capture test expectations from the real suggester and confirm we can tell the difference
        // between them.
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion1 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, null /* countryIsoCode */, nitzSignal);
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion2 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, networkCountryIsoCode, nitzSignal);
        assertNotEquals(expectedTimeZoneSuggestion1, expectedTimeZoneSuggestion2);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate receiving the NITZ signal.
        script.nitzReceived(nitzSignal);

        // Verify the state machine did the right thing.
        TelephonyTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, nitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedTimeSuggestion, expectedTimeZoneSuggestion1);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());

        // Simulate country being known and verify the behavior.
        script.countryReceived(networkCountryIsoCode)
                .verifyOnlyTimeZoneWasSuggestedAndReset(expectedTimeZoneSuggestion2);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    @Test
    public void test_emptyCountryString_countryReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate an empty country being set.
        script.countryReceived("");

        // Nothing should be set. The country is not valid.
        script.verifyOnlyTimeZoneWasSuggestedAndReset(EMPTY_TIME_ZONE_SUGGESTION);

        // Check NitzStateMachine exposed state.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // Simulate receiving the NITZ signal.
        script.nitzReceived(nitzSignal);

        TelephonyTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, nitzSignal);
        // Capture output from the real suggester and confirm it meets the test's needs /
        // expectations.
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, "" /* countryIsoCode */, nitzSignal);
        assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY,
                expectedTimeZoneSuggestion.getMatchType());
        assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET,
                expectedTimeZoneSuggestion.getQuality());

        // Verify the state machine did the right thing.
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedTimeSuggestion, expectedTimeZoneSuggestion);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    @Test
    public void test_emptyCountryStringUsTime_nitzReceivedFirst() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate receiving the NITZ signal.
        script.nitzReceived(nitzSignal);

        // Verify the state machine did the right thing.
        // No time zone should be set. A NITZ signal by itself is not enough.
        TelephonyTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, nitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedTimeSuggestion, EMPTY_TIME_ZONE_SUGGESTION);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());

        // Simulate an empty country being set.
        script.countryReceived("");

        // Capture output from the real suggester and confirm it meets the test's needs /
        // expectations.
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, "" /* countryIsoCode */, nitzSignal);
        assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY,
                expectedTimeZoneSuggestion.getMatchType());
        assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET,
                expectedTimeZoneSuggestion.getQuality());

        // Verify the state machine did the right thing.
        script.verifyOnlyTimeZoneWasSuggestedAndReset(expectedTimeZoneSuggestion);

        // Check NitzStateMachine exposed state.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    @Test
    public void test_airplaneModeClearsState() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO.mutableCopy();
        int timeStepMillis = (int) TimeUnit.HOURS.toMillis(3);

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Pre-flight: Simulate a device receiving signals that allow it to detect time and time
        // zone.
        TimestampedValue<NitzData> preFlightNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        TelephonyTimeSuggestion expectedPreFlightTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, preFlightNitzSignal);
        String preFlightCountryIsoCode = scenario.getNetworkCountryIsoCode();

        // Simulate receiving the NITZ signal and country.
        script.nitzReceived(preFlightNitzSignal)
                .countryReceived(preFlightCountryIsoCode);

        // Verify the state machine did the right thing.
        TelephonyTimeZoneSuggestion expectedPreFlightTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, preFlightCountryIsoCode, preFlightNitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedPreFlightTimeSuggestion, expectedPreFlightTimeZoneSuggestion);

        // Check state that NitzStateMachine must expose.
        assertEquals(preFlightNitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());

        // Boarded flight: Airplane mode turned on / time zone detection still enabled.
        // The NitzStateMachine must lose all state and stop having an opinion about time zone.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate airplane mode being turned on.
        script.toggleAirplaneMode(true);

        // Verify the state machine did the right thing.
        // Check the time zone suggestion was withdrawn (time is not currently withdrawn).
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                EMPTY_TIME_SUGGESTION, EMPTY_TIME_ZONE_SUGGESTION);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // During flight: Airplane mode turned off / time zone detection still enabled.
        // The NitzStateMachine still must not have an opinion about time zone / hold any state.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate airplane mode being turned off.
        script.toggleAirplaneMode(false);

        // Verify nothing was suggested: The last suggestion was empty so nothing has changed.
        script.verifyNothingWasSuggested();

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // Post flight: Device has moved and receives new signals.

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the movement to the destination.
        scenario.changeCountry(UNIQUE_US_ZONE_SCENARIO1.getTimeZoneId(),
                UNIQUE_US_ZONE_SCENARIO1.getNetworkCountryIsoCode());

        // Simulate the device receiving NITZ signal and country again after the flight. Now the
        // NitzStateMachine should be opinionated again.
        TimestampedValue<NitzData> postFlightNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        String postFlightCountryCode = scenario.getNetworkCountryIsoCode();
        script.countryReceived(postFlightCountryCode)
                .nitzReceived(postFlightNitzSignal);

        // Verify the state machine did the right thing.
        TelephonyTimeSuggestion expectedPostFlightTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, postFlightNitzSignal);
        TelephonyTimeZoneSuggestion expectedPostFlightTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, postFlightCountryCode, postFlightNitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedPostFlightTimeSuggestion, expectedPostFlightTimeZoneSuggestion);

        // Check state that NitzStateMachine must expose.
        assertEquals(postFlightNitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    /**
     * Confirm losing the network / NITZ doesn't clear country state.
     */
    @Test
    public void test_handleNetworkUnavailableClearsNetworkState() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1.mutableCopy();
        int timeStepMillis = (int) TimeUnit.HOURS.toMillis(3);
        String countryIsoCode = scenario.getNetworkCountryIsoCode();

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate a device receiving signals that allow it to detect time and time zone.
        TimestampedValue<NitzData> initialNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        TelephonyTimeSuggestion expectedInitialTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, initialNitzSignal);

        // Simulate receiving the NITZ signal and country.
        script.nitzReceived(initialNitzSignal)
                .countryReceived(countryIsoCode);

        // Verify the state machine did the right thing.
        TelephonyTimeZoneSuggestion expectedInitialTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, countryIsoCode, initialNitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedInitialTimeSuggestion, expectedInitialTimeZoneSuggestion);

        // Check state that NitzStateMachine must expose.
        assertEquals(initialNitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate network being lost.
        script.networkUnavailable();

        // Check the "no NITZ" time and time zone suggestions are made.
        TelephonyTimeZoneSuggestion expectedMiddleTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, countryIsoCode, null /* nitzSignal */);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                EMPTY_TIME_SUGGESTION, expectedMiddleTimeZoneSuggestion);

        // Check state that NitzStateMachine must expose.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the network being found.
        script.networkAvailable()
                .verifyNothingWasSuggested();

        // Check the state that NitzStateMachine must expose.
        assertNull(mNitzStateMachineImpl.getCachedNitzData());

        // Simulate the passage of time and update the device realtime clock.
        scenario.incrementTime(timeStepMillis);
        script.incrementTime(timeStepMillis);

        // Simulate the device receiving NITZ signal again. Now the NitzStateMachine should be
        // opinionated again.
        TimestampedValue<NitzData> finalNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
        script.nitzReceived(finalNitzSignal);

        // Verify the state machine did the right thing.
        TelephonyTimeSuggestion expectedFinalTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, finalNitzSignal);
        TelephonyTimeZoneSuggestion expectedFinalTimeZoneSuggestion =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, countryIsoCode, finalNitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedFinalTimeSuggestion, expectedFinalTimeZoneSuggestion);

        // Check state that NitzStateMachine must expose.
        assertEquals(finalNitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    @Test
    public void test_countryUnavailableClearsTimeZoneSuggestion() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        Script script = new Script()
                .initializeSystemClock(ARBITRARY_SYSTEM_CLOCK_TIME)
                .networkAvailable();

        // Simulate receiving the country and verify the state machine does the right thing.
        script.countryReceived(scenario.getNetworkCountryIsoCode());
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion1 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
        script.verifyOnlyTimeZoneWasSuggestedAndReset(expectedTimeZoneSuggestion1);

        // Simulate receiving an NITZ signal and verify the state machine does the right thing.
        script.nitzReceived(nitzSignal);
        TelephonyTimeSuggestion expectedTimeSuggestion =
                createTimeSuggestionFromNitzSignal(SLOT_INDEX, nitzSignal);
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion2 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
        script.verifyTimeAndTimeZoneSuggestedAndReset(
                expectedTimeSuggestion, expectedTimeZoneSuggestion2);

        // Check state that NitzStateMachine must expose.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());

        // Simulate the country becoming unavailable and verify the state machine does the right
        // thing.
        script.countryUnavailable();
        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion3 =
                mRealTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, null /* countryIsoCode */, nitzSignal);
        script.verifyOnlyTimeZoneWasSuggestedAndReset(expectedTimeZoneSuggestion3);

        // Check state that NitzStateMachine must expose.
        assertEquals(nitzSignal.getValue(), mNitzStateMachineImpl.getCachedNitzData());
    }

    /**
     * A "fluent" helper class allowing reuse of logic for test state initialization, simulation of
     * events, and verification of device state changes with self-describing method names.
     */
    private class Script {

        Script() {
            // Set initial fake device state.
            mFakeDeviceState.ignoreNitz = false;
            mFakeDeviceState.nitzUpdateDiffMillis = 2000;
            mFakeDeviceState.nitzUpdateSpacingMillis = 1000 * 60 * 10;
        }

        // Initialization methods for setting simulated device state, usually before simulation.

        Script initializeSystemClock(long timeMillis) {
            mFakeDeviceState.currentTimeMillis = timeMillis;
            return this;
        }

        // Simulation methods that are used by tests to pretend that something happens.

        Script incrementTime(int timeIncrementMillis) {
            mFakeDeviceState.simulateTimeIncrement(timeIncrementMillis);
            return this;
        }

        Script networkAvailable() {
            mNitzStateMachineImpl.handleNetworkAvailable();
            return this;
        }

        Script nitzReceived(TimestampedValue<NitzData> nitzSignal) {
            mNitzStateMachineImpl.handleNitzReceived(nitzSignal);
            return this;
        }

        Script networkUnavailable() {
            mNitzStateMachineImpl.handleNetworkUnavailable();
            return this;
        }

        Script countryUnavailable() {
            mNitzStateMachineImpl.handleCountryUnavailable();
            return this;
        }

        Script countryReceived(String countryIsoCode) {
            mNitzStateMachineImpl.handleCountryDetected(countryIsoCode);
            return this;
        }

        Script toggleAirplaneMode(boolean on) {
            mNitzStateMachineImpl.handleAirplaneModeChanged(on);
            return this;
        }

        // Verification methods.

        Script verifyNothingWasSuggested() {
            justVerifyTimeWasNotSuggested();
            justVerifyTimeWasNotSuggested();
            return this;
        }

        Script verifyOnlyTimeZoneWasSuggestedAndReset(
                TelephonyTimeZoneSuggestion timeZoneSuggestion) {
            justVerifyTimeZoneWasSuggested(timeZoneSuggestion);
            justVerifyTimeWasNotSuggested();
            commitStateChanges();
            return this;
        }

        Script verifyTimeAndTimeZoneSuggestedAndReset(TelephonyTimeSuggestion timeSuggestion,
                TelephonyTimeZoneSuggestion timeZoneSuggestion) {
            justVerifyTimeZoneWasSuggested(timeZoneSuggestion);
            justVerifyTimeWasSuggested(timeSuggestion);
            commitStateChanges();
            return this;
        }

        private void justVerifyTimeWasNotSuggested() {
            mFakeTimeServiceHelper.suggestedTimes.assertHasNotBeenSet();
        }

        private void justVerifyTimeZoneWasSuggested(
                TelephonyTimeZoneSuggestion timeZoneSuggestion) {
            mFakeTimeServiceHelper.suggestedTimeZones.assertHasBeenSet();
            mFakeTimeServiceHelper.suggestedTimeZones.assertLatestEquals(timeZoneSuggestion);
        }

        private void justVerifyTimeWasSuggested(TelephonyTimeSuggestion timeSuggestion) {
            mFakeTimeServiceHelper.suggestedTimes.assertChangeCount(1);
            mFakeTimeServiceHelper.suggestedTimes.assertLatestEquals(timeSuggestion);
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
                mInitialValue = mValues.getFirst();
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

    /**
     * A fake implementation of {@link TimeServiceHelper} that enables tests to detect what
     * {@link NitzStateMachineImpl} would do to a real device's state.
     */
    private static class FakeTimeServiceHelper implements TimeServiceHelper {

        private final FakeDeviceState mFakeDeviceState;

        // State we want to track.
        public final TestState<TelephonyTimeSuggestion> suggestedTimes = new TestState<>();
        public final TestState<TelephonyTimeZoneSuggestion> suggestedTimeZones = new TestState<>();

        FakeTimeServiceHelper(FakeDeviceState fakeDeviceState) {
            mFakeDeviceState = fakeDeviceState;
        }

        @Override
        public void suggestDeviceTime(TelephonyTimeSuggestion timeSuggestion) {
            suggestedTimes.set(timeSuggestion);
            if (timeSuggestion.getUtcTime() != null) {
                // The fake time service just uses the latest suggestion.
                mFakeDeviceState.currentTimeMillis = timeSuggestion.getUtcTime().getValue();
            }
        }

        @Override
        public void maybeSuggestDeviceTimeZone(TelephonyTimeZoneSuggestion timeZoneSuggestion) {
            suggestedTimeZones.set(timeZoneSuggestion);
        }

        @Override
        public void dumpLogs(IndentingPrintWriter ipw) {
            // No-op in tests
        }

        @Override
        public void dumpState(PrintWriter pw) {
            // No-op in tests
        }

        void commitState() {
            suggestedTimeZones.commitLatest();
            suggestedTimes.commitLatest();
        }
    }
}
