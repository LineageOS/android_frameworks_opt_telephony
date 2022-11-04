/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.ims.IRadioImsResponse;

/**
 * Interface declaring response functions to solicited radio requests for IMS APIs.
 */
public class ImsResponse extends IRadioImsResponse.Stub {
    private final RIL mRil;

    public ImsResponse(RIL ril) {
        mRil = ril;
    }

    @Override
    public String getInterfaceHash() {
        return IRadioImsResponse.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioImsResponse.VERSION;
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void setSrvccCallInfoResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void updateImsRegistrationInfoResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. and error.
     * @param failureInfo Failure information.
     */
    public void startImsTrafficResponse(RadioResponseInfo responseInfo,
            android.hardware.radio.ims.ConnectionFailureInfo failureInfo) {
        RILRequest rr = mRil.processResponse(RIL.IMS_SERVICE, responseInfo);

        if (rr != null) {
            Object[] response = { "", null };

            if (responseInfo.error == RadioError.NONE) {
                if (failureInfo != null) {
                    int[] info = new int[3];
                    info[0] = failureInfo.failureReason;
                    info[1] = failureInfo.causeCode;
                    info[2] = failureInfo.waitTimeMillis;
                    response[1] = info;
                }

                RadioResponse.sendMessageResponse(rr.mResult, response);
            }
            mRil.processResponseDone(rr, responseInfo, response);
        }
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void stopImsTrafficResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void triggerEpsFallbackResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void sendAnbrQueryResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }

    /**
     * @param info Response info struct containing response type, serial no. and error.
     */
    public void updateImsCallStatusResponse(RadioResponseInfo info) {
        RadioResponse.responseVoid(RIL.IMS_SERVICE, mRil, info);
    }
}
