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

package com.android.internal.telephony.emergency;

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_CALLBACK;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_NONE;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.sysprop.TelephonyProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.EmergencyRegResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.data.PhoneSwitcher;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Tracks the emergency call state and notifies listeners of changes to the emergency mode.
 */
public class EmergencyStateTracker {

    private static final String TAG = "EmergencyStateTracker";

    /**
     * Timeout before we continue with the emergency call without waiting for DDS switch response
     * from the modem.
     */
    private static final int DEFAULT_DATA_SWITCH_TIMEOUT_MS = 1000;
    /** Default value for if Emergency Callback Mode is supported. */
    private static final boolean DEFAULT_EMERGENCY_CALLBACK_MODE_SUPPORTED = true;
    /** Default Emergency Callback Mode exit timeout value. */
    private static final long DEFAULT_ECM_EXIT_TIMEOUT_MS = 300000;

    private static EmergencyStateTracker INSTANCE = null;

    private final long mEcmExitTimeoutMs;
    private final Context mContext;
    private final CarrierConfigManager mConfigManager;
    private final Handler mHandler;
    private final boolean mIsSuplDdsSwitchRequiredForEmergencyCall;
    /** Tracks emergency calls by callId that have reached {@link Call.State#ACTIVE}.*/
    private final Set<String> mActiveEmergencyCalls = new HashSet<>();
    private final PowerManager.WakeLock mWakeLock;

    @EmergencyConstants.EmergencyMode
    private int mEmergencyMode = MODE_EMERGENCY_NONE;
    private Phone mPhone;
    private RadioOnHelper mRadioOnHelper;
    private CompletableFuture<Integer> mOnCompleted = null;
    // Domain of the active emergency call. Assuming here that there will only be one domain active.
    private int mEmergencyCallDomain = -1;
    private EmergencyRegResult mLastEmergencyRegResult;
    private boolean mIsInEmergencyCall = false;
    private boolean mIsTestEmergencyNumber = false;
    private boolean mIsPhoneInEcmState = false;
    private Runnable mOnEcmExitCompleteRunnable = null;

    // A runnable which is used to automatically exit from Ecm after a period of time.
    private final Runnable mExitEcmRunnable = this::exitEmergencyCallbackMode;

