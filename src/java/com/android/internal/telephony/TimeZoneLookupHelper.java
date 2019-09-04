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

import android.icu.util.TimeZone;
import android.text.TextUtils;

import libcore.timezone.CountryTimeZones;
import libcore.timezone.TimeZoneFinder;

import java.util.Date;
import java.util.Objects;

/**
 * An interface to various time zone lookup behaviors.
 */
// Non-final to allow mocking.
public class TimeZoneLookupHelper {

    /**
     * The result of looking up a time zone using offset information (and possibly more).
     */
    public static final class OffsetResult {

        /** A zone that matches the supplied criteria. See also {@link #mIsOnlyMatch}. */
        private final TimeZone mTimeZone;

        /** True if there is only one matching time zone for the supplied criteria. */
        private final boolean mIsOnlyMatch;

        public OffsetResult(TimeZone timeZone, boolean isOnlyMatch) {
            mTimeZone = Objects.requireNonNull(timeZone);
            mIsOnlyMatch = isOnlyMatch;
        }

        /**
         * Returns a time zone that matches the supplied criteria.
         */
        public TimeZone getTimeZone() {
            return mTimeZone;
        }

        /**
         * Returns {@code true} if there is only one matching time zone for the supplied criteria.
         */
        public boolean getIsOnlyMatch() {
            return mIsOnlyMatch;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OffsetResult that = (OffsetResult) o;
            return mIsOnlyMatch == that.mIsOnlyMatch
                    && mTimeZone.getID().equals(that.mTimeZone.getID());
        }

        @Override
        public int hashCode() {
            return Objects.hash(mTimeZone, mIsOnlyMatch);
        }

        @Override
        public String toString() {
            return "OffsetResult{"
                    + "mTimeZone=" + mTimeZone
                    + ", mIsOnlyMatch=" + mIsOnlyMatch
                    + '}';
        }
    }

    /**
     * The result of looking up a time zone using country information.
     */
    public static final class CountryResult {

        /** A time zone to use for the country. */
        public final String zoneId;

        /**
         * True if all the time zones in the country have the same offset at {@link #whenMillis}.
         */
        public final boolean allZonesHaveSameOffset;

        /** The time associated with {@link #allZonesHaveSameOffset}. */
        public final long whenMillis;

        public CountryResult(String zoneId, boolean allZonesHaveSameOffset, long whenMillis) {
            this.zoneId = zoneId;
            this.allZonesHaveSameOffset = allZonesHaveSameOffset;
            this.whenMillis = whenMillis;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CountryResult that = (CountryResult) o;

            if (allZonesHaveSameOffset != that.allZonesHaveSameOffset) {
                return false;
            }
            if (whenMillis != that.whenMillis) {
                return false;
            }
            return zoneId.equals(that.zoneId);
        }

