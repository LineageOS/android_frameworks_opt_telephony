/*
 * Copyright (c) 2018, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 *
 * Copyright 2013, 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.sysprop.TelephonyProperties;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsEcbm;
import com.android.ims.ImsEcbmStateListener;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import java.util.concurrent.Executor;

import static java.util.Arrays.copyOf;
public class EcbmHandler extends Handler {

    private static final String LOG_TAG = "EcbmHandler";
    private static final boolean DBG = true;
    private static EcbmHandler mInstance;
    private ECBMTracker[] trackers = null;
    private ImsEcbmStateListener[] mImsEcbmStateListener;
    private int mNumPhones = TelephonyManager.getDefault().getPhoneCount();
    private WakeLock mWakeLock;
    private Context mContext;
    // Default Emergency Callback Mode exit timer
    private static final long DEFAULT_ECM_EXIT_TIMER_VALUE = 300000;
    public static final int RESTART_ECM_TIMER = 0; // restart Ecm timer
    public static final int CANCEL_ECM_TIMER = 1; // cancel Ecm timer

    // Keep track of whether or not the phone is in Emergency Callback Mode for Phone and
    // subclasses
    protected boolean mIsPhoneInEcmState = false;

    // Keep track of the case where ECM was cancelled to place another outgoing emergency call.
    // We will need to restart it after the emergency call ends.
    private boolean mEcmCanceledForEmergency = false;

    // mEcmExitRespRegistrant is informed after the phone has been exited
    private Registrant mEcmExitRespRegistrant;
    // mEcmTimerResetRegistrants are informed after Ecm timer is canceled or re-started
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();
    private boolean mIsEcbmOnIms = false;
    private int mEcbmPhoneId = 0;

    private static final String PREF_KEY_ECBM_PHONEID = "ecbm_phoneid";
    private static final String PREF_KEY_IS_ECBM_ON_IMS = "is_ecbm_on_ims";

    protected static final int EVENT_EMERGENCY_CALLBACK_MODE_ENTER  = 1;
    protected static final int EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE = 2;

    // A runnable which is used to automatically exit from Ecm after a period of time.
    private Runnable mExitEcmRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                exitEmergencyCallbackMode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    public class ECBMTracker {
        public int phoneId;
        public CommandsInterface mCi;
        public GsmCdmaPhone mPhone;
        public ImsPhone mImsPhone;
        public ImsEcbmStateHandler mImsEcbmStateHandler;
    }

    private EcbmHandler () {
        trackers = new ECBMTracker[mNumPhones];
        for (int i = 0; i < mNumPhones; i++) {
            trackers[i] = new ECBMTracker();
        }
        mIsPhoneInEcmState = getInEcmMode();
    }

    public static EcbmHandler getInstance () {
        if (mInstance == null) {
            mInstance = new EcbmHandler();
        }
        return mInstance;
    }


    public EcbmHandler initialize (Context context, GsmCdmaPhone phone,
                CommandsInterface ci, int phoneId) {
        if(mContext ==null) {
            mContext = context;
            PowerManager pm
                    = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
            mWakeLock.setReferenceCounted(false);

            if (mIsPhoneInEcmState) {
                restoreCachedEcbmState();
                logd("initialize: ecbmPhoneId = " + mEcbmPhoneId +
                        " isEcbmOnIms = " + mIsEcbmOnIms);
            }
        }

        mNumPhones = TelephonyManager.getDefault().getActiveModemCount();
        int prevModemCount = trackers.length;
        trackers = copyOf(trackers, mNumPhones);
        // in the case of SS to DSDS, increase the size of trackers
        for (int i = prevModemCount; i < mNumPhones; ++i) {
            trackers[i] = new ECBMTracker();
        }

        if ( phoneId >= 0 && phoneId < mNumPhones) {
            trackers[phoneId].phoneId = phoneId;
            trackers[phoneId].mCi = ci;
            trackers[phoneId].mPhone = phone;
            ci.setEmergencyCallbackMode(this, EVENT_EMERGENCY_CALLBACK_MODE_ENTER, phoneId);
            ci.registerForExitEmergencyCallbackMode(this, EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE,
                phoneId);

        }
        return mInstance;
    }

    public void updateImsPhone (Phone imsPhone, int phoneId) {
        if (phoneId >= 0 && phoneId < mNumPhones) {
            if (imsPhone != null) {
                trackers[phoneId].mImsPhone = (ImsPhone)imsPhone;
                trackers[phoneId].mImsEcbmStateHandler = new ImsEcbmStateHandler(phoneId,imsPhone.getContext().getMainExecutor());
            } else {
                trackers[phoneId].mImsPhone = null;
                trackers[phoneId].mImsEcbmStateHandler = null;
            }
        }
    }

    @VisibleForTesting
    public ImsEcbmStateListener getImsEcbmStateListener(int phoneId) {
        return trackers[phoneId].mImsEcbmStateHandler;
    }

    private void sendEmergencyCallbackModeChange(){
        //Send an Intent
        Intent intent = new Intent(TelephonyIntents.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        intent.putExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, isInEcm());
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mEcbmPhoneId);
        ActivityManager.broadcastStickyIntent(intent, UserHandle.USER_ALL);
        if (DBG) logd("sendEmergencyCallbackModeChange");
    }

    /**
     * Listen to the IMS ECBM state change
     */
    public class ImsEcbmStateHandler extends ImsEcbmStateListener {
        int phoneId;
        public ImsEcbmStateHandler (int id, Executor executor) {
            super(executor);
            phoneId = id;
        }
        @Override
        public void onECBMEntered(Executor executor) {
            if (DBG) logd("onECBMEntered: " + phoneId);
            mIsEcbmOnIms = true;
            mEcbmPhoneId = phoneId;
            handleEnterEmergencyCallbackMode(phoneId);
        }

        @Override
        public void onECBMExited(Executor executor) {
            if (DBG) logd("onECBMExited: " + phoneId);
            handleExitEmergencyCallbackMode(phoneId);
            mIsEcbmOnIms = false;
            mEcbmPhoneId = 0;
        }
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
            case EVENT_EMERGENCY_CALLBACK_MODE_ENTER:{
                ar = (AsyncResult)msg.obj;
                mEcbmPhoneId = (int) ar.userObj;
                Rlog.d(LOG_TAG, " EVENT_EMERGENCY_CALLBACK_MODE_ENTER mEcbmPhoneId: "
                        + mEcbmPhoneId);
                handleEnterEmergencyCallbackMode(mEcbmPhoneId);
            }
            break;

            case  EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE:{
                ar = (AsyncResult)msg.obj;
                int phoneId = (int) ar.userObj;
                Rlog.d(LOG_TAG, " EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE phoneId: " + phoneId +
                        "ar.exception: " + ar.exception);
                if (ar.exception == null) {
                    handleExitEmergencyCallbackMode(phoneId);
                    mEcbmPhoneId = 0;
                }
            }
            break;
            default:
                Rlog.d(LOG_TAG, "Unnown event received: " + msg.what);
        }
    }


    public void exitEmergencyCallbackMode() throws Exception {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        if (DBG) logd("exitEmergencyCallbackMode() mIsEcbmOnIms: " + mIsEcbmOnIms +
                " mEcbmPhoneId: " + mEcbmPhoneId);
        if (mIsEcbmOnIms && trackers[mEcbmPhoneId].mImsPhone != null) {
            ImsPhoneCallTracker cT =
                    (ImsPhoneCallTracker)trackers[mEcbmPhoneId].mImsPhone.getCallTracker();
            if (cT != null) {
                try {
                    ImsEcbm ecbm;
                    ecbm = cT.getEcbmInterface();
                    ecbm.exitEmergencyCallbackMode();
                } catch (ImsException e) {
                    e.printStackTrace();
                    throw new Exception("exitEmergencyCallbackMode");
                }
            }
        } else {
            trackers[mEcbmPhoneId].mCi.exitEmergencyCallbackMode(
                    obtainMessage(EVENT_EXIT_EMERGENCY_CALLBACK_RESPONSE, mEcbmPhoneId));
        }
    }

    private void handleEnterEmergencyCallbackMode(int phoneId) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleEnterEmergencyCallbackMode, isInEcm()="
                    + isInEcm() + "phoneId: " + phoneId);
        }
        // if phone is not in Ecm mode, and it's changed to Ecm mode
        if (!isInEcm()) {
            setIsInEcm(true);
            cacheEcbmState();

            // notify change
            sendEmergencyCallbackModeChange();
            trackers[phoneId].mPhone.notifyEmergencyCallRegistrants(true);

            // Post this runnable so we will automatically exit
            // if no one invokes exitEmergencyCallbackMode() directly.
            long delayInMillis = TelephonyProperties.ecm_exit_timer()
                    .orElse(DEFAULT_ECM_EXIT_TIMER_VALUE);
            postDelayed(mExitEcmRunnable, delayInMillis);
            // We don't want to go to sleep while in Ecm
            mWakeLock.acquire();
        }
    }

    public void handleExitEmergencyCallbackMode(int phoneId) {
        if (DBG) {
            Rlog.d(LOG_TAG, "handleExitEmergencyCallbackMode, isInEcm=" + isInEcm());
        }
        // Remove pending exit Ecm runnable, if any
        removeCallbacks(mExitEcmRunnable);

        if (mEcmExitRespRegistrant != null) {
            mEcmExitRespRegistrant.notifyRegistrant();
        }

        setIsInEcm(false);
        removeEcbmCache();

        // release wakeLock
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        // send an Intent
        sendEmergencyCallbackModeChange();
        // Re-initiate data connection
        setInternalDataEnabled(true);
        trackers[phoneId].mPhone.notifyEmergencyCallRegistrants(false);
    }

    /**
     * Handle to cancel or restart Ecm timer in emergency call back mode
     * if action is CANCEL_ECM_TIMER, cancel Ecm timer and notify apps the timer is canceled;
     * otherwise, restart Ecm timer and notify apps the timer is restarted.
     */
    public void handleTimerInEmergencyCallbackMode(int action) {
        switch(action) {
            case CANCEL_ECM_TIMER:
                removeCallbacks(mExitEcmRunnable);
                mEcmTimerResetRegistrants.notifyResult(Boolean.TRUE);
                setEcmCanceledForEmergency(true /*isCanceled*/);
                break;
            case RESTART_ECM_TIMER:
                long delayInMillis = TelephonyProperties.ecm_exit_timer()
                        .orElse(DEFAULT_ECM_EXIT_TIMER_VALUE);
                postDelayed(mExitEcmRunnable, delayInMillis);
                mEcmTimerResetRegistrants.notifyResult(Boolean.FALSE);
                setEcmCanceledForEmergency(false /*isCanceled*/);
                break;
            default:
                Rlog.e(LOG_TAG, "handleTimerInEmergencyCallbackMode, unsupported action " + action);
        }
    }

    public void setOnEcbModeExitResponse(Handler h, int what, Object obj) {
        mEcmExitRespRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnEcbModeExitResponse(Handler h) {
        mEcmExitRespRegistrant.clear();
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        mEcmTimerResetRegistrants.notifyResult(flag);
    }

    public void setInternalDataEnabled(boolean flag) {
        for (int i = 0; i < mNumPhones; i++) {
            if (trackers[i].mPhone != null && trackers[i].mPhone.getDataEnabledSettings() != null) {
                trackers[i].mPhone.getDataEnabledSettings().setInternalDataEnabled(flag);
            }
        }
    }

    /**
     * Registration point for Ecm timer reset
     *
     * @param h handler to notify
     * @param what User-defined message code
     * @param obj placed in Message.obj
     */
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForEcmTimerReset(Handler h) {
        mEcmTimerResetRegistrants.remove(h);
    }

    /**
     * @return {@code true} if we are in emergency call back mode. This is a period where the phone
     * should be using as little power as possible and be ready to receive an incoming call from the
     * emergency operator.
     */
    public boolean isInEcm() {
        return mIsPhoneInEcmState;
    }

    public boolean isInImsEcm() {
        return mIsEcbmOnIms;
    }

    public void setIsInEcm(boolean isInEcm) {
        TelephonyProperties.in_ecm_mode(isInEcm);
        mIsPhoneInEcmState = isInEcm;
    }

    public static boolean getInEcmMode() {
        return TelephonyProperties.in_ecm_mode().orElse(false);
    }

    /**
     * Cache the phoneid and ecbm on ims state in shared preference.
     * It is used when phone process restarts after a crash.
     * This is invoked only when ecbm is entered.
     */
    private void cacheEcbmState() {
        if (mContext == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(PREF_KEY_ECBM_PHONEID, mEcbmPhoneId);
        editor.putBoolean(PREF_KEY_IS_ECBM_ON_IMS, mIsEcbmOnIms);
        editor.apply();
    }

    /**
     * Restore the phoneId and ecbm on ims state.
     */
    private void restoreCachedEcbmState() {
        if (mContext == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        mEcbmPhoneId = sp.getInt(PREF_KEY_ECBM_PHONEID, 0);
        if (mEcbmPhoneId < 0  || mEcbmPhoneId >=
                TelephonyManager.getDefault().getActiveModemCount()) {
            mEcbmPhoneId = 0;
        }
        mIsEcbmOnIms = sp.getBoolean(PREF_KEY_IS_ECBM_ON_IMS, false);
    }

    private void removeEcbmCache() {
        if (mContext == null) return;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(PREF_KEY_ECBM_PHONEID);
        editor.remove(PREF_KEY_IS_ECBM_ON_IMS);
        editor.apply();
    }

    /**
     * @return true if this Phone is in an emergency call that caused emergency callback mode to be
     * canceled, false if not.
     */
    public boolean isEcmCanceledForEmergency() {
        return mEcmCanceledForEmergency;
    }

    /**
     * Set whether or not this Phone has an active emergency call that was placed during emergency
     * callback mode and caused it to be temporarily canceled.
     * @param isCanceled true if an emergency call was placed that caused ECM to be canceled, false
     *                   if it is not in this state.
     */
    public void setEcmCanceledForEmergency(boolean isCanceled) {
        mEcmCanceledForEmergency = isCanceled;
    }

    private void logd(String s) {
        Rlog.d(LOG_TAG, s);
    }

}
