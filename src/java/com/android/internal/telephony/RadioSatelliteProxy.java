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
 * A holder for IRadioSatellite.
 * Use getAidl to get IRadioSatellite and call the AIDL implementations of the HAL APIs.
 */
public class RadioSatelliteProxy extends RadioServiceProxy {
    private static final String TAG = "RadioSatelliteProxy";
    private volatile android.hardware.radio.satellite.IRadioSatellite mSatelliteProxy = null;

    /**
     * Sets IRadioSatellite as the AIDL implementation for RadioServiceProxy.
     * @param halVersion Radio HAL version.
     * @param satellite IRadioSatellite implementation.
     *
     * @return updated HAL version.
     */
    public HalVersion setAidl(HalVersion halVersion,
            android.hardware.radio.satellite.IRadioSatellite satellite) {
        HalVersion version = halVersion;
        try {
            version = RIL.getServiceHalVersion(satellite.getInterfaceVersion());
        } catch (RemoteException e) {
            Rlog.e(TAG, "setAidl: " + e);
        }
        mHalVersion = version;
        mSatelliteProxy = satellite;
        mIsAidl = true;

        Rlog.d(TAG, "AIDL initialized mHalVersion=" + mHalVersion);
        return mHalVersion;
    }

    /**
     * Gets the AIDL implementation of RadioSatelliteProxy.
     * @return IRadioSatellite implementation.
     */
    public android.hardware.radio.satellite.IRadioSatellite getAidl() {
        return mSatelliteProxy;
    }

    /**
     * Resets RadioSatelliteProxy.
     */
    @Override
    public void clear() {
        super.clear();
        mSatelliteProxy = null;
    }

    /**
     * Checks whether a IRadioSatellite implementation exists.
     * @return true if there is neither a HIDL nor AIDL implementation.
     */
    @Override
    public boolean isEmpty() {
        return mRadioProxy == null && mSatelliteProxy == null;
    }

    /**
     * Call IRadioSatellite#responseAcknowledgement
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    @Override
    public void responseAcknowledgement() throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.responseAcknowledgement();
        }
    }

    /**
     * Call IRadioSatellite#getCapabilities
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getCapabilities(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getCapabilities(serial);
        }
    }

    /**
     * Call IRadioSatellite#setPower
     * @param serial Serial number of request.
     * @param on True for turning on.
     *           False for turning off.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void setPower(int serial, boolean on) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.setPower(serial, on);
        }
    }

    /**
     * Call IRadioSatellite#getPowerState
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getPowerState(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getPowerState(serial);
        }
    }

    /**
     * Call IRadioSatellite#provisionService
     * @param serial Serial number of request.
     * @param imei IMEI of the SIM associated with the satellite modem.
     * @param msisdn MSISDN of the SIM associated with the satellite modem.
     * @param imsi IMSI of the SIM associated with the satellite modem.
     * @param features List of features to be provisioned.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void provisionService(int serial, String imei, String msisdn, String imsi,
            int[] features) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.provisionService(serial, imei, msisdn, imsi, features);
        }
    }

    /**
     * Call IRadioSatellite#addAllowedSatelliteContacts
     * @param serial Serial number of request.
     * @param contacts List of allowed contacts to be added.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void addAllowedSatelliteContacts(int serial, String[] contacts) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.addAllowedSatelliteContacts(serial, contacts);
        }
    }

    /**
     * Call IRadioSatellite#removeAllowedSatelliteContacts
     * @param serial Serial number of request.
     * @param contacts List of allowed contacts to be removed.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void removeAllowedSatelliteContacts(int serial, String[] contacts)
            throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.removeAllowedSatelliteContacts(serial, contacts);
        }
    }

    /**
     * Call IRadioSatellite#sendMessages
     * @param serial Serial number of request.
     * @param messages List of messages in text format to be sent.
     * @param destination The recipient of the message.
     * @param latitude The current latitude of the device.
     * @param longitude The current longitude of the device
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void sendMessages(int serial, String[] messages, String destination, double latitude,
            double longitude) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.sendMessages(serial, messages, destination, latitude, longitude);
        }
    }

    /**
     * Call IRadioSatellite#getPendingMessages
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getPendingMessages(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getPendingMessages(serial);
        }
    }

    /**
     * Call IRadioSatellite#getSatelliteMode
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getSatelliteMode(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getSatelliteMode(serial);
        }
    }

    /**
     * Call IRadioSatellite#setIndicationFilter
     * @param serial Serial number of request.
     * @param filterBitmask The filter identifying what type of indication framework want to
     *                         receive from modem.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void setIndicationFilter(int serial, int filterBitmask) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.setIndicationFilter(serial, filterBitmask);
        }
    }

    /**
     * Call IRadioSatellite#startSendingSatellitePointingInfo
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void startSendingSatellitePointingInfo(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.startSendingSatellitePointingInfo(serial);
        }
    }

    /**
     * Call IRadioSatellite#stopSendingSatellitePointingInfo
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void stopSendingSatellitePointingInfo(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.stopSendingSatellitePointingInfo(serial);
        }
    }

    /**
     * Call IRadioSatellite#getMaxCharactersPerTextMessage
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getMaxCharactersPerTextMessage(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getMaxCharactersPerTextMessage(serial);
        }
    }

    /**
     * Call IRadioSatellite#getTimeForNextSatelliteVisibility
     * @param serial Serial number of request.
     * @throws RemoteException Throws RemoteException when RadioSatellite service is not available.
     */
    public void getTimeForNextSatelliteVisibility(int serial) throws RemoteException {
        if (isEmpty()) return;
        if (isAidl()) {
            mSatelliteProxy.getTimeForNextSatelliteVisibility(serial);
        }
    }
}
