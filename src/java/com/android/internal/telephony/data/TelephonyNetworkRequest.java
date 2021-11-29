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

import android.annotation.CurrentTimeMillisLong;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.telephony.Annotation.NetCapability;
import android.telephony.data.ApnSetting;

import com.android.internal.telephony.Phone;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * TelephonyNetworkRequest is a wrapper class on top of {@link NetworkRequest}, which is originated
 * from the apps to request network. This class is intended to track supplemental information
 * related to this request, for example priority, evaluation result, whether this request is
 * actively being satisfied, timestamp, etc...
 *
 */
public class TelephonyNetworkRequest {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"REQUEST_STATE_"},
            value = {
                    REQUEST_STATE_UNSATISFIED,
                    REQUEST_STATE_SATISFIED})
    public @interface RequestState {}

    /**
     * Indicating currently no data networks can satisfy this network request.
     */
    public static final int REQUEST_STATE_UNSATISFIED = 0;

    /**
     * Indicating this request is already satisfied. It must have an attached network (which could
     * be in any state, including disconnecting). Also note this does not mean the network request
     * is satisfied in telephony layer. Whether the network request is finally satisfied or not is
     * determined at the connectivity service layer.
     */
    public static final int REQUEST_STATE_SATISFIED = 1;

    /**
     * Native network request from the clients. See {@link NetworkRequest};
     */
    private final @NonNull NetworkRequest mNativeNetworkRequest;

    /**
     * Priority of the network request. The network request has higher priority will be satisfied
     * first than lower priority ones.
     */
    private int mPriority;

    /**
     * Data config manager for retrieving data config.
     */
    private final @NonNull DataConfigManager mDataConfigManager;

    /**
     * The attached data network. Note that the data network could be in any state. {@code null}
     * indicates this network request is not satisfied.
     */
    private @Nullable DataNetwork mAttachedDataNetwork;

    /**
     * The state of the network request.
     *
     * @see #REQUEST_STATE_UNSATISFIED
     * @see #REQUEST_STATE_SATISFIED
     */
    // This is not a boolean because there might be more states in the future.
    private @RequestState int mState;

    /** The timestamp when this network request enters telephony. */
    private final @CurrentTimeMillisLong long mCreatedTimeMillis;

    /** The data evaluation result. */
    private @Nullable DataEvaluation mEvaluation;

    /**
     * Constructor
     *
     * @param request The native network request from the clients.
     * @param phone The phone instance
     */
    public TelephonyNetworkRequest(NetworkRequest request, Phone phone) {
        mNativeNetworkRequest = request;
        mDataConfigManager = phone.getDataNetworkController().getDataConfigManager();

        mPriority = 0;
        mAttachedDataNetwork = null;
        // When the request was first created, it is in active state so we can actively attempt
        // to satisfy it.
        mState = REQUEST_STATE_UNSATISFIED;
        mCreatedTimeMillis = System.currentTimeMillis();

        updatePriority();
    }

    /**
     * @see NetworkRequest#getNetworkSpecifier()
     */
    public @Nullable NetworkSpecifier getNetworkSpecifier() {
        return mNativeNetworkRequest.getNetworkSpecifier();
    }

    /**
     * @see NetworkRequest#getCapabilities()
     */
    public @NonNull @NetCapability int[] getCapabilities() {
        return mNativeNetworkRequest.getCapabilities();
    }

    /**
     * @see NetworkRequest#hasCapability(int)
     */
    public boolean hasCapability(@NetCapability int capability) {
        return mNativeNetworkRequest.hasCapability(capability);
    }

    /**
     * @see NetworkRequest#canBeSatisfiedBy(NetworkCapabilities)
     */
    public boolean canBeSatisfiedBy(@Nullable NetworkCapabilities nc) {
        return mNativeNetworkRequest.canBeSatisfiedBy(nc);
    }

    /**
     * Get the priority of the network request.
     *
     * @return The priority from 0 to 100. 100 indicates the highest priority.
     */
    public int getPriority() {
        return mPriority;
    }

    /**
     * Update the priority from data config manager.
     */
    public void updatePriority() {
        mPriority = Arrays.stream(mNativeNetworkRequest.getCapabilities())
                .map(mDataConfigManager::getNetworkCapabilityPriority)
                .max()
                .orElse(0);
    }

    /**
     * Get the highest priority network capability from the network request. Note that only APN-type
     * capabilities are supported here because this is currently used for transport selection and
     * data retry.
     *
     * @return The highest priority network capability from this network request.
     */
    public @NetCapability int getHighestPriorityNetworkCapability() {
        int highestPriority = 0;
        int highestPriorityCapability = -1;
        for (int capability : getCapabilities()) {
            // Convert the capability to APN type. For non-APN-type capabilities, TYPE_NONE is
            // returned.
            int apnType = DataUtils.networkCapabilityToApnType(capability);
            if (apnType != ApnSetting.TYPE_NONE) {
                int priority = mDataConfigManager.getNetworkCapabilityPriority(capability);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestPriorityCapability = capability;
                }
            }
        }
        return highestPriorityCapability;
    }

    /**
     * Get the capabilities that can be translated to APN types.
     *
     * @return The capabilities that can be translated to APN types.
     */
    public @NonNull @NetCapability int[] getApnTypesCapabilities() {
        return Arrays.stream(getCapabilities()).boxed()
                .filter(cap -> DataUtils.networkCapabilityToApnType(cap) != ApnSetting.TYPE_NONE)
                .mapToInt(Number::intValue)
                .toArray();
    }

    /**
     * @return The native network request.
     */
    public @NonNull NetworkRequest getNativeNetworkRequest() {
        return mNativeNetworkRequest;
    }

    /**
     * Set the attached data network.
     *
     * @param dataNetwork The data network.
     */
    public void setAttachedNetwork(@NonNull DataNetwork dataNetwork) {
        mAttachedDataNetwork = dataNetwork;
    }

    /**
     * @return The attached network. {@code null} indicates the request is not attached to any
     * network (i.e. the request is unsatisfied).
     */
    public @Nullable DataNetwork getAttachedNetwork() {
        return mAttachedDataNetwork;
    }

    /**
     * Set the state of the network request.
     *
     * @param state The state.
     */
    public void setState(@RequestState int state) {
        mState = state;
    }

    /**
     * @return The state of the network request.
     */
    public @RequestState int getState() {
        return mState;
    }

    /**
     * Set the data evaluation result.
     *
     * @param evaluation The data evaluation result.
     */
    public void setEvaluation(@NonNull DataEvaluation evaluation) {
        mEvaluation = evaluation;
    }

    /**
     * Convert the telephony request state to string.
     *
     * @param state The request state.
     * @return The request state in string format.
     */
    private static @NonNull String requestStateToString(
            @TelephonyNetworkRequest.RequestState int state) {
        switch (state) {
            case TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED: return "UNSATISFIED";
            case TelephonyNetworkRequest.REQUEST_STATE_SATISFIED: return "SATISFIED";
            default: return "UNKNOWN(" + Integer.toString(state) + ")";
        }
    }

    @Override
    public String toString() {
        return "[" + mNativeNetworkRequest.toString() + ", mPriority=" + mPriority
                + ", state=" + requestStateToString(mState)
                + ", mAttachedDataNetwork=" + (mAttachedDataNetwork != null
                ? mAttachedDataNetwork.name() : null) + ", created time="
                + DataUtils.systemTimeToString(mCreatedTimeMillis)
                + ", evaluation result=" + mEvaluation + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TelephonyNetworkRequest that = (TelephonyNetworkRequest) o;
        // Only compare the native network request.
        return mNativeNetworkRequest.equals(that.mNativeNetworkRequest);
    }

    @Override
    public int hashCode() {
        // Only use the native network request's hash code.
        return mNativeNetworkRequest.hashCode();
    }
}
