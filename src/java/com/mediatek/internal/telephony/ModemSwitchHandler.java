/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
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

package com.mediatek.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
// import com.mediatek.internal.telephony.ltedc.svlte.SvltePhoneProxy;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.mediatek.internal.telephony.worldphone.WorldPhoneUtil;


public class ModemSwitchHandler {
    private static final String LOG_TAG = "PHONE";

    /** @internal */
    public static final int MD_TYPE_UNKNOWN = 0;
    /** @internal */
    public static final int MD_TYPE_WG      = 3;
    /** @internal */
    public static final int MD_TYPE_TG      = 4;
    /** @internal */
    public static final int MD_TYPE_LWG     = 5;
    /** @internal */
    public static final int MD_TYPE_LTG     = 6;
    /** @internal */
    public static final int MD_TYPE_FDD     = 100;
    /** @internal */
    public static final int MD_TYPE_TDD     = 101;
    private static final int PROJECT_SIM_NUM = WorldPhoneUtil.getProjectSimNum();

    private static int sCurrentModemType = getActiveModemType();
    private static Phone[] sProxyPhones = null;
    private static Phone[] sActivePhones = new Phone[PROJECT_SIM_NUM];
    private static Context sContext = null;
    private static CommandsInterface[] sCi = new CommandsInterface[PROJECT_SIM_NUM];

    //C2K world phone
    private static Phone[] sSvlteLtePhone = new Phone[PROJECT_SIM_NUM];
    private static CommandsInterface[] sSvlteLteCi = new CommandsInterface[PROJECT_SIM_NUM];

