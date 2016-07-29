/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2014. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.internal.telephony;

import android.os.SystemProperties;

import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.UiccController;

import java.util.ArrayList;
import java.util.Arrays;

import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;

/**
 * Utility for capability switch.
 *
 */
public class RadioCapabilitySwitchUtil {
    private static final String LOG_TAG = "GSM";

    public static final int SIM_OP_INFO_UNKNOWN = 0;
    public static final int SIM_OP_INFO_OVERSEA = 1;
    public static final int SIM_OP_INFO_OP01 = 2;
    public static final int SIM_OP_INFO_OP02 = 3;

    public static final int SIM_TYPE_SIM = 0;
    public static final int SIM_TYPE_USIM = 1;
    public static final int SIM_TYPE_OTHER = 2;

    // sync to ril_oem.h for dsda
    public static final int SIM_SWITCH_MODE_SINGLE_TALK_MDSYS       = 1;
    public static final int SIM_SWITCH_MODE_SINGLE_TALK_MDSYS_LITE  = 2;
    public static final int SIM_SWITCH_MODE_DUAL_TALK               = 3;
    public static final int SIM_SWITCH_MODE_DUAL_TALK_SWAP          = 4;

    public static final String MAIN_SIM_PROP = "persist.radio.simswitch.iccid";
    private static final String PROPERTY_ICCID = "ril.iccid.sim";
    // OP01 SIMs
    private static final String[] PLMN_TABLE_TYPE1 = {
        "46000", "46002", "46007", "46008", "45412", "45413",
        // Lab test IMSI
        "00101", "00211", "00321", "00431", "00541", "00651",
        "00761", "00871", "00902", "01012", "01122", "01232",
        "46004", "46602", "50270", "46003", "46011"
    };

    // non-OP01 SIMs
    private static final String[] PLMN_TABLE_TYPE3 = {
        "46001", "46006", "46009", "45407",
        "46005", "45502"
    };

    // OP02 case
    private static final String NO_SIM_VALUE = "N/A";
    public static final String CN_MCC = "460";
    private static final String[] PROPERTY_SIM_ICCID = {
        "ril.iccid.sim1",
        "ril.iccid.sim2",
        "ril.iccid.sim3",
        "ril.iccid.sim4"
    };
    public static final int SHOW_DIALOG = 0;
    public static final int NOT_SHOW_DIALOG = 1;
    public static final int IMSI_NOT_READY_OR_SIM_LOCKED = 2;
    public static final int ICCID_ERROR = 3;


    /** sim switch decision based on sim info/status**/
    public static final int DO_SWITCH = 0;
    public static final int NOT_SWITCH = 1;
    public static final int NOT_SWITCH_SIM_INFO_NOT_READY = 2;
    /**
     * Update current main protocol ICCID.
     *
     * @param mProxyPhones Phone array for all phones
     */
    public static void updateIccid(Phone[] mProxyPhones) {
        boolean bIsMajorPhone = false;
        for (int i = 0; i < mProxyPhones.length; i++) {
            bIsMajorPhone = false;
            if (SystemProperties.getInt("ro.mtk_lte_support", 0) == 1) {
                if ((mProxyPhones[i].getRadioAccessFamily() & RadioAccessFamily.RAF_LTE)
                        == RadioAccessFamily.RAF_LTE) {
                    bIsMajorPhone = true;
                }
            } else {
                if ((mProxyPhones[i].getRadioAccessFamily() & RadioAccessFamily.RAF_UMTS)
                        == RadioAccessFamily.RAF_UMTS) {
                    bIsMajorPhone = true;
                }
            }
            if (bIsMajorPhone) {
                String currIccId = SystemProperties.get(PROPERTY_ICCID + (i + 1));
                SystemProperties.set(MAIN_SIM_PROP, currIccId);
                logd("updateIccid " + currIccId);
                break;
            }
        }
    }

