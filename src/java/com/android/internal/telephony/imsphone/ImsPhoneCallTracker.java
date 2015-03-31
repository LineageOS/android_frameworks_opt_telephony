/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.internal.telephony.imsphone;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.widget.Toast;
import android.preference.PreferenceManager;
import android.telecom.ConferenceParticipant;
import android.telecom.VideoProfile;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.ServiceState;

import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.ims.ImsConfig;
import com.android.ims.ImsConnectionStateListener;
import com.android.ims.ImsEcbm;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.ims.ImsReasonInfo;
import com.android.ims.ImsServiceClass;
import com.android.ims.ImsUtInterface;
import com.android.ims.internal.CallGroup;
import com.android.ims.internal.IImsVideoCallProvider;
import com.android.ims.internal.ImsCallSession;
import com.android.ims.internal.ImsVideoCallProviderWrapper;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CallTracker;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import android.widget.Toast;

/**
 * {@hide}
 */
public final class ImsPhoneCallTracker extends CallTracker {
    static final String LOG_TAG = "ImsPhoneCallTracker";

    private static final boolean DBG = true;

    private boolean mIsVolteEnabled = false;
    private boolean mIsVtEnabled = false;
    private boolean mIsUtEnabled = false;
    private boolean mIsSrvccCompleted = false;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ImsManager.ACTION_IMS_INCOMING_CALL)) {
                if (DBG) log("onReceive : incoming call intent");

                if (mImsManager == null) return;

                if (mServiceId < 0) return;

                // Very rare case, something wrong here. Most probably, it's Incoming call
                // indication from lower layer after SRVCC call successfully transfered to CS.
                // Ignore the call indication.
                if (mIsSrvccCompleted) return;

                try {
                    // Network initiated USSD will be treated by mImsUssdListener
                    boolean isUssd = intent.getBooleanExtra(ImsManager.EXTRA_USSD, false);
                    if (isUssd) {
                        if (DBG) log("onReceive : USSD");
                        mUssdSession = mImsManager.takeCall(mServiceId, intent, mImsUssdListener);
                        if (mUssdSession != null) {
                            mUssdSession.accept(ImsCallProfile.CALL_TYPE_VOICE);
                        }
                        return;
                    }

                    boolean isUnknown = intent.getBooleanExtra(ImsManager.EXTRA_IS_UNKNOWN_CALL,
                            false);
                    int phantomState = intent.getIntExtra(ImsManager.EXTRA_UNKNOWN_CALL_STATE, 0);
                    String address = intent.getStringExtra(ImsManager.EXTRA_UNKNOWN_CALL_ADDRESS);

                    if (DBG) {
                        log("onReceive : isUnknown = " + isUnknown +
                                " state = " + phantomState +
                                " fg = " + mForegroundCall.getState() +
                                " bg = " + mBackgroundCall.getState() +
                                " address = " + address);
                    }

                    // Normal MT/Unknown call
                    ImsCall imsCall = mImsManager.takeCall(mServiceId, intent, mImsCallListener);
                    ImsPhoneCall.State state = convertIntToCallState(phantomState);

                    ImsPhoneCall call = null;

                    if (!isUnknown) {
                        call = mRingingCall;
                    } else if ((isUnknown) && (state == ImsPhoneCall.State.HOLDING)) {
                        call = mBackgroundCall;
                    } else {
                        call = mForegroundCall;
                    }

                    ImsPhoneConnection conn = new ImsPhoneConnection(mPhone.getContext(), imsCall,
                            ImsPhoneCallTracker.this, call, isUnknown, state, address);

                    addConnection(conn);

                    // Updates mHold value in ImsCall for phantom held call scenario
                    if (isUnknown && (state == ImsPhoneCall.State.HOLDING)) {
                        imsCall.updateHoldValues();
                    }
                    setVideoCallProvider(conn, imsCall);

                    if (isUnknown) {
                        mPhone.notifyUnknownConnection(conn);
                    } else {
                        if ((mForegroundCall.getState() != ImsPhoneCall.State.IDLE) ||
                                (mBackgroundCall.getState() != ImsPhoneCall.State.IDLE)) {
                            conn.update(imsCall, ImsPhoneCall.State.WAITING);
                        }

                        mPhone.notifyNewRingingConnection(conn);
                        mPhone.notifyIncomingRing();
                    }

                    updatePhoneState();
                    mPhone.notifyPreciseCallStateChanged();
                } catch (ImsException e) {
                    loge("onReceive : exception " + e);
                } catch (RemoteException e) {
                }
            }
        }
    };

    private ImsPhoneCall.State convertIntToCallState(int state) {
        switch (state) {
            case ImsManager.CALL_ACTIVE:        return ImsPhoneCall.State.ACTIVE;
            case ImsManager.CALL_HOLD:          return ImsPhoneCall.State.HOLDING;
            case ImsManager.CALL_DIALING:       return ImsPhoneCall.State.DIALING;
            case ImsManager.CALL_ALERTING:      return ImsPhoneCall.State.ALERTING;
            case ImsManager.CALL_INCOMING:      return ImsPhoneCall.State.INCOMING;
            case ImsManager.CALL_WAITING:       return ImsPhoneCall.State.WAITING;
            default:
                {
                    log("convertIntToCallState: illegal call state:" + state);
                    return ImsPhoneCall.State.INCOMING;
                }
        }
    }

    //***** Constants

    static final int MAX_CONNECTIONS = 7;
    static final int MAX_CONNECTIONS_PER_CALL = 5;

    private static final int EVENT_HANGUP_PENDINGMO = 18;
    private static final int EVENT_RESUME_BACKGROUND = 19;
    private static final int EVENT_DIAL_PENDINGMO = 20;

    private static final int TIMEOUT_HANGUP_PENDINGMO = 500;

    //***** Instance Variables
    private ArrayList<ImsPhoneConnection> mConnections = new ArrayList<ImsPhoneConnection>();
    private RegistrantList mVoiceCallEndedRegistrants = new RegistrantList();
    private RegistrantList mVoiceCallStartedRegistrants = new RegistrantList();

    ImsPhoneCall mRingingCall = new ImsPhoneCall(this);
    ImsPhoneCall mForegroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mBackgroundCall = new ImsPhoneCall(this);
    ImsPhoneCall mHandoverCall = new ImsPhoneCall(this);

    private ImsPhoneConnection mPendingMO;
    private int mClirMode = CommandsInterface.CLIR_DEFAULT;
    private Object mSyncHold = new Object();

    private ImsCall mUssdSession = null;
    private Message mPendingUssd = null;

    ImsPhone mPhone;

    private boolean mDesiredMute = false;    // false = mute off
    private boolean mOnHoldToneStarted = false;

    PhoneConstants.State mState = PhoneConstants.State.IDLE;

    private ImsManager mImsManager;
    private int mServiceId = -1;

    private Call.SrvccState mSrvccState = Call.SrvccState.NONE;

    private boolean mIsInEmergencyCall = false;

    private int pendingCallClirMode;
    private int mPendingCallVideoState;
    private boolean pendingCallInEcm = false;
    private long mConferenceTime = 0;

    //***** Events


    //***** Constructors

    ImsPhoneCallTracker(ImsPhone phone) {
        this.mPhone = phone;

        IntentFilter intentfilter = new IntentFilter();
        intentfilter.addAction(ImsManager.ACTION_IMS_INCOMING_CALL);
        mPhone.getContext().registerReceiver(mReceiver, intentfilter);

        Thread t = new Thread() {
            public void run() {
                getImsService();
            }
        };
        t.start();
    }

    private PendingIntent createIncomingCallPendingIntent() {
        Intent intent = new Intent(ImsManager.ACTION_IMS_INCOMING_CALL);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(mPhone.getContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void getImsService() {
        if (DBG) log("getImsService");
        mImsManager = ImsManager.getInstance(mPhone.getContext(), mPhone.getPhoneId());
        try {
            mServiceId = mImsManager.open(ImsServiceClass.MMTEL,
                    createIncomingCallPendingIntent(),
                    mImsConnectionStateListener);

            // Get the ECBM interface and set IMSPhone's listener object for notifications
            getEcbmInterface().setEcbmStateListener(mPhone.mImsEcbmStateListener);
            if (mPhone.isInEcm()) {
                // Call exit ECBM which will invoke onECBMExited
                mPhone.exitEmergencyCallbackMode();
            }
            int mPreferredTtyMode = Settings.Secure.getInt(
                mPhone.getContext().getContentResolver(),
                Settings.Secure.PREFERRED_TTY_MODE,
                Phone.TTY_MODE_OFF);
           mImsManager.setUiTTYMode(mPhone.getContext(), mServiceId, mPreferredTtyMode, null);

        } catch (ImsException e) {
            loge("getImsService: " + e);
            //Leave mImsManager as null, then CallStateException will be thrown when dialing
            mImsManager = null;
        }
    }

    public void dispose() {
        if (DBG) log("dispose");
        mRingingCall.dispose();
        mBackgroundCall.dispose();
        mForegroundCall.dispose();
        mHandoverCall.dispose();

        clearDisconnected();
        mPhone.getContext().unregisterReceiver(mReceiver);
    }

    @Override
    protected void finalize() {
        log("ImsPhoneCallTracker finalized");
    }

    //***** Instance Methods

    //***** Public Methods
    @Override
    public void registerForVoiceCallStarted(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVoiceCallStartedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceCallStarted(Handler h) {
        mVoiceCallStartedRegistrants.remove(h);
    }

    @Override
    public void registerForVoiceCallEnded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        mVoiceCallEndedRegistrants.add(r);
    }

    @Override
    public void unregisterForVoiceCallEnded(Handler h) {
        mVoiceCallEndedRegistrants.remove(h);
    }

    Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, videoState, null);
    }

    Connection
    dial(String dialString, int videoState, Bundle extras) throws CallStateException {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mPhone.getContext());
        int oirMode = sp.getInt(PhoneBase.CLIR_KEY, CommandsInterface.CLIR_DEFAULT);
        return dial(dialString, oirMode, videoState, extras);
    }

    /**
     * oirMode is one of the CLIR_ constants
     */
    synchronized Connection
    dial(String dialString, int clirMode, int videoState, Bundle extras) throws CallStateException {
        boolean isPhoneInEcmMode = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_INECM_MODE, false);
        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(dialString);

        if (DBG) log("dial clirMode=" + clirMode);

        // note that this triggers call state changed notif
        clearDisconnected();

        if (mImsManager == null) {
            throw new CallStateException("service not available");
        }

        if (!canDial()) {
            throw new CallStateException("cannot dial in current state");
        }

        if (isPhoneInEcmMode && isEmergencyNumber) {
            handleEcmTimer(ImsPhone.CANCEL_ECM_TIMER);
        }

        boolean holdBeforeDial = false;

        // The new call must be assigned to the foreground call.
        // That call must be idle, so place anything that's
        // there on hold
        if (mForegroundCall.getState() == ImsPhoneCall.State.ACTIVE) {
            if (mBackgroundCall.getState() != ImsPhoneCall.State.IDLE) {
                //we should have failed in !canDial() above before we get here
                throw new CallStateException("cannot dial in current state");
            }
            // foreground call is empty for the newly dialed connection
            holdBeforeDial = true;
            // Cache the video state for pending MO call.
            mPendingCallVideoState = videoState;
            switchWaitingOrHoldingAndActive();
        }

        ImsPhoneCall.State fgState = ImsPhoneCall.State.IDLE;
        ImsPhoneCall.State bgState = ImsPhoneCall.State.IDLE;

        mClirMode = clirMode;

        synchronized (mSyncHold) {
            if (holdBeforeDial) {
                fgState = mForegroundCall.getState();
                bgState = mBackgroundCall.getState();

                //holding foreground call failed
                if (fgState == ImsPhoneCall.State.ACTIVE) {
                    throw new CallStateException("cannot dial in current state");
                }

                //holding foreground call succeeded
                if (bgState == ImsPhoneCall.State.HOLDING) {
                    holdBeforeDial = false;
                }
            }

            mPendingMO = new ImsPhoneConnection(mPhone.getContext(),
                    checkForTestEmergencyNumber(dialString), this,
                    mForegroundCall, extras);
        }
        addConnection(mPendingMO);

        if (!holdBeforeDial) {
            if ((!isPhoneInEcmMode) || (isPhoneInEcmMode && isEmergencyNumber)) {
                dialInternal(mPendingMO, clirMode, videoState, extras);
            } else {
                try {
                    getEcbmInterface().exitEmergencyCallbackMode();
                } catch (ImsException e) {
                    e.printStackTrace();
                    throw new CallStateException("service not available");
                }
                mPhone.setOnEcbModeExitResponse(this, EVENT_EXIT_ECM_RESPONSE_CDMA, null);
                pendingCallClirMode = clirMode;
                mPendingCallVideoState = videoState;
                pendingCallInEcm = true;
            }
        }

        updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();

        return mPendingMO;
    }

    public void
    addParticipant(String dialString) throws CallStateException {
        if (mForegroundCall != null) {
            ImsCall imsCall = mForegroundCall.getImsCall();
            if (imsCall == null) {
                loge("addParticipant : No foreground ims call");
            } else {
                ImsCallSession imsCallSession = imsCall.getCallSession();
                if (imsCallSession != null) {
                    String[] callees = new String[] { dialString };
                    imsCallSession.inviteParticipants(callees);
                } else {
                    loge("addParticipant : ImsCallSession does not exist");
                }
            }
        } else {
            loge("addParticipant : Foreground call does not exist");
        }
    }

    private void handleEcmTimer(int action) {
        mPhone.handleTimerInEmergencyCallbackMode(action);
        switch (action) {
            case ImsPhone.CANCEL_ECM_TIMER:
                break;
            case ImsPhone.RESTART_ECM_TIMER:
                break;
            default:
                log("handleEcmTimer, unsupported action " + action);
        }
    }

    private void
    dialInternal(ImsPhoneConnection conn, int clirMode, int videoState, Bundle extras) {
        if (conn == null) {
            return;
        }

        boolean isConferenceUri = false;
        boolean isSkipSchemaParsing = false;
        if (extras != null) {
            isConferenceUri = extras.getBoolean(TelephonyProperties.EXTRA_DIAL_CONFERENCE_URI,
                    false);
            isSkipSchemaParsing = extras.getBoolean(TelephonyProperties.EXTRA_SKIP_SCHEMA_PARSING,
                    false);
        }
        if (!isConferenceUri && !isSkipSchemaParsing && (conn.getAddress()== null
                || conn.getAddress().length() == 0
                || conn.getAddress().indexOf(PhoneNumberUtils.WILD) >= 0)) {
            // Phone number is invalid
            conn.setDisconnectCause(DisconnectCause.INVALID_NUMBER);
            sendEmptyMessageDelayed(EVENT_HANGUP_PENDINGMO, TIMEOUT_HANGUP_PENDINGMO);
            return;
        }

        // Always unmute when initiating a new call
        setMute(false);
        int serviceType = PhoneNumberUtils.isEmergencyNumber(conn.getAddress()) ?
                ImsCallProfile.SERVICE_TYPE_EMERGENCY : ImsCallProfile.SERVICE_TYPE_NORMAL;
        int callType = ImsCallProfile.getCallTypeFromVideoState(videoState);
        //TODO(vt): Is this sufficient?  At what point do we know the video state of the call?
        conn.setVideoState(videoState);

        try {
            String[] callees = new String[] { conn.getAddress() };
            ImsCallProfile profile = mImsManager.createCallProfile(mServiceId,
                    serviceType, callType);
            profile.setCallExtraInt(ImsCallProfile.EXTRA_OIR, clirMode);
            profile.setCallExtraBoolean(TelephonyProperties.EXTRAS_IS_CONFERENCE_URI,
                    isConferenceUri);

            if (extras != null) {
                // Pack the OEM-specific call extras.
                profile.mCallExtras.putBundle(ImsCallProfile.EXTRA_OEM_EXTRAS, extras);
                log("Packing OEM extras bundle in call profile.");
            } else {
                log("No dial extras packed in call profile.");
            }

            ImsCall imsCall = mImsManager.makeCall(mServiceId, profile,
                    callees, mImsCallListener);
            conn.setImsCall(imsCall);

            setVideoCallProvider(conn, imsCall);
        } catch (ImsException e) {
            loge("dialInternal : " + e);
            conn.setDisconnectCause(DisconnectCause.ERROR_UNSPECIFIED);
            sendEmptyMessageDelayed(EVENT_HANGUP_PENDINGMO, TIMEOUT_HANGUP_PENDINGMO);
        } catch (RemoteException e) {
        }
    }

    /**
     * Accepts a call with the specified video state.  The video state is the video state that the
     * user has agreed upon in the InCall UI.
     *
     * @param videoState The video State
     * @throws CallStateException
     */
    void acceptCall (int videoState) throws CallStateException {
        if (DBG) log("acceptCall");

        if (mForegroundCall.getState().isAlive()
                && mBackgroundCall.getState().isAlive()) {
            throw new CallStateException("cannot accept call");
        }

        if ((mRingingCall.getState() == ImsPhoneCall.State.WAITING)
                && mForegroundCall.getState().isAlive()) {
            setMute(false);
            // Cache video state for pending MT call.
            mPendingCallVideoState = videoState;
            switchWaitingOrHoldingAndActive();
        } else if (mRingingCall.getState().isRinging()) {
            if (DBG) log("acceptCall: incoming...");
            // Always unmute when answering a new call
            setMute(false);
            try {
                ImsCall imsCall = mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(ImsCallProfile.getCallTypeFromVideoState(videoState));
                } else {
                    throw new CallStateException("no valid ims call");
                }
            } catch (ImsException e) {
                throw new CallStateException("cannot accept call");
            }
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void
    deflectCall (String number) throws CallStateException {
        if (DBG) log("deflectCall");

        if (mRingingCall.getState().isRinging()) {
            try {
                ImsCall imsCall = mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.deflect(number);
                } else {
                    throw new CallStateException("no valid ims call to deflect");
                }
            } catch (ImsException e) {
                throw new CallStateException("cannot deflect call");
            }
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void
    rejectCall () throws CallStateException {
        if (DBG) log("rejectCall");

        if (mRingingCall.getState().isRinging()) {
            hangup(mRingingCall);
        } else {
            throw new CallStateException("phone not ringing");
        }
    }

    void
    switchWaitingOrHoldingAndActive() throws CallStateException {
        if (DBG) log("switchWaitingOrHoldingAndActive");

        if (mRingingCall.getState() == ImsPhoneCall.State.INCOMING) {
            throw new CallStateException("cannot be in the incoming state");
        }

        if (mForegroundCall.getState() == ImsPhoneCall.State.ACTIVE) {
            ImsCall imsCall = mForegroundCall.getImsCall();
            if (imsCall == null) {
                throw new CallStateException("no ims call");
            }

            mForegroundCall.switchWith(mBackgroundCall);

            try {
                imsCall.hold();
            } catch (ImsException e) {
                mForegroundCall.switchWith(mBackgroundCall);
                throw new CallStateException(e.getMessage());
            }
        } else if (mBackgroundCall.getState() == ImsPhoneCall.State.HOLDING) {
            resumeWaitingOrHolding();
        }
    }

    void
    conference() {
        if (DBG) log("conference");

        ImsCall fgImsCall = mForegroundCall.getImsCall();
        if (fgImsCall == null) {
            log("conference no foreground ims call");
            return;
        }

        ImsCall bgImsCall = mBackgroundCall.getImsCall();
        if (bgImsCall == null) {
            log("conference no background ims call");
            return;
        }
        mConferenceTime = Math.min(mBackgroundCall.getEarliestConnectTime(),
                mForegroundCall.getEarliestConnectTime());
        try {
            fgImsCall.merge(bgImsCall);
        } catch (ImsException e) {
            log("conference " + e.getMessage());
        }
    }

    void
    explicitCallTransfer() {
        //TODO : implement
    }

    void
    clearDisconnected() {
        if (DBG) log("clearDisconnected");

        internalClearDisconnected();

        updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();
    }

    boolean
    canConference() {
        return mForegroundCall.getState() == ImsPhoneCall.State.ACTIVE
            && mBackgroundCall.getState() == ImsPhoneCall.State.HOLDING
            && !mBackgroundCall.isFull()
            && !mForegroundCall.isFull();
    }

    boolean
    canDial() {
        boolean ret;
        int serviceState = mPhone.getServiceState().getState();
        String disableCall = SystemProperties.get(
                TelephonyProperties.PROPERTY_DISABLE_CALL, "false");

        Rlog.d(LOG_TAG, "canDial(): "
                + "\nserviceState = " + serviceState
                + "\npendingMO == null::=" + String.valueOf(mPendingMO == null)
                + "\nringingCall: " + mRingingCall.getState()
                + "\ndisableCall = " + disableCall
                + "\nforegndCall: " + mForegroundCall.getState()
                + "\nbackgndCall: " + mBackgroundCall.getState());

        ret = (serviceState != ServiceState.STATE_POWER_OFF)
            && mPendingMO == null
            && !mRingingCall.isRinging()
            && !disableCall.equals("true")
            && (!mForegroundCall.getState().isAlive()
                    || !mBackgroundCall.getState().isAlive());

        return ret;
    }

    boolean
    canTransfer() {
        return mForegroundCall.getState() == ImsPhoneCall.State.ACTIVE
            && mBackgroundCall.getState() == ImsPhoneCall.State.HOLDING;
    }

    //***** Private Instance Methods

    private void
    internalClearDisconnected() {
        mRingingCall.clearDisconnected();
        mForegroundCall.clearDisconnected();
        mBackgroundCall.clearDisconnected();
        mHandoverCall.clearDisconnected();
    }

    private void
    updatePhoneState() {
        PhoneConstants.State oldState = mState;

        if (mRingingCall.isRinging()) {
            mState = PhoneConstants.State.RINGING;
        } else if (mPendingMO != null ||
                !(mForegroundCall.isIdle() && mBackgroundCall.isIdle())) {
            mState = PhoneConstants.State.OFFHOOK;
        } else {
            mState = PhoneConstants.State.IDLE;
        }

        if (mState == PhoneConstants.State.IDLE && oldState != mState) {
            mVoiceCallEndedRegistrants.notifyRegistrants(
                    new AsyncResult(null, null, null));
        } else if (oldState == PhoneConstants.State.IDLE && oldState != mState) {
            mVoiceCallStartedRegistrants.notifyRegistrants (
                    new AsyncResult(null, null, null));
        }

        if (DBG) log("updatePhoneState oldState=" + oldState + ", newState=" + mState);

        if (mState != oldState) {
            mPhone.notifyPhoneStateChanged();
        }
    }

    private void
    handleRadioNotAvailable() {
        // handlePollCalls will clear out its
        // call list when it gets the CommandException
        // error result from this
        pollCallsWhenSafe();
    }

    private void
    dumpState() {
        List l;

        log("Phone State:" + mState);

        log("Ringing call: " + mRingingCall.toString());

        l = mRingingCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            log(l.get(i).toString());
        }

        log("Foreground call: " + mForegroundCall.toString());

        l = mForegroundCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            log(l.get(i).toString());
        }

        log("Background call: " + mBackgroundCall.toString());

        l = mBackgroundCall.getConnections();
        for (int i = 0, s = l.size(); i < s; i++) {
            log(l.get(i).toString());
        }

    }

    //***** Called from ImsPhone

    void setUiTTYMode(int uiTtyMode, Message onComplete) {
        try {
            mImsManager.setUiTTYMode(mPhone.getContext(), mServiceId, uiTtyMode, onComplete);
        } catch (ImsException e) {
            loge("setTTYMode : " + e);
            mPhone.sendErrorResponse(onComplete, e);
        }
    }

    /*package*/ void
    setMute(boolean mute) {
        mDesiredMute = mute;
        mForegroundCall.setMute(mute);
    }

    /*package*/ boolean
    getMute() {
        return mDesiredMute;
    }

    /*package*/ void
    sendDtmf(char c) {
        if (DBG) log("sendDtmf");

        ImsCall imscall = mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.sendDtmf(c);
        }
    }

    /*package*/ void
    sendDtmf(char c, Message result) {
        if (DBG) log("sendDtmf");

        ImsCall imscall = mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.sendDtmf(c, result);
        }
    }

    /*package*/ void
    startDtmf(char c) {
        if (DBG) log("startDtmf");

        ImsCall imscall = mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.startDtmf(c);
        } else {
            loge("startDtmf : no foreground call");
        }
    }

    /*package*/ void
    stopDtmf() {
        if (DBG) log("stopDtmf");

        ImsCall imscall = mForegroundCall.getImsCall();
        if (imscall != null) {
            imscall.stopDtmf();
        } else {
            loge("stopDtmf : no foreground call");
        }
    }

    //***** Called from ImsPhoneConnection

    /*package*/ void
    hangup (ImsPhoneConnection conn) throws CallStateException {
        if (DBG) log("hangup connection");

        if (conn.getOwner() != this) {
            throw new CallStateException ("ImsPhoneConnection " + conn
                    + "does not belong to ImsPhoneCallTracker " + this);
        }

        hangup(conn.getCall());
    }

    //***** Called from ImsPhoneCall

    /* package */ void
    hangup (ImsPhoneCall call) throws CallStateException {
        if (DBG) log("hangup call");

        if (call.getConnections().size() == 0) {
            throw new CallStateException("no connections");
        }

        ImsCall imsCall = call.getImsCall();
        boolean rejectCall = false;

        if (call == mRingingCall) {
            if (Phone.DEBUG_PHONE) log("(ringing) hangup incoming");
            rejectCall = true;
        } else if (call == mForegroundCall) {
            if (call.isDialingOrAlerting()) {
                if (Phone.DEBUG_PHONE) {
                    log("(foregnd) hangup dialing or alerting...");
                }
            } else {
                if (Phone.DEBUG_PHONE) {
                    log("(foregnd) hangup foreground");
                }
                //held call will be resumed by onCallTerminated
            }
        } else if (call == mBackgroundCall) {
            if (Phone.DEBUG_PHONE) {
                log("(backgnd) hangup waiting or background");
            }
        } else {
            throw new CallStateException ("ImsPhoneCall " + call +
                    "does not belong to ImsPhoneCallTracker " + this);
        }

        call.onHangupLocal();

        try {
            if (imsCall != null) {
                if (rejectCall) imsCall.reject(ImsReasonInfo.CODE_USER_DECLINE);
                else imsCall.terminate(ImsReasonInfo.CODE_USER_TERMINATED);
            } else if (mPendingMO != null && call == mForegroundCall) {
                // is holding a foreground call
                mPendingMO.update(null, ImsPhoneCall.State.DISCONNECTED);
                mPendingMO.onDisconnect();
                removeConnection(mPendingMO);
                mPendingMO = null;
                updatePhoneState();
                removeMessages(EVENT_DIAL_PENDINGMO);
            }
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }

        mPhone.notifyPreciseCallStateChanged();
    }

    /* package */
    void resumeWaitingOrHolding() throws CallStateException {
        if (DBG) log("resumeWaitingOrHolding");

        try {
            if (mForegroundCall.getState().isAlive()) {
                //resume foreground call after holding background call
                //they were switched before holding
                ImsCall imsCall = mForegroundCall.getImsCall();
                if (imsCall != null) imsCall.resume();
            } else if (mRingingCall.getState() == ImsPhoneCall.State.WAITING) {
                //accept waiting call after holding background call
                ImsCall imsCall = mRingingCall.getImsCall();
                if (imsCall != null) {
                    imsCall.accept(
                        ImsCallProfile.getCallTypeFromVideoState(mPendingCallVideoState));
                }
            } else {
                //Just resume background call.
                //To distinguish resuming call with swapping calls
                //we do not switch calls.here
                //ImsPhoneConnection.update will chnage the parent when completed
                ImsCall imsCall = mBackgroundCall.getImsCall();
                if (imsCall != null) imsCall.resume();
            }
        } catch (ImsException e) {
            throw new CallStateException(e.getMessage());
        }
    }

    /* package */
    void sendUSSD (String ussdString, Message response) {
        if (DBG) log("sendUSSD");

        try {
            if (mUssdSession != null) {
                mUssdSession.sendUssd(ussdString);
                AsyncResult.forMessage(response, null, null);
                response.sendToTarget();
                return;
            }

            String[] callees = new String[] { ussdString };
            ImsCallProfile profile = mImsManager.createCallProfile(mServiceId,
                    ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.CALL_TYPE_VOICE);
            profile.setCallExtraInt(ImsCallProfile.EXTRA_DIALSTRING,
                    ImsCallProfile.DIALSTRING_USSD);

            mUssdSession = mImsManager.makeCall(mServiceId, profile,
                    callees, mImsUssdListener);
        } catch (ImsException e) {
            loge("sendUSSD : " + e);
            mPhone.sendErrorResponse(response, e);
        }
    }

    /* package */
    void cancelUSSD() {
        if (mUssdSession == null) return;

        try {
            mUssdSession.terminate(ImsReasonInfo.CODE_USER_TERMINATED);
        } catch (ImsException e) {
        }

    }

    private synchronized ImsPhoneConnection findConnection(ImsCall imsCall) {
        for (ImsPhoneConnection conn : mConnections) {
            if (conn.getImsCall() == imsCall) {
                return conn;
            }
        }
        return null;
    }

    private synchronized void removeConnection(ImsPhoneConnection conn) {
        mConnections.remove(conn);
    }

    private synchronized void addConnection(ImsPhoneConnection conn) {
        mConnections.add(conn);
    }

    private void processCallStateChange(ImsCall imsCall, ImsPhoneCall.State state, int cause) {
        processCallStateChange(imsCall, state, cause, false);
    }

    private void processCallStateChange(ImsCall imsCall, ImsPhoneCall.State state, int cause,
            boolean ignoreState) {
        if (DBG) {
            log("processCallStateChange state=" + state + " cause=" + cause
                    + " ignoreState=" + ignoreState);
        }

        if (imsCall == null) return;

        boolean changed = false;
        ImsPhoneConnection conn = findConnection(imsCall);

        if (conn == null) {
            // TODO : what should be done?
            return;
        }

        if (ignoreState) {
            changed = conn.update(imsCall);
        } else {
            changed = conn.update(imsCall, state);
            if (state == ImsPhoneCall.State.DISCONNECTED) {
                changed = conn.onDisconnect(cause) || changed;
                //detach the disconnected connections
                conn.getCall().detach(conn);
                removeConnection(conn);
            }
        }

        if (changed) {
            if (conn.getCall() == mHandoverCall) return;
            updatePhoneState();
            mPhone.notifyPreciseCallStateChanged();
        }
    }

    private int getDisconnectCauseFromReasonInfo(ImsReasonInfo reasonInfo) {
        int cause = DisconnectCause.ERROR_UNSPECIFIED;

        //int type = reasonInfo.getReasonType();
        int code = reasonInfo.getCode();
        switch (code) {
            case ImsReasonInfo.CODE_SIP_BAD_ADDRESS:
            case ImsReasonInfo.CODE_SIP_NOT_REACHABLE:
                return DisconnectCause.NUMBER_UNREACHABLE;

            case ImsReasonInfo.CODE_SIP_BUSY:
                return DisconnectCause.BUSY;

            case ImsReasonInfo.CODE_USER_TERMINATED:
                return DisconnectCause.LOCAL;

            case ImsReasonInfo.CODE_LOCAL_CALL_DECLINE:
                return DisconnectCause.INCOMING_REJECTED;

            case ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE:
                return DisconnectCause.NORMAL;

            case ImsReasonInfo.CODE_SIP_REDIRECTED:
            case ImsReasonInfo.CODE_SIP_BAD_REQUEST:
            case ImsReasonInfo.CODE_SIP_FORBIDDEN:
            case ImsReasonInfo.CODE_SIP_NOT_ACCEPTABLE:
            case ImsReasonInfo.CODE_SIP_USER_REJECTED:
            case ImsReasonInfo.CODE_SIP_GLOBAL_ERROR:
                return DisconnectCause.SERVER_ERROR;

            case ImsReasonInfo.CODE_SIP_SERVICE_UNAVAILABLE:
            case ImsReasonInfo.CODE_SIP_NOT_FOUND:
            case ImsReasonInfo.CODE_SIP_SERVER_ERROR:
                return DisconnectCause.SERVER_UNREACHABLE;

            case ImsReasonInfo.CODE_LOCAL_NETWORK_ROAMING:
            case ImsReasonInfo.CODE_LOCAL_NETWORK_IP_CHANGED:
            case ImsReasonInfo.CODE_LOCAL_IMS_SERVICE_DOWN:
            case ImsReasonInfo.CODE_LOCAL_SERVICE_UNAVAILABLE:
            case ImsReasonInfo.CODE_LOCAL_NOT_REGISTERED:
            case ImsReasonInfo.CODE_LOCAL_NETWORK_NO_LTE_COVERAGE:
            case ImsReasonInfo.CODE_LOCAL_NETWORK_NO_SERVICE:
            case ImsReasonInfo.CODE_LOCAL_CALL_VCC_ON_PROGRESSING:
                return DisconnectCause.OUT_OF_SERVICE;

            case ImsReasonInfo.CODE_SIP_REQUEST_TIMEOUT:
            case ImsReasonInfo.CODE_TIMEOUT_1XX_WAITING:
            case ImsReasonInfo.CODE_TIMEOUT_NO_ANSWER:
            case ImsReasonInfo.CODE_TIMEOUT_NO_ANSWER_CALL_UPDATE:
                return DisconnectCause.TIMED_OUT;

            case ImsReasonInfo.CODE_LOCAL_LOW_BATTERY:
            case ImsReasonInfo.CODE_LOCAL_POWER_OFF:
                return DisconnectCause.POWER_OFF;

            case ImsReasonInfo.CODE_FDN_BLOCKED:
                return DisconnectCause.FDN_BLOCKED;
            default:
        }

        return cause;
    }

    /**
     * Listen to the IMS call state change
     */
    private ImsCall.Listener mImsCallListener = new ImsCall.Listener() {
        @Override
        public void onCallProgressing(ImsCall imsCall) {
            if (DBG) log("onCallProgressing");

            mPendingMO = null;
            processCallStateChange(imsCall, ImsPhoneCall.State.ALERTING,
                    DisconnectCause.NOT_DISCONNECTED);
        }

        @Override
        public void onCallStarted(ImsCall imsCall) {
            if (DBG) log("onCallStarted");

            mPendingMO = null;
            processCallStateChange(imsCall, ImsPhoneCall.State.ACTIVE,
                    DisconnectCause.NOT_DISCONNECTED);
        }

        @Override
        public void onCallUpdated(ImsCall imsCall) {
            if (DBG) log("onCallUpdated");
            if (imsCall == null) {
                return;
            }
            ImsPhoneConnection conn = findConnection(imsCall);
            if (conn != null) {
                processCallStateChange(imsCall, conn.getCall().mState,
                        DisconnectCause.NOT_DISCONNECTED, true);
            }
        }

        /**
         * onCallStartFailed will be invoked when:
         * case 1) Dialing fails
         * case 2) Ringing call is disconnected by local or remote user
         */
        @Override
        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            if (DBG) log("onCallStartFailed reasonCode=" + reasonInfo.getCode());

            if (mPendingMO != null) {
                // To initiate dialing circuit-switched call
                if (reasonInfo.getCode() == ImsReasonInfo.CODE_LOCAL_CALL_CS_RETRY_REQUIRED
                        && mBackgroundCall.getState() == ImsPhoneCall.State.IDLE
                        && mRingingCall.getState() == ImsPhoneCall.State.IDLE) {
                    mForegroundCall.detach(mPendingMO);
                    removeConnection(mPendingMO);
                    mPendingMO.finalize();
                    mPendingMO = null;
                    mPhone.initiateSilentRedial();
                    return;
                }
                mPendingMO.setDisconnectCause(DisconnectCause.ERROR_UNSPECIFIED);
                sendEmptyMessageDelayed(EVENT_HANGUP_PENDINGMO, TIMEOUT_HANGUP_PENDINGMO);
            }
        }

        @Override
        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            if (DBG) log("onCallTerminated reasonCode=" + reasonInfo.getCode());

            ImsPhoneCall.State oldState = mForegroundCall.getState();
            int cause = getDisconnectCauseFromReasonInfo(reasonInfo);
            ImsPhoneConnection conn = findConnection(imsCall);
            if (DBG) log("cause = " + cause + " conn = " + conn);

            if (conn != null && conn.isIncoming() && conn.getConnectTime() == 0) {
                // Missed
                if (cause == DisconnectCause.NORMAL) {
                    cause = DisconnectCause.INCOMING_MISSED;
                }
            }

            if (cause == DisconnectCause.NORMAL && conn != null && conn.getImsCall().isMerged()) {
                // Call was terminated while it is merged instead of a remote disconnect.
                cause = DisconnectCause.IMS_MERGED_SUCCESSFULLY;
            }

            processCallStateChange(imsCall, ImsPhoneCall.State.DISCONNECTED, cause);

            if (reasonInfo.getCode() == ImsReasonInfo.CODE_USER_TERMINATED) {
                if ((oldState == ImsPhoneCall.State.DISCONNECTING)
                        && (mForegroundCall.getState() == ImsPhoneCall.State.IDLE)
                        && (mBackgroundCall.getState() == ImsPhoneCall.State.HOLDING)) {
                    sendEmptyMessage(EVENT_RESUME_BACKGROUND);
                }
            }
            if (mForegroundCall.getState() != ImsPhoneCall.State.ACTIVE) {
                if (mRingingCall.getState().isRinging()) {
                    // Drop pending MO. We should address incoming call first
                    mPendingMO = null;
                } else if (mPendingMO != null) {
                    sendEmptyMessage(EVENT_DIAL_PENDINGMO);
                }
            }
        }

        @Override
        public void onCallHeld(ImsCall imsCall) {
            if (DBG) log("onCallHeld");

            synchronized (mSyncHold) {
                ImsPhoneCall.State oldState = mBackgroundCall.getState();
                processCallStateChange(imsCall, ImsPhoneCall.State.HOLDING,
                        DisconnectCause.NOT_DISCONNECTED);
                if (oldState == ImsPhoneCall.State.ACTIVE) {
                    if ((mForegroundCall.getState() == ImsPhoneCall.State.HOLDING)
                            || (mRingingCall.getState() == ImsPhoneCall.State.WAITING)) {
                        boolean isOwner = true;
                        CallGroup callGroup =  imsCall.getCallGroup();
                        if (callGroup != null) {
                            isOwner = callGroup.isOwner(imsCall);
                        }
                        if (isOwner) {
                            sendEmptyMessage(EVENT_RESUME_BACKGROUND);
                        }
                    } else {
                        //when multiple connections belong to background call,
                        //only the first callback reaches here
                        //otherwise the oldState is already HOLDING
                        if (mPendingMO != null) {
                            sendEmptyMessage(EVENT_DIAL_PENDINGMO);
                        }
                    }
                }
            }
        }

        @Override
        public void onCallHoldFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            if (DBG) log("onCallHoldFailed reasonCode=" + reasonInfo.getCode());

            synchronized (mSyncHold) {
                ImsPhoneCall.State bgState = mBackgroundCall.getState();
                if (reasonInfo.getCode() == ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED) {
                    // disconnected while processing hold
                    if (mPendingMO != null) {
                        sendEmptyMessage(EVENT_DIAL_PENDINGMO);
                    }
                } else if (bgState == ImsPhoneCall.State.ACTIVE) {
                    mForegroundCall.switchWith(mBackgroundCall);

                    if (mPendingMO != null) {
                        mPendingMO.setDisconnectCause(DisconnectCause.ERROR_UNSPECIFIED);
                        sendEmptyMessageDelayed(EVENT_HANGUP_PENDINGMO, TIMEOUT_HANGUP_PENDINGMO);
                    }
                }
            }
        }

        @Override
        public void onCallResumed(ImsCall imsCall) {
            if (DBG) log("onCallResumed");

            processCallStateChange(imsCall, ImsPhoneCall.State.ACTIVE,
                    DisconnectCause.NOT_DISCONNECTED);
        }

        @Override
        public void onCallResumeFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            // TODO : What should be done?
        }

        @Override
        public void onCallResumeReceived(ImsCall imsCall) {
            if (DBG) log("onCallResumeReceived");

            if (mOnHoldToneStarted) {
                mPhone.stopOnHoldTone();
                mOnHoldToneStarted = false;
            }

            SuppServiceNotification supp = new SuppServiceNotification();
            // Type of notification: 0 = MO; 1 = MT
            // Refer SuppServiceNotification class documentation.
            supp.notificationType = 1;
            supp.code = SuppServiceNotification.MT_CODE_CALL_RETRIEVED;
            mPhone.notifySuppSvcNotification(supp);
        }

        @Override
        public void onCallHoldReceived(ImsCall imsCall) {
            if (DBG) log("onCallHoldReceived");

            ImsPhoneConnection conn = findConnection(imsCall);
            if (conn != null && conn.getState() == ImsPhoneCall.State.ACTIVE) {
                if (!mOnHoldToneStarted && ImsPhoneCall.isLocalTone(imsCall)) {
                    mPhone.startOnHoldTone();
                    mOnHoldToneStarted = true;
                }
            }

            SuppServiceNotification supp = new SuppServiceNotification();
            // Type of notification: 0 = MO; 1 = MT
            // Refer SuppServiceNotification class documentation.
            supp.notificationType = 1;
            supp.code = SuppServiceNotification.MT_CODE_CALL_ON_HOLD;
            mPhone.notifySuppSvcNotification(supp);
        }

        @Override
        public void onCallMerged(final ImsCall call) {
            if (DBG) log("onCallMerged");

            mForegroundCall.merge(mBackgroundCall, mForegroundCall.getState(), mConferenceTime);
            mConferenceTime = 0;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        final ImsPhoneConnection conn = findConnection(call);
                        log("onCallMerged: ImsPhoneConnection=" + conn);
                        log("onCallMerged: mConferenceTime=" + mConferenceTime);
                        log("onCallMerged: CurrentVideoProvider=" + conn.getVideoProvider());
                        setVideoCallProvider(conn, call);
                        log("onCallMerged: CurrentVideoProvider=" + conn.getVideoProvider());
                    } catch (Exception e) {
                        loge("onCallMerged: exception " + e);
                    }
                }
            };

            ImsPhoneCallTracker.this.post(r);

            processCallStateChange(call, ImsPhoneCall.State.ACTIVE,
                    DisconnectCause.NOT_DISCONNECTED, true);
        }

        @Override
        public void onCallMergeFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            if (DBG) log("onCallMergeFailed reasonInfo=" + reasonInfo);
            mPhone.notifySuppServiceFailed(Phone.SuppService.CONFERENCE);
        }

        /**
         * Called when the state of IMS conference participant(s) has changed.
         *
         * @param call the call object that carries out the IMS call.
         * @param participants the participant(s) and their new state information.
         */
        @Override
        public void onConferenceParticipantsStateChanged(ImsCall call,
                List<ConferenceParticipant> participants) {
            if (DBG) log("onConferenceParticipantsStateChanged");

            ImsPhoneConnection conn = findConnection(call);
            if (conn != null) {
                conn.updateConferenceParticipants(participants);
            }
        }

        @Override
        public void onCallHandover(ImsCall imsCall, int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) {
            if (DBG) {
                log("onCallHandover ::  srcAccessTech=" + srcAccessTech + ", targetAccessTech=" +
                    targetAccessTech + ", reasonInfo=" + reasonInfo);
            }
        }

        @Override
        public void onCallHandoverFailed(ImsCall imsCall, int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) {
            if (DBG) {
                log("onCallHandoverFailed :: srcAccessTech=" + srcAccessTech +
                    ", targetAccessTech=" + targetAccessTech + ", reasonInfo=" + reasonInfo);
            }

            String msg = reasonInfo.getExtraMessage();
            if (mPhone != null && msg != null) {
                Toast.makeText(mPhone.getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }
    };

    /**
     * Listen to the IMS call state change
     */
    private ImsCall.Listener mImsUssdListener = new ImsCall.Listener() {
        @Override
        public void onCallStarted(ImsCall imsCall) {
            if (DBG) log("mImsUssdListener onCallStarted");

            if (imsCall == mUssdSession) {
                if (mPendingUssd != null) {
                    AsyncResult.forMessage(mPendingUssd);
                    mPendingUssd.sendToTarget();
                    mPendingUssd = null;
                }
            }
        }

        @Override
        public void onCallStartFailed(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            if (DBG) log("mImsUssdListener onCallStartFailed reasonCode=" + reasonInfo.getCode());

            onCallTerminated(imsCall, reasonInfo);
        }

        @Override
        public void onCallTerminated(ImsCall imsCall, ImsReasonInfo reasonInfo) {
            if (DBG) log("mImsUssdListener onCallTerminated reasonCode=" + reasonInfo.getCode());

            if (imsCall == mUssdSession) {
                mUssdSession = null;
                if (mPendingUssd != null) {
                    CommandException ex =
                            new CommandException(CommandException.Error.GENERIC_FAILURE);
                    AsyncResult.forMessage(mPendingUssd, null, ex);
                    mPendingUssd.sendToTarget();
                    mPendingUssd = null;
                }
            }
            imsCall.close();
        }

        @Override
        public void onCallUssdMessageReceived(ImsCall call,
                int mode, String ussdMessage) {
            if (DBG) log("mImsUssdListener onCallUssdMessageReceived mode=" + mode);

            int ussdMode = -1;

            switch(mode) {
                case ImsCall.USSD_MODE_REQUEST:
                    ussdMode = CommandsInterface.USSD_MODE_REQUEST;
                    break;

                case ImsCall.USSD_MODE_NOTIFY:
                    ussdMode = CommandsInterface.USSD_MODE_NOTIFY;
                    break;
            }

            mPhone.onIncomingUSSD(ussdMode, ussdMessage);
        }
    };

    /**
     * Listen to the IMS service state change
     *
     */
    private ImsConnectionStateListener mImsConnectionStateListener =
        new ImsConnectionStateListener() {
        @Override
        public void onImsConnected() {
            if (DBG) log("onImsConnected");
            mPhone.setServiceState(ServiceState.STATE_IN_SERVICE);
            mPhone.setImsRegistered(true);
        }

        @Override
        public void onImsDisconnected(ImsReasonInfo imsReasonInfo) {
            if (DBG) log("onImsDisconnected imsReasonInfo=" + imsReasonInfo);
            mPhone.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        }

        @Override
        public void onImsDisconnected() {
            if (DBG) log("onImsDisconnected");
            mPhone.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        }

        @Override
        public void onImsProgressing() {
            if (DBG) log("onImsProgressing");
            mPhone.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
            mPhone.setImsRegistered(false);
        }

        @Override
        public void onImsResumed() {
            if (DBG) log("onImsResumed");
            mPhone.setServiceState(ServiceState.STATE_IN_SERVICE);
        }

        @Override
        public void onImsSuspended() {
            if (DBG) log("onImsSuspended");
            mPhone.setServiceState(ServiceState.STATE_OUT_OF_SERVICE);
        }

        @Override
        public void onFeatureCapabilityChanged(int serviceClass,
                int[] enabledFeatures, int[] disabledFeatures) {
            if (serviceClass == ImsServiceClass.MMTEL) {
                boolean tmpIsVtEnabled = mIsVtEnabled;

                if (disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE ||
                        disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                    mIsVolteEnabled = false;
                }
                if (disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE ||
                        disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI) {
                    mIsVtEnabled = false;
                }
                if ((disabledFeatures.length > 5) &&
                        (disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_LTE] ==
                         ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_LTE ||
                         disabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_WIFI] ==
                         ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_WIFI)) {
                    mIsUtEnabled = false;
                }
                if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_LTE ||
                        enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VOICE_OVER_WIFI) {
                    mIsVolteEnabled = true;
                    mIsSrvccCompleted = false;
                }
                if (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_LTE ||
                        enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_VIDEO_OVER_WIFI) {
                    mIsVtEnabled = true;
                    mIsSrvccCompleted = false;
                }
                if ((enabledFeatures.length > 5) &&
                       (enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_LTE] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_LTE ||
                        enabledFeatures[ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_WIFI] ==
                        ImsConfig.FeatureConstants.FEATURE_TYPE_UT_OVER_WIFI)) {
                    mIsUtEnabled = true;
                 }

                if (tmpIsVtEnabled != mIsVtEnabled) {
                    mPhone.notifyForVideoCapabilityChanged(mIsVtEnabled);
                }
            }

            if (DBG) log("onFeatureCapabilityChanged, mIsVolteEnabled = " +  mIsVolteEnabled
                    + " mIsVtEnabled = " + mIsVtEnabled + "mIsUtEnabled = " + mIsUtEnabled);
        }

        @Override
        public void onVoiceMessageCountChanged(int count) {
            if (DBG) log("onVoiceMessageCountChanged :: count=" + count);
            mPhone.mDefaultPhone.setVoiceMessageCount(count);
        }
    };

    /* package */
    ImsUtInterface getUtInterface() throws ImsException {
        if (mImsManager == null) {
            throw new ImsException("no ims manager", ImsReasonInfo.CODE_UNSPECIFIED);
        }

        ImsUtInterface ut = mImsManager.getSupplementaryServiceConfiguration(mServiceId);
        return ut;
    }

    private void transferHandoverConnections(ImsPhoneCall call) {
        if (call.mConnections != null) {
            for (Connection c : call.mConnections) {
                c.mPreHandoverState = call.mState;
                log ("Connection state before handover is " + c.getStateBeforeHandover());
            }
        }
        if (mHandoverCall.mConnections == null ) {
            mHandoverCall.mConnections = call.mConnections;
        } else { // Multi-call SRVCC
            mHandoverCall.mConnections.addAll(call.mConnections);
        }
        if (mHandoverCall.mConnections != null) {
            if (call.getImsCall() != null) {
                call.getImsCall().close();
            }
            for (Connection c : mHandoverCall.mConnections) {
                ((ImsPhoneConnection)c).changeParent(mHandoverCall);
                ((ImsPhoneConnection)c).releaseWakeLock();
            }
        }
        if (call.getState().isAlive()) {
            log ("Call is alive and state is " + call.mState);
            mHandoverCall.mState = call.mState;
        }
        call.mConnections.clear();
        call.mState = ImsPhoneCall.State.IDLE;
    }

    /* package */
    void notifySrvccState(Call.SrvccState state) {
        if (DBG) log("notifySrvccState state=" + state);

        mSrvccState = state;

        if (mSrvccState == Call.SrvccState.COMPLETED) {
            transferHandoverConnections(mForegroundCall);
            transferHandoverConnections(mBackgroundCall);
            transferHandoverConnections(mRingingCall);
            // Make mIsSrvccCompleted flag to true after SRVCC complete.
            // After SRVCC complete sometimes SRV_STATUS_UPDATE come late.
            mIsSrvccCompleted = true;
        }
    }

    //****** Overridden from Handler

    @Override
    public void
    handleMessage (Message msg) {
        AsyncResult ar;
        if (DBG) log("handleMessage what=" + msg.what);

        switch (msg.what) {
            case EVENT_HANGUP_PENDINGMO:
                if (mPendingMO != null) {
                    mPendingMO.onDisconnect();
                    removeConnection(mPendingMO);
                    mPendingMO = null;
                }

                updatePhoneState();
                mPhone.notifyPreciseCallStateChanged();
                break;
            case EVENT_RESUME_BACKGROUND:
                try {
                    resumeWaitingOrHolding();
                } catch (CallStateException e) {
                    if (Phone.DEBUG_PHONE) {
                        loge("handleMessage EVENT_RESUME_BACKGROUND exception=" + e);
                    }
                }
                break;
            case EVENT_DIAL_PENDINGMO:
                dialInternal(mPendingMO, mClirMode, mPendingCallVideoState,
                    mPendingMO.getCallExtras());
                break;

            case EVENT_EXIT_ECM_RESPONSE_CDMA:
                // no matter the result, we still do the same here
                if (pendingCallInEcm) {
                    dialInternal(mPendingMO, pendingCallClirMode,
                            mPendingCallVideoState, mPendingMO.getCallExtras());
                    pendingCallInEcm = false;
                }
                mPhone.unsetOnEcbModeExitResponse(this);
                break;
        }
    }

    @Override
    protected void log(String msg) {
        Rlog.d(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[ImsPhoneCallTracker] " + msg);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("ImsPhoneCallTracker extends:");
        super.dump(fd, pw, args);
        pw.println(" mVoiceCallEndedRegistrants=" + mVoiceCallEndedRegistrants);
        pw.println(" mVoiceCallStartedRegistrants=" + mVoiceCallStartedRegistrants);
        pw.println(" mRingingCall=" + mRingingCall);
        pw.println(" mForegroundCall=" + mForegroundCall);
        pw.println(" mBackgroundCall=" + mBackgroundCall);
        pw.println(" mHandoverCall=" + mHandoverCall);
        pw.println(" mPendingMO=" + mPendingMO);
        //pw.println(" mHangupPendingMO=" + mHangupPendingMO);
        pw.println(" mPhone=" + mPhone);
        pw.println(" mDesiredMute=" + mDesiredMute);
        pw.println(" mState=" + mState);
    }

    @Override
    protected void handlePollCalls(AsyncResult ar) {
    }

    /* package */
    ImsEcbm getEcbmInterface() throws ImsException {
        if (mImsManager == null) {
            throw new ImsException("no ims manager", ImsReasonInfo.CODE_UNSPECIFIED);
        }

        ImsEcbm ecbm = mImsManager.getEcbmInterface(mServiceId);
        return ecbm;
    }

    public boolean isInEmergencyCall() {
        return mIsInEmergencyCall;
    }

    public boolean isVolteEnabled() {
        return mIsVolteEnabled;
    }

    public boolean isVtEnabled() {
        return mIsVtEnabled;
    }

    private void setVideoCallProvider(ImsPhoneConnection conn, ImsCall imsCall)
            throws RemoteException {
        IImsVideoCallProvider imsVideoCallProvider =
                imsCall.getCallSession().getVideoCallProvider();
        if (imsVideoCallProvider != null) {
            ImsVideoCallProviderWrapper imsVideoCallProviderWrapper =
                    new ImsVideoCallProviderWrapper(imsVideoCallProvider);
            conn.setVideoProvider(imsVideoCallProviderWrapper);
        }
    }

    public boolean isUtEnabled() {
        return mIsUtEnabled;
   }

    @Override
    public PhoneConstants.State getState() {
        return mState;
    }
}
