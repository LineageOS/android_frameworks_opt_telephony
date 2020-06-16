/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import android.app.StatsManager;
import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

import java.util.Random;

/**
 * Implements statsd pullers for Telephony.
 *
 * <p>This class is currently a stub. When statsd pulled atom support is merged, this class will
 * register pullers to statsd, which will be called once a day to obtain telephony statistics that
 * cannot be sent to statsd in real time.
 */
public class MetricsCollector {
    private static final String TAG = MetricsCollector.class.getSimpleName();

    /** Disables various restrictions to ease debugging during development. */
    private static final boolean DBG = false; // STOPSHIP if true

    /**
     * Sets atom pull cool down to 23 hours to help enforcing privacy requirement.
     *
     * <p>Applies to certain atoms. The interval of 23 hours leaves some margin for pull operations
     * that occur once a day.
     */
    private static final long MIN_COOLDOWN_MILLIS =
            DBG ? 10L * SECOND_IN_MILLIS : 23L * HOUR_IN_MILLIS;

    /**
     * Buckets with less than these many calls will be dropped.
     *
     * <p>Applies to metrics with duration fields. Currently used by voice call RAT usages.
     */
    private static final long MIN_CALLS_PER_BUCKET = DBG ? 0L : 5L;

    /** Bucket size in milliseconds to round call durations into. */
    private static final long DURATION_BUCKET_MILLIS =
            DBG ? 2L * SECOND_IN_MILLIS : 5L * MINUTE_IN_MILLIS;

    private PersistAtomsStorage mStorage;
    private final StatsManager mStatsManager;
    private static final Random sRandom = new Random();

    public MetricsCollector(Context context) {
        mStorage = new PersistAtomsStorage(context);
        mStatsManager = (StatsManager) context.getSystemService(Context.STATS_MANAGER);
        if (mStatsManager != null) {
            // TODO: registration/puller needs to be added and the following code needs to be
            // enabled once dependencies are available.
            /*
            registerAtom(SIM_SLOT_STATE, null);
            registerAtom(SUPPORTED_RADIO_ACCESS_FAMILY, null);
            registerAtom(VOICE_CALL_RAT_USAGE, POLICY_PULL_DAILY);
            registerAtom(VOICE_CALL_SESSION, POLICY_PULL_DAILY);
            Rlog.d(TAG, "registered");
            */
        } else {
            Rlog.e(TAG, "could not get StatsManager, atoms not registered");
        }
    }

    /** Replaces the {@link PersistAtomsStorage} backing the puller. Used during unit tests. */
    @VisibleForTesting
    public void setPersistAtomsStorage(PersistAtomsStorage storage) {
        mStorage = storage;
    }

    /** Returns the {@link PersistAtomsStorage} backing the puller. */
    public PersistAtomsStorage getAtomsStorage() {
        return mStorage;
    }

    /** Returns the value rounded to the bucket. */
    private static long round(long value, long bucket) {
        return ((value + bucket / 2) / bucket) * bucket;
    }
}
