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

package com.android.internal.telephony.nitz.service;

import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.EMULATOR_ZONE_ID;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MULTIPLE_ZONES_WITH_SAME_OFFSET;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.NETWORK_COUNTRY_AND_OFFSET;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.NETWORK_COUNTRY_ONLY;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.SINGLE_ZONE;
import static com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.TEST_NETWORK_OFFSET_ONLY;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_HIGH;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_HIGHEST;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_LOW;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_MEDIUM;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_NONE;
import static com.android.internal.telephony.nitz.service.TimeZoneDetectionService.SCORE_USAGE_THRESHOLD;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.internal.telephony.IndentingPrintWriter;
import com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.MatchType;
import com.android.internal.telephony.nitz.service.PhoneTimeZoneSuggestion.Quality;
import com.android.internal.telephony.nitz.service.TimeZoneDetectionService.QualifiedPhoneTimeZoneSuggestion;

import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

/**
 * White-box unit tests for {@link TimeZoneDetectionService}.
 */
public class TimeZoneDetectionServiceTest {

    private static final int PHONE1_ID = 10000;
    private static final int PHONE2_ID = 20000;

    // Suggestion test cases are ordered so that each successive one is of the same or higher score
    // than the previous.
    private static final SuggestionTestCase[] TEST_CASES = new SuggestionTestCase[] {
            newTestCase(NETWORK_COUNTRY_ONLY, MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS, SCORE_LOW),
            newTestCase(NETWORK_COUNTRY_ONLY, MULTIPLE_ZONES_WITH_SAME_OFFSET, SCORE_MEDIUM),
            newTestCase(NETWORK_COUNTRY_AND_OFFSET, MULTIPLE_ZONES_WITH_SAME_OFFSET, SCORE_MEDIUM),
            newTestCase(NETWORK_COUNTRY_ONLY, SINGLE_ZONE, SCORE_HIGH),
            newTestCase(NETWORK_COUNTRY_AND_OFFSET, SINGLE_ZONE, SCORE_HIGH),
            newTestCase(TEST_NETWORK_OFFSET_ONLY, MULTIPLE_ZONES_WITH_SAME_OFFSET, SCORE_HIGHEST),
            newTestCase(EMULATOR_ZONE_ID, SINGLE_ZONE, SCORE_HIGHEST),
    };

    private TimeZoneDetectionService mTimeZoneDetectionService;
    private FakeTimeZoneDetectionServiceHelper mFakeTimeZoneDetectionServiceHelper;

    @Before
    public void setUp() {
        mFakeTimeZoneDetectionServiceHelper = new FakeTimeZoneDetectionServiceHelper();
        mTimeZoneDetectionService =
                new TimeZoneDetectionService(mFakeTimeZoneDetectionServiceHelper);
    }

