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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.TelephonyNetworkSpecifier;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.DataState;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.DataFailCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.TrafficDescriptor;
import android.text.TextUtils;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;
import com.android.internal.telephony.dataconnection.AccessNetworksManager;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.net.module.util.NetworkCapabilitiesUtils;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DataNetwork class represents a single PDN (Packet Data Network).
 *
 * The life cycle of a data network starts from {@link ConnectingState}. If setup data request
 * succeeds, then it enters {@link ConnectedState}, otherwise it enters
 * {@link DisconnectedState}.
 *
 * When data network is in {@link ConnectingState}, it can enter {@link HandoverState} if handover
 * between IWLAN and cellular occurs. After handover completes or fails, it return back to
 * {@link ConnectedState}. When the data network is about to be disconnected, it first enters
 * {@link DisconnectingState} when performing graceful tear down or when sending the data
 * deactivation request. At the end, it enters {@link DisconnectedState} when {@link DataService}
 * notifies data disconnected. Note that a unsolicited disconnected event from {@link DataService}
 * can immediately move data network transited from {@link ConnectedState} to
 * {@link DisconnectedState}. {@link DisconnectedState} is the final state of a data network.
 *
 * State machine diagram:
 *
 *                   ┌─────────────────────────────────────────┐
 *                   │                                         │
 *                   │              ┌─────────┐                │
 *                   │              │Handover │                │
 *                   │              └─▲────┬──┘                │
 *                   │                │    │                   │
 *             ┌─────┴─────┐        ┌─┴────▼──┐        ┌───────▼──────┐
 *             │Connecting ├────────►Connected├────────►Disconnecting │
 *             └─────┬─────┘        └────┬────┘        └───────┬──────┘
 *                   │                   │                     │
 *                   │             ┌─────▼──────┐              │
 *                   └─────────────►Disconnected◄──────────────┘
 *                                 └────────────┘
 *
 */
public class DataNetwork extends StateMachine {
    private static final boolean VDBG = false;
    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for attaching a network request. */
    private static final int EVENT_ATTACH_NETWORK_REQUEST = 2;

    /** Event for detaching a network request. */
    private static final int EVENT_DETACH_NETWORK_REQUEST = 3;

    /** Event for network validation status result arrives. */
    private static final int EVENT_VALIDATION_STATUS_RESULT = 4;

    /** Event for allocating PDU session id response. */
    private static final int EVENT_ALLOCATE_PDU_SESSION_ID_RESPONSE = 5;

    /** Event for setup data network response. */
    private static final int EVENT_SETUP_DATA_CALL_RESPONSE = 6;

    /** Event for tearing down data network. */
    private static final int EVENT_TEAR_DOWN_NETWORK = 7;

    /** Event triggered by {@link DataServiceCallback#onDataCallListChanged(List)}. */
    private static final int EVENT_DATA_STATE_CHANGED = 8;

    /** Data network service state changed event. */
    private static final int EVENT_SERVICE_STATE_CHANGED = 9;

    /** The default MTU for IPv4 network */
    private static final int DEFAULT_MTU_V4 = 1280;

    /** The default MTU for IPv6 network */
    private static final int DEFAULT_MTU_V6 = 1280;

    /** Invalid context id. */
    private static final int INVALID_CID = -1;

    /**
     * The data network providing default internet will have a higher score of 50. Other network
     * will have a slightly lower score of 45. The intention is other connections will not cause
     * connectivity service to tear down default internet connection. For example, to validate
     * internet connection on non-default data SIM, we'll set up a temporary internet network on
     * that data SIM. In this case, score of 45 is assigned so connectivity service will not replace
     * the default internet network with it.
     */
    private static final int DEFAULT_INTERNET_NETWORK_SCORE = 50;
    private static final int OTHER_NETWORK_SCORE = 45;

    @IntDef(prefix = {"DEACTIVATION_REASON_"},
            value = {
                    TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED,
                    TEAR_DOWN_REASON_SIM_REMOVAL,
                    TEAR_DOWN_REASON_AIRPLANE_MODE_ON,
                    TEAR_DOWN_REASON_DATA_DISABLED,
            })
    public @interface TearDownReason {}

    /** Data network tear down requested by connectivity service. */
    public static final int TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED = 1;

    /** Data network tear down due to SIM removal. */
    public static final int TEAR_DOWN_REASON_SIM_REMOVAL = 2;

    /** Data network tear down due to airplane mode turned on. */
    public static final int TEAR_DOWN_REASON_AIRPLANE_MODE_ON = 3;

    /** Data network tear down due to data disabled (by user, policy, carrier, etc...). */
    public static final int TEAR_DOWN_REASON_DATA_DISABLED = 4;

    /** Data network tear down due to no live network request. */
    public static final int TEAR_DOWN_REASON_NO_LIVE_REQUEST = 5;

    /** The phone instance. */
    private final @NonNull Phone mPhone;
    /**
     * The subscription id. This is assigned when the network is created, and not supposed to
     * change afterwards.
     */
    private final int mSubId;

    /** RIL interface. */
    private final @NonNull CommandsInterface mRil;

    /** Local log. */
    private final LocalLog mLocalLog = new LocalLog(128);

    private final DefaultState mDefaultState = new DefaultState();
    private final ConnectingState mConnectingState = new ConnectingState();
    private final ConnectedState mConnectedState = new ConnectedState();
    private final HandoverState mHandoverState = new HandoverState();
    private final DisconnectingState mDisconnectingState = new DisconnectingState();
    private final DisconnectedState mDisconnectedState = new DisconnectedState();

    /** The callback to receives data network state update. */
    private final @NonNull DataNetworkCallback mDataNetworkCallback;

    /** The log tag. */
    private String mLogTag;

    /**
     * The unique context id assigned by the data service in {@link DataCallResponse#getId()}. One
     * for {@link AccessNetworkConstants#TRANSPORT_TYPE_WWAN} and one for
     * {@link AccessNetworkConstants#TRANSPORT_TYPE_WLAN}. The reason for storing both is that
     * during handover, both cid will be used.
     */
    private SparseIntArray mCid = new SparseIntArray(2);

    /** PDU session id. */
    private int mPduSessionId = DataCallResponse.PDU_SESSION_ID_NOT_SET;

    /**
     * Data service managers for accessing {@link AccessNetworkConstants#TRANSPORT_TYPE_WWAN} and
     * {@link AccessNetworkConstants#TRANSPORT_TYPE_WLAN} data services.
     */
    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers;

