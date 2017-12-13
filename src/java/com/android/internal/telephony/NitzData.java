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

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.telephony.Rlog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Represents NITZ data. Various static methods are provided to help with parsing and intepretation
 * of NITZ data.
 *
 * {@hide}
 */
@VisibleForTesting(visibility = PACKAGE)
public final class NitzData {
    private static final String LOG_TAG = ServiceStateTracker.LOG_TAG;
    private static final int MS_PER_HOUR = 60 * 60 * 1000;
    private static final int MS_PER_QUARTER_HOUR = 15 * 60 * 1000;

    /* Time stamp after 19 January 2038 is not supported under 32 bit */
    private static final int MAX_NITZ_YEAR = 2037;

    // Stored For logging / debugging only.
    private final String mOriginalString;

    private final int mZoneOffset;

    private final Integer mDstOffset;

    private final long mCurrentTimeMillis;

    private final TimeZone mEmulatorHostTimeZone;

    private NitzData(String originalString, int zoneOffsetMillis, Integer dstOffsetMillis,
            long utcTimeMillis, TimeZone timeZone) {
        if (originalString == null) {
            throw new NullPointerException("originalString==null");
        }
        this.mOriginalString = originalString;
        this.mZoneOffset = zoneOffsetMillis;
        this.mDstOffset = dstOffsetMillis;
        this.mCurrentTimeMillis = utcTimeMillis;
        this.mEmulatorHostTimeZone = timeZone;
    }

    /**
     * Parses the supplied NITZ string, returning the encoded data.
     */
    public static NitzData parse(String nitz) {
        // "yy/mm/dd,hh:mm:ss(+/-)tz[,dt[,tzid]]"
        // tz, dt are in number of quarter-hours

        try {
            /* NITZ time (hour:min:sec) will be in UTC but it supplies the timezone
             * offset as well (which we won't worry about until later) */
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
            c.clear();
            c.set(Calendar.DST_OFFSET, 0);

            String[] nitzSubs = nitz.split("[/:,+-]");

            int year = 2000 + Integer.parseInt(nitzSubs[0]);
            if (year > MAX_NITZ_YEAR) {
                if (ServiceStateTracker.DBG) {
                    Rlog.e(LOG_TAG, "NITZ year: " + year + " exceeds limit, skip NITZ time update");
                }
                return null;
            }
            c.set(Calendar.YEAR, year);

            // month is 0 based!
            int month = Integer.parseInt(nitzSubs[1]) - 1;
            c.set(Calendar.MONTH, month);

            int date = Integer.parseInt(nitzSubs[2]);
            c.set(Calendar.DATE, date);

            int hour = Integer.parseInt(nitzSubs[3]);
            c.set(Calendar.HOUR, hour);

            int minute = Integer.parseInt(nitzSubs[4]);
            c.set(Calendar.MINUTE, minute);

            int second = Integer.parseInt(nitzSubs[5]);
            c.set(Calendar.SECOND, second);

            // The offset received from NITZ is the offset to add to get current local time.
            boolean sign = (nitz.indexOf('-') == -1);
            int totalUtcOffsetQuarterHours = Integer.parseInt(nitzSubs[6]);
            int totalUtcOffsetMillis =
                    (sign ? 1 : -1) * totalUtcOffsetQuarterHours * MS_PER_QUARTER_HOUR;

            // DST correction is already applied to the UTC offset. We could subtract it if we
            // wanted the raw offset.
            Integer dstAdjustmentQuarterHours =
                    (nitzSubs.length >= 8) ? Integer.parseInt(nitzSubs[7]) : null;
            Integer dstAdjustmentMillis = null;
            if (dstAdjustmentQuarterHours != null) {
                dstAdjustmentMillis = dstAdjustmentQuarterHours * MS_PER_QUARTER_HOUR;
            }

            // As a special extension, the Android emulator appends the name of
            // the host computer's timezone to the nitz string. this is zoneinfo
            // timezone name of the form Area!Location or Area!Location!SubLocation
            // so we need to convert the ! into /
            TimeZone zone = null;
            if (nitzSubs.length >= 9) {
                String tzname = nitzSubs[8].replace('!', '/');
                zone = TimeZone.getTimeZone(tzname);
            }
            return new NitzData(nitz, totalUtcOffsetMillis, dstAdjustmentMillis,
                    c.getTimeInMillis(), zone);
        } catch (RuntimeException ex) {
            Rlog.e(LOG_TAG, "NITZ: Parsing NITZ time " + nitz + " ex=" + ex);
            return null;
        }
    }

