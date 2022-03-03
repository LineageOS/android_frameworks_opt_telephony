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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RegistrantList;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.SimState;
import android.telephony.data.DataProfile;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.data.DataEvaluation.DataAllowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataEvaluationReason;
import com.android.internal.telephony.data.DataNetwork.DataValidationResult;
import com.android.internal.telephony.data.DataRetryManager.DataRetryEntry;
import com.android.internal.telephony.dataconnection.AccessNetworksManager;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DataNetworkController in the central module of the telephony data stack. It is responsible to
 * create and manage all the mobile data networks.
 */
public class DataNetworkController extends Handler {
    private static final boolean VDBG = false;

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for adding a network request. */
    private static final int EVENT_ADD_NETWORK_REQUEST = 2;

    /** Event for removing a network request. */
    private static final int EVENT_REMOVE_NETWORK_REQUEST = 3;

    /** Event for satisfying a single network request. */
    private static final int EVENT_SATISFY_NETWORK_REQUEST = 4;

    /** Re-evaluate all unsatisfied network requests. */
    private static final int EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS = 5;

    /** Event for data stall action reestablish. */
    private static final int EVENT_DATA_STALL_ACTION_REESTABLISH = 6;

    /** Event for packet switch restricted enabled by network. */
    private static final int EVENT_PS_RESTRICT_ENABLED = 7;

    /** Event for packet switch restricted disabled by network. */
    private static final int EVENT_PS_RESTRICT_DISABLED = 8;

    /** Event for data service binding changed. */
    private static final int EVENT_DATA_SERVICE_BINDING_CHANGED = 9;

    /** Event for SIM state changed. */
    private static final int EVENT_SIM_STATE_CHANGED = 10;

    /** Event for data profile changed. */
    private static final int EVENT_DATA_PROFILES_CHANGED = 11;

    /** Event for data retry. */
    private static final int EVENT_DATA_RETRY = 12;

    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    private final @NonNull DataConfigManager mDataConfigManager;
    private final @NonNull DataSettingsManager mDataSettingsManager;
    private final @NonNull DataProfileManager mDataProfileManager;
    private final @NonNull DataStallRecoveryManager mDataStallRecoveryManager;
    private final @NonNull AccessNetworksManager mAccessNetworksManager;
    private final @NonNull DataRetryManager mDataRetryManager;
    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers =
            new SparseArray<>();

    /**
     * The list of all network requests.
     */
    private final @NonNull NetworkRequestList mAllNetworkRequestList = new NetworkRequestList();

    /**
     * The current data network list, including the ones that are connected, connecting, or
     * disconnecting.
     */
    private final @NonNull List<DataNetwork> mDataNetworkList = new ArrayList<>();

    /**
     * Contain the last 10 data networks that were connected. This is for debugging purposes only.
     */
    private final @NonNull List<DataNetwork> mHistoricalDataNetworkList = new ArrayList<>();

    /**
     * Registrant list for internet validation status changed.
     */
    private final @NonNull RegistrantList mInternetValidationStatusRegistrants =
            new RegistrantList();

    /** Indicates if packet switch data is restricted by the network. */
    private boolean mPsRestricted = false;

    /**
     * Indicates if the data services are bound. Key if the transport type, and value is the boolean
     * indicating service is bound or not.
     */
    private final @NonNull SparseBooleanArray mDataServiceBound = new SparseBooleanArray();

    /** SIM state. */
    private @SimState int mSimState = TelephonyManager.SIM_STATE_UNKNOWN;

