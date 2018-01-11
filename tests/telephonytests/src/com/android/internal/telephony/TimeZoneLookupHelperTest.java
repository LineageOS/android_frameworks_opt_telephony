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

import libcore.util.TimeZoneFinder;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class TimeZoneLookupHelperTest {

    private TimeZoneLookupHelper mTimeZoneLookupHelper;

    @Before
    public void setUp() {
        mTimeZoneLookupHelper = new TimeZoneLookupHelper();
    }

    @Test
    public void testGuessZoneIdByNitz() {
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

            // Summer, known DST state (DST == true).
            NitzData lonSummerNitzDataWithOffset = NitzData.parse(lonSummerTimeString + ",4");
            assertTimeZoneId(nhSummerTimeMillis, lonSummerOffsetMillis, lonSummerDstOffsetMillis,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(lonSummerNitzDataWithOffset));

            // Winter, known DST state (DST == false).
            NitzData lonWinterNitzDataWithOffset = NitzData.parse(lonWinterTimeString + ",0");
            assertTimeZoneId(nhWinterTimeMillis, lonWinterOffsetMillis, lonWinterDstOffsetMillis,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(lonWinterNitzDataWithOffset));

            // Summer, unknown DST state
            NitzData lonSummerNitzDataWithoutOffset = NitzData.parse(lonSummerTimeString);
            assertTimeZoneId(nhSummerTimeMillis, lonSummerOffsetMillis, null,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(lonSummerNitzDataWithoutOffset));

            // Winter, unknown DST state
            NitzData lonWinterNitzDataWithoutOffset = NitzData.parse(lonWinterTimeString);
            assertTimeZoneId(nhWinterTimeMillis, lonWinterOffsetMillis, null,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(lonWinterNitzDataWithoutOffset));
        }

        // Tests for Mountain View, CA, US.
        {
            String mtvSummerTimeString = nhSummerTimeString + "-32";
            int mtvSummerOffsetMillis = (int) TimeUnit.HOURS.toMillis(-8);
            int mtvSummerDstOffsetMillis = (int) TimeUnit.HOURS.toMillis(1);

            String mtvWinterTimeString = nhWinterTimeString + "-28";
            int mtvWinterOffsetMillis = (int) TimeUnit.HOURS.toMillis(-7);
            int mtvWinterDstOffsetMillis = 0;

            // Summer, known DST state (DST == true).
            NitzData mtvSummerNitzDataWithOffset = NitzData.parse(mtvSummerTimeString + ",4");
            assertTimeZoneId(nhSummerTimeMillis, mtvSummerOffsetMillis, mtvSummerDstOffsetMillis,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(mtvSummerNitzDataWithOffset));

            // Winter, known DST state (DST == false).
            NitzData mtvWinterNitzDataWithOffset = NitzData.parse(mtvWinterTimeString + ",0");
            assertTimeZoneId(nhWinterTimeMillis, mtvWinterOffsetMillis, mtvWinterDstOffsetMillis,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(mtvWinterNitzDataWithOffset));

            // Summer, unknown DST state
            NitzData mtvSummerNitzDataWithoutOffset = NitzData.parse(mtvSummerTimeString);
            assertTimeZoneId(nhSummerTimeMillis, mtvSummerOffsetMillis, null,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(mtvSummerNitzDataWithoutOffset));

            // Winter, unknown DST state
            NitzData mtvWinterNitzDataWithoutOffset = NitzData.parse(mtvWinterTimeString);
            assertTimeZoneId(nhWinterTimeMillis, mtvWinterOffsetMillis, null,
                    mTimeZoneLookupHelper.guessZoneIdByNitz(mtvWinterNitzDataWithoutOffset));
        }
    }

    @Test
    public void testGuessZoneIdByNitzCountry() {
        // Historical dates are used to avoid the test breaking due to data changes.
        // However, algorithm updates may change the exact time zone returned, though it shouldn't
        // ever be a less exact match.
        long nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);

        // Two countries in the northern hemisphere that share the same Winter and Summer DST
        // offsets at the dates being used.
        String isoCountry1 = "DE"; // Germany
        String isoCountry2 = "ES"; // Spain
        String summerTimeNitzString = "15/06/20,01:02:03+8";
        String winterTimeNitzString = "15/01/20,01:02:03+4";

        // Summer, known DST state (DST == true).
        {
            String summerTimeNitzStringWithDst = summerTimeNitzString + ",4";
            NitzData nitzData = NitzData.parse(summerTimeNitzStringWithDst);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(2);
            Integer expectedDstOffset = (int) TimeUnit.HOURS.toMillis(1);
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            String country1SummerWithDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry1);
            assertTimeZoneId(nhSummerTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country1SummerWithDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry1, country1SummerWithDstResult);

            String country2SummerWithDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry2);
            assertTimeZoneId(nhSummerTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country2SummerWithDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry2, country2SummerWithDstResult);

            assertDifferentZoneIds(country1SummerWithDstResult, country2SummerWithDstResult);
        }

        // Winter, known DST state (DST == false)
        {
            String winterTimeNitzStringWithDst = winterTimeNitzString + ",0";
            NitzData nitzData = NitzData.parse(winterTimeNitzStringWithDst);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(1);
            Integer expectedDstOffset = 0;
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            String country1WinterWithDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry1);
            assertTimeZoneId(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country1WinterWithDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry1, country1WinterWithDstResult);

            String country2WinterWithDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry2);
            assertTimeZoneId(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country2WinterWithDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry2, country2WinterWithDstResult);

            assertDifferentZoneIds(country1WinterWithDstResult, country2WinterWithDstResult);
        }

        // Summer, unknown DST state
        // For historic reasons, GuessZoneIdByNitzCountry() does not handle unknown DST state - it
        // assumes that "unknown DST" means "no DST": This leads to no match when DST is actually in
        // force.
        {
            NitzData nitzData = NitzData.parse(summerTimeNitzString);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(2);
            Integer expectedDstOffset = null; // Unknown
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            String country1SummerUnknownDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry1);
            assertNull(country1SummerUnknownDstResult);

            String country2SummerUnknownDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry2);
            assertNull(country2SummerUnknownDstResult);
        }

        // Winter, unknown DST state
        {
            NitzData nitzData = NitzData.parse(winterTimeNitzString);
            int expectedUtcOffset = (int) TimeUnit.HOURS.toMillis(1);
            Integer expectedDstOffset = null; // Unknown
            assertEquals(expectedUtcOffset, nitzData.getLocalOffsetMillis());
            assertEquals(expectedDstOffset, nitzData.getDstAdjustmentMillis());

            String country1WinterUnknownDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry1);
            assertTimeZoneId(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country1WinterUnknownDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry1, country1WinterUnknownDstResult);

            String country2WinterUnknownDstResult =
                    mTimeZoneLookupHelper.guessZoneIdByNitzCountry(nitzData, isoCountry2);
            assertTimeZoneId(nhWinterTimeMillis, expectedUtcOffset, expectedDstOffset,
                    country2WinterUnknownDstResult);
            assertTimeZoneIdUsedInCountry(isoCountry2, country2WinterUnknownDstResult);

            assertDifferentZoneIds(country1WinterUnknownDstResult, country2WinterUnknownDstResult);
        }
    }

    @Test
    public void testGuessZoneIdByCountry() {
        // Historical dates are used to avoid the test breaking due to data changes.
        long nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);

        // GB has one time zone.
        assertEquals("Europe/London",
                mTimeZoneLookupHelper.guessZoneIdByCountry("gb", nhSummerTimeMillis));
        assertEquals("Europe/London",
                mTimeZoneLookupHelper.guessZoneIdByCountry("gb", nhWinterTimeMillis));

        // DE has two time zones according to data, but they agree on offset.
        assertEquals("Europe/Berlin",
                mTimeZoneLookupHelper.guessZoneIdByCountry("de", nhSummerTimeMillis));
        assertEquals("Europe/Berlin",
                mTimeZoneLookupHelper.guessZoneIdByCountry("de", nhWinterTimeMillis));

        // US has many time zones that have different offsets.
        assertNull(mTimeZoneLookupHelper.guessZoneIdByCountry("us", nhSummerTimeMillis));
        assertNull(mTimeZoneLookupHelper.guessZoneIdByCountry("us", nhWinterTimeMillis));
    }

    @Test
    public void testCountryUsesUtc() {
        assertFalse(mTimeZoneLookupHelper.countryUsesUtc("us"));
        assertTrue(mTimeZoneLookupHelper.countryUsesUtc("gb"));
    }

    private static void assertDifferentZoneIds(String zone1, String zone2) {
        assertFalse("Zone IDs not different, both=" + zone1, zone1.equals(zone2));
    }

    private static void assertTimeZoneIdUsedInCountry(String isoCountryCode, String timeZoneId) {
        List<String> zoneIdsByCountry =
                TimeZoneFinder.getInstance().lookupTimeZoneIdsByCountry(isoCountryCode);
        assertTrue(timeZoneId + " must be used in " + isoCountryCode,
                zoneIdsByCountry.contains(timeZoneId));
    }

    /**
     * Assert the timeZone has the expected properties at the specified time.
     */
    private static void assertTimeZoneId(
            long time, int expectedOffsetAtTime, Integer expectedDstAtTime, String timeZoneId) {
        TimeZone timeZone = TimeZone.getTimeZone(timeZoneId);
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

    private static long createUtcTime(
            int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minute, int second) {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear(); // Clear millis, etc.
        calendar.set(year, monthOfYear - 1, dayOfMonth, hourOfDay, minute, second);
        return calendar.getTimeInMillis();
    }
}
