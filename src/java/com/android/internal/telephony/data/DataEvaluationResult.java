/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.NonNull;

import java.text.DateFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * The class to describe the result of environment evaluation for whether allowing or disallowing
 * establishing a data network.
 */
public class DataEvaluationResult {
    /** Data disallowed reasons. There could be multiple reasons for not allowing data. */
    private @NonNull Set<DataDisallowedReason> mDataDisallowedReasons = new HashSet<>();

    /** Data allowed reason. It is intended to only have one allowed reason. */
    private DataAllowedReason mDataAllowedReason = DataAllowedReason.NONE;

    /** The timestamp of evaluation time */
    private @CurrentTimeMillisLong long mEvaluatedTime = 0;

    /**
     * Add a data disallowed reason. Note that adding a disallowed reason will clean up the
     * allowed reason because they are mutual exclusive.
     *
     * @param reason Disallowed reason.
     */
    public void add(DataDisallowedReason reason) {
        mDataAllowedReason = DataAllowedReason.NONE;
        mDataDisallowedReasons.add(reason);
        mEvaluatedTime = System.currentTimeMillis();
    }

    /**
     * Add a data allowed reason. Note that adding an allowed reason will clean up the disallowed
     * reasons because they are mutual exclusive.
     *
     * @param reason Allowed reason.
     */
    public void add(DataAllowedReason reason) {
        mDataDisallowedReasons.clear();

        // Only higher priority allowed reason can overwrite the old one. See
        // DataAllowedReason for the oder.
        if (reason.ordinal() > mDataAllowedReason.ordinal()) {
            mDataAllowedReason = reason;
        }
        mEvaluatedTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        StringBuilder reasonStr = new StringBuilder();
        reasonStr.append("EvaluationResult: ");
        if (mDataDisallowedReasons.size() > 0) {
            reasonStr.append("Data disallowed reasons:");
            for (DataDisallowedReason reason : mDataDisallowedReasons) {
                reasonStr.append(" ").append(reason);
            }
        } else {
            reasonStr.append("Data allowed reason:");
            reasonStr.append(" ").append(mDataAllowedReason);
        }
        reasonStr.append(", time=" + DateFormat.getDateTimeInstance().format(mEvaluatedTime));
        return reasonStr.toString();
    }

    /**
     * @return {@code true} if data is allowed.
     */
    public boolean isDataAllowed() {
        return mDataDisallowedReasons.size() == 0;
    }

    /**
     * Check if it contains a certain disallowed reason.
     *
     * @param reason The disallowed reason to check.
     * @return {@code true} if the provided reason matches one of the disallowed reasons.
     */
    public boolean contains(DataDisallowedReason reason) {
        return mDataDisallowedReasons.contains(reason);
    }

    /**
     * Check if only one disallowed reason prevent data connection.
     *
     * @param reason The given reason to check
     * @return True if the given reason is the only one that prevents data connection
     */
    public boolean containsOnly(DataDisallowedReason reason) {
        return mDataDisallowedReasons.size() == 1 && contains(reason);
    }

    /**
     * Check if the allowed reason is the specified reason.
     *
     * @param reason The allowed reason.
     * @return {@code true} if the specified reason matches the allowed reason.
     */
    public boolean contains(DataAllowedReason reason) {
        return reason == mDataAllowedReason;
    }

    /**
     * @return {@code true} if the disallowed reasons contains hard reasons.
     */
    public boolean containsHardDisallowedReasons() {
        for (DataDisallowedReason reason : mDataDisallowedReasons) {
            if (reason.isHardReason()) {
                return true;
            }
        }
        return false;
    }

    /** Disallowed reasons. There could be multiple reasons if data connection is not allowed. */
    public enum DataDisallowedReason {
        // Soft failure reasons. A soft reason means that in certain conditions, data is still
        // allowed. Normally those reasons are due to users settings.

        /** Data is disabled by the user or policy. */
        DATA_DISABLED(false),
        /** Data roaming is disabled by the user. */
        ROAMING_DISABLED(false),
        /** Default data not selected. */
        DEFAULT_DATA_UNSELECTED(false),

        // Belows are all hard failure reasons. A hard reason means no matter what the data should
        // not be allowed.
        /** Data registration state is not in service. */
        NOT_IN_SERVICE(true),
        /** SIM is not ready. */
        SIM_NOT_READY(true),
        /** Concurrent voice and data is not allowed. */
        CONCURRENT_VOICE_DATA_NOT_ALLOWED(true),
        /** Carrier notified data should be restricted. */
        DATA_RESTRICTED_BY_NETWORK(true),
        /** Radio power is off (i.e. airplane mode on) */
        RADIO_POWER_OFF(true),
        /** Data disabled by telephony in some scenarios, for example, emergency call. */
        INTERNAL_DATA_DISABLED(true),
        /** Airplane mode is forcibly turned on by the carrier. */
        RADIO_DISABLED_BY_CARRIER(true),
        /** certain APNs are only allowed when the device is camped on NR. */
        NOT_ON_NR(true),
        /** Data is not allowed while device is in emergency callback mode. */
        IN_ECBM(true),
        /** Underlying data service is not bound. */
        DATA_SERVICE_NOT_READY(true);

        private final boolean mIsHardReason;

        /**
         * @return {@code true} if the disallowed reason is a hard reason.
         */
        public boolean isHardReason() {
            return mIsHardReason;
        }

        /**
         * Constructor
         *
         * @param isHardReason {@code true} if the disallowed reason is a hard reason. A hard reason
         * means no matter what the data should not be allowed. A soft reason means that in certain
         * conditions, data is still allowed.
         */
        DataDisallowedReason(boolean isHardReason) {
            mIsHardReason = isHardReason;
        }
    }

    /**
     * Data allowed reasons. There will be only one reason if data is allowed.
     */
    enum DataAllowedReason {
        // Note that unlike disallowed reasons, we only have one allowed reason every time
        // when we check data is allowed or not. The order of these allowed reasons is very
        // important. The lower ones take precedence over the upper ones.
        /**
         * None. This is the initial value.
         */
        NONE,
        /**
         * The normal reason. This is the most common case.
         */
        NORMAL,
        /**
         * The network brought up by this network request is unmetered. Should allowed no matter
         * the user enables or disables data.
         */
        UNMETERED_USAGE,
        /**
         * The network request is restricted (i.e. Only privilege apps can access the network.)
         */
        RESTRICTED_REQUEST,
        /**
         * Data is allowed because the network request is for emergency. This should be always at
         * the bottom (i.e. highest priority)
         */
        EMERGENCY_REQUEST,
    }
}
