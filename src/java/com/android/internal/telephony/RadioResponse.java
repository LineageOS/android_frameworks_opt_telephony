/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.hardware.radio.V1_0.ActivityStatsInfo;
import android.hardware.radio.V1_0.AppStatus;
import android.hardware.radio.V1_0.CardStatus;
import android.hardware.radio.V1_0.CarrierRestrictions;
import android.hardware.radio.V1_0.CdmaBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.DataRegStateResult;
import android.hardware.radio.V1_0.GsmBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.IRadioResponse;
import android.hardware.radio.V1_0.LastCallFailCauseInfo;
import android.hardware.radio.V1_0.LceDataInfo;
import android.hardware.radio.V1_0.LceStatusInfo;
import android.hardware.radio.V1_0.NeighboringCell;
import android.hardware.radio.V1_0.RadioError;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.V1_0.SendSmsResult;
import android.hardware.radio.V1_0.SetupDataCallResult;
import android.hardware.radio.V1_0.VoiceRegStateResult;
import android.os.AsyncResult;
import android.os.Message;
import android.telephony.PhoneNumberUtils;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;
import java.util.Collections;

public class RadioResponse extends IRadioResponse.Stub {
    RIL mRil;

    public RadioResponse(RIL ril) {
        mRil = ril;
    }

    /**
     * Helper function to send response msg
     * @param msg Response message to be sent
     * @param ret Return object to be included in the response message
     */
    private void sendMessageResponse(Message msg, Object ret) {
        if (msg != null) {
            AsyncResult.forMessage(msg, ret, null);
            msg.sendToTarget();
        }
    }

