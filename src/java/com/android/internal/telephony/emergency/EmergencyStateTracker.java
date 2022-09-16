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

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_NONE;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;

import android.annotation.NonNull;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.EmergencyRegResult;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.data.PhoneSwitcher;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Tracks the emergency call state and notifies listeners of changes to the emergency mode.
 */
public class EmergencyStateTracker {

    private static final String TAG = "EmergencyStateTracker";

    // Timeout before we continue with the emergency call without waiting for DDS switch response
    // from the modem.
    private static final int DEFAULT_DATA_SWITCH_TIMEOUT_MS = 1000;

    private static EmergencyStateTracker INSTANCE = null;

    private final Context mContext;
    private final Handler mHandler;
    private @EmergencyConstants.EmergencyMode int mEmergencyMode = MODE_EMERGENCY_NONE;
    private Phone mPhone;
    private RadioOnHelper mRadioOnHelper;
    private CompletableFuture<Integer> mOnCompleted = null;
    /** Tracks emergency calls by callId that have reached {@link Call.State.Active}.*/
    private Set<String> mActiveEmergencyCalls = new HashSet();
    private boolean mIsSuplDdsSwitchRequiredForEmergencyCall;
    private EmergencyRegResult mLastEmergencyRegResult;
    private boolean mIsInEmergencyCall;
    private boolean mIsTestEmergencyNumber;

    /** PhoneFactory Dependencies for testing. */
    @VisibleForTesting
    public interface PhoneFactoryProxy {
        Phone[] getPhones();
    }

    private PhoneFactoryProxy mPhoneFactoryProxy = new PhoneFactoryProxy() {
        @Override
        public Phone[] getPhones() {
            return PhoneFactory.getPhones();
        }
    };

    /** PhoneSwitcher dependencies for testing. */
    @VisibleForTesting
    public interface PhoneSwitcherProxy {

        PhoneSwitcher getPhoneSwitcher();
    }

    private PhoneSwitcherProxy mPhoneSwitcherProxy = new PhoneSwitcherProxy() {
        @Override
        public PhoneSwitcher getPhoneSwitcher() {
            return PhoneSwitcher.getInstance();
        }
    };

    /**
     * TelephonyManager dependencies for testing.
     */
    @VisibleForTesting
    public interface TelephonyManagerProxy {
        int getPhoneCount();
    }

    private TelephonyManagerProxy mTelephonyManagerProxy;

    private static class TelephonyManagerProxyImpl implements TelephonyManagerProxy {
        private final TelephonyManager mTelephonyManager;


        TelephonyManagerProxyImpl(Context context) {
            mTelephonyManager = new TelephonyManager(context);
        }

        @Override
        public int getPhoneCount() {
            return mTelephonyManager.getPhoneCount();
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

    private final class MyHandler extends Handler {

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
        mContext = context;
        mHandler = new MyHandler(looper);
        mIsSuplDdsSwitchRequiredForEmergencyCall = isSuplDdsSwitchRequiredForEmergencyCall;
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
            RadioOnHelper radioOnHelper) {
        mContext = context;
        mHandler = new MyHandler(looper);
        mIsSuplDdsSwitchRequiredForEmergencyCall = isSuplDdsSwitchRequiredForEmergencyCall;
        mPhoneFactoryProxy = phoneFactoryProxy;
        mPhoneSwitcherProxy = phoneSwitcherProxy;
        mTelephonyManagerProxy = telephonyManagerProxy;
        mRadioOnHelper = radioOnHelper;
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
        Rlog.i(TAG, "startEmergencyCall");

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
                        mPhone = null;
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
            setEmergencyMode(MODE_EMERGENCY_WWAN);
        });
    }

    /**
     * Triggers modem to set new emergency mode.
     *
     * @param mode the new emergency mode
     */
    private void setEmergencyMode(@EmergencyConstants.EmergencyMode int mode) {
        Rlog.i(TAG, "setEmergencyMode from " + mEmergencyMode + " to " + mode);

        if (mEmergencyMode == mode) {
            return;
        }
        mEmergencyMode = mode;

        if (mIsTestEmergencyNumber) {
            Rlog.d(TAG, "IsTestEmergencyNumber true. Skipping setting emergency mode on modem.");
            return;
        }
        mPhone.setEmergencyMode(mode, mHandler.obtainMessage(MSG_SET_EMERGENCY_MODE_DONE));
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

        mEmergencyMode = MODE_EMERGENCY_NONE;

        mPhone.exitEmergencyMode(mHandler.obtainMessage(MSG_EXIT_EMERGENCY_MODE_DONE));
    }

    /**
     * Ends emergency call.
     *
     * @param callId the call id on which to end the emergency call.
     */
    public void endCall(String callId) {
        mActiveEmergencyCalls.remove(callId);
        exitEmergencyMode();
        mPhone = null;
    }

    /** Returns last {@link EmergencyRegResult} as set by {@code setEmergencyMode()}. */
    public EmergencyRegResult getEmergencyRegResult() {
        return mLastEmergencyRegResult;
    };

    /**
     * Handles emergency transport change by setting new emergency mode.
     *
     * @param mode the new emergency mode
     */
    public void onEmergencyTransportChanged(@EmergencyConstants.EmergencyMode int mode) {
        setEmergencyMode(mode);
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

    /** Returns {@code true} if any phones from PhoneFactory have radio on. */
    private boolean isRadioOn() {
        boolean result = false;
        for (Phone phone : mPhoneFactoryProxy.getPhones()) {
            result |= phone.isRadioOn();
        }
        return result;
    }

    /** Returns {@code true} if airplane mode is on. */
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

        CarrierConfigManager cfgManager = (CarrierConfigManager) phone.getContext()
                .getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (cfgManager == null) {
            // For some reason CarrierConfigManager is unavailable. Do not block emergency call.
            Rlog.w(TAG, "possiblyOverrideDefaultDataForEmergencyCall: couldn't get"
                    + "CarrierConfigManager");
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
        String[] dataPlaneRoamPlmns = cfgManager.getConfigForSubId(phone.getSubId()).getStringArray(
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
        final boolean supportsCpFallback = cfgManager.getConfigForSubId(phone.getSubId()).getInt(
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
            extensionTime = Integer.parseInt(cfgManager.getConfigForSubId(phone.getSubId())
                    .getString(CarrierConfigManager.Gps.KEY_ES_EXTENSION_SEC_STRING, "0"));
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

    /**
     * Returns true if the state of the Phone is IN_SERVICE or available for emergency calling only.
     */
    private boolean isAvailableForEmergencyCalls(Phone phone) {
        return ServiceState.STATE_IN_SERVICE == phone.getServiceState().getState()
                || phone.getServiceState().isEmergencyOnly();
    }
}
