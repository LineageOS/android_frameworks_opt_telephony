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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.net.NetworkPolicyManager.SubscriptionCallback;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.RadioAccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.PcoData;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.DataState;
import android.telephony.TelephonyManager.SimState;
import android.telephony.TelephonyRegistryManager;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataCallResponse.HandoverFailureMode;
import android.telephony.data.DataCallResponse.LinkStatus;
import android.telephony.data.DataProfile;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager;
import android.telephony.ims.feature.ImsFeature;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.data.AccessNetworksManager.AccessNetworksManagerCallback;
import com.android.internal.telephony.data.DataEvaluation.DataAllowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataEvaluationReason;
import com.android.internal.telephony.data.DataNetwork.DataNetworkCallback;
import com.android.internal.telephony.data.DataNetwork.TearDownReason;
import com.android.internal.telephony.data.DataProfileManager.DataProfileManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataHandoverRetryEntry;
import com.android.internal.telephony.data.DataRetryManager.DataRetryEntry;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataSetupRetryEntry;
import com.android.internal.telephony.data.DataSettingsManager.DataSettingsManagerCallback;
import com.android.internal.telephony.data.DataStallRecoveryManager.DataStallRecoveryManagerCallback;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DataNetworkController in the central module of the telephony data stack. It is responsible to
 * create and manage all the mobile data networks. It is per-SIM basis which means for DSDS devices,
 * there will be two DataNetworkController instances. Unlike the Android 12 DcTracker, which is
 * designed to be per-transport (i.e. cellular, IWLAN), DataNetworkController is designed to handle
 * data networks on both cellular and IWLAN.
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

    /** Event for packet switch restricted enabled by network. */
    private static final int EVENT_PS_RESTRICT_ENABLED = 6;

    /** Event for packet switch restricted disabled by network. */
    private static final int EVENT_PS_RESTRICT_DISABLED = 7;

    /** Event for data service binding changed. */
    private static final int EVENT_DATA_SERVICE_BINDING_CHANGED = 8;

    /** Event for SIM state changed. */
    private static final int EVENT_SIM_STATE_CHANGED = 9;

    /** Event for tearing down all data networks. */
    private static final int EVENT_TEAR_DOWN_ALL_DATA_NETWORKS = 12;

    /** Event for registering data network controller callback. */
    private static final int EVENT_REGISTER_DATA_NETWORK_CONTROLLER_CALLBACK = 13;

    /** Event for unregistering data network controller callback. */
    private static final int EVENT_UNREGISTER_DATA_NETWORK_CONTROLLER_CALLBACK = 14;

    /** Event for subscription info changed. */
    private static final int EVENT_SUBSCRIPTION_CHANGED = 15;

    /** Event for re-evaluating existing data networks. */
    private static final int EVENT_REEVALUATE_EXISTING_DATA_NETWORKS = 16;

    /** Event for data RAT or registration state changed. */
    private static final int EVENT_SERVICE_STATE_CHANGED = 17;

    /** Event for voice call ended. */
    private static final int EVENT_VOICE_CALL_ENDED = 18;

    /** Event for registering all events. */
    private static final int EVENT_REGISTER_ALL_EVENTS = 19;

    /** Event for emergency call started or ended. */
    private static final int EVENT_EMERGENCY_CALL_CHANGED = 20;

    /** Event for preferred transport changed. */
    private static final int EVENT_PREFERRED_TRANSPORT_CHANGED = 21;

    /** Event for subscription plans changed. */
    private static final int EVENT_SUBSCRIPTION_PLANS_CHANGED = 22;

    /** Event for unmetered or congested subscription override. */
    private static final int EVENT_SUBSCRIPTION_OVERRIDE = 23;

    /** The supported IMS features. This is for IMS graceful tear down support. */
    private static final Collection<Integer> SUPPORTED_IMS_FEATURES =
            List.of(ImsFeature.FEATURE_MMTEL, ImsFeature.FEATURE_RCS);

    /** The maximum number of previously connected data networks for debugging purposes. */
    private static final int MAX_HISTORICAL_CONNECTED_DATA_NETWORKS = 10;

    /** The delay to re-evaluate preferred transport when handover failed and fallback to source. */
    private static final long REEVALUATE_PREFERRED_TRANSPORT_DELAY_MILLIS =
            TimeUnit.SECONDS.toMillis(3);

    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    private final @NonNull DataConfigManager mDataConfigManager;
    private final @NonNull DataSettingsManager mDataSettingsManager;
    private final @NonNull DataProfileManager mDataProfileManager;
    private final @NonNull DataStallRecoveryManager mDataStallRecoveryManager;
    private final @NonNull AccessNetworksManager mAccessNetworksManager;
    private final @NonNull DataRetryManager mDataRetryManager;
    private final @NonNull ImsManager mImsManager;
    private final @NonNull NetworkPolicyManager mNetworkPolicyManager;
    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers =
            new SparseArray<>();

    /** The subscription index associated with this data network controller. */
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    /** The current service state of the device. */
    // Note that keeping a copy here instead of directly using ServiceStateTracker.getServiceState()
    // is intended for detecting the delta.
    private @NonNull ServiceState mServiceState;

    /** The list of SubscriptionPlans, updated when initialized and when plans are changed. */
    private final @NonNull List<SubscriptionPlan> mSubscriptionPlans = new ArrayList<>();

    /**
     * The set of network types an unmetered override applies to, set by onSubscriptionOverride
     * and cleared when the device is rebooted or the override expires.
     */
    private final @NonNull @NetworkType Set<Integer> mUnmeteredOverrideNetworkTypes =
            new ArraySet<>();

    /**
     * The set of network types a congested override applies to, set by onSubscriptionOverride
     * and cleared when the device is rebooted or the override expires.
     */
    private final @NonNull @NetworkType Set<Integer> mCongestedOverrideNetworkTypes =
            new ArraySet<>();

    /**
     * The list of all network requests.
     */
    private final @NonNull NetworkRequestList mAllNetworkRequestList = new NetworkRequestList();

    /**
     * The current data network list, including the ones that are connected, connecting, or
     * disconnecting.
     */
    private final @NonNull List<DataNetwork> mDataNetworkList = new ArrayList<>();

    /** {@code true} indicating at least one data network exists. */
    private boolean mAnyDataNetworkExisting;

    /**
     * Contain the last 10 data networks that were connected. This is for debugging purposes only.
     */
    private final @NonNull List<DataNetwork> mPreviousConnectedDataNetworkList = new ArrayList<>();

    /**
     * The internet data network state. Note that this is the best effort if more than one
     * data network supports internet.
     */
    private @DataState int mInternetDataNetworkState = TelephonyManager.DATA_DISCONNECTED;

    /**
     * The IMS data network state. For now this is just for debugging purposes.
     */
    private @DataState int mImsDataNetworkState = TelephonyManager.DATA_DISCONNECTED;

    /** Overall aggregated link status from internet data networks. */
    private @LinkStatus int mInternetLinkStatus = DataCallResponse.LINK_STATUS_UNKNOWN;

    /** Data network controller callbacks. */
    private final @NonNull Set<DataNetworkControllerCallback> mDataNetworkControllerCallbacks =
            new ArraySet<>();

    /** Indicates if packet switch data is restricted by the network. */
    private boolean mPsRestricted = false;

    /** Indicates if NR advanced is allowed by PCO. */
    private boolean mNrAdvancedCapableByPco = false;

    /**
     * Indicates if the data services are bound. Key if the transport type, and value is the boolean
     * indicating service is bound or not.
     */
    private final @NonNull SparseBooleanArray mDataServiceBound = new SparseBooleanArray();

    /** SIM state. */
    private @SimState int mSimState = TelephonyManager.SIM_STATE_UNKNOWN;

    /**
     * IMS state callbacks. Key is the IMS feature, value is the callback.
     */
    private final @NonNull SparseArray<ImsStateCallback> mImsStateCallbacks = new SparseArray<>();

    /** Registered IMS features. Unregistered IMS features are removed from the set. */
    private final @NonNull Set<Integer> mRegisteredImsFeatures = new ArraySet<>();

    /** IMS feature package names. Key is the IMS feature, value is the package name. */
    private final @NonNull SparseArray<String> mImsFeaturePackageName = new SparseArray<>();

    /**
     * Networks that are pending IMS de-registration. Key is the data network, value is the function
     * to tear down the network.
     */
    private final @NonNull Map<DataNetwork, Runnable> mPendingImsDeregDataNetworks =
            new ArrayMap<>();

    /**
     * IMS feature registration callback. The key is the IMS feature, the value is the registration
     * callback. When new SIM inserted, the old callbacks associated with the old subscription index
     * will be unregistered.
     */
    private final @NonNull SparseArray<RegistrationManager.RegistrationCallback>
            mImsFeatureRegistrationCallbacks = new SparseArray<>();

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
        public NetworkRequestList(@NonNull NetworkRequestList requestList) {
            addAll(requestList);
        }

        /**
         * Constructor
         *
         * @param requestList The network request list.
         */
        public NetworkRequestList(@NonNull List<TelephonyNetworkRequest> requestList) {
            addAll(requestList);
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

        /**
         * Check if any network request is requested by the specified package.
         *
         * @param packageName The package name.
         * @return {@code true} if any request is originated from the specified package.
         */
        public boolean hasNetworkRequestsFromPackage(@NonNull String packageName) {
            for (TelephonyNetworkRequest networkRequest : this) {
                if (packageName.equals(
                        networkRequest.getNativeNetworkRequest().getRequestorPackageName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return "[NetworkRequestList: size=" + size() + (size() > 0 ? ", leading by "
                    + get(0) : "") + "]";
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
     * The data network controller callback. Note this is only used for passing information
     * internally in the data stack, should not be used externally.
     */
    public static class DataNetworkControllerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataNetworkControllerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when internet data network validation status changed.
         *
         * @param validationStatus The validation status.
         */
        public void onInternetDataNetworkValidationStatusChanged(
                @ValidationStatus int validationStatus) {}

        /**
         * Called when internet data network is connected.
         *
         * @param dataProfiles The data profiles of the connected internet data network. It should
         * be only one in most of the cases.
         */
        public void onInternetDataNetworkConnected(@NonNull List<DataProfile> dataProfiles) {}

        /** Called when internet data network is disconnected. */
        public void onInternetDataNetworkDisconnected() {}

        /**
         * Called when any data network existing status changed.
         *
         * @param anyDataExisting {@code true} indicating there is at least one data network
         * existing regardless of its state. {@code false} indicating all data networks are
         * disconnected.
         */
        public void onAnyDataNetworkExistingChanged(boolean anyDataExisting) {}

        /**
         * Called when {@link SubscriptionPlan}s change or an unmetered or congested subscription
         * override is set.
         */
        public void onSubscriptionPlanOverride() {}

        /**
         * Called when the physical link status changed.
         *
         * @param status The latest link status.
         */
        public void onPhysicalLinkStatusChanged(@LinkStatus int status) {}

        /**
         * Called when NR advanced capable by PCO changed.
         *
         * @param nrAdvancedCapable {@code true} if at least one of the data network is NR advanced
         * capable.
         */
        public void onNrAdvancedCapableByPcoChanged(boolean nrAdvancedCapable) {}
    }

    /**
     * This class represent a rule allowing or disallowing handover between IWLAN and cellular
     * networks.
     *
     * @see CarrierConfigManager#KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY
     */
    public static class HandoverRule {
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = {"RULE_TYPE_"},
                value = {
                        RULE_TYPE_ALLOWED,
                        RULE_TYPE_DISALLOWED,
                })
        public @interface HandoverRuleType {}

        /** Indicating this rule is for allowing handover. */
        public static final int RULE_TYPE_ALLOWED = 1;

        /** Indicating this rule is for disallowing handover. */
        public static final int RULE_TYPE_DISALLOWED = 2;

        private static final String RULE_TAG_SOURCE_ACCESS_NETWORKS = "source";

        private static final String RULE_TAG_TARGET_ACCESS_NETWORKS = "target";

        private static final String RULE_TAG_TYPE = "type";

        private static final String RULE_TAG_ROAMING = "roaming";

        /** Handover rule type. */
        public final @HandoverRuleType int type;

        /** The applicable source access networks for handover. */
        public final @NonNull @RadioAccessNetworkType Set<Integer> sourceAccessNetworks;

        /** The applicable target access networks for handover. */
        public final @NonNull @RadioAccessNetworkType Set<Integer> targetAccessNetworks;

        /** {@code true} indicates this policy is only applicable when the device is roaming. */
        public final boolean isOnlyForRoaming;

        /**
         * Constructor
         *
         * @param ruleString The rule in string format.
         *
         * @see CarrierConfigManager#KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY
         */
        public HandoverRule(@NonNull String ruleString) {
            if (TextUtils.isEmpty(ruleString)) {
                throw new IllegalArgumentException("illegal rule " + ruleString);
            }

            Set<Integer> source = null, target = null;
            int type = 0;
            boolean roaming = false;

            ruleString = ruleString.trim().toLowerCase(Locale.ROOT);
            String[] expressions = ruleString.split("\\s*,\\s*");
            for (String expression : expressions) {
                String[] tokens = expression.trim().split("\\s*=\\s*");
                if (tokens.length != 2) {
                    throw new IllegalArgumentException("illegal rule " + ruleString + ", tokens="
                            + Arrays.toString(tokens));
                }
                String key = tokens[0].trim();
                String value = tokens[1].trim();
                try {
                    switch (key) {
                        case RULE_TAG_SOURCE_ACCESS_NETWORKS:
                            source = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .map(AccessNetworkType::fromString)
                                    .collect(Collectors.toSet());
                            break;
                        case RULE_TAG_TARGET_ACCESS_NETWORKS:
                            target = Arrays.stream(value.split("\\s*\\|\\s*"))
                                    .map(String::trim)
                                    .map(AccessNetworkType::fromString)
                                    .collect(Collectors.toSet());
                            break;
                        case RULE_TAG_TYPE:
                            if (value.toLowerCase(Locale.ROOT).equals("allowed")) {
                                type = RULE_TYPE_ALLOWED;
                            } else if (value.toLowerCase(Locale.ROOT).equals("disallowed")) {
                                type = RULE_TYPE_DISALLOWED;
                            } else {
                                throw new IllegalArgumentException("unexpected rule type " + value);
                            }
                            break;
                        case RULE_TAG_ROAMING:
                            roaming = Boolean.parseBoolean(value);
                            break;
                        default:
                            throw new IllegalArgumentException("unexpected key " + key);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalArgumentException("illegal rule \"" + ruleString + "\", e="
                            + e);
                }
            }

            if (source == null || target == null) {
                throw new IllegalArgumentException("Need to specify both source and target. "
                        + "\"" + ruleString + "\"");
            }

            if (source.contains(AccessNetworkType.UNKNOWN)) {
                throw new IllegalArgumentException("Source access networks contains unknown. "
                        + "\"" + ruleString + "\"");
            }

            if (target.contains(AccessNetworkType.UNKNOWN)) {
                throw new IllegalArgumentException("Target access networks contains unknown. "
                        + "\"" + ruleString + "\"");
            }

            if (type == 0) {
                throw new IllegalArgumentException("Rule type is not specified correctly. "
                        + "\"" + ruleString + "\"");
            }

            if (!source.contains(AccessNetworkType.IWLAN)
                    && !target.contains(AccessNetworkType.IWLAN)) {
                throw new IllegalArgumentException("IWLAN must be specified in either source or "
                        + "target access networks.\"" + ruleString + "\"");
            }

            sourceAccessNetworks = source;
            targetAccessNetworks = target;
            this.type = type;
            isOnlyForRoaming = roaming;
        }

        @Override
        public String toString() {
            return "[HandoverRule: type=" + (type == RULE_TYPE_ALLOWED ? "allowed"
                    : "disallowed") + ", source=" + sourceAccessNetworks.stream()
                    .map(AccessNetworkType::toString).collect(Collectors.joining("|"))
                    + ", target=" + targetAccessNetworks.stream().map(AccessNetworkType::toString)
                    .collect(Collectors.joining("|")) + ", isRoaming=" + isOnlyForRoaming + "]";
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
                            AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        }
        mDataConfigManager = new DataConfigManager(mPhone, looper);
        mDataSettingsManager = new DataSettingsManager(mPhone, this, looper,
                new DataSettingsManagerCallback(this::post) {
                    @Override
                    public void onDataEnabledChanged(boolean enabled,
                            @TelephonyManager.DataEnabledChangedReason int reason) {
                        // If mobile data is enabled by the user, evaluate the unsatisfied network
                        // requests and then attempt to setup data networks to satisfy them.
                        // If mobile data is disabled, evaluate the existing data networks and
                        // see if they need to be torn down.
                        logl("onDataEnabledChanged: enabled=" + enabled);
                        sendMessage(obtainMessage(enabled
                                        ? EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS
                                        : EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                                DataEvaluationReason.DATA_ENABLED_CHANGED));
                    }
                    @Override
                    public void onDataRoamingEnabledChanged(boolean enabled) {
                        // If data roaming is enabled by the user, evaluate the unsatisfied network
                        // requests and then attempt to setup data networks to satisfy them.
                        // If data roaming is disabled, evaluate the existing data networks and
                        // see if they need to be torn down.
                        logl("onDataRoamingEnabledChanged: enabled=" + enabled);
                        sendMessage(obtainMessage(enabled
                                        ? EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS
                                        : EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                                DataEvaluationReason.ROAMING_ENABLED_CHANGED));
                    }
                });
        mDataProfileManager = new DataProfileManager(mPhone, this, mDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), looper,
                new DataProfileManagerCallback(this::post) {
                    @Override
                    public void onDataProfilesChanged() {
                        sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                                DataEvaluationReason.DATA_PROFILES_CHANGED));
                        sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                                DataEvaluationReason.DATA_PROFILES_CHANGED));
                    }
                });
        mDataStallRecoveryManager = new DataStallRecoveryManager(mPhone, this, mDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), looper,
                new DataStallRecoveryManagerCallback(this::post) {
                    @Override
                    public void onDataStallReestablishInternet() {
                        DataNetworkController.this.onDataStallReestablishInternet();
                    }
                });
        mDataRetryManager = new DataRetryManager(mPhone, this,
                mDataServiceManagers, looper,
                new DataRetryManagerCallback(this::post) {
                    @Override
                    public void onDataNetworkSetupRetry(
                            @NonNull DataSetupRetryEntry dataSetupRetryEntry) {
                        Objects.requireNonNull(dataSetupRetryEntry);
                        DataNetworkController.this.onDataNetworkSetupRetry(dataSetupRetryEntry);
                    }
                    @Override
                    public void onDataNetworkHandoverRetry(
                            @NonNull DataHandoverRetryEntry dataHandoverRetryEntry) {
                        Objects.requireNonNull(dataHandoverRetryEntry);
                        DataNetworkController.this
                                .onDataNetworkHandoverRetry(dataHandoverRetryEntry);
                    }
                    @Override
                    public void onDataNetworkHandoverRetryStopped(
                            @NonNull DataNetwork dataNetwork) {
                        Objects.requireNonNull(dataNetwork);
                        tearDownGracefully(dataNetwork,
                                DataNetwork.TEAR_DOWN_REASON_HANDOVER_FAILED);
                    }
                });
        mImsManager = mPhone.getContext().getSystemService(ImsManager.class);
        mNetworkPolicyManager = mPhone.getContext().getSystemService(NetworkPolicyManager.class);

        // Use the raw one from ServiceStateTracker instead of the combined one from
        // mPhone.getServiceState().
        mServiceState = mPhone.getServiceStateTracker().getServiceState();

        // Instead of calling onRegisterAllEvents directly from the constructor, send the event.
        // The reason is that getImsPhone is null when we are still in the constructor here.
        sendEmptyMessage(EVENT_REGISTER_ALL_EVENTS);
    }

    /**
     * Called when needed to register for all events that data network controller is interested.
     */
    private void onRegisterAllEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_SIM_CARD_STATE_CHANGED);
        filter.addAction(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED);
        mPhone.getContext().registerReceiver(mIntentReceiver, filter, null, mPhone);

        mAccessNetworksManager.registerCallback(new AccessNetworksManagerCallback(this::post) {
            @Override
            public void onPreferredTransportChanged(@NetCapability int capability) {
                DataNetworkController.this.onPreferredTransportChanged(capability);
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.PREFERRED_TRANSPORT_CHANGED));
            }
        });

        mNetworkPolicyManager.registerSubscriptionCallback(new SubscriptionCallback() {
            @Override
            public void onSubscriptionPlansChanged(int subId, SubscriptionPlan[] plans) {
                if (mSubId != subId) return;
                obtainMessage(EVENT_SUBSCRIPTION_PLANS_CHANGED, plans).sendToTarget();
            }

            @Override
            public void onSubscriptionOverride(int subId, int overrideMask, int overrideValue,
                    int[] networkTypes) {
                if (mSubId != subId) return;
                obtainMessage(EVENT_SUBSCRIPTION_OVERRIDE, overrideMask, overrideValue,
                        networkTypes).sendToTarget();
            }
        });

        mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, this, EVENT_SERVICE_STATE_CHANGED,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mPhone.getServiceStateTracker().registerForPsRestrictedEnabled(this,
                EVENT_PS_RESTRICT_ENABLED, null);
        mPhone.getServiceStateTracker().registerForPsRestrictedDisabled(this,
                EVENT_PS_RESTRICT_DISABLED, null);
        mPhone.registerForEmergencyCallToggle(this, EVENT_EMERGENCY_CALL_CHANGED, null);
        mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .registerForServiceBindingChanged(this, EVENT_DATA_SERVICE_BINDING_CHANGED);

        if (!mAccessNetworksManager.isInLegacyMode()) {
            mPhone.getServiceStateTracker().registerForDataRegStateOrRatChanged(
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN, this, EVENT_SERVICE_STATE_CHANGED,
                    AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
            mDataServiceManagers.get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                    .registerForServiceBindingChanged(this, EVENT_DATA_SERVICE_BINDING_CHANGED);
        }

        mPhone.getContext().getSystemService(TelephonyRegistryManager.class)
                .addOnSubscriptionsChangedListener(new OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        sendEmptyMessage(EVENT_SUBSCRIPTION_CHANGED);
                    }
                }, this::post);

        // Register for call ended event for voice/data concurrent not supported case. It is
        // intended to only listen for events from the same phone as most of the telephony modules
        // are designed as per-SIM basis. For DSDS call ended on non-DDS sub, the frameworks relies
        // on service state on DDS sub change from out-of-service to in-service to trigger data
        // retry.
        mPhone.getCallTracker().registerForVoiceCallEnded(this, EVENT_VOICE_CALL_ENDED, null);
        // Check null for devices not supporting FEATURE_TELEPHONY_IMS.
        if (mPhone.getImsPhone() != null) {
            mPhone.getImsPhone().getCallTracker().registerForVoiceCallEnded(
                    this, EVENT_VOICE_CALL_ENDED, null);
        }
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_REGISTER_ALL_EVENTS:
                onRegisterAllEvents();
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
            case EVENT_REEVALUATE_EXISTING_DATA_NETWORKS:
                reason = (DataEvaluationReason) msg.obj;
                onReevaluateExistingDataNetworks(reason);
                break;
            case EVENT_REMOVE_NETWORK_REQUEST:
                onRemoveNetworkRequest((TelephonyNetworkRequest) msg.obj);
                break;
            case EVENT_VOICE_CALL_ENDED:
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.VOICE_CALL_ENDED));
                break;
            case EVENT_PS_RESTRICT_ENABLED:
                mPsRestricted = true;
                sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                        DataEvaluationReason.DATA_RESTRICTED_CHANGED));
                break;
            case EVENT_PS_RESTRICT_DISABLED:
                mPsRestricted = false;
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.DATA_RESTRICTED_CHANGED));
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
            case EVENT_TEAR_DOWN_ALL_DATA_NETWORKS:
                onTearDownAllDataNetworks(msg.arg1);
                break;
            case EVENT_REGISTER_DATA_NETWORK_CONTROLLER_CALLBACK:
                mDataNetworkControllerCallbacks.add((DataNetworkControllerCallback) msg.obj);
                break;
            case EVENT_UNREGISTER_DATA_NETWORK_CONTROLLER_CALLBACK:
                mDataNetworkControllerCallbacks.remove((DataNetworkControllerCallback) msg.obj);
                break;
            case EVENT_SUBSCRIPTION_CHANGED:
                onSubscriptionChanged();
                break;
            case EVENT_SERVICE_STATE_CHANGED:
                onServiceStateChanged();
                break;
            case EVENT_EMERGENCY_CALL_CHANGED:
                if (mPhone.isInEcm()) {
                    sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                            DataEvaluationReason.EMERGENCY_CALL_CHANGED));
                } else {
                    sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                            DataEvaluationReason.EMERGENCY_CALL_CHANGED));
                }
                break;
            case EVENT_PREFERRED_TRANSPORT_CHANGED:
                onPreferredTransportChanged(msg.arg1);
                break;
            case EVENT_SUBSCRIPTION_PLANS_CHANGED:
                SubscriptionPlan[] plans = (SubscriptionPlan[]) msg.obj;
                log("Subscription plans changed: " + Arrays.toString(plans));
                mSubscriptionPlans.clear();
                mSubscriptionPlans.addAll(Arrays.asList(plans));
                mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                        () -> callback.onSubscriptionPlanOverride()));
                break;
            case EVENT_SUBSCRIPTION_OVERRIDE:
                int overrideMask = msg.arg1;
                boolean override = msg.arg2 != 0;
                int[] networkTypes = (int[]) msg.obj;

                if (overrideMask == NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_UNMETERED) {
                    log("Unmetered subscription override: override=" + override
                            + ", networkTypes=" + Arrays.stream(networkTypes)
                            .mapToObj(TelephonyManager::getNetworkTypeName)
                            .collect(Collectors.joining(",")));
                    for (int networkType : networkTypes) {
                        if (override) {
                            mUnmeteredOverrideNetworkTypes.add(networkType);
                        } else {
                            mUnmeteredOverrideNetworkTypes.remove(networkType);
                        }
                    }
                    mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                            () -> callback.onSubscriptionPlanOverride()));
                } else if (overrideMask == NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_CONGESTED) {
                    log("Congested subscription override: override=" + override
                            + ", networkTypes=" + Arrays.stream(networkTypes)
                            .mapToObj(TelephonyManager::getNetworkTypeName)
                            .collect(Collectors.joining(",")));
                    for (int networkType : networkTypes) {
                        if (override) {
                            mCongestedOverrideNetworkTypes.add(networkType);
                        } else {
                            mCongestedOverrideNetworkTypes.remove(networkType);
                        }
                    }
                    mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                            () -> callback.onSubscriptionPlanOverride()));
                } else {
                    loge("Unknown override mask: " + overrideMask);
                }
                break;
            default:
                loge("Unexpected event " + msg.what);
        }
    }

    /**
     * Add a network request, which is originated from the apps. Note that add a network request
     * is not necessarily setting up a {@link DataNetwork}.
     *
     * @param networkRequest Network request
     *
     */
    public void addNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        sendMessage(obtainMessage(EVENT_ADD_NETWORK_REQUEST, networkRequest));
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
                setupDataNetwork(dataProfile, null);
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
            logv("Found a compatible data network " + dataNetwork + ". Attaching "
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
     * @param reason The reason for evaluation.
     * @return The data evaluation result.
     */
    private @NonNull DataEvaluation evaluateNetworkRequest(
            @NonNull TelephonyNetworkRequest networkRequest, DataEvaluationReason reason) {
        DataEvaluation evaluation = new DataEvaluation(reason);
        int transport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkRequest.getApnTypeNetworkCapability());

        // Bypass all checks for emergency network request.
        if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            evaluation.addDataAllowedReason(DataAllowedReason.EMERGENCY_REQUEST);
            evaluation.setCandidateDataProfile(mDataProfileManager.getDataProfileForNetworkRequest(
                    networkRequest, getDataNetworkType(transport)));
            networkRequest.setEvaluation(evaluation);
            log(evaluation.toString());
            return evaluation;
        }

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

        // Check if data roaming is disabled.
        if (mServiceState.getDataRoaming() && !mDataSettingsManager.isDataRoamingEnabled()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.ROAMING_DISABLED);
        }

        // Check if data is restricted by the network.
        if (mPsRestricted) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_RESTRICTED_BY_NETWORK);
        }

        // Check if the request is preferred on cellular and radio is/will be turned off.
        // We are using getDesiredPowerState() instead of isRadioOn() because we also don't want
        // to setup data network when radio power is about to be turned off.
        if (transport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN
                && (!mPhone.getServiceStateTracker().getDesiredPowerState()
                || mPhone.mCi.getRadioState() != TelephonyManager.RADIO_POWER_ON)) {
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

        // Check if device is CDMA and is currently in ECBM
        if (mPhone.isInEcm() && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.EMERGENCY_CALL);
        }


        if (!mDataSettingsManager.isDataEnabled(DataUtils.networkCapabilityToApnType(
                networkRequest.getApnTypeNetworkCapability()))) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_DISABLED);
        }

        // Check whether to allow data in certain situations if data is disallowed for soft reasons
        if (evaluation.isDataAllowed()) {
            evaluation.addDataAllowedReason(DataAllowedReason.NORMAL);
        } else if (!evaluation.containsHardDisallowedReasons()) {
            // Check if request is MMS and MMS is always allowed
            if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                    && mDataSettingsManager.isMmsAlwaysAllowed()) {
                evaluation.addDataAllowedReason(DataAllowedReason.MMS_REQUEST);
            }

            // Check if request is unmetered (WiFi or unmetered APN)
            if (transport == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                evaluation.addDataAllowedReason(DataAllowedReason.UNMETERED_USAGE);
            } else if (transport == AccessNetworkConstants.TRANSPORT_TYPE_WWAN) {
                int apnType = DataUtils.networkCapabilityToApnType(
                        networkRequest.getApnTypeNetworkCapability());
                Set<Integer> meteredApns = mServiceState.getDataRoaming()
                        ? mDataConfigManager.getMeteredApnTypesWhenRoaming()
                        : mDataConfigManager.getMeteredApnTypes();
                if (!meteredApns.contains(apnType)) {
                    evaluation.addDataAllowedReason(DataAllowedReason.UNMETERED_USAGE);
                }
            }

            // Check if request is restricted
            if (!networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
                evaluation.addDataAllowedReason(DataAllowedReason.RESTRICTED_REQUEST);
            }
        }

        // Check if there is any compatible data profile
        DataProfile dataProfile = mDataProfileManager
                .getDataProfileForNetworkRequest(networkRequest, getDataNetworkType(transport));
        if (dataProfile == null) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.NO_SUITABLE_DATA_PROFILE);
        } else if (reason == DataEvaluationReason.NEW_REQUEST
                && (mDataRetryManager.isAnySetupRetryScheduled(dataProfile)
                || mDataRetryManager.isSimilarNetworkRequestRetryScheduled(networkRequest))) {
            // If this is a new request, check if there is any retry already scheduled. For all
            // other evaluation reasons, since they are all condition changes, so if there is any
            // retry scheduled, we still want to go ahead and setup the data network.
            evaluation.addDataDisallowedReason(DataDisallowedReason.RETRY_SCHEDULED);
        } else if (mDataRetryManager.isDataProfileThrottled(dataProfile)) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_THROTTLED);
        }

        if (evaluation.isDataAllowed()) {
            evaluation.setCandidateDataProfile(dataProfile);
        }

        networkRequest.setEvaluation(evaluation);
        log(evaluation.toString() + ", network type="
                + TelephonyManager.getNetworkTypeName(getDataNetworkType(transport))
                + ", reg state="
                + NetworkRegistrationInfo.registrationStateToString(
                        getDataRegistrationState(transport)));
        return evaluation;
    }

    /**
     * @return The grouped unsatisfied network requests. The network requests that have the same
     * network capabilities is grouped into one {@link NetworkRequestList}.
     */
    private @NonNull List<NetworkRequestList> getGroupedUnsatisfiedNetworkRequests() {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        for (TelephonyNetworkRequest networkRequest : mAllNetworkRequestList) {
            if (networkRequest.getState() == TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED) {
                networkRequestList.add(networkRequest);
            }
        }
        return DataUtils.getGroupedNetworkRequestList(networkRequestList);
    }

    /**
     * Called when it's needed to evaluate all unsatisfied network requests.
     *
     * @param reason The reason for evaluation.
     */
    private void onReevaluateUnsatisfiedNetworkRequests(@NonNull DataEvaluationReason reason) {
        // First, try to group similar network request together.
        List<NetworkRequestList> networkRequestLists = getGroupedUnsatisfiedNetworkRequests();
        log("Re-evaluating " + networkRequestLists.stream().mapToInt(List::size).sum()
                + " unsatisfied network requests in " + networkRequestLists.size()
                + " groups, " + networkRequestLists.stream().map(
                        requestList -> DataUtils.networkCapabilitiesToString(
                                requestList.get(0).getCapabilities()))
                .collect(Collectors.joining(", ")) + " due to " + reason);

        // Second, see if any existing network can satisfy those network requests.
        for (NetworkRequestList requestList : networkRequestLists) {
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
                    setupDataNetwork(dataProfile, null);
                }
            }
        }
    }

    /**
     * Evaluate an existing data network to see if it is still allowed to exist. For example, if
     * RAT changes from LTE to UMTS, an IMS data network is not allowed anymore. Or when SIM is
     * removal, all data networks (except emergency) should be torn down.
     *
     * @param dataNetwork The data network to evaluate.
     * @param reason The reason for evaluation.
     *
     * @return The data evaluation result.
     */
    private @NonNull DataEvaluation evaluateDataNetwork(@NonNull DataNetwork dataNetwork,
            @NonNull DataEvaluationReason reason) {
        DataEvaluation evaluation = new DataEvaluation(reason);
        // Bypass all checks for emergency data network.
        if (dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            evaluation.addDataAllowedReason(DataAllowedReason.EMERGENCY_REQUEST);
            log(evaluation.toString());
            return evaluation;
        }

        // Check SIM state
        if (mSimState != TelephonyManager.SIM_STATE_LOADED) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.SIM_NOT_READY);
        }

        // Check if data is restricted by the network.
        if (mPsRestricted) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_RESTRICTED_BY_NETWORK);
        }

        // Check if device is CDMA and is currently in ECBM
        if (mPhone.isInEcm() && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.EMERGENCY_CALL);
        }

        // Check if data is disabled
        boolean dataDisabled = false;
        if (!mDataSettingsManager.isDataEnabled()) {
            dataDisabled = true;
        }

        // Check if data roaming is disabled
        if (mPhone.getServiceState().getDataRoaming()
                && !mDataSettingsManager.isDataRoamingEnabled()) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.ROAMING_DISABLED);
        }

        // Check if current data network type is allowed by the data profile. Use the lingering
        // network type. Some data network is allowed to create on certain RATs, but can linger
        // to extended RATs. For example, IMS is allowed to be created on LTE only, but can
        // extend its life cycle to 3G.
        int networkType = getDataNetworkType(dataNetwork.getTransport());
        DataProfile dataProfile = dataNetwork.getDataProfile();
        if (dataProfile.getApnSetting() != null) {
            // Check if data is disabled for the APN type
            dataDisabled = !mDataSettingsManager.isDataEnabled(DataUtils
                    .networkCapabilityToApnType(DataUtils
                            .getHighestPriorityNetworkCapabilityFromDataProfile(
                                    mDataConfigManager, dataProfile)));

            // Sometimes network temporarily OOS and network type becomes UNKNOWN. We don't
            // tear down network in that case.
            if (networkType != TelephonyManager.NETWORK_TYPE_UNKNOWN
                    && !dataProfile.getApnSetting().canSupportLingeringNetworkType(networkType)) {
                log("networkType=" + TelephonyManager.getNetworkTypeName(networkType)
                        + ", networkTypeBitmask="
                        + dataProfile.getApnSetting().getNetworkTypeBitmask()
                        + ", lingeringNetworkTypeBitmask="
                        + dataProfile.getApnSetting().getLingeringNetworkTypeBitmask());
                evaluation.addDataDisallowedReason(
                        DataDisallowedReason.DATA_NETWORK_TYPE_NOT_ALLOWED);
            }
        }

        if (dataDisabled) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_DISABLED);
        }

        // Check if the data profile is still valid, sometimes the users can remove it from the APN
        // editor.
        if (!mDataProfileManager.isDataProfileValid(dataProfile)) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_PROFILE_INVALID);
        }

        // If users switch preferred profile in APN editor, we need to tear down network.
        if (dataNetwork.isInternetSupported()
                && !mDataProfileManager.isDataProfilePreferred(dataProfile)) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_PROFILE_NOT_PREFERRED);
        }

        if (evaluation.isDataAllowed()) {
            evaluation.addDataAllowedReason(DataAllowedReason.NORMAL);
        }

        log("Evaluated " + dataNetwork + ", " + evaluation.toString());
        return evaluation;
    }

    /**
     * Called when needed to re-evaluate existing data networks and tear down networks if needed.
     *
     * @param reason The reason for this data evaluation.
     */
    private void onReevaluateExistingDataNetworks(@NonNull DataEvaluationReason reason) {
        if (mDataNetworkList.isEmpty()) {
            log("onReevaluateExistingDataNetworks: No existing data networks to re-evaluate.");
            return;
        }
        log("Re-evaluating " + mDataNetworkList.size() + " existing data networks due to "
                + reason);
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.isConnecting() || dataNetwork.isConnected()) {
                DataEvaluation dataEvaluation = evaluateDataNetwork(dataNetwork, reason);
                if (!dataEvaluation.isDataAllowed()) {
                    tearDownGracefully(dataNetwork, getTearDownReason(dataEvaluation));
                }
            }
        }
    }

    /**
     * Evaluate if it is allowed to handover the data network between IWLAN and cellular. Some
     * carriers do not allow handover in certain conditions.
     *
     * @param dataNetwork The data network to be handover.
     * @return The evaluation result.
     *
     * @see CarrierConfigManager#KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY
     */
    private @NonNull DataEvaluation evaluateDataNetworkHandover(@NonNull DataNetwork dataNetwork) {
        DataEvaluation dataEvaluation = new DataEvaluation(DataEvaluationReason.DATA_HANDOVER);
        if (!dataNetwork.isConnecting() && !dataNetwork.isConnected()) {
            log("evaluateDataNetworkHandover:" + dataNetwork
                    + " it not in the right state.");
            dataEvaluation.addDataDisallowedReason(DataDisallowedReason.ILLEGAL_STATE);
            return dataEvaluation;
        }

        if (mDataRetryManager.isAnyHandoverRetryScheduled(dataNetwork)) {
            log("evaluateDataNetworkHandover: Found handover retry entry for "
                    + dataNetwork);
            dataEvaluation.addDataDisallowedReason(DataDisallowedReason.RETRY_SCHEDULED);
            return dataEvaluation;
        }

        List<HandoverRule> handoverRules = mDataConfigManager.getHandoverRules();

        int sourceAccessNetwork = DataUtils.networkTypeToAccessNetworkType(
                getDataNetworkType(dataNetwork.getTransport()));
        int targetAccessNetwork = DataUtils.networkTypeToAccessNetworkType(
                getDataNetworkType(DataUtils.getTargetTransport(dataNetwork.getTransport())));
        log("evaluateDataNetworkHandover: "
                + "source=" + AccessNetworkType.toString(sourceAccessNetwork)
                + ", target=" + AccessNetworkType.toString(targetAccessNetwork)
                + ", ServiceState=" + mServiceState);

        // Matching the rules by the configured order. Bail out if find first matching rule.
        for (HandoverRule rule : handoverRules) {
            // Check if the rule is only for roaming and we are not roaming.
            if (rule.isOnlyForRoaming && !mServiceState.getDataRoaming()) continue;

            if (rule.sourceAccessNetworks.contains(sourceAccessNetwork)
                    && rule.targetAccessNetworks.contains(targetAccessNetwork)) {
                log("evaluateDataNetworkHandover: Matched " + rule);
                if (rule.type == HandoverRule.RULE_TYPE_DISALLOWED) {
                    dataEvaluation.addDataDisallowedReason(
                            DataDisallowedReason.NOT_ALLOWED_BY_POLICY);
                } else {
                    dataEvaluation.addDataAllowedReason(DataAllowedReason.NORMAL);
                }
                log("evaluateDataNetworkHandover: " + dataEvaluation);
                return dataEvaluation;
            }
        }

        log("evaluateDataNetworkHandover: Did not find matching rule. " + dataEvaluation);
        // Allow handover anyway if no rule is found.
        dataEvaluation.addDataAllowedReason(DataAllowedReason.NORMAL);
        return dataEvaluation;
    }

    /**
     * Get tear down reason from the evaluation result.
     *
     * @param dataEvaluation The evaluation result from
     * {@link #evaluateDataNetwork(DataNetwork, DataEvaluationReason)}.
     * @return The tear down reason.
     */
    private @TearDownReason int getTearDownReason(@NonNull DataEvaluation dataEvaluation) {
        if (!dataEvaluation.isDataAllowed()) {
            switch (dataEvaluation.getDataDisallowedReasons().get(0)) {
                case DATA_DISABLED:
                    return DataNetwork.TEAR_DOWN_REASON_DATA_DISABLED;
                case ROAMING_DISABLED:
                    return DataNetwork.TEAR_DOWN_REASON_ROAMING_DISABLED;
                case SIM_NOT_READY:
                    return DataNetwork.TEAR_DOWN_REASON_SIM_REMOVAL;
                case CONCURRENT_VOICE_DATA_NOT_ALLOWED:
                    return DataNetwork.TEAR_DOWN_REASON_CONCURRENT_VOICE_DATA_NOT_ALLOWED;
                case DATA_RESTRICTED_BY_NETWORK:
                    return DataNetwork.TEAR_DOWN_REASON_DATA_RESTRICTED_BY_NETWORK;
                case RADIO_DISABLED_BY_CARRIER:
                    return DataNetwork.TEAR_DOWN_REASON_POWER_OFF_BY_CARRIER;
                case RADIO_POWER_OFF:
                    return DataNetwork.TEAR_DOWN_REASON_AIRPLANE_MODE_ON;
                case DATA_SERVICE_NOT_READY:
                    return DataNetwork.TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY;
                case DATA_NETWORK_TYPE_NOT_ALLOWED:
                    return DataNetwork.TEAR_DOWN_REASON_RAT_NOT_ALLOWED;
            }
        }
        return 0;
    }

    /**
     * Remove a network request, which is originated from the apps. Note that remove a network
     * will not result in tearing down the network. The tear down request directly comes from
     * {@link com.android.server.ConnectivityService} through
     * {@link NetworkAgent#onNetworkUnwanted()}.
     *
     * @param networkRequest Network request
     */
    public void removeNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        sendMessage(obtainMessage(EVENT_REMOVE_NETWORK_REQUEST, networkRequest));
    }

    private void onRemoveNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
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
     * Register for IMS feature registration state.
     *
     * @param subId The subscription index.
     * @param imsFeature The IMS feature. Only {@link ImsFeature#FEATURE_MMTEL} and
     * {@link ImsFeature#FEATURE_RCS} are supported at this point.
     */
    private void registerImsFeatureRegistrationState(int subId,
            @ImsFeature.FeatureType int imsFeature) {
        RegistrationManager.RegistrationCallback callback =
                new RegistrationManager.RegistrationCallback() {
                    @Override
                    public void onRegistered(ImsRegistrationAttributes attributes) {
                        log("IMS " + DataUtils.imsFeatureToString(imsFeature)
                                + " registered. Attributes=" + attributes);
                        mRegisteredImsFeatures.add(imsFeature);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        log("IMS " + DataUtils.imsFeatureToString(imsFeature)
                                + " deregistered. Info=" + info);
                        mRegisteredImsFeatures.remove(imsFeature);
                        evaluatePendingImsDeregDataNetworks();
                    }
                };

        try {
            // Use switch here as we can't make a generic callback registration logic because
            // RcsManager does not implement RegistrationManager.
            switch (imsFeature) {
                case ImsFeature.FEATURE_MMTEL:
                    mImsManager.getImsMmTelManager(subId).registerImsRegistrationCallback(
                            DataNetworkController.this::post, callback);
                    break;
                case ImsFeature.FEATURE_RCS:
                    mImsManager.getImsRcsManager(subId).registerImsRegistrationCallback(
                            DataNetworkController.this::post, callback);
                    break;
            }

            // Store the callback so that we can unregister in the future.
            mImsFeatureRegistrationCallbacks.put(imsFeature, callback);
            log("Successfully register " + DataUtils.imsFeatureToString(imsFeature)
                    + " registration state. subId=" + subId);
        } catch (ImsException e) {
            loge("updateImsFeatureRegistrationStateListening: subId=" + subId
                    + ", imsFeature=" + DataUtils.imsFeatureToString(imsFeature) + ", " + e);
        }
    }

    /**
     * Unregister IMS feature callback.
     *
     * @param subId The subscription index.
     * @param imsFeature The IMS feature. Only {@link ImsFeature#FEATURE_MMTEL} and
     * {@link ImsFeature#FEATURE_RCS} are supported at this point.
     */
    private void unregisterImsFeatureRegistrationState(int subId,
            @ImsFeature.FeatureType int imsFeature) {
        RegistrationManager.RegistrationCallback oldCallback =
                mImsFeatureRegistrationCallbacks.get(imsFeature);
        if (oldCallback != null) {
            if (imsFeature == ImsFeature.FEATURE_MMTEL) {
                mImsManager.getImsMmTelManager(subId)
                        .unregisterImsRegistrationCallback(oldCallback);
            } else if (imsFeature == ImsFeature.FEATURE_RCS) {
                mImsManager.getImsRcsManager(subId)
                        .unregisterImsRegistrationCallback(oldCallback);
            }
            log("Successfully unregistered " + DataUtils.imsFeatureToString(imsFeature)
                    + " registration state. sudId=" + subId);
            mImsFeatureRegistrationCallbacks.remove(imsFeature);
        }
    }

    /**
     * Register IMS state callback.
     *
     * @param subId Subscription index.
     */
    private void registerImsStateCallback(int subId) {
        Function<Integer, ImsStateCallback> imsFeatureStateCallbackFactory =
                imsFeature -> new ImsStateCallback() {
                    @Override
                    public void onUnavailable(int reason) {
                        // Unregister registration state update when IMS service is unbound.
                        unregisterImsFeatureRegistrationState(subId, imsFeature);
                    }

                    @Override
                    public void onAvailable() {
                        mImsFeaturePackageName.put(imsFeature, ImsResolver.getInstance()
                                .getConfiguredImsServicePackageName(mPhone.getPhoneId(),
                                        imsFeature));
                        // Once IMS service is bound, register for registration state update.
                        registerImsFeatureRegistrationState(subId, imsFeature);
                    }

                    @Override
                    public void onError() {
                    }
                };

        try {
            ImsStateCallback callback = imsFeatureStateCallbackFactory
                    .apply(ImsFeature.FEATURE_MMTEL);
            mImsManager.getImsMmTelManager(subId).registerImsStateCallback(this::post,
                    callback);
            mImsStateCallbacks.put(ImsFeature.FEATURE_MMTEL, callback);
            log("Successfully register MMTEL state on sub " + subId);

            callback = imsFeatureStateCallbackFactory.apply(ImsFeature.FEATURE_RCS);
            mImsManager.getImsRcsManager(subId).registerImsStateCallback(this::post, callback);
            mImsStateCallbacks.put(ImsFeature.FEATURE_RCS, callback);
            log("Successfully register RCS state on sub " + subId);
        } catch (ImsException e) {
            loge("Exception when registering IMS state callback. " + e);
        }
    }

    /**
     * Unregister IMS faeture state callbacks.
     *
     * @param subId Subscription index.
     */
    private void unregisterImsStateCallbacks(int subId) {
        ImsStateCallback callback = mImsStateCallbacks.get(ImsFeature.FEATURE_MMTEL);
        if (callback != null) {
            mImsManager.getImsMmTelManager(subId).unregisterImsStateCallback(callback);
            mImsStateCallbacks.remove(ImsFeature.FEATURE_MMTEL);
            log("Unregister MMTEL state on sub " + subId);
        }

        callback = mImsStateCallbacks.get(ImsFeature.FEATURE_RCS);
        if (callback != null) {
            mImsManager.getImsRcsManager(subId).unregisterImsStateCallback(callback);
            mImsStateCallbacks.remove(ImsFeature.FEATURE_RCS);
            log("Unregister RCS state on sub " + subId);
        }
    }

    /** Called when subscription info changed. */
    private void onSubscriptionChanged() {
        if (mSubId != mPhone.getSubId()) {
            log("onDataConfigUpdated: mSubId changed from " + mSubId + " to "
                    + mPhone.getSubId());
            if (isImsGracefulTearDownSupported()) {
                if (SubscriptionManager.isValidSubscriptionId(mPhone.getSubId())) {
                    registerImsStateCallback(mPhone.getSubId());
                } else {
                    unregisterImsStateCallbacks(mSubId);
                }
            }
            mSubId = mPhone.getSubId();
            updateSubscriptionPlans();
        }
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
     * Find unsatisfied network requests that can be satisfied by the given data profile.
     *
     * @param dataProfile The data profile.
     * @return The network requests list.
     */
    private @NonNull NetworkRequestList findSatisfiableNetworkRequests(
            @NonNull DataProfile dataProfile) {
        return new NetworkRequestList(mAllNetworkRequestList.stream()
                .filter(request -> request.getState()
                        == TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED)
                .filter(request -> dataProfile.canSatisfy(request.getCapabilities()))
                .collect(Collectors.toList()));
    }

    /**
     * Setup data network.
     *
     * @param dataProfile The data profile to setup the data network.
     * @param dataSetupRetryEntry Data retry entry. {@code null} if this data network setup is not
     * initiated by a data retry.
     */
    private void setupDataNetwork(@NonNull DataProfile dataProfile,
            @Nullable DataSetupRetryEntry dataSetupRetryEntry) {
        log("onSetupDataNetwork: dataProfile=" + dataProfile + ", retryEntry="
                + dataSetupRetryEntry + ", service state=" + mServiceState);
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.getDataProfile().equals(dataProfile)) {
                log("onSetupDataNetwork: Found existing data network " + dataNetwork
                        + " has the same data profile.");
                if (dataSetupRetryEntry != null) {
                    dataSetupRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
                }
                return;
            }
        }

        NetworkRequestList networkRequestList = findSatisfiableNetworkRequests(dataProfile);

        if (networkRequestList.isEmpty()) {
            log("Can't find any unsatisfied network requests that can be satisfied by this data "
                    + "profile.");
            if (dataSetupRetryEntry != null) {
                dataSetupRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            }

            return;
        }

        int transport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkRequestList.get(0).getApnTypeNetworkCapability());
        logl("Creating data network on "
                + AccessNetworkConstants.transportTypeToString(transport) + " with " + dataProfile
                + ", and attaching " + networkRequestList.size() + " network requests to it.");

        mDataNetworkList.add(new DataNetwork(mPhone, getLooper(), mDataServiceManagers,
                dataProfile, networkRequestList, transport, new DataNetworkCallback(this::post) {
                    @Override
                    public void onSetupDataFailed(@NonNull DataNetwork dataNetwork,
                            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                            long retryDelayMillis) {
                        if (dataSetupRetryEntry != null) {
                            dataSetupRetryEntry.setState(DataRetryEntry.RETRY_STATE_FAILED);
                        }
                        DataNetworkController.this.onDataNetworkSetupFailed(
                                dataNetwork, requestList, cause, retryDelayMillis);
                    }

                    @Override
                    public void onConnected(@NonNull DataNetwork dataNetwork) {
                        if (dataSetupRetryEntry != null) {
                            dataSetupRetryEntry.setState(DataRetryEntry.RETRY_STATE_SUCCEEDED);
                        }
                        DataNetworkController.this.onDataNetworkConnected(dataNetwork);
                    }

                    @Override
                    public void onAttachFailed(@NonNull DataNetwork dataNetwork,
                            @NonNull NetworkRequestList requestList) {
                        DataNetworkController.this.onAttachNetworkRequestsFailed(
                                dataNetwork, requestList);
                    }

                    @Override
                    public void onValidationStatusChanged(@NonNull DataNetwork dataNetwork,
                            @ValidationStatus int status, @Nullable Uri redirectUri) {
                        DataNetworkController.this.onDataNetworkValidationStatusChanged(
                                dataNetwork, status, redirectUri);
                    }

                    @Override
                    public void onSuspendedStateChanged(@NonNull DataNetwork dataNetwork,
                            boolean suspended) {
                        DataNetworkController.this.onDataNetworkSuspendedStateChanged(
                                dataNetwork, suspended);
                    }

                    @Override
                    public void onDisconnected(@NonNull DataNetwork dataNetwork,
                            @DataFailureCause int cause) {
                        DataNetworkController.this.onDataNetworkDisconnected(
                                dataNetwork, cause);
                    }

                    @Override
                    public void onHandoverSucceeded(@NonNull DataNetwork dataNetwork) {
                        DataNetworkController.this.onDataNetworkHandoverSucceeded(dataNetwork);
                    }

                    @Override
                    public void onHandoverFailed(@NonNull DataNetwork dataNetwork,
                            @DataFailureCause int cause, long retryDelayMillis,
                            @HandoverFailureMode int handoverFailureMode) {
                        DataNetworkController.this.onDataNetworkHandoverFailed(
                                dataNetwork, cause, retryDelayMillis, handoverFailureMode);
                    }

                    @Override
                    public void onLinkStatusChanged(@NonNull DataNetwork dataNetwork,
                            @LinkStatus int linkStatus) {
                        DataNetworkController.this.onLinkStatusChanged(dataNetwork, linkStatus);
                    }

                    @Override
                    public void onPcoDataChanged(@NonNull DataNetwork dataNetwork) {
                        DataNetworkController.this.onPcoDataChanged(dataNetwork);
                    }
                }));
        if (!mAnyDataNetworkExisting) {
            mAnyDataNetworkExisting = true;
            mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                    () -> callback.onAnyDataNetworkExistingChanged(mAnyDataNetworkExisting)));
        }
    }

    /**
     * Called when setup data network failed.
     *
     * @param dataNetwork The data network.
     * @param requestList The network requests attached to the data network.
     * @param cause The fail cause
     * @param retryDelayMillis The retry timer suggested by the network/data service.
     */
    private void onDataNetworkSetupFailed(@NonNull DataNetwork dataNetwork,
            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
            long retryDelayMillis) {
        logl("onDataNetworkSetupDataFailed: " + dataNetwork + ", cause="
                + DataFailCause.toString(cause) + "(0x" + Integer.toHexString(cause)
                + "), retryDelayMillis=" + retryDelayMillis + "ms.");
        mDataNetworkList.remove(dataNetwork);
        if (mAnyDataNetworkExisting && mDataNetworkList.isEmpty()) {
            mAnyDataNetworkExisting = false;
            mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                    () -> callback.onAnyDataNetworkExistingChanged(mAnyDataNetworkExisting)));
        }
        // Data retry manager will determine if retry is needed. If needed, retry will be scheduled.
        mDataRetryManager.evaluateDataSetupRetry(dataNetwork.getDataProfile(),
                dataNetwork.getTransport(), requestList, cause, retryDelayMillis);
    }

    /**
     * Called when data network is connected.
     *
     * @param dataNetwork The data network.
     */
    private void onDataNetworkConnected(@NonNull DataNetwork dataNetwork) {
        logl("onDataNetworkConnected: " + dataNetwork);
        mPreviousConnectedDataNetworkList.add(0, dataNetwork);
        // Preserve the connected data networks for debugging purposes.
        if (mPreviousConnectedDataNetworkList.size() > MAX_HISTORICAL_CONNECTED_DATA_NETWORKS) {
            mPreviousConnectedDataNetworkList.remove(MAX_HISTORICAL_CONNECTED_DATA_NETWORKS);
        }

        updateOverallInternetDataState();

        if (dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)) {
            logl("IMS data state changed from "
                    + TelephonyUtils.dataStateToString(mImsDataNetworkState) + " to CONNECTED.");
            mImsDataNetworkState = TelephonyManager.DATA_CONNECTED;
        }
    }

    /**
     * Called when needed to retry data setup.
     *
     * @param dataSetupRetryEntry The data setup retry entry scheduled by {@link DataRetryManager}.
     */
    private void onDataNetworkSetupRetry(@NonNull DataSetupRetryEntry dataSetupRetryEntry) {
        TelephonyNetworkRequest telephonyNetworkRequest =
                dataSetupRetryEntry.networkRequestList.get(0);
        int networkCapability = telephonyNetworkRequest.getApnTypeNetworkCapability();
        int preferredTransport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkCapability);
        if (preferredTransport != dataSetupRetryEntry.transport) {
            log("Cannot re-satisfy " + telephonyNetworkRequest + " on "
                    + AccessNetworkConstants.transportTypeToString(dataSetupRetryEntry.transport)
                    + ". The preferred transport has switched to "
                    + AccessNetworkConstants.transportTypeToString(preferredTransport)
                    + ". " + dataSetupRetryEntry);
            dataSetupRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return;
        }

        DataEvaluation evaluation = evaluateNetworkRequest(
                telephonyNetworkRequest, DataEvaluationReason.DATA_RETRY);
        if (evaluation.isDataAllowed()) {
            DataProfile dataProfile = dataSetupRetryEntry.dataProfile;
            if (dataProfile == null) {
                dataProfile = evaluation.getCandidateDataProfile();
            }
            if (dataProfile != null) {
                setupDataNetwork(dataProfile, dataSetupRetryEntry);
            } else {
                loge("onDataNetworkSetupRetry: Not able to find a suitable data profile to retry.");
            }
        }
    }

    /**
     * Called when needed to retry data network handover.
     *
     * @param dataHandoverRetryEntry The handover entry.
     */
    private void onDataNetworkHandoverRetry(
            @NonNull DataHandoverRetryEntry dataHandoverRetryEntry) {
        DataNetwork dataNetwork = dataHandoverRetryEntry.dataNetwork;
        if (!mDataNetworkList.contains(dataNetwork)) {
            log("onDataNetworkHandoverRetry: " + dataNetwork + " no longer exists.");
            dataHandoverRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return;
        }

        if (!dataNetwork.isConnected()) {
            log("onDataNetworkHandoverRetry: " + dataNetwork + " is not in the right state.");
            dataHandoverRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return;
        }

        int preferredTransport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                dataNetwork.getHighestPriorityNetworkCapability());
        if (dataNetwork.getTransport() == preferredTransport) {
            log("onDataNetworkHandoverRetry: " + dataNetwork + " is already on the preferred "
                    + "transport " + AccessNetworkConstants.transportTypeToString(
                            preferredTransport) + ".");
            dataHandoverRetryEntry.setState(DataRetryEntry.RETRY_STATE_CANCELLED);
            return;
        }

        logl("Start handover " + dataNetwork + " to "
                + AccessNetworkConstants.transportTypeToString(preferredTransport)
                + ", " + dataHandoverRetryEntry);
        dataNetwork.startHandover(preferredTransport, dataHandoverRetryEntry);
    }

    /**
     * Called when data network validation status changed.
     *
     * @param status one of {@link NetworkAgent#VALIDATION_STATUS_VALID} or
     * {@link NetworkAgent#VALIDATION_STATUS_NOT_VALID}.
     * @param redirectUri If internet connectivity is being redirected (e.g., on a captive portal),
     * this is the destination the probes are being redirected to, otherwise {@code null}.
     *
     * @param dataNetwork The data network.
     */
    private void onDataNetworkValidationStatusChanged(@NonNull DataNetwork dataNetwork,
            @ValidationStatus int status, @Nullable Uri redirectUri) {
        log("onDataNetworkValidationStatusChanged: " + dataNetwork + ", validation status="
                + DataUtils.validationStatusToString(status)
                + (redirectUri != null ? ", " + redirectUri : ""));
        if (!TextUtils.isEmpty(redirectUri.toString())) {
            Intent intent = new Intent(TelephonyManager.ACTION_CARRIER_SIGNAL_REDIRECTED);
            intent.putExtra(TelephonyManager.EXTRA_REDIRECTION_URL, redirectUri);
            mPhone.getCarrierSignalAgent().notifyCarrierSignalReceivers(intent);
            log("Notify carrier signal receivers with redirectUri: " + redirectUri);
        }

        if (status != NetworkAgent.VALIDATION_STATUS_VALID
                && status != NetworkAgent.VALIDATION_STATUS_NOT_VALID) {
            loge("Invalid validation status " + status + " received.");
            return;
        }

        // TODO: Add DataConfigManager.isRecoveryOnBadNetworkEnabled()
        if (dataNetwork.isInternetSupported()) {
            mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                    () -> callback.onInternetDataNetworkValidationStatusChanged(status)));
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
        updateOverallInternetDataState();

        if (dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)) {
            logl("IMS data state changed from "
                    + TelephonyUtils.dataStateToString(mImsDataNetworkState) + " to "
                    + (suspended ? "SUSPENDED" : "CONNECTED"));
            mImsDataNetworkState = suspended
                    ? TelephonyManager.DATA_SUSPENDED : TelephonyManager.DATA_CONNECTED;
        }
    }

    /**
     * Called when data network disconnected.
     *
     * @param dataNetwork The data network.
     * @param cause The disconnect cause.
     */
    private void onDataNetworkDisconnected(@NonNull DataNetwork dataNetwork,
            @DataFailureCause int cause) {
        logl("onDataNetworkDisconnected: " + dataNetwork + ", cause="
                + DataFailCause.toString(cause) + "(" + cause + ")");
        mDataNetworkList.remove(dataNetwork);
        mPendingImsDeregDataNetworks.remove(dataNetwork);
        mDataRetryManager.cancelPendingHandoverRetry(dataNetwork);
        updateOverallInternetDataState();

        if (dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)) {
            logl("IMS data state changed from "
                    + TelephonyUtils.dataStateToString(mImsDataNetworkState) + " to DISCONNECTED.");
            mImsDataNetworkState = TelephonyManager.DATA_DISCONNECTED;
        }

        if (mAnyDataNetworkExisting && mDataNetworkList.isEmpty()) {
            log("All data networks disconnected now.");
            mAnyDataNetworkExisting = false;
            mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                    () -> callback.onAnyDataNetworkExistingChanged(mAnyDataNetworkExisting)));
        }

        // Sometimes network was unsolicitedly reported lost for reasons. We should re-evaluate
        // and see if data network can be re-established again.
        sendMessageDelayed(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                DataEvaluationReason.RETRY_AFTER_DISCONNECTED),
                mDataConfigManager.getRetrySetupAfterDisconnectMillis());
    }

    /**
     * Called when handover between IWLAN and cellular network succeeded.
     *
     * @param dataNetwork The data network.
     */
    private void onDataNetworkHandoverSucceeded(@NonNull DataNetwork dataNetwork) {
        logl("Successfully handover " + dataNetwork + " to "
                + AccessNetworkConstants.transportTypeToString(dataNetwork.getTransport()));
    }

    /**
     * Called when data network handover between IWLAN and cellular network failed.
     *
     * @param dataNetwork The data network.
     * @param cause The fail cause.
     * @param retryDelayMillis Network suggested retry time in milliseconds.
     * {@link Long#MAX_VALUE} indicates data retry should not occur.
     * {@link DataCallResponse#RETRY_DURATION_UNDEFINED} indicates network did not suggest any
     * retry duration.
     * @param handoverFailureMode The handover failure mode that determine the behavior of
     * how frameworks should handle the handover failure.
     */
    private void onDataNetworkHandoverFailed(@NonNull DataNetwork dataNetwork,
            @DataFailureCause int cause, long retryDelayMillis,
            @HandoverFailureMode int handoverFailureMode) {
        logl("onDataNetworkHandoverFailed: " + dataNetwork + ", cause="
                + DataFailCause.toString(cause) + "(0x" + Integer.toHexString(cause)
                + "), retryDelayMillis=" + retryDelayMillis + "ms, handoverFailureMode="
                + DataCallResponse.failureModeToString(handoverFailureMode));
        if (handoverFailureMode == DataCallResponse.HANDOVER_FAILURE_MODE_DO_FALLBACK
                || (handoverFailureMode == DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY
                && cause == DataFailCause.HANDOFF_PREFERENCE_CHANGED)) {
            // Don't retry handover anymore. Give QNS some time to switch the preferred transport
            // to the original one, but we should re-evaluate the preferred transport again to
            // make sure QNS does change it back, if not, we still need to perform handover at that
            // time.
            sendMessageDelayed(obtainMessage(EVENT_PREFERRED_TRANSPORT_CHANGED,
                    dataNetwork.getHighestPriorityNetworkCapability(), 0),
                    REEVALUATE_PREFERRED_TRANSPORT_DELAY_MILLIS);
        } else if (handoverFailureMode == DataCallResponse
                .HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_SETUP_NORMAL || handoverFailureMode
                == DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY) {
            int targetTransport = DataUtils.getTargetTransport(dataNetwork.getTransport());
            mDataRetryManager.evaluateDataSetupRetry(dataNetwork.getDataProfile(), targetTransport,
                    dataNetwork.getAttachedNetworkRequestList(), cause, retryDelayMillis);
            // Tear down the data network on source transport. Retry manager will schedule
            // setup a new data network on the target transport.
            tearDownGracefully(dataNetwork, DataNetwork.TEAR_DOWN_REASON_HANDOVER_FAILED);
        } else {
            mDataRetryManager.evaluateDataHandoverRetry(dataNetwork, cause, retryDelayMillis);
        }
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
     * Called when data stall occurs and needed to tear down / setup a new data network for
     * internet. This event is from {@link DataStallRecoveryManager}.
     */
    private void onDataStallReestablishInternet() {
        log("onDataStallReestablishInternet: Tear down data networks that support internet.");
        // Tear down all data networks that support internet. After data disconnected, unsatisfied
        // network requests will be re-evaluate again and data network controller will attempt to
        // setup data networks to satisfy them.
        mDataNetworkList.stream()
                .filter(DataNetwork::isInternetSupported)
                .forEach(dataNetwork -> dataNetwork.tearDown(
                        DataNetwork.TEAR_DOWN_REASON_DATA_STALL));
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
                if (!mDataConfigManager.shouldPersistIwlanDataNetworksWhenDataServiceRestarted()) {
                    for (DataNetwork dataNetwork : mDataNetworkList) {
                        if (dataNetwork.getTransport()
                                == AccessNetworkConstants.TRANSPORT_TYPE_WLAN) {
                            dataNetwork.tearDown(
                                    DataNetwork.TEAR_DOWN_REASON_DATA_SERVICE_NOT_READY);
                        }
                    }
                }
            }
        } else {
            mDataRetryManager.reset();
        }
        mDataServiceBound.put(transport, bound);
    }

    /**
     * Called when SIM is absent.
     */
    private void onSimAbsent() {
        log("onSimAbsent");
        sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                DataEvaluationReason.SIM_REMOVAL));
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
     * Called when preferred transport changed for certain capability.
     *
     * @param capability The network capability that has preferred transport changed.
     */
    private void onPreferredTransportChanged(@NetCapability int capability) {
        int preferredTransport = mAccessNetworksManager
                .getPreferredTransportByNetworkCapability(capability);
        logl("onPreferredTransportChanged: " + DataUtils.networkCapabilityToString(capability)
                + " preferred on "
                + AccessNetworkConstants.transportTypeToString(preferredTransport));
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.getHighestPriorityNetworkCapability() == capability) {
                // Check if the data network's current transport is different than from the
                // preferred transport. If it's different, then handover is needed.
                if (dataNetwork.getTransport() == preferredTransport) {
                    log("onPreferredTransportChanged:" + dataNetwork + " already on "
                            + AccessNetworkConstants.transportTypeToString(preferredTransport));
                    continue;
                }

                DataEvaluation dataEvaluation = evaluateDataNetworkHandover(dataNetwork);
                if (dataEvaluation.isDataAllowed()) {
                    logl("Start handover " + dataNetwork + " to "
                            + AccessNetworkConstants.transportTypeToString(preferredTransport));
                    dataNetwork.startHandover(preferredTransport, null);
                } else if (dataEvaluation.containsOnly(
                        DataDisallowedReason.NOT_ALLOWED_BY_POLICY)) {
                    logl("onPreferredTransportChanged: Handover not allowed by policy. Tear "
                            + "down the network so a new network can be setup on "
                            + AccessNetworkConstants.transportTypeToString(preferredTransport)
                            + ".");
                    tearDownGracefully(dataNetwork,
                            DataNetwork.TEAR_DOWN_REASON_HANDOVER_NOT_ALLOWED);
                }
            }
        }
    }

    /**
     * Update {@link SubscriptionPlan}s from {@link NetworkPolicyManager}.
     */
    private void updateSubscriptionPlans() {
        SubscriptionPlan[] plans = mNetworkPolicyManager.getSubscriptionPlans(
                mSubId, mPhone.getContext().getOpPackageName());
        mSubscriptionPlans.clear();
        mSubscriptionPlans.addAll(plans != null ? Arrays.asList(plans) : Collections.emptyList());
        mCongestedOverrideNetworkTypes.clear();
        mUnmeteredOverrideNetworkTypes.clear();
        log("Subscription plans initialized: " + mSubscriptionPlans);
    }

    /**
     * Called when data network's link status changed.
     *
     * @param dataNetwork The data network that has link status changed.
     * @param linkStatus The link status (i.e. RRC state).
     */
    private void onLinkStatusChanged(@NonNull DataNetwork dataNetwork, @LinkStatus int linkStatus) {
        // TODO: Since this is only used for 5G icon display logic, so we only use internet data
        //   data network's link status. Consider expanding to all data networks if needed, and
        //   should use CarrierConfigManager.KEY_LTE_ENDC_USING_USER_DATA_FOR_RRC_DETECTION_BOOL
        //   to determine if using all data networks or only internet data networks.
        boolean anyActive = mDataNetworkList.stream()
                .filter(DataNetwork::isInternetSupported)
                .anyMatch(network -> network.getLinkStatus()
                        == DataCallResponse.LINK_STATUS_ACTIVE);
        int status = anyActive ? DataCallResponse.LINK_STATUS_ACTIVE
                : DataCallResponse.LINK_STATUS_DORMANT;
        if (mInternetLinkStatus != status) {
            log("Internet link status changed to " + DataUtils.linkStatusToString(status));
            mInternetLinkStatus = status;
            mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                    () -> callback.onPhysicalLinkStatusChanged(mInternetLinkStatus)));
        }
    }

    /**
     * Called when PCO data changed.
     *
     * @param dataNetwork The data network.
     */
    private void onPcoDataChanged(@NonNull DataNetwork dataNetwork) {
        // Check if any data network is using NR advanced bands.
        int nrAdvancedPcoId = mDataConfigManager.getNrAdvancedCapablePcoId();
        if (nrAdvancedPcoId != 0) {
            boolean nrAdvancedCapableByPco = false;
            for (DataNetwork network : mDataNetworkList) {
                PcoData pcoData = network.getPcoData().get(nrAdvancedPcoId);
                if (pcoData != null && pcoData.contents.length > 0
                        && pcoData.contents[pcoData.contents.length - 1] == 1) {
                    nrAdvancedCapableByPco = true;
                    break;
                }
            }

            if (nrAdvancedCapableByPco != mNrAdvancedCapableByPco) {
                log("onPcoDataChanged: mNrAdvancedCapableByPco = " + mNrAdvancedCapableByPco);
                mNrAdvancedCapableByPco = nrAdvancedCapableByPco;
                mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                        () -> callback.onNrAdvancedCapableByPcoChanged(mNrAdvancedCapableByPco)));
            }
        }
    }

    /**
     * Check if needed to re-evaluate the existing data networks.
     *
     * @param oldNri Previous network registration info.
     * @param newNri Current network registration info.
     * @return {@code true} if needed to re-evaluate the existing data networks.
     */
    private boolean shouldReevaluateDataNetworks(@Nullable NetworkRegistrationInfo oldNri,
            @Nullable NetworkRegistrationInfo newNri) {
        if (oldNri == null || newNri == null) return false;
        if (newNri.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            // Sometimes devices temporarily lose signal and RAT becomes unknown. We don't tear
            // down data network in this case.
            return false;
        }

        if (oldNri.getAccessNetworkTechnology() != newNri.getAccessNetworkTechnology()
                || (!oldNri.isRoaming() && newNri.isRoaming())) {
            return true;
        }

        return false;
    }

    /**
     * Check if needed to re-evaluate the unsatisfied network requests.
     *
     * @param oldNri Previous network registration info.
     * @param newNri Current network registration info.
     * @return {@code true} if needed to re-evaluate the unsatisfied network requests.
     */
    private boolean shouldReevaluateNetworkRequests(@Nullable NetworkRegistrationInfo oldNri,
            @Nullable NetworkRegistrationInfo newNri) {
        if (newNri == null) return false;
        if (newNri.getAccessNetworkTechnology() == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
            // Sometimes devices temporarily lose signal and RAT becomes unknown. We don't setup
            // data in this case.
            return false;
        }

        if (oldNri == null
                || oldNri.getAccessNetworkTechnology() != newNri.getAccessNetworkTechnology()
                || (!oldNri.isInService() && newNri.isInService())) {
            return true;
        }

        return false;
    }

    /**
     * Called when service state changed.
     */
    // Note that this is only called when data RAT or data registration changed. If we need to know
    // more "changed" events other than data RAT and data registration state, we should add
    // a new listening ServiceStateTracker.registerForServiceStateChanged().
    private void onServiceStateChanged() {
        // Use the raw service state instead of the mPhone.getServiceState().
        ServiceState newServiceState = mPhone.getServiceStateTracker().getServiceState();
        logv("onServiceStateChanged: " + newServiceState);
        StringBuilder debugMessage = new StringBuilder("onServiceStateChanged: ");
        boolean evaluateNetworkRequests = false, evaluateDataNetworks = false;

        if (!mServiceState.equals(newServiceState)) {
            for (int transport : mAccessNetworksManager.getAvailableTransports()) {
                NetworkRegistrationInfo oldNri = mServiceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS, transport);
                NetworkRegistrationInfo newNri = newServiceState.getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS, transport);
                debugMessage.append("[").append(
                        AccessNetworkConstants.transportTypeToString(transport)).append(": ");
                debugMessage.append(oldNri != null ? TelephonyManager.getNetworkTypeName(
                        oldNri.getAccessNetworkTechnology()) : null);
                debugMessage.append("->").append(
                        newNri != null ? TelephonyManager.getNetworkTypeName(
                                newNri.getAccessNetworkTechnology()) : null).append(", ");
                debugMessage.append(
                        oldNri != null ? NetworkRegistrationInfo.registrationStateToString(
                                oldNri.getRegistrationState()) : null);
                debugMessage.append("->").append(newNri != null
                        ? NetworkRegistrationInfo.registrationStateToString(
                        newNri.getRegistrationState()) : null).append("] ");
                if (shouldReevaluateDataNetworks(oldNri, newNri)) {
                    if (!hasMessages(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS)) {
                        sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                                DataEvaluationReason.DATA_SERVICE_STATE_CHANGED));
                        evaluateDataNetworks = true;
                    }
                }
                if (shouldReevaluateNetworkRequests(oldNri, newNri)) {
                    if (!hasMessages(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS)) {
                        sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                                DataEvaluationReason.DATA_SERVICE_STATE_CHANGED));
                        evaluateNetworkRequests = true;
                    }
                }
            }
            mServiceState = newServiceState;
        } else {
            debugMessage.append("not changed");
        }
        debugMessage.append(". Evaluating network requests is ").append(
                evaluateNetworkRequests ? "" : "not ").append(
                "needed, evaluating existing data networks is ").append(
                evaluateDataNetworks ? "" : "not ").append("needed.");
        log(debugMessage.toString());
    }

    /**
     * Update the internet data network state. For now only {@link TelephonyManager#DATA_CONNECTED}
     * , {@link TelephonyManager#DATA_SUSPENDED}, and
     * {@link TelephonyManager#DATA_DISCONNECTED} are supported.
     */
    private void updateOverallInternetDataState() {
        boolean anyInternetConnected = mDataNetworkList.stream()
                .anyMatch(dataNetwork -> dataNetwork.isInternetSupported()
                        && (dataNetwork.isConnected() || dataNetwork.isHandoverInProgress()));
        // If any one is not suspended, then the overall is not suspended.
        List<DataNetwork> allConnectedInternetDataNetworks = mDataNetworkList.stream()
                .filter(DataNetwork::isInternetSupported)
                .filter(dataNetwork -> dataNetwork.isConnected()
                        || dataNetwork.isHandoverInProgress())
                .collect(Collectors.toList());
        boolean isSuspended = !allConnectedInternetDataNetworks.isEmpty()
                && allConnectedInternetDataNetworks.stream().allMatch(DataNetwork::isSuspended);
        logv("isSuspended=" + isSuspended + ", anyInternetConnected=" + anyInternetConnected
                + ", mDataNetworkList=" + mDataNetworkList);

        int dataNetworkState = TelephonyManager.DATA_DISCONNECTED;
        if (isSuspended) {
            dataNetworkState = TelephonyManager.DATA_SUSPENDED;
        } else if (anyInternetConnected) {
            dataNetworkState = TelephonyManager.DATA_CONNECTED;
        }

        if (mInternetDataNetworkState != dataNetworkState) {
            logl("Internet data state changed from "
                    + TelephonyUtils.dataStateToString(mInternetDataNetworkState) + " to "
                    + TelephonyUtils.dataStateToString(dataNetworkState) + ".");
            // TODO: Create a new route to notify TelephonyRegistry.
            if (dataNetworkState == TelephonyManager.DATA_CONNECTED) {
                mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                        () -> callback.onInternetDataNetworkConnected(
                                allConnectedInternetDataNetworks.stream()
                                        .map(DataNetwork::getDataProfile)
                                        .collect(Collectors.toList()))));
            } else if (dataNetworkState == TelephonyManager.DATA_DISCONNECTED) {
                mDataNetworkControllerCallbacks.forEach(callback -> callback.invokeFromExecutor(
                        callback::onInternetDataNetworkDisconnected));
            } // TODO: Add suspended callback if needed.
            mInternetDataNetworkState = dataNetworkState;
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
     * @return Data settings manager instance.
     */
    public @NonNull DataSettingsManager getDataSettingsManager() {
        return mDataSettingsManager;
    }

    /**
     * @return Data retry manager instance.
     */
    public @NonNull DataRetryManager getDataRetryManager() {
        return mDataRetryManager;
    }

    /**
     * @return The list of SubscriptionPlans
     */
    @VisibleForTesting
    public @NonNull List<SubscriptionPlan> getSubscriptionPlans() {
        return mSubscriptionPlans;
    }

    /**
     * @return The set of network types an unmetered override applies to
     */
    @VisibleForTesting
    public @NonNull @NetworkType Set<Integer> getUnmeteredOverrideNetworkTypes() {
        return mUnmeteredOverrideNetworkTypes;
    }

    /**
     * @return The set of network types a congested override applies to
     */
    @VisibleForTesting
    public @NonNull @NetworkType Set<Integer> getCongestedOverrideNetworkTypes() {
        return mCongestedOverrideNetworkTypes;
    }

    /**
     * Get data network type based on transport.
     *
     * @param transport The transport.
     * @return The current network type.
     */
    private @NetworkType int getDataNetworkType(@TransportType int transport) {
        NetworkRegistrationInfo nrs = mServiceState.getNetworkRegistrationInfo(
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
        NetworkRegistrationInfo nrs = mServiceState.getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, transport);
        if (nrs != null) {
            return nrs.getRegistrationState();
        }
        return NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
    }

    /**
     * Register data network controller callback.
     *
     * @param callback The callback.
     */
    public void registerDataNetworkControllerCallback(
            @NonNull DataNetworkControllerCallback callback) {
        sendMessage(obtainMessage(EVENT_REGISTER_DATA_NETWORK_CONTROLLER_CALLBACK, callback));
    }

    /**
     * Unregister data network controller callback.
     *
     * @param callback The callback.
     */
    public void unregisterDataNetworkControllerCallback(
            @NonNull DataNetworkControllerCallback callback) {
        sendMessage(obtainMessage(EVENT_UNREGISTER_DATA_NETWORK_CONTROLLER_CALLBACK, callback));
    }

    /**
     * Tear down all data networks.
     *
     * @param reason The reason to tear down.
     */
    public void tearDownAllDataNetworks(@TearDownReason int reason) {
        sendMessage(obtainMessage(EVENT_TEAR_DOWN_ALL_DATA_NETWORKS, reason, 0));
    }

    /**
     * Called when needed to tear down all data networks.
     *
     * @param reason The reason to tear down.
     */
    public void onTearDownAllDataNetworks(@TearDownReason int reason) {
        log("onTearDownAllDataNetworks: reason=" + DataNetwork.tearDownReasonToString(reason));
        if (mDataNetworkList.isEmpty()) {
            log("tearDownAllDataNetworks: No pending networks. All disconnected now.");
            return;
        }

        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (!dataNetwork.isDisconnecting()) {
                tearDownGracefully(dataNetwork, reason);
            }
        }
    }

    /**
     * Evaluate the pending IMS de-registration networks and tear it down if it is safe to do that.
     */
    private void evaluatePendingImsDeregDataNetworks() {
        Iterator<Map.Entry<DataNetwork, Runnable>> it =
                mPendingImsDeregDataNetworks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DataNetwork, Runnable> entry = it.next();
            if (isSafeToTearDown(entry.getKey())) {
                // Now tear down the network.
                log("evaluatePendingImsDeregDataNetworks: Safe to tear down data network "
                        + entry.getKey() + " now.");
                entry.getValue().run();
                it.remove();
            } else {
                log("Still not safe to tear down " + entry.getKey() + ".");
            }
        }
    }

    /**
     * Check if the data network is safe to tear down at this moment.
     *
     * @param dataNetwork The data network.
     * @return {@code true} if the data network is safe to tear down. {@code false} indicates this
     * data network has requests originated from the IMS/RCS service and IMS/RCS is not
     * de-registered yet.
     */
    private boolean isSafeToTearDown(@NonNull DataNetwork dataNetwork) {
        for (int imsFeature : SUPPORTED_IMS_FEATURES) {
            String imsFeaturePackage = mImsFeaturePackageName.get(imsFeature);
            if (imsFeaturePackage != null) {
                if (dataNetwork.getAttachedNetworkRequestList()
                        .hasNetworkRequestsFromPackage(imsFeaturePackage)) {
                    if (mRegisteredImsFeatures.contains(imsFeature)) {
                        return false;
                    }
                }
            }
        }
        // All IMS features are de-registered (or this data network has no requests from IMS feature
        // packages.
        return true;
    }

    /**
     * @return {@code true} if IMS graceful tear down is supported by frameworks.
     */
    private boolean isImsGracefulTearDownSupported() {
        return mDataConfigManager.getImsDeregistrationDelay() > 0;
    }

    /**
     * Tear down the data network gracefully.
     *
     * @param dataNetwork The data network.
     */
    private void tearDownGracefully(@NonNull DataNetwork dataNetwork, @TearDownReason int reason) {
        long deregDelay = mDataConfigManager.getImsDeregistrationDelay();
        if (isImsGracefulTearDownSupported() && !isSafeToTearDown(dataNetwork)) {
            log("tearDownGracefully: Not safe to tear down " + dataNetwork
                    + " at this point. Wait for IMS de-registration or timeout. MMTEL="
                    + (mRegisteredImsFeatures.contains(ImsFeature.FEATURE_MMTEL)
                    ? "registered" : "not registered")
                    + ", RCS="
                    + (mRegisteredImsFeatures.contains(ImsFeature.FEATURE_RCS)
                    ? "registered" : "not registered")
            );
            mPendingImsDeregDataNetworks.put(dataNetwork,
                    dataNetwork.tearDownWithCondition(reason, deregDelay));
        } else {
            // Graceful tear down is not turned on. Tear down the network immediately.
            log("tearDownGracefully: Safe to tear down " + dataNetwork);
            dataNetwork.tearDown(reason);
        }
    }

    /**
     * Get the internet data network state. Note that this is the best effort if more than one
     * data network supports internet. For now only {@link TelephonyManager#DATA_CONNECTED}
     * , {@link TelephonyManager#DATA_SUSPENDED}, and {@link TelephonyManager#DATA_DISCONNECTED}
     * are supported.
     *
     * @return The data network state.
     */
    public @DataState int getInternetDataNetworkState() {
        return mInternetDataNetworkState;
    }

    /**
     * @return List of bound data service packages name on WWAN and WLAN.
     */
    public @NonNull List<String> getDataServicePackages() {
        List<String> packages = new ArrayList<>();
        for (int i = 0; i < mDataServiceManagers.size(); i++) {
            packages.add(mDataServiceManagers.valueAt(i).getDataServicePackageName());
        }
        return packages;
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
            dn.dump(fd, pw, args);
        }
        pw.decreaseIndent();

        pw.println("Pending tear down data networks:");
        pw.increaseIndent();
        for (DataNetwork dn : mPendingImsDeregDataNetworks.keySet()) {
            dn.dump(fd, pw, args);
        }
        pw.decreaseIndent();

        pw.println("Previously connected data networks: (up to "
                + MAX_HISTORICAL_CONNECTED_DATA_NETWORKS + ")");
        pw.increaseIndent();
        for (DataNetwork dn: mPreviousConnectedDataNetworkList) {
            // Do not print networks which is already in current network list.
            if (!mDataNetworkList.contains(dn)) {
                dn.dump(fd, pw, args);
            }
        }
        pw.decreaseIndent();

        pw.println("All telephony network requests:");
        pw.increaseIndent();
        for (TelephonyNetworkRequest networkRequest : mAllNetworkRequestList) {
            pw.println(networkRequest);
        }
        pw.decreaseIndent();

        pw.println("IMS features registration state: MMTEL="
                + (mRegisteredImsFeatures.contains(ImsFeature.FEATURE_MMTEL)
                ? "registered" : "not registered")
                + ", RCS="
                + (mRegisteredImsFeatures.contains(ImsFeature.FEATURE_RCS)
                ? "registered" : "not registered"));
        pw.println("mServiceState=" + mServiceState);
        pw.println("mPsRestricted=" + mPsRestricted);
        pw.println("mAnyDataNetworkExisting=" + mAnyDataNetworkExisting);
        pw.println("mInternetDataNetworkState="
                + TelephonyUtils.dataStateToString(mInternetDataNetworkState));
        pw.println("mImsDataNetworkState="
                + TelephonyUtils.dataStateToString(mImsDataNetworkState));
        pw.println("mDataServiceBound=" + mDataServiceBound);
        pw.println("mSimState=" + SubscriptionInfoUpdater.simStateString(mSimState));
        pw.println("mDataNetworkControllerCallbacks=" + mDataNetworkControllerCallbacks);
        pw.println("Subscription plans:");
        pw.increaseIndent();
        mSubscriptionPlans.forEach(pw::println);
        pw.decreaseIndent();
        pw.println("Unmetered override network types=" + mUnmeteredOverrideNetworkTypes.stream()
                .map(TelephonyManager::getNetworkTypeName).collect(Collectors.joining(",")));
        pw.println("Congested override network types=" + mCongestedOverrideNetworkTypes.stream()
                .map(TelephonyManager::getNetworkTypeName).collect(Collectors.joining(",")));
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println("-------------------------------------");
        mDataProfileManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataRetryManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataSettingsManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataStallRecoveryManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataConfigManager.dump(fd, pw, args);

        pw.decreaseIndent();
    }
}
