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

package com.mediatek.internal.telephony.worldphone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;

public class WorldMode extends Handler{
    private static final String LOG_TAG = "PHONE";

    public static final int MD_WORLD_MODE_UNKNOWN = 0;
    public static final int MD_WORLD_MODE_LTG     = 8;   //uLTG
    public static final int MD_WORLD_MODE_LWG     = 9;   //uLWG
    public static final int MD_WORLD_MODE_LWTG    = 10;  //uLWTG
    public static final int MD_WORLD_MODE_LWCG    = 11;  //uLWCG
    public static final int MD_WORLD_MODE_LWCTG   = 12;  //uLWTCG(Auto mode)
    public static final int MD_WORLD_MODE_LTTG    = 13;  //LtTG
    public static final int MD_WORLD_MODE_LFWG    = 14;  //LfWG

    public static final int MD_WM_CHANGED_UNKNOWN = -1;
    public static final int MD_WM_CHANGED_START   = 0;
    public static final int MD_WM_CHANGED_END     = 1;

    static final int EVENT_RADIO_ON_1 = 1;
    static final int EVENT_RADIO_ON_2 = 2;

    static final String ACTION_ADB_SWITCH_WORLD_MODE =
               "android.intent.action.ACTION_ADB_SWITCH_WORLD_MODE";
    static final String EXTRA_WORLDMODE = "worldMode";

    /** The singleton instance. */
    private static WorldMode sInstance;

    private static final int PROJECT_SIM_NUM = WorldPhoneUtil.getProjectSimNum();
    private static int sCurrentWorldMode = updateCurrentWorldMode();
    private static int sActiveWorldMode = MD_WORLD_MODE_UNKNOWN;
    private static boolean sSwitchingState = false;
    private static Phone[] sProxyPhones = null;
    private static Phone[] sActivePhones = new Phone[PROJECT_SIM_NUM];
    private static Context sContext = null;
    private static CommandsInterface[] sCi = new CommandsInterface[PROJECT_SIM_NUM];

    public WorldMode() {
        logd("Constructor invoked");
        logd("Init world mode: " + sCurrentWorldMode);
        sProxyPhones = PhoneFactory.getPhones();
        for (int i = 0; i < PROJECT_SIM_NUM; i++) {
            sActivePhones[i] = ((PhoneProxy) sProxyPhones[i]).getActivePhone();
            sCi[i] = ((PhoneBase) sActivePhones[i]).mCi;
            sCi[i].registerForOn(this, EVENT_RADIO_ON_1 + i, null);
        }

        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_WORLD_MODE_CHANGED);
        intentFilter.addAction(ACTION_ADB_SWITCH_WORLD_MODE);

