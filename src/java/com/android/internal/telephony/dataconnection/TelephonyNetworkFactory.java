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
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Rlog;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnSetting.ApnType;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.SubscriptionMonitor;
import com.android.internal.telephony.dataconnection.DcTracker.ReleaseNetworkType;
import com.android.internal.telephony.dataconnection.DcTracker.RequestNetworkType;
import com.android.internal.telephony.dataconnection.TransportManager.HandoverParams;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

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
    private final LocalLog mLocalLog = new LocalLog(REQUEST_LOG_SIZE);

    // Key: network request. Value: the transport of DcTracker it applies to,
    // TransportType.INVALID if not applied.
    private final Map<NetworkRequest, Integer> mNetworkRequests = new HashMap<>();

    private final Phone mPhone;

    private final TransportManager mTransportManager;

    private int mSubscriptionId;

    private final static int TELEPHONY_NETWORK_SCORE = 50;

    private final Handler mInternalHandler;
    private static final int EVENT_ACTIVE_PHONE_SWITCH              = 1;
    private static final int EVENT_SUBSCRIPTION_CHANGED             = 2;
    private static final int EVENT_NETWORK_REQUEST                  = 3;
    private static final int EVENT_NETWORK_RELEASE                  = 4;
    private static final int EVENT_DATA_HANDOVER_NEEDED             = 5;
    private static final int EVENT_DATA_HANDOVER_COMPLETED          = 6;

    public TelephonyNetworkFactory(SubscriptionMonitor subscriptionMonitor, Looper looper,
                                   Phone phone) {
        super(looper, phone.getContext(), "TelephonyNetworkFactory[" + phone.getPhoneId()
                + "]", null);
        mPhone = phone;
        mTransportManager = mPhone.getTransportManager();
        mInternalHandler = new InternalHandler(looper);

        mSubscriptionController = SubscriptionController.getInstance();

        setCapabilityFilter(makeNetworkFilter(mSubscriptionController, mPhone.getPhoneId()));
        setScoreFilter(TELEPHONY_NETWORK_SCORE);

        mPhoneSwitcher = PhoneSwitcher.getInstance();
        mSubscriptionMonitor = subscriptionMonitor;
        LOG_TAG = "TelephonyNetworkFactory[" + mPhone.getPhoneId() + "]";

        mPhoneSwitcher.registerForActivePhoneSwitch(mInternalHandler, EVENT_ACTIVE_PHONE_SWITCH,
                null);
        mTransportManager.registerForHandoverNeededEvent(mInternalHandler,
                EVENT_DATA_HANDOVER_NEEDED);

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
                case EVENT_DATA_HANDOVER_NEEDED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    HandoverParams handoverParams = (HandoverParams) ar.result;
                    onDataHandoverNeeded(handoverParams.apnType, handoverParams.targetTransport);
                    break;
                }
                case EVENT_DATA_HANDOVER_COMPLETED: {
                    Bundle bundle = msg.getData();
                    int requestType = bundle.getInt(DcTracker.DATA_COMPLETE_MSG_EXTRA_REQUEST_TYPE);
                    if (requestType == DcTracker.REQUEST_TYPE_HANDOVER) {
                        NetworkRequest nr = bundle.getParcelable(
                                DcTracker.DATA_COMPLETE_MSG_EXTRA_NETWORK_REQUEST);
                        boolean success = bundle.getBoolean(
                                DcTracker.DATA_COMPLETE_MSG_EXTRA_SUCCESS);
                        int transport = bundle.getInt(
                                DcTracker.DATA_COMPLETE_MSG_EXTRA_TRANSPORT_TYPE);
                        onDataHandoverSetupCompleted(nr, success, transport);
                    }
                    break;
                }
            }
        }
    }

    private int getTransportTypeFromNetworkRequest(NetworkRequest networkRequest) {
        int apnType = ApnContext.getApnTypeFromNetworkRequest(networkRequest);
        return mTransportManager.getCurrentTransport(apnType);
    }

    private void requestNetworkInternal(NetworkRequest networkRequest,
                                        @RequestNetworkType int requestType,
                                        int transport, Message onCompleteMsg) {
        if (mPhone.getDcTracker(transport) != null) {
            mPhone.getDcTracker(transport).requestNetwork(networkRequest, requestType,
                    onCompleteMsg, mLocalLog);
        }
    }

    private void releaseNetworkInternal(NetworkRequest networkRequest,
                                        @ReleaseNetworkType int releaseType,
                                        int transport) {
        if (mPhone.getDcTracker(transport) != null) {
            mPhone.getDcTracker(transport).releaseNetwork(networkRequest, releaseType,
                    mLocalLog);
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
        for (HashMap.Entry<NetworkRequest, Integer> entry : mNetworkRequests.entrySet()) {
            NetworkRequest networkRequest = entry.getKey();
            boolean applied = entry.getValue() != TransportType.INVALID;

            boolean shouldApply = mPhoneSwitcher.shouldApplyNetworkRequest(
                    networkRequest, mPhone.getPhoneId());

            int action = getAction(applied, shouldApply);
            if (action == ACTION_NO_OP) continue;

            String logStr = "onActivePhoneSwitch: " + ((action == ACTION_REQUEST)
                    ? "Requesting" : "Releasing") + " network request " + networkRequest;
            mLocalLog.log(logStr);
            int transportType = getTransportTypeFromNetworkRequest(networkRequest);
            if (action == ACTION_REQUEST) {
                requestNetworkInternal(networkRequest, DcTracker.REQUEST_TYPE_NORMAL,
                        getTransportTypeFromNetworkRequest(networkRequest), null);
            } else if (action == ACTION_RELEASE) {
                int transport = getTransportTypeFromNetworkRequest(networkRequest);
                releaseNetworkInternal(networkRequest, DcTracker.RELEASE_TYPE_DETACH, transport);
            }

            mNetworkRequests.put(networkRequest,
                    shouldApply ? transportType : TransportType.INVALID);
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

        mNetworkRequests.put(networkRequest, shouldApply
                ? getTransportTypeFromNetworkRequest(networkRequest) : TransportType.INVALID);

        String s = "onNeedNetworkFor " + networkRequest + " shouldApply " + shouldApply;
        log(s);
        mLocalLog.log(s);

        if (shouldApply) {
            requestNetworkInternal(networkRequest, DcTracker.REQUEST_TYPE_NORMAL,
                    getTransportTypeFromNetworkRequest(networkRequest), null);
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
        boolean applied = mNetworkRequests.get(networkRequest) != TransportType.INVALID;

        mNetworkRequests.remove(networkRequest);

        String s = "onReleaseNetworkFor " + networkRequest + " applied " + applied;
        log(s);
        mLocalLog.log(s);

        if (applied) {
            int transport = getTransportTypeFromNetworkRequest(networkRequest);
            releaseNetworkInternal(networkRequest, DcTracker.RELEASE_TYPE_NORMAL, transport);
        }
    }

    private void onDataHandoverNeeded(@ApnType int apnType, int targetTransport) {
        log("onDataHandoverNeeded: apnType=" + ApnSetting.getApnTypeString(apnType)
                + ", target transport=" + TransportType.toString(targetTransport));
        if (mTransportManager.getCurrentTransport(apnType) == targetTransport) {
            log("apnType " + ApnSetting.getApnTypeString(apnType) + " is already on "
                    + TransportType.toString(targetTransport));
            return;
        }

        for (HashMap.Entry<NetworkRequest, Integer> entry : mNetworkRequests.entrySet()) {
            NetworkRequest networkRequest = entry.getKey();
            int currentTransport = entry.getValue();
            boolean applied = currentTransport != TransportType.INVALID;
            if (ApnContext.getApnTypeFromNetworkRequest(networkRequest) == apnType
                    && applied
                    && currentTransport != targetTransport) {
                Message onCompleteMsg = mInternalHandler.obtainMessage(
                        EVENT_DATA_HANDOVER_COMPLETED);
                onCompleteMsg.getData().putParcelable(
                        DcTracker.DATA_COMPLETE_MSG_EXTRA_NETWORK_REQUEST, networkRequest);
                requestNetworkInternal(networkRequest, DcTracker.REQUEST_TYPE_HANDOVER,
                        targetTransport, onCompleteMsg);
            }
        }
    }

    private void onDataHandoverSetupCompleted(NetworkRequest networkRequest, boolean success,
                                              int targetTransport) {
        log("onDataHandoverSetupCompleted: " + networkRequest + ", success=" + success
                + ", targetTransport=" + TransportType.toString(targetTransport));

        // At this point, handover setup has been completed on the target transport. If succeeded,
        // we can release the data connection on the original transport. If failed, then we also
        // need to remove the network request from the targeting transport.

        if (success) {
            // Handover setup succeeded on targeting transport. Now we can release the network
            // request on the original transport.
            int originTransport = (targetTransport == TransportType.WWAN)
                    ? TransportType.WLAN : TransportType.WWAN;
            int apnType = ApnContext.getApnTypeFromNetworkRequest(networkRequest);
            releaseNetworkInternal(networkRequest, DcTracker.RELEASE_TYPE_HANDOVER,
                    originTransport);
            mNetworkRequests.put(networkRequest, targetTransport);
            // Switch the current transport to the new one.
            mTransportManager.setCurrentTransport(apnType, targetTransport);
        } else {
            // If handover failed, we need to remove the request on the targeting transport.
            releaseNetworkInternal(networkRequest, DcTracker.RELEASE_TYPE_NORMAL, targetTransport);
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("Network Requests:");
        pw.increaseIndent();
        for (HashMap.Entry<NetworkRequest, Integer> entry : mNetworkRequests.entrySet()) {
            NetworkRequest nr = entry.getKey();
            int transport = entry.getValue();
            pw.println(nr + (transport != TransportType.INVALID
                    ? (" applied on " + transport) : " not applied"));
        }
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
    }
}
