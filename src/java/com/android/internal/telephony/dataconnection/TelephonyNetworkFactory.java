/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.StringNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import android.util.LocalLog;

import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.SubscriptionMonitor;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;

public class TelephonyNetworkFactory extends NetworkFactory {
    public final String LOG_TAG;
    protected static final boolean DBG = true;

    private final PhoneSwitcher mPhoneSwitcher;
    private final SubscriptionController mSubscriptionController;
    private final SubscriptionMonitor mSubscriptionMonitor;
    private final DcTracker mDcTracker;

    private final HashMap<NetworkRequest, LocalLog> mDefaultRequests =
            new HashMap<NetworkRequest, LocalLog>();
    private final HashMap<NetworkRequest, LocalLog> mSpecificRequests =
            new HashMap<NetworkRequest, LocalLog>();

    private final int mPhoneId;
    // Only when this network factory is active, it will apply any network requests.
    private boolean mIsActive;
    // Whether this network factory is active and should handle default network requests.
    // Default network requests are those that don't specify subscription ID.
    private boolean mIsActiveForDefault;
    private int mSubscriptionId;

    private final static int TELEPHONY_NETWORK_SCORE = 50;

    private final Handler mInternalHandler;
    private static final int EVENT_ACTIVE_PHONE_SWITCH          = 1;
    private static final int EVENT_SUBSCRIPTION_CHANGED         = 2;
    private static final int EVENT_NETWORK_REQUEST              = 3;
    private static final int EVENT_NETWORK_RELEASE              = 4;

    public TelephonyNetworkFactory(PhoneSwitcher phoneSwitcher,
            SubscriptionController subscriptionController, SubscriptionMonitor subscriptionMonitor,
            Looper looper, Context context, int phoneId, DcTracker dcTracker) {
        super(looper, context, "TelephonyNetworkFactory[" + phoneId + "]", null);
        mInternalHandler = new InternalHandler(looper);

        setCapabilityFilter(makeNetworkFilter(subscriptionController, phoneId));
        setScoreFilter(TELEPHONY_NETWORK_SCORE);

        mPhoneSwitcher = phoneSwitcher;
        mSubscriptionController = subscriptionController;
        mSubscriptionMonitor = subscriptionMonitor;
        mPhoneId = phoneId;
        LOG_TAG = "TelephonyNetworkFactory[" + phoneId + "]";
        mDcTracker = dcTracker;

        mIsActive = false;
        mPhoneSwitcher.registerForActivePhoneSwitch(mInternalHandler, EVENT_ACTIVE_PHONE_SWITCH,
                null);

        mSubscriptionId = INVALID_SUBSCRIPTION_ID;
        mSubscriptionMonitor.registerForSubscriptionChanged(mPhoneId, mInternalHandler,
                EVENT_SUBSCRIPTION_CHANGED, null);

        mIsActiveForDefault = false;

        register();
    }

    private NetworkCapabilities makeNetworkFilter(SubscriptionController subscriptionController,
            int phoneId) {
        final int subscriptionId = subscriptionController.getSubIdUsingPhoneId(phoneId);
        return makeNetworkFilter(subscriptionId);
    }

