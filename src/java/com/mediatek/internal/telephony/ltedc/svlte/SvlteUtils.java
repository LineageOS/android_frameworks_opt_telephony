/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.mediatek.internal.telephony.ltedc.svlte;

import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;

/**
 * Provide SVLTE sub id, phone id get, check, convert and so on.
 */

public class SvlteUtils {
    public static final String LOG_TAG = "SvlteUtils";

     /**
         * Check if subId is lte dc sub id.
         * @param subId sub id
         * @return true subId is lte dc sub id
         */
    public static boolean isLteDcSubId(int subId) {
        return (subId == SubscriptionManager.LTE_DC_SUB_ID_1)
            || (subId == SubscriptionManager.LTE_DC_SUB_ID_2);
    }

     /**
         * Get the lte sub id by slot id.
         * @param slotId slot id
         * @return Lte sub`s id
         */
    public static int getLteDcSubId(int slotId) {
        if (slotId == PhoneConstants.SIM_ID_1) {
            return SubscriptionManager.LTE_DC_SUB_ID_1;
        } else if (slotId == PhoneConstants.SIM_ID_2) {
            return SubscriptionManager.LTE_DC_SUB_ID_2;
        } else {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }
    }

     /**
         * Check if phoneId is lte dc phone id.
         * @param phoneId phone id
         * @return true phoneId is lte dc phone id
         */
     public static boolean isLteDcPhoneId(int phoneId) {
        return (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1)
            || (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2);
     }

      /**
         * Get the lte phone id by slot id.
         * @param slotId slot id
         * @return Lte phone`s id
         */
     public static int getLteDcPhoneId(int slotId) {
         if (slotId == PhoneConstants.SIM_ID_1) {
             return SubscriptionManager.LTE_DC_PHONE_ID_1;
         } else if (slotId == PhoneConstants.SIM_ID_2) {
             return SubscriptionManager.LTE_DC_PHONE_ID_2;
         } else {
             return SubscriptionManager.INVALID_PHONE_INDEX;
         }
     }

    /**
       * Get slot id by lte dc phone id.
       * @param phoneId lte dc phone id
       * @return slot id of lte dc phone
       */
    public static int getSlotId(int phoneId) {
         int slotId = phoneId;
         if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
             slotId = PhoneConstants.SIM_ID_1;
         } else if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
             slotId = PhoneConstants.SIM_ID_2;
         }
         return slotId;
    }

    /**
       * Get slot id by lte dc sub id.
       * @param subId lte dc phone id
       * @return slot id of lte dc phone
       */
    public static int getSlotIdbySubId(int subId) {
         int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
         if (subId == SubscriptionManager.LTE_DC_SUB_ID_1) {
             slotId = PhoneConstants.SIM_ID_1;
         } else if (subId == SubscriptionManager.LTE_DC_SUB_ID_2) {
             slotId = PhoneConstants.SIM_ID_2;
         } else {
             slotId = SubscriptionManager.getSlotId(subId);
         }
         return slotId;
    }

    /**
       * Check if phone id is validate phone id in svlte.
       * @param phoneId  phone id that will be check
       * @return check result
       */
    public static boolean isValidPhoneId(int phoneId) {
        return ((phoneId >= 0 && phoneId < TelephonyManager.getDefault().getPhoneCount())
                   || isLteDcPhoneId(phoneId));
    }

    /**
       * Check if slot id is validate slot id in svlte.
       * @param slotId slot id that will be check
       * @return check result
       */
    public static boolean isValidateSlotId(int slotId) {
        return (slotId >= 0 && slotId < TelephonyManager.getDefault().getSimCount());
    }

    // MTK TODO
    /**
       * Get SvltePhoneProxy of phoneId.
       * @param phoneId phone id
       * @return SvltePhoneProxy of phoneId
       */
    /*
    public static SvltePhoneProxy getSvltePhoneProxy(int phoneId) {
        if (PhoneFactory.getPhone(phoneId) instanceof SvltePhoneProxy) {
            return (SvltePhoneProxy) (PhoneFactory.getPhone(phoneId));
        } else {
            throw new IllegalStateException("Not SvltePhoneProxy!");
        }
    }
    */

    /**
        * Check if phoneId is active svlte mode.
        * @param phoneId Phone id
        * @return true if phoneId is active svlte mode
        */
    public static boolean isActiveSvlteMode(int phoneId) {
        // MTK TODO
        // return SvlteModeController.getActiveSvlteModeSlotId() == getSlotId(phoneId);
        return false;
    }

    /**
        * Check if phone is active svlte mode.
        * @param phone Phone
        * @return true if phone is active svlte mode
        */
    public static boolean isActiveSvlteMode(Phone phone) {
        return isActiveSvlteMode(phone.getPhoneId());
    }

    /**
        * Get phone id of SVLTE active phone.
        * @param phoneId Phone id
        * @return  phone id of SVLTE phone
        */
    public static int getSvltePhoneIdByPhoneId(int phoneId) {
        int curPhoneId = phoneId;
        if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_1) {
            curPhoneId = PhoneConstants.SIM_ID_1;
        } else if (phoneId == SubscriptionManager.LTE_DC_PHONE_ID_2) {
            curPhoneId = PhoneConstants.SIM_ID_2;
        }
        return curPhoneId;
    }

   /**
       * Get phone id of SVLTE active phone.
       * @param phone Phone
       * @return  phone id of SVLTE phone
       */
    public static int getSvltePhoneIdByPhone(Phone phone) {
        return getSvltePhoneIdByPhoneId(phone.getPhoneId());
    }

   /**
       * Get sub id of SVLTE active phone.
       * @param subId Sub id
       * @return sub id of SVLTE phone
       */
    public static int getSvlteSubIdBySubId(int subId) {
        if (isLteDcSubId(subId)) {
            int[] subIds = SubscriptionManager.getSubId(getSlotIdbySubId(subId));
            if (subIds != null && subIds.length > 0) {
                return subIds[0];
            }
        }
        return subId;
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    /**
     * Get the cdma_rat_mode key.
     * @param subId sub id
     * @return the cdma_rat_mode key
     */
    public static String getCdmaRatModeKey(int subId) {
        // MTK TODO
        /*
        if (("OP09").equals(SystemProperties.get("ro.operator.optr", "OM"))) {
            return Settings.Global.LTE_ON_CDMA_RAT_MODE;
        } else {
            return Settings.Global.LTE_ON_CDMA_RAT_MODE + subId;
        }
        */
        return null;
    }
}

