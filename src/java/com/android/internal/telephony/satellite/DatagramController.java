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
import android.content.Context;
import android.os.Looper;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;

import com.android.internal.annotations.VisibleForTesting;

import java.util.function.Consumer;

/**
 * Datagram controller used for sending and receiving satellite datagrams.
 */
public class DatagramController {
    private static final String TAG = "DatagramController";

    @NonNull private static DatagramController sInstance;
    @NonNull private final Context mContext;
    @NonNull private final PointingAppController mPointingAppController;
    @NonNull private final DatagramDispatcher mDatagramDispatcher;
    @NonNull private final DatagramReceiver mDatagramReceiver;
    public static final long MAX_DATAGRAM_ID = (long) Math.pow(2, 16);
    public static final int ROUNDING_UNIT = 10;

    /** Variables used to update onSendDatagramStateChanged(). */
    private int mSendSubId;
    private int mSendDatagramTransferState =
            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_UNKNOWN;
    private int mSendPendingCount = 0;
    private int mSendErrorCode = SatelliteManager.SATELLITE_ERROR_NONE;
    /** Variables used to update onReceiveDatagramStateChanged(). */
    private int mReceiveSubId;
    private int mReceiveDatagramTransferState =
            SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_UNKNOWN;
    private int mReceivePendingCount = 0;
    private int mReceiveErrorCode = SatelliteManager.SATELLITE_ERROR_NONE;
    private SatelliteDatagram mDemoModeDatagram;
    private boolean mIsDemoMode = false;

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
     * @param looper The looper for the handler.
     * @param pointingAppController PointingAppController is used to update
     *                              PointingApp about datagram transfer state changes.
     * @return The singleton instance of DatagramController.
     */
    public static DatagramController make(@NonNull Context context, @NonNull Looper looper,
            @NonNull PointingAppController pointingAppController) {
        if (sInstance == null) {
            sInstance = new DatagramController(context, looper, pointingAppController);
        }
        return sInstance;
    }

    /**
     * Create a DatagramController to send and receive satellite datagrams.
     *
     * @param context The Context for the DatagramController.
     * @param looper The looper for the handler
     * @param pointingAppController PointingAppController is used to update PointingApp
     *                              about datagram transfer state changes.
     */
    @VisibleForTesting
    protected DatagramController(@NonNull Context context, @NonNull Looper  looper,
            @NonNull PointingAppController pointingAppController) {
        mContext = context;
        mPointingAppController = pointingAppController;

        // Create the DatagramDispatcher singleton,
        // which is used to send satellite datagrams.
        mDatagramDispatcher = DatagramDispatcher.make(mContext, looper, this);

        // Create the DatagramReceiver singleton,
        // which is used to receive satellite datagrams.
        mDatagramReceiver = DatagramReceiver.make(mContext, looper, this);
    }

    /**
     * Register to receive incoming datagrams over satellite.
     *
     * @param subId The subId of the subscription to register for incoming satellite datagrams.
     * @param callback The callback to handle incoming datagrams over satellite.
     *
     * @return The {@link SatelliteManager.SatelliteError} result of the operation.
     */
    @SatelliteManager.SatelliteError public int registerForSatelliteDatagram(int subId,
            @NonNull ISatelliteDatagramCallback callback) {
        return mDatagramReceiver.registerForSatelliteDatagram(subId, callback);
    }

