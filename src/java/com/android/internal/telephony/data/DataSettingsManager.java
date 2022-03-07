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
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.sysprop.TelephonyProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyRegistryManager;
import android.telephony.data.ApnSetting;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.GlobalSettingsHelper;
import com.android.internal.telephony.MultiSimSettingController;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SubscriptionController;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * DataSettingsManager maintains the data related settings, for example, data enabled settings,
 * data roaming settings, etc...
 */
public class DataSettingsManager extends Handler {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"REASON_"},
            value = {
                    REASON_INITIALIZED,
                    REASON_USER_DATA_ENABLED,
                    REASON_POLICY_DATA_ENABLED,
                    REASON_CARRIER_DATA_ENABLED,
                    REASON_PROVISIONED_CHANGED,
                    REASON_PROVISIONING_DATA_ENABLED_CHANGED,
                    REASON_OVERRIDE_RULE_CHANGED,
                    REASON_OVERRIDE_CONDITION_CHANGED,
                    REASON_THERMAL_DATA_ENABLED
            })
    public @interface DataEnabledChangedReason {}

    /** Data enabled changed because DataSettingsManager was initialized. */
    public static final int REASON_INITIALIZED = 0;
    /** Data enabled changed because user enabled data. */
    public static final int REASON_USER_DATA_ENABLED = 1;
    /** Data enabled changed because policy data was enabled. */
    public static final int REASON_POLICY_DATA_ENABLED = 2;
    /** Data enabled changed because carrier enabled data. */
    public static final int REASON_CARRIER_DATA_ENABLED = 3;
    /** Data enabled changed because device provisioning status changed. */
    public static final int REASON_PROVISIONED_CHANGED = 4;
    /** Data enabled changed because provisioning data was enabled. */
    public static final int REASON_PROVISIONING_DATA_ENABLED_CHANGED = 5;
    /** Data enabled changed because data override rules changed. */
    public static final int REASON_OVERRIDE_RULE_CHANGED = 6;
    /** Data enabled changed because data override condition changed. */
    public static final int REASON_OVERRIDE_CONDITION_CHANGED = 7;
    /** Data enabled changed because thermal data was enabled. */
    public static final int REASON_THERMAL_DATA_ENABLED = 8;
    /** Data enabled changed because data configs changed. */
    public static final int REASON_DATA_CONFIGS_CHANGED = 9;

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;
    /** Event for call state changed. */
    private static final int EVENT_CALL_STATE_CHANGED = 2;
    /** Event for subscriptions updated. */
    private static final int EVENT_SUBSCRIPTIONS_CHANGED = 4;
    /** Event for set data enabled for reason. */
    private static final int EVENT_SET_DATA_ENABLED_FOR_REASON = 5;
    /** Event for set user data enabled. */
    private static final int EVENT_SET_USER_DATA_ENABLED = 6;
    /** Event for set policy data enabled. */
    private static final int EVENT_SET_POLICY_DATA_ENABLED = 7;
    /** Event for set carrier data enabled. */
    private static final int EVENT_SET_CARRIER_DATA_ENABLED = 8;
    /** Event for set thermal data enabled. */
    private static final int EVENT_SET_THERMAL_DATA_ENABLED = 9;
    /** Event for set data roaming enabled. */
    private static final int EVENT_SET_DATA_ROAMING_ENABLED = 10;
    /** Event for set always allow MMS data. */
    private static final int EVENT_SET_ALWAYS_ALLOW_MMS_DATA = 11;
    /** Event for set allow data during voice call. */
    private static final int EVENT_SET_ALLOW_DATA_DURING_VOICE_CALL = 12;
    /** Event for update data enabled */
    private static final int EVENT_UPDATE_DATA_ENABLED = 13;

    private final Phone mPhone;
    private final ContentResolver mResolver;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);
    private int mSubId;
    private DataEnabledOverride mDataEnabledOverride;

    /** Data config manager */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Data settings manager callback. */
    private final @NonNull DataSettingsManagerCallback mDataSettingsManagerCallback;

    /** Mapping of {@link TelephonyManager.DataEnabledReason} to data enabled values. */
    private final Map<Integer, Boolean> mDataEnabledSettings = new ArrayMap<>();

    /**
     * Flag indicating whether data is allowed or not for the device.
     * It can be disabled by user, carrier, policy or thermal.
     */
    private boolean mIsDataEnabled = false;

    /**
     * Data settings manager callback. This should be only used by {@link DataNetworkController}.
     */
    public abstract static class DataSettingsManagerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataSettingsManagerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when overall data enabled state changed.
         *
         * @param enabled {@code true} indicates mobile data is enabled.
         */
        public abstract void onDataEnabledChanged(boolean enabled);

        /**
         * Called when data roaming enabled state changed.
         *
         * @param enabled {@code true} indicates data roaming is enabled.
         */
        public abstract void onDataRoamingEnabledChanged(boolean enabled);
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataNetworkController Data network controller.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param callback Data settings manager callback.
     */
    public DataSettingsManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController, @NonNull Looper looper,
            @NonNull DataSettingsManagerCallback callback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DSMGR-" + mPhone.getPhoneId();
        log("DataSettingsManager created.");
        mSubId = mPhone.getSubId();
        mResolver = mPhone.getContext().getContentResolver();
        mDataSettingsManagerCallback = callback;
        mDataConfigManager = dataNetworkController.getDataConfigManager();
        mDataEnabledOverride = getDataEnabledOverride();
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mPhone.getCallTracker().registerForVoiceCallStarted(this, EVENT_CALL_STATE_CHANGED, null);
        mPhone.getCallTracker().registerForVoiceCallEnded(this, EVENT_CALL_STATE_CHANGED, null);
        mPhone.getContext().getSystemService(TelephonyRegistryManager.class)
                .addOnSubscriptionsChangedListener(new OnSubscriptionsChangedListener() {
                    @Override
                    public void onSubscriptionsChanged() {
                        if (mSubId != mPhone.getSubId()) {
                            log("onSubscriptionsChanged: " + mSubId + " to " + mPhone.getSubId());
                            obtainMessage(EVENT_SUBSCRIPTIONS_CHANGED, mPhone.getSubId())
                                    .sendToTarget();
                        }
                    }
                }, this::post);
        mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_POLICY, true);
        mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_CARRIER, true);
        mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_THERMAL, true);
        updateDataEnabledAndNotify(REASON_INITIALIZED);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED: {
                updateDataEnabledAndNotify(REASON_DATA_CONFIGS_CHANGED);
                break;
            }
            case EVENT_CALL_STATE_CHANGED: {
                updateDataEnabledAndNotify(REASON_OVERRIDE_CONDITION_CHANGED);
                break;
            }
            case EVENT_SUBSCRIPTIONS_CHANGED: {
                mSubId = (int) msg.obj;
                mDataEnabledOverride = getDataEnabledOverride();
                updateDataEnabledAndNotify(REASON_USER_DATA_ENABLED);
                mPhone.notifyUserMobileDataStateChanged(isUserDataEnabled());
                break;
            }
            case EVENT_SET_DATA_ENABLED_FOR_REASON: {
                boolean enabled = msg.arg2 == 1;
                switch (msg.arg1) {
                    case TelephonyManager.DATA_ENABLED_REASON_USER:
                        setUserDataEnabled(enabled);
                        break;
                    case TelephonyManager.DATA_ENABLED_REASON_CARRIER:
                        setCarrierDataEnabled(enabled);
                        break;
                    case TelephonyManager.DATA_ENABLED_REASON_POLICY:
                        setPolicyDataEnabled(enabled);
                        break;
                    case TelephonyManager.DATA_ENABLED_REASON_THERMAL:
                        setThermalDataEnabled(enabled);
                        break;
                    default:
                        log("Invalid data enable reason " + msg.arg1);
                        break;
                }
                break;
            }
            case EVENT_SET_USER_DATA_ENABLED: {
                boolean enabled = (boolean) msg.obj;
                // Can't disable data for stand alone opportunistic subscription.
                if (isStandAloneOpportunistic(mSubId, mPhone.getContext()) && !enabled) return;
                boolean changed = GlobalSettingsHelper.setInt(mPhone.getContext(),
                        Settings.Global.MOBILE_DATA, mSubId, (enabled ? 1 : 0));
                if (changed) {
                    logl("UserDataEnabled changed to " + enabled);
                    mPhone.notifyUserMobileDataStateChanged(enabled);
                    updateDataEnabledAndNotify(REASON_USER_DATA_ENABLED);
                    MultiSimSettingController.getInstance().notifyUserDataEnabled(mSubId, enabled);
                }
                break;
            }
            case EVENT_SET_POLICY_DATA_ENABLED: {
                boolean enabled = (boolean) msg.obj;
                if (mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_POLICY)
                        != enabled) {
                    logl("PolicyDataEnabled changed to " + enabled);
                    mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_POLICY, enabled);
                    updateDataEnabledAndNotify(REASON_POLICY_DATA_ENABLED);
                }
                break;
            }
            case EVENT_SET_CARRIER_DATA_ENABLED: {
                boolean enabled = (boolean) msg.obj;
                if (mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_CARRIER)
                        != enabled) {
                    logl("CarrierDataEnabled changed to " + enabled);
                    mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_CARRIER, enabled);
                    updateDataEnabledAndNotify(REASON_CARRIER_DATA_ENABLED);
                }
                break;
            }
            case EVENT_SET_THERMAL_DATA_ENABLED: {
                boolean enabled = (boolean) msg.obj;
                if (mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_THERMAL)
                        != enabled) {
                    logl("ThermalDataEnabled changed to " + enabled);
                    mDataEnabledSettings.put(TelephonyManager.DATA_ENABLED_REASON_THERMAL, enabled);
                    updateDataEnabledAndNotify(REASON_THERMAL_DATA_ENABLED);
                }
                break;
            }
            case EVENT_SET_DATA_ROAMING_ENABLED: {
                boolean enabled = (boolean) msg.obj;
                // Will trigger handleDataOnRoamingChange() through observer
                boolean changed = GlobalSettingsHelper.setBoolean(mPhone.getContext(),
                        Settings.Global.DATA_ROAMING, mSubId, enabled);
                if (changed) {
                    logl("DataRoamingEnabled changed to " + enabled);
                    MultiSimSettingController.getInstance().notifyRoamingDataEnabled(mSubId,
                            enabled);
                    mDataSettingsManagerCallback.onDataRoamingEnabledChanged(enabled);
                }
                break;
            }
            case EVENT_SET_ALWAYS_ALLOW_MMS_DATA: {
                boolean alwaysAllow = (boolean) msg.obj;
                if (alwaysAllow == isMmsAlwaysAllowed()) {
                    break;
                }
                logl("AlwaysAllowMmsData changed to " + alwaysAllow);
                mDataEnabledOverride.setAlwaysAllowMms(alwaysAllow);
                if (SubscriptionController.getInstance()
                        .setDataEnabledOverrideRules(mSubId, mDataEnabledOverride.getRules())) {
                    updateDataEnabledAndNotify(REASON_OVERRIDE_RULE_CHANGED);
                }
                break;
            }
            case EVENT_SET_ALLOW_DATA_DURING_VOICE_CALL: {
                boolean allow = (boolean) msg.obj;
                if (allow == isDataAllowedInVoiceCall()) {
                    break;
                }
                logl("AllowDataDuringVoiceCall changed to " + allow);
                mDataEnabledOverride.setDataAllowedInVoiceCall(allow);
                if (SubscriptionController.getInstance()
                        .setDataEnabledOverrideRules(mSubId, mDataEnabledOverride.getRules())) {
                    updateDataEnabledAndNotify(REASON_OVERRIDE_RULE_CHANGED);
                }
                break;
            }
            case EVENT_UPDATE_DATA_ENABLED: {
                boolean prevDataEnabled = mIsDataEnabled;
                mIsDataEnabled = isDataEnabled(ApnSetting.TYPE_ALL);
                if (prevDataEnabled != mIsDataEnabled) {
                    notifyDataEnabledChanged(mIsDataEnabled, (int) msg.obj);
                }
                break;
            }
            default:
                loge("Unknown msg.what: " + msg.what);
        }
    }

    /**
     * Enable or disable data for a specific {@link TelephonyManager.DataEnabledReason}.
     * @param reason The reason the data enabled change is taking place.
     * @param enabled {@code true} to enable data for the given reason and {@code false} to disable.
     */
    public void setDataEnabled(@TelephonyManager.DataEnabledReason int reason, boolean enabled) {
        obtainMessage(EVENT_SET_DATA_ENABLED_FOR_REASON, reason, enabled ? 1 : 0).sendToTarget();
    }

    /**
     * Check whether the data is enabled for a specific {@link TelephonyManager.DataEnabledReason}.
     * @return {@code true} if data is enabled for the given reason and {@code false} otherwise.
     */
    public boolean isDataEnabledForReason(@TelephonyManager.DataEnabledReason int reason) {
        if (reason == TelephonyManager.DATA_ENABLED_REASON_USER) {
            return isUserDataEnabled();
        } else {
            return mDataEnabledSettings.get(reason);
        }
    }

    private void updateDataEnabledAndNotify(@DataEnabledChangedReason int reason) {
        obtainMessage(EVENT_UPDATE_DATA_ENABLED, reason).sendToTarget();
    }

    /**
     * Check whether the user data is enabled when the device is in the provisioning stage.
     * In provisioning, we might want to enable mobile data depending on the value of
     * Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED, which is set by setupwizard.
     * @return {@code true} if user data is enabled when provisioning and {@code false} otherwise.
     */
    public boolean isProvisioningDataEnabled() {
        final String prov_property = SystemProperties.get("ro.com.android.prov_mobiledata",
                "false");
        boolean retVal = "true".equalsIgnoreCase(prov_property);

        final int prov_mobile_data = Settings.Global.getInt(mResolver,
                Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED,
                retVal ? 1 : 0);
        retVal = prov_mobile_data != 0;
        log("getDataEnabled during provisioning retVal=" + retVal + " - (" + prov_property
                + ", " + prov_mobile_data + ")");

        return retVal;
    }

    /**
     * Update data enabled value because device provisioned status has changed.
     */
    public void updateProvisionedChanged() {
        updateDataEnabledAndNotify(REASON_PROVISIONED_CHANGED);
    }

    /**
     * Update data enabled value because provisioning data has been enabled for the device.
     */
    public void updateProvisioningDataEnabled() {
        updateDataEnabledAndNotify(REASON_PROVISIONING_DATA_ENABLED_CHANGED);
    }

    /**
     * Check whether the overall data is enabled for the device.
     * @return {@code true} if the overall data is enabled and {@code false} otherwise.
     */
    public boolean isDataEnabled() {
        return mIsDataEnabled;
    }

    /**
     * Check whether the overall data is enabled for the device for the given APN type.
     * @param apnType APN type to check data enabled for.
     * @return {@code true} if the overall data is enabled for the APN and {@code false} otherwise.
     */
    public boolean isDataEnabled(int apnType) {
        if (Settings.Global.getInt(mResolver, Settings.Global.DEVICE_PROVISIONED, 0) == 0) {
            return isProvisioningDataEnabled();
        } else {
            boolean userDataEnabled = isUserDataEnabled();
            // Check if we should temporarily enable data in certain conditions.
            boolean isDataEnabledOverridden = mDataEnabledOverride
                    .shouldOverrideDataEnabledSettings(mPhone, apnType);

            return ((userDataEnabled || isDataEnabledOverridden)
                    && mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_POLICY)
                    && mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_CARRIER)
                    && mDataEnabledSettings.get(TelephonyManager.DATA_ENABLED_REASON_THERMAL));
        }
    }

    private static boolean isStandAloneOpportunistic(int subId, Context context) {
        SubscriptionInfo info = SubscriptionController.getInstance().getActiveSubscriptionInfo(
                subId, context.getOpPackageName(), context.getAttributionTag());
        return (info != null) && info.isOpportunistic() && info.getGroupUuid() == null;
    }

    /**
     * Enable or disable user data.
     * @param enabled {@code true} to enable user data and {@code false} to disable.
     */
    public void setUserDataEnabled(boolean enabled) {
        obtainMessage(EVENT_SET_USER_DATA_ENABLED, enabled).sendToTarget();
    }

    /**
     * Check whether user data is enabled for the device.
     * @return {@code true} if user data is enabled and {@code false} otherwise.
     */
    public boolean isUserDataEnabled() {
        // User data should always be true for opportunistic subscription.
        if (isStandAloneOpportunistic(mSubId, mPhone.getContext())) return true;

        boolean defaultVal = TelephonyProperties.mobile_data().orElse(true);

        return GlobalSettingsHelper.getBoolean(mPhone.getContext(),
                Settings.Global.MOBILE_DATA, mSubId, defaultVal);
    }

    /**
     * Enable or disable policy data.
     * @param enabled {@code true} to enable policy data and {@code false} to disable.
     */
    private void setPolicyDataEnabled(boolean enabled) {
        obtainMessage(EVENT_SET_POLICY_DATA_ENABLED, enabled).sendToTarget();
    }

    /**
     * Enable or disable carrier data.
     * @param enabled {@code true} to enable carrier data and {@code false} to disable.
     */
    private void setCarrierDataEnabled(boolean enabled) {
        obtainMessage(EVENT_SET_CARRIER_DATA_ENABLED, enabled).sendToTarget();
    }

    /**
     * Enable or disable thermal data.
     * @param enabled {@code true} to enable thermal data and {@code false} to disable.
     */
    private void setThermalDataEnabled(boolean enabled) {
        obtainMessage(EVENT_SET_THERMAL_DATA_ENABLED, enabled).sendToTarget();
    }

    /**
     * Enable or disable data roaming.
     * @param enabled {@code true} to enable data roaming and {@code false} to disable.
     */
    public void setDataRoamingEnabled(boolean enabled) {
        obtainMessage(EVENT_SET_DATA_ROAMING_ENABLED, enabled).sendToTarget();
    }

    /**
     * Check whether data roaming is enabled for the device based on the current
     * {@link Settings.Global#DATA_ROAMING} value.
     * @return {@code true} if data roaming is enabled and {@code false} otherwise.
     */
    public boolean isDataRoamingEnabled() {
        return GlobalSettingsHelper.getBoolean(mPhone.getContext(),
                Settings.Global.DATA_ROAMING, mSubId, isDefaultDataRoamingEnabled());
    }

    /**
     * Check whether data roaming is enabled by default, if either the
     * {@link CarrierConfigManager#KEY_CARRIER_DEFAULT_DATA_ROAMING_ENABLED_BOOL} value and
     * system property "ro.com.android.dataroaming" are true.
     * @return {@code true} if data roaming is enabled by default and {@code false} otherwise.
     */
    public boolean isDefaultDataRoamingEnabled() {
        return "true".equalsIgnoreCase(SystemProperties.get("ro.com.android.dataroaming", "false"))
                || mPhone.getDataNetworkController().getDataConfigManager()
                        .isDataRoamingEnabledByDefault();
    }

    private @NonNull DataEnabledOverride getDataEnabledOverride() {
        return new DataEnabledOverride(SubscriptionController.getInstance()
                .getDataEnabledOverrideRules(mSubId));
    }

    /**
     * Set whether to always allow the MMS data connection.
     * @param alwaysAllow {@code true} if MMS data is always allowed and {@code false} otherwise.
     */
    public void setAlwaysAllowMmsData(boolean alwaysAllow) {
        obtainMessage(EVENT_SET_ALWAYS_ALLOW_MMS_DATA, alwaysAllow).sendToTarget();
    }

    /**
     * Check whether MMS is always allowed.
     * @return {@code true} if MMS is always allowed and {@code false} otherwise.
     */
    public boolean isMmsAlwaysAllowed() {
        return mDataEnabledOverride.isMmsAlwaysAllowed();
    }

    /**
     * Set whether to allow mobile data during voice call. This is used for allowing data on the
     * non-default data SIM. When a voice call is placed on the non-default data SIM on DSDS
     * devices, users will not be able to use mobile data. By calling this API, data will be
     * temporarily enabled on the non-default data SIM during the life cycle of the voice call.
     * @param allow {@code true} if data is allowed during a voice call and {@code false} otherwise.
     */
    public void setAllowDataDuringVoiceCall(boolean allow) {
        obtainMessage(EVENT_SET_ALLOW_DATA_DURING_VOICE_CALL, allow).sendToTarget();
    }

    /**
     * Check whether data is allowed during a voice call.
     * @return {@code true} if data is allowed during voice call and {@code false} otherwise.
     */
    public boolean isDataAllowedInVoiceCall() {
        return mDataEnabledOverride.isDataAllowedInVoiceCall();
    }

    private void notifyDataEnabledChanged(boolean enabled, int reason) {
        mDataSettingsManagerCallback.onDataEnabledChanged(enabled);
        mPhone.notifyDataEnabled(enabled, reason);
    }

    @Override
    public String toString() {
        return "[isUserDataEnabled=" + isUserDataEnabled()
                + ", isProvisioningDataEnabled=" + isProvisioningDataEnabled()
                + ", mIsDataEnabled=" + mIsDataEnabled
                + ", mDataEnabledSettings=" + mDataEnabledSettings
                + ", mDataEnabledOverride=" + mDataEnabledOverride
                + "]";
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
     * Dump the state of DataSettingsManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataSettingsManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
