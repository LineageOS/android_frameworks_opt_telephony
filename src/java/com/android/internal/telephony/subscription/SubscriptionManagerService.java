/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.internal.telephony.subscription;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.compat.CompatChanges;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.TelephonyServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.service.euicc.EuiccService;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.DataRoamingMode;
import android.telephony.SubscriptionManager.DeviceToDeviceStatusSharingPreference;
import android.telephony.SubscriptionManager.PhoneNumberSource;
import android.telephony.SubscriptionManager.SimDisplayNameSource;
import android.telephony.SubscriptionManager.SubscriptionType;
import android.telephony.SubscriptionManager.UsageSetting;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.EuiccManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.EventLog;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CarrierResolver;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.MultiSimSettingController;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyPermissions;
import com.android.internal.telephony.data.PhoneSwitcher;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManager.SubscriptionDatabaseManagerCallback;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccSlot;
import com.android.internal.telephony.util.ArrayUtils;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * The subscription manager service is the backend service of {@link SubscriptionManager}.
 * The service handles all SIM subscription related requests from clients.
 */
public class SubscriptionManagerService extends ISub.Stub {
    private static final String LOG_TAG = "SMSVC";

    /** Whether enabling verbose debugging message or not. */
    private static final boolean VDBG = false;

    /**
     * Apps targeting on Android T and beyond will get exception if there is no access to device
     * identifiers nor has carrier privileges when calling
     * {@link SubscriptionManager#getSubscriptionsInGroup}.
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.TIRAMISU)
    public static final long REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID = 213902861L;

    /** Instance of subscription manager service. */
    @NonNull
    private static SubscriptionManagerService sInstance;

    /** The context */
    @NonNull
    private final Context mContext;

    /** App Ops manager instance. */
    @NonNull
    private final AppOpsManager mAppOpsManager;

    /** Telephony manager instance. */
    @NonNull
    private final TelephonyManager mTelephonyManager;

    /** Subscription manager instance. */
    @NonNull
    private final SubscriptionManager mSubscriptionManager;

    /**
     * Euicc manager instance. Will be null if the device does not support
     * {@link PackageManager#FEATURE_TELEPHONY_EUICC}.
     */
    @Nullable
    private final EuiccManager mEuiccManager;

    /** Uicc controller instance. */
    @NonNull
    private final UiccController mUiccController;

    /**
     * Euicc controller instance. Will be null if the device does not support
     * {@link PackageManager#FEATURE_TELEPHONY_EUICC}.
     */
    @Nullable
    private EuiccController mEuiccController;

    /** The main handler of subscription manager service. */
    @NonNull
    private final Handler mHandler;

    /** Local log for most important debug messages. */
    @NonNull
    private final LocalLog mLocalLog = new LocalLog(128);

    /** The subscription database manager. */
    @NonNull
    private final SubscriptionDatabaseManager mSubscriptionDatabaseManager;

    /** The slot index subscription id map. Key is the slot index, and the value is sub id. */
    @NonNull
    private final WatchedMap<Integer, Integer> mSlotIndexToSubId = new WatchedMap<>();

    /** Subscription manager service callbacks. */
    @NonNull
    private final Set<SubscriptionManagerServiceCallback> mSubscriptionManagerServiceCallbacks =
            new ArraySet<>();

    /**
     * Default sub id. Derived from {@link #mDefaultVoiceSubId} and {@link #mDefaultDataSubId},
     * depending on device capability.
     */
    @NonNull
    private final WatchedInt mDefaultSubId;

    /** Default voice subscription id. */
    @NonNull
    private final WatchedInt mDefaultVoiceSubId;

    /** Default data subscription id. */
    @NonNull
    private final WatchedInt mDefaultDataSubId;

    /** Default sms subscription id. */
    @NonNull
    private final WatchedInt mDefaultSmsSubId;

    /**
     * Watched map that automatically invalidate cache in {@link SubscriptionManager}.
     */
    private static class WatchedMap<K, V> extends ConcurrentHashMap<K, V> {
        @Override
        public void clear() {
            super.clear();
            SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
        }

        @Override
        public V put(K key, V value) {
            V oldValue = super.put(key, value);
            if (!Objects.equals(oldValue, value)) {
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
            }
            return oldValue;
        }

        @Override
        public V remove(Object key) {
            V oldValue = super.remove(key);
            if (oldValue != null) {
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
            }
            return oldValue;
        }
    }

    /**
     * Watched integer.
     */
    public static class WatchedInt {
        private int mValue;

        /**
         * Constructor.
         *
         * @param initialValue The initial value.
         */
        public WatchedInt(int initialValue) {
            mValue = initialValue;
        }

        /**
         * @return The value.
         */
        public int get() {
            return mValue;
        }

        /**
         * Set the value.
         *
         * @param newValue The new value.
         *
         * @return {@code true} if {@code newValue} is different from the existing value.
         */
        public boolean set(int newValue) {
            if (mValue != newValue) {
                mValue = newValue;
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
                return true;
            }
            return false;
        }
    }

    /**
     * This is the callback used for listening events from {@link SubscriptionManagerService}.
     */
    public static class SubscriptionManagerServiceCallback {
        /** The executor of the callback. */
        @NonNull
        private final Executor mExecutor;

        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public SubscriptionManagerServiceCallback(@NonNull @CallbackExecutor Executor executor) {
            mExecutor = executor;
        }

        /**
         * @return The executor of the callback.
         */
        @NonNull
        @VisibleForTesting
        public Executor getExecutor() {
            return mExecutor;
        }

        /**
         * Invoke the callback from executor.
         *
         * @param runnable The callback method to invoke.
         */
        public void invokeFromExecutor(@NonNull Runnable runnable) {
            mExecutor.execute(runnable);
        }

        /**
         * Called when subscription changed.
         *
         * @param subId The subscription id.
         */
        public void onSubscriptionChanged(int subId) {}

        /**
         * Called when {@link SubscriptionInfoInternal#areUiccApplicationsEnabled()} changed.
         *
         * @param subId The subscription id.
         */
        public void onUiccApplicationsEnabled(int subId) {}
    }

    /**
     * The constructor
     *
     * @param context The context
     * @param looper The looper for the handler.
     */
    public SubscriptionManagerService(@NonNull Context context, @NonNull Looper looper) {
        sInstance = this;
        mContext = context;
        mTelephonyManager = context.getSystemService(TelephonyManager.class);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
        mEuiccManager = context.getSystemService(EuiccManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);

        mUiccController = UiccController.getInstance();
        mHandler = new Handler(looper);
        TelephonyServiceManager.ServiceRegisterer subscriptionServiceRegisterer =
                TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getSubscriptionServiceRegisterer();
        if (subscriptionServiceRegisterer.get() == null) {
            subscriptionServiceRegisterer.register(this);
        }

        mDefaultVoiceSubId = new WatchedInt(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
            @Override
            public boolean set(int newValue) {
                if (super.set(newValue)) {
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION, newValue);
                    return true;
                }
                return false;
            }
        };