    /** The broadcast receiver. */
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED:
                case TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED:
                    if (mPhone.getPhoneId() == intent.getIntExtra(
                            SubscriptionManager.EXTRA_SLOT_INDEX,
                            SubscriptionManager.INVALID_SIM_SLOT_INDEX)) {
                        int simState = intent.getIntExtra(TelephonyManager.EXTRA_SIM_STATE,
                                TelephonyManager.SIM_STATE_UNKNOWN);
                        sendMessage(obtainMessage(EVENT_SIM_STATE_CHANGED, simState, 0));
                    }
            }
        }
    };

    /**
     * The sorted network request list by priority. The highest priority network request stays at
     * the head of the list. The highest priority is 100, the lowest is 0.
     *
     * Note this list is not thread-safe. Do not access the list from different threads.
     */
    @VisibleForTesting
    public static class NetworkRequestList extends LinkedList<TelephonyNetworkRequest> {
        /**
         * Constructor
         */
        public NetworkRequestList() {
        }

        /**
         * Copy constructor
         *
         * @param requestList The network request list.
         */
        public NetworkRequestList(NetworkRequestList requestList) {
            this.addAll(requestList);
        }

        /**
         * Constructor
         *
         * @param newRequest The initial request of the list.
         */
        public NetworkRequestList(@NonNull TelephonyNetworkRequest newRequest) {
            this();
            add(newRequest);
        }

        /**
         * Add the network request to the list. Note that the item will be inserted to the position
         * based on the priority.
         *
         * @param newRequest The network request to be added.
         * @return {@code true} if added successfully. {@code false} if the request already exists.
         */
        @Override
        public boolean add(@NonNull TelephonyNetworkRequest newRequest) {
            int index = 0;
            while (index < size()) {
                TelephonyNetworkRequest networkRequest = get(index);
                if (networkRequest.equals(newRequest)) {
                    return false;   // Do not allow duplicate
                }
                if (newRequest.getPriority() > networkRequest.getPriority()) {
                    break;
                }
                index++;
            }
            super.add(index, newRequest);
            return true;
        }

        @Override
        public void add(int index, @NonNull TelephonyNetworkRequest newRequest) {
            throw new UnsupportedOperationException("Insertion to certain position is illegal.");
        }

        @Override
        public boolean addAll(Collection<? extends TelephonyNetworkRequest> requests) {
            for (TelephonyNetworkRequest networkRequest : requests) {
                add(networkRequest);
            }
            return true;
        }
        /**
         * Get the first network request that contains all the provided network capabilities.
         *
         * @param netCaps The network capabilities.
         * @return The first network request in the list that contains all the provided
         * capabilities.
         */
        public @Nullable TelephonyNetworkRequest get(@NonNull @NetCapability int[] netCaps) {
            int index = 0;
            while (index < size()) {
                TelephonyNetworkRequest networkRequest = get(index);
                // Check if any network requests contains all the provided capabilities.
                if (Arrays.stream(networkRequest.getCapabilities())
                        .boxed()
                        .collect(Collectors.toSet())
                        .containsAll(Arrays.stream(netCaps).boxed()
                                .collect(Collectors.toList()))) {
                    return networkRequest;
                }
                index++;
            }
            return null;
        }

        @Override
        public String toString() {
            return "[NetworkRequestList: size=" + size() + ", leading by " + get(0) + "]";
        }

        /**
         * Dump the network request list.
         *
         * @param pw print writer.
         */
        public void dump(IndentingPrintWriter pw) {
            pw.increaseIndent();
            for (TelephonyNetworkRequest networkRequest : this) {
                pw.println(networkRequest);
            }
            pw.decreaseIndent();
        }
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataNetworkController(@NonNull Phone phone, @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DNC-" + mPhone.getPhoneId();
        log("DataNetworkController created.");

        mAccessNetworksManager = phone.getAccessNetworksManager();
        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                new DataServiceManager(mPhone, looper, AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        if (!mAccessNetworksManager.isInLegacyMode()) {
            mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                    new DataServiceManager(mPhone, looper,
                            AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        }
        mDataConfigManager = new DataConfigManager(mPhone, looper);
        mDataSettingsManager = new DataSettingsManager(mPhone, this, looper);
        mDataProfileManager = new DataProfileManager(mPhone, this, mDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), looper);
        mDataStallRecoveryManager = new DataStallRecoveryManager(mPhone, this, mDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), looper);
        mDataRetryManager = new DataRetryManager(mPhone, this, looper);

        registerAllEvents();
    }

    /**
     * Register for all events that data network controller is interested.
     */
    private void registerAllEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        mDataRetryManager.registerForDataRetryCallback(dataRetryEntry ->
                sendMessage(obtainMessage(EVENT_DATA_RETRY, dataRetryEntry)));
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mDataStallRecoveryManager.registerForDataStallReestablishEvent(this,
                EVENT_DATA_STALL_ACTION_REESTABLISH);
        mDataProfileManager.registerForDataProfilesChanged(this, EVENT_DATA_PROFILES_CHANGED);
        mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this,
                EVENT_PS_RESTRICT_ENABLED, null);
        mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this,
                EVENT_PS_RESTRICT_DISABLED, null);
        mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .registerForServiceBindingChanged(this, EVENT_DATA_SERVICE_BINDING_CHANGED);
        if (!mAccessNetworksManager.isInLegacyMode()) {
            mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                    .registerForServiceBindingChanged(this, EVENT_DATA_SERVICE_BINDING_CHANGED);
        }
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_ADD_NETWORK_REQUEST:
                onAddNetworkRequest((TelephonyNetworkRequest) msg.obj);
                break;
            case EVENT_SATISFY_NETWORK_REQUEST:
                onSatisfyNetworkRequest((TelephonyNetworkRequest) msg.obj);
                break;
            case EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS:
                DataEvaluationReason reason = (DataEvaluationReason) msg.obj;
                onReevaluateUnsatisfiedNetworkRequests(reason);
                break;
            case EVENT_REMOVE_NETWORK_REQUEST:
                onRemoveNetworkRequest((NetworkRequest) msg.obj);
                break;
            case EVENT_DATA_STALL_ACTION_REESTABLISH:
                onDataStallActionReestablish();
                break;
            case EVENT_PS_RESTRICT_ENABLED:
                mPsRestricted = true;
                break;
            case EVENT_PS_RESTRICT_DISABLED:
                mPsRestricted = false;
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.DATA_RESTRICTED_LIFTED));
                break;
            case EVENT_DATA_SERVICE_BINDING_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                int transport = (int) ar.userObj;
                boolean bound = (boolean) ar.result;
                onDataServiceBindingChanged(transport, bound);
                break;
            case EVENT_SIM_STATE_CHANGED:
                int simState = msg.arg1;
                onSimStateChanged(simState);
                break;
            case EVENT_DATA_PROFILES_CHANGED:
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.DATA_PROFILES_CHANGED));
                break;
            case EVENT_DATA_RETRY:
                DataRetryEntry dataRetryEntry = (DataRetryEntry) msg.obj;
                setupDataNetwork(dataRetryEntry.dataProfile, null, dataRetryEntry);
                break;
            default:
                loge("Unexpected event " + msg.what);
        }
    }

    /**
     * Add a network request, which is originated from the apps. Note that add a network request
     * is not necessarily setting up a net {@link DataNetwork}.
     *
     * @param networkRequest Network request
     *
     */
    public void addNetworkRequest(@NonNull NetworkRequest networkRequest) {
        // TODO: TelephonyNetworkRequest should be created in TelephonyNetworkFactory after
        //       DcTracker and other legacy data stacks are removed.
        sendMessage(obtainMessage(EVENT_ADD_NETWORK_REQUEST,
                new TelephonyNetworkRequest(networkRequest, mPhone)));
    }

    /**
     * Called when a network request arrives data network controller.
     *
     * @param networkRequest The network request.
     */
    private void onAddNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        if (!mAllNetworkRequestList.add(networkRequest)) {
            loge("onAddNetworkRequest: Duplicate network request. " + networkRequest);
            return;
        }
        logv("onAddNetworkRequest: added " + networkRequest);
        sendMessage(obtainMessage(EVENT_SATISFY_NETWORK_REQUEST, networkRequest));
    }

    /**
     * Called when attempting to satisfy a network request. If after evaluation, the network
     * request is determined that can be satisfied, the data network controller will establish
     * the data network. If the network request can't be satisfied, it will remain in the
     * unsatisfied pool until the environment changes.
     *
     * @param networkRequest The network request to be satisfied.
     */
    private void onSatisfyNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        if (networkRequest.getState() == TelephonyNetworkRequest.REQUEST_STATE_SATISFIED) {
            logv("Already satisfied. " + networkRequest);
            return;
        }

        // Check if there is any existing data network that can satisfy the network request, and
        // attempt to attach if possible.
        if (findCompatibleDataNetworkAndAttach(networkRequest)) {
            return;
        }

        // If no data network can satisfy the requests, then start the evaluation process. Since
        // all the requests in the list have the same capabilities, we can only evaluate one
        // of them.
        DataEvaluation evaluation = evaluateNetworkRequest(networkRequest,
                DataEvaluationReason.NEW_REQUEST);
        if (evaluation.isDataAllowed()) {
            DataProfile dataProfile = evaluation.getCandidateDataProfile();
            if (dataProfile != null) {
                setupDataNetwork(dataProfile, new NetworkRequestList(networkRequest), null);
            }
        }
    }

    /**
     * Attempt to attach a network request to an existing data network that can satisfy the
     * network request.
     *
     * @param networkRequest The network request to attach.
     *
     * @return {@code false} if can't find the data network to to satisfy the network request.
     * {@code true} if the network request has been scheduled to attach to the data network.
     * If attach succeeds, the network request's state will be set to
     * {@link TelephonyNetworkRequest#REQUEST_STATE_SATISFIED}. If failed,
     * {@link #onAttachNetworkRequestsFailed(DataNetwork, NetworkRequestList)} will be invoked.
     */
    private boolean findCompatibleDataNetworkAndAttach(
            @NonNull TelephonyNetworkRequest networkRequest) {
        return findCompatibleDataNetworkAndAttach(new NetworkRequestList(networkRequest));
    }

    /**
     * Attempt to attach a network request list to an existing data network that can satisfy all the
     * network requests. Note this method does not support partial attach (i.e. Only attach some
     * of the satisfiable requests to the network). All requests must be satisfied so they can be
     * attached.
     *
     * @param requestList The network request list to attach.
     *
     * @return {@code false} if can't find the data network to to satisfy the network requests, even
     * if only one of network request can't be satisfied. {@code true} if the network request
     * has been scheduled to attach to the data network. If attach succeeds, the network request's
     * state will be set to
     * {@link TelephonyNetworkRequest#REQUEST_STATE_SATISFIED}. If failed,
     * {@link #onAttachNetworkRequestsFailed(DataNetwork, NetworkRequestList)} will be invoked.
     */
    private boolean findCompatibleDataNetworkAndAttach(@NonNull NetworkRequestList requestList) {
        // Try to find a data network that can satisfy all the network requests.
        for (DataNetwork dataNetwork : mDataNetworkList) {
            TelephonyNetworkRequest networkRequest = requestList.stream()
                    .filter(request -> !request.canBeSatisfiedBy(
                            dataNetwork.getNetworkCapabilities()))
                    .findAny()
                    .orElse(null);
            // If found any request that can't be satisfied by this data network, continue to try
            // next data network. We must find a data network that can satisfy all the provided
            // network requests.
            if (networkRequest != null) {
                continue;
            }

            // When reaching here, it means this data network can satisfy all the network requests.
            log("Found a compatible data network " + dataNetwork.name() + ". Attaching "
                    + requestList);
            return dataNetwork.attachNetworkRequests(requestList);
        }
        return false;
    }

    /**
     * @return {@code true} if checking registration state is needed before setup data network.
     * {@code false} indicates regardless in-service or out-of-service, setup data request will
     * be sent down to the data service.
     */
    private boolean shouldCheckRegistrationState() {
        // Always don't check registration state on non-DDS sub.
        if (mPhone.getPhoneId() != PhoneSwitcher.getInstance().getPreferredDataPhoneId()) {
            return false;
        }

        // TODO: Expand this method to support more scenarios if needed. On Android 12 or older
        //  Android, auto attach is enabled by default. We dropped that support in Android 13 since
        //  it's for the old 2G network. If there are other scenarios that we need to support
        //  auto-attach, can implement the logic in this method.
        return true;
    }

    /**
     * Evaluate a network request. The goal is to find a suitable {@link DataProfile} that can be
     * used to setup the data network.
     *
     * @param networkRequest The network request to evaluate.
     * @return The data evaluation result
     */
    private @NonNull DataEvaluation evaluateNetworkRequest(
            @NonNull TelephonyNetworkRequest networkRequest, DataEvaluationReason reason) {
        DataEvaluation evaluation = new DataEvaluation(reason);

        // Bypass all checks for emergency network request.
        if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            evaluation.addDataAllowedReason(DataAllowedReason.EMERGENCY_REQUEST);
            evaluation.setCandidateDataProfile(mDataProfileManager
                    .getDataProfileForNetworkRequest(networkRequest));
            log(evaluation.toString());
            return evaluation;
        }

        int transport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkRequest.getHighestPriorityNetworkCapability());
        int regState = getDataRegistrationState(transport);
        if (shouldCheckRegistrationState()
                && regState != NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                && regState != NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.NOT_IN_SERVICE);
        }

        // Check SIM state
        if (mSimState != TelephonyManager.SIM_STATE_LOADED) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.SIM_NOT_READY);
        }

        // Check if carrier specific config is loaded or not.
        if (!mDataConfigManager.isConfigCarrierSpecific()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_CONFIG_NOT_READY);
        }

        // Check CS call state and see if concurrent voice/data is allowed.
        if (mPhone.getCallTracker().getState() != PhoneConstants.State.IDLE
                && !mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()) {
            evaluation.addDataDisallowedReason(
                    DataDisallowedReason.CONCURRENT_VOICE_DATA_NOT_ALLOWED);
        }

        // Check if default data is selected.
        if (!SubscriptionManager.isValidSubscriptionId(
                SubscriptionManager.getDefaultDataSubscriptionId())) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DEFAULT_DATA_UNSELECTED);
        }

        // TODO: Support data roaming check
        /*if (mPhone.getServiceState().getDataRoaming() &&
                !mDataSettingManager.isDataRoamingEnabled()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.ROAMING_DISABLED);
        }*/

        // Check if data is restricted by the network.
        if (mPsRestricted) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_RESTRICTED_BY_NETWORK);
        }

        // Check if the request is preferred on cellular and radio is/will be turned off.
        // We are using getDesiredPowerState() instead of isRadioOn() because we also don't want
        // to setup data network when radio power is about to be turned off.
        if (transport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                && !mPhone.getServiceStateTracker().getDesiredPowerState()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.RADIO_POWER_OFF);
        }

        // Check if radio is/will be turned off by carrier.
        if (!mPhone.getServiceStateTracker().getPowerStateFromCarrier()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.RADIO_DISABLED_BY_CARRIER);
        }

        // Check if the underlying data service is bound.
        if (!mDataServiceBound.get(transport)) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_SERVICE_NOT_READY);
        }

        // TODO: Support data enabled/disabled check

        // TODO: Handle restricted request
        // TODO: Handle unmetered request

        if (!evaluation.isDataAllowed()) {
            // TODO: Add more soft disallowed reason bypass support.
        } else {
            evaluation.addDataAllowedReason(DataAllowedReason.NORMAL);
            evaluation.setCandidateDataProfile(mDataProfileManager
                    .getDataProfileForNetworkRequest(networkRequest));
        }

        log(evaluation.toString());
        return evaluation;
    }

    /**
     * Called when it's needed to evaluate all unsatisfied network requests.
     *
     * @param reason The reason for evaluation.
     */
    private void onReevaluateUnsatisfiedNetworkRequests(@NonNull DataEvaluationReason reason) {
        // First, try to group similar network request together.
        Map<Set<Integer>, NetworkRequestList> requestsMap = new ArrayMap<>();
        int count = 0;
        for (TelephonyNetworkRequest networkRequest : mAllNetworkRequestList) {
            if (networkRequest.getState() == TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED) {
                Set<Integer> key = Arrays.stream(networkRequest.getCapabilities()).boxed()
                        .collect(Collectors.toSet());
                requestsMap.computeIfAbsent(key, v -> new NetworkRequestList());
                requestsMap.get(key).add(networkRequest);
                count++;
            }
        }

        if (count == 0) {
            return;
        }

        log("Re-evaluating " + count + " unsatisfied network requests in " + requestsMap.size()
                + " groups, " + requestsMap.keySet().stream()
                .map(capsSet -> DataUtils.networkCapabilitiesToString(
                        capsSet.stream().mapToInt(Number::intValue).toArray()))
                .collect(Collectors.joining(",")));

        // Second, see if any existing network can satisfy those network requests.
        for (NetworkRequestList requestList : requestsMap.values()) {
            if (findCompatibleDataNetworkAndAttach(requestList)) {
                continue;
            }

            // If no data network can satisfy the requests, then start the evaluation process. Since
            // all the requests in the list have the same capabilities, we can only evaluate one
            // of them.
            DataEvaluation evaluation = evaluateNetworkRequest(requestList.get(0), reason);
            if (evaluation.isDataAllowed()) {
                DataProfile dataProfile = evaluation.getCandidateDataProfile();
                if (dataProfile != null) {
                    setupDataNetwork(dataProfile, requestList, null);
                }
            }
        }
    }

    /**
     * Remove a network request, which is originated from the apps. Note that remove a network
     * will not result in tearing down the network. The tear down request directly comes from
     * {@link com.android.server.ConnectivityService} through
     * {@link NetworkAgent#onNetworkUnwanted()}.
     *
     * @param networkRequest Network request
     */
    // TODO: TelephonyNetworkRequest should be used after DcTracker and other legacy data stacks are
    //  removed.
    public void removeNetworkRequest(@NonNull NetworkRequest networkRequest) {
        sendMessage(obtainMessage(EVENT_REMOVE_NETWORK_REQUEST, networkRequest));
    }

    private void onRemoveNetworkRequest(@NonNull NetworkRequest request) {
        // TODO: TelephonyNetworkRequest should be used after DcTracker and other legacy data stacks
        //  are removed.
        // temp solution: find the original telephony network request.
        TelephonyNetworkRequest networkRequest = mAllNetworkRequestList.stream()
                .filter(nr -> nr.getNativeNetworkRequest().equals(request))
                .findFirst()
                .orElse(null);
        if (networkRequest == null) {
            return;
        }

        if (!mAllNetworkRequestList.remove(networkRequest)) {
            loge("onRemoveNetworkRequest: Network request does not exist. " + networkRequest);
            return;
        }

        if (networkRequest.getAttachedNetwork() != null) {
            networkRequest.getAttachedNetwork().detachNetworkRequest(networkRequest);
        }
        logv("onRemoveNetworkRequest: Removed " + networkRequest);
    }

    /**
     * Called when data config was updated.
     */
    private void onDataConfigUpdated() {
        log("onDataConfigUpdated: config is "
                + (mDataConfigManager.isConfigCarrierSpecific() ? "" : "not ")
                + "carrier specific. mSimState="
                + SubscriptionInfoUpdater.simStateString(mSimState));
        updateNetworkRequestsPriority();

        sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                DataEvaluationReason.DATA_CONFIG_CHANGED));
    }

    /**
     * Update each network request's priority.
     */
    private void updateNetworkRequestsPriority() {
        for (TelephonyNetworkRequest networkRequest : mAllNetworkRequestList) {
            networkRequest.updatePriority();
        }
    }

    /**
     * Setup data network.
     *
     * @param dataProfile The data profile to setup the data network.
     * @param networkRequestList The network requests associated with the data network. {@code null}
     * indicates that all unsatisfied network requests that can be satisfied by the data profile
     * will be used to attach to the created network.
     * @param dataRetryEntry Data retry entry. {@code null} if this data network setup is not
     * initiated by a data retry.
     */
    private void setupDataNetwork(@NonNull DataProfile dataProfile,
            @Nullable NetworkRequestList networkRequestList,
            @Nullable DataRetryEntry dataRetryEntry) {
        log("onSetupDataNetwork: dataProfile=" + dataProfile);
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.getDataProfile().equals(dataProfile)) {
                log("onSetupDataNetwork: Found existing data network " + dataNetwork.name()
                        + " has the same data profile.");
                return;
            }
        }

        // If the provided network request list is empty, then find all unsatisfied network requests
        // that can be satisfied by this data profile.
        if (networkRequestList == null || networkRequestList.isEmpty()) {
            networkRequestList = new NetworkRequestList();
            networkRequestList.addAll(mAllNetworkRequestList.stream()
                    .filter(request -> request.getState()
                            == TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED)
                    .filter(request -> dataProfile.canSatisfy(request.getCapabilities()))
                    .collect(Collectors.toList()));
        }

        if (networkRequestList.isEmpty()) {
            log("Can't find any unsatisfied network requests that can be satisfied by this data "
                    + "profile.");
            return;
        }

        logl("Creating data network with " + dataProfile + ", and attaching "
                + networkRequestList.size() + " network requests to it.");
        mDataNetworkList.add(new DataNetwork(mPhone, getLooper(), mDataServiceManagers,
                dataProfile, networkRequestList, new DataNetwork.DataNetworkCallback() {
                    @Override
                    public void onSetupDataFailed(@NonNull DataNetwork dataNetwork,
                            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                            long retryDurationMillis) {
                        post(() -> {
                            if (dataRetryEntry != null) {
                                dataRetryEntry.setState(DataRetryEntry.RETRY_STATE_FAILED);
                            }
                            DataNetworkController.this.onDataNetworkSetupDataFailed(
                                    dataNetwork, requestList, cause, retryDurationMillis);
                        });
                    }

                    @Override
                    public void onConnected(@NonNull DataNetwork dataNetwork) {
                        post(() -> {
                            if (dataRetryEntry != null) {
                                dataRetryEntry.setState(DataRetryEntry.RETRY_STATE_SUCCEEDED);
                            }
                            DataNetworkController.this.onDataNetworkConnected(dataNetwork);
                        });
                    }

                    @Override
                    public void onAttachFailed(@NonNull DataNetwork dataNetwork,
                            @NonNull NetworkRequestList requestList) {
                        post(() -> {
                            DataNetworkController.this.onAttachNetworkRequestsFailed(
                                    dataNetwork, requestList);
                        });
                    }

                    @Override
                    public void onValidationStatusChanged(@NonNull DataNetwork dataNetwork,
                            @NonNull DataValidationResult dataValidationResult) {
                        post(() -> {
                            DataNetworkController.this.onDataNetworkValidationStatusChanged(
                                    dataNetwork, dataValidationResult);
                        });
                    }

                    @Override
                    public void onSuspendedStateChanged(@NonNull DataNetwork dataNetwork,
                            boolean suspended) {
                        post(() -> {
                            DataNetworkController.this.onDataNetworkSuspendedStateChanged(
                                    dataNetwork, suspended);
                        });
                    }

                    @Override
                    public void onDisconnected(@NonNull DataNetwork dataNetwork,
                            @DataFailureCause int cause) {
                        post(() -> {
                            DataNetworkController.this.onDataNetworkDisconnected(
                                    dataNetwork, cause);
                        });
                    }
                }));
    }

    /**
     * Called when setup data network failed.
     *
     * @param dataNetwork The data network.
     * @param requestList The network requests attached to the data network.
     * @param cause The fail cause
     * @param retryDurationMillis The retry timer suggested by the network/data service.
     */
    private void onDataNetworkSetupDataFailed(@NonNull DataNetwork dataNetwork,
            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
            long retryDurationMillis) {
        mDataNetworkList.remove(dataNetwork);
        // Data retry manager will determine if retry is needed. If needed, retry will be scheduled.
        mDataRetryManager.evaluateDataRetry(dataNetwork.getDataProfile(), requestList, cause,
                retryDurationMillis);
    }

    /**
     * Called when data network is connected.
     *
     * @param dataNetwork The data network.
     */
    private void onDataNetworkConnected(@NonNull DataNetwork dataNetwork) {

    }

    /**
     * Called when data network validation status changed.
     *
     * @param dataNetwork The data network.
     * @param dataValidationResult Data validation result from connectivity service.
     */
    private void onDataNetworkValidationStatusChanged(@NonNull DataNetwork dataNetwork,
            @NonNull DataValidationResult dataValidationResult) {
        String redirectUrl = dataValidationResult.getRedirectUri().toString();
        if (!TextUtils.isEmpty(redirectUrl)) {
            Intent intent = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_REDIRECTED);
            intent.putExtra(TelephonyManager.EXTRA_REDIRECTION_URL, redirectUrl);
            mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
            log("Notify carrier signal receivers with redirectUrl: " + redirectUrl);
        }

        // TODO: Add DataConfigManager.isRecoveryOnBadNetworkEnabled()

        NetworkCapabilities nc = dataNetwork.getNetworkCapabilities();
        if (nc != null
                && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            mInternetValidationStatusRegistrants.notifyResult(
                    dataValidationResult.getValidationStatus());
        }
    }

    /**
     * Called when data network suspended state changed.
     *
     * @param dataNetwork The data network.
     * @param suspended {@code true} if data is suspended.
     */
    private void onDataNetworkSuspendedStateChanged(@NonNull DataNetwork dataNetwork,
            boolean suspended) {

    }

    /**
     * Called when data network disconnected.
     *
     * @param dataNetwork The data network.
     * @param cause The disconnect cause.
     */
    private void onDataNetworkDisconnected(@NonNull DataNetwork dataNetwork,
            @DataFailureCause int cause) {
        // TODO: Should perform retry here.

        mDataNetworkList.remove(dataNetwork);
    }

    /**
     * Called when network requests failed to attach to the data network.
     *
     * @param dataNetwork The data network that can't be attached.
     * @param requestList The requests failed to attach to the network.
     */
    private void onAttachNetworkRequestsFailed(@NonNull DataNetwork dataNetwork,
            @NonNull NetworkRequestList requestList) {
        // TODO: Perform retry if needed.
    }

    /**
     * Handle data stall action reestablish event.
     */
    private void onDataStallActionReestablish() {
    }

    /**
     * Called when data service binding changed.
     *
     * @param transport The transport of the changed data service.
     * @param bound {code @true} if data service is bound.
     */
    private void onDataServiceBindingChanged(@TransportType int transport, boolean bound) {
        if (!bound) {
            if (transport == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                /*if (mDataConfigManager.shouldCleanupIwlanDataNetworksWhenDataServiceRestarted()) {
                    // Clean up IWLAN DNs
                }*/
            }
        } else {
            // reset throttling after binding to data service
            // mDataThrottler.reset();
        }
        mDataServiceBound.put(transport, bound);
    }

    /**
     * Called when SIM is absent.
     */
    private void onSimAbsent() {

    }

    /**
     * Called when SIM state changes.
     *
     * @param simState SIM state. (Note this is mixed with card state and application state.)
     */
    private void onSimStateChanged(@SimState int simState) {
        log("onSimStateChanged: state=" + SubscriptionInfoUpdater.simStateString(simState));
        if (mSimState != simState) {
            mSimState = simState;
            if (simState == TelephonyManager.SIM_STATE_ABSENT) {
                onSimAbsent();
            } else if (simState == TelephonyManager.SIM_STATE_LOADED) {
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.SIM_LOADED));
            }
        }
    }

    /**
     * @return Data config manager instance.
     */
    public @NonNull DataConfigManager getDataConfigManager() {
        return mDataConfigManager;
    }

    /**
     * @return Data profile manager instance.
     */
    public @NonNull DataProfileManager getDataProfileManager() {
        return mDataProfileManager;
    }

    /**
     * Get data network type based on transport.
     *
     * @param transport The transport.
     * @return The current network type.
     */
    private @NetworkType int getDataNetworkType(@TransportType int transport) {
        ServiceState ss = mPhone.getServiceState();
        NetworkRegistrationInfo nrs = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, transport);
        if (nrs != null) {
            return nrs.getAccessNetworkTechnology();
        }
        return TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    /**
     * Get data registration state based on transport.
     *
     * @param transport The transport.
     * @return The registration state.
     */
    private @RegistrationState int getDataRegistrationState(@TransportType int transport) {
        ServiceState ss = mPhone.getServiceState();
        NetworkRegistrationInfo nrs = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, transport);
        if (nrs != null) {
            return nrs.getRegistrationState();
        }
        return NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
    }

    /**
     * Register for internet data network validation status changed event.
     *
     * @param handler The handler to handle the event.
     * @param what The event.
     */
    public void registerForInternetValidationStatusChanged(@NonNull Handler handler, int what) {
        mInternetValidationStatusRegistrants.addUnique(handler, what, null);
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
     * Dump the state of DataNetworkController
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataNetworkController.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Current data networks:");
        pw.increaseIndent();
        for (DataNetwork dn : mDataNetworkList) {
            dn.dump(fd, printWriter, args);
        }
        pw.decreaseIndent();
        pw.println("Historical connected data networks:");
        pw.increaseIndent();
        for (DataNetwork dn: mHistoricalDataNetworkList) {
            // Do not print networks which is already in current network list.
            if (!mDataNetworkList.contains(dn)) {
                dn.dump(fd, printWriter, args);
            }
        }
        pw.decreaseIndent();

        pw.println("All telephony network requests:");
        pw.increaseIndent();
        for (TelephonyNetworkRequest networkRequest : mAllNetworkRequestList) {
            pw.println(networkRequest);
        }
        pw.decreaseIndent();

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println("-------------------------------------");
        mDataProfileManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataSettingsManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataStallRecoveryManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataConfigManager.dump(fd, pw, args);

        pw.decreaseIndent();
    }
}
