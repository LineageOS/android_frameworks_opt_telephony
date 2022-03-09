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
import android.annotation.ElapsedRealtimeLong;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.content.Intent;
import android.net.NetworkAgent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.Annotation.RadioPowerState;
import android.telephony.Annotation.ValidationStatus;
import android.telephony.CellSignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import com.android.internal.telephony.metrics.DataStallRecoveryStats;
import com.android.internal.telephony.metrics.TelephonyMetrics;
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
    private static final boolean VDBG = false;

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

    /* DataStallRecoveryManager queries RIL for link properties (IP addresses, DNS server addresses
     * etc) using RIL_REQUEST_GET_DATA_CALL_LIST.  This will help in cases where the data stall
     * occurred because of a link property changed but not notified to connectivity service.
     */
    private static final int RECOVERY_ACTION_GET_DATA_CALL_LIST = 0;

    /* DataStallRecoveryManager will request DataNetworkController to reestablish internet using
     * RIL_REQUEST_DEACTIVATE_DATA_CALL and sets up the data call back using SETUP_DATA_CALL.
     * It will help to reestablish the channel between RIL and modem.
     */
    private static final int RECOVERY_ACTION_CLEANUP = 1;

    /* DataStallRecoveryManager will request ServiceStateTracker to send RIL_REQUEST_RADIO_POWER
     * to restart radio. It will restart the radio and re-attch to the network.
     */
    private static final int RECOVERY_ACTION_RADIO_RESTART = 2;

    /* DataStallRecoveryManager will request to reboot modem using NV_RESET_CONFIG. It will recover
     * if there is a problem in modem side.
     */
    private static final int RECOVERY_ACTION_RESET_MODEM = 3;

    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;


    /** Event for triggering recovery action. */
    private static final int EVENT_DO_RECOVERY = 2;

    /** Event for mobile data setting changed. */
    private static final int EVENT_MOBILE_DATA_SETTINGS_CHANGED = 3;

    /** Event for radio state changed. */
    private static final int EVENT_RADIO_STATE_CHANGED = 4;

    private final @NonNull Phone mPhone;
    private final @NonNull String mLogTag;
    private final @NonNull LocalLog mLocalLog = new LocalLog(128);

    /** Data network controller */
    private final @NonNull DataNetworkController mDataNetworkController;

    /** Data config manager */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Cellular data service */
    private final @NonNull DataServiceManager mWwanDataServiceManager;

    /** The data stall recovery action. */
    private @RecoveryAction int mRecovryAction;
    /** The elapsed real time of last recovery attempted */
    private @ElapsedRealtimeLong long mTimeLastRecoveryStartMs;
    /** Whether current network is good or not */
    private boolean mIsValidNetwork;
    /** Whether data stall happened or not. */
    private boolean mDataStalled;
    /** Whether the result of last action(RADIO_RESTART) reported. */
    private boolean mLastActionReported;
    /** The real time for data stall start. */
    private @ElapsedRealtimeLong long mDataStallStartMs;
    /** Last data stall recovery action. */
    private @RecoveryAction int mLastAction;
    /** Last radio power state. */
    private @RadioPowerState int mRadioPowerState;
    /** Whether the NetworkCheckTimer start. */
    private boolean mNetworkCheckTimerStarted = false;

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
     * @param looper The looper to be used by the handler. Currently the handler thread is the phone
     *     process's main thread.
     * @param callback Callback to notify data network controller for data stall events.
     */
    public DataStallRecoveryManager(
            @NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController,
            @NonNull DataServiceManager dataServiceManager,
            @NonNull Looper looper,
            @NonNull DataStallRecoveryManagerCallback callback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DSRM-" + mPhone.getPhoneId();
        mDataNetworkController = dataNetworkController;
        mWwanDataServiceManager = dataServiceManager;
        mDataConfigManager = mDataNetworkController.getDataConfigManager();
        mDataStallRecoveryManagerCallback = callback;
        mRadioPowerState = mPhone.getRadioPowerState();

        registerAllEvents();
    }

    /** Register for all events that data stall monitor is interested. */
    private void registerAllEvents() {
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mDataNetworkController.registerDataNetworkControllerCallback(
                new DataNetworkControllerCallback(this::post) {
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
                });
        mPhone.mCi.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);
    }

    @Override
    public void handleMessage(Message msg) {
        logv("handleMessage = " + msg);
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_DO_RECOVERY:
                doRecovery();
                break;
            case EVENT_MOBILE_DATA_SETTINGS_CHANGED:
                onMobileDataSettingsChanged();
                break;
            case EVENT_RADIO_STATE_CHANGED:
                mRadioPowerState = mPhone.getRadioPowerState();
                break;
            default:
                loge("Unexpected message = " + msg);
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
    private void onInternetValidationStatusChanged(@ValidationStatus int status) {
        logl("onInternetValidationStatusChanged: " + DataUtils.validationStatusToString(status));
        final boolean isValid = status == NetworkAgent.VALIDATION_STATUS_VALID;
        setNetworkValidationState(isValid);
        if (isValid) {
            mIsValidNetwork = true;
            cancelNetworkCheckTimer();
            resetAction();
        } else {
            if (mIsValidNetwork || isRecoveryAlreadyStarted()) {
                mIsValidNetwork = false;
                if (isRecoveryNeeded()) {
                    log("trigger data stall recovery");
                    mTimeLastRecoveryStartMs = SystemClock.elapsedRealtime();
                    sendMessage(obtainMessage(EVENT_DO_RECOVERY));
                }
            }
        }
    }

    /** Called when mobile data setiings changed. */
    private void onMobileDataSettingsChanged() {
        logv("onMobileDataSettingsChanged");
        // TODO: (b/178670629): Get mobile data settings from DataSettingsManager.
    }

    /** Reset the action to initial step. */
    private void resetAction() {
        mTimeLastRecoveryStartMs = 0;
        setRecoveryAction(RECOVERY_ACTION_GET_DATA_CALL_LIST);
    }

    /**
     * Get recovery action from settings.
     *
     * @return recovery action
     */
    @VisibleForTesting
    @RecoveryAction
    public int getRecoveryAction() {
        log("getRecoveryAction: " + recoveryActionToString(mRecovryAction));
        return mRecovryAction;
    }

    /**
     * Put recovery action into settings.
     *
     * @param action The next recovery action.
     */
    @VisibleForTesting
    public void setRecoveryAction(@RecoveryAction int action) {
        mRecovryAction = action;
        log("setRecoveryAction: " + recoveryActionToString(mRecovryAction));
    }

    /**
     * Check if recovery already started.
     *
     * @return {@code true} if recovery already started, {@code false} recovery not started.
     */
    private boolean isRecoveryAlreadyStarted() {
        return getRecoveryAction() != RECOVERY_ACTION_GET_DATA_CALL_LIST;
    }

    /**
     * Get elapsed time since last recovery.
     *
     * @return the time since last recovery started.
     */
    private long getElapsedTimeSinceRecoveryMs() {
        return (SystemClock.elapsedRealtime() - mTimeLastRecoveryStartMs);
    }

    /**
     * Get duration between recovery from DataStallRecoveryConfig.
     *
     * @return the time in milliseconds between recovery action.
     */
    private long getMinDurationBetweenRecovery() {
        // TODO: (b/178670629): Get the duration from DataConfigManager
        return 3 * 60 * 1000;
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
    private void getDataCallList() {
        log("getDataCallList: request data call list");
        mWwanDataServiceManager.requestDataCallList(null);
    }

    /** Recovery Action: RECOVERY_ACTION_CLEANUP */
    private void cleanUpDataNetwork() {
        log("cleanUpDataNetwork: notify clean up data network");
        mDataStallRecoveryManagerCallback.invokeFromExecutor(
                () -> mDataStallRecoveryManagerCallback.onDataStallReestablishInternet());
    }

    /** Recovery Action: RECOVERY_ACTION_RADIO_RESTART */
    private void powerOffRadio() {
        log("powerOffRadio: Restart radio");
        mPhone.getServiceStateTracker().powerOffRadioSafely();
    }

    /** Recovery Action: RECOVERY_ACTION_RESET_MODEM */
    private void rebootModem() {
        log("rebootModem: reboot modem");
        mPhone.rebootModem(null);
    }

    /** Initialize the network check timer. */
    private void startNetworkCheckTimer() {
        log("startNetworkCheckTimer()");
        if (!mNetworkCheckTimerStarted) {
            mNetworkCheckTimerStarted = true;
            sendMessageDelayed(obtainMessage(EVENT_DO_RECOVERY), getMinDurationBetweenRecovery());
        }
    }

    /** Cancel the network check timer. */
    private void cancelNetworkCheckTimer() {
        log("cancelNetworkCheckTimer()");
        if (mNetworkCheckTimerStarted) {
            mNetworkCheckTimerStarted = false;
            removeMessages(EVENT_DO_RECOVERY);
        }
    }

    /**
     * Check the conditions if we need to do recovery action.
     *
     * @return {@code true} if need to do recovery action, {@code false} no need to do recovery
     *     action.
     */
    private boolean isRecoveryNeeded() {
        logv("enter: isRecoveryNeeded()");
        // To avoid back to back recovery, wait for a grace period
        if (getElapsedTimeSinceRecoveryMs() < getMinDurationBetweenRecovery()) {
            log("skip back to back data stall recovery");
            return false;
        }

        // Skip recovery if it can cause a call to drop
        if (mPhone.getState() != PhoneConstants.State.IDLE
                && getRecoveryAction() > RECOVERY_ACTION_CLEANUP) {
            log("skip data stall recovery as there is an active call");
            return false;
        }

        // Skip when poor signal strength
        if (mPhone.getSignalStrength().getLevel() <= CellSignalStrength.SIGNAL_STRENGTH_POOR) {
            log("skip data stall recovery as in poor signal condition");
            resetAction();
            return false;
        }

        // TODO: (b/178670629): check the customized carrier config to skip the recovery action

        return true;
    }

    /**
     * Set the validation status into metrics.
     *
     * @param isValid true for validation passed & false for validation failed
     */
    private void setNetworkValidationState(boolean isValid) {
        // Validation status is true and was not data stall.
        if (isValid && !mDataStalled) {
            return;
        }

        if (!mDataStalled) {
            mDataStalled = true;
            mDataStallStartMs = SystemClock.elapsedRealtime();
            logl("data stall: start time = " + DataUtils.elapsedTimeToString(mDataStallStartMs));
            return;
        }

        if (!mLastActionReported) {
            int timeDuration = (int) (SystemClock.elapsedRealtime() - mDataStallStartMs);
            logl(
                    "data stall: lastaction = "
                            + recoveryActionToString(mLastAction)
                            + ", isRecovered = "
                            + isValid
                            + ", TimeDuration = "
                            + timeDuration);
            DataStallRecoveryStats.onDataStallEvent(mLastAction, mPhone, isValid, timeDuration);
            mLastActionReported = true;
        }

        if (isValid) {
            mLastActionReported = false;
            mDataStalled = false;
        }
    }

    /** Perform a series of data stall recovery actions. */
    private void doRecovery() {
        @RecoveryAction final int recoveryAction = getRecoveryAction();
        final int signalStrength = mPhone.getSignalStrength().getLevel();
        TelephonyMetrics.getInstance()
                .writeSignalStrengthEvent(mPhone.getPhoneId(), signalStrength);
        TelephonyMetrics.getInstance().writeDataStallEvent(mPhone.getPhoneId(), recoveryAction);
        mLastAction = recoveryAction;
        mLastActionReported = false;
        broadcastDataStallDetected(recoveryAction);
        mNetworkCheckTimerStarted = false;

        switch (recoveryAction) {
            case RECOVERY_ACTION_GET_DATA_CALL_LIST:
                logl("doRecovery(): get data call list");
                getDataCallList();
                setRecoveryAction(RECOVERY_ACTION_CLEANUP);
                break;
            case RECOVERY_ACTION_CLEANUP:
                logl("doRecovery(): cleanup all connections");
                cleanUpDataNetwork();
                setRecoveryAction(RECOVERY_ACTION_RADIO_RESTART);
                break;
            case RECOVERY_ACTION_RADIO_RESTART:
                logl("doRecovery(): restarting radio");
                setRecoveryAction(RECOVERY_ACTION_RESET_MODEM);
                powerOffRadio();
                break;
            case RECOVERY_ACTION_RESET_MODEM:
                logl("doRecovery(): modem reset");
                rebootModem();
                resetAction();
                break;
            default:
                throw new RuntimeException(
                        "doRecovery: Invalid recoveryAction = "
                                + recoveryActionToString(recoveryAction));
        }

        startNetworkCheckTimer();
    }

    /**
     * Convert RecoveryAction to string
     *
     * @param action The recovery action
     * @return The recovery action in string format.
     */
    private static @NonNull String recoveryActionToString(@RecoveryAction int action) {
        switch (action) {
            case RECOVERY_ACTION_GET_DATA_CALL_LIST:
                return "RECOVERY_ACTION_GET_DATA_CALL_LIST";
            case RECOVERY_ACTION_CLEANUP:
                return "RECOVERY_ACTION_CLEANUP";
            case RECOVERY_ACTION_RADIO_RESTART:
                return "RECOVERY_ACTION_RADIO_RESTART";
            case RECOVERY_ACTION_RESET_MODEM:
                return "RECOVERY_ACTION_RESET_MODEM";
            default:
                return "Unknown(" + action + ")";
        }
    }

    /** The data stall recovery config. */
    public static final class DataStallRecoveryConfig {
        DataStallRecoveryConfig(String stringConfig) {
            // TODO: (b/178670629): Parsing the config string.
        }
    }

    /**
     * Log debug messages.
     *
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log verbose messages.
     *
     * @param s debug messages.
     */
    private void logv(@NonNull String s) {
        if (VDBG) Rlog.v(mLogTag, s);
    }

    /**
     * Log error messages.
     *
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Log debug messages and also log into the local log.
     *
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
        pw.println(
                DataStallRecoveryManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();

        pw.println("mIsValidNetwork=" + mIsValidNetwork);
        pw.println("mDataStalled=" + mDataStalled);
        pw.println("mDataStallStartMs=" + mDataStallStartMs);
        pw.println("mRadioPowerState=" + mRadioPowerState);
        pw.println("mLastActionReported=" + mLastActionReported);
        pw.println("mTimeLastRecoveryStartMs=" + mTimeLastRecoveryStartMs);
        pw.println("getRecoveryAction()=" + recoveryActionToString(getRecoveryAction()));
        pw.println("");

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