        @Override
        public int hashCode() {
            int result = zoneId.hashCode();
            result = 31 * result + (allZonesHaveSameOffset ? 1 : 0);
            result = 31 * result + (int) (whenMillis ^ (whenMillis >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "CountryResult{"
                    + "zoneId='" + zoneId + '\''
                    + ", allZonesHaveSameOffset=" + allZonesHaveSameOffset
                    + ", whenMillis=" + whenMillis
                    + '}';
        }
    }

    private static final int MS_PER_HOUR = 60 * 60 * 1000;

    /** The last CountryTimeZones object retrieved. */
    private CountryTimeZones mLastCountryTimeZones;

    public TimeZoneLookupHelper() {}

    /**
     * Looks for a time zone for the supplied NITZ and country information.
     *
     * <p><em>Note:</em> When there are multiple matching zones then one of the matching candidates
     * will be returned in the result. If the current device default zone matches it will be
     * returned in preference to other candidates. This method can return {@code null} if no
     * matching time zones are found.
     */
    public OffsetResult lookupByNitzCountry(NitzData nitzData, String isoCountryCode) {
        CountryTimeZones countryTimeZones = getCountryTimeZones(isoCountryCode);
        if (countryTimeZones == null) {
            return null;
        }
        TimeZone bias = TimeZone.getDefault();

        CountryTimeZones.OffsetResult offsetResult = countryTimeZones.lookupByOffsetWithBias(
                nitzData.getLocalOffsetMillis(), nitzData.isDst(),
                nitzData.getCurrentTimeInMillis(), bias);

        if (offsetResult == null) {
            return null;
        }
        return new OffsetResult(offsetResult.mTimeZone, offsetResult.mOneMatch);
    }

    /**
     * Looks for a time zone using only information present in the supplied {@link NitzData} object.
     *
     * <p><em>Note:</em> Because multiple time zones can have the same offset / DST state at a given
     * time this process is error prone; an arbitrary match is returned when there are multiple
     * candidates. The algorithm can also return a non-exact match by assuming that the DST
     * information provided by NITZ is incorrect. This method can return {@code null} if no matching
     * time zones are found.
     */
    public OffsetResult lookupByNitz(NitzData nitzData) {
        return lookupByNitzStatic(nitzData);
    }

    /**
     * Returns information about the time zones used in a country at a given time.
     *
     * {@code null} can be returned if a problem occurs during lookup, e.g. if the country code is
     * unrecognized, if the country is uninhabited, or if there is a problem with the data.
     */
    public CountryResult lookupByCountry(String isoCountryCode, long whenMillis) {
        CountryTimeZones countryTimeZones = getCountryTimeZones(isoCountryCode);
        if (countryTimeZones == null) {
            // Unknown country code.
            return null;
        }
        if (countryTimeZones.getDefaultTimeZoneId() == null) {
            return null;
        }

        return new CountryResult(
                countryTimeZones.getDefaultTimeZoneId(),
                countryTimeZones.isDefaultOkForCountryTimeZoneDetection(whenMillis),
                whenMillis);
    }

    /**
     * Returns a time zone ID for the country if possible. For counties that use a single time zone
     * this will provide a good choice. For countries with multiple time zones, a time zone is
     * returned but it may be appropriate for only part of the country. {@code null} can be returned
     * if a problem occurs during lookup, e.g. if the country code is unrecognized, if the country
     * is uninhabited, or if there is a problem with the data.
     */
    public String lookupDefaultTimeZoneIdByCountry(String isoCountryCode) {
        CountryTimeZones countryTimeZones =
                TimeZoneFinder.getInstance().lookupCountryTimeZones(isoCountryCode);
        return countryTimeZones == null ? null : countryTimeZones.getDefaultTimeZoneId();
    }

    /**
     * Finds a time zone using only information present in the supplied {@link NitzData} object.
     * This is a static method for use by {@link ServiceStateTracker}.
     *
     * <p><em>Note:</em> Because multiple time zones can have the same offset / DST state at a given
     * time this process is error prone; an arbitrary match is returned when there are multiple
     * candidates. The algorithm can also return a non-exact match by assuming that the DST
     * information provided by NITZ is incorrect. This method can return {@code null} if no matching
     * time zones are found.
     */
    static TimeZone guessZoneByNitzStatic(NitzData nitzData) {
        OffsetResult result = lookupByNitzStatic(nitzData);
        return result != null ? result.getTimeZone() : null;
    }

    private static OffsetResult lookupByNitzStatic(NitzData nitzData) {
        int utcOffsetMillis = nitzData.getLocalOffsetMillis();
        boolean isDst = nitzData.isDst();
        long timeMillis = nitzData.getCurrentTimeInMillis();

        OffsetResult match = lookupByInstantOffsetDst(timeMillis, utcOffsetMillis, isDst);
        if (match == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            match = lookupByInstantOffsetDst(timeMillis, utcOffsetMillis, !isDst);
        }
        return match;
    }

    private static OffsetResult lookupByInstantOffsetDst(long timeMillis, int utcOffsetMillis,
            boolean isDst) {
        int rawOffset = utcOffsetMillis;
        if (isDst) {
            rawOffset -= MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone match = null;
        Date d = new Date(timeMillis);
        boolean isOnlyMatch = true;
        for (String zone : zones) {
            TimeZone tz = TimeZone.getFrozenTimeZone(zone);
            if (tz.getOffset(timeMillis) == utcOffsetMillis && tz.inDaylightTime(d) == isDst) {
                if (match == null) {
                    match = tz;
                } else {
                    isOnlyMatch = false;
                    break;
                }
            }
        }

        if (match == null) {
            return null;
        }
        return new OffsetResult(match, isOnlyMatch);
    }

    /**
     * Returns {@code true} if the supplied (lower-case) ISO country code is for a country known to
     * use a raw offset of zero from UTC at the time specified.
     */
    public boolean countryUsesUtc(String isoCountryCode, long whenMillis) {
        if (TextUtils.isEmpty(isoCountryCode)) {
            return false;
        }

        CountryTimeZones countryTimeZones = getCountryTimeZones(isoCountryCode);
        return countryTimeZones != null && countryTimeZones.hasUtcZone(whenMillis);
    }

    private CountryTimeZones getCountryTimeZones(String isoCountryCode) {
        // A single entry cache of the last CountryTimeZones object retrieved since there should
        // be strong consistency across calls.
        synchronized (this) {
            if (mLastCountryTimeZones != null) {
                if (mLastCountryTimeZones.isForCountryCode(isoCountryCode)) {
                    return mLastCountryTimeZones;
                }
            }

            // Perform the lookup. It's very unlikely to return null, but we won't cache null.
            CountryTimeZones countryTimeZones =
                    TimeZoneFinder.getInstance().lookupCountryTimeZones(isoCountryCode);
            if (countryTimeZones != null) {
                mLastCountryTimeZones = countryTimeZones;
            }
            return countryTimeZones;
        }
    }
}
