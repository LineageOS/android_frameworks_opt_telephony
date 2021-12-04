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

import android.annotation.CallbackExecutor;
import android.annotation.CurrentTimeMillisLong;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.DataFailCause;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * DataRetryManager manages data network setup retry and its configurations.
 */
public class DataRetryManager extends Handler {
    private static final boolean VDBG = false;

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for evaluating if data retry is needed or not. */
    private static final int EVENT_DATA_RETRY_EVALUATION = 2;

    /** Event for data retry. */
    private static final int EVENT_DATA_RETRY = 3;

    /** Event for cancelling all data retries. */
    private static final int EVENT_CANCEL_ALL_DATA_RETRIES = 4;

    /** The maximum retry entries to preserve. */
    private static final int MAXIMUM_HISTORICAL_RETRY_ENTRIES = 100;

    private final @NonNull Phone mPhone;
    private final @NonNull String mLogTag;
    private final @NonNull LocalLog mLocalLog = new LocalLog(128);

    /**
     * The data retry callback. This is only used to notify {@link DataNetworkController} to retry
     * setup data network.
     */
    private @NonNull DataRetryManagerCallback mDataRetryManagerCallback;

    /** Data config manager instance. */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Data profile manager. */
    private final @NonNull DataProfileManager mDataProfileManager;

    /** Retry rule list. */
    private @NonNull List<DataRetryRule> mDataRetryRuleList = new ArrayList<>();

    /** Data retry entries. */
    private final List<DataRetryEntry> mDataRetryEntries = new ArrayList<>();

    /**
     * Represent a data retry rule. A rule consists a retry type (e.g. either by capabilities,
     * fail cause, or both), and a retry interval.
     */
    public static final class DataRetryRule {
        private static final String RULE_TAG_CAPABILITIES = "capabilities";
        private static final String RULE_TAG_FAIL_CAUSES = "fail_causes";
        private static final String RULE_TAG_RETRY_INTERVAL = "retry_interval";
        private static final String RULE_TAG_MAXIMUM_RETRIES = "maximum_retries";

        /**
         * The data network setup retry interval. Note that if this is empty, then
         * {@link #getMaxRetries()} must return 0. Default retry interval is 5 seconds.
         */
        private List<Long> mRetryIntervalsMillis = List.of(TimeUnit.SECONDS.toMillis(5));

        /**
         * The maximum retry times. After reaching the retry times, data retry will not be scheduled
         * with timer. Only environment changes (e.g. Airplane mode, SIM state, RAT, registration
         * state changes, etc..) can trigger the retry.
         */
        private int mMaxRetries = 10;

        /**
         * The network capabilities. Each data setup must be
         * associated with at least one network request. If that network request contains network
         * capabilities specified here, then retry will happen. Empty set indicates the retry rule
         * is not using network capabilities.
         */
        private @NonNull @NetCapability Set<Integer> mNetworkCapabilities = new ArraySet<>();

        /**
         * The fail causes. If data setup failed with certain fail causes, then retry will happen.
         * Empty set indicates the retry rule is not using the fail causes.
         */
        private @NonNull @DataFailureCause Set<Integer> mFailCauses = new ArraySet<>();

