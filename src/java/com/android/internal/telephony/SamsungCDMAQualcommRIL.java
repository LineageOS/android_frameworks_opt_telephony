/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SmsMessage;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.SignalStrength;

import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.SignalToneUtil;

import java.util.ArrayList;
import java.util.Collections;

/**
 * As stands, the RIL supports calling get subscription source which can
 * handle both ruim and nv qc devices. The lteOnCdma property allows
 * for the devices to provision for multiple cdma dun types at run time. This
 * extension of shared merely fixes nuance issues arising from the differences
 * caused by embedded SIM and legacy CDMA.
 * {@hide}
 */
public class SamsungCDMAQualcommRIL extends RIL implements
CommandsInterface {
    protected HandlerThread mIccThread;
    protected IccHandler mIccHandler;
    private Object mSMSLock = new Object();
    private boolean mIsSendingSMS = false;
    public static final long SEND_SMS_TIMEOUT_IN_MS = 30000;

    private final int RIL_INT_RADIO_OFF = 0;
    private final int RIL_INT_RADIO_UNAVALIABLE = 1;
    private final int RIL_INT_RADIO_ON = 2;

    public SamsungCDMAQualcommRIL(Context context, int networkMode,
            int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object responseIccCardStatus(Parcel p) {
        IccCardApplicationStatus ca;

        IccCardStatus status = new IccCardStatus();
        status.setCardState(p.readInt());
        status.setUniversalPinState(p.readInt());
        status.mGsmUmtsSubscriptionAppIndex = p.readInt();
        status.mCdmaSubscriptionAppIndex = p.readInt();
        status.mImsSubscriptionAppIndex = p.readInt();

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        status.mApplications = new IccCardApplicationStatus[numApplications];

        for (int i = 0; i < numApplications; i++) {
            ca = new IccCardApplicationStatus();
            ca.app_type = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            if ((ca.app_state == IccCardApplicationStatus.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((ca.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_READY) ||
                 (ca.perso_substate == IccCardApplicationStatus.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                    // ridiculous hack for network SIM unlock pin
                    ca.app_state = IccCardApplicationStatus.AppState.APPSTATE_UNKNOWN;
                    Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                    Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
                }
            ca.aid = p.readString();
            ca.app_label = p.readString();
            ca.pin1_replaced = p.readInt();
            ca.pin1 = ca.PinStateFromRILInt(p.readInt());
            ca.pin2 = ca.PinStateFromRILInt(p.readInt());
            p.readInt(); // remaining_count_pin1 - pin1_num_retries
            p.readInt(); // remaining_count_puk1 - puk1_num_retries
            p.readInt(); // remaining_count_pin2 - pin2_num_retries
            p.readInt(); // remaining_count_puk2 - puk2_num_retries
            p.readInt(); // - perso_unblock_retries
            status.mApplications[i] = ca;
        }
        return status;
    }

    @Override
    public void
    sendCdmaSms(byte[] pdu, Message result) {
        // Do not send a new SMS until the response for the previous SMS has been received
        //   * for the error case where the response never comes back, time out after
        //     30 seconds and just try the next CDMA_SEND_SMS
        synchronized (mSMSLock) {
            long timeoutTime  = SystemClock.elapsedRealtime() + SEND_SMS_TIMEOUT_IN_MS;
            long waitTimeLeft = SEND_SMS_TIMEOUT_IN_MS;
            while (mIsSendingSMS && (waitTimeLeft > 0)) {
                Log.d(LOG_TAG, "sendCdmaSms() waiting for response of previous CDMA_SEND_SMS");
                try {
                    mSMSLock.wait(waitTimeLeft);
                } catch (InterruptedException ex) {
                    // ignore the interrupt and rewait for the remainder
                }
                waitTimeLeft = timeoutTime - SystemClock.elapsedRealtime();
            }
            if (waitTimeLeft <= 0) {
                Log.e(LOG_TAG, "sendCdmaSms() timed out waiting for response of previous CDMA_SEND_SMS");
            }
            mIsSendingSMS = true;
        }

        super.sendCdmaSms(pdu, result);
    }

    @Override
    protected Object responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        // This is a mashup of algorithms used in
        // SamsungQualcommUiccRIL.java

        // Get raw data
        response = new int[numInts];
        for (int i = 0; i < numInts; i++) {
            response[i] = p.readInt();
        }
        // Take just the least significant byte as the signal strength
        response[2] %= 256;
        response[4] %= 256;

        // RIL_LTE_SignalStrength
        if (response[7] == 99) {
            // If LTE is not enabled, clear LTE results
            // 7-11 must be -1 for GSM signal strength to be used (see
            // frameworks/base/telephony/java/android/telephony/SignalStrength.java)
            response[8] = -1;
            response[9] = -1;
            response[10] = -1;
            response[11] = -1;
        }

        return new SignalStrength(response[0], response[1], response[2], response[3], response[4], response[5], response[6], response[7], response[8], response[9], response[10], response[11], false);

    }

    @Override
    protected Object responseCallList(Parcel p) {
        int num;
        int voiceSettings;
        ArrayList<DriverCall> response;
        DriverCall dc;

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0; i < num; i++) {
            dc = new DriverCall();

            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            dc.isMpty = (0 != p.readInt());
            dc.isMT = (0 != p.readInt());
            dc.als = p.readInt();
            voiceSettings = p.readInt();
            dc.isVoice = (0 == voiceSettings) ? false : true;
            dc.isVoicePrivacy = (0 != p.readInt());
            // Some Samsung magic data for Videocalls
            // hack taken from smdk4210ril class
            voiceSettings = p.readInt();
            // printing it to cosole for later investigation
            Log.d(LOG_TAG, "Samsung magic = " + voiceSettings);
            dc.number = p.readString();
            int np = p.readInt();
            dc.numberPresentation = DriverCall.presentationFromCLIP(np);
            dc.name = p.readString();
            dc.namePresentation = p.readInt();
            int uusInfoPresent = p.readInt();
            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                riljLogv(String.format(
                        "Incoming UUS : type=%d, dcs=%d, length=%d",
                        dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                        dc.uusInfo.getUserData().length));
                riljLogv("Incoming UUS : data (string)="
                        + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): "
                        + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }

            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number,
                    dc.TOA);

            response.add(dc);

            if (dc.isVoicePrivacy) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }

        Collections.sort(response);

        return response;
    }

    @Override
    protected void
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            return;
        }

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {
            try {switch (rr.mRequest) {
            case RIL_REQUEST_GET_SIM_STATUS: ret =  responseIccCardStatus(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: ret =  responseInts(p); break;
            case RIL_REQUEST_GET_CURRENT_CALLS: ret =  responseCallList(p); break;
            case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMSI: ret =  responseString(p); break;
            case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseSetupDataCall(p); break;
            case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
            case RIL_REQUEST_SEND_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =  responseCallForward(p); break;
            case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
            case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMEI: ret =  responseString(p); break;
            case RIL_REQUEST_GET_IMEISV: ret =  responseString(p); break;
            case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
            case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF_STOP: ret =  responseVoid(p); break;
            case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
            case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
            case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
            case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
            case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
            case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
            case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_BAND_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseGetPreferredNetworkType(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: ret =  responseGmsBroadcastConfig(p); break;
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret =  responseCdmaBroadcastConfig(p); break;
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  responseStrings(p); break;
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
            case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
            case 104: ret = responseInts(p); break; // RIL_REQUEST_VOICE_RADIO_TECH
            case 105: ret = responseInts(p); break; // RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE
            case 106: ret = responseStrings(p); break; // RIL_REQUEST_CDMA_PRL_VERSION
            case 107: ret = responseInts(p);  break; // RIL_REQUEST_IMS_REGISTRATION_STATE
            case RIL_REQUEST_VOICE_RADIO_TECH: ret = responseInts(p); break;

            default:
                throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
            }} catch (Throwable tr) {
                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                rr.release();
                return;
            }
        }

        if (error != 0) {
            rr.onError(error, ret);
            rr.release();
            return;
        }

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
            case 1035: ret = responseVoid(p); break; // RIL_UNSOL_VOICE_RADIO_TECH_CHANGED
            case 1036: ret = responseVoid(p); break; // RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED
            case 1037: ret = responseVoid(p); break; // RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE
            case 1038: ret = responseVoid(p); break; // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                int state = p.readInt();
                setRadioStateFromRILInt(state);
                break;
            case RIL_UNSOL_RIL_CONNECTED:
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            case 1035:
            case 1036:
                break;
            case 1037: // RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE
                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
            case 1038:
                break;
        }
    }

    /**
     * Notify all registrants that the ril has connected or disconnected.
     *
     * @param rilVer is the version of the ril or -1 if disconnected.
     */
    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        mRilVersion = rilVer;
        if (mRilConnectedRegistrants != null) {
            mRilConnectedRegistrants.notifyRegistrants(
                                new AsyncResult (null, new Integer(rilVer), null));
        }
    }

    private void setRadioStateFromRILInt (int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;

        switch (stateCode) {
            case RIL_INT_RADIO_OFF:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                if (mIccHandler != null) {
                    mIccThread = null;
                    mIccHandler = null;
                }
                break;
            case RIL_INT_RADIO_UNAVALIABLE:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case RIL_INT_RADIO_ON:
                if (mIccHandler == null) {
                    handlerThread = new HandlerThread("IccHandler");
                    mIccThread = handlerThread;

                    mIccThread.start();

                    looper = mIccThread.getLooper();
                    mIccHandler = new IccHandler(this,looper);
                    mIccHandler.run();
                }
                radioState = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }

        setRadioState (radioState);
    }

    class IccHandler extends Handler implements Runnable {
        private static final int EVENT_RADIO_ON = 1;
        private static final int EVENT_ICC_STATUS_CHANGED = 2;
        private static final int EVENT_GET_ICC_STATUS_DONE = 3;
        private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

        private RIL mRil;
        private boolean mRadioOn = false;

        public IccHandler (RIL ril, Looper looper) {
            super (looper);
            mRil = ril;
        }

        public void handleMessage (Message paramMessage) {
            switch (paramMessage.what) {
                case EVENT_RADIO_ON:
                    mRadioOn = true;
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    AsyncResult asyncResult = (AsyncResult) paramMessage.obj;
                    if (asyncResult.exception != null) {
                        break;
                    }
                    IccCardStatus status = (IccCardStatus) asyncResult.result;
                    if (status.mApplications == null || status.mApplications.length == 0) {
                        if (!mRil.getRadioState().isOn()) {
                            break;
                        }

                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                    } else {
                        int appIndex = status.mCdmaSubscriptionAppIndex;
                        IccCardApplicationStatus application = status.mApplications[appIndex];
                        IccCardApplicationStatus.AppState app_state = application.app_state;
                        IccCardApplicationStatus.AppType app_type = application.app_type;
                        switch (app_state) {
                            case APPSTATE_PIN:
                            case APPSTATE_PUK:
                                switch (app_type) {
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        return;
                                }
                                break;
                            case APPSTATE_READY:
                                switch (app_type) {
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        return;
                                }
                                break;
                            default:
                                return;
                        }
                    }
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (mRadioOn) {
                        mRil.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, paramMessage.obj));
                    }
                    break;
                case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                    mRadioOn = false;
                default:
                    break;
            }
        }

        public void run () {
            mRil.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
            Message msg = obtainMessage(EVENT_RADIO_ON);
            mRil.getIccCardStatus(msg);
        }
    }

    // Workaround for Samsung CDMA "ring of death" bug:
    //
    // Symptom: As soon as the phone receives notice of an incoming call, an
    // audible "old fashioned ring" is emitted through the earpiece and
    // persists through the duration of the call, or until reboot if the call
    // isn't answered.
    //
    // Background: The CDMA telephony stack implements a number of "signal info
    // tones" that are locally generated by ToneGenerator and mixed into the
    // voice call path in response to radio RIL_UNSOL_CDMA_INFO_REC requests.
    // One of these tones, IS95_CONST_IR_SIG_IS54B_L, is requested by the
    // radio just prior to notice of an incoming call when the voice call
    // path is muted. CallNotifier is responsible for stopping all signal
    // tones (by "playing" the TONE_CDMA_SIGNAL_OFF tone) upon receipt of a
    // "new ringing connection", prior to unmuting the voice call path.
    //
    // Problem: CallNotifier's incoming call path is designed to minimize
    // latency to notify users of incoming calls ASAP. Thus,
    // SignalInfoTonePlayer requests are handled asynchronously by spawning a
    // one-shot thread for each. Unfortunately the ToneGenerator API does
    // not provide a mechanism to specify an ordering on requests, and thus,
    // unexpected thread interleaving may result in ToneGenerator processing
    // them in the opposite order that CallNotifier intended. In this case,
    // playing the "signal off" tone first, followed by playing the "old
    // fashioned ring" indefinitely.
    //
    // Solution: An API change to ToneGenerator is required to enable
    // SignalInfoTonePlayer to impose an ordering on requests (i.e., drop any
    // request that's older than the most recent observed). Such a change,
    // or another appropriate fix should be implemented in AOSP first.
    //
    // Workaround: Intercept RIL_UNSOL_CDMA_INFO_REC requests from the radio,
    // check for a signal info record matching IS95_CONST_IR_SIG_IS54B_L, and
    // drop it so it's never seen by CallNotifier. If other signal tones are
    // observed to cause this problem, they should be dropped here as well.
    @Override
    protected void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        final int response = RIL_UNSOL_CDMA_INFO_REC;

        if (infoRec.record instanceof CdmaSignalInfoRec) {
            CdmaSignalInfoRec sir = (CdmaSignalInfoRec) infoRec.record;
            if (sir != null
                    && sir.isPresent
                    && sir.signalType == SignalToneUtil.IS95_CONST_IR_SIGNAL_IS54B
                    && sir.alertPitch == SignalToneUtil.IS95_CONST_IR_ALERT_MED
                    && sir.signal == SignalToneUtil.IS95_CONST_IR_SIG_IS54B_L) {

                Log.d(LOG_TAG, "Dropping \"" + responseToString(response) + " "
                        + retToString(response, sir)
                        + "\" to prevent \"ring of death\" bug.");
                return;
            }
        }

        super.notifyRegistrantsCdmaInfoRec(infoRec);
    }

    @Override
    protected Object
    responseSMS(Parcel p) {
        // Notify that sendSMS() can send the next SMS
        synchronized (mSMSLock) {
            mIsSendingSMS = false;
            mSMSLock.notify();
        }

        return super.responseSMS(p);
    }
}
