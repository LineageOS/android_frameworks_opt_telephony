/* Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony.util;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

/**
 * This class contains QtiImsExt specific utiltity functions.
 */
public class QtiImsUtils {
    private static final String LOG_TAG = "QtiImsUtils";

    /**
     * Controls dial request route for CS calls.
     * 0 - Use the default routing strategy.
     * 1 - Place the call over CS path
     * 2 - Place the call over PS path
     */
    public static final String EXTRA_CALL_DOMAIN =
        "org.codeaurora.extra.CALL_DOMAIN";
    public static final int DOMAIN_AUTOMATIC = 0;
    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_PS = 2;

    public static final int CODE_IS_PS_ONLY_ATTACHED = 4001;
    public static final int CODE_IS_NOT_PS_ONLY_ATTACHED = 4002;

    public static final int RETRY_ON_IMS_WITHOUT_RTT = 301;
    //value of below constant needs to have same value as QtiCallConstants.java
    public static final int CODE_RETRY_ON_IMS_WITHOUT_RTT = 3001;
    public static final String EXTRA_RETRY_ON_IMS_WITHOUT_RTT = "retryOnImsWithoutRTT";
    //holds the call fail cause because of which redial is attempted
    public static final String EXTRA_RETRY_CALL_FAIL_REASON = "RetryCallFailReason";
    //holds the call radiotech on which lower layers may try attempting redial
    public static final String EXTRA_RETRY_CALL_FAIL_RADIOTECH = "RetryCallFailRadioTech";
    public static final String EXTRA_EMERGENCY_SERVICE_CATEGORY = "EmergencyServiceCategory";

    public static final String SIMLESS_RTT_SUPPORTED = "simless_rtt_supported";
    public static final String SIMLESS_RTT_DOWNGRADE_SUPPORTED = "simless_rtt_downgrade_supported";

    // RTT Off
    public static final int RTT_MODE_DISABLED = 0;
    public static final int RTT_DEFAULT_PHONE_ID = 0;
    public static final String EXTRA_PHONE_ID = "slotId";

    // Call Type RTT
    public static final int RTT_CALL_TYPE_RTT = 0;

    // RTT Operating mode
    // Dials normal voice call by default and provides an option
    // to upgrade call to RTT in InCallUi.
    public static final int RTT_UPON_REQUEST_MODE = 0;
    // All the calls dialed are RTT calls by default.
    public static final int RTT_AUTOMATIC_MODE = 1;

    /*RTT not supported */
    public static final int RTT_SUPPORTED = 1;
    public static final int RTT_NOT_SUPPORTED = 0;
    public static final int RTT_DOWNGRADE_SUPPORTED = 1;
    public static final int RTT_DOWNGRADE_NOT_SUPPORTED = 0;

    /**
     * RTT Operating mode
     * 0 : Upon Request Mode (Disabled)
     * 1 : Automatic Mode (Full)
     *
     */
    public static final String QTI_IMS_RTT_OPERATING_MODE = "qti.settings.rtt_operation";

    /**
     * Whether dialing normal call is ON or OFF
     * The value 1 - enable (Voice call), 0 - disable (RTT call)
     *
     */
    public static final String QTI_IMS_CAN_START_RTT_CALL = "qti.settings.can_start_rtt_call";

    /* Config to determine if Carrier supports RTT Visibility Setting
     * true - if supported else false
     */
    public static final String KEY_SHOW_RTT_VISIBILITY_SETTING =
            "show_rtt_visibility_setting_bool";

    // Returns true if global setting has stored value as true
    public static boolean isRttOn(Context context) {
        return isRttOn(RTT_DEFAULT_PHONE_ID, context);
    }

    public static boolean isRttOn(int phoneId, Context context) {
        return getRttMode(context, phoneId) != RTT_MODE_DISABLED;
    }

    // Returns value of RTT mode
    public static int getRttMode(Context context) {
        return getRttMode(context, RTT_DEFAULT_PHONE_ID);
    }