        if (PhoneFactory.getDefaultPhone() != null) {
            sContext = PhoneFactory.getDefaultPhone().getContext();
        } else {
            logd("DefaultPhone = null");
        }
        sContext.registerReceiver(mWorldModeReceiver, intentFilter);
    }

    /**
     * Initialize the singleton WorldMode instance.
     * This is only done once, at startup, from PhoneFactory.makeDefaultPhone().
     */
    public static void init() {
        synchronized (WorldMode.class) {
            if (sInstance == null) {
                sInstance = new WorldMode();
            } else {
                logd("init() called multiple times!  sInstance = " + sInstance);
            }
        }
    }

    private final BroadcastReceiver mWorldModeReceiver = new  BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            logd("[Receiver]+");
            String action = intent.getAction();
            logd("Action: " + action);
            int wmState = MD_WM_CHANGED_UNKNOWN;

            if (TelephonyIntents.ACTION_WORLD_MODE_CHANGED.equals(action)) {
                wmState = intent.getIntExtra(
                        TelephonyIntents.EXTRA_WORLD_MODE_CHANGE_STATE, MD_WM_CHANGED_UNKNOWN);
                logd("wmState: " + wmState);
                if (wmState == MD_WM_CHANGED_END) {
                    sCurrentWorldMode = updateCurrentWorldMode();
                }
            } else if (ACTION_ADB_SWITCH_WORLD_MODE.equals(action)){
                int toMode = intent.getIntExtra(
                        EXTRA_WORLDMODE, MD_WORLD_MODE_UNKNOWN);
                logd("toModem: " + toMode);
                if (toMode == MD_WORLD_MODE_LTG
                        || toMode == MD_WORLD_MODE_LWG
                        || toMode == MD_WORLD_MODE_LWTG
                        || toMode == MD_WORLD_MODE_LTTG) {
                    setWorldMode(toMode);
                }
            }
            logd("[Receiver]-");
        }
    };

    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        int protocolSim = WorldPhoneUtil.getMajorSim();
        switch (msg.what) {
            case EVENT_RADIO_ON_1:
                logd("handleMessage : <EVENT_RADIO_ON_1>");
                protocolSim = WorldPhoneUtil.getMajorSim();
                if (protocolSim == PhoneConstants.SIM_ID_1) {
                    sCurrentWorldMode = updateCurrentWorldMode();
                }
                break;
            case EVENT_RADIO_ON_2:
                logd("handleMessage : <EVENT_RADIO_ON_2>");
                protocolSim = WorldPhoneUtil.getMajorSim();
                if (protocolSim == PhoneConstants.SIM_ID_2) {
                    sCurrentWorldMode = updateCurrentWorldMode();
                }
                break;
            default:
                logd("Unknown msg:" + msg.what);
        }
    }

    /**
     * Trigger RILD to switch world mode
     * @param worldMode 0 : world mode is unknown
     *                  8 : uTLG (MD_WORLD_MODE_LTG)
     *                  9 : uLWG (MD_WORLD_MODE_LWG)
     *                  10 : uLWTG (MD_WORLD_MODE_LWTG)
     *                  11 : uLWCG (MD_WORLD_MODE_LWCG)
     *                  12 : uLWTCG (MD_WORLD_MODE_LWCTG)
     *                  13 : LtTG (MD_WORLD_MODE_LTTG)
     *                  14 : LfWG (MD_WORLD_MODE_LFWG)
     */
    public static void setWorldMode(int worldMode) {
        int protocolSim = WorldPhoneUtil.getMajorSim();
        logd("[setWorldMode]protocolSim: " + protocolSim);
        if (protocolSim >= PhoneConstants.SIM_ID_1 &&
                protocolSim <= PhoneConstants.SIM_ID_4) {
            setWorldMode(sCi[protocolSim], worldMode);
        } else {
            setWorldMode(sCi[PhoneConstants.SIM_ID_1], worldMode);
        }
    }

    /**
     * Trigger RILD to switch world mode with desinated RIL instance
     * @param worldMode 0 : world mode is unknown
     *                  8 : uTLG (MD_WORLD_MODE_LTG)
     *                  9 : uLWG (MD_WORLD_MODE_LWG)
     *                  10 : uLWTG (MD_WORLD_MODE_LWTG)
     *                  11 : uLWCG (MD_WORLD_MODE_LWCG)
     *                  12 : uLWTCG (MD_WORLD_MODE_LWCTG)
     *                  13 : LtTG (MD_WORLD_MODE_LTTG)
     *                  14 : LfWG (MD_WORLD_MODE_LFWG)
     */
    private static void setWorldMode(CommandsInterface ci, int worldMode) {
        logd("[setWorldMode] worldMode=" + worldMode);
        if (worldMode == sCurrentWorldMode) {
            if (worldMode == MD_WORLD_MODE_LTG) {
                logd("Already in uTLG mode");
            } else if (worldMode == MD_WORLD_MODE_LWG) {
                logd("Already in uLWG mode");
            } else if (worldMode == MD_WORLD_MODE_LWTG) {
                logd("Already in uLWTG mode");
            } else if (worldMode == MD_WORLD_MODE_LWCG) {
                logd("Already in uLWCG mode");
            } else if (worldMode == MD_WORLD_MODE_LWCTG) {
                logd("Already in uLWTCG mode");
            } else if (worldMode == MD_WORLD_MODE_LTTG) {
                logd("Already in LtTG mode");
            } else if (worldMode == MD_WORLD_MODE_LFWG) {
                logd("Already in LfWG mode");
            }
            return;
        }
        if (ci.getRadioState() ==
                CommandsInterface.RadioState.RADIO_UNAVAILABLE) {
            logd("Radio unavailable, can not switch world mode");
            return;
        }
        if ((worldMode >= MD_WORLD_MODE_LTG) &&
                (worldMode <= MD_WORLD_MODE_LFWG)){
            ci.reloadModemType(worldMode, null);
            ci.storeModemType(worldMode, null);
            ci.setTrm(2, null);
        } else {
            logd("Invalid world mode:" + worldMode);
            return;
        }
    }

    /**
     * Returns modem world mode
     *
     * @return 0 : world mode is unknown
     *         8 : uTLG (MD_WORLD_MODE_LTG)
     *         9 : uLWG (MD_WORLD_MODE_LWG)
     *         10 : uLWTG (MD_WORLD_MODE_LWTG)
     *         11 : uLWCG (MD_WORLD_MODE_LWCG)
     *         12 : uLWTCG (MD_WORLD_MODE_LWCTG)
     *         13 : LtTG (MD_WORLD_MODE_LTTG)
     *         14 : LfWG (MD_WORLD_MODE_LFWG)
     */
    public static int getWorldMode() {
        logd("getWorldMode=" + WorldModeToString(sCurrentWorldMode));
        return sCurrentWorldMode;
    }

    /**
     * Returns modem world mode
     *
     * @return 0 : world mode is unknown
     *         8 : uTLG (MD_WORLD_MODE_LTG)
     *         9 : uLWG (MD_WORLD_MODE_LWG)
     *         10 : uLWTG (MD_WORLD_MODE_LWTG)
     *         11 : uLWCG (MD_WORLD_MODE_LWCG)
     *         12 : uLWTCG (MD_WORLD_MODE_LWCTG)
     *         13 : LtTG (MD_WORLD_MODE_LTTG)
     *         14 : LfWG (MD_WORLD_MODE_LFWG)
     */
    private static int updateCurrentWorldMode() {
        sCurrentWorldMode = Integer.valueOf(
                SystemProperties.get(TelephonyProperties.PROPERTY_ACTIVE_MD,
                Integer.toString(MD_WORLD_MODE_UNKNOWN)));
        logd("updateCurrentWorldMode=" + WorldModeToString(sCurrentWorldMode));
        return sCurrentWorldMode;
    }

    public static void updateSwitchingState(boolean isSwitching){
        sSwitchingState = isSwitching;
        logd("updateSwitchingState=" + sSwitchingState);
    }

    public static boolean isWorldModeSwitching(){
        if (sSwitchingState){
            return true;
        } else {
            return false;
        }
    }

    public static String WorldModeToString(int worldMode) {
        String worldModeString;
        if (worldMode == MD_WORLD_MODE_LTG) {
            worldModeString = "uTLG";
        } else if (worldMode == MD_WORLD_MODE_LWG) {
            worldModeString = "uLWG";
        } else if (worldMode == MD_WORLD_MODE_LWTG) {
            worldModeString = "uLWTG";
        } else if (worldMode == MD_WORLD_MODE_LWCG) {
            worldModeString = "uLWCG";
        } else if (worldMode == MD_WORLD_MODE_LWCTG) {
            worldModeString = "uLWTCG";
        } else if (worldMode == MD_WORLD_MODE_LTTG) {
            worldModeString = "LtTG";
        } else if (worldMode == MD_WORLD_MODE_LFWG) {
            worldModeString = "LfWG";
        } else {
            worldModeString = "Invalid world mode";
        }

        return worldModeString;
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[WorldMode]" + msg);
    }
}
