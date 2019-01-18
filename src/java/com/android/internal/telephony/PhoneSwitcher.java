/*
* Copyright (C) 2015 The Android Open Source Project
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

package com.android.internal.telephony;

import static android.telephony.PhoneStateListener.LISTEN_PHONE_CAPABILITY_CHANGE;
import static android.telephony.PhoneStateListener.LISTEN_PRECISE_CALL_STATE;
import static android.telephony.SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
import static android.telephony.SubscriptionManager.INVALID_PHONE_INDEX;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.MatchAllNetworkSpecifier;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.StringNetworkSpecifier;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.telephony.PhoneCapability;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.dataconnection.DcRequest;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Utility singleton to monitor subscription changes and incoming NetworkRequests
 * and determine which phone/phones are active.
 *
 * Manages the ALLOW_DATA calls to modems and notifies phones about changes to
 * the active phones.  Note we don't wait for data attach (which may not happen anyway).
 */
public class PhoneSwitcher extends Handler {
    private final static String LOG_TAG = "PhoneSwitcher";
    private final static boolean VDBG = false;

    private final List<DcRequest> mPrioritizedDcRequests = new ArrayList<DcRequest>();
    private final RegistrantList mActivePhoneRegistrants;
    private final SubscriptionController mSubscriptionController;
    private final int[] mPhoneSubscriptions;
    private final CommandsInterface[] mCommandsInterfaces;
    private final Context mContext;
    private final PhoneState[] mPhoneStates;
    private final int mNumPhones;
    private final Phone[] mPhones;
    private final LocalLog mLocalLog;
    @VisibleForTesting
    public final PhoneStateListener mPhoneStateListener;

    private int mMaxActivePhones;
    private static PhoneSwitcher sPhoneSwitcher = null;

    // Default subscription ID from user setting.
    private int mDefaultDataSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    // If mPreferredDataSubId is an active subscription, it overrides
    // mDefaultDataSubId and decides:
    // 1. In modem layer, which subscription is preferred to have data traffic on.
    // 2. In TelephonyNetworkFactory, which subscription will apply default network requests, which
    //    are requests without specifying a subId.
    private int mPreferredDataSubId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;

    @VisibleForTesting
    // Corresponding phoneId after considerting mPreferredDataSubId and mDefaultDataSubId above.
    protected int mPreferredDataPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;

    private int mPhoneIdInCall = SubscriptionManager.INVALID_PHONE_INDEX;

    private static final int EVENT_DEFAULT_SUBSCRIPTION_CHANGED   = 101;
    private static final int EVENT_SUBSCRIPTION_CHANGED           = 102;
    private static final int EVENT_REQUEST_NETWORK                = 103;
    private static final int EVENT_RELEASE_NETWORK                = 104;
    private static final int EVENT_EMERGENCY_TOGGLE               = 105;
    private static final int EVENT_RADIO_CAPABILITY_CHANGED       = 106;
    private static final int EVENT_PREFERRED_SUBSCRIPTION_CHANGED = 107;
    private static final int EVENT_RADIO_AVAILABLE                = 108;
    private static final int EVENT_PHONE_IN_CALL_CHANGED          = 109;

    // Depending on version of IRadioConfig, we need to send either RIL_REQUEST_ALLOW_DATA if it's
    // 1.0, or RIL_REQUEST_SET_PREFERRED_DATA if it's 1.1 or later. So internally mHalCommandToUse
    // will be either HAL_COMMAND_ALLOW_DATA or HAL_COMMAND_ALLOW_DATA or HAL_COMMAND_UNKNOWN.
    private static final int HAL_COMMAND_UNKNOWN        = 0;
    private static final int HAL_COMMAND_ALLOW_DATA     = 1;
    private static final int HAL_COMMAND_PREFERRED_DATA = 2;
    private int mHalCommandToUse = HAL_COMMAND_UNKNOWN;

    private RadioConfig mRadioConfig;

    private final static int MAX_LOCAL_LOG_LINES = 30;

    /**
     * Method to get singleton instance.
     */
    public static PhoneSwitcher getInstance() {
        return sPhoneSwitcher;
    }

