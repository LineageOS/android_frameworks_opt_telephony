/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.hardware.radio.V1_0.RadioError;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.config.V1_0.IRadioConfigResponse;
import android.hardware.radio.config.V1_0.SimSlotStatus;

import com.android.internal.telephony.uicc.IccSlotStatus;

import java.util.ArrayList;

/**
 * This class is the implementation of IRadioConfigResponse interface.
 */
public class RadioConfigResponse extends IRadioConfigResponse.Stub {
    private final RadioConfig mRadioConfig;

    public RadioConfigResponse(RadioConfig radioConfig) {
        mRadioConfig = radioConfig;
    }

    /**
     * Response function for IRadioConfig.getSimSlotsStatus().
     */
    public void getSimSlotsStatusResponse(RadioResponseInfo responseInfo,
                                          ArrayList<SimSlotStatus> slotStatus) {
        RILRequest rr = mRadioConfig.processResponse(responseInfo);

        if (rr != null) {
            ArrayList<IccSlotStatus> ret = RadioConfig.convertHalSlotStatus(slotStatus);
            if (responseInfo.error == RadioError.NONE) {
                // send response
                RadioResponse.sendMessageResponse(rr.mResult, ret);
            } else {
                rr.onError(responseInfo.error, ret);
            }
        }
    }

    /**
     * Response function for IRadioConfig.setSimSlotsMapping().
     */
    public void setSimSlotsMappingResponse(RadioResponseInfo responseInfo) {
        RILRequest rr = mRadioConfig.processResponse(responseInfo);

        if (rr != null) {
            if (responseInfo.error == RadioError.NONE) {
                // send response
                RadioResponse.sendMessageResponse(rr.mResult, null);
            } else {
                rr.onError(responseInfo.error, null);
            }
        }
    }
}