    /**
     * Get all SIMs operator and type.
     *
     * @param simOpInfo SIM operator info
     * @param simType SIM type
     */
    public static boolean getSimInfo(int[] simOpInfo, int[] simType, int insertedStatus) {
        String[] strMnc = new String[simOpInfo.length];
        String[] strSimType = new String[simOpInfo.length];
        String propStr;

        for (int i = 0; i < simOpInfo.length; i++) {
            if (i == 0) {
                propStr = "gsm.ril.uicctype";
            } else {
                propStr = "gsm.ril.uicctype." + (i + 1);
            }
            strSimType[i] = SystemProperties.get(propStr, "");
            if (strSimType[i].equals("SIM")) {
                simType[i] = RadioCapabilitySwitchUtil.SIM_TYPE_SIM;
            } else if (strSimType[i].equals("USIM")) {
                simType[i] = RadioCapabilitySwitchUtil.SIM_TYPE_USIM;
            } else {
                simType[i] = RadioCapabilitySwitchUtil.SIM_TYPE_OTHER;
            }
            logd("SimType[" + i + "]= " + strSimType[i] + ", simType[" + i + "]=" + simType[i]);
            strMnc[i] = TelephonyManager.getTelephonyProperty(i, "gsm.sim.operator.imsi", "");
            if (strMnc[i].length() >= 5) {
                strMnc[i] = strMnc[i].substring(0, 5);
            }
            logd("strMnc[" + i + "] from gsm.sim.operator.imsi:" + strMnc[i]);
            if (strMnc[i].equals("")) {
                if (i == 0) {
                    propStr = "gsm.sim.ril.mcc.mnc";
                } else {
                    propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                }
                strMnc[i] = SystemProperties.get(propStr, "");
                logd("strMnc[" + i + "] from ril.mcc.mnc:" + strMnc[i]);
            }
            logd("insertedStatus:" + insertedStatus);
            if ((insertedStatus >= 0) && (((1 << i) & insertedStatus) > 0)) {
                if (strMnc[i].equals("")) {
                    logd("SIM is inserted but no imsi");
                    return false;
                }
                if (strMnc[i].equals("sim_lock")) {
                    logd("SIM is lock, wait pin unlock");
                    return false;
                }
            }
            for (String mccmnc : PLMN_TABLE_TYPE1) {
                if (strMnc[i].equals(mccmnc)) {
                    simOpInfo[i] = SIM_OP_INFO_OP01;
                    break;
                }
            }
            if (simOpInfo[i] == SIM_OP_INFO_UNKNOWN) {
                for (String mccmnc : PLMN_TABLE_TYPE3) {
                    if (strMnc[i].equals(mccmnc)) {
                        simOpInfo[i] = SIM_OP_INFO_OP02;
                        break;
                    }
                }
            }
            if (simOpInfo[i] == SIM_OP_INFO_UNKNOWN) {
                if (!strMnc[i].equals("")) {
                    simOpInfo[i] = SIM_OP_INFO_OVERSEA;
                }
            }
            logd("strMnc[" + i + "]= " + strMnc[i] + ", simOpInfo[" + i + "]=" + simOpInfo[i]);
        }
        logd("getSimInfo(simOpInfo): " + Arrays.toString(simOpInfo));
        logd("getSimInfo(simType): " + Arrays.toString(simType));
        return true;
    }

    /**
     * Check if need to switch capability.
     *
     * @param mProxyPhones Phone array for all phones
     * @param rats new capability for phones
     * @return DO_SWITCH : keep do setCapability
     * @       NOT_SWITCH : don't switch and stay current capability setting
     * @       NOT_SWITCH_SIM_INFO_NOT_READY : don't swith due to sim status/info is not ready (no imsi or sim is locked)
     */
    public static int isNeedSwitchInOpPackage(Phone[] mProxyPhones, RadioAccessFamily[] rats) {
        String operatorSpec = SystemProperties.get("ro.operator.optr", "");
        int[] simOpInfo = new int[mProxyPhones.length];
        int[] simType = new int[mProxyPhones.length];

        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        int insertedSimCount = 0;
        int insertedStatus = 0;
        String[] currIccId = new String[phoneCount];

        logd("Operator Spec:" + operatorSpec);
        if (SystemProperties.getBoolean("ro.mtk_disable_cap_switch", false) == true) {
            logd("mtk_disable_cap_switch is true");
            return NOT_SWITCH;
        } else if (operatorSpec.equals("OP01")) {
            // handle later
        } else {
            // OM package, default enable
            return DO_SWITCH;
        }

        /** Goal: get insertedStatus for getSimInfo();**/
        for (int i = 0; i < phoneCount ; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_SIM_ICCID[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                logd("error: iccid not found, not switch, return NOT_SWITCH_SIM_INFO_NOT_READY");
                return NOT_SWITCH_SIM_INFO_NOT_READY;
            }
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                ++insertedSimCount;
                insertedStatus = insertedStatus | (1 << i);
            }
        }
        if ((false == getSimInfo(simOpInfo, simType, insertedStatus)) &&
                (insertedSimCount != 1)) {
            return NOT_SWITCH_SIM_INFO_NOT_READY;
        }

