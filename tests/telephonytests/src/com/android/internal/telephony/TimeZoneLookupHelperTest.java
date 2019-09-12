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

import com.android.internal.telephony.TimeZoneLookupHelper.CountryResult;
import com.android.internal.telephony.TimeZoneLookupHelper.OffsetResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TimeZoneLookupHelperTest {
    // Note: Historical dates are used to avoid the test breaking due to data changes.
    /* Arbitrary summer date in the Northern hemisphere. */
    private static final long NH_SUMMER_TIME_MILLIS = createUtcTime(2015, 6, 20, 1, 2, 3);
    /* Arbitrary winter date in the Northern hemisphere. */
    private static final long NH_WINTER_TIME_MILLIS = createUtcTime(2015, 1, 20, 1, 2, 3);

    private TimeZoneLookupHelper mTimeZoneLookupHelper;

    @Before
    public void setUp() {
        mTimeZoneLookupHelper = new TimeZoneLookupHelper();
    }

    @Test
    public void testLookupByNitzByNitz() {
        // Historical dates are used to avoid the test breaking due to data changes.
        // However, algorithm updates may change the exact time zone returned, though it shouldn't
        // ever be a less exact match.
        long nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);

        String nhSummerTimeString = "15/06/20,01:02:03";
        String nhWinterTimeString = "15/01/20,01:02:03";

        // Tests for London, UK.
        {
            String lonSummerTimeString = nhSummerTimeString + "+4";
            int lonSummerOffsetMillis = (int) TimeUnit.HOURS.toMillis(1);
            int lonSummerDstOffsetMillis = (int) TimeUnit.HOURS.toMillis(1);

            String lonWinterTimeString = nhWinterTimeString + "+0";
            int lonWinterOffsetMillis = 0;
            int lonWinterDstOffsetMillis = 0;

            OffsetResult lookupResult;

            // Summer, known DST state (DST == true).
            NitzData lonSummerNitzDataWithOffset = NitzData.parse(lonSummerTimeString + ",4");
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(lonSummerNitzDataWithOffset);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, lonSummerOffsetMillis,
                    lonSummerDstOffsetMillis, lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Winter, known DST state (DST == false).
            NitzData lonWinterNitzDataWithOffset = NitzData.parse(lonWinterTimeString + ",0");
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(lonWinterNitzDataWithOffset);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, lonWinterOffsetMillis,
                    lonWinterDstOffsetMillis, lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Summer, unknown DST state
            NitzData lonSummerNitzDataWithoutOffset = NitzData.parse(lonSummerTimeString);
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(lonSummerNitzDataWithoutOffset);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, lonSummerOffsetMillis, null,
                    lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Winter, unknown DST state
            NitzData lonWinterNitzDataWithoutOffset = NitzData.parse(lonWinterTimeString);
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(lonWinterNitzDataWithoutOffset);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, lonWinterOffsetMillis, null,
                    lookupResult);
            assertOffsetResultMetadata(false, lookupResult);
        }

        // Tests for Mountain View, CA, US.
        {
            String mtvSummerTimeString = nhSummerTimeString + "-32";
            int mtvSummerOffsetMillis = (int) TimeUnit.HOURS.toMillis(-8);
            int mtvSummerDstOffsetMillis = (int) TimeUnit.HOURS.toMillis(1);

            String mtvWinterTimeString = nhWinterTimeString + "-28";
            int mtvWinterOffsetMillis = (int) TimeUnit.HOURS.toMillis(-7);
            int mtvWinterDstOffsetMillis = 0;

            OffsetResult lookupResult;

            // Summer, known DST state (DST == true).
            NitzData mtvSummerNitzDataWithOffset = NitzData.parse(mtvSummerTimeString + ",4");
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(mtvSummerNitzDataWithOffset);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, mtvSummerOffsetMillis,
                    mtvSummerDstOffsetMillis, lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Winter, known DST state (DST == false).
            NitzData mtvWinterNitzDataWithOffset = NitzData.parse(mtvWinterTimeString + ",0");
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(mtvWinterNitzDataWithOffset);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, mtvWinterOffsetMillis,
                    mtvWinterDstOffsetMillis, lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Summer, unknown DST state
            NitzData mtvSummerNitzDataWithoutOffset = NitzData.parse(mtvSummerTimeString);
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(mtvSummerNitzDataWithoutOffset);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, mtvSummerOffsetMillis, null,
                    lookupResult);
            assertOffsetResultMetadata(false, lookupResult);

            // Winter, unknown DST state
            NitzData mtvWinterNitzDataWithoutOffset = NitzData.parse(mtvWinterTimeString);
            lookupResult = mTimeZoneLookupHelper.lookupByNitz(mtvWinterNitzDataWithoutOffset);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, mtvWinterOffsetMillis, null,
                    lookupResult);
            assertOffsetResultMetadata(false, lookupResult);
        }
    }

    @Test
    public void testLookupByNitzCountry_filterByEffectiveDate() {
        // America/North_Dakota/Beulah was on Mountain Time until 2010-11-07, when it switched to
        // Central Time.
        String usIso = "US";

        // Try MDT / -6 hours in summer before America/North_Dakota/Beulah switched to Central Time.
        {
            String nitzString = "10/11/05,00:00:00-24,1"; // 2010-11-05 00:00:00 UTC, UTC-6, DST
            NitzData nitzData = NitzData.parse(nitzString);
            // The zone chosen is a side effect of zone ordering in the data files so we just check
            // the isOnlyMatch value.
            OffsetResult offsetResult = mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, usIso);
            assertFalse(offsetResult.getIsOnlyMatch());
        }

        // Try MDT / -6 hours in summer after America/North_Dakota/Beulah switched to central time.
        {
            String nitzString = "11/11/05,00:00:00-24,1"; // 2011-11-05 00:00:00 UTC, UTC-6, DST
            NitzData nitzData = NitzData.parse(nitzString);
            OffsetResult offsetResult = mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, usIso);
            assertTrue(offsetResult.getIsOnlyMatch());
        }
    }

    @Test
    public void testLookupByNitzCountry_multipleMatches() {
        // America/Denver & America/Phoenix share the same Mountain Standard Time offset (i.e.
        // during winter).
        String usIso = "US";

        // Try MDT for a recent summer date: No ambiguity here.
        {
            String nitzString = "15/06/01,00:00:00-24,1"; // 2015-06-01 00:00:00 UTC, UTC-6, DST
            NitzData nitzData = NitzData.parse(nitzString);
            OffsetResult offsetResult = mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, usIso);
            assertTrue(offsetResult.getIsOnlyMatch());
        }

        // Try MST for a recent summer date: No ambiguity here.
        {
            String nitzString = "15/06/01,00:00:00-28,0"; // 2015-06-01 00:00:00 UTC, UTC-7, not DST
            NitzData nitzData = NitzData.parse(nitzString);
            OffsetResult offsetResult = mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, usIso);
            assertTrue(offsetResult.getIsOnlyMatch());
        }

        // Try MST for a recent winter date: There are multiple zones to pick from because of the
        // America/Denver & America/Phoenix ambiguity.
        {
            String nitzString = "15/01/01,00:00:00-28,0"; // 2015-01-01 00:00:00 UTC, UTC-7, not DST
            NitzData nitzData = NitzData.parse(nitzString);
            OffsetResult offsetResult = mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, usIso);
            assertFalse(offsetResult.getIsOnlyMatch());
        }
    }

    @Test
    public void testLookupByNitzCountry_dstKnownAndUnknown() {
        // Historical dates are used to avoid the test breaking due to data changes.
        // However, algorithm updates may change the exact time zone returned, though it shouldn't
        // ever be a less exact match.
        long nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);

        // A country in the northern hemisphere with one time zone.
        String adIso = "AD"; // Andora
        String summerTimeNitzString = "15/06/20,01:02:03+8"; // 2015-06-20 01:02:03 UTC, UTC+2
        String winterTimeNitzString = "15/01/20,01:02:03+4"; // 2015-01-20 01:02:03 UTC, UTC+1

        // Summer, known & correct DST state (DST == true).
        {
            String summerTimeNitzStringWithDst = summerTimeNitzString + ",1";
            NitzData nitzData = NitzData.parse(summerTimeNitzStringWithDst);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(2);
            Integer expectedDstOffset = (int) TimeUnit.HOURS.toMillis(1);
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            OffsetResult adSummerWithDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            OffsetResult expectedResult =
                    new OffsetResult(zone("Europe/Andorra"), true /* isOnlyMatch */);
            assertEquals(expectedResult, adSummerWithDstResult);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, expectedUtcOffset, expectedDstOffset,
                    adSummerWithDstResult);
        }

        // Summer, known & incorrect DST state (DST == false)
        {
            String summerTimeNitzStringWithNoDst = summerTimeNitzString + ",0";
            NitzData nitzData = NitzData.parse(summerTimeNitzStringWithNoDst);

            OffsetResult adSummerWithNoDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            assertNull(adSummerWithNoDstResult);
        }

        // Winter, known & correct DST state (DST == false)
        {
            String winterTimeNitzStringWithNoDst = winterTimeNitzString + ",0";
            NitzData nitzData = NitzData.parse(winterTimeNitzStringWithNoDst);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(1);
            Integer expectedDstOffset = 0;
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            OffsetResult adWinterWithDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            OffsetResult expectedResult =
                    new OffsetResult(zone("Europe/Andorra"), true /* isOnlyMatch */);
            assertEquals(expectedResult, adWinterWithDstResult);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    adWinterWithDstResult);
        }

        // Winter, known & incorrect DST state (DST == true)
        {
            String winterTimeNitzStringWithDst = winterTimeNitzString + ",1";
            NitzData nitzData = NitzData.parse(winterTimeNitzStringWithDst);

            OffsetResult adWinterWithDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            assertNull(adWinterWithDstResult);
        }

        // Summer, unknown DST state (will match any DST state with the correct offset).
        {
            NitzData nitzData = NitzData.parse(summerTimeNitzString);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(2);
            Integer expectedDstOffset = null; // Unknown
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            OffsetResult adSummerUnknownDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            OffsetResult expectedResult =
                    new OffsetResult(zone("Europe/Andorra"), true /* isOnlyMatch */);
            assertEquals(expectedResult, adSummerUnknownDstResult);
            assertOffsetResultZoneOffsets(nhSummerTimeMillis, expectedUtcOffset, expectedDstOffset,
                    adSummerUnknownDstResult);
        }

        // Winter, unknown DST state (will match any DST state with the correct offset)
        {
            NitzData nitzData = NitzData.parse(winterTimeNitzString);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(1);
            Integer expectedDstOffset = null; // Unknown
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            OffsetResult adWinterUnknownDstResult =
                    mTimeZoneLookupHelper.lookupByNitzCountry(nitzData, adIso);
            OffsetResult expectedResult =
                    new OffsetResult(zone("Europe/Andorra"), true /* isOnlyMatch */);
            assertEquals(expectedResult, adWinterUnknownDstResult);
            assertOffsetResultZoneOffsets(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    adWinterUnknownDstResult);
        }
    }

    @Test
    public void testLookupByCountry_oneZone() {
        CountryResult expectedResult;

        // GB has one time zone.
        expectedResult = new CountryResult("Europe/London", false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, NH_SUMMER_TIME_MILLIS);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("gb", NH_SUMMER_TIME_MILLIS));
        expectedResult = new CountryResult("Europe/London", false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, NH_WINTER_TIME_MILLIS);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("gb", NH_WINTER_TIME_MILLIS));
    }

    @Test
    public void testLookupByCountry_oneEffectiveZone() {
        // Historical dates are used to avoid the test breaking due to data changes.

        // DE has two time zones according to IANA data: Europe/Berlin and Europe/Busingen, but they
        // become effectively identical after 338950800000 millis (Sun, 28 Sep 1980 01:00:00 GMT).
        // Android data tells us that Europe/Berlin the one that was "kept".
        long nhSummerTimeMillis = createUtcTime(1975, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(1975, 1, 20, 1, 2, 3);

        CountryResult expectedResult;

        expectedResult = new CountryResult("Europe/Berlin", true /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, nhSummerTimeMillis);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("de", nhSummerTimeMillis));
        expectedResult = new CountryResult("Europe/Berlin", true /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, nhWinterTimeMillis);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("de", nhWinterTimeMillis));

        // And in 2015, multipleZonesInCountry == false because Europe/Busingen became irrelevant
        // after 1980.
        nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);

        expectedResult = new CountryResult("Europe/Berlin", false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, nhSummerTimeMillis);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("de", nhSummerTimeMillis));
        expectedResult = new CountryResult("Europe/Berlin", false /* multipleZonesInCountry */,
                true /* allZonesHaveSameOffset */, nhWinterTimeMillis);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("de", nhWinterTimeMillis));
    }

    @Test
    public void testLookupByCountry_multipleZones() {
        CountryResult expectedResult;

        // US has many time zones that have different offsets.
        expectedResult = new CountryResult("America/New_York", true /* multipleZonesInCountry */,
                false /* allZonesHaveSameOffset */, NH_SUMMER_TIME_MILLIS);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("us", NH_SUMMER_TIME_MILLIS));
        expectedResult = new CountryResult("America/New_York", true /* multipleZonesInCountry */,
                false /* allZonesHaveSameOffset */, NH_WINTER_TIME_MILLIS);
        assertEquals(expectedResult,
                mTimeZoneLookupHelper.lookupByCountry("us", NH_WINTER_TIME_MILLIS));
    }

    @Test
    public void testLookupDefaultTimeZoneIdByCountry() {
        assertEquals("Europe/London", mTimeZoneLookupHelper.lookupDefaultTimeZoneIdByCountry("gb"));
        assertEquals("Europe/London", mTimeZoneLookupHelper.lookupDefaultTimeZoneIdByCountry("Gb"));
        assertEquals("Europe/London", mTimeZoneLookupHelper.lookupDefaultTimeZoneIdByCountry("GB"));
        assertEquals("Europe/Berlin", mTimeZoneLookupHelper.lookupDefaultTimeZoneIdByCountry("DE"));
    }

    @Test
    public void testCountryUsesUtc() {
        assertFalse(mTimeZoneLookupHelper.countryUsesUtc("us", NH_SUMMER_TIME_MILLIS));
        assertFalse(mTimeZoneLookupHelper.countryUsesUtc("us", NH_WINTER_TIME_MILLIS));
        assertFalse(mTimeZoneLookupHelper.countryUsesUtc("gb", NH_SUMMER_TIME_MILLIS));
        assertTrue(mTimeZoneLookupHelper.countryUsesUtc("gb", NH_WINTER_TIME_MILLIS));
    }

    /**
     * Assert the time zone in the OffsetResult has the expected properties at the specified time.
     */
    private static void assertOffsetResultZoneOffsets(long time, int expectedOffsetAtTime,
            Integer expectedDstAtTime, OffsetResult lookupResult) {

        TimeZone timeZone = lookupResult.getTimeZone();
        GregorianCalendar calendar = new GregorianCalendar(timeZone);
        calendar.setTimeInMillis(time);
        int actualOffsetAtTime =
                calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET);
        assertEquals(expectedOffsetAtTime, actualOffsetAtTime);

        if (expectedDstAtTime != null) {
            Date date = new Date(time);
            assertEquals(expectedDstAtTime > 0, timeZone.inDaylightTime(date));

            // The code under test assumes DST means +1 in all cases,
            // This code makes fewer assumptions.
            assertEquals(expectedDstAtTime.intValue(), calendar.get(Calendar.DST_OFFSET));
        }
    }

    private static void assertOffsetResultMetadata(boolean isOnlyMatch, OffsetResult lookupResult) {
        assertEquals(isOnlyMatch, lookupResult.getIsOnlyMatch());
    }

    private static long createUtcTime(
            int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minute, int second) {
        GregorianCalendar calendar = new GregorianCalendar(zone("UTC"));
        calendar.clear(); // Clear millis, etc.
        calendar.set(year, monthOfYear - 1, dayOfMonth, hourOfDay, minute, second);
        return calendar.getTimeInMillis();
    }

    private static TimeZone zone(String zoneId) {
        return TimeZone.getFrozenTimeZone(zoneId);
    }
}
