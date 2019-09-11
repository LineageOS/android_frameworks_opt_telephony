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
import libcore.timezone.CountryTimeZones.TimeZoneMapping;
import libcore.timezone.TimeZoneFinder;

import java.util.List;
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
         * True if the country has multiple effective zones to choose from at {@link #whenMillis}.
         */
        public final boolean multipleZonesInCountry;

        /**
         * True if all the effective time zones in the country have the same offset at
         * {@link #whenMillis}.
         */
        public final boolean allZonesHaveSameOffset;

        /**
         * The time associated with {@link #allZonesHaveSameOffset} and
         * {@link #multipleZonesInCountry}.
         */
        public final long whenMillis;

        public CountryResult(String zoneId, boolean multipleZonesInCountry,
                boolean allZonesHaveSameOffset, long whenMillis) {
            this.zoneId = zoneId;
            this.multipleZonesInCountry = multipleZonesInCountry;
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

            if (multipleZonesInCountry != that.multipleZonesInCountry) {
                return false;
            }
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
            result = 31 * result + (multipleZonesInCountry ? 1 : 0);
            result = 31 * result + (allZonesHaveSameOffset ? 1 : 0);
            result = 31 * result + (int) (whenMillis ^ (whenMillis >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "CountryResult{"
                    + "zoneId='" + zoneId + '\''
                    + ", multipleZonesInCountry=" + multipleZonesInCountry
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

        // Android NITZ time zone matching doesn't try to do a precise match using the DST offset
        // supplied by the carrier. It only considers whether or not the carrier suggests local time
        // is DST (if known). NITZ is limited in only being able to express DST offsets in whole
        // hours and the DST info is optional.
        Integer dstAdjustmentMillis = nitzData.getDstAdjustmentMillis();
        Boolean isDst = dstAdjustmentMillis == null ? null : dstAdjustmentMillis != 0;
        Integer dstAdjustmentMillisToMatch = null; // Don't try to match the precise DST offset.
        CountryTimeZones.OffsetResult offsetResult = countryTimeZones.lookupByOffsetWithBias(
                nitzData.getLocalOffsetMillis(), isDst, dstAdjustmentMillisToMatch,
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

        List<TimeZoneMapping> effectiveTimeZoneMappings =
                countryTimeZones.getEffectiveTimeZoneMappingsAt(whenMillis);
        boolean multipleZonesInCountry = effectiveTimeZoneMappings.size() > 1;
        boolean defaultOkForCountryTimeZoneDetection = isDefaultOkForCountryTimeZoneDetection(
                countryTimeZones.getDefaultTimeZone(), effectiveTimeZoneMappings, whenMillis);
        return new CountryResult(
                countryTimeZones.getDefaultTimeZoneId(), multipleZonesInCountry,
                defaultOkForCountryTimeZoneDetection, whenMillis);
    }

    /**
     * Returns {@code true} if the default time zone for the country is either the only zone used or
     * if it has the same offsets as all other zones used by the country <em>at the specified time
     * </em> making the default equivalent to all other zones used by the country <em>at that time
     * </em>.
     */
    private static boolean isDefaultOkForCountryTimeZoneDetection(
            TimeZone countryDefault, List<TimeZoneMapping> timeZoneMappings, long whenMillis) {
        if (timeZoneMappings.isEmpty()) {
            // Should never happen unless there's been an error loading the data.
            return false;
        } else if (timeZoneMappings.size() == 1) {
            // The default is the only zone so it's a good candidate.
            return true;
        } else {
            if (countryDefault == null) {
                return false;
            }

            String countryDefaultId = countryDefault.getID();
            int countryDefaultOffset = countryDefault.getOffset(whenMillis);
            for (TimeZoneMapping timeZoneMapping : timeZoneMappings) {
                if (timeZoneMapping.timeZoneId.equals(countryDefaultId)) {
                    continue;
                }

                TimeZone timeZone = timeZoneMapping.getTimeZone();
                if (timeZone == null) {
                    continue;
                }

                int candidateOffset = timeZone.getOffset(whenMillis);
                if (countryDefaultOffset != candidateOffset) {
                    // Multiple different offsets means the default should not be used.
                    return false;
                }
            }
            return true;
        }
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
        long timeMillis = nitzData.getCurrentTimeInMillis();

        // Android NITZ time zone matching doesn't try to do a precise match using the DST offset
        // supplied by the carrier. It only considers whether or not the carrier suggests local time
        // is DST (if known). NITZ is limited in only being able to express DST offsets in whole
        // hours and the DST info is optional.
        Integer dstAdjustmentMillis = nitzData.getDstAdjustmentMillis();
        Boolean isDst = dstAdjustmentMillis == null ? null : dstAdjustmentMillis != 0;

        OffsetResult match = lookupByInstantOffsetDst(timeMillis, utcOffsetMillis, isDst);
        if (match == null && isDst != null) {
            // This branch is extremely unlikely and could probably be removed. The match above will
            // have searched the entire tzdb for a zone with the same total offset and isDst state.
            // Here we try another match but use "null" for isDst to indicate that only the total
            // offset should be considered. If, by the end of this, there isn't a match then the
            // current offset suggested by the carrier must be highly unusual.
            match = lookupByInstantOffsetDst(timeMillis, utcOffsetMillis, null /* isDst */);
        }
        return match;
    }

    private static OffsetResult lookupByInstantOffsetDst(long timeMillis, int utcOffsetMillis,
            Boolean isDst) {

        String[] zones = TimeZone.getAvailableIDs();
        TimeZone match = null;
        boolean isOnlyMatch = true;
        for (String zone : zones) {
            TimeZone tz = TimeZone.getFrozenTimeZone(zone);
            if (offsetMatchesAtTime(tz, utcOffsetMillis, isDst, timeMillis)) {
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
     * Returns {@code true} if the specified {@code totalOffset} and {@code isDst} would be valid in
     * the {@code timeZone} at time {@code whenMillis}. {@code totalOffetMillis} is always matched.
     * If {@code isDst} is {@code null} this means the DST state is unknown so DST state is ignored.
     * If {@code isDst} is not {@code null} then it is also matched.
     */
    private static boolean offsetMatchesAtTime(TimeZone timeZone, int totalOffsetMillis,
            Boolean isDst, long whenMillis) {
        int[] offsets = new int[2];
        timeZone.getOffset(whenMillis, false /* local */, offsets);

        if (totalOffsetMillis != (offsets[0] + offsets[1])) {
            return false;
        }

        return isDst == null || isDst == (offsets[1] != 0);
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
