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
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.satellite.ISatelliteDatagramCallback;
import android.telephony.satellite.ISatelliteDatagramReceiverAck;
import android.telephony.satellite.ISatellitePositionUpdateCallback;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.ISatelliteStateCallback;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;
import android.util.Pair;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILUtils;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.subscription.SubscriptionManagerService;
import com.android.internal.util.FunctionalUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Satellite controller is the backend service of
 * {@link android.telephony.satellite.SatelliteManager}.
 */
public class SatelliteController extends Handler {
    private static final String TAG = "SatelliteController";
    /** Whether enabling verbose debugging message or not. */
    private static final boolean DBG = false;

    /** Message codes used in handleMessage() */
    private static final int CMD_START_SATELLITE_POSITION_UPDATES = 1;
    private static final int EVENT_START_SATELLITE_POSITION_UPDATES_DONE = 2;
    private static final int CMD_STOP_SATELLITE_POSITION_UPDATES = 3;
    private static final int EVENT_STOP_SATELLITE_POSITION_UPDATES_DONE = 4;
    private static final int CMD_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG = 5;
    private static final int EVENT_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG_DONE = 6;
    private static final int CMD_PROVISION_SATELLITE_SERVICE = 7;
    private static final int EVENT_PROVISION_SATELLITE_SERVICE_DONE = 8;
    private static final int CMD_DEPROVISION_SATELLITE_SERVICE = 9;
    private static final int EVENT_DEPROVISION_SATELLITE_SERVICE_DONE = 10;
    private static final int CMD_SET_SATELLITE_ENABLED = 11;
    private static final int EVENT_SET_SATELLITE_ENABLED_DONE = 12;
    private static final int CMD_IS_SATELLITE_ENABLED = 13;
    private static final int EVENT_IS_SATELLITE_ENABLED_DONE = 14;
    private static final int CMD_IS_SATELLITE_SUPPORTED = 15;
    private static final int EVENT_IS_SATELLITE_SUPPORTED_DONE = 16;
    private static final int CMD_GET_SATELLITE_CAPABILITIES = 17;
    private static final int EVENT_GET_SATELLITE_CAPABILITIES_DONE = 18;
    private static final int CMD_POLL_PENDING_SATELLITE_DATAGRAMS = 19;
    private static final int EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE = 20;
    private static final int CMD_SEND_SATELLITE_DATAGRAM = 21;
    private static final int EVENT_SEND_SATELLITE_DATAGRAM_DONE = 22;
    private static final int CMD_IS_SATELLITE_COMMUNICATION_ALLOWED = 23;
    private static final int EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE = 24;
    private static final int CMD_GET_TIME_SATELLITE_NEXT_VISIBLE = 25;
    private static final int EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE = 26;

    @NonNull private static SatelliteController sInstance;
    @NonNull private final Context mContext;
    @NonNull private final SatelliteModemInterface mSatelliteModemInterface;
    @NonNull private final SatelliteSessionController mSatelliteSessionController;
    @NonNull private final PointingAppController mPointingAppController;
    @NonNull private final DatagramController mDatagramController;


    /**
     * Map key: subId, value: callback to get error code of the provision request.
     */
    private final ConcurrentHashMap<Integer, Consumer<Integer>> mSatelliteProvisionCallbacks =
            new ConcurrentHashMap<>();
    /**
     * Map key: subId, value: SatellitePositionUpdateHandler to notify registrants.
     */
    private final ConcurrentHashMap<Integer, SatellitePositionUpdateHandler>
            mSatellitePositionUpdateHandlers = new ConcurrentHashMap<>();
    /**
     * Map key: subId, value: SatelliteProvisionStateChangedHandler to notify registrants.
     */
    private final ConcurrentHashMap<Integer, SatelliteProvisionStateChangedHandler>
            mSatelliteProvisionStateChangedHandlers = new ConcurrentHashMap<>();

    /**
     * Map key: subId, value: SatelliteStateListenerHandler to notify registrants.
     */
    private final ConcurrentHashMap<Integer, SatelliteStateListenerHandler>
            mSatelliteStateListenerHandlers = new ConcurrentHashMap<>();

    /**
     * Map key: subId, value: SatelliteDatagramListenerHandler to notify registrants.
     */
    private final ConcurrentHashMap<Integer, SatelliteDatagramListenerHandler>
            mSatelliteDatagramListenerHandlers = new ConcurrentHashMap<>();

    private Boolean mIsSatelliteSupported = null;
    private final Object mIsSatelliteSupportedLock = new Object();
    private final ResultReceiver mSatelliteSupportedReceiver;
    private boolean mIsSatelliteDemoModeEnabled = false;

