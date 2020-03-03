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

import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_EMULATOR_ZONE_ID;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_NETWORK_COUNTRY_ONLY;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET;
import static android.app.timezonedetector.TelephonyTimeZoneSuggestion.QUALITY_SINGLE_ZONE;

import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.ARBITRARY_REALTIME_MILLIS;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.CZECHIA_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_DEFAULT_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_OTHER_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NON_UNIQUE_US_ZONE_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NON_UNIQUE_US_ZONE_SCENARIO_ZONES;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO2;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNITED_KINGDOM_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.US_COUNTRY_DEFAULT_ZONE_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.timezonedetector.TelephonyTimeZoneSuggestion;
import android.os.TimestampedValue;

import com.android.internal.telephony.NitzData;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nitz.NitzStateMachineImpl.TimeZoneSuggester;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.FakeDeviceState;
import com.android.internal.telephony.nitz.NitzStateMachineTestSupport.Scenario;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TimeZoneSuggesterImplTest extends TelephonyTest {

    private static final int SLOT_INDEX = 99999;
    private static final TelephonyTimeZoneSuggestion EMPTY_TIME_ZONE_SUGGESTION =
            new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX).build();

    private FakeDeviceState mFakeDeviceState;
    private TimeZoneSuggester mTimeZoneSuggester;

    @Before
    public void setUp() throws Exception {
        TelephonyTest.logd("TimeZoneSuggesterImplTest +Setup!");
        super.setUp("TimeZoneSuggesterImplTest");

        // In tests a fake impl is used for DeviceState, which allows historic data to be used.
        mFakeDeviceState = new FakeDeviceState();

        // In tests the real TimeZoneLookupHelper implementation is used: this makes it easy to
        // construct tests using known historic examples.
        TimeZoneLookupHelper timeZoneLookupHelper = new TimeZoneLookupHelper();
        mTimeZoneSuggester = new TimeZoneSuggesterImpl(mFakeDeviceState, timeZoneLookupHelper);

        TelephonyTest.logd("TimeZoneSuggesterImplTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test_emptySuggestionForNullCountryNullNitz() throws Exception {
        assertEquals(EMPTY_TIME_ZONE_SUGGESTION,
                mTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, null /* countryIsoCode */, null /* nitzSignal */));
    }

    @Test
    public void test_emptySuggestionForNullCountryWithNitz() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
        TimestampedValue<NitzData> nitzSignal =
                scenario.createNitzSignal(ARBITRARY_REALTIME_MILLIS);
        assertEquals(EMPTY_TIME_ZONE_SUGGESTION,
                mTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, null /* countryIsoCode */, nitzSignal));
    }

    @Test
    public void test_emptySuggestionForEmptyCountryNullNitz() throws Exception {
        assertEquals(EMPTY_TIME_ZONE_SUGGESTION,
                mTimeZoneSuggester.getTimeZoneSuggestion(
                        SLOT_INDEX, "" /* countryIsoCoe */, null /* nitzSignal */));
    }

    /**
     * Tests behavior for various scenarios for a user in the US. The US is a complicated case
     * with multiple time zones, some overlapping and with no good default. The scenario used here
     * is a "unique" scenario, meaning it is possible to determine the correct zone using both
     * country and NITZ information.
     */
    @Test
    public void test_uniqueUsZone() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;

        // Country won't be enough to get a quality result for time zone detection but a suggestion
        // will be made.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(US_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ with a "" country code is interpreted as a test network so only offset is used
        // to get a match.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, "" /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(SLOT_INDEX, actualSuggestion.getSlotIndex());
            assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY, actualSuggestion.getMatchType());
            assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, actualSuggestion.getQuality());
        }

        // NITZ alone is not enough to get a result when the country is not available.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, null /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }

        // Country + NITZ is enough for a unique time zone detection result for this scenario.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // Country + NITZ with a bad offset should not trigger fall back, country-only behavior
        // since there are multiple zones to choose from.
        {
            // We use an NITZ from CZ to generate an NITZ signal with a bad offset.
            TimestampedValue<NitzData> badNitzSignal =
                    CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion = EMPTY_TIME_ZONE_SUGGESTION;
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    badNitzSignal);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }
    }

    /**
     * Tests behavior for various scenarios for a user in the US. The US is a complicated case
     * with multiple time zones, some overlapping and with no good default. The scenario used here
     * is a "non unique" scenario, meaning it is not possible to determine the a single zone using
     * both country and NITZ information.
     */
    @Test
    public void test_nonUniqueUsZone() throws Exception {
        Scenario scenario = NON_UNIQUE_US_ZONE_SCENARIO;

        // Country won't be enough to get a quality result for time zone detection but a suggestion
        // will be made.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(US_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ with a "" country code is interpreted as a test network so only offset is used
        // to get a match.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, "" /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(SLOT_INDEX, actualSuggestion.getSlotIndex());
            assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY, actualSuggestion.getMatchType());
            assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, actualSuggestion.getQuality());
        }

        // NITZ alone is not enough to get a result when the country is not available.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, null /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }

        // Country + NITZ is not enough for a unique time zone detection result for this scenario.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(SLOT_INDEX, actualSuggestion.getSlotIndex());
            assertEquals(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET, actualSuggestion.getMatchType());
            assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, actualSuggestion.getQuality());
            List<String> allowedZoneIds = Arrays.asList(NON_UNIQUE_US_ZONE_SCENARIO_ZONES);
            assertTrue(allowedZoneIds.contains(actualSuggestion.getZoneId()));
        }

        // Country + NITZ with a bad offset should not trigger fall back, country-only behavior
        // since there are multiple zones to choose from.
        {
            // We use an NITZ from CZ to generate an NITZ signal with a bad offset.
            TimestampedValue<NitzData> badNitzSignal =
                    CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion = EMPTY_TIME_ZONE_SUGGESTION;
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    badNitzSignal);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }
    }

    /**
     * Tests behavior for various scenarios for a user in the UK. The UK is simple: it has a single
     * time zone so only the country needs to be known to find a time zone. It is special in that
     * it uses UTC for some of the year, which makes it difficult to detect bogus NITZ signals with
     * zero'd offset information.
     */
    @Test
    public void test_unitedKingdom() throws Exception {
        Scenario scenario = UNITED_KINGDOM_SCENARIO;

        // Country alone is enough to guess the time zone.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ with a "" country code is interpreted as a test network so only offset is used
        // to get a match.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, "" /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(SLOT_INDEX, actualSuggestion.getSlotIndex());
            assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY, actualSuggestion.getMatchType());
            assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, actualSuggestion.getQuality());

        }

        // NITZ alone is not enough to get a result when the country is not available.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, null /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }

        // Country + NITZ is enough for both time + time zone detection.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // Country + NITZ with a bad offset should trigger fall back, country-only behavior since
        // there's only one zone.
        {
            // We use an NITZ from Czechia to generate an NITZ signal with a bad offset.
            TimestampedValue<NitzData> badNitzSignal =
                    CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    badNitzSignal);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }
    }

    /**
     * Tests behavior for various scenarios for a user in Czechia. CZ is simple: it has a single
     * time zone so only the country needs to be known to find a time zone. It never uses UTC so it
     * is useful to contrast with the UK and can be used for bogus signal detection.
     */
    @Test
    public void test_cz() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;

        // Country alone is enough to guess the time zone.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ with a "" country code is interpreted as a test network so only offset is used
        // to get a match.
        {
            TelephonyTimeZoneSuggestion actualSuggestion =
                    mTimeZoneSuggester.getTimeZoneSuggestion(
                            SLOT_INDEX, "" /* countryIsoCode */,
                            scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(SLOT_INDEX, actualSuggestion.getSlotIndex());
            assertEquals(MATCH_TYPE_TEST_NETWORK_OFFSET_ONLY, actualSuggestion.getMatchType());
            assertEquals(QUALITY_MULTIPLE_ZONES_WITH_SAME_OFFSET, actualSuggestion.getQuality());

        }

        // NITZ alone is not enough to get a result when the country is not available.
        {
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, null /* countryIsoCode */,
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }

        // Country + NITZ is enough for both time + time zone detection.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime()));
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // Country + NITZ with a bad offset should trigger fall back, country-only behavior since
        // there's only one zone.
        {
            // We use an NITZ from the US to generate an NITZ signal with a bad offset.
            TimestampedValue<NitzData> badNitzSignal =
                    UNIQUE_US_ZONE_SCENARIO1.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(),
                    badNitzSignal);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }
    }

    @Test
    public void test_bogusCzNitzSignal() throws Exception {
        Scenario scenario = CZECHIA_SCENARIO;

        // Country alone is enough to guess the time zone.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ + bogus NITZ is not enough to get a result.
        {
            // Create a corrupted NITZ signal, where the offset information has been lost.
            TimestampedValue<NitzData> goodNitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            NitzData bogusNitzData = NitzData.createForTests(
                    0 /* UTC! */, null /* dstOffsetMillis */,
                    goodNitzSignal.getValue().getCurrentTimeInMillis(),
                    null /* emulatorHostTimeZone */);
            TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                    goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), badNitzSignal);
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }
    }

    @Test
    public void test_bogusUniqueUsNitzSignal() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;

        // Country alone is not enough to guess the time zone.
        {
            TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(US_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
        }

        // NITZ + bogus NITZ is not enough to get a result.
        {
            // Create a corrupted NITZ signal, where the offset information has been lost.
            TimestampedValue<NitzData> goodNitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            NitzData bogusNitzData = NitzData.createForTests(
                    0 /* UTC! */, null /* dstOffsetMillis */,
                    goodNitzSignal.getValue().getCurrentTimeInMillis(),
                    null /* emulatorHostTimeZone */);
            TimestampedValue<NitzData> badNitzSignal = new TimestampedValue<>(
                    goodNitzSignal.getReferenceTimeMillis(), bogusNitzData);

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), badNitzSignal);
            assertEquals(EMPTY_TIME_ZONE_SUGGESTION, actualSuggestion);
        }
    }

    @Test
    public void test_emulatorNitzExtensionUsedForTimeZone() throws Exception {
        Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;

        TimestampedValue<NitzData> originalNitzSignal =
                scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());

        // Create an NITZ signal with an explicit time zone (as can happen on emulators).
        NitzData originalNitzData = originalNitzSignal.getValue();

        // A time zone that is obviously not in the US, but because the explicit value is present it
        // should not be questioned.
        String emulatorTimeZoneId = "Europe/London";
        NitzData emulatorNitzData = NitzData.createForTests(
                originalNitzData.getLocalOffsetMillis(),
                originalNitzData.getDstAdjustmentMillis(),
                originalNitzData.getCurrentTimeInMillis(),
                java.util.TimeZone.getTimeZone(emulatorTimeZoneId) /* emulatorHostTimeZone */);
        TimestampedValue<NitzData> emulatorNitzSignal = new TimestampedValue<>(
                originalNitzSignal.getReferenceTimeMillis(), emulatorNitzData);

        TelephonyTimeZoneSuggestion expectedTimeZoneSuggestion =
                new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                        .setZoneId(emulatorTimeZoneId)
                        .setMatchType(MATCH_TYPE_EMULATOR_ZONE_ID)
                        .setQuality(QUALITY_SINGLE_ZONE)
                        .build();

        TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                SLOT_INDEX, scenario.getNetworkCountryIsoCode(), emulatorNitzSignal);
        assertEquals(expectedTimeZoneSuggestion, actualSuggestion);
    }

    @Test
    public void test_countryDefaultBoost() throws Exception {
        // Demonstrate the defaultTimeZoneBoost behavior: we can get a zone only from the
        // countryIsoCode.
        {
            Scenario scenario = NEW_ZEALAND_DEFAULT_SCENARIO;
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // Confirm what happens when NITZ is correct for the country default.
        {
            Scenario scenario = NEW_ZEALAND_DEFAULT_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // A valid NITZ signal for the non-default zone should still be correctly detected.
        {
            Scenario scenario = NEW_ZEALAND_OTHER_SCENARIO;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // Demonstrate what happens with a bogus NITZ for NZ: because the default zone is boosted
        // then we should return to the country default zone.
        {
            Scenario scenario = NEW_ZEALAND_DEFAULT_SCENARIO;
            // Use a scenario that has a different offset than NZ to generate the NITZ signal.
            TimestampedValue<NitzData> nitzSignal =
                    CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }
    }

    @Test
    public void test_noCountryDefaultBoost() throws Exception {
        // Demonstrate the behavior without default country boost for a country with multiple zones:
        // we cannot get a zone only from the countryIsoCode.
        {
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(US_COUNTRY_DEFAULT_ZONE_ID)
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_ONLY)
                            .setQuality(QUALITY_MULTIPLE_ZONES_WITH_DIFFERENT_OFFSETS)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), null /* nitzSignal */);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // Confirm what happens when NITZ is correct for the country default.
        {
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // A valid NITZ signal for the non-default zone should still be correctly detected.
        {
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO2;
            TimestampedValue<NitzData> nitzSignal =
                    scenario.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion =
                    new TelephonyTimeZoneSuggestion.Builder(SLOT_INDEX)
                            .setZoneId(scenario.getTimeZoneId())
                            .setMatchType(MATCH_TYPE_NETWORK_COUNTRY_AND_OFFSET)
                            .setQuality(QUALITY_SINGLE_ZONE)
                            .build();

            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }

        // Demonstrate what happens with a bogus NITZ for US: because the default zone is not
        // boosted we should not get a suggestion.
        {
            // A scenario that has a different offset than US.
            Scenario scenario = UNIQUE_US_ZONE_SCENARIO1;
            // Use a scenario that has a different offset than the US to generate the NITZ signal.
            TimestampedValue<NitzData> nitzSignal =
                    CZECHIA_SCENARIO.createNitzSignal(mFakeDeviceState.elapsedRealtime());
            TelephonyTimeZoneSuggestion expectedSuggestion = EMPTY_TIME_ZONE_SUGGESTION;
            TelephonyTimeZoneSuggestion actualSuggestion = mTimeZoneSuggester.getTimeZoneSuggestion(
                    SLOT_INDEX, scenario.getNetworkCountryIsoCode(), nitzSignal);
            assertEquals(expectedSuggestion, actualSuggestion);
        }
    }
}
