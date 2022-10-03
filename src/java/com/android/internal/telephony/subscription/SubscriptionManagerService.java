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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.TelephonyServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.SubscriptionManager.SubscriptionType;
import android.telephony.TelephonyFrameworkInitializer;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.ISub;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * The subscription manager service is the backend service of {@link SubscriptionManager}.
 * The service handles all SIM subscription related requests from clients.
 */
public class SubscriptionManagerService extends ISub.Stub {
    private static final String LOG_TAG = "SMSVC";

    /** Whether enabling verbose debugging message or not. */
    private static final boolean VDBG = false;

    /** The context */
    private final Context mContext;

    /** Local log for most important debug messages. */
    private final LocalLog mLocalLog = new LocalLog(128);

    /**
     * The constructor
     *
     * @param context The context
     */
    public SubscriptionManagerService(@NonNull Context context) {
        mContext = context;
        TelephonyServiceManager.ServiceRegisterer subscriptionServiceRegisterer =
                TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .getSubscriptionServiceRegisterer();
        subscriptionServiceRegisterer.register(this);
    }

    /**
     * @param callingPackage The package making the call.
     * @param callingFeatureId The feature in the package
     * @return a list of all subscriptions in the database, this includes
     * all subscriptions that have been seen.
     */
    @Override
    public List<SubscriptionInfo> getAllSubInfoList(@NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * Get the active {@link SubscriptionInfo} with the subscription id key.
     *
     * @param subId The unique {@link SubscriptionInfo} key in database
     * @param callingPackage The package making the call
     * @param callingFeatureId The feature in the package
     *
     * @return The subscription info.
     */
    @Override
    @Nullable
    public SubscriptionInfo getActiveSubscriptionInfo(int subId, @NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * Get the active {@link SubscriptionInfo} associated with the iccId.
     *
     * @param iccId the IccId of SIM card
     * @param callingPackage The package making the call
     * @param callingFeatureId The feature in the package
     *
     * @return The subscription info.
     */
    @Override
    @Nullable
    public SubscriptionInfo getActiveSubscriptionInfoForIccId(@NonNull String iccId,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * Get the active {@link SubscriptionInfo} associated with the logical SIM slot index.
     *
     * @param slotIndex the logical SIM slot index which the subscription is inserted
     * @param callingPackage The package making the call
     * @param callingFeatureId The feature in the package
     *
     * @return SubscriptionInfo, null for Remote-SIMs or non-active logical SIM slot index.
     */
    @Override
    public SubscriptionInfo getActiveSubscriptionInfoForSimSlotIndex(int slotIndex,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * Get the SubscriptionInfo(s) of the active subscriptions. The records will be sorted
     * by {@link SubscriptionInfo#getSimSlotIndex} then by
     * {@link SubscriptionInfo#getSubscriptionId}.
     *
     * @param callingPackage The package making the call
     * @param callingFeatureId The feature in the package
     * @return Sorted list of the currently {@link SubscriptionInfo} records available on the
     * device.
     * <ul>
     * <li>
     * If null is returned the current state is unknown but if a
     * {@link OnSubscriptionsChangedListener} has been registered
     * {@link OnSubscriptionsChangedListener#onSubscriptionsChanged} will be invoked in the future.
     * </li>
     * <li>
     * If the list is empty then there are no {@link SubscriptionInfo} records currently available.
     * </li>
     * <li>
     * if the list is non-empty the list is sorted by {@link SubscriptionInfo#getSimSlotIndex}
     * then by {@link SubscriptionInfo#getSubscriptionId}.
     * </li>
     * </ul>
     */
    @Override
    public List<SubscriptionInfo> getActiveSubscriptionInfoList(@NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * Get the number of active {@link SubscriptionInfo}.
     *
     * @param callingPackage The package making the call
     * @param callingFeatureId The feature in the package
     * @return the number of active subscriptions
     */
    @Override
    public int getActiveSubInfoCount(@NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return 0;
    }

    /**
     * @return the maximum number of subscriptions this device will support at any one time.
     */
    @Override
    public int getActiveSubInfoCountMax() {
        return 0;
    }

    /**
     * @see SubscriptionManager#getAvailableSubscriptionInfoList
     */
    @Override
    public List<SubscriptionInfo> getAvailableSubscriptionInfoList(@NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return null;
    }

    /**
     * @see SubscriptionManager#getAccessibleSubscriptionInfoList
     */
    @Override
    public List<SubscriptionInfo> getAccessibleSubscriptionInfoList(
            @NonNull String callingPackage) {
        return null;
    }

    /**
     * @see SubscriptionManager#requestEmbeddedSubscriptionInfoListRefresh
     */
    @Override
    public void requestEmbeddedSubscriptionInfoListRefresh(int cardId) {

    }

    /**
     * Add a new subscription record to subscription database if needed.
     *
     * @param iccId the IccId of the SIM card
     * @param slotIndex the slot which the SIM is inserted
     *
     * @return 0 if success, negative if failed.
     */
    @Override
    public int addSubInfoRecord(@NonNull String iccId, int slotIndex) {
        return 0;
    }

    /**
     * Add a new subscription info record, if needed.
     *
     * @param uniqueId This is the unique identifier for the subscription within the specific
     * subscription type
     * @param displayName human-readable name of the device the subscription corresponds to
     * @param slotIndex the slot assigned to this device
     * @param subscriptionType the type of subscription to be added
     *
     * @return 0 if success, < 0 on error
     */
    @Override
    public int addSubInfo(@NonNull String uniqueId, @NonNull String displayName, int slotIndex,
            @SubscriptionType int subscriptionType) {
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
     * @param tint the icon tint color of the SIM
     * @param subId the unique subscription index in database
     *
     * @return the number of records updated
     */
    @Override
    public int setIconTint(int tint, int subId) {
        return 0;
    }

    /**
     * Set display name by simInfo index with name source.
     *
     * @param displayName the display name of SIM card
     * @param subId the unique SubscriptionInfo index in database
     * @param nameSource 0: DEFAULT_SOURCE, 1: SIM_SOURCE, 2: USER_INPUT
     *
     * @return the number of records updated
     */
    @Override
    public int setDisplayNameUsingSrc(@NonNull String displayName, int subId, int nameSource) {
        return 0;
    }

    /**
     * Set phone number by subscription id.
     *
     * @param number the phone number of the SIM
     * @param subId the unique SubscriptionInfo index in database
     *
     * @return the number of records updated
     */
    @Override
    public int setDisplayNumber(@NonNull String number, int subId) {
        return 0;
    }

    /**
     * Set data roaming by simInfo index
     *
     * @param roaming 0:Don't allow data when roaming, 1:Allow data when roaming
     * @param subId the unique SubscriptionInfo index in database
     *
     * @return the number of records updated
     */
    @Override
    public int setDataRoaming(int roaming, int subId) {
        return 0;
    }

    /**
     * Switch to a certain subscription.
     *
     * @param opportunistic whether it’s opportunistic subscription
     * @param subId the unique SubscriptionInfo index in database
     * @param callingPackage The package making the call
     *
     * @return the number of records updated
     */
    @Override
    public int setOpportunistic(boolean opportunistic, int subId, @NonNull String callingPackage) {
        return 0;
    }

    /**
     * Inform SubscriptionManager that subscriptions in the list are bundled as a group. Typically
     * it's a primary subscription and an opportunistic subscription. It should only affect
     * multi-SIM scenarios where primary and opportunistic subscriptions can be activated together.
     *
     * Being in the same group means they might be activated or deactivated together, some of them
     * may be invisible to the users, etc.
     *
     * Caller will either have {@link android.Manifest.permission#MODIFY_PHONE_STATE} permission or
     * can manage all subscriptions in the list, according to their access rules.
     *
     * @param subIdList list of subId that will be in the same group
     * @param callingPackage The package making the call
     *
     * @return groupUUID a UUID assigned to the subscription group. It returns null if fails.
     */
    @Override
    public ParcelUuid createSubscriptionGroup(int[] subIdList, @NonNull String callingPackage) {
        return null;
    }

    /**
     * Set which subscription is preferred for cellular data. It's designed to overwrite default
     * data subscription temporarily.
     *
     * @param subId which subscription is preferred to for cellular data
     * @param needValidation whether validation is needed before switching
     * @param callback callback upon request completion
     */
    @Override
    public void setPreferredDataSubscriptionId(int subId, boolean needValidation,
            @Nullable ISetOpportunisticDataCallback callback) {
    }

    /**
     * @return The subscription id of preferred subscription for cellular data. This reflects
     * the active modem which can serve large amount of cellular data.
     */
    @Override
    public int getPreferredDataSubscriptionId() {
        return 0;
    }

    /**
     * @return The list of opportunistic subscription info that can be accessed by the callers.
     */
    @Override
    @NonNull
    public List<SubscriptionInfo> getOpportunisticSubscriptions(@NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return Collections.emptyList();
    }

    @Override
    public void removeSubscriptionsFromGroup(int[] subIdList, @NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage) {
    }

    @Override
    public void addSubscriptionsIntoGroup(int[] subIdList, @NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage) {
    }

    @Override
    public List<SubscriptionInfo> getSubscriptionsInGroup(@NonNull ParcelUuid groupUuid,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    @Override
    public int getSlotIndex(int subId) {
        return 0;
    }

    @Override
    public int[] getSubId(int slotIndex) {
        return null;
    }

    @Override
    public int getDefaultSubId() {
        return 0;
    }

    @Override
    public int clearSubInfo() {
        return 0;
    }

    @Override
    public int getPhoneId(int subId) {
        return 0;
    }

    /**
     * @return Subscription id of the default cellular data. This reflects the user's default data
     * choice, which might be a little bit different than the active one returned by
     * {@link #getPreferredDataSubscriptionId()}.
     */
    @Override
    public int getDefaultDataSubId() {
        return 0;
    }

    @Override
    public void setDefaultDataSubId(int subId) {
    }

    @Override
    public int getDefaultVoiceSubId() {
        return 0;
    }

    @Override
    public void setDefaultVoiceSubId(int subId) {
    }

    @Override
    public int getDefaultSmsSubId() {
        return 0;
    }

    @Override
    public void setDefaultSmsSubId(int subId) {
    }

    @Override
    public int[] getActiveSubIdList(boolean visibleOnly) {
        return null;
    }

    @Override
    public int setSubscriptionProperty(int subId, @NonNull String propKey,
            @NonNull String propValue) {
        return 0;
    }

    @Override
    public String getSubscriptionProperty(int subId, @NonNull String propKey,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    @Override
    public boolean setSubscriptionEnabled(boolean enable, int subId) {
        return true;
    }

    @Override
    public boolean isSubscriptionEnabled(int subId) {
        return true;
    }

    @Override
    public int getEnabledSubscriptionId(int slotIndex) {
        return 0;
    }

    @Override
    public int getSimStateForSlotIndex(int slotIndex) {
        return 0;
    }

    @Override
    public boolean isActiveSubId(int subId, @NonNull String callingPackage,
            @NonNull String callingFeatureId) {
        return true;
    }

    @Override
    public int getActiveDataSubscriptionId() {
        return 0;
    }

    @Override
    public boolean canDisablePhysicalSubscription() {
        return false;
    }

    @Override
    public int setUiccApplicationsEnabled(boolean enabled, int subscriptionId) {
        return 0;
    }

    @Override
    public int setDeviceToDeviceStatusSharing(int sharing, int subId) {
        return 0;
    }

    @Override
    public int setDeviceToDeviceStatusSharingContacts(@NonNull String contacts,
            int subscriptionId) {
        return 0;
    }

    @Override
    public String getPhoneNumber(int subId, int source,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    @Override
    public String getPhoneNumberFromFirstAvailableSource(int subId,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
        return null;
    }

    @Override
    public void setPhoneNumber(int subId, int source, @NonNull String number,
            @NonNull String callingPackage, @NonNull String callingFeatureId) {
    }

    /**
     * Set the Usage Setting for this subscription.
     *
     * @param usageSetting the usage setting for this subscription
     * @param subId the unique SubscriptionInfo index in database
     * @param callingPackage The package making the IPC.
     *
     * @throws SecurityException if doesn't have MODIFY_PHONE_STATE or Carrier Privileges
     */
    @Override
    public int setUsageSetting(int usageSetting, int subId, @NonNull String callingPackage) {
        return 0;
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
    }
}
