/*
 * Copyright 2019 The Android Open Source Project
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

import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_NONE;
import static android.telephony.TelephonyManager.EXTRA_SUBSCRIPTION_ID;

import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.stream.Collectors;

/**
 * This class will make sure below setting rules are coordinated across different subscriptions
 * and phones in multi-SIM case:
 *
 * 1) Grouped subscriptions will have same settings for MOBILE_DATA and DATA_ROAMING.
 * 2) Default settings updated automatically. It may be cleared or inherited within group.
 *    If default subscription A switches to profile B which is in the same group, B will
 *    become the new default.
 * 3) For primary subscriptions, only default data subscription will have MOBILE_DATA on.
 */
public class MultiSimSettingController {
    private static final String LOG_TAG = "MultiSimSettingController";
    private static final boolean DBG = true;

    private final Context mContext;
    private final Phone[] mPhones;
    private final SubscriptionController mSubController;
    private boolean mIsAllSubscriptionsLoaded;
    private List<SubscriptionInfo> mPrimarySubList;

    /** The singleton instance. */
    private static MultiSimSettingController sInstance = null;

    /**
     * Return the singleton or create one if not existed.
     */
    public static MultiSimSettingController getInstance() {
        synchronized (SubscriptionController.class) {
            if (sInstance == null) {
                sInstance = new MultiSimSettingController();
            }
            return sInstance;
        }
    }

    @VisibleForTesting
    public MultiSimSettingController() {
        mContext = PhoneFactory.getDefaultPhone().getContext();
        mPhones = PhoneFactory.getPhones();
        mSubController = SubscriptionController.getInstance();
    }

    /**
     * Make sure MOBILE_DATA of subscriptions in same group are synced.
     *
     * If user is enabling a non-default non-opportunistic subscription, make it default
     * data subscription.
     */
    public synchronized void onUserDataEnabled(int subId, boolean enable) {
        if (DBG) log("onUserDataEnabled");
        // Make sure MOBILE_DATA of subscriptions in same group are synced.
        setUserDataEnabledForGroup(subId, enable);

        // If user is enabling a non-default non-opportunistic subscription, make it default.
        if (mSubController.getDefaultDataSubId() != subId && !mSubController.isOpportunistic(subId)
                && enable) {
            mSubController.setDefaultDataSubId(subId);
        }
    }

    /**
     * Make sure DATA_ROAMING of subscriptions in same group are synced.
     */
    public synchronized void onRoamingDataEnabled(int subId, boolean enable) {
        if (DBG) log("onRoamingDataEnabled");
        setRoamingDataEnabledForGroup(subId, enable);

        // Also inform SubscriptionController as it keeps another copy of user setting.
        mSubController.setDataRoaming(enable ? 1 : 0, subId);
    }

    /**
     * Mark mIsAllSubscriptionsLoaded and update defaults and mobile data enabling.
     */
    public synchronized void onAllSubscriptionsLoaded() {
        if (DBG) log("onAllSubscriptionsLoaded");
        mIsAllSubscriptionsLoaded = true;
        updateDefaults();
        disableDataForNonDefaultNonOpportunisticSubscriptions();
    }

    /**
     * Make sure default values are cleaned or updated.
     *
     * Make sure non-default non-opportunistic subscriptions has data off.
     */
    public synchronized void onSubscriptionsChanged() {
        if (DBG) log("onSubscriptionsChanged");
        if (!mIsAllSubscriptionsLoaded) return;
        updateDefaults();
        disableDataForNonDefaultNonOpportunisticSubscriptions();
    }

    /**
     * Make sure non-default non-opportunistic subscriptions has data disabled.
     */
    public synchronized void onDefaultDataSettingChanged() {
        if (DBG) log("onDefaultDataSettingChanged");
        disableDataForNonDefaultNonOpportunisticSubscriptions();
    }

    /**
     * When a subscription group is created or new subscriptions are added in the group, make
     * sure the settings among them are synced.
     */
    public synchronized void onSubscriptionGroupCreated(int[] subGroup) {
        if (DBG) log("onSubscriptionGroupCreated");
        if (subGroup == null || subGroup.length == 0) return;

        // Get a reference subscription to copy settings from.
        // TODO: the reference sub should be passed in from external caller.
        int refSubId = subGroup[0];
        for (int subId : subGroup) {
            if (mSubController.isActiveSubId(subId) && !mSubController.isOpportunistic(subId)) {
                refSubId = subId;
                break;
            }
        }
        if (DBG) log("refSubId is " + refSubId);

        try {
            boolean enable = GlobalSettingsHelper.getBoolean(
                    mContext, Settings.Global.MOBILE_DATA, refSubId);
            onUserDataEnabled(refSubId, enable);
        } catch (SettingNotFoundException exception) {
            // Do nothing if it's never set.
        }

        try {
            boolean enable = GlobalSettingsHelper.getBoolean(
                    mContext, Settings.Global.DATA_ROAMING, refSubId);
            onRoamingDataEnabled(refSubId, enable);
        } catch (SettingNotFoundException exception) {
            // Do nothing if it's never set.
        }
    }