    /**
     * @return The singleton instance of SatelliteController.
     */
    public static SatelliteController getInstance() {
        if (sInstance == null) {
            loge("SatelliteController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the SatelliteController singleton instance.
     * @param context The Context to use to create the SatelliteController.
     */
    public static void make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new SatelliteController(context);
        }
    }

    /**
     * Create a SatelliteController to act as a backend service of
     * {@link android.telephony.satellite.SatelliteManager}
     *
     * @param context The Context for the SatelliteController.
     */
    private SatelliteController(@NonNull Context context) {
        super(context.getMainLooper());
        mContext = context;

        // Create the SatelliteModemInterface singleton, which is used to manage connections
        // to the satellite service and HAL interface.
        mSatelliteModemInterface = SatelliteModemInterface.make(mContext);

        // Create the SatelliteSessionController singleton,
        // which is used to manage all the data during a satellite session.
        mSatelliteSessionController = SatelliteSessionController.make(mContext);

        // Create the PointingUIController singleton,
        // which is used to manage interactions with PointingUI app.
        mPointingAppController = PointingAppController.make(mContext);

        // Create the DatagramController singleton,
        // which is used to send and receive satellite datagrams.
        mDatagramController = DatagramController.make(mContext);

        mSatelliteSupportedReceiver = new ResultReceiver(this) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == SatelliteManager.SATELLITE_ERROR_NONE
                        && resultData.containsKey(SatelliteManager.KEY_SATELLITE_SUPPORTED)) {
                    synchronized (mIsSatelliteSupportedLock) {
                        mIsSatelliteSupported = resultData.getBoolean(
                                SatelliteManager.KEY_SATELLITE_SUPPORTED);
                    }
                } else {
                    synchronized (mIsSatelliteSupportedLock) {
                        mIsSatelliteSupported = null;
                    }
                }
            }
        };
        requestIsSatelliteSupported(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                mSatelliteSupportedReceiver);
    }

    private void internalInit() {

    }

    private static final class SatelliteControllerHandlerRequest {
        /** The argument to use for the request */
        public @NonNull Object argument;
        /** The caller needs to specify the phone to be used for the request */
        public @NonNull Phone phone;
        /** The result of the request that is run on the main thread */
        public @Nullable Object result;

        SatelliteControllerHandlerRequest(Object argument, Phone phone) {
            this.argument = argument;
            this.phone = phone;
        }
    }

    private static final class ProvisionSatelliteServiceArgument {
        public @NonNull String token;
        public @NonNull Consumer<Integer> callback;
        public int subId;

        ProvisionSatelliteServiceArgument(String token, Consumer<Integer> callback, int subId) {
            this.token = token;
            this.callback = callback;
            this.subId = subId;
        }
    }

    private static final class SendSatelliteDatagramArgument {
        public long datagramId;

        public @SatelliteManager.DatagramType int datagramType;
        public @NonNull SatelliteDatagram datagram;
        public boolean needFullScreenPointingUI;
        public @NonNull ResultReceiver result;

        SendSatelliteDatagramArgument(long datagramId,
                @SatelliteManager.DatagramType int datagramType,
                SatelliteDatagram datagram, boolean needFullScreenPointingUI,
                ResultReceiver result) {
            this.datagramId = datagramId;
            this.datagramType = datagramType;
            this.datagram = datagram;
            this.needFullScreenPointingUI = needFullScreenPointingUI;
            this.result = result;
        }
    }

    private static final class SatellitePositionUpdateArgument {
        public @NonNull Consumer<Integer> errorCallback;
        public @NonNull ISatellitePositionUpdateCallback callback;
        public int subId;

        SatellitePositionUpdateArgument(Consumer<Integer> errorCallback,
                ISatellitePositionUpdateCallback callback, int subId) {
            this.errorCallback = errorCallback;
            this.callback = callback;
            this.subId = subId;
        }
    }

    private static final class SatellitePositionUpdateHandler extends Handler {
        public static final int EVENT_POSITION_INFO_CHANGED = 1;
        public static final int EVENT_DATAGRAM_TRANSFER_STATE_CHANGED = 2;

        private final ConcurrentHashMap<IBinder, ISatellitePositionUpdateCallback> mListeners;
        SatellitePositionUpdateHandler(Looper looper) {
            super(looper);
            mListeners = new ConcurrentHashMap<>();
        }

        public void addListener(ISatellitePositionUpdateCallback listener) {
            mListeners.put(listener.asBinder(), listener);
        }

        public void removeListener(ISatellitePositionUpdateCallback listener) {
            mListeners.remove(listener.asBinder());
        }

        public boolean hasListeners() {
            return !mListeners.isEmpty();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVENT_POSITION_INFO_CHANGED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    PointingInfo pointingInfo = (PointingInfo) ar.result;
                    mListeners.values().forEach(listener -> {
                        try {
                            listener.onSatellitePositionChanged(pointingInfo);
                        } catch (RemoteException e) {
                            logd("EVENT_POSITION_INFO_CHANGED RemoteException: " + e);
                        }
                    });
                    break;
                }
                case EVENT_DATAGRAM_TRANSFER_STATE_CHANGED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int result = (int) ar.result;
                    mListeners.values().forEach(listener -> {
                        try {
                            // TODO: process and return the rest of the values correctly
                            listener.onDatagramTransferStateChanged(result, 0, 0, 0);
                        } catch (RemoteException e) {
                            logd("EVENT_DATAGRAM_TRANSFER_STATE_CHANGED RemoteException: " + e);
                        }
                    });
                    break;
                }
                default:
                    loge("SatellitePositionUpdateHandler unknown event: " + msg.what);
            }
        }
    }

    private static final class SatelliteProvisionStateChangedHandler extends Handler {
        public static final int EVENT_PROVISION_STATE_CHANGED = 1;

        private final ConcurrentHashMap<IBinder, ISatelliteProvisionStateCallback> mListeners;
        private final int mSubId;

        SatelliteProvisionStateChangedHandler(Looper looper, int subId) {
            super(looper);
            mListeners = new ConcurrentHashMap<>();
            mSubId = subId;
        }

        public void addListener(ISatelliteProvisionStateCallback listener) {
            mListeners.put(listener.asBinder(), listener);
        }

        public void removeListener(ISatelliteProvisionStateCallback listener) {
            mListeners.remove(listener.asBinder());
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVENT_PROVISION_STATE_CHANGED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    boolean provisioned = (boolean) ar.userObj;
                    logd("Received EVENT_PROVISION_STATE_CHANGED for subId=" + mSubId
                            + ", provisioned=" + provisioned);
                    mListeners.values().forEach(listener -> {
                        try {
                            listener.onSatelliteProvisionStateChanged(provisioned);
                        } catch (RemoteException e) {
                            logd("EVENT_PROVISION_STATE_CHANGED RemoteException: " + e);
                        }
                    });

                    setSatelliteProvisioned(provisioned);
                    /**
                     * TODO: Take bugreport if provisioned is true and user did not initiate the
                     * provision procedure for the corresponding subscription.
                     */
                    break;
                }
                default:
                    loge("SatelliteProvisionStateChangedHandler unknown event: " + msg.what);
            }
        }

        private void setSatelliteProvisioned(boolean isProvisioned) {
            if (mSubId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
                SubscriptionManager.setSubscriptionProperty(
                        mSubId, SubscriptionManager.SATELLITE_ENABLED, isProvisioned ? "1" : "0");
            } else {
                //TODO (b/267826133): set via SatelliteController.
            }
        }
    }

    private static final class SatelliteStateListenerHandler extends Handler {
        public static final int EVENT_SATELLITE_MODEM_STATE_CHANGE = 1;
        public static final int EVENT_PENDING_DATAGRAM_COUNT = 2;

        private final ConcurrentHashMap<IBinder, ISatelliteStateCallback> mListeners;
        private final int mSubId;

        SatelliteStateListenerHandler(Looper looper, int subId) {
            super(looper);
            mSubId = subId;
            mListeners = new ConcurrentHashMap<>();
        }

        public void addListener(ISatelliteStateCallback listener) {
            mListeners.put(listener.asBinder(), listener);
        }

        public void removeListener(ISatelliteStateCallback listener) {
            mListeners.remove(listener.asBinder());
        }

        public boolean hasListeners() {
            return !mListeners.isEmpty();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVENT_SATELLITE_MODEM_STATE_CHANGE : {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int state = (int) ar.result;
                    logd("Received EVENT_SATELLITE_MODEM_STATE_CHANGE for subId=" + mSubId
                            + ", state=" + state);
                    mListeners.values().forEach(listener -> {
                        try {
                            listener.onSatelliteModemStateChanged(state);
                        } catch (RemoteException e) {
                            logd("EVENT_SATELLITE_MODEM_STATE_CHANGE RemoteException: " + e);
                        }
                    });
                    break;
                }
                case EVENT_PENDING_DATAGRAM_COUNT: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    int count = (int) ar.result;
                    logd("Received EVENT_PENDING_DATAGRAM_COUNT for subId=" + mSubId
                            + ", count=" + count);
                    mListeners.values().forEach(listener -> {
                        try {
                            listener.onPendingDatagramCount(count);
                        } catch (RemoteException e) {
                            logd("EVENT_PENDING_DATAGRAM_COUNT RemoteException: " + e);
                        }
                    });
                    break;
                }
                default:
                    loge("SatelliteStateListenerHandler unknown event: " + msg.what);
            }
        }
    }

    /** Callback used by datagram receiver app to send ack back to Telephony. */
    private static final ISatelliteDatagramReceiverAck.Stub mDatagramReceiverAck =
            new ISatelliteDatagramReceiverAck.Stub() {
                /**
                 * This callback will be used by datagram receiver app to send ack back to
                 * Telephony. If the callback is not received within five minutes,
                 * then Telephony will resend the datagram again.
                 *
                 * @param datagramId An id that uniquely identifies datagram
                 *                   received by satellite datagram receiver app.
                 *                   This should match with datagramId passed in
                 *                   {@link android.telephony.satellite.SatelliteDatagramCallback
                 *                   #onSatelliteDatagramReceived(long, SatelliteDatagram, int,
                 *                   ISatelliteDatagramReceiverAck)}
                 *                   Upon receiving the ack, Telephony will remove the datagram from
                 *                   the persistent memory.
                 */
                public void acknowledgeSatelliteDatagramReceived(long datagramId) {
                    // TODO (b/269637555): remove from db.
                }
            };

    private static final class SatelliteDatagramListenerHandler extends Handler {
        public static final int EVENT_SATELLITE_DATAGRAMS_RECEIVED = 1;

        private final ConcurrentHashMap<IBinder, ISatelliteDatagramCallback> mListeners;
        private final int mSubId;

        SatelliteDatagramListenerHandler(Looper looper, int subId) {
            super(looper);
            mSubId = subId;
            mListeners = new ConcurrentHashMap<>();
        }

        public void addListener(ISatelliteDatagramCallback listener) {
            mListeners.put(listener.asBinder(), listener);
        }

        public void removeListener(ISatelliteDatagramCallback listener) {
            mListeners.remove(listener.asBinder());
        }

        public boolean hasListeners() {
            return !mListeners.isEmpty();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case EVENT_SATELLITE_DATAGRAMS_RECEIVED: {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    Pair<SatelliteDatagram[], Integer> result =
                            (Pair<SatelliteDatagram[], Integer>) ar.result;
                    SatelliteDatagram[] satelliteDatagrams = result.first;
                    int pendingCount = result.second;

                    logd("Received EVENT_SATELLITE_DATAGRAMS_RECEIVED for subId=" + mSubId);
                    mListeners.values().forEach(listener -> {
                        try {
                            for (int i = 0; i < satelliteDatagrams.length; i++) {
                                // TODO (b/269637555): wait for ack and retry after 5mins
                                listener.onSatelliteDatagramReceived(
                                        // TODO: create a new datagramId every time
                                        i, satelliteDatagrams[i], pendingCount,
                                        // TODO: create a new instance of ack that will resend
                                        mDatagramReceiverAck);
                            }
                        } catch (RemoteException e) {
                            logd("EVENT_SATELLITE_DATAGRAMS_RECEIVED RemoteException: " + e);
                        }
                    });
                    break;
                }
                default:
                    loge("SatelliteDatagramListenerHandler unknown event: " + msg.what);
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        SatelliteControllerHandlerRequest request;
        Message onCompleted;
        AsyncResult ar;

        switch(msg.what) {
            case CMD_START_SATELLITE_POSITION_UPDATES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_START_SATELLITE_POSITION_UPDATES_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.startSendingSatellitePointingInfo(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.startSatellitePositionUpdates(onCompleted);
                } else {
                    loge("startSatellitePositionUpdates: No phone object");
                    SatellitePositionUpdateArgument arg =
                            (SatellitePositionUpdateArgument) request.argument;
                    arg.errorCallback.accept(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                }
                break;
            }

            case EVENT_START_SATELLITE_POSITION_UPDATES_DONE: {
                handleStartSatellitePositionUpdatesDone((AsyncResult) msg.obj);
                break;
            }

            case CMD_STOP_SATELLITE_POSITION_UPDATES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_STOP_SATELLITE_POSITION_UPDATES_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.stopSendingSatellitePointingInfo(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.stopSatellitePositionUpdates(onCompleted);
                } else {
                    loge("stopSatellitePositionUpdates: No phone object");
                    ((Consumer<Integer>) request.argument).accept(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                }
                break;
            }

            case EVENT_STOP_SATELLITE_POSITION_UPDATES_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "stopSatellitePositionUpdates", false);
                ((Consumer<Integer>) request.argument).accept(error);
                break;
            }

            case CMD_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .requestMaxCharactersPerMOTextMessage(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.getMaxCharactersPerSatelliteTextMessage(onCompleted);
                } else {
                    loge("getMaxCharactersPerSatelliteTextMessage: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =
                        getSatelliteError(ar, "getMaxCharactersPerSatelliteTextMessage", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    int maxCharLimit = ((int[]) ar.result)[0];
                    if (DBG) logd("getMaxCharactersPerSatelliteTextMessage: " + maxCharLimit);
                    bundle.putInt(SatelliteManager.KEY_MAX_CHARACTERS_PER_SATELLITE_TEXT,
                            maxCharLimit);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_PROVISION_SATELLITE_SERVICE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                ProvisionSatelliteServiceArgument argument =
                        (ProvisionSatelliteServiceArgument) request.argument;
                if (mSatelliteProvisionCallbacks.containsKey(argument.subId)) {
                    argument.callback.accept(
                            SatelliteManager.SATELLITE_SERVICE_PROVISION_IN_PROGRESS);
                    notifyRequester(request);
                    break;
                }
                mSatelliteProvisionCallbacks.put(argument.subId, argument.callback);
                onCompleted = obtainMessage(EVENT_PROVISION_SATELLITE_SERVICE_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .provisionSatelliteService(argument.token, onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.provisionSatelliteService(onCompleted, argument.token);
                } else {
                    loge("provisionSatelliteService: No phone object");
                    argument.callback.accept(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                    notifyRequester(request);
                }
                break;
            }

            case EVENT_PROVISION_SATELLITE_SERVICE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int errorCode = getSatelliteError(ar, "provisionSatelliteService", false);
                handleEventProvisionSatelliteServiceDone(
                        (ProvisionSatelliteServiceArgument) request.argument, errorCode);
                notifyRequester(request);
                break;
            }

            case CMD_DEPROVISION_SATELLITE_SERVICE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                ProvisionSatelliteServiceArgument argument =
                        (ProvisionSatelliteServiceArgument) request.argument;
                onCompleted = obtainMessage(EVENT_DEPROVISION_SATELLITE_SERVICE_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .deprovisionSatelliteService(argument.token, onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.deprovisionSatelliteService(onCompleted, argument.token);
                } else {
                    loge("deprovisionSatelliteService: No phone object");
                    if (argument.callback != null) {
                        argument.callback.accept(
                                SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                    }
                }
                break;
            }

            case EVENT_DEPROVISION_SATELLITE_SERVICE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int errorCode = getSatelliteError(ar, "deprovisionSatelliteService", false);
                handleEventDeprovisionSatelliteServiceDone(
                        (ProvisionSatelliteServiceArgument) request.argument, errorCode);
                break;
            }

            case CMD_SET_SATELLITE_ENABLED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                Pair<Boolean, Consumer<Integer>> argument =
                        (Pair<Boolean, Consumer<Integer>>) request.argument;
                onCompleted = obtainMessage(EVENT_SET_SATELLITE_ENABLED_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .requestSatelliteEnabled(argument.first, onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.setSatellitePower(onCompleted, argument.first);
                } else {
                    loge("requestSatelliteEnabled: No phone object");
                    argument.second.accept(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                }
                break;
            }

            case EVENT_SET_SATELLITE_ENABLED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                Pair<Boolean, Consumer<Integer>> argument =
                        (Pair<Boolean, Consumer<Integer>>) request.argument;
                int error = getSatelliteError(ar, "setSatelliteEnabled", false);
                argument.second.accept(error);
                break;
            }

            case CMD_IS_SATELLITE_ENABLED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_IS_SATELLITE_ENABLED_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.requestIsSatelliteEnabled(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.isSatellitePowerOn(onCompleted);
                } else {
                    loge("isSatelliteEnabled: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_IS_SATELLITE_ENABLED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "isSatelliteEnabled", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    boolean enabled = ((int[]) ar.result)[0] == 1;
                    if (DBG) logd("isSatelliteEnabled: " + enabled);
                    bundle.putBoolean(SatelliteManager.KEY_SATELLITE_ENABLED, enabled);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_IS_SATELLITE_SUPPORTED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_IS_SATELLITE_SUPPORTED_DONE, request);

                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.requestIsSatelliteSupported(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.isSatelliteSupported(onCompleted);
                } else {
                    loge("isSatelliteSupported: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_IS_SATELLITE_SUPPORTED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "isSatelliteSupported", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    boolean supported = (boolean) ar.result;
                    if (DBG) logd("isSatelliteSupported: " + supported);
                    bundle.putBoolean(SatelliteManager.KEY_SATELLITE_SUPPORTED, supported);
                    synchronized (mIsSatelliteSupportedLock) {
                        mIsSatelliteSupported = supported;
                    }
                } else {
                    synchronized (mIsSatelliteSupportedLock) {
                        mIsSatelliteSupported = null;
                    }
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_GET_SATELLITE_CAPABILITIES: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_GET_SATELLITE_CAPABILITIES_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.requestSatelliteCapabilities(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.getSatelliteCapabilities(onCompleted);
                } else {
                    loge("getSatelliteCapabilities: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_GET_SATELLITE_CAPABILITIES_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "getSatelliteCapabilities", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    SatelliteCapabilities capabilities = (SatelliteCapabilities) ar.result;
                    if (DBG) logd("getSatelliteCapabilities: " + capabilities);
                    bundle.putParcelable(SatelliteManager.KEY_SATELLITE_CAPABILITIES,
                            capabilities);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_POLL_PENDING_SATELLITE_DATAGRAMS: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.pollPendingSatelliteDatagrams(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.pollPendingSatelliteDatagrams(onCompleted);
                } else {
                    loge("pollPendingSatelliteDatagrams: No phone object");
                    ((Consumer<Integer>) request.argument).accept(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                }
                break;
            }

            case EVENT_POLL_PENDING_SATELLITE_DATAGRAMS_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "pollPendingSatelliteDatagrams", false);
                ((Consumer<Integer>) request.argument).accept(error);
                break;
            }

            case CMD_SEND_SATELLITE_DATAGRAM: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                SendSatelliteDatagramArgument argument =
                        (SendSatelliteDatagramArgument) request.argument;
                onCompleted = obtainMessage(EVENT_SEND_SATELLITE_DATAGRAM_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.sendSatelliteDatagram(
                            argument.datagram,
                            argument.datagramType == SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE,
                            argument.needFullScreenPointingUI, onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.sendSatelliteDatagram(onCompleted, argument.datagram,
                            argument.needFullScreenPointingUI);
                } else {
                    loge("sendSatelliteDatagram: No phone object");
                    argument.result.send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_SEND_SATELLITE_DATAGRAM_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(ar, "sendSatelliteDatagram", false);
                SendSatelliteDatagramArgument argument =
                        (SendSatelliteDatagramArgument) request.argument;
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    bundle.putLong(SatelliteManager.KEY_SEND_SATELLITE_DATAGRAM,
                            argument.datagramId);
                }
                argument.result.send(error, bundle);
                break;
            }

            case CMD_IS_SATELLITE_COMMUNICATION_ALLOWED: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted =
                        obtainMessage(EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE, request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .requestIsSatelliteCommunicationAllowedForCurrentLocation(
                                    onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.isSatelliteCommunicationAllowedForCurrentLocation(onCompleted);
                } else {
                    loge("isSatelliteCommunicationAllowedForCurrentLocation: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_IS_SATELLITE_COMMUNICATION_ALLOWED_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error = getSatelliteError(
                        ar, "isSatelliteCommunicationAllowedForCurrentLocation", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    boolean communicationAllowed = (boolean) ar.result;
                    if (DBG) {
                        logd("isSatelliteCommunicationAllowedForCurrentLocation: "
                                + communicationAllowed);
                    }
                    bundle.putBoolean(SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED,
                            communicationAllowed);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            case CMD_GET_TIME_SATELLITE_NEXT_VISIBLE: {
                request = (SatelliteControllerHandlerRequest) msg.obj;
                onCompleted = obtainMessage(EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE,
                        request);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface
                            .requestTimeForNextSatelliteVisibility(onCompleted);
                    break;
                }
                Phone phone = request.phone;
                if (phone != null) {
                    phone.requestTimeForNextSatelliteVisibility(onCompleted);
                } else {
                    loge("requestTimeForNextSatelliteVisibility: No phone object");
                    ((ResultReceiver) request.argument).send(
                            SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, null);
                }
                break;
            }

            case EVENT_GET_TIME_SATELLITE_NEXT_VISIBLE_DONE: {
                ar = (AsyncResult) msg.obj;
                request = (SatelliteControllerHandlerRequest) ar.userObj;
                int error =
                        getSatelliteError(ar, "requestTimeForNextSatelliteVisibility", true);
                Bundle bundle = new Bundle();
                if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
                    int nextVisibilityDuration = ((int[]) ar.result)[0];
                    if (DBG) {
                        logd("requestTimeForNextSatelliteVisibility: " + nextVisibilityDuration);
                    }
                    bundle.putInt(SatelliteManager.KEY_SATELLITE_NEXT_VISIBILITY,
                            nextVisibilityDuration);
                }
                ((ResultReceiver) request.argument).send(error, bundle);
                break;
            }

            default:
                Log.w(TAG, "SatelliteControllerHandler: unexpected message code: " +
                        msg.what);
                break;
        }
    }

    private void notifyRequester(SatelliteControllerHandlerRequest request) {
        synchronized (request) {
            request.notifyAll();
        }
    }

    /**
     * Request to enable or disable the satellite modem. If the satellite modem is enabled, this
     * will also disable the cellular modem, and if the satellite modem is disabled, this will also
     * re-enable the cellular modem.
     *
     * @param subId The subId of the subscription to set satellite enabled for.
     * @param enable {@code true} to enable the satellite modem and {@code false} to disable.
     * @param callback The callback to get the error code of the request.
     */
    public void requestSatelliteEnabled(
            int subId, boolean enable, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "requestSatelliteEnabled");
        Pair<Boolean, Consumer<Integer>> arg = new Pair<>(enable, result);
        sendRequestAsync(CMD_SET_SATELLITE_ENABLED, arg, phone);
    }

    /**
     * Request to get whether the satellite modem is enabled.
     *
     * @param subId The subId of the subscription to check whether satellite is enabled for.
     * @param result The result receiver that returns whether the satellite modem is enabled
     *               if the request is successful or an error code if the request failed.
     */
    public void requestIsSatelliteEnabled(int subId, @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "requestIsSatelliteEnabled");
        sendRequest(CMD_IS_SATELLITE_ENABLED, result, phone);
    }

    /**
     * Request to enable or disable the satellite service demo mode.
     *
     * @param subId The subId of the subscription to set the satellite demo mode enabled for.
     * @param enable {@code true} to enable the satellite demo mode and {@code false} to disable.
     * @param callback The callback to get the error code of the request.
     */
    public void requestSatelliteDemoModeEnabled(
            int subId, boolean enable, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED);
            return;
        }

        mIsSatelliteDemoModeEnabled = enable;
        result.accept(SatelliteManager.SATELLITE_ERROR_NONE);
    }

    /**
     * Request to get whether the satellite service demo mode is enabled.
     *
     * @param subId The subId of the subscription to check whether the satellite demo mode
     *              is enabled for.
     * @param result The result receiver that returns whether the satellite demo mode is enabled
     *               if the request is successful or an error code if the request failed.
     */
    public void requestIsSatelliteDemoModeEnabled(int subId, @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        final Bundle bundle = new Bundle();
        bundle.putBoolean(SatelliteManager.KEY_DEMO_MODE_ENABLED, mIsSatelliteDemoModeEnabled);
        result.send(SatelliteManager.SATELLITE_ERROR_NONE, bundle);
    }

    /**
     * Request to get whether the satellite service is supported on the device.
     *
     * @param subId The subId of the subscription to check satellite service support for.
     * @param result The result receiver that returns whether the satellite service is supported on
     *               the device if the request is successful or an error code if the request failed.
     */
    public void requestIsSatelliteSupported(int subId, @NonNull ResultReceiver result) {
        synchronized (mIsSatelliteSupportedLock) {
            if (mIsSatelliteSupported != null) {
                /* We have already successfully queried the satellite modem. */
                Bundle bundle = new Bundle();
                bundle.putBoolean(SatelliteManager.KEY_SATELLITE_SUPPORTED, mIsSatelliteSupported);
                result.send(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE, bundle);
                return;
            }
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(validSubId, "requestIsSatelliteSupported");
        sendRequestAsync(CMD_IS_SATELLITE_SUPPORTED, result, phone);
    }

    /**
     * Request to get the {@link SatelliteCapabilities} of the satellite service.
     *
     * @param subId The subId of the subscription to get the satellite capabilities for.
     * @param result The result receiver that returns the {@link SatelliteCapabilities}
     *               if the request is successful or an error code if the request failed.
     */
    public void requestSatelliteCapabilities(int subId, @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(validSubId, "requestSatelliteCapabilities");
        sendRequestAsync(CMD_GET_SATELLITE_CAPABILITIES, result, phone);
    }

    /**
     * Start receiving satellite position updates.
     * This can be called by the pointing UI when the user starts pointing to the satellite.
     * Modem should continue to report the pointing input as the device or satellite moves.
     *
     * @param subId The subId of the subscription to start satellite position updates for.
     * @param errorCallback The callback to get the error code of the request.
     * @param callback The callback to notify of changes in satellite position.
     */
    public void startSatellitePositionUpdates(int subId, @NonNull IIntegerConsumer errorCallback,
            @NonNull ISatellitePositionUpdateCallback callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(errorCallback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "startSatellitePositionUpdates");
        SatellitePositionUpdateHandler handler = mSatellitePositionUpdateHandlers.get(validSubId);
        if (handler != null) {
            handler.addListener(callback);
            return;
        } else {
            handler = new SatellitePositionUpdateHandler(Looper.getMainLooper());
            handler.addListener(callback);
            mSatellitePositionUpdateHandlers.put(validSubId, handler);
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                mSatelliteModemInterface.registerForSatellitePositionInfoChanged(handler,
                        SatellitePositionUpdateHandler.EVENT_POSITION_INFO_CHANGED, null);
                mSatelliteModemInterface.registerForDatagramTransferStateChanged(handler,
                        SatellitePositionUpdateHandler.EVENT_DATAGRAM_TRANSFER_STATE_CHANGED, null);
            } else {
                phone.registerForSatellitePositionInfoChanged(handler,
                        SatellitePositionUpdateHandler.EVENT_POSITION_INFO_CHANGED, null);
                // TODO: registerForDatagramTransferStateChanged through SatelliteController
            }
        }

        sendRequestAsync(CMD_START_SATELLITE_POSITION_UPDATES,
                new SatellitePositionUpdateArgument(result, callback, validSubId), phone);
    }

    /**
     * Stop receiving satellite position updates.
     * This can be called by the pointing UI when the user stops pointing to the satellite.
     *
     * @param subId The subId of the subscription to stop satellite position updates for.
     * @param errorCallback The callback to get the error code of the request.
     * @param callback The callback that was passed to {@link
     * #startSatellitePositionUpdates(int, IIntegerConsumer, ISatellitePositionUpdateCallback)}
     */
    public void stopSatellitePositionUpdates(int subId, @NonNull IIntegerConsumer errorCallback,
            @NonNull ISatellitePositionUpdateCallback callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(errorCallback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "stopSatellitePositionUpdates");
        SatellitePositionUpdateHandler handler = mSatellitePositionUpdateHandlers.get(validSubId);
        if (handler != null) {
            handler.removeListener(callback);

            if (handler.hasListeners()) {
                /**
                 * TODO (b/269194948): If the calling apps crash, the handler will always have some
                 * listener. That is, we will not request modem to stop position update and
                 * cleaning our resources. We need to monitor the calling apps and clean up the
                 * resources when the apps die. We need to this for other satellite callbacks
                 * as well.
                 */
                result.accept(SatelliteManager.SATELLITE_ERROR_NONE);
                return;
            }

            mSatellitePositionUpdateHandlers.remove(validSubId);
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                mSatelliteModemInterface.unregisterForSatellitePositionInfoChanged(handler);
                mSatelliteModemInterface.unregisterForDatagramTransferStateChanged(handler);
            } else {
                if (phone == null) {
                    result.accept(SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE);
                    return;
                }
                phone.unregisterForSatellitePositionInfoChanged(handler);
                // TODO: unregisterForDatagramTransferStateChanged through SatelliteController
            }
        }

        /**
         * Even if handler is null - which means there are not any listeners, the command to stop
         * satellite position updates sent to the modem might have failed. The callers might want to
         * retry sending the command. Thus, we always need to send this command to the modem.
         */
        sendRequestAsync(CMD_STOP_SATELLITE_POSITION_UPDATES, result, phone);
    }

    /**
     * Request to get the maximum number of bytes per datagram that can be sent to satellite.
     *
     * @param subId The subId of the subscription to get the maximum number of characters for.
     * @param result The result receiver that returns the maximum number of bytes per datagram
     *               message on satellite if the request is successful or an error code
     *               if the request failed.
     */
    public void requestMaxSizePerSendingDatagram(int subId,
            @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "requestMaxSizePerSendingDatagram");
        sendRequestAsync(CMD_GET_MAX_CHAR_PER_SATELLITE_TEXT_MSG, result, phone);
    }

    /**
     * Register the subscription with a satellite provider.
     * This is needed to register the subscription if the provider allows dynamic registration.
     *
     * @param subId The subId of the subscription to be provisioned.
     * @param token The token to be used as a unique identifier for provisioning with satellite
     *              gateway.
     * @param callback The callback to get the error code of the request.
     *
     * @return The signal transport used by the caller to cancel the provision request,
     *         or {@code null} if the request failed.
     */
    @Nullable public ICancellationSignal provisionSatelliteService(int subId,
            @NonNull String token, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return null;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(validSubId, "provisionSatelliteService");

        if (mSatelliteProvisionCallbacks.containsKey(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_PROVISION_IN_PROGRESS);
            return null;
        }

        if (isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_ERROR_NONE);
            return null;
        }

        sendRequestAsync(CMD_PROVISION_SATELLITE_SERVICE,
                new ProvisionSatelliteServiceArgument(token, result, validSubId), phone);

        ICancellationSignal cancelTransport = CancellationSignal.createTransport();
        CancellationSignal.fromTransport(cancelTransport).setOnCancelListener(() -> {
            sendRequestAsync(CMD_DEPROVISION_SATELLITE_SERVICE,
                    new ProvisionSatelliteServiceArgument(token, null, validSubId),
                    phone);
        });
        return cancelTransport;
    }

    /**
     * Unregister the device/subscription with the satellite provider.
     * This is needed if the provider allows dynamic registration. Once deprovisioned,
     * {@link android.telephony.satellite.SatelliteProvisionStateCallback
     * #onSatelliteProvisionStateChanged(boolean)}
     * should report as deprovisioned.
     *
     * @param subId The subId of the subscription to be deprovisioned.
     * @param token The token of the device/subscription to be deprovisioned.
     * @param callback The callback to get the error code of the request.
     */
    public void deprovisionSatelliteService(int subId,
            @NonNull String token, @NonNull IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);
        if (!isSatelliteSupported()) {
            result.accept(SatelliteManager.SATELLITE_NOT_SUPPORTED);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_ERROR_NONE);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "deprovisionSatelliteService");
        sendRequestAsync(CMD_DEPROVISION_SATELLITE_SERVICE,
                new ProvisionSatelliteServiceArgument(token, result, validSubId), phone);
    }

    /**
     * Registers for the satellite provision state changed.
     *
     * @param subId The subId of the subscription to register for provision state changed.
     * @param callback The callback to handle the satellite provision state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteError} result of the operation.
     */
    @SatelliteManager.SatelliteError public int registerForSatelliteProvisionStateChanged(int subId,
            @NonNull ISatelliteProvisionStateCallback callback) {
        return registerForSatelliteProvisionStateChangedInternal(subId, callback);
    }

    /**
     * Unregisters for the satellite provision state changed.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for provision state changed.
     * @param callback The callback that was passed to
     * {@link #registerForSatelliteProvisionStateChanged(int, ISatelliteProvisionStateCallback)}.
     */
    public void unregisterForSatelliteProvisionStateChanged(
            int subId, @NonNull ISatelliteProvisionStateCallback callback) {
        final int validSubId = getValidSatelliteSubId(subId);
        SatelliteProvisionStateChangedHandler satelliteProvisionStateChangedHandler =
                mSatelliteProvisionStateChangedHandlers.get(validSubId);
        if (satelliteProvisionStateChangedHandler != null) {
            satelliteProvisionStateChangedHandler.removeListener(callback);
        }
    }

    /**
     * Request to get whether the device is provisioned with a satellite provider.
     *
     * @param subId The subId of the subscription to get whether the device is provisioned for.
     * @param result The result receiver that returns whether the device is provisioned with a
     *               satellite provider if the request is successful or an error code if the
     *               request failed.
     */
    public void requestIsSatelliteProvisioned(int subId, @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Bundle bundle = new Bundle();
        bundle.putBoolean(SatelliteManager.KEY_SATELLITE_PROVISIONED,
                isSatelliteProvisioned(validSubId));
        result.send(SatelliteManager.SATELLITE_ERROR_NONE, bundle);
    }

    /**
     * Registers for modem state changed from satellite modem.
     *
     * @param subId The subId of the subscription to register for satellite modem state changed.
     * @param callback The callback to handle the satellite modem state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteError} result of the operation.
     */
    @SatelliteManager.SatelliteError public int registerForSatelliteModemStateChanged(int subId,
            @NonNull ISatelliteStateCallback callback) {
        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(
                validSubId, "registerForSatelliteModemStateChanged");

        SatelliteStateListenerHandler satelliteStateListenerHandler =
                mSatelliteStateListenerHandlers.get(validSubId);
        if (satelliteStateListenerHandler == null) {
            satelliteStateListenerHandler = new SatelliteStateListenerHandler(
                    Looper.getMainLooper(), validSubId);
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                mSatelliteModemInterface.registerForSatelliteModemStateChanged(
                        satelliteStateListenerHandler,
                        SatelliteStateListenerHandler.EVENT_SATELLITE_MODEM_STATE_CHANGE, null);
                mSatelliteModemInterface.registerForPendingDatagramCount(
                        satelliteStateListenerHandler,
                        SatelliteStateListenerHandler.EVENT_PENDING_DATAGRAM_COUNT, null);
            } else {
                phone.registerForSatelliteModemStateChanged(satelliteStateListenerHandler,
                        SatelliteStateListenerHandler.EVENT_SATELLITE_MODEM_STATE_CHANGE, null);
                phone.registerForPendingDatagramCount(satelliteStateListenerHandler,
                        SatelliteStateListenerHandler.EVENT_PENDING_DATAGRAM_COUNT, null);
            }
        }

        satelliteStateListenerHandler.addListener(callback);
        mSatelliteStateListenerHandlers.put(validSubId, satelliteStateListenerHandler);
        return SatelliteManager.SATELLITE_ERROR_NONE;
    }

    /**
     * Unregisters for modem state changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param subId The subId of the subscription to unregister for satellite modem state changed.
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteModemStateChanged(int, ISatelliteStateCallback)}.
     */
    public void unregisterForSatelliteModemStateChanged(int subId,
            @NonNull ISatelliteStateCallback callback) {
        final int validSubId = getValidSatelliteSubId(subId);
        SatelliteStateListenerHandler handler = mSatelliteStateListenerHandlers.get(validSubId);
        if (handler != null) {
            handler.removeListener(callback);
            if (!handler.hasListeners()) {
                mSatelliteStateListenerHandlers.remove(validSubId);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.unregisterForSatelliteModemStateChanged(handler);
                    mSatelliteModemInterface.unregisterForPendingDatagramCount(handler);
                } else {
                    Phone phone = getPhoneOrDefault(
                            validSubId, "unregisterForSatelliteModemStateChanged");
                    if (phone != null) {
                        phone.unregisterForSatelliteModemStateChanged(handler);
                        phone.unregisterForPendingDatagramCount(handler);
                    }
                }
            }
        }
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
        if (!isSatelliteSupported()) {
            return SatelliteManager.SATELLITE_NOT_SUPPORTED;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(validSubId, "registerForSatelliteDatagram");

        SatelliteDatagramListenerHandler satelliteDatagramListenerHandler =
                mSatelliteDatagramListenerHandlers.get(validSubId);
        if (satelliteDatagramListenerHandler == null) {
            satelliteDatagramListenerHandler = new SatelliteDatagramListenerHandler(
                    Looper.getMainLooper(), validSubId);
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                mSatelliteModemInterface.registerForSatelliteDatagramsReceived(
                        satelliteDatagramListenerHandler,
                        SatelliteDatagramListenerHandler.EVENT_SATELLITE_DATAGRAMS_RECEIVED, null);
            } else {
                phone.registerForSatelliteDatagramsReceived(satelliteDatagramListenerHandler,
                        SatelliteDatagramListenerHandler.EVENT_SATELLITE_DATAGRAMS_RECEIVED, null);
            }
        }

        satelliteDatagramListenerHandler.addListener(callback);
        mSatelliteDatagramListenerHandlers.put(validSubId, satelliteDatagramListenerHandler);
        return SatelliteManager.SATELLITE_ERROR_NONE;
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
        final int validSubId = getValidSatelliteSubId(subId);
        SatelliteDatagramListenerHandler handler =
                mSatelliteDatagramListenerHandlers.get(validSubId);
        if (handler != null) {
            handler.removeListener(callback);

            if (!handler.hasListeners()) {
                mSatelliteDatagramListenerHandlers.remove(validSubId);
                if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                    mSatelliteModemInterface.unregisterForSatelliteDatagramsReceived(handler);
                } else {
                    Phone phone = getPhoneOrDefault(validSubId,
                            "unregisterForSatelliteDatagram");
                    if (phone != null) {
                        phone.unregisterForSatelliteDatagramsReceived(handler);
                    }
                }
            }
        }
    }

    /**
     * Poll pending satellite datagrams over satellite.
     *
     * This method requests modem to check if there are any pending datagrams to be received over
     * satellite. If there are any incoming datagrams, they will be received via
     * {@link android.telephony.satellite.SatelliteDatagramCallback
     * #onSatelliteDatagramReceived(long, SatelliteDatagram, int, ISatelliteDatagramReceiverAck)}
     *
     * @param subId The subId of the subscription used for receiving datagrams.
     * @param callback The callback to get {@link SatelliteManager.SatelliteError} of the request.
     */
    public void pollPendingSatelliteDatagrams(int subId, IIntegerConsumer callback) {
        Consumer<Integer> result = FunctionalUtils.ignoreRemoteException(callback::accept);

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.accept(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "pollPendingSatelliteDatagrams");
        sendRequestAsync(CMD_POLL_PENDING_SATELLITE_DATAGRAMS, result, phone);
    }

    /**
     * Send datagram over satellite.
     *
     * Gateway encodes SOS message or location sharing message into a datagram and passes it as
     * input to this method. Datagram received here will be passed down to modem without any
     * encoding or encryption.
     *
     * @param subId The subId of the subscription to send satellite datagrams for.
     * @param datagramId An id that uniquely identifies datagram requested to be sent.
     * @param datagramType datagram type indicating whether the datagram is of type
     *                     SOS_SMS or LOCATION_SHARING.
     * @param datagram encoded gateway datagram which is encrypted by the caller.
     *                 Datagram will be passed down to modem without any encoding or encryption.
     * @param needFullScreenPointingUI this is used to indicate pointingUI app to open in
     *                                 full screen mode.
     * @param result The result receiver that returns datagramId if datagram is sent successfully
     *               or {@link SatelliteManager.SatelliteError} of the request if it is failed.
     */
    public void sendSatelliteDatagram(int subId, long datagramId,
            @SatelliteManager.DatagramType int datagramType, SatelliteDatagram datagram,
            boolean needFullScreenPointingUI, @NonNull ResultReceiver result) {
        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "sendSatelliteDatagram");
        // TODO: check if we need to start PointingUI.
        sendRequestAsync(CMD_SEND_SATELLITE_DATAGRAM,
                new SendSatelliteDatagramArgument(datagramId, datagramType, datagram,
                        needFullScreenPointingUI, result), phone);
    }

    /**
     * Request to get whether satellite communication is allowed for the current location.
     *
     * @param subId The subId of the subscription to check whether satellite communication is
     *              allowed for the current location for.
     * @param result The result receiver that returns whether satellite communication is allowed
     *               for the current location if the request is successful or an error code
     *               if the request failed.
     */
    public void requestIsSatelliteCommunicationAllowedForCurrentLocation(int subId,
            @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId,
                "requestIsSatelliteCommunicationAllowedForCurrentLocation");
        sendRequest(CMD_IS_SATELLITE_COMMUNICATION_ALLOWED, result, phone);
    }

    /**
     * Request to get the time after which the satellite will be visible
     *
     * @param subId The subId to get the time after which the satellite will be visible for.
     * @param result The result receiver that returns the time after which the satellite will
     *               be visible if the request is successful or an error code if the request failed.
     */
    public void requestTimeForNextSatelliteVisibility(int subId, @NonNull ResultReceiver result) {
        if (!isSatelliteSupported()) {
            result.send(SatelliteManager.SATELLITE_NOT_SUPPORTED, null);
            return;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        if (!isSatelliteProvisioned(validSubId)) {
            result.send(SatelliteManager.SATELLITE_SERVICE_NOT_PROVISIONED, null);
            return;
        }

        Phone phone = getPhoneOrDefault(validSubId, "requestTimeForNextSatelliteVisibility");
        sendRequestAsync(CMD_GET_TIME_SATELLITE_NEXT_VISIBLE, result, phone);
    }
    private void handleEventProvisionSatelliteServiceDone(
            @NonNull ProvisionSatelliteServiceArgument arg,
            @SatelliteManager.SatelliteError int result) {
        logd("handleEventProvisionSatelliteServiceDone: result="
                + result + ", subId=" + arg.subId);

        Consumer<Integer> callback = mSatelliteProvisionCallbacks.remove(arg.subId);
        if (callback == null) {
            loge("handleEventProvisionSatelliteServiceDone: callback is null for subId="
                    + arg.subId);
            return;
        }
        callback.accept(result);

        if (result == SatelliteManager.SATELLITE_ERROR_NONE) {
            setSatelliteProvisioned(arg.subId, true);
        }

        /**
         * We need to update satellite provision status in SubscriptionController
         * or SatelliteController.
         * TODO (b/267826133) we need to do this for all subscriptions on the device.
         */
        registerForSatelliteProvisionStateChangedInternal(arg.subId, null);
    }

    public boolean isSatelliteDemoModeEnabled() {
        return mIsSatelliteDemoModeEnabled;
    }

    private void handleEventDeprovisionSatelliteServiceDone(
            @NonNull ProvisionSatelliteServiceArgument arg,
            @SatelliteManager.SatelliteError int result) {
        if (arg == null) {
            loge("handleEventDeprovisionSatelliteServiceDone: arg is null");
            return;
        }
        logd("handleEventDeprovisionSatelliteServiceDone: result="
                + result + ", subId=" + arg.subId);

        if (arg.callback != null) {
            arg.callback.accept(result);
        }

        if (result == SatelliteManager.SATELLITE_ERROR_NONE) {
            setSatelliteProvisioned(arg.subId, false);
        }
    }

    /**
     * Registers for the satellite provision state changed.
     *
     * @param subId The subId of the subscription associated with the satellite service.
     * @param callback The callback to handle the satellite provision state changed event.
     *
     * @return The {@link SatelliteManager.SatelliteError} result of the operation.
     */
    @SatelliteManager.SatelliteError private int registerForSatelliteProvisionStateChangedInternal(
            int subId, @Nullable ISatelliteProvisionStateCallback callback) {
        if (!isSatelliteSupported()) {
            return SatelliteManager.SATELLITE_NOT_SUPPORTED;
        }

        final int validSubId = getValidSatelliteSubId(subId);
        Phone phone = getPhoneOrDefault(validSubId, "registerForSatelliteProvisionStateChanged");

        SatelliteProvisionStateChangedHandler satelliteProvisionStateChangedHandler =
                mSatelliteProvisionStateChangedHandlers.get(validSubId);
        if (satelliteProvisionStateChangedHandler == null) {
            satelliteProvisionStateChangedHandler = new SatelliteProvisionStateChangedHandler(
                    Looper.getMainLooper(), validSubId);
            if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                mSatelliteModemInterface.registerForSatelliteProvisionStateChanged(
                        satelliteProvisionStateChangedHandler,
                        SatelliteProvisionStateChangedHandler.EVENT_PROVISION_STATE_CHANGED, null);
            } else {
                phone.registerForSatelliteProvisionStateChanged(
                        satelliteProvisionStateChangedHandler,
                        SatelliteProvisionStateChangedHandler.EVENT_PROVISION_STATE_CHANGED, null);
            }
        }

        if (callback != null) {
            satelliteProvisionStateChangedHandler.addListener(callback);
        }
        mSatelliteProvisionStateChangedHandlers.put(
                validSubId, satelliteProvisionStateChangedHandler);
        return SatelliteManager.SATELLITE_ERROR_NONE;
    }

    private void handleStartSatellitePositionUpdatesDone(@NonNull AsyncResult ar) {
        SatelliteControllerHandlerRequest request = (SatelliteControllerHandlerRequest) ar.userObj;
        SatellitePositionUpdateArgument arg = (SatellitePositionUpdateArgument) request.argument;
        int errorCode = getSatelliteError(
                ar, "handleStartSatellitePositionUpdatesDone", false);
        arg.errorCallback.accept(errorCode);

        if (errorCode != SatelliteManager.SATELLITE_ERROR_NONE) {
            /**
             * We need to remove the callback from our listener list since the caller might not call
             * {@link #stopSatellitePositionUpdates(int, IIntegerConsumer, ISatellitePositionUpdateCallback)}
             * to unregister the callback in case of failure.
             */
            SatellitePositionUpdateHandler handler =
                    mSatellitePositionUpdateHandlers.get(arg.subId);
            if (handler != null) {
                handler.removeListener(arg.callback);

                if (!handler.hasListeners()) {
                    mSatellitePositionUpdateHandlers.remove(arg.subId);

                    if (mSatelliteModemInterface.isSatelliteServiceSupported()) {
                        mSatelliteModemInterface.unregisterForSatellitePositionInfoChanged(
                                handler);
                        mSatelliteModemInterface.unregisterForDatagramTransferStateChanged(
                                handler);
                    } else {
                        Phone phone = request.phone;
                        if (phone == null) {
                            loge("handleStartSatellitePositionUpdatesDone: phone is null");
                        } else {
                            phone.unregisterForSatellitePositionInfoChanged(handler);
                            // TODO: unregisterForDatagramTransferStateChanged through
                            //  SatelliteController
                        }
                    }
                }
            }
        }
    }

    /**
     * Set satellite provisioned for a subscription or the device.
     *
     * The permission {@link android.Manifest.permission#MODIFY_PHONE_STATE} will be enforced by
     * {@link SubscriptionController} when setting satellite enabled for an active subscription.
     * Otherwise, {@link android.Manifest.permission#SATELLITE_COMMUNICATION} will be enforced.
     */
    private synchronized void setSatelliteProvisioned(int subId, boolean isEnabled) {
        if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
            SubscriptionManager.setSubscriptionProperty(
                    subId, SubscriptionManager.SATELLITE_ENABLED, isEnabled ? "1" : "0");
        } else {
            //TODO (b/267826133): set via SatelliteController
        }
    }


    /**
     * If we have not successfully queried the satellite modem for its satellite service support,
     * we will retry the query one more time. Otherwise, we will return the queried result.
     */
    private boolean isSatelliteSupported() {
        synchronized (mIsSatelliteSupportedLock) {
            if (mIsSatelliteSupported != null) {
                /* We have already successfully queried the satellite modem. */
                return mIsSatelliteSupported;
            }
        }
        /**
         * We have not successfully checked whether the modem supports satellite service.
         * Thus, we need to retry it now.
         */
        requestIsSatelliteSupported(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                mSatelliteSupportedReceiver);
        return false;
    }

    /**
     * Posts the specified command to be executed on the main thread and returns immediately.
     *
     * @param command command to be executed on the main thread
     * @param argument additional parameters required to perform of the operation
     * @param phone phone object used to perform the operation.
     */
    private void sendRequestAsync(int command, @NonNull Object argument, @Nullable Phone phone) {
        SatelliteControllerHandlerRequest request = new SatelliteControllerHandlerRequest(
                argument, phone);
        Message msg = this.obtainMessage(command, request);
        msg.sendToTarget();
    }

    /**
     * Posts the specified command to be executed on the main thread. As this is a synchronous
     * request, it waits until the request is complete and then return the result.
     *
     * @param command command to be executed on the main thread
     * @param argument additional parameters required to perform of the operation
     * @param phone phone object used to perform the operation.
     * @return result of the operation
     */
    private @Nullable Object sendRequest(int command, @NonNull Object argument,
            @Nullable Phone phone) {
        if (Looper.myLooper() == this.getLooper()) {
            throw new RuntimeException("This method will deadlock if called from the main thread");
        }

        SatelliteControllerHandlerRequest request = new SatelliteControllerHandlerRequest(
                argument, phone);
        Message msg = this.obtainMessage(command, request);
        msg.sendToTarget();

        synchronized (request) {
            while(request.result == null) {
                try {
                    request.wait();
                } catch (InterruptedException e) {
                    // Do nothing, go back and wait until the request is complete.
                }
            }
        }
        return request.result;
    }

    /**
     * Return phone associated with this subscription or default phone.
     *
     * @param subId The subscription id.
     * @param caller caller name to include in the error message.
     * @return phone associated with this subscription or default phone.
     */
    private Phone getPhoneOrDefault(int subId, String caller) {
        Phone phone = PhoneFactory.getPhone(SubscriptionManager.getPhoneId(subId));
        if (phone == null) {
            loge(caller + " called with invalid subId: " + subId
                    + ". Retrying with default subscription.");
            phone = PhoneFactory.getPhone(SubscriptionManager
                    .getPhoneId(PhoneFactory.getDefaultSubscription()));
        }

        if (phone == null) {
            loge(caller + " called with invalid default subId: " + subId
                    + ". Retrying with default phone.");
            phone = PhoneFactory.getDefaultPhone();
        }

        if (phone == null) {
            loge(caller + " failed with no phone object.");
        }
        return phone;
    }

    /**
     * Get valid subscription id for satellite communication.
     *
     * @param subId The subscription id.
     * @return input subId if the subscription is active else return default subscription id.
     */
    private int getValidSatelliteSubId(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            boolean isActive;
            if (PhoneFactory.isSubscriptionManagerServiceEnabled()) {
                isActive = SubscriptionManagerService.getInstance().isActiveSubId(subId,
                        mContext.getOpPackageName(), mContext.getAttributionTag());
            } else {
                isActive = SubscriptionController.getInstance().isActiveSubId(subId,
                        mContext.getOpPackageName(), mContext.getAttributionTag());
            }

            if (isActive) {
                return subId;
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        if (DBG) logd("getValidSatelliteSubId: use DEFAULT_SUBSCRIPTION_ID for subId=" + subId);
        return SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    }

    /**
     * Get the {@link SatelliteManager.SatelliteError} from the provided result.
     *
     * @param ar AsyncResult used to determine the error code.
     * @param caller The satellite request.
     * @param checkResult Whether to check if the result exists.
     *
     * @return The {@link SatelliteManager.SatelliteError} error code from the request.
     */
    @SatelliteManager.SatelliteError private int getSatelliteError(@NonNull AsyncResult ar,
            @NonNull String caller, boolean checkResult) {
        int errorCode;
        if (ar.exception == null) {
            errorCode = SatelliteManager.SATELLITE_ERROR_NONE;
            if (checkResult && ar.result == null) {
                loge(caller + ": result is null");
                errorCode = SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE;
            }
        } else {
            errorCode = SatelliteManager.SATELLITE_ERROR;
            if (ar.exception instanceof CommandException) {
                CommandException.Error error =
                        ((CommandException) (ar.exception)).getCommandError();
                errorCode = RILUtils.convertToSatelliteError(error);
                loge(caller + " CommandException: " + ar.exception);
            } else {
                loge(caller + " unknown exception: " + ar.exception);
            }
        }
        logd(caller + " error: " + errorCode);
        return errorCode;
    }

    /**
     * Check if satellite is provisioned for a subscription on the device.
     * @param subId The subscription id.
     * @return true if satellite is provisioned on the given subscription else return false.
     */
    private boolean isSatelliteProvisioned(int subId) {
        final long identity = Binder.clearCallingIdentity();
        try {
            if (subId != SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) {
                String strResult = null;
                if (PhoneFactory.isSubscriptionManagerServiceEnabled()) {
                    strResult = SubscriptionManagerService.getInstance()
                            .getSubscriptionProperty(subId, SubscriptionManager.SATELLITE_ENABLED,
                                    mContext.getOpPackageName(), mContext.getAttributionTag());
                } else {
                    strResult = SubscriptionController.getInstance()
                            .getSubscriptionProperty(subId, SubscriptionManager.SATELLITE_ENABLED);
                }

                if (strResult != null) {
                    int intResult = Integer.parseInt(strResult);
                    return (intResult == 1);
                }
            } else {
                //TODO (b/267826133): check via SatelliteController
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
        return false;
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}

