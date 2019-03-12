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

    public static final int RETRY_ON_IMS_WITHOUT_RTT = 301;
    //value of below constant needs to have same value as QtiCallConstants.java
    public static final int CODE_RETRY_ON_IMS_WITHOUT_RTT = 3001;
    public static final String EXTRA_RETRY_ON_IMS_WITHOUT_RTT = "retryOnImsWithoutRTT";

    // RTT Off
    public static final int RTT_MODE_DISABLED = 0;

    /**
     * Broadcast Action: Send RTT Text Message
     */
    public static final String ACTION_SEND_RTT_TEXT =
            "org.codeaurora.intent.action.send.rtt.text";

    /**
     * RTT Text Value
     */
    public static final String RTT_TEXT_VALUE =
            "org.codeaurora.intent.action.rtt.textvalue";

    /**
     * Broadcast Action: RTT Operation
     */
    public static final String ACTION_RTT_OPERATION =
            "org.codeaurora.intent.action.send.rtt.operation";

    /**
     * RTT Operation Type
     */
    public static final String RTT_OPERATION_TYPE =
            "org.codeaurora.intent.action.rtt.operation.type";

    /**
     * Property for RTT Operating mode
     * For TMO - 0 : Upon Request Mode (Disabled)
     *           1 : Automatic Mode (Full)
     * For Vzw - 0 : Full Mode (Full)
     *
     */
    public static final String PROPERTY_RTT_OPERATING_MODE = "persist.vendor.radio.rtt.operval";

    /* Config to determine if Carrier supports RTT for Video Calls
       true - enabled, false - disabled */
    public static final String KEY_CARRIER_RTT_SUPPORTED_ON_VTCALLS =
            "carrier_rtt_supported_on_vtcalls";

    /* Config to determine if Carrier supports RTT Visibility Setting
     * true - if supported else false
     */
    public static final String KEY_SHOW_RTT_VISIBILITY_SETTING =
            "show_rtt_visibility_setting_bool";

    // RTT Operation Type can be one of the following
    // To request upgrade of regular call to RTT call
    public static final int RTT_UPGRADE_INITIATE = 1;
    // To accept incoming RTT upgrade request
    public static final int RTT_UPGRADE_CONFIRM = 2;
    // To reject incoming RTT upgrade request
    public static final int RTT_UPGRADE_REJECT = 3;
    // To request downgrade of RTT call to regular call
    public static final int RTT_DOWNGRADE_INITIATE = 4;
    // To request showing the RTT Keyboard
    public static final int SHOW_RTT_KEYBOARD = 5;
    // To request hiding the RTT Keyboard
    public static final int HIDE_RTT_KEYBOARD = 6;

    // Returns true if global setting has stored value as true
    public static boolean isRttOn(Context context) {
        return (getRttMode(context) != RTT_MODE_DISABLED);
    }

    // Returns value of RTT mode
    public static int getRttMode(Context context) {
        return android.provider.Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.RTT_CALLING_MODE, RTT_MODE_DISABLED);
    }

    // Returns true if Carrier supports RTT for Video Calls
    public static boolean isRttSupportedOnVtCalls(int phoneId, Context context) {
        boolean isRttSupportedOnVtCall = false;
        PersistableBundle b = getConfigForPhoneId(context, phoneId);
        if (b != null) {
            isRttSupportedOnVtCall = b.getBoolean(KEY_CARRIER_RTT_SUPPORTED_ON_VTCALLS);
        }
        return isRttSupportedOnVtCall;
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

    // Utility to get the RTT Mode that is set through adb property
    // Mode can be either RTT_MODE_DISABLED or RTT_MODE_FULL
    public static int getRttOperatingMode(Context context) {
        int mode = SystemProperties.getInt(PROPERTY_RTT_OPERATING_MODE, 0);
        return mode;
    }
}
