/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;

/**
 * DataStallRecoveryManager monitors the network validation result from connectivity service and
 * takes actions to recovery data network.
 */
public class DataStallRecoveryManager extends Handler {

    /** Recovery actions taken in case of data stall */
    @IntDef(
        value = {
            RECOVERY_ACTION_GET_DATA_CALL_LIST,
            RECOVERY_ACTION_CLEANUP,
            RECOVERY_ACTION_RADIO_RESTART,
            RECOVERY_ACTION_RESET_MODEM
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RecoveryAction {};
    private static final int RECOVERY_ACTION_GET_DATA_CALL_LIST      = 0;
    private static final int RECOVERY_ACTION_CLEANUP                 = 1;
    private static final int RECOVERY_ACTION_RADIO_RESTART           = 2;
    private static final int RECOVERY_ACTION_RESET_MODEM             = 3;

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    private final @NonNull Phone mPhone;
    private final @NonNull String mLogTag;
    private final @NonNull LocalLog mLocalLog = new LocalLog(128);

    /** Data network controller */
    private final @NonNull DataNetworkController mDataNetworkController;

    /** Data config manager */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Cellular data service */
    private final @NonNull DataServiceManager mWwanDataServiceManager;

    /** Resolver */
    private ContentResolver mResolver;

    private Network mNetwork;
    private NetworkCallback mNetworkCallback;

    /** Connectivity Manager */
    private ConnectivityManager mConnectivityManager;

    /** Telephony Manager */
    private TelephonyManager mTelephonyManager;

    /** Listening the callback from TelephonyCallback. */
    private TelephonyStateListener mTelephonyStateListener;

    private @NonNull DataStallRecoveryManagerCallback mDataStallRecoveryManagerCallback;

    /**
     * The data stall recovery manager callback. Note this is only used for passing information
     * internally in the data stack, should not be used externally.
     */
    public abstract static class DataStallRecoveryManagerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataStallRecoveryManagerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when data stall occurs and needed to tear down / setup a new data network for
         * internet.
         */
        public abstract void onDataStallReestablishInternet();
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataNetworkController Data network controller
     * @param dataServiceManager The WWAN data service manager.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param callback Callback to notify data network controller for data stall events.
     */
    public DataStallRecoveryManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController,
            @NonNull DataServiceManager dataServiceManager, @NonNull Looper looper,
            @NonNull DataStallRecoveryManagerCallback callback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DSTMTR-" + mPhone.getPhoneId();
        mDataNetworkController = dataNetworkController;
        mWwanDataServiceManager = dataServiceManager;
        mDataConfigManager = mDataNetworkController.getDataConfigManager();
        mDataStallRecoveryManagerCallback = callback;
        mResolver = mPhone.getContext().getContentResolver();
        mTelephonyManager = mPhone.getContext().getSystemService(TelephonyManager.class);
        mConnectivityManager = mPhone.getContext().getSystemService(ConnectivityManager.class);
        mTelephonyStateListener = new TelephonyStateListener();

        registerAllEvents();
    }

    /** Register for all events that data stall monitor is interested. */
    private void registerAllEvents() {
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mDataNetworkController.registerDataNetworkControllerCallback(
                this::post,
                new DataNetworkControllerCallback() {
                    @Override
                    public void onInternetDataNetworkValidationStatusChanged(
                            @ValidationStatus int validationStatus) {
                        onInternetValidationStatusChanged(validationStatus);
                    }

                    @Override
                    public void onInternetDataNetworkConnected() {
                        // onInternetDataNetworkConnected();
                    }

                    @Override
                    public void onInternetDataNetworkDisconnected() {
                        // onInternetDataNetworkDisconnected();
                    }
                }, false);
        mTelephonyManager.registerTelephonyCallback(
                    new HandlerExecutor(this), mTelephonyStateListener);
    }

    /** Telephony State Listener. */
    private class TelephonyStateListener extends TelephonyCallback implements
                TelephonyCallback.RadioPowerStateListener,
                TelephonyCallback.PreciseDataConnectionStateListener,
                TelephonyCallback.SignalStrengthsListener {

        @Override
        public void onPreciseDataConnectionStateChanged(
            PreciseDataConnectionState connectionState) {
            // TODO: (b/178670629): Add the logic for PreciseDataConnectionStateChanged.
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            // TODO: (b/178670629): Add the logic for Signal Strength Changed.
        }

        @Override
        public void onRadioPowerStateChanged(int state) {
            // TODO: (b/178670629): Listen the Radio Power State Changed.
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            default:
                loge("Unexpected message " + msg);
                break;
        }
    }

    /** Called when data config was updated. */
    private void onDataConfigUpdated() {
        // TODO: (b/178670629): Get the new config from DataConfigManager.
    }

    /**
     * Called when internet validation status changed.
     *
     * @param validationStatus Validation status.
     */
    private void onInternetValidationStatusChanged(@ValidationStatus int validationStatus) {
        // TODO: (b/178670629): Add the logic when Validation Status Changed.

    }

    /** Get recovery action from settings. */
    @RecoveryAction
    private int getRecoveryAction() {
        @RecoveryAction int action = Settings.System.getInt(mResolver,
                "radio.data.stall.recovery.action", RECOVERY_ACTION_GET_DATA_CALL_LIST);
        log("getRecoveryAction: " + action);
        return action;
    }

    /**
     * Put recovery action into settings.
     *
     * @param action The next recovery action.
     */
    private void putRecoveryAction(@RecoveryAction int action) {
        Settings.System.putInt(mResolver, "radio.data.stall.recovery.action", action);
        log("putRecoveryAction: " + action);
    }

    /** Check if recovery already started. */
    private boolean isRecoveryAlreadyStarted() {
        return getRecoveryAction() != RECOVERY_ACTION_GET_DATA_CALL_LIST;
    }

    /** Get duration between recovery from DataStallRecoveryRule. */
    private long getMinDurationBetweenRecovery() {
        // TODO: (b/178670629): Get the duration from DataConfigManager
        return 0;
    }

    /**
     * Broadcast intent when data stall occurred.
     *
     * @param recoveryAction Send the data stall detected intent with RecoveryAction info.
     */
    private void broadcastDataStallDetected(@RecoveryAction int recoveryAction) {
        log("broadcastDataStallDetected recoveryAction: " + recoveryAction);
        Intent intent = new Intent(TelephonyManager.ACTION_DATA_STALL_DETECTED);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
        intent.putExtra(TelephonyManager.EXTRA_RECOVERY_ACTION, recoveryAction);
        mPhone.getContext().sendBroadcast(intent);
    }

    /** Recovery Action: RECOVERY_ACTION_GET_DATA_CALL_LIST */
    private void getDataCallList(){
        if (mPhone == null) {
            loge("getDataCallList: mPhone is null");
            return;
        }
        log("getDataCallList: request data call list");
        mWwanDataServiceManager.requestDataCallList(null);
    }

    /** Recovery Action: RECOVERY_ACTION_CLEANUP */
    private void cleanUpDataCall(){
        if (mPhone == null) {
            loge("cleanUpDataCall: getDataCallList: mPhone is null");
            return;
        }
        log("cleanUpDataCall: notify clean up data call");
        mDataStallRecoveryManagerCallback.invokeFromExecutor(
                () -> mDataStallRecoveryManagerCallback.onDataStallReestablishInternet());
    }

    /** Recovery Action: RECOVERY_ACTION_RADIO_RESTART */
    private void restartRadio(){
        if(mPhone == null) {
            loge("restartRadio: No mPhone found!");
            return;
        }
        log("restartRadio: Restart radio" );
        mPhone.getServiceStateTracker().powerOffRadioSafely();
    }

    /** Recovery Action: RECOVERY_ACTION_RESET_MODEM */
    private void modemReset(){
        AsyncTask.execute(() -> {
            try{
                log("modemReset: NV reload");
                mTelephonyManager.rebootRadio();
            }
            catch(Exception e){
                loge("modemReset: Exception: " + e.toString());
            }
        });
    }

    /** The data stall recovery config. */
    public static final class DataStallRecoveryConfig {
        DataStallRecoveryConfig(String stringConfig){
            // TODO: (b/178670629): Parsing the config string.
        }
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataStallRecoveryManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataStallRecoveryManager.class.getSimpleName() + "-" + mPhone.getPhoneId()
                + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
