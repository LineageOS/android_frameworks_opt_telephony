/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Message;
import android.os.ResultReceiver;
import android.telephony.Rlog;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;

import com.android.internal.telephony.Phone;

/**
 * Datagram controller used for sending and receiving satellite datagrams.
 */
public class DatagramController {
    private static final String TAG = "DatagramController";

    @NonNull private static DatagramController sInstance;
    @NonNull private final Context mContext;
    @NonNull private final DatagramDispatcher mDatagramDispatcher;
    @NonNull private final DatagramReceiver mDatagramReceiver;

    /**
     * @return The singleton instance of DatagramController.
     */
    public static DatagramController getInstance() {
        if (sInstance == null) {
            loge("DatagramController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the DatagramController singleton instance.
     * @param context The Context to use to create the DatagramController.
     * @return The singleton instance of DatagramController.
     */
    public static DatagramController make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new DatagramController(context);
        }
        return sInstance;
    }

    /**
     * Create a DatagramController to send and receive satellite datagrams.
     *
     * @param context The Context for the DatagramController.
     */
    private DatagramController(@NonNull Context context) {
        mContext = context;
        // Create the DatagramDispatcher singleton,
        // which is used to send satellite datagrams.
        mDatagramDispatcher = DatagramDispatcher.make(mContext);

        // Create the DatagramReceiver singleton,
        // which is used to receive satellite datagrams.
        mDatagramReceiver = DatagramReceiver.make(mContext);
    }

    /**
     * Register to receive incoming datagrams over satellite.
     *
     * @param subId The subId of the subscription to register for incoming satellite datagrams.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param callback The callback to handle incoming datagrams over satellite.
     *
     * @return The {@link SatelliteManager.SatelliteError} result of the operation.
     */
    @SatelliteManager.SatelliteError public int registerForSatelliteDatagram(int subId,
            @SatelliteManager.DatagramType int datagramType,
            @NonNull ISatelliteDatagramCallback callback) {
        return mDatagramReceiver.registerForSatelliteDatagram(subId, datagramType, callback);
    }

    /**
     * Unregister to stop receiving incoming datagrams over satellite.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for incoming satellite datagrams.
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteDatagram(int, int, ISatelliteDatagramCallback)}.
     */
    public void unregisterForSatelliteDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        mDatagramReceiver.unregisterForSatelliteDatagram(subId, callback);
    }

    /**
     * Poll pending satellite datagrams over satellite.
     *
     * This method requests modem to check if there are any pending datagrams to be received over
     * satellite. If there are any incoming datagrams, they will be received via
     * {@link android.telephony.satellite.SatelliteDatagramCallback
     * #onSatelliteDatagramReceived(long, SatelliteDatagram, int, ISatelliteDatagramReceiverAck)}
     *
     */
    public void pollPendingSatelliteDatagrams(@NonNull Message message, @Nullable Phone phone) {
        // TODO: set modemTransferState = SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING
        mDatagramReceiver.pollPendingSatelliteDatagrams(message, phone);
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * @param datagramId An id that uniquely identifies datagram requested to be sent.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param isSatelliteDemoModeEnabled True if satellite demo mode is enabled
     * @param result The result receiver that returns datagramId if datagram is sent successfully
     *               or {@link SatelliteManager.SatelliteError} of the request if it is failed.
     */
    public void sendSatelliteDatagram(long datagramId,
            @SatelliteManager.DatagramType int datagramType, @NonNull SatelliteDatagram datagram,
            boolean needFullScreenPointingUI, boolean isSatelliteDemoModeEnabled,
            @NonNull ResultReceiver result) {
        // TODO: set modemTransferState = SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING
        mDatagramDispatcher.sendSatelliteDatagram(datagramId, datagramType, datagram,
                needFullScreenPointingUI, isSatelliteDemoModeEnabled, result);
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