        /**
         * Represent a single setup data network retry rule.
         *
         * The syntax of the retry rule:
         * 1. Retry based on {@link NetworkCapabilities}. Note that only APN-type network
         * capabilities are supported.
         * "capabilities=[netCaps1|netCaps2|...], [retry_interval=n1|n2|n3|n4...],
         * [maximum_retries=n]"
         *
         * 2. Retry based on {@link DataFailCause}
         * "fail_causes=[cause1|cause2|cause3|..], [retry_interval=n1|n2|n3|n4...],
         * [maximum_retries=n]"
         *
         * 3. Retry based on {@link NetworkCapabilities} and {@link DataFailCause}. Note that only
         *    APN-type network capabilities are supported.
         * "capabilities=[netCaps1|netCaps2|...], fail_causes=[cause1|cause2|cause3|...],
         *     [retry_interval=n1|n2|n3|n4...], [maximum_retries=n]"
         *
         * For example,
         * "capabilities=eims, retry_interval=1000, maximum_retries=20" means if the attached
         * network request is emergency, then retry data network setup every 1 second for up to 20
         * times.
         *
         * "fail_causes=8|27|28|29|30|32|33|35|50|51|111|-5|-6|65537|65538|-3|2253|2254
         * , maximum_retries=0" means for those fail causes, never retry with timers. Note that
         * when environment changes, retry can still happen.
         *
         * "capabilities=internet|enterprise|dun|ims|fota, retry_interval=2500|3000|"
         * "5000|10000|15000|20000|40000|60000|120000|240000|600000|1200000|1800000"
         * "1800000, maximum_retries=20" means for those capabilities, retry happens in 2.5s, 3s,
         * 5s, 10s, 15s, 20s, 40s, 1m, 2m, 4m, 10m, 20m, 30m, 30m, 30m, until reaching 20 retries.
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
                                    .map(DataUtils::getNetworkCapabilityFromString)
                                    .collect(Collectors.toSet());
                            break;
                        case RULE_TAG_FAIL_CAUSES:
                            mFailCauses = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .map(Integer::valueOf)
                                    .collect(Collectors.toSet());
                            break;
                        case RULE_TAG_RETRY_INTERVAL:
                            mRetryIntervalsMillis = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .map(Long::valueOf)
                                    .collect(Collectors.toList());
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

            if (mMaxRetries == 0) {
                mRetryIntervalsMillis = Collections.emptyList();
            }

            if (mMaxRetries < 0) {
                throw new IllegalArgumentException("Max retries should not be less than 0. "
                        + "mMaxRetries=" + mMaxRetries);
            }

            if (mRetryIntervalsMillis.stream().anyMatch(i -> i <= 0)) {
                throw new IllegalArgumentException("Retry interval should not be less than 0. "
                        + "mRetryIntervalsMillis=" + mRetryIntervalsMillis);
            }

            if (mNetworkCapabilities.size() == 0 && mFailCauses.size() == 0) {
                throw new IllegalArgumentException("illegal rule " + ruleString
                        + ". Should have either network capabilities or fail causes.");
            }
        }

        /**
         * @return The data network setup retry intervals in milliseconds. If this is empty, then
         * {@link #getMaxRetries()} must return 0.
         */
        public @NonNull List<Long> getRetryIntervalsMillis() {
            return mRetryIntervalsMillis;
        }

        /**
         * @return The maximum retry times. After reaching the retry times, data retry will not be
         * scheduled with timer. Only environment changes (e.g. Airplane mode, SIM state, RAT,
         * registration state changes, etc..) can trigger the retry. Note that if max retries
         * is 0, then {@link #getRetryIntervalsMillis()} must be {@code null}.
         */
        public int getMaxRetries() {
            return mMaxRetries;
        }

        /**
         * @return The network capabilities. Each data setup must be associated with at least one
         * network request. If that network request contains network capabilities specified here,
         * then retry will happen. Empty set indicates the retry rule is not using network
         * capabilities.
         */
        @VisibleForTesting
        public @NonNull @NetCapability Set<Integer> getNetworkCapabilities() {
            return mNetworkCapabilities;
        }

        /**
         * @return The fail causes. If data setup failed with certain fail causes, then retry will
         * happen. Empty set indicates the retry rule is not using the fail causes.
         */
        @VisibleForTesting
        public @NonNull @DataFailureCause Set<Integer> getFailCauses() {
            return mFailCauses;
        }

        /**
         * Check if this rule can be matched.
         *
         * @param networkCapabilities The network capabilities for matching.
         * @param cause Fail cause from previous setup data request.
         * @return {@code true} if the retry rule can be matched.
         */
        public boolean canBeMatched(@NonNull @NetCapability Set<Integer> networkCapabilities,
                @DataFailureCause int cause) {
            if (!mFailCauses.isEmpty() && !mFailCauses.contains(cause)) {
                return false;
            }

            return mNetworkCapabilities.isEmpty()
                    || mNetworkCapabilities.containsAll(networkCapabilities);
        }

