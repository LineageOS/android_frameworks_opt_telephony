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

package com.android.internal.telephony.thirdpartyphone;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.Rlog;
import android.telephony.PhoneNumberUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.ThirdPartyCallListener;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IThirdPartyCallService;
import com.android.internal.telephony.IThirdPartyCallProvider;
import com.android.internal.telephony.IThirdPartyCallListener;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;

class ThirdPartyConnection extends Connection {
    private static final String TAG = ThirdPartyConnection.class.getSimpleName();
    private static final boolean DBG = false;
    private static final int TIMEOUT_MAKE_CALL = 15; // in seconds
    private static final int TIMEOUT_ANSWER_CALL = 80; // in seconds

    private String mPostDialString;      // outgoing calls only
    private int mNextPostDialChar;       // index into postDialString
    /*
     * These time/timespan values are based on System.currentTimeMillis(),
     * i.e., "wall clock" time.
     */
    private long mCreateTime;
    private long mConnectTime;
    private long mDisconnectTime;

    /*
     * These time/timespan values are based on SystemClock.elapsedRealTime(),
     * i.e., time since boot.  They are appropriate for comparison and
     * calculating deltas.
     */
    private long mConnectTimeReal;
    private long mDurationReal = -1L;
    // The time when the Connection last transitioned into HOLDING
    private long mHoldingStartTimeReal;

    private DisconnectCause mCause = DisconnectCause.NOT_DISCONNECTED;
    private PostDialState mPostDialState = PostDialState.NOT_STARTED;

    private ThirdPartyCall mOwner;
    private Call.State mState = Call.State.IDLE;
    private boolean mIsMuted = false;
    private boolean mIncoming = false;
    private String mOriginalNumber;
    private String mCallId;

