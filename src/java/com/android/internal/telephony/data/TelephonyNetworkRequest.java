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
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.data.DataProfile;

import com.android.internal.telephony.Phone;

import java.util.Arrays;

/**
 * TelephonyNetworkRequest is a wrapper class on top of {@link NetworkRequest}, which is originated
 * from the apps to request network. In addition to t{@link NetworkRequest}, this class is used
 * to track more telephony specific items such as priority, attached data network, etc...
 *
 * The reason that TelephonyNetworkRequest does not extend {@link NetworkRequest} is that
 * the constructor of NetworkRequest is a hidden API.
 */
public class TelephonyNetworkRequest implements Parcelable {
    private final @NonNull NetworkRequest mNetworkRequest;

    /**
     * Priority of the network request. The network request has higher priority will be satisfied
     * first than lower priority ones.
     */
    private final int mPriority;

    private @Nullable DataNetwork mAttachedDataNetwork;

    /**
     * Constructor
     *
     * @param request The native network request from the clients.
     * @param phone The phone instance
     */
    public TelephonyNetworkRequest(NetworkRequest request, Phone phone) {
        mNetworkRequest = request;
        DataConfigManager dcm = phone.getDataNetworkController().getDataConfigManager();
        mPriority = Arrays.stream(request.getCapabilities())
                .map(dcm::getNetworkCapabilityPriority)
                .max()
                .orElse(0);
    }

    /**
     * Create the request from the parcel.
     *
     * @param p The parcel.
     */
    private TelephonyNetworkRequest(Parcel p) {
        mNetworkRequest = p.readParcelable(NetworkRequest.class.getClassLoader());
        mPriority = p.readInt();
    }

    /**
     * @see NetworkRequest#getNetworkSpecifier()
     */
    public @Nullable NetworkSpecifier getNetworkSpecifier() {
        return mNetworkRequest.getNetworkSpecifier();
    }

    /**
     * @see NetworkRequest#getCapabilities()
     */
    public @NonNull int[] getCapabilities() {
        return mNetworkRequest.getCapabilities();
    }

    /**
     * @see NetworkRequest#hasCapability(int)
     */
    public boolean hasCapability(int capability) {
        return mNetworkRequest.hasCapability(capability);
    }

    /**
     * @see NetworkRequest#canBeSatisfiedBy(NetworkCapabilities)
     */
    public boolean canBeSatisfiedBy(@Nullable NetworkCapabilities nc) {
        return mNetworkRequest.canBeSatisfiedBy(nc);
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

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mNetworkRequest, flags);
        dest.writeInt(mPriority);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Parcelable.Creator<TelephonyNetworkRequest> CREATOR =
            new Parcelable.Creator<TelephonyNetworkRequest>() {
                @Override
                public TelephonyNetworkRequest createFromParcel(Parcel source) {
                    return new TelephonyNetworkRequest(source);
                }

                @Override
                public TelephonyNetworkRequest[] newArray(int size) {
                    return new TelephonyNetworkRequest[size];
                }
            };

    @Override
    public String toString() {
        return "[" + mNetworkRequest.toString() + ", mPriority=" + mPriority
                + ", mAttachedDataNetwork=" + mAttachedDataNetwork != null
                ? mAttachedDataNetwork.getLogTag() : null + "]";
    }
}