        // find target phone ID
        int targetPhoneId;
        for (targetPhoneId = 0; targetPhoneId < rats.length; targetPhoneId++) {
            if (SystemProperties.getInt("ro.mtk_lte_support", 0) == 1) {
                if ((rats[targetPhoneId].getRadioAccessFamily() & RadioAccessFamily.RAF_LTE)
                        == RadioAccessFamily.RAF_LTE) {
                    break;
                }
            } else {
                if ((rats[targetPhoneId].getRadioAccessFamily() & RadioAccessFamily.RAF_UMTS)
                        == RadioAccessFamily.RAF_UMTS) {
                    break;
                }
            }
        }
        if (operatorSpec.equals("OP01")) {
            return checkOp01(targetPhoneId, simOpInfo, simType) ? DO_SWITCH : NOT_SWITCH;
        } else {
            return DO_SWITCH;
        }
    }

    /**
     * Check if any higher priority SIM exists.
     *
     * @param curId current phone ID uses main capability
     * @param op01Usim array to indicate if op01 USIM
     * @param op01Sim array to indicate if op01 SIM
     * @param overseaUsim array to indicate if oversea USIM
     * @param overseaSim array to indicate if oversea SIM
     * @return higher priority SIM ID
     */
    public static int getHigherPrioritySimForOp01(int curId, boolean[] op01Usim, boolean[] op01Sim
            , boolean[] overseaUsim, boolean[] overseaSim) {
        int targetSim = -1;
        int phoneNum = op01Usim.length;

        if (op01Usim[curId] == true) {
            return curId;
        }
        for (int i = 0; i < phoneNum; i++) {
            if (op01Usim[i] == true) {
                targetSim = i;
            }
        }
        if (targetSim != -1 || op01Sim[curId] == true) {
            return targetSim;
        }
        for (int i = 0; i < phoneNum; i++) {
            if (op01Sim[i] == true) {
                targetSim = i;
            }
        }
        if (targetSim != -1 || overseaUsim[curId] == true) {
            return targetSim;
        }
        for (int i = 0; i < phoneNum; i++) {
            if (overseaUsim[i] == true) {
                targetSim = i;
            }
        }
        if (targetSim != -1 || overseaSim[curId] == true) {
            return targetSim;
        }
        for (int i = 0; i < phoneNum; i++) {
            if (overseaSim[i] == true) {
                targetSim = i;
            }
        }
        return targetSim;
    }

    private static boolean checkOp01(int targetPhoneId, int[] simOpInfo, int[] simType) {
        int curPhoneId = Integer.valueOf(
                SystemProperties.get(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, "1")) - 1;

        logd("checkOp01 : curPhoneId: " + curPhoneId);
        if (simOpInfo[targetPhoneId] == SIM_OP_INFO_OP01) {
            if (simType[targetPhoneId] == SIM_TYPE_SIM) {
                if ((simOpInfo[curPhoneId] == SIM_OP_INFO_OP01)
                    && simType[curPhoneId] != SIM_TYPE_SIM) {
                    logd("checkOp01 : case 1,2; stay in current phone");
                    return false;
                } else {
                    // case 3: op01-SIM + op01-SIM
                    // case 4: op01-SIM + others
                    logd("checkOp01 : case 3,4");
                    return true;
                }
            } else { // USIM, ISIM...
                // case 1: op01-USIM + op01-USIM
                // case 2: op01-USIM + others
                logd("checkOp01 : case 1,2");
                return true;
            }
        } else if (simOpInfo[targetPhoneId] == SIM_OP_INFO_OVERSEA) {
            if (simOpInfo[curPhoneId] == SIM_OP_INFO_OP01) {
                logd("checkOp01 : case 1,2,3,4; stay in current phone");
                return false;
            } else if (simType[targetPhoneId] == SIM_TYPE_SIM) {
                if ((simOpInfo[curPhoneId] == SIM_OP_INFO_OVERSEA)
                    && simType[curPhoneId] != SIM_TYPE_SIM) {
                    logd("checkOp01 : case 5,6; stay in current phone");
                    return false;
                } else {
                    // case 7: non-China SIM + non-China SIM
                    // case 8: non-China SIM + others
                    logd("checkOp01 : case 7,8");
                    return true;
                }
            } else { // USIM, ISIM...
                // case 5: non-China USIM + non-China USIM
                // case 6: non-China USIM + others
                logd("checkOp01 : case 5,6");
                return true;
            }
        } else {
            if (simOpInfo[targetPhoneId] == SIM_OP_INFO_UNKNOWN) {
                logd("checkOp01 : case 10, target IMSI not ready");
                int insertedStatus = 0;
                int phoneNum = simOpInfo.length;
                String[] currIccId = new String[phoneNum];
                for (int i = 0; i < phoneNum; i++) {
                    currIccId[i] = SystemProperties.get(PROPERTY_ICCID + (i + 1));
                    if (!NO_SIM_VALUE.equals(currIccId[i])) {
                        insertedStatus = insertedStatus | (1 << i);
                    }
                }
                if (insertedStatus <= 2) {
                    logd("checkOp01 : case 10, single SIM case, switch!");
                    return true;
                }
            }
            if (SystemProperties.get("ro.mtk_world_phone_policy").equals("1")) {
                if ((simOpInfo[curPhoneId] != SIM_OP_INFO_OP01) &&
                        (simOpInfo[curPhoneId] != SIM_OP_INFO_OVERSEA)) {
                    logd("checkOp01 : case 11, op01-B, switch it!");
                    return true;
                }
            }
            // case 9: non-op01 USIM/SIM + non-op01 USIM/SIM
            logd("checkOp01 : case 9");
            return false;
        }
    }

    /**
     * Get main capability phone ID.
     *
     * @return Phone ID with main capability
     */
    public static int getMainCapabilityPhoneId() {
        int phoneId = 0;
        if (SystemProperties.getBoolean("ro.mtk_dt_support", false) == true) {
            int swapMode = SystemProperties.getInt("persist.ril.simswitch.swapmode", 3);
            if (swapMode == SIM_SWITCH_MODE_DUAL_TALK) {
                phoneId = 0;
            } else if (swapMode == SIM_SWITCH_MODE_DUAL_TALK_SWAP) {
                phoneId = 1;
            }
        } else {
            phoneId = SystemProperties.getInt(PhoneConstants.PROPERTY_CAPABILITY_SWITCH, 1) - 1;
        }
        // MTK TODO
        /*
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
            if (phoneId == PhoneConstants.SIM_ID_1) {
                if (SvlteUtils.getSvltePhoneProxy(phoneId).getActivePhone().
                    getPhoneName()== "CDMA") {
                    // for svlte project, we need re-mapping slot 0 to DC phone
                    phoneId = SubscriptionManager.LTE_DC_PHONE_ID_1;
                }
            }
            if (phoneId == PhoneConstants.SIM_ID_2) {
                if (SvlteUtils.getSvltePhoneProxy(phoneId).getActivePhone().
                    getPhoneName()== "CDMA") {
                    // for svlte project, we need re-mapping slot 1 to DC phone
                    phoneId = SubscriptionManager.LTE_DC_PHONE_ID_2;
                }
            }
        }
        */
        Log.d(LOG_TAG, "[RadioCapSwitchUtil] getMainCapabilityPhoneId " + phoneId);
        return phoneId;
    }

    private static void logd(String s) {
        Log.d(LOG_TAG, "[RadioCapSwitchUtil] " + s);
    }

    public static boolean isSimContainsCdmaApp(int simId) {
        // MTK TODO
        /*
        int[] cardType = new int[TelephonyManager.getDefault().getPhoneCount()];
        cardType = UiccController.getInstance().getC2KWPCardType();
        Log.d(LOG_TAG, "[RadioCapSwitchUtil][getCardType]: SIM" + simId + " type: " + cardType[simId]);
        if ((cardType[simId] & UiccController.CARD_TYPE_RUIM) > 0
                || (cardType[simId] & UiccController.CARD_TYPE_CSIM) > 0) {
            return true;
        }
        */
        return false;
    }

    /**
     * Check SIM type and operator type to decide displaying dialog or not.
     *
     * @return SHOW_DIALOG: show dialog
     *             NOT_SHOW_DIALOG: don't show dialog
     *             IMSI_NOT_READY_OR_SIM_LOCKED: IMSI not ready or sim locked. Wait
     *             ACTION_SIM_STATE_CHANGED to check isNeedShowSimDialog() again
     *             ICCID_ERROR: read ICCID error. Wait ACTION_SIM_STATE_CHANGED
     *              to check isNeedShowSimDialog() again
     */
    public static int isNeedShowSimDialog() {
        if (SystemProperties.getBoolean("ro.mtk_disable_cap_switch", false) == true) {
            logd("mtk_disable_cap_switch is true");
            return SHOW_DIALOG;
        }

        logd("isNeedShowSimDialog start");
        int phoneCount = TelephonyManager.getDefault().getPhoneCount();
        int[] simOpInfo = new int[phoneCount];
        int[] simType = new int[phoneCount];
        String[] currIccId = new String[phoneCount];
        int insertedSimCount = 0;
        int insertedStatus = 0;
        int op02CardCount = 0;
        ArrayList<Integer> usimIndexList = new ArrayList<Integer>();
        ArrayList<Integer> simIndexList = new ArrayList<Integer>();
        ArrayList<Integer> op02IndexList = new ArrayList<Integer>();
        ArrayList<Integer> otherIndexList = new ArrayList<Integer>();

        for (int i = 0; i < phoneCount; i++) {
            currIccId[i] = SystemProperties.get(PROPERTY_SIM_ICCID[i]);
            logd("currIccid[" + i + "] : " + currIccId[i]);
            if (currIccId[i] == null || "".equals(currIccId[i])) {
                Log.e(LOG_TAG, "iccid not found, wait for next sim state change");
                return ICCID_ERROR;
            }
            if (!NO_SIM_VALUE.equals(currIccId[i])) {
                insertedSimCount++;
                insertedStatus = insertedStatus | (1 << i);
            }
        }

        if (insertedSimCount < 2) {
            logd("isNeedShowSimDialog: insert sim count < 2, do not show dialog");
            return NOT_SHOW_DIALOG;
        }

        if (getSimInfo(simOpInfo, simType, insertedStatus) == false) {
            Log.e(LOG_TAG, "Can't get SIM information");
            return IMSI_NOT_READY_OR_SIM_LOCKED;
        }
        for (int i = 0; i < phoneCount; i++) {
            // check SIM type
            if (SIM_TYPE_USIM == simType[i]) {
                usimIndexList.add(i);
            } else if (SIM_TYPE_SIM == simType[i]) {
                simIndexList.add(i);
            }

            // check SIM operator
            if (SIM_OP_INFO_OP02 == simOpInfo[i]) {
                op02IndexList.add(i);
            } else {
                otherIndexList.add(i);
            }
        }
        logd("usimIndexList size = " + usimIndexList.size());
        logd("op02IndexList size = " + op02IndexList.size());

        if (usimIndexList.size() >= 2) {
            // check OP02USIM count
            for (int i = 0; i < usimIndexList.size(); i++) {
                if (op02IndexList.contains(usimIndexList.get(i))) {
                    op02CardCount++;
                }
            }

            if (op02CardCount == 1) {
                logd("One OP02Usim inserted, not show dialog");
                return NOT_SHOW_DIALOG;
            }
        } else if (usimIndexList.size() == 1) {
            logd("One Usim inserted, not show dialog");
            return NOT_SHOW_DIALOG;
        } else {
            // usimIndexList.size() = 0 (all SIM cards)
            // check OP02SIM count
            for (int i = 0; i < simIndexList.size(); i++) {
                if (op02IndexList.contains(simIndexList.get(i))) {
                    op02CardCount++;
                }
            }

            if (op02CardCount == 1) {
                logd("One non-OP02 Usim inserted, not show dialog");
                return NOT_SHOW_DIALOG;
            }
        }
        logd("Show dialog");
        return SHOW_DIALOG;
    }

    public static boolean isAnySimLocked(int phoneNum) {
        if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
           logd("isAnySimLocked always returns false in C2K");
           return false;
        }

        // iccid property is not equal to N/A and imsi property is empty => sim locked
        String[] mnc = new String[phoneNum];
        String[] iccid = new String[phoneNum];
        String propStr;
        for (int i=0; i<phoneNum; i++) {
            iccid[i] = SystemProperties.get(PROPERTY_SIM_ICCID[i]);
            // only get mcc,mnc when SIM inserted
            if (!iccid[i].equals(NO_SIM_VALUE)) {
                mnc[i] = TelephonyManager.getTelephonyProperty(i, "gsm.sim.operator.imsi", "");
                if (mnc[i].length() >= 5) {
                    mnc[i] = mnc[i].substring(0, 5);
                }
                if (mnc[i].equals("")) {
                    if (i == 0) {
                        propStr = "gsm.sim.ril.mcc.mnc";
                    } else {
                        propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                    }
                    mnc[i] = SystemProperties.get(propStr, "");
                    logd("mnc[" + i + "] from ril.mcc.mnc:" + mnc[i]);
                }
            }
            logd("i = " + i + " from gsm.sim.operator.imsi:" + mnc[i] + " ,iccid = " + iccid[i]);
            if (!iccid[i].equals(NO_SIM_VALUE) && (mnc[i].equals("") ||
                    mnc[i].equals("sim_lock"))) {
                return true;
            } else {
                // SIM does not locked
                logd("clear mcc.mnc:" + i);
                if (i == 0) {
                    propStr = "gsm.sim.ril.mcc.mnc";
                } else {
                    propStr = "gsm.sim.ril.mcc.mnc." + (i + 1);
                }
                SystemProperties.set(propStr, "");
            }
        }
        return false;
    }
}
