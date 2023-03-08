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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.telephony.Phone;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Datagram dispatcher used to send satellite datagrams.
 */
public class DatagramDispatcher extends Handler {
    private static final String TAG = "DatagramDispatcher";

    private static final int CMD_SEND_SATELLITE_DATAGRAM = 1;
    private static final int EVENT_SEND_SATELLITE_DATAGRAM_DONE = 2;

    @NonNull private static DatagramDispatcher sInstance;
    @NonNull private final Context mContext;

    private static AtomicLong mNextDatagramId = new AtomicLong(0);

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mSendingDatagramInProgress;

    /**
     * Map key: datagramId, value: SendSatelliteDatagramArgument to retry sending emergency
     * datagrams.
     */
    @GuardedBy("mLock")
    private final LinkedHashMap<Long, SendSatelliteDatagramArgument>
            mPendingEmergencyDatagramsMap = new LinkedHashMap<>();

    /**
     * Map key: datagramId, value: SendSatelliteDatagramArgument to retry sending non-emergency
     * datagrams.
     */
    @GuardedBy("mLock")
    private final LinkedHashMap<Long, SendSatelliteDatagramArgument>
            mPendingNonEmergencyDatagramsMap = new LinkedHashMap<>();

    /**
     * Create the DatagramDispatcher singleton instance.
     * @param context The Context to use to create the DatagramDispatcher.
     * @param looper The looper for the handler.
     * @return The singleton instance of DatagramDispatcher.
     */
    public static DatagramDispatcher make(@NonNull Context context, @NonNull Looper looper) {
        if (sInstance == null) {
            sInstance = new DatagramDispatcher(context, looper);
        }
        return sInstance;
    }

    /**
     * Create a DatagramDispatcher to send satellite datagrams.
     *
     * @param context The Context for the DatagramDispatcher.
     * @param looper The looper for the handler.
     */
    private DatagramDispatcher(@NonNull Context context, @NonNull Looper looper) {
        super(looper);
        mContext = context;
        synchronized (mLock) {
            mSendingDatagramInProgress = false;
        }
    }

    private static final class DatagramDispatcherHandlerRequest {
        /** The argument to use for the request */
        public @NonNull Object argument;
        /** The caller needs to specify the phone to be used for the request */
        public @NonNull Phone phone;
        /** The result of the request that is run on the main thread */
        public @Nullable Object result;

        DatagramDispatcherHandlerRequest(Object argument, Phone phone) {
            this.argument = argument;
            this.phone = phone;
        }
    }

    private static final class SendSatelliteDatagramArgument {
        public long datagramId;
        public @SatelliteManager.DatagramType int datagramType;
        public @NonNull SatelliteDatagram datagram;
        public boolean needFullScreenPointingUI;
        public boolean isSatelliteDemoModeEnabled;
        public @NonNull Consumer<Integer> callback;