    /**
     * Automatically update default settings (data / voice / sms).
     *
     * Opportunistic subscriptions can't be default data / voice / sms subscription.
     *
     * 1) If the default subscription is still active, keep it unchanged.
     * 2) Or if there's another active primary subscription that's in the same group,
     *    make it the new default value.
     * 3) Or if there's only one active primary subscription, automatically set default
     *    data subscription on it. Because default data in Android Q is an internal value,
     *    not a user settable value anymore.
     * 4) If non above is met, clear the default value to INVALID.
     */
    @VisibleForTesting
    public synchronized void updateDefaults() {
        if (DBG) log("updateDefaults");

        if (!mIsAllSubscriptionsLoaded) return;

        List<SubscriptionInfo> activeSubInfos = mSubController
                .getActiveSubscriptionInfoList(mContext.getOpPackageName());
        // If subscription controller is not ready, do nothing.
        if (activeSubInfos == null) return;

        List<SubscriptionInfo> prevPrimarySubList = mPrimarySubList;

        // Opportunistic subscriptions can't be default data / voice / sms subscription.
        mPrimarySubList = activeSubInfos.stream().filter(info -> !info.isOpportunistic())
                .collect(Collectors.toList());

        // If there's only one primary subscription active, we trigger PREFERRED_PICK_DIALOG
        // dialog if and only if there were multiple primary SIM cards and one is removed.
        // Otherwise, if user just inserted their first SIM, or there's one primary and one
        // opportunistic subscription active (activeSubInfos.size() > 1), we automatically
        // set the primary to be default SIM and return.
        if (mPrimarySubList.size() == 1 && (activeSubInfos.size() > 1
                || (prevPrimarySubList != null && prevPrimarySubList.isEmpty()))) {
            int subId = mPrimarySubList.get(0).getSubscriptionId();
            if (DBG) log("[updateDefaultValues] to only primary sub " + subId);
            mSubController.setDefaultDataSubId(subId);
            mSubController.setDefaultVoiceSubId(subId);
            mSubController.setDefaultSmsSubId(subId);
            return;
        }

        if (DBG) log("[updateDefaultValues] records: " + mPrimarySubList);

        // TODO: b/121394765 update logic once confirmed the behaviors.
        // Update default data subscription.
        if (DBG) log("[updateDefaultValues] Update default data subscription");
        boolean dataSelected = updateDefaultValue(mPrimarySubList,
                mSubController.getDefaultDataSubId(),
                (newValue -> mSubController.setDefaultDataSubId(newValue)));

        // Update default voice subscription.
        if (DBG) log("[updateDefaultValues] Update default voice subscription");
        boolean voiceSelected = updateDefaultValue(mPrimarySubList,
                mSubController.getDefaultVoiceSubId(),
                (newValue -> mSubController.setDefaultVoiceSubId(newValue)));

        // Update default sms subscription.
        if (DBG) log("[updateDefaultValues] Update default sms subscription");
        boolean smsSelected = updateDefaultValue(mPrimarySubList,
                mSubController.getDefaultSmsSubId(),
                (newValue -> mSubController.setDefaultSmsSubId(newValue)));

        showSimSelectDialogIfNeeded(prevPrimarySubList, dataSelected, voiceSelected, smsSelected);
    }