    /**
     * Unregister to stop receiving incoming datagrams over satellite.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for incoming satellite datagrams.
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteDatagram(int, ISatelliteDatagramCallback)}.
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
     * {@link android.telephony.satellite.SatelliteDatagramCallback#onSatelliteDatagramReceived(
     * long, SatelliteDatagram, int, Consumer)}
     *
     * @param subId The subId of the subscription used for receiving datagrams.
     * @param callback The callback to get {@link SatelliteManager.SatelliteError} of the request.
     */
    public void pollPendingSatelliteDatagrams(int subId, @NonNull Consumer<Integer> callback) {
        mDatagramReceiver.pollPendingSatelliteDatagrams(subId, callback);
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * When demo mode is on, save the sent datagram and this datagram will be used as a received
     * datagram.
     *
     * @param subId The subId of the subscription to send satellite datagrams for.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param callback The callback to get {@link SatelliteManager.SatelliteError} of the request.
     */
    public void sendSatelliteDatagram(int subId, @SatelliteManager.DatagramType int datagramType,
            @NonNull SatelliteDatagram datagram, boolean needFullScreenPointingUI,
            @NonNull Consumer<Integer> callback) {
        setDemoModeDatagram(datagramType, datagram);
        mDatagramDispatcher.sendSatelliteDatagram(subId, datagramType, datagram,
                needFullScreenPointingUI, callback);
    }

    /**
     * Update send status to {@link PointingAppController}.
     *
     * @param subId The subId of the subscription to send satellite datagrams for
     * @param datagramTransferState The new send datagram transfer state.
     * @param sendPendingCount number of datagrams that are currently being sent
     * @param errorCode If datagram transfer failed, the reason for failure.
     */
    public void updateSendStatus(int subId,
            @SatelliteManager.SatelliteDatagramTransferState int datagramTransferState,
            int sendPendingCount, int errorCode) {
        logd("updateSendStatus"
                + " subId: " + subId
                + " datagramTransferState: " + datagramTransferState
                + " sendPendingCount: " + sendPendingCount + " errorCode: " + errorCode);

        mSendSubId = subId;
        mSendDatagramTransferState = datagramTransferState;
        mSendPendingCount = sendPendingCount;
        mSendErrorCode = errorCode;
        notifyDatagramTransferStateChangedToSessionController();
        mPointingAppController.updateSendDatagramTransferState(subId, datagramTransferState,
                sendPendingCount, errorCode);
    }

    /**
     * Update receive status to {@link PointingAppController}.
     *
     * @param subId The subId of the subscription used to receive datagrams
     * @param datagramTransferState The new receive datagram transfer state.
     * @param receivePendingCount The number of datagrams that are currently pending to be received.
     * @param errorCode If datagram transfer failed, the reason for failure.
     */
    public void updateReceiveStatus(int subId,
            @SatelliteManager.SatelliteDatagramTransferState int datagramTransferState,
            int receivePendingCount, int errorCode) {
        logd("updateReceiveStatus"
                + " subId: " + subId
                + " datagramTransferState: " + datagramTransferState
                + " receivePendingCount: " + receivePendingCount + " errorCode: " + errorCode);

        mReceiveSubId = subId;
        mReceiveDatagramTransferState = datagramTransferState;
        mReceivePendingCount = receivePendingCount;
        mReceiveErrorCode = errorCode;
        notifyDatagramTransferStateChangedToSessionController();
        mPointingAppController.updateReceiveDatagramTransferState(subId, datagramTransferState,
                receivePendingCount, errorCode);
    }

    /**
     * Return receive pending datagram count
     * @return receive pending datagram count.
     */
    public int getReceivePendingCount() {
        return mReceivePendingCount;
    }

    /**
     * This function is used by {@link SatelliteController} to notify {@link DatagramController}
     * that satellite modem state has changed.
     *
     * @param state Current satellite modem state.
     */
    public void onSatelliteModemStateChanged(@SatelliteManager.SatelliteModemState int state) {
        if (state == SatelliteManager.SATELLITE_MODEM_STATE_OFF
                || state == SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE) {
            logd("onSatelliteModemStateChanged: cleaning up resources");
            cleanUpResources();
        }
        mDatagramDispatcher.onSatelliteModemStateChanged(state);
    }

    private void cleanUpResources() {
        if (mReceiveDatagramTransferState
                == SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING) {
            updateReceiveStatus(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                    SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                    mReceivePendingCount,
                    SatelliteManager.SATELLITE_REQUEST_ABORTED);
        }
        updateReceiveStatus(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, 0,
                SatelliteManager.SATELLITE_ERROR_NONE);
    }

    /**
     * Set variables for {@link DatagramDispatcher} and {@link DatagramReceiver} to run demo mode
     * @param isDemoMode {@code true} means demo mode is on, {@code false} otherwise.
     */
    void setDemoMode(boolean isDemoMode) {
        mIsDemoMode = isDemoMode;
        mDatagramDispatcher.setDemoMode(isDemoMode);
        mDatagramReceiver.setDemoMode(isDemoMode);

        if (!isDemoMode) {
            mDemoModeDatagram = null;
        }
    }

    /** Get the last sent datagram for demo mode */
    @VisibleForTesting
    public SatelliteDatagram getDemoModeDatagram() {
        return mDemoModeDatagram;
    }

    /**
     * Set last sent datagram for demo mode
     * @param datagramType datagram type, only DATAGRAM_TYPE_SOS_MESSAGE will be saved
     * @param datagram datagram The last datagram saved when sendSatelliteDatagramForDemo is called
     */
    @VisibleForTesting
    protected void setDemoModeDatagram(@SatelliteManager.DatagramType int datagramType,
            SatelliteDatagram datagram) {
        if (mIsDemoMode &&  datagramType == SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE) {
            mDemoModeDatagram = datagram;
        }
    }

    private void notifyDatagramTransferStateChangedToSessionController() {
        SatelliteSessionController sessionController = SatelliteSessionController.getInstance();
        if (sessionController == null) {
            loge("notifyDatagramTransferStateChangeToSessionController: SatelliteSessionController"
                    + " is not initialized yet");
        } else {
            sessionController.onDatagramTransferStateChanged(
                    mSendDatagramTransferState, mReceiveDatagramTransferState);
        }
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
