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

package com.android.internal.telephony;

import static com.android.internal.telephony.NitzStateMachineTestSupport.ARBITRARY_SYSTEM_CLOCK_TIME;
import static com.android.internal.telephony.NitzStateMachineTestSupport.CZECHIA_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.NitzStateMachineTestSupport.CZECHIA_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNIQUE_US_ZONE_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID;
import static com.android.internal.telephony.NitzStateMachineTestSupport.UNITED_KINGDOM_SCENARIO;
import static com.android.internal.telephony.NitzStateMachineTestSupport.US_COUNTRY_DEFAULT_ZONE_ID;

import static org.junit.Assert.assertEquals;

import com.android.internal.telephony.TimeZoneLookupHelper.CountryResult;
import com.android.internal.telephony.TimeZoneLookupHelper.OffsetResult;

import org.junit.Before;
import org.junit.Test;

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

        // allZonesHaveSameOffset == false, so the default zone isn't generally suitable.
        CountryResult expectedCountryLookupResult = new CountryResult(
                US_COUNTRY_DEFAULT_ZONE_ID,
                true /* multipleZonesInCountry */,
                false /* allZonesHaveSameOffset */,
                ARBITRARY_SYSTEM_CLOCK_TIME);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        // isOnlyMatch == true, so the combination of country + NITZ should be enough for a match.
        OffsetResult expectedLookupResult = new OffsetResult(
                UNIQUE_US_ZONE_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                UNIQUE_US_ZONE_SCENARIO.createNitzData(),
                UNIQUE_US_ZONE_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }

    @Test
    public void test_unitedKingdom_assumptions() {
        assertEquals(UNITED_KINGDOM_SCENARIO.getTimeZone().getID(),
                UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID);

        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // allZonesHaveSameOffset == true (not only that, there is only one zone), so we can pick
        // the zone knowing only the country.
        CountryResult expectedCountryLookupResult = new CountryResult(
                UNITED_KINGDOM_COUNTRY_DEFAULT_ZONE_ID,
                false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */,
                ARBITRARY_SYSTEM_CLOCK_TIME);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        OffsetResult expectedLookupResult = new OffsetResult(
                UNITED_KINGDOM_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                UNITED_KINGDOM_SCENARIO.createNitzData(),
                UNITED_KINGDOM_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }

    @Test
    public void test_cz_assumptions() {
        // Check we'll get the expected behavior from TimeZoneLookupHelper.

        // allZonesHaveSameOffset == true (not only that, there is only one zone), so we can pick
        // the zone knowing only the country.
        CountryResult expectedCountryLookupResult = new CountryResult(
                CZECHIA_COUNTRY_DEFAULT_ZONE_ID,
                false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */,
                ARBITRARY_SYSTEM_CLOCK_TIME);
        CountryResult actualCountryLookupResult =
                mTimeZoneLookupHelper.lookupByCountry(
                        CZECHIA_SCENARIO.getNetworkCountryIsoCode(),
                        ARBITRARY_SYSTEM_CLOCK_TIME);
        assertEquals(expectedCountryLookupResult, actualCountryLookupResult);

        OffsetResult expectedLookupResult = new OffsetResult(
                CZECHIA_SCENARIO.getTimeZone(), true /* isOnlyMatch */);
        OffsetResult actualLookupResult = mTimeZoneLookupHelper.lookupByNitzCountry(
                CZECHIA_SCENARIO.createNitzData(),
                CZECHIA_SCENARIO.getNetworkCountryIsoCode());
        assertEquals(expectedLookupResult, actualLookupResult);
    }
}