    /** A method for use in tests to create NitzData instances. */
    public static NitzData createForTests(int zoneOffsetMillis, Integer dstOffsetMillis,
            long utcTimeMillis, TimeZone timeZone) {
        return new NitzData("Test data", zoneOffsetMillis, dstOffsetMillis, utcTimeMillis,
                timeZone);
    }

    /**
     * Returns the current time as the number of milliseconds since the beginning of the Unix epoch
     * (1/1/1970 00:00:00 UTC).
     */
    public long getCurrentTimeInMillis() {
        return mCurrentTimeMillis;
    }

    /**
     * Returns the total offset to apply to the {@link #getCurrentTimeInMillis()} to arrive at a
     * local time.
     */
    public int getLocalOffsetMillis() {
        return mZoneOffset;
    }

    /**
     * Returns the offset (already included in {@link #getLocalOffsetMillis()}) associated with
     * Daylight Savings Time (DST). This field is optional: {@code null} means the DST offset is
     * unknown.
     */
    public Integer getDstAdjustmentMillis() {
        return mDstOffset;
    }

    /**
     * Returns {@link true} if the time is in Daylight Savings Time (DST), {@link false} if it is
     * unknown or not in DST. See {@link #getDstAdjustmentMillis()}.
     */
    public boolean isDst() {
        return mDstOffset != null && mDstOffset != 0;
    }


    /**
     * Returns the time zone of the host computer when Android is running in an emulator. It is
     * {@code null} for real devices. This information is communicated via a non-standard Android
     * extension to NITZ.
     */
    public TimeZone getEmulatorHostTimeZone() {
        return mEmulatorHostTimeZone;
    }

    /**
     * Using information present in the supplied {@link NitzData} object, guess the time zone.
     * Because multiple time zones can have the same offset / DST state at a given time this process
     * is error prone; an arbitrary match is returned when there are multiple candidates. The
     * algorithm can also return a non-exact match by assuming that the DST information provided by
     * NITZ is incorrect. This method can return {@code null} if no time zones are found.
     */
    public static TimeZone guessTimeZone(NitzData nitzData) {
        int offset = nitzData.getLocalOffsetMillis();
        boolean dst = nitzData.isDst();
        long when = nitzData.getCurrentTimeInMillis();
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            guess = findTimeZone(offset, !dst, when);
        }
        return guess;
    }

    private static TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= MS_PER_HOUR;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone guess = null;
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset && tz.inDaylightTime(d) == dst) {
                guess = tz;
                break;
            }
        }

        return guess;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NitzData nitzData = (NitzData) o;

        if (mZoneOffset != nitzData.mZoneOffset) {
            return false;
        }
        if (mCurrentTimeMillis != nitzData.mCurrentTimeMillis) {
            return false;
        }
        if (!mOriginalString.equals(nitzData.mOriginalString)) {
            return false;
        }
        if (mDstOffset != null ? !mDstOffset.equals(nitzData.mDstOffset)
                : nitzData.mDstOffset != null) {
            return false;
        }
        return mEmulatorHostTimeZone != null ? mEmulatorHostTimeZone
                .equals(nitzData.mEmulatorHostTimeZone) : nitzData.mEmulatorHostTimeZone == null;
    }

    @Override
    public int hashCode() {
        int result = mOriginalString.hashCode();
        result = 31 * result + mZoneOffset;
        result = 31 * result + (mDstOffset != null ? mDstOffset.hashCode() : 0);
        result = 31 * result + (int) (mCurrentTimeMillis ^ (mCurrentTimeMillis >>> 32));
        result = 31 * result + (mEmulatorHostTimeZone != null ? mEmulatorHostTimeZone.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NitzData{"
                + "mOriginalString=" + mOriginalString
                + ", mZoneOffset=" + mZoneOffset
                + ", mDstOffset=" + mDstOffset
                + ", mCurrentTimeMillis=" + mCurrentTimeMillis
                + ", mEmulatorHostTimeZone=" + mEmulatorHostTimeZone
                + '}';
    }
}