        @Override
        public String toString() {
            return "[RetryRule: Network capabilities:" + DataUtils.networkCapabilitiesToString(
                    mNetworkCapabilities.stream().mapToInt(Number::intValue).toArray())
                    + ", Fail causes=" + mFailCauses
                    + ", Retry intervals=" + mRetryIntervalsMillis + ", Maximum retries="
                    + mMaxRetries + "]";
        }
    }

    /**
     * Data retry evaluation entry.
     */
    private static class DataRetryEvaluationEntry {
        /**
         * The data profile that has been used in the previous data network setup.
         */
        public final @NonNull DataProfile dataProfile;

        /**
         * The network requests attached to the previous data network setup.
         */
        public final @NonNull NetworkRequestList requestList;

        /**
         * The fail cause of previous data network setup.
         */
        public final @DataFailureCause int cause;

        /**
         * The data retry delay in milliseconds suggested by the network/data service.
         * {@link android.telephony.data.DataCallResponse#RETRY_DURATION_UNDEFINED} indicates
         * network /data service did not suggest retry or not. Telephony frameworks would use its
         * logic to perform data retry.
         */
        public final long retryDelayMillis;

        /**
         * Constructor.
         *
         * @param dataProfile The data profile that has been used in the previous data network
         * setup.
         * @param requestList The network requests attached to the previous data network setup.
         * @param cause The fail cause of previous data network setup.
         * @param retryDelayMillis The retry duration suggested by the network/data service.
         * {@link android.telephony.data.DataCallResponse#RETRY_DURATION_UNDEFINED} indicates
         * network /data service did not suggest retry or not. Telephony frameworks would use its
         * logic to perform data retry.
         */
        DataRetryEvaluationEntry(@NonNull DataProfile dataProfile,
                @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                long retryDelayMillis) {
            this.dataProfile = dataProfile;
            this.requestList = requestList;
            this.cause = cause;
            this.retryDelayMillis = retryDelayMillis;
        }
    }

    /**
     * Represent a data retry entry.
     */
    public static class DataRetryEntry {
        /**
         * To retry setup data with the same data profile.
         */
        public static final int RETRY_TYPE_DATA_PROFILE = 1;

        /**
         * To retry satisfying the network request with certain capabilities. Could be using a
         * different data profile.
         */
        public static final int RETRY_TYPE_NETWORK_CAPABILITIES = 2;

        @IntDef(prefix = {"RETRY_TYPE_"},
                value = {
                        RETRY_TYPE_DATA_PROFILE,
                        RETRY_TYPE_NETWORK_CAPABILITIES,
                })
        public @interface DataRetryType {}

        public static final int RETRY_STATE_NOT_RETRIED = 1;

        public static final int RETRY_STATE_FAILED = 2;

        public static final int RETRY_STATE_SUCCEEDED = 3;

        public static final int RETRY_STATE_CANCELLED = 4;

        @IntDef(prefix = {"RETRY_STATE_"},
                value = {
                        RETRY_STATE_NOT_RETRIED,
                        RETRY_STATE_FAILED,
                        RETRY_STATE_SUCCEEDED,
                        RETRY_STATE_CANCELLED
                })
        public @interface DataRetryState {}

        /** Data retry type. Could be retry by same data profile or same capabilities. */
        public final @DataRetryType int retryType;

        /** The network capabilities to satisfy when retry happens. */
        public final @NonNull @NetCapability Set<Integer> networkCapabilities;

        /** The data profile that will be used for retry. */
        public final @NonNull DataProfile dataProfile;

        /** The rule used for this data retry. {@code null} if the retry is requested by network. */
        public final @Nullable DataRetryRule appliedDataRetryRule;

        /** The retry delay in milliseconds. */
        public final long retryDelayMillis;

        /**
         * Retry system time. This is the system time retrieved from
         * {@link System#currentTimeMillis()}. For debugging purposes only.
         */
        private final @CurrentTimeMillisLong long mRetrySystemTime;

