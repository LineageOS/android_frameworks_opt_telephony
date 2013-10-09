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

    @Override
    protected int translateOut(int id) {
        switch(id) {
            case RILConstants.RIL_REQUEST_IMS_REGISTRATION_STATE:
                return RIL_REQUEST_IMS_REGISTRATION_STATE;
            case RILConstants.RIL_REQUEST_IMS_SEND_SMS:
                return RIL_REQUEST_IMS_SEND_SMS;
            case RILConstants.RIL_REQUEST_GET_DATA_CALL_PROFILE:
                return RIL_REQUEST_GET_DATA_CALL_PROFILE;
            case RILConstants.RIL_REQUEST_SET_UICC_SUBSCRIPTION:
                return RIL_REQUEST_SET_UICC_SUBSCRIPTION;
            case RILConstants.RIL_REQUEST_SET_DATA_SUBSCRIPTION:
                return RIL_REQUEST_SET_DATA_SUBSCRIPTION;
            case RILConstants.RIL_REQUEST_GET_UICC_SUBSCRIPTION:
                return RIL_REQUEST_GET_UICC_SUBSCRIPTION;
            case RILConstants.RIL_REQUEST_GET_DATA_SUBSCRIPTION:
                return RIL_REQUEST_GET_DATA_SUBSCRIPTION;
            case RILConstants.RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
                return RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED;
            case RILConstants.RIL_UNSOL_TETHERED_MODE_STATE_CHANGED:
                return RIL_UNSOL_TETHERED_MODE_STATE_CHANGED;
        }
        return id;
    }
    
    @Override
    protected int translateIn(int id) {
        switch(id) {
            case RIL_REQUEST_IMS_REGISTRATION_STATE:
                return RILConstants.RIL_REQUEST_IMS_REGISTRATION_STATE;
            case RIL_REQUEST_IMS_SEND_SMS:
                return RILConstants.RIL_REQUEST_IMS_SEND_SMS;
            case RIL_REQUEST_GET_DATA_CALL_PROFILE:
                return RILConstants.RIL_REQUEST_GET_DATA_CALL_PROFILE;
            case RIL_REQUEST_SET_UICC_SUBSCRIPTION:
                return RILConstants.RIL_REQUEST_SET_UICC_SUBSCRIPTION;
            case RIL_REQUEST_SET_DATA_SUBSCRIPTION:
                return RILConstants.RIL_REQUEST_SET_DATA_SUBSCRIPTION;
            case RIL_REQUEST_GET_UICC_SUBSCRIPTION:
                return RILConstants.RIL_REQUEST_GET_UICC_SUBSCRIPTION;
            case RIL_REQUEST_GET_DATA_SUBSCRIPTION:
                return RILConstants.RIL_REQUEST_GET_DATA_SUBSCRIPTION;
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
                return RILConstants.RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED;
            case RIL_UNSOL_TETHERED_MODE_STATE_CHANGED:
                return RILConstants.RIL_UNSOL_TETHERED_MODE_STATE_CHANGED;
        }
        return id;
    }
}
