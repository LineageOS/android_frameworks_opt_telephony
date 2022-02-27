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
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.telephony.data.DataProfile;

import com.android.internal.telephony.Phone;

import java.util.Arrays;

/**
 * TelephonyNetworkRequest is a wrapper class on top of {@link NetworkRequest}, which is originated
 * from the apps to request network. In addition to to {@link NetworkRequest}, this class is used
 * to track more telephony specific items such as priority, attached data network, etc...
 *
 * TelephonyNetworkRequest is not a pure container class.
 */
public class TelephonyNetworkRequest {
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
    private final DataConfigManager mDataConfigManager;

    /**
     * The attached data network. Note that the data network could be in any state. {@code null}
     * indicates this network request is not satisfied.
     */
    private @Nullable DataNetwork mAttachedDataNetwork;

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
    public @NonNull int[] getCapabilities() {
        return mNativeNetworkRequest.getCapabilities();
    }

    /**
     * @see NetworkRequest#hasCapability(int)
     */
    public boolean hasCapability(int capability) {
        return mNativeNetworkRequest.hasCapability(capability);
    }

    /**
     * @see NetworkRequest#canBeSatisfiedBy(NetworkCapabilities)
     */
    public boolean canBeSatisfiedBy(@Nullable NetworkCapabilities nc) {
        return mNativeNetworkRequest.canBeSatisfiedBy(nc);
    }

    /**
     * Check if a data profile can satisfy the network request.
     *
     * @param dataProfile The data profile
     * @return {@code true} if this network request can be satisfied by the data profile, meaning
     * the network brought up with this data profile can satisfy the network request.
     */
    public boolean canBeSatisfiedBy(@NonNull DataProfile dataProfile) {
        return true;
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

    @Override
    public String toString() {
        return "[" + mNativeNetworkRequest.toString() + ", mPriority=" + mPriority
                + ", mAttachedDataNetwork=" + (mAttachedDataNetwork != null
                ? mAttachedDataNetwork.getLogTag() : null) + "]";
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