        /** The retry state. */
        private int mRetryState = RETRY_STATE_NOT_RETRIED;

        /** Timestamp when a state is set. For debugging purposes only. */
        private @CurrentTimeMillisLong long mRetryStateTimestamp = 0;

        /**
         * Constructor
         *
         * @param retryType Data retry type. Could be retry by same data profile or same
         * capabilities.
         * @param networkCapabilities The network capabilities to satisfy when retry happens.
         * @param dataProfile The data profile that will be used for retry.
         * @param appliedDataRetryRule The applied data retry rule.
         * @param retryDelayMillis The retry delay in milliseconds.
         */
        private DataRetryEntry(@DataRetryType int retryType,
                @NonNull Set<Integer> networkCapabilities, @NonNull DataProfile dataProfile,
                @Nullable DataRetryRule appliedDataRetryRule, long retryDelayMillis) {
            this.retryType = retryType;
            this.networkCapabilities = networkCapabilities;
            this.dataProfile = dataProfile;
            this.appliedDataRetryRule = appliedDataRetryRule;
            this.retryDelayMillis = retryDelayMillis;

            mRetryStateTimestamp = System.currentTimeMillis();
            mRetrySystemTime =  mRetryStateTimestamp + retryDelayMillis;
        }

        /**
         * Set the state of a data retry.
         *
         * @param state The retry state.
         */
        public void setState(@DataRetryState int state) {
            mRetryState = state;
            mRetryStateTimestamp = System.currentTimeMillis();
        }

        /**
         * @return Get the retry state.
         */
        public @DataRetryState int getState() {
            return mRetryState;
        }

        /**
         * Convert retry type to string.
         *
         * @param retryType Retry type.
         * @return Retry type in string format.
         */
        private static String retryTypeToString(@DataRetryType int retryType) {
            switch (retryType) {
                case RETRY_TYPE_DATA_PROFILE: return "BY_PROFILE";
                case RETRY_TYPE_NETWORK_CAPABILITIES: return "BY_CAPABILITIES";
                default: return "Unknown(" + retryType + ")";
            }
        }

        /**
         * Convert retry state to string.
         *
         * @param retryState Retry state.
         * @return Retry state in string format.
         */
        private static String retryStateToString(@DataRetryState int retryState) {
            switch (retryState) {
                case RETRY_STATE_NOT_RETRIED: return "NOT_RETRIED";
                case RETRY_STATE_FAILED: return "FAILED";
                case RETRY_STATE_SUCCEEDED: return "SUCCEEDED";
                case RETRY_STATE_CANCELLED: return "CANCELLED";
                default: return "Unknown(" + retryState + ")";
            }
        }

        @Override
        public String toString() {
            return "[DataRetryEntry: delay=" + retryDelayMillis + "ms, retry time:"
                    + DataUtils.systemTimeToString(mRetrySystemTime) + ", " + dataProfile
                    + ", retry type=" + retryTypeToString(retryType) + ", retry capabilities="
                    + DataUtils.networkCapabilitiesToString(networkCapabilities.stream()
                    .mapToInt(Number::intValue).toArray()) + ", applied rule="
                    + appliedDataRetryRule + ", state=" + retryStateToString(mRetryState)
                    + ", timestamp=" + DataUtils.systemTimeToString(mRetryStateTimestamp) + "]";
        }

        /** Data retry entry builder. */
        public static class Builder {
            /** Data retry type. Could be retry by same data profile or same capabilities. */
            private @DataRetryType int mRetryType;

            /** The network capabilities to satisfy when retry happens. */
            private @NonNull @NetCapability Set<Integer> mNetworkCapabilities = new ArraySet<>();

            /** The data profile that will be used for retry. */
            private @NonNull DataProfile mDataProfile;

            /**
             * The retry delay in milliseconds. Default is 5 seconds.
             */
            private long mRetryDelayMillis = TimeUnit.SECONDS.toMillis(5);

