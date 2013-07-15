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
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.text.TextUtils;
import android.telephony.CellInfo;
import android.telephony.Rlog;
import android.telephony.SignalStrength;

import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Qualcomm RIL class for basebands that do not send the SIM status
 * piggybacked in RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED. Instead,
 * these radios will send radio state and we have to query for SIM
 * status separately.
 * {@hide}
 */
public class HTCCDMAQualcommRIL extends HTCQualcommRIL implements CommandsInterface {

    private String homeOperator = SystemProperties.get("ro.cdma.home.operator.numeric");
    private String operator = SystemProperties.get("ro.cdma.home.operator.alpha");
    private boolean isGSM = false;

    public HTCCDMAQualcommRIL(Context context, int networkMode,
                              int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    public void setPhoneType(int phoneType) {
        super.setPhoneType(phoneType);
        isGSM = (phoneType != RILConstants.CDMA_PHONE);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        // force CDMA + LTE network mode
        boolean forceCdmaLte = needsOldRilFeature("forceCdmaLteNetworkType");

        if (forceCdmaLte && !isGSM) {
            setPreferredNetworkType(NETWORK_MODE_LTE_CDMA_EVDO, null);
        }

        return super.responseIccCardStatus(p);
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        /* HTC signal strength format:
         * 0: GW_SignalStrength
         * 1: GW_SignalStrength.bitErrorRate
         * 2: CDMA_SignalStrength.dbm
         * 3: CDMA_SignalStrength.ecio
         * 4: EVDO_SignalStrength.dbm
         * 5: EVDO_SignalStrength.ecio
         * 6: EVDO_SignalStrength.signalNoiseRatio
         * 7: ATT_SignalStrength.dbm
         * 8: ATT_SignalStrength.ecno
         * 9: LTE_SignalStrength.signalStrength
         * 10: LTE_SignalStrength.rsrp
         * 11: LTE_SignalStrength.rsrq
         * 12: LTE_SignalStrength.rssnr
         * 13: LTE_SignalStrength.cqi
         */
        if(p.dataAvail() < 14*4)
            return super.responseSignalStrength(p);

        int gsmSignalStrength = p.readInt();
        int gsmBitErrorRate = p.readInt();
        int cdmaDbm = p.readInt();
        int cdmaEcio = p.readInt();
        int evdoDbm = p.readInt();
        int evdoEcio = p.readInt();
        int evdoSnr = p.readInt();
        p.readInt(); // ATT_SignalStrength.dbm
        p.readInt(); // ATT_SignalStrength.ecno
        int lteSignalStrength = p.readInt();
        int lteRsrp = p.readInt();
        int lteRsrq = p.readInt();
        int lteRssnr = p.readInt();
        int lteCqi = p.readInt();
        boolean isGsm = (p.readInt() != 0);

        SignalStrength signalStrength = new SignalStrength(gsmSignalStrength,
                gsmBitErrorRate, cdmaDbm, cdmaEcio, evdoDbm, evdoEcio, evdoSnr,
                lteSignalStrength, lteRsrp, lteRsrq, lteRssnr, lteCqi, isGsm);

        return signalStrength;
    }

    @Override
    protected void
    processSolicited(Parcel p) {
        if (isGSM) {
            super.processSolicited(p);
            return;
        }
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: "
                    + serial + " error: " + error);
            return;
        }

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {
            // either command succeeds or command fails but with data payload
            try {
                switch (rr.mRequest) {
            /*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
             */
                    case RIL_REQUEST_GET_SIM_STATUS:
                        ret = responseIccCardStatus(p);
                        break;
                    case RIL_REQUEST_ENTER_SIM_PIN:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_ENTER_SIM_PUK:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_ENTER_SIM_PIN2:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_ENTER_SIM_PUK2:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CHANGE_SIM_PIN:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CHANGE_SIM_PIN2:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_GET_CURRENT_CALLS:
                        ret = responseCallList(p);
                        break;
                    case RIL_REQUEST_DIAL:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GET_IMSI:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_HANGUP:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: {
                        if (mTestingEmergencyCall.getAndSet(false)) {
                            if (mEmergencyCallbackModeRegistrant != null) {
                                riljLog("testing emergency call, notify ECM Registrants");
                                mEmergencyCallbackModeRegistrant.notifyRegistrant();
                            }
                        }
                        ret = responseVoid(p);
                        break;
                    }
                    case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CONFERENCE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_UDUB:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_LAST_CALL_FAIL_CAUSE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SIGNAL_STRENGTH:
                        ret = responseSignalStrength(p);
                        break;
                    //modification start
                    // prevent exceptions from happenimg because the null value is null or a hexadecimel. so convert if it is not null
                    case RIL_REQUEST_VOICE_REGISTRATION_STATE:
                        ret = responseVoiceRegistrationState(p);
                        break;
                    case RIL_REQUEST_DATA_REGISTRATION_STATE:
                        ret = responseDataRegistrationState(p);
                        break;
                    // this fixes bogus values the modem creates
                    // sometimes the  ril may print out
                    // (always on sprint)
                    // sprint: (empty,empty,31000)
                    // this problemaic on sprint, lte won't start, response is slow
                    //speeds up response time on eherpderpd/lte networks
                    case RIL_REQUEST_OPERATOR:
                        ret = operatorCheck(p);
                        break;
                    //end modification
                    case RIL_REQUEST_RADIO_POWER:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_DTMF:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SEND_SMS:
                        ret = responseSMS(p);
                        break;
                    case RIL_REQUEST_SEND_SMS_EXPECT_MORE:
                        ret = responseSMS(p);
                        break;
                    case RIL_REQUEST_SETUP_DATA_CALL:
                        ret = responseSetupDataCall(p);
                        break;
                    case RIL_REQUEST_SIM_IO:
                        ret = responseICC_IO(p);
                        break;
                    case RIL_REQUEST_SEND_USSD:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CANCEL_USSD:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GET_CLIR:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SET_CLIR:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS:
                        ret = responseCallForward(p);
                        break;
                    case RIL_REQUEST_SET_CALL_FORWARD:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_CALL_WAITING:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SET_CALL_WAITING:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SMS_ACKNOWLEDGE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GET_IMEI:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_GET_IMEISV:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_ANSWER:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_DEACTIVATE_DATA_CALL:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_FACILITY_LOCK:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SET_FACILITY_LOCK:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CHANGE_BARRING_PASSWORD:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS:
                        ret = responseOperatorInfos(p);
                        break;
                    case RIL_REQUEST_DTMF_START:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_DTMF_STOP:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_BASEBAND_VERSION:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_SEPARATE_CONNECTION:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SET_MUTE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GET_MUTE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_QUERY_CLIP:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_DATA_CALL_LIST:
                        ret = responseDataCallList(p);
                        break;
                    case RIL_REQUEST_RESET_RADIO:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_OEM_HOOK_RAW:
                        ret = responseRaw(p);
                        break;
                    case RIL_REQUEST_OEM_HOOK_STRINGS:
                        ret = responseStrings(p);
                        break;
                    case RIL_REQUEST_SCREEN_STATE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_WRITE_SMS_TO_SIM:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_DELETE_SMS_ON_SIM:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SET_BAND_MODE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_STK_GET_PROFILE:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_STK_SET_PROFILE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_EXPLICIT_CALL_TRANSFER:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE:
                        ret = responseGetPreferredNetworkType(p);
                        break;
                    case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS:
                        ret = responseCellList(p);
                        break;
                    case RIL_REQUEST_SET_LOCATION_UPDATES:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_SET_TTY_MODE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_QUERY_TTY_MODE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CDMA_FLASH:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_BURST_DTMF:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_SEND_SMS:
                        ret = responseSMS(p);
                        break;
                    case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG:
                        ret = responseGmsBroadcastConfig(p);
                        break;
                    case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_GSM_BROADCAST_ACTIVATION:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG:
                        ret = responseCdmaBroadcastConfig(p);
                        break;
                    case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_SUBSCRIPTION:
                        ret = responseStrings(p);
                        break;
                    case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_DEVICE_IDENTITY:
                        ret = responseStrings(p);
                        break;
                    case RIL_REQUEST_GET_SMSC_ADDRESS:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_SET_SMSC_ADDRESS:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_ISIM_AUTHENTICATION:
                        ret = responseString(p);
                        break;
                    case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU:
                        ret = responseVoid(p);
                        break;
                    case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS:
                        ret = responseICC_IO(p);
                        break;
                    case RIL_REQUEST_VOICE_RADIO_TECH:
                        ret = responseInts(p);
                        break;
                    case RIL_REQUEST_GET_CELL_INFO_LIST:
                        ret = responseCellInfoList(p);
                        break;
                    case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE:
                        ret = responseVoid(p);
                        break;
                    default:
                        throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
                        //break;
                }
            } catch (Throwable tr) {
                // Exceptions here usually mean invalid RIL responses

                Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< "
                        + requestToString(rr.mRequest)
                        + " exception, possible invalid RIL response", tr);

                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                rr.release();
                return;
            }
        }

        // Here and below fake RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED, see b/7255789.
        // This is needed otherwise we don't automatically transition to the main lock
        // screen when the pin or puk is entered incorrectly.
        switch (rr.mRequest) {
            case RIL_REQUEST_ENTER_SIM_PUK:
            case RIL_REQUEST_ENTER_SIM_PUK2:
                if (mIccStatusChangedRegistrants != null) {
                    if (RILJ_LOGD) {
                        riljLog("ON enter sim puk fakeSimStatusChanged: reg count="
                                + mIccStatusChangedRegistrants.size());
                    }
                    mIccStatusChangedRegistrants.notifyRegistrants();
                }
                break;
        }

        if (error != 0) {
            switch (rr.mRequest) {
                case RIL_REQUEST_ENTER_SIM_PIN:
                case RIL_REQUEST_ENTER_SIM_PIN2:
                case RIL_REQUEST_CHANGE_SIM_PIN:
                case RIL_REQUEST_CHANGE_SIM_PIN2:
                case RIL_REQUEST_SET_FACILITY_LOCK:
                    if (mIccStatusChangedRegistrants != null) {
                        if (RILJ_LOGD) {
                            riljLog("ON some errors fakeSimStatusChanged: reg count="
                                    + mIccStatusChangedRegistrants.size());
                        }
                        mIccStatusChangedRegistrants.notifyRegistrants();
                    }
                    break;
            }

            rr.onError(error, ret);
            rr.release();
            return;
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
                + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

    // CDMA FIXES, this fixes  bogus values in nv/sim on htc cdma family
    private Object
    operatorCheck(Parcel p) {
        String response[] = (String[]) responseStrings(p);
        for (int i = 0; i < response.length; i++) {
            if (response[i] != null) {
                if (response[i].equals("       Empty") || (response[i].equals("") && i < 2))
                    response[i] = operator;
                if (response[i].equals("31000") || response[i].equals("11111") || response[i].equals("123456") || response[i].equals("31099") || (response[i].equals("") && i >= 2))
                    response[i] = homeOperator;
            }
        }
        return response;
    }

    // handle exceptions
    private Object
    responseVoiceRegistrationState(Parcel p) {
        String response[] = (String[]) responseStrings(p);
        if (response.length >6) {
            for (int i = 6; i <= 9; i++) {
                if (response[i] == null) {
                    response[i] = Integer.toString(Integer.MAX_VALUE);
                }
            }
        }else{
             return responseDataRegistrationState(p);
        }

        return response;
    }

    // Adjust LTE registration messages in nonstandard format
    private Object
    responseDataRegistrationState(Parcel p) {
        String[] response = (String[]) responseStrings(p);
        if (response.length <= 6 ) {
            String[] newResponse = new String[11];

            for (int i = 0; i < 6; i++)
                newResponse[i] = response[i];

            // TAC and ECI are in hexadecimal without a prefix, so decode won't work
            if (response[1] == null) { // TAC
                newResponse[6] = Integer.toString(Integer.MAX_VALUE);
            } else {
                newResponse[6] = Integer.toString(Integer.parseInt(response[1], 16));
            }
            if (response[2] == null) { // ECI
                newResponse[8] = Integer.toString(Integer.MAX_VALUE);
            } else {
                    newResponse[8] = Integer.toString(Integer.parseInt(response[2], 16));
            }
            // PCI, CSGID, and TADV
            newResponse[7] = newResponse[9] = newResponse[10] = Integer.toString(Integer.MAX_VALUE);
            return newResponse;
        } else {
            return responseVoiceRegistrationState(p) ;
        }
    }
    @Override
    protected DataCallResponse getDataCallResponse(Parcel p, int version) {
        DataCallResponse dataCall = new DataCallResponse();

        dataCall.version = version;
        if (version < 5) {
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            if (version < 4 || needsOldRilFeature("datacallapn")) {
                p.readString(); // APN - not used
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            // DataCallState needs an ifname. Since we don't have one use the name from the ThrottleService resource (default=rmnet0).
            dataCall.ifname = Resources.getSystem().getString(com.android.internal.R.string.config_datause_iface);
        } else {
            dataCall.status = p.readInt();
            if (needsOldRilFeature("usehcradio"))
                dataCall.suggestedRetryTime = -1;
            else
                dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
            if ((dataCall.status == DcFailCause.NONE.getErrorCode()) &&
                    TextUtils.isEmpty(dataCall.ifname)) {
                dataCall.ifname = Resources.getSystem().getString(com.android.internal.R.string.config_datause_iface);
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            String dnses = p.readString();
            if (!TextUtils.isEmpty(dnses)) {
                dataCall.dnses = dnses.split(" ");
            }
            String gateways = p.readString();
            if (!TextUtils.isEmpty(gateways)) {
                dataCall.gateways = gateways.split(" ");
            }
        }
        return dataCall;
    }
}
