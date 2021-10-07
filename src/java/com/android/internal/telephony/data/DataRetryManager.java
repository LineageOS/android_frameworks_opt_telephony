/*
 * Copyright 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.DataFailCause;
import android.text.TextUtils;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * DataRetryManager manages data network setup retry and its configurations.
 */
public class DataRetryManager extends Handler {
    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    private final @NonNull Phone mPhone;
    private final @NonNull String mLogTag;
    private final @NonNull LocalLog mLocalLog = new LocalLog(128);

    private final @NonNull DataConfigManager mDataConfigManager;

    private @NonNull List<DataRetryRule> mDataRetryRuleList = new ArrayList<>();

    /**
     * Represent a data retry rule. A rule consists a retry type (e.g. either by capabilities,
     * fail cause, or both), and a retry interval.
     */
    public static final class DataRetryRule {
        private static final String RULE_TAG_CAPABILITIES = "capabilities";
        private static final String RULE_TAG_FAIL_CAUSES = "fail_causes";
        private static final String RULE_TAG_RETRY_INTERVAL = "retry_interval";
        private static final String RULE_TAG_BACKOFF = "backoff";
        private static final String RULE_TAG_MAXIMUM_RETRIES = "maximum_retries";

        /**
         * The data network setup retry interval. Note that if this is {@code null}, then
         * {@link #getMaxRetries()} must return 0.
         */
        private @Nullable Duration mRetryInterval = null;

        /**
         * {@code true} if this the retry interval should be backed off. For example, if the first
         * time retry interval is 5 seconds, next time if it fails again, the interval should be
         * doubled to 10 seconds.
         */
        private boolean mBackedOffDuration = false;

        /**
         * The maximum retry times. After reaching the retry times, data retry will not be scheduled
         * with timer. Only environment changes (e.g. Airplane mode, SIM state, RAT, registration
         * state changes, etc..) can trigger the retry.
         */
        private int mMaxRetries = 10;

        /**
         * The network capabilities NetworkCapability.NET_CAPABILITY_XXX. Each data setup must be
         * associated with at least one network request. If that network request contains network
         * capabilities specified here, then retry will happen. {@code null} means the retry rule
         * is not using network capabilities.
         */
        private @Nullable int[] mNetworkCapabilities = null;

        /**
         * The fail causes. If data setup failed with certain fail causes, then retry will happen.
         * {@code null} means the retry rule is not using the fail causes.
         */
        private @Nullable @DataFailureCause int[] mFailCauses = null;