    private void showSimSelectDialogIfNeeded(List<SubscriptionInfo> prevPrimarySubs,
            boolean dataSelected, boolean voiceSelected, boolean smsSelected) {
        @TelephonyManager.DefaultSubscriptionSelectType
        int dialogType = EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_NONE;
        int preferredSubId = INVALID_SUBSCRIPTION_ID;
        boolean primarySubRemoved = prevPrimarySubs != null
                && mPrimarySubList.size() < prevPrimarySubs.size();
        boolean primarySubAdded = prevPrimarySubs != null
                && mPrimarySubList.size() > prevPrimarySubs.size();

        // If a primary subscription is removed and only one is left active, ask user
        // for preferred sub selection if any default setting is not set.
        // If another primary subscription is added or default data is not selected, ask
        // user to select default for data as it's most important.
        if (mPrimarySubList.size() == 1 && primarySubRemoved
                && (!dataSelected || !smsSelected || !voiceSelected)) {
            dialogType = EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL;
            preferredSubId = mPrimarySubList.get(0).getSubscriptionId();
        } else if (mPrimarySubList.size() > 1 && (!dataSelected || primarySubAdded)) {
            dialogType = EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA;
        }

        if (dialogType != EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_NONE) {
            Intent intent = new Intent();
            intent.setAction(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED);
            intent.setClassName("com.android.settings",
                    "com.android.settings.sim.SimSelectNotification");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, dialogType);
            if (dialogType == EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL) {
                intent.putExtra(EXTRA_SUBSCRIPTION_ID, preferredSubId);
            }
            mContext.sendBroadcast(intent);
        }
    }

    private void disableDataForNonDefaultNonOpportunisticSubscriptions() {
        int defaultDataSub = mSubController.getDefaultDataSubId();

        for (Phone phone : mPhones) {
            if (phone.getSubId() != defaultDataSub
                    && SubscriptionManager.isValidSubscriptionId(phone.getSubId())
                    && !mSubController.isOpportunistic(phone.getSubId())
                    && phone.isUserDataEnabled()) {
                log("setting data to false on " + phone.getSubId());
                phone.getDataEnabledSettings().setUserDataEnabled(false);
            }
        }
    }

    /**
     * Make sure MOBILE_DATA of subscriptions in the same group with the subId
     * are synced.
     */
    private synchronized void setUserDataEnabledForGroup(int subId, boolean enable) {
        log("setUserDataEnabledForGroup subId " + subId + " enable " + enable);
        List<SubscriptionInfo> infoList = mSubController.getSubscriptionsInGroup(
                mSubController.getGroupUuid(subId), mContext.getOpPackageName());

        if (infoList == null) return;

        for (SubscriptionInfo info : infoList) {
            int currentSubId = info.getSubscriptionId();
            if (currentSubId == subId) continue;
            // TODO: simplify when setUserDataEnabled becomes singleton
            if (mSubController.isActiveSubId(currentSubId)) {
                // If we end up enabling two active primary subscriptions, don't enable the
                // non-default data sub. This will only happen if two primary subscriptions
                // in a group are both active. This is not a valid use-case now, but we are
                // handling it just in case.
                if (enable && !mSubController.isOpportunistic(currentSubId)
                        && currentSubId != mSubController.getDefaultSubId()) {
                    loge("Can not enable two active primary subscriptions.");
                    continue;
                }
                // For active subscription, call setUserDataEnabled through DataEnabledSettings.
                Phone phone = PhoneFactory.getPhone(mSubController.getPhoneId(currentSubId));
                // If enable is true and it's not opportunistic subscription, we don't enable it,
                // as there can't e two
                if (phone != null) {
                    phone.getDataEnabledSettings().setUserDataEnabled(enable);
                }
            } else {
                // For inactive subscription, directly write into global settings.
                GlobalSettingsHelper.setBoolean(
                        mContext, Settings.Global.MOBILE_DATA, currentSubId, enable);
            }
        }
    }

    /**
     * Make sure DATA_ROAMING of subscriptions in the same group with the subId
     * are synced.
     */
    private synchronized void setRoamingDataEnabledForGroup(int subId, boolean enable) {
        SubscriptionController subController = SubscriptionController.getInstance();
        List<SubscriptionInfo> infoList = subController.getSubscriptionsInGroup(
                mSubController.getGroupUuid(subId), mContext.getOpPackageName());

        if (infoList == null) return;

        for (SubscriptionInfo info : infoList) {
            // For inactive subscription, directly write into global settings.
            GlobalSettingsHelper.setBoolean(
                    mContext, Settings.Global.DATA_ROAMING, info.getSubscriptionId(), enable);
        }
    }

    private interface UpdateDefaultAction {
        void update(int newValue);
    }

    // Returns whether the new default value is valid.
    private boolean updateDefaultValue(List<SubscriptionInfo> subInfos, int oldValue,
            UpdateDefaultAction action) {
        int newValue = INVALID_SUBSCRIPTION_ID;

        if (subInfos.size() > 0) {
            // Get groupUuid of old
            ParcelUuid groupUuid = mSubController.getGroupUuid(oldValue);

            for (SubscriptionInfo info : subInfos) {
                int id = info.getSubscriptionId();
                if (DBG) log("[updateDefaultValue] Record.id: " + id);
                // If the old subId is still active, or there's another active primary subscription
                // that is in the same group, that should become the new default subscription.
                if (id == oldValue || (groupUuid != null && groupUuid.equals(
                        info.getGroupUuid()))) {
                    log("[updateDefaultValue] updates to subId=" + id);
                    newValue = id;
                    break;
                }
            }
        }

        if (oldValue != newValue) {
            if (DBG) log("[updateDefaultValue: subId] from " + oldValue + " to " + newValue);
            action.update(newValue);
        }

        return SubscriptionManager.isValidSubscriptionId(newValue);
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, msg);
    }
}
