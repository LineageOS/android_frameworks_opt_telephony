/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (c) 2012-13, The Linux Foundation. All rights reserved.
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
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IThirdPartyCallProvider;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.UUSInfo;

import java.util.ArrayList;
import java.util.List;
import java.lang.Thread;
import android.os.Looper;

public class ThirdPartyPhone extends PhoneBase {
    private static final String TAG = ThirdPartyPhone.class.getSimpleName();
    private static final boolean DBG = false;

    public static final String ACTION_THIRD_PARTY_CALL_SERVICE =
            "android.intent.action.THIRD_PARTY_CALL_SERVICE";

    private RegistrantList mRingbackRegistrants = new RegistrantList();
    private PhoneConstants.State mState = PhoneConstants.State.IDLE;
    private ThirdPartyCall mRingingCall = new ThirdPartyCall(this);
    private ThirdPartyCall mForegroundCall = new ThirdPartyCall(this);
    private ThirdPartyCall mBackgroundCall = new ThirdPartyCall(this);

    private final ComponentName mCallProviderComponent;

    public ThirdPartyPhone(PhoneNotifier notifier, Context context, ComponentName component) {
        super("ThirdPartyPhone", notifier, context, new ThirdPartyCommandInterface(context), false);
        if (DBG) log("new ThirdPartyPhone, component: " + component);
        mCallProviderComponent = component;
    }

    public ComponentName getCallProviderComponent() {
        return mCallProviderComponent;
    }

    public boolean takeIncomingCall(final ComponentName component, final String callId) {
        if (mRingingCall.getState().isAlive()) {
            if (DBG) log("takeIncomingCall: returning false, ringingCall not alive");
            return false;
        }
        if (mForegroundCall.getState().isAlive() && mBackgroundCall.getState().isAlive()) {
            if (DBG) log("takeIncomingCall: returning false, foreground and backgroud alive");
            return false;
        }
        boolean makeCallWait = mForegroundCall.getState().isAlive();
        mRingingCall.initIncomingCall(callId, makeCallWait);
        return true;
    }

    protected void updatePhoneState() {
        PhoneConstants.State oldState = mState;

        if (getRingingCall().isRinging()) {
            mState = PhoneConstants.State.RINGING;
        } else if (getForegroundCall().isIdle()
                && getBackgroundCall().isIdle()) {
            mState = PhoneConstants.State.IDLE;
        } else {
            mState = PhoneConstants.State.OFFHOOK;
        }

        if (mState != oldState) {
            if (DBG) log("updatePhoneState " + oldState + " -> " + mState);
            notifyPhoneStateChanged();
        }
    }