    /** Access networks manager. */
    private final @NonNull AccessNetworksManager mAccessNetworksManager;

    /** Data network controller. */
    private final @NonNull DataNetworkController mDataNetworkController;

    /** Data config manager. */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** The network agent associated with this data network. */
    private @NonNull TelephonyNetworkAgent mNetworkAgent;

    /** The data profile used to establish this data network. */
    private final @NonNull DataProfile mDataProfile;

    /** The network capabilities of this data network. */
    private @NonNull NetworkCapabilities mNetworkCapabilities;

    /** The link properties of this data network. */
    private @NonNull LinkProperties mLinkProperties;

    /** The network requests associated with this data network */
    private @NonNull NetworkRequestList mAttachedNetworkRequestList = new NetworkRequestList();

    /**
     * The latest data call response received from either
     * {@link DataServiceCallback#onSetupDataCallComplete(int, DataCallResponse)} or
     * {@link DataServiceCallback#onDataCallListChanged(List)}. The very first update must be
     * from {@link DataServiceCallback#onSetupDataCallComplete(int, DataCallResponse)}.
     */
    private @Nullable DataCallResponse mDataCallResponse = null;

    /**
     * The fail cause from either setup data failure or unsolicited disconnect reported by data
     * service.
     */
    private @DataFailureCause int mFailCause = DataFailCause.NONE;

    /** Indicates if data network is suspended. */
    private boolean mSuspended = false;

    /**
     * The network validation result from connectivity service.
     */
    private @Nullable DataValidationResult mDataValidationResult;

    /**
     * The current transport of the data network. For handover, the current transport will be set
     * after handover completes.
     */
    private @TransportType int mTransport = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;

    /**
     * The preferred transport of the data network. If the preferred transport is different from
     * the current transport, then handover will happen.
     */
    private @TransportType int mPreferredTransport = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;

    /**
     * The interface for data network callback.
     */
    public interface DataNetworkCallback {
        /**
         * Called when data setup failed.
         *
         * @param dataNetwork The data network.
         * @param requestList The network requests attached to this data network.
         * @param cause The fail cause of setup data network.
         * @param retryDurationMillis The network suggested data retry duration in milliseconds as
         * specified in 3GPP TS 24.302 section 8.2.9.1. The {@link DataProfile} associated to this
         * data network will be throttled for the specified duration unless
         * {@link DataServiceCallback#onApnUnthrottled} is called. {@link Long#MAX_VALUE} indicates
         * data retry should not occur. {@link DataCallResponse#RETRY_DURATION_UNDEFINED} indicates
         * network did not suggest any retry duration.
         */
        void onSetupDataFailed(@NonNull DataNetwork dataNetwork,
                @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                long retryDurationMillis);

        /**
         * Called when data network enters {@link ConnectedState}.
         *
         * @param dataNetwork The data network.
         */
        void onConnected(@NonNull DataNetwork dataNetwork);

        /**
         * Called when data network validation status changed.
         *
         * @param dataNetwork The data network.
         * @param dataValidationResult Data validation result from connectivity service.
         */
        void onValidationStatusChanged(@NonNull DataNetwork dataNetwork,
                @NonNull DataValidationResult dataValidationResult);

        /**
         * Called when data network suspended state changed.
         *
         * @param dataNetwork The data network.
         * @param suspended {@code true} if data is suspended.
         */
        void onSuspendedStateChanged(@NonNull DataNetwork dataNetwork, boolean suspended);

        /**
         * Called when network requests were failed to attach to the data network.
         *
         * @param dataNetwork The data network.
         * @param requestList The requests failed to attach.
         */
        void onAttachFailed(@NonNull DataNetwork dataNetwork,
                @NonNull NetworkRequestList requestList);

        /**
         * Called when data network enters {@link DisconnectedState}. Note this is only called
         * when the data network was previously connected. For setup data network failed,
         * {@link #onSetupDataFailed(DataNetwork, int, long)} is called.
         *
         * @param dataNetwork The data network.
         * @param cause The disconnect cause.
         */
        void onDisconnected(@NonNull DataNetwork dataNetwork, @DataFailureCause int cause);
    }

    /**
     * The wrapper of the validation result from connectivity service.
     */
    public static class DataValidationResult {
        /** Validation status */
        private final @ValidationStatus int mValidationStatus;
        /** Redirected URI */
        private final @Nullable Uri mRedirectUri;

        /**
         * Constructor
         *
         * @param validationStatus The validation status.
         * @param redirectUri The redirected URI.
         */
        public DataValidationResult(@ValidationStatus int validationStatus,
                @Nullable Uri redirectUri) {
            mValidationStatus = validationStatus;
            mRedirectUri = redirectUri;
        }

        /**
         * @return The validation status
         */
        public @ValidationStatus int getValidationStatus() {
            return mValidationStatus;
        }

        /**
         * @return The redirected URI.
         */
        public @Nullable Uri getRedirectUri() {
            return mRedirectUri;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DataValidationResult that = (DataValidationResult) o;
            return mValidationStatus == that.mValidationStatus
                    && Objects.equals(mRedirectUri, that.mRedirectUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mValidationStatus, mRedirectUri);
        }
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the state machine. Currently the handler thread is the
     * phone process's main thread.
     * @param dataServiceManagers Data service managers.
     * @param dataProfile The data profile for establishing the data network.
     * @param networkRequestList The initial network requests attached to this data network.
     * @param callback The callback to receives data network state update.
     */
    public DataNetwork(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull SparseArray<DataServiceManager> dataServiceManagers,
            @NonNull DataProfile dataProfile,
            @NonNull NetworkRequestList networkRequestList,
            @NonNull DataNetworkCallback callback) {
        super("DataNetwork", looper);
        mPhone = phone;
        mSubId = phone.getSubId();
        mRil = mPhone.mCi;
        mLinkProperties = new LinkProperties();
        mDataServiceManagers = dataServiceManagers;
        mAccessNetworksManager = phone.getAccessNetworksManager();
        mDataNetworkController = phone.getDataNetworkController();
        mDataConfigManager = mDataNetworkController.getDataConfigManager();
        mDataNetworkCallback = callback;
        mDataProfile = dataProfile;
        dataProfile.setLastSetupTimestamp(SystemClock.elapsedRealtime());
        mAttachedNetworkRequestList.addAll(networkRequestList);
        mCid.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, INVALID_CID);
        mCid.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, INVALID_CID);

