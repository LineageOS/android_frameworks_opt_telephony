/*
 * Copyright (c) 2012-13, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2008 The Android Open Source Project
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


import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.CellInfo;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.Rlog;

import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccCardProxy;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.UsimServiceTable;
import com.android.internal.telephony.CallManager;

import java.util.List;
import java.util.Map;

public class PhoneProxy extends Handler implements Phone {
    public final static Object lockForRadioTechnologyChange = new Object();

    protected Phone mActivePhone;
    protected CommandsInterface mCommandsInterface;
    protected IccSmsInterfaceManager mIccSmsInterfaceManager;
    protected IccSmsInterfaceManagerProxy mIccSmsInterfaceManagerProxy;
    protected IccPhoneBookInterfaceManagerProxy mIccPhoneBookInterfaceManagerProxy;
    protected PhoneSubInfoProxy mPhoneSubInfoProxy;
    protected IccCardProxy mIccCardProxy;

    private boolean mResetModemOnRadioTechnologyChange = false;

    private int mRilVersion;

    private static final int EVENT_VOICE_RADIO_TECH_CHANGED = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_REQUEST_VOICE_RADIO_TECH_DONE = 3;
    private static final int EVENT_RIL_CONNECTED = 4;

    protected static final String LOG_TAG = "PhoneProxy";

    //***** Class Methods
    public PhoneProxy(PhoneBase phone) {
        mActivePhone = phone;
        mResetModemOnRadioTechnologyChange = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_RESET_ON_RADIO_TECH_CHANGE, false);
        mIccPhoneBookInterfaceManagerProxy = new IccPhoneBookInterfaceManagerProxy(
                phone.getIccPhoneBookInterfaceManager());
        mPhoneSubInfoProxy = new PhoneSubInfoProxy(phone.getPhoneSubInfo());
        mCommandsInterface = ((PhoneBase)mActivePhone).mCi;

        mCommandsInterface.registerForRilConnected(this, EVENT_RIL_CONNECTED, null);
        mCommandsInterface.registerForOn(this, EVENT_RADIO_ON, null);
        mCommandsInterface.registerForVoiceRadioTechChanged(
                             this, EVENT_VOICE_RADIO_TECH_CHANGED, null);

        init();

        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
            // For the purpose of IccCardProxy we only care about the technology family
            mIccCardProxy.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_UMTS);
        } else if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) {
            mIccCardProxy.setVoiceRadioTech(ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT);
        }
    }

    protected void init() {
        mIccSmsInterfaceManager =
            new IccSmsInterfaceManager((PhoneBase)this.mActivePhone);
        mIccSmsInterfaceManagerProxy =
            new IccSmsInterfaceManagerProxy(mActivePhone.getContext(), mIccSmsInterfaceManager);
        mIccCardProxy = new IccCardProxy(mActivePhone.getContext(), mCommandsInterface);
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar = (AsyncResult) msg.obj;
        switch(msg.what) {
        case EVENT_RADIO_ON:
            /* Proactively query voice radio technologies */
            mCommandsInterface.getVoiceRadioTechnology(
                    obtainMessage(EVENT_REQUEST_VOICE_RADIO_TECH_DONE));
            break;

        case EVENT_RIL_CONNECTED:
            if (ar.exception == null && ar.result != null) {
                mRilVersion = (Integer) ar.result;
            } else {
                logd("Unexpected exception on EVENT_RIL_CONNECTED");
                mRilVersion = -1;
            }
            break;

        case EVENT_VOICE_RADIO_TECH_CHANGED:
        case EVENT_REQUEST_VOICE_RADIO_TECH_DONE:

            if (ar.exception == null) {
                if ((ar.result != null) && (((int[]) ar.result).length != 0)) {
                    int newVoiceTech = ((int[]) ar.result)[0];
                    updatePhoneObject(newVoiceTech);
                } else {
                    loge("Voice Radio Technology event " + msg.what + " has no tech!");
                }
            } else {
                loge("Voice Radio Technology event " + msg.what + " exception!" + ar.exception);
            }
            break;

        default:
            loge("Error! This handler was not registered for this message type. Message: "
                    + msg.what);
            break;
        }
        super.handleMessage(msg);
    }

    private static void logd(String msg) {
        Rlog.d(LOG_TAG, "[PhoneProxy] " + msg);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, "[PhoneProxy] " + msg);
    }

    public void updatePhoneObject(int newVoiceRadioTech) {

        if (mActivePhone != null) {
            if(mRilVersion >= 6 && getLteOnCdmaMode() == PhoneConstants.LTE_ON_CDMA_TRUE) {
                /*
                 * On v6 or greater RIL, when LTE_ON_CDMA is TRUE, always create CDMALTEPhone
                 * irrespective of the voice radio tech reported. Handle instance
                 * where device may be global phone, reporting as cdma device. Don't update
                 * voice tech in that scenario.
                 */
                if ((ServiceState.isCdma(newVoiceRadioTech) &&
                        mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA)) {
                    logd("LTE ON CDMA property is set. Use CDMA Phone" +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " Active Phone = " + mActivePhone.getPhoneName());
                    // IccCardProxy needs to be kept in sync
                    mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
                    return;
                } else if ((ServiceState.isGsm(newVoiceRadioTech) &&
                        mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA)) {
                    logd("LTE ON CDMA property is set. Already CDMA Phone" +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " Active Phone = " + mActivePhone.getPhoneName());
                    return;
                } else {
                    logd("LTE ON CDMA property is set. Switch to CDMALTEPhone" +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " Active Phone = " + mActivePhone.getPhoneName());
                    newVoiceRadioTech = ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT;
                }
            } else {
                if ((ServiceState.isCdma(newVoiceRadioTech) &&
                        mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA) ||
                        (ServiceState.isGsm(newVoiceRadioTech) &&
                                mActivePhone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM)) {
                    // Nothing changed. Keep phone as it is.
                    logd("Ignoring voice radio technology changed message." +
                            " newVoiceRadioTech = " + newVoiceRadioTech +
                            " Active Phone = " + mActivePhone.getPhoneName());
                    // IccCardProxy needs to be kept in sync
                    mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
                    return;
                }
            }
        }

        if (newVoiceRadioTech == ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
            // We need some voice phone object to be active always, so never
            // delete the phone without anything to replace it with!
            logd("Ignoring voice radio technology changed message. newVoiceRadioTech = Unknown."
                    + " Active Phone = " + mActivePhone.getPhoneName());
            // IccCardProxy, though, needs to know even if radio tech is unknown
            mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
            return;
        }

        boolean oldPowerState = false; // old power state to off
        if (mResetModemOnRadioTechnologyChange) {
            if (mCommandsInterface.getRadioState().isOn()) {
                oldPowerState = true;
                logd("Setting Radio Power to Off");
                mCommandsInterface.setRadioPower(false, null);
            }
        }

        deleteAndCreatePhone(newVoiceRadioTech);

        if (mResetModemOnRadioTechnologyChange && oldPowerState) { // restore power state
            logd("Resetting Radio");
            mCommandsInterface.setRadioPower(oldPowerState, null);
        }

        // Set the new interfaces in the proxy's
        mIccSmsInterfaceManager.updatePhoneObject((PhoneBase)mActivePhone);
        mIccPhoneBookInterfaceManagerProxy.setmIccPhoneBookInterfaceManager(mActivePhone
                .getIccPhoneBookInterfaceManager());
        mPhoneSubInfoProxy.setmPhoneSubInfo(mActivePhone.getPhoneSubInfo());

        mCommandsInterface = ((PhoneBase)mActivePhone).mCi;
        mIccCardProxy.setVoiceRadioTech(newVoiceRadioTech);
        sendBroadcastStickyIntent();
    }

    protected void sendBroadcastStickyIntent() {
        // Send an Intent to the PhoneApp that we had a radio technology change
        Intent intent = new Intent(TelephonyIntents.ACTION_RADIO_TECHNOLOGY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(PhoneConstants.PHONE_NAME_KEY, mActivePhone.getPhoneName());
        ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);

    }

    private void deleteAndCreatePhone(int newVoiceRadioTech) {

        String outgoingPhoneName = "Unknown";
        Phone oldPhone = mActivePhone;

        if (oldPhone != null) {
            outgoingPhoneName = ((PhoneBase) oldPhone).getPhoneName();
        }

        logd("Switching Voice Phone : " + outgoingPhoneName + " >>> "
                + (ServiceState.isGsm(newVoiceRadioTech) ? "GSM" : "CDMA"));

        if (oldPhone != null) {
            CallManager.getInstance().unregisterPhone(oldPhone);
            logd("Disposing old phone..");
            oldPhone.dispose();
        }

        // Give the garbage collector a hint to start the garbage collection
        // asap NOTE this has been disabled since radio technology change could
        // happen during e.g. a multimedia playing and could slow the system.
        // Tests needs to be done to see the effects of the GC call here when
        // system is busy.
        // System.gc();

        createNewPhone(newVoiceRadioTech);

        if (oldPhone != null) {
            oldPhone.removeReferences();
        }

        if(mActivePhone != null) {
            CallManager.getInstance().registerPhone(mActivePhone);
        }

        oldPhone = null;
    }

    protected void createNewPhone(int newVoiceRadioTech) {
        if (ServiceState.isCdma(newVoiceRadioTech)) {
            mActivePhone = PhoneFactory.getCdmaPhone();
        } else if (ServiceState.isGsm(newVoiceRadioTech)) {
            mActivePhone = PhoneFactory.getGsmPhone();
        }
    }

    @Override
    public ServiceState getServiceState() {
        return mActivePhone.getServiceState();
    }

    @Override
    public CellLocation getCellLocation() {
        return mActivePhone.getCellLocation();
    }

    /**
     * @return all available cell information or null if none.
     */
    @Override
    public List<CellInfo> getAllCellInfo() {
        return mActivePhone.getAllCellInfo();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCellInfoListRate(int rateInMillis) {
        mActivePhone.setCellInfoListRate(rateInMillis);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState() {
        return mActivePhone.getDataConnectionState(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        return mActivePhone.getDataConnectionState(apnType);
    }

    @Override
    public DataActivityState getDataActivityState() {
        return mActivePhone.getDataActivityState();
    }

    @Override
    public Context getContext() {
        return mActivePhone.getContext();
    }

    @Override
    public void disableDnsCheck(boolean b) {
        mActivePhone.disableDnsCheck(b);
    }

    @Override
    public boolean isDnsCheckDisabled() {
        return mActivePhone.isDnsCheckDisabled();
    }

    @Override
    public PhoneConstants.State getState() {
        return mActivePhone.getState();
    }

    @Override
    public String getPhoneName() {
        return mActivePhone.getPhoneName();
    }

    @Override
    public int getPhoneType() {
        return mActivePhone.getPhoneType();
    }

    @Override
    public String[] getActiveApnTypes() {
        return mActivePhone.getActiveApnTypes();
    }

    @Override
    public String getActiveApnHost(String apnType) {
        return mActivePhone.getActiveApnHost(apnType);
    }

    @Override
    public LinkProperties getLinkProperties(String apnType) {
        return mActivePhone.getLinkProperties(apnType);
    }

    @Override
    public LinkCapabilities getLinkCapabilities(String apnType) {
        return mActivePhone.getLinkCapabilities(apnType);
    }

    @Override
    public SignalStrength getSignalStrength() {
        return mActivePhone.getSignalStrength();
    }

    @Override
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        mActivePhone.registerForUnknownConnection(h, what, obj);
    }

    @Override
    public void unregisterForUnknownConnection(Handler h) {
        mActivePhone.unregisterForUnknownConnection(h);
    }

    @Override
    public void registerForPreciseCallStateChanged(Handler h, int what, Object obj) {
        mActivePhone.registerForPreciseCallStateChanged(h, what, obj);
    }

    @Override
    public void unregisterForPreciseCallStateChanged(Handler h) {
        mActivePhone.unregisterForPreciseCallStateChanged(h);
    }

    @Override
    public void registerForNewRingingConnection(Handler h, int what, Object obj) {
        mActivePhone.registerForNewRingingConnection(h, what, obj);
    }

    @Override
    public void unregisterForNewRingingConnection(Handler h) {
        mActivePhone.unregisterForNewRingingConnection(h);
    }

    @Override
    public void registerForIncomingRing(Handler h, int what, Object obj) {
        mActivePhone.registerForIncomingRing(h, what, obj);
    }

    @Override
    public void unregisterForIncomingRing(Handler h) {
        mActivePhone.unregisterForIncomingRing(h);
    }

    @Override
    public void registerForDisconnect(Handler h, int what, Object obj) {
        mActivePhone.registerForDisconnect(h, what, obj);
    }

    @Override
    public void unregisterForDisconnect(Handler h) {
        mActivePhone.unregisterForDisconnect(h);
    }

    @Override
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        mActivePhone.registerForMmiInitiate(h, what, obj);
    }

    @Override
    public void unregisterForMmiInitiate(Handler h) {
        mActivePhone.unregisterForMmiInitiate(h);
    }

    @Override
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        mActivePhone.registerForMmiComplete(h, what, obj);
    }

    @Override
    public void unregisterForMmiComplete(Handler h) {
        mActivePhone.unregisterForMmiComplete(h);
    }

    @Override
    public List<? extends MmiCode> getPendingMmiCodes() {
        return mActivePhone.getPendingMmiCodes();
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        mActivePhone.sendUssdResponse(ussdMessge);
    }

    @Override
    public void registerForServiceStateChanged(Handler h, int what, Object obj) {
        mActivePhone.registerForServiceStateChanged(h, what, obj);
    }

    @Override
    public void unregisterForServiceStateChanged(Handler h) {
        mActivePhone.unregisterForServiceStateChanged(h);
    }

    @Override
    public void registerForSuppServiceNotification(Handler h, int what, Object obj) {
        mActivePhone.registerForSuppServiceNotification(h, what, obj);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mActivePhone.unregisterForSuppServiceNotification(h);
    }

    @Override
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        mActivePhone.registerForSuppServiceFailed(h, what, obj);
    }

    @Override
    public void unregisterForSuppServiceFailed(Handler h) {
        mActivePhone.unregisterForSuppServiceFailed(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj){
        mActivePhone.registerForInCallVoicePrivacyOn(h,what,obj);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mActivePhone.unregisterForInCallVoicePrivacyOn(h);
    }

    @Override
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj){
        mActivePhone.registerForInCallVoicePrivacyOff(h,what,obj);
    }

    @Override
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mActivePhone.unregisterForInCallVoicePrivacyOff(h);
    }

    @Override
    public void registerForCdmaOtaStatusChange(Handler h, int what, Object obj) {
        mActivePhone.registerForCdmaOtaStatusChange(h,what,obj);
    }

    @Override
    public void unregisterForCdmaOtaStatusChange(Handler h) {
         mActivePhone.unregisterForCdmaOtaStatusChange(h);
    }

    @Override
    public void registerForSubscriptionInfoReady(Handler h, int what, Object obj) {
        mActivePhone.registerForSubscriptionInfoReady(h, what, obj);
    }

    @Override
    public void unregisterForSubscriptionInfoReady(Handler h) {
        mActivePhone.unregisterForSubscriptionInfoReady(h);
    }

    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mActivePhone.registerForEcmTimerReset(h,what,obj);
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        mActivePhone.unregisterForEcmTimerReset(h);
    }

    @Override
    public void registerForRingbackTone(Handler h, int what, Object obj) {
        mActivePhone.registerForRingbackTone(h,what,obj);
    }

    @Override
    public void unregisterForRingbackTone(Handler h) {
        mActivePhone.unregisterForRingbackTone(h);
    }

    @Override
    public void registerForResendIncallMute(Handler h, int what, Object obj) {
        mActivePhone.registerForResendIncallMute(h,what,obj);
    }

    @Override
    public void unregisterForResendIncallMute(Handler h) {
        mActivePhone.unregisterForResendIncallMute(h);
    }

    @Override
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        mActivePhone.registerForSimRecordsLoaded(h,what,obj);
    }

    public void unregisterForSimRecordsLoaded(Handler h) {
        mActivePhone.unregisterForSimRecordsLoaded(h);
    }

    @Override
    public void registerForUnsolVoiceSystemId(Handler h, int what, Object obj) {
        mActivePhone.registerForUnsolVoiceSystemId(h,what,obj);
    }

    @Override
    public void unregisterForUnsolVoiceSystemId(Handler h) {
        mActivePhone.unregisterForUnsolVoiceSystemId(h);
    }

    @Override
    public boolean getIccRecordsLoaded() {
        return mIccCardProxy.getIccRecordsLoaded();
    }

    @Override
    public IccCard getIccCard() {
        return mIccCardProxy;
    }

    @Override
    public void acceptCall() throws CallStateException {
        mActivePhone.acceptCall();
    }

    @Override
    public void acceptCall(int callType) throws CallStateException {
        mActivePhone.acceptCall(callType);
    }

    @Override
    public int getCallType(Call call) throws CallStateException {
        return mActivePhone.getCallType(call);
    }

    @Override
    public int getCallDomain(Call call) throws CallStateException {
        return mActivePhone.getCallDomain(call);
    }

    @Override
    public void rejectCall() throws CallStateException {
        mActivePhone.rejectCall();
    }

    @Override
    public void switchHoldingAndActive() throws CallStateException {
        mActivePhone.switchHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        return mActivePhone.canConference();
    }

    @Override
    public void conference() throws CallStateException {
        mActivePhone.conference();
    }

    public void changeConnectionType(Message msg, Connection conn,
            int newCallType, Map<String, String> newExtras) throws CallStateException {
        mActivePhone.changeConnectionType(msg, conn, newCallType, newExtras);
    }

    public void acceptConnectionTypeChange(Connection conn, Map<String, String> newExtras)
            throws CallStateException {
        mActivePhone.acceptConnectionTypeChange(conn, newExtras);
    }

    public void rejectConnectionTypeChange(Connection conn) throws CallStateException {
        mActivePhone.rejectConnectionTypeChange(conn);
    }

    public int getProposedConnectionType(Connection conn) throws CallStateException {
        return mActivePhone.getProposedConnectionType(conn);
    }

    @Override
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        mActivePhone.enableEnhancedVoicePrivacy(enable, onComplete);
    }

    @Override
    public void getEnhancedVoicePrivacy(Message onComplete) {
        mActivePhone.getEnhancedVoicePrivacy(onComplete);
    }

    @Override
    public boolean canTransfer() {
        return mActivePhone.canTransfer();
    }

    @Override
    public void explicitCallTransfer() throws CallStateException {
        mActivePhone.explicitCallTransfer();
    }

    @Override
    public void clearDisconnected() {
        mActivePhone.clearDisconnected();
    }

    @Override
    public Call getForegroundCall() {
        return mActivePhone.getForegroundCall();
    }

    @Override
    public Call getBackgroundCall() {
        return mActivePhone.getBackgroundCall();
    }

    @Override
    public Call getRingingCall() {
        return mActivePhone.getRingingCall();
    }

    @Override
    public Connection dial(String dialString) throws CallStateException {
        return mActivePhone.dial(dialString);
    }

    @Override
    public Connection dial(String dialString, UUSInfo uusInfo) throws CallStateException {
        return mActivePhone.dial(dialString, uusInfo);
    }

    @Override
    public Connection dial(String dialString, int callType, String[] extras)
            throws CallStateException {
        return mActivePhone.dial(dialString, callType, extras);
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        return mActivePhone.handlePinMmi(dialString);
    }

    @Override
    public boolean handleInCallMmiCommands(String command) throws CallStateException {
        return mActivePhone.handleInCallMmiCommands(command);
    }

    @Override
    public void sendDtmf(char c) {
        mActivePhone.sendDtmf(c);
    }

    @Override
    public void startDtmf(char c) {
        mActivePhone.startDtmf(c);
    }

    @Override
    public void stopDtmf() {
        mActivePhone.stopDtmf();
    }

    @Override
    public void setRadioPower(boolean power) {
        mActivePhone.setRadioPower(power);
    }

    @Override
    public boolean getMessageWaitingIndicator() {
        return mActivePhone.getMessageWaitingIndicator();
    }

    @Override
    public boolean getCallForwardingIndicator() {
        return mActivePhone.getCallForwardingIndicator();
    }

    @Override
    public String getLine1Number() {
        return mActivePhone.getLine1Number();
    }

    @Override
    public String getCdmaMin() {
        return mActivePhone.getCdmaMin();
    }

    @Override
    public boolean isMinInfoReady() {
        return mActivePhone.isMinInfoReady();
    }

    @Override
    public String getCdmaPrlVersion() {
        return mActivePhone.getCdmaPrlVersion();
    }

    @Override
    public String getLine1AlphaTag() {
        return mActivePhone.getLine1AlphaTag();
    }

    @Override
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        mActivePhone.setLine1Number(alphaTag, number, onComplete);
    }

    @Override
    public String getVoiceMailNumber() {
        return mActivePhone.getVoiceMailNumber();
    }

     /** @hide */
    @Override
    public int getVoiceMessageCount(){
        return mActivePhone.getVoiceMessageCount();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        return mActivePhone.getVoiceMailAlphaTag();
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,String voiceMailNumber,
            Message onComplete) {
        mActivePhone.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason,
            Message onComplete) {
        mActivePhone.getCallForwardingOption(commandInterfaceCFReason,
                onComplete);
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFReason,
            int commandInterfaceCFAction, String dialingNumber,
            int timerSeconds, Message onComplete) {
        mActivePhone.setCallForwardingOption(commandInterfaceCFReason,
            commandInterfaceCFAction, dialingNumber, timerSeconds, onComplete);
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mActivePhone.getOutgoingCallerIdDisplay(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
            Message onComplete) {
        mActivePhone.setOutgoingCallerIdDisplay(commandInterfaceCLIRMode,
                onComplete);
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        mActivePhone.getCallWaiting(onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        mActivePhone.setCallWaiting(enable, onComplete);
    }

    @Override
    public void getAvailableNetworks(Message response) {
        mActivePhone.getAvailableNetworks(response);
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
        mActivePhone.setNetworkSelectionModeAutomatic(response);
    }

    @Override
    public void selectNetworkManually(OperatorInfo network, Message response) {
        mActivePhone.selectNetworkManually(network, response);
    }

    @Override
    public void setPreferredNetworkType(int networkType, Message response) {
        mActivePhone.setPreferredNetworkType(networkType, response);
    }

    @Override
    public void getPreferredNetworkType(Message response) {
        mActivePhone.getPreferredNetworkType(response);
    }

    @Override
    public void getNeighboringCids(Message response) {
        mActivePhone.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mActivePhone.setOnPostDialCharacter(h, what, obj);
    }

    @Override
    public void setMute(boolean muted) {
        mActivePhone.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mActivePhone.getMute();
    }

    @Override
    public void setEchoSuppressionEnabled(boolean enabled) {
        mActivePhone.setEchoSuppressionEnabled(enabled);
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        mActivePhone.invokeOemRilRequestRaw(data, response);
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        mActivePhone.invokeOemRilRequestStrings(strings, response);
    }

    @Override
    public void getDataCallList(Message response) {
        mActivePhone.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mActivePhone.updateServiceLocation();
    }

    @Override
    public void enableLocationUpdates() {
        mActivePhone.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mActivePhone.disableLocationUpdates();
    }

    @Override
    public void setUnitTestMode(boolean f) {
        mActivePhone.setUnitTestMode(f);
    }

    @Override
    public boolean getUnitTestMode() {
        return mActivePhone.getUnitTestMode();
    }

    @Override
    public void setBandMode(int bandMode, Message response) {
        mActivePhone.setBandMode(bandMode, response);
    }

    @Override
    public void queryAvailableBandMode(Message response) {
        mActivePhone.queryAvailableBandMode(response);
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mActivePhone.getDataRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mActivePhone.setDataRoamingEnabled(enable);
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        mActivePhone.queryCdmaRoamingPreference(response);
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        mActivePhone.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    @Override
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        mActivePhone.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    @Override
    public SimulatedRadioControl getSimulatedRadioControl() {
        return mActivePhone.getSimulatedRadioControl();
    }

    @Override
    public int enableApnType(String type) {
        return mActivePhone.enableApnType(type);
    }

    @Override
    public int disableApnType(String type) {
        return mActivePhone.disableApnType(type);
    }

    @Override
    public boolean isDataConnectivityPossible() {
        return mActivePhone.isDataConnectivityPossible(PhoneConstants.APN_TYPE_DEFAULT);
    }

    @Override
    public boolean isDataConnectivityPossible(String apnType) {
        return mActivePhone.isDataConnectivityPossible(apnType);
    }

    @Override
    public String getDeviceId() {
        return mActivePhone.getDeviceId();
    }

    @Override
    public String getDeviceSvn() {
        return mActivePhone.getDeviceSvn();
    }

    @Override
    public String getSubscriberId() {
        return mActivePhone.getSubscriberId();
    }

    @Override
    public String getGroupIdLevel1() {
        return mActivePhone.getGroupIdLevel1();
    }

    @Override
    public String getIccSerialNumber() {
        return mActivePhone.getIccSerialNumber();
    }

    @Override
    public String getEsn() {
        return mActivePhone.getEsn();
    }

    @Override
    public String getMeid() {
        return mActivePhone.getMeid();
    }

    @Override
    public String getMsisdn() {
        return mActivePhone.getMsisdn();
    }

    @Override
    public String getImei() {
        return mActivePhone.getImei();
    }

    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mActivePhone.getPhoneSubInfo();
    }

    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mActivePhone.getIccPhoneBookInterfaceManager();
    }

    @Override
    public void setTTYMode(int ttyMode, Message onComplete) {
        mActivePhone.setTTYMode(ttyMode, onComplete);
    }

    @Override
    public void queryTTYMode(Message onComplete) {
        mActivePhone.queryTTYMode(onComplete);
    }

    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        mActivePhone.activateCellBroadcastSms(activate, response);
    }

    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        mActivePhone.getCellBroadcastSmsConfig(response);
    }

    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        mActivePhone.setCellBroadcastSmsConfig(configValuesArray, response);
    }

    @Override
    public void notifyDataActivity() {
         mActivePhone.notifyDataActivity();
    }

    @Override
    public void getSmscAddress(Message result) {
        mActivePhone.getSmscAddress(result);
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        mActivePhone.setSmscAddress(address, result);
    }

    @Override
    public int getCdmaEriIconIndex() {
        return mActivePhone.getCdmaEriIconIndex();
    }

    @Override
    public String getCdmaEriText() {
        return mActivePhone.getCdmaEriText();
    }

    @Override
    public int getCdmaEriIconMode() {
        return mActivePhone.getCdmaEriIconMode();
    }

    public Phone getActivePhone() {
        return mActivePhone;
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message onComplete){
        mActivePhone.sendBurstDtmf(dtmfString, on, off, onComplete);
    }

    @Override
    public void exitEmergencyCallbackMode(){
        mActivePhone.exitEmergencyCallbackMode();
    }

    @Override
    public boolean needsOtaServiceProvisioning(){
        return mActivePhone.needsOtaServiceProvisioning();
    }

    @Override
    public boolean isOtaSpNumber(String dialStr){
        return mActivePhone.isOtaSpNumber(dialStr);
    }

    @Override
    public void registerForCallWaiting(Handler h, int what, Object obj){
        mActivePhone.registerForCallWaiting(h,what,obj);
    }

    @Override
    public void unregisterForCallWaiting(Handler h){
        mActivePhone.unregisterForCallWaiting(h);
    }

    @Override
    public void registerForSignalInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForSignalInfo(h,what,obj);
    }

    @Override
    public void unregisterForSignalInfo(Handler h) {
        mActivePhone.unregisterForSignalInfo(h);
    }

    @Override
    public void registerForDisplayInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForDisplayInfo(h,what,obj);
    }

    @Override
    public void unregisterForDisplayInfo(Handler h) {
        mActivePhone.unregisterForDisplayInfo(h);
    }

    @Override
    public void registerForNumberInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForNumberInfo(Handler h) {
        mActivePhone.unregisterForNumberInfo(h);
    }

    @Override
    public void registerForRedirectedNumberInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForRedirectedNumberInfo(h, what, obj);
    }

    @Override
    public void unregisterForRedirectedNumberInfo(Handler h) {
        mActivePhone.unregisterForRedirectedNumberInfo(h);
    }

    @Override
    public void registerForLineControlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForLineControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForLineControlInfo(Handler h) {
        mActivePhone.unregisterForLineControlInfo(h);
    }

    @Override
    public void registerFoT53ClirlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerFoT53ClirlInfo(h, what, obj);
    }

    @Override
    public void unregisterForT53ClirInfo(Handler h) {
        mActivePhone.unregisterForT53ClirInfo(h);
    }

    @Override
    public void registerForT53AudioControlInfo(Handler h, int what, Object obj) {
        mActivePhone.registerForT53AudioControlInfo( h, what, obj);
    }

    @Override
    public void unregisterForT53AudioControlInfo(Handler h) {
        mActivePhone.unregisterForT53AudioControlInfo(h);
    }

    @Override
    public void setOnEcbModeExitResponse(Handler h, int what, Object obj){
        mActivePhone.setOnEcbModeExitResponse(h,what,obj);
    }

    @Override
    public void unsetOnEcbModeExitResponse(Handler h){
        mActivePhone.unsetOnEcbModeExitResponse(h);
    }

    public boolean isManualNetSelAllowed() {
        return mActivePhone.isManualNetSelAllowed();
    }

    @Override
    public boolean isCspPlmnEnabled() {
        return mActivePhone.isCspPlmnEnabled();
    }

    @Override
    public IsimRecords getIsimRecords() {
        return mActivePhone.getIsimRecords();
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        mActivePhone.requestIsimAuthentication(nonce, response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLteOnCdmaMode() {
        return mActivePhone.getLteOnCdmaMode();
    }

    /**
     * {@hide}
     */
    @Override
    public int getLteOnGsmMode() {
        return mActivePhone.getLteOnGsmMode();
    }

    @Override
    public UsimServiceTable getUsimServiceTable() {
        return mActivePhone.getUsimServiceTable();
    }

    @Override
    public void dispose() {
        mCommandsInterface.unregisterForOn(this);
        mCommandsInterface.unregisterForVoiceRadioTechChanged(this);
        mCommandsInterface.unregisterForRilConnected(this);
    }

    @Override
    public void removeReferences() {
        mActivePhone = null;
        mCommandsInterface = null;
    }

    public void getCallBarringOption(String facility, String password, Message onComplete) {
        mActivePhone.getCallBarringOption(facility, password, onComplete);
    }

    public void setCallBarringOption(String facility, boolean lockState, String password,
            Message onComplete) {
        mActivePhone.setCallBarringOption(facility, lockState, password, onComplete);
    }

    public void requestChangeCbPsw(String facility, String oldPwd, String newPwd, Message result) {
        mActivePhone.requestChangeCbPsw(facility, oldPwd, newPwd, result);
    }

    public void registerForModifyCallRequest(Handler h, int what, Object obj)
            throws CallStateException {
        mActivePhone.registerForModifyCallRequest(h, what, obj);
    }

    public void unregisterForModifyCallRequest(Handler h) throws CallStateException {
        mActivePhone.unregisterForModifyCallRequest(h);
    }

    public void registerForAvpUpgradeFailure(Handler h, int what, Object obj)
            throws CallStateException {
        mActivePhone.registerForAvpUpgradeFailure(h, what, obj);
    }

    public void unregisterForAvpUpgradeFailure(Handler h) throws CallStateException {
        mActivePhone.unregisterForAvpUpgradeFailure(h);
    }

    public int getSubscription() {
        return mActivePhone.getSubscription();
    }

    @Override
    public void setTuneAway(boolean tuneAway, Message response) {
        mActivePhone.setTuneAway(tuneAway, response);
    }

    @Override
    public void setPrioritySub(int subIndex, Message response) {
        mActivePhone.setPrioritySub(subIndex, response);
    }

    @Override
    public void setDefaultVoiceSub(int subIndex, Message response) {
        mActivePhone.setDefaultVoiceSub(subIndex, response);
    }

    @Override
    public void setLocalCallHold(int lchStatus, Message response) {
        mActivePhone.setLocalCallHold(lchStatus, response);
    }

    @Override
    public boolean isRadioOn() {
        return mCommandsInterface.getRadioState().isOn();
    }
}