            /** The applied data retry rule. */
            private @Nullable DataRetryRule mAppliedDataRetryRule;

            /**
             * Set the data retry type.
             *
             * @param retryType Data retry type. Could be retry by same data profile or same
             * capabilities.
             * @return This builder.
             */
            public @NonNull Builder setRetryType(@DataRetryType int retryType) {
                mRetryType = retryType;
                return this;
            }

            /**
             * Set the network capabilities to satisfy when retry happens.
             *
             * @param networkCapabilities the network capabilities to satisfy when retry happens.
             * @return This builder.
             */
            public @NonNull Builder setNetworkCapabilities(
                    @NonNull @NetCapability Set<Integer> networkCapabilities) {
                mNetworkCapabilities = networkCapabilities;
                return this;
            }

            /**
             * Set the data profile that will be used for retry.
             *
             * @param dataProfile The data profile that will be used for retry.
             * @return This builder.
             */
            public @NonNull Builder setDataProfile(@NonNull DataProfile dataProfile) {
                mDataProfile = dataProfile;
                return this;
            }

            /**
             * Set the data retry delay.
             *
             * @param retryDelayMillis The retry delay in milliseconds.
             * @return This builder.
             */
            public @NonNull Builder setRetryDelay(long retryDelayMillis) {
                mRetryDelayMillis = retryDelayMillis;
                return this;
            }

            /**
             * Set the applied retry rule.
             *
             * @param dataRetryRule The rule that used for this data retry.
             * @return This builder.
             */
            public @NonNull Builder setAppliedRetryRule(@NonNull DataRetryRule dataRetryRule) {
                mAppliedDataRetryRule = dataRetryRule;
                return this;
            }

            /** Builder the retry entry. */
            public @NonNull DataRetryEntry build() {
                return new DataRetryEntry(mRetryType, mNetworkCapabilities, mDataProfile,
                        mAppliedDataRetryRule, mRetryDelayMillis);
            }
        }
    }

    /** Data retry callback. This should be only used by {@link DataNetworkController}. */
    public abstract static class DataRetryManagerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataRetryManagerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when data retry occurs.
         *
         * @param dataRetryEntry The data retry entry.
         */
        public abstract void onDataRetry(@NonNull DataRetryEntry dataRetryEntry);
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataNetworkController Data network controller.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param dataRetryManagerCallback Data retry callback.
     */
    public DataRetryManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController, @NonNull Looper looper,
            @NonNull DataRetryManagerCallback dataRetryManagerCallback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DRM-" + mPhone.getPhoneId();
        mDataRetryManagerCallback = dataRetryManagerCallback;

