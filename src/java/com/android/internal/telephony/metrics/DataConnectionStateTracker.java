/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;

/**
 *  This Tracker is monitoring precise data connection states for each APNs which are used for IMS
 * calling such as IMS and Emergency APN. It uses a SparseArray to track each SIM's connection
 * state.
 *  The tracker is started by {@link VoiceCallSessionStats} and update the states to
 * VoiceCallSessionStats directly.
 */
public class DataConnectionStateTracker {
    private static final SparseArray<DataConnectionStateTracker> sDataConnectionStateTracker =
            new SparseArray<>();
    private final Executor mExecutor;
    private Phone mPhone;
    private int mSubId;
    private HashMap<Integer, PreciseDataConnectionState> mLastPreciseDataConnectionState =
            new HashMap<>();
    private PreciseDataConnectionStateListenerImpl mDataConnectionStateListener;

    private final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionsChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override
                public void onSubscriptionsChanged() {
                    if (mPhone == null) {
                        return;
                    }
                    int newSubId = mPhone.getSubId();
                    if (mSubId == newSubId
                            || newSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        return;
                    }

                    unregisterTelephonyListener();
                    mSubId = newSubId;
                    registerTelephonyListener(mSubId);
                }
            };

    private DataConnectionStateTracker() {
        HandlerThread handlerThread =
                new HandlerThread(DataConnectionStateTracker.class.getSimpleName());
        handlerThread.start();
        mExecutor = new HandlerExecutor(new Handler(handlerThread.getLooper()));
    }

    /** Getting or Creating DataConnectionStateTracker based on phoneId */
    public static synchronized DataConnectionStateTracker getInstance(int phoneId) {
        DataConnectionStateTracker dataConnectionStateTracker =
                sDataConnectionStateTracker.get(phoneId);
        if (dataConnectionStateTracker != null) {
            return dataConnectionStateTracker;
        }

        dataConnectionStateTracker = new DataConnectionStateTracker();
        sDataConnectionStateTracker.put(phoneId, dataConnectionStateTracker);
        return dataConnectionStateTracker;
    }

    /** Starting to monitor the precise data connection states */
    public void start(Phone phone) {
        mPhone = phone;
        mSubId = mPhone.getSubId();
        registerTelephonyListener(mSubId);
        SubscriptionManager mSubscriptionManager = mPhone.getContext()
                .getSystemService(SubscriptionManager.class);
        if (mSubscriptionManager != null) {
            mSubscriptionManager
                    .addOnSubscriptionsChangedListener(mExecutor, mSubscriptionsChangedListener);
        }
    }

    /** Stopping monitoring for the precise data connection states */
    public void stop() {
        if (mPhone == null) {
            return;
        }
        SubscriptionManager mSubscriptionManager = mPhone.getContext()
                .getSystemService(SubscriptionManager.class);
        if (mSubscriptionManager != null) {
            mSubscriptionManager
                    .removeOnSubscriptionsChangedListener(mSubscriptionsChangedListener);
        }
        unregisterTelephonyListener();
        mPhone = null;
        mLastPreciseDataConnectionState.clear();
    }

    /** Returns data state of the last notified precise data connection state for apn type */
    public int getDataState(int apnType) {
        if (!mLastPreciseDataConnectionState.containsKey(apnType)) {
            return TelephonyManager.DATA_UNKNOWN;
        }
        return mLastPreciseDataConnectionState.get(apnType).getState();
    }

    private void registerTelephonyListener(int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return;
        }
        TelephonyManager telephonyManager =
                mPhone.getContext().getSystemService(TelephonyManager.class);
        if (telephonyManager != null) {
            mDataConnectionStateListener = new PreciseDataConnectionStateListenerImpl(mExecutor);
            mDataConnectionStateListener.register(telephonyManager.createForSubscriptionId(subId));
        }
    }

    private void unregisterTelephonyListener() {
        if (mDataConnectionStateListener != null) {
            mDataConnectionStateListener.unregister();
            mDataConnectionStateListener = null;
        }
    }

    @VisibleForTesting
    public void notifyDataConnectionStateChanged(PreciseDataConnectionState connectionState) {
        List<Integer> apnTypes = connectionState.getApnSetting().getApnTypes();
        if (apnTypes != null) {
            for (int apnType : apnTypes) {
                mLastPreciseDataConnectionState.put(apnType, connectionState);
            }
        }

        mPhone.getVoiceCallSessionStats().onPreciseDataConnectionStateChanged(connectionState);
    }

    private class PreciseDataConnectionStateListenerImpl extends TelephonyCallback
            implements TelephonyCallback.PreciseDataConnectionStateListener {
        private final Executor mExecutor;
        private TelephonyManager mTelephonyManager = null;

        PreciseDataConnectionStateListenerImpl(Executor executor) {
            mExecutor = executor;
        }

        public void register(TelephonyManager tm) {
            if (tm == null) {
                return;
            }
            mTelephonyManager = tm;
            mTelephonyManager.registerTelephonyCallback(mExecutor, this);
        }

        public void unregister() {
            if (mTelephonyManager != null) {
                mTelephonyManager.unregisterTelephonyCallback(this);
                mTelephonyManager = null;
            }
        }

        @Override
        public void onPreciseDataConnectionStateChanged(
                PreciseDataConnectionState connectionState) {
            notifyDataConnectionStateChanged(connectionState);
        }
    }
}