    /**
     * Acknowledge the receipt of radio request sent to the vendor. This must be sent only for
     * radio request which take long time to respond.
     * For more details, refer https://source.android.com/devices/tech/connect/ril.html
     *
     * @param serial Serial no. of the request whose acknowledgement is sent.
     */
    public void acknowledgeRequest(int serial) {
        mRil.processRequestAck(serial);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param cardStatus ICC card status as defined by CardStatus in types.hal
     */
    public void getIccCardStatusResponse(RadioResponseInfo responseInfo, CardStatus cardStatus) {
        responseIccCardStatus(responseInfo, cardStatus);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void supplyIccPinForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void supplyIccPukForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void supplyIccPin2ForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void supplyIccPuk2ForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void changeIccPinForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param remainingAttempts Number of retries remaining, must be equal to -1 if unknown.
     */
    public void changeIccPin2ForAppResponse(RadioResponseInfo responseInfo, int remainingAttempts) {
        responseInts(responseInfo, remainingAttempts);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param retriesRemaining Number of retries remaining, must be equal to -1 if unknown.
     */
    public void supplyNetworkDepersonalizationResponse(RadioResponseInfo responseInfo,
                                                       int retriesRemaining) {
        responseInts(responseInfo, retriesRemaining);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     * @param calls Current call list
     */
    public void getCurrentCallsResponse(RadioResponseInfo responseInfo,
                                        ArrayList<android.hardware.radio.V1_0.Call> calls) {
        responseCurrentCalls(responseInfo, calls);
    }

    /**
     * @param responseInfo Response info struct containing response type, serial no. & error
     */
    public void dialResponse(RadioResponseInfo responseInfo) {
        responseVoid(responseInfo);
    }

    public void getIMSIForAppResponse(RadioResponseInfo responseInfo, String var2) {}

    public void hangupConnectionResponse(RadioResponseInfo responseInfo) {}

    public void hangupWaitingOrBackgroundResponse(RadioResponseInfo responseInfo) {}

    public void hangupForegroundResumeBackgroundResponse(RadioResponseInfo responseInfo) {}

    public void switchWaitingOrHoldingAndActiveResponse(RadioResponseInfo responseInfo) {}

    public void conferenceResponse(RadioResponseInfo responseInfo) {}

    public void rejectCallResponse(RadioResponseInfo responseInfo) {}

    public void getLastCallFailCauseResponse(RadioResponseInfo responseInfo,
                                             LastCallFailCauseInfo var2) {}

    public void getSignalStrengthResponse(RadioResponseInfo responseInfo,
                                          android.hardware.radio.V1_0.SignalStrength var2) {}

    public void getVoiceRegistrationStateResponse(RadioResponseInfo responseInfo,
                                                  VoiceRegStateResult var2) {}

    public void getDataRegistrationStateResponse(RadioResponseInfo responseInfo,
                                                 DataRegStateResult var2) {}

    public void getOperatorResponse(RadioResponseInfo responseInfo,
                                    String var2,
                                    String var3,
                                    String var4) {}

    public void setRadioPowerResponse(RadioResponseInfo responseInfo) {}

    public void sendDtmfResponse(RadioResponseInfo responseInfo) {}

    public void sendSmsResponse(RadioResponseInfo responseInfo,
                                SendSmsResult var2) {}

    public void sendSMSExpectMoreResponse(RadioResponseInfo responseInfo,
                                          SendSmsResult var2) {}

    public void setupDataCallResponse(RadioResponseInfo responseInfo,
                                      SetupDataCallResult var2) {}

    public void iccIOForAppResponse(RadioResponseInfo responseInfo,
                                    android.hardware.radio.V1_0.IccIoResult var2) {}

    public void sendUssdResponse(RadioResponseInfo responseInfo) {}

    public void cancelPendingUssdResponse(RadioResponseInfo responseInfo) {}

    public void getClirResponse(RadioResponseInfo responseInfo, int var2, int var3) {}

    public void setClirResponse(RadioResponseInfo responseInfo) {}

    public void getCallForwardStatusResponse(RadioResponseInfo responseInfo,
                                             ArrayList<android.hardware.radio.V1_0.CallForwardInfo> var2) {}

    public void setCallForwardResponse(RadioResponseInfo responseInfo) {}

    public void getCallWaitingResponse(RadioResponseInfo responseInfo,
                                       boolean var2,
                                       int var3) {}

    public void setCallWaitingResponse(RadioResponseInfo responseInfo) {}

    public void acknowledgeLastIncomingGsmSmsResponse(RadioResponseInfo responseInfo) {}

    public void acceptCallResponse(RadioResponseInfo responseInfo) {}

    public void deactivateDataCallResponse(RadioResponseInfo responseInfo) {}

    public void getFacilityLockForAppResponse(RadioResponseInfo responseInfo, int var2) {}

    public void setFacilityLockForAppResponse(RadioResponseInfo responseInfo, int var2) {}

    public void setBarringPasswordResponse(RadioResponseInfo responseInfo) {}

    public void getNetworkSelectionModeResponse(RadioResponseInfo responseInfo, boolean var2) {}

    public void setNetworkSelectionModeAutomaticResponse(RadioResponseInfo responseInfo) {}

    public void setNetworkSelectionModeManualResponse(RadioResponseInfo responseInfo) {}

    public void getAvailableNetworksResponse(
            RadioResponseInfo responseInfo,
            ArrayList<android.hardware.radio.V1_0.OperatorInfo> var2) {}

    public void startDtmfResponse(RadioResponseInfo responseInfo) {}

    public void stopDtmfResponse(RadioResponseInfo responseInfo) {}

    public void getBasebandVersionResponse(RadioResponseInfo responseInfo, String var2) {}

    public void separateConnectionResponse(RadioResponseInfo responseInfo) {}

    public void setMuteResponse(RadioResponseInfo responseInfo) {}

    public void getMuteResponse(RadioResponseInfo responseInfo, boolean var2) {}

    public void getClipResponse(RadioResponseInfo responseInfo, int var2) {}

    public void getDataCallListResponse(RadioResponseInfo responseInfo,
                                        ArrayList<SetupDataCallResult> var2) {}

    public void sendOemRilRequestRawResponse(RadioResponseInfo responseInfo,
                                             ArrayList<Byte> var2) {}

    public void sendOemRilRequestStringsResponse(RadioResponseInfo responseInfo,
                                                 ArrayList<String> var2) {}

    public void sendScreenStateResponse(RadioResponseInfo responseInfo) {}

    public void setSuppServiceNotificationsResponse(RadioResponseInfo responseInfo) {}

    public void writeSmsToSimResponse(RadioResponseInfo responseInfo, int var2) {}

    public void deleteSmsOnSimResponse(RadioResponseInfo responseInfo) {}

    public void setBandModeResponse(RadioResponseInfo responseInfo) {}

    public void getAvailableBandModesResponse(RadioResponseInfo responseInfo,
                                              ArrayList<Integer> var2) {}

    public void sendEnvelopeResponse(RadioResponseInfo responseInfo, String var2) {}

    public void sendTerminalResponseToSimResponse(RadioResponseInfo responseInfo) {}

    public void handleStkCallSetupRequestFromSimResponse(RadioResponseInfo responseInfo) {}

    public void explicitCallTransferResponse(RadioResponseInfo responseInfo) {}

    public void setPreferredNetworkTypeResponse(RadioResponseInfo responseInfo) {}

    public void getPreferredNetworkTypeResponse(RadioResponseInfo responseInfo, int var2) {}

    public void getNeighboringCidsResponse(RadioResponseInfo responseInfo,
                                           ArrayList<NeighboringCell> var2) {}

    public void setLocationUpdatesResponse(RadioResponseInfo responseInfo) {}

    public void setCdmaSubscriptionSourceResponse(RadioResponseInfo responseInfo) {}

    public void setCdmaRoamingPreferenceResponse(RadioResponseInfo responseInfo) {}

    public void getCdmaRoamingPreferenceResponse(RadioResponseInfo responseInfo, int var2) {}

    public void setTTYModeResponse(RadioResponseInfo responseInfo) {}

    public void getTTYModeResponse(RadioResponseInfo responseInfo, int var2) {}

    public void setPreferredVoicePrivacyResponse(RadioResponseInfo responseInfo) {}

    public void getPreferredVoicePrivacyResponse(RadioResponseInfo responseInfo,
                                                 boolean var2) {}

    public void sendCDMAFeatureCodeResponse(RadioResponseInfo responseInfo) {}

    public void sendBurstDtmfResponse(RadioResponseInfo responseInfo) {}

    public void sendCdmaSmsResponse(RadioResponseInfo responseInfo, SendSmsResult var2) {}

    public void acknowledgeLastIncomingCdmaSmsResponse(RadioResponseInfo responseInfo) {}

    public void getGsmBroadcastConfigResponse(RadioResponseInfo responseInfo,
                                              ArrayList<GsmBroadcastSmsConfigInfo> var2) {}

    public void setGsmBroadcastConfigResponse(RadioResponseInfo responseInfo) {}

    public void setGsmBroadcastActivationResponse(RadioResponseInfo responseInfo) {}

    public void getCdmaBroadcastConfigResponse(RadioResponseInfo responseInfo,
                                               ArrayList<CdmaBroadcastSmsConfigInfo> var2) {}

    public void setCdmaBroadcastConfigResponse(RadioResponseInfo responseInfo) {}

    public void setCdmaBroadcastActivationResponse(RadioResponseInfo responseInfo) {}

    public void getCDMASubscriptionResponse(RadioResponseInfo responseInfo,
                                            String var2,
                                            String var3,
                                            String var4,
                                            String var5,
                                            String var6) {}

    public void writeSmsToRuimResponse(RadioResponseInfo responseInfo, int var2) {}

    public void deleteSmsOnRuimResponse(RadioResponseInfo responseInfo) {}

    public void getDeviceIdentityResponse(RadioResponseInfo responseInfo,
                                          String var2,
                                          String var3,
                                          String var4,
                                          String var5) {}

    public void exitEmergencyCallbackModeResponse(RadioResponseInfo responseInfo) {}

    public void getSmscAddressResponse(RadioResponseInfo responseInfo, String var2) {}

    public void setSmscAddressResponse(RadioResponseInfo responseInfo) {}

    public void reportSmsMemoryStatusResponse(RadioResponseInfo responseInfo) {}

    public void getCdmaSubscriptionSourceResponse(RadioResponseInfo responseInfo, int var2) {}

    public void requestIsimAuthenticationResponse(RadioResponseInfo responseInfo, String var2) {}

    public void acknowledgeIncomingGsmSmsWithPduResponse(RadioResponseInfo responseInfo) {}

    public void sendEnvelopeWithStatusResponse(RadioResponseInfo responseInfo,
                                               android.hardware.radio.V1_0.IccIoResult var2) {}

    public void getVoiceRadioTechnologyResponse(RadioResponseInfo responseInfo, int var2) {}

    public void getCellInfoListResponse(RadioResponseInfo responseInfo,
                                        ArrayList<android.hardware.radio.V1_0.CellInfo> var2) {}

    public void setCellInfoListRateResponse(RadioResponseInfo responseInfo) {}

    public void setInitialAttachApnResponse(RadioResponseInfo responseInfo) {}

    public void getImsRegistrationStateResponse(RadioResponseInfo responseInfo,
                                                boolean var2,
                                                int var3) {}

    public void sendImsSmsResponse(RadioResponseInfo responseInfo, SendSmsResult var2) {}

    public void iccTransmitApduBasicChannelResponse(
            RadioResponseInfo responseInfo,
            android.hardware.radio.V1_0.IccIoResult var2) {}

    public void iccOpenLogicalChannelResponse(RadioResponseInfo responseInfo,
                                              int var2,
                                              ArrayList<Byte> var3) {}

    public void iccCloseLogicalChannelResponse(RadioResponseInfo responseInfo) {}

    public void iccTransmitApduLogicalChannelResponse(
            RadioResponseInfo responseInfo,
            android.hardware.radio.V1_0.IccIoResult var2) {}

    public void nvReadItemResponse(RadioResponseInfo responseInfo, String var2) {}

    public void nvWriteItemResponse(RadioResponseInfo responseInfo) {}

    public void nvWriteCdmaPrlResponse(RadioResponseInfo responseInfo) {}

    public void nvResetConfigResponse(RadioResponseInfo responseInfo) {}

    public void setUiccSubscriptionResponse(RadioResponseInfo responseInfo) {}

    public void setDataAllowedResponse(RadioResponseInfo responseInfo) {}

    public void getHardwareConfigResponse(
            RadioResponseInfo responseInfo,
            ArrayList<android.hardware.radio.V1_0.HardwareConfig> var2) {}

    public void requestIccSimAuthenticationResponse(
            RadioResponseInfo responseInfo,
            android.hardware.radio.V1_0.IccIoResult var2) {}

    public void setDataProfileResponse(RadioResponseInfo responseInfo) {}

    public void requestShutdownResponse(RadioResponseInfo responseInfo) {}

    public void getRadioCapabilityResponse(RadioResponseInfo responseInfo,
                                           android.hardware.radio.V1_0.RadioCapability var2) {}

    public void setRadioCapabilityResponse(RadioResponseInfo responseInfo,
                                           android.hardware.radio.V1_0.RadioCapability var2) {}

    public void startLceServiceResponse(RadioResponseInfo responseInfo, LceStatusInfo var2) {}

    public void stopLceServiceResponse(RadioResponseInfo responseInfo, LceStatusInfo var2) {}

    public void pullLceDataResponse(RadioResponseInfo responseInfo, LceDataInfo var2) {}

    public void getModemActivityInfoResponse(RadioResponseInfo responseInfo,
                                             ActivityStatsInfo var2) {}

    public void setAllowedCarriersResponse(RadioResponseInfo responseInfo, int var2) {}

    public void getAllowedCarriersResponse(RadioResponseInfo responseInfo,
                                           boolean var2,
                                           CarrierRestrictions var3) {}

    public void sendDeviceStateResponse(RadioResponseInfo responseInfo) {}

    public void setIndicationFilterResponse(RadioResponseInfo responseInfo) {}

    private void responseIccCardStatus(RadioResponseInfo responseInfo, CardStatus cardStatus) {
        RILRequest rr = mRil.processResponse(responseInfo);

        if (rr != null) {
            Object ret = null;
            if (responseInfo.error == RadioError.NONE) {
                IccCardStatus iccCardStatus = new IccCardStatus();
                iccCardStatus.setCardState(cardStatus.cardState);
                iccCardStatus.setUniversalPinState(cardStatus.universalPinState);
                iccCardStatus.mGsmUmtsSubscriptionAppIndex = cardStatus.gsmUmtsSubscriptionAppIndex;
                iccCardStatus.mCdmaSubscriptionAppIndex = cardStatus.cdmaSubscriptionAppIndex;
                iccCardStatus.mImsSubscriptionAppIndex = cardStatus.imsSubscriptionAppIndex;
                int numApplications = cardStatus.applications.size();

                // limit to maximum allowed applications
                if (numApplications
                        > com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS) {
                    numApplications =
                            com.android.internal.telephony.uicc.IccCardStatus.CARD_MAX_APPS;
                }
                iccCardStatus.mApplications = new IccCardApplicationStatus[numApplications];
                for (int i = 0; i < numApplications; i++) {
                    AppStatus rilAppStatus = cardStatus.applications.get(i);
                    IccCardApplicationStatus appStatus = new IccCardApplicationStatus();
                    appStatus.app_type       = appStatus.AppTypeFromRILInt(rilAppStatus.appType);
                    appStatus.app_state      = appStatus.AppStateFromRILInt(rilAppStatus.appState);
                    appStatus.perso_substate = appStatus.PersoSubstateFromRILInt(
                            rilAppStatus.persoSubstate);
                    appStatus.aid            = rilAppStatus.aidPtr;
                    appStatus.app_label      = rilAppStatus.appLabelPtr;
                    appStatus.pin1_replaced  = rilAppStatus.pin1Replaced;
                    appStatus.pin1           = appStatus.PinStateFromRILInt(rilAppStatus.pin1);
                    appStatus.pin2           = appStatus.PinStateFromRILInt(rilAppStatus.pin2);
                    iccCardStatus.mApplications[i] = appStatus;
                }
                mRil.riljLog("responseIccCardStatus: from HIDL: " + iccCardStatus);
                ret = iccCardStatus;
                sendMessageResponse(rr.mResult, ret);
            }
            mRil.processResponseDone(rr, responseInfo, ret);
        }
    }

    private void responseInts(RadioResponseInfo responseInfo, int ...var) {
        RILRequest rr = mRil.processResponse(responseInfo);

        if (rr != null) {
            Object ret = null;
            if (responseInfo.error == RadioError.NONE) {
                int[] response = new int[var.length];
                for (int i = 0; i < var.length; i++) {
                    response[i] = var[i];
                }
                ret = response;
                sendMessageResponse(rr.mResult, ret);
            }
            mRil.processResponseDone(rr, responseInfo, ret);
        }
    }

    private void responseCurrentCalls(RadioResponseInfo responseInfo,
                                      ArrayList<android.hardware.radio.V1_0.Call> calls) {
        RILRequest rr = mRil.processResponse(responseInfo);

        if (rr != null) {
            Object ret = null;
            if (responseInfo.error == RadioError.NONE) {
                int num;
                ArrayList<DriverCall> dcCalls;
                DriverCall dc;

                num = calls.size();
                dcCalls = new ArrayList<DriverCall>(num);

                for (int i = 0; i < num; i++) {
                    dc = new DriverCall();
                    // TODO: change name of function stateFromCLCC() in DriverCall.java to name
                    // clarifying what is CLCC
                    dc.state = DriverCall.stateFromCLCC((int) (calls.get(i).state));
                    dc.index = calls.get(i).index;
                    dc.TOA = calls.get(i).toa;
                    dc.isMpty = calls.get(i).isMpty;
                    dc.isMT = calls.get(i).isMT;
                    dc.als = calls.get(i).als;
                    dc.isVoice = calls.get(i).isVoice;
                    dc.isVoicePrivacy = calls.get(i).isVoicePrivacy;
                    dc.number = calls.get(i).number;
                    dc.numberPresentation =
                            DriverCall.presentationFromCLIP(
                                    (int) (calls.get(i).numberPresentation));
                    dc.name = calls.get(i).name;
                    dc.namePresentation =
                            DriverCall.presentationFromCLIP((int) (calls.get(i).namePresentation));
                    if (calls.get(i).uusInfo.size() == 1) {
                        dc.uusInfo = new UUSInfo();
                        dc.uusInfo.setType(calls.get(i).uusInfo.get(0).uusType);
                        dc.uusInfo.setDcs(calls.get(i).uusInfo.get(0).uusDcs);
                        if (calls.get(i).uusInfo.get(0).uusData != null) {
                            byte[] userData = calls.get(i).uusInfo.get(0).uusData.getBytes();
                            dc.uusInfo.setUserData(userData);
                        } else {
                            mRil.riljLog("responseCurrentCalls: uusInfo data is null");
                        }

                        mRil.riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d",
                                dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                                dc.uusInfo.getUserData().length));
                        mRil.riljLogv("Incoming UUS : data (hex): "
                                + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
                    } else {
                        mRil.riljLogv("Incoming UUS : NOT present!");
                    }

                    // Make sure there's a leading + on addresses with a TOA of 145
                    dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

                    dcCalls.add(dc);

                    if (dc.isVoicePrivacy) {
                        mRil.mVoicePrivacyOnRegistrants.notifyRegistrants();
                        mRil.riljLog("InCall VoicePrivacy is enabled");
                    } else {
                        mRil.mVoicePrivacyOffRegistrants.notifyRegistrants();
                        mRil.riljLog("InCall VoicePrivacy is disabled");
                    }
                }

                Collections.sort(dcCalls);

                if ((num == 0) && mRil.mTestingEmergencyCall.getAndSet(false)) {
                    if (mRil.mEmergencyCallbackModeRegistrant != null) {
                        mRil.riljLog("responseCurrentCalls: call ended, testing emergency call,"
                                + " notify ECM Registrants");
                        mRil.mEmergencyCallbackModeRegistrant.notifyRegistrant();
                    }
                }

                ret = dcCalls;
                sendMessageResponse(rr.mResult, ret);
            }
            mRil.processResponseDone(rr, responseInfo, ret);
        }
    }

    private void responseVoid(RadioResponseInfo responseInfo) {
        RILRequest rr = mRil.processResponse(responseInfo);

        if (rr != null) {
            Object ret = null;
            if (responseInfo.error == RadioError.NONE) {
                sendMessageResponse(rr.mResult, ret);
            }
            mRil.processResponseDone(rr, responseInfo, ret);
        }
    }

}
