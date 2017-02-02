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

import android.hardware.radio.V1_0.CdmaCallWaiting;
import android.hardware.radio.V1_0.CdmaSignalInfoRecord;
import android.hardware.radio.V1_0.CdmaSmsMessage;
import android.hardware.radio.V1_0.IRadioIndication;
import android.hardware.radio.V1_0.LceDataInfo;
import android.hardware.radio.V1_0.PcoDataInfo;
import android.hardware.radio.V1_0.SetupDataCallResult;
import android.hardware.radio.V1_0.SimRefreshResult;
import android.hardware.radio.V1_0.StkCcUnsolSsResult;
import android.hardware.radio.V1_0.SuppSvcNotification;
import android.os.AsyncResult;
import android.os.SystemProperties;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.TelephonyProto.SmsSession;
import com.android.internal.telephony.uicc.IccRefreshResponse;

import static com.android.internal.telephony.RILConstants.RIL_UNSOL_CALL_RING;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_DATA_CALL_LIST_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_NITZ_TIME_RECEIVED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_ON_USSD;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_CDMA_NEW_SMS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIGNAL_STRENGTH;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIM_REFRESH;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SIM_SMS_STORAGE_FULL;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_CALL_SETUP;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_EVENT_NOTIFY;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_PROACTIVE_COMMAND;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_STK_SESSION_END;
import static com.android.internal.telephony.RILConstants.RIL_UNSOL_SUPP_SVC_NOTIFICATION;

import java.util.ArrayList;

public class RadioIndication extends IRadioIndication.Stub {
    RIL mRil;

    RadioIndication(RIL ril) {
        mRil = ril;
    }

    /**
     * Indicates when radio state changes.
     * @param indicationType RadioIndicationType
     * @param radioState android.hardware.radio.V1_0.RadioState
     */
    public void radioStateChanged(int indicationType, int radioState) {
        mRil.processIndication(indicationType);

        CommandsInterface.RadioState newState = getRadioStateFromInt(radioState);
        if (RIL.RILJ_LOGD) {
            mRil.unsljLogMore(RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED, "radioStateChanged: " +
                    newState);
        }

        mRil.setRadioState(newState);
    }

