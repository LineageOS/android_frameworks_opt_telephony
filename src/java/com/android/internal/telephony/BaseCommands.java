
/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.RegistrantList;
import android.os.Registrant;
import android.os.Handler;
import android.os.AsyncResult;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.SrvccCallContext;

import com.android.internal.telephony.RadioCapability;

/**
 * {@hide}
 */
public abstract class BaseCommands implements CommandsInterface {
    //***** Instance Variables
    protected Context mContext;
    protected RadioState mState = RadioState.RADIO_UNAVAILABLE;
    protected Object mStateMonitor = new Object();

    protected RegistrantList mRadioStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mOnRegistrants = new RegistrantList();
    protected RegistrantList mAvailRegistrants = new RegistrantList();
    protected RegistrantList mOffOrNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mNotAvailRegistrants = new RegistrantList();
    protected RegistrantList mCallStateRegistrants = new RegistrantList();
    protected RegistrantList mVoiceNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mDataNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mVoiceRadioTechChangedRegistrants = new RegistrantList();
    protected RegistrantList mImsNetworkStateChangedRegistrants = new RegistrantList();
    protected RegistrantList mIccStatusChangedRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOnRegistrants = new RegistrantList();
    protected RegistrantList mVoicePrivacyOffRegistrants = new RegistrantList();
    protected Registrant mUnsolOemHookRawRegistrant;
    protected RegistrantList mOtaProvisionRegistrants = new RegistrantList();
    protected RegistrantList mCallWaitingInfoRegistrants = new RegistrantList();
    protected RegistrantList mDisplayInfoRegistrants = new RegistrantList();
    protected RegistrantList mSignalInfoRegistrants = new RegistrantList();
    protected RegistrantList mNumberInfoRegistrants = new RegistrantList();
    protected RegistrantList mRedirNumInfoRegistrants = new RegistrantList();
    protected RegistrantList mLineControlInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53ClirInfoRegistrants = new RegistrantList();
    protected RegistrantList mT53AudCntrlInfoRegistrants = new RegistrantList();
    protected RegistrantList mRingbackToneRegistrants = new RegistrantList();
    protected RegistrantList mResendIncallMuteRegistrants = new RegistrantList();
    protected RegistrantList mCdmaSubscriptionChangedRegistrants = new RegistrantList();
    protected RegistrantList mCdmaPrlChangedRegistrants = new RegistrantList();
    protected RegistrantList mExitEmergencyCallbackModeRegistrants = new RegistrantList();
    protected RegistrantList mRilConnectedRegistrants = new RegistrantList();
    protected RegistrantList mIccRefreshRegistrants = new RegistrantList();
    protected RegistrantList mRilCellInfoListRegistrants = new RegistrantList();
    protected RegistrantList mSubscriptionStatusRegistrants = new RegistrantList();
    protected RegistrantList mSrvccStateRegistrants = new RegistrantList();
    protected RegistrantList mHardwareConfigChangeRegistrants = new RegistrantList();
    protected RegistrantList mPhoneRadioCapabilityChangedRegistrants =
            new RegistrantList();

    protected Registrant mGsmSmsRegistrant;
    protected Registrant mCdmaSmsRegistrant;
    protected Registrant mNITZTimeRegistrant;
    protected Registrant mSignalStrengthRegistrant;
    protected Registrant mUSSDRegistrant;
    protected Registrant mSmsOnSimRegistrant;
    protected Registrant mSmsStatusRegistrant;
    protected Registrant mSsnRegistrant;
    protected Registrant mCatSessionEndRegistrant;
    protected Registrant mCatProCmdRegistrant;
    protected Registrant mCatEventRegistrant;
    protected Registrant mCatCallSetUpRegistrant;
    protected Registrant mCatSendSmsResultRegistrant;
    protected Registrant mIccSmsFullRegistrant;
    protected Registrant mEmergencyCallbackModeRegistrant;
    protected Registrant mRingRegistrant;
    protected Registrant mRestrictedStateRegistrant;
    protected Registrant mGsmBroadcastSmsRegistrant;
    protected Registrant mCatCcAlphaRegistrant;
    protected Registrant mSsRegistrant;
    protected Registrant mLceInfoRegistrant;

    // Preferred network type received from PhoneFactory.
    // This is used when establishing a connection to the
    // vendor ril so it starts up in the correct mode.
    protected int mPreferredNetworkType;
    // CDMA subscription received from PhoneFactory
    protected int mCdmaSubscription;
    // Type of Phone, GSM or CDMA. Set by CDMAPhone or GSMPhone.
    protected int mPhoneType;
    // RIL Version
    protected int mRilVersion = -1;

    public BaseCommands(Context context) {
        mContext = context;  // May be null (if so we won't log statistics)
    }

    //***** CommandsInterface implementation

    @Override
    public RadioState getRadioState() {
        return mState;
    }

