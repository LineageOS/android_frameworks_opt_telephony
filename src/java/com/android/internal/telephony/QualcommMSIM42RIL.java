/*
 * Copyright (C) 2012-2013 The CyanogenMod Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.os.Message;

/**
 * Backwards compatible RIL implementation for Qualcomm MSIM based
 * radios. Android 4.3 added the CELL_INFO_LIST commands, displacing several
 * command ids already used in pre-4.3 RILs.
 * 
 * {@hide}
 */
public class QualcommMSIM42RIL extends RIL implements CommandsInterface {

    static final int RIL_REQUEST_IMS_REGISTRATION_STATE = 109;
    static final int RIL_REQUEST_IMS_SEND_SMS = 110;
    static final int RIL_REQUEST_GET_DATA_CALL_PROFILE = 111;
    static final int RIL_REQUEST_SET_UICC_SUBSCRIPTION = 118;
    static final int RIL_REQUEST_SET_DATA_SUBSCRIPTION = 119;
    static final int RIL_REQUEST_GET_UICC_SUBSCRIPTION = 120;
    static final int RIL_REQUEST_GET_DATA_SUBSCRIPTION = 121;
    static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 1036;
    static final int RIL_UNSOL_TETHERED_MODE_STATE_CHANGED = 1037;

    public QualcommMSIM42RIL(Context context, int networkMode,
            int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getCellInfoList(Message result) {
        if (RILJ_LOGD) riljLog("[STUB] > getCellInfoList");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCellInfoListRate(int rateInMillis, Message response) {
        if (RILJ_LOGD) riljLog("[STUB] > setCellInfoListRate");
    }

    public void getImsRegistrationState(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_IMS_REGISTRATION_STATE, result);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    public void setUiccSubscription(int slotId, int appIndex, int subId,
            int subStatus, Message result) {
        //Note: This RIL request is also valid for SIM and RUIM (ICC card)
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_UICC_SUBSCRIPTION, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " slot: " + slotId + " appIndex: " + appIndex
                + " subId: " + subId + " subStatus: " + subStatus);

        rr.mParcel.writeInt(slotId);
        rr.mParcel.writeInt(appIndex);
        rr.mParcel.writeInt(subId);
        rr.mParcel.writeInt(subStatus);

        send(rr);
    }

    public void setDataSubscription(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_DATA_SUBSCRIPTION, result);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void
    getDataCallProfile(int appType, Message result) {
        RILRequest rr = RILRequest.obtain(
                RIL_REQUEST_GET_DATA_CALL_PROFILE, result);

        // count of ints
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(appType);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + appType);

        send(rr);
    }

}
