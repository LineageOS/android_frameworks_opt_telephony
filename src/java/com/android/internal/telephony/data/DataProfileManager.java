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
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Telephony;
import android.telephony.Annotation;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.TrafficDescriptor;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * DataProfileManager manages the all {@link DataProfile}s for the current
 * subscription.
 */
public class DataProfileManager extends Handler {
    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for APN database changed. */
    private static final int EVENT_APN_DATABASE_CHANGED = 2;

    /** Event for SIM refresh. */
    private static final int EVENT_SIM_REFRESH = 3;

    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    /** Data network controller. */
    private final @NonNull DataNetworkController mDataNetworkController;

    /** Data config manager. */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Cellular data service. */
    private final @NonNull DataServiceManager mWwanDataServiceManager;

    /** Access networks manager. */
    private final @NonNull AccessNetworksManager mAccessNetworksManager;

    /** All data profiles for the current carrier. */
    private final @NonNull List<DataProfile> mAllDataProfiles = new ArrayList<>();

    /** The data profile used for initial attach. */
    private @Nullable DataProfile mInitialAttachDataProfile = null;

    /** The preferred data profile used for internet. */
    private @Nullable DataProfile mPreferredDataProfile = null;

    /** Preferred data profile set id. */
    private int mPreferredDataProfileSetId = Telephony.Carriers.NO_APN_SET_ID;

    /** Data profile manager callback. */
    private final @NonNull DataProfileManagerCallback mDataProfileManagerCallback;

