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

package com.android.internal.telephony.dataconnection;

import android.telephony.Annotation.ApnType;
import android.telephony.data.ApnSetting;

import com.android.internal.telephony.RetryManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Data throttler tracks the throttling status of data network. The throttler is per phone and per
 * transport type.
 */
public class DataThrottler {
    /**
     * Throttling tracker for APNs. Key is the APN type. Value is the elapsed time that APN
     * should not be retried. {@link RetryManager#NO_SUGGESTED_RETRY_DELAY} indicates throttling
     * does not exist. {@link RetryManager#NO_RETRY} indicates retry should never happen.
     */
    private final Map<Integer, Long> mThrottlingTracker = new HashMap<>();

    /**
     * Set retry time for the APN type.
     *
     * @param apnTypes APN types
     * @param retryElapsedTime The elapsed time that data connection for APN types should not be
     * retried. {@link RetryManager#NO_SUGGESTED_RETRY_DELAY} indicates throttling does not exist.
     * {@link RetryManager#NO_RETRY} indicates retry should never happen.
     */
    public void setRetryTime(@ApnType int apnTypes, long retryElapsedTime) {
        if (retryElapsedTime < 0) {
            retryElapsedTime = RetryManager.NO_SUGGESTED_RETRY_DELAY;
        }
        while (apnTypes != 0) {
            // Extract the least significant bit.
            int apnType = apnTypes & -apnTypes;
            mThrottlingTracker.put(apnType, retryElapsedTime);
            // Remove the least significant bit.
            apnTypes &= apnTypes - 1;
        }
    }

    /**
     * Get the earliest retry time for given APN type. The time is the system's elapse time.
     *
     * @param apnType APN type
     * @return The earliest retry time for APN type. The time is the system's elapse time.
     * {@link RetryManager#NO_SUGGESTED_RETRY_DELAY} indicates there is no throttling for given APN
     * type, {@link RetryManager#NO_RETRY} indicates retry should never happen.
     */
    public long getRetryTime(@ApnType int apnType) {
        // This is the workaround to handle the mistake that
        // ApnSetting.TYPE_DEFAULT = ApnTypes.DEFAULT | ApnTypes.HIPRI.
        if (apnType == ApnSetting.TYPE_DEFAULT) {
            apnType &= ~(ApnSetting.TYPE_HIPRI);
        }
        if (mThrottlingTracker.containsKey(apnType)) {
            return mThrottlingTracker.get(apnType);
        }

        return RetryManager.NO_SUGGESTED_RETRY_DELAY;
    }
}