    public ModemSwitchHandler() {
        logd("Constructor invoked");
        logd("Init modem type: " + sCurrentModemType);
        sProxyPhones = PhoneFactory.getPhones();
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            //C2K world phone - start
            sSvlteLtePhone[i] = null;
            sSvlteLteCi[i] = null;
            /*
            if (WorldPhoneUtil.isCdmaLteDcSupport() &&
                    sProxyPhones[i] instanceof SvltePhoneProxy) {
                logd("Phone " + i + " is SVLTE case so get lte phone directly");
                sSvlteLtePhone[i] = ((SvltePhoneProxy) sProxyPhones[i]).getLtePhone();
                sSvlteLteCi[i] = ((PhoneBase) sSvlteLtePhone[i]).mCi;
            }
            */
            //C2K world phone - end
            sActivePhones[i] = ((PhoneProxy) sProxyPhones[i]).getActivePhone();
            sCi[i] = ((PhoneBase) sActivePhones[i]).mCi;
        }
        if (PhoneFactory.getDefaultPhone() != null) {
            sContext = PhoneFactory.getDefaultPhone().getContext();
        } else {
            logd("DefaultPhone = null");
        }
    }

    /**
     * Trigger TRM to switch modem type
     * @param modemType 3 : switch to WG(MD_TYPE_WG)
     *                  4 : switch to TG(MD_TYPE_TG)
     *                  5 : switch to FDD CSFB(MD_TYPE_LWG)
     *                  6 : switch to TDD CSFB(MD_TYPE_LTG)
     */
    public static void switchModem(int modemType) {
        int protocolSim = WorldPhoneUtil.getMajorSim();
        CommandsInterface ci = null;
        logd("protocolSim: " + protocolSim);
        if (protocolSim >= PhoneConstants.SIM_ID_1 && protocolSim <= PhoneConstants.SIM_ID_4) {
            //C2K world phone - start
            ci = getActiveCi(protocolSim);
            if (ci != null) {
                switchModem(ci, modemType);
                //switchModem(sCi[protocolSim], modemType);
            }
            //C2K world phone - end
        } else {
            //C2K world phone - start
            ci = getActiveCi(PhoneConstants.SIM_ID_1);
            if (ci != null) {
                switchModem(ci, modemType);
                //switchModem(sCi[PhoneConstants.SIM_ID_1], modemType);
            }
            //C2K world phone - end
        }
    }

    /**
     * Trigger TRM to switch modem type with desinated RIL instance
     * @param modemType 3 : switch to WG(MD_TYPE_WG)
     *                  4 : switch to TG(MD_TYPE_TG)
     *                  5 : switch to FDD CSFB(MD_TYPE_LWG)
     *                  6 : switch to TDD CSFB(MD_TYPE_LTG)
     */
    public static void switchModem(CommandsInterface ci, int modemType) {
        logd("[switchModem]");
        if (modemType == sCurrentModemType) {
            if (modemType == MD_TYPE_WG) {
                logd("Already in WG modem");
            } else if (modemType == MD_TYPE_TG) {
                logd("Already in TG modem");
            } else if (modemType == MD_TYPE_LWG) {
                logd("Already in FDD CSFB modem");
            } else if (modemType == MD_TYPE_LTG) {
                logd("Already in TDD CSFB modem");
            }
            return;
        }
        if (ci.getRadioState() == CommandsInterface.RadioState.RADIO_UNAVAILABLE) {
            logd("Radio unavailable, can not switch modem");
            return;
        }
        if (modemType == MD_TYPE_WG) {
            ci.setTrm(9, null);
        } else if (modemType == MD_TYPE_TG) {
            ci.setTrm(10, null);
        } else if (modemType == MD_TYPE_LWG) {
            ci.setTrm(11, null);
        } else if (modemType == MD_TYPE_LTG) {
            ci.setTrm(12, null);
        } else {
            logd("Invalid modem type:" + modemType);
            return;
        }
        // Update sCurrentModemType variable & set ril.active.md system property
        setActiveModemType(modemType);

        // Broadcast modem switch notification
        logd("Broadcast intent ACTION_MD_TYPE_CHANGE");
        Intent intent = new Intent(TelephonyIntents.ACTION_MD_TYPE_CHANGE);
        intent.putExtra(TelephonyIntents.EXTRA_MD_TYPE, modemType);
        sContext.sendBroadcast(intent);
    }

    /**
     * Trigger CCCI to reload modem bin
     * @param modemType 3 : reload WG(MD_TYPE_WG)
     *                  4 : reload TG(MD_TYPE_TG)
     *                  5 : reload FDD CSFB(MD_TYPE_LWG)
     *                  6 : reload TDD CSFB(MD_TYPE_LTG)
     */
    public static void reloadModem(int modemType) {
        int majorSim = WorldPhoneUtil.getMajorSim();
        CommandsInterface ci = null;
        if (majorSim >= PhoneConstants.SIM_ID_1 && majorSim <= PhoneConstants.SIM_ID_4) {
            //C2K world phone - start
            ci = getActiveCi(majorSim);
            if (ci != null) {
                reloadModem(ci, modemType);
                //reloadModem(sCi[majorSim], modemType);
            }
            //C2K world phone - end
        } else {
            //C2K world phone - start
            ci = getActiveCi(PhoneConstants.SIM_ID_1);
            if (ci != null) {
                reloadModem(ci, modemType);
                //reloadModem(sCi[PhoneConstants.SIM_ID_1], modemType);
            }
            //C2K world phone - end
        }
    }

    /**
     * Trigger CCCI to reload modem bin with desinated RIL instance
     * @param modemType 3 : reload WG(MD_TYPE_WG)
     *                  4 : reload TG(MD_TYPE_TG)
     *                  5 : reload FDD CSFB(MD_TYPE_LWG)
     *                  6 : reload TDD CSFB(MD_TYPE_LTG)
     */
    public static void reloadModem(CommandsInterface ci, int modemType) {
        logd("[reloadModem]");
        if (ci.getRadioState() == CommandsInterface.RadioState.RADIO_UNAVAILABLE) {
            logd("Radio unavailable, can not reload modem");
            return;
        }
        if (modemType == MD_TYPE_WG) {
            ci.setTrm(14, null);
        } else if (modemType == MD_TYPE_TG) {
            ci.setTrm(15, null);
        } else if (modemType == MD_TYPE_LWG) {
            ci.setTrm(16, null);
        } else if (modemType == MD_TYPE_LTG) {
            ci.setTrm(17, null);
        } else {
            logd("Invalid modem type:" + modemType);
        }
    }

    /**
     * Returns current modem type
     * @internal
     * @return 0 : modem type is unknown
     *         3 : switch to WG(MD_TYPE_WG)
     *         4 : switch to TG(MD_TYPE_TG)
     *         5 : switch to FDD CSFB(MD_TYPE_LWG)
     *         6 : switch to TDD CSFB(MD_TYPE_LTG)
     */
    public static int getActiveModemType() {
        sCurrentModemType = Integer.valueOf(
                SystemProperties.get(TelephonyProperties.PROPERTY_ACTIVE_MD, Integer.toString(MD_TYPE_UNKNOWN)));

        return sCurrentModemType;
    }

    public static void setActiveModemType(int modemType) {
        SystemProperties.set(TelephonyProperties.PROPERTY_ACTIVE_MD, Integer.toString(modemType));
        sCurrentModemType = modemType;
        logd("[setActiveModemType] " + modemToString(sCurrentModemType));
    }

    public static String modemToString(int modemType) {
        String modemString;
        if (modemType == MD_TYPE_WG) {
            modemString = "WG";
        } else if (modemType == MD_TYPE_TG) {
            modemString = "TG";
        } else if (modemType == MD_TYPE_LWG) {
            modemString = "FDD CSFB";
        } else if (modemType == MD_TYPE_LTG) {
            modemString = "TDD CSFB";
        } else if (modemType == MD_TYPE_UNKNOWN) {
            modemString = "UNKNOWN";
        } else {
            modemString = "Invalid modem type";
        }

        return modemString;
    }

    private static CommandsInterface getActiveCi(int slotId) {
        int radioTechModeForWp = WorldPhoneUtil.getRadioTechModeForWp();
        logd("[getActiveCi]: slotId" + slotId +
                " radioTechModeForWp=" + radioTechModeForWp);

        if (WorldPhoneUtil.isCdmaLteDcSupport()) {
            // MTK TODO
            /*
            if (sProxyPhones[slotId] instanceof SvltePhoneProxy) {
                logd("[getActiveCi]: return sSvlteLteCi");
                return sSvlteLteCi[slotId];
            } else {
                logd("[getActiveCi]: return sCi");
                return sCi[slotId];
            }
            */
            logd("[getActiveCi]: xen0n: CDMA support TODO!");
            return sCi[slotId];
        } else {
            logd("[getActiveCi]: return sCi");
            return sCi[slotId];
        }
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[MSH]" + msg);
    }
}