    @Test
    public void testEmptySuggestions() {
        PhoneTimeZoneSuggestion phone1TimeZoneSuggestion = createEmptyPhone1Suggestion();
        PhoneTimeZoneSuggestion phone2TimeZoneSuggestion = createEmptyPhone2Suggestion();
        Script script = new Script()
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(true);

        script.suggestPhoneTimeZone(phone1TimeZoneSuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedPhoneTimeZoneSuggestion expectedPhone1ScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(phone1TimeZoneSuggestion, SCORE_NONE);
        assertEquals(expectedPhone1ScoredSuggestion,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
        assertNull(mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
        assertEquals(expectedPhone1ScoredSuggestion,
                mTimeZoneDetectionService.findBestSuggestionForTests());

        script.suggestPhoneTimeZone(phone2TimeZoneSuggestion)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedPhoneTimeZoneSuggestion expectedPhone2ScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(phone2TimeZoneSuggestion, SCORE_NONE);
        assertEquals(expectedPhone1ScoredSuggestion,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
        assertEquals(expectedPhone2ScoredSuggestion,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
        // Phone 1 should always beat phone 2, all other things being equal.
        assertEquals(expectedPhone1ScoredSuggestion,
                mTimeZoneDetectionService.findBestSuggestionForTests());
    }

    @Test
    public void testFirstPlausibleSuggestionAcceptedWhenTimeZoneUninitialized() {
        SuggestionTestCase testCase =
                newTestCase(NETWORK_COUNTRY_ONLY, MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS, SCORE_LOW);
        PhoneTimeZoneSuggestion lowQualitySuggestion =
                testCase.createSuggestion(PHONE1_ID, "America/New_York");
        Script script = new Script()
                .initializeTimeZoneDetectionEnabled(true);

        // The device is uninitialized.
        script.initializeTimeZoneSetting(false);

        // The very first suggestion will be taken.
        script.suggestPhoneTimeZone(lowQualitySuggestion)
                .verifyTimeZoneSetAndReset(lowQualitySuggestion);

        // Assert internal service state.
        QualifiedPhoneTimeZoneSuggestion expectedScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(lowQualitySuggestion, testCase.expectedScore);
        assertEquals(expectedScoredSuggestion,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
        assertEquals(expectedScoredSuggestion,
                mTimeZoneDetectionService.findBestSuggestionForTests());

        // Another low quality suggestion will be ignored now that the setting is initialized.
        PhoneTimeZoneSuggestion lowQualitySuggestion2 =
                testCase.createSuggestion(PHONE1_ID, "America/Los_Angeles");
        script.suggestPhoneTimeZone(lowQualitySuggestion2)
                .verifyTimeZoneNotSet();

        // Assert internal service state.
        QualifiedPhoneTimeZoneSuggestion expectedScoredSuggestion2 =
                new QualifiedPhoneTimeZoneSuggestion(lowQualitySuggestion2, testCase.expectedScore);
        assertEquals(expectedScoredSuggestion2,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
        assertEquals(expectedScoredSuggestion2,
                mTimeZoneDetectionService.findBestSuggestionForTests());
    }

    @Test
    public void testTogglingTimeZoneDetection() {
        Script script = new Script()
                .initializeTimeZoneSetting(true);

        boolean timeZoneDetectionEnabled = false;
        script.initializeTimeZoneDetectionEnabled(timeZoneDetectionEnabled);

        for (int i = 0; i < TEST_CASES.length; i++) {
            SuggestionTestCase testCase = TEST_CASES[i];

            PhoneTimeZoneSuggestion suggestion =
                    testCase.createSuggestion(PHONE1_ID, "Europe/London");
            script.suggestPhoneTimeZone(suggestion);

            // When time zone detection is already enabled the suggestion (if it scores highly
            // enough) should be set immediately.
            if (timeZoneDetectionEnabled) {
                if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
                    script.verifyTimeZoneSetAndReset(suggestion);
                } else {
                    script.verifyTimeZoneNotSet();
                }
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            QualifiedPhoneTimeZoneSuggestion expectedScoredSuggestion =
                    new QualifiedPhoneTimeZoneSuggestion(suggestion, testCase.expectedScore);
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectionService.findBestSuggestionForTests());

            // Now toggle the time zone detection setting: when it is toggled to on and the most
            // recent suggestion scores highly enough, the time zone should be set.
            timeZoneDetectionEnabled = !timeZoneDetectionEnabled;
            script.timeZoneDetectionEnabled(timeZoneDetectionEnabled);
            if (timeZoneDetectionEnabled) {
                if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
                    script.verifyTimeZoneSetAndReset(suggestion);
                } else {
                    script.verifyTimeZoneNotSet();
                }
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedScoredSuggestion,
                    mTimeZoneDetectionService.findBestSuggestionForTests());
        }
    }

    @Test
    public void testSuggestionsSinglePhone() {
        Script script = new Script()
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(true);

        for (SuggestionTestCase testCase : TEST_CASES) {
            makePhone1SuggestionAndCheckState(script, testCase);
        }

        /*
         * This is the same test as above but the test cases are in
         * reverse order of their expected score. New suggestions always replace previous ones:
         * there's effectively no history and so ordering shouldn't make any difference.
         */

        // Each test case will have the same or lower score than the last.
        ArrayList<SuggestionTestCase> descendingCasesByScore =
                new ArrayList<>(Arrays.asList(TEST_CASES));
        Collections.reverse(descendingCasesByScore);

        for (SuggestionTestCase testCase : descendingCasesByScore) {
            makePhone1SuggestionAndCheckState(script, testCase);
        }
    }

    private void makePhone1SuggestionAndCheckState(Script script, SuggestionTestCase testCase) {
        String zoneId = "Europe/London";
        PhoneTimeZoneSuggestion zonePhone1Suggestion = testCase.createSuggestion(PHONE1_ID, zoneId);
        QualifiedPhoneTimeZoneSuggestion expectedZonePhone1ScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(zonePhone1Suggestion, testCase.expectedScore);

        script.suggestPhoneTimeZone(zonePhone1Suggestion);
        if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
            script.verifyTimeZoneSetAndReset(zonePhone1Suggestion);
        } else {
            script.verifyTimeZoneNotSet();
        }

        // Assert internal service state.
        assertEquals(expectedZonePhone1ScoredSuggestion,
                mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
        assertEquals(expectedZonePhone1ScoredSuggestion,
                mTimeZoneDetectionService.findBestSuggestionForTests());
    }

    /**
     * Tries a set of test cases to see if the phone with the lowest ID is given preference. This
     * test also confirms that the time zone setting would only be set if a suggestion is of
     * sufficient quality.
     */
    @Test
    public void testMultiplePhoneSuggestionScoringAndPhoneIdBias() {
        String[] zoneIds = { "Europe/London", "Europe/Paris" };
        PhoneTimeZoneSuggestion emptyPhone1Suggestion = createEmptyPhone1Suggestion();
        PhoneTimeZoneSuggestion emptyPhone2Suggestion = createEmptyPhone2Suggestion();
        QualifiedPhoneTimeZoneSuggestion expectedEmptyPhone1ScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(emptyPhone1Suggestion, SCORE_NONE);
        QualifiedPhoneTimeZoneSuggestion expectedEmptyPhone2ScoredSuggestion =
                new QualifiedPhoneTimeZoneSuggestion(emptyPhone2Suggestion, SCORE_NONE);

        Script script = new Script()
                .initializeTimeZoneDetectionEnabled(true)
                .initializeTimeZoneSetting(true)
                // Initialize the latest suggestions as empty so we don't need to worry about nulls
                // below for the first loop.
                .suggestPhoneTimeZone(emptyPhone1Suggestion)
                .suggestPhoneTimeZone(emptyPhone2Suggestion)
                .resetState();

        for (SuggestionTestCase testCase : TEST_CASES) {
            PhoneTimeZoneSuggestion zonePhone1Suggestion =
                    testCase.createSuggestion(PHONE1_ID, zoneIds[0]);
            PhoneTimeZoneSuggestion zonePhone2Suggestion =
                    testCase.createSuggestion(PHONE2_ID, zoneIds[1]);
            QualifiedPhoneTimeZoneSuggestion expectedZonePhone1ScoredSuggestion =
                    new QualifiedPhoneTimeZoneSuggestion(zonePhone1Suggestion,
                            testCase.expectedScore);
            QualifiedPhoneTimeZoneSuggestion expectedZonePhone2ScoredSuggestion =
                    new QualifiedPhoneTimeZoneSuggestion(zonePhone2Suggestion,
                            testCase.expectedScore);

            // Start the test by making a suggestion for phone 1.
            script.suggestPhoneTimeZone(zonePhone1Suggestion);
            if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(zonePhone1Suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedZonePhone1ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedEmptyPhone2ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
            assertEquals(expectedZonePhone1ScoredSuggestion,
                    mTimeZoneDetectionService.findBestSuggestionForTests());

            // Phone 2 then makes an identical suggestion. Phone 1's suggestion should still "win"
            // if it is above the required threshold.
            script.suggestPhoneTimeZone(zonePhone2Suggestion);
            if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(zonePhone1Suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedZonePhone1ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedZonePhone2ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
            // Phone 1 should always beat phone 2, all other things being equal.
            assertEquals(expectedZonePhone1ScoredSuggestion,
                    mTimeZoneDetectionService.findBestSuggestionForTests());

            // Withdrawing phone 1's suggestion should leave phone 2 as the new winner. Since the
            // zoneId is different, the time zone setting should be updated.
            script.suggestPhoneTimeZone(emptyPhone1Suggestion);
            if (testCase.expectedScore >= SCORE_USAGE_THRESHOLD) {
                script.verifyTimeZoneSetAndReset(zonePhone2Suggestion);
            } else {
                script.verifyTimeZoneNotSet();
            }

            // Assert internal service state.
            assertEquals(expectedEmptyPhone1ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedZonePhone2ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
            assertEquals(expectedZonePhone2ScoredSuggestion,
                    mTimeZoneDetectionService.findBestSuggestionForTests());

            // Reset the state for the next loop.
            script.suggestPhoneTimeZone(emptyPhone2Suggestion)
                    .verifyTimeZoneNotSet();
            assertEquals(expectedEmptyPhone1ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE1_ID));
            assertEquals(expectedEmptyPhone2ScoredSuggestion,
                    mTimeZoneDetectionService.getLatestPhoneSuggestion(PHONE2_ID));
        }
    }

    /**
     * The {@link TimeZoneDetectionService.Helper} is left to detect whether changing the the time
     * zone is actually necessary. This test proves that the service doesn't assume it knows the
     * current setting.
     */
    @Test
    public void testTimeZoneDetectionServiceDoesNotAssumeCurrentSetting() {
        Script script = new Script()
                .initializeTimeZoneDetectionEnabled(true);

        SuggestionTestCase testCase =
                newTestCase(NETWORK_COUNTRY_AND_OFFSET, SINGLE_ZONE, SCORE_HIGH);
        PhoneTimeZoneSuggestion losAngelesSuggestion =
                testCase.createSuggestion(PHONE1_ID, "America/Los_Angeles");
        PhoneTimeZoneSuggestion newYorkSuggestion =
                testCase.createSuggestion(PHONE1_ID, "America/New_York");

        // Initialization.
        script.suggestPhoneTimeZone(losAngelesSuggestion)
                .verifyTimeZoneSetAndReset(losAngelesSuggestion);
        // Suggest it again - it should be set.
        script.suggestPhoneTimeZone(losAngelesSuggestion)
                .verifyTimeZoneSetAndReset(losAngelesSuggestion);

        // Toggling time zone detection should set it.
        script.timeZoneDetectionEnabled(false)
                .timeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(losAngelesSuggestion);

        // Simulate a user turning detection off, a new suggestion being made, and the user turning
        // it on again.
        script.timeZoneDetectionEnabled(false)
                .suggestPhoneTimeZone(newYorkSuggestion)
                .verifyTimeZoneNotSet();
        // Latest suggestion should be used.
        script.timeZoneDetectionEnabled(true)
                .verifyTimeZoneSetAndReset(newYorkSuggestion);
    }

    private static PhoneTimeZoneSuggestion createEmptyPhone1Suggestion() {
        return new PhoneTimeZoneSuggestion(PHONE1_ID);
    }

    private static PhoneTimeZoneSuggestion createEmptyPhone2Suggestion() {
        return new PhoneTimeZoneSuggestion(PHONE2_ID);
    }

    class FakeTimeZoneDetectionServiceHelper implements TimeZoneDetectionService.Helper {

        private Listener mListener;
        private boolean mTimeZoneDetectionEnabled;
        private boolean mTimeZoneInitialized = false;
        private TestState<PhoneTimeZoneSuggestion> mTimeZoneSuggestion = new TestState<>();

        @Override
        public void setListener(Listener listener) {
            this.mListener = listener;
        }

        @Override
        public boolean isTimeZoneDetectionEnabled() {
            return mTimeZoneDetectionEnabled;
        }

        @Override
        public boolean isTimeZoneSettingInitialized() {
            return mTimeZoneInitialized;
        }

        @Override
        public void setDeviceTimeZoneFromSuggestion(PhoneTimeZoneSuggestion timeZoneSuggestion) {
            mTimeZoneInitialized = true;
            mTimeZoneSuggestion.set(timeZoneSuggestion);
        }

        @Override
        public void dumpState(PrintWriter pw) {
            // No-op for fake
        }

        @Override
        public void dumpLogs(IndentingPrintWriter ipw) {
            // No-op for fake
        }

        void initializeTimeZoneDetectionEnabled(boolean enabled) {
            mTimeZoneDetectionEnabled = enabled;
        }

        void initializeTimeZone(boolean initialized) {
            mTimeZoneInitialized = initialized;
        }

        void simulateTimeZoneDetectionEnabled(boolean enabled) {
            mTimeZoneDetectionEnabled = enabled;
            mListener.onTimeZoneDetectionChange(enabled);
        }

        void assertTimeZoneNotSet() {
            mTimeZoneSuggestion.assertHasNotBeenSet();
        }

        void assertTimeZoneSuggested(PhoneTimeZoneSuggestion timeZoneSuggestion) {
            mTimeZoneSuggestion.assertHasBeenSet();
            mTimeZoneSuggestion.assertChangeCount(1);
            mTimeZoneSuggestion.assertLatestEquals(timeZoneSuggestion);
        }

        void commitAllChanges() {
            mTimeZoneSuggestion.commitLatest();
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

    /**
     * A "fluent" class allows reuse of code in tests: initialization, simulation and verification
     * logic.
     */
    private class Script {

        Script initializeTimeZoneDetectionEnabled(boolean enabled) {
            mFakeTimeZoneDetectionServiceHelper.initializeTimeZoneDetectionEnabled(enabled);
            return this;
        }

        Script initializeTimeZoneSetting(boolean initialized) {
            mFakeTimeZoneDetectionServiceHelper.initializeTimeZone(initialized);
            return this;
        }

        Script timeZoneDetectionEnabled(boolean timeZoneDetectionEnabled) {
            mFakeTimeZoneDetectionServiceHelper.simulateTimeZoneDetectionEnabled(
                    timeZoneDetectionEnabled);
            return this;
        }

        /** Simulates the time zone detection service receiving a phone-originated suggestion. */
        Script suggestPhoneTimeZone(PhoneTimeZoneSuggestion phoneTimeZoneSuggestion) {
            mTimeZoneDetectionService.suggestPhoneTimeZone(phoneTimeZoneSuggestion);
            return this;
        }

        Script verifyTimeZoneNotSet() {
            mFakeTimeZoneDetectionServiceHelper.assertTimeZoneNotSet();
            return this;
        }

        Script verifyTimeZoneSetAndReset(PhoneTimeZoneSuggestion timeZoneSuggestion) {
            mFakeTimeZoneDetectionServiceHelper.assertTimeZoneSuggested(timeZoneSuggestion);
            mFakeTimeZoneDetectionServiceHelper.commitAllChanges();
            return this;
        }

        Script resetState() {
            mFakeTimeZoneDetectionServiceHelper.commitAllChanges();
            return this;
        }
    }

    private static class SuggestionTestCase {
        public final int matchType;
        public final int quality;
        public final int expectedScore;

        SuggestionTestCase(int matchType, int quality, int expectedScore) {
            this.matchType = matchType;
            this.quality = quality;
            this.expectedScore = expectedScore;
        }

        private PhoneTimeZoneSuggestion createSuggestion(int phoneId, String zoneId) {
            PhoneTimeZoneSuggestion suggestion = new PhoneTimeZoneSuggestion(phoneId);
            suggestion.setZoneId(zoneId);
            suggestion.setMatchType(matchType);
            suggestion.setQuality(quality);
            return suggestion;
        }
    }

    private static SuggestionTestCase newTestCase(
            @MatchType int matchType, @Quality int quality, int expectedScore) {
        return new SuggestionTestCase(matchType, quality, expectedScore);
    }
}
