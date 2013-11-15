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

import android.telephony.Rlog;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IThirdPartyCallProvider;
import com.android.internal.telephony.Phone;

import java.util.Iterator;
import java.util.List;

class ThirdPartyCall extends Call {
    private static final String TAG = ThirdPartyCall.class.getSimpleName();
    private static final boolean DBG = false;

    private ThirdPartyPhone mPhone;

    public ThirdPartyCall(ThirdPartyPhone phone) {
        if (DBG) log("new ThirdPartyCall");
        mPhone = phone;
    }

    public List<Connection> getConnections() {
        return mConnections;
    }

    @Override
    public Phone getPhone() {
        return mPhone;
    }

    @Override
    public boolean isMultiparty() {
        return mConnections.size() > 1;
    }

    @Override
    public void hangup() throws CallStateException {
        if (DBG) log("hangup, current state: " + getState());
        if (mState.isAlive()) {
            setState(State.DISCONNECTING);
            CallStateException excp = null;
            for (Connection c : mConnections) {
                try {
                    c.hangup();
                } catch (CallStateException e) {
                    log("CallStateException during hangup: " + e);
                    excp = e;
                }
            }
            if (excp != null) throw excp;
        }
    }

    void initIncomingCall(String callId, String callerDisplayName, boolean makeCallWait) {
        try {
            ThirdPartyConnection c = new ThirdPartyConnection(this);
            mConnections.add(c);
            Call.State newState = makeCallWait ? State.WAITING : State.INCOMING;
            c.initIncomingCall(callId, callerDisplayName, newState);
            setState(newState);
            mPhone.notifyNewRingingConnection(c);
        } catch (Throwable e) {
            log("initIncomingCall exception: " + e);
        }
    }

    void rejectCall() throws CallStateException {
        if (DBG) log("rejectCall:");
        hangup();
    }

    void acceptCall() throws CallStateException {
        if (DBG) log("acceptCall: accepting");
        if (this != mPhone.getRingingCall()) {
            throw new CallStateException("acceptCall() in a non-ringing call");
        }
        if (mConnections.size() != 1) {
            throw new CallStateException("acceptCall() in a conf call");
        }
        ((ThirdPartyConnection) mConnections.get(0)).acceptCall();
    }

    void reset() {
        mConnections.clear();
        setState(Call.State.IDLE);
    }

    void switchWith(ThirdPartyCall that) {
        synchronized (ThirdPartyCall.class) {
            ThirdPartyCall tmp = new ThirdPartyCall(mPhone);
            tmp.takeOver(this);
            this.takeOver(that);
            that.takeOver(tmp);
        }
    }

    void setMute(boolean muted) {
        for (Connection c : mConnections) {
            ((ThirdPartyConnection) c).setMute(muted);
        }
    }

    boolean getMute() {
        if (mConnections.isEmpty()) {
            return false;
        }
        return ((ThirdPartyConnection) mConnections.get(0)).getMute();
    }

    void clearDisconnected() {
        for (Iterator<Connection> it = mConnections.iterator(); it.hasNext(); ) {
            Connection c = it.next();
            if (c.getState() == State.DISCONNECTED) it.remove();
        }

        if (mConnections.isEmpty()) {
            setState(State.IDLE);
        }
    }

    Connection dial(String originalNumber) {
        ThirdPartyConnection c = new ThirdPartyConnection(this, originalNumber);
        c.dial();
        mConnections.add(c);
        setState(Call.State.DIALING);
        return c;
    }

    void sendDtmf(char c) {
        if (!mConnections.isEmpty()) {
            ((ThirdPartyConnection) mConnections.get(0)).sendDtmf(c, null);
        }
    }

    void onConnectionStateChanged(ThirdPartyConnection conn) {
        // This can be called back when a conf call is formed.
        if (mState != State.ACTIVE) {
            setState(conn.getState());
        }
    }

    void onConnectionEnded(ThirdPartyConnection conn) {
        // Set state to DISCONNECTED only when all conns are disconnected.
        if (mState != State.DISCONNECTED) {
            boolean allConnectionsDisconnected = true;
            for (Connection c : mConnections) {
                if (c.getState() != State.DISCONNECTED) {
                    allConnectionsDisconnected = false;
                    break;
                }
            }
            if (allConnectionsDisconnected) {
                setState(State.DISCONNECTED);
            }
        }
        mPhone.notifyDisconnect(conn);
    }

    private void setState(State newState) {
        if (DBG) log("setState: " + mState + " -> " + newState);
        if (mState == newState) {
            return;
        }

        if (newState == Call.State.ALERTING) {
            mState = newState; // need in ALERTING to enable ringback
            mPhone.startRingbackTone();
        } else if (mState == Call.State.ALERTING) {
            mPhone.stopRingbackTone();
        }
        mState = newState;
        mPhone.updatePhoneState();
        mPhone.notifyPreciseCallStateChanged();
    }

    private void takeOver(ThirdPartyCall that) {
        mConnections = that.mConnections;
        mState = that.mState;
        mPhone = that.mPhone;
        for (Connection c : mConnections) {
            ((ThirdPartyConnection) c).changeOwner(this);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }
}
