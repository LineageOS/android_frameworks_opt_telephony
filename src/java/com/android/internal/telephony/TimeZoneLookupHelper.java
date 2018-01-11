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

import android.util.TimeUtils;

import libcore.util.CountryTimeZones;
import libcore.util.TimeZoneFinder;

import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

/**
 * An interface to various time zone lookup behaviors.
 */
// Non-final to allow mocking.
public class TimeZoneLookupHelper {
    private static final int MS_PER_HOUR = 60 * 60 * 1000;

    /**
     * List of ISO codes for countries that can have an offset of
     * GMT+0 when not in daylight savings time.  This ignores some
     * small places such as the Canary Islands (Spain) and
     * Danmarkshavn (Denmark).  The list must be sorted by code.
     */
    private static final String[] GMT_COUNTRY_CODES = {
            "bf", // Burkina Faso
            "ci", // Cote d'Ivoire
            "eh", // Western Sahara
            "fo", // Faroe Islands, Denmark
            "gb", // United Kingdom of Great Britain and Northern Ireland
            "gh", // Ghana
            "gm", // Gambia
            "gn", // Guinea
            "gw", // Guinea Bissau
            "ie", // Ireland
            "lr", // Liberia
            "is", // Iceland
            "ma", // Morocco
            "ml", // Mali
            "mr", // Mauritania
            "pt", // Portugal
            "sl", // Sierra Leone
            "sn", // Senegal
            "st", // Sao Tome and Principe
            "tg", // Togo
    };

    public TimeZoneLookupHelper() {}

    /**
     * Finds a time zone ID that fits the supplied NITZ and country information.
     *
     * <p><em>Note:</em> When there are multiple matching zones then one of the matching candidates
     * will be returned. If the current device default zone matches it will be returned in
     * preference to other candidates. This method can return {@code null} if no matching time
     * zones are found.
     */
    public String guessZoneIdByNitzCountry(NitzData nitzData, String isoCountryCode) {
        return guessZoneIdByInstantOffsetDstCountry(
                nitzData.getCurrentTimeInMillis(),
                nitzData.getLocalOffsetMillis(),
                nitzData.isDst(),
                isoCountryCode);
    }

    /**
     * Finds a time zone ID that fits the supplied time / offset and country information.
     *
     * <p><em>Note:</em> When there are multiple matching zones then one of the matching candidates
     * will be returned. If the current device default zone matches it will be returned in
     * preference to other candidates. This method can return {@code null} if no matching time
     * zones are found.
     */
    public String guessZoneIdByInstantOffsetDstCountry(
            long timeMillis, int utcOffsetMillis, boolean isDst, String isoCountryCode) {
        TimeZone timeZone =
                TimeUtils.getTimeZone(utcOffsetMillis, isDst, timeMillis, isoCountryCode);
        return timeZone == null ? null : timeZone.getID();
    }

    /**
     * Finds a time zone ID using only information present in the supplied {@link NitzData} object.
     *
     * <p><em>Note:</em> Because multiple time zones can have the same offset / DST state at a given
     * time this process is error prone; an arbitrary match is returned when there are multiple
     * candidates. The algorithm can also return a non-exact match by assuming that the DST
     * information provided by NITZ is incorrect. This method can return {@code null} if no matching
     * time zones are found.
     */
    public String guessZoneIdByNitz(NitzData nitzData) {
        TimeZone zone = guessZoneByNitzStatic(nitzData);
        return zone == null ? null : zone.getID();
    }

    /**
     * Returns a time zone ID for the country if possible. For counties that use a single time zone
     * this will provide a good choice. For countries with multiple time zones, a time zone is
     * returned if all time zones used in the country currently have the same offset (currently ==
     * according to the device's current system clock time). If this is not the case then
     * {@code null} can be returned.
     */
    public String guessZoneIdByCountry(String isoCountryCode, long whenMillis) {
        CountryTimeZones countryTimeZones =
                TimeZoneFinder.getInstance().lookupCountryTimeZones(isoCountryCode);
        if (countryTimeZones == null) {
            // Unknown country code.
            return null;
        }

        if (countryTimeZones.isDefaultOkForCountryTimeZoneDetection(whenMillis)) {
            return countryTimeZones.getDefaultTimeZoneId();
        }
        return null;
    }

    /** Static method for use by {@link ServiceStateTracker}. */
    static TimeZone guessZoneByNitzStatic(NitzData nitzData) {
        int utcOffsetMillis = nitzData.getLocalOffsetMillis();
        boolean isDst = nitzData.isDst();
        long timeMillis = nitzData.getCurrentTimeInMillis();

        TimeZone guess = guessByInstantOffsetDst(timeMillis, utcOffsetMillis, isDst);
        if (guess == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            guess = guessByInstantOffsetDst(timeMillis, utcOffsetMillis, !isDst);
        }
        return guess;
    }

    private static TimeZone guessByInstantOffsetDst(long timeMillis, int utcOffsetMillis,
            boolean isDst) {
        int rawOffset = utcOffsetMillis;
        if (isDst) {
            rawOffset -= MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone guess = null;
        Date d = new Date(timeMillis);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(timeMillis) == utcOffsetMillis && tz.inDaylightTime(d) == isDst) {
                guess = tz;
                break;
            }
        }

        return guess;
    }

    /**
     * Returns {@code true} if the supplied (lower-case) ISO country code is for a country known to
     * use a raw offset of zero from UTC.
     */
    public boolean countryUsesUtc(String isoCountryCode) {
        return Arrays.binarySearch(GMT_COUNTRY_CODES, isoCountryCode) >= 0;
    }
}
