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

import static android.telephony.TelephonyManager.HAL_SERVICE_SATELLITE;

import static com.android.internal.telephony.RILConstants.RIL_UNSOL_NEW_SATELLITE_MESSAGES;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_PENDING_SATELLITE_MESSAGE_COUNT;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SATELLITE_MESSAGES_TRANSFER_COMPLETE;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SATELLITE_MODE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SATELLITE_POINTING_INFO_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SATELLITE_PROVISION_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SATELLITE_RADIO_TECHNOLOGY_CHANGED;

import android.hardware.radio.satellite.IRadioSatelliteIndication;
import android.os.AsyncResult;
import android.telephony.satellite.SatelliteDatagram;
import android.util.Pair;

/**
 * Interface declaring unsolicited radio indications for Satellite APIs.
 */
public class SatelliteIndication extends IRadioSatelliteIndication.Stub {
    private final RIL mRil;

    public SatelliteIndication(RIL ril) {
        mRil = ril;
    }

    @Override
    public String getInterfaceHash() {
        return IRadioSatelliteIndication.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IRadioSatelliteIndication.VERSION;
    }

    /**
     * Indicates that satellite has pending messages for the device to be pulled.
     *
     * @param indicationType Type of radio indication
     * @param count Number of pending messages.
     */
    public void onPendingMessageCount(int indicationType, int count) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_PENDING_SATELLITE_MESSAGE_COUNT);

        if (mRil.mPendingSatelliteMessageCountRegistrants != null) {
            mRil.mPendingSatelliteMessageCountRegistrants.notifyRegistrants(
                    new AsyncResult(null, count, null));
        }
    }

    /**
     * Indicates new message received on device.
     *
     * @param indicationType Type of radio indication
     * @param messages List of new messages received.
     */
    public void onNewMessages(int indicationType, String[] messages) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_NEW_SATELLITE_MESSAGES);

        if (mRil.mNewSatelliteMessagesRegistrants != null) {
            for (int i = 0; i < messages.length; i++) {
                SatelliteDatagram datagram = new SatelliteDatagram(messages[i].getBytes());
                mRil.mNewSatelliteMessagesRegistrants.notifyRegistrants(
                        new AsyncResult(null, new Pair<>(datagram, messages.length - i - 1), null));
            }
        }
    }

    /**
     * Confirms that ongoing message transfer is complete.
     *
     * @param indicationType Type of radio indication
     * @param complete True mean the transfer is complete.
     *                 False means the transfer is not complete.
     */
    public void onMessagesTransferComplete(int indicationType, boolean complete) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_SATELLITE_MESSAGES_TRANSFER_COMPLETE);

        if (mRil.mSatelliteMessagesTransferCompleteRegistrants != null) {
            mRil.mSatelliteMessagesTransferCompleteRegistrants.notifyRegistrants(
                    new AsyncResult(null, complete, null));
        }
    }

    /**
     * Indicate that satellite Pointing input has changed.
     *
     * @param indicationType Type of radio indication
     * @param pointingInfo The current pointing info.
     */
    public void onSatellitePointingInfoChanged(int indicationType,
            android.hardware.radio.satellite.PointingInfo pointingInfo) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_SATELLITE_POINTING_INFO_CHANGED);

        if (mRil.mSatellitePointingInfoChangedRegistrants != null) {
            mRil.mSatellitePointingInfoChangedRegistrants.notifyRegistrants(
                    new AsyncResult(
                            null,
                            RILUtils.convertHalSatellitePointingInfo(pointingInfo),
                            null));
        }
    }

    /**
     * Indicate that satellite mode has changed.
     *
     * @param indicationType Type of radio indication
     * @param mode The current mode of the satellite modem.
     */
    public void onSatelliteModeChanged(int indicationType, int mode) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_SATELLITE_MODE_CHANGED);

        if (mRil.mSatelliteModeChangedRegistrants != null) {
            mRil.mSatelliteModeChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, mode, null));
        }
    }

    /**
     * Indicate that satellite radio technology has changed.
     *
     * @param indicationType Type of radio indication
     * @param technology The current technology of the satellite modem.
     */
    public void onSatelliteRadioTechnologyChanged(int indicationType, int technology) {
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_SATELLITE_RADIO_TECHNOLOGY_CHANGED);

        if (mRil.mSatelliteRadioTechnologyChangedRegistrants != null) {
            mRil.mSatelliteRadioTechnologyChangedRegistrants.notifyRegistrants(
                    new AsyncResult(null, technology, null));
        }
    }

    /**
     * Indicate that satellite provision state has changed.
     *
     * @param indicationType Type of radio indication
     * @param provisioned True means the service is provisioned.
     *                    False means the service is not provisioned.
     * @param features List of Feature whose provision state has changed.
     */
    public void onProvisionStateChanged(int indicationType, boolean provisioned, int[] features) {
        // TODO: remove features and update AsyncResult
        mRil.processIndication(HAL_SERVICE_SATELLITE, indicationType);

        if (mRil.isLogOrTrace()) mRil.unsljLog(RIL_UNSOL_SATELLITE_PROVISION_STATE_CHANGED);

        if (mRil.mSatelliteProvisionStateChangedRegistrants != null) {
            mRil.mSatelliteProvisionStateChangedRegistrants.notifyRegistrants(
                    new AsyncResult(provisioned, null, null));
        }
    }
}
