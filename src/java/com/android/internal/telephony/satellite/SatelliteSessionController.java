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

import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.telephony.Rlog;
import android.telephony.satellite.ISatelliteStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This module is responsible for managing session state transition and inform listeners of modem
 * state changed events accordingly.
 */
public class SatelliteSessionController extends StateMachine {
    private static final String TAG = "SatelliteSessionController";
    private static final boolean DBG = true;
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);

    /**
     * The time duration in millis that the satellite will stay at listening mode to wait for the
     * next incoming page before disabling listening mode when transitioning from sending mode.
     */
    public static final String SATELLITE_STAY_AT_LISTENING_FROM_SENDING_MILLIS =
            "satellite_stay_at_listening_from_sending_millis";
    /**
     * The default value of {@link #SATELLITE_STAY_AT_LISTENING_FROM_SENDING_MILLIS}.
     */
    public static final long DEFAULT_SATELLITE_STAY_AT_LISTENING_FROM_SENDING_MILLIS = 180000;
    /**
     * The time duration in millis that the satellite will stay at listening mode to wait for the
     * next incoming page before disabling listening mode when transitioning from receiving mode.
     */
    public static final String SATELLITE_STAY_AT_LISTENING_FROM_RECEIVING_MILLIS =
            "satellite_stay_at_listening_from_receiving_millis";
    /**
     * The default value of {@link #SATELLITE_STAY_AT_LISTENING_FROM_RECEIVING_MILLIS}
     */
    public static final long DEFAULT_SATELLITE_STAY_AT_LISTENING_FROM_RECEIVING_MILLIS = 30000;

    private static final int EVENT_DATAGRAM_TRANSFER_STATE_CHANGED = 1;
    private static final int EVENT_LISTENING_TIMER_TIMEOUT = 2;
    private static final int EVENT_SATELLITE_ENABLED_STATE_CHANGED = 3;

    @NonNull private static SatelliteSessionController sInstance;

    @NonNull private final Context mContext;
    @NonNull private final SatelliteModemInterface mSatelliteModemInterface;
    @NonNull private final UnavailableState mUnavailableState = new UnavailableState();
    @NonNull private final PowerOffState mPowerOffState = new PowerOffState();
    @NonNull private final IdleState mIdleState = new IdleState();
    @NonNull private final TransferringState mTransferringState = new TransferringState();
    @NonNull private final ListeningState mListeningState = new ListeningState();
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected AtomicBoolean mIsSendingTriggeredDuringTransferringState;
    private long mSatelliteStayAtListeningFromSendingMillis;
    private long mSatelliteStayAtListeningFromReceivingMillis;
    private final ConcurrentHashMap<IBinder, ISatelliteStateCallback> mListeners;
    @SatelliteManager.SatelliteModemState private int mCurrentState;
    final boolean mIsSatelliteSupported;

    /**
     * @return The singleton instance of SatelliteSessionController.
     */
    public static SatelliteSessionController getInstance() {
        if (sInstance == null) {
            Log.e(TAG, "SatelliteSessionController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the SatelliteSessionController singleton instance.
     *
     * @param context The Context for the SatelliteSessionController.
     * @param looper The looper associated with the handler of this class.
     * @param isSatelliteSupported Whether satellite is supported on the device.
     * @return The singleton instance of SatelliteSessionController.
     */
    public static SatelliteSessionController make(
            @NonNull Context context, @NonNull Looper looper, boolean isSatelliteSupported) {
        if (sInstance == null) {
            sInstance = new SatelliteSessionController(context, looper, isSatelliteSupported,
                    SatelliteModemInterface.getInstance(),
                    getSatelliteStayAtListeningFromSendingMillis(),
                    getSatelliteStayAtListeningFromReceivingMillis());
        } else {
            if (isSatelliteSupported != sInstance.mIsSatelliteSupported) {
                Rlog.e(TAG, "New satellite support state " + isSatelliteSupported
                        + " is different from existing state " + sInstance.mIsSatelliteSupported
                        + ". Ignore the new state.");
            }
        }
        return sInstance;
    }

    /**
     * Create a SatelliteSessionController to manage satellite session.
     *
     * @param context The Context for the SatelliteSessionController.
     * @param looper The looper associated with the handler of this class.
     * @param isSatelliteSupported Whether satellite is supported on the device.
     * @param satelliteModemInterface The singleton of SatelliteModemInterface.
     * @param satelliteStayAtListeningFromSendingMillis The duration to stay at listening mode when
     *                                                  transitioning from sending mode.
     * @param satelliteStayAtListeningFromReceivingMillis The duration to stay at listening mode
     *                                                    when transitioning from receiving mode.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected SatelliteSessionController(@NonNull Context context, @NonNull Looper looper,
            boolean isSatelliteSupported,
            @NonNull SatelliteModemInterface satelliteModemInterface,
            long satelliteStayAtListeningFromSendingMillis,
            long satelliteStayAtListeningFromReceivingMillis) {
        super(TAG, looper);

        mContext = context;
        mSatelliteModemInterface = satelliteModemInterface;
        mSatelliteStayAtListeningFromSendingMillis = satelliteStayAtListeningFromSendingMillis;
        mSatelliteStayAtListeningFromReceivingMillis = satelliteStayAtListeningFromReceivingMillis;
        mListeners = new ConcurrentHashMap<>();
        mIsSendingTriggeredDuringTransferringState = new AtomicBoolean(false);
        mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_UNKNOWN;
        mIsSatelliteSupported = isSatelliteSupported;

        addState(mUnavailableState);
        addState(mPowerOffState);
        addState(mIdleState);
        addState(mTransferringState);
        addState(mListeningState, mTransferringState);
        setInitialState(isSatelliteSupported);
        start();
    }

    /**
     * {@link DatagramController} uses this function to notify {@link SatelliteSessionController}
     * that its datagram transfer state has changed.
     *
     * @param sendState The current datagram send state of {@link DatagramController}.
     * @param receiveState The current datagram receive state of {@link DatagramController}.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onDatagramTransferStateChanged(
            @SatelliteManager.SatelliteDatagramTransferState int sendState,
            @SatelliteManager.SatelliteDatagramTransferState int receiveState) {
        sendMessage(EVENT_DATAGRAM_TRANSFER_STATE_CHANGED,
                new DatagramTransferState(sendState, receiveState));
        if (sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING) {
            mIsSendingTriggeredDuringTransferringState.set(true);
        }
    }

    /**
     * {@link SatelliteController} uses this function to notify {@link SatelliteSessionController}
     * that the satellite enabled state has changed.
     *
     * @param enabled {@code true} means enabled and {@code false} means disabled.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void onSatelliteEnabledStateChanged(boolean enabled) {
        sendMessage(EVENT_SATELLITE_ENABLED_STATE_CHANGED, enabled);
    }

    /**
     * Registers for modem state changed from satellite modem.
     *
     * @param callback The callback to handle the satellite modem state changed event.
     */
    public void registerForSatelliteModemStateChanged(@NonNull ISatelliteStateCallback callback) {
        try {
            callback.onSatelliteModemStateChanged(mCurrentState);
            mListeners.put(callback.asBinder(), callback);
        } catch (RemoteException ex) {
            loge("registerForSatelliteModemStateChanged: Got RemoteException ex=" + ex);
        }
    }

    /**
     * Unregisters for modem state changed from satellite modem.
     * If callback was not registered before, the request will be ignored.
     *
     * @param callback The callback that was passed to
     *                 {@link #registerForSatelliteModemStateChanged(ISatelliteStateCallback)}.
     */
    public void unregisterForSatelliteModemStateChanged(@NonNull ISatelliteStateCallback callback) {
        mListeners.remove(callback.asBinder());
    }

    /**
     * This API can be used by only CTS to update the timeout duration in milliseconds that
     * satellite should stay at listening mode to wait for the next incoming page before disabling
     * listening mode.
     *
     * @param timeoutMillis The timeout duration in millisecond.
     * @return {@code true} if the timeout duration is set successfully, {@code false} otherwise.
     */
    boolean setSatelliteListeningTimeoutDuration(long timeoutMillis) {
        if (!isMockModemAllowed()) {
            loge("Updating listening timeout duration is not allowed");
            return false;
        }

        logd("setSatelliteListeningTimeoutDuration: timeoutMillis=" + timeoutMillis);
        if (timeoutMillis == 0) {
            mSatelliteStayAtListeningFromSendingMillis =
                    getSatelliteStayAtListeningFromSendingMillis();
            mSatelliteStayAtListeningFromReceivingMillis =
                    getSatelliteStayAtListeningFromReceivingMillis();
        } else {
            mSatelliteStayAtListeningFromSendingMillis = timeoutMillis;
            mSatelliteStayAtListeningFromReceivingMillis = timeoutMillis;
        }

        return true;
    }

    private static class DatagramTransferState {
        @SatelliteManager.SatelliteDatagramTransferState public int sendState;
        @SatelliteManager.SatelliteDatagramTransferState public int receiveState;

        DatagramTransferState(@SatelliteManager.SatelliteDatagramTransferState int sendState,
                @SatelliteManager.SatelliteDatagramTransferState int receiveState) {
            this.sendState = sendState;
            this.receiveState = receiveState;
        }
    }

    private class UnavailableState extends State {
        @Override
        public void enter() {
            if (DBG) logd("Entering UnavailableState");
            mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_UNAVAILABLE;
        }

        @Override
        public boolean processMessage(Message msg) {
            loge("UnavailableState: receive msg " + getWhatToString(msg.what) + " unexpectedly");
            return HANDLED;
        }
    }

    private class PowerOffState extends State {
        @Override
        public void enter() {
            if (DBG) logd("Entering PowerOffState");
            mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_OFF;
            mIsSendingTriggeredDuringTransferringState.set(false);
            notifyStateChangedEvent(SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) log("PowerOffState: processing " + getWhatToString(msg.what));
            switch (msg.what) {
                case EVENT_SATELLITE_ENABLED_STATE_CHANGED:
                    handleSatelliteEnabledStateChanged((boolean) msg.obj);
                    break;
            }
            // Ignore all unexpected events.
            return HANDLED;
        }

        private void handleSatelliteEnabledStateChanged(boolean on) {
            if (on) {
                transitionTo(mIdleState);
            }
        }
    }

    private class IdleState extends State {
        @Override
        public void enter() {
            if (DBG) logd("Entering IdleState");
            mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_IDLE;
            mIsSendingTriggeredDuringTransferringState.set(false);
            notifyStateChangedEvent(SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
            //Disable Cellular Modem
            mSatelliteModemInterface.enableCellularModemWhileSatelliteModeIsOn(false, null);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) log("IdleState: processing " + getWhatToString(msg.what));
            switch (msg.what) {
                case EVENT_DATAGRAM_TRANSFER_STATE_CHANGED:
                    handleEventDatagramTransferStateChanged((DatagramTransferState) msg.obj);
                    break;
                case EVENT_SATELLITE_ENABLED_STATE_CHANGED:
                    handleSatelliteEnabledStateChanged(!(boolean) msg.obj, "IdleState");
                    break;
            }
            // Ignore all unexpected events.
            return HANDLED;
        }

        private void handleEventDatagramTransferStateChanged(
                @NonNull DatagramTransferState datagramTransferState) {
            if ((datagramTransferState.sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING)
                    || (datagramTransferState.receiveState
                    == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING)) {
                transitionTo(mTransferringState);
            }
        }

        @Override
        public void exit() {
            if (DBG) logd("Exiting IdleState");
            //Enable Cellular Modem
            mSatelliteModemInterface.enableCellularModemWhileSatelliteModeIsOn(true, null);
        }
    }

    private class TransferringState extends State {
        @Override
        public void enter() {
            if (DBG) logd("Entering TransferringState");
            mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING;
            notifyStateChangedEvent(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) log("TransferringState: processing " + getWhatToString(msg.what));
            switch (msg.what) {
                case EVENT_DATAGRAM_TRANSFER_STATE_CHANGED:
                    handleEventDatagramTransferStateChanged((DatagramTransferState) msg.obj);
                    return HANDLED;
                case EVENT_SATELLITE_ENABLED_STATE_CHANGED:
                    handleSatelliteEnabledStateChanged(!(boolean) msg.obj, "TransferringState");
                    break;
            }
            // Ignore all unexpected events.
            return HANDLED;
        }

        private void handleEventDatagramTransferStateChanged(
                @NonNull DatagramTransferState datagramTransferState) {
            if (isSending(datagramTransferState.sendState) || isReceiving(
                    datagramTransferState.receiveState)) {
                // Stay at transferring state.
            } else if ((datagramTransferState.sendState
                    == SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED)
                    || (datagramTransferState.receiveState
                    == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED)) {
                transitionTo(mIdleState);
            } else {
                transitionTo(mListeningState);
            }
        }
    }

    private class ListeningState extends State {
        @Override
        public void enter() {
            if (DBG) logd("Entering ListeningState");

            mCurrentState = SatelliteManager.SATELLITE_MODEM_STATE_LISTENING;
            long timeoutMillis = updateListeningMode(true);
            sendMessageDelayed(EVENT_LISTENING_TIMER_TIMEOUT, timeoutMillis);
            mIsSendingTriggeredDuringTransferringState.set(false);
            notifyStateChangedEvent(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        }

        @Override
        public void exit() {
            removeMessages(EVENT_LISTENING_TIMER_TIMEOUT);
            updateListeningMode(false);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (DBG) log("ListeningState: processing " + getWhatToString(msg.what));
            switch (msg.what) {
                case EVENT_LISTENING_TIMER_TIMEOUT:
                    transitionTo(mIdleState);
                    break;
                case EVENT_DATAGRAM_TRANSFER_STATE_CHANGED:
                    handleEventDatagramTransferStateChanged((DatagramTransferState) msg.obj);
                    break;
                case EVENT_SATELLITE_ENABLED_STATE_CHANGED:
                    handleSatelliteEnabledStateChanged(!(boolean) msg.obj, "ListeningState");
                    break;
            }
            // Ignore all unexpected events.
            return HANDLED;
        }

        private long updateListeningMode(boolean enabled) {
            long timeoutMillis;
            if (mIsSendingTriggeredDuringTransferringState.get()) {
                timeoutMillis = mSatelliteStayAtListeningFromSendingMillis;
            } else {
                timeoutMillis = mSatelliteStayAtListeningFromReceivingMillis;
            }
            mSatelliteModemInterface.requestSatelliteListeningEnabled(
                    enabled, (int) timeoutMillis, null);
            return timeoutMillis;
        }

        private void handleEventDatagramTransferStateChanged(
                @NonNull DatagramTransferState datagramTransferState) {
            if (datagramTransferState.sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING
                    || datagramTransferState.receiveState
                    == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING) {
                transitionTo(mTransferringState);
            }
        }
    }

    /**
     * @return the string for msg.what
     */
    @Override
    protected String getWhatToString(int what) {
        String whatString;
        switch (what) {
            case EVENT_DATAGRAM_TRANSFER_STATE_CHANGED:
                whatString = "EVENT_DATAGRAM_TRANSFER_STATE_CHANGED";
                break;
            case EVENT_LISTENING_TIMER_TIMEOUT:
                whatString = "EVENT_LISTENING_TIMER_TIMEOUT";
                break;
            case EVENT_SATELLITE_ENABLED_STATE_CHANGED:
                whatString = "EVENT_SATELLITE_ENABLED_STATE_CHANGED";
                break;
            default:
                whatString = "UNKNOWN EVENT " + what;
        }
        return whatString;
    }

    private void setInitialState(boolean isSatelliteSupported) {
        if (isSatelliteSupported) {
            setInitialState(mPowerOffState);
        } else {
            setInitialState(mUnavailableState);
        }
    }

    private void notifyStateChangedEvent(@SatelliteManager.SatelliteModemState int state) {
        List<ISatelliteStateCallback> toBeRemoved = new ArrayList<>();
        mListeners.values().forEach(listener -> {
            try {
                listener.onSatelliteModemStateChanged(state);
            } catch (RemoteException e) {
                logd("notifyStateChangedEvent RemoteException: " + e);
                toBeRemoved.add(listener);
            }
        });

        toBeRemoved.forEach(listener -> {
            mListeners.remove(listener.asBinder());
        });
    }

    private void handleSatelliteEnabledStateChanged(boolean off, String caller) {
        if (off) {
            transitionTo(mPowerOffState);
        } else {
            loge(caller + ": Unexpected satellite radio powered-on state changed event");
        }
    }

    private boolean isSending(@SatelliteManager.SatelliteDatagramTransferState int sendState) {
        return (sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING
                || sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS);
    }

    private boolean isReceiving(@SatelliteManager.SatelliteDatagramTransferState int receiveState) {
        return (receiveState == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING
                || receiveState == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS
                || receiveState == SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_NONE);
    }

    private boolean isMockModemAllowed() {
        return (DEBUG || SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false));
    }

    private static long getSatelliteStayAtListeningFromSendingMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                SATELLITE_STAY_AT_LISTENING_FROM_SENDING_MILLIS,
                DEFAULT_SATELLITE_STAY_AT_LISTENING_FROM_SENDING_MILLIS);
    }

    private static long getSatelliteStayAtListeningFromReceivingMillis() {
        return DeviceConfig.getLong(DeviceConfig.NAMESPACE_TELEPHONY,
                SATELLITE_STAY_AT_LISTENING_FROM_RECEIVING_MILLIS,
                DEFAULT_SATELLITE_STAY_AT_LISTENING_FROM_RECEIVING_MILLIS);
    }
}