    protected void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }

    protected void notifyPreciseCallStateChanged() {
        super.notifyPreciseCallStateChangedP();
    }

    protected void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    protected void notifyDisconnect(Connection cn) {
        super.notifyDisconnectP(cn);
    }

    protected void notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    @Override
    public Call getForegroundCall() {
        return mForegroundCall;
    }

    @Override
    public Call getBackgroundCall() {
        return mBackgroundCall;
    }

    @Override
    public Call getRingingCall() {
        return mRingingCall;
    }

    @Override
    public Connection dial(String dialString, UUSInfo uusInfo)
            throws CallStateException {
        return dial(dialString);
    }

    @Override
    public Connection dial(String dialString) throws CallStateException {
        if (DBG) log("dial");
        clearDisconnected();

        if (!canDial()) {
            throw new CallStateException("dial: cannot dial in current state");
        }
        if (mForegroundCall.getState() == Call.State.ACTIVE) {
            switchHoldingAndActive();
        }
        if (mForegroundCall.getState() != Call.State.IDLE) {
            //we should have failed in !canDial() above before we get here
            throw new CallStateException("cannot dial in current state");
        }

        mForegroundCall.setMute(false);
        return mForegroundCall.dial(dialString);
    }

    @Override
    public void acceptCall() throws CallStateException {
        if ((mRingingCall.getState() == Call.State.INCOMING) ||
                (mRingingCall.getState() == Call.State.WAITING)) {
            if (DBG) log("acceptCall: accepting");
            mRingingCall.setMute(false);
            mRingingCall.acceptCall();
        } else {
            if (DBG) log("acceptCall: throw CallStateException(\"phone not ringing\")");
            throw new CallStateException("phone not ringing");
        }
    }

    @Override
    public void rejectCall() throws CallStateException {
        if (mRingingCall.getState().isRinging()) {
            if (DBG) log("rejectCall: rejecting");
            mRingingCall.rejectCall();
        } else {
            if (DBG) log("rejectCall: throw CallStateException(\"phone not ringing\")");
            throw new CallStateException("phone not ringing");
        }
    }

    @Override
    public void switchHoldingAndActive() throws CallStateException {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public boolean canConference() {
        // TODO(sail): This is not supported yet.
        return false;
    }

    @Override
    public void conference() throws CallStateException {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public boolean canTransfer() {
        return false;
    }

    @Override
    public void explicitCallTransfer() {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void clearDisconnected() {
        mRingingCall.clearDisconnected();
        mForegroundCall.clearDisconnected();
        mBackgroundCall.clearDisconnected();
    }

    @Override
    public void sendDtmf(char c) {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void startDtmf(char c) {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void stopDtmf() {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        mRingbackRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForRingbackTone(Handler h) {
        mRingbackRegistrants.remove(h);
    }

    protected void startRingbackTone() {
        if (DBG) log("startRingBackTone");
        AsyncResult result = new AsyncResult(null, true, null);
        mRingbackRegistrants.notifyRegistrants(result);
    }

    protected void stopRingbackTone() {
        if (DBG) log("stopRingBackTone");
        AsyncResult result = new AsyncResult(null, false, null);
        mRingbackRegistrants.notifyRegistrants(result);
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
    }

    @Override
    public void setEchoSuppressionEnabled(boolean enabled) {
        // TODO(sail): This is not supported yet.
    }

    @Override
    public void setMute(boolean muted) {
        mForegroundCall.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return (mForegroundCall.getState().isAlive()
                ? mForegroundCall.getMute()
                : mBackgroundCall.getMute());
    }

    @Override
    public ServiceState getServiceState() {
        ServiceState s = new ServiceState();
        s.setState(ServiceState.STATE_IN_SERVICE);
        return s;
    }

    @Override
    public CellLocation getCellLocation() {
        return null;
    }

    @Override
    public PhoneConstants.State getState() {
        return mState;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_THIRD_PARTY;
    }

    @Override
    public SignalStrength getSignalStrength() {
        return new SignalStrength();
    }

    @Override
    public boolean getMessageWaitingIndicator() {
        return false;
    }

    @Override
    public boolean getCallForwardingIndicator() {
        return false;
    }

    @Override
    public List<? extends MmiCode> getPendingMmiCodes() {
        return new ArrayList<MmiCode>(0);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState() {
        return PhoneConstants.DataState.DISCONNECTED;
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return PhoneConstants.DataState.DISCONNECTED;
    }

    @Override
    public DataActivityState getDataActivityState() {
        return DataActivityState.NONE;
    }

    @Override
    public void notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
    }

    public boolean canDial() {
        int serviceState = getServiceState().getState();
        if (serviceState == ServiceState.STATE_POWER_OFF) {
            return false;
        }

        String disableCall = SystemProperties.get(
                TelephonyProperties.PROPERTY_DISABLE_CALL, "false");
        if (disableCall.equals("true")) {
            return false;
        }

        return !getRingingCall().isRinging()
                && (!getForegroundCall().getState().isAlive()
                    || !getBackgroundCall().getState().isAlive());
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) {
        return false;
    }

    boolean isInCall() {
        Call.State foregroundCallState = getForegroundCall().getState();
        Call.State backgroundCallState = getBackgroundCall().getState();
        Call.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() || backgroundCallState.isAlive()
            || ringingCallState.isAlive());
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        return false;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
    }

    @Override
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
    }

    @Override
    public void setRadioPower(boolean power) {
    }

    @Override
    public String getVoiceMailNumber() {
        return null;
    }

    @Override
    public String getVoiceMailAlphaTag() {
        return null;
    }

    @Override
    public String getDeviceId() {
        return null;
    }

    @Override
    public String getDeviceSvn() {
        return null;
    }

    @Override
    public String getImei() {
        return null;
    }

    @Override
    public String getEsn() {
        return "0";
    }

    @Override
    public String getMeid() {
        return "0";
    }

    @Override
    public String getSubscriberId() {
        return null;
    }

    @Override
    public String getGroupIdLevel1() {
        return null;
    }

    @Override
    public String getIccSerialNumber() {
        return null;
    }

    @Override
    public String getLine1Number() {
        return null;
    }

    @Override
    public String getLine1AlphaTag() {
        return null;
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    @Override
    public void setVoiceMailNumber(String alphaTag, String voiceMailNumber, Message onComplete) {
        AsyncResult.forMessage(onComplete, null, null);
        onComplete.sendToTarget();
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction, int commandInterfaceCFReason,
            String dialingNumber, int timerSeconds, Message onComplete) {
    }

    @Override
    public boolean getIccRecordsLoaded() {
        return false;
    }

    @Override
    public IccCard getIccCard() {
        return null;
    }

    @Override
    public void getAvailableNetworks(Message response) {
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
    }

    @Override
    public void selectNetworkManually(OperatorInfo network, Message response) {
    }

    @Override
    public void getNeighboringCids(Message response) {
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
    }

    @Override
    public void getDataCallList(Message response) {
    }

    public List<DataConnection> getCurrentDataConnectionList () {
        return null;
    }

    @Override
    public void updateServiceLocation() {
    }

    @Override
    public void enableLocationUpdates() {
    }

    @Override
    public void disableLocationUpdates() {
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return false;
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
    }

    public boolean enableDataConnectivity() {
        return false;
    }

    public boolean disableDataConnectivity() {
        return false;
    }

    @Override
    public boolean isDataConnectivityPossible() {
        return false;
    }

    boolean updateCurrentCarrierInProvider() {
        return false;
    }

    public void saveClirSetting(int commandInterfaceCLIRMode) {
    }

    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return null;
    }

    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return null;
    }

    @Override
    public IccFileHandler getIccFileHandler(){
        return null;
    }

    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
    }

    @Override
    public void getCellBroadcastSmsConfig(Message response) {
    }

    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response){
    }

    @Override
    public boolean needsOtaServiceProvisioning() {
        return false;
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        return null;
    }

    @Override
    protected void onUpdateIccAvailability() {
    }

    private void log(String msg) {
        Rlog.d(TAG, msg);
    }
}