    private final Handler mTimeoutHandler = new Handler();
    private final Runnable mTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            handleCallEnded(DisconnectCause.TIMED_OUT);
        }
    };

    private IThirdPartyCallService mCallService;
    private IThirdPartyCallProvider mCallProvider;
    private CallListener mCallListener = new CallListener();
    private ServiceConnection mConnection;

    ThirdPartyConnection(ThirdPartyCall owner, String dialString) {
        if (DBG) log("new ThirdPartyConnection, dialString: " + dialString);
        mPostDialString = PhoneNumberUtils.extractPostDialPortion(dialString);
        mCreateTime = System.currentTimeMillis();
        mOriginalNumber = dialString;
        mOwner = owner;
    }

    ThirdPartyConnection(ThirdPartyCall owner) {
        if (DBG) log("new ThirdPartyConnection");
        mCreateTime = System.currentTimeMillis();
        mOriginalNumber = "";
        mOwner = owner;
    }

    @Override
    public String getAddress() {
        return mOriginalNumber;
    }

    @Override
    public String getOrigDialString() {
        return mOriginalNumber;
    }

    @Override
    public Call getCall() {
        return mOwner;
    }

    @Override
    public long getCreateTime() {
        return mCreateTime;
    }

    @Override
    public long getConnectTime() {
        return mConnectTime;
    }

    @Override
    public long getDisconnectTime() {
        return mDisconnectTime;
    }

    @Override
    public long getDurationMillis() {
        long dur;
        if (mConnectTimeReal == 0) {
            dur = 0;
        } else if (mDurationReal < 0) {
            dur = SystemClock.elapsedRealtime() - mConnectTimeReal;
        } else {
            dur = mDurationReal;
        }
        return dur;
    }

    @Override
    public long getHoldDurationMillis() {
        long dur;
        if (getState() != Call.State.HOLDING) {
            // If not holding, return 0
            dur = 0;
        } else {
            dur = SystemClock.elapsedRealtime() - mHoldingStartTimeReal;
        }
        return dur;
    }

    @Override
    public DisconnectCause getDisconnectCause() {
        return mCause;
    }

    @Override
    public boolean isIncoming() {
        return mIncoming;
    }

    @Override
    public Call.State getState() {
        return mState;
    }

    @Override
    public void hangup() throws CallStateException {
        if (mState.isAlive()) {
            setState(Call.State.DISCONNECTING);
            if (mCallProvider != null) {
                try {
                    mCallProvider.hangup();
                } catch (RemoteException e) {
                    throw new CallStateException("hangup(): " + e);
                }
            }
        }
    }

    @Override
    public void separate() throws CallStateException {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public PostDialState getPostDialState() {
        return mPostDialState;
    }

    @Override
    public String getRemainingPostDialString() {
        if (mPostDialState == PostDialState.CANCELLED
            || mPostDialState == PostDialState.COMPLETE
            || mPostDialString == null
            || mPostDialString.length() <= mNextPostDialChar) {
            return "";
        }
        return mPostDialString.substring(mNextPostDialChar);
    }

    @Override
    public void proceedAfterWaitChar() {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void proceedAfterWildChar(String str) {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void cancelPostDial() {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public String getCnapName() {
        return null;
    }

    @Override
    public int getNumberPresentation() {
        return PhoneConstants.PRESENTATION_ALLOWED;
    }

    @Override
    public UUSInfo getUUSInfo() {
        return null;
    }

    void initIncomingCall(String callId, String callerDisplayName, Call.State newState) {
        if (DBG) log("initIncomingCall - calling setState");
        // TODO: move mCallProvider setter here
        setState(newState);
        mIncoming = true;
        mCallId = callId;
        mOriginalNumber = callerDisplayName;
        connectToCallProviderService();
    }

    void acceptCall() throws CallStateException {
        if (mCallProvider == null) {
            throw new CallStateException("acceptCall(): invalid state");
        }
        try {
            mCallProvider.incomingCallAccept();
            mTimeoutHandler.postDelayed(mTimeoutRunnable, TIMEOUT_ANSWER_CALL * 1000);
        } catch (RemoteException e) {
            throw new CallStateException("acceptCall(): " + e);
        }
    }

    void changeOwner(ThirdPartyCall owner) {
        mOwner = owner;
    }

    void dial() {
        if (DBG) log("dial");
        setState(Call.State.DIALING);
        connectToCallProviderService();
        mTimeoutHandler.postDelayed(mTimeoutRunnable,  TIMEOUT_MAKE_CALL * 1000);
    }

    void setMute(boolean muted) {
        if (mCallProvider == null) {
            log("setMute(): invalid state");
            return;
        }
        try {
            mCallProvider.mute(muted);
            mIsMuted = muted;
        } catch (RemoteException e) {
            log("setMute(): " + e);
        }
    }

    boolean getMute() {
        return mIsMuted;
    }

    void sendDtmf(char c) {
        if (mCallProvider != null) {
            try {
                mCallProvider.sendDtmf(c);
            } catch (RemoteException e) {
                log("sendDtmf(): " + e);
            }
        }
    }

    private void setState(Call.State state) {
        if (DBG) log("setState: " + mState + " -> " + state);
        if (state == mState) {
            return;
        }

        switch (state) {
            case ACTIVE:
                if (mConnectTime == 0) {
                    mConnectTimeReal = SystemClock.elapsedRealtime();
                    mConnectTime = System.currentTimeMillis();
                }
                break;
            case DISCONNECTED:
                mDurationReal = getDurationMillis();
                mDisconnectTime = System.currentTimeMillis();
                break;
            case HOLDING:
                mHoldingStartTimeReal = SystemClock.elapsedRealtime();
                break;
            default:
                // Ignore
                break;
        }
        mState = state;
    }

    private void handleCallEnded(DisconnectCause cause) {
        if (DBG) log("handleCallEnded cause: " + cause);
        mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
        if (getDisconnectCause() != DisconnectCause.LOCAL) {
            setDisconnectCause(cause);
        }
        setState(Call.State.DISCONNECTED);
        mOwner.onConnectionEnded(this);

        if (mConnection != null) {
            mOwner.getPhone().getContext().unbindService(mConnection);
            mConnection = null;
        }
    }

    private void setDisconnectCause(DisconnectCause cause) {
        if (DBG) log("setDisconnectCause: prev=" + mCause + " new=" + cause);
        mCause = cause;
    }

    private void connectToCallProviderService() {
        Intent intent = new Intent(ThirdPartyPhone.ACTION_THIRD_PARTY_CALL_SERVICE);
        intent.setComponent(((ThirdPartyPhone) mOwner.getPhone()).getCallProviderComponent());
        Context context = mOwner.getPhone().getContext();
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                if (DBG) log("connected");
                mCallService = IThirdPartyCallService.Stub.asInterface((IBinder) iBinder);
                if (mState == Call.State.INCOMING) {
                    try {
                        mCallService.incomingCallAttach(mCallListener, mCallId);
                    } catch (RemoteException e) {
                        log("incomingCallAttach exception: " + e);
                    }
                } else if (mState == Call.State.DIALING) {
                    try {
                        mCallService.outgoingCallInitiate(mCallListener, mOriginalNumber);
                    } catch (RemoteException e) {
                        log("outgoingCallInitiate exception: " + e);
                    }
                } else {
                    handleCallEnded(DisconnectCause.ERROR_UNSPECIFIED);
                }
            }
            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                if (DBG) log("disconnected");
                mCallService = null;
                mConnection = null;
            }
        };
        if (!context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            handleCallEnded(DisconnectCause.ERROR_UNSPECIFIED);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }

    private class CallListener extends IThirdPartyCallListener.Stub {
        private static final int MSG_CALL_PROVIDER_ATTACHED = 1;
        private static final int MSG_RINGING_STARTED = 2;
        private static final int MSG_CALL_ESTABLISHED = 3;
        private static final int MSG_CALL_ENDED = 4;

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_CALL_PROVIDER_ATTACHED:
                        mCallProvider = (IThirdPartyCallProvider) msg.obj;
                        break;
                    case MSG_RINGING_STARTED:
                        mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                        setState(Call.State.ALERTING);
                        mOwner.onConnectionStateChanged(ThirdPartyConnection.this);
                        break;
                    case MSG_CALL_ESTABLISHED:
                        mTimeoutHandler.removeCallbacks(mTimeoutRunnable);
                        if (mOwner == mOwner.getPhone().getRingingCall()) {
                            ThirdPartyCall foregroundCall =
                                    (ThirdPartyCall)mOwner.getPhone().getForegroundCall();
                            ThirdPartyCall ringingCall =
                                    (ThirdPartyCall)mOwner.getPhone().getRingingCall();
                            foregroundCall.switchWith(ringingCall);
                        }
                        setState(Call.State.ACTIVE);
                        mOwner.onConnectionStateChanged(ThirdPartyConnection.this);
                        break;
                    case MSG_CALL_ENDED:
                        handleCallEnded(getDisconnectCauseFromReason(msg.arg1));
                        break;
                }
            }
        };

        private DisconnectCause getDisconnectCauseFromReason(int reason) {
            switch (reason) {
                case ThirdPartyCallListener.CALL_END_NORMAL:
                    return DisconnectCause.NORMAL;
                case ThirdPartyCallListener.CALL_END_INCOMING_MISSED:
                    return DisconnectCause.INCOMING_MISSED;
                case ThirdPartyCallListener.CALL_END_OTHER:
                default:
                    return DisconnectCause.ERROR_UNSPECIFIED;
            }
        }

        @Override
        public void onCallProviderAttached(IThirdPartyCallProvider callProvider) {
            if (DBG) log("listener.onCallProviderAttached");
            Message.obtain(mHandler, MSG_CALL_PROVIDER_ATTACHED, callProvider).sendToTarget();
        }

        @Override
        public void onRingingStarted() {
            if (DBG) log("listener.onRingingStarted");
            Message.obtain(mHandler, MSG_RINGING_STARTED).sendToTarget();
        }

        @Override
        public void onCallEstablished() {
            if (DBG) log("listener.onCallEstablished");
            Message.obtain(mHandler, MSG_CALL_ESTABLISHED).sendToTarget();
        }

        @Override
        public void onCallEnded(int reason) {
            if (DBG) log("listener.onCallEnded");
            Message.obtain(mHandler, MSG_CALL_ENDED, reason, 0).sendToTarget();
        }
    }
}
