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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;


class SubscriptionHelper extends Handler {
    private static final String LOG_TAG = "SubHelper";
    private static SubscriptionHelper sInstance;

    private Context mContext;
    private CommandsInterface[] mCi;
    private int[] pendingSubStatus;

    private static final int EVENT_SET_UICC_SUBSCRIPTION_DONE = 1;

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
        int numPhones = TelephonyManager.getDefault().getPhoneCount();
        pendingSubStatus = new int[numPhones];

        logd("SubscriptionHelper init by Context");
    }

    @Override
    public void handleMessage(Message msg) {
        switch(msg.what) {
            case EVENT_SET_UICC_SUBSCRIPTION_DONE:
                logd("EVENT_SET_UICC_SUBSCRIPTION_DONE");
                processSetUiccSubscriptionDone((AsyncResult)msg.obj);
                break;
           default:
           break;
        }
    }

    public void setUiccSubscription(int slotId, int subStatus) {
        pendingSubStatus[slotId] = subStatus;
        boolean set3GPPDone = false, set3GPP2Done = false;
        UiccCard uiccCard = UiccController.getInstance().getUiccCard(slotId);

        //Activate/Deactivate first 3GPP and 3GPP2 app in the sim, if available
        for (int i = 0; i < uiccCard.getNumApplications(); i++) {
            int appType = uiccCard.getApplicationIndex(i).getType().ordinal();
            if (set3GPPDone == false && (appType == PhoneConstants.APPTYPE_USIM ||
                    appType == PhoneConstants.APPTYPE_SIM)) {
                Message msgSetUiccSubDone = Message.obtain(
                        this, EVENT_SET_UICC_SUBSCRIPTION_DONE, new Integer(slotId));
                mCi[slotId].setUiccSubscription(slotId, i, slotId, subStatus, msgSetUiccSubDone);
                set3GPPDone = true;
            } else if (set3GPP2Done == false && (appType == PhoneConstants.APPTYPE_CSIM ||
                    appType == PhoneConstants.APPTYPE_RUIM)) {
                Message msgSetUiccSubDone = Message.obtain(
                        this, EVENT_SET_UICC_SUBSCRIPTION_DONE, new Integer(slotId));
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
    private void processSetUiccSubscriptionDone(AsyncResult ar) {
        Integer slotId = (Integer)ar.userObj;
        boolean saveGlobalSettings = false;

        if (ar.exception != null) {
            return;
        }

        SubscriptionController subCtrlr = SubscriptionController.getInstance();
        if (subCtrlr != null) {
            long[] subId = subCtrlr.getSubIdUsingSlotId(slotId);
            int subStatus = subCtrlr.getSubState(subId[0]);

            if (pendingSubStatus[slotId] != subStatus) {
                subCtrlr.setSubState(subId[0], pendingSubStatus[slotId]);
            }
        }
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
