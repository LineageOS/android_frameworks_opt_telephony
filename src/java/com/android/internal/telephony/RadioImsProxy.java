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

import android.os.RemoteException;
import android.telephony.Rlog;

/**
 * A holder for IRadioIms.
 * Use getAidl to get IRadioIms and call the AIDL implementations of the HAL APIs.
 */
public class RadioImsProxy extends RadioServiceProxy {
    private static final String TAG = "RadioImsProxy";
    private volatile android.hardware.radio.ims.IRadioIms mImsProxy = null;

    /**
     * Sets IRadioIms as the AIDL implementation for RadioServiceProxy.
     * @param halVersion Radio HAL version.
     * @param ims IRadioIms implementation.
     */
    public void setAidl(HalVersion halVersion, android.hardware.radio.ims.IRadioIms ims) {
        mHalVersion = halVersion;
        mImsProxy = ims;
        mIsAidl = true;
        Rlog.d(TAG, "AIDL initialized");
    }

    /**
     * Gets the AIDL implementation of RadioImsProxy.
     * @return IRadioIms implementation.
     */
    public android.hardware.radio.ims.IRadioIms getAidl() {
        return mImsProxy;
    }

    /**
     * Resets RadioImsProxy.
     */
    @Override
    public void clear() {
        super.clear();
        mImsProxy = null;
    }

    /**
     * Checks whether a RadioIms implementation exists.
     * @return true if there is neither a HIDL nor AIDL implementation.
     */
    @Override
    public boolean isEmpty() {
        return mRadioProxy == null && mImsProxy == null;
    }

    /**
     * No implementation in IRadioIms.
     * @throws RemoteException.
     */
    @Override
    public void responseAcknowledgement() throws RemoteException {
        /* Currently, IRadioIms doesn't support the following response types:
         * - RadioIndicationType.UNSOLICITED_ACK_EXP
         * - RadioResponseType.SOLICITED_ACK_EXP */
        // no-op
    }

    /**
     * Calls IRadioIms#setSrvccCallInfo.
     * @param serial Serial number of request.
     * @param srvccCalls The list of call information.
     * @throws RemoteException.
     */
    public void setSrvccCallInfo(int serial,
            android.hardware.radio.ims.SrvccCall[] srvccCalls) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.setSrvccCallInfo(serial, srvccCalls);
        }
    }

    /**
     * Calls IRadioIms#updateImsRegistrationInfo.
     * @param serial Serial number of request.
     * @param registrationInfo The registration state information.
     * @throws RemoteException.
     */
    public void updateImsRegistrationInfo(int serial,
            android.hardware.radio.ims.ImsRegistration registrationInfo) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.updateImsRegistrationInfo(serial, registrationInfo);
        }
    }

    /**
     * Calls IRadioIms#startImsTraffic.
     * @param serial Serial number of request.
     * @throws RemoteException.
     */
    public void notifyImsTraffic(int serial, int token, int trafficType, boolean isStart)
            throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.notifyImsTraffic(serial, token, trafficType, isStart);
        }
    }

    /**
     * Calls IRadioIms#performAcbCheck.
     * @param serial Serial number of request.
     * @throws RemoteException.
     */
    public void performAcbCheck(int serial, int token, int trafficType) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.performAcbCheck(serial, token, trafficType);
        }
    }

    /**
     * Call IRadioIms#setAnbrEnabled
     * @param serial Serial number of request
     * @throws RemoteException
     */
    public void setAnbrEnabled(int serial, int qosSessionId, boolean isEnabled)
            throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.setAnbrEnabled(serial, qosSessionId, isEnabled);
        }
    }

    /**
     * Calls IRadioIms#sendAnbrQuery.
     * @param serial Serial number of request.
     * @param qosSessionId QoS session ID is used to identify media stream such as audio or video.
     * @param imsdirection Direction of this packet stream (e.g. uplink or downlink).
     * @param bitsPerSecond The bit rate requested by the opponent UE.
     * @throws RemoteException.
     */
    public void sendAnbrQuery(int serial, int qosSessionId, int imsdirection, int bitsPerSecond)
            throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mImsProxy.sendAnbrQuery(serial, qosSessionId, imsdirection, bitsPerSecond);
        }
    }
}
