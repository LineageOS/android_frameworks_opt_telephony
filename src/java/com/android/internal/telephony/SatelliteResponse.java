/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.telephony.TelephonyManager.HAL_SERVICE_SATELLITE;

import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.satellite.IRadioSatelliteResponse;
import android.telephony.satellite.SatelliteCapabilities;

/**
 * Interface declaring response functions to solicited radio requests for Satellite APIs.
 */
public class SatelliteResponse extends IRadioSatelliteResponse.Stub {
    private final RIL mRil;

    public SatelliteResponse(RIL ril) {
        mRil = ril;
    }

    /**
     * Acknowledge the receipt of radio request sent to the vendor. This must be sent only for
     * radio request which take long time to respond.
     * For more details, refer https://source.android.com/devices/tech/connect/ril.html
     * @param serial Serial no. of the request whose acknowledgement is sent.
     */
    public void acknowledgeRequest(int serial) {
        mRil.processRequestAck(serial);
    }
    /**
     * Response of the request getCapabilities.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param capabilities List of capabilities that the satellite modem supports.
     */
    public void getCapabilitiesResponse(RadioResponseInfo responseInfo,
            android.hardware.radio.satellite.SatelliteCapabilities capabilities) {
        RILRequest rr = mRil.processResponse(HAL_SERVICE_SATELLITE, responseInfo);

        if (rr != null) {
            SatelliteCapabilities convertedSatelliteCapabilities =
                    RILUtils.convertHalSatelliteCapabilities(capabilities);
            if (responseInfo.error == RadioError.NONE) {
                RadioResponse.sendMessageResponse(rr.mResult, convertedSatelliteCapabilities);
            }
            mRil.processResponseDone(rr, responseInfo, convertedSatelliteCapabilities);
        }
    }

    /**
     * Response of the request setPower.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void setPowerResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request getPowerState.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param on True means the modem is ON.
     *           False means the modem is OFF.
     */
    public void getPowerStateResponse(RadioResponseInfo responseInfo, boolean on) {
        RadioResponse.responseInts(HAL_SERVICE_SATELLITE, mRil, responseInfo, on ? 1 : 0);
    }

    /**
     * Response of the request provisionService.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param provisioned True means the service is provisioned.
     *                    False means the service is not provisioned.
     */
    public void provisionServiceResponse(RadioResponseInfo responseInfo, boolean provisioned) {
        RadioResponse.responseInts(HAL_SERVICE_SATELLITE, mRil, responseInfo, provisioned ? 1 : 0);
    }

    /**
     * Response of the request addAllowedSatelliteContacts.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void addAllowedSatelliteContactsResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request removeAllowedSatelliteContacts.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void removeAllowedSatelliteContactsResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request sendMessages.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void sendMessagesResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request getPendingMessages.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param messages List of pending messages received.
     */
    public void getPendingMessagesResponse(RadioResponseInfo responseInfo, String[] messages) {
        RILRequest rr = mRil.processResponse(HAL_SERVICE_SATELLITE, responseInfo);

        if (rr != null) {
            if (responseInfo.error == RadioError.NONE) {
                RadioResponse.sendMessageResponse(rr.mResult, messages);
            }
            mRil.processResponseDone(rr, responseInfo, messages);
        }
    }

    /**
     * Response of the request getSatelliteMode.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param mode Current Mode of the satellite modem.
     * @param technology The current technology of the satellite modem.
     */
    public void getSatelliteModeResponse(RadioResponseInfo responseInfo, int mode, int technology) {
        RILRequest rr = mRil.processResponse(HAL_SERVICE_SATELLITE, responseInfo);

        if (rr != null) {
            int[] ret = new int[]{mode, technology};
            if (responseInfo.error == RadioError.NONE) {
                RadioResponse.sendMessageResponse(rr.mResult, ret);
            }
            mRil.processResponseDone(rr, responseInfo, ret);
        }
    }

    /**
     * Response of the request setIndicationFilter.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void setIndicationFilterResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request startSendingSatellitePointingInfo.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void startSendingSatellitePointingInfoResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request stopSendingSatellitePointingInfo.
     *
     * @param responseInfo Response info struct containing serial no. and error
     */
    public void stopSendingSatellitePointingInfoResponse(RadioResponseInfo responseInfo) {
        RadioResponse.responseVoid(HAL_SERVICE_SATELLITE, mRil, responseInfo);
    }

    /**
     * Response of the request getMaxCharactersPerTextMessage.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param charLimit Maximum number of characters in a text message that can be sent.
     */
    public void getMaxCharactersPerTextMessageResponse(
            RadioResponseInfo responseInfo, int charLimit) {
        RadioResponse.responseInts(HAL_SERVICE_SATELLITE, mRil, responseInfo, charLimit);
    }

    /**
     * Response of the request getTimeForNextSatelliteVisibility.
     *
     * @param responseInfo Response info struct containing serial no. and error
     * @param timeInSeconds The duration in seconds after which the satellite will be visible.
     */
    public void getTimeForNextSatelliteVisibilityResponse(
            RadioResponseInfo responseInfo, int timeInSeconds) {
        RadioResponse.responseInts(HAL_SERVICE_SATELLITE, mRil, responseInfo, timeInSeconds);
    }

    @Override
    public String getInterfaceHash() {
        return IRadioSatelliteResponse.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioSatelliteResponse.VERSION;
    }
}