        for (TelephonyNetworkRequest networkRequest : networkRequestList) {
            networkRequest.setAttachedNetwork(DataNetwork.this);
            networkRequest.setState(
                    TelephonyNetworkRequest.REQUEST_STATE_SATISFIED);
        }

        addState(mDefaultState);
        addState(mConnectingState, mDefaultState);
        addState(mConnectedState, mDefaultState);
        addState(mHandoverState, mDefaultState);
        addState(mDisconnectingState, mDefaultState);
        addState(mDisconnectedState, mDefaultState);
        setInitialState(mConnectingState);

        start();
    }

    /**
     * Create the telephony network agent.
     *
     * @return The telephony network agent.
     */
    private @NonNull TelephonyNetworkAgent createNetworkAgent() {
        final NetworkAgentConfig.Builder configBuilder = new NetworkAgentConfig.Builder();
        configBuilder.setLegacyType(ConnectivityManager.TYPE_MOBILE);
        configBuilder.setLegacyTypeName("MOBILE");
        int networkType = getDataNetworkType();
        configBuilder.setLegacySubType(networkType);
        configBuilder.setLegacySubTypeName(TelephonyManager.getNetworkTypeName(networkType));
        if (mDataProfile.getApnSetting() != null) {
            configBuilder.setLegacyExtraInfo(mDataProfile.getApnSetting().getApnName());
        }

        final NetworkFactory factory = PhoneFactory.getNetworkFactory(
                mPhone.getPhoneId());
        final NetworkProvider provider = (null == factory) ? null : factory.getProvider();

        return new TelephonyNetworkAgent(mPhone, getHandler().getLooper(), this,
                getNetworkScore(), configBuilder.build(), provider);
    }

    /**
     * The default state. Any events that were not handled by the child states fallback to this
     * state.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DefaultState extends State {
        @Override
        public void enter() {
            mDataConfigManager.registerForConfigUpdate(getHandler(), EVENT_DATA_CONFIG_UPDATED);
            mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                    .registerForDataCallListChanged(getHandler(), EVENT_DATA_STATE_CHANGED);
            mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN, getHandler(),
                    EVENT_SERVICE_STATE_CHANGED,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            if (!mAccessNetworksManager.isInLegacyMode()) {
                mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .registerForDataCallListChanged(getHandler(), EVENT_DATA_STATE_CHANGED);
                mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN, getHandler(),
                        EVENT_SERVICE_STATE_CHANGED,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
            }


        }

        @Override
        public void exit() {
            mDataConfigManager.unregisterForConfigUpdate(getHandler());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DATA_CONFIG_UPDATED:
                    onDataConfigUpdated();
                    break;
                case EVENT_SERVICE_STATE_CHANGED: {
                    updateSuspendState();
                    // TODO: Update TCP buffer size
                    // TODO: Update Bandwidth
                    break;
                }
                case EVENT_ATTACH_NETWORK_REQUEST: {
                    NetworkRequestList requestList = (NetworkRequestList) msg.obj;
                    NetworkRequestList failedList = new NetworkRequestList();
                    for (TelephonyNetworkRequest networkRequest : requestList) {
                        if (networkRequest.canBeSatisfiedBy(getNetworkCapabilities())) {
                            mAttachedNetworkRequestList.add(networkRequest);
                            networkRequest.setAttachedNetwork(DataNetwork.this);
                            networkRequest.setState(
                                    TelephonyNetworkRequest.REQUEST_STATE_SATISFIED);
                            log("Successfully attached network request " + networkRequest);
                        } else {
                            failedList.add(networkRequest);
                            log("Attached failed. Cannot satisfy the network request "
                                    + networkRequest);
                        }
                        if (failedList.size() > 0) {
                            mDataNetworkCallback.onAttachFailed(DataNetwork.this, failedList);
                        }
                    }
                    break;
                }
                case EVENT_DETACH_NETWORK_REQUEST: {
                    TelephonyNetworkRequest networkRequest = (TelephonyNetworkRequest) msg.obj;
                    mAttachedNetworkRequestList.remove(networkRequest);
                    networkRequest.setState(TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED);
                    networkRequest.setAttachedNetwork(null);
                    break;
                }
                case EVENT_VALIDATION_STATUS_RESULT:
                    loge("Ignore the validation status: "
                            + DataUtils.validationStatusToString(msg.arg1));
                    break;
                case EVENT_DATA_STATE_CHANGED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int transport = (int) ar.userObj;
                    onDataStateChanged(transport, (ArrayList<DataCallResponse>) ar.result);
                    break;
                }
                case EVENT_TEAR_DOWN_NETWORK:
                    // Ignore the request when not in the correct state.
                    break;
                default:
                    loge("Unhandled event " + eventToString(msg.what));
                    break;
            }
            return HANDLED;
        }
    }

    /**
     * The connecting state. This is the initial state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class ConnectingState extends State {
        @Override
        public void enter() {
            // Need to update the transport so we have the correct log tag.
            updatePreferredTransports();
            // Need to calculate the initial capabilities before creating the network agent.
            updateNetworkCapabilities();
            mNetworkAgent = createNetworkAgent();
            mLogTag = "DN-" + mNetworkAgent.getId() + "-"
                    + ((mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) ? "C" : "I");

            notifyPreciseDataConnectionState();
            if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                allocatePduSessionId();
                return;
            }

            setupData();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            switch (msg.what) {
                case EVENT_ALLOCATE_PDU_SESSION_ID_RESPONSE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception == null) {
                        mPduSessionId = (int) ar.result;
                        log("Set PDU session id to " + mPduSessionId);
                    } else {
                        loge("Failed to allocate PDU session id. e=" + ar.exception);
                    }
                    setupData();
                    break;
                case EVENT_SETUP_DATA_CALL_RESPONSE:
                    int resultCode = msg.arg1;
                    DataCallResponse dataCallResponse =
                            msg.getData().getParcelable(DataServiceManager.DATA_CALL_RESPONSE);
                    onSetupResponse(resultCode, dataCallResponse);
                    break;
                case EVENT_TEAR_DOWN_NETWORK:
                    // Defer the tear down request until connected or disconnected.
                    deferMessage(msg);
                    break;
                case EVENT_DATA_STATE_CHANGED:
                    // Ignore any data call list changed event before connected.
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * The connected state. This is the state when data network becomes usable.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class ConnectedState extends State {
        @Override
        public void enter() {
            log("network connected.");
            notifyPreciseDataConnectionState();
            mNetworkAgent.markConnected();
            mDataNetworkCallback.onConnected(DataNetwork.this);
            updateSuspendState();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            switch (msg.what) {
                case EVENT_TEAR_DOWN_NETWORK:
                    transitionTo(mDisconnectingState);
                    onTearDown(msg.arg1);
                    break;
                case EVENT_VALIDATION_STATUS_RESULT:
                    DataValidationResult result = new DataValidationResult(msg.arg1, (Uri) msg.obj);
                    if (!result.equals(mDataValidationResult)) {
                        mDataValidationResult = result;
                        mDataNetworkCallback.onValidationStatusChanged(DataNetwork.this,
                                mDataValidationResult);
                        log("Validation status changed: "
                                + DataUtils.validationStatusToString(msg.arg1));
                    } else {
                        log("Validation status not changed: "
                                + DataUtils.validationStatusToString(msg.arg1));
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * The handover state. This is the state when data network handover between IWLAN and cellular.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class HandoverState extends State {
        @Override
        public void enter() {
            notifyPreciseDataConnectionState();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            switch (msg.what) {
                case EVENT_TEAR_DOWN_NETWORK:
                    // Defer the deactivate request until handover succeeds or fails.
                    deferMessage(msg);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    /**
     * The disconnecting state. This is the state when data network is about to be disconnected.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DisconnectingState extends State {
        @Override
        public void enter() {
            notifyPreciseDataConnectionState();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            return NOT_HANDLED;
        }
    }

    /**
     * The disconnected state. This is the final state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DisconnectedState extends State {
        @Override
        public void enter() {
            log("Data network disconnected.");
            // Gracefully handle all the un-processed events then quit the state machine.
            // quit() throws a QUIT event to the end of message queue. All the events before quit()
            // will be processed. Events after quit() will not be processed.
            quit();
            notifyPreciseDataConnectionState();
            mNetworkAgent.unregister();

            if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WLAN
                    && mPduSessionId != DataCallResponse.PDU_SESSION_ID_NOT_SET) {
                mRil.releasePduSessionId(null, mPduSessionId);
            }
            detachAllNetworkRequests();
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            return NOT_HANDLED;
        }
    }

    @Override
    protected void unhandledMessage(Message msg) {
        IState state = getCurrentState();
        loge("Unhandled message " + msg.what + " in state "
                + (state == null ? "null" : state.getName()));
    }

    /**
     * Attempt to attach the network request list to this data network. Whether the network can
     * satisfy the request or not will be checked when EVENT_ATTACH_NETWORK_REQUEST is processed.
     * If the request can't be attached, {@link DataNetworkCallback#onAttachFailed(
     * DataNetwork, NetworkRequestList)} will be called, and retry should be scheduled.
     *
     * @param requestList Network request list to attach.
     * @return {@code false} if the network is already disconnected. {@code true} means the request
     * has been scheduled to attach to the network. If attach succeeds, the network request's state
     * will be set to {@link TelephonyNetworkRequest#REQUEST_STATE_SATISFIED}. If failed, the
     * callback {@link DataNetworkCallback#onAttachFailed(DataNetwork, NetworkRequestList)} will
     * be called, and retry should be scheduled.
     */
    public boolean attachNetworkRequests(@NonNull NetworkRequestList requestList) {
        // If the network is already ended, we still attach the network request to the data network,
        // so it can be retried later by data network controller.
        if (getCurrentState() == null || isDisconnected()) {
            // The state machine has already stopped. This is due to data network is disconnected.
            return false;
        }
        sendMessage(obtainMessage(EVENT_ATTACH_NETWORK_REQUEST, requestList));
        return true;
    }

    /** Detach all network requests from the data network. */
    private void detachAllNetworkRequests() {
        for (TelephonyNetworkRequest networkRequest : mAttachedNetworkRequestList) {
            networkRequest.setState(TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED);
            networkRequest.setAttachedNetwork(null);
        }
        mAttachedNetworkRequestList.clear();
    }

    /**
     * Detach the network request from this data network. Note that this will not tear down the
     * network.
     *
     * @param networkRequest Network request to detach.
     */
    public void detachNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        if (getCurrentState() == null || isDisconnected()) {
            mAttachedNetworkRequestList.remove(networkRequest);
            return;
        }
        sendMessage(obtainMessage(EVENT_DETACH_NETWORK_REQUEST, networkRequest));
    }

    /**
     * Update the network capabilities.
     */
    private void updateNetworkCapabilities() {
        final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

        builder.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(mSubId).build());
        builder.setSubscriptionIds(Collections.singleton(mSubId));

        ApnSetting apnSetting = mDataProfile.getApnSetting();
        if (apnSetting != null) {
            for (int apnType : apnSetting.getApnTypes()) {
                int cap = DataUtils.apnTypeToNetworkCapability(apnType);
                if (cap >= 0) {
                    builder.addCapability(cap);
                }
            }
        }

        // TODO: Support NET_CAPABILITY_NOT_METERED
        // TODO: Support NET_CAPABILITY_NOT_RESTRICTED
        // TODO: Support NET_CAPABILITY_NOT_CONGESTED correctly
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);

        // TODO: Support NET_CAPABILITY_TEMPORARILY_NOT_METERED
        // TODO: Support NET_CAPABILITY_NOT_VCN_MANAGED correctly
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);

        if (!mPhone.getServiceState().getDataRoaming()) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        }

        if (!mSuspended) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        }

        if (NetworkCapabilitiesUtils.inferRestrictedCapability(builder.build())) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }

        NetworkCapabilities nc = builder.build();
        if (!nc.equals(mNetworkCapabilities)) {
            mNetworkCapabilities = nc;
            if (mNetworkAgent != null) {
                log("sendNetworkCapabilities: " + mNetworkCapabilities);
                mNetworkAgent.sendNetworkCapabilities(mNetworkCapabilities);
            }
        }
    }

    /**
     * @return The network capabilities of this data network.
     */
    public @NonNull NetworkCapabilities getNetworkCapabilities() {
        return mNetworkCapabilities;
    }

    /**
     * @return The link properties of this data network.
     */
    public @NonNull LinkProperties getLinkProperties() {
        return mLinkProperties;
    }

    /**
     * @return The data profile of this data network.
     */
    public @NonNull DataProfile getDataProfile() {
        return mDataProfile;
    }

    /**
     * Set the validation result. This should be only called by {@link TelephonyNetworkAgent}.
     *
     * @param status one of {@link NetworkAgent#VALIDATION_STATUS_VALID} or
     * {@link NetworkAgent#VALIDATION_STATUS_NOT_VALID}.
     * @param redirectUri If internet connectivity is being redirected (e.g., on a captive portal),
     * this is the destination the probes are being redirected to, otherwise {@code null}.
     */
    public void setValidationResult(@ValidationStatus int status, @Nullable Uri redirectUri) {
        sendMessage(obtainMessage(EVENT_VALIDATION_STATUS_RESULT, status, 0, redirectUri));
    }

    /**
     * Update the preferred transport based on the attached network request.
     */
    private void updatePreferredTransports() {
        if (mAttachedNetworkRequestList.size() == 0) return;
        // Get the highest priority network request.
        TelephonyNetworkRequest networkRequest = mAttachedNetworkRequestList.get(0);

        mPreferredTransport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkRequest.getHighestPriorityNetworkCapability());
        if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_INVALID) {
            mTransport = mPreferredTransport;
        }

        // TODO: Compare preferred transport and current transport. If they are different, initiate
        //  handover.
    }

    /**
     * Update data suspended state.
     */
    private void updateSuspendState() {
        if (isConnecting() || isDisconnected()) {
            // Return if not in the right state.
            return;
        }

        boolean newSuspendedState = false;
        // Get the uncombined service state directly.
        NetworkRegistrationInfo nri = mPhone.getServiceStateTracker().getServiceState()
                .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS, mTransport);
        if (nri == null) {
            loge("Can't get network registration info for "
                    + AccessNetworkConstants.transportTypeToString(mTransport));
            return;
        }

        // Never set suspended for emergency apn. Emergency data connection
        // can work while device is not in service.
        if (mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            newSuspendedState = false;
            // If we are not in service, change to suspended.
        } else if (nri.getRegistrationState()
                != NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                && nri.getRegistrationState()
                != NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING) {
            newSuspendedState = true;
            // Check voice/data concurrency.
        } else if (!mPhone.getServiceStateTracker().isConcurrentVoiceAndDataAllowed()
                && mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
            newSuspendedState = mPhone.getCallTracker().getState() != PhoneConstants.State.IDLE;
        }

        // Only notify when there is a change.
        if (mSuspended != newSuspendedState) {
            mSuspended = newSuspendedState;
            log("Network becomes " + (mSuspended ? "suspended" : "not suspended"));
            // To update NOT_SUSPENDED capability.
            updateNetworkCapabilities();
            notifyPreciseDataConnectionState();
            mDataNetworkCallback.onSuspendedStateChanged(DataNetwork.this, mSuspended);
        }
    }

    /**
     * Allocate PDU session ID from the modem. This is only needed when the data network is
     * initiated on IWLAN.
     */
    private void allocatePduSessionId() {
        mRil.allocatePduSessionId(obtainMessage(EVENT_ALLOCATE_PDU_SESSION_ID_RESPONSE));
    }

    /**
     * Setup a data network.
     */
    private void setupData() {
        int dataNetworkType = getDataNetworkType();

        // We need to use the actual modem roaming state instead of the framework roaming state
        // here. This flag is only passed down to ril_service for picking the correct protocol (for
        // old modem backward compatibility).
        boolean isModemRoaming = mPhone.getServiceState().getDataRoamingFromRegistration();

        // Set this flag to true if the user turns on data roaming. Or if we override the roaming
        // state in framework, we should set this flag to true as well so the modem will not reject
        // the data call setup (because the modem actually thinks the device is roaming).
        boolean allowRoaming = mPhone.getDataRoamingEnabled()
                || (isModemRoaming && (!mPhone.getServiceState().getDataRoaming()
                /*|| isUnmeteredUseOnly()*/));

        TrafficDescriptor trafficDescriptor = mDataProfile.getTrafficDescriptor();
        final boolean matchAllRuleAllowed = trafficDescriptor == null
                || trafficDescriptor.getOsAppId() == null;

        int accessNetwork = DataUtils.networkTypeToAccessNetworkType(dataNetworkType);

        mDataServiceManagers.get(mTransport)
                .setupDataCall(accessNetwork, mDataProfile, isModemRoaming, allowRoaming,
                        DataService.REQUEST_REASON_NORMAL, null, mPduSessionId, null,
                        trafficDescriptor, matchAllRuleAllowed,
                        obtainMessage(EVENT_SETUP_DATA_CALL_RESPONSE));

        logl("setupData: accessNetwork="
                + AccessNetworkType.toString(accessNetwork) + ", " + mDataProfile
                + ", isModemRoaming=" + isModemRoaming + ", allowRoaming=" + allowRoaming
                + ", PDU session id=" + mPduSessionId + ", matchAllRuleAllowed="
                + matchAllRuleAllowed);
        TelephonyMetrics.getInstance().writeSetupDataCall(mPhone.getPhoneId(),
                ServiceState.networkTypeToRilRadioTechnology(dataNetworkType),
                mDataProfile.getProfileId(), mDataProfile.getApn(), mDataProfile.getProtocolType());
    }

    /**
     * Get fail cause from {@link DataCallResponse} and the result code.
     *
     * @param resultCode The result code returned from
     * {@link DataServiceCallback#onSetupDataCallComplete(int, DataCallResponse)}.
     * @param response The data call response returned from
     * {@link DataServiceCallback#onSetupDataCallComplete(int, DataCallResponse)}.
     *
     * @return The fail cause. {@link DataFailCause#NONE} if succeeds.
     */
    private @DataFailureCause int getFailCauseFromDataCallResponse(
            @DataServiceCallback.ResultCode int resultCode, @Nullable DataCallResponse response) {
        int failCause = DataFailCause.NONE;
        switch (resultCode) {
            case DataServiceCallback.RESULT_ERROR_ILLEGAL_STATE:
                failCause = DataFailCause.RADIO_NOT_AVAILABLE;
                break;
            case DataServiceCallback.RESULT_ERROR_BUSY:
            case DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE:
                failCause = DataFailCause.SERVICE_TEMPORARILY_UNAVAILABLE;
                break;
            case DataServiceCallback.RESULT_ERROR_INVALID_ARG:
                failCause = DataFailCause.UNACCEPTABLE_NETWORK_PARAMETER;
                break;
            case DataServiceCallback.RESULT_ERROR_UNSUPPORTED:
                failCause = DataFailCause.REQUEST_NOT_SUPPORTED;
                break;
            case DataServiceCallback.RESULT_SUCCESS:
                if (response != null) {
                    failCause = DataFailCause.getFailCause(response.getCause());
                }
                break;
        }
        return failCause;
    }

    /**
     * Update data network based on the latest {@link DataCallResponse}.
     *
     * @param response The data call response from data service.
     */
    private void updateDataNetwork(@NonNull DataCallResponse response) {
        mCid.put(mTransport, response.getId());
        LinkProperties linkProperties = new LinkProperties();

        // Set interface name
        linkProperties.setInterfaceName(response.getInterfaceName());

        // Set PDU session id
        if (mPduSessionId != response.getPduSessionId()) {
            log("PDU session id updated to " + mPduSessionId);
            mPduSessionId = response.getPduSessionId();
        }

        // Set link addresses
        if (response.getAddresses().size() > 0) {
            for (LinkAddress la : response.getAddresses()) {
                if (!la.getAddress().isAnyLocalAddress()) {
                    logv("addr/pl=" + la.getAddress() + "/" + la.getPrefixLength());
                    linkProperties.addLinkAddress(la);
                }
            }
        } else {
            loge("no address for ifname=" + response.getInterfaceName());
        }

        // Set DNS servers
        if (response.getDnsAddresses().size() > 0) {
            for (InetAddress dns : response.getDnsAddresses()) {
                if (!dns.isAnyLocalAddress()) {
                    linkProperties.addDnsServer(dns);
                }
            }
        } else {
            loge("Empty dns response");
        }

        // Set PCSCF
        if (response.getPcscfAddresses().size() > 0) {
            for (InetAddress pcscf : response.getPcscfAddresses()) {
                linkProperties.addPcscfServer(pcscf);
            }
        }

        // For backwards compatibility, use getMtu() if getMtuV4() is not available.
        int mtuV4 = response.getMtuV4() > 0 ? response.getMtuV4() : response.getMtu();

        if (mtuV4 <= 0) {
            // Use back up value from data profile.
            if (mDataProfile.getApnSetting() != null) {
                mtuV4 = mDataProfile.getApnSetting().getMtuV4();
            }
            if (mtuV4 <= 0) {
                mtuV4 = DEFAULT_MTU_V4;
            }
        }

        // For backwards compatibility, use getMtu() if getMtuV6() is not available.
        int mtuV6 = response.getMtuV6() > 0 ? response.getMtuV6() : response.getMtu();
        if (mtuV6 <= 0) {
            // Use back up value from data profile.
            if (mDataProfile.getApnSetting() != null) {
                mtuV6 = mDataProfile.getApnSetting().getMtuV6();
            }
            if (mtuV6 <= 0) {
                mtuV6 = DEFAULT_MTU_V6;
            }
        }

        // Set MTU for each route.
        for (InetAddress gateway : response.getGatewayAddresses()) {
            int mtu = gateway instanceof java.net.Inet6Address ? mtuV6 : mtuV4;
            linkProperties.addRoute(new RouteInfo(null, gateway, null,
                    RouteInfo.RTN_UNICAST, mtu));
        }

        // LinkProperties.setMtu should be deprecated. The mtu for each route has been already
        // provided in addRoute() above. For backwards compatibility, we still need to provide
        // a value for the legacy MTU. Use the lower value of v4 and v6 value here.
        linkProperties.setMtu(Math.min(mtuV4, mtuV6));

        if (mDataProfile.getApnSetting() != null
                && !TextUtils.isEmpty(mDataProfile.getApnSetting().getProxyAddressAsString())) {
            int port = mDataProfile.getApnSetting().getProxyPort();
            if (port == -1) {
                port = 8080;
            }
            ProxyInfo proxy = ProxyInfo.buildDirectProxy(
                    mDataProfile.getApnSetting().getProxyAddressAsString(), port);
            linkProperties.setHttpProxy(proxy);
        }

        // updateTcpBufferSizes
        linkProperties.setTcpBufferSizes(getTcpConfig());

        if (!linkProperties.equals(mLinkProperties)) {
            mLinkProperties = linkProperties;
            log("sendLinkProperties " + mLinkProperties);
            mNetworkAgent.sendLinkProperties(mLinkProperties);
        }

        // TODO: Support default QOS update
        // TODO: Support QOS bearer sessions
    }

    /**
     * @return {@code true} if the device is connected to NR cell in 5G NSA mode, and the current
     * data network is using the NR cell.
     */
    private boolean isNrConnected() {
        return mPhone.getServiceState().getNrState() == NetworkRegistrationInfo.NR_STATE_CONNECTED
                && mPhone.getServiceStateTracker().getNrContextIds().contains(
                        mCid.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
    }

    /**
     * Get the TCP config string.
     *
     * @return The TCP config string used in {@link LinkProperties#setTcpBufferSizes(String)}.
     */
    private @NonNull String getTcpConfig() {
        ServiceState ss = mPhone.getServiceState();
        NetworkRegistrationInfo nrs = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mTransport);
        int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        if (nrs != null) {
            networkType = nrs.getAccessNetworkTechnology();
            if (networkType == TelephonyManager.NETWORK_TYPE_LTE && isNrConnected()) {
                // Use the 5G SA TCP config for 5G NSA.
                networkType = TelephonyManager.NETWORK_TYPE_NR;
            }

            if (networkType == TelephonyManager.NETWORK_TYPE_LTE
                    && nrs.isUsingCarrierAggregation()) {
                // Although LTE_CA is not a real RAT, but since LTE CA gernally has higher speed,
                // so we use a different network type to get a different TCP config for LTE CA.
                networkType = TelephonyManager.NETWORK_TYPE_LTE_CA;
            }
        }
        return mDataConfigManager.getTcpConfigString(networkType);
    }

    /**
     * Called when receiving setup data network response from the data service.
     *
     * @param resultCode The result code.
     * @param response The response.
     */
    private void onSetupResponse(@DataServiceCallback.ResultCode int resultCode,
            @Nullable DataCallResponse response) {
        log("onSetupResponse: resultCode=" + DataServiceCallback.resultCodeToString(resultCode)
                + ", response=" + response);
        int failCause = getFailCauseFromDataCallResponse(resultCode, response);
        if (failCause == DataFailCause.NONE) {
            updateDataNetwork(response);

            // TODO: Check if the cid already exists. If yes, should notify DNC and let it force
            //  attach the network requests to that existing data network.

            // TODO: Check if there is still network request attached, if not, silently deactivate
            //  the network.

            // TODO: Evaluate all network requests and see if each request still can be satisfied.
            //  For requests that can't be satisfied anymore, we need to put them back to the
            //  unsatisfied pool. If none of network requests can be satisfied, then there is no
            //  need to mark network agent connected. Just silently deactivate the data network.

            if (mAttachedNetworkRequestList.size() != 0) {
                // Setup data succeeded.
                transitionTo(mConnectedState);
            } else {
                log("Tear down the network since there is no live network request.");
                // Directly call onTearDown hear. We should not enter disconnecting state for silent
                // tear down. Once the tear down is complete, the data call list changed event will
                // move the state into disconnected there.
                onTearDown(TEAR_DOWN_REASON_NO_LIVE_REQUEST);
            }
        } else {
            // Setup data failed.
            long retry = response != null ? response.getRetryDurationMillis()
                    : DataCallResponse.RETRY_DURATION_UNDEFINED;
            NetworkRequestList requestList = new NetworkRequestList(mAttachedNetworkRequestList);
            detachAllNetworkRequests();
            mDataNetworkCallback.onSetupDataFailed(DataNetwork.this, requestList,
                    failCause, retry);
            transitionTo(mDisconnectedState);
        }
    }

    /**
     * Check if should perform graceful teardown. Currently we only support IMS/RCS PDN tear down.
     * Frameworks wait for IMS de-registration before tearing down the IMS PDN.
     *
     * @return {@code true} if graceful tear down should be performed.
     */
    private boolean shouldPerformGracefulTearDown() {
        // TODO: The following APIs could be used
        //  ImsMmTelManager.registerImsRegistrationCallback() for IMS registration
        //  ImsRcsManager.registerImsRegistrationCallback() for RCS registration
        //  Then check if the network requests are from those packages.
        //  ImsResolver.getInstance().getConfiguredImsServicePackageName(ImsFeature.FEATURE_MMTEL);
        //  ImsResolver.getInstance().getConfiguredImsServicePackageName(ImsFeature.FEATURE_RCS)
        return false;
    }

    /**
     * Tear down the data network
     *
     * @param reason The reason of tearing down the network.
     */
    public void tearDown(@TearDownReason int reason) {
        if (getCurrentState() == null || isDisconnected()) {
            return;
        }
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_NETWORK, reason));
    }

    private void onTearDown(@TearDownReason int reason) {
        log("onTearDown: reason=" + tearDownReasonToString(reason));
        if (shouldPerformGracefulTearDown()) {
            logd("Performing graceful tear down. Wait for IMS/RCS de-registered.");
            return;
        }
        // TODO: Need to support DataService.REQUEST_REASON_SHUTDOWN
        mDataServiceManagers.get(mTransport).deactivateDataCall(mCid.get(mTransport),
                DataService.REQUEST_REASON_NORMAL, null);
    }

    /**
     * Called when receiving {@link DataServiceCallback#onDataCallListChanged(List)} from the data
     * service.
     *
     * @param transport The transport where this event from.
     * @param responseList The data call response list.
     */
    private void onDataStateChanged(@TransportType int transport,
            @NonNull List<DataCallResponse> responseList) {
        // Ignore the update if it's not from the data service on the right transport.
        // Also if never received data call response from setup call response, which updates the
        // cid, ignore the update here.
        if (transport != mTransport || mCid.get(mTransport) == INVALID_CID) return;

        DataCallResponse response = responseList.stream()
                .filter(r -> mCid.get(mTransport) == r.getId())
                .findFirst()
                .orElse(null);
        if (response != null) {
            if (!response.equals(mDataCallResponse)) {
                log("onDataStateChanged: " + response);
                mDataCallResponse = response;
                if (response.getLinkStatus() != DataCallResponse.LINK_STATUS_INACTIVE) {
                    updateDataNetwork(response);
                } else {
                    log("onDataStateChanged: PDN inactive reported by "
                            + AccessNetworkConstants.transportTypeToString(mTransport)
                            + " data service.");
                    mDataNetworkCallback.onDisconnected(DataNetwork.this, response.getCause());
                    transitionTo(mDisconnectedState);
                }
            }
        } else {
            // The data call response is missing from the list. This means the PDN is gone. This
            // is the PDN lost reported by the modem. We don't send another DEACTIVATE_DATA request
            // for that
            log("onDataStateChanged: PDN disconnected reported by "
                    + AccessNetworkConstants.transportTypeToString(mTransport) + " data service.");
            mDataNetworkCallback.onDisconnected(DataNetwork.this, DataFailCause.LOST_CONNECTION);
            transitionTo(mDisconnectedState);
        }
    }

    /**
     * Called when data config updated.
     */
    private void onDataConfigUpdated() {
        log("onDataConfigUpdated");
    }

    /**
     * @return The unique context id assigned by the data service in
     * {@link DataCallResponse#getId()}.
     */
    public int getId() {
        return mCid.get(mTransport);
    }

    /**
     * @return The current network type reported by the network service.
     */
    private @NetworkType int getDataNetworkType() {
        ServiceState ss = mPhone.getServiceState();
        NetworkRegistrationInfo nrs = ss.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, mTransport);
        if (nrs != null) {
            return nrs.getAccessNetworkTechnology();
        }
        return TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    /**
     * @return The network score. The higher score of the network has higher chance to be
     * selected by the connectivity service as active network.
     */
    private @NonNull NetworkScore getNetworkScore() {
        // If it's serving a network request that asks NET_CAPABILITY_INTERNET and doesn't have
        // specify a sub id, this data network is considered to be default internet data
        // connection. In this case we assign a slightly higher score of 50. The intention is
        // it will not be replaced by other data networks accidentally in DSDS use case.
        int score = OTHER_NETWORK_SCORE;
        for (TelephonyNetworkRequest networkRequest : mAttachedNetworkRequestList) {
            if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && networkRequest.getNetworkSpecifier() == null) {
                score = DEFAULT_INTERNET_NETWORK_SCORE;
            }
        }

        return new NetworkScore.Builder().setLegacyInt(score).build();
    }

    /**
     * @return {@code true} if in connecting state.
     */
    private boolean isConnecting() {
        return getCurrentState() == mConnectingState;
    }

    /**
     * @return {@code true} if in connected state.
     */
    private boolean isConnected() {
        return getCurrentState() == mConnectedState;
    }

    /**
     * @return {@code true} if in disconnecting state.
     */
    private boolean isDisconnecting() {
        return getCurrentState() == mDisconnectingState;
    }

    /**
     * @return {@code true} if in disconnected state.
     */
    private boolean isDisconnected() {
        return getCurrentState() == mDisconnectedState;
    }

    /**
     * @return {@code true} if in handover state.
     */
    private boolean isUnderHandover() {
        return getCurrentState() == mHandoverState;
    }

    /**
     * @return {@code true} if the data network is suspended.
     */
    private boolean isSuspended() {
        return mSuspended;
    }

    private @DataState int getState() {
        IState state = getCurrentState();
        if (isDisconnected() || state == null) {
            return TelephonyManager.DATA_DISCONNECTED;
        } else if (isConnecting()) {
            return TelephonyManager.DATA_CONNECTING;
        } else if (isConnected()) {
            // The data connection can only be suspended when it's in active state.
            if (isSuspended()) {
                return TelephonyManager.DATA_SUSPENDED;
            }
            return TelephonyManager.DATA_CONNECTED;
        } else if (isDisconnecting()) {
            return TelephonyManager.DATA_DISCONNECTING;
        }
        // TODO: Support handover
        /* else if (state == mHandoverState) {
            return TelephonyManager.DATA_UNDER_HANDOVER;
        }*/

        return TelephonyManager.DATA_UNKNOWN;
    }

    /**
     * Get precise data connection state
     *
     * @return The {@link PreciseDataConnectionState}
     */
    private PreciseDataConnectionState getPreciseDataConnectionState() {
        return new PreciseDataConnectionState.Builder()
                .setTransportType(mTransport)
                .setId(mCid.get(mTransport))
                .setState(getState())
                .setApnSetting(mDataProfile.getApnSetting())
                .setLinkProperties(mLinkProperties)
                .setNetworkType(getDataNetworkType())
                .setFailCause(mFailCause)
                .build();
    }

    /**
     * Send the precise data connection state to the listener of
     * {@link android.telephony.TelephonyCallback.PreciseDataConnectionStateListener}.
     */
    private void notifyPreciseDataConnectionState() {
        PreciseDataConnectionState pdcs = getPreciseDataConnectionState();
        logv("notifyPreciseDataConnectionState=" + pdcs);
        mPhone.notifyDataConnection(pdcs);
    }

    /**
     * Convert the data tear down reason to string.
     *
     * @param reason Data deactivation reason.
     * @return The deactivation reason in string format.
     */
    public static @NonNull String tearDownReasonToString(@TearDownReason int reason) {
        switch (reason) {
            case TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED:
                return "CONNECTIVITY_SERVICE_UNWANTED";
            case TEAR_DOWN_REASON_SIM_REMOVAL:
                return "SIM_REMOVAL";
            case TEAR_DOWN_REASON_AIRPLANE_MODE_ON:
                return "AIRPLANE_MODE_ON";
            case TEAR_DOWN_REASON_DATA_DISABLED:
                return "DATA_DISABLED";
            case TEAR_DOWN_REASON_NO_LIVE_REQUEST:
                return "TEAR_DOWN_REASON_NO_LIVE_REQUEST";
            default:
                return "UNKNOWN(" + reason + ")";
        }
    }

    /**
     * Convert event to string
     *
     * @param event The event
     * @return The event in string format.
     */
    private static @NonNull String eventToString(int event) {
        switch (event) {
            case EVENT_DATA_CONFIG_UPDATED:
                return "EVENT_DATA_CONFIG_UPDATED";
            case EVENT_ATTACH_NETWORK_REQUEST:
                return "EVENT_ATTACH_NETWORK_REQUEST";
            case EVENT_DETACH_NETWORK_REQUEST:
                return "EVENT_DETACH_NETWORK_REQUEST";
            case EVENT_VALIDATION_STATUS_RESULT:
                return "EVENT_VALIDATION_STATUS_RESULT";
            case EVENT_ALLOCATE_PDU_SESSION_ID_RESPONSE:
                return "EVENT_ALLOCATE_PDU_SESSION_ID_RESPONSE";
            case EVENT_SETUP_DATA_CALL_RESPONSE:
                return "EVENT_SETUP_DATA_NETWORK_RESPONSE";
            case EVENT_TEAR_DOWN_NETWORK:
                return "EVENT_TEAR_DOWN_NETWORK";
            case EVENT_DATA_STATE_CHANGED:
                return "EVENT_DATA_STATE_CHANGED";
            case EVENT_SERVICE_STATE_CHANGED:
                return "EVENT_DATA_NETWORK_TYPE_REG_STATE_CHANGED";
            default:
                return "Unknown(" + event + ")";
        }
    }

    /**
     * @return The short name of the data network (e.g. DN-C-1)
     */
    public @NonNull String name() {
        return mLogTag;
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    @Override
    protected void log(@NonNull String s) {
        Rlog.d(mLogTag, (getCurrentState() != null
                ? (getCurrentState().getName() + ": ") : "") + s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    @Override
    protected void loge(@NonNull String s) {
        Rlog.e(mLogTag, (getCurrentState() != null
                ? (getCurrentState().getName() + ": ") : "") + s);
    }

    /**
     * Log verbose messages.
     * @param s error messages
     */
    @Override
    protected void logv(@NonNull String s) {
        if (VDBG) {
            Rlog.v(mLogTag, (getCurrentState() != null
                    ? (getCurrentState().getName() + ": ") : "") + s);
        }
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
     * Dump the state of DataNetwork
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataNetwork.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        super.dump(fd, pw, args);

        pw.increaseIndent();
        pw.println("WWAN cid=" + mCid.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        pw.println("WLAN cid=" + mCid.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        mNetworkAgent.dump(fd, pw, args);

        pw.println("Attached network requests:");
        for (TelephonyNetworkRequest request : mAttachedNetworkRequestList) {
            pw.println(request);
        }

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
