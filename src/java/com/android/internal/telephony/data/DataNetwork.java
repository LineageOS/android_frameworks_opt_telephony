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

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Intent;
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
import android.net.vcn.VcnManager;
import android.net.vcn.VcnManager.VcnNetworkPolicyChangeListener;
import android.net.vcn.VcnNetworkPolicyResult;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.DataState;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.DataFailCause;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.LinkCapacityEstimate;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PcoData;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataCallResponse.HandoverFailureMode;
import android.telephony.data.DataCallResponse.LinkStatus;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.QosBearerSession;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.TrafficDescriptor.OsAppId;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.data.DataEvaluation.DataAllowedReason;
import com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;
import com.android.internal.telephony.data.DataRetryManager.DataHandoverRetryEntry;
import com.android.internal.telephony.data.DataRetryManager.DataRetryEntry;
import com.android.internal.telephony.data.TelephonyNetworkAgent.TelephonyNetworkAgentCallback;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.net.module.util.NetworkCapabilitiesUtils;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
 *
 *                                  ┌─────────┐
 *                                  │Handover │
 *                                  └─▲────┬──┘
 *                                    │    │
 *             ┌───────────┐        ┌─┴────▼──┐        ┌───────▼──────┐
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

    /** Event for detaching all network requests. */
    private static final int EVENT_DETACH_ALL_NETWORK_REQUESTS = 10;

    /** Event for bandwidth estimation from the modem changed. */
    private static final int EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED = 11;

    /** Event for bandwidth estimation from the bandwidth estimator changed. */
    private static final int EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED = 12;

    /** Event for display info changed. This is for getting 5G NSA or mmwave information. */
    private static final int EVENT_DISPLAY_INFO_CHANGED = 13;

    /** Event for initiating an handover between cellular and IWLAN. */
    private static final int EVENT_START_HANDOVER = 14;

    /** Event for setup data call (for handover) response from the data service. */
    private static final int EVENT_HANDOVER_RESPONSE = 15;

    /** Event for subscription plan changed or unmetered/congested override set. */
    private static final int EVENT_SUBSCRIPTION_PLAN_OVERRIDE = 16;

    /** Event for PCO data received from network. */
    private static final int EVENT_PCO_DATA_RECEIVED = 17;

    /** The default MTU for IPv4 network. */
    private static final int DEFAULT_MTU_V4 = 1280;

    /** The default MTU for IPv6 network. */
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
                    TEAR_DOWN_REASON_NO_LIVE_REQUEST,
                    TEAR_DOWN_REASON_RAT_NOT_ALLOWED,
                    TEAR_DOWN_REASON_ROAMING_DISABLED,
                    TEAR_DOWN_REASON_CONCURRENT_VOICE_DATA_NOT_ALLOWED,
                    TEAR_DOWN_REASON_DATA_RESTRICTED_BY_NETWORK,
                    TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY,
                    TEAR_DOWN_REASON_POWER_OFF_BY_CARRIER,
                    TEAR_DOWN_REASON_DATA_STALL,
                    TEAR_DOWN_REASON_HANDOVER_FAILED,
                    TEAR_DOWN_REASON_HANDOVER_NOT_ALLOWED,
                    TEAR_DOWN_REASON_VCN_REQUESTED,
                    TEAR_DOWN_REASON_VOPS_NOT_SUPPORTED,
                    TEAR_DOWN_REASON_DEFAULT_DATA_UNSELECTED,
                    TEAR_DOWN_REASON_NOT_IN_SERVICE,
                    TEAR_DOWN_REASON_DATA_CONFIG_NOT_READY,
                    TEAR_DOWN_REASON_PENDING_TEAR_DOWN_ALL,
                    TEAR_DOWN_REASON_NO_SUITABLE_DATA_PROFILE,
                    TEAR_DOWN_REASON_EMERGENCY_CALL,
                    TEAR_DOWN_REASON_RETRY_SCHEDULED,
                    TEAR_DOWN_REASON_DATA_THROTTLED,
                    TEAR_DOWN_REASON_DATA_PROFILE_INVALID,
                    TEAR_DOWN_REASON_DATA_PROFILE_NOT_PREFERRED,
                    TEAR_DOWN_REASON_NOT_ALLOWED_BY_POLICY,
                    TEAR_DOWN_REASON_ILLEGAL_STATE,
                    TEAR_DOWN_ONLY_ALLOWED_SINGLE_NETWORK,
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

    /** Data network tear down due to current RAT is not allowed by the data profile. */
    public static final int TEAR_DOWN_REASON_RAT_NOT_ALLOWED = 6;

    /** Data network tear down due to data roaming not enabled. */
    public static final int TEAR_DOWN_REASON_ROAMING_DISABLED = 7;

    /** Data network tear down due to concurrent voice/data not allowed. */
    public static final int TEAR_DOWN_REASON_CONCURRENT_VOICE_DATA_NOT_ALLOWED = 8;

    /** Data network tear down due to network restricted. */
    public static final int TEAR_DOWN_REASON_DATA_RESTRICTED_BY_NETWORK = 9;

    /** Data network tear down due to data service unbound. */
    public static final int TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY = 10;

    /** Data network tear down due to radio turned off by the carrier. */
    public static final int TEAR_DOWN_REASON_POWER_OFF_BY_CARRIER = 11;

    /** Data network tear down due to data stall. */
    public static final int TEAR_DOWN_REASON_DATA_STALL = 12;

    /** Data network tear down due to handover failed. */
    public static final int TEAR_DOWN_REASON_HANDOVER_FAILED = 13;

    /** Data network tear down due to handover not allowed. */
    public static final int TEAR_DOWN_REASON_HANDOVER_NOT_ALLOWED = 14;

    /** Data network tear down due to VCN service requested. */
    public static final int TEAR_DOWN_REASON_VCN_REQUESTED = 15;

    /** Data network tear down due to VOPS no longer supported. */
    public static final int TEAR_DOWN_REASON_VOPS_NOT_SUPPORTED = 16;

    /** Data network tear down due to default data unselected. */
    public static final int TEAR_DOWN_REASON_DEFAULT_DATA_UNSELECTED = 17;

    /** Data network tear down due to device not in service. */
    public static final int TEAR_DOWN_REASON_NOT_IN_SERVICE = 18;

    /** Data network tear down due to data config not ready. */
    public static final int TEAR_DOWN_REASON_DATA_CONFIG_NOT_READY = 19;

    /** Data network tear down due to tear down all pending. */
    public static final int TEAR_DOWN_REASON_PENDING_TEAR_DOWN_ALL = 20;

    /** Data network tear down due to no suitable data profile. */
    public static final int TEAR_DOWN_REASON_NO_SUITABLE_DATA_PROFILE = 21;

    /** Data network tear down due to emergency call. */
    public static final int TEAR_DOWN_REASON_EMERGENCY_CALL = 22;

    /** Data network tear down due to retry scheduled. */
    public static final int TEAR_DOWN_REASON_RETRY_SCHEDULED = 23;

    /** Data network tear down due to data throttled. */
    public static final int TEAR_DOWN_REASON_DATA_THROTTLED = 24;

    /** Data network tear down due to data profile invalid. */
    public static final int TEAR_DOWN_REASON_DATA_PROFILE_INVALID = 25;

    /** Data network tear down due to data profile not preferred. */
    public static final int TEAR_DOWN_REASON_DATA_PROFILE_NOT_PREFERRED = 26;

    /** Data network tear down due to not allowed by policy. */
    public static final int TEAR_DOWN_REASON_NOT_ALLOWED_BY_POLICY = 27;

    /** Data network tear down due to illegal state. */
    public static final int TEAR_DOWN_REASON_ILLEGAL_STATE = 28;

    /** Data network tear down due to only allowed single network. */
    public static final int TEAR_DOWN_ONLY_ALLOWED_SINGLE_NETWORK = 29;

    @IntDef(prefix = {"BANDWIDTH_SOURCE_"},
            value = {
                    BANDWIDTH_SOURCE_UNKNOWN,
                    BANDWIDTH_SOURCE_MODEM,
                    BANDWIDTH_SOURCE_CARRIER_CONFIG,
                    BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR,
            })
    public @interface BandwidthEstimationSource {}

    /** Indicates the bandwidth estimation source is unknown. This must be a configuration error. */
    public static final int BANDWIDTH_SOURCE_UNKNOWN = 0;

    /** Indicates the bandwidth estimation source is from the modem. */
    public static final int BANDWIDTH_SOURCE_MODEM = 1;

    /** Indicates the bandwidth estimation source is from the static carrier config. */
    public static final int BANDWIDTH_SOURCE_CARRIER_CONFIG = 2;

    /** Indicates the bandwidth estimation source is from {@link LinkBandwidthEstimator}. */
    public static final int BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR = 3;

    /**
     * The capabilities that are allowed to changed dynamically during the life cycle of network.
     * This is copied from {@code NetworkCapabilities#MUTABLE_CAPABILITIES}. There is no plan to
     * make this a connectivity manager API since in the future, immutable network capabilities
     * would be allowed to changed dynamically. (i.e. not immutable anymore.)
     */
    private static final List<Integer> MUTABLE_CAPABILITIES = List.of(
            NetworkCapabilities.NET_CAPABILITY_TRUSTED,
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL,
            NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING,
            NetworkCapabilities.NET_CAPABILITY_FOREGROUND,
            NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED,
            NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED,
            NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY,
            NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED,
            NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED,
            NetworkCapabilities.NET_CAPABILITY_HEAD_UNIT);

    /** The parent state. Any messages not handled by the child state fallback to this. */
    private final DefaultState mDefaultState = new DefaultState();

    /**
     * The connecting state. This is the initial state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final ConnectingState mConnectingState = new ConnectingState();

    /**
     * The connected state. This is the state when data network becomes usable.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final ConnectedState mConnectedState = new ConnectedState();

    /**
     * The handover state. This is the state when data network handover between IWLAN and cellular.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final HandoverState mHandoverState = new HandoverState();

    /**
     * The disconnecting state. This is the state when data network is about to be disconnected.
     * The network is still usable in this state, but the clients should be prepared to lose the
     * network in any moment. This state is particular useful for IMS graceful tear down, where
     * the network enters disconnecting state while waiting for IMS de-registration signal.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final DisconnectingState mDisconnectingState = new DisconnectingState();

    /**
     * The disconnected state. This is the final state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final DisconnectedState mDisconnectedState = new DisconnectedState();

    /** The phone instance. */
    private final @NonNull Phone mPhone;

    /**
     * The subscription id. This is assigned when the network is created, and not supposed to
     * change afterwards.
     */
    private final int mSubId;

    /**
     * Indicates that
     * {@link DataService.DataServiceProvider#deactivateDataCall(int, int, DataServiceCallback)}
     * has been called. This flag can be only changed from {@code false} to {@code true}.
     */
    private boolean mInvokedDataDeactivation = false;

    /**
     * Indicates that if the data network has ever entered {@link ConnectedState}.
     */
    private boolean mEverConnected = false;

    /** RIL interface. */
    private final @NonNull CommandsInterface mRil;

    /** Local log. */
    private final LocalLog mLocalLog = new LocalLog(128);

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
    private final SparseIntArray mCid = new SparseIntArray(2);

    /**
     * The initial network agent id. The network agent can be re-created due to immutable capability
     * changed. This is to preserve the initial network agent id so the id in the logging tag won't
     * change for the entire life cycle of data network.
     */
    private int mInitialNetworkAgentId;

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

    /** VCN manager. */
    private final @Nullable VcnManager mVcnManager;

    /** VCN policy changed listener. */
    private @Nullable VcnNetworkPolicyChangeListener mVcnPolicyChangeListener;

    /** The network agent associated with this data network. */
    private @NonNull TelephonyNetworkAgent mNetworkAgent;

    /** QOS callback tracker. This is only created after network connected on WWAN. */
    private @Nullable QosCallbackTracker mQosCallbackTracker;

    /** NAT keepalive tracker. */
    private @Nullable KeepaliveTracker mKeepaliveTracker;

    /** The data profile used to establish this data network. */
    private final @NonNull DataProfile mDataProfile;

    /** The network capabilities of this data network. */
    private @NonNull NetworkCapabilities mNetworkCapabilities;

    /** The matched traffic descriptor returned from setup data call request. */
    private final @NonNull List<TrafficDescriptor> mTrafficDescriptors = new ArrayList<>();

    /** The link properties of this data network. */
    private @NonNull LinkProperties mLinkProperties;

    /** The network slice info. */
    private @Nullable NetworkSliceInfo mNetworkSliceInfo;

    /** The link status (i.e. RRC state). */
    private @LinkStatus int mLinkStatus = DataCallResponse.LINK_STATUS_UNKNOWN;

    /** The network bandwidth. */
    private @NonNull NetworkBandwidth mNetworkBandwidth = new NetworkBandwidth(14, 14);

    /** Whether {@link NetworkCapabilities#NET_CAPABILITY_TEMPORARILY_NOT_METERED} is supported. */
    private boolean mTempNotMeteredSupported = false;

    /** Whether the current data network is temporarily not metered. */
    private boolean mTempNotMetered = false;

    /** Whether the current data network is congested. */
    private boolean mCongested = false;

    /** The network requests associated with this data network */
    private final @NonNull NetworkRequestList mAttachedNetworkRequestList =
            new NetworkRequestList();

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

    /**
     * Indicates if data network is suspended. Note this is slightly different from the
     * {@link TelephonyManager#DATA_SUSPENDED}, which is only possible when data network is in
     * connected state. This flag reflects to the
     * {@link NetworkCapabilities#NET_CAPABILITY_NOT_SUSPENDED} which can happen when data network
     * is in connected or disconnecting state.
     */
    private boolean mSuspended = false;

    /**
     * The current transport of the data network. For handover, the current transport will be set
     * after handover completes.
     */
    private @TransportType int mTransport;

    /** The reason that why setting up this data network is allowed. */
    private @NonNull DataAllowedReason mDataAllowedReason;

    /**
     * PCO (Protocol Configuration Options) data received from the network. Key is the PCO id, value
     * is the PCO content.
     */
    private final @NonNull Map<Integer, PcoData> mPcoData = new ArrayMap<>();

    /** The QOS bearer sessions. */
    private final @NonNull List<QosBearerSession> mQosBearerSessions = new ArrayList<>();

    /**
     * The network bandwidth.
     */
    public static class NetworkBandwidth {
        /** The downlink bandwidth in Kbps. */
        public final int downlinkBandwidthKbps;

        /** The uplink Bandwidth in Kbps. */
        public final int uplinkBandwidthKbps;

        /**
         * Constructor.
         *
         * @param downlinkBandwidthKbps The downlink bandwidth in Kbps.
         * @param uplinkBandwidthKbps The uplink Bandwidth in Kbps.
         */
        public NetworkBandwidth(int downlinkBandwidthKbps, int uplinkBandwidthKbps) {
            this.downlinkBandwidthKbps = downlinkBandwidthKbps;
            this.uplinkBandwidthKbps = uplinkBandwidthKbps;
        }

        @Override
        public String toString() {
            return String.format("NetworkBandwidth=[downlink=%d, uplink=%d]",
                    downlinkBandwidthKbps, uplinkBandwidthKbps);
        }
    }

    /**
     * Data network callback. Should only be used by {@link DataNetworkController}.
     */
    public abstract static class DataNetworkCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataNetworkCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

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
        public abstract void onSetupDataFailed(@NonNull DataNetwork dataNetwork,
                @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                long retryDurationMillis);

        /**
         * Called when data network enters {@link ConnectedState}.
         *
         * @param dataNetwork The data network.
         */
        public abstract void onConnected(@NonNull DataNetwork dataNetwork);

        /**
         * Called when data network validation status changed.
         *
         * @param dataNetwork The data network.
         * @param status one of {@link NetworkAgent#VALIDATION_STATUS_VALID} or
         * {@link NetworkAgent#VALIDATION_STATUS_NOT_VALID}.
         * @param redirectUri If internet connectivity is being redirected (e.g., on a captive
         * portal), this is the destination the probes are being redirected to, otherwise
         * {@code null}.
         */
        public abstract void onValidationStatusChanged(@NonNull DataNetwork dataNetwork,
                @ValidationStatus int status, @Nullable Uri redirectUri);

        /**
         * Called when data network suspended state changed.
         *
         * @param dataNetwork The data network.
         * @param suspended {@code true} if data is suspended.
         */
        public abstract void onSuspendedStateChanged(@NonNull DataNetwork dataNetwork,
                boolean suspended);

        /**
         * Called when network requests were failed to attach to the data network.
         *
         * @param dataNetwork The data network.
         * @param requestList The requests failed to attach.
         */
        public abstract void onAttachFailed(@NonNull DataNetwork dataNetwork,
                @NonNull NetworkRequestList requestList);

        /**
         * Called when data network enters {@link DisconnectedState}. Note this is only called
         * when the data network was previously connected. For setup data network failed,
         * {@link #onSetupDataFailed(DataNetwork, NetworkRequestList, int, long)} is called.
         *
         * @param dataNetwork The data network.
         * @param cause The disconnect cause.
         */
        public abstract void onDisconnected(@NonNull DataNetwork dataNetwork,
                @DataFailureCause int cause);

        /**
         * Called when handover between IWLAN and cellular network succeeded.
         *
         * @param dataNetwork The data network.
         */
        public abstract void onHandoverSucceeded(@NonNull DataNetwork dataNetwork);

        /**
         * Called when data network handover between IWLAN and cellular network failed.
         *
         * @param dataNetwork The data network.
         * @param cause The fail cause.
         * @param retryDurationMillis Network suggested retry time in milliseconds.
         * {@link Long#MAX_VALUE} indicates data retry should not occur.
         * {@link DataCallResponse#RETRY_DURATION_UNDEFINED} indicates network did not suggest any
         * retry duration.
         * @param handoverFailureMode The handover failure mode that determine the behavior of
         * how frameworks should handle the handover failure.
         */
        public abstract void onHandoverFailed(@NonNull DataNetwork dataNetwork,
                @DataFailureCause int cause, long retryDurationMillis,
                @HandoverFailureMode int handoverFailureMode);

        /**
         * Called when data network link status (i.e. RRC state) changed.
         *
         * @param dataNetwork The data network.
         * @param linkStatus The link status (i.e. RRC state).
         */
        public abstract void onLinkStatusChanged(@NonNull DataNetwork dataNetwork,
                @LinkStatus int linkStatus);

        /**
         * Called when PCO data changed.
         *
         * @param dataNetwork The data network.
         */
        public abstract void onPcoDataChanged(@NonNull DataNetwork dataNetwork);

        /**
         * Called when network capabilities changed.
         *
         * @param dataNetwork The data network.
         */
        public abstract void onNetworkCapabilitiesChanged(@NonNull DataNetwork dataNetwork);
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
     * @param transport The initial transport of the data network.
     * @param dataAllowedReason The reason that why setting up this data network is allowed.
     * @param callback The callback to receives data network state update.
     */
    public DataNetwork(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull SparseArray<DataServiceManager> dataServiceManagers,
            @NonNull DataProfile dataProfile,
            @NonNull NetworkRequestList networkRequestList,
            @TransportType int transport,
            @NonNull DataAllowedReason dataAllowedReason,
            @NonNull DataNetworkCallback callback) {
        super("DataNetwork", looper);
        mPhone = phone;
        mSubId = phone.getSubId();
        mRil = mPhone.mCi;
        mLinkProperties = new LinkProperties();
        mDataServiceManagers = dataServiceManagers;
        mAccessNetworksManager = phone.getAccessNetworksManager();
        mVcnManager = mPhone.getContext().getSystemService(VcnManager.class);
        mDataNetworkController = phone.getDataNetworkController();
        mDataNetworkController.registerDataNetworkControllerCallback(
                new DataNetworkController.DataNetworkControllerCallback(getHandler()::post) {
                    @Override
                    public void onSubscriptionPlanOverride() {
                        sendMessage(EVENT_SUBSCRIPTION_PLAN_OVERRIDE);
                    }});
        mDataConfigManager = mDataNetworkController.getDataConfigManager();
        mDataNetworkCallback = callback;
        mDataProfile = dataProfile;
        mTransport = transport;
        mDataAllowedReason = dataAllowedReason;
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

        /**
         * This will trigger {@link DefaultState#enter()}, and then {@link ConnectingState#enter()}.
         * Check {@link StateMachine} class to see how Android state machine works.
         */
        start();
        // Do not add more stuffs here.
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
                getNetworkScore(), configBuilder.build(), provider,
                new TelephonyNetworkAgentCallback(getHandler()::post) {
                    @Override
                    public void onValidationStatus(@ValidationStatus int status,
                            @Nullable Uri redirectUri) {
                        mDataNetworkCallback.invokeFromExecutor(
                                () -> mDataNetworkCallback.onValidationStatusChanged(
                                        DataNetwork.this, status, redirectUri));
                    }
                });
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
            logv("Registering all events.");
            mDataConfigManager.registerForConfigUpdate(getHandler(), EVENT_DATA_CONFIG_UPDATED);
            mPhone.getDisplayInfoController().registerForTelephonyDisplayInfoChanged(
                    getHandler(), EVENT_DISPLAY_INFO_CHANGED, null);
            for (int transport : mAccessNetworksManager.getAvailableTransports()) {
                mDataServiceManagers.get(transport)
                        .registerForDataCallListChanged(getHandler(), EVENT_DATA_STATE_CHANGED);
                mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                        transport, getHandler(), EVENT_SERVICE_STATE_CHANGED, transport);
            }

            // Only add symmetric code here, for example, registering and unregistering.
            // DefaultState.enter() is the starting point in the life cycle of the DataNetwork,
            // and DefaultState.exit() is the end. For non-symmetric initializing works, put them
            // in ConnectingState.enter().
        }

        @Override
        public void exit() {
            logv("Unregistering all events.");
            for (int transport : mAccessNetworksManager.getAvailableTransports()) {
                mDataServiceManagers.get(transport)
                        .unregisterForDataCallListChanged(getHandler());
                mPhone.getServiceStateTracker().unregisterForDataRegStateOrRatChanged(
                        transport, getHandler());
            }
            mPhone.getDisplayInfoController().unregisterForTelephonyDisplayInfoChanged(
                    getHandler());
            mDataConfigManager.unregisterForConfigUpdate(getHandler());
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DATA_CONFIG_UPDATED:
                    onDataConfigUpdated();
                    break;
                case EVENT_SERVICE_STATE_CHANGED: {
                    // TODO: Should update suspend state when CSS indicator changes.
                    // TODO: Should update suspend state when call started/ended.
                    updateSuspendState();
                    updateBandwidthFromDataConfig();
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
                            mDataNetworkCallback.invokeFromExecutor(() -> mDataNetworkCallback
                                    .onAttachFailed(DataNetwork.this, failedList));
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
                case EVENT_DETACH_ALL_NETWORK_REQUESTS: {
                    for (TelephonyNetworkRequest networkRequest : mAttachedNetworkRequestList) {
                        networkRequest.setState(TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED);
                        networkRequest.setAttachedNetwork(null);
                    }
                    mAttachedNetworkRequestList.clear();
                    break;
                }
                case EVENT_DATA_STATE_CHANGED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int transport = (int) ar.userObj;
                    onDataStateChanged(transport, (List<DataCallResponse>) ar.result);
                    break;
                }
                case EVENT_START_HANDOVER:
                case EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED:
                case EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED:
                case EVENT_TEAR_DOWN_NETWORK:
                case EVENT_PCO_DATA_RECEIVED:
                    // Ignore the events when not in the correct state.
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
            // Need to calculate the initial capabilities before creating the network agent.
            updateNetworkCapabilities();
            mNetworkAgent = createNetworkAgent();
            mInitialNetworkAgentId = mNetworkAgent.getId();
            mLogTag = "DN-" + mInitialNetworkAgentId + "-"
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
                case EVENT_START_HANDOVER:
                case EVENT_TEAR_DOWN_NETWORK:
                case EVENT_PCO_DATA_RECEIVED:
                    // Defer the request until connected or disconnected.
                    deferMessage(msg);
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
            // Note that reaching here could mean from connecting -> connected, or from
            // handover -> connected.
            if (!mEverConnected) {
                // Transited from ConnectingState
                log("network connected.");
                mEverConnected = true;
                mNetworkAgent.markConnected();
                mDataNetworkCallback.invokeFromExecutor(
                        () -> mDataNetworkCallback.onConnected(DataNetwork.this));

                mQosCallbackTracker = new QosCallbackTracker(mNetworkAgent, mPhone);
                mQosCallbackTracker.updateSessions(mQosBearerSessions);
                mKeepaliveTracker = new KeepaliveTracker(mPhone,
                        getHandler().getLooper(), DataNetwork.this, mNetworkAgent);
                if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                    registerForWwanEvents();
                }

                // Create the VCN policy changed listener. When the policy changed, we might need
                // to tear down the VCN-managed network.
                if (mVcnManager != null) {
                    mVcnPolicyChangeListener = () -> {
                        if (mVcnManager.applyVcnNetworkPolicy(mNetworkCapabilities, mLinkProperties)
                                .isTeardownRequested()) {
                            tearDown(TEAR_DOWN_REASON_VCN_REQUESTED);
                        }
                    };
                    mVcnManager.addVcnNetworkPolicyChangeListener(
                            getHandler()::post, mVcnPolicyChangeListener);
                }
            }

            notifyPreciseDataConnectionState();
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
                    if (mInvokedDataDeactivation) {
                        log("Ignore tear down request because network is being torn down.");
                        break;
                    }
                    removeMessages(EVENT_TEAR_DOWN_NETWORK);
                    removeDeferredMessages(EVENT_TEAR_DOWN_NETWORK);
                    transitionTo(mDisconnectingState);
                    onTearDown(msg.arg1);
                    break;
                case EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        log("EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED: error ignoring, e="
                                + ar.exception);
                        break;
                    }
                    onBandwidthUpdatedFromModem((List<LinkCapacityEstimate>) ar.result);
                    break;
                case EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED:
                    ar = (AsyncResult) msg.obj;
                    Pair<Integer, Integer> pair = (Pair<Integer, Integer>) ar.result;
                    onBandwidthUpdated(pair.first, pair.second);
                    break;
                case EVENT_DISPLAY_INFO_CHANGED:
                    onDisplayInfoChanged();
                    break;
                case EVENT_START_HANDOVER:
                    onStartHandover(msg.arg1, (DataHandoverRetryEntry) msg.obj);
                    break;
                case EVENT_SUBSCRIPTION_PLAN_OVERRIDE:
                    updateMeteredAndCongested();
                    break;
                case EVENT_PCO_DATA_RECEIVED:
                    ar = (AsyncResult) msg.obj;
                    onPcoDataReceived((PcoData) ar.result);
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
                case EVENT_DATA_STATE_CHANGED:
                    // The data call list changed event should be conditionally deferred.
                    // Otherwise the deferred message might be incorrectly treated as "disconnected"
                    // signal. So we only defer the related data call list changed event, and drop
                    // the unrelated.
                    if (shouldDeferDataStateChangedEvent(msg)) {
                        deferMessage(msg);
                    }
                    break;
                case EVENT_START_HANDOVER:
                case EVENT_TEAR_DOWN_NETWORK:
                    // Defer the request until handover succeeds or fails.
                    deferMessage(msg);
                    break;
                case EVENT_HANDOVER_RESPONSE:
                    int resultCode = msg.arg1;
                    DataCallResponse dataCallResponse =
                            msg.getData().getParcelable(DataServiceManager.DATA_CALL_RESPONSE);
                    onHandoverResponse(resultCode, dataCallResponse,
                            (DataHandoverRetryEntry) msg.obj);
                    break;
                case EVENT_PCO_DATA_RECEIVED:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    onPcoDataReceived((PcoData) ar.result);
                    break;
                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        /**
         * Check if the data call list changed event should be deferred or dropped when handover
         * is in progress.
         *
         * @param msg The data call list changed message.
         *
         * @return {@code true} if the message should be deferred.
         */
        private boolean shouldDeferDataStateChangedEvent(@NonNull Message msg) {
            // The data call list changed event should be conditionally deferred.
            // Otherwise the deferred message might be incorrectly treated as "disconnected"
            // signal. So we only defer the related data call list changed event, and drop
            // the unrelated.
            AsyncResult ar = (AsyncResult) msg.obj;
            int transport = (int) ar.userObj;
            List<DataCallResponse> responseList = (List<DataCallResponse>) ar.result;
            if (transport != mTransport) {
                log("Dropped unrelated " + AccessNetworkConstants.transportTypeToString(transport)
                        + " data call list changed event. " + responseList);
                return false;
            }

            // Check if the data call list changed event are related to the current data network.
            boolean related = responseList.stream().anyMatch(
                    r -> mCid.get(mTransport) == r.getId());
            if (related) {
                log("Deferred the related data call list changed event." + responseList);
            } else {
                log("Dropped unrelated data call list changed event. " + responseList);
            }
            return related;
        }
    }

    /**
     * The disconnecting state. This is the state when data network is about to be disconnected.
     * The network is still usable in this state, but the clients should be prepared to lose the
     * network in any moment. This state is particular useful for IMS graceful tear down, where
     * the network enters disconnecting state while waiting for IMS de-registration signal.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DisconnectingState extends State {
        @Override
        public void enter() {
            notifyPreciseDataConnectionState();
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
            logl("Data network disconnected.");
            // The detach all network requests must be tge last message to handle.
            sendMessage(EVENT_DETACH_ALL_NETWORK_REQUESTS);
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

            if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN && mEverConnected) {
                unregisterForWwanEvents();
            }

            if (mVcnManager != null && mVcnPolicyChangeListener != null) {
                mVcnManager.removeVcnNetworkPolicyChangeListener(mVcnPolicyChangeListener);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            logv("event=" + eventToString(msg.what));
            return NOT_HANDLED;
        }
    }

    /**
     * Register for events that can only happen on cellular networks.
     */
    private void registerForWwanEvents() {
        registerForBandwidthUpdate();
        mKeepaliveTracker.registerForKeepaliveStatus();
        mRil.registerForPcoData(this.getHandler(), EVENT_PCO_DATA_RECEIVED, null);
    }

    /**
     * Unregister for events that can only happen on cellular networks.
     */
    private void unregisterForWwanEvents() {
        unregisterForBandwidthUpdate();
        mKeepaliveTracker.unregisterForKeepaliveStatus();
        mRil.unregisterForPcoData(this.getHandler());
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
     * Register for bandwidth update.
     */
    private void registerForBandwidthUpdate() {
        int bandwidthEstimateSource = mDataConfigManager.getBandwidthEstimateSource();
        if (bandwidthEstimateSource == BANDWIDTH_SOURCE_MODEM) {
            mPhone.mCi.registerForLceInfo(
                    getHandler(), EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED, null);
        } else if (bandwidthEstimateSource == BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR) {
            mPhone.getLinkBandwidthEstimator().registerForBandwidthChanged(
                    getHandler(), EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED, null);
        } else {
            loge("Invalid bandwidth source configuration: " + bandwidthEstimateSource);
        }
    }

    /**
     * Unregister bandwidth update.
     */
    private void unregisterForBandwidthUpdate() {
        int bandwidthEstimateSource = mDataConfigManager.getBandwidthEstimateSource();
        if (bandwidthEstimateSource == BANDWIDTH_SOURCE_MODEM) {
            mPhone.mCi.unregisterForLceInfo(getHandler());
        } else if (bandwidthEstimateSource == BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR) {
            mPhone.getLinkBandwidthEstimator().unregisterForBandwidthChanged(getHandler());
        } else {
            loge("Invalid bandwidth source configuration: " + bandwidthEstimateSource);
        }
    }

    /**
     * Remove network requests that can't be satisfied anymore.
     */
    private void removeUnsatisfiedNetworkRequests() {
        for (TelephonyNetworkRequest networkRequest : mAttachedNetworkRequestList) {
            if (!networkRequest.canBeSatisfiedBy(mNetworkCapabilities)) {
                log("removeUnsatisfiedNetworkRequests: " + networkRequest
                        + " can't be satisfied anymore. Will be detached.");
                detachNetworkRequest(networkRequest);
            }
        }
    }

    /**
     * Check if there are immutable capabilities changed. The connectivity service is not able
     * to handle immutable capabilities changed, but in very rare scenarios, immutable capabilities
     * need to be changed dynamically, such as in setup data call response, modem responded with the
     * same cid. In that case, we need to merge the new capabilities into the existing data network.
     *
     * @param oldCapabilities The old network capabilities.
     * @param newCapabilities The new network capabilities.
     * @return {@code true} if there are immutable network capabilities changed.
     */
    private static boolean areImmutableCapabilitiesChanged(
            @NonNull NetworkCapabilities oldCapabilities,
            @NonNull NetworkCapabilities newCapabilities) {
        if (oldCapabilities == null
                || ArrayUtils.isEmpty(oldCapabilities.getCapabilities())) return false;

        // Remove mutable capabilities from both old and new capabilities, the remaining
        // capabilities would be immutable capabilities.
        List<Integer> oldImmutableCapabilities = Arrays.stream(oldCapabilities.getCapabilities())
                .boxed().collect(Collectors.toList());
        oldImmutableCapabilities.removeAll(MUTABLE_CAPABILITIES);
        List<Integer> newImmutableCapabilities = Arrays.stream(newCapabilities.getCapabilities())
                .boxed().collect(Collectors.toList());
        newImmutableCapabilities.removeAll(MUTABLE_CAPABILITIES);
        return oldImmutableCapabilities.size() != newImmutableCapabilities.size()
                || !oldImmutableCapabilities.containsAll(newImmutableCapabilities);
    }

    /**
     * Update the network capabilities.
     */
    private void updateNetworkCapabilities() {
        final NetworkCapabilities.Builder builder = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        boolean roaming = mPhone.getServiceState().getDataRoaming();

        builder.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                .setSubscriptionId(mSubId).build());
        builder.setSubscriptionIds(Collections.singleton(mSubId));

        ApnSetting apnSetting = mDataProfile.getApnSetting();

        if (apnSetting != null) {
            apnSetting.getApnTypes().stream()
                    .map(DataUtils::apnTypeToNetworkCapability)
                    .filter(cap -> cap >= 0)
                    .forEach(builder::addCapability);
        }

        // If voice call is on-going, do not change MMTEL capability, which is a immutable
        // capability. Changing it will result in re-recreating network agent below, and the voice
        // call will drop. Whether tearing down an IMS network or not when VoPS is lost
        if (mPhone.getImsPhone() != null && mPhone.getImsPhone().getCallTracker().getState()
                != PhoneConstants.State.IDLE && mNetworkCapabilities != null
                && mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL)) {
            // Previous capability has MMTEL, so add it again.
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL);
        } else {
            // Always add MMTEL capability on IMS network unless network explicitly indicates VoPS
            // not supported.
            if (mDataProfile.canSatisfy(NetworkCapabilities.NET_CAPABILITY_IMS)) {
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL);
                if (mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                    NetworkRegistrationInfo nri = mPhone.getServiceStateTracker().getServiceState()
                            .getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
                    if (nri != null) {
                        DataSpecificRegistrationInfo dsri = nri.getDataSpecificInfo();
                        // Check if VoPS is supported by the network.
                        if (dsri != null && dsri.getVopsSupportInfo() != null
                                && !dsri.getVopsSupportInfo().isVopsSupported()) {
                            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL);
                        }
                    }
                }
            }
        }

        // Extract network capabilities from the traffic descriptor.
        for (TrafficDescriptor trafficDescriptor : mTrafficDescriptors) {
            try {
                OsAppId osAppId = new OsAppId(trafficDescriptor.getOsAppId());
                if (!osAppId.getOsId().equals(OsAppId.ANDROID_OS_ID)) {
                    loge("Received non-Android OS id " + osAppId.getOsId());
                    continue;
                }
                int networkCapability = DataUtils.getNetworkCapabilityFromString(
                        osAppId.getAppId());
                switch (networkCapability) {
                    case NetworkCapabilities.NET_CAPABILITY_ENTERPRISE:
                        builder.addCapability(networkCapability);
                        // Enterprise is the only capability supporting differentiator.
                        if (networkCapability == NetworkCapabilities.NET_CAPABILITY_ENTERPRISE) {
                            builder.addEnterpriseId(osAppId.getDifferentiator());
                        }
                        break;
                    case NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY:
                    case NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH:
                    case NetworkCapabilities.NET_CAPABILITY_CBS:
                        builder.addCapability(networkCapability);
                        break;
                    default:
                        loge("Invalid app id " + osAppId.getAppId());
                }
            } catch (Exception e) {
                loge("Exception: " + e + ". Failed to create osAppId from "
                        + new BigInteger(1, trafficDescriptor.getOsAppId()).toString(16));
            }
        }

        if (!mCongested) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        }

        if (mTempNotMeteredSupported && mTempNotMetered) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED);
        }

        // Always start with NOT_VCN_MANAGED, then remove if VcnManager indicates this is part of a
        // VCN.
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
        if (isVcnManaged(builder.build())) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
        }

        if (!roaming) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        }

        if (!mSuspended) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
        }

        Set<Integer> meteredCapabilities = mDataConfigManager
                .getMeteredNetworkCapabilities(roaming).stream()
                .filter(cap -> mAccessNetworksManager.getPreferredTransportByNetworkCapability(cap)
                        == AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .collect(Collectors.toSet());
        boolean unmeteredNetwork = meteredCapabilities.stream().noneMatch(
                Arrays.stream(builder.build().getCapabilities()).boxed()
                        .collect(Collectors.toSet())::contains);

        if (unmeteredNetwork) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }

        // Always start with not-restricted, and then remove if needed.
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        // When data is disabled, or data roaming is disabled and the device is roaming, we need
        // to remove certain capabilities depending on scenarios.
        if (!mDataNetworkController.getDataSettingsManager().isDataEnabled()
                || (mPhone.getServiceState().getDataRoaming()
                && !mDataNetworkController.getDataSettingsManager().isDataRoamingEnabled())) {
            // If data is allowed because the request is a restricted network request, we need
            // to mark the network as restricted when data is disabled or data roaming is disabled
            // and the device is roaming. If we don't do that, non-privileged apps will be able
            // to use this network when data is disabled.
            if (mDataAllowedReason == DataAllowedReason.RESTRICTED_REQUEST) {
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            } else if (mDataAllowedReason == DataAllowedReason.UNMETERED_USAGE
                    || mDataAllowedReason == DataAllowedReason.MMS_REQUEST) {
                // If data is allowed due to unmetered usage, or MMS always-allowed, we need to
                // remove unrelated-but-metered capabilities.
                for (int capability : meteredCapabilities) {
                    // 1. If it's unmetered usage, remove all metered capabilities.
                    // 2. if it's MMS always-allowed, then remove all metered capabilities but MMS.
                    if (capability != NetworkCapabilities.NET_CAPABILITY_MMS
                            || mDataAllowedReason != DataAllowedReason.MMS_REQUEST) {
                        builder.removeCapability(capability);
                    }
                }
            }
        }

        // If one of the capabilities are for special use, for example, IMS, CBS, then this
        // network should be restricted, regardless data is enabled or not.
        if (NetworkCapabilitiesUtils.inferRestrictedCapability(builder.build())) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }

        // Set the bandwidth information.
        builder.setLinkDownstreamBandwidthKbps(mNetworkBandwidth.downlinkBandwidthKbps);
        builder.setLinkUpstreamBandwidthKbps(mNetworkBandwidth.uplinkBandwidthKbps);

        NetworkCapabilities nc = builder.build();
        if (mNetworkCapabilities == null || mNetworkAgent == null) {
            // This is the first time when network capabilities is created. The agent is not created
            // at this time. Just return here. The network capabilities will be used when network
            // agent is created.
            mNetworkCapabilities = nc;
            return;
        }

        if (!nc.equals(mNetworkCapabilities)) {
            // Check if we are changing the immutable capabilities. Note that we should be very
            // careful and limit the use cases of changing immutable capabilities. Connectivity
            // service would not close sockets for clients if a network request becomes
            // unsatisfiable.
            if (mEverConnected && areImmutableCapabilitiesChanged(mNetworkCapabilities, nc)
                    && (isConnected() || isHandoverInProgress())) {
                // Before connectivity service supports making all capabilities mutable, it is
                // suggested to de-register and re-register the network agent if it is needed to
                // add/remove immutable capabilities.
                logl("updateNetworkCapabilities: Immutable capabilities changed. Re-create the "
                        + "network agent. Attempted to change from " + mNetworkCapabilities + " to "
                        + nc);
                // Abandon the network agent because we are going to create a new one.
                mNetworkAgent.abandon();
                // Update the capabilities first so the new network agent would be created with the
                // new capabilities.
                mNetworkCapabilities = nc;
                mNetworkAgent = createNetworkAgent();
                mNetworkAgent.markConnected();
            } else {
                // Now we need to inform connectivity service and data network controller
                // about the capabilities changed.
                mNetworkCapabilities = nc;
                mNetworkAgent.sendNetworkCapabilities(mNetworkCapabilities);
            }

            removeUnsatisfiedNetworkRequests();
            mDataNetworkCallback.invokeFromExecutor(() -> mDataNetworkCallback
                    .onNetworkCapabilitiesChanged(DataNetwork.this));
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
            logl("Network becomes " + (mSuspended ? "suspended" : "unsuspended"));
            // To update NOT_SUSPENDED capability.
            updateNetworkCapabilities();
            notifyPreciseDataConnectionState();
            mDataNetworkCallback.invokeFromExecutor(() ->
                    mDataNetworkCallback.onSuspendedStateChanged(DataNetwork.this, mSuspended));
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
            mPduSessionId = response.getPduSessionId();
            log("PDU session id updated to " + mPduSessionId);
        }

        // Set the link status
        if (mLinkStatus != response.getLinkStatus()) {
            mLinkStatus = response.getLinkStatus();
            log("Link status updated to " + DataUtils.linkStatusToString(mLinkStatus));
            mDataNetworkCallback.invokeFromExecutor(
                    () -> mDataNetworkCallback.onLinkStatusChanged(DataNetwork.this, mLinkStatus));
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

        linkProperties.setTcpBufferSizes(mDataConfigManager.getTcpConfigString());

        mNetworkSliceInfo = response.getSliceInfo();

        mTrafficDescriptors.clear();
        mTrafficDescriptors.addAll(response.getTrafficDescriptors());

        mQosBearerSessions.clear();
        mQosBearerSessions.addAll(response.getQosBearerSessions());
        if (mQosCallbackTracker != null) {
            mQosCallbackTracker.updateSessions(mQosBearerSessions);
        }

        if (!linkProperties.equals(mLinkProperties)) {
            mLinkProperties = linkProperties;
            log("sendLinkProperties " + mLinkProperties);
            mNetworkAgent.sendLinkProperties(mLinkProperties);
        }

        updateNetworkCapabilities();
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
     * Called when receiving setup data network response from the data service.
     *
     * @param resultCode The result code.
     * @param response The response.
     */
    private void onSetupResponse(@DataServiceCallback.ResultCode int resultCode,
            @Nullable DataCallResponse response) {
        logl("onSetupResponse: resultCode=" + DataServiceCallback.resultCodeToString(resultCode)
                + ", response=" + response);
        mFailCause = getFailCauseFromDataCallResponse(resultCode, response);
        if (mFailCause == DataFailCause.NONE) {
            updateDataNetwork(response);

            // TODO: Check if the cid already exists. If yes, should notify DNC and let it force
            //  attach the network requests to that existing data network.

            // TODO: Evaluate all network requests and see if each request still can be satisfied.
            //  For requests that can't be satisfied anymore, we need to put them back to the
            //  unsatisfied pool. If none of network requests can be satisfied, then there is no
            //  need to mark network agent connected. Just silently deactivate the data network.
            if (mAttachedNetworkRequestList.size() == 0) {
                log("Tear down the network since there is no live network request.");
                // Directly call onTearDown here. Calling tearDown will cause deadlock because
                // EVENT_TEAR_DOWN_NETWORK is deferred until state machine enters connected state,
                // which will never happen in this case.
                onTearDown(TEAR_DOWN_REASON_NO_LIVE_REQUEST);
                return;
            }

            if (mVcnManager != null && mVcnManager.applyVcnNetworkPolicy(mNetworkCapabilities,
                    mLinkProperties).isTeardownRequested()) {
                log("VCN service requested to tear down the network.");
                // Directly call onTearDown here. Calling tearDown will cause deadlock because
                // EVENT_TEAR_DOWN_NETWORK is deferred until state machine enters connected state,
                // which will never happen in this case.
                onTearDown(TEAR_DOWN_REASON_VCN_REQUESTED);
                return;
            }

            transitionTo(mConnectedState);
        } else {
            // Setup data failed.
            long retryDelayMillis = response != null ? response.getRetryDurationMillis()
                    : DataCallResponse.RETRY_DURATION_UNDEFINED;
            NetworkRequestList requestList = new NetworkRequestList(mAttachedNetworkRequestList);
            mDataNetworkCallback.invokeFromExecutor(()
                    -> mDataNetworkCallback.onSetupDataFailed(
                            DataNetwork.this, requestList, mFailCause, retryDelayMillis));
            transitionTo(mDisconnectedState);
        }
    }

    /**
     * Tear down the data network immediately.
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
        logl("onTearDown: reason=" + tearDownReasonToString(reason));
        if (mDataConfigManager.isImsDelayTearDownEnabled()
                && mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                && reason == TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED
                && mPhone.getImsPhone() != null
                && mPhone.getImsPhone().getCallTracker().getState() != PhoneConstants.State.IDLE) {
            logl("onTearDown: Delay IMS tear down until call ends.");
            return;
        }

        // TODO: Need to support DataService.REQUEST_REASON_SHUTDOWN
        mDataServiceManagers.get(mTransport).deactivateDataCall(mCid.get(mTransport),
                DataService.REQUEST_REASON_NORMAL, null);
        mInvokedDataDeactivation = true;
    }

    /**
     * Tear down the data network when condition is met or timed out. Data network will enter
     * {@link DisconnectingState} immediately and waiting for condition met. When condition is met,
     * {@link DataNetworkController} should invoke {@link Consumer#accept(Object)} so the actual
     * tear down work can be performed.
     *
     * This is primarily used for IMS graceful tear down. {@link DataNetworkController} inform
     * {@link DataNetwork} to enter {@link DisconnectingState}. IMS service can observe this
     * through {@link PreciseDataConnectionState#getState()} and then perform IMS de-registration
     * work. After IMS de-registered, {@link DataNetworkController} informs {@link DataNetwork}
     * that it's okay to tear down the network.
     *
     * @param reason The tear down reason.
     *
     * @param timeoutMillis Timeout in milliseconds. Within the time window, clients will have to
     * call {@link Consumer#accept(Object)}, otherwise, data network will be torn down when
     * timed out.
     *
     * @return The runnable for client to execute when condition is met. When executed, tear down
     * will be performed. {@code null} if the data network is already disconnected or being
     * disconnected.
     */
    public @Nullable Runnable tearDownWithCondition(@TearDownReason int reason,
            long timeoutMillis) {
        if (getCurrentState() == null || isDisconnected() || isDisconnecting()) {
            loge("tearDownGracefully: Not in the right state. State=" + getCurrentState());
            return null;
        }
        logl("tearDownWithCondition: reason=" + tearDownReasonToString(reason) + ", timeout="
                + timeoutMillis + "ms.");
        sendMessageDelayed(EVENT_TEAR_DOWN_NETWORK, reason, timeoutMillis);
        return () -> this.tearDown(reason);
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
        logv("onDataStateChanged: " + responseList);
        if (transport != mTransport || mCid.get(mTransport) == INVALID_CID || isDisconnected()) {
            return;
        }

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
                    if (mEverConnected) {
                        mDataNetworkCallback.invokeFromExecutor(
                                () -> mDataNetworkCallback.onDisconnected(
                                        DataNetwork.this, response.getCause()));
                    } else {
                        log("onDataStateChanged: never in connected state. Treated as a setup "
                                + "failure.");
                        mDataNetworkCallback.invokeFromExecutor(() -> mDataNetworkCallback
                                .onSetupDataFailed(DataNetwork.this, mAttachedNetworkRequestList,
                                        DataFailCause.NO_RETRY_FAILURE,
                                        DataCallResponse.RETRY_DURATION_UNDEFINED));
                    }
                    transitionTo(mDisconnectedState);
                }
            }
        } else {
            // The data call response is missing from the list. This means the PDN is gone. This
            // is the PDN lost reported by the modem. We don't send another DEACTIVATE_DATA request
            // for that
            log("onDataStateChanged: PDN disconnected reported by "
                    + AccessNetworkConstants.transportTypeToString(mTransport) + " data service.");
            if (mEverConnected) {
                mDataNetworkCallback.invokeFromExecutor(() -> mDataNetworkCallback
                        .onDisconnected(DataNetwork.this, DataFailCause.LOST_CONNECTION));
            } else {
                log("onDataStateChanged: never in connected state. Treated as a setup failure.");
                mDataNetworkCallback.invokeFromExecutor(() -> mDataNetworkCallback
                        .onSetupDataFailed(DataNetwork.this, mAttachedNetworkRequestList,
                                DataFailCause.NO_RETRY_FAILURE,
                                DataCallResponse.RETRY_DURATION_UNDEFINED));
            }
            transitionTo(mDisconnectedState);
        }
    }

    /**
     * Called when data config updated.
     */
    private void onDataConfigUpdated() {
        log("onDataConfigUpdated");

        updateBandwidthFromDataConfig();
        updateMeteredAndCongested();
    }

    /**
     * Called when receiving bandwidth update from the modem.
     *
     * @param linkCapacityEstimates The link capacity estimate list from the modem.
     */
    private void onBandwidthUpdatedFromModem(
            @NonNull List<LinkCapacityEstimate> linkCapacityEstimates) {
        Objects.requireNonNull(linkCapacityEstimates);
        if (linkCapacityEstimates.isEmpty()) return;

        int uplinkBandwidthKbps = 0, downlinkBandwidthKbps = 0;
        for (LinkCapacityEstimate linkCapacityEstimate : linkCapacityEstimates) {
            if (linkCapacityEstimate.getType() == LinkCapacityEstimate.LCE_TYPE_COMBINED) {
                uplinkBandwidthKbps = linkCapacityEstimate.getUplinkCapacityKbps();
                downlinkBandwidthKbps = linkCapacityEstimate.getDownlinkCapacityKbps();
                break;
            } else if (linkCapacityEstimate.getType() == LinkCapacityEstimate.LCE_TYPE_PRIMARY
                    || linkCapacityEstimate.getType() == LinkCapacityEstimate.LCE_TYPE_SECONDARY) {
                uplinkBandwidthKbps += linkCapacityEstimate.getUplinkCapacityKbps();
                downlinkBandwidthKbps += linkCapacityEstimate.getDownlinkCapacityKbps();
            } else {
                loge("Invalid LinkCapacityEstimate type " + linkCapacityEstimate.getType());
            }
        }
        onBandwidthUpdated(uplinkBandwidthKbps, downlinkBandwidthKbps);
    }

    /**
     * Called when bandwidth estimation updated from either modem or the bandwidth estimator.
     *
     * @param uplinkBandwidthKbps Uplink bandwidth estimate in Kbps.
     * @param downlinkBandwidthKbps Downlink bandwidth estimate in Kbps.
     */
    private void onBandwidthUpdated(int uplinkBandwidthKbps, int downlinkBandwidthKbps) {
        log("onBandwidthUpdated: downlinkBandwidthKbps=" + downlinkBandwidthKbps
                + ", uplinkBandwidthKbps=" + uplinkBandwidthKbps);
        NetworkBandwidth bandwidthFromConfig =  mDataConfigManager.getBandwidthForNetworkType(
                getDataNetworkType(), mPhone.getServiceState());

        if (downlinkBandwidthKbps == LinkCapacityEstimate.INVALID && bandwidthFromConfig != null) {
            // Fallback to carrier config.
            downlinkBandwidthKbps = bandwidthFromConfig.downlinkBandwidthKbps;
        }

        if (uplinkBandwidthKbps == LinkCapacityEstimate.INVALID && bandwidthFromConfig != null) {
            // Fallback to carrier config.
            uplinkBandwidthKbps = bandwidthFromConfig.uplinkBandwidthKbps;
        }

        // Make sure uplink is not greater than downlink.
        uplinkBandwidthKbps = Math.min(uplinkBandwidthKbps, downlinkBandwidthKbps);
        mNetworkBandwidth = new NetworkBandwidth(downlinkBandwidthKbps, uplinkBandwidthKbps);

        updateNetworkCapabilities();
    }

    /**
     * Called when display info changed. This can happen when network types changed or override
     * types (5G NSA, 5G MMWAVE) changes.
     */
    private void onDisplayInfoChanged() {
        updateBandwidthFromDataConfig();
        updateMeteredAndCongested();
    }

    /**
     * Update the bandwidth from carrier config. Note this is no-op if the bandwidth source is not
     * carrier config.
     */
    private void updateBandwidthFromDataConfig() {
        if (mDataConfigManager.getBandwidthEstimateSource() != BANDWIDTH_SOURCE_CARRIER_CONFIG) {
            return;
        }
        log("updateBandwidthFromDataConfig");
        mNetworkBandwidth = mDataConfigManager.getBandwidthForNetworkType(
                getDataNetworkType(), mPhone.getServiceState());
        updateNetworkCapabilities();
    }

    /**
     * Update the metered and congested values from carrier configs and subscription overrides
     */
    private void updateMeteredAndCongested() {
        int networkType = isNrConnected() ? TelephonyManager.NETWORK_TYPE_NR : getDataNetworkType();
        log("updateMeteredAndCongested: networkType="
                + TelephonyManager.getNetworkTypeName(networkType));
        boolean changed = false;
        if (mDataConfigManager.isTempNotMeteredSupportedByCarrier() != mTempNotMeteredSupported) {
            mTempNotMeteredSupported = !mTempNotMeteredSupported;
            changed = true;
            log("updateMeteredAndCongested: mTempNotMeteredSupported changed to "
                    + mTempNotMeteredSupported);
        }
        if ((mDataNetworkController.getUnmeteredOverrideNetworkTypes().contains(networkType)
                || isNetworkTypeUnmetered(networkType)) != mTempNotMetered) {
            mTempNotMetered = !mTempNotMetered;
            changed = true;
            log("updateMeteredAndCongested: mTempNotMetered changed to " + mTempNotMetered);
        }
        if (mDataNetworkController.getCongestedOverrideNetworkTypes().contains(networkType)
                != mCongested) {
            mCongested = !mCongested;
            changed = true;
            log("updateMeteredAndCongested: mCongested changed to " + mCongested);
        }
        if (changed) {
            updateNetworkCapabilities();
        }
    }

    /**
     * Get whether the network type is unmetered from SubscriptionPlans, from either an unmetered
     * general plan or specific plan for the given network type.
     *
     * @param networkType The network type to check meteredness for
     * @return Whether the given network type is unmetered based on SubscriptionPlans
     */
    private boolean isNetworkTypeUnmetered(@NetworkType int networkType) {
        List<SubscriptionPlan> plans = mDataNetworkController.getSubscriptionPlans();
        if (plans.isEmpty()) return false;
        boolean isGeneralUnmetered = true;
        Set<Integer> allNetworkTypes = Arrays.stream(TelephonyManager.getAllNetworkTypes())
                .boxed().collect(Collectors.toSet());
        for (SubscriptionPlan plan : plans) {
            // Check if plan is general (applies to all network types) or specific
            if (Arrays.stream(plan.getNetworkTypes()).boxed().collect(Collectors.toSet())
                    .containsAll(allNetworkTypes)) {
                if (plan.getDataLimitBytes() != SubscriptionPlan.BYTES_UNLIMITED) {
                    // Metered takes precedence over unmetered for safety
                    isGeneralUnmetered = false;
                }
            } else {
                // Check if plan applies to given network type
                if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                    for (int planNetworkType : plan.getNetworkTypes()) {
                        if (planNetworkType == networkType) {
                            return plan.getDataLimitBytes() == SubscriptionPlan.BYTES_UNLIMITED;
                        }
                    }
                }
            }
        }
        return isGeneralUnmetered;
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
        return getDataNetworkType(mTransport);
    }

    /**
     * Get the data network type on the specified transport.
     *
     * @param transport The transport.
     * @return The data network type.
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
     * @return The physical link status (i.e. RRC state).
     */
    public @LinkStatus int getLinkStatus() {
        return mLinkStatus;
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
        // TODO: Should update the score when attached list changed.
        for (TelephonyNetworkRequest networkRequest : mAttachedNetworkRequestList) {
            if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && networkRequest.getNetworkSpecifier() == null) {
                score = DEFAULT_INTERNET_NETWORK_SCORE;
            }
        }

        return new NetworkScore.Builder().setLegacyInt(score).build();
    }

    /**
     * Get the highest priority network capability from the network request. This is used to get
     * the representative APN-type capability for different purposes. It will never return a
     * non-APN-type capability.
     *
     * @return The highest priority network capability from this network.
     */
    public @NetCapability int getHighestPriorityNetworkCapability() {
        int highestPriority = 0;
        int highestPriorityCapability = -1;
        for (int capability : getNetworkCapabilities().getCapabilities()) {
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
     * @return The priority of the network. The priority is derived from the highest priority
     * capability of the network.
     */
    public int getPriority() {
        return Arrays.stream(getNetworkCapabilities().getCapabilities()).boxed()
                .map(mDataConfigManager::getNetworkCapabilityPriority)
                .max(Integer::compare)
                .orElse(-1);
    }

    /**
     * @return The attached network request list.
     */
    public @NonNull NetworkRequestList getAttachedNetworkRequestList() {
        return mAttachedNetworkRequestList;
    }

    /**
     * @return {@code true} if in connecting state.
     */
    public boolean isConnecting() {
        return getCurrentState() == mConnectingState;
    }

    /**
     * @return {@code true} if in connected state.
     */
    public boolean isConnected() {
        return getCurrentState() == mConnectedState;
    }

    /**
     * @return {@code true} if in disconnecting state.
     */
    public boolean isDisconnecting() {
        return getCurrentState() == mDisconnectingState;
    }

    /**
     * @return {@code true} if in disconnected state.
     */
    public boolean isDisconnected() {
        return getCurrentState() == mDisconnectedState;
    }

    /**
     * @return {@code true} if in handover state.
     */
    public boolean isHandoverInProgress() {
        return getCurrentState() == mHandoverState;
    }

    /**
     * @return {@code true} if the data network is suspended.
     */
    public boolean isSuspended() {
        return getState() == TelephonyManager.DATA_SUSPENDED;
    }

    /**
     * @return The current transport of the data network.
     */
    public @TransportType int getTransport() {
        return mTransport;
    }

    private @DataState int getState() {
        IState state = getCurrentState();
        if (state == null || isDisconnected()) {
            return TelephonyManager.DATA_DISCONNECTED;
        } else if (isConnecting()) {
            return TelephonyManager.DATA_CONNECTING;
        } else if (isConnected()) {
            // The data connection can only be suspended when it's in active state.
            if (mSuspended) {
                return TelephonyManager.DATA_SUSPENDED;
            }
            return TelephonyManager.DATA_CONNECTED;
        } else if (isDisconnecting()) {
            return TelephonyManager.DATA_DISCONNECTING;
        } else if (isHandoverInProgress()) {
            return TelephonyManager.DATA_HANDOVER_IN_PROGRESS;
        }

        return TelephonyManager.DATA_UNKNOWN;
    }

    /**
     * @return {@code true} if this data network supports internet.
     */
    public boolean isInternetSupported() {
        return mNetworkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && mNetworkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                && mNetworkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                && mNetworkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                && mNetworkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
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
     * Request the data network to handover to the target transport.
     *
     * @param targetTransport The target transport.
     * @param retryEntry Data handover retry entry. This would be {@code null} for first time
     * handover attempt.
     * @return {@code true} if the request has been accepted.
     */
    public boolean startHandover(@TransportType int targetTransport,
            @Nullable DataHandoverRetryEntry retryEntry) {
        if (getCurrentState() == null || isDisconnected() || isDisconnecting()) {
            // Fail the request if not in the appropriate state.
            if (retryEntry != null) retryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return false;
        }
        sendMessage(obtainMessage(EVENT_START_HANDOVER, targetTransport, 0, retryEntry));
        return true;
    }

    /**
     * Called when handover between IWLAN and cellular is needed.
     *
     * @param targetTransport The target transport.
     * @param retryEntry Data handover retry entry. This would be {@code null} for first time
     * handover attempt.
     */
    private void onStartHandover(@TransportType int targetTransport,
            @Nullable DataHandoverRetryEntry retryEntry) {
        if (mTransport == targetTransport) {
            log("onStartHandover: The network is already on "
                    + AccessNetworkConstants.transportTypeToString(mTransport)
                    + ", handover is not needed.");
            if (retryEntry != null) retryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return;
        }

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

        logl("Start handover from " + AccessNetworkConstants.transportTypeToString(mTransport)
                + " to " + AccessNetworkConstants.transportTypeToString(targetTransport));
        // Send the handover request to the target transport data service.
        mDataServiceManagers.get(targetTransport).setupDataCall(
                DataUtils.networkTypeToAccessNetworkType(getDataNetworkType(targetTransport)),
                mDataProfile, isModemRoaming, allowRoaming,
                DataService.REQUEST_REASON_HANDOVER, mLinkProperties, mPduSessionId,
                mNetworkSliceInfo, mDataProfile.getTrafficDescriptor(), true,
                obtainMessage(EVENT_HANDOVER_RESPONSE, retryEntry));
        transitionTo(mHandoverState);
    }

    /**
     * Called when receiving handover response from the data service.
     *
     * @param resultCode The result code.
     * @param response The response.
     * @param retryEntry Data handover retry entry. This would be {@code null} for first time
     * handover attempt.
     */
    private void onHandoverResponse(@DataServiceCallback.ResultCode int resultCode,
            @Nullable DataCallResponse response, @Nullable DataHandoverRetryEntry retryEntry) {
        logl("onHandoverResponse: resultCode=" + DataServiceCallback.resultCodeToString(resultCode)
                + ", response=" + response);
        mFailCause = getFailCauseFromDataCallResponse(resultCode, response);
        if (mFailCause == DataFailCause.NONE) {
            // Handover succeeded.

            // Clean up on the source transport.
            mDataServiceManagers.get(mTransport).deactivateDataCall(mCid.get(mTransport),
                    DataService.REQUEST_REASON_HANDOVER, null);
            // Switch the transport to the target.
            mTransport = DataUtils.getTargetTransport(mTransport);
            // Update the logging tag
            mLogTag = "DN-" + mInitialNetworkAgentId + "-"
                    + ((mTransport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) ? "C" : "I");
            updateDataNetwork(response);
            if (mTransport != AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                // Handover from WWAN to WLAN
                mPcoData.clear();
                unregisterForWwanEvents();
            } else {
                // Handover from WLAN to WWAN
                registerForWwanEvents();
            }
            if (retryEntry != null) retryEntry.setState(DataRetryEntry.RETRY_STATE_SUCCEEDED);
            mDataNetworkCallback.invokeFromExecutor(
                    () -> mDataNetworkCallback.onHandoverSucceeded(DataNetwork.this));
        } else {
            // Handover failed.
            long retry = response != null ? response.getRetryDurationMillis()
                    : DataCallResponse.RETRY_DURATION_UNDEFINED;
            int handoverFailureMode = response != null ? response.getHandoverFailureMode()
                    : DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY;
            if (retryEntry != null) retryEntry.setState(DataRetryEntry.RETRY_STATE_FAILED);
            mDataNetworkCallback.invokeFromExecutor(
                    () -> mDataNetworkCallback.onHandoverFailed(DataNetwork.this,
                            mFailCause, retry, handoverFailureMode));
        }

        // No matter handover succeeded or not, transit back to connected state.
        transitionTo(mConnectedState);
    }

    /**
     * Called when receiving PCO (Protocol Configuration Options) data from the cellular network.
     *
     * @param pcoData PCO data.
     */
    private void onPcoDataReceived(@NonNull PcoData pcoData) {
        if (pcoData.cid != getId()) return;
        PcoData oldData = mPcoData.put(pcoData.pcoId, pcoData);
        if (!Objects.equals(oldData, pcoData)) {
            log("onPcoDataReceived: " + pcoData);
            mDataNetworkCallback.invokeFromExecutor(
                    () -> mDataNetworkCallback.onPcoDataChanged(DataNetwork.this));
            if (mDataProfile.getApnSetting() != null) {
                for (int apnType : mDataProfile.getApnSetting().getApnTypes()) {
                    Intent intent = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_PCO_VALUE);
                    intent.putExtra(TelephonyManager.EXTRA_APN_TYPE, apnType);
                    intent.putExtra(TelephonyManager.EXTRA_APN_PROTOCOL,
                            ApnSetting.getProtocolIntFromString(pcoData.bearerProto));
                    intent.putExtra(TelephonyManager.EXTRA_PCO_ID, pcoData.pcoId);
                    intent.putExtra(TelephonyManager.EXTRA_PCO_VALUE, pcoData.contents);
                    mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
                }
            }
        }
    }

    /**
     * @return The PCO data received from the network.
     */
    public @NonNull Map<Integer, PcoData> getPcoData() {
        return mPcoData;
    }

    /**
     * Check if the this data network is VCN-managed.
     *
     * @param networkCapabilities The network capabilities of this data network.
     * @return {@code true} if this data network is VCN-managed.
     */
    private boolean isVcnManaged(NetworkCapabilities networkCapabilities) {
        if (mVcnManager == null) return false;
        VcnNetworkPolicyResult policyResult =
                mVcnManager.applyVcnNetworkPolicy(networkCapabilities, getLinkProperties());

        // if the Network does have capability NOT_VCN_MANAGED, return false to indicate it's not
        // VCN-managed
        return !policyResult
                .getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
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
            case TEAR_DOWN_REASON_RAT_NOT_ALLOWED:
                return "TEAR_DOWN_REASON_RAT_NOT_ALLOWED";
            case TEAR_DOWN_REASON_ROAMING_DISABLED:
                return "TEAR_DOWN_REASON_ROAMING_DISABLED";
            case TEAR_DOWN_REASON_CONCURRENT_VOICE_DATA_NOT_ALLOWED:
                return "TEAR_DOWN_REASON_CONCURRENT_VOICE_DATA_NOT_ALLOWED";
            case TEAR_DOWN_REASON_DATA_RESTRICTED_BY_NETWORK:
                return "TEAR_DOWN_REASON_DATA_RESTRICTED_BY_NETWORK";
            case TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY:
                return "TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY";
            case TEAR_DOWN_REASON_POWER_OFF_BY_CARRIER:
                return "TEAR_DOWN_REASON_POWER_OFF_BY_CARRIER";
            case TEAR_DOWN_REASON_DATA_STALL:
                return "TEAR_DOWN_REASON_DATA_STALL";
            case TEAR_DOWN_REASON_HANDOVER_FAILED:
                return "TEAR_DOWN_REASON_HANDOVER_FAILED";
            case TEAR_DOWN_REASON_HANDOVER_NOT_ALLOWED:
                return "TEAR_DOWN_REASON_HANDOVER_NOT_ALLOWED";
            case TEAR_DOWN_REASON_VCN_REQUESTED:
                return "TEAR_DOWN_REASON_VCN_REQUESTED";
            case TEAR_DOWN_REASON_VOPS_NOT_SUPPORTED:
                return "TEAR_DOWN_REASON_VOPS_NOT_SUPPORTED";
            case TEAR_DOWN_REASON_DEFAULT_DATA_UNSELECTED:
                return "TEAR_DOWN_REASON_DEFAULT_DATA_UNSELECTED";
            case TEAR_DOWN_REASON_NOT_IN_SERVICE:
                return "TEAR_DOWN_REASON_NOT_IN_SERVICE";
            case TEAR_DOWN_REASON_DATA_CONFIG_NOT_READY:
                return "TEAR_DOWN_REASON_DATA_CONFIG_NOT_READY";
            case TEAR_DOWN_REASON_PENDING_TEAR_DOWN_ALL:
                return "TEAR_DOWN_REASON_PENDING_TEAR_DOWN_ALL";
            case TEAR_DOWN_REASON_NO_SUITABLE_DATA_PROFILE:
                return "TEAR_DOWN_REASON_NO_SUITABLE_DATA_PROFILE";
            case TEAR_DOWN_REASON_EMERGENCY_CALL:
                return "TEAR_DOWN_REASON_EMERGENCY_CALL";
            case TEAR_DOWN_REASON_RETRY_SCHEDULED:
                return "TEAR_DOWN_REASON_RETRY_SCHEDULED";
            case TEAR_DOWN_REASON_DATA_THROTTLED:
                return "TEAR_DOWN_REASON_DATA_THROTTLED";
            case TEAR_DOWN_REASON_DATA_PROFILE_INVALID:
                return "TEAR_DOWN_REASON_DATA_PROFILE_INVALID";
            case TEAR_DOWN_REASON_DATA_PROFILE_NOT_PREFERRED:
                return "TEAR_DOWN_REASON_DATA_PROFILE_NOT_PREFERRED";
            case TEAR_DOWN_REASON_NOT_ALLOWED_BY_POLICY:
                return "TEAR_DOWN_REASON_NOT_ALLOWED_BY_POLICY";
            case TEAR_DOWN_REASON_ILLEGAL_STATE:
                return "TEAR_DOWN_REASON_ILLEGAL_STATE";
            case TEAR_DOWN_ONLY_ALLOWED_SINGLE_NETWORK:
                return "TEAR_DOWN_ONLY_ALLOWED_SINGLE_NETWORK";
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
            case EVENT_DETACH_ALL_NETWORK_REQUESTS:
                return "EVENT_DETACH_ALL_NETWORK_REQUESTS";
            case EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED:
                return "EVENT_BANDWIDTH_ESTIMATE_FROM_MODEM_CHANGED";
            case EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED:
                return "EVENT_BANDWIDTH_ESTIMATE_FROM_BANDWIDTH_ESTIMATOR_CHANGED";
            case EVENT_DISPLAY_INFO_CHANGED:
                return "EVENT_DISPLAY_INFO_CHANGED";
            case EVENT_START_HANDOVER:
                return "EVENT_START_HANDOVER";
            case EVENT_HANDOVER_RESPONSE:
                return "EVENT_HANDOVER_RESPONSE";
            case EVENT_SUBSCRIPTION_PLAN_OVERRIDE:
                return "EVENT_SUBSCRIPTION_PLAN_OVERRIDE";
            case EVENT_PCO_DATA_RECEIVED:
                return "EVENT_PCO_DATA_RECEIVED";
            default:
                return "Unknown(" + event + ")";
        }
    }

    @Override
    public String toString() {
        return "[DataNetwork: " + mLogTag + ", " + (mDataProfile.getApnSetting() != null
                ? mDataProfile.getApnSetting().getApnName() : null) + ", state="
                + (getCurrentState() != null ? getCurrentState().getName() : null) + "]";
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
        super.dump(fd, pw, args);
        pw.println("Tag: " + name());
        pw.increaseIndent();
        pw.println("mSubId=" + mSubId);
        pw.println("WWAN cid=" + mCid.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        pw.println("WLAN cid=" + mCid.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        pw.println("mPduSessionId=" + mPduSessionId);
        pw.println("mDataProfile=" + mDataProfile);
        pw.println("mNetworkCapabilities" + mNetworkCapabilities);
        pw.println("mLinkProperties=" + mLinkProperties);
        pw.println("mNetworkSliceInfo=" + mNetworkSliceInfo);
        pw.println("mNetworkBandwidth=" + mNetworkBandwidth);
        pw.println("mTempNotMeteredSupported=" + mTempNotMeteredSupported);
        pw.println("mTempNotMetered=" + mTempNotMetered);
        pw.println("mCongested=" + mCongested);
        pw.println("mDataCallResponse=" + mDataCallResponse);

        pw.println("Attached network requests:");
        pw.increaseIndent();
        for (TelephonyNetworkRequest request : mAttachedNetworkRequestList) {
            pw.println(request);
        }
        pw.decreaseIndent();
        pw.println("mQosBearerSessions=" + mQosBearerSessions);
        pw.println("mEverConnected=" + mEverConnected);
        pw.println("mInvokedDataDeactivation=" + mInvokedDataDeactivation);

        mNetworkAgent.dump(fd, pw, args);
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
        pw.println("---------------");
    }
}