        mDataConfigManager = dataNetworkController.getDataConfigManager();
        mDataProfileManager = dataNetworkController.getDataProfileManager();
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_DATA_RETRY_EVALUATION:
                DataRetryEvaluationEntry evaluationEntry = (DataRetryEvaluationEntry) msg.obj;
                onEvaluateDataRetry(evaluationEntry.dataProfile, evaluationEntry.requestList,
                        evaluationEntry.cause, evaluationEntry.retryDelayMillis);
                break;
            case EVENT_DATA_RETRY:
                mDataRetryManagerCallback.invokeFromExecutor(
                        () -> mDataRetryManagerCallback.onDataRetry((DataRetryEntry) msg.obj));
                break;
            case EVENT_CANCEL_ALL_DATA_RETRIES:
                onCancelAllRetries();
                break;
            default:
                loge("Unexpected message " + msg.what);
        }
    }

    /**
     * Called when data config is updated.
     */
    private void onDataConfigUpdated() {
        cancelAllDataRetries();
        mDataRetryRuleList = mDataConfigManager.getDataRetryRules();
    }

    /**
     * Cancel all pending data retries.
     */
    public void cancelAllDataRetries() {
        sendMessageAtFrontOfQueue(obtainMessage(EVENT_CANCEL_ALL_DATA_RETRIES));
    }

    /**
     * Called when setup data failed. Evaluate if data retry is needed or not. If needed, retry will
     * be scheduled automatically after evaluation.
     *
     * @param dataProfile The data profile that has been used in the previous data network setup.
     * @param requestList The network requests attached to the previous data network setup.
     * @param cause The fail cause of previous data network setup.
     * @param retryTimeMillis The retry time in milliseconds suggested by the network/data service.
     * {@link android.telephony.data.DataCallResponse#RETRY_DURATION_UNDEFINED} indicates network
     * /data service did not suggest retry or not. Telephony frameworks would use its logic to
     * perform data retry.
     */
    public void evaluateDataRetry(@NonNull DataProfile dataProfile,
            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
            long retryTimeMillis) {
        sendMessage(obtainMessage(EVENT_DATA_RETRY_EVALUATION, new DataRetryEvaluationEntry(
                dataProfile, requestList, cause, retryTimeMillis)));
    }

    /**
     * Called when setup data failed to evaluate whether a data retry is needed or not. If
     * needed, retry will be scheduled automatically after evaluation.
     *
     * @param dataProfile The data profile that has been used in the previous data network setup.
     * @param requestList The network requests attached to the previous data network setup.
     * @param cause The fail cause of previous data network setup.
     * @param retryDelayMillis The retry delay in milliseconds suggested by the network/data
     * service. {@link android.telephony.data.DataCallResponse#RETRY_DURATION_UNDEFINED}
     * indicates network/data service did not suggest retry or not. Telephony frameworks would use
     * its logic to perform data retry.
     */
    private void onEvaluateDataRetry(@NonNull DataProfile dataProfile,
            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
            long retryDelayMillis) {
        log("onEvaluateDataRetry: " + dataProfile + ", cause=" + DataFailCause.toString(cause)
                + ", retryDelayMillis=" + retryDelayMillis + "ms" + ", " + requestList);
        // Check if network suggested never retry for this data profile. Note that for HAL 1.5
        // and older, Integer.MAX_VALUE indicates never retry. For 1.6 or above, Long.MAX_VALUE
        // indicates never retry.
        if (retryDelayMillis == Long.MAX_VALUE || retryDelayMillis == Integer.MAX_VALUE) {
            logl("Network suggested never retry for " + dataProfile);
            // mDataThrottler...
        } else if (retryDelayMillis != DataCallResponse.RETRY_DURATION_UNDEFINED) {
            // Network specifically asks retry the previous data profile again.
            schedule(new DataRetryEntry.Builder()
                    .setRetryType(DataRetryEntry.RETRY_TYPE_DATA_PROFILE)
                    .setDataProfile(dataProfile)
                    .setRetryDelay(retryDelayMillis)
                    .build());
        } else {
            // Network did not suggest any retry. Use the configured rules to perform retry.

            // Extract APN-types capabilities from each network requests attached to the failed
            // setup. For most of the cases, there should be only one capability in the inner set,
            // and there should be only one in the outer set.
            Set<Set<Integer>> networkCapabilitiesSet = new ArraySet<>();
            for (TelephonyNetworkRequest networkRequest : requestList) {
                networkCapabilitiesSet.add(Arrays.stream(networkRequest.getApnTypesCapabilities())
                        .boxed()
                        .collect(Collectors.toSet()));
            }
            logv("networkCapabilitiesSet=" + networkCapabilitiesSet);

            // Matching the rule in configured order.
            for (DataRetryRule retryRule : mDataRetryRuleList) {
                // Now check if the retry rule contains the capabilities from the network request
                // list.
                Set<Integer> matchedCapabilities = networkCapabilitiesSet.stream()
                        .filter(caps -> retryRule.canBeMatched(caps, cause))
                        .findFirst()
                        .orElse(Collections.emptySet());
                logv("retryRule=" + retryRule + ", matchedCapabilities="
                        + DataUtils.networkCapabilitiesToString(matchedCapabilities.stream()
                        .mapToInt(Number::intValue).toArray()));
                if (!matchedCapabilities.isEmpty()) {
                    int failedCount = getRetryFailedCount(matchedCapabilities, retryRule);
                    log("Found matching rule " + retryRule + ", failed count=" + failedCount);
                    if (failedCount == retryRule.getMaxRetries()) {
                        log("Data retry failed for " + failedCount + " times. Stopped "
                                + "timer-based data retry. Condition-based retry will still happen "
                                + "when condition changes.");
                        return;
                    }

                    retryDelayMillis = retryRule.getRetryIntervalsMillis().get(
                            Math.min(failedCount, retryRule.getRetryIntervalsMillis().size() - 1));

                    // Try a different data profile that hasn't been tried for the longest
                    // time and see if that works.
                    List<DataProfile> dataProfiles = mDataProfileManager
                            .getDataProfilesForNetworkCapabilities(matchedCapabilities.stream()
                                    .mapToInt(Number::intValue).toArray());
                    if (dataProfiles.isEmpty()) {
                        loge("Can't find any data profiles for retrying.");
                        return;
                    }

                    // Schedule a data retry.
                    schedule(new DataRetryEntry.Builder()
                            .setRetryType(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES)
                            .setRetryDelay(retryDelayMillis)
                            .setDataProfile(dataProfiles.get(0))
                            .setNetworkCapabilities(matchedCapabilities)
                            .setAppliedRetryRule(retryRule)
                            .build());
                    return;
                }
            }
            log("onEvaluateDataRetry: Did not match any retry rule. Stopped timer-based retry.");
        }
    }

    /** Cancel all retries. */
    private void onCancelAllRetries() {
        removeMessages(EVENT_DATA_RETRY);
        for (DataRetryEntry retryEntry : mDataRetryEntries) {
            if (retryEntry.getState() == DataRetryEntry.RETRY_STATE_NOT_RETRIED) {
                retryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            }
        }
    }

    /**
     * Count how many times the same retry rules has been used for these capabilities since last
     * success data setup.
     *
     * @param networkCapabilities The network capabilities to check.
     * @param dataRetryRule The data retry rule.
     * @return The failed count since last successful data setup.
     */
    private int getRetryFailedCount(@NonNull Set<Integer> networkCapabilities,
            @NonNull DataRetryRule dataRetryRule) {
        int count = 0;
        for (int i = mDataRetryEntries.size() - 1; i >= 0; i--) {
            DataRetryEntry entry = mDataRetryEntries.get(i);
            // count towards the last succeeded data setup.
            if (entry.retryType == DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES
                    && entry.networkCapabilities.equals(networkCapabilities)
                    && entry.appliedDataRetryRule.equals(dataRetryRule)) {
                if (entry.getState() == DataRetryEntry.RETRY_STATE_SUCCEEDED
                        || entry.getState() == DataRetryEntry.RETRY_STATE_CANCELLED) {
                    break;
                }
                count++;
            }
        }
        return count;
    }

    /**
     * Schedule the data retry.
     *
     * @param dataRetryEntry The retry entry.
     */
    private void schedule(@NonNull DataRetryEntry dataRetryEntry) {
        logl("Scheduled data retry: " + dataRetryEntry);
        mDataRetryEntries.add(dataRetryEntry);
        if (mDataRetryEntries.size() > MAXIMUM_HISTORICAL_RETRY_ENTRIES) {
            // Discard the oldest retry entry.
            mDataRetryEntries.remove(0);
        }

        // Using delayed message instead of alarm manager to schedule data retry is intentional.
        // When the device enters doze mode, the handler message might be extremely delayed than the
        // original scheduled time. There is no need to wake up the device to perform data retry in
        // that case.
        sendMessageDelayed(obtainMessage(EVENT_DATA_RETRY, dataRetryEntry),
                dataRetryEntry.retryDelayMillis);
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
     * Log verbose messages.
     * @param s debug messages.
     */
    private void logv(@NonNull String s) {
        if (VDBG) Rlog.v(mLogTag, s);
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

        pw.println("Retry entries:");
        pw.increaseIndent();
        for (DataRetryEntry entry : mDataRetryEntries) {
            pw.println(entry);
        }
        pw.decreaseIndent();

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