        SendSatelliteDatagramArgument(long datagramId,
                @SatelliteManager.DatagramType int datagramType,
                @NonNull SatelliteDatagram datagram, boolean needFullScreenPointingUI,
                boolean isSatelliteDemoModeEnabled, @NonNull Consumer<Integer> callback) {
            this.datagramId = datagramId;
            this.datagramType = datagramType;
            this.datagram = datagram;
            this.needFullScreenPointingUI = needFullScreenPointingUI;
            this.isSatelliteDemoModeEnabled = isSatelliteDemoModeEnabled;
            this.callback = callback;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        DatagramDispatcherHandlerRequest request;
        Message onCompleted;
        AsyncResult ar;

        switch(msg.what) {
            case CMD_SEND_SATELLITE_DATAGRAM: {
                request = (DatagramDispatcherHandlerRequest) msg.obj;
                SendSatelliteDatagramArgument argument =
                        (SendSatelliteDatagramArgument) request.argument;
                onCompleted = obtainMessage(EVENT_SEND_SATELLITE_DATAGRAM_DONE, request);
                if (SatelliteModemInterface.getInstance().isSatelliteServiceSupported()) {
                    SatelliteModemInterface.getInstance().sendSatelliteDatagram(argument.datagram,
                            argument.datagramType == SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                            argument.needFullScreenPointingUI, argument.isSatelliteDemoModeEnabled,
                            onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.sendSatelliteDatagram(onCompleted, argument.datagram,
                            argument.needFullScreenPointingUI);
                } else {
                    loge("sendSatelliteDatagram: No phone object");
                    argument.callback.accept(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                }
                break;
            }

            case EVENT_SEND_SATELLITE_DATAGRAM_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (DatagramDispatcherHandlerRequest) ar.userObj;
                int error = SatelliteServiceUtils.getSatelliteError(ar, "sendSatelliteDatagram");
                SendSatelliteDatagramArgument argument =
                        (SendSatelliteDatagramArgument) request.argument;

                synchronized (mLock) {
                    mSendingDatagramInProgress = false;
                }

                // Send response for current datagram
                argument.callback.accept(error);
                synchronized (mLock) {
                    if (argument.datagramType == SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE) {
                        mPendingEmergencyDatagramsMap.remove(argument.datagramId);
                    } else {
                        mPendingNonEmergencyDatagramsMap.remove(argument.datagramId);
                    }
                }

                // Handle pending datagrams
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    sendPendingDatagrams();
                } else {
                    // TODO: set modemTransferState = SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED.
                    sendErrorCodeAndCleanupPendingDatagrams(mPendingEmergencyDatagramsMap,
                            SatelliteManager.SATELLITE_REQUEST_ABORTED);
                    sendErrorCodeAndCleanupPendingDatagrams(mPendingNonEmergencyDatagramsMap,
                            SatelliteManager.SATELLITE_REQUEST_ABORTED);
                }
                break;
            }

            default:
                logw("DatagramDispatcherHandler: unexpected message code: " + msg.what);
                break;
        }
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param isSatelliteDemoModeEnabled True if satellite demo mode is enabled
     * @param callback The callback to get {@link SatelliteManager.SatelliteError} of the request.
     */
    public void sendSatelliteDatagram(@SatelliteManager.DatagramType int datagramType,
            @NonNull SatelliteDatagram datagram, boolean needFullScreenPointingUI,
            boolean isSatelliteDemoModeEnabled, @NonNull Consumer<Integer> callback) {
        Phone phone = SatelliteServiceUtils.getPhone();

        long datagramId = mNextDatagramId.getAndUpdate(
                n -> ((n + 1) % DatagramController.MAX_DATAGRAM_ID));
        SendSatelliteDatagramArgument datagramArgs = new SendSatelliteDatagramArgument(datagramId,
                datagramType, datagram, needFullScreenPointingUI, isSatelliteDemoModeEnabled,
                callback);

        synchronized (mLock) {
            if (datagramType == SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE) {
                mPendingEmergencyDatagramsMap.put(datagramId, datagramArgs);
            } else {
                mPendingNonEmergencyDatagramsMap.put(datagramId, datagramArgs);
            }

            if (!mSendingDatagramInProgress) {
                mSendingDatagramInProgress = true;
                sendRequestAsync(CMD_SEND_SATELLITE_DATAGRAM, datagramArgs, phone);
            }
        }
    }

    /**
     * Send pending satellite datagrams. Emergency datagrams are given priority over
     * non-emergency datagrams.
     */
    private void sendPendingDatagrams() {
        Phone phone = SatelliteServiceUtils.getPhone();
        Set<Entry<Long, SendSatelliteDatagramArgument>> pendingDatagram = null;
        synchronized (mLock) {
            if (!mSendingDatagramInProgress && !mPendingEmergencyDatagramsMap.isEmpty()) {
                pendingDatagram = mPendingEmergencyDatagramsMap.entrySet();
            } else if (!mSendingDatagramInProgress && !mPendingNonEmergencyDatagramsMap.isEmpty()) {
                pendingDatagram = mPendingNonEmergencyDatagramsMap.entrySet();
            }

            if ((pendingDatagram != null) && pendingDatagram.iterator().hasNext()) {
                mSendingDatagramInProgress = true;
                sendRequestAsync(CMD_SEND_SATELLITE_DATAGRAM,
                        pendingDatagram.iterator().next().getValue(), phone);
            }
        }
    }

    /**
     * Send error code to all the pending datagrams
     * @param errorCode error code to be returned.
     */
    private void sendErrorCodeAndCleanupPendingDatagrams(
            LinkedHashMap<Long, SendSatelliteDatagramArgument> pendingDatagramsMap,
            @SatelliteManager.SatelliteError int errorCode) {
        synchronized (mLock) {
            // Send error code to all the pending datagrams
            for (Entry<Long, SendSatelliteDatagramArgument> entry :
                    pendingDatagramsMap.entrySet()) {
                SendSatelliteDatagramArgument argument = entry.getValue();
                argument.callback.accept(errorCode);
            }

            // Clear pending datagram maps
            pendingDatagramsMap.clear();
        }
    }

    /**
     * Posts the specified command to be executed on the main thread and returns immediately.
     *
     * @param command command to be executed on the main thread
     * @param argument additional parameters required to perform of the operation
     * @param phone phone object used to perform the operation.
     */
    private void sendRequestAsync(int command, @NonNull Object argument, @Nullable Phone phone) {
        DatagramDispatcherHandlerRequest request = new DatagramDispatcherHandlerRequest(
                argument, phone);
        Message msg = this.obtainMessage(command, request);
        msg.sendToTarget();
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    private static void logw(@NonNull String log) { Rlog.w(TAG, log); }
}