        /**
         * Represent a single setup data network retry rule.
         *
         * The syntax of the retry rule:
         * 1. Retry based on {@link NetworkCapabilities}
         * "capabilities=[netCaps1|netCaps2|...], [retry_interval=x], [backoff=[true|false]],
         *     [maximum_retries=y]"
         *
         * 2. Retry based on {@link DataFailCause}
         * "fail_causes=[cause1|cause2|cause3|...], [retry_interval=x], [backoff=[true|false]],
         *     [maximum_retries=y]"
         *
         * 3. Retry based on {@link NetworkCapabilities} and {@link DataFailCause}
         * "capabilities=[netCaps1|netCaps2|...], fail_causes=[cause1|cause2|cause3|...],
         *     [retry_interval=x], [backoff=[true|false]], [maximum_retries=y]"
         *
         * For example,
         * "capabilities=eims, retry_interval=1000, maximum_retries=20" means if the attached
         * network request is emergency, then retry data network setup every 1 second for up to 20
         * times.
         *
         * "fail_causes=8|27|28|29|30|32|33|35|50|51|111|-5|-6|65537|65538|-3|2253|2254
         * , maximum_retries=0" means for those fail causes, never retry with timers. Note that
         * when environment changes, retry can still happens.
         *
         * @param ruleString The retry rule in string format.
         * @throws IllegalArgumentException if the string can't be parsed to a retry rule.
         */
        public DataRetryRule(@NonNull String ruleString) {
            if (TextUtils.isEmpty(ruleString)) {
                throw new IllegalArgumentException("illegal rule " + ruleString);
            }
            ruleString = ruleString.trim().toLowerCase(Locale.ROOT);
            String[] expressions = ruleString.split("\\s*,\\s*");
            for (String expression : expressions) {
                String[] tokens = expression.trim().split("\\s*=\\s*");
                if (tokens.length != 2) {
                    throw new IllegalArgumentException("illegal rule " + ruleString);
                }
                String key = tokens[0].trim();
                String value = tokens[1].trim();
                try {
                    switch (key) {
                        case RULE_TAG_CAPABILITIES:
                            mNetworkCapabilities = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .mapToInt(DataUtils::getNetworkCapabilityFromString)
                                    .toArray();
                            break;
                        case RULE_TAG_FAIL_CAUSES:
                            mFailCauses = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .mapToInt(Integer::valueOf)
                                    .toArray();
                            break;
                        case RULE_TAG_RETRY_INTERVAL:
                            mRetryInterval = Duration.ofMillis(Long.parseLong(value));
                            break;
                        case RULE_TAG_BACKOFF:
                            mBackedOffDuration = Boolean.parseBoolean(value);
                            break;
                        case RULE_TAG_MAXIMUM_RETRIES:
                            mMaxRetries = Integer.parseInt(value);
                            break;
                        default:
                            throw new IllegalArgumentException("unexpected key " + key);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("illegal rule " + ruleString + ", e=" + e);
                }
            }
        }

        /**
         * @return The data network setup retry interval. Note that if this is {@code null}, then
         * {@link #getMaxRetries()} must return 0.
         */
        public @Nullable Duration getRetryInterval() {
            return mRetryInterval;
        }

        /**
         * @return {@code true} if this the retry interval should be backed off. For example, if
         * the first time retry interval is 5 seconds, next time if it fails again, the interval
         * should be doubled to 10 seconds.
         */
        public boolean isBackedOffDuration() {
            return mBackedOffDuration;
        }

        /**
         * @return The maximum retry times. After reaching the retry times, data retry will not be
         * scheduled with timer. Only environment changes (e.g. Airplane mode, SIM state, RAT,
         * registration state changes, etc..) can trigger the retry.
         */
        public int getMaxRetries() {
            return mMaxRetries;
        }

        /**
         * @return The network capabilities. Each data setup must be associated with at least one
         * network request. If that network request contains network capabilities specified here,
         * then retry will happen. {@code null} means the retry rule is not using network
         * capabilities.
         */
        public @Nullable int[] getNetworkCapabilities() {
            return mNetworkCapabilities;
        }

        /**
         * @return The fail causes. If data setup failed with certain fail causes, then retry will
         * happen. {@code null} means the retry rule is not using the fail causes.
         */
        public @Nullable @DataFailureCause int[] getFailCauses() {
            return mFailCauses;
        }

        @Override
        public String toString() {
            return "[RetryRule: Network capabilities:[" + DataUtils.networkCapabilitiesToString(
                    mNetworkCapabilities) + "], Fail causes=" + Arrays.toString(mFailCauses)
                    + ", Retry interval=" + mRetryInterval + ", Maximum retries=" + mMaxRetries
                    + ", Backoff=" + mBackedOffDuration + "]";
        }
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataRetryManager(Phone phone, Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DRM-" + mPhone.getPhoneId();

        mDataConfigManager = mPhone.getDataNetworkController().getDataConfigManager();
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
        }
    }

    /**
     * Called when data config is updated.
     */
    private void onDataConfigUpdated() {
        // TODO: Reset all retry related stuffs.

        mDataRetryRuleList = mDataConfigManager.getDataRetryRules();
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataRetryManager.
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataRetryManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Retry rules:");
        pw.increaseIndent();
        for (DataRetryRule rule : mDataRetryRuleList) {
            pw.println(rule);
        }
        pw.decreaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