    public static int getRttMode(Context context, int phoneId) {
        return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.RTT_CALLING_MODE + convertRttPhoneId(phoneId), RTT_MODE_DISABLED);
    }

    private static String convertRttPhoneId(int phoneId) {
        return phoneId != 0 ? Integer.toString(phoneId) : "";
    }

    // Returns true if Carrier supports RTT for Video Calls
    public static boolean isRttSupportedOnVtCalls(int phoneId, Context context) {
        boolean isRttSupportedOnVtCall = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            isRttSupportedOnVtCall = b.getBoolean(
                    CarrierConfigManager.KEY_RTT_SUPPORTED_FOR_VT_BOOL);
        }
        return isRttSupportedOnVtCall;
    }

    // Returns true if Carrier supports RTT upgrade
    public static boolean isRttUpgradeSupported(int phoneId, Context context) {
        boolean isRttUpgradeSupported = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            isRttUpgradeSupported = b.getBoolean(
                    CarrierConfigManager.KEY_RTT_UPGRADE_SUPPORTED_BOOL);
        }
        return isRttUpgradeSupported;
    }

    // Returns true if Carrier supports RTT downgrade
    public static boolean isRttDowngradeSupported(int phoneId, Context context) {
        boolean isRttDowngradeSupported = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            isRttDowngradeSupported = b.getBoolean(
                    CarrierConfigManager.KEY_RTT_DOWNGRADE_SUPPORTED_BOOL);
        }
        return isRttDowngradeSupported;
    }

    // Returns true if Carrier supports RTT Visibility Setting
    public static boolean shallShowRttVisibilitySetting(int phoneId, Context context) {
        boolean showRttVisibilitySetting = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            showRttVisibilitySetting = b.getBoolean(KEY_SHOW_RTT_VISIBILITY_SETTING);
        }
        return showRttVisibilitySetting;
    }

    // Returns true if Carrier supports RTT
    public static boolean isRttSupported(int phoneId, Context context) {
        boolean isRttSupported = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            isRttSupported = b.getBoolean(
                    CarrierConfigManager.KEY_RTT_SUPPORTED_BOOL);
        }
        return isRttSupported;
    }

    // Returns true if Previous Carrier supported RTT
    public static boolean isSimLessRttSupported(int phoneId, Context context) {
        int simLessRttSupportedValue = android.provider.Settings.Secure.getInt(
                context.getContentResolver(),
                SIMLESS_RTT_SUPPORTED + convertRttPhoneId(phoneId), RTT_NOT_SUPPORTED);
        return simLessRttSupportedValue != RTT_NOT_SUPPORTED;
    }

    private static PersistableBundle getConfigForPhoneId(Context context, int phoneId) {
        SubscriptionManager subManager = (SubscriptionManager) context.getSystemService(
                 Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subManager == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId SubscriptionManager is null");
            return null;
        }

        SubscriptionInfo subInfo = subManager.getActiveSubscriptionInfoForSimSlotIndex(phoneId);
        if (subInfo == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId subInfo is null");
            return null;
        }

        int subId = subInfo.getSubscriptionId();
        if (!subManager.isActiveSubscriptionId(subId)) {
            Log.e(LOG_TAG, "getConfigForPhoneId subscription is not active");
            return null;
        }

        CarrierConfigManager mgr = (CarrierConfigManager) context.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        if (mgr == null) {
            Log.e(LOG_TAG, "getConfigForPhoneId CarrierConfigManager is null");
            return null;
        }

        return mgr.getConfigForSubId(subId);
    }

    public static void updateRttConfigCache(Context context, int phoneId,
            PersistableBundle carrierConfig) {
        android.provider.Settings.Secure.putInt(context.getContentResolver(),
                SIMLESS_RTT_SUPPORTED + convertRttPhoneId(phoneId), carrierConfig.getBoolean(
                CarrierConfigManager.KEY_RTT_SUPPORTED_BOOL) ? RTT_SUPPORTED
                : RTT_NOT_SUPPORTED);

        android.provider.Settings.Secure.putInt(context.getContentResolver(),
                SIMLESS_RTT_DOWNGRADE_SUPPORTED + convertRttPhoneId(phoneId),
                carrierConfig.getBoolean(CarrierConfigManager.
                KEY_RTT_DOWNGRADE_SUPPORTED_BOOL) ? RTT_DOWNGRADE_SUPPORTED
                : RTT_DOWNGRADE_NOT_SUPPORTED);
    }

    // Utility to get the RTT Mode that is set through adb property
    // Mode can be either RTT_MODE_DISABLED or RTT_MODE_FULL
    public static int getRttOperatingMode(Context context) {
        return getRttOperatingMode(RTT_DEFAULT_PHONE_ID, context);
    }

    public static int getRttOperatingMode(int phoneId, Context context) {
        if (shallShowRttVisibilitySetting(phoneId, context)) {
            return RTT_AUTOMATIC_MODE;
        }
        return android.provider.Settings.Global.getInt(
                context.getContentResolver(),
                QTI_IMS_RTT_OPERATING_MODE + convertRttPhoneId(phoneId),
                RTT_UPON_REQUEST_MODE);
    }

    // Returns true if we can start RTT call
    public static boolean canStartRttCall(int phoneId, Context context) {
        if (!shallShowRttVisibilitySetting(phoneId, context)) {
            return true;
        }
        return android.provider.Settings.Global.getInt(context.getContentResolver(),
               QTI_IMS_CAN_START_RTT_CALL + convertRttPhoneId(phoneId), RTT_CALL_TYPE_RTT)
               == RTT_CALL_TYPE_RTT;
    }
}