    public void callStateChanged(int indicationType) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED);

        mRil.mCallStateRegistrants.notifyRegistrants();
    }

    public void voiceNetworkStateChanged(int indicationType) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED);

        mRil.mVoiceNetworkStateRegistrants.notifyRegistrants();
    }

    public void newSms(int indicationType, ArrayList<Byte> pdu) {
        mRil.processIndication(indicationType);

        byte[] pduArray = RIL.arrayListToPrimitiveArray(pdu);
        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_NEW_SMS);

        mRil.writeMetricsNewSms(SmsSession.Event.Tech.SMS_GSM,
                SmsSession.Event.Format.SMS_FORMAT_3GPP);

        SmsMessage sms = SmsMessage.newFromCMT(pduArray);
        if (mRil.mGsmSmsRegistrant != null) {
            mRil.mGsmSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
        }
    }

    public void newSmsStatusReport(int indicationType, ArrayList<Byte> pdu) {
        mRil.processIndication(indicationType);

        byte[] pduArray = RIL.arrayListToPrimitiveArray(pdu);
        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT);

        if (mRil.mSmsStatusRegistrant != null) {
            mRil.mSmsStatusRegistrant.notifyRegistrant(new AsyncResult(null, pduArray, null));
        }
    }

    public void newSmsOnSim(int indicationType, int recordNumber) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM);

        if (mRil.mSmsOnSimRegistrant != null) {
            mRil.mSmsOnSimRegistrant.notifyRegistrant(new AsyncResult(null, recordNumber, null));
        }
    }

    public void onUssd(int indicationType, int ussdModeType, String msg) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLogMore(RIL_UNSOL_ON_USSD, "" + ussdModeType);

        // todo: Clean this up with a parcelable class for better self-documentation
        String[] resp = new String[2];
        resp[0] = "" + ussdModeType;
        resp[1] = msg;
        if (mRil.mUSSDRegistrant != null) {
            mRil.mUSSDRegistrant.notifyRegistrant(new AsyncResult (null, resp, null));
        }
    }

    public void nitzTimeReceived(int indicationType, String nitzTime, long receivedTime) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_NITZ_TIME_RECEIVED, nitzTime);

        // todo: Clean this up with a parcelable class for better self-documentation
        Object[] result = new Object[2];
        result[0] = nitzTime;
        result[1] = receivedTime;

        boolean ignoreNitz = SystemProperties.getBoolean(
                TelephonyProperties.PROPERTY_IGNORE_NITZ, false);

        if (ignoreNitz) {
            if (RIL.RILJ_LOGD) mRil.riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
        } else {
            if (mRil.mNITZTimeRegistrant != null) {
                mRil.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult (null, result, null));
            }
            // in case NITZ time registrant isn't registered yet, or a new registrant
            // registers later
            mRil.mLastNITZTimeInfo = result;
        }
    }

    public void currentSignalStrength(int indicationType,
                                      android.hardware.radio.V1_0.SignalStrength signalStrength) {
        mRil.processIndication(indicationType);

        /*
        SignalStrength ss = SignalStrength.makeSignalStrengthFromHalObject(signalStrength);
        // Note this is set to "verbose" because it happens frequently
        if (RIL.RILJ_LOGV) mRil.unsljLogvRet(RIL_UNSOL_SIGNAL_STRENGTH, ss);

        if (mRil.mSignalStrengthRegistrant != null) {
            mRil.mSignalStrengthRegistrant.notifyRegistrant(new AsyncResult (null, ss, null));
        } */
    }

    public void dataCallListChanged(int indicationType, ArrayList<SetupDataCallResult> dcList) {
        mRil.processIndication(indicationType);

        ArrayList<DataCallResponse> response = RIL.convertHalDcList(dcList);
        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_DATA_CALL_LIST_CHANGED, response);

        mRil.mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, response, null));
    }

    public void suppSvcNotify(int indicationType, SuppSvcNotification suppSvcNotification) {
        mRil.processIndication(indicationType);

        SuppServiceNotification notification = new SuppServiceNotification();
        notification.notificationType = suppSvcNotification.isMT ? 1 : 0;
        notification.code = suppSvcNotification.code;
        notification.index = suppSvcNotification.index;
        notification.type = suppSvcNotification.type;
        notification.number = suppSvcNotification.number;

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_SUPP_SVC_NOTIFICATION, notification);

        if (mRil.mSsnRegistrant != null) {
            mRil.mSsnRegistrant.notifyRegistrant(new AsyncResult (null, notification, null));
        }
    }

    public void stkSessionEnd(int indicationType) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_STK_SESSION_END);

        if (mRil.mCatSessionEndRegistrant != null) {
            mRil.mCatSessionEndRegistrant.notifyRegistrant(new AsyncResult (null, null, null));
        }
    }

    public void stkProactiveCommand(int indicationType, String cmd) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_STK_PROACTIVE_COMMAND);

        if (mRil.mCatProCmdRegistrant != null) {
            mRil.mCatProCmdRegistrant.notifyRegistrant(new AsyncResult (null, cmd, null));
        }
    }

    public void stkEventNotify(int indicationType, String cmd) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_STK_EVENT_NOTIFY);

        if (mRil.mCatEventRegistrant != null) {
            mRil.mCatEventRegistrant.notifyRegistrant(new AsyncResult (null, cmd, null));
        }
    }

    public void stkCallSetup(int indicationType, long timeout) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_STK_CALL_SETUP, timeout);

        if (mRil.mCatCallSetUpRegistrant != null) {
            mRil.mCatCallSetUpRegistrant.notifyRegistrant(new AsyncResult (null, timeout, null));
        }
    }

    public void simSmsStorageFull(int indicationType) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_SIM_SMS_STORAGE_FULL);

        if (mRil.mIccSmsFullRegistrant != null) {
            mRil.mIccSmsFullRegistrant.notifyRegistrant();
        }
    }

    public void simRefresh(int indicationType, SimRefreshResult refreshResult) {
        mRil.processIndication(indicationType);

        IccRefreshResponse response = new IccRefreshResponse();
        response.refreshResult = refreshResult.type;
        response.efId = refreshResult.efId;
        response.aid = refreshResult.aid;

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_SIM_REFRESH, response);

        if (mRil.mIccRefreshRegistrants != null) {
            mRil.mIccRefreshRegistrants.notifyRegistrants(new AsyncResult (null, response, null));
        }
    }

    public void callRing(int indicationType, boolean isGsm, CdmaSignalInfoRecord record) {
        mRil.processIndication(indicationType);

        char response[] = null;

        // Ignore record for gsm
        if (!isGsm) {
            // todo: Clean this up with a parcelable class for better self-documentation
            response = new char[4];
            response[0] = (char) (record.isPresent ? 1 : 0);
            response[1] = (char) record.signalType;
            response[2] = (char) record.alertPitch;
            response[3] = (char) record.signal;
            mRil.writeMetricsCallRing(response);
        }

        if (RIL.RILJ_LOGD) mRil.unsljLogRet(RIL_UNSOL_CALL_RING, response);

        if (mRil.mRingRegistrant != null) {
            mRil.mRingRegistrant.notifyRegistrant(new AsyncResult (null, response, null));
        }
    }

    public void simStatusChanged(int indicationType) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED);

        if (mRil.mIccStatusChangedRegistrants != null) {
            mRil.mIccStatusChangedRegistrants.notifyRegistrants();
        }
    }

    public void cdmaNewSms(int indicationType, CdmaSmsMessage msg) {
        mRil.processIndication(indicationType);

        if (RIL.RILJ_LOGD) mRil.unsljLog(RIL_UNSOL_RESPONSE_CDMA_NEW_SMS);

        mRil.writeMetricsNewSms(SmsSession.Event.Tech.SMS_CDMA,
                SmsSession.Event.Format.SMS_FORMAT_3GPP2);

        // todo: conversion from CdmaSmsMessage to SmsMessage should be contained in this class so
        // that usage of auto-generated HAL classes is limited to this file
        SmsMessage sms = SmsMessage.newCdmaSmsFromRil(msg);
        if (mRil.mCdmaSmsRegistrant != null) {
            mRil.mCdmaSmsRegistrant.notifyRegistrant(new AsyncResult(null, sms, null));
        }
    }

    public void newBroadcastSms(int indicationType, ArrayList<Byte> var2) {}

    public void cdmaRuimSmsStorageFull(int indicationType) {}

    public void restrictedStateChanged(int indicationType, int var2) {}

    public void enterEmergencyCallbackMode(int indicationType) {}

    public void cdmaCallWaiting(int indicationType, CdmaCallWaiting var2) {}

    public void cdmaOtaProvisionStatus(int indicationType, int var2) {}

    public void cdmaInfoRec(int indicationType,
                            android.hardware.radio.V1_0.CdmaInformationRecords var2) {}

    public void oemHookRaw(int indicationType, ArrayList<Byte> var2) {}

    public void indicateRingbackTone(int indicationType, boolean var2) {}

    public void resendIncallMute(int indicationType) {}

    public void cdmaSubscriptionSourceChanged(int indicationType, int var2) {}

    public void cdmaPrlChanged(int indicationType, int var2) {}

    public void exitEmergencyCallbackMode(int indicationType) {}

    public void rilConnected(int indicationType) {}

    public void voiceRadioTechChanged(int indicationType, int var2) {}

    public void cellInfoList(int indicationType, ArrayList<android.hardware.radio.V1_0.CellInfo> var2) {}

    public void imsNetworkStateChanged(int indicationType) {}

    public void subscriptionStatusChanged(int indicationType, boolean var2) {}

    public void srvccStateNotify(int indicationType, int var2) {}

    public void hardwareConfigChanged(
            int indicationType,
            ArrayList<android.hardware.radio.V1_0.HardwareConfig> var2) {}

    public void radioCapabilityIndication(int indicationType,
                                          android.hardware.radio.V1_0.RadioCapability var2) {}

    public void onSupplementaryServiceIndication(int indicationType, StkCcUnsolSsResult var2) {}

    public void stkCallControlAlphaNotify(int indicationType, String var2) {}

    public void lceData(int indicationType, LceDataInfo var2) {}

    public void pcoData(int indicationType, PcoDataInfo var2) {}

    public void modemReset(int indicationType, String var2) {}

    private CommandsInterface.RadioState getRadioStateFromInt(int stateInt) {
        CommandsInterface.RadioState state;

        switch(stateInt) {
            case android.hardware.radio.V1_0.RadioState.OFF:
                state = CommandsInterface.RadioState.RADIO_OFF;
                break;
            case android.hardware.radio.V1_0.RadioState.UNAVAILABLE:
                state = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case android.hardware.radio.V1_0.RadioState.ON:
                state = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RadioState: " + stateInt);
        }
        return state;
    }
}
