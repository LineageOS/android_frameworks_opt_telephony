/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.mediatek.telephony;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

import com.android.internal.telephony.IPhoneSubInfo;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;

import android.os.Message;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import java.util.List;

import android.telephony.SubscriptionManager;
import com.mediatek.internal.telephony.ITelephonyEx;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;


/**
 * Provides access to information about the telephony services on
 * the device, especially for multiple SIM cards device.
 *
 * Applications can use the methods in this class to
 * determine telephony services and states, as well as to access some
 * types of subscriber information. Applications can also register
 * a listener to receive notification of telephony state changes.
 *
 * Note that access to some telephony information is
 * permission-protected. Your application cannot access the protected
 * information unless it has the appropriate permissions declared in
 * its manifest file. Where permissions apply, they are noted in the
 * the methods through which you access the protected information.
 */
public class TelephonyManagerEx {
    private static final String TAG = "TelephonyManagerEx";

    private Context mContext = null;
    private ITelephonyRegistry mRegistry;


    /* Add for  Phone2 */
    private static int defaultSimId = PhoneConstants.SIM_ID_1;

   /**
    * The property record the card's ICC ID.
    * @hide
    */
    private String[] PROPERTY_ICCID_SIM = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4",
    };


    /**
     * Construction function for TelephonyManager
     * @param context a context
     */
    public TelephonyManagerEx(Context context) {

        Rlog.d(TAG, "getSubscriberInfo");
        mContext = context;
        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                    "telephony.registry"));

    }

    /*  Construction function for TelephonyManager */
    private TelephonyManagerEx() {

        mRegistry = ITelephonyRegistry.Stub.asInterface(ServiceManager.getService(
                   "telephony.registry"));
    }

    private  static TelephonyManagerEx sInstance = new TelephonyManagerEx();

    /** @hide
     *  @return return the static instance of TelephonyManagerEx
     */
    public static TelephonyManagerEx getDefault() {
        return sInstance;
    }

    /**
     * @param simId Indicates which SIM(slot) to query
     * @return The software version number for the device
     * @hide
     */
    public String getDeviceSoftwareVersion(int simId) {
        Rlog.d(TAG, "getDeviceSoftwareVersion simId=" + simId);
        try {
            return TelephonyManager.getDefault().getDeviceSoftwareVersion(simId);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "getDeviceSoftwareVersion error, return null. (" + ex.toString() + ")");
            return null;
        }
    }

    //
    //
    // Device Info
    //
    //

    /**
     * Returns the unique device identifier e.g. IMEI for GSM phones. MEID or ESN for CDMA phones.
     * For GSM phone with multiple SIM support , there is IMEI for each SIM.
     * Required Permission:
     *  android.Manifest.permission READ_PHONE_STATE READ_PHONE_STATE
     *
     * @param simId Indicates which SIM(slot) to query
     * @return Unique device ID. For GSM phones,a string of IMEI
     * returns null if the device ID is not available.
     */
    public String getDeviceId(int simId) {
        Rlog.d(TAG, "getDeviceId simId=" + simId);
        try {
            return TelephonyManager.getDefault().getDeviceId(simId);
        } catch (NullPointerException ex) {
            Rlog.e(TAG, "getDeviceId error, return null. (" + ex.toString() + ")");
            return null;
        }
    }

    /**
     * Returns the current cell location of the device.
     * <p>
     * Required Permission:
     *  android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_COARSE_LOCATION or
     *  android.Manifest.permission#ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION.
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return current cell location of the device. A CellLocation object
     * returns null if the current location is not available.
     *
     */
    public CellLocation getCellLocation(int simId) {
        Rlog.e(TAG, "Deprecated! getCellLocation with simId " + simId);


        try {
            return TelephonyManager.getDefault().getCellLocation();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the neighboring cell information of the device. The getAllCellInfo is preferred
     * and use this only if getAllCellInfo return nulls or an empty list.
     *<p>
     * In the future this call will be deprecated.
     *<p>
     * @return List of NeighboringCellInfo or null if info unavailable.
     *
     * Required Permission:
     *   "android.Manifest.permission#ACCESS_COARSE_UPDATES"
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return list of NeighboringCellInfo, java.util.List<android.telephony.NeighboringCellInfo>, or null if info is unavailable
     *
     */
    public List<NeighboringCellInfo> getNeighboringCellInfo(int simId) {
        Rlog.e(TAG, "Deprecated! getNeighboringCellInfo with simId " + simId);

        try {
            return TelephonyManager.getDefault().getNeighboringCellInfo();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @param simId Indicates which SIM(slot) to query
     * @return  a constant indicating the device phone type
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     */
    public int getPhoneType(int simId) {
        int subIds[] = SubscriptionManager.getSubId(simId);
        Rlog.e(TAG, "Deprecated! getPhoneType with simId " + simId + ", subId " + subIds[0]);
        return TelephonyManager.getDefault().getCurrentPhoneType(subIds[0]);
    }

    //
    // Current Network
    //

    /**
     * Returns the alphabetic name of current registered operator.
     * Please call TelephonyManager.getNetworkOperatorName(int subId) instead
     * <p>
     * Availability: Only when the user is registered to a network. Result may be
     * unreliable on CDMA networks (use getPhoneType(int simId)) to determine if
     * it is on a CDMA network).
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return alphabetic name of the current registered operator, e.g. "Vodafone"
     */
    public String getNetworkOperatorName(int simId) {
        try {
            int subId = getSubIdBySlot(simId);
            Rlog.e(TAG, "Deprecated! getNetworkOperatorName with simId " + simId + " ,subId " + subId);
            return TelephonyManager.getDefault().getNetworkOperatorName(subId);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * Returns the numeric name (MCC+MNC) of the current registered operator.
     * Please call TelephonyManager.getNetworkOperator(int subId) instead
     * <p>
     * Availability: Only when the user is registered to a network. Result may be
     * unreliable on CDMA networks (use getPhoneType(int simId)) to determine if
     * it is on a CDMA network).
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return numeric name (MCC+MNC) of current registered operator, e.g. "46000".
     */
    public String getNetworkOperator(int simId) {
        try {
            int subId = getSubIdBySlot(simId);
            Rlog.e(TAG, "Deprecated! getNetworkOperator with simId " + simId + " ,subId " + subId);
            return TelephonyManager.getDefault().getNetworkOperatorForSubscription(subId);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * Indicates whether the device is considered roaming on the current  network, for GSM purposes.
     * Please call TelephonyManager.isNetworkRoaming(int subId) instead
     * <p>
     * Availability: Only when the user is registered to a network.
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return Returns True if the device is considered roaming on the current network; otherwise false.
     *
     */
    public boolean isNetworkRoaming(int simId) {
        try {
            int subId = getSubIdBySlot(simId);
            Rlog.e(TAG, "Deprecated! isNetworkRoaming with simId " + simId + " ,subId " + subId);
            return TelephonyManager.getDefault().isNetworkRoaming(subId);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Returns the ISO country code of the current registered operator's MCC(Mobile Country Code).
     * Please call TelephonyManager.getNetworkCountryIso(int subId) instead
     * <p>
     * Availability: Only when the user is registered to a network. Result may be
     * unreliable on CDMA networks (use getPhoneType(int simId)) to determine if
     * it is on a CDMA network).
     *
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return ISO country code equivilent of the current registered
     * operator's MCC (Mobile Country Code), e.g. "en","fr"
     */
    public String getNetworkCountryIso(int simId) {
        try {
            int subId = getSubIdBySlot(simId);
            Rlog.e(TAG, "Deprecated! getNetworkCountryIso with simId " + simId + " ,subId " + subId);
            return TelephonyManager.getDefault().getNetworkCountryIsoForSubscription(subId);
        } catch (NullPointerException ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently used on the device for data transmission.
     * Please  call TelephonyManager.getNetworkType(int subId) instead
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * @return constant indicating the radio technology (network type)
     * currently used on the device. Constant may be one of the following items.
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_GPRS
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_EDGE
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_UMTS
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_HSPA
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_CDMA
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_0
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_A
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_EVDO_B
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_1xRTT
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_IDEN
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_LTE
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_EHRPD
     * <p>
     * android.telephony.TelephonyManager.NETWORK_TYPE_HSPAP
     */
    public int getNetworkType(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "Deprecated! getNetworkType with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getNetworkType(subId);
    }


    //
    //
    // SIM Card
    //
    //

    /**
     * Gets true if a ICC card is present
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Returns True if a ICC card is present.
     */
    public boolean hasIccCard(int simId) {
        Rlog.d(TAG, "hasIccCard simId=" + simId);
        int slot = simId;
        return TelephonyManager.getDefault().hasIccCard(slot);
    }

    private int getSubIdBySlot(int slot) {
        int [] subId = SubscriptionManager.getSubId(slot);
        Rlog.d(TAG, "getSubIdBySlot, simId " + slot +
                "subId " + ((subId != null) ? subId[0] : "invalid!"));
        return (subId != null) ? subId[0] : SubscriptionManager.getDefaultSubId();
    }

    /**
     * Get Icc Card Type
     * @param subId which subId to query
     * @return "SIM" for SIM card or "USIM" for USIM card.
     * @hide
     * @internal
     */
    public String getIccCardType(int subId) {
        String type = null;
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                type = telephony.getIccCardType(subId);
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();
        }
        Rlog.d(TAG, "getIccCardType sub " + subId + " ,icc type " + ((type != null) ? type : "null"));
        return type;
    }

    /**
     * Request to get UICC card type.
     *
     * @param slotId indicated sim id
     *
     * @return index for UICC card type
     * @hide
    */
   public int getSvlteCardType(int slotId) {
       int type = 0;
       try {
           ITelephonyEx telephony = getITelephonyEx();
           if (telephony != null && CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
               type = telephony.getSvlteCardType(slotId);
           }
       } catch (RemoteException ex) {
           ex.printStackTrace();
       } catch (NullPointerException ex) {
           // This could happen before phone restarts due to crashing
           ex.printStackTrace();
       }
       Rlog.d(TAG, "getSvlteCardType(): slotId " + slotId + " ,icc type " + type);
       return type;
   }

    /**
     * Query is indicated app type is support of indicated Uicc Card.
     * @param slotId which slotId to query
     * @return "true" for supported case or "boolean" for non-supported case.
     * @hide
     */
    public boolean isAppTypeSupported(int slotId, int appType) {
        boolean isSupported = false;
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                isSupported = telephony.isAppTypeSupported(slotId, appType);
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();
        }
        Rlog.d(TAG, "isAppTypeSupported slotId: " + slotId + ", appType: " + appType +
                ", isSupported: " + isSupported);
        return isSupported;
    }

    /**
     * Get Icc Card is a test card or not.
     * @param simId Indicate which sim(slot) to query
     * @return ture if the ICC card is a test card.
     * @hide
     */
    public boolean isTestIccCard(int simId) {
        boolean result = false;
        Rlog.d(TAG, "isTestIccCard simId=" + simId);
        try {
            ITelephonyEx telephony = getITelephonyEx();
            if (telephony != null) {
                result = telephony.isTestIccCard(simId);
            }
        } catch (RemoteException ex) {
            ex.printStackTrace();
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            ex.printStackTrace();
        }
        Rlog.d(TAG, "isTestIccCard sim " + simId + " ,result " + result);
        return result;
    }

    /**
     * Gets a constant indicating the state of the device SIM card.
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Constant indicating the state of the device SIM card.
     * Constant may be one of the following items.
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_UNKNOWN
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_ABSENT
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_PIN_REQUIRED
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_PUK_REQUIRED
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_NETWORK_LOCKED
     * <p>
     * android.telephony.TelephonyManager.SIM_STATE_READY
     *
     */
    public int getSimState(int simId) {
        Rlog.d(TAG, "getSimState simId=" + simId);
        return TelephonyManager.getDefault().getSimState(simId);
    }

    /**
     * Gets the MCC+MNC (mobile country code + mobile network code) of the provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: The result of calling getSimState() must be android.telephony.TelephonyManager.SIM_STATE_READY.
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       MCC+MNC (mobile country code + mobile network code) of the provider of the SIM. 5 or 6 decimal digits.
     *
     */
    public String getSimOperator(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getSimOperator with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getSimOperator(subId);
    }

    /**
     * Gets the Service Provider Name (SPN).
     * <p>
     * Availability: The result of calling getSimState() must be android.telephony.TelephonyManager.SIM_STATE_READY.
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Service Provider Name (SPN)
     *
     */
    public String getSimOperatorName(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getSimOperatorName with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getSimOperatorNameForSubscription(subId);
    }

    /**
     * Gets the ISO country code equivalent for the SIM provider's country code.
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Gets the ISO country code equivalent for the SIM provider's country code.
     *
     */
    public String getSimCountryIso(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getSimCountryIso with simId " + simId + " ,subId " + subId);

        return TelephonyManager.getDefault().getSimCountryIso(subId);
    }


    /**
     * Returns the serial number for the given subscription, if applicable. Return null if it is
     * unavailable.
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       serial number of the SIM, if applicable. Null is returned if it is unavailable.
     *
     */
    public String getSimSerialNumber(int simId) {
        if (simId < 0 || simId >= TelephonyManager.getDefault().getSimCount()) {
            Rlog.e(TAG, "getSimSerialNumber with invalid simId " + simId);
            return null;
        }

        String iccId = SystemProperties.get(PROPERTY_ICCID_SIM[simId], "");

        if (iccId != null && (iccId.equals("N/A") || iccId.equals(""))) {
            iccId = null;
        }

        return iccId;
    }

    //
    //
    // Subscriber Info
    //
    //

    /**
     * Gets the unique subscriber ID, for example, the IMSI for a GSM phone.
     * <p>
     * Required Permission:
     *   "android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE"
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       unique subscriber ID, for example, the IMSI for a GSM phone. Null is returned if it is unavailable.
     *
     */
    public String getSubscriberId(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getSubscriberId with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getSubscriberId(subId);
    }

    /**
     * Gets the phone number string for line 1, for example, the MSISDN for a GSM phone
     * <p>
     * Required Permission:
     *   "android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE"
     * <p>
     * @param simId  Indicates which SIM to quer
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Phone number string for line 1, for example, the MSISDN for a GSM phone. Returns null if it is unavailable.
     *
     */

    public String getLine1Number(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getLine1Number with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getLine1NumberForSubscriber(subId);
    }


    /**
     * Gets the voice mail number.
     * <p>
     * Required Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * <p>
     * @param simId  Indicates which SIM (slot) to query
     * <p>
     * @return       Voice mail number. Null is returned if it is unavailable.
     *
     */
    public String getVoiceMailNumber(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getVoiceMailNumber with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getVoiceMailNumber(subId);
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice mail number.
     * <p>
     * Required Permission:
     *   "android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE"
     * <p>
     * @param simId  Indicates which SIM to query.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     * <p>
     * @return       Alphabetic identifier associated with the voice mail number
     *
     */
    public String getVoiceMailAlphaTag(int simId) {
        int subId = getSubIdBySlot(simId);
        Rlog.e(TAG, "getVoiceMailAlphaTag with simId " + simId + " ,subId " + subId);
        return TelephonyManager.getDefault().getVoiceMailAlphaTag(subId);
    }

      /**
     * Gets the current call state according to the specific SIM ID.
     * The call state can be one of the following states:
     * 1) android.telephony.TelephonyManager.CALL_STATE_IDLE;
     * 2) android.telephony.TelephonyManager.CALL_STATE_RINGING;
     * 3) android.telephony.TelephonyManager.CALL_STATE_OFFHOOK
     * @param  simId SIM ID for getting call state.
     *         Value of simId:
     *         0 for SIM1;
     *         1 for SIM2
     * @return Constant indicating the call state (cellular) on the device.
     * @internal
     */
    public int getCallState(int simId) {
        Rlog.d(TAG, "getCallState simId=" + simId);
        return TelephonyManager.getDefault().getCallState(getSubIdBySlot(simId));
    }

    /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * The data activity can be one of the following:
     * 1) DATA_ACTIVITY_NONE;
     * 2) DATA_ACTIVITY_IN;
     * 3) DATA_ACTIVITY_OUT;
     * 4) DATA_ACTIVITY_INOUT;
     * 5) DATA_ACTIVITY_DORMANT
     *
     * @param simId Indicates which SIM(slot) to query
     * @return Constant indicating the type of activity on specific SIM's data connection
     * (cellular).
     * @internal
     */
    public int getDataActivity(int simId) {
        Rlog.d(TAG, "getDataActivity simId=" + simId);
        // MTK TODO
        return TelephonyManager.getDefault().getDataActivity(/* getSubIdBySlot(simId) */);
    }

    /**
     * Returns a constant indicating the specific SIM's data connection state
     * (cellular).
     *
     * The data connection state can be one of the following states:
     * 1) DATA_DISCONNECTED;
     * 2) DATA_CONNECTING;
     * 3) DATA_CONNECTED;
     * 4) DATA_SUSPENDED
     *
     * @param simId Indicates which SIM(slot) to query
     * @return Constant indicating specific SIM's data connection state
     * (cellular).
     * @internal
     */
    public int getDataState(int simId) {
        Rlog.d(TAG, "getDataState simId=" + simId);
        // MTK TODO
        return TelephonyManager.getDefault().getDataState(/* getSubIdBySlot(simId) */);
    }


    //
    //
    // PhoneStateListener
    //
    //


    /**
     *
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     *
     * To register a listener, pass a PhoneStateListener
     * and specify at least one telephony state of interest in
     * the events argument.
     *
     * At registration, and when a specified telephony state
     * changes, the telephony manager invokes the appropriate
     * callback method on the listener object and passes the
     * current (udpated) values.
     *
     * To unregister a listener, pass the listener object and set the
     * events argument to PhoneStateListener LISTEN_NONE LISTEN_NONE.
     *
     * @param listener  the android.telephony.PhoneStateListener object
     *                  to register or unregister
     * @param events  the telephony state(s) of interest to the listener,
     *               as a bitwise-OR combination of PhoneStateListener
     *               LISTEN_ flags.
     * @param simId  Indicates which SIM to regisrer or unregister.
     *               Value of simId:
     *                 0 for SIM1
     *                 1 for SIM2
     */
    public void listen(PhoneStateListener listener, int events, int simId) {
        Rlog.d(TAG, "deprecated api, listen simId=" + simId + ",events=" + events);
        String pkgForDebug = mContext != null ? mContext.getPackageName() : "<unknown>";
        TelephonyManager.getDefault().listen(listener, events);
    }


    private ITelephony getITelephony() {
        return ITelephony.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE));
    }

    private ITelephonyEx getITelephonyEx() {
        return ITelephonyEx.Stub.asInterface(ServiceManager.getService(Context.TELEPHONY_SERVICE_EX));
    }

    /* Get current active phone type by system property, GsmPhone or CdmaPhone */
    private int getPhoneTypeFromProperty() {
        int type =
            SystemProperties.getInt(TelephonyProperties.CURRENT_ACTIVE_PHONE,
                    getPhoneTypeFromNetworkType());
        return type;
    }

    /* Get phone type by network type, GsmPhone or CdmaPhone */
    private int getPhoneTypeFromNetworkType() {
    	int phoneId = SubscriptionManager.getPhoneId(SubscriptionManager.getDefaultSubId());    	
        String mode = TelephonyManager.getTelephonyProperty(
                phoneId, "ro.telephony.default_network", null);
        if (mode != null) {
            return TelephonyManager.getDefault().getPhoneType(Integer.parseInt(mode));
        }
        return TelephonyManager.PHONE_TYPE_NONE;
    }


    /**
     * Get service center address
     *
     * @param subId subscripiton identity
     *
     * @return Current service center address
     * @hide
     * @internal
     */
    public String getScAddress(int subId) {
        Bundle result = getScAddressWithErroCode(subId);
        if (result != null) {
            return (String) result.getCharSequence(GET_SC_ADDRESS_KEY_ADDRESS);
        }
        return null;
    }

    /**
     * Get SC address bundle key: result.
     *
     * @internal
     * @hide
     */
    public static final String GET_SC_ADDRESS_KEY_RESULT = "errorCode";

    /**
     * Get SC address bundle key: scAddress.
     *
     * @internal
     * @hide
     */
    public static final String GET_SC_ADDRESS_KEY_ADDRESS = "scAddress";

    /**
     * Error Code: success.
     * Now, it is only used by {@link #getScAddressWithErroCode(subId)}.
     *
     * @internal
     * @hide
     */
    public static final byte ERROR_CODE_NO_ERROR = 0x00;

    /**
     * Error Code: generic error.
     * Now, it is only used by {@link #getScAddressWithErroCode(subId)}.
     *
     * @internal
     * @hide
     */
    public static final byte ERROR_CODE_GENERIC_ERROR = 0x01;

    /**
     * Get service center address with error code.
     *
     * @param subId subscripiton identity
     *
     * @return Current service center address and error code by Bundle
     * The error code will be
     *     {@link #ERROR_CODE_NO_ERROR}
     *     {@link #ERROR_CODE_GENERIC_ERROR}
     *
     * @internal
     * @hide
     */
    public Bundle getScAddressWithErroCode(int subId) {
        try {
            return getITelephonyEx().getScAddressUsingSubId(subId);
        } catch (RemoteException e1) {
            e1.printStackTrace();
            return null;
        } catch (NullPointerException e2) {
            e2.printStackTrace();
            return null;
        }
    }

    /**
     * Set service center address
     *
     * @param subId subscripiton identity
     * @param address Address to be set
     *
     * @return True for success, false for failure
     * @hide
     * @internal
     */
    public boolean setScAddress(int subId, String address) {
        try {
            return getITelephonyEx().setScAddressUsingSubId(subId, address);
        } catch (RemoteException e1) {
            e1.printStackTrace();
            return false;
        } catch (NullPointerException e2) {
            e2.printStackTrace();
            return false;
        }
    }

    private IPhoneSubInfo getSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfo.Stub.asInterface(ServiceManager.getService("iphonesubinfo"));
    }


    /**
     * Returns the IMS private user identity (IMPI) that was loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return the IMPI, or null if not present or not loaded
     * @hide
     */
    public String getIsimImpi(int subId) {
        try {
            // MTK TODO
            // return getSubscriberInfo().getIsimImpiForSubscriber(subId);
            return getSubscriberInfo().getIsimImpi();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS home network domain name that was loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return the IMS domain name, or null if not present or not loaded
     * @hide
     */
    public String getIsimDomain(int subId) {
        try {
            // MTK TODO
            // return getSubscriberInfo().getIsimDomainForSubscriber(subId);
            return getSubscriberInfo().getIsimDomain();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS public user identities (IMPU) that were loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return an array of IMPU strings, with one IMPU per string, or null if
     *      not present or not loaded
     * @hide
     */
    public String[] getIsimImpu(int subId) {
        try {
            // MTK TODO
            // return getSubscriberInfo().getIsimImpuForSubscriber(subId);
            return getSubscriberInfo().getIsimImpu();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS Service Table (IST) that was loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return IMS Service Table or null if not present or not loaded
     * @hide
     */
    public String getIsimIst(int subId) {
        try {
            // MTK TODO
            // return getSubscriberInfo().getIsimIstForSubscriber(subId);
            return getSubscriberInfo().getIsimIst();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the IMS Proxy Call Session Control Function(PCSCF) that were loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return an array of PCSCF strings with one PCSCF per string, or null if
     *         not present or not loaded
     * @hide
     */
    public String[] getIsimPcscf(int subId) {
        try {
            // MTK TODO
            // return getSubscriberInfo().getIsimPcscfForSubscriber(subId);
            return getSubscriberInfo().getIsimPcscf();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     * @hide
     */
    public String getIsimGbabp() {
        return getIsimGbabp(SubscriptionManager.getDefaultSubId());
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the ISIM.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     * @hide
     */
    public String getIsimGbabp(int subId) {
        // MTK TODO
        /*
        try {
            return getSubscriberInfo().getIsimGbabpForSubscriber(subId);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
        */
        return null;
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     * @hide
     */
    public void setIsimGbabp(String gbabp, Message onComplete) {
        setIsimGbabp(SubscriptionManager.getDefaultSubId(), gbabp, onComplete);
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the ISIM.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     * @hide
     */
    public void setIsimGbabp(int subId, String gbabp, Message onComplete) {
        // MTK TODO
        /*
        try {
            getSubscriberInfo().setIsimGbabpForSubscriber(subId, gbabp, onComplete);
        } catch (RemoteException ex) {
            return;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return;
        }
        */
    }

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM.
     * @param service service index on UST
     * @return the indicated service is supported or not
     * @hide
     */
    public boolean getUsimService(int service) {
        return getUsimService(SubscriptionManager.getDefaultSubId(), service);
    }

    /**
     * Returns the USIM Service Table (UST) that was loaded from the USIM.
     * @param subId subscription ID to be queried
     * @param service service index on UST
     * @return the indicated service is supported or not
     * @hide
     */
    public boolean getUsimService(int subId, int service) {
        // MTK TODO
        /*
        try {
            return getSubscriberInfo().getUsimServiceForSubscriber(subId, service);
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
        */
        return false;
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM.
     * @return GBA bootstrapping parameters or null if not present or not loaded
     * @hide
     */
    public String getUsimGbabp() {
        return getUsimGbabp(SubscriptionManager.getDefaultSubId());
    }

    /**
     * Returns the GBA bootstrapping parameters (GBABP) that was loaded from the USIM.
     * @param subId subscription ID to be queried
     * @return GBA bootstrapping parameters or null if not present or not loaded
     * @hide
     */
    public String getUsimGbabp(int subId) {
        // MTK TODO
        /*
        try {
            return getSubscriberInfo().getUsimGbabpForSubscriber(subId);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
        */
        return null;
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM.
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     * @hide
     */
    public void setUsimGbabp(String gbabp, Message onComplete) {
        setUsimGbabp(SubscriptionManager.getDefaultSubId(), gbabp, onComplete);
    }

    /**
     * Set the GBA bootstrapping parameters (GBABP) value into the USIM.
     * @param subId subscription ID to be queried
     * @param gbabp a GBA bootstrapping parameters value in String type
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     * @hide
     */
    public void setUsimGbabp(int subId, String gbabp, Message onComplete) {
        // MTK TODO
        /*
        try {
            getSubscriberInfo().setUsimGbabpForSubscriber(subId, gbabp, onComplete);
        } catch (RemoteException ex) {
            return;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return;
        }
        */
    }

    /**
     * Get subscriber Id of LTE phone.
     * @param subId the subId of CDMAPhone
     * @return The subscriber Id of LTE phone.
     *
     * @hide
     */
    public String getSubscriberIdForLteDcPhone(int subId) {
        try {
            return getITelephonyEx().getSubscriberIdForLteDcPhone(subId);
        } catch (RemoteException e1) {
            e1.printStackTrace();
            return null;
        } catch (NullPointerException e2) {
            e2.printStackTrace();
            return null;
        }
    }

    /// M: [C2K][SVLTE] Define SVLTE RAT mode. @{
    /**
     * For C2K SVLTE RAT controll, LTE preferred mode.
     * @hide
     */
    public static final int SVLTE_RAT_MODE_4G = 0;

    /**
     * For C2K SVLTE RAT controll, EVDO preferred mode, will disable LTE.
     * @hide
     */
    public static final int SVLTE_RAT_MODE_3G = 1;

    /**
     * For C2K SVLTE RAT controll, LTE Data only mode, will disable CDMA and only allow LTE PS.
     * @hide
     */
    public static final int SVLTE_RAT_MODE_4G_DATA_ONLY = 2;
    /// @}

    /**
     * It used to get whether the device is in dual standby dual connection.
     * For example, call application will be able to support dual connection
     * if the device mode is in DSDA.
     *
     * @return true if the device is in DSDA mode, false for others
     * @hide
     */
    public boolean isInDsdaMode() {
        if (SystemProperties.get("ro.mtk_switch_antenna", "0").equals("1")) {
            return false;
        }
        String mSimConfig =
                SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);
        String svlteMode =
                SystemProperties.get(TelephonyProperties.PROPERTY_RADIO_SVLTE_MODE);

        if (mSimConfig.equals("dsda")) {
            if (!CdmaFeatureOptionUtils.isCdmaLteDcSupport() ||
                    (CdmaFeatureOptionUtils.isCdmaLteDcSupport() && svlteMode.equals("svlte"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if a ICC card is present for a subscription
     *
     */
    /** {@hide} */
    public boolean isAllowAirplaneModeChange() {
        try {
            return getITelephonyEx().isAllowAirplaneModeChange();
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * Get whether allow the airplane mode change.
     *
     * @return true if allow the airplane mode change.
     * @hide
     */
    public boolean isAirplanemodeAvailableNow() {
        try {
            return getITelephonyEx().isAirplanemodeAvailableNow();
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * Get cdma rat mode key for the different load.
     *
     * @param subId The subId to get cdma rat mode.
     * @return the rat mode key of the specific subId.
     * @hide
     */
    public String getCdmaRatModeKey(int subId) {
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

    /**
     * Return the sim card if in home network.
     *
     * @param subId subscription ID to be queried.
     * @return true if in home network.
     * @hide
     */
    public boolean isInHomeNetwork(int subId) {
        try {
            return getITelephonyEx().isInHomeNetwork(subId);
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }
}