    private NetworkCapabilities makeNetworkFilter(int subscriptionId) {
        NetworkCapabilities nc = new NetworkCapabilities();
        nc.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_IA);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_RCS);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        nc.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        nc.setNetworkSpecifier(new StringNetworkSpecifier(String.valueOf(subscriptionId)));
        return nc;
    }

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_ACTIVE_PHONE_SWITCH: {
                    onActivePhoneSwitch();
                    break;
                }
                case EVENT_SUBSCRIPTION_CHANGED: {
                    onSubIdChange();
                    break;
                }
                case EVENT_NETWORK_REQUEST: {
                    onNeedNetworkFor(msg);
                    break;
                }
                case EVENT_NETWORK_RELEASE: {
                    onReleaseNetworkFor(msg);
                    break;
                }
            }
        }
    }

    private static final int REQUEST_LOG_SIZE = 40;

    private static final int ACTION_NO_OP   = 0;
    private static final int ACTION_REQUEST = 1;
    private static final int ACTION_RELEASE = 2;

    private void applyRequests(HashMap<NetworkRequest, LocalLog> requestMap,
            int action, String logStr) {
        if (action == ACTION_NO_OP) return;

        for (NetworkRequest networkRequest : requestMap.keySet()) {
            LocalLog localLog = requestMap.get(networkRequest);
            localLog.log(logStr);
            if (action == ACTION_REQUEST) {
                mDcTracker.requestNetwork(networkRequest, localLog);
            } else if (action == ACTION_RELEASE) {
                mDcTracker.releaseNetwork(networkRequest, localLog);
            }
        }
    }

    private static int getAction(boolean wasActive, boolean isActive) {
        if (!wasActive && isActive) {
            return ACTION_REQUEST;
        } else if (wasActive && !isActive) {
            return ACTION_RELEASE;
        } else {
            return ACTION_NO_OP;
        }
    }

    // apply or revoke requests if our active-ness changes
    private void onActivePhoneSwitch() {
        final boolean newIsActive = mPhoneSwitcher.shouldApplySpecifiedRequests(mPhoneId);
        final boolean newIsActiveForDefault =
                mPhoneSwitcher.shouldApplyUnspecifiedRequests(mPhoneId);

        String logString = "onActivePhoneSwitch(newIsActive " + newIsActive + ", "
                + "newIsActiveForDefault " + newIsActiveForDefault + ")";
        if (DBG) log(logString);

        applyRequests(mSpecificRequests, getAction(mIsActive, newIsActive), logString);
        applyRequests(mDefaultRequests, getAction(mIsActiveForDefault, newIsActiveForDefault),
                logString);

        mIsActive = newIsActive;
        mIsActiveForDefault = newIsActiveForDefault;
    }

    // watch for phone->subId changes, reapply new filter and let
    // that flow through to apply/revoke of requests
    private void onSubIdChange() {
        final int newSubscriptionId = mSubscriptionController.getSubIdUsingPhoneId(mPhoneId);
        if (mSubscriptionId != newSubscriptionId) {
            if (DBG) log("onSubIdChange " + mSubscriptionId + "->" + newSubscriptionId);
            mSubscriptionId = newSubscriptionId;
            setCapabilityFilter(makeNetworkFilter(mSubscriptionId));
        }
    }

    @Override
    public void needNetworkFor(NetworkRequest networkRequest, int score) {
        Message msg = mInternalHandler.obtainMessage(EVENT_NETWORK_REQUEST);
        msg.obj = networkRequest;
        msg.sendToTarget();
    }

    private void onNeedNetworkFor(Message msg) {
        NetworkRequest networkRequest = (NetworkRequest)msg.obj;
        boolean isApplicable = false;
        LocalLog localLog = null;
        if (networkRequest.networkCapabilities.getNetworkSpecifier() == null) {
            // request only for the default network
            localLog = mDefaultRequests.get(networkRequest);
            if (localLog == null) {
                localLog = new LocalLog(REQUEST_LOG_SIZE);
                localLog.log("created for " + networkRequest);
                mDefaultRequests.put(networkRequest, localLog);
                isApplicable = mIsActiveForDefault;
            }
        } else {
            localLog = mSpecificRequests.get(networkRequest);
            if (localLog == null) {
                localLog = new LocalLog(REQUEST_LOG_SIZE);
                mSpecificRequests.put(networkRequest, localLog);
                isApplicable = mIsActive;
            }
        }
        if (isApplicable) {
            String s = "onNeedNetworkFor";
            localLog.log(s);
            log(s + " " + networkRequest);
            mDcTracker.requestNetwork(networkRequest, localLog);
        } else {
            String s = "not acting - isApplicable=" + isApplicable + ", mIsActive=" + mIsActive;
            localLog.log(s);
            log(s + " " + networkRequest);
        }
    }

    @Override
    public void releaseNetworkFor(NetworkRequest networkRequest) {
        Message msg = mInternalHandler.obtainMessage(EVENT_NETWORK_RELEASE);
        msg.obj = networkRequest;
        msg.sendToTarget();
    }

    private void onReleaseNetworkFor(Message msg) {
        NetworkRequest networkRequest = (NetworkRequest)msg.obj;
        LocalLog localLog = null;
        boolean isApplicable = false;
        if (networkRequest.networkCapabilities.getNetworkSpecifier() == null) {
            // request only for the default network
            isApplicable = mDefaultRequests.containsKey(networkRequest) && mIsActiveForDefault;
            localLog = mDefaultRequests.remove(networkRequest);
        } else {
            isApplicable = mSpecificRequests.containsKey(networkRequest) && mIsActive;
            localLog = mSpecificRequests.remove(networkRequest);
        }
        if (isApplicable) {
            String s = "onReleaseNetworkFor";
            localLog.log(s);
            log(s + " " + networkRequest);
            mDcTracker.releaseNetwork(networkRequest, localLog);
        } else {
            String s = "not releasing - isApplicable=" + isApplicable + ", mIsActive=" + mIsActive;
            localLog.log(s);
            log(s + " " + networkRequest);
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println(LOG_TAG + " mSubId=" + mSubscriptionId + " mIsActive=" +
                mIsActive + " mIsActiveForDefault=" + mIsActiveForDefault);
        pw.println("Default Requests:");
        pw.increaseIndent();
        for (NetworkRequest nr : mDefaultRequests.keySet()) {
            pw.println(nr);
            pw.increaseIndent();
            mDefaultRequests.get(nr).dump(fd, pw, args);
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }
}
