/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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
 *
 */

package com.android.internal.telephony;


import android.telephony.Rlog;
import android.content.Context;
import android.database.ContentObserver;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.ModemBindingPolicyHandler;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;


class SubscriptionHelper extends Handler {
    private static final String LOG_TAG = "SubHelper";
    private static SubscriptionHelper sInstance;

    private Context mContext;
    private CommandsInterface[] mCi;
    private int[] mSubStatus;
    private static int sNumPhones;
    // This flag is used to trigger Dds during boot-up
    // and when flex mapping performed
    private static boolean sTriggerDds = true;

    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 1;

    public static final int SUB_SIM_NOT_INSERTED = -99;
    public static final int SUB_INIT_STATE = -1;
    private static boolean mNwModeUpdated = false;

    private final ContentObserver nwModeObserver =
        new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfUpdate) {
                logd("NwMode Observer onChange hit !!!");
                if (!mNwModeUpdated) return;
                //get nwMode from all slots in Db and update to subId table.
                updateNwModesInSubIdTable(true);
            }
        };


    public static SubscriptionHelper init(Context c, CommandsInterface[] ci) {
        synchronized (SubscriptionHelper.class) {
            if (sInstance == null) {
                sInstance = new SubscriptionHelper(c, ci);
            } else {
                Log.wtf(LOG_TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    public static SubscriptionHelper getInstance() {
        if (sInstance == null) {
           Log.wtf(LOG_TAG, "getInstance null");
        }

        return sInstance;
    }

    private SubscriptionHelper(Context c, CommandsInterface[] ci) {
        mContext = c;
        mCi = ci;
        sNumPhones = TelephonyManager.getDefault().getPhoneCount();
        mSubStatus = new int[sNumPhones];
        for (int i=0; i < sNumPhones; i++ ) {
            mSubStatus[i] = SUB_INIT_STATE;
        }
        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.PREFERRED_NETWORK_MODE), false, nwModeObserver);


        logd("SubscriptionHelper init by Context, num phones = " + sNumPhones);
    }

    private void updateNwModesInSubIdTable(boolean override) {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        for (int i=0; i < sNumPhones; i++ ) {
            long[] subIdList = subCtrlr.getSubId(i);
            if (subIdList != null && subIdList[0] > 0) {
                int nwModeInDb;
                try {
                    nwModeInDb = TelephonyManager.getIntAtIndex(mContext.getContentResolver(),
                            Settings.Global.PREFERRED_NETWORK_MODE, i);
                } catch (SettingNotFoundException snfe) {
                    loge("Settings Exception Reading Value At Index[" + i +
                            "] Settings.Global.PREFERRED_NETWORK_MODE");
                    nwModeInDb = RILConstants.PREFERRED_NETWORK_MODE;
                }
                int nwModeinSubIdTable = subCtrlr.getNwMode(subIdList[0]);
                logd("updateNwModesInSubIdTable: nwModeinSubIdTable: " + nwModeinSubIdTable
                        + ", nwModeInDb: " + nwModeInDb);

                //store Db value to table only if value in table is default
                //OR if override is set to True.
                if (override || nwModeinSubIdTable == SubscriptionManager.DEFAULT_NW_MODE) {
                    subCtrlr.setNwMode(subIdList[0], nwModeInDb);
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case EVENT_SET_UICC_SUBSCRIPTION_DONE:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone(msg);
                break;
           default:
           break;
        }
    }

    public void updateSubActivation(int[] simStatus, boolean isStackReadyEvent) {
        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        boolean setUiccSent = false;
        if (isStackReadyEvent) {
            sTriggerDds = true;
        }

        for (int slotId = 0; slotId < sNumPhones; slotId++) {
            if (simStatus[slotId] == SUB_SIM_NOT_INSERTED) {
                mSubStatus[slotId] = simStatus[slotId];
                logd(" Sim not inserted in slot [" + slotId + "] simStatus= " + simStatus[slotId]);
                continue;
            }
            long[] subId = subCtrlr.getSubId(slotId);
            int subState = subCtrlr.getSubState(subId[0]);

            logd("setUicc for [" + slotId + "] = " + subState + "subId = " + subId[0] +
                    " prev subState = " + mSubStatus[slotId] + " stackReady " + isStackReadyEvent);
            // Do not send SET_UICC if its already sent with same state
            if ((mSubStatus[slotId] != subState) || isStackReadyEvent) {
                // If sim card present in the slot, get the stored sub status and
                // perform the activation/deactivation of subscription
                setUiccSubscription(slotId, subState);
                setUiccSent = true;
            }
        }
        // If at least one uiccrequest sent, updateUserPrefs() will be called
        // from processSetUiccSubscriptionDone()
        if (isAllSubsAvailable() && (!setUiccSent)) {
            logd("Received all sim info, update user pref subs, triggerDds= " + sTriggerDds);
            subCtrlr.updateUserPrefs(sTriggerDds);
            sTriggerDds = false;
        }
    }

    public void updateNwMode() {
        updateNwModesInSubIdTable(false);
        ModemBindingPolicyHandler.getInstance().updatePrefNwTypeIfRequired();
        mNwModeUpdated = true;
    }

    public void setUiccSubscription(int slotId, int subStatus) {
        boolean set3GPPDone = false, set3GPP2Done = false;
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(slotId);
        if (uiccCard == null) {
            logd("setUiccSubscription: slotId:" + slotId + " card info not available");
            return;
        }

        //Activate/Deactivate first 3GPP and 3GPP2 app in the sim, if available
        for (int i = 0; i < uiccCard.getNumApplications(); i++) {
            int appType = uiccCard.getApplicationIndex(i).getType().ordinal();
            if (set3GPPDone == false && (appType == PhoneConstants.APPTYPE_USIM ||
                    appType == PhoneConstants.APPTYPE_SIM)) {
                Message msgSetUiccSubDone = Message.obtain(
                        this, EVENT_SET_UICC_SUBSCRIPTION_DONE, slotId, subStatus);
                mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, msgSetUiccSubDone);
                set3GPPDone = true;
            } else if (set3GPP2Done == false && (appType == PhoneConstants.APPTYPE_CSIM ||
                    appType == PhoneConstants.APPTYPE_RUIM)) {
                Message msgSetUiccSubDone = Message.obtain(
                        this, EVENT_SET_UICC_SUBSCRIPTION_DONE, slotId, subStatus);
                mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, msgSetUiccSubDone);
                set3GPP2Done = true;
            }

            if (set3GPPDone && set3GPP2Done) break;
        }
    }

    /**
     * Handles the EVENT_SET_UICC_SUBSCRPTION_DONE.
     * @param ar
     */
    private void processSetUiccSubscriptionDone(Message msg) {
        AsyncResult ar = (AsyncResult)msg.obj;
        int slotId = msg.arg1;
        int newSubState = msg.arg2;

        if (ar.exception != null) {
            logd("Exception in SET_UICC_SUBSCRIPTION, slotId = " + slotId);
            return;
        }

        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        if (subCtrlr != null) {
            long[] subId = subCtrlr.getSubIdUsingSlotId(slotId);
            int subStatus = subCtrlr.getSubState(subId[0]);

            if (newSubState != subStatus) {
                subCtrlr.setSubState(subId[0], newSubState);
            }
        }

        mSubStatus[slotId] = newSubState;
        // After activating all subs, updated the user preferred sub values
        if (isAllSubsAvailable()) {
            logd("Received all subs, now update user preferred subs, slotid = " + slotId
                    + " newSubState = " + newSubState + " sTriggerDds = " + sTriggerDds);
            subCtrlr.updateUserPrefs(sTriggerDds);
            sTriggerDds = false;
        }
    }

    private boolean isAllSubsAvailable() {
        boolean allSubsAvailable = true;

        for (int i=0; i < sNumPhones; i++) {
            if (mSubStatus[i] == SUB_INIT_STATE) {
                allSubsAvailable = false;
            }
        }
        return allSubsAvailable;
    }

    private static void logd(String message) {
        Rlog.d(LOG_TAG,  message);
    }

    private void logi(String msg) {
        Rlog.i(LOG_TAG,  msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG,  msg);
    }
}