    /**
     * Method to create singleton instance.
     */
    public static PhoneSwitcher make(int maxActivePhones, int numPhones, Context context,
            SubscriptionController subscriptionController, Looper looper, ITelephonyRegistry tr,
            CommandsInterface[] cis, Phone[] phones) {
        if (sPhoneSwitcher == null) {
            sPhoneSwitcher = new PhoneSwitcher(maxActivePhones, numPhones, context,
                    subscriptionController, looper, tr, cis, phones);
        }

        return sPhoneSwitcher;
    }

    @VisibleForTesting
    public PhoneSwitcher(int numPhones, Looper looper) {
        super(looper);
        mMaxActivePhones = 0;
        mSubscriptionController = null;
        mCommandsInterfaces = null;
        mContext = null;
        mPhoneStates = null;
        mPhones = null;
        mLocalLog = null;
        mActivePhoneRegistrants = null;
        mNumPhones = numPhones;
        mPhoneSubscriptions = new int[numPhones];
        mRadioConfig = RadioConfig.getInstance(mContext);
        mPhoneStateListener = new PhoneStateListener(looper) {
            public void onPhoneCapabilityChanged(PhoneCapability capability) {
                onPhoneCapabilityChangedInternal(capability);
            }
        };
    }

    @VisibleForTesting
    public PhoneSwitcher(int maxActivePhones, int numPhones, Context context,
            SubscriptionController subscriptionController, Looper looper, ITelephonyRegistry tr,
            CommandsInterface[] cis, Phone[] phones) {
        super(looper);
        mContext = context;
        mNumPhones = numPhones;
        mPhones = phones;
        mPhoneSubscriptions = new int[numPhones];
        mMaxActivePhones = maxActivePhones;
        mLocalLog = new LocalLog(MAX_LOCAL_LOG_LINES);

        mSubscriptionController = subscriptionController;
        mRadioConfig = RadioConfig.getInstance(mContext);

        mPhoneStateListener = new PhoneStateListener(looper) {
            @Override
            public void onPhoneCapabilityChanged(PhoneCapability capability) {
                onPhoneCapabilityChangedInternal(capability);
            }

            @Override
            public void onPreciseCallStateChanged(PreciseCallState callState) {
                int oldPhoneIdInCall = mPhoneIdInCall;
                // If there's no active call, the value will become INVALID_PHONE_INDEX
                // and internet data will be switched back to system selected or user selected
                // subscription.
                mPhoneIdInCall = SubscriptionManager.INVALID_PHONE_INDEX;
                for (Phone phone : mPhones) {
                    if (isCallActive(phone) || isCallActive(phone.getImsPhone())) {
                        mPhoneIdInCall = phone.getPhoneId();
                        break;
                    }
                }

                if (mPhoneIdInCall != oldPhoneIdInCall) {
                    log("mPhoneIdInCall changed from" + oldPhoneIdInCall
                            + " to " + mPhoneIdInCall);
                    Message msg = PhoneSwitcher.this.obtainMessage(EVENT_PHONE_IN_CALL_CHANGED);
                    msg.sendToTarget();
                }
            }
        };

        TelephonyManager telephonyManager =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, LISTEN_PHONE_CAPABILITY_CHANGE
                | LISTEN_PRECISE_CALL_STATE);

        mActivePhoneRegistrants = new RegistrantList();
        mPhoneStates = new PhoneState[numPhones];
        for (int i = 0; i < numPhones; i++) {
            mPhoneStates[i] = new PhoneState();
            if (mPhones[i] != null) {
                mPhones[i].registerForEmergencyCallToggle(this, EVENT_EMERGENCY_TOGGLE, null);
            }
        }

        mCommandsInterfaces = cis;

        mCommandsInterfaces[0].registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        try {
            tr.addOnSubscriptionsChangedListener(context.getOpPackageName(),
                    mSubscriptionsChangedListener);
        } catch (RemoteException e) {
        }

        mContext.registerReceiver(mDefaultDataChangedReceiver,
                new IntentFilter(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED));

        NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_IA);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_RCS);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        netCap.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        netCap.setNetworkSpecifier(new MatchAllNetworkSpecifier());

        NetworkFactory networkFactory = new PhoneSwitcherNetworkRequestListener(looper, context,
                netCap, this);
        // we want to see all requests
        networkFactory.setScoreFilter(101);
        networkFactory.register();

        log("PhoneSwitcher started");
    }

    private final BroadcastReceiver mDefaultDataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Message msg = PhoneSwitcher.this.obtainMessage(EVENT_DEFAULT_SUBSCRIPTION_CHANGED);
            msg.sendToTarget();
        }
    };

    private final IOnSubscriptionsChangedListener mSubscriptionsChangedListener =
            new IOnSubscriptionsChangedListener.Stub() {
        @Override
        public void onSubscriptionsChanged() {
            Message msg = PhoneSwitcher.this.obtainMessage(EVENT_SUBSCRIPTION_CHANGED);
            msg.sendToTarget();
        }
    };

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SUBSCRIPTION_CHANGED: {
                onEvaluate(REQUESTS_UNCHANGED, "subChanged");
                break;
            }
            case EVENT_DEFAULT_SUBSCRIPTION_CHANGED: {
                onEvaluate(REQUESTS_UNCHANGED, "defaultChanged");
                break;
            }
            case EVENT_REQUEST_NETWORK: {
                onRequestNetwork((NetworkRequest)msg.obj);
                break;
            }
            case EVENT_RELEASE_NETWORK: {
                onReleaseNetwork((NetworkRequest)msg.obj);
                break;
            }
            case EVENT_EMERGENCY_TOGGLE: {
                onEvaluate(REQUESTS_CHANGED, "emergencyToggle");
                break;
            }
            case EVENT_RADIO_CAPABILITY_CHANGED: {
                resendRilCommands(msg);
                break;
            }
            case EVENT_PREFERRED_SUBSCRIPTION_CHANGED: {
                onEvaluate(REQUESTS_UNCHANGED, "preferredDataSubscriptionIdChanged");
                break;
            }
            case EVENT_RADIO_AVAILABLE: {
                updateHalCommandToUse();
                onEvaluate(REQUESTS_UNCHANGED, "EVENT_RADIO_AVAILABLE");
                break;
            }
            case EVENT_PHONE_IN_CALL_CHANGED: {
                onEvaluate(REQUESTS_UNCHANGED, "EVENT_PHONE_IN_CALL_CHANGED");
                break;
            }
        }
    }

    private boolean isEmergency() {
        for (Phone p : mPhones) {
            if (p == null) continue;
            if (p.isInEcm() || p.isInEmergencyCall()) return true;
        }
        return false;
    }

    private static class PhoneSwitcherNetworkRequestListener extends NetworkFactory {
        private final PhoneSwitcher mPhoneSwitcher;
        public PhoneSwitcherNetworkRequestListener (Looper l, Context c,
                NetworkCapabilities nc, PhoneSwitcher ps) {
            super(l, c, "PhoneSwitcherNetworkRequstListener", nc);
            mPhoneSwitcher = ps;
        }

        @Override
        protected void needNetworkFor(NetworkRequest networkRequest, int score) {
            if (VDBG) log("needNetworkFor " + networkRequest + ", " + score);
            Message msg = mPhoneSwitcher.obtainMessage(EVENT_REQUEST_NETWORK);
            msg.obj = networkRequest;
            msg.sendToTarget();
        }

        @Override
        protected void releaseNetworkFor(NetworkRequest networkRequest) {
            if (VDBG) log("releaseNetworkFor " + networkRequest);
            Message msg = mPhoneSwitcher.obtainMessage(EVENT_RELEASE_NETWORK);
            msg.obj = networkRequest;
            msg.sendToTarget();
        }
    }

    private void onRequestNetwork(NetworkRequest networkRequest) {
        final DcRequest dcRequest = new DcRequest(networkRequest, mContext);
        if (mPrioritizedDcRequests.contains(dcRequest) == false) {
            mPrioritizedDcRequests.add(dcRequest);
            Collections.sort(mPrioritizedDcRequests);
            onEvaluate(REQUESTS_CHANGED, "netRequest");
        }
    }

    private void onReleaseNetwork(NetworkRequest networkRequest) {
        final DcRequest dcRequest = new DcRequest(networkRequest, mContext);

        if (mPrioritizedDcRequests.remove(dcRequest)) {
            onEvaluate(REQUESTS_CHANGED, "netReleased");
        }
    }

    private static final boolean REQUESTS_CHANGED   = true;
    private static final boolean REQUESTS_UNCHANGED = false;
    /**
     * Re-evaluate things.
     * Do nothing if nothing's changed.
     *
     * Otherwise, go through the requests in priority order adding their phone
     * until we've added up to the max allowed.  Then go through shutting down
     * phones that aren't in the active phone list.  Finally, activate all
     * phones in the active phone list.
     */
    private void onEvaluate(boolean requestsChanged, String reason) {
        StringBuilder sb = new StringBuilder(reason);
        if (isEmergency()) {
            log("onEvalute aborted due to Emergency");
            return;
        }

        // If we use HAL_COMMAND_PREFERRED_DATA,
        boolean diffDetected = mHalCommandToUse != HAL_COMMAND_PREFERRED_DATA && requestsChanged;

        // Check if user setting of default data sub is changed.
        final int dataSub = mSubscriptionController.getDefaultDataSubId();
        if (dataSub != mDefaultDataSubId) {
            sb.append(" default ").append(mDefaultDataSubId).append("->").append(dataSub);
            mDefaultDataSubId = dataSub;
        }

        // Check if phoneId to subId mapping is changed.
        for (int i = 0; i < mNumPhones; i++) {
            int sub = mSubscriptionController.getSubIdUsingPhoneId(i);
            if (sub != mPhoneSubscriptions[i]) {
                sb.append(" phone[").append(i).append("] ").append(mPhoneSubscriptions[i]);
                sb.append("->").append(sub);
                mPhoneSubscriptions[i] = sub;
                diffDetected = true;
            }
        }

        // Check if phoneId for preferred data is changed.
        int oldPreferredDataPhoneId = mPreferredDataPhoneId;
        updatePhoneIdForDefaultNetworkRequests();
        if (oldPreferredDataPhoneId != mPreferredDataPhoneId) {
            sb.append(" preferred phoneId ").append(oldPreferredDataPhoneId)
                    .append("->").append(mPreferredDataPhoneId);
            diffDetected = true;
        }

        if (diffDetected) {
            log("evaluating due to " + sb.toString());
            if (mHalCommandToUse == HAL_COMMAND_PREFERRED_DATA) {
                // With HAL_COMMAND_PREFERRED_DATA, all phones are assumed to allow PS attach.
                // So marking all phone as active.
                for (int phoneId = 0; phoneId < mNumPhones; phoneId++) {
                    activate(phoneId);
                }
                if (SubscriptionManager.isUsableSubIdValue(mPreferredDataPhoneId)) {
                    mRadioConfig.setPreferredDataModem(mPreferredDataPhoneId, null);
                }
            } else {
                List<Integer> newActivePhones = new ArrayList<Integer>();

                /**
                 * If all phones can have PS attached, activate all.
                 * Otherwise, choose to activate phones according to requests. And
                 * if list is not full, add mPreferredDataPhoneId.
                 */
                if (mMaxActivePhones == mPhones.length) {
                    for (int i = 0; i < mMaxActivePhones; i++) {
                        newActivePhones.add(mPhones[i].getPhoneId());
                    }
                } else {
                    for (DcRequest dcRequest : mPrioritizedDcRequests) {
                        int phoneIdForRequest = phoneIdForRequest(dcRequest.networkRequest);
                        if (phoneIdForRequest == INVALID_PHONE_INDEX) continue;
                        if (newActivePhones.contains(phoneIdForRequest)) continue;
                        newActivePhones.add(phoneIdForRequest);
                        if (newActivePhones.size() >= mMaxActivePhones) break;
                    }

                    if (newActivePhones.size() < mMaxActivePhones
                            && newActivePhones.contains(mPreferredDataPhoneId)
                            && SubscriptionManager.isUsableSubIdValue(mPreferredDataPhoneId)) {
                        newActivePhones.add(mPreferredDataPhoneId);
                    }
                }

                if (VDBG) {
                    log("default subId = " + mDefaultDataSubId);
                    log("preferred subId = " + mPreferredDataSubId);
                    for (int i = 0; i < mNumPhones; i++) {
                        log(" phone[" + i + "] using sub[" + mPhoneSubscriptions[i] + "]");
                    }
                    log(" newActivePhones:");
                    for (Integer i : newActivePhones) log("  " + i);
                }

                for (int phoneId = 0; phoneId < mNumPhones; phoneId++) {
                    if (!newActivePhones.contains(phoneId)) {
                        deactivate(phoneId);
                    }
                }

                // only activate phones up to the limit
                for (int phoneId : newActivePhones) {
                    activate(phoneId);
                }
            }
            // Notify all registrants.
            mActivePhoneRegistrants.notifyRegistrants();
        }
    }

    private static class PhoneState {
        public volatile boolean active = false;
        public long lastRequested = 0;
    }

    private void activate(int phoneId) {
        switchPhone(phoneId, true);
    }

    private void deactivate(int phoneId) {
        switchPhone(phoneId, false);
    }

    private void switchPhone(int phoneId, boolean active) {
        PhoneState state = mPhoneStates[phoneId];
        if (state.active == active) return;
        state.active = active;
        log((active ? "activate " : "deactivate ") + phoneId);
        state.lastRequested = System.currentTimeMillis();
        if (mHalCommandToUse == HAL_COMMAND_ALLOW_DATA || mHalCommandToUse == HAL_COMMAND_UNKNOWN) {
            // Skip ALLOW_DATA for single SIM device
            if (mNumPhones > 1) {
                mCommandsInterfaces[phoneId].setDataAllowed(active, null);
            }
        }
    }

    /**
     * Used when the modem may have been rebooted and we
     * want to resend setDataAllowed or setPreferredDataSubscriptionId
     */
    public void onRadioCapChanged(int phoneId) {
        validatePhoneId(phoneId);
        Message msg = obtainMessage(EVENT_RADIO_CAPABILITY_CHANGED);
        msg.arg1 = phoneId;
        msg.sendToTarget();
    }

    private void resendRilCommands(Message msg) {
        final int phoneId = msg.arg1;
        if (mHalCommandToUse == HAL_COMMAND_ALLOW_DATA || mHalCommandToUse == HAL_COMMAND_UNKNOWN) {
            // Skip ALLOW_DATA for single SIM device
            if (mNumPhones > 1) {
                mCommandsInterfaces[phoneId].setDataAllowed(isPhoneActive(phoneId), null);
            }
        } else {
            mRadioConfig.setPreferredDataModem(mPreferredDataPhoneId, null);
        }
    }

    private void onPhoneCapabilityChangedInternal(PhoneCapability capability) {
        int newMaxActivePhones = TelephonyManager.getDefault()
                .getNumberOfModemsWithSimultaneousDataConnections();
        if (mMaxActivePhones != newMaxActivePhones) {
            mMaxActivePhones = newMaxActivePhones;
            log("Max active phones changed to " + mMaxActivePhones);
            onEvaluate(REQUESTS_UNCHANGED, "phoneCfgChanged");
        }
    }

    private int phoneIdForRequest(NetworkRequest netRequest) {
        int subId = getSubIdFromNetworkRequest(netRequest);

        if (subId == DEFAULT_SUBSCRIPTION_ID) return mPreferredDataPhoneId;
        if (subId == INVALID_SUBSCRIPTION_ID) return INVALID_PHONE_INDEX;

        int preferredDataSubId = SubscriptionManager.isValidPhoneId(mPreferredDataPhoneId)
                ? mPhoneSubscriptions[mPreferredDataPhoneId] : INVALID_SUBSCRIPTION_ID;
        // Currently we assume multi-SIM devices will only support one Internet PDN connection. So
        // if Internet PDN is established on the non-preferred phone, it will interrupt
        // Internet connection on the preferred phone. So we only accept Internet request with
        // preferred data subscription or no specified subscription.
        if (netRequest.networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET) && subId != preferredDataSubId) {
            // Returning INVALID_PHONE_INDEX will result in netRequest not being handled.
            return INVALID_PHONE_INDEX;
        }

        // Try to find matching phone ID. If it doesn't exist, we'll end up returning INVALID.
        int phoneId = INVALID_PHONE_INDEX;
        for (int i = 0; i < mNumPhones; i++) {
            if (mPhoneSubscriptions[i] == subId) {
                phoneId = i;
                break;
            }
        }
        return phoneId;
    }

    private int getSubIdFromNetworkRequest(NetworkRequest networkRequest) {
        NetworkSpecifier specifier = networkRequest.networkCapabilities.getNetworkSpecifier();
        if (specifier == null) {
            return DEFAULT_SUBSCRIPTION_ID;
        }

        int subId;

        if (specifier instanceof StringNetworkSpecifier) {
            try {
                subId = Integer.parseInt(((StringNetworkSpecifier) specifier).specifier);
            } catch (NumberFormatException e) {
                Rlog.e(LOG_TAG, "NumberFormatException on "
                        + ((StringNetworkSpecifier) specifier).specifier);
                return INVALID_SUBSCRIPTION_ID;
            }
        } else {
            return INVALID_SUBSCRIPTION_ID;
        }

        return subId;
    }

    private int getSubIdForDefaultNetworkRequests() {
        if (mSubscriptionController.isActiveSubId(mPreferredDataSubId)) {
            return mPreferredDataSubId;
        } else {
            return mDefaultDataSubId;
        }
    }

    // This updates mPreferredDataPhoneId which decides which phone should
    // handle default network requests.
    private void updatePhoneIdForDefaultNetworkRequests() {
        if (SubscriptionManager.isValidPhoneId(mPhoneIdInCall)) {
            // If a phone is in call and user enabled its mobile data, we
            // should switch internet connection to it. Because the other modem
            // will lose data connection anyway.
            // TODO: validate network first.

            mPreferredDataPhoneId = mPhoneIdInCall;
        } else {
            int subId = getSubIdForDefaultNetworkRequests();
            int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;

            if (SubscriptionManager.isUsableSubIdValue(subId)) {
                for (int i = 0; i < mNumPhones; i++) {
                    if (mPhoneSubscriptions[i] == subId) {
                        phoneId = i;
                        break;
                    }
                }
            }

            mPreferredDataPhoneId = phoneId;
        }
    }

    public boolean shouldApplyNetworkRequest(NetworkRequest networkRequest, int phoneId) {
        validatePhoneId(phoneId);

        // In any case, if phone state is inactive, don't apply the network request.
        if (!isPhoneActive(phoneId)) return false;

        int phoneIdToHandle = phoneIdForRequest(networkRequest);

        return phoneId == phoneIdToHandle;
    }

    @VisibleForTesting
    protected boolean isPhoneActive(int phoneId) {
        return mPhoneStates[phoneId].active;
    }

    /**
     * If preferred phone changes, or phone activation status changes, registrants
     * will be notified.
     */
    public void registerForActivePhoneSwitch(Handler h, int what, Object o) {
        Registrant r = new Registrant(h, what, o);
        mActivePhoneRegistrants.add(r);
        r.notifyRegistrant();
    }

    public void unregisterForActivePhoneSwitch(Handler h) {
        mActivePhoneRegistrants.remove(h);
    }

    @VisibleForTesting
    protected void validatePhoneId(int phoneId) {
        if (phoneId < 0 || phoneId >= mNumPhones) {
            throw new IllegalArgumentException("Invalid PhoneId");
        }
    }

    /**
     * Set a subscription as preferred data subscription.
     * See {@link SubscriptionManager#setPreferredDataSubscriptionId(int)} for more details.
     */
    public void setPreferredDataSubscriptionId(int subId) {
        if (mPreferredDataSubId != subId) {
            log("setPreferredDataSubscriptionId subId changed to " + subId);
            mPreferredDataSubId = subId;
            Message msg = PhoneSwitcher.this.obtainMessage(EVENT_PREFERRED_SUBSCRIPTION_CHANGED);
            msg.sendToTarget();
        }
    }

    private boolean isCallActive(Phone phone) {
        if (phone == null) {
            return false;
        }

        return (phone.getForegroundCall().getState() == Call.State.ACTIVE
                || phone.getBackgroundCall().getState() == Call.State.ACTIVE
                || phone.getRingingCall().getState() == Call.State.ACTIVE);
    }

    private void updateHalCommandToUse() {
        mHalCommandToUse = mRadioConfig.isSetPreferredDataCommandSupported()
                ? HAL_COMMAND_PREFERRED_DATA : HAL_COMMAND_ALLOW_DATA;
    }

    private void log(String l) {
        Rlog.d(LOG_TAG, l);
        mLocalLog.log(l);
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("PhoneSwitcher:");
        Calendar c = Calendar.getInstance();
        for (int i = 0; i < mNumPhones; i++) {
            PhoneState ps = mPhoneStates[i];
            c.setTimeInMillis(ps.lastRequested);
            pw.println("PhoneId(" + i + ") active=" + ps.active + ", lastRequest=" +
                    (ps.lastRequested == 0 ? "never" :
                     String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c)));
        }
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
    }
}