    /**
     * Listens for Emergency Callback Mode state change intents
     */
    private final BroadcastReceiver mEcmExitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(
                    TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED)) {

                boolean isInEcm = intent.getBooleanExtra(
                        TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false);
                Rlog.d(TAG, "Received ACTION_EMERGENCY_CALLBACK_MODE_CHANGED isInEcm = " + isInEcm);

                // If we exit ECM mode, notify all connections.
                if (!isInEcm) {
                    exitEmergencyCallbackMode();
                }
            }
        }
    };

    /** PhoneFactory Dependencies for testing. */
    @VisibleForTesting
    public interface PhoneFactoryProxy {
        Phone[] getPhones();
    }

    private PhoneFactoryProxy mPhoneFactoryProxy = PhoneFactory::getPhones;

    /** PhoneSwitcher dependencies for testing. */
    @VisibleForTesting
    public interface PhoneSwitcherProxy {

        PhoneSwitcher getPhoneSwitcher();
    }

    private PhoneSwitcherProxy mPhoneSwitcherProxy = PhoneSwitcher::getInstance;

    /**
     * TelephonyManager dependencies for testing.
     */
    @VisibleForTesting
    public interface TelephonyManagerProxy {
        int getPhoneCount();
    }

    private final TelephonyManagerProxy mTelephonyManagerProxy;

    private static class TelephonyManagerProxyImpl implements TelephonyManagerProxy {
        private final TelephonyManager mTelephonyManager;


        TelephonyManagerProxyImpl(Context context) {
            mTelephonyManager = new TelephonyManager(context);
        }

        @Override
        public int getPhoneCount() {
            return mTelephonyManager.getActiveModemCount();
        }
    }

    /**
     * Return the handler for testing.
     */
    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }

    @VisibleForTesting
    public static final int MSG_SET_EMERGENCY_MODE_DONE = 1;
    @VisibleForTesting
    public static final int MSG_EXIT_EMERGENCY_MODE_DONE = 2;
    @VisibleForTesting
    public static final int MSG_SET_EMERGENCY_CALLBACK_MODE_DONE = 3;

    private class MyHandler extends Handler {

        MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
            case MSG_SET_EMERGENCY_MODE_DONE:
                Rlog.v(TAG, "MSG_SET_EMERGENCY_MODE_DONE");
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    mLastEmergencyRegResult = (EmergencyRegResult) ar.result;
                } else {
                    Rlog.w(TAG, "LastEmergencyRegResult not set. AsyncResult.exception: "
                            + ar.exception);
                }
                setIsInEmergencyCall(true);
                mOnCompleted.complete(DisconnectCause.NOT_DISCONNECTED);
                break;

            case MSG_EXIT_EMERGENCY_MODE_DONE:
                Rlog.v(TAG, "MSG_EXIT_EMERGENCY_MODE_DONE");
                setIsInEmergencyCall(false);
                if (mOnEcmExitCompleteRunnable != null) {
                    mOnEcmExitCompleteRunnable.run();
                    mOnEcmExitCompleteRunnable = null;
                }
                break;

            case MSG_SET_EMERGENCY_CALLBACK_MODE_DONE:
                Rlog.v(TAG, "MSG_SET_EMERGENCY_CALLBACK_MODE_DONE");
                break;

            default:
                break;
            }
        }
    }

    /**
     * Creates the EmergencyStateTracker singleton instance.
     *
     * @param context                                 The context of the application.
     * @param isSuplDdsSwitchRequiredForEmergencyCall Whether gnss supl requires default data for
     *                                                emergency call.
     */
    public static void make(Context context, boolean isSuplDdsSwitchRequiredForEmergencyCall) {
        if (INSTANCE == null) {
            INSTANCE = new EmergencyStateTracker(context, Looper.myLooper(),
                    isSuplDdsSwitchRequiredForEmergencyCall);
        }
    }

    /**
     * Returns the singleton instance of EmergencyStateTracker.
     *
     * @return {@link EmergencyStateTracker} instance.
     */
    public static EmergencyStateTracker getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("EmergencyStateTracker is not ready!");
        }
        return INSTANCE;
    }

    /**
     * Initializes EmergencyStateTracker.
     */
    private EmergencyStateTracker(Context context, Looper looper,
            boolean isSuplDdsSwitchRequiredForEmergencyCall) {
        mEcmExitTimeoutMs = DEFAULT_ECM_EXIT_TIMEOUT_MS;
        mContext = context;
        mHandler = new MyHandler(looper);
        mIsSuplDdsSwitchRequiredForEmergencyCall = isSuplDdsSwitchRequiredForEmergencyCall;

        PowerManager pm = context.getSystemService(PowerManager.class);
        mWakeLock = (pm != null) ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "telephony:" + TAG) : null;
        mConfigManager = context.getSystemService(CarrierConfigManager.class);

        // Register receiver for ECM exit.
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        context.registerReceiver(mEcmExitReceiver, filter);
        mTelephonyManagerProxy = new TelephonyManagerProxyImpl(context);
    }

    /**
     * Initializes EmergencyStateTracker with injections for testing.
     *
     * @param context                                 The context of the application.
     * @param looper                                  The {@link Looper} of the application.
     * @param isSuplDdsSwitchRequiredForEmergencyCall Whether gnss supl requires default data for
     *                                                emergency call.
     * @param phoneFactoryProxy                       The {@link PhoneFactoryProxy} to be injected.
     * @param phoneSwitcherProxy                      The {@link PhoneSwitcherProxy} to be injected.
     * @param telephonyManagerProxy                   The {@link TelephonyManagerProxy} to be
     *                                                injected.
     * @param radioOnHelper                           The {@link RadioOnHelper} to be injected.
     */
    @VisibleForTesting
    public EmergencyStateTracker(Context context, Looper looper,
            boolean isSuplDdsSwitchRequiredForEmergencyCall, PhoneFactoryProxy phoneFactoryProxy,
            PhoneSwitcherProxy phoneSwitcherProxy, TelephonyManagerProxy telephonyManagerProxy,
            RadioOnHelper radioOnHelper, long ecmExitTimeoutMs) {
        mContext = context;
        mHandler = new MyHandler(looper);
        mIsSuplDdsSwitchRequiredForEmergencyCall = isSuplDdsSwitchRequiredForEmergencyCall;
        mPhoneFactoryProxy = phoneFactoryProxy;
        mPhoneSwitcherProxy = phoneSwitcherProxy;
        mTelephonyManagerProxy = telephonyManagerProxy;
        mRadioOnHelper = radioOnHelper;
        mEcmExitTimeoutMs = ecmExitTimeoutMs;
        mWakeLock = null; // Don't declare a wakelock in tests
        mConfigManager = context.getSystemService(CarrierConfigManager.class);
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        context.registerReceiver(mEcmExitReceiver, filter);
    }

    /**
     * Starts the process of an emergency call.
     *
     * <p>
     * Handles turning on radio and switching DDS.
     *
     * @param phone                 the {@code Phone} on which to process the emergency call.
     * @param callId                the call id on which to process the emergency call.
     * @param isTestEmergencyNumber whether this is a test emergency number.
     * @return a {@code CompletableFuture} that results in {@code DisconnectCause.NOT_DISCONNECTED}
     *         if emergency call successfully started.
     */
    public CompletableFuture<Integer> startEmergencyCall(Phone phone, String callId,
            boolean isTestEmergencyNumber) {
        Rlog.i(TAG, "startEmergencyCall for callId:" + callId);

        if (mPhone != null) {
            Rlog.e(TAG, "startEmergencyCall failed. Existing emergency call in progress.");
            // Create new future to return as to not interfere with any uncompleted futures.
            CompletableFuture<Integer> future = new CompletableFuture<>();
            future.complete(DisconnectCause.ERROR_UNSPECIFIED);
            return future;
        }
        mPhone = phone;
        mIsTestEmergencyNumber = isTestEmergencyNumber;
        mLastEmergencyRegResult = null;
        mOnCompleted = new CompletableFuture<>();

        final boolean isAirplaneModeOn = isAirplaneModeOn(mContext);
        boolean needToTurnOnRadio = !isRadioOn() || isAirplaneModeOn;

        if (needToTurnOnRadio) {
            if (mRadioOnHelper == null) {
                mRadioOnHelper = new RadioOnHelper(mContext);
            }

            mRadioOnHelper.triggerRadioOnAndListen(new RadioOnStateListener.Callback() {
                @Override
                public void onComplete(RadioOnStateListener listener, boolean isRadioReady) {
                    if (!isRadioReady) {
                        // Could not turn radio on
                        Rlog.e(TAG, "Failed to turn on radio.");
                        mOnCompleted.complete(DisconnectCause.POWER_OFF);
                        // If call setup fails, then move to MODE_EMERGENCY_NONE.
                        exitEmergencyMode();
                    } else {
                        delayDialAndSetEmergencyMode(phone);
                    }
                }

                @Override
                public boolean isOkToCall(Phone phone, int serviceState) {
                    // We currently only look to make sure that the radio is on before dialing. We
                    // should be able to make emergency calls at any time after the radio has been
                    // powered on and isn't in the UNAVAILABLE state, even if it is reporting the
                    // OUT_OF_SERVICE state.
                    return phone.getServiceStateTracker().isRadioOn();
                }
            }, !isTestEmergencyNumber, phone, isTestEmergencyNumber);
        } else {
            delayDialAndSetEmergencyMode(phone);
        }

        return mOnCompleted;
    }

    private void delayDialAndSetEmergencyMode(Phone phone) {
        delayDialForDdsSwitch(phone, result -> {
            Rlog.i(TAG, "delayDialForDdsSwitch: result = " + result);
            if (!result) {
                // DDS Switch timed out/failed, but continue with call as it may still succeed.
                Rlog.e(TAG, "DDS Switch failed.");
            }
            // Once radio is on and DDS switched, must call setEmergencyMode() before selecting
            // emergency domain. EmergencyRegResult is required to determine domain and this is the
            // only API that can receive it before starting domain selection. Once domain selection
            // is finished, the actual emergency mode will be set when onEmergencyTransportChanged()
            // is called.
            setEmergencyMode(MODE_EMERGENCY_WWAN, MSG_SET_EMERGENCY_MODE_DONE);
        });
    }

    /**
     * Triggers modem to set new emergency mode.
     *
     * @param mode the new emergency mode.
     * @param msg the message to be sent once mode has been set.
     */
    private void setEmergencyMode(@EmergencyConstants.EmergencyMode int mode, int msg) {
        Rlog.i(TAG, "setEmergencyMode from " + mEmergencyMode + " to " + mode);

        if (mEmergencyMode == mode) {
            return;
        }
        mEmergencyMode = mode;

        Message m = mHandler.obtainMessage(msg);
        if (mIsTestEmergencyNumber) {
            Rlog.d(TAG, "IsTestEmergencyNumber true. Skipping setting emergency mode on modem.");
            // Send back a response for the command, but with null information
            AsyncResult.forMessage(m, null, null);
            // Ensure that we do not accidentally block indefinitely when trying to validate test
            // emergency numbers
            m.sendToTarget();
            return;
        }
        mPhone.setEmergencyMode(mode, mHandler.obtainMessage(msg));
    }

    /**
     * Notifies external app listeners of emergency mode changes.
     *
     * @param callActive whether there is an active emergency call.
     */
    private void setIsInEmergencyCall(boolean callActive) {
        mIsInEmergencyCall = callActive;
    }

    /**
     * Checks if there is an ongoing emergency call.
     *
     * @return true if in emergency call
     */
    public boolean isInEmergencyCall() {
        return mIsInEmergencyCall;
    }

    /**
     * Triggers modem to exit emergency mode.
     */
    private void exitEmergencyMode() {
        Rlog.i(TAG, "exitEmergencyMode");

        if (mEmergencyMode != MODE_EMERGENCY_NONE) {
            mEmergencyMode = MODE_EMERGENCY_NONE;
            mPhone.exitEmergencyMode(mHandler.obtainMessage(MSG_EXIT_EMERGENCY_MODE_DONE));
        }
        mPhone = null;
    }

    /**
     * Ends emergency call.
     *
     * <p>
     * Enter ECM only once all active emergency calls have ended. If a call never reached
     * {@link Call.State#ACTIVE}, then no need to enter ECM.
     *
     * @param callId the call id on which to end the emergency call.
     */
    public void endCall(String callId) {
        boolean wasActive = mActiveEmergencyCalls.remove(callId);
        if (mIsTestEmergencyNumber
                || (wasActive && emergencyCallbackModeSupported()
                    && mActiveEmergencyCalls.isEmpty())) {
            enterEmergencyCallbackMode();
        } else {
            exitEmergencyMode();
            mEmergencyCallDomain = -1;
        }
    }

    /** Returns last {@link EmergencyRegResult} as set by {@code setEmergencyMode()}. */
    public EmergencyRegResult getEmergencyRegResult() {
        return mLastEmergencyRegResult;
    }

    /**
     * Handles emergency transport change by setting new emergency mode.
     *
     * @param mode the new emergency mode
     */
    public void onEmergencyTransportChanged(@EmergencyConstants.EmergencyMode int mode) {
        setEmergencyMode(mode, MSG_SET_EMERGENCY_MODE_DONE);
    }

    /**
     * Notify the tracker that the emergency call domain has been updated.
     * @param phoneType The new PHONE_TYPE_* of the call.
     * @param callId The ID of the call
     */
    public void onEmergencyCallDomainUpdated(int phoneType, String callId) {
        Rlog.d(TAG, "domain update for callId: " + callId);
        int domain = -1;
        switch(phoneType) {
            case (PhoneConstants.PHONE_TYPE_CDMA_LTE):
                //fallthrough
            case (PhoneConstants.PHONE_TYPE_GSM):
                //fallthrough
            case (PhoneConstants.PHONE_TYPE_CDMA): {
                domain = NetworkRegistrationInfo.DOMAIN_CS;
                break;
            }
            case (PhoneConstants.PHONE_TYPE_IMS): {
                domain = NetworkRegistrationInfo.DOMAIN_PS;
                break;
            }
            default: {
                Rlog.w(TAG, "domain updated: Unexpected phoneType:" + phoneType);
            }
        }
        if (mEmergencyCallDomain == domain) return;
        Rlog.i(TAG, "domain updated: from " + mEmergencyCallDomain + " to " + domain);
        mEmergencyCallDomain = domain;
    }

    /**
     * Handles emergency call state change.
     *
     * @param state the new call state
     * @param callId the callId whose state has changed
     */
    public void onEmergencyCallStateChanged(Call.State state, String callId) {
        if (state == Call.State.ACTIVE) {
            mActiveEmergencyCalls.add(callId);
        }
    }

    /**
     * Returns {@code true} if device and carrier support emergency callback mode.
     */
    private boolean emergencyCallbackModeSupported() {
        return getConfig(mPhone.getSubId(),
                CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_CALLBACK_MODE_SUPPORTED_BOOL,
                DEFAULT_EMERGENCY_CALLBACK_MODE_SUPPORTED);
    }

    /**
     * Trigger entry into emergency callback mode.
     */
    private void enterEmergencyCallbackMode() {
        Rlog.d(TAG, "enter ECBM");
        setIsInEmergencyCall(false);
        // Check if not in ECM already.
        if (!isInEcm()) {
            setIsInEcm(true);
            if (!mPhone.getUnitTestMode()) {
                TelephonyProperties.in_ecm_mode(true);
            }

            // Notify listeners of the entrance to ECM.
            sendEmergencyCallbackModeChange();
            if (isInImsEcm()) {
                // emergency call registrants are not notified of new emergency call until entering
                // ECBM (see ImsPhone#handleEnterEmergencyCallbackMode)
                ((GsmCdmaPhone) mPhone).notifyEmergencyCallRegistrants(true);
            }

            // Set emergency mode on modem.
            setEmergencyMode(MODE_EMERGENCY_CALLBACK, MSG_SET_EMERGENCY_CALLBACK_MODE_DONE);

            // Post this runnable so we will automatically exit if no one invokes
            // exitEmergencyCallbackMode() directly.
            long delayInMillis = TelephonyProperties.ecm_exit_timer()
                    .orElse(mEcmExitTimeoutMs);
            mHandler.postDelayed(mExitEcmRunnable, delayInMillis);

            // We don't want to go to sleep while in ECM.
            if (mWakeLock != null) mWakeLock.acquire(mEcmExitTimeoutMs);
        }
    }

    /**
     * Exits emergency callback mode and notifies relevant listeners.
     */
    public void exitEmergencyCallbackMode() {
        Rlog.d(TAG, "ecit ECBM");
        // Remove pending exit ECM runnable, if any.
        mHandler.removeCallbacks(mExitEcmRunnable);
        mEmergencyCallDomain = -1;
        mIsTestEmergencyNumber = false;

        if (isInEcm()) {
            setIsInEcm(false);
            if (!mPhone.getUnitTestMode()) {
                TelephonyProperties.in_ecm_mode(false);
            }

            // Release wakeLock.
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }

            // Send intents that ECM has changed.
            sendEmergencyCallbackModeChange();
            ((GsmCdmaPhone) mPhone).notifyEmergencyCallRegistrants(false);

            // Exit emergency mode on modem.
            exitEmergencyMode();
        }
    }

    /**
     * Exits emergency callback mode and triggers runnable after exit response is received.
     */
    public void exitEmergencyCallbackMode(Runnable onComplete) {
        mOnEcmExitCompleteRunnable = onComplete;
        exitEmergencyCallbackMode();
    }

    /**
     * Sends intents that emergency callback mode changed.
     */
    private void sendEmergencyCallbackModeChange() {
        Rlog.d(TAG, "sendEmergencyCallbackModeChange: isInEcm=" + isInEcm());

        Intent intent = new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, isInEcm());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Returns {@code true} if currently in emergency callback mode.
     *
     * <p>
     * This is a period where the phone should be using as little power as possible and be ready to
     * receive an incoming call from the emergency operator.
     */
    public boolean isInEcm() {
        return mIsPhoneInEcmState;
    }

    /**
     * Sets the emergency callback mode state.
     */
    private void setIsInEcm(boolean isInEcm) {
        mIsPhoneInEcmState = isInEcm;
    }

    /**
     * Returns {@code true} if currently in emergency callback mode over PS
     */
    public boolean isInImsEcm() {
        return mEmergencyCallDomain == NetworkRegistrationInfo.DOMAIN_PS && isInEcm();
    }

    /**
     * Returns {@code true} if currently in emergency callback mode over CS
     */
    public boolean isInCdmaEcm() {
        // Phone can be null in the case where we are not actively tracking an emergency call.
        if (mPhone == null) return false;
        // Ensure that this method doesn't return true when we are attached to GSM.
        return mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA
                && mEmergencyCallDomain == NetworkRegistrationInfo.DOMAIN_CS && isInEcm();
    }

    /**
     * Returns {@code true} if any phones from PhoneFactory have radio on.
     */
    private boolean isRadioOn() {
        boolean result = false;
        for (Phone phone : mPhoneFactoryProxy.getPhones()) {
            result |= phone.isRadioOn();
        }
        return result;
    }

    /**
     * Returns {@code true} if airplane mode is on.
     */
    private boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) > 0;
    }

    /**
     * If needed, block until the default data is switched for outgoing emergency call, or
     * timeout expires.
     *
     * @param phone            The Phone to switch the DDS on.
     * @param completeConsumer The consumer to call once the default data subscription has been
     *                         switched, provides {@code true} result if the switch happened
     *                         successfully or {@code false} if the operation timed out/failed.
     */
    @VisibleForTesting
    public void delayDialForDdsSwitch(Phone phone, Consumer<Boolean> completeConsumer) {
        if (phone == null) {
            // Do not block indefinitely.
            completeConsumer.accept(false);
        }
        try {
            // Waiting for PhoneSwitcher to complete the operation.
            CompletableFuture<Boolean> future = possiblyOverrideDefaultDataForEmergencyCall(phone);
            // In the case that there is an issue or bug in PhoneSwitcher logic, do not wait
            // indefinitely for the future to complete. Instead, set a timeout that will complete
            // the future as to not block the outgoing call indefinitely.
            CompletableFuture<Boolean> timeout = new CompletableFuture<>();
            mHandler.postDelayed(() -> timeout.complete(false), DEFAULT_DATA_SWITCH_TIMEOUT_MS);
            // Also ensure that the Consumer is completed on the main thread.
            CompletableFuture<Void> unused = future.acceptEitherAsync(timeout, completeConsumer,
                    phone.getContext().getMainExecutor());
        } catch (Exception e) {
            Rlog.w(TAG, "delayDialForDdsSwitch - exception= " + e.getMessage());
        }
    }

    /**
     * If needed, block until Default Data subscription is switched for outgoing emergency call.
     *
     * <p>
     * In some cases, we need to try to switch the Default Data subscription before placing the
     * emergency call on DSDS devices. This includes the following situation: - The modem does not
     * support processing GNSS SUPL requests on the non-default data subscription. For some carriers
     * that do not provide a control plane fallback mechanism, the SUPL request will be dropped and
     * we will not be able to get the user's location for the emergency call. In this case, we need
     * to swap default data temporarily.
     *
     * @param phone Evaluates whether or not the default data should be moved to the phone
     *              specified. Should not be null.
     */
    private CompletableFuture<Boolean> possiblyOverrideDefaultDataForEmergencyCall(
            @NonNull Phone phone) {
        int phoneCount = mTelephonyManagerProxy.getPhoneCount();
        // Do not override DDS if this is a single SIM device.
        if (phoneCount <= PhoneConstants.MAX_PHONE_COUNT_SINGLE_SIM) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Do not switch Default data if this device supports emergency SUPL on non-DDS.
        if (!mIsSuplDdsSwitchRequiredForEmergencyCall) {
            Rlog.d(TAG, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS, does not "
                    + "require DDS switch.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Only override default data if we are IN_SERVICE already.
        if (!isAvailableForEmergencyCalls(phone)) {
            Rlog.d(TAG, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Only override default data if we are not roaming, we do not want to switch onto a network
        // that only supports data plane only (if we do not know).
        boolean isRoaming = phone.getServiceState().getVoiceRoaming();
        // In some roaming conditions, we know the roaming network doesn't support control plane
        // fallback even though the home operator does. For these operators we will need to do a DDS
        // switch anyway to make sure the SUPL request doesn't fail.
        boolean roamingNetworkSupportsControlPlaneFallback = true;
        String[] dataPlaneRoamPlmns = getConfig(phone.getSubId(),
                CarrierConfigManager.Gps.KEY_ES_SUPL_DATA_PLANE_ONLY_ROAMING_PLMN_STRING_ARRAY);
        if (dataPlaneRoamPlmns != null && Arrays.asList(dataPlaneRoamPlmns)
                .contains(phone.getServiceState().getOperatorNumeric())) {
            roamingNetworkSupportsControlPlaneFallback = false;
        }
        if (isRoaming && roamingNetworkSupportsControlPlaneFallback) {
            Rlog.d(TAG, "possiblyOverrideDefaultDataForEmergencyCall: roaming network is assumed "
                    + "to support CP fallback, not switching DDS.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
        // Do not try to swap default data if we support CS fallback or it is assumed that the
        // roaming network supports control plane fallback, we do not want to introduce a lag in
        // emergency call setup time if possible.
        final boolean supportsCpFallback = getConfig(phone.getSubId(),
                CarrierConfigManager.Gps.KEY_ES_SUPL_CONTROL_PLANE_SUPPORT_INT,
                CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_CP_ONLY)
                != CarrierConfigManager.Gps.SUPL_EMERGENCY_MODE_TYPE_DP_ONLY;
        if (supportsCpFallback && roamingNetworkSupportsControlPlaneFallback) {
            Rlog.d(TAG, "possiblyOverrideDefaultDataForEmergencyCall: not switching DDS, carrier "
                    + "supports CP fallback.");
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        // Get extension time, may be 0 for some carriers that support ECBM as well. Use
        // CarrierConfig default if format fails.
        int extensionTime = 0;
        try {
            extensionTime = Integer.parseInt(getConfig(phone.getSubId(),
                    CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0"));
        } catch (NumberFormatException e) {
            // Just use default.
        }
        CompletableFuture<Boolean> modemResultFuture = new CompletableFuture<>();
        try {
            Rlog.d(TAG, "possiblyOverrideDefaultDataForEmergencyCall: overriding DDS for "
                    + extensionTime + "seconds");
            mPhoneSwitcherProxy.getPhoneSwitcher().overrideDefaultDataForEmergency(
                    phone.getPhoneId(), extensionTime, modemResultFuture);
            // Catch all exceptions, we want to continue with emergency call if possible.
        } catch (Exception e) {
            Rlog.w(TAG,
                    "possiblyOverrideDefaultDataForEmergencyCall: exception = " + e.getMessage());
            modemResultFuture = CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return modemResultFuture;
    }

    // Helper functions for easy CarrierConfigManager access
    private String getConfig(int subId, String key, String defVal) {
        return getConfigBundle(subId, key).getString(key, defVal);
    }
    private int getConfig(int subId, String key, int defVal) {
        return getConfigBundle(subId, key).getInt(key, defVal);
    }
    private String[] getConfig(int subId, String key) {
        return getConfigBundle(subId, key).getStringArray(key);
    }
    private boolean getConfig(int subId, String key, boolean defVal) {
        return getConfigBundle(subId, key).getBoolean(key, defVal);
    }
    private PersistableBundle getConfigBundle(int subId, String key) {
        if (mConfigManager == null) return new PersistableBundle();
        return mConfigManager.getConfigForSubId(subId, key);
    }

    /**
     * Returns true if the state of the Phone is IN_SERVICE or available for emergency calling only.
     */
    private boolean isAvailableForEmergencyCalls(Phone phone) {
        return ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState()
                || phone.getServiceState().isEmergencyOnly();
    }
}