    @Override
    public void registerForRadioStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mRadioStateChangedRegistrants.add(r);
            r.notifyRegistrant();
        }
    }

    @Override
    public void unregisterForRadioStateChanged(Handler h) {
        synchronized (mStateMonitor) {
            mRadioStateChangedRegistrants.remove(h);
        }
    }

    public void registerForImsNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mImsNetworkStateChangedRegistrants.add(r);
    }

    public void unregisterForImsNetworkStateChanged(Handler h) {
        mImsNetworkStateChangedRegistrants.remove(h);
    }

    @Override
    public void registerForOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mOnRegistrants.add(r);

            if (mState.isOn()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }
    @Override
    public void unregisterForOn(Handler h) {
        synchronized (mStateMonitor) {
            mOnRegistrants.remove(h);
        }
    }


    @Override
    public void registerForAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mAvailRegistrants.add(r);

            if (mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForAvailable(Handler h) {
        synchronized(mStateMonitor) {
            mAvailRegistrants.remove(h);
        }
    }

    @Override
    public void registerForNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mNotAvailRegistrants.add(r);

            if (!mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }

    @Override
    public void unregisterForNotAvailable(Handler h) {
        synchronized (mStateMonitor) {
            mNotAvailRegistrants.remove(h);
        }
    }

    @Override
    public void registerForOffOrNotAvailable(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        synchronized (mStateMonitor) {
            mOffOrNotAvailRegistrants.add(r);

            if (mState == RadioState.RADIO_OFF || !mState.isAvailable()) {
                r.notifyRegistrant(new AsyncResult(null, null, null));
            }
        }
    }
    @Override
    public void unregisterForOffOrNotAvailable(Handler h) {
        synchronized(mStateMonitor) {
            mOffOrNotAvailRegistrants.remove(h);
        }
    }

    @Override
    public void registerForCallStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mCallStateRegistrants.add(r);
    }

    @Override
    public void unregisterForCallStateChanged(Handler h) {
        mCallStateRegistrants.remove(h);
    }

    @Override
    public void registerForVoiceNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mVoiceNetworkStateRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceNetworkStateChanged(Handler h) {
        mVoiceNetworkStateRegistrants.remove(h);
    }

    @Override
    public void registerForDataNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mDataNetworkStateRegistrants.add(r);
    }

    @Override
    public void unregisterForDataNetworkStateChanged(Handler h) {
        mDataNetworkStateRegistrants.remove(h);
    }

    @Override
    public void registerForVoiceRadioTechChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoiceRadioTechChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceRadioTechChanged(Handler h) {
        mVoiceRadioTechChangedRegistrants.remove(h);
    }

    @Override
    public void registerForIccStatusChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mIccStatusChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForIccStatusChanged(Handler h) {
        mIccStatusChangedRegistrants.remove(h);
    }

    @Override
    public void setOnNewGsmSms(Handler h, int what, Object obj) {
        mGsmSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewGsmSms(Handler h) {
        if (mGsmSmsRegistrant != null && mGsmSmsRegistrant.getHandler() == h) {
            mGsmSmsRegistrant.clear();
            mGsmSmsRegistrant = null;
        }
    }

    @Override
    public void setOnNewCdmaSms(Handler h, int what, Object obj) {
        mCdmaSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewCdmaSms(Handler h) {
        if (mCdmaSmsRegistrant != null && mCdmaSmsRegistrant.getHandler() == h) {
            mCdmaSmsRegistrant.clear();
            mCdmaSmsRegistrant = null;
        }
    }

    @Override
    public void setOnNewGsmBroadcastSms(Handler h, int what, Object obj) {
        mGsmBroadcastSmsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNewGsmBroadcastSms(Handler h) {
        if (mGsmBroadcastSmsRegistrant != null && mGsmBroadcastSmsRegistrant.getHandler() == h) {
            mGsmBroadcastSmsRegistrant.clear();
            mGsmBroadcastSmsRegistrant = null;
        }
    }

    @Override
    public void setOnSmsOnSim(Handler h, int what, Object obj) {
        mSmsOnSimRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSmsOnSim(Handler h) {
        if (mSmsOnSimRegistrant != null && mSmsOnSimRegistrant.getHandler() == h) {
            mSmsOnSimRegistrant.clear();
            mSmsOnSimRegistrant = null;
        }
    }

    @Override
    public void setOnSmsStatus(Handler h, int what, Object obj) {
        mSmsStatusRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSmsStatus(Handler h) {
        if (mSmsStatusRegistrant != null && mSmsStatusRegistrant.getHandler() == h) {
            mSmsStatusRegistrant.clear();
            mSmsStatusRegistrant = null;
        }
    }

    @Override
    public void setOnSignalStrengthUpdate(Handler h, int what, Object obj) {
        mSignalStrengthRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSignalStrengthUpdate(Handler h) {
        if (mSignalStrengthRegistrant != null && mSignalStrengthRegistrant.getHandler() == h) {
            mSignalStrengthRegistrant.clear();
            mSignalStrengthRegistrant = null;
        }
    }

    @Override
    public void setOnNITZTime(Handler h, int what, Object obj) {
        mNITZTimeRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnNITZTime(Handler h) {
        if (mNITZTimeRegistrant != null && mNITZTimeRegistrant.getHandler() == h) {
            mNITZTimeRegistrant.clear();
            mNITZTimeRegistrant = null;
        }
    }

    @Override
    public void setOnUSSD(Handler h, int what, Object obj) {
        mUSSDRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnUSSD(Handler h) {
        if (mUSSDRegistrant != null && mUSSDRegistrant.getHandler() == h) {
            mUSSDRegistrant.clear();
            mUSSDRegistrant = null;
        }
    }

    @Override
    public void setOnSuppServiceNotification(Handler h, int what, Object obj) {
        mSsnRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSuppServiceNotification(Handler h) {
        if (mSsnRegistrant != null && mSsnRegistrant.getHandler() == h) {
            mSsnRegistrant.clear();
            mSsnRegistrant = null;
        }
    }

    @Override
    public void setOnCatSessionEnd(Handler h, int what, Object obj) {
        mCatSessionEndRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatSessionEnd(Handler h) {
        if (mCatSessionEndRegistrant != null && mCatSessionEndRegistrant.getHandler() == h) {
            mCatSessionEndRegistrant.clear();
            mCatSessionEndRegistrant = null;
        }
    }

    @Override
    public void setOnCatProactiveCmd(Handler h, int what, Object obj) {
        mCatProCmdRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatProactiveCmd(Handler h) {
        if (mCatProCmdRegistrant != null && mCatProCmdRegistrant.getHandler() == h) {
            mCatProCmdRegistrant.clear();
            mCatProCmdRegistrant = null;
        }
    }

    @Override
    public void setOnCatEvent(Handler h, int what, Object obj) {
        mCatEventRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatEvent(Handler h) {
        if (mCatEventRegistrant != null && mCatEventRegistrant.getHandler() == h) {
            mCatEventRegistrant.clear();
            mCatEventRegistrant = null;
        }
    }

    @Override
    public void setOnCatCallSetUp(Handler h, int what, Object obj) {
        mCatCallSetUpRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatCallSetUp(Handler h) {
        if (mCatCallSetUpRegistrant != null && mCatCallSetUpRegistrant.getHandler() == h) {
            mCatCallSetUpRegistrant.clear();
            mCatCallSetUpRegistrant = null;
        }
    }

    // For Samsung STK
    public void setOnCatSendSmsResult(Handler h, int what, Object obj) {
        mCatSendSmsResultRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCatSendSmsResult(Handler h) {
        mCatSendSmsResultRegistrant.clear();
    }

    @Override
    public void setOnIccSmsFull(Handler h, int what, Object obj) {
        mIccSmsFullRegistrant = new Registrant (h, what, obj);

        // MTK-START, SMS part
        if (mIsSmsSimFull == true) {
            mIccSmsFullRegistrant.notifyRegistrant();
            // Already notify, set as false. Because there is no URC to notify avaliable and
            // only one module will register. Looks like a workaround solution and make it easy
            mIsSmsSimFull = false;
        }
        // MTK-END, SMS part
    }

    @Override
    public void unSetOnIccSmsFull(Handler h) {
        if (mIccSmsFullRegistrant != null && mIccSmsFullRegistrant.getHandler() == h) {
            mIccSmsFullRegistrant.clear();
            mIccSmsFullRegistrant = null;
        }
    }

    @Override
    public void registerForIccRefresh(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mIccRefreshRegistrants.add(r);
    }
    @Override
    public void setOnIccRefresh(Handler h, int what, Object obj) {
        registerForIccRefresh(h, what, obj);
    }

    @Override
    public void setEmergencyCallbackMode(Handler h, int what, Object obj) {
        mEmergencyCallbackModeRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unregisterForIccRefresh(Handler h) {
        mIccRefreshRegistrants.remove(h);
    }
    @Override
    public void unsetOnIccRefresh(Handler h) {
        unregisterForIccRefresh(h);
    }

    @Override
    public void setOnCallRing(Handler h, int what, Object obj) {
        mRingRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCallRing(Handler h) {
        if (mRingRegistrant != null && mRingRegistrant.getHandler() == h) {
            mRingRegistrant.clear();
            mRingRegistrant = null;
        }
    }

    @Override
    public void setOnSs(Handler h, int what, Object obj) {
        mSsRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnSs(Handler h) {
        mSsRegistrant.clear();
    }

    @Override
    public void setOnCatCcAlphaNotify(Handler h, int what, Object obj) {
        mCatCcAlphaRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnCatCcAlphaNotify(Handler h) {
        mCatCcAlphaRegistrant.clear();
    }

    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoicePrivacyOnRegistrants.add(r);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mVoicePrivacyOnRegistrants.remove(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mVoicePrivacyOffRegistrants.add(r);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mVoicePrivacyOffRegistrants.remove(h);
    }

    @Override
    public void setOnRestrictedStateChanged(Handler h, int what, Object obj) {
        mRestrictedStateRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnRestrictedStateChanged(Handler h) {
        if (mRestrictedStateRegistrant != null && mRestrictedStateRegistrant.getHandler() != h) {
            mRestrictedStateRegistrant.clear();
            mRestrictedStateRegistrant = null;
        }
    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mDisplayInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForDisplayInfo(Handler h) {
        mDisplayInfoRegistrants.remove(h);
    }

    @Override
    public void registerForCallWaitingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCallWaitingInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForCallWaitingInfo(Handler h) {
        mCallWaitingInfoRegistrants.remove(h);
    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSignalInfoRegistrants.add(r);
    }

    public void setOnUnsolOemHookRaw(Handler h, int what, Object obj) {
        mUnsolOemHookRawRegistrant = new Registrant (h, what, obj);
    }

    public void unSetOnUnsolOemHookRaw(Handler h) {
        if (mUnsolOemHookRawRegistrant != null && mUnsolOemHookRawRegistrant.getHandler() == h) {
            mUnsolOemHookRawRegistrant.clear();
            mUnsolOemHookRawRegistrant = null;
        }
    }

    @Override
    public void unregisterForSignalInfo(Handler h) {
        mSignalInfoRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaOtaProvision(Handler h,int what, Object obj){
        Registrant r = new Registrant (h, what, obj);
        mOtaProvisionRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaOtaProvision(Handler h){
        mOtaProvisionRegistrants.remove(h);
    }

    @Override
    public void registerForNumberInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNumberInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForNumberInfo(Handler h){
        mNumberInfoRegistrants.remove(h);
    }

     @Override
    public void registerForRedirectedNumberInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRedirNumInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {
        mRedirNumInfoRegistrants.remove(h);
    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mLineControlInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {
        mLineControlInfoRegistrants.remove(h);
    }

    @Override
    public void registerFoT53ClirlInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mT53ClirInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {
        mT53ClirInfoRegistrants.remove(h);
    }

    @Override
    public void registerForT53AudioControlInfo(Handler h,int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mT53AudCntrlInfoRegistrants.add(r);
    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {
        mT53AudCntrlInfoRegistrants.remove(h);
    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRingbackToneRegistrants.add(r);
    }

    @Override
    public void unregisterForRingbackTone(Handler h) {
        mRingbackToneRegistrants.remove(h);
    }

    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mResendIncallMuteRegistrants.add(r);
    }

    @Override
    public void unregisterForResendIncallMute(Handler h) {
        mResendIncallMuteRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCdmaSubscriptionChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaSubscriptionChanged(Handler h) {
        mCdmaSubscriptionChangedRegistrants.remove(h);
    }

    @Override
    public void registerForCdmaPrlChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mCdmaPrlChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForCdmaPrlChanged(Handler h) {
        mCdmaPrlChangedRegistrants.remove(h);
    }

    @Override
    public void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mExitEmergencyCallbackModeRegistrants.add(r);
    }

    @Override
    public void unregisterForExitEmergencyCallbackMode(Handler h) {
        mExitEmergencyCallbackModeRegistrants.remove(h);
    }

    @Override
    public void registerForHardwareConfigChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mHardwareConfigChangeRegistrants.add(r);
    }

    @Override
    public void unregisterForHardwareConfigChanged(Handler h) {
        mHardwareConfigChangeRegistrants.remove(h);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerForRilConnected(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRilConnectedRegistrants.add(r);
        if (mRilVersion != -1) {
            r.notifyRegistrant(new AsyncResult(null, new Integer(mRilVersion), null));
        }
    }

    @Override
    public void unregisterForRilConnected(Handler h) {
        mRilConnectedRegistrants.remove(h);
    }

    public void registerForSubscriptionStatusChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mSubscriptionStatusRegistrants.add(r);
    }

    public void unregisterForSubscriptionStatusChanged(Handler h) {
        mSubscriptionStatusRegistrants.remove(h);
    }

    //***** Protected Methods
    /**
     * Store new RadioState and send notification based on the changes
     *
     * This function is called only by RIL.java when receiving unsolicited
     * RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED
     *
     * RadioState has 3 values : RADIO_OFF, RADIO_UNAVAILABLE, RADIO_ON.
     *
     * @param newState new RadioState decoded from RIL_UNSOL_RADIO_STATE_CHANGED
     */
    protected void setRadioState(RadioState newState) {
        RadioState oldState;

        synchronized (mStateMonitor) {
            oldState = mState;
            mState = newState;

            if (oldState == mState) {
                // no state transition
                return;
            }

            mRadioStateChangedRegistrants.notifyRegistrants();

            if (mState.isAvailable() && !oldState.isAvailable()) {
                mAvailRegistrants.notifyRegistrants();
                onRadioAvailable();
            }

            if (!mState.isAvailable() && oldState.isAvailable()) {
                mNotAvailRegistrants.notifyRegistrants();
            }

            if (mState.isOn() && !oldState.isOn()) {
                mOnRegistrants.notifyRegistrants();
            }

            if ((!mState.isOn() || !mState.isAvailable())
                && !((!oldState.isOn() || !oldState.isAvailable()))
            ) {
                mOffOrNotAvailRegistrants.notifyRegistrants();
            }
        }
    }

    public void sendSMSExpectMore (String smscPDU, String pdu, Message result) {
    }

    protected void onRadioAvailable() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLteOnCdmaMode() {
        return TelephonyManager.getLteOnCdmaModeStatic();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerForCellInfoList(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mRilCellInfoListRegistrants.add(r);
    }
    @Override
    public void unregisterForCellInfoList(Handler h) {
        mRilCellInfoListRegistrants.remove(h);
    }

    @Override
    public void registerForSrvccStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);

        mSrvccStateRegistrants.add(r);
    }

    @Override
    public void unregisterForSrvccStateChanged(Handler h) {
        mSrvccStateRegistrants.remove(h);
    }

    @Override
    public void testingEmergencyCall() {}

    @Override
    public int getRilVersion() {
        return mRilVersion;
    }

    public void setUiccSubscription(int appIndex, boolean activate, Message response) {
    }

    public void setDataAllowed(boolean allowed, Message response) {
    }

    @Override
    public void requestShutdown(Message result) {
    }

    @Override
    public void getRadioCapability(Message result) {
    }

    @Override
    public void setRadioCapability(RadioCapability rc, Message response) {
    }

    @Override
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPhoneRadioCapabilityChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForRadioCapabilityChanged(Handler h) {
        mPhoneRadioCapabilityChangedRegistrants.remove(h);
    }

    @Override
    public void startLceService(int reportIntervalMs, boolean pullMode, Message result) {
    }

    @Override
    public void stopLceService(Message result) {
    }

    @Override
    public void pullLceData(Message result) {
    }

    @Override
    public void registerForLceInfo(Handler h, int what, Object obj) {
      mLceInfoRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unregisterForLceInfo(Handler h) {
      if (mLceInfoRegistrant != null && mLceInfoRegistrant.getHandler() == h) {
          mLceInfoRegistrant.clear();
          mLceInfoRegistrant = null;
      }
    }

    @Override
    public void setLocalCallHold(boolean lchStatus) {
    }

    @Override
    public void iccOpenLogicalChannel(String AID, Message response) {}

    @Override
    public void iccOpenLogicalChannel(String AID, byte p2, Message response) {}

    @Override
    public void iccCloseLogicalChannel(int channel, Message response) {}

    @Override
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
                                              int p1, int p2, int p3, String data,
                                              Message response) {}
    @Override
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
                                            int p3, String data, Message response) {}

    @Override
    public void getAtr(Message response) {}

    /**
     * @hide
     */
    @Override
    public int getLteOnGsmMode() {
        return TelephonyManager.getLteOnGsmModeStatic();
    }

    // MTK

    //MTK-START Support Multi-Application
    protected RegistrantList mSessionChangedRegistrants = new RegistrantList();
    //MTK-END Support Multi-Application

    protected Registrant mStkEvdlCallRegistrant;
    protected Registrant mStkSetupMenuResetRegistrant;
    protected Registrant mStkCallCtrlRegistrant;

    /// M: CC010: Add RIL interface @{
    protected RegistrantList mCallForwardingInfoRegistrants = new RegistrantList();
    protected Registrant mCallRelatedSuppSvcRegistrant;
    protected Registrant mIncomingCallIndicationRegistrant;
    //protected Registrant mCnapNotifyRegistrant; //obsolete
    protected RegistrantList mCipherIndicationRegistrant = new RegistrantList();
    /// @}
    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    protected Registrant mSpeechCodecInfoRegistrant;
    /// @}

    //Remote SIM ME lock related APIs [Start]
    protected RegistrantList mMelockRegistrants = new RegistrantList();
    //Remote SIM ME lock related APIs [End]

    protected RegistrantList mPhbReadyRegistrants = new RegistrantList();

    /// M: IMS feature. @{
    /* Register for updating call ids for conference call after SRVCC is done. */
    protected RegistrantList mEconfSrvccRegistrants = new RegistrantList();
    /* Register for updating conference call merged/added result. */
    protected RegistrantList mEconfResultRegistrants = new RegistrantList();
    /* Register for updating call mode and pau. */
    protected RegistrantList mCallInfoRegistrants = new RegistrantList();
    /// @}

    // M: fast dormancy.
    protected Registrant mScriResultRegistrant;
    // M: CC33 LTE.
    protected RegistrantList mRacUpdateRegistrants = new RegistrantList();
    protected RegistrantList mRemoveRestrictEutranRegistrants = new RegistrantList();

    protected RegistrantList mResetAttachApnRegistrants = new RegistrantList();

    /// M: [C2K] for eng mode
    protected RegistrantList mEngModeNetworkInfoRegistrant = new RegistrantList();

    /// M: [C2K] for call fade
    protected Registrant mCdmaSignalFadeRegistrant;
    /// M: [C2K] for tone signal
    protected Registrant mCdmaToneSignalsRegistrant;
    /// M: BIP {
    protected Registrant mBipProCmdRegistrant;
    /// M: BIP }

    // Mode of Xtk, Stk or Utk. Set by SvlteRatController
    protected int mStkSwitchMode;
    // xTK BIP PS Type. Set by SvltePhoneProxy
    protected int mBipPsType;
    // for capability switch to early get modem capability
    RadioCapability mRadioCapability;

    /* M: network part start */
    protected RegistrantList mFemtoCellInfoRegistrants = new RegistrantList();
    protected RegistrantList mNeighboringInfoRegistrants = new RegistrantList();
    protected RegistrantList mNetworkInfoRegistrants = new RegistrantList();
    protected RegistrantList mNetworkExistRegistrants = new RegistrantList();

    protected RegistrantList mPlmnChangeNotificationRegistrant = new RegistrantList();
    protected Registrant mRegistrationSuspendedRegistrant;
    protected Object mEmsrReturnValue = null;
    protected Object mEcopsReturnValue = null;
    protected Object mWPMonitor = new Object();

    //VoLTE
    protected RegistrantList mImsEnableRegistrants = new RegistrantList();
    protected RegistrantList mImsDisableRegistrants = new RegistrantList();
    protected RegistrantList mImsRegistrationInfoRegistrants = new RegistrantList();
    protected RegistrantList mDedicateBearerActivatedRegistrant = new RegistrantList();
    protected RegistrantList mDedicateBearerModifiedRegistrant = new RegistrantList();
    protected RegistrantList mDedicateBearerDeactivatedRegistrant = new RegistrantList();

    protected RegistrantList mPsNetworkStateRegistrants = new RegistrantList();
    protected RegistrantList mImeiLockRegistrant = new RegistrantList();
    protected RegistrantList mInvalidSimInfoRegistrant = new RegistrantList();
    protected RegistrantList mGetAvailableNetworkDoneRegistrant = new RegistrantList();
    /* M: network part end */

    /// M: CC010: Add RIL interface @{
    protected Object mCfuReturnValue = null; ///* M: SS part */
    /// @}

    // MTK-START, SMS part
    // In order to cache the event from modem at boot-up sequence
    protected boolean mIsSmsSimFull = false;
    protected boolean mIsSmsReady = false;
    protected RegistrantList mSmsReadyRegistrants = new RegistrantList();
    protected Registrant mMeSmsFullRegistrant;
    protected Registrant mEtwsNotificationRegistrant;
    // MTK-END

    // IMS VoLTE
    protected RegistrantList mEpsNetworkFeatureSupportRegistrants = new RegistrantList();
    protected RegistrantList mEpsNetworkFeatureInfoRegistrants = new RegistrantList();
    protected RegistrantList mSrvccHandoverInfoIndicationRegistrants = new RegistrantList();
    protected RegistrantList mMoDataBarringInfoRegistrants = new RegistrantList();
    protected RegistrantList mSsacBarringInfoRegistrants = new RegistrantList();
    /// M: CC071: Add Customer proprietary-IMS RIL interface. @{
    protected RegistrantList mEmergencyBearerSupportInfoRegistrants = new RegistrantList();
    /// @}

    /* C2K part start */
    protected RegistrantList mViaGpsEvent = new RegistrantList();
    protected RegistrantList mAcceptedRegistrant = new RegistrantList();
    protected RegistrantList mNetworkTypeChangedRegistrant = new RegistrantList();
    protected Registrant mUtkSessionEndRegistrant;
    protected Registrant mUtkProCmdRegistrant;
    protected Registrant mUtkEventRegistrant;
    protected RegistrantList mInvalidSimDetectedRegistrant = new RegistrantList();

    /// M: [C2K][IR] Support SVLTE IR feature. @{
    protected RegistrantList mMccMncChangeRegistrants = new RegistrantList();
    /// M: [C2K][IR] Support SVLTE IR feature. @}

    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
    protected RegistrantList mGmssRatChangedRegistrant = new RegistrantList();
    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

    /// M: [C2K] for ps type changed.
    protected RegistrantList mDataNetworkTypeChangedRegistrant = new RegistrantList();

    /// M: [C2K][MD IRAT] add IRat state change registrant.
    protected RegistrantList mIratStateChangeRegistrant = new RegistrantList();

    /* C2K part end */

    protected RegistrantList mAbnormalEventRegistrant = new RegistrantList();

    /// M: For 3G VT only @{
    protected RegistrantList mVtStatusInfoRegistrants = new RegistrantList();
    protected RegistrantList mVtRingRegistrants = new RegistrantList();
    /// @}

    protected RegistrantList mCdmaImsiReadyRegistrant = new RegistrantList();
    protected RegistrantList mImsiRefreshDoneRegistrant = new RegistrantList();

    // M: [LTE][Low Power][UL traffic shaping] Start
    protected RegistrantList mLteAccessStratumStateRegistrants = new RegistrantList();
    // M: [LTE][Low Power][UL traffic shaping] End

    /// M: BIP {
    @Override
    public void setOnBipProactiveCmd(Handler h, int what, Object obj) {
        mBipProCmdRegistrant = new Registrant (h, what, obj);
    }

    @Override
    public void unSetOnBipProactiveCmd(Handler h) {
        if (mBipProCmdRegistrant != null && mBipProCmdRegistrant.getHandler() == h) {
            mBipProCmdRegistrant.clear();
            mBipProCmdRegistrant = null;
        }
    }
    /// M: BIP }

    @Override
    public void setStkEvdlCallByAP(int enabled, Message response) {
    }


    @Override
    public void setOnStkEvdlCall(Handler h, int what, Object obj) {
        mStkEvdlCallRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnStkEvdlCall(Handler h) {
        mStkEvdlCallRegistrant.clear();
    }

    @Override
    public void setOnStkSetupMenuReset(Handler h, int what, Object obj) {
        mStkSetupMenuResetRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnStkSetupMenuReset(Handler h) {
        mStkSetupMenuResetRegistrant.clear();
    }

    @Override
    public void setOnStkCallCtrl(Handler h, int what, Object obj) {
        mStkCallCtrlRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnStkCallCtrl(Handler h) {
        mStkCallCtrlRegistrant.clear();
    }

    //MTK-START [mtk06800] modem power on/off
    @Override
    public void setModemPower(boolean power, Message response) {
    }
    //MTK-END [mtk06800] modem power on/off

    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus,
            Message response) {
    }

    /// M: CC010: Add RIL interface @{
    public void registerForCipherIndication(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCipherIndicationRegistrant.add(r);
    }

    public void unregisterForCipherIndication(Handler h) {
        mCipherIndicationRegistrant.remove(h);
    }

    public void registerForCallForwardingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCallForwardingInfoRegistrants.add(r);
        /* M: SS part */
        if (mCfuReturnValue != null) {
           r.notifyRegistrant(new AsyncResult(null, mCfuReturnValue, null));
        }
        /* M: SS part end */
    }

    public void unregisterForCallForwardingInfo(Handler h) {
        mCallForwardingInfoRegistrants.remove(h);
    }

    public void setOnCallRelatedSuppSvc(Handler h, int what, Object obj) {
        mCallRelatedSuppSvcRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnCallRelatedSuppSvc(Handler h) {
        mCallRelatedSuppSvcRegistrant.clear();
    }

    public void setOnIncomingCallIndication(Handler h, int what, Object obj) {
        mIncomingCallIndicationRegistrant = new Registrant(h, what, obj);
    }

    public void unsetOnIncomingCallIndication(Handler h) {
        mIncomingCallIndicationRegistrant.clear();
    }

    //obsolete
    /*
    public void setCnapNotify(Handler h, int what, Object obj) {
        mCnapNotifyRegistrant = new Registrant(h, what, obj);
    }

    public void unSetCnapNotify(Handler h) {
        mCnapNotifyRegistrant.clear();
    }
    */
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    @Override
    public void setOnSpeechCodecInfo(Handler h, int what, Object obj) {
        mSpeechCodecInfoRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnSpeechCodecInfo(Handler h) {
        if (mSpeechCodecInfoRegistrant != null && mSpeechCodecInfoRegistrant.getHandler() == h) {
            mSpeechCodecInfoRegistrant.clear();
            mSpeechCodecInfoRegistrant = null;
        }
    }
    /// @}

    public void hangupAll(Message result) {}
    public void forceReleaseCall(int index, Message response) {}
    public void setCallIndication(int mode, int callId, int seqNumber, Message response) {}
    public void emergencyDial(String address, int clirMode, UUSInfo uusInfo, Message result) {}
    public void setEccServiceCategory(int serviceCategory) {}
    /// @}
    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    public void setSpeechCodecInfo(boolean enable, Message response) {}
    /// @}

    /// M: For 3G VT only @{
    public void registerForVtStatusInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVtStatusInfoRegistrants.add(r);
    }

    public void unregisterForVtStatusInfo(Handler h) {
        mVtStatusInfoRegistrants.remove(h);
    }

    public void registerForVtRingInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVtRingRegistrants.add(r);
    }

    public void unregisterForVtRingInfo(Handler h) {
        mVtRingRegistrants.remove(h);
    }
    /// @}

    /// M: For 3G VT only @{
    public void vtDial(String address, int clirMode, UUSInfo uusInfo, Message result) {}
    public void acceptVtCallWithVoiceOnly(int callId, Message result) {}
    public void replaceVtCall(int index, Message result) {}
    /// @}
    /* M: SS part  */
    ///M: For query CNAP
    public void sendCNAPSS(String cnapssString, Message response){}
    public void setCLIP(boolean enable, Message response) {}
    /* M: SS part end */

    //MTK-START Support Multi-Application
    @Override
    public void openIccApplication(int application, Message response){}
    @Override
    public void getIccApplicationStatus(int sessionId, Message result){}

    @Override
    public void registerForSessionChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSessionChangedRegistrants.add(r);
    }

    @Override
    public void unregisterForSessionChanged(Handler h) {
        mSessionChangedRegistrants.remove(h);
    }
    //MTK-END Support Multi-Application

    //MTK-START Support SIM ME lock
    @Override
    public void queryNetworkLock(int categrory, Message response){};

    @Override
    public void setNetworkLock(int catagory, int lockop, String password,
            String data_imsi, String gid1, String gid2, Message response){};
    //MTK-END Support SIM ME lock

    @Override
    public void doGeneralSimAuthentication(int sessionId, int mode , int tag, String param1,
                                          String param2, Message response) {
    }
    // Added by M begin

    protected RegistrantList mSimMissing = new RegistrantList();
    protected RegistrantList mSimRecovery = new RegistrantList();
    protected RegistrantList mVirtualSimOn = new RegistrantList();
    protected RegistrantList mVirtualSimOff = new RegistrantList();
    protected RegistrantList mSimPlugOutRegistrants = new RegistrantList();
    protected RegistrantList mSimPlugInRegistrants = new RegistrantList();
    protected RegistrantList mTrayPlugInRegistrants = new RegistrantList();
    protected RegistrantList mCdmaCardTypeRegistrants = new RegistrantList();
    protected RegistrantList mCommonSlotNoChangedRegistrants = new RegistrantList();
    protected RegistrantList mDataAllowedRegistrants = new RegistrantList();
    protected RegistrantList mEusimReady = new RegistrantList();
    protected boolean mIsEusimReady = false;
    protected Object mCdmaCardTypeValue = null;

    public void registerForSimMissing(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSimMissing.add(r);
    }
    public void unregisterForSimMissing(Handler h) {
        mSimMissing.remove(h);
    }

    public void registerForSimRecovery(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSimRecovery.add(r);
    }

    public void unregisterForSimRecovery(Handler h) {
        mSimRecovery.remove(h);
    }

    public void registerForVirtualSimOn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVirtualSimOn.add(r);
    }

    public void unregisterForVirtualSimOn(Handler h) {
        mVirtualSimOn.remove(h);
    }

    public void registerForVirtualSimOff(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVirtualSimOff.add(r);
    }

    public void unregisterForVirtualSimOff(Handler h) {
        mVirtualSimOff.remove(h);
    }

    public void registerForSimPlugOut(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSimPlugOutRegistrants.add(r);
    }

    public void unregisterForSimPlugOut(Handler h) {
        mSimPlugOutRegistrants.remove(h);
    }

    public void registerForSimPlugIn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSimPlugInRegistrants.add(r);
    }

    public void unregisterForSimPlugIn(Handler h) {
        mSimPlugInRegistrants.remove(h);
    }

    public void registerForTrayPlugIn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mTrayPlugInRegistrants.add(r);
    }

    public void unregisterForTrayPlugIn(Handler h) {
        mTrayPlugInRegistrants.remove(h);
    }
    /**
      * Rregister for cdma card type.
      * @param h Handler for network information messages.
      * @param what User-defined message code.
      * @param obj User object.
      */
    public void registerForCdmaCardType(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCdmaCardTypeRegistrants.add(r);

        if (mCdmaCardTypeValue != null) {
            r.notifyRegistrant(new AsyncResult(null, mCdmaCardTypeValue, null));
        }
    }

    /**
      * Rregister for cdma card type.
      * @param h Handler for network information messages.
      */
    public void unregisterForCdmaCardType(Handler h) {
        mCdmaCardTypeRegistrants.remove(h);
    }

    public void registerForCommonSlotNoChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCommonSlotNoChangedRegistrants.add(r);
    }

    public void unregisterForCommonSlotNoChanged(Handler h) {
        mCommonSlotNoChangedRegistrants.remove(h);
    }

    public void registerForEusimReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEusimReady.add(r);
    }

    public void unregisterForEusimReady(Handler h) {
        mEusimReady.remove(h);
    }

    /* M: CC33 LTE. */
    public void setDataOnToMD(boolean enable, Message result){}
    public void setRemoveRestrictEutranMode(boolean enable, Message result){}
    public void registerForRemoveRestrictEutran(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mRemoveRestrictEutranRegistrants.add(r);
    }
    public void unregisterForRemoveRestrictEutran(Handler h) {
        mRemoveRestrictEutranRegistrants.remove(h);
    }
    public void registerForRacUpdate(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);

        mRacUpdateRegistrants.add(r);
    }
    public void unregisterForRacUpdate(Handler h) {
        mRacUpdateRegistrants.remove(h);
    }

    public void registerForResetAttachApn(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mResetAttachApnRegistrants.add(r);
    }

    public void unregisterForResetAttachApn(Handler h) {
        mResetAttachApnRegistrants.remove(h);
    }

    public void registerSetDataAllowed(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataAllowedRegistrants.add(r);
    }

    public void unregisterSetDataAllowed(Handler h) {
        mDataAllowedRegistrants.remove(h);
    }

    public void sendBTSIMProfile(int nAction, int nType, String strData, Message response){
    }

    protected Registrant mEfCspPlmnModeBitRegistrant;

    public void registerForEfCspPlmnModeBitChanged(Handler h, int what, Object obj) {
        mEfCspPlmnModeBitRegistrant = new Registrant(h, what, obj);
    }

    public void unregisterForEfCspPlmnModeBitChanged(Handler h) {
        mEfCspPlmnModeBitRegistrant.clear();
    }

    public void queryPhbStorageInfo(int type, Message response) {
    }

    public void writePhbEntry(PhbEntry entry, Message result) {
    }

    public void ReadPhbEntry(int type, int bIndex, int eIndex, Message response) {
    }

    public void registerForPhbReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mPhbReadyRegistrants.add(r);
    }

    public void unregisterForPhbReady(Handler h) {
        mPhbReadyRegistrants.remove(h);
    }

    public void queryUPBCapability(Message response){
    }

    public void editUPBEntry(int entryType, int adnIndex, int entryIndex, String strVal, String tonForNum, Message response) {
    }

    public void deleteUPBEntry(int entryType, int adnIndex, int entryIndex, Message response) {
    }

    public void readUPBGasList(int startIndex, int endIndex, Message response) {
    }

    public void readUPBGrpEntry(int adnIndex, Message response) {
    }

    public void writeUPBGrpEntry(int adnIndex, int[] grpIds, Message response) {
    }

    public void getPhoneBookStringsLength(Message result) {

    }
    public void getPhoneBookMemStorage(Message result) {

    }
    public void setPhoneBookMemStorage(String storage, String password, Message result) {
    }

    // xen0n: MTK TODO
    /*
    public void readPhoneBookEntryExt(int index1, int index2, Message result) {
    }

    public void writePhoneBookEntryExt(PBEntry entry, Message result) {
    }
    */

    // M: [LTE][Low Power][UL traffic shaping] Start
    public void setLteAccessStratumReport(boolean enable, Message result) {
    }

    public void setLteUplinkDataTransfer(int state, int interfaceId, Message result) {
    }

    public void registerForLteAccessStratumState(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mLteAccessStratumStateRegistrants.add(r);
    }

    public void unregisterForLteAccessStratumState(Handler h) {
        mLteAccessStratumStateRegistrants.remove(h);
    }
    // M: [LTE][Low Power][UL traffic shaping] End

    // Added by M end

    // MTK-START, SMS part
    public void registerForSmsReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSmsReadyRegistrants.add(r);

        if (mIsSmsReady == true) {
            // Only notify the new registrant
            r.notifyRegistrant();
        }
    }

    public void unregisterForSmsReady(Handler h) {
        mSmsReadyRegistrants.remove(h);
    }

    public void setOnMeSmsFull(Handler h, int what, Object obj) {
        mMeSmsFullRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnMeSmsFull(Handler h) {
        mMeSmsFullRegistrant.clear();
    }

    // xen0n: MTK TODO
    /*
    public void getSmsParameters(Message response) {
    }

    public void setSmsParameters(SmsParameters params, Message response) {
    }
    */

    public void setEtws(int mode, Message result) {
    }

    public void setOnEtwsNotification(Handler h, int what, Object obj) {
        mEtwsNotificationRegistrant = new Registrant(h, what, obj);
    }

    public void unSetOnEtwsNotification(Handler h) {
        mEtwsNotificationRegistrant.clear();
    }

    public void setCellBroadcastChannelConfigInfo(String config, int cb_set_type,
            Message response) {
    }

    public void setCellBroadcastLanguageConfigInfo(String config, Message response) {
    }

    public void queryCellBroadcastConfigInfo(Message response) {
    }

    public void removeCellBroadcastMsg(int channelId, int serialId, Message response) {
    }

    public void getSmsSimMemoryStatus(Message result) {
    }
    // MTK-END, SMS part

    public void registerForNeighboringInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mNeighboringInfoRegistrants.add(r);
    }

    public void unregisterForNeighboringInfo(Handler h) {
        mNeighboringInfoRegistrants.remove(h);
    }

    public void registerForNetworkInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mNetworkInfoRegistrants.add(r);
    }

    public void unregisterForNetworkInfo(Handler h) {
        mNetworkInfoRegistrants.remove(h);
    }

    public void setInvalidSimInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mInvalidSimInfoRegistrant.add(r);
    }

    public void unSetInvalidSimInfo(Handler h) {
        mInvalidSimInfoRegistrant.remove(h);
    }

    public void registerForIMEILock(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mImeiLockRegistrant.add(r);
    }

    public void unregisterForIMEILock(Handler h) {
        mImeiLockRegistrant.remove(h);
    }

    public void setNetworkSelectionModeManualWithAct(String operatorNumeric,
                                                                  String act, Message result) {
    }

    public void setNetworkSelectionModeSemiAutomatic(String operatorNumeric, String act, Message response) {

    }

    public void cancelAvailableNetworks(Message response) {}

    public void registerForGetAvailableNetworksDone(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mGetAvailableNetworkDoneRegistrant.add(r);
    }

    public void unregisterForGetAvailableNetworksDone(Handler h) {
        mGetAvailableNetworkDoneRegistrant.remove(h);
    }

    public void getPOLCapabilty(Message response) {
    }
    public void getCurrentPOLList(Message response) {
    }
    public void setPOLEntry(int index, String numeric, int nAct, Message response) {
    }

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric, int rat, Message response){}
    public void abortFemtoCellList(Message response){}
    public void selectFemtoCell(FemtoCellInfo femtocell, Message response){}
    public void registerForFemtoCellInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);

        mFemtoCellInfoRegistrants.add(r);
    }

    public void registerForPsNetworkStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);

        mPsNetworkStateRegistrants.add(r);
    }

    public void unregisterForPsNetworkStateChanged(Handler h) {
        mPsNetworkStateRegistrants.remove(h);
    }

    public boolean isGettingAvailableNetworks() { return false; }

    public void unregisterForFemtoCellInfo(Handler h) {
        mFemtoCellInfoRegistrants.remove(h);
    }

    // IMS
    public void registerForImsEnable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mImsEnableRegistrants.add(r);
    }

    public void unregisterForImsEnable(Handler h) {
        mImsEnableRegistrants.remove(h);
    }

    public void registerForImsDisable(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mImsDisableRegistrants.add(r);
    }

    public void unregisterForImsDisable(Handler h) {
        mImsDisableRegistrants.remove(h);
    }

    public void registerForImsRegistrationInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mImsRegistrationInfoRegistrants.add(r);
    }

    public void unregisterForImsRegistrationInfo(Handler h) {
        mImsRegistrationInfoRegistrants.remove(h);
    }

    public void setIMSEnabled(boolean enable, Message response){}
    public void registerForImsDisableDone(Handler h, int what, Object obj){}
    public void unregisterForImsDisableDone(Handler h){}

    public void setTrm(int mode, Message result) {}

    public void setOnPlmnChangeNotification(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
         synchronized (mWPMonitor) {
            mPlmnChangeNotificationRegistrant.add(r);

            if (mEcopsReturnValue != null) {
               // Only notify the new registrant
               r.notifyRegistrant(new AsyncResult(null, mEcopsReturnValue, null));
               mEcopsReturnValue = null;
            }
        }
    }

    public void unSetOnPlmnChangeNotification(Handler h) {
        synchronized (mWPMonitor) {
            mPlmnChangeNotificationRegistrant.remove(h);
        }
    }

    public void setOnRegistrationSuspended(Handler h, int what, Object obj) {
        synchronized (mWPMonitor) {
            mRegistrationSuspendedRegistrant = new Registrant(h, what, obj);

            if (mEmsrReturnValue != null) {
                // Only notify the new registrant
                mRegistrationSuspendedRegistrant.notifyRegistrant(
                    new AsyncResult(null, mEmsrReturnValue, null));
                mEmsrReturnValue = null;
            }
        }
    }

    public void unSetOnRegistrationSuspended(Handler h) {
        synchronized (mWPMonitor) {
            mRegistrationSuspendedRegistrant.clear();
        }
    }

    //Remote SIM ME lock related APIs [Start]
    public void registerForMelockChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mMelockRegistrants.add(r);
    }

    public void unregisterForMelockChanged(Handler h) {
        mMelockRegistrants.remove(h);
    }
    //Remote SIM ME lock related APIs [End]

    /** M: start */
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, Message result) {
    }

    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, String interfaceId, Message result) {
    }

    // M: fast dormancy
    public void setScriResult(Handler h, int what, Object obj) {
        mScriResultRegistrant = new Registrant(h, what, obj);
    }

    public void unSetScriResult(Handler h) {
        mScriResultRegistrant.clear();
    }

    public void setScri(boolean forceRelease, Message response){
    }

    public void setFDMode(int mode, int parameter1, int parameter2, Message response){
    }


    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Object obj, Message result) {
    }
    /** M: end */

    // For IMS VoLTE, EPS network feature support
    public void registerForEpsNetworkFeatureSupport(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEpsNetworkFeatureSupportRegistrants.add(r);
    }

    public void unregisterForEpsNetworkFeatureSupport(Handler h) {
        mEpsNetworkFeatureSupportRegistrants.remove(h);
    }

    /// M: IMS feature. @{
    /* Register for updating call ids for conference call after SRVCC is done. */
    public void registerForEconfSrvcc(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEconfSrvccRegistrants.add(r);
    }

    public void unregisterForEconfSrvcc(Handler h) {
        mEconfSrvccRegistrants.remove(h);
    }

    /* Register for updating conference call merged/added result. */
    public void registerForEconfResult(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEconfResultRegistrants.add(r);
    }

    public void unregisterForEconfResult(Handler h) {
        mEconfResultRegistrants.remove(h);
    }

    public void registerForCallInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCallInfoRegistrants.add(r);
    }

    public void unregisterForCallInfo(Handler h) {
        mCallInfoRegistrants.remove(h);
    }


    /* Add/Remove VoLTE(IMS) conference call member. */
    public void addConferenceMember(int confCallId, String address, int callIdToAdd, Message response) {}
    public void removeConferenceMember(int confCallId, String address, int callIdToRemove, Message response) {}

    /**
     * To resume the call.
     * @param callIdToResume toIndicate which call session to resume.
     * @param response command response.
     */
    public void resumeCall(int callIdToResume, Message response) {}

    /**
     * To hold the call.
     * @param callIdToHold toIndicate which call session to hold.
     * @param response command response.
     */
    public void holdCall(int callIdToHold, Message response) {}
    /// @}

    public void registerForEpsNetworkFeatureInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEpsNetworkFeatureInfoRegistrants.add(r);
    }

    public void unregisterForEpsNetworkFeatureInfo(Handler h) {
        mEpsNetworkFeatureInfoRegistrants.remove(h);
    }

    public void registerForMoDataBarringInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mMoDataBarringInfoRegistrants.add(r);
    }

    public void unregisterForMoDataBarringInfo(Handler h) {
        mMoDataBarringInfoRegistrants.remove(h);
    }

    public void registerForSsacBarringInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSsacBarringInfoRegistrants.add(r);
    }

    public void unregisterForSsacBarringInfo(Handler h) {
        mSsacBarringInfoRegistrants.remove(h);
    }

    public void registerForSrvccHandoverInfoIndication(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mSrvccHandoverInfoIndicationRegistrants.add(r);
    }
    public void unregisterForSrvccHandoverInfoIndication(Handler h) {
        mSrvccHandoverInfoIndicationRegistrants.remove(h);
    }

    /// M: CC071: Add Customer proprietary-IMS RIL interface. @{
    public void registerForEmergencyBearerSupportInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEmergencyBearerSupportInfoRegistrants.add(r);
    }

    public void unregisterForEmergencyBearerSupportInfo(Handler h) {
        mEmergencyBearerSupportInfoRegistrants.remove(h);
    }
    /// @}

    public void sendScreenState(boolean on){}

    @Override
    public void setDataCentric(boolean enable, Message response) {}

    /// M: CC010: Add RIL interface @{
    @Override
    public void setImsCallStatus(boolean existed, Message response) {}
    /// @}

    /// M: CC072: Add Customer proprietary-IMS RIL interface. @{
    /**
     * Transfer IMS call to modem.
     *
     * @param numberOfCall The number of call
     * @param callList IMS call context
     */
     public void setSrvccCallContextTransfer(int numberOfCall, SrvccCallContext[] callList) {}

    /**
     * Update IMS registration status to modem.
     *
     * @param regState IMS registration state
     *                 0: IMS unregistered
     *                 1: IMS registered
     * @param regType  IMS registration type
     *                 0: Normal IMS registration
     *                 1: Emergency IMS registration
     * @param reason   The reason of state transition from registered to unregistered
     *                 0: Unspecified
     *                 1: Power off
     *                 2: RF off
     */
     public void updateImsRegistrationStatus(int regState, int regType, int reason) {}
     /// @}

    /* C2K part start */
    @Override
    public void setViaTRM(int mode, Message result) {}

    @Override
    public void getNitzTime(Message result) {}

    @Override
    public void requestSwitchHPF(boolean enableHPF, Message response) {}

    @Override
    public void setAvoidSYS(boolean avoidSYS, Message response) {}

    @Override
    public void getAvoidSYSList(Message response) {}

    @Override
    public void queryCDMANetworkInfo(Message response) {}

    @Override
    public void setOplmn(String oplmnInfo, Message response) {
    }

    @Override
    public void getOplmnVersion(Message response) {
    }

    @Override
    public void registerForCallAccepted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mAcceptedRegistrant.add(r);
    }

    @Override
    public void unregisterForCallAccepted(Handler h) {
        mAcceptedRegistrant.remove(h);
    }

    @Override
    public void registerForViaGpsEvent(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mViaGpsEvent.add(r);
    }

    @Override
    public void unregisterForViaGpsEvent(Handler h) {
        mViaGpsEvent.remove(h);
    }

    @Override
    public void setMeid(String meid, Message response) {}

    @Override
    public void setArsiReportThreshold(int threshold, Message response) {}

    @Override
    public void registerForNetworkTypeChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mNetworkTypeChangedRegistrant.add(r);
    }

    @Override
    public void unregisterForNetworkTypeChanged(Handler h) {
        mNetworkTypeChangedRegistrant.remove(h);
    }

    @Override
    public void queryCDMASmsAndPBStatus(Message response) {}

    @Override
    public void queryCDMANetWorkRegistrationState(Message response) {}

    @Override
    public void requestSetEtsDev(int dev, Message result) {}

    @Override
    public void requestAGPSGetMpcIpPort(Message result) {}

    @Override
    public void requestAGPSSetMpcIpPort(String ip, String port, Message result) {}

    @Override
    public void requestAGPSTcpConnected(int connected, Message result) {}

    @Override
    public void setMdnNumber(String mdn, Message response) {}

    // UTK start
    @Override
    public void setOnUtkSessionEnd(Handler h, int what, Object obj) {
        mUtkSessionEndRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnUtkSessionEnd(Handler h) {
        mUtkSessionEndRegistrant.clear();
    }

    @Override
    public void setOnUtkProactiveCmd(Handler h, int what, Object obj) {
        mUtkProCmdRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnUtkProactiveCmd(Handler h) {
        mUtkProCmdRegistrant.clear();
    }

    @Override
    public void setOnUtkEvent(Handler h, int what, Object obj) {
        mUtkEventRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnUtkEvent(Handler h) {
        mUtkEventRegistrant.clear();
    }
    //UTK end

    //C2K SVLTE remote SIM access
    @Override
    public void configModemStatus(int modemStatus, int remoteSimProtocol, Message result) {}

    @Override
    public void disconnectRilSocket() {}

    @Override
    public void connectRilSocket() {}

    @Override
    public void configEvdoMode(int evdoMode, Message result) {}
    /* C2k part end */

    public void registerForAbnormalEvent(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mAbnormalEventRegistrant.add(r);
    }

    public void unregisterForAbnormalEvent(Handler h) {
        mAbnormalEventRegistrant.remove(h);
    }

    /// M: [C2K] for eng mode start
    @Override
    public void registerForEngModeNetworkInfo(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mEngModeNetworkInfoRegistrant.add(r);
    }

    @Override
    public void unregisterForEngModeNetworkInfo(Handler h) {
        mEngModeNetworkInfoRegistrant.remove(h);
    }
    /// M: [C2K] for eng mode end

    public int getDisplayState() {
        //return Display type: Unknown display type.
        return 0;
    }

    public String lookupOperatorNameFromNetwork(long subId, String numeric, boolean desireLongName) {
        // return operator name from network: null string
        return null;
    }

    /* M: IMS VoLTE conference dial feature start*/
    /**
     * Dial conference call.
     * @param participants participants' dailing number.
     * @param clirMode indication to present the dialing number or not.
     * @param isVideoCall indicate this call is belong to video call or voice call.
     * @param result the command result.
     */
    public void conferenceDial(String[] participants, int clirMode,
            boolean isVideoCall, Message result) {}
    /* IMS VoLTE conference dial feature end*/

    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
    @Override
    public void registerForGmssRatChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mGmssRatChangedRegistrant.add(r);
    }

    @Override
    public void unregisterForGmssRatChanged(Handler h) {
        mGmssRatChangedRegistrant.remove(h);
    }
    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

    /// M: [C2K] for ps type changed. @{
    @Override
    public void registerForDataNetworkTypeChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mDataNetworkTypeChangedRegistrant.add(r);
    }

    @Override
    public void unregisterForDataNetworkTypeChanged(Handler h) {
        mDataNetworkTypeChangedRegistrant.remove(h);
    }
    /// @}

    /// [C2K][IRAT] @{
    @Override
    public void registerForIratStateChanged(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mIratStateChangeRegistrant.add(r);
    }

    @Override
    public void unregisterForIratStateChanged(Handler h) {
        mIratStateChangeRegistrant.remove(h);
    }

    @Override
    public void confirmIratChange(int apDecision, Message result) {

    }

    @Override
    public void requestSetPsActiveSlot(int psSlot, Message response) {
    }

    @Override
    public void syncNotifyDataCallList(AsyncResult dcList) {

    }

    @Override
    public void requestDeactivateLinkDownPdn(Message response) {

    }
    /// @}

    @Override
    public void registerForImsiRefreshDone(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mImsiRefreshDoneRegistrant.add(r);
    }

    @Override
    public void unregisterForImsiRefreshDone(Handler h) {
        mImsiRefreshDoneRegistrant.remove(h);
    }

    @Override
    public RadioCapability getBootupRadioCapability() {
        Rlog.d("RILJ", "getBootupRadioCapability: " + mRadioCapability);
        return mRadioCapability;
    }

    @Override
    public void registerForCdmaImsiReady(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mCdmaImsiReadyRegistrant.add(r);
    }

    @Override
    public void unregisterForCdmaImsiReady(Handler h) {
        mCdmaImsiReadyRegistrant.remove(h);
    }

    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @{
    @Override
    public void setSvlteRatMode(int radioTechMode, int preSvlteMode, int svlteMode,
            int preRoamingMode, int roamingMode, boolean is3GDualModeCard, Message response) {
    }
    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @}

    /// M: [C2K][SVLTE] Set the STK UTK mode. @{
    public void setStkUtkMode(int stkUtkMode, Message response) {
    }
    /// M: [C2K][SVLTE] Set the STK UTK mode. @}

    /// M: [C2K][SVLTE] Update RIL instance id for SVLTE switch ActivePhone. @{
    @Override
    public void setInstanceId(int instanceId) {
    }
    /// @}

    /// M: [C2K][IR] Support SVLTE IR feature. @{

    @Override
    public void setRegistrationSuspendEnabled(int enabled, Message response) {
    }

    @Override
    public void setResumeRegistration(int sessionId, Message response) {
    }

    @Override
    public void setCdmaRegistrationSuspendEnabled(boolean enabled, Message response) {
    }

    @Override
    public void setResumeCdmaRegistration(Message response) {
    }

    @Override
    public void registerForMccMncChange(Handler h, int what, Object obj) {
        Rlog.d(RIL.RILJ_LOG_TAG, "registerForMccMncChange h=" + h + " w=" + what);
        Registrant r = new Registrant(h, what, obj);
        mMccMncChangeRegistrants.add(r);
    }

    @Override
    public void unregisterForMccMncChange(Handler h) {
        Rlog.d(RIL.RILJ_LOG_TAG, "unregisterForMccMncChange");
        mMccMncChangeRegistrants.remove(h);
    }

    @Override
    public void queryStkSetUpMenuFromMD(String contents, Message response) {}

    /// M: [C2K][IR] Support SVLTE IR feature. @}

    /// M: [C2K] Support Signal Fade. @{
    @Override
    public void setOnCdmaSignalFade(Handler h, int what, Object obj) {
        mCdmaSignalFadeRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnCdmaSignalFade(Handler h) {
        if (mCdmaSignalFadeRegistrant != null && mCdmaSignalFadeRegistrant.getHandler() == h) {
            mCdmaSignalFadeRegistrant.clear();
            mCdmaSignalFadeRegistrant = null;
        }
    }
    /// @}

    /// M: [C2K] Support Tone Signal. @{
    @Override
    public void setOnCdmaToneSignal(Handler h, int what, Object obj) {
        mCdmaToneSignalsRegistrant = new Registrant(h, what, obj);
    }

    @Override
    public void unSetOnCdmaToneSignal(Handler h) {
        if (mCdmaToneSignalsRegistrant != null && mCdmaToneSignalsRegistrant.getHandler() == h) {
            mCdmaToneSignalsRegistrant.clear();
            mCdmaToneSignalsRegistrant = null;
        }
    }
    /// @}

    @Override
    public void switchAntenna(int callState, int ratMode) {}
    @Override
    public void switchCardType(int cardtype) {}

    @Override
    public void enableMd3Sleep(int enable) {
    }

    @Override
    public void registerForNetworkExsit(Handler h, int what, Object obj) {
        Rlog.d(RIL.RILJ_LOG_TAG, "registerForNetworkExsit h=" + h + " w=" + what);
        Registrant r = new Registrant(h, what, obj);
        mNetworkExistRegistrants.add(r);
    }

    @Override
    public void unregisterForNetworkExsit(Handler h) {
        Rlog.d(RIL.RILJ_LOG_TAG, "registerForNetworkExsit");
        mNetworkExistRegistrants.remove(h);
    }
}
