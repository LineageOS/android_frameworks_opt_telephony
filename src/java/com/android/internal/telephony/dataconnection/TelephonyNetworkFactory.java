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

import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.StringNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Rlog;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
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

    private static final int REQUEST_LOG_SIZE = 40;

    private static final int ACTION_NO_OP   = 0;
    private static final int ACTION_REQUEST = 1;
    private static final int ACTION_RELEASE = 2;

    private final PhoneSwitcher mPhoneSwitcher;
    private final SubscriptionController mSubscriptionController;
    private final SubscriptionMonitor mSubscriptionMonitor;
    private final DcTracker mDcTracker;
    private final LocalLog mLocalLog = new LocalLog(REQUEST_LOG_SIZE);

    // Key: network request. Value: whether it's applied to DcTracker.
    private final HashMap<NetworkRequest, Boolean> mNetworkRequests = new HashMap();

    private final Phone mPhone;
    private int mSubscriptionId;

    private final static int TELEPHONY_NETWORK_SCORE = 50;

    private final Handler mInternalHandler;
    private static final int EVENT_ACTIVE_PHONE_SWITCH          = 1;
    private static final int EVENT_SUBSCRIPTION_CHANGED         = 2;
    private static final int EVENT_NETWORK_REQUEST              = 3;
    private static final int EVENT_NETWORK_RELEASE              = 4;

    public TelephonyNetworkFactory(SubscriptionMonitor subscriptionMonitor, Looper looper,
                                   Phone phone) {
        super(looper, phone.getContext(), "TelephonyNetworkFactory[" + phone.getPhoneId()
                + "]", null);
        mPhone = phone;
        mInternalHandler = new InternalHandler(looper);

        mSubscriptionController = SubscriptionController.getInstance();

        setCapabilityFilter(makeNetworkFilter(mSubscriptionController, mPhone.getPhoneId()));
        setScoreFilter(TELEPHONY_NETWORK_SCORE);

        mPhoneSwitcher = PhoneSwitcher.getInstance();
        mSubscriptionMonitor = subscriptionMonitor;
        LOG_TAG = "TelephonyNetworkFactory[" + mPhone.getPhoneId() + "]";
        // TODO: Will need to dynamically route network requests to the corresponding DcTracker in
        // the future. For now we route everything to WWAN.
        mDcTracker = mPhone.getDcTracker(TransportType.WWAN);

        mPhoneSwitcher.registerForActivePhoneSwitch(mInternalHandler, EVENT_ACTIVE_PHONE_SWITCH,
                null);

        mSubscriptionId = INVALID_SUBSCRIPTION_ID;
        mSubscriptionMonitor.registerForSubscriptionChanged(mPhone.getPhoneId(), mInternalHandler,
                EVENT_SUBSCRIPTION_CHANGED, null);

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

    private void applyRequestsOnActivePhoneSwitch(NetworkRequest networkRequest,
            boolean cleanUpOnRelease, int action) {
        if (action == ACTION_NO_OP) return;

        String logStr = "onActivePhoneSwitch: " + ((action == ACTION_REQUEST)
                ? "Requesting" : "Releasing") + " network request " + networkRequest;
        mLocalLog.log(logStr);
        if (action == ACTION_REQUEST) {
            mDcTracker.requestNetwork(networkRequest, mLocalLog);
        } else if (action == ACTION_RELEASE) {
            mDcTracker.releaseNetwork(networkRequest, mLocalLog, cleanUpOnRelease);
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
        for (HashMap.Entry<NetworkRequest, Boolean> entry : mNetworkRequests.entrySet()) {
            NetworkRequest networkRequest = entry.getKey();
            boolean applied = entry.getValue();

            boolean shouldApply = mPhoneSwitcher.shouldApplyNetworkRequest(
                    networkRequest, mPhone.getPhoneId());

            applyRequestsOnActivePhoneSwitch(networkRequest, true,
                    getAction(applied, shouldApply));
            mNetworkRequests.put(networkRequest, shouldApply);
        }
    }

    // watch for phone->subId changes, reapply new filter and let
    // that flow through to apply/revoke of requests
    private void onSubIdChange() {
        final int newSubscriptionId = mSubscriptionController.getSubIdUsingPhoneId(
                mPhone.getPhoneId());
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
        boolean shouldApply = mPhoneSwitcher.shouldApplyNetworkRequest(
                networkRequest, mPhone.getPhoneId());

        mNetworkRequests.put(networkRequest, shouldApply);

        String s = "onNeedNetworkFor " + networkRequest + " shouldApply " + shouldApply;
        log(s);
        mLocalLog.log(s);

        if (shouldApply) {
            mDcTracker.requestNetwork(networkRequest, mLocalLog);
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
        boolean applied = mNetworkRequests.get(networkRequest);

        mNetworkRequests.remove(networkRequest);

        String s = "onReleaseNetworkFor " + networkRequest + " applied " + applied;
        log(s);
        mLocalLog.log(s);


        if (applied) {
            mDcTracker.releaseNetwork(networkRequest, mLocalLog, false);
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("Network Requests:");
        pw.increaseIndent();
        for (HashMap.Entry<NetworkRequest, Boolean> entry : mNetworkRequests.entrySet()) {
            NetworkRequest nr = entry.getKey();
            boolean applied = entry.getValue();
            pw.println((applied ? "Applied: " : "Not applied: ") + nr);
        }
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
    }
}
