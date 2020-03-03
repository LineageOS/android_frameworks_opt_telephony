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

import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.ARBITRARY_DEBUG_INFO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.ARBITRARY_SYSTEM_CLOCK_TIME;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.CZECHIA_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.CZECHIA_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_DEFAULT_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NEW_ZEALAND_OTHER_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NON_UNIQUE_US_ZONE_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.NON_UNIQUE_US_ZONE_SCENARIO_ZONES;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO1;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO2;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.UNITED_KINGDOM_SCENARIO;
import static com.android.internal.telephony.nitz.NitzStateMachineTestSupport.US_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.nitz.TimeZoneLookupHelper.CountryResult.QUALITY_DEFAULT_BOOSTED;
import static com.android.internal.telephony.nitz.TimeZoneLookupHelper.CountryResult.QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS;
import static com.android.internal.telephony.nitz.TimeZoneLookupHelper.CountryResult.QUALITY_SINGLE_ZONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.timezone.CountryTimeZones.OffsetResult;

import com.android.internal.telephony.nitz.TimeZoneLookupHelper.CountryResult;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NitzStateMachineTestSupportTest {

    private TimeZoneLookupHelper mTimeZoneLookupHelper;

    @Before
    public void setUp() throws Exception {
        // In tests we use the real TimeZoneLookupHelper implementation.
        mTimeZoneLookupHelper = new TimeZoneLookupHelper();
    }

    @Test
    public void test_uniqueUs_assumptions() {
        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // quality == QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS, therefore the country's default zone
        // shouldn't be considered a good match.
        CountryResult expectedCountryLookupResult = new CountryResult(
                US_COUNTRY_DEFAULT_ZONE_ID, QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS,
                ARBITRARY_DEBUG_INFO);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        UNIQUE_US_ZONE_SCENARIO1.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // isOnlyMatch == true, so the combination of country + NITZ should be enough for a match.
        {
            OffsetResult expectedLookupResult = new OffsetResult(
                    UNIQUE_US_ZONE_SCENARIO1.getTimeZone(), true /* isOnlyMatch */);
            OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                    UNIQUE_US_ZONE_SCENARIO1.createNitzData(),
                    UNIQUE_US_ZONE_SCENARIO1.getNetworkCountryIsoCode());
            assertEquals(expectedLookupResult, actualLookupResult);
        }

        // isOnlyMatch == true, so the combination of country + NITZ should be enough for a match.
        {
            OffsetResult expectedLookupResult = new OffsetResult(
                    UNIQUE_US_ZONE_SCENARIO2.getTimeZone(), true /* isOnlyMatch */);
            OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                    UNIQUE_US_ZONE_SCENARIO2.createNitzData(),
                    UNIQUE_US_ZONE_SCENARIO2.getNetworkCountryIsoCode());
            assertEquals(expectedLookupResult, actualLookupResult);
        }
    }

    @Test
    public void test_nonUniqueUs_assumptions() {
        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // quality == QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS, therefore the country's default zone
        // shouldn't be considered a good match.
        CountryResult expectedCountryLookupResult = new CountryResult(
                US_COUNTRY_DEFAULT_ZONE_ID, QUALITY_MULTIPLE_ZONES_DIFFERENT_OFFSETS,
                ARBITRARY_DEBUG_INFO);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        NON_UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // By definition, there are multiple matching zones for the NON_UNIQUE_US_ZONE_SCENARIO.
        {
            OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                    NON_UNIQUE_US_ZONE_SCENARIO.createNitzData(),
                    NON_UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode());
            List<String> possibleZones = Arrays.asList(NON_UNIQUE_US_ZONE_SCENARIO_ZONES);
            assertTrue(possibleZones.contains(actualLookupResult.getTimeZone().getID()));
            assertFalse(actualLookupResult.isOnlyMatch());
        }
    }

    @Test
    public void test_unitedKingdom_assumptions() {
        assertEquals(UNITED_KINGDOM_SCENARIO.getTimeZone().getID(),
                UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID);

        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // quality == QUALITY_SINGLE_ZONE, so the default zone is a good match.
        CountryResult expectedCountryLookupResult = new CountryResult(
                UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID, QUALITY_SINGLE_ZONE, ARBITRARY_DEBUG_INFO);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // isOnlyMatch == true, so the combination of country + NITZ should be enough for a match.
        OffsetResult expectedLookupResult = new OffsetResult(
                UNITED_KINGDOM_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                UNITED_KINGDOM_SCENARIO.createNitzData(),
                UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }

    @Test
    public void test_newZealand_assumptions() {
        assertEquals(NEW_ZEALAND_DEFAULT_SCENARIO.getTimeZone().getID(),
                NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID);

        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // quality == QUALITY_DEFAULT_BOOSTED, so the default zone is a good match.
        CountryResult expectedCountryLookupResult = new CountryResult(
                NEW_ZEALAND_COUNTRY_DEFAULT_ZONE_ID, QUALITY_DEFAULT_BOOSTED, ARBITRARY_DEBUG_INFO);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        NEW_ZEALAND_DEFAULT_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // Check NEW_ZEALAND_DEFAULT_SCENARIO.
        {
            // isOnlyMatch == true, so the combination of country + NITZ should be enough for a
            // match.
            OffsetResult expectedLookupResult = new OffsetResult(
                    NEW_ZEALAND_DEFAULT_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
            OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                    NEW_ZEALAND_DEFAULT_SCENARIO.createNitzData(),
                    NEW_ZEALAND_DEFAULT_SCENARIO.getNetworkCountryIsoCode());
            assertEquals(expectedLookupResult, actualLookupResult);
        }

        // Check NEW_ZEALAND_OTHER_SCENARIO.
        {
            // isOnlyMatch == true, so the combination of country + NITZ should be enough for a
            // match.
            OffsetResult expectedLookupResult = new OffsetResult(
                    NEW_ZEALAND_OTHER_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
            OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                    NEW_ZEALAND_OTHER_SCENARIO.createNitzData(),
                    NEW_ZEALAND_OTHER_SCENARIO.getNetworkCountryIsoCode());
            assertEquals(expectedLookupResult, actualLookupResult);
        }
    }

    @Test
    public void test_czechia_assumptions() {
        assertEquals(CZECHIA_SCENARIO.getTimeZone().getID(), CZECHIA_COUNTRY_DEFAULT_ZONE_ID);

        // quality == QUALITY_SINGLE_ZONE, so the default zone is a good match.
        CountryResult expectedCountryLookupResult = new CountryResult(
                CZECHIA_COUNTRY_DEFAULT_ZONE_ID, QUALITY_SINGLE_ZONE, ARBITRARY_DEBUG_INFO);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        CZECHIA_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // isOnlyMatch == true, so the combination of country + NITZ should be enough for a match.
        OffsetResult expectedLookupResult = new OffsetResult(
                CZECHIA_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                CZECHIA_SCENARIO.createNitzData(),
                CZECHIA_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }
}
