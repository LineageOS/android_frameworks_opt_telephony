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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.DataFailCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.DataState;
import android.telephony.TelephonyManager.SimState;
import android.telephony.TelephonyRegistryManager;
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
import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.SubscriptionInfoUpdater;
import com.android.internal.telephony.data.DataEvaluation.DataAllowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;
import com.android.internal.telephony.data.DataEvaluation.DataEvaluationReason;
import com.android.internal.telephony.data.DataNetwork.TearDownReason;
import com.android.internal.telephony.data.DataProfileManager.DataProfileManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataRetryEntry;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;
import com.android.internal.telephony.data.DataSettingsManager.DataSettingsManagerCallback;
import com.android.internal.telephony.data.DataStallRecoveryManager.DataStallRecoveryManagerCallback;
import com.android.internal.telephony.ims.ImsResolver;
import com.android.internal.telephony.util.TelephonyUtils;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
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

    /** Event for data profile changed. */
    private static final int EVENT_DATA_PROFILES_CHANGED = 10;

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

    /** The supported IMS features. This is for IMS graceful tear down support. */
    private static final Collection<Integer> SUPPORTED_IMS_FEATURES =
            List.of(ImsFeature.FEATURE_MMTEL, ImsFeature.FEATURE_RCS);

    /** The maximum number of previously connected data networks for debugging purposes. */
    private static final int MAX_HISTORICAL_CONNECTED_DATA_NETWORKS = 10;

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
    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers =
            new SparseArray<>();

    /** The subscription index associated with this data network controller. */
    private int mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    /** The current service state of the device. */
    // Note that keeping a copy here instead of directly using ServiceStateTracker.getServiceState()
    // is intended for detecting the delta.
    private @NonNull ServiceState mServiceState;

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

    /**
     * Data network controller callback. Used for listening events from data network controller.
     */
    private final @NonNull DataNetworkControllerCallbackList mDataNetworkControllerCallbacks =
            new DataNetworkControllerCallbackList();

    /** Indicates if packet switch data is restricted by the network. */
    private boolean mPsRestricted = false;

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

    /** IMS feature registration state. Key is the IMS feature, value is the registration state. */
    private final @NonNull SparseArray<Boolean> mImsFeatureRegistrationState = new SparseArray<>();

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
    public static class DataNetworkControllerCallback {
        /** The executor of the callback. */
        private @NonNull Executor mExecutor;

        /**
         * Indicates the callback is automatically unregistered after first invocation. This is
         * useful for the clients which only want to get the result once.
         */
        private boolean mAutoUnregisterEnabled = false;

        /**
         * Indicates callback auto unregister should be skipped this time. This
         * is internally used by {@link DataNetworkControllerCallbackList}.
         */
        private boolean mSkipAutoUnregisterThisTime = false;

        /**
         * Set the executor of the callback.
         *
         * @param executor The executor
         * @param enableAutoUnregister {@code true} if this callback should be unregistered
         * automatically after invoked the overridden callback method.
         */
        final void init(@NonNull @CallbackExecutor Executor executor,
                boolean enableAutoUnregister) {
            Objects.requireNonNull(executor);
            mExecutor = executor;
            mAutoUnregisterEnabled = enableAutoUnregister;
        }

        /**
         * @return The executor of the callback.
         */
        final @NonNull Executor getExecutor() {
            return mExecutor;
        }

        /**
         * @return {@code true} if this callback should be unregistered automatically after invoked
         * the overridden callback method.
         */
        final boolean isAutoUnregisterEnabled() {
            return mAutoUnregisterEnabled;
        }

        /**
         * @return {@code true} if the callback auto unregister should be skipped this time. This
         * is internally used by {@link DataNetworkControllerCallbackList}.
         */
        final boolean shouldSkipAutoUnregister() {
            return mSkipAutoUnregisterThisTime;
        }

        /**
         * Set the flag that indicates whether this callback should be auto unregistered or not.
         * This should be only called by
         *
         * @param skip {@code true} if this callback should be auto unregistered.
         */
        final void setSkipAutoUnregister(boolean skip) {
            mSkipAutoUnregisterThisTime = skip;
        }

        /**
         * Called when internet data network validation status changed.
         *
         * @param validationStatus The validation status.
         */
        public void onInternetDataNetworkValidationStatusChanged(
                @ValidationStatus int validationStatus) {
            mSkipAutoUnregisterThisTime = true;
        }

        /** Called when internet data network is connected. */
        public void onInternetDataNetworkConnected() {
            // Never remove this line.
            mSkipAutoUnregisterThisTime = true;
        }

        /** Called when internet data network is disconnected. */
        public void onInternetDataNetworkDisconnected() {
            // Never remove this line.
            mSkipAutoUnregisterThisTime = true;
        }

        /** Called when all data networks are disconnected. */
        public void onAllDataNetworksDisconnected() {
            // Never remove this line.
            mSkipAutoUnregisterThisTime = true;
        }
    }

    /**
     * The list of all registered callbacks.
     */
    @VisibleForTesting
    public class DataNetworkControllerCallbackList {
        /** Callbacks set. */
        private final @NonNull Set<DataNetworkControllerCallback> mCallbacks = new ArraySet<>();

        /**
         * Register the callback.
         *
         * @param callback The callback.
         */
        public void registerCallback(@NonNull DataNetworkControllerCallback callback) {
            logv("registerCallback: " + callback);
            mCallbacks.add(Objects.requireNonNull(callback));

            if (mDataNetworkList.isEmpty()) {
                notifyListeners(DataNetworkControllerCallback::onAllDataNetworksDisconnected);
            }
        }

        /**
         * Unregister the callback.
         *
         * @param callback The callback.
         */
        public void unregisterCallback(@NonNull DataNetworkControllerCallback callback) {
            logv("unregisterCallback: " + callback);
            mCallbacks.remove(callback);
        }

        /**
         * Notify the listeners
         *
         * @param callbackConsumer The consumer which contains the actual callback method.
         */
        public void notifyListeners(Consumer<DataNetworkControllerCallback> callbackConsumer) {
            Iterator<DataNetworkControllerCallback> it = mCallbacks.iterator();
            while (it.hasNext()) {
                DataNetworkControllerCallback callback = it.next();
                callback.setSkipAutoUnregister(false);
                // Invoke the actual callback passed in consumer.
                callback.getExecutor().execute(() -> callbackConsumer.accept(callback));
                // The client might not override this method, we should skip auto unregister in
                // this case.
                if (callback.shouldSkipAutoUnregister()) {
                    logv("Callback " + callback + " skipped auto unregistering.");
                    continue;
                }

                // If the callback was registered as an auto-unregistered callback, unregister now
                // since the callback has been invoked.
                if (callback.isAutoUnregisterEnabled()) {
                    logv("Callback " + callback + " automatically removed.");
                    it.remove();
                }
            }
        }

        @Override
        public String toString() {
            return "[DataNetworkControllerCallbackList: " + mCallbacks + "]";
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
        mDataSettingsManager = new DataSettingsManager(mPhone, this, looper,
                new DataSettingsManagerCallback(this::post) {
                    @Override
                    public void onDataEnabledChanged(boolean enabled) {
                        // If mobile data is enabled by the user, evaluate the unsatisfied network
                        // requests and then attempt to setup data networks to satisfy them.
                        // If mobile data is disabled, evaluate the existing data networks and
                        // see if they need to be torn down.
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
                        DataNetworkController.this.onDataStallReestablishInternet();
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
        mDataRetryManager = new DataRetryManager(mPhone, this, looper,
                new DataRetryManagerCallback(this::post) {
                    @Override
                    public void onDataRetry(@NonNull DataRetryEntry dataRetryEntry) {
                        setupDataNetwork(dataRetryEntry.dataProfile, dataRetryEntry);
                    }
                });
        mImsManager = mPhone.getContext().getSystemService(ImsManager.class);

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
                onRemoveNetworkRequest((NetworkRequest) msg.obj);
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
            case EVENT_DATA_PROFILES_CHANGED:
                sendMessage(obtainMessage(EVENT_REEVALUATE_EXISTING_DATA_NETWORKS,
                        DataEvaluationReason.DATA_PROFILES_CHANGED));
                sendMessage(obtainMessage(EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS,
                        DataEvaluationReason.DATA_PROFILES_CHANGED));
                break;
            case EVENT_TEAR_DOWN_ALL_DATA_NETWORKS:
                onTearDownAllDataNetworks(msg.arg1);
                break;
            case EVENT_REGISTER_DATA_NETWORK_CONTROLLER_CALLBACK:
                mDataNetworkControllerCallbacks
                        .registerCallback((DataNetworkControllerCallback) msg.obj);
                break;
            case EVENT_UNREGISTER_DATA_NETWORK_CONTROLLER_CALLBACK:
                mDataNetworkControllerCallbacks.unregisterCallback(
                        (DataNetworkControllerCallback) msg.obj);
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
            log("Found a compatible data network " + dataNetwork + ". Attaching "
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
     * @return The data evaluation result.
     */
    private @NonNull DataEvaluation evaluateNetworkRequest(
            @NonNull TelephonyNetworkRequest networkRequest, DataEvaluationReason reason) {
        DataEvaluation evaluation = new DataEvaluation(reason);

        int transport = mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                networkRequest.getHighestPriorityNetworkCapability());

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

        // Check if there is any compatible data profile
        DataProfile dataProfile = mDataProfileManager
                .getDataProfileForNetworkRequest(networkRequest, getDataNetworkType(transport));
        if (dataProfile == null) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.NO_SUITABLE_DATA_PROFILE);
        }

        // Check if data is disabled
        int[] apnTypes = Arrays.stream(networkRequest.getApnTypesCapabilities())
                .map(DataUtils::networkCapabilityToApnType).toArray();
        int apnType = 0;
        for (int apn : apnTypes) {
            apnType |= apn;
        }
        if (apnType == 0 ? !mDataSettingsManager.isDataEnabled()
                : !mDataSettingsManager.isDataEnabled(apnType)) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.DATA_DISABLED);
        }

        // Check if device is CDMA and is currently in ECBM
        if (mPhone.isInEcm() && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            evaluation.addDataDisallowedReason(DataDisallowedReason.EMERGENCY_CALL);
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
                List<Integer> meteredApns = mServiceState.getDataRoaming()
                        ? mDataConfigManager.getMeteredApnTypesWhenRoaming()
                        : mDataConfigManager.getMeteredApnTypes();
                boolean unmetered = !meteredApns.isEmpty() && apnTypes.length != 0;
                for (int apn : apnTypes) {
                    if (meteredApns.contains(apn)) {
                        unmetered = false;
                        break;
                    }
                }
                if (unmetered) {
                    evaluation.addDataAllowedReason(DataAllowedReason.UNMETERED_USAGE);
                }
            }

            // Check if request is restricted
            if (!networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
                evaluation.addDataAllowedReason(DataAllowedReason.RESTRICTED_REQUEST);
            }
        }

        if (evaluation.isDataAllowed()) {
            evaluation.setCandidateDataProfile(dataProfile);
        }

        networkRequest.setEvaluation(evaluation);
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
            log("onReevaluateUnsatisfiedNetworkRequests: No unsatisfied network requests to "
                    + "evaluate.");
            return;
        }

        log("Re-evaluating " + count + " unsatisfied network requests in " + requestsMap.size()
                + " groups, " + requestsMap.keySet().stream()
                .map(capsSet -> DataUtils.networkCapabilitiesToString(
                        capsSet.stream().mapToInt(Number::intValue).toArray()))
                .collect(Collectors.joining(",")) + " due to " + reason);

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
            dataDisabled = !mDataSettingsManager.isDataEnabled(
                    dataProfile.getApnSetting().getApnTypeBitmask());

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
                        mImsFeatureRegistrationState.put(imsFeature, true);
                    }

                    @Override
                    public void onUnregistered(ImsReasonInfo info) {
                        log("IMS " + DataUtils.imsFeatureToString(imsFeature)
                                + " deregistered. Info=" + info);
                        mImsFeatureRegistrationState.put(imsFeature, false);
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
     * Find unsatisfied network requests that can be satisfied by the given network capabilities.
     *
     * @param capabilities The network capabilities.
     * @return The network requests list.
     */
    private @NonNull NetworkRequestList findSatisfiableNetworkRequests(
            @NonNull NetworkCapabilities capabilities) {
        return new NetworkRequestList(mAllNetworkRequestList.stream()
                .filter(request -> request.getState()
                        == TelephonyNetworkRequest.REQUEST_STATE_UNSATISFIED)
                .filter(request -> capabilities.satisfiedByNetworkCapabilities(capabilities))
                .collect(Collectors.toList()));
    }

    /**
     * Setup data network.
     *
     * @param dataProfile The data profile to setup the data network.
     * @param dataRetryEntry Data retry entry. {@code null} if this data network setup is not
     * initiated by a data retry.
     */
    private void setupDataNetwork(@NonNull DataProfile dataProfile,
            @Nullable DataRetryEntry dataRetryEntry) {
        log("onSetupDataNetwork: dataProfile=" + dataProfile);
        for (DataNetwork dataNetwork : mDataNetworkList) {
            if (dataNetwork.getDataProfile().equals(dataProfile)) {
                log("onSetupDataNetwork: Found existing data network " + dataNetwork
                        + " has the same data profile.");
                return;
            }
        }

        NetworkRequestList networkRequestList = findSatisfiableNetworkRequests(dataProfile);

        if (networkRequestList.isEmpty()) {
            log("Can't find any unsatisfied network requests that can be satisfied by this data "
                    + "profile.");
            return;
        }

        logl("Creating data network with " + dataProfile + ", and attaching "
                + networkRequestList.size() + " network requests to it.");
        mDataNetworkList.add(new DataNetwork(mPhone, getLooper(), mDataServiceManagers,
                dataProfile, networkRequestList, new DataNetwork.DataNetworkCallback(this::post) {
                    @Override
                    public void onSetupDataFailed(@NonNull DataNetwork dataNetwork,
                            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
                            long retryDurationMillis) {
                        if (dataRetryEntry != null) {
                            dataRetryEntry.setState(DataRetryEntry.RETRY_STATE_FAILED);
                        }
                        DataNetworkController.this.onDataNetworkSetupDataFailed(
                                dataNetwork, requestList, cause, retryDurationMillis);
                    }

                    @Override
                    public void onConnected(@NonNull DataNetwork dataNetwork) {
                        if (dataRetryEntry != null) {
                            dataRetryEntry.setState(DataRetryEntry.RETRY_STATE_SUCCEEDED);
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
                }));
    }

    /**
     * Called when setup data network failed.
     *
     * @param dataNetwork The data network.
     * @param requestList The network requests attached to the data network.
     * @param cause The fail cause
     * @param retryDelayMillis The retry timer suggested by the network/data service.
     */
    private void onDataNetworkSetupDataFailed(@NonNull DataNetwork dataNetwork,
            @NonNull NetworkRequestList requestList, @DataFailureCause int cause,
            long retryDelayMillis) {
        logl("onDataNetworkSetupDataFailed: " + dataNetwork + ", cause="
                + DataFailCause.toString(cause) + "(" + cause + "), retryDelayMillis="
                + retryDelayMillis + "ms.");
        mDataNetworkList.remove(dataNetwork);
        // Data retry manager will determine if retry is needed. If needed, retry will be scheduled.
        mDataRetryManager.evaluateDataRetry(dataNetwork.getDataProfile(), requestList, cause,
                retryDelayMillis);
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
        if (dataNetwork.isInternetSupported()) {
            mDataNetworkControllerCallbacks.notifyListeners(
                    DataNetworkControllerCallback::onInternetDataNetworkConnected);
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
            mDataNetworkControllerCallbacks.notifyListeners(callback ->
                    callback.onInternetDataNetworkValidationStatusChanged(status));
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
                    + TelephonyUtils.dataStateToString(mImsDataNetworkState) + " to CONNECTED.");
            mImsDataNetworkState = TelephonyManager.DATA_CONNECTED;
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
        // TODO: Should perform retry here.
        logl("onDataNetworkDisconnected: " + dataNetwork + ", cause="
                + DataFailCause.toString(cause) + "(" + cause + ")");
        mDataNetworkList.remove(dataNetwork);
        mPendingImsDeregDataNetworks.remove(dataNetwork);
        updateOverallInternetDataState();

        if (dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)) {
            logl("IMS data state changed from "
                    + TelephonyUtils.dataStateToString(mImsDataNetworkState) + " to DISCONNECTED.");
            mImsDataNetworkState = TelephonyManager.DATA_DISCONNECTED;
        }

        if (dataNetwork.isInternetSupported()) {
            mDataNetworkControllerCallbacks.notifyListeners(
                    DataNetworkControllerCallback::onInternetDataNetworkDisconnected);
        }

        if (mDataNetworkList.isEmpty()) {
            log("All data networks disconnected now.");
            mDataNetworkControllerCallbacks.notifyListeners(
                    DataNetworkControllerCallback::onAllDataNetworksDisconnected);
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
        log("onDataStallReestablishInternet");
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
            // reset throttling after binding to data service
            // mDataThrottler.reset();
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
                        && (dataNetwork.isConnected() || dataNetwork.isUnderHandover()));
        // If any one is not suspended, then the overall is not suspended.
        List<DataNetwork> allConnectedInternetDataNetworks = mDataNetworkList.stream()
                .filter(DataNetwork::isInternetSupported)
                .filter(dataNetwork -> dataNetwork.isConnected() || dataNetwork.isUnderHandover())
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
            mInternetDataNetworkState = dataNetworkState;
        }
    }

    /**
     * Update the IMS data network state. For now only {@link TelephonyManager#DATA_CONNECTED}
     * , {@link TelephonyManager#DATA_SUSPENDED}, and
     * {@link TelephonyManager#DATA_DISCONNECTED} are supported. This method is only for debugging
     * purposes now.
     */
    private void updateImsDataState() {
        int dataNetworkState = TelephonyManager.DATA_DISCONNECTED;
        // There should not be more than one IMS PDN.
        DataNetwork imsInternetDataNetwork = mDataNetworkList.stream()
                .filter(dataNetwork -> dataNetwork.getNetworkCapabilities()
                        .hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS))
                .findAny()
                .orElse(null);
        if (imsInternetDataNetwork != null) {
            // TODO: Support handover state
            if (imsInternetDataNetwork.isSuspended()) {
                dataNetworkState = TelephonyManager.DATA_SUSPENDED;
            } else if (imsInternetDataNetwork.isConnected()
                    || imsInternetDataNetwork.isUnderHandover()) {
                dataNetworkState = TelephonyManager.DATA_CONNECTED;
            }
        }

        if (mImsDataNetworkState != dataNetworkState) {
            logl("IMS data state changed from "
                    + TelephonyUtils.dataStateToString(mInternetDataNetworkState) + " to "
                    + TelephonyUtils.dataStateToString(dataNetworkState) + ".");
            mImsDataNetworkState = dataNetworkState;
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
     * @param executor The executor of the callback.
     * @param callback The callback.
     * @param autoUnregister {@code true} if this callback should be unregistered automatically
     * after invoked the overridden callback method.
     */
    public void registerDataNetworkControllerCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull DataNetworkControllerCallback callback, boolean autoUnregister) {
        callback.init(executor, autoUnregister);
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
                    if (mImsFeatureRegistrationState.get(imsFeature)) {
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
                    + (mImsFeatureRegistrationState.get(ImsFeature.FEATURE_MMTEL)
                    ? "registered" : "deregistered")
                    + ", RCS="
                    + (mImsFeatureRegistrationState.get(ImsFeature.FEATURE_RCS)
                    ? "registered" : "deregistered")
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
                + (mImsFeatureRegistrationState.get(ImsFeature.FEATURE_MMTEL)
                ? "registered" : "deregistered")
                + ", RCS="
                + (mImsFeatureRegistrationState.get(ImsFeature.FEATURE_RCS)
                ? "registered" : "deregistered"));
        pw.println("mServiceState=" + mServiceState);
        pw.println("mPsRestricted=" + mPsRestricted);
        pw.println("mInternetDataNetworkState="
                + TelephonyUtils.dataStateToString(mInternetDataNetworkState));
        pw.println("mImsDataNetworkState="
                + TelephonyUtils.dataStateToString(mImsDataNetworkState));
        pw.println("mDataServiceBound=" + mDataServiceBound);
        pw.println("mSimState=" + SubscriptionInfoUpdater.simStateString(mSimState));
        pw.println(mDataNetworkControllerCallbacks);
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
