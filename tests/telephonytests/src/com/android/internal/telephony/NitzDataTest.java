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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class NitzDataTest {

    @Test
    public void testParse_dateOutsideAllowedRange() {
        assertNull(NitzData.parse("38/06/20,00:00:00+0"));
    }

    @Test
    public void testParse_missingRequiredFields() {
        // "yy/mm/dd,hh:mm:ss(+/-)tz[,dt[,tzid]]"

        // No tz field.
        assertNull(NitzData.parse("38/06/20,00:00:00"));
    }

    @Test
    public void testParse_withDst() {
        // "yy/mm/dd,hh:mm:ss(+/-)tz[,dt[,tzid]]"
        // tz, dt are in number of quarter-hours
        {
            NitzData nitz = NitzData.parse("15/06/20,01:02:03-1,0");
            assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(-1 * 15), nitz.getLocalOffsetMillis());
            assertEquals(0, nitz.getDstAdjustmentMillis().longValue());
            assertNull(nitz.getEmulatorHostTimeZone());
        }
        {
            NitzData nitz = NitzData.parse("15/06/20,01:02:03+8,4");
            assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(8 * 15), nitz.getLocalOffsetMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(4 * 15),
                    nitz.getDstAdjustmentMillis().longValue());
            assertNull(nitz.getEmulatorHostTimeZone());
        }
        {
            NitzData nitz = NitzData.parse("15/06/20,01:02:03-8,4");
            assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(-8 * 15), nitz.getLocalOffsetMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(4 * 15),
                    nitz.getDstAdjustmentMillis().longValue());
            assertNull(nitz.getEmulatorHostTimeZone());
        }
    }

    @Test
    public void testParse_noDstField() {
        {
            NitzData nitz = NitzData.parse("15/06/20,01:02:03+4");
            assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(4 * 15), nitz.getLocalOffsetMillis());
            assertNull(nitz.getDstAdjustmentMillis());
            assertNull(nitz.getEmulatorHostTimeZone());
        }
        {
            NitzData nitz = NitzData.parse("15/06/20,01:02:03-4");
            assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
            assertEquals(TimeUnit.MINUTES.toMillis(-4 * 15), nitz.getLocalOffsetMillis());
            assertNull(nitz.getDstAdjustmentMillis());
            assertNull(nitz.getEmulatorHostTimeZone());
        }
    }

    @Test
    public void testParse_androidEmulatorTimeZoneExtension() {
        NitzData nitz = NitzData.parse("15/06/20,01:02:03-32,4,America!Los_Angeles");
        assertEquals(createUtcTime(2015, 6, 20, 1, 2, 3), nitz.getCurrentTimeInMillis());
        assertEquals(TimeUnit.MINUTES.toMillis(-32 * 15), nitz.getLocalOffsetMillis());
        assertEquals(TimeUnit.MINUTES.toMillis(4 * 15),
                nitz.getDstAdjustmentMillis().longValue());
        assertEquals("America/Los_Angeles", nitz.getEmulatorHostTimeZone().getID());
    }

    @Test
    public void testToString() {
        assertNotNull(NitzData.parse("15/06/20,01:02:03-32").toString());
        assertNotNull(NitzData.parse("15/06/20,01:02:03-32,4").toString());
        assertNotNull(NitzData.parse("15/06/20,01:02:03-32,4,America!Los_Angeles")
                .toString());
    }

    @Test
    public void testGuessTimeZone() {
        // Historical dates are used to avoid the test breaking due to data changes.
        // However, algorithm updates may change the exact time zone returned, though it shouldn't
        // ever be a less exact match.
        long nhSummerTimeMillis = createUtcTime(2015, 6, 20, 1, 2, 3);
        long nhWinterTimeMillis = createUtcTime(2015, 1, 20, 1, 2, 3);
        String nhSummerTimeString = "15/06/20,01:02:03";
        String nhWinterTimeString = "15/01/20,01:02:03";

        // Known DST state (true).
        assertTimeZone(nhSummerTimeMillis, TimeUnit.HOURS.toMillis(1), TimeUnit.HOURS.toMillis(1),
                NitzData.guessTimeZone(NitzData.parse(nhSummerTimeString + "+4,4")));
        assertTimeZone(nhSummerTimeMillis, TimeUnit.HOURS.toMillis(-8), TimeUnit.HOURS.toMillis(1),
                NitzData.guessTimeZone(NitzData.parse("15/06/20,01:02:03-32,4")));

        // Known DST state (false)
        assertTimeZone(nhWinterTimeMillis, 0L, 0L,
                NitzData.guessTimeZone(NitzData.parse(nhWinterTimeString + "+0,0")));
        assertTimeZone(nhWinterTimeMillis, TimeUnit.HOURS.toMillis(-8), 0L,
                NitzData.guessTimeZone(NitzData.parse(nhWinterTimeString + "-32,0")));

        // Unknown DST state
        assertTimeZone(nhSummerTimeMillis, TimeUnit.HOURS.toMillis(1), null,
                NitzData.guessTimeZone(NitzData.parse(nhSummerTimeString + "+4")));
        assertTimeZone(nhSummerTimeMillis, TimeUnit.HOURS.toMillis(-8), null,
                NitzData.guessTimeZone(NitzData.parse(nhSummerTimeString + "-32")));
        assertTimeZone(nhWinterTimeMillis, 0L, null,
                NitzData.guessTimeZone(NitzData.parse(nhWinterTimeString + "+0")));
        assertTimeZone(nhWinterTimeMillis, TimeUnit.HOURS.toMillis(-8), null,
                NitzData.guessTimeZone(NitzData.parse(nhWinterTimeString + "-32")));
    }

    private static void assertTimeZone(
            long time, long expectedOffsetAtTime, Long expectedDstAtTime, TimeZone timeZone) {

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