        mDefaultDataSubId = new WatchedInt(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
            @Override
            public boolean set(int newValue) {
                if (super.set(newValue)) {
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION, newValue);
                    return true;
                }
                return false;
            }
        };

        mDefaultSmsSubId = new WatchedInt(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID)) {
            @Override
            public boolean set(int newValue) {
                if (super.set(newValue)) {
                    Settings.Global.putInt(mContext.getContentResolver(),
                            Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION, newValue);
                    return true;
                }
                return false;
            }
        };

        mDefaultSubId = new WatchedInt(SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        // Create a separate thread for subscription database manager. The database will be updated
        // from a different thread.
        HandlerThread handlerThread = new HandlerThread(LOG_TAG);
        handlerThread.start();
        mSubscriptionDatabaseManager = new SubscriptionDatabaseManager(context,
                handlerThread.getLooper(), new SubscriptionDatabaseManagerCallback(mHandler::post) {
                    /**
                     * Called when database has been loaded into the cache.
                     */
                    @Override
                    public void onDatabaseLoaded() {
                        log("Subscription database has been loaded.");
                    }

                    /**
                     * Called when subscription changed.
                     *
                     * @param subId The subscription id.
                     */
                    @Override
                    public void onSubscriptionChanged(int subId) {
                        mSubscriptionManagerServiceCallbacks.forEach(
                                callback -> callback.invokeFromExecutor(
                                        () -> callback.onSubscriptionChanged(subId)));

                        MultiSimSettingController.getInstance().notifySubscriptionInfoChanged();

                        TelephonyRegistryManager telephonyRegistryManager =
                                mContext.getSystemService(TelephonyRegistryManager.class);
                        if (telephonyRegistryManager != null) {
                            telephonyRegistryManager.notifySubscriptionInfoChanged();
                        }

                        SubscriptionInfoInternal subInfo =
                                mSubscriptionDatabaseManager.getSubscriptionInfoInternal(subId);
                        if (subInfo != null && subInfo.isOpportunistic()
                                && telephonyRegistryManager != null) {
                            telephonyRegistryManager.notifyOpportunisticSubscriptionInfoChanged();
                        }

                        // TODO: Call TelephonyMetrics.updateActiveSubscriptionInfoList when active
                        //  subscription changes.
                    }

                    /**
                     * Called when {@link SubscriptionInfoInternal#areUiccApplicationsEnabled()}
                     * changed.
                     *
                     * @param subId The subscription id.
                     */
                    @Override
                    public void onUiccApplicationsEnabled(int subId) {
                        log("onUiccApplicationsEnabled: subId=" + subId);
                        mSubscriptionManagerServiceCallbacks.forEach(
                                callback -> callback.invokeFromExecutor(
                                        () -> callback.onUiccApplicationsEnabled(subId)));
                    }
                });

        updateDefaultSubId();

        mHandler.post(() -> {
            // EuiccController is created after SubscriptionManagerService. So we need to get
            // the instance later in the handler.
            if (mContext.getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY_EUICC)) {
                mEuiccController = EuiccController.get();
            }
        });
    }

    /**
     * @return The singleton instance of {@link SubscriptionManagerService}.
     */
    @NonNull
    public static SubscriptionManagerService getInstance() {
        return sInstance;
    }

    /**
     * Check if the calling package can manage the subscription group.
     *
     * @param groupUuid a UUID assigned to the subscription group.
     * @param callingPackage the package making the IPC.
     *
     * @return {@code true} if calling package is the owner of or has carrier privileges for all
     * subscriptions in the group.
     */
    private boolean canPackageManageGroup(@NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage) {
        if (groupUuid == null) {
            throw new IllegalArgumentException("Invalid groupUuid");
        }

        if (TextUtils.isEmpty(callingPackage)) {
            throw new IllegalArgumentException("Empty callingPackage");
        }

        List<SubscriptionInfo> infoList;

        // Getting all subscriptions in the group.
        infoList = mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                .filter(subInfo -> subInfo.getGroupUuid().equals(groupUuid.toString()))
                .map(SubscriptionInfoInternal::toSubscriptionInfo)
                .collect(Collectors.toList());

        // If the group does not exist, then by default the UUID is up for grabs so no need to
        // restrict management of a group (that someone may be attempting to create).
        if (ArrayUtils.isEmpty(infoList)) {
            return true;
        }

        // If the calling package is the group owner, skip carrier permission check and return
        // true as it was done before.
        if (callingPackage.equals(infoList.get(0).getGroupOwner())) return true;

        // Check carrier privilege for all subscriptions in the group.
        return (checkCarrierPrivilegeOnSubList(infoList.stream()
                .mapToInt(SubscriptionInfo::getSubscriptionId).toArray(), callingPackage));
    }

    /**
     * Helper function to check if the caller has carrier privilege permissions on a list of subId.
     * The check can either be processed against access rules on currently active SIM cards, or
     * the access rules we keep in our database for currently inactive SIMs.
     *
     * @param subIdList List of subscription ids.
     * @param callingPackage The package making the call.
     *
     * @throws IllegalArgumentException if the some subId is invalid or doesn't exist.
     *
     * @return {@code true} if checking passes on all subId, {@code false} otherwise.
     */
    private boolean checkCarrierPrivilegeOnSubList(@NonNull int[] subIdList,
            @NonNull String callingPackage) {
        for (int subId : subIdList) {
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);
            if (subInfo == null) {
                loge("checkCarrierPrivilegeOnSubList: subId " + subId + " does not exist.");
                return false;
            }

            if (subInfo.isActive()) {
                if (!mTelephonyManager.hasCarrierPrivileges(subId)) {
                    loge("checkCarrierPrivilegeOnSubList: Does not have carrier privilege on sub "
                            + subId);
                    return false;
                }
            } else {
                if (!mSubscriptionManager.canManageSubscription(subInfo.toSubscriptionInfo(),
                        callingPackage)) {
                    loge("checkCarrierPrivilegeOnSubList: cannot manage sub " + subId);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Sync the settings from specified subscription to all groupped subscriptions.
     *
     * @param subId The subscription id of the referenced subscription.
     */
    public void syncGroupedSetting(int subId) {
        mHandler.post(() -> {
            SubscriptionInfoInternal reference = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);
            if (reference == null) {
                loge("syncSettings: Can't find subscription info for sub " + subId);
                return;
            }

            if (reference.getGroupUuid().isEmpty()) {
                // The reference subscription is not in a group. No need to sync.
                return;
            }

            for (SubscriptionInfoInternal subInfo : mSubscriptionDatabaseManager
                    .getAllSubscriptions()) {
                if (subInfo.getSubscriptionId() != subId
                        && subInfo.getGroupUuid().equals(reference.getGroupUuid())) {
                    // Copy all settings from reference sub to the grouped subscriptions.
                    SubscriptionInfoInternal newSubInfo = new SubscriptionInfoInternal
                            .Builder(subInfo)
                            .setEnhanced4GModeEnabled(reference.getEnhanced4GModeEnabled())
                            .setVideoTelephonyEnabled(reference.getVideoTelephonyEnabled())
                            .setWifiCallingEnabled(reference.getWifiCallingEnabled())
                            .setWifiCallingModeForRoaming(reference.getWifiCallingModeForRoaming())
                            .setWifiCallingMode(reference.getWifiCallingMode())
                            .setWifiCallingEnabledForRoaming(
                                    reference.getWifiCallingEnabledForRoaming())
                            .setDataRoaming(reference.getDataRoaming())
                            .setDisplayName(reference.getDisplayName())
                            .setEnabledMobileDataPolicies(reference.getEnabledMobileDataPolicies())
                            .setUiccApplicationsEnabled(reference.getUiccApplicationsEnabled())
                            .setRcsUceEnabled(reference.getRcsUceEnabled())
                            .setCrossSimCallingEnabled(reference.getCrossSimCallingEnabled())
                            .setNrAdvancedCallingEnabled(reference.getNrAdvancedCallingEnabled())
                            .setUserId(reference.getUserId())
                            .build();
                    mSubscriptionDatabaseManager.updateSubscription(newSubInfo);
                    log("Synced settings from sub " + subId + " to sub "
                            + newSubInfo.getSubscriptionId());
                }
            }
        });
    }

    /**
     * Check whether the {@code callingPackage} has access to the phone number on the specified
     * {@code subId} or not.
     *
     * @param subId The subscription id.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     * @param message Message to include in the exception or NoteOp.
     *
     * @return {@code true} if the caller has phone number access.
     */
    private boolean hasPhoneNumberAccess(int subId, @NonNull String callingPackage,
            @Nullable String callingFeatureId, @Nullable String message) {
        try {
            return TelephonyPermissions.checkCallingOrSelfReadPhoneNumber(mContext, subId,
                    callingPackage, callingFeatureId, message);
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Check whether the {@code callingPackage} has access to subscriber identifiers on the
     * specified {@code subId} or not.
     *
     * @param subId The subscription id.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     * @param message Message to include in the exception or NoteOp.
     * @param reportFailure Indicates if failure should be reported.
     *
     * @return {@code true} if the caller has identifier access.
     */
    private boolean hasSubscriberIdentifierAccess(int subId, @NonNull String callingPackage,
            @Nullable String callingFeatureId, @Nullable String message, boolean reportFailure) {
        try {
            return TelephonyPermissions.checkCallingOrSelfReadSubscriberIdentifiers(mContext, subId,
                    callingPackage, callingFeatureId, message, reportFailure);
        } catch (SecurityException e) {
            // A SecurityException indicates that the calling package is targeting at least the
            // minimum level that enforces identifier access restrictions and the new access
            // requirements are not met.
            return false;
        }
    }

    /**
     * Conditionally removes identifiers from the provided {@link SubscriptionInfo} if the {@code
     * callingPackage} does not meet the access requirements for identifiers and returns the
     * potentially modified object.
     *
     * <p>
     * If the caller does not have {@link Manifest.permission#READ_PHONE_NUMBERS} permission,
     * {@link SubscriptionInfo#getNumber()} will return empty string.
     * If the caller does not have {@link Manifest.permission#USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER},
     * {@link SubscriptionInfo#getIccId()} and {@link SubscriptionInfo#getCardString()} will return
     * empty string, and {@link SubscriptionInfo#getGroupUuid()} will return {@code null}.
     *
     * @param subInfo The subscription info.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     * @param message Message to include in the exception or NoteOp.
     *
     * @return The modified {@link SubscriptionInfo} depending on caller's permission.
     */
    @NonNull
    private SubscriptionInfo conditionallyRemoveIdentifiers(@NonNull SubscriptionInfo subInfo,
            @NonNull String callingPackage, @Nullable String callingFeatureId,
            @Nullable String message) {
        int subId = subInfo.getSubscriptionId();
        boolean hasIdentifierAccess = hasSubscriberIdentifierAccess(subId, callingPackage,
                callingFeatureId, message, true);
        boolean hasPhoneNumberAccess = hasPhoneNumberAccess(subId, callingPackage,
                callingFeatureId, message);

        if (hasIdentifierAccess && hasPhoneNumberAccess) {
            return subInfo;
        }

        SubscriptionInfo.Builder result = new SubscriptionInfo.Builder(subInfo);
        if (!hasIdentifierAccess) {
            result.setIccId(null);
            result.setCardString(null);
            result.setGroupUuid(null);
        }

        if (!hasPhoneNumberAccess) {
            result.setNumber(null);
        }
        return result.build();
    }

    /**
     * @return The list of ICCIDs from the inserted physical SIMs.
     */
    @NonNull
    private List<String> getIccIdsOfInsertedPhysicalSims() {
        List<String> iccidList = new ArrayList<>();
        UiccSlot[] uiccSlots = mUiccController.getUiccSlots();
        if (uiccSlots == null) return iccidList;

        for (UiccSlot uiccSlot : uiccSlots) {
            if (uiccSlot != null && uiccSlot.getCardState() != null
                    && uiccSlot.getCardState().isCardPresent() && !uiccSlot.isEuicc()) {
                // Non euicc slots will have single port, so use default port index.
                String iccId = uiccSlot.getIccId(TelephonyManager.DEFAULT_PORT_INDEX);
                if (!TextUtils.isEmpty(iccId)) {
                    iccidList.add(IccUtils.stripTrailingFs(iccId));
                }
            }
        }

        return iccidList;
    }

    /**
     * Set the subscription carrier id.
     *
     * @param subId Subscription id.
     * @param carrierId The carrier id.
     *
     * @throws IllegalArgumentException if {@code subId} is invalid or the subscription does not
     * exist.
     *
     * @see TelephonyManager#getSimCarrierId()
     */
    public void setCarrierId(int subId, int carrierId) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setCarrierId(subId, carrierId);
        } catch (IllegalArgumentException e) {
            loge("setCarrierId: invalid subId=" + subId);
        }
    }

    /**
     * Set MCC/MNC by subscription id.
     *
     * @param mccMnc MCC/MNC associated with the subscription.
     * @param subId The subscription id.
     */
    public void setMccMnc(int subId, @NonNull String mccMnc) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setMcc(subId, mccMnc.substring(0, 3));
            mSubscriptionDatabaseManager.setMnc(subId, mccMnc.substring(3));
        } catch (IllegalArgumentException e) {
            loge("setMccMnc: invalid subId=" + subId);
        }
    }

    /**
     * Set ISO country code by subscription id.
     *
     * @param iso ISO country code associated with the subscription.
     * @param subId The subscription id.
     */
    public void setCountryIso(int subId, @NonNull String iso) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setCountryIso(subId, iso);
        } catch (IllegalArgumentException e) {
            loge("setCountryIso: invalid subId=" + subId);
        }
    }

    /**
     * Set the name displayed to the user that identifies subscription provider name. This name
     * is the SPN displayed in status bar and many other places. Can't be renamed by the user.
     *
     * @param subId Subscription id.
     * @param carrierName The carrier name.
     */
    public void setCarrierName(int subId, @NonNull String carrierName) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setCarrierName(subId, carrierName);
        } catch (IllegalArgumentException e) {
            loge("setCarrierName: invalid subId=" + subId);
        }
    }

    /**
     * Set last used TP message reference.
     *
     * @param subId Subscription id.
     * @param lastUsedTPMessageReference Last used TP message reference.
     */
    public void setLastUsedTPMessageReference(int subId, int lastUsedTPMessageReference) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setLastUsedTPMessageReference(
                    subId, lastUsedTPMessageReference);
        } catch (IllegalArgumentException e) {
            loge("setLastUsedTPMessageReference: invalid subId=" + subId);
        }
    }

    /**
     * Set the enabled mobile data policies.
     *
     * @param subId Subscription id.
     * @param enabledMobileDataPolicies The enabled mobile data policies.
     */
    public void setEnabledMobileDataPolicies(int subId, @NonNull String enabledMobileDataPolicies) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setEnabledMobileDataPolicies(
                    subId, enabledMobileDataPolicies);
        } catch (IllegalArgumentException e) {
            loge("setEnabledMobileDataPolicies: invalid subId=" + subId);
        }
    }

    /**
     * Set the phone number retrieved from IMS.
     *
     * @param subId Subscription id.
     * @param numberFromIms The phone number retrieved from IMS.
     */
    public void setNumberFromIms(int subId, @NonNull String numberFromIms) {
        // This can throw IllegalArgumentException if the subscription does not exist.
        try {
            mSubscriptionDatabaseManager.setNumberFromIms(subId, numberFromIms);
        } catch (IllegalArgumentException e) {
            loge("setNumberFromIms: invalid subId=" + subId);
        }
    }

    /**
     * Mark all subscriptions on this SIM slot index inactive.
     *
     * @param simSlotIndex The SIM slot index.
     */
    public void markSubscriptionsInactive(int simSlotIndex) {
        mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                .filter(subInfo -> subInfo.getSimSlotIndex() == simSlotIndex)
                .forEach(subInfo -> mSubscriptionDatabaseManager.setSimSlotIndex(
                        subInfo.getSubscriptionId(), SubscriptionManager.INVALID_SIM_SLOT_INDEX));
    }

    /**
     * This is only for internal use and the returned priority is arbitrary. The idea is to give a
     * higher value to name source that has higher priority to override other name sources.
     *
     * @param nameSource Source of display name.
     *
     * @return The priority. Higher value means higher priority.
     */
    private static int getNameSourcePriority(@SimDisplayNameSource int nameSource) {
        int index = Arrays.asList(
                SubscriptionManager.NAME_SOURCE_UNKNOWN,
                SubscriptionManager.NAME_SOURCE_CARRIER_ID,
                SubscriptionManager.NAME_SOURCE_SIM_PNN,
                SubscriptionManager.NAME_SOURCE_SIM_SPN,
                SubscriptionManager.NAME_SOURCE_CARRIER,
                SubscriptionManager.NAME_SOURCE_USER_INPUT // user has highest priority.
        ).indexOf(nameSource);
        return Math.max(0, index);
    }

    /**
     * Get the embedded profile port index by ICCID.
     *
     * @param iccId The ICCID.
     * @return The port index.
     */
    private int getEmbeddedProfilePortIndex(String iccId) {
        UiccSlot[] slots = UiccController.getInstance().getUiccSlots();
        for (UiccSlot slot : slots) {
            if (slot != null && slot.isEuicc()
                    && slot.getPortIndexFromIccId(iccId) != TelephonyManager.INVALID_PORT_INDEX) {
                return slot.getPortIndexFromIccId(iccId);
            }
        }
        return TelephonyManager.INVALID_PORT_INDEX;
    }

    /**
     * Pull the embedded subscription from {@link EuiccController} for the eUICC with the given list
     * of card IDs {@code cardIds}.
     *
     * @param cardIds The card ids of the embedded subscriptions.
     * @param callback Callback to be called upon completion.
     */
    public void updateEmbeddedSubscriptions(@NonNull List<Integer> cardIds,
            @Nullable Runnable callback) {
        mHandler.post(() -> {
            // Do nothing if eUICCs are disabled. (Previous entries may remain in the cache, but
            // they are filtered out of list calls as long as EuiccManager.isEnabled returns false).
            if (mEuiccManager == null || !mEuiccManager.isEnabled()) {
                loge("updateEmbeddedSubscriptions: eUICC not enabled");
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            log("updateEmbeddedSubscriptions: start to get euicc profiles.");
            for (int cardId : cardIds) {
                GetEuiccProfileInfoListResult result = mEuiccController
                        .blockingGetEuiccProfileInfoList(cardId);
                log("updateEmbeddedSubscriptions: cardId=" + cardId + ", result=" + result);

                if (result.getResult() != EuiccService.RESULT_OK) {
                    loge("Failed to get euicc profile info. result="
                            + EuiccService.resultToString(result.getResult()));
                    continue;
                }

                if (result.getProfiles() == null || result.getProfiles().isEmpty()) {
                    loge("No profiles returned.");
                    continue;
                }

                final boolean isRemovable = result.getIsRemovable();

                for (EuiccProfileInfo embeddedProfile : result.getProfiles()) {
                    SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                            .getSubscriptionInfoInternalByIccId(embeddedProfile.getIccid());

                    // The subscription does not exist in the database. Insert a new one here.
                    if (subInfo == null) {
                        subInfo = new SubscriptionInfoInternal.Builder()
                                .setIccId(embeddedProfile.getIccid())
                                .build();
                        int subId = mSubscriptionDatabaseManager.insertSubscriptionInfo(subInfo);
                        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                                .setId(subId).build();
                    }

                    int nameSource = subInfo.getDisplayNameSource();
                    int carrierId = subInfo.getCarrierId();

                    SubscriptionInfoInternal.Builder builder = new SubscriptionInfoInternal
                            .Builder(subInfo);

                    builder.setEmbedded(1);

                    List<UiccAccessRule> ruleList = embeddedProfile.getUiccAccessRules();
                    if (ruleList != null && !ruleList.isEmpty()) {
                        builder.setNativeAccessRules(embeddedProfile.getUiccAccessRules());
                    }
                    builder.setRemovableEmbedded(isRemovable ? 1 : 0);

                    // override DISPLAY_NAME if the priority of existing nameSource is <= carrier
                    if (getNameSourcePriority(nameSource) <= getNameSourcePriority(
                            SubscriptionManager.NAME_SOURCE_CARRIER)) {
                        builder.setDisplayName(embeddedProfile.getNickname());
                        builder.setDisplayNameSource(SubscriptionManager.NAME_SOURCE_CARRIER);
                    }
                    builder.setProfileClass(embeddedProfile.getProfileClass());
                    builder.setPortIndex(getEmbeddedProfilePortIndex(embeddedProfile.getIccid()));

                    CarrierIdentifier cid = embeddedProfile.getCarrierIdentifier();
                    if (cid != null) {
                        // Due to the limited subscription information, carrier id identified here
                        // might not be accurate compared with CarrierResolver. Only update carrier
                        // id if there is no valid carrier id present.
                        if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
                            builder.setCarrierId(CarrierResolver
                                    .getCarrierIdFromIdentifier(mContext, cid));
                        }
                        String mcc = cid.getMcc();
                        String mnc = cid.getMnc();
                        builder.setMcc(mcc);
                        builder.setMnc(mnc);
                    }
                    // If cardId = unsupported or un-initialized, we have no reason to update DB.
                    // Additionally, if the device does not support cardId for default eUICC, the
                    // CARD_ID field should not contain the EID
                    if (cardId >= 0 && mUiccController.getCardIdForDefaultEuicc()
                            != TelephonyManager.UNSUPPORTED_CARD_ID) {
                        builder.setCardString(mUiccController.convertToCardString(cardId));
                    }

                    subInfo = builder.build();
                    log("updateEmbeddedSubscriptions: update subscription " + subInfo);
                    mSubscriptionDatabaseManager.updateSubscription(subInfo);
                }
            }
        });
        log("updateEmbeddedSubscriptions: Finished embedded subscription update.");
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * Get all subscription info records from SIMs that are inserted now or were inserted before.
     *
     * <p>
     * If the caller does not have {@link Manifest.permission#READ_PHONE_NUMBERS} permission,
     * {@link SubscriptionInfo#getNumber()} will return empty string.
     * If the caller does not have {@link Manifest.permission#USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER},
     * {@link SubscriptionInfo#getIccId()} and {@link SubscriptionInfo#getCardString()} will return
     * empty string, and {@link SubscriptionInfo#getGroupUuid()} will return {@code null}.
     *
     * <p>
     * The carrier app will always have full {@link SubscriptionInfo} for the subscriptions
     * that it has carrier privilege. Subscriptions that the carrier app has no privilege will be
     * excluded from the list.
     *
     * @return List of all {@link SubscriptionInfo} records from SIMs that are inserted or
     * inserted before. Sorted by {@link SubscriptionInfo#getSimSlotIndex()}, then
     * {@link SubscriptionInfo#getSubscriptionId()}.
     *
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @throws SecurityException if the caller does not have required permissions.
     *
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public List<SubscriptionInfo> getAllSubInfoList(@NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        // Check if the caller has READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or carrier
        // privilege on any active subscription. The carrier app will get full subscription infos
        // on the subs it has carrier privilege.
        if (!TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(mContext,
                Binder.getCallingPid(), Binder.getCallingUid(), callingPackage, callingFeatureId,
                "getAllSubInfoList")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    // callers have READ_PHONE_STATE or READ_PRIVILEGED_PHONE_STATE can get a full
                    // list. Carrier apps can only get the subscriptions they have privileged.
                    .filter(subInfo -> TelephonyPermissions.checkCallingOrSelfReadPhoneStateNoThrow(
                            mContext, subInfo.getSubscriptionId(), callingPackage, callingFeatureId,
                            "getAllSubInfoList"))
                    // Remove the identifier if the caller does not have sufficient permission.
                    // carrier apps will get full subscription info on the subscriptions associated
                    // to them.
                    .map(subInfo -> conditionallyRemoveIdentifiers(subInfo.toSubscriptionInfo(),
                            callingPackage, callingFeatureId, "getAllSubInfoList"))
                    .sorted(Comparator.comparing(SubscriptionInfo::getSimSlotIndex)
                            .thenComparing(SubscriptionInfo::getSubscriptionId))
                    .collect(Collectors.toList());

        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the active {@link SubscriptionInfo} with the subscription id key.
     *
     * @param subId The unique {@link SubscriptionInfo} key in database
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The subscription info.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @Nullable
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public SubscriptionInfo getActiveSubscriptionInfo(int subId, @NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mContext, subId, callingPackage,
                callingFeatureId, "getActiveSubscriptionInfo")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }

        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid sub id " + subId);
        }

        SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                .getSubscriptionInfoInternal(subId);
        if (subInfo.isActive()) {
            return conditionallyRemoveIdentifiers(subInfo.toSubscriptionInfo(), callingPackage,
                    callingFeatureId, "getActiveSubscriptionInfo");
        }
        return null;
    }

    /**
     * Get the active {@link SubscriptionInfo} associated with the iccId.
     *
     * @param iccId the IccId of SIM card
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The subscription info.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @Nullable
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public SubscriptionInfo getActiveSubscriptionInfoForIccId(@NonNull String iccId,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        enforcePermissions("getActiveSubscriptionInfoForIccId",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            iccId = IccUtils.stripTrailingFs(iccId);
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternalByIccId(iccId);

            return (subInfo != null && subInfo.isActive()) ? subInfo.toSubscriptionInfo() : null;

        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the active {@link SubscriptionInfo} associated with the logical SIM slot index.
     *
     * @param slotIndex the logical SIM slot index which the subscription is inserted.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return {@link SubscriptionInfo}, null for Remote-SIMs or non-active logical SIM slot index.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @Nullable
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public SubscriptionInfo getActiveSubscriptionInfoForSimSlotIndex(int slotIndex,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        int subId = mSlotIndexToSubId.getOrDefault(slotIndex,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mContext, subId,
                callingPackage, callingFeatureId,
                "getActiveSubscriptionInfoForSimSlotIndex")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");

        }

        if (!SubscriptionManager.isValidSlotIndex(slotIndex)) {
            throw new IllegalArgumentException("Invalid slot index " + slotIndex);
        }

        SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                .getSubscriptionInfoInternal(subId);
        if (subInfo != null && subInfo.isActive()) {
            return conditionallyRemoveIdentifiers(subInfo.toSubscriptionInfo(), callingPackage,
                    callingFeatureId, "getActiveSubscriptionInfoForSimSlotIndex");
        }

        return null;
    }

    /**
     * Get the SubscriptionInfo(s) of the active subscriptions. The records will be sorted
     * by {@link SubscriptionInfo#getSimSlotIndex} then by
     * {@link SubscriptionInfo#getSubscriptionId}.
     *
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return Sorted list of the currently {@link SubscriptionInfo} records available on the
     * device.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public List<SubscriptionInfo> getActiveSubscriptionInfoList(@NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        // Check if the caller has READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or carrier
        // privilege on any active subscription. The carrier app will get full subscription infos
        // on the subs it has carrier privilege.
        if (!TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(mContext,
                Binder.getCallingPid(), Binder.getCallingUid(), callingPackage, callingFeatureId,
                "getAllSubInfoList")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    .filter(SubscriptionInfoInternal::isActive)
                    // Remove the identifier if the caller does not have sufficient permission.
                    // carrier apps will get full subscription info on the subscriptions associated
                    // to them.
                    .map(subInfo -> conditionallyRemoveIdentifiers(subInfo.toSubscriptionInfo(),
                            callingPackage, callingFeatureId, "getAllSubInfoList"))
                    .sorted(Comparator.comparing(SubscriptionInfo::getSimSlotIndex)
                            .thenComparing(SubscriptionInfo::getSubscriptionId))
                    .collect(Collectors.toList());

        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the number of active {@link SubscriptionInfo}.
     *
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return the number of active subscriptions.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public int getActiveSubInfoCount(@NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        if (!TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(mContext,
                Binder.getCallingPid(), Binder.getCallingUid(), callingPackage, callingFeatureId,
                "getAllSubInfoList")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            return getActiveSubIdList(false).length;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * @return the maximum number of subscriptions this device will support at any one time.
     */
    @Override
    public int getActiveSubInfoCountMax() {
        return mTelephonyManager.getSimCount();
    }

    /**
     * Gets the SubscriptionInfo(s) of all available subscriptions, if any.
     *
     * Available subscriptions include active ones (those with a non-negative
     * {@link SubscriptionInfo#getSimSlotIndex()}) as well as inactive but installed embedded
     * subscriptions.
     *
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The available subscription info.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @NonNull
    public List<SubscriptionInfo> getAvailableSubscriptionInfoList(@NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        enforcePermissions("getAvailableSubscriptionInfoList",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        // Now that all security checks pass, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            // Available eSIM profiles are reported by EuiccManager. However for physical SIMs if
            // they are in inactive slot or programmatically disabled, they are still considered
            // available. In this case we get their iccid from slot info and include their
            // subscriptionInfos.
            List<String> iccIds = getIccIdsOfInsertedPhysicalSims();

            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    .filter(subInfo -> subInfo.isActive() || iccIds.contains(subInfo.getIccId())
                            || (mEuiccManager != null && mEuiccManager.isEnabled()
                            && subInfo.isEmbedded()))
                    .map(SubscriptionInfoInternal::toSubscriptionInfo)
                    .sorted(Comparator.comparing(SubscriptionInfo::getSimSlotIndex)
                            .thenComparing(SubscriptionInfo::getSubscriptionId))
                    .collect(Collectors.toList());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Gets the SubscriptionInfo(s) of all embedded subscriptions accessible to the calling app, if
     * any.
     *
     * <p>Only those subscriptions for which the calling app has carrier privileges per the
     * subscription metadata, if any, will be included in the returned list.
     *
     * <p>The records will be sorted by {@link SubscriptionInfo#getSimSlotIndex} then by
     * {@link SubscriptionInfo#getSubscriptionId}.
     *
     * @return Sorted list of the current embedded {@link SubscriptionInfo} records available on the
     * device which are accessible to the caller.
     * <ul>
     * <li>
     *
     * if the list is non-empty the list is sorted by {@link SubscriptionInfo#getSimSlotIndex}
     * then by {@link SubscriptionInfo#getSubscriptionId}.
     * </ul>
     *
     * @param callingPackage The package making the call.
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    public List<SubscriptionInfo> getAccessibleSubscriptionInfoList(
            @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        if (!mEuiccManager.isEnabled()) {
            return null;
        }

        return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                .map(SubscriptionInfoInternal::toSubscriptionInfo)
                .filter(subInfo -> mSubscriptionManager
                        .canManageSubscription(subInfo, callingPackage))
                .sorted(Comparator.comparing(SubscriptionInfo::getSimSlotIndex)
                        .thenComparing(SubscriptionInfo::getSubscriptionId))
                .collect(Collectors.toList());
    }

    /**
     * @see SubscriptionManager#requestEmbeddedSubscriptionInfoListRefresh
     */
    @Override
    public void requestEmbeddedSubscriptionInfoListRefresh(int cardId) {

    }

    /**
     * Add a new subscription info record, if needed. This should be only used for remote SIM.
     *
     * @param iccId ICCID of the SIM card.
     * @param displayName human-readable name of the device the subscription corresponds to.
     * @param slotIndex the logical SIM slot index assigned to this device.
     * @param subscriptionType the type of subscription to be added
     *
     * @return 0 if success, < 0 on error
     *
     * @throws SecurityException if the caller does not have required permissions.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int addSubInfo(@NonNull String iccId, @NonNull String displayName, int slotIndex,
            @SubscriptionType int subscriptionType) {
        log("addSubInfo: iccId=" + SubscriptionInfo.givePrintableIccid(iccId) + ", slotIndex="
                + slotIndex + ", displayName=" + displayName + ", type="
                + SubscriptionManager.subscriptionTypeToString(subscriptionType));
        enforcePermissions("addSubInfo", Manifest.permission.MODIFY_PHONE_STATE);

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            if (TextUtils.isEmpty(iccId)) {
                loge("addSubInfo: null or empty iccId");
                return -1;
            }

            iccId = IccUtils.stripTrailingFs(iccId);
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternalByIccId(iccId);

            // Check if the record exists or not.
            if (subInfo == null) {
                // Record does not exist.
                if (mSlotIndexToSubId.containsKey(slotIndex)) {
                    loge("Already a subscription on slot " + slotIndex);
                    return -1;
                }
                int subId = mSubscriptionDatabaseManager.insertSubscriptionInfo(
                        new SubscriptionInfoInternal.Builder()
                                .setIccId(iccId)
                                .setSimSlotIndex(slotIndex)
                                .setDisplayName(displayName)
                                .setType(subscriptionType)
                                .build()
                );
                mSlotIndexToSubId.put(slotIndex, subId);
            } else {
                // Record already exists.
                loge("Subscription record already existed.");
                return -1;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return 0;

    }

    /**
     * Remove subscription info record for the given device.
     *
     * @param uniqueId This is the unique identifier for the subscription within the specific
     * subscription type.
     * @param subscriptionType the type of subscription to be removed
     *
     * @return 0 if success, < 0 on error
     */
    @Override
    public int removeSubInfo(@NonNull String uniqueId, int subscriptionType) {
        return 0;
    }

    /**
     * Set SIM icon tint color by simInfo index.
     *
     * @param subId the unique subscription index in database
     * @param tint the icon tint color of the SIM
     *
     * @return the number of records updated
     *
     * @throws IllegalArgumentException if {@code subId} is invalid or the subscription does not
     * exist.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setIconTint(int subId, @ColorInt int tint) {
        enforcePermissions("setIconTint", Manifest.permission.MODIFY_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                throw new IllegalArgumentException("Invalid sub id passed as parameter");
            }

            mSubscriptionDatabaseManager.setIconTint(subId, tint);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set display name of a subscription.
     *
     * @param displayName The display name of SIM card.
     * @param subId The subscription id.
     * @param nameSource The display name source.
     *
     * @return the number of records updated
     *
     * @throws IllegalArgumentException if {@code nameSource} is invalid, or {@code subId} is
     * invalid.
     * @throws NullPointerException if {@code displayName} is {@code null}.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setDisplayNameUsingSrc(@NonNull String displayName, int subId,
            @SimDisplayNameSource int nameSource) {
        enforcePermissions("setDisplayNameUsingSrc", Manifest.permission.MODIFY_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            Objects.requireNonNull(displayName, "setDisplayNameUsingSrc");

            if (nameSource < SubscriptionManager.NAME_SOURCE_CARRIER_ID
                    || nameSource > SubscriptionManager.NAME_SOURCE_SIM_PNN) {
                throw new IllegalArgumentException("illegal name source " + nameSource);
            }

            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);

            if (subInfo == null) {
                throw new IllegalArgumentException("Cannot find subscription info with sub id "
                        + subId);
            }

            if (getNameSourcePriority(subInfo.getDisplayNameSource())
                    > getNameSourcePriority(nameSource)
                    || (getNameSourcePriority(subInfo.getDisplayNameSource())
                    == getNameSourcePriority(nameSource))
                    && (TextUtils.equals(displayName, subInfo.getDisplayName()))) {
                log("No need to update the display name. nameSource="
                        + SubscriptionManager.displayNameSourceToString(nameSource)
                        + ", existing name=" + subInfo.getDisplayName() + ", source="
                        + SubscriptionManager.displayNameSourceToString(
                                subInfo.getDisplayNameSource()));
                return 0;
            }

            String nameToSet;
            if (TextUtils.isEmpty(displayName) || displayName.trim().length() == 0) {
                nameToSet = mTelephonyManager.getSimOperatorName(subId);
                if (TextUtils.isEmpty(nameToSet)) {
                    if (nameSource == SubscriptionManager.NAME_SOURCE_USER_INPUT
                            && SubscriptionManager.isValidSlotIndex(getSlotIndex(subId))) {
                        Resources r = Resources.getSystem();
                        nameToSet = r.getString(R.string.default_card_name,
                                (getSlotIndex(subId) + 1));
                    } else {
                        nameToSet = mContext.getString(SubscriptionManager.DEFAULT_NAME_RES);
                    }
                }
            } else {
                nameToSet = displayName;
            }

            mSubscriptionDatabaseManager.setDisplayName(subId, nameToSet);
            mSubscriptionDatabaseManager.setDisplayNameSource(subId, nameSource);

            // Update the nickname on the eUICC chip if it's an embedded subscription.
            SubscriptionInfo sub = getSubscriptionInfo(subId);
            if (sub != null && sub.isEmbedded()) {
                int cardId = sub.getCardId();
                log("Updating embedded sub nickname on cardId: " + cardId);
                mEuiccManager.updateSubscriptionNickname(subId, nameToSet,
                        // This PendingIntent simply fulfills the requirement to pass in a callback;
                        // we don't care about the result (hence 0 requestCode and no action
                        // specified on the intent).
                        PendingIntent.getService(mContext, 0 /* requestCode */, new Intent(),
                                PendingIntent.FLAG_IMMUTABLE /* flags */));
            }

            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set phone number by subscription id.
     *
     * @param number the phone number of the SIM.
     * @param subId the unique SubscriptionInfo index in database.
     *
     * @return the number of records updated.
     *
     * @throws SecurityException if callers do not hold the required permission.
     * @throws NullPointerException if {@code number} is {@code null}.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setDisplayNumber(@NonNull String number, int subId) {
        enforcePermissions("setDisplayNumber", Manifest.permission.MODIFY_PHONE_STATE);

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            mSubscriptionDatabaseManager.setDisplayName(subId, number);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set data roaming by simInfo index
     *
     * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
     * @param subId the unique SubscriptionInfo index in database
     *
     * @return the number of records updated
     *
     * @throws IllegalArgumentException if {@code subId} or {@code roaming} is not valid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setDataRoaming(@DataRoamingMode int roaming, int subId) {
        enforcePermissions("setDataRoaming", Manifest.permission.MODIFY_PHONE_STATE);

        // Now that all security checks passes, perform the operation as ourselves.
        final long identity = Binder.clearCallingIdentity();
        try {
            if (roaming < 0) {
                throw new IllegalArgumentException("Invalid roaming value " + roaming);
            }

            mSubscriptionDatabaseManager.setDataRoaming(subId, roaming);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Switch to a certain subscription.
     *
     * @param opportunistic whether it’s opportunistic subscription
     * @param subId the unique SubscriptionInfo index in database
     * @param callingPackage The package making the call
     *
     * @return the number of records updated
     *
     * @throws IllegalArgumentException if {@code subId} is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    public int setOpportunistic(boolean opportunistic, int subId, @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(
                mContext, Binder.getCallingUid(), subId, true, "setOpportunistic",
                Manifest.permission.MODIFY_PHONE_STATE);

        long token = Binder.clearCallingIdentity();
        try {
            mSubscriptionDatabaseManager.setOpportunistic(subId, opportunistic);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Inform SubscriptionManager that subscriptions in the list are bundled as a group. Typically
     * it's a primary subscription and an opportunistic subscription. It should only affect
     * multi-SIM scenarios where primary and opportunistic subscriptions can be activated together.
     *
     * Being in the same group means they might be activated or deactivated together, some of them
     * may be invisible to the users, etc.
     *
     * Caller will either have {@link Manifest.permission#MODIFY_PHONE_STATE} permission or
     * can manage all subscriptions in the list, according to their access rules.
     *
     * @param subIdList list of subId that will be in the same group.
     * @param callingPackage The package making the call.
     *
     * @return groupUUID a UUID assigned to the subscription group. It returns null if fails.
     *
     * @throws IllegalArgumentException if {@code subId} is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(anyOf = {
            Manifest.permission.MODIFY_PHONE_STATE,
            "carrier privileges",
    })
    public ParcelUuid createSubscriptionGroup(@NonNull int[] subIdList,
            @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        Objects.requireNonNull(subIdList, "createSubscriptionGroup");
        if (subIdList.length == 0) {
            throw new IllegalArgumentException("Invalid subIdList " + Arrays.toString(subIdList));
        }

        // If it doesn't have modify phone state permission, or carrier privilege permission,
        // a SecurityException will be thrown.
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED && !checkCarrierPrivilegeOnSubList(
                        subIdList, callingPackage)) {
            throw new SecurityException("CreateSubscriptionGroup needs MODIFY_PHONE_STATE or"
                    + " carrier privilege permission on all specified subscriptions");
        }

        long identity = Binder.clearCallingIdentity();

        try {
            // Generate a UUID.
            ParcelUuid groupUUID = new ParcelUuid(UUID.randomUUID());
            String uuidString = groupUUID.toString();

            for (int subId : subIdList) {
                mSubscriptionDatabaseManager.setGroupUuid(subId, uuidString);
                mSubscriptionDatabaseManager.setGroupOwner(subId, callingPackage);
            }

            MultiSimSettingController.getInstance().notifySubscriptionGroupChanged(groupUUID);
            return groupUUID;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set which subscription is preferred for cellular data. It's designed to overwrite default
     * data subscription temporarily.
     *
     * @param subId which subscription is preferred to for cellular data
     * @param needValidation whether validation is needed before switching
     * @param callback callback upon request completion
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setPreferredDataSubscriptionId(int subId, boolean needValidation,
            @Nullable ISetOpportunisticDataCallback callback) {
        enforcePermissions("setPreferredDataSubscriptionId",
                Manifest.permission.MODIFY_PHONE_STATE);
        final long token = Binder.clearCallingIdentity();

        try {
            PhoneSwitcher phoneSwitcher = PhoneSwitcher.getInstance();
            if (phoneSwitcher == null) {
                loge("Set preferred data sub: phoneSwitcher is null.");
                if (callback != null) {
                    try {
                        callback.onComplete(
                                TelephonyManager.SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION);
                    } catch (RemoteException exception) {
                        loge("RemoteException " + exception);
                    }
                }
                return;
            }

            phoneSwitcher.trySetOpportunisticDataSubscription(subId, needValidation, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * @return The subscription id of preferred subscription for cellular data. This reflects
     * the active modem which can serve large amount of cellular data.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public int getPreferredDataSubscriptionId() {
        enforcePermissions("getPreferredDataSubscriptionId",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        final long token = Binder.clearCallingIdentity();

        try {
            PhoneSwitcher phoneSwitcher = PhoneSwitcher.getInstance();
            if (phoneSwitcher == null) {
                loge("getPreferredDataSubscriptionId: PhoneSwitcher not available. Return the "
                        + "default data sub " + getDefaultDataSubId());
                return getDefaultDataSubId();
            }

            return phoneSwitcher.getAutoSelectedDataSubId();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get the opportunistic subscriptions.
     *
     * Callers with {@link Manifest.permission#READ_PHONE_STATE} or
     * {@link Manifest.permission#READ_PRIVILEGED_PHONE_STATE} will have a full list of
     * opportunistic subscriptions. Subscriptions that the carrier app has no privilege will be
     * excluded from the list.
     *
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The list of opportunistic subscription info that can be accessed by the callers.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public List<SubscriptionInfo> getOpportunisticSubscriptions(@NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        // Check if the caller has READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or carrier
        // privilege on any active subscription. The carrier app will get full subscription infos
        // on the subs it has carrier privilege.
        if (!TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(mContext,
                Binder.getCallingPid(), Binder.getCallingUid(), callingPackage, callingFeatureId,
                "getOpportunisticSubscriptions")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    // callers have READ_PHONE_STATE or READ_PRIVILEGED_PHONE_STATE can get a full
                    // list. Carrier apps can only get the subscriptions they have privileged.
                    .filter(subInfo -> subInfo.isOpportunistic()
                            && TelephonyPermissions.checkCallingOrSelfReadPhoneStateNoThrow(
                                    mContext, subInfo.getSubscriptionId(), callingPackage,
                            callingFeatureId, "getOpportunisticSubscriptions"))
                    // Remove the identifier if the caller does not have sufficient permission.
                    // carrier apps will get full subscription info on the subscriptions associated
                    // to them.
                    .map(subInfo -> conditionallyRemoveIdentifiers(subInfo.toSubscriptionInfo(),
                            callingPackage, callingFeatureId, "getOpportunisticSubscriptions"))
                    .sorted(Comparator.comparing(SubscriptionInfo::getSimSlotIndex)
                            .thenComparing(SubscriptionInfo::getSubscriptionId))
                    .collect(Collectors.toList());
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public void removeSubscriptionsFromGroup(int[] subIdList, @NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);
    }

    /**
     * Add a list of subscriptions into a group.
     *
     * Caller should either have {@link android.Manifest.permission#MODIFY_PHONE_STATE}
     * permission or had carrier privilege permission on the subscriptions.
     *
     * @param subIdList list of subId that need adding into the group
     * @param groupUuid the groupUuid the subscriptions are being added to.
     *
     * @throws SecurityException if the caller doesn't meet the requirements outlined above.
     * @throws IllegalArgumentException if the some subscriptions in the list doesn't exist.
     *
     * @see SubscriptionManager#createSubscriptionGroup(List)
     */
    @RequiresPermission(anyOf = {
            Manifest.permission.MODIFY_PHONE_STATE,
            "carrier privileges",
    })
    @Override
    public void addSubscriptionsIntoGroup(@NonNull int[] subIdList, @NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        Objects.requireNonNull(subIdList, "subIdList");
        if (subIdList.length == 0) {
            throw new IllegalArgumentException("Invalid subId list");
        }

        Objects.requireNonNull(groupUuid, "groupUuid");
        String groupUuidString = groupUuid.toString();
        if (groupUuidString.equals(CarrierConfigManager.REMOVE_GROUP_UUID_STRING)) {
            throw new IllegalArgumentException("Invalid groupUuid");
        }

        // If it doesn't have modify phone state permission, or carrier privilege permission,
        // a SecurityException will be thrown.
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
                && !(checkCarrierPrivilegeOnSubList(subIdList, callingPackage)
                && canPackageManageGroup(groupUuid, callingPackage))) {
            throw new SecurityException("Requires MODIFY_PHONE_STATE or carrier privilege"
                    + " permissions on subscriptions and the group.");
        }

        long identity = Binder.clearCallingIdentity();

        try {
            for (int subId : subIdList) {
                mSubscriptionDatabaseManager.setGroupUuid(subId, groupUuidString);
                mSubscriptionDatabaseManager.setGroupOwner(subId, callingPackage);
            }

            MultiSimSettingController.getInstance().notifySubscriptionGroupChanged(groupUuid);
            logl("addSubscriptionsIntoGroup: add subs " + Arrays.toString(subIdList)
                    + " to the group.");
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get subscriptionInfo list of subscriptions that are in the same group of given subId.
     * See {@link #createSubscriptionGroup(int[], String)} for more details.
     *
     * Caller must have {@link android.Manifest.permission#READ_PHONE_STATE}
     * or carrier privilege permission on the subscription.
     *
     * <p>Starting with API level 33, the caller also needs permission to access device identifiers
     * to get the list of subscriptions associated with a group UUID.
     * This method can be invoked if one of the following requirements is met:
     * <ul>
     *     <li>If the app has carrier privilege permission.
     *     {@link TelephonyManager#hasCarrierPrivileges()}
     *     <li>If the app has {@link android.Manifest.permission#READ_PHONE_STATE} permission and
     *     access to device identifiers.
     * </ul>
     *
     * @param groupUuid of which list of subInfo will be returned.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return List of {@link SubscriptionInfo} that belong to the same group, including the given
     * subscription itself. It will return an empty list if no subscription belongs to the group.
     *
     * @throws SecurityException if the caller doesn't meet the requirements outlined above.
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public List<SubscriptionInfo> getSubscriptionsInGroup(@NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        // If the calling app neither has carrier privileges nor READ_PHONE_STATE and access to
        // device identifiers, it will throw a SecurityException.
        if (CompatChanges.isChangeEnabled(REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID,
                Binder.getCallingUid())) {
            try {
                if (!TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mContext,
                        callingPackage, callingFeatureId, "getSubscriptionsInGroup")) {
                    EventLog.writeEvent(0x534e4554, "213902861", Binder.getCallingUid());
                    throw new SecurityException("Need to have carrier privileges or access to "
                            + "device identifiers to call getSubscriptionsInGroup");
                }
            } catch (SecurityException e) {
                EventLog.writeEvent(0x534e4554, "213902861", Binder.getCallingUid());
                throw e;
            }
        }

        return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                .map(SubscriptionInfoInternal::toSubscriptionInfo)
                .filter(info -> groupUuid.equals(info.getGroupUuid())
                        && (mSubscriptionManager.canManageSubscription(info, callingPackage)
                        || TelephonyPermissions.checkCallingOrSelfReadPhoneStateNoThrow(
                                mContext, info.getSubscriptionId(), callingPackage,
                        callingFeatureId, "getSubscriptionsInGroup")))
                .map(subscriptionInfo -> conditionallyRemoveIdentifiers(subscriptionInfo,
                        callingPackage, callingFeatureId, "getSubscriptionsInGroup"))
                .collect(Collectors.toList());
    }

    /**
     * Get slot index associated with the subscription.
     *
     * @param subId The subscription id.
     *
     * @return Logical slot indexx (i.e. phone id) as a positive integer or
     * {@link SubscriptionManager#INVALID_SIM_SLOT_INDEX} if the supplied {@code subId} doesn't have
     * an associated slot index.
     */
    @Override
    public int getSlotIndex(int subId) {
        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            subId = getDefaultSubId();
        }

        for (Map.Entry<Integer, Integer> entry : mSlotIndexToSubId.entrySet()) {
            if (entry.getValue() == subId) return entry.getKey();
        }

        return SubscriptionManager.INVALID_SIM_SLOT_INDEX;
    }

    /**
     * Get the subscription id for specified slot index.
     *
     * @param slotIndex Logical SIM slot index.
     * @return The subscription id. {@link SubscriptionManager#INVALID_SUBSCRIPTION_ID} if SIM is
     * absent.
     */
    @Override
    public int getSubId(int slotIndex) {
        if (slotIndex == SubscriptionManager.DEFAULT_SIM_SLOT_INDEX) {
            slotIndex = getSlotIndex(getDefaultSubId());
        }

        // Check that we have a valid slotIndex or the slotIndex is for a remote SIM (remote SIM
        // uses special slot index that may be invalid otherwise)
        if (!SubscriptionManager.isValidSlotIndex(slotIndex)
                && slotIndex != SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        return mSlotIndexToSubId.getOrDefault(slotIndex,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Override
    public int[] getSubIds(int slotIndex) {
        return new int[]{getSubId(slotIndex)};
    }

    /**
     * Update default sub id.
     */
    private void updateDefaultSubId() {
        int subId;
        boolean isVoiceCapable = mTelephonyManager.isVoiceCapable();

        if (isVoiceCapable) {
            subId = getDefaultVoiceSubId();
        } else {
            subId = getDefaultDataSubId();
        }

        // If the subId is not active, use the fist active subscription's subId.
        if (!mSlotIndexToSubId.containsValue(subId)) {
            int[] activeSubIds = getActiveSubIdList(true);
            if (activeSubIds.length > 0) {
                subId = activeSubIds[0];
                log("updateDefaultSubId: First available active sub = " + subId);
            } else {
                subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            }
        }

        if (mDefaultSubId.get() != subId) {
            int phoneId = getPhoneId(subId);
            logl("updateDefaultSubId: Default sub id updated from " + mDefaultSubId.get() + " to "
                    + subId + ", phoneId=" + phoneId);
            mDefaultSubId.set(subId);

            Intent intent = new Intent(SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId, subId);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    @Override
    public int getDefaultSubId() {
        return mDefaultSubId.get();
    }

    @Override
    public int clearSubInfo() {
        return 0;
    }

    @Override
    public int getPhoneId(int subId) {
        // slot index and phone id are equivalent in the current implementation.
        // It is intended NOT to return DEFAULT_PHONE_INDEX any more from this method.
        return getSlotIndex(subId);
    }

    /**
     * @return Subscription id of the default cellular data. This reflects the user's default data
     * choice, which might be a little bit different than the active one returned by
     * {@link #getPreferredDataSubscriptionId()}.
     */
    @Override
    public int getDefaultDataSubId() {
        return mDefaultDataSubId.get();
    }

    /**
     * Set the default data subscription id.
     *
     * @param subId The default data subscription id.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setDefaultDataSubId(int subId) {
        enforcePermissions("setDefaultDataSubId", Manifest.permission.MODIFY_PHONE_STATE);

        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            throw new RuntimeException("setDefaultDataSubId called with DEFAULT_SUBSCRIPTION_ID");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            int oldDefaultDataSubId = mDefaultDataSubId.get();
            if (mDefaultDataSubId.set(subId)) {
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
                logl("Default data subId changed from " + oldDefaultDataSubId + " to " + subId);

                MultiSimSettingController.getInstance().notifyDefaultDataSubChanged();

                Intent intent = new Intent(
                        TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                SubscriptionManager.putSubscriptionIdExtra(intent, subId);
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

                updateDefaultSubId();
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int getDefaultVoiceSubId() {
        return mDefaultVoiceSubId.get();
    }

    /**
     * Set the default voice subscription id.
     *
     * @param subId The default SMS subscription id.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setDefaultVoiceSubId(int subId) {
        enforcePermissions("setDefaultVoiceSubId", Manifest.permission.MODIFY_PHONE_STATE);

        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            throw new RuntimeException("setDefaultVoiceSubId called with DEFAULT_SUB_ID");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            int oldDefaultVoiceSubId = mDefaultVoiceSubId.get();
            if (mDefaultVoiceSubId.set(subId)) {
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
                logl("Default voice subId changed from " + oldDefaultVoiceSubId + " to " + subId);

                Intent intent = new Intent(
                        TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                SubscriptionManager.putSubscriptionIdExtra(intent, subId);
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);

                PhoneAccountHandle newHandle = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                        ? null : mTelephonyManager.getPhoneAccountHandleForSubscriptionId(subId);

                TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
                if (telecomManager != null) {
                    telecomManager.setUserSelectedOutgoingPhoneAccount(newHandle);
                }

                updateDefaultSubId();
            }

        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int getDefaultSmsSubId() {
        return mDefaultSmsSubId.get();
    }

    /**
     * Set the default SMS subscription id.
     *
     * @param subId The default SMS subscription id.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public void setDefaultSmsSubId(int subId) {
        enforcePermissions("setDefaultSmsSubId", Manifest.permission.MODIFY_PHONE_STATE);

        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            throw new RuntimeException("setDefaultSmsSubId called with DEFAULT_SUB_ID");
        }

        final long token = Binder.clearCallingIdentity();
        try {
            int oldDefaultSmsSubId = mDefaultSmsSubId.get();
            if (mDefaultSmsSubId.set(subId)) {
                SubscriptionManager.invalidateSubscriptionManagerServiceCaches();
                logl("Default SMS subId changed from " + oldDefaultSmsSubId + " to " + subId);

                Intent intent = new Intent(
                        SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                SubscriptionManager.putSubscriptionIdExtra(intent, subId);
                mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
            }

        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get the active subscription id list.
     *
     * @param visibleOnly {@code true} if only includes user visible subscription's sub id.
     *
     * @return List of the active subscription id.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public int[] getActiveSubIdList(boolean visibleOnly) {
        enforcePermissions("getActiveSubIdList", Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        final long token = Binder.clearCallingIdentity();
        try {
            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    .filter(subInfo -> subInfo.isActive() && (!visibleOnly || subInfo.isVisible()))
                    .mapToInt(SubscriptionInfoInternal::getSubscriptionId)
                    .toArray();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public int setSubscriptionProperty(int subId, @NonNull String propKey,
            @NonNull String propValue) {
        return 0;
    }

    @Override
    public String getSubscriptionProperty(int subId, @NonNull String propKey,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        return null;
    }

    @Override
    public boolean setSubscriptionEnabled(boolean enable, int subId) {
        return true;
    }

    /**
     * Check if a subscription is active.
     *
     * @param subId The subscription id to check.
     *
     * @return {@code true} if the subscription is active.
     *
     * @throws IllegalArgumentException if the provided slot index is invalid.
     * @throws SecurityException if callers do not hold the required permission.     *
     */
    @Override
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean isSubscriptionEnabled(int subId) {
        enforcePermissions("isSubscriptionEnabled",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        if (!SubscriptionManager.isValidSubscriptionId(subId)) {
            throw new IllegalArgumentException("Invalid subscription id " + subId);
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);
            return subInfo != null && subInfo.isActive();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get the active subscription id by logical SIM slot index.
     *
     * @param slotIndex The logical SIM slot index.
     * @return The active subscription id.
     *
     * @throws IllegalArgumentException if the provided slot index is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public int getEnabledSubscriptionId(int slotIndex) {
        enforcePermissions("getEnabledSubscriptionId",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        if (!SubscriptionManager.isValidSlotIndex(slotIndex)) {
            throw new IllegalArgumentException("Invalid slot index " + slotIndex);
        }

        final long identity = Binder.clearCallingIdentity();
        try {
            return mSubscriptionDatabaseManager.getAllSubscriptions().stream()
                    .filter(subInfo -> subInfo.isActive() && subInfo.getSimSlotIndex() == slotIndex)
                    .mapToInt(SubscriptionInfoInternal::getSubscriptionId)
                    .findFirst()
                    .orElse(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public int getSimStateForSlotIndex(int slotIndex) {
        return 0;
    }

    /**
     * Check if a subscription is active.
     *
     * @param subId The subscription id.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return {@code true} if the subscription is active.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public boolean isActiveSubId(int subId, @NonNull String callingPackage,
            @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        if (!TelephonyPermissions.checkCallingOrSelfReadPhoneState(mContext, subId, callingPackage,
                callingFeatureId, "isActiveSubId")) {
            throw new SecurityException("Need READ_PHONE_STATE, READ_PRIVILEGED_PHONE_STATE, or "
                    + "carrier privilege");
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);
            return subInfo != null && subInfo.isActive();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get active data subscription id. Active data subscription refers to the subscription
     * currently chosen to provide cellular internet connection to the user. This may be
     * different from getDefaultDataSubscriptionId().
     *
     * @return Active data subscription id if any is chosen, or
     * SubscriptionManager.INVALID_SUBSCRIPTION_ID if not.
     *
     * @see android.telephony.TelephonyCallback.ActiveDataSubscriptionIdListener
     */
    @Override
    public int getActiveDataSubscriptionId() {
        final long token = Binder.clearCallingIdentity();
        try {
            PhoneSwitcher phoneSwitcher = PhoneSwitcher.getInstance();
            if (phoneSwitcher != null) {
                int activeDataSubId = phoneSwitcher.getActiveDataSubId();
                if (SubscriptionManager.isUsableSubscriptionId(activeDataSubId)) {
                    return activeDataSubId;
                }
            }
            // If phone switcher isn't ready, or active data sub id is not available, use default
            // sub id from settings.
            return getDefaultDataSubId();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Whether it's supported to disable / re-enable a subscription on a physical (non-euicc) SIM.
     *
     * Physical SIM refers non-euicc, or aka non-programmable SIM.
     *
     * It provides whether a physical SIM card can be disabled without taking it out, which is done
     * via {@link SubscriptionManager#setSubscriptionEnabled(int, boolean)} API.
     *
     * @return whether can disable subscriptions on physical SIMs.
     *
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
    public boolean canDisablePhysicalSubscription() {
        enforcePermissions("canDisablePhysicalSubscription",
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            Phone phone = PhoneFactory.getDefaultPhone();
            return phone != null && phone.canDisablePhysicalSubscription();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set uicc applications being enabled or disabled.
     *
     * The value will be remembered on the subscription and will be applied whenever it's present.
     * If the subscription in currently present, it will also apply the setting to modem
     * immediately (the setting in the modem will not change until the modem receives and responds
     * to the request, but typically this should only take a few seconds. The user visible setting
     * available from {@link SubscriptionInfo#areUiccApplicationsEnabled()} will be updated
     * immediately.)
     *
     * @param enabled whether uicc applications are enabled or disabled.
     * @param subId which subscription to operate on.
     *
     * @return the number of records updated.
     *
     * @throws IllegalArgumentException if the subscription does not exist.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setUiccApplicationsEnabled(boolean enabled, int subId) {
        enforcePermissions("setUiccApplicationsEnabled",
                Manifest.permission.MODIFY_PHONE_STATE);

        long identity = Binder.clearCallingIdentity();
        try {
            mSubscriptionDatabaseManager.setUiccApplicationsEnabled(subId, enabled);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the device to device status sharing user preference for a subscription ID. The setting
     * app uses this method to indicate with whom they wish to share device to device status
     * information.
     *
     * @param sharing the status sharing preference.
     * @param subId the unique Subscription ID in database.
     *
     * @return the number of records updated.
     *
     * @throws IllegalArgumentException if the subscription does not exist, or the sharing
     * preference is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setDeviceToDeviceStatusSharing(@DeviceToDeviceStatusSharingPreference int sharing,
            int subId) {
        enforcePermissions("setDeviceToDeviceStatusSharing",
                Manifest.permission.MODIFY_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            if (sharing < SubscriptionManager.D2D_SHARING_DISABLED
                    || sharing > SubscriptionManager.D2D_SHARING_ALL) {
                throw new IllegalArgumentException("invalid sharing " + sharing);
            }

            mSubscriptionDatabaseManager.setDeviceToDeviceStatusSharingPreference(subId, sharing);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the list of contacts that allow device to device status sharing for a subscription ID.
     * The setting app uses this method to indicate with whom they wish to share device to device
     * status information.
     *
     * @param contacts The list of contacts that allow device to device status sharing
     * @param subId The unique Subscription ID in database.
     *
     * @throws IllegalArgumentException if {@code subId} is invalid.
     * @throws NullPointerException if {@code contacts} is {@code null}.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    public int setDeviceToDeviceStatusSharingContacts(@NonNull String contacts, int subId) {
        enforcePermissions("setDeviceToDeviceStatusSharingContacts",
                Manifest.permission.MODIFY_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            Objects.requireNonNull(contacts, "contacts");
            mSubscriptionDatabaseManager.setDeviceToDeviceStatusSharingContacts(subId, contacts);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the phone number for the given {@code subscriptionId} and {@code source},
     * or an empty string if not available.
     *
     * <p>General apps that need to know the phone number should use
     * {@link SubscriptionManager#getPhoneNumber(int)} instead. This API may be suitable specific
     * apps that needs to know the phone number from a specific source. For example, a carrier app
     * needs to know exactly what's on {@link SubscriptionManager#PHONE_NUMBER_SOURCE_UICC UICC} and
     * decide if the previously set phone number of source
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_CARRIER carrier} should be updated.
     *
     * <p>The API provides no guarantees of what format the number is in: the format can vary
     * depending on the {@code source} and the network etc. Programmatic parsing should be done
     * cautiously, for example, after formatting the number to a consistent format with
     * {@link android.telephony.PhoneNumberUtils#formatNumberToE164(String, String)}.
     *
     * <p>Note the assumption is that one subscription (which usually means one SIM) has
     * only one phone number. The multiple sources backup each other so hopefully at least one
     * is availavle. For example, for a carrier that doesn't typically set phone numbers
     * on {@link SubscriptionManager#PHONE_NUMBER_SOURCE_UICC UICC}, the source
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_IMS IMS} may provide one. Or, a carrier may
     * decide to provide the phone number via source
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_CARRIER carrier} if neither source UICC nor
     * IMS is available.
     *
     * <p>The availability and correctness of the phone number depends on the underlying source
     * and the network etc. Additional verification is needed to use this number for
     * security-related or other sensitive scenarios.
     *
     * @param subId The subscription ID.
     * @param source The source of the phone number.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The phone number, or an empty string if not available.
     *
     * @throws IllegalArgumentException if {@code source} or {@code subId} is invalid.
     * @throws SecurityException if the caller doesn't have permissions required.
     *
     * @see SubscriptionManager#PHONE_NUMBER_SOURCE_UICC
     * @see SubscriptionManager#PHONE_NUMBER_SOURCE_CARRIER
     * @see SubscriptionManager#PHONE_NUMBER_SOURCE_IMS
     *
     * @throws IllegalArgumentException if {@code subId} is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public String getPhoneNumber(int subId, @PhoneNumberSource int source,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(
                mContext, subId, Binder.getCallingUid(), "getPhoneNumber",
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();

        SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                .getSubscriptionInfoInternal(subId);

        if (subInfo == null) {
            throw new IllegalArgumentException("Invalid sub id " + subId);
        }

        try {
            switch(source) {
                case SubscriptionManager.PHONE_NUMBER_SOURCE_UICC:
                    Phone phone = PhoneFactory.getPhone(getPhoneId(subId));
                    if (phone != null) {
                        return TextUtils.emptyIfNull(phone.getLine1Number());
                    } else {
                        return subInfo.getNumber();
                    }
                case SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER:
                    return subInfo.getNumberFromCarrier();
                case SubscriptionManager.PHONE_NUMBER_SOURCE_IMS:
                    return subInfo.getNumberFromIms();
                default:
                    throw new IllegalArgumentException("Invalid number source " + source);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Get phone number from first available source. The order would be
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_CARRIER},
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_UICC}, then
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_IMS}.
     *
     * @param subId The subscription ID.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @return The phone number from the first available source.
     *
     * @throws IllegalArgumentException if {@code subId} is invalid.
     * @throws SecurityException if callers do not hold the required permission.
     */
    @Override
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
            "carrier privileges",
    })
    public String getPhoneNumberFromFirstAvailableSource(int subId,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(
                mContext, subId, Binder.getCallingUid(), "getPhoneNumberFromFirstAvailableSource",
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        final long identity = Binder.clearCallingIdentity();
        try {
            String numberFromCarrier = getPhoneNumber(subId,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, callingPackage,
                    callingFeatureId);
            if (!TextUtils.isEmpty(numberFromCarrier)) {
                return numberFromCarrier;
            }
            String numberFromUicc = getPhoneNumber(
                    subId, SubscriptionManager.PHONE_NUMBER_SOURCE_UICC, callingPackage,
                    callingFeatureId);
            if (!TextUtils.isEmpty(numberFromUicc)) {
                return numberFromUicc;
            }
            String numberFromIms = getPhoneNumber(
                    subId, SubscriptionManager.PHONE_NUMBER_SOURCE_IMS, callingPackage,
                    callingFeatureId);
            if (!TextUtils.isEmpty(numberFromIms)) {
                return numberFromIms;
            }
            return "";
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the phone number of the subscription.
     *
     * @param subId The subscription id.
     * @param source The phone number source.
     * @param number The phone number.
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package.
     *
     * @throws IllegalArgumentException {@code subId} is invalid, or {@code source} is not
     * {@link SubscriptionManager#PHONE_NUMBER_SOURCE_CARRIER}.
     * @throws NullPointerException if {@code number} is {@code null}.
     */
    @Override
    @RequiresPermission("carrier privileges")
    public void setPhoneNumber(int subId, @PhoneNumberSource int source, @NonNull String number,
            @NonNull String callingPackage, @Nullable String callingFeatureId) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        if (!TelephonyPermissions.checkCarrierPrivilegeForSubId(mContext, subId)) {
            throw new SecurityException("setPhoneNumber for CARRIER needs carrier privilege.");
        }

        if (source != SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER) {
            throw new IllegalArgumentException("setPhoneNumber doesn't accept source "
                    + SubscriptionManager.phoneNumberSourceToString(source));
        }

        Objects.requireNonNull(number, "number");

        final long identity = Binder.clearCallingIdentity();
        try {
            mSubscriptionDatabaseManager.setNumberFromCarrier(subId, number);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Set the Usage Setting for this subscription.
     *
     * @param usageSetting the usage setting for this subscription
     * @param subId the unique SubscriptionInfo index in database
     * @param callingPackage The package making the IPC.
     *
     * @throws IllegalArgumentException if the subscription does not exist, or {@code usageSetting}
     * is invalid.
     * @throws SecurityException if doesn't have MODIFY_PHONE_STATE or Carrier Privileges
     */
    @Override
    @RequiresPermission(anyOf = {
            Manifest.permission.MODIFY_PHONE_STATE,
            "carrier privileges",
    })
    public int setUsageSetting(@UsageSetting int usageSetting, int subId,
            @NonNull String callingPackage) {
        // Verify that the callingPackage belongs to the calling UID
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        TelephonyPermissions.enforceAnyPermissionGrantedOrCarrierPrivileges(
                mContext, Binder.getCallingUid(), subId, true, "setUsageSetting",
                Manifest.permission.MODIFY_PHONE_STATE);

        if (usageSetting < SubscriptionManager.USAGE_SETTING_DEFAULT
                || usageSetting > SubscriptionManager.USAGE_SETTING_DATA_CENTRIC) {
            throw new IllegalArgumentException("setUsageSetting: Invalid usage setting: "
                    + usageSetting);
        }

        final long token = Binder.clearCallingIdentity();
        try {
            mSubscriptionDatabaseManager.setUsageSetting(subId, usageSetting);
            return 1;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Set UserHandle for this subscription.
     *
     * @param userHandle the userHandle associated with the subscription
     * Pass {@code null} user handle to clear the association
     * @param subId the unique SubscriptionInfo index in database
     * @return the number of records updated.
     *
     * @throws SecurityException if callers do not hold the required permission.
     * @throws IllegalArgumentException if {@code subId} is invalid.
     */
    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION)
    public int setSubscriptionUserHandle(@Nullable UserHandle userHandle, int subId) {
        enforcePermissions("setSubscriptionUserHandle",
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);

        if (userHandle == null) {
            userHandle = UserHandle.of(UserHandle.USER_NULL);
        }

        long token = Binder.clearCallingIdentity();
        try {
            // This can throw IllegalArgumentException if the subscription does not exist.
            mSubscriptionDatabaseManager.setUserId(subId, userHandle.getIdentifier());
            return 1;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Get UserHandle of this subscription.
     *
     * @param subId the unique SubscriptionInfo index in database
     * @return userHandle associated with this subscription
     * or {@code null} if subscription is not associated with any user.
     *
     * @throws SecurityException if doesn't have required permission.
     * @throws IllegalArgumentException if {@code subId} is invalid.
     */
    @Override
    @RequiresPermission(Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION)
    public UserHandle getSubscriptionUserHandle(int subId) {
        enforcePermissions("getSubscriptionUserHandle",
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);

        long token = Binder.clearCallingIdentity();
        try {
            SubscriptionInfoInternal subInfo = mSubscriptionDatabaseManager
                    .getSubscriptionInfoInternal(subId);
            if (subInfo == null) {
                throw new IllegalArgumentException("getSubscriptionUserHandle: Invalid subId: "
                        + subId);
            }

            UserHandle userHandle = UserHandle.of(subInfo.getUserId());
            if (userHandle.getIdentifier() == UserHandle.USER_NULL) {
                return null;
            }
            return userHandle;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Register the callback for receiving information from {@link SubscriptionManagerService}.
     *
     * @param callback The callback.
     */
    public void registerCallback(@NonNull SubscriptionManagerServiceCallback callback) {
        mSubscriptionManagerServiceCallbacks.add(callback);
    }

    /**
     * Unregister the previously registered {@link SubscriptionManagerServiceCallback}.
     *
     * @param callback The callback to unregister.
     */
    public void unregisterCallback(@NonNull SubscriptionManagerServiceCallback callback) {
        mSubscriptionManagerServiceCallbacks.remove(callback);
    }

    /**
     * Enforce callers have any of the provided permissions.
     *
     * @param message Message to include in the exception.
     * @param permissions The permissions to enforce.
     *
     * @throws SecurityException if the caller does not have any permissions.
     */
    private void enforcePermissions(@Nullable String message, @NonNull String ...permissions) {
        for (String permission : permissions) {
            if (mContext.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException(message + ". Does not have any of the following permissions. "
                + Arrays.toString(permissions));
    }

    /**
     * Get the {@link SubscriptionInfoInternal} by subscription id.
     *
     * @param subId The subscription id.
     *
     * @return The subscription info. {@code null} if not found.
     */
    @Nullable
    public SubscriptionInfoInternal getSubscriptionInfoInternal(int subId) {
        return mSubscriptionDatabaseManager.getSubscriptionInfoInternal(subId);
    }

    /**
     * Get the {@link SubscriptionInfo} by subscription id.
     *
     * @param subId The subscription id.
     *
     * @return The subscription info. {@code null} if not found.
     */
    @Nullable
    public SubscriptionInfo getSubscriptionInfo(int subId) {
        SubscriptionInfoInternal subscriptionInfoInternal = getSubscriptionInfoInternal(subId);
        return subscriptionInfoInternal != null
                ? subscriptionInfoInternal.toSubscriptionInfo() : null;
    }

    /**
     * Log debug messages.
     *
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(LOG_TAG, s);
    }

    /**
     * Log error messages.
     *
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(LOG_TAG, s);
    }

    /**
     * Log verbose messages.
     *
     * @param s debug messages.
     */
    private void logv(@NonNull String s) {
        if (VDBG) Rlog.v(LOG_TAG, s);
    }

    /**
     * Log debug messages and also log into the local log.
     *
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of {@link SubscriptionManagerService}.
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter printWriter,
            @NonNull String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(SubscriptionManagerService.class.getSimpleName() + ":");
        pw.println("All subscriptions:");
        pw.increaseIndent();
        mSubscriptionDatabaseManager.getAllSubscriptions().forEach(pw::println);
        pw.decreaseIndent();
        pw.println("defaultSubId=" + getDefaultSubId());
        pw.println("defaultVoiceSubId=" + getDefaultVoiceSubId());
        pw.println("defaultDataSubId" + getDefaultDataSubId());
        pw.println("activeDataSubId" + getActiveDataSubscriptionId());
        pw.println("defaultSmsSubId" + getDefaultSmsSubId());
        if (mEuiccManager != null) {
            pw.println("Euicc enabled=" + mEuiccManager.isEnabled());
        }
        pw.decreaseIndent();
    }
}