    /**
     * Data profile manager callback. This should be only used by {@link DataNetworkController}.
     */
    public abstract static class DataProfileManagerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataProfileManagerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when data profiles changed.
         */
        public abstract void onDataProfilesChanged();
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataNetworkController Data network controller.
     * @param dataServiceManager WWAN data service manager.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param callback Data profile manager callback.
     */
    public DataProfileManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController,
            @NonNull DataServiceManager dataServiceManager, @NonNull Looper looper,
            @NonNull DataProfileManagerCallback callback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DPM-" + mPhone.getPhoneId();
        mDataNetworkController = dataNetworkController;
        mWwanDataServiceManager = dataServiceManager;
        mDataConfigManager = dataNetworkController.getDataConfigManager();
        mAccessNetworksManager = phone.getAccessNetworksManager();
        mDataProfileManagerCallback = callback;
        registerAllEvents();
    }

    /**
     * Register for all events that data network controller is interested.
     */
    private void registerAllEvents() {
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mPhone.getContext().getContentResolver().registerContentObserver(
                Telephony.Carriers.CONTENT_URI, true, new ContentObserver(this) {
                    @Override
                    public void onChange(boolean selfChange) {
                        super.onChange(selfChange);
                        sendEmptyMessage(EVENT_APN_DATABASE_CHANGED);
                    }
                });
        mPhone.mCi.registerForIccRefresh(this, EVENT_SIM_REFRESH, null);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_SIM_REFRESH:
                log("SIM refreshed.");
                updateDataProfiles();
                break;
            case EVENT_APN_DATABASE_CHANGED:
                log("APN database changed.");
                updateDataProfiles();
                break;
        }
    }

    private void onApnDatabaseChanged() {
        updateDataProfiles();
    }

    /**
     * Called when data config was updated.
     */
    private void onDataConfigUpdated() {
        updateDataProfiles();

        //TODO: more works needed to be done here.
    }

    /**
     * Update all data profiles, including preferred data profile, and initial attach data profile.
     * Also send those profiles down to the modem if needed.
     */
    private void updateDataProfiles() {
        log("updateDataProfiles");
        List<DataProfile> profiles = new ArrayList<>();
        if (mDataConfigManager.isConfigCarrierSpecific()) {
            Cursor cursor = mPhone.getContext().getContentResolver().query(
                    Uri.withAppendedPath(Telephony.Carriers.SIM_APN_URI, "filtered/subId/"
                            + mPhone.getSubId()), null, null, null, Telephony.Carriers._ID);
            if (cursor == null) {
                loge("Cannot access APN database through telephony provider.");
                return;
            }

            while (cursor.moveToNext()) {
                ApnSetting apn = ApnSetting.makeApnSetting(cursor);
                if (apn != null) {
                    profiles.add(new DataProfile.Builder()
                            .setApnSetting(apn)
                            // TODO: Support TD correctly once ENTERPRISE becomes an APN type.
                            .setTrafficDescriptor(new TrafficDescriptor(apn.getApnName(), null))
                            .setPreferred(false)
                            .build());
                    log("Added " + apn);
                }
            }
            cursor.close();
        }

        // Check if any of the profile already supports IMS, if not, add the default one.
        DataProfile dataProfile = profiles.stream()
                .filter(dp -> dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_IMS))
                .findFirst()
                .orElse(null);
        if (dataProfile == null) {
            profiles.add(new DataProfile.Builder()
                    .setApnSetting(buildDefaultApnSetting("DEFAULT IMS", "ims",
                            ApnSetting.TYPE_IMS))
                    .build());
            log("Added default IMS data profile.");
        }

        // Check if any of the profile already supports EIMS, if not, add the default one.
        dataProfile = profiles.stream()
                .filter(dp -> dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_EIMS))
                .findFirst()
                .orElse(null);
        if (dataProfile == null) {
            profiles.add(new DataProfile.Builder()
                    .setApnSetting(buildDefaultApnSetting("DEFAULT EIMS", "sos",
                            ApnSetting.TYPE_EMERGENCY))
                    .build());
            log("Added default EIMS data profile.");
        }

        log("Found " + profiles.size() + " data profiles. profiles = " + profiles);

        if (mAllDataProfiles.size() != profiles.size() || !mAllDataProfiles.containsAll(profiles)) {
            log("Data profiles changed.");
            mAllDataProfiles.clear();
            mAllDataProfiles.addAll(profiles);
            mDataProfileManagerCallback.invokeFromExecutor(
                    mDataProfileManagerCallback::onDataProfilesChanged);
        }

        mPreferredDataProfileSetId = getPreferredDataProfileSetId();
        updatePreferredDataProfile();

        updateInitialAttachDataProfileAtModem();
        updateDataProfilesAtModem();
    }

    /**
     * @return The preferred data profile set id.
     */
    private int getPreferredDataProfileSetId() {
        // preferapnset uri returns all APNs for the current carrier which have an apn_set_id
        // equal to the preferred APN (if no preferred APN, or if the preferred APN has no set id,
        // the query will return null)
        Cursor cursor = mPhone.getContext().getContentResolver()
                .query(Uri.withAppendedPath(Telephony.Carriers.PREFERRED_APN_SET_URI,
                        String.valueOf(mPhone.getSubId())),
                        new String[] {Telephony.Carriers.APN_SET_ID}, null, null, null);
        if (cursor == null) {
            loge("getPreferredDataProfileSetId: cursor is null");
            return Telephony.Carriers.NO_APN_SET_ID;
        }

        int setId;
        if (cursor.getCount() < 1) {
            loge("getPreferredDataProfileSetId: no APNs found");
            setId = Telephony.Carriers.NO_APN_SET_ID;
        } else {
            cursor.moveToFirst();
            setId = cursor.getInt(0 /* index of Telephony.Carriers.APN_SET_ID */);
        }

        cursor.close();
        return setId;
    }

    /**
     * Update the preferred data profile used for internet.
     */
    private void updatePreferredDataProfile() {
        if (SubscriptionManager.isValidSubscriptionId(mPhone.getSubId())) {
            Cursor cursor = mPhone.getContext().getContentResolver().query(
                    Uri.withAppendedPath(Telephony.Carriers.PREFERRED_APN_URI,
                            String.valueOf(mPhone.getSubId())), null, null, null,
                    Telephony.Carriers.DEFAULT_SORT_ORDER);
            if (cursor != null) {
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    int id = ApnSetting.makeApnSetting(cursor).getId();
                    DataProfile dataProfile = mAllDataProfiles.stream()
                            .filter(dp -> dp.getApnSetting() != null
                                    && dp.getApnSetting().getId() == id)
                            .findFirst()
                            .orElse(null);
                    if (!Objects.equals(mPreferredDataProfile, dataProfile)) {
                        // Replaced the data profile with preferred bit set.
                        dataProfile.setPreferred(true);
                        mPreferredDataProfile = dataProfile;

                        log("Updated preferred data profile with " + mPreferredDataProfile);
                    }
                }
                cursor.close();
            }
        }
    }

    /**
     * Update the data profile used for initial attach.
     *
     * Note that starting from Android 13 only APNs that supports "IA" type will be used for
     * initial attach. Please update APN configuration file if needed.
     */
    private void updateInitialAttachDataProfileAtModem() {
        DataProfile initialAttachDataProfile = null;
        if (mPreferredDataProfile != null
                && mPreferredDataProfile.canSatisfy(NetworkCapabilities.NET_CAPABILITY_IA)) {
            initialAttachDataProfile = mPreferredDataProfile;
        } else {
            initialAttachDataProfile = mAllDataProfiles.stream()
                    .filter(dp -> dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_IA))
                    .findFirst()
                    .orElse(null);
        }

        if (initialAttachDataProfile == null) {
            loge("Cannot find initial attach data profile. APN database needs to be configured"
                    + " correctly.");
            // return here as we can't push a null data profile to the modem as initial attach APN.
            return;
        }

        if (!Objects.equals(mInitialAttachDataProfile, initialAttachDataProfile)) {
            mInitialAttachDataProfile = initialAttachDataProfile;
            log("Initial attach data profile updated as " + mInitialAttachDataProfile);
            mWwanDataServiceManager.setInitialAttachApn(mInitialAttachDataProfile,
                    mPhone.getServiceState().getDataRoamingFromRegistration(), null);
        }
    }

    /**
     * Update the data profiles at modem.
     */
    private void updateDataProfilesAtModem() {
        log("updateDataProfilesAtModem: set " + mAllDataProfiles.size() + " data profiles.");
        mWwanDataServiceManager.setDataProfile(mAllDataProfiles,
                mPhone.getServiceState().getDataRoamingFromRegistration(), null);
    }

    /**
     * Create default apn settings for the apn type like emergency, and ims
     *
     * @param entry Entry name
     * @param apn APN name
     * @param apnTypeBitmask APN type
     * @return The APN setting
     */
    private @NonNull ApnSetting buildDefaultApnSetting(@NonNull String entry,
            @NonNull String apn, @Annotation.ApnType int apnTypeBitmask) {
        return new ApnSetting.Builder()
                .setEntryName(entry)
                .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                .setApnName(apn)
                .setApnTypeBitmask(apnTypeBitmask)
                .setCarrierEnabled(true)
                .setApnSetId(Telephony.Carriers.MATCH_ALL_APN_SET_ID)
                .build();
    }

    /**
     * Get the data profile that can satisfy the network request.
     *
     * @param networkRequest The network request.
     * @param networkType The current data network type.
     * @return The data profile. {@code null} if can't find any satisfiable data profile.
     */
    public @Nullable DataProfile getDataProfileForNetworkRequest(
            @NonNull TelephonyNetworkRequest networkRequest, @NetworkType int networkType) {
        // Step 1: Check if preferred data profile can satisfy the request.
        if (mPreferredDataProfile != null
                && mPreferredDataProfile.canSatisfy(networkRequest.getCapabilities())) {
            return mPreferredDataProfile;
        }

        // Step 2: Filter out the data profile that can't satisfy the request.
        List<DataProfile> dataProfiles = mAllDataProfiles.stream()
                .filter(dp -> dp.canSatisfy(networkRequest.getCapabilities()))
                .collect(Collectors.toList());
        if (dataProfiles.size() == 0) {
            log("Can't find any data profile that can satisfy " + networkRequest);
            return null;
        }

        // Step 3: Check if the remaining data profiles can used in current data network type.
        dataProfiles = dataProfiles.stream()
                .filter(dp -> dp.getApnSetting() != null
                        && dp.getApnSetting().canSupportNetworkType(networkType))
                .collect(Collectors.toList());
        if (dataProfiles.size() == 0) {
            log("Can't find any data profile for network type "
                    + TelephonyManager.getNetworkTypeName(networkType));
            return null;
        }

        // Step 4: Check if preferred data profile set id matches.
        int preferredDataProfileSetId = getPreferredDataProfileSetId();
        dataProfiles = dataProfiles.stream()
                .filter(dp -> dp.getApnSetting() != null
                        && (dp.getApnSetting().getApnSetId()
                        == Telephony.Carriers.MATCH_ALL_APN_SET_ID
                        || dp.getApnSetting().getApnSetId() == preferredDataProfileSetId))
                .collect(Collectors.toList());
        if (dataProfiles.size() == 0) {
            log("Can't find any data profile has APN set id matched. preferredDataProfileSetId="
                    + preferredDataProfileSetId);
            return null;
        }

        // TODO: Need a lot more works here.
        //   1. Should rotate data profiles if multiple data profile can satisfy the request.
        //   2. Should consider data throttling.

        return dataProfiles.get(0);
    }

    /**
     * Get data profiles that can satisfy given network capabilities.
     *
     * @param networkCapabilities The network capabilities.
     * @return data profiles that can satisfy given network capabilities.
     */
    public @NonNull List<DataProfile> getDataProfilesForNetworkCapabilities(
            @NonNull @NetCapability int[] networkCapabilities) {
        return mAllDataProfiles.stream()
                .filter(dp -> dp.canSatisfy(networkCapabilities))
                .sorted((dp1, dp2) ->
                        Long.compare(dp1.getLastSetupTimestamp(), dp2.getLastSetupTimestamp()))
                .collect(Collectors.toList());
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
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataProfileManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataProfileManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();

        pw.println("Data profiles for the current carrier:");
        pw.increaseIndent();
        for (DataProfile dp : mAllDataProfiles) {
            pw.print(dp);
            pw.println(", last setup time: " + DataUtils.elapsedTimeToString(
                    dp.getLastSetupTimestamp()));
        }
        pw.decreaseIndent();
        pw.println("Preferred data profile:");
        pw.increaseIndent();
        pw.println(mPreferredDataProfile);
        pw.decreaseIndent();
        pw.println("Initial attach data profile:");
        pw.increaseIndent();
        pw.println(mInitialAttachDataProfile);
        pw.decreaseIndent();
        pw.println("mPreferredDataProfileSetId=" + mPreferredDataProfileSetId);

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
