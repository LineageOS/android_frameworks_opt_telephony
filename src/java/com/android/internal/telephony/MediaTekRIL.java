/*
 * Copyright (C) 2014 The OmniROM Project <http://www.omnirom.org>
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
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;

import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.Display;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import java.io.IOException;
import java.io.InputStream;

import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccRefreshResponse;
import com.android.internal.telephony.uicc.SIMRecords;
import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.uicc.UiccController;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.IccSmsStorageStatus;
import com.mediatek.internal.telephony.NetworkInfoWithAcT;
import com.mediatek.internal.telephony.SrvccCallContext;
import com.mediatek.internal.telephony.cdma.CdmaFeatureOptionUtils;
import com.mediatek.internal.telephony.dataconnection.IaExtendParam;
import com.mediatek.internal.telephony.ltedc.svlte.SvlteUtils;
import com.mediatek.internal.telephony.worldphone.WorldMode;


public class MediaTekRIL extends RIL implements CommandsInterface {

    private static final String  PROPERTY_RIL_CARD_TYPE_SET = "gsm.ril.cardtypeset";
    private static final String  PROPERTY_RIL_CARD_TYPE_SET_2 = "gsm.ril.cardtypeset.2";
    private static final String  PROPERTY_NET_CDMA_MDMSTAT = "net.cdma.mdmstat";
    private static final int INITIAL_RETRY_INTERVAL_MSEC = 200;
    private static final String  PROPERTY_CONFIG_EMDSTATUS_SEND = "ril.cdma.emdstatus.send";

    /* ALPS00799783: for restore previous preferred network type when set type fail */
    private int mPreviousPreferredType = -1;

    /// M: C2K RILD socket name definition
    static final String C2K_SOCKET_NAME_RIL = "rild-via";

    private static final String[]  PROPERTY_RIL_FULL_UICC_TYPE = {
        "gsm.ril.fulluicctype",
        "gsm.ril.fulluicctype.2",
        "gsm.ril.fulluicctype.3",
        "gsm.ril.fulluicctype.4",
    };
    private static final int CARD_TYPE_SIM  = 1;
    private static final int CARD_TYPE_USIM = 2;
    private static final int CARD_TYPE_CSIM = 4;
    private static final int CARD_TYPE_RUIM = 8;

    /// M: CC009: DTMF request special handling @{
    /* DTMF request will be ignored when duplicated sending */
    private class dtmfQueueHandler {

        public dtmfQueueHandler() {
            mDtmfStatus = DTMF_STATUS_STOP;
        }

        public void start() {
            mDtmfStatus = DTMF_STATUS_START;
        }

        public void stop() {
            mDtmfStatus = DTMF_STATUS_STOP;
        }

        public boolean isStart() {
            return (mDtmfStatus == DTMF_STATUS_START);
        }

        public void add(RILRequest o) {
            mDtmfQueue.addElement(o);
        }

        public void remove(RILRequest o) {
            mDtmfQueue.remove(o);
        }

        public void remove(int idx) {
            mDtmfQueue.removeElementAt(idx);
        }

        public RILRequest get() {
            return (RILRequest) mDtmfQueue.get(0);
        }

        public int size() {
            return mDtmfQueue.size();
        }

        public void setPendingRequest(RILRequest r) {
            mPendingCHLDRequest = r;
        }

        public RILRequest getPendingRequest() {
            return mPendingCHLDRequest;
        }

        public void setSendChldRequest() {
            mIsSendChldRequest = true;
        }

        public void resetSendChldRequest() {
            mIsSendChldRequest = false;
        }

        public boolean hasSendChldRequest() {
            riljLog("mIsSendChldRequest = " + mIsSendChldRequest);
            return mIsSendChldRequest;
        }

        public final int MAXIMUM_DTMF_REQUEST = 32;
        private final boolean DTMF_STATUS_START = true;
        private final boolean DTMF_STATUS_STOP = false;

        private boolean mDtmfStatus = DTMF_STATUS_STOP;
        private Vector mDtmfQueue = new Vector(MAXIMUM_DTMF_REQUEST);

        private RILRequest mPendingCHLDRequest = null;
        private boolean mIsSendChldRequest = false;
    }

    private dtmfQueueHandler mDtmfReqQueue = new dtmfQueueHandler();
    /// @}

    public MediaTekRIL(Context context, int networkMode, int cdmaSubscription) {
            super(context, networkMode, cdmaSubscription, null);
    }

    public MediaTekRIL(Context context, int networkMode, int cdmaSubscription, Integer instanceId) {
            super(context, networkMode, cdmaSubscription, instanceId);
    }

    // all that C&P just for responseOperator overriding?
    @Override
    protected RILRequest
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: "
                            + serial + " error: " + error);
            return null;
        }

        /// M: CC012: DTMF request special handling @{
        /* DTMF request will be ignored when the count of requests reaches 32 */
        if ((rr.mRequest == RIL_REQUEST_DTMF_START) ||
            (rr.mRequest == RIL_REQUEST_DTMF_STOP)) {
            synchronized (mDtmfReqQueue) {
                mDtmfReqQueue.remove(rr);
                riljLog("remove first item in dtmf queue done, size = " + mDtmfReqQueue.size());
                if (mDtmfReqQueue.size() > 0) {
                    RILRequest rr2 = mDtmfReqQueue.get();
                    if (RILJ_LOGD) riljLog(rr2.serialString() + "> " + requestToString(rr2.mRequest));
                    send(rr2);
                } else {
                    if (mDtmfReqQueue.getPendingRequest() != null) {
                        riljLog("send pending switch request");
                        send(mDtmfReqQueue.getPendingRequest());
                        mDtmfReqQueue.setSendChldRequest();
                        mDtmfReqQueue.setPendingRequest(null);
                    }
                }
            }
        }
        /// @}
        Object ret = null;

        if ((rr.mRequest == RIL_REQUEST_QUERY_AVAILABLE_NETWORKS) ||
            (rr.mRequest == RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT)) {
            mGetAvailableNetworkDoneRegistrant.notifyRegistrants();
        }

        /* ALPS00799783 START */
        if (rr.mRequest == RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE) {
            if ((error != 0) && (mPreviousPreferredType != -1)) {
                riljLog("restore mPreferredNetworkType from " + mPreferredNetworkType + " to " + mPreviousPreferredType);
                mPreferredNetworkType = mPreviousPreferredType;
            }
            mPreviousPreferredType = -1; //reset
        }
        /* ALPS00799783 END */

        /// M: CC012: DTMF request special handling @{
        if (rr.mRequest == RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE ||
            rr.mRequest == RIL_REQUEST_CONFERENCE ||
            rr.mRequest == RIL_REQUEST_SEPARATE_CONNECTION ||
            rr.mRequest == RIL_REQUEST_EXPLICIT_CALL_TRANSFER) {
            riljLog("clear mIsSendChldRequest");
            mDtmfReqQueue.resetSendChldRequest();
        }
        /// @}

        if (error == 0 || p.dataAvail() > 0) {

            /* Convert RIL_REQUEST_GET_MODEM_VERSION back */
            if (SystemProperties.get("ro.cm.device").indexOf("e73") == 0 &&
                  rr.mRequest == 220) {
                rr.mRequest = RIL_REQUEST_BASEBAND_VERSION;
            }

            // either command succeeds or command fails but with data payload
            try {switch (rr.mRequest) {
            /*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
             */
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
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: {
                if (mTestingEmergencyCall.getAndSet(false)) {
                    if (mEmergencyCallbackModeRegistrant != null) {
                        riljLog("testing emergency call, notify ECM Registrants");
                        mEmergencyCallbackModeRegistrant.notifyRegistrant();
                    }
                }
                ret =  responseVoid(p);
                break;
            }
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseFailCause(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseOperator(p); break;
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
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret = responseVoid(p); break; //VoLTE
            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
            case RIL_REQUEST_ABORT_QUERY_AVAILABLE_NETWORKS: ret =  responseVoid(p); break;
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
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret = responseSetPreferredNetworkType(p); break;
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret = responseGetPreferredNetworkType(p); break;
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
            /*ret = responseInts(p);RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM modify for UIM sms cache*/
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseStrings(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
            case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
            case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: ret =  responseInts(p); break;
            case RIL_REQUEST_ISIM_AUTHENTICATION:
                if (SystemProperties.get("ro.mtk_tc1_feature").equals("1"))
                    ret =  responseStringEncodeBase64(p);
                else
                    ret =  responseString(p);
                break;
            case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU: ret = responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS: ret = responseICC_IO(p); break;
            case RIL_REQUEST_VOICE_RADIO_TECH: ret = responseInts(p); break;
            case RIL_REQUEST_GET_CELL_INFO_LIST: ret = responseCellInfoList(p); break;
            case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_INITIAL_ATTACH_APN: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_DATA_PROFILE: ret = responseVoid(p); break;
            case RIL_REQUEST_IMS_REGISTRATION_STATE: ret = responseInts(p); break;
            case RIL_REQUEST_IMS_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC: ret =  responseICC_IO(p); break;
            case RIL_REQUEST_SIM_OPEN_CHANNEL: ret  = responseInts(p); break;
            case RIL_REQUEST_SIM_CLOSE_CHANNEL: ret  = responseVoid(p); break;
            case RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL: ret = responseICC_IO(p); break;
            case RIL_REQUEST_NV_READ_ITEM: ret = responseString(p); break;
            case RIL_REQUEST_NV_WRITE_ITEM: ret = responseVoid(p); break;
            case RIL_REQUEST_NV_WRITE_CDMA_PRL: ret = responseVoid(p); break;
            case RIL_REQUEST_NV_RESET_CONFIG: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_UICC_SUBSCRIPTION: ret = responseVoid(p); break;
            case RIL_REQUEST_ALLOW_DATA: ret = responseVoid(p); break;
            case RIL_REQUEST_GET_HARDWARE_CONFIG: ret = responseHardwareConfig(p); break;
            case RIL_REQUEST_SIM_AUTHENTICATION: ret =  responseICC_IOBase64(p); break;
            case RIL_REQUEST_SHUTDOWN: ret = responseVoid(p); break;
            case RIL_REQUEST_GET_RADIO_CAPABILITY: ret =  responseRadioCapability(p); break;
            case RIL_REQUEST_SET_RADIO_CAPABILITY: ret =  responseRadioCapability(p); break;
            /// M: CC010: Add RIL interface @{
            case RIL_REQUEST_HANGUP_ALL: ret =  responseVoid(p); break;
            case RIL_REQUEST_FORCE_RELEASE_CALL: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_CALL_INDICATION: ret = responseVoid(p); break;
            case RIL_REQUEST_EMERGENCY_DIAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_ECC_SERVICE_CATEGORY: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_ECC_LIST: ret = responseVoid(p); break;
            /// @}
            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            case RIL_REQUEST_SET_SPEECH_CODEC_INFO: ret = responseVoid(p); break;
            /// @}
            /// M: For 3G VT only @{
            case RIL_REQUEST_VT_DIAL: ret = responseVoid(p); break;
            case RIL_REQUEST_VOICE_ACCEPT: ret = responseVoid(p); break;
            case RIL_REQUEST_REPLACE_VT_CALL: ret = responseVoid(p); break;
            /// @}
            /// M: IMS feature. @{
            case RIL_REQUEST_ADD_IMS_CONFERENCE_CALL_MEMBER: responseString(p); break;
            case RIL_REQUEST_REMOVE_IMS_CONFERENCE_CALL_MEMBER: responseString(p); break;
            case RIL_REQUEST_DIAL_WITH_SIP_URI: ret = responseVoid(p); break;
            case RIL_REQUEST_RESUME_CALL: ret = responseVoid(p); break;
            case RIL_REQUEST_HOLD_CALL: ret = responseVoid(p); break;
            /// @}

            //MTK-START SS
            case RIL_REQUEST_GET_COLP: ret = responseInts(p); break;
            case RIL_REQUEST_SET_COLP: ret = responseVoid(p); break;
            case RIL_REQUEST_GET_COLR: ret = responseInts(p); break;
            //MTK-END SS

            //MTK-START SIM ME lock
            case RIL_REQUEST_QUERY_SIM_NETWORK_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_SIM_NETWORK_LOCK: ret =  responseInts(p); break;
            //MTK-END SIM ME lock
            //MTK-START multiple application support
            case RIL_REQUEST_GENERAL_SIM_AUTH: ret =  responseICC_IO(p); break;
            case RIL_REQUEST_OPEN_ICC_APPLICATION: ret = responseInts(p); break;
            case RIL_REQUEST_GET_ICC_APPLICATION_STATUS: ret = responseIccCardStatus(p); break;
            //MTK-END multiple application support
            case RIL_REQUEST_SIM_IO_EX: ret =  responseICC_IO(p); break;
            // PHB Start
            case RIL_REQUEST_QUERY_PHB_STORAGE_INFO: ret = responseInts(p); break;
            case RIL_REQUEST_WRITE_PHB_ENTRY: ret = responseVoid(p); break;
            case RIL_REQUEST_READ_PHB_ENTRY: ret = responsePhbEntries(p); break;
            case RIL_REQUEST_QUERY_UPB_CAPABILITY: ret = responseInts(p); break;
            case RIL_REQUEST_READ_UPB_GRP: ret = responseInts(p); break;
            case RIL_REQUEST_WRITE_UPB_GRP: ret = responseVoid(p); break;
            case RIL_REQUEST_EDIT_UPB_ENTRY: ret = responseVoid(p); break;
            case RIL_REQUEST_DELETE_UPB_ENTRY: ret = responseVoid(p); break;
            case RIL_REQUEST_READ_UPB_GAS_LIST: ret = responseStrings(p); break;
            case RIL_REQUEST_GET_PHB_STRING_LENGTH: ret = responseInts(p); break;
            case RIL_REQUEST_GET_PHB_MEM_STORAGE : ret = responseGetPhbMemStorage(p); break;
            case RIL_REQUEST_SET_PHB_MEM_STORAGE : responseVoid(p); break;
            case RIL_REQUEST_READ_PHB_ENTRY_EXT: ret = responseReadPhbEntryExt(p); break;
            case RIL_REQUEST_WRITE_PHB_ENTRY_EXT: ret = responseVoid(p); break;
            // PHB End


            /* M: network part start */
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL_WITH_ACT: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_POL_CAPABILITY: ret = responseInts(p); break;
            case RIL_REQUEST_GET_POL_LIST: ret = responseNetworkInfoWithActs(p); break;
            case RIL_REQUEST_SET_POL_ENTRY: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_TRM: ret = responseInts(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT : ret =  responseOperatorInfosWithAct(p); break;
            case RIL_REQUEST_STK_EVDL_CALL_BY_AP: ret = responseVoid(p); break;

            //Femtocell (CSG) feature START
            case RIL_REQUEST_GET_FEMTOCELL_LIST: ret = responseFemtoCellInfos(p); break;
            case RIL_REQUEST_ABORT_FEMTOCELL_LIST: ret = responseVoid(p); break;
            case RIL_REQUEST_SELECT_FEMTOCELL: ret = responseVoid(p); break;
            //Femtocell (CSG) feature END
            /* M: network part end */

            case RIL_REQUEST_QUERY_MODEM_TYPE: ret = responseInts(p); break;
            case RIL_REQUEST_STORE_MODEM_TYPE: ret = responseVoid(p); break;

            // IMS
            case RIL_REQUEST_SET_IMS_ENABLE: ret = responseVoid(p); break;
            case RIL_REQUEST_SIM_GET_ATR: ret = responseString(p); break;
            // M: Fast Dormancy
            case RIL_REQUEST_SET_SCRI: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_FD_MODE: ret = responseInts(p); break;

            // MTK-START, SMS part
            case RIL_REQUEST_GET_SMS_PARAMS: ret = responseSmsParams(p); break;
            case RIL_REQUEST_SET_SMS_PARAMS: ret = responseVoid(p); break;
            case RIL_REQUEST_GET_SMS_SIM_MEM_STATUS: ret = responseSimSmsMemoryStatus(p); break;
            case RIL_REQUEST_SET_ETWS: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_CB_CHANNEL_CONFIG_INFO: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_CB_LANGUAGE_CONFIG_INFO: ret = responseVoid(p); break;
            case RIL_REQUEST_GET_CB_CONFIG_INFO: ret = responseCbConfig(p); break;
            case RIL_REQUEST_REMOVE_CB_MESSAGE: ret = responseVoid(p); break;
            // MTK-END, SMS part
            case RIL_REQUEST_SET_DATA_CENTRIC: ret = responseVoid(p); break;

            /// M: SVLTE Remove access feature
            case RIL_REQUEST_CONFIG_MODEM_STATUS: ret = responseVoid(p); break;

            // M: CC33 LTE.
            case RIL_REQUEST_SET_DATA_ON_TO_MD: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_REMOVE_RESTRICT_EUTRAN_MODE: ret = responseVoid(p); break;

            case RIL_REQUEST_BTSIM_CONNECT: ret = responseString(p); break;
            case RIL_REQUEST_BTSIM_DISCONNECT_OR_POWEROFF: ret = responseVoid(p); break;
            case RIL_REQUEST_BTSIM_POWERON_OR_RESETSIM: ret = responseString(p); break;
            case RIL_REQUEST_BTSIM_TRANSFERAPDU: ret = responseString(p); break;

            /// M: IMS VoLTE conference dial feature. @{
            case RIL_REQUEST_CONFERENCE_DIAL: ret =  responseVoid(p); break;
            /// @}
            case RIL_REQUEST_RELOAD_MODEM_TYPE: ret =  responseVoid(p); break;
            /// M: CC010: Add RIL interface @{
            case RIL_REQUEST_SET_IMS_CALL_STATUS: ret = responseVoid(p); break;
            /// @}

            /// M: CC072: Add Customer proprietary-IMS RIL interface. @{
            case RIL_REQUEST_SET_SRVCC_CALL_CONTEXT_TRANSFER: ret = responseVoid(p); break;
            case RIL_REQUEST_UPDATE_IMS_REGISTRATION_STATUS: ret = responseVoid(p); break;
            /// @}

            /* M: C2K part start */
            case RIL_REQUEST_GET_NITZ_TIME: ret = responseGetNitzTime(p); break;
            case RIL_REQUEST_QUERY_UIM_INSERTED: ret = responseInts(p); break;
            case RIL_REQUEST_SWITCH_HPF: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_AVOID_SYS: ret = responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVOID_SYS: ret = responseInts(p); break;
            case RIL_REQUEST_QUERY_CDMA_NETWORK_INFO: ret = responseStrings(p); break;
            case RIL_REQUEST_GET_LOCAL_INFO: ret =  responseInts(p); break;
            case RIL_REQUEST_UTK_REFRESH: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_SMS_AND_PHONEBOOK_STATUS: ret = responseInts(p); break;
            case RIL_REQUEST_QUERY_NETWORK_REGISTRATION: ret = responseInts(p); break;
            case RIL_REQUEST_AGPS_TCP_CONNIND: ret = responseVoid(p); break;
            case RIL_REQUEST_AGPS_SET_MPC_IPPORT: ret = responseVoid(p); break;
            case RIL_REQUEST_AGPS_GET_MPC_IPPORT: ret = responseStrings(p); break;
            case RIL_REQUEST_SET_MEID: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_ETS_DEV: ret =  responseVoid(p); break;
            case RIL_REQUEST_WRITE_MDN: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_VIA_TRM: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_ARSI_THRESHOLD: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_ACTIVE_PS_SLOT: ret = responseVoid(p); break;
            case RIL_REQUEST_CONFIRM_INTER_3GPP_IRAT_CHANGE: ret = responseVoid(p); break;
            case RIL_REQUEST_CONFIG_EVDO_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_QUERY_UTK_MENU_FROM_MD: ret =  responseString(p); break;
            case RIL_REQUEST_QUERY_STK_MENU_FROM_MD: ret =  responseString(p); break;
            case RIL_REQUEST_DEACTIVATE_LINK_DOWN_PDN: ret = responseVoid(p); break;
            /* M: C2K part end */

            case RIL_REQUEST_MODEM_POWERON: ret =  responseVoid(p); break;
            case RIL_REQUEST_MODEM_POWEROFF: ret =  responseVoid(p); break;

            /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @{
            case RIL_REQUEST_SET_SVLTE_RAT_MODE: ret =  responseVoid(p); break;
            /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @}

            /// M: [C2K][IR] Support SVLTE IR feature. @{
            case RIL_REQUEST_SET_REG_SUSPEND_ENABLED: ret = responseVoid(p); break;
            case RIL_REQUEST_RESUME_REGISTRATION: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_REG_SUSPEND_ENABLED_CDMA: ret =  responseVoid(p); break;
            case RIL_REQUEST_RESUME_REGISTRATION_CDMA: ret =  responseVoid(p); break;
            /// M: [C2K][IR] Support SVLTE IR feature. @}
            case RIL_REQUEST_SET_STK_UTK_MODE: ret = responseVoid(p); break;

            case RIL_REQUEST_SWITCH_ANTENNA: ret = responseVoid(p); break;
            case RIL_REQUEST_START_LCE: ret = responseLceStatus(p); break;
            case RIL_REQUEST_STOP_LCE: ret = responseLceStatus(p); break;
            case RIL_REQUEST_PULL_LCEDATA: ret = responseLceData(p); break;
            case RIL_REQUEST_GET_ACTIVITY_INFO: ret = responseActivityData(p); break;
            case RIL_REQUEST_SWITCH_CARD_TYPE: ret = responseVoid(p); break;
            case RIL_REQUEST_ENABLE_MD3_SLEEP: ret = responseVoid(p); break;

            // M: [LTE][Low Power][UL traffic shaping] Start
            case RIL_REQUEST_SET_LTE_ACCESS_STRATUM_REPORT: ret = responseVoid(p); break;
            case RIL_REQUEST_SET_LTE_UPLINK_DATA_TRANSFER: ret = responseVoid(p); break;
            // M: [LTE][Low Power][UL traffic shaping] End
            default:
                throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
            //break;
            }} catch (Throwable tr) {
                // Exceptions here usually mean invalid RIL responses

                Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< "
                        + requestToString(rr.mRequest)
                        + " exception, possible invalid RIL response", tr);

                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                return rr;
            }
        }

        if (rr.mRequest == RIL_REQUEST_SHUTDOWN) {
            // Set RADIO_STATE to RADIO_UNAVAILABLE to continue shutdown process
            // regardless of error code to continue shutdown procedure.
            riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " +
                    error + " Setting Radio State to Unavailable regardless of error.");
            setRadioState(RadioState.RADIO_UNAVAILABLE);
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
                case RIL_REQUEST_GET_RADIO_CAPABILITY: {
                    // Ideally RIL's would support this or at least give NOT_SUPPORTED
                    // but the hammerhead RIL reports GENERIC :(
                    // TODO - remove GENERIC_FAILURE catching: b/21079604
                    if (REQUEST_NOT_SUPPORTED == error ||
                            GENERIC_FAILURE == error) {
                        // we should construct the RAF bitmask the radio
                        // supports based on preferred network bitmasks
                        ret = makeStaticRadioCapability();
                        error = 0;
                    }
                    break;
                }
                case RIL_REQUEST_GET_ACTIVITY_INFO:
                    ret = new ModemActivityInfo(0, 0, 0,
                            new int [ModemActivityInfo.TX_POWER_LEVELS], 0, 0);
                    error = 0;
                    break;
            }

            rr.onError(error, ret);
            return rr;
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
            + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        return rr;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        switch(response) {
            // select AOSP unsols to process differently
            /*
            case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
            */
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
            // MTK unsols
            case RIL_UNSOL_NEIGHBORING_CELL_INFO: ret = responseStrings(p); break;
            case RIL_UNSOL_NETWORK_INFO: ret = responseStrings(p); break;
            case RIL_UNSOL_NETWORK_EXIST: ret = responseInts(p); break;
            case RIL_UNSOL_FEMTOCELL_INFO: ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_PS_NETWORK_STATE_CHANGED: ret =  responseInts(p); break;
            case RIL_UNSOL_INVALID_SIM:  ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_ACMT: ret = responseInts(p); break;
            case RIL_UNSOL_IMEI_LOCK: ret = responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED: ret = responseInts(p); break;
            case RIL_UNSOL_STK_EVDL_CALL: ret = responseInts(p); break;
            case RIL_UNSOL_STK_CALL_CTRL: ret = responseStrings(p); break;

            case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED: ret =  responseInts(p); break;
            case RIL_UNSOL_SRVCC_STATE_NOTIFY: ret = responseInts(p); break;
            case RIL_UNSOL_HARDWARE_CONFIG_CHANGED: ret = responseHardwareConfig(p); break;
            case RIL_UNSOL_RADIO_CAPABILITY:
                    ret = responseRadioCapability(p); break;
            case RIL_UNSOL_ON_SS: ret =  responseSsData(p); break;
            case RIL_UNSOL_STK_CC_ALPHA_NOTIFY: ret =  responseStrings(p); break;
            case RIL_UNSOL_LCEDATA_RECV: ret = responseLceData(p); break;
            /// M: CC010: Add RIL interface @{
            case RIL_UNSOL_CALL_FORWARDING: ret = responseInts(p); break;
            case RIL_UNSOL_CRSS_NOTIFICATION: ret = responseCrssNotification(p); break;
            case RIL_UNSOL_INCOMING_CALL_INDICATION: ret = responseStrings(p); break;
            case RIL_UNSOL_CIPHER_INDICATION: ret = responseStrings(p); break;
            //case RIL_UNSOL_CNAP: ret = responseStrings(p); break; //obsolete
            /// @}
            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            case RIL_UNSOL_SPEECH_CODEC_INFO: ret =  responseInts(p); break;
            /// @}
            //MTK-START multiple application support
            case RIL_UNSOL_APPLICATION_SESSION_ID_CHANGED: ret = responseInts(p); break;
            //MTK-END multiple application support
            case RIL_UNSOL_SIM_MISSING: ret = responseInts(p); break;
            case RIL_UNSOL_SIM_RECOVERY: ret = responseInts(p); break;
            case RIL_UNSOL_VIRTUAL_SIM_ON: ret = responseInts(p); break;
            case RIL_UNSOL_VIRTUAL_SIM_OFF: ret = responseInts(p); break;
            case RIL_UNSOL_SIM_PLUG_OUT: ret = responseVoid(p); break;
            case RIL_UNSOL_SIM_PLUG_IN: ret = responseVoid(p); break;
            case RIL_UNSOL_TRAY_PLUG_IN: ret = responseVoid(p); break;
            case RIL_UNSOL_SIM_COMMON_SLOT_NO_CHANGED: ret = responseVoid(p); break;
            case RIL_UNSOL_DATA_ALLOWED: ret = responseVoid(p); break;
            case RIL_UNSOL_PHB_READY_NOTIFICATION: ret = responseInts(p); break;
            case RIL_UNSOL_STK_SETUP_MENU_RESET: ret = responseVoid(p); break;
            // IMS
            case RIL_UNSOL_IMS_ENABLE_DONE: ret = responseVoid(p); break;
            case RIL_UNSOL_IMS_DISABLE_DONE: ret = responseVoid(p); break;
            case RIL_UNSOL_IMS_REGISTRATION_INFO: ret = responseInts(p); break;
            // M: Fast Dormancy
            case RIL_UNSOL_SCRI_RESULT: ret = responseInts(p); break;

            case RIL_UNSOL_RESPONSE_PLMN_CHANGED: ret = responseStrings(p); break;
            case RIL_UNSOL_RESPONSE_REGISTRATION_SUSPENDED: ret = responseInts(p); break;
            //Remote SIM ME lock related APIs [Start]
            case RIL_UNSOL_MELOCK_NOTIFICATION: ret = responseInts(p); break;
            //Remote SIM ME lock related APIs [End]
            case RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_SUPPORT: ret = responseInts(p); break;

            /// M: IMS feature. @{
            //For updating call ids for conference call after SRVCC is done.
            case RIL_UNSOL_ECONF_SRVCC_INDICATION: ret = responseInts(p); break;
            //For updating conference call merged/added result.
            case RIL_UNSOL_ECONF_RESULT_INDICATION: ret = responseStrings(p); break;
            //For updating call mode and pau information.
            case RIL_UNSOL_CALL_INFO_INDICATION : ret = responseStrings(p); break;
            /// @}

            case RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_INFO: ret = responseInts(p); break;
            case RIL_UNSOL_SRVCC_HANDOVER_INFO_INDICATION:ret = responseInts(p); break;
            // M: CC33 LTE.
            case RIL_UNSOL_RAC_UPDATE: ret = responseVoid(p); break;
            case RIL_UNSOL_REMOVE_RESTRICT_EUTRAN: ret = responseVoid(p); break;

            //MTK-START for MD state change
            case RIL_UNSOL_MD_STATE_CHANGE: ret = responseInts(p); break;
            //MTK-END for MD state change

            case RIL_UNSOL_MO_DATA_BARRING_INFO: ret = responseInts(p); break;
            case RIL_UNSOL_SSAC_BARRING_INFO: ret = responseInts(p); break;

            /// M: CC071: Add Customer proprietary-IMS RIL interface. @{
            case RIL_UNSOL_EMERGENCY_BEARER_SUPPORT_NOTIFY: ret = responseInts(p); break;
            /// @}

            /* M: C2K part start*/
            case RIL_UNSOL_CDMA_CALL_ACCEPTED: ret = responseVoid(p); break;
            case RIL_UNSOL_UTK_SESSION_END: ret = responseVoid(p); break;
            case RIL_UNSOL_UTK_PROACTIVE_COMMAND: ret = responseString(p); break;
            case RIL_UNSOL_UTK_EVENT_NOTIFY: ret = responseString(p); break;
            case RIL_UNSOL_VIA_GPS_EVENT: ret = responseInts(p); break;
            case RIL_UNSOL_VIA_NETWORK_TYPE_CHANGE: ret = responseInts(p); break;
            case RIL_UNSOL_VIA_INVALID_SIM_DETECTED: ret = responseVoid(p); break;
            /* M: C2K part end*/
            case RIL_UNSOL_ABNORMAL_EVENT: ret = responseStrings(p); break;
            case RIL_UNSOL_CDMA_CARD_TYPE: ret = responseInts(p); break;
            /// M: [C2K] for eng mode start
            case RIL_UNSOL_ENG_MODE_NETWORK_INFO:
                ret = responseStrings(p);
                unsljLog(response);
                break;
            /// M: [C2K] for eng mode end

            /// M: [C2K][IR] Support SVLTE IR feature. @{
            case RIL_UNSOL_CDMA_PLMN_CHANGED: ret = responseStrings(p); break;
            /// M: [C2K][IR] Support SVLTE IR feature. @}

            /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
            case RIL_UNSOL_GMSS_RAT_CHANGED: ret = responseInts(p); break;
            /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

            case RIL_UNSOL_SET_ATTACH_APN: ret = responseVoid(p); break;

            // MTK-START, SMS part
            // SMS ready
            case RIL_UNSOL_SMS_READY_NOTIFICATION: ret = responseVoid(p); break;
            // New SMS but phone storage is full
            case RIL_UNSOL_ME_SMS_STORAGE_FULL: ret = responseVoid(p); break;
            // ETWS primary notification
            case RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION: ret = responseEtwsNotification(p); break;
            // MTK-END, SMS part

            /// M: [C2K] For ps type changed.
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_TYPE_CHANGED: ret = responseInts(p); break;

            ///M: [C2K][MD IRAT] start @{
            case RIL_UNSOL_INTER_3GPP_IRAT_STATE_CHANGE:
                riljLog(" RIL_UNSOL_INTER_3GPP_IRAT_STATE_CHANGE...");
                ret = responseIratStateChange(p);
                break;
            /// }@ [C2K][MD IRAT] end
            case RIL_UNSOL_IMSI_REFRESH_DONE: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_IMSI_READY: ret = responseVoid(p); break;
            // M: Notify RILJ that the AT+EUSIM was received
            case RIL_UNSOL_EUSIM_READY: ret = responseVoid(p); break;
            // M: Notify RILJ that call fade happened
            case RIL_UNSOL_CDMA_SIGNAL_FADE: ret = responseInts(p); break;
            // M: Notify RILJ that the AT+EFNM was received
            case RIL_UNSOL_CDMA_TONE_SIGNALS: ret = responseInts(p); break;
            /// M: BIP {
            case RIL_UNSOL_STK_BIP_PROACTIVE_COMMAND: ret = responseString(p); break;
            /// M: BIP }
            //WorldMode
            case RIL_UNSOL_WORLD_MODE_CHANGED: ret = responseInts(p); break;
            /// M: For 3G VT only @{
            case RIL_UNSOL_VT_STATUS_INFO: ret = responseInts(p); break;
            case RIL_UNSOL_VT_RING_INFO: ret = responseVoid(p); break;
            /// @}

            // M: [LTE][Low Power][UL traffic shaping] Start
            case RIL_UNSOL_LTE_ACCESS_STRATUM_STATE_CHANGE: ret = responseInts(p); break;
            // M: [LTE][Low Power][UL traffic shaping] End
            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        // To avoid duplicating code from RIL.java, we rewrite some response codes to fit
        // AOSP's one (when they do the same effect)
        boolean rewindAndReplace = false;
        int newResponseCode = 0;

        switch (response) {
            // xen0n: MTK TODO
            /*
            case RIL_UNSOL_STK_SESSION_END:
                if (RILJ_LOGD) unsljLog(response);

                /// M: SVLTE UTK feature @{
                if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport())
                        && (mUtkSessionEndRegistrant != null)
                        && (mUtkSessionEndRegistrant.getHandler() != null)
                        && (mStkSwitchMode == IUtkService.SVLTE_UTK_MODE)) {
                    riljLog("SVLTE UTK received PS session end from MD1");
                    mUtkSessionEndRegistrant.notifyRegistrant(
                            new AsyncResult(null, ret, null));
                } else {
                    if (mCatSessionEndRegistrant != null) {
                        mCatSessionEndRegistrant.notifyRegistrant(
                                new AsyncResult(null, ret, null));
                    }
                }
                /// @}
                break;

            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
                if (RILJ_LOGD) unsljLog(response);

                /// M: SVLTE UTK feature @{
                if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport())
                        && (mUtkProCmdRegistrant != null)
                        && (mUtkProCmdRegistrant.getHandler() != null)
                        && (mStkSwitchMode == IUtkService.SVLTE_UTK_MODE)
                        && (mBipPsType != IUtkService.SVLTE_BIP_TYPE_ON_LTE)) {
                    riljLog("SVLTE UTK received PS proactive command from MD1");
                    mUtkProCmdRegistrant.notifyRegistrant(
                            new AsyncResult(null, ret, null));
                } else {
                    if (mCatProCmdRegistrant != null) {
                        mCatProCmdRegistrant.notifyRegistrant(
                                new AsyncResult(null, ret, null));
                    }
                }
                /// @}
                break;

            // case RIL_UNSOL_STK_EVENT_NOTIFY:
            // identical to upstream, not ported
            */

            case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
                if (RILJ_LOGD) unsljLog(response);

                // MTK-START, SMS part
                if (mIccSmsFullRegistrant != null) {
                    mIccSmsFullRegistrant.notifyRegistrant();
                } else {
                    // Phone process is not ready and cache it then wait register to notify
                    if (RILJ_LOGD) Rlog.d(RILJ_LOG_TAG, "Cache sim sms full event");
                    mIsSmsSimFull = true;
                }
                // MTK-END, SMS part
                break;

            case RIL_UNSOL_RIL_CONNECTED: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                // Set ecc list before MO call
                if  (TelephonyManager.getDefault().getMultiSimConfiguration() == TelephonyManager.MultiSimVariants.DSDA
                        || mInstanceId == 0) {
                    setEccList();
                }

                // Initial conditions
                //setRadioPower(false, null);

                setCdmaSubscriptionSource(mCdmaSubscription, null);
                setCellInfoListRate(Integer.MAX_VALUE, null);
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                //[ALPS01810775,ALPS01868743]-Start
                //"isScreenOn" removed and replaced by mDefaultDisplayState
                //sendScreenState(isScreenOn);
                if (mDefaultDisplayState == Display.STATE_ON) {
                    sendScreenState(true);
                } else if (mDefaultDisplayState == Display.STATE_OFF) {
                    sendScreenState(false);
                } else {
                    riljLog("not setScreenState mDefaultDisplayState="
                            + mDefaultDisplayState);
                }
                //[ALPS01810775,ALPS01868743]-End
                break;
            }

            case RIL_UNSOL_NEIGHBORING_CELL_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mNeighboringInfoRegistrants != null) {
                    mNeighboringInfoRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_NETWORK_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                 /* M: Start - abnormal event logging for logger */
                if (ret != null) {
                    String[] networkinfo = (String[]) ret;
                    int type = Integer.parseInt(networkinfo[0]);
                    //type 400 for abnormal testing event.
                    //type 401 for no service event.
                    //type 402 for C2K voice call drop.
                    //type 403 for C2K SMS failure.
                    if (type == 401 || type == 402 || type == 403) {
                        Intent intent = new Intent(
                                TelephonyIntents.ACTION_EXCEPTION_HAPPENED);
                        intent.putExtra("Reason", "SmartLogging");
                        intent.putExtra("from_where", "RIL");
                        mContext.sendBroadcast(intent);
                        riljLog("Broadcast for SmartLogging " + type);
                        break;
                    }
                }
                /* M: End - abnormal event logging for logger */
                if (mNetworkInfoRegistrants != null) {
                    mNetworkInfoRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_NETWORK_EXIST:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mNetworkExistRegistrants != null) {
                    mNetworkExistRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_HARDWARE_CONFIG_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mHardwareConfigChangeRegistrants != null) {
                    mHardwareConfigChangeRegistrants.notifyRegistrants(
                                             new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_RADIO_CAPABILITY:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                mRadioCapability = (RadioCapability)ret;
                if (mPhoneRadioCapabilityChangedRegistrants != null) {
                    mPhoneRadioCapabilityChangedRegistrants.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                 }
                 break;
            case RIL_UNSOL_ON_SS:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mSsRegistrant != null) {
                    mSsRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;
            case RIL_UNSOL_STK_CC_ALPHA_NOTIFY:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatCcAlphaRegistrant != null) {
                    mCatCcAlphaRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;
            case RIL_UNSOL_LCEDATA_RECV:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mLceInfoRegistrant != null) {
                    mLceInfoRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;
            /// M: CC010: Add RIL interface @{
            case RIL_UNSOL_CALL_FORWARDING:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mCallForwardingInfoRegistrants != null) {
                    boolean bCfuEnabled = (((int[]) ret)[0] == 1);
                    boolean bIsLine1 = (((int[]) ret)[1] == 1);
                    /* ONLY notify for Line1 */
                    if (bIsLine1) {
                        mCfuReturnValue = ret;
                        mCallForwardingInfoRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                    }
                }
                break;

            case RIL_UNSOL_CRSS_NOTIFICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCallRelatedSuppSvcRegistrant != null) {
                    mCallRelatedSuppSvcRegistrant.notifyRegistrant(
                                        new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_INCOMING_CALL_INDICATION:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                // example of how mindless copying can adversely affect functionality
                // if (mIncomingCallIndicationRegistrant != null) {
                //     mIncomingCallIndicationRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                // }
                setCallIndication((String[])ret);
                rewindAndReplace = true;
                newResponseCode = RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED;
                break;

            case RIL_UNSOL_CIPHER_INDICATION:
                if (RILJ_LOGD) unsljLogvRet(response, ret);

                int simCipherStatus = Integer.parseInt(((String[]) ret)[0]);
                int sessionStatus = Integer.parseInt(((String[]) ret)[1]);
                int csStatus = Integer.parseInt(((String[]) ret)[2]);
                int psStatus = Integer.parseInt(((String[]) ret)[3]);

                riljLog("RIL_UNSOL_CIPHER_INDICATION :" + simCipherStatus + " " + sessionStatus + " " + csStatus + " " + psStatus);

                int[] cipherResult = new int[3];

                cipherResult[0] = simCipherStatus;
                cipherResult[1] = csStatus;
                cipherResult[2] = psStatus;

                if (mCipherIndicationRegistrant != null) {
                    mCipherIndicationRegistrant.notifyRegistrants(
                        new AsyncResult(null, cipherResult, null));
                }

                break;
            //obsolete
            /*
            case RIL_UNSOL_CNAP:
                    String[] respCnap = (String[]) ret;
                    int validity = Integer.parseInt(((String[]) ret)[1]);

                    riljLog("RIL_UNSOL_CNAP :" + respCnap[0] + " " + respCnap[1]);
                    if (validity == 0) {
                        if (mCnapNotifyRegistrant != null) {
                            mCnapNotifyRegistrant.notifyRegistrant(
                                            new AsyncResult(null, respCnap, null));
                        }
                    }

                break;
                */
            /// @}

            /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
            case RIL_UNSOL_SPEECH_CODEC_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);

                if (mSpeechCodecInfoRegistrant != null) {
                    mSpeechCodecInfoRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
                }
            break;
            /// @}

            case RIL_UNSOL_RESPONSE_PS_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLog(response);
                int[] stat = null;
                if (ret != null) {
                    stat = (int[]) ret;
                }
                mPsNetworkStateRegistrants
                        .notifyRegistrants(new AsyncResult(null, stat, null));
            break;

            /* M: network part start */
            case RIL_UNSOL_IMEI_LOCK:
                if (RILJ_LOGD) unsljLog(response);
                if (mImeiLockRegistrant != null) {
                    mImeiLockRegistrant.notifyRegistrants(new AsyncResult(null, null, null));
                }
                break;

            //ALPS00248788 START
            case RIL_UNSOL_INVALID_SIM:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mInvalidSimInfoRegistrant != null) {
                   mInvalidSimInfoRegistrant.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            //ALPS00248788 END
            //MTK-START [MTK80515] [ALPS00368272]
            case RIL_UNSOL_RESPONSE_MMRR_STATUS_CHANGED:
                if (RILJ_LOGD) unsljLog(response);
                if (ret != null) {
                    int[] emmrrs = (int[]) ret;
                    int ps_status = Integer.valueOf(emmrrs[0]);

                    /*
                    if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
                        try {
                            if (mServiceStateExt.isBroadcastEmmrrsPsResume(ps_status)) {
                                riljLog("Broadcast for EMMRRS: android.intent.action.EMMRRS_PS_RESUME ");
                            }
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }
                    */
                }
                break;
            //MTK-END [MTK80515] [ALPS00368272]

            case RIL_UNSOL_FEMTOCELL_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                mFemtoCellInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                break;

            // ALPS00297719 START
            case RIL_UNSOL_RESPONSE_ACMT:
                if (RILJ_LOGD) unsljLog(response);
                if (ret != null) {
                    int[] acmt = (int[]) ret;
                    if (acmt.length == 2) {
                        int error_type = Integer.valueOf(acmt[0]);
                        int error_cause = acmt[1];

                        /*
                        if (!SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
                            try {
                                if (mServiceStateExt.needBrodcastAcmt(error_type, error_cause)
                                        == true) {
                                    Intent intent = new Intent(
                                            TelephonyIntents.ACTION_ACMT_NETWORK_SERVICE_STATUS_INDICATOR);
                                    intent.putExtra("CauseCode", acmt[1]);
                                    intent.putExtra("CauseType", acmt[0]);
                                    mContext.sendBroadcast(intent);
                                    riljLog("Broadcast for ACMT: com.VendorName.CauseCode "
                                            + acmt[1] + "," + acmt[0]);
                                }
                            } catch (RuntimeException e) {
                                e.printStackTrace();
                            }
                        }
                        */
                    }
                }
                break;
            // ALPS00297719 END
            /* M: network part end */
            case RIL_UNSOL_STK_EVDL_CALL:
                // if (false == SystemProperties.get("ro.mtk_bsp_package").equals("1")) {
                    if (RILJ_LOGD) unsljLogvRet(response, ret);
                    if (mStkEvdlCallRegistrant != null) {
                        mStkEvdlCallRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    }
                // }
                break;

            case RIL_UNSOL_STK_CALL_CTRL:
                if (RILJ_LOGD) {
                    unsljLogvRet(response, ret);
                }
                if (mStkCallCtrlRegistrant != null) {
                    mStkCallCtrlRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_STK_SETUP_MENU_RESET:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mStkSetupMenuResetRegistrant != null) {
                    mStkSetupMenuResetRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;

            //MTK-START multiple application support
            case RIL_UNSOL_APPLICATION_SESSION_ID_CHANGED: {
                if (RILJ_LOGD) unsljLog(response);
                if (mSessionChangedRegistrants != null) {
                    mSessionChangedRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            }
            //MTK-END multiple application support
            case RIL_UNSOL_SIM_MISSING:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mSimMissing != null) {
                    mSimMissing.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_SIM_RECOVERY:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mSimRecovery != null) {
                    mSimRecovery.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_VIRTUAL_SIM_ON:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mVirtualSimOn != null) {
                    mVirtualSimOn.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_VIRTUAL_SIM_OFF:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mVirtualSimOff != null) {
                    mVirtualSimOff.notifyRegistrants(
                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_SIM_PLUG_OUT:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mSimPlugOutRegistrants != null) {
                    mSimPlugOutRegistrants.notifyRegistrants(
                        new AsyncResult(null, ret, null));
                }
                mCfuReturnValue = null;
                break;
            case RIL_UNSOL_SIM_PLUG_IN:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mSimPlugInRegistrants != null) {
                    mSimPlugInRegistrants.notifyRegistrants(
                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_TRAY_PLUG_IN:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mTrayPlugInRegistrants != null) {
                    mTrayPlugInRegistrants.notifyRegistrants(
                        new AsyncResult(null, ret, null));
                }
                break;

            // MTK-START, SMS part
            // SMS ready notification
            case RIL_UNSOL_SMS_READY_NOTIFICATION:
                if (RILJ_LOGD) unsljLog(response);

                if (mSmsReadyRegistrants.size() != 0) {
                    mSmsReadyRegistrants.notifyRegistrants();
                } else {
                    // Phone process is not ready and cache it then wait register to notify
                    if (RILJ_LOGD) Rlog.d(RILJ_LOG_TAG, "Cache sms ready event");
                    mIsSmsReady = true;
                }
                break;

            // New SMS but phone storage is full
            case RIL_UNSOL_ME_SMS_STORAGE_FULL:
                if (RILJ_LOGD) unsljLog(response);
                if (mMeSmsFullRegistrant != null) {
                    mMeSmsFullRegistrant.notifyRegistrant();
                }
                break;

            // ETWS primary notification
            case RIL_UNSOL_RESPONSE_ETWS_NOTIFICATION:
                if (RILJ_LOGD) unsljLog(response);
                if (mEtwsNotificationRegistrant != null) {
                    mEtwsNotificationRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;
            // MTK-END, SMS part

            case RIL_UNSOL_SIM_COMMON_SLOT_NO_CHANGED:
                if (RILJ_LOGD) unsljLog(response);
                if (mCommonSlotNoChangedRegistrants != null) {
                    mCommonSlotNoChangedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                }
                break;
            case RIL_UNSOL_DATA_ALLOWED:
                if (RILJ_LOGD) unsljLog(response);
                if (mDataAllowedRegistrants != null) {
                    mDataAllowedRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
                }
                break;

            case RIL_UNSOL_PHB_READY_NOTIFICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mPhbReadyRegistrants != null) {
                    mPhbReadyRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_IMS_REGISTRATION_INFO:
                if (RILJ_LOGD) unsljLog(response);
                if (mImsRegistrationInfoRegistrants != null) {
                    mImsRegistrationInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_RESPONSE_PLMN_CHANGED:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                synchronized (mWPMonitor) {
                    mEcopsReturnValue = ret;
                    if (mPlmnChangeNotificationRegistrant.size() > 0) {
                        if (RILJ_LOGD) riljLog("ECOPS,notify mPlmnChangeNotificationRegistrant");
                        mPlmnChangeNotificationRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                    }
                }
                break;
            case RIL_UNSOL_RESPONSE_REGISTRATION_SUSPENDED:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                synchronized (mWPMonitor) {
                    mEmsrReturnValue = ret;
                    if (mRegistrationSuspendedRegistrant != null) {
                        if (RILJ_LOGD) riljLog("EMSR, notify mRegistrationSuspendedRegistrant");
                        mRegistrationSuspendedRegistrant.notifyRegistrant(
                            new AsyncResult(null, ret, null));
                    }
                }
                break;
            //Remote SIM ME lock related APIs [Start]
            case RIL_UNSOL_MELOCK_NOTIFICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mMelockRegistrants != null) {
                    mMelockRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;
            //Remote SIM ME lock related APIs [End]
            case RIL_UNSOL_IMS_ENABLE_DONE:
                if (RILJ_LOGD) unsljLog(response);
                if (mImsEnableRegistrants != null) {
                    mImsEnableRegistrants.notifyRegistrants();
                }
                break;
            case RIL_UNSOL_IMS_DISABLE_DONE:
                if (RILJ_LOGD) unsljLog(response);
                if (mImsDisableRegistrants != null) {
                    mImsDisableRegistrants.notifyRegistrants();
                }
                break;
            // M: Fast Dormancy
            case RIL_UNSOL_SCRI_RESULT:
                Integer scriResult = (((int[]) ret)[0]);
                riljLog("s:" + scriResult + ":" + (((int[]) ret)[0]));
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mScriResultRegistrant != null) {
                   mScriResultRegistrant.notifyRegistrant(new AsyncResult(null, scriResult, null));
                }
                break;
            case RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_SUPPORT:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mEpsNetworkFeatureSupportRegistrants != null) {
                    mEpsNetworkFeatureSupportRegistrants.notifyRegistrants(
                                        new AsyncResult(null, ret, null));
                }
                break;

            /// M: IMS feature. @{
            //For updating call ids for conference call after SRVCC is done.
            case RIL_UNSOL_ECONF_SRVCC_INDICATION:
                if (RILJ_LOGD) unsljLog(response);
                if (mEconfSrvccRegistrants != null) {
                    mEconfSrvccRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            //For updating conference call merged/added result.
            case RIL_UNSOL_ECONF_RESULT_INDICATION:
                if (RILJ_LOGD) unsljLog(response);
                if (mEconfResultRegistrants != null) {
                     riljLog("Notify ECONF result");
                     String[] econfResult = (String[]) ret;
                     riljLog("ECONF result = " + econfResult[3]);
                     mEconfResultRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            //For updating call mode and pau information.
            case RIL_UNSOL_CALL_INFO_INDICATION :
                if (RILJ_LOGD) unsljLog(response);
                if (mCallInfoRegistrants != null) {
                   mCallInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            /// @}

            case RIL_UNSOL_VOLTE_EPS_NETWORK_FEATURE_INFO:
                if (RILJ_LOGD) unsljLog(response);
                if (mEpsNetworkFeatureInfoRegistrants != null) {
                   mEpsNetworkFeatureInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_SRVCC_HANDOVER_INFO_INDICATION:
                if (RILJ_LOGD) unsljLog(response);
                if (mSrvccHandoverInfoIndicationRegistrants != null) {
                    mSrvccHandoverInfoIndicationRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            //MTK-START for MD state change
            case RIL_UNSOL_MD_STATE_CHANGE:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                break;
            //MTK-END for MD state change

            case RIL_UNSOL_MO_DATA_BARRING_INFO:
                if (RILJ_LOGD) unsljLog(response);
                if (mMoDataBarringInfoRegistrants != null) {
                    mMoDataBarringInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_SSAC_BARRING_INFO:
                if (RILJ_LOGD) unsljLog(response);
                if (mSsacBarringInfoRegistrants != null) {
                    mSsacBarringInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;

            /// M: CC071: Add Customer proprietary-IMS RIL interface. @[
            case RIL_UNSOL_EMERGENCY_BEARER_SUPPORT_NOTIFY:
                if (RILJ_LOGD) unsljLog(response);
                if (mEmergencyBearerSupportInfoRegistrants != null) {
                    mEmergencyBearerSupportInfoRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            /// @}

            // M: CC33 LTE.
            case RIL_UNSOL_RAC_UPDATE:
                if (RILJ_LOGD) unsljLog(response);
                mRacUpdateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
                break;
            case RIL_UNSOL_REMOVE_RESTRICT_EUTRAN:
                if (RILJ_LOGD) unsljLog(response);
                mRemoveRestrictEutranRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
                break;

            case RIL_UNSOL_SET_ATTACH_APN:
                if (RILJ_LOGD) unsljLog(response);
                mResetAttachApnRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
                break;

            /* M: C2K part start */
            case RIL_UNSOL_CDMA_CALL_ACCEPTED:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }

                if (mAcceptedRegistrant != null) {
                    mAcceptedRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_UTK_SESSION_END:
                if (RILJ_LOGD) {
                    unsljLog(response);
                }

                if (mUtkSessionEndRegistrant != null) {
                    mUtkSessionEndRegistrant.notifyRegistrant(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_UTK_PROACTIVE_COMMAND:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }

                if (mUtkProCmdRegistrant != null) {
                    mUtkProCmdRegistrant.notifyRegistrant(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_UTK_EVENT_NOTIFY:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mUtkEventRegistrant != null) {
                    mUtkEventRegistrant.notifyRegistrant(
                                        new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_VIA_GPS_EVENT:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mViaGpsEvent != null) {
                    mViaGpsEvent.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_VIA_NETWORK_TYPE_CHANGE:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mNetworkTypeChangedRegistrant != null) {
                    mNetworkTypeChangedRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_VIA_INVALID_SIM_DETECTED:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mInvalidSimDetectedRegistrant != null) {
                    mInvalidSimDetectedRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            /* M: C2K part end*/
            case RIL_UNSOL_ABNORMAL_EVENT:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mAbnormalEventRegistrant != null) {
                    mAbnormalEventRegistrant.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_CDMA_CARD_TYPE:
                if (RILJ_LOGD) {
                    unsljLogvRet(response, ret);
                }
                if (mCdmaCardTypeRegistrants != null) {
                    mCdmaCardTypeValue = ret;
                    mCdmaCardTypeRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
                }
                break;
            /// M:[C2K] for eng mode start
            case RIL_UNSOL_ENG_MODE_NETWORK_INFO:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                if (mEngModeNetworkInfoRegistrant != null) {
                    mEngModeNetworkInfoRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            /// M:[C2K] for eng mode end

            /// M: [C2K][IR] Support SVLTE IR feature. @{
            case RIL_UNSOL_CDMA_PLMN_CHANGED:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                String mccmnc = "";
                if (ret != null && ret instanceof String[]) {
                    String s[] = (String[]) ret;
                    if (s.length >= 2) {
                        mccmnc = s[0] + s[1];
                    }
                }
                riljLog("mccmnc changed mccmnc=" + mccmnc);
                mMccMncChangeRegistrants.notifyRegistrants(new AsyncResult(null, mccmnc, null));
                break;
            /// M: [C2K][IR] Support SVLTE IR feature. @}

            /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
            case RIL_UNSOL_GMSS_RAT_CHANGED:
                if (RILJ_LOGD) {
                    unsljLogvRet(response, ret);
                }
                int[] rat = (int[]) ret;
                riljLog("Notify RIL_UNSOL_GMSS_RAT_CHANGED result rat = " + rat);
                if (mGmssRatChangedRegistrant != null) {
                    mGmssRatChangedRegistrant.notifyRegistrants(
                            new AsyncResult(null, rat, null));
                }
                break;
            /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

            /// M: [C2K] for ps type changed. @{
            case RIL_UNSOL_RESPONSE_DATA_NETWORK_TYPE_CHANGED:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }

                if (mDataNetworkTypeChangedRegistrant != null) {
                    mDataNetworkTypeChangedRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            /// @}
            ///M: [C2K][MD IRAT] start @{
            case RIL_UNSOL_INTER_3GPP_IRAT_STATE_CHANGE:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                mIratStateChangeRegistrant.notifyRegistrants(new AsyncResult(null, ret, null));
                break;
            /// @} [C2K][MD IRAT] end
            case RIL_UNSOL_IMSI_REFRESH_DONE:
                if (RILJ_LOGD) {
                    unsljLog(response);
                }
                if (mImsiRefreshDoneRegistrant != null) {
                    mImsiRefreshDoneRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_CDMA_IMSI_READY:
                if (RILJ_LOGD) {
                    unsljLog(response);
                }
                if (mCdmaImsiReadyRegistrant != null) {
                    mCdmaImsiReadyRegistrant.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_EUSIM_READY:
                if (RILJ_LOGD) {
                    unsljLogRet(response, ret);
                }
                mIsEusimReady = true;
                if (mEusimReady != null) {
                    mEusimReady.notifyRegistrants(new AsyncResult(null, null, null));
                    if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                        if ((mInstanceId == 0) || (mInstanceId == 10)) {
                            SystemProperties.set(PROPERTY_RIL_CARD_TYPE_SET, "1");
                            riljLog("set gsm.ril.cardtypeset to 1");
                        } else if ((mInstanceId == 1) || (mInstanceId == 11)) {
                            SystemProperties.set(PROPERTY_RIL_CARD_TYPE_SET_2, "1");
                            riljLog("set gsm.ril.cardtypeset.2 to 1");
                        } else {
                            riljLog("not set cardtypeset mInstanceId=" + mInstanceId);
                        }
                    }
                }
                break;
            // M: Notify RILJ that call fade happened
            case RIL_UNSOL_CDMA_SIGNAL_FADE:
                if (RILJ_LOGD) {
                    unsljLogvRet(response, ret);
                }
                if (mCdmaSignalFadeRegistrant != null) {
                    mCdmaSignalFadeRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
                }
                break;
            // M: Notify RILJ that the AT+EFNM was received
            case RIL_UNSOL_CDMA_TONE_SIGNALS:
                if (RILJ_LOGD) {
                    unsljLogvRet(response, ret);
                }
                if (mCdmaToneSignalsRegistrant != null) {
                    mCdmaToneSignalsRegistrant.notifyRegistrant(
                        new AsyncResult(null, ret, null));
                }
                break;
            // xen0n: MTK TODO
            /// M: BIP {
            case RIL_UNSOL_STK_BIP_PROACTIVE_COMMAND:
                if (RILJ_LOGD) unsljLog(response);
                /*
                if ((CdmaFeatureOptionUtils.isCdmaLteDcSupport())
                        && (mUtkProCmdRegistrant != null)
                        && (mUtkProCmdRegistrant.getHandler() != null)
                        && (mStkSwitchMode == IUtkService.SVLTE_UTK_MODE)
                        && (mBipPsType != IUtkService.SVLTE_BIP_TYPE_ON_LTE)) {
                    riljLog("SVLTE UTK received BIP proactive command from MD1");
                    mUtkProCmdRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                } else {
                */
                    if (mBipProCmdRegistrant != null) {
                        mBipProCmdRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                    }
                // }
                break;
            /// M: BIP }
            //WorldMode
            case RIL_UNSOL_WORLD_MODE_CHANGED:
                int[] state = null;
                if (RILJ_LOGD) {
                    unsljLog(response);
                }
                if (ret != null) {
                    state = (int[]) ret;

                    //update switching state
                    if (state[0] == 0){
                        WorldMode.updateSwitchingState(true);
                    }else{
                        WorldMode.updateSwitchingState(false);
                    }

                    //sendBroadcast with state
                    Intent intent = new Intent(
                            TelephonyIntents.ACTION_WORLD_MODE_CHANGED);
                    intent.putExtra(TelephonyIntents.EXTRA_WORLD_MODE_CHANGE_STATE,
                            (Integer)state[0]);
                    mContext.sendBroadcast(intent);
                    if (RILJ_LOGD) {
                        riljLog("Broadcast for WorldModeChanged: state=" + state[0]);
                    }
                }
                break;
            /// M: For 3G VT only @{
            case RIL_UNSOL_VT_STATUS_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mVtStatusInfoRegistrants != null) {
                    mVtStatusInfoRegistrants.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_VT_RING_INFO:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mVtRingRegistrants != null) {
                    mVtRingRegistrants.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            /// @}

            // M: [LTE][Low Power][UL traffic shaping] Start
            case RIL_UNSOL_LTE_ACCESS_STRATUM_STATE_CHANGE:
                if (RILJ_LOGD) unsljLogRet(response, ret);
                if (mLteAccessStratumStateRegistrants != null) {
                    mLteAccessStratumStateRegistrants.notifyRegistrants(
                            new AsyncResult(null, ret, null));
                }
                break;
            // M: [LTE][Low Power][UL traffic shaping] End
            default:
                Rlog.i(RILJ_LOG_TAG, "Unprocessed unsolicited known MTK response: " + response);
        }

        if (rewindAndReplace) {
            Rlog.w(RILJ_LOG_TAG, "Rewriting MTK unsolicited response " + response + " to " + newResponseCode);

            // Rewrite
            p.setDataPosition(dataPosition);
            p.writeInt(newResponseCode);

            // And rewind again in front
            p.setDataPosition(dataPosition);

            super.processUnsolicited(p);
        }
    }

    /*
     * to protect modem status we need to avoid two case :
     * 1. DTMF start -> CHLD request -> DTMF stop
     * 2. CHLD request -> DTMF request
     */
    private void handleChldRelatedRequest(RILRequest rr) {
        synchronized (mDtmfReqQueue) {
            int queueSize = mDtmfReqQueue.size();
            int i, j;
            if (queueSize > 0) {
                RILRequest rr2 = mDtmfReqQueue.get();
                if (rr2.mRequest == RIL_REQUEST_DTMF_START) {
                    // need to send the STOP command
                    if (RILJ_LOGD) riljLog("DTMF queue isn't 0, first request is START, send stop dtmf and pending switch");
                    if (queueSize > 1) {
                        j = 2;
                    } else {
                        // need to create a new STOP command
                        j = 1;
                    }
                    if (RILJ_LOGD) riljLog("queue size  " + mDtmfReqQueue.size());

                    for (i = queueSize - 1; i >= j; i--) {
                        mDtmfReqQueue.remove(i);
                    }
                    if (RILJ_LOGD) riljLog("queue size  after " + mDtmfReqQueue.size());
                    if (mDtmfReqQueue.size() == 1) { // only start command, we need to add stop command
                        RILRequest rr3 = RILRequest.obtain(RIL_REQUEST_DTMF_STOP, null);
                        if (RILJ_LOGD) riljLog("add dummy stop dtmf request");
                        mDtmfReqQueue.stop();
                        mDtmfReqQueue.add(rr3);
                    }
                }
                else {
                    // first request is STOP, just remove it and send switch
                    if (RILJ_LOGD) riljLog("DTMF queue isn't 0, first request is STOP, penging switch");
                    j = 1;
                    for (i = queueSize - 1; i >= j; i--) {
                        mDtmfReqQueue.remove(i);
                    }
                }
                mDtmfReqQueue.setPendingRequest(rr);
            } else {
                if (RILJ_LOGD) riljLog("DTMF queue is 0, send switch Immediately");
                mDtmfReqQueue.setSendChldRequest();
                send(rr);
            }
        }
    }

    private static int readRilMessage(InputStream is, byte[] buffer)
            throws IOException {
        int countRead;
        int offset;
        int remaining;
        int messageLength;

        // First, read in the length of the message
        offset = 0;
        remaining = 4;
        do {
            countRead = is.read(buffer, offset, remaining);

            if (countRead < 0 ) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message length");
                return -1;
            }

            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);

        messageLength = ((buffer[0] & 0xff) << 24)
                | ((buffer[1] & 0xff) << 16)
                | ((buffer[2] & 0xff) << 8)
                | (buffer[3] & 0xff);

        // Then, re-use the buffer and read in the message itself
        offset = 0;
        remaining = messageLength;
        do {
            countRead = is.read(buffer, offset, remaining);

            if (countRead < 0 ) {
                Rlog.e(RILJ_LOG_TAG, "Hit EOS reading message.  messageLength=" + messageLength
                        + " remaining=" + remaining);
                return -1;
            }

            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);

        return messageLength;
    }

        protected RILReceiver createRILReceiver() {
        return new MTKRILReceiver();
    }

        protected class MTKRILReceiver extends RILReceiver {
        byte[] buffer;

        protected MTKRILReceiver() {
            buffer = new byte[RIL_MAX_COMMAND_BYTES];
        }

        /// M: For SVLTE to disconnect socket in C2K only mode.
        boolean mStoped = false;

        @Override
        public void
        run() {
            int retryCount = 0;
            String rilSocket = "rild";

            try {for (;;) {
                /// M: For SVLTE to disconnect socket in C2K only mode.
                if (mStoped) {
                    riljLog("[RIL SWITCH] stoped now!");
                    return;
                }

                LocalSocket s = null;
                LocalSocketAddress l;

                /// M: If SVLTE support, LTE RIL ID is a special value, force connect to rild socket
                if (mInstanceId == null || SvlteUtils.isValidPhoneId(mInstanceId)) {
                    rilSocket = SOCKET_NAME_RIL[SvlteUtils.getSlotId(mInstanceId)];
                } else {
                    if (SystemProperties.getInt("ro.mtk_dt_support", 0) != 1) {
                        // dsds
                        rilSocket = SOCKET_NAME_RIL[mInstanceId];
                    } else {
                        // dsda
                        if (SystemProperties.getInt("ro.evdo_dt_support", 0) == 1) {
                            // c2k dsda
                            rilSocket = SOCKET_NAME_RIL[mInstanceId];
                        } else if (SystemProperties.getInt("ro.telephony.cl.config", 0) == 1) {
                            // for C+L
                            rilSocket = SOCKET_NAME_RIL[mInstanceId];
                        } else {
                            // gsm dsda
                            rilSocket = "rild-md2";
                        }
                    }
                }

                /* M: C2K start */
                int phoneType = TelephonyManager.getPhoneType(mPreferredNetworkType);
                if (phoneType == PhoneConstants.PHONE_TYPE_CDMA) {
                    rilSocket = C2K_SOCKET_NAME_RIL;
                }
                /* M: C2K end */

                riljLog("rilSocket[" + mInstanceId + "] = " + rilSocket);

                try {
                    s = new LocalSocket();
                    l = new LocalSocketAddress(rilSocket,
                            LocalSocketAddress.Namespace.RESERVED);
                    s.connect(l);
                } catch (IOException ex){
                    try {
                        if (s != null) {
                            s.close();
                        }
                    } catch (IOException ex2) {
                        //ignore failure to close after failure to connect
                    }

                    // don't print an error message after the the first time
                    // or after the 8th time

                    if (retryCount == 8) {
                        Rlog.e (RILJ_LOG_TAG,
                            "Couldn't find '" + rilSocket
                            + "' socket after " + retryCount
                            + " times, continuing to retry silently");
                    } else if (retryCount >= 0 && retryCount < 8) {
                        Rlog.i (RILJ_LOG_TAG,
                            "Couldn't find '" + rilSocket
                            + "' socket; retrying after timeout");
                    }

                    try {
                        Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                    } catch (InterruptedException er) {
                    }

                    retryCount++;
                    continue;
                }

                retryCount = 0;

                mSocket = s;
                Rlog.i(RILJ_LOG_TAG, "(" + mInstanceId + ") Connected to '"
                        + rilSocket + "' socket");

                int length = 0;
                try {
                    InputStream is = mSocket.getInputStream();
                    for (;;) {
                        Parcel p;
                        length = readRilMessage(is, buffer);
                        if (length < 0) {
                            // End-of-stream reached
                            break;
                        }
                        p = Parcel.obtain();
                        p.unmarshall(buffer, 0, length);
                        p.setDataPosition(0);

                        //Rlog.v(RILJ_LOG_TAG, "Read packet: " + length + " bytes");

                        processResponse(p);
                        p.recycle();
                    }
                } catch (java.io.IOException ex) {
                    Rlog.i(RILJ_LOG_TAG, "'" + rilSocket + "' socket closed",
                          ex);
                } catch (Throwable tr) {
                    Rlog.e(RILJ_LOG_TAG, "Uncaught exception read length=" + length +
                        "Exception:" + tr.toString());
                }

                Rlog.i(RILJ_LOG_TAG, "(" + mInstanceId + ") Disconnected from '" + rilSocket
                      + "' socket");

                setRadioState (RadioState.RADIO_UNAVAILABLE);

                try {
                    mSocket.close();
                } catch (IOException ex) {
                }

                mSocket = null;
                RILRequest.resetSerial();

                // Clear request list on close
                clearRequestList(RADIO_NOT_AVAILABLE, false);
            }} catch (Throwable tr) {
                Rlog.e(RILJ_LOG_TAG,"Uncaught exception", tr);
            }

            /* We're disconnected so we don't know the ril version */
            notifyRegistrantsRilConnectionChanged(-1);
        }
    }

    /* broken by new version of MTK RIL, disable for now */
    /*
    public void handle3GSwitch() {
        int simId = mInstanceId == null ? 0 : mInstanceId;
        int newsim = SystemProperties.getInt("gsm.3gswitch", 0);
        newsim = newsim - 1;
        if(!(simId==newsim)) {
            int prop = SystemProperties.getInt("gsm.3gswitch", 0);
            if (RILJ_LOGD) riljLog("Setting data subscription on SIM" + (simId + 1) + " mInstanceid=" + mInstanceId + " gsm.3gswitch=" + prop);
            RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_3G_CAPABILITY, null);
            rr.mParcel.writeInt(1);
            int realsim = simId + 1;
            rr.mParcel.writeInt(realsim);
            if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
            send(rr);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException er) {
            }
            resetRadio(null);
            try {
                Thread.sleep(4*1000);
            } catch (InterruptedException er) {
            }
        }
        else {
            if (RILJ_LOGD) riljLog("Not setting data subscription on same SIM");
        }
    }

    public void setDataAllowed(boolean allowed, Message result) {
        handle3GSwitch();

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ALLOW_DATA, result);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + allowed);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(allowed ? 1 : 0);
        send(rr);
    }
    */

    @Override
    public void connectRilSocket() {
        if (RILJ_LOGD) {
            riljLog("[RIL SWITCH]reconnectRilSocket()");
        }
        if (mReceiverThread == null && mReceiver == null) {
            connectRild();
        } else {
            if (RILJ_LOGD) {
                riljLog("[RIL SWITCH] Already connected, abort connect request.");
            }
        }
    }

    @Override
    public void disconnectRilSocket() {
        if (RILJ_LOGD) {
            riljLog("[RIL SWITCH]disconnectRilSocket()");
        }
        if (mSenderThread != null) {
            mSenderThread.getLooper().quit();
            mSenderThread = null;
        }
        if (mReceiver != null) {
            if (mReceiver instanceof MTKRILReceiver) {
                ((MTKRILReceiver) mReceiver).mStoped = true;
            }
        }

        try {
            if (mSocket != null) {
                mSocket.shutdownInput();
            }
            if (mReceiverThread != null) {
                while (mReceiverThread.isAlive()) {
                    riljLog("[RIL SWITCH]mReceiverThread.isAlive() = true;");
                    Thread.sleep(500);
                }
            }
            mReceiverThread = null;
            mReceiver = null;
            // Set mRilVersion to -1, it will not notifyRegistrant in registerForRilConnected.
            mRilVersion = -1;
        } catch (IOException ex) {
            if (RILJ_LOGD) {
                riljLog("[RIL SWITCH]IOException ex = " + ex);
            }
        } catch (InterruptedException er) {
            if (RILJ_LOGD) {
                riljLog("[RIL SWITCH]InterruptedException er = " + er);
            }
        }
    }

    // ported from sprout RIL
    protected Object
    responseFailCause(Parcel p) {
        int numInts;
        int response[];

        numInts = p.readInt();
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
        }
        LastCallFailCause failCause = new LastCallFailCause();
        failCause.causeCode = response[0];
        if (p.dataAvail() > 0) {
          failCause.vendorCause = p.readString();
        }
        return failCause;
    }

    // CommandsInterface impl

    public void setUiccSubscription(int slotId, int appIndex, int subId,
            int subStatus, Message result) {
        //Note: This RIL request is also valid for SIM and RUIM (ICC card)
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_UICC_SUBSCRIPTION, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " slot: " + slotId + " appIndex: " + appIndex
                + " subId: " + subId + " subStatus: " + subStatus);

        rr.mParcel.writeInt(slotId);
        rr.mParcel.writeInt(appIndex);
        rr.mParcel.writeInt(subId);
        rr.mParcel.writeInt(subStatus);

        send(rr);
    }

    @Override
    public void
    supplyNetworkDepersonalization(String netpin, String type, Message result) {
        riljLog("supplyNetworkDepersonalization: type is ignored on MTK!");
        supplyNetworkDepersonalization(netpin, result);
    }

    @Override
    public void
    supplyNetworkDepersonalization(String netpin, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mParcel.writeInt(1);
        rr.mParcel.writeString(netpin);

        send(rr);
    }

    // xen0n refactored
    @Override
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        if (PhoneNumberUtils.isUriNumber(address)) {
           RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL_WITH_SIP_URI, result);

           rr.mParcel.writeString(address);
           if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
           send(rr);
           return;
        }

        super.dial(address, clirMode, uusInfo, result);
    }

    @Override
    public void
    switchWaitingOrHoldingAndActive (Message result) {
        RILRequest rr
                = RILRequest.obtain(
                        RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE,
                                        result);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        /// M: CC012: DTMF request special handling @{
        handleChldRelatedRequest(rr);
        /// @}
    }

    @Override
    public void
    conference (Message result) {

        /// M: CC053: MoMS [Mobile Managerment] @{
        // 3. Permission Control for Conference call
        /*
        if (MobileManagerUtils.isSupported()) {
            if (!checkMoMSSubPermission(SubPermissions.MAKE_CONFERENCE_CALL)) {
                return;
            }
        }
        */
        /// @}

        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_CONFERENCE, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        /// M: CC012: DTMF request special handling @{
        handleChldRelatedRequest(rr);
        ///@}
    }

    @Override
    public void
    separateConnection (int gsmIndex, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SEPARATE_CONNECTION, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                            + " " + gsmIndex);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(gsmIndex);

        /// M: CC012: DTMF request special handling @{
        handleChldRelatedRequest(rr);
        /// @}
    }

    @Override
    public void
    explicitCallTransfer (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_EXPLICIT_CALL_TRANSFER, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        /// M: CC012: DTMF request special handling @{
        handleChldRelatedRequest(rr);
        /// @}
    }

    @Override
    public void
    startDtmf(char c, Message result) {
        /// M: CC012: DTMF request special handling @{
        /* DTMF request will be ignored when the count of requests reaches 32 */
        synchronized (mDtmfReqQueue) {
            if (!mDtmfReqQueue.hasSendChldRequest() && mDtmfReqQueue.size() < mDtmfReqQueue.MAXIMUM_DTMF_REQUEST) {
                if (!mDtmfReqQueue.isStart()) {
                    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DTMF_START, result);

                    rr.mParcel.writeString(Character.toString(c));
                    mDtmfReqQueue.start();
                    mDtmfReqQueue.add(rr);
                    if (mDtmfReqQueue.size() == 1) {
                        riljLog("send start dtmf");
                        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
                        send(rr);
                    }
                } else {
                    riljLog("DTMF status conflict, want to start DTMF when status is " + mDtmfReqQueue.isStart());
                }
            }
        }
        /// @}
    }

    @Override
    public void
    stopDtmf(Message result) {
        /// M: CC012: DTMF request special handling @{
        /* DTMF request will be ignored when the count of requests reaches 32 */
        synchronized (mDtmfReqQueue) {
            if (!mDtmfReqQueue.hasSendChldRequest() && mDtmfReqQueue.size() < mDtmfReqQueue.MAXIMUM_DTMF_REQUEST) {
                if (mDtmfReqQueue.isStart()) {
                    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DTMF_STOP, result);

                    mDtmfReqQueue.stop();
                    mDtmfReqQueue.add(rr);
                    if (mDtmfReqQueue.size() == 1) {
                        riljLog("send stop dtmf");
                        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
                        send(rr);
                    }
                } else {
                    riljLog("DTMF status conflict, want to start DTMF when status is " + mDtmfReqQueue.isStart());
                }
            }
        }
        /// @}
    }

    @Override
    public void
    setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, String authType, String protocol,
            Message result) {
        /* [Note by mtk01411] In original Android2.1 release: MAX PDP Connection is 1
        * request_cid is only allowed to set as "1" manually
        */
        setupDataCall(radioTechnology, profile, apn, user, password, authType, protocol, "1", result);
    }

    @Override
    public void
    setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, String authType, String protocol,
            String interfaceId, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SETUP_DATA_CALL, result);

        rr.mParcel.writeInt(8); //the number should be changed according to number of parameters

        rr.mParcel.writeString(radioTechnology);
        rr.mParcel.writeString(profile);
        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(user);
        rr.mParcel.writeString(password);
        rr.mParcel.writeString(authType);
        rr.mParcel.writeString(protocol);

        /** M: specify interface Id */
        rr.mParcel.writeString(interfaceId);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                + requestToString(rr.mRequest) + " " + radioTechnology + " "
                + profile + " " + apn + " " + user + " "
                + password + " " + authType + " " + protocol + " " + interfaceId);

        send(rr);
    }

    @Override
    public void setModemPower(boolean power, Message result) {

        if (RILJ_LOGD) riljLog("Set Modem power as: " + power);
        RILRequest rr;

        if (power) {
            rr = RILRequest.obtain(RIL_REQUEST_MODEM_POWERON, result);
        }
        else {
            rr = RILRequest.obtain(RIL_REQUEST_MODEM_POWEROFF, result);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
            + requestToString(rr.mRequest));

        send(rr);
    }

    /* M: SS part */
    // mtk00732 add for getCOLP
    public void
    getCOLP(Message result) {
        RILRequest rr
                //= RILRequest.obtain(RIL_REQUEST_GET_COLP, result, mySimId);
                = RILRequest.obtain(RIL_REQUEST_GET_COLP, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    // mtk00732 add for setCOLP
    public void
    setCOLP(boolean enable, Message result) {
        RILRequest rr
                //= RILRequest.obtain(RIL_REQUEST_SET_COLP, result, mySimId);
                = RILRequest.obtain(RIL_REQUEST_SET_COLP, result);

        // count ints
        rr.mParcel.writeInt(1);

        if (enable) {
            rr.mParcel.writeInt(1);
        } else {
            rr.mParcel.writeInt(0);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + enable);

        send(rr);
    }

    // mtk00732 add for getCOLR
    public void
    getCOLR(Message result) {
        RILRequest rr
                //= RILRequest.obtain(RIL_REQUEST_GET_COLR, result, mySimId);
                = RILRequest.obtain(RIL_REQUEST_GET_COLR, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }
    /* M: SS part end */

    @Override
    public void
    getAvailableNetworks(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    cancelAvailableNetworks(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_ABORT_QUERY_AVAILABLE_NETWORKS,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    /* M: SS part */
    ///M: For query CNAP
    public void sendCNAPSS(String cnapssString, Message response) {
        RILRequest rr
                //= RILRequest.obtain(RIL_REQUEST_SEND_CNAP, response, mySimId);
                = RILRequest.obtain(RIL_REQUEST_SEND_CNAP, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + cnapssString);

        rr.mParcel.writeString(cnapssString);

        send(rr);
    }
    /* M: SS part end */

    public void setBandMode(int[] bandMode, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_BAND_MODE, response);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeInt(bandMode[0]);
        rr.mParcel.writeInt(bandMode[1]);
        rr.mParcel.writeInt(bandMode[2]);

        Rlog.d(RILJ_LOG_TAG, "Set band modes: " + bandMode[1] + ", " + bandMode[2]);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                 + " " + bandMode);

        send(rr);
     }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCallSetupRequestFromSim(
            boolean accept, int resCode, Message response) {

        RILRequest rr = RILRequest.obtain(
            RILConstants.RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM,
            response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        int[] param = new int[1];
        if (resCode == 0x21 || resCode == 0x20) {
            param[0] = resCode;
        } else {
            param[0] = accept ? 1 : 0;
        }
        rr.mParcel.writeIntArray(param);
        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void queryUtkSetupMenuFromMD(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_QUERY_UTK_MENU_FROM_MD, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void queryStkSetUpMenuFromMD(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_QUERY_STK_MENU_FROM_MD, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPreferredNetworkType(int networkType , Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(networkType);

        mPreviousPreferredType = mPreferredNetworkType; //ALPS00799783
        mPreferredNetworkType = networkType;

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + networkType);
        }

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocationUpdates(boolean enable, Message response) {
        //MTK-START [ALPS00093395]Consider screen on/off state
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if ((pm.isScreenOn()) && (false == enable)) return;
        //MTK-END [ALPS00093395]Consider screen on/off state

        super.setLocationUpdates(enable, response);
    }

    @Override
    protected Object
    responseInts(Parcel p) {
        int numInts;
        int response[];

        numInts = p.readInt();

        response = new int[numInts];
        // MTK
        riljLog("responseInts numInts=" + numInts);

        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
            // MTK
            riljLog("responseInts response[" + i + "]=" + response[i]);
        }

        return response;
    }

    protected Object
    responseStringEncodeBase64(Parcel p) {
        String response;

        response = p.readString();

        if (RILJ_LOGD) {
            riljLog("responseStringEncodeBase64 - Response = " + response);
        }

        byte[] auth_output = new byte[response.length() / 2];
        for (int i = 0; i < auth_output.length; i++) {
            auth_output[i] |= Character.digit(response.charAt(i * 2), 16) * 16;
            auth_output[i] |= Character.digit(response.charAt(i * 2 + 1), 16);
        }
        response = android.util.Base64.encodeToString(auth_output, android.util.Base64.NO_WRAP);

        if (RILJ_LOGD) {
            riljLog("responseStringEncodeBase64 - Encoded Response = " + response);
        }

        return response;
    }

    @Override
    protected Object
    responseSimRefresh(Parcel p) {
        IccRefreshResponse response = new IccRefreshResponse();

        int i = 0;
        int files_num = 0;
        String efId_String;

        if (SystemProperties.get("ro.mtk_wifi_calling_ril_support").equals("1")) {
            response.sessionId = p.readInt();
            //files_num = numInts - 2; //sessionid + refresh type
        } else {
            //files_num = numInts - 1; //refresh type
        }
        //refresh type
        response.refreshResult = p.readInt();
        //efIds hex string
        efId_String = p.readString();
        if (null != efId_String && 4 <= efId_String.length()) {
            files_num = efId_String.length() / 4;
        }
        response.efId = new int[files_num];
        riljLog("efId_String: " + efId_String + ", files_num: " + files_num);

        int startIdx = 0;
        int endIdx = 0;
        if (null != efId_String && 4 <= efId_String.length()) {
            for (i = 0; i < files_num; i++) {
                String efidStr = efId_String.substring(startIdx, startIdx + 4);
                response.efId[i] = (Integer.valueOf(efidStr, 16)).intValue();
                startIdx += 4;
                riljLog("EFId " + i + ":" + response.efId[i]);
            }
        }
        /*
        for (i = 0; i < files_num; i++) {
            response.efId[i] = p.readInt();
            riljLog("EFId " + i + ":" + response.efId[i]);
        }
        */
        response.aid = p.readString();

        if (SystemProperties.get("ro.mtk_wifi_calling_ril_support").equals("1")) {
            riljLog("responseSimRefresh, sessionId=" + response.sessionId + ", result=" + response.refreshResult
                + ", efId=" + response.efId + ", aid=" + response.aid);
        }

        return response;
    }

    @Override
    protected Object
    responseCallList(Parcel p) {
        int num;
        int voiceSettings;
        ArrayList<DriverCall> response;
        DriverCall dc;

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        if (RILJ_LOGV) {
            riljLog("responseCallList: num=" + num +
                    " mEmergencyCallbackModeRegistrant=" + mEmergencyCallbackModeRegistrant +
                    " mTestingEmergencyCall=" + mTestingEmergencyCall.get());
        }
        for (int i = 0 ; i < num ; i++) {
            dc = new DriverCall();

            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            dc.isMpty = (0 != p.readInt());
            dc.isMT = (0 != p.readInt());
            dc.als = p.readInt();
            voiceSettings = p.readInt();
            dc.isVoice = (0 == voiceSettings) ? false : true;
            /// M: For 3G VT only @{
            // Assume that call can be either Voice or Video (no Fax, data type is supported)
            dc.isVideo = !(dc.isVoice);
            riljLog("isVoice = " + dc.isVoice + ", isVideo = " + dc.isVideo);
            /// @}
            dc.isVoicePrivacy = (0 != p.readInt());
            dc.number = p.readString();
            int np = p.readInt();
            dc.numberPresentation = DriverCall.presentationFromCLIP(np);
            dc.name = p.readString();
            // according to ril.h, namePresentation should be handled as numberPresentation;
            dc.namePresentation = DriverCall.presentationFromCLIP(p.readInt());
            int uusInfoPresent = p.readInt();
            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d",
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
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

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

        if ((num == 0) && mTestingEmergencyCall.getAndSet(false)) {
            if (mEmergencyCallbackModeRegistrant != null) {
                riljLog("responseCallList: call ended, testing emergency call," +
                            " notify ECM Registrants");
                mEmergencyCallbackModeRegistrant.notifyRegistrant();
            }
        }

        return response;
    }

    // slightly modified from the original MediaTekRIL
    private Object
    responseOperator(Parcel p) {
        int num;
        String response[] = null;

        response = p.readStringArray();

        for (int i = 0; i < response.length; i++) {
            if((response[i] != null) && (response[i].startsWith("uCs2") == true))
            {
                riljLog("responseOperator handling UCS2 format name: response[" + i + "]");
                try{
                    response[i] = new String(IccUtils.hexStringToBytes(response[i].substring(4)),"UTF-16");
                }catch(UnsupportedEncodingException ex){
                    riljLog("responseOperatorInfos UnsupportedEncodingException");
                }
            }
        }

        // NOTE: the original code seemingly has some nontrivial SpnOverride
        // modifications, so I'm not going to port that.
        if (response.length > 2 && response[2] != null) {
            if (response[0] != null && (response[0].equals("") || response[0].equals(response[2]))) {
                // xen0n: seems the Operators class is now gone
                // Operators init = new Operators ();
                // String temp = init.unOptimizedOperatorReplace(response[2]);

                // NOTE: using MTK methods here! (all the used methods are not visible from this class)
                SpnOverride spnOverride = SpnOverride.getInstance();
                final String mccmnc = response[2];
                final String temp = spnOverride.containsCarrierEx(mccmnc) ? spnOverride.getSpnEx(mccmnc) : mccmnc;
                riljLog("lookup RIL responseOperator() " + response[2] + " gave " + temp + " was " + response[0] + "/" + response[1] + " before.");
                response[0] = temp;
                response[1] = temp;
            }
        }

        return response;
    }

    // MTK TODO
    /*
    @Override
    protected Object
    responseOperatorInfos(Parcel p) {
        String strings[] = (String [])responseStrings(p);
        ArrayList<OperatorInfo> ret;

        SpnOverride spnOverride = SpnOverride.getInstance();

        if (strings.length % 4 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multible of 4");
        }

        ret = new ArrayList<OperatorInfo>(strings.length / 4);

        for (int i = 0 ; i < strings.length ; i += 4) {
            String strOperatorLong = null;
            if (spnOverride.containsCarrierEx(strings[i + 2])) {
                strOperatorLong = spnOverride.getSpnEx(strings[i + 2]);
            } else {
                strOperatorLong = strings[i + 0]; // use operator name from RIL
            }
            ret.add (
                new OperatorInfo(
                    strOperatorLong,
                    strings[i+1],
                    strings[i+2],
                    strings[i+3]));
        }

        return ret;
    }
    */

    protected Object
    responseOperatorInfosWithAct(Parcel p) {
        String strings[] = (String []) responseStrings(p);
        ArrayList<OperatorInfo> ret;

        if (strings.length % 5 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT: invalid response. Got "
                + strings.length + " strings, expected multible of 5");
        }

        String lacStr = SystemProperties.get("gsm.cops.lac");
        boolean lacValid = false;
        int lacIndex = 0;

        Rlog.d(RILJ_LOG_TAG, "lacStr = " + lacStr + " lacStr.length=" + lacStr.length() + " strings.length=" + strings.length);
        if ((lacStr.length() > 0) && (lacStr.length() % 4 == 0) && ((lacStr.length() / 4) == (strings.length / 5))) {
            Rlog.d(RILJ_LOG_TAG, "lacValid set to true");
            lacValid = true;
        }

        SystemProperties.set("gsm.cops.lac", ""); //reset property

        ret = new ArrayList<OperatorInfo>(strings.length / 5);

        for (int i = 0 ; i < strings.length ; i += 5) {
            /* Default display manufacturer maintained operator name table */
            if (strings[i + 2] != null) {
                strings[i + 0] = SpnOverride.getInstance().lookupOperatorName(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 2], true, mContext);
                strings[i + 1] = SpnOverride.getInstance().lookupOperatorName(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 2], false, mContext);
                riljLog("lookup RIL responseOperator(), longAlpha= " + strings[i + 0] + ",shortAlpha= " + strings[i + 1] + ",numeric=" + strings[i + 2]);
            }

            String longName = null;
            String shortName = null;
            /* Operator name from network MM information has higher priority to display */
            longName = lookupOperatorNameFromNetwork(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 2], true);
            shortName = lookupOperatorNameFromNetwork(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 2], false);
            if (longName != null) {
                strings[i + 0] = longName;
            }
            if (shortName != null) {
                strings[i + 1] = shortName;
            }
            riljLog("lookupOperatorNameFromNetwork in responseOperatorInfosWithAct(),updated longAlpha= " + strings[i + 0] + ",shortAlpha= " + strings[i + 1] + ",numeric=" + strings[i + 2]);

            // Not to show MVNO name for registered operator name display for certain SIM @{
            // MTK TODO
            /*
            if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                int phoneNum = TelephonyManager.getDefault().getPhoneCount();
                int[] cardType = new int[phoneNum];
                int targetCardType;
                String strOperatorOverride = "";
                boolean isCdma3GDualModeOr4GSim = false;
                SpnOverride spnOverride = SpnOverride.getInstance();

                if ((strings[i + 2].equals("45403")) || (strings[i + 2].equals("45404"))) {
                    cardType = UiccController.getInstance().getC2KWPCardType();
                    //FIX ME in svlte solution 2
                    if (mInstanceId == PhoneConstants.SIM_ID_1) {
                        targetCardType = cardType[PhoneConstants.SIM_ID_1];

                        if (((targetCardType & UiccController.CARD_TYPE_RUIM) > 0 || (targetCardType & UiccController.CARD_TYPE_CSIM) > 0)
                            && ((targetCardType & UiccController.CARD_TYPE_USIM) > 0)
                            || SvlteUiccUtils.getInstance().isCt3gDualMode(
                                    PhoneConstants.SIM_ID_1)) {
                                isCdma3GDualModeOr4GSim = true;
                        }

                        if ((spnOverride != null) && (spnOverride.containsCarrierEx(strings[i + 2]))) {
                            strOperatorOverride = spnOverride.getSpnEx(strings[i + 2]);
                        }

                        riljLog("targetCardType= " + targetCardType + " strOperatorOverride= " + strOperatorOverride
                                + " isCdma3GDualModeOr4GSim=" + isCdma3GDualModeOr4GSim
                                + " opNumeric= " + strings[i + 2]);

                        if (isCdma3GDualModeOr4GSim == true) {
                            riljLog("longAlpha: " + strings[i + 0] + " is overwritten to " + strOperatorOverride);
                            strings[i + 0] = strOperatorOverride;
                        }
                    }
                }
            }
            */
            ///Not to show MVNO name for registered operator name display for certain SIM.@}


            /* Operator name from SIM (EONS/CPHS) has highest priority to display. This will be handled in GsmSST updateSpnDisplay() */
            /* ALPS00353868: To get operator name from OPL/PNN/CPHS, which need lac info */
            // MTK TODO
            /*
            if ((lacValid == true) && (strings[i + 0] != null)) {
                int phoneId = mInstanceId;
                if (CdmaFeatureOptionUtils.isCdmaLteDcSupport()) {
                       phoneId = SvlteUtils.getSlotId(phoneId);
                }
                UiccController uiccController = UiccController.getInstance();
                SIMRecords simRecord = (SIMRecords) uiccController.getIccRecords(mInstanceId, UiccController.APP_FAM_3GPP);
                int lacValue = -1;
                String sEons = null;
                String lac = lacStr.substring(lacIndex, lacIndex + 4);
                Rlog.d(RILJ_LOG_TAG, "lacIndex=" + lacIndex + " lacValue=" + lacValue + " lac=" + lac + " plmn numeric=" + strings[i + 2] + " plmn name" + strings[i + 0]);

                if (lac != "") {
                    lacValue = Integer.parseInt(lac, 16);
                    lacIndex += 4;
                    if (lacValue != 0xfffe) {
                        sEons = simRecord.getEonsIfExist(strings[i + 2], lacValue, true);
                        if (sEons != null) {
                            strings[i + 0] = sEons;
                            Rlog.d(RILJ_LOG_TAG, "plmn name update to Eons: " + strings[i + 0]);
                        } else {
                            //[ALPS01858353]-Start: The CPHS operator name shall only be used for HPLMN name dispaly
                            String mSimOperatorNumeric = simRecord.getOperatorNumeric();
                            if ((mSimOperatorNumeric != null) &&
                                    (mSimOperatorNumeric.equals(strings[i + 2]))) {
                                String sCphsOns = null;
                                sCphsOns = simRecord.getSIMCPHSOns();
                                if (sCphsOns != null) {
                                    strings[i + 0] = sCphsOns;
                                    Rlog.d(RILJ_LOG_TAG, "plmn name update to CPHS Ons: "
                                            + strings[i + 0]);
                                }
                            }
                            //[ALPS01858353]-End
                        }
                    } else {
                        Rlog.d(RILJ_LOG_TAG, "invalid lac ignored");
                    }
                }
            }
            */
            // ALPS00353868 END

            /* ALPS01597054 Always show Act info(ex: "2G","3G","4G") for PLMN list result */
            strings[i + 0] = strings[i + 0].concat(" " + strings[i + 4]);
            strings[i + 1] = strings[i + 1].concat(" " + strings[i + 4]);

            ret.add(
                new OperatorInfo(
                    strings[i + 0],
                    strings[i + 1],
                    strings[i + 2],
                    strings[i + 3]));
        }
        return ret;
    }

    @Override
    protected Object
    responseCellList(Parcel p) {
       int num, rssi;
       String location;
       ArrayList<NeighboringCellInfo> response;
       NeighboringCellInfo cell;

       num = p.readInt();
       response = new ArrayList<NeighboringCellInfo>();

       // ALPS00269882 START
       // Get the radio access type
       /*
       int[] subId = SubscriptionManager.getSubId(mInstanceId);
       int radioType =
               ((TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)).
               getDataNetworkType(subId[0]);
       */
       int radioType = SystemProperties.getInt("gsm.enbr.rat", NETWORK_TYPE_GPRS);
       riljLog("gsm.enbr.rat=" + radioType);
       // ALPS00269882 END

       // Interpret the location based on radio access type
       if (radioType != NETWORK_TYPE_UNKNOWN) {
           for (int i = 0 ; i < num ; i++) {
               rssi = p.readInt();
               location = p.readString();
               cell = new NeighboringCellInfo(rssi, location, radioType);
               response.add(cell);
           }
       }
       return response;
    }

    protected Object responseSetPreferredNetworkType(Parcel p) {
        int count = getRequestCount(RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE);
        if (count == 0) {
            Intent intent = new Intent(
                    TelephonyIntents.ACTION_RAT_CHANGED);
            intent.putExtra(PhoneConstants.PHONE_KEY, mInstanceId);
            intent.putExtra(TelephonyIntents.EXTRA_RAT, mPreferredNetworkType);
            mContext.sendBroadcast(intent);
        }
        riljLog("SetRatRequestCount: " + count);

        return null;
    }

    private int getRequestCount(int reuestId) {
        int count = 0;
        synchronized (mRequestList) {
            for (int i = 0, s = mRequestList.size() ; i < s ; i++) {
                RILRequest rr = mRequestList.valueAt(i);
                if (rr != null && rr.mRequest == reuestId) {
                    count++;
                }
            }
        }
        return count;
    }

    //MTK-START Femtocell (CSG)
    protected Object
    responseFemtoCellInfos(Parcel p) {
        String strings[] = (String []) responseStrings(p);
        ArrayList<FemtoCellInfo> ret;

        if (strings.length % 6 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_GET_FEMTOCELL_LIST: invalid response. Got "
                + strings.length + " strings, expected multible of 6");
        }

        ret = new ArrayList<FemtoCellInfo>(strings.length / 6);

        /* <plmn numeric>,<act>,<plmn long alpha name>,<csgId>,,csgIconType>,<hnbName> */
        for (int i = 0 ; i < strings.length ; i += 6) {
            String actStr;
            String hnbName;
            int rat;

            /* ALPS00273663 handle UCS2 format name : prefix + hex string ex: "uCs2806F767C79D1" */
            if ((strings[i + 1] != null) && (strings[i + 1].startsWith("uCs2") == true))
            {
                Rlog.d(RILJ_LOG_TAG, "responseOperatorInfos handling UCS2 format name");

                try {
                    strings[i + 0] = new String(IccUtils.hexStringToBytes(strings[i + 1].substring(4)), "UTF-16");
                } catch (UnsupportedEncodingException ex) {
                    Rlog.d(RILJ_LOG_TAG, "responseOperatorInfos UnsupportedEncodingException");
                }
            }

            if (strings[i + 1] != null && (strings[i + 1].equals("") || strings[i + 1].equals(strings[i + 0]))) {
                Rlog.d(RILJ_LOG_TAG, "lookup RIL responseFemtoCellInfos() for plmn id= " + strings[i + 0]);
                strings[i + 1] = SpnOverride.getInstance().lookupOperatorName(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 0], true, mContext);
            }

            if (strings[i + 2].equals("7")) {
                actStr = "4G";
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
            } else if (strings[i + 2].equals("2")) {
                actStr = "3G";
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
            } else {
                actStr = "2G";
                rat = ServiceState.RIL_RADIO_TECHNOLOGY_GPRS;
            }

            //1 and 2 is 2g. above 2 is 3g
            String property_name = "gsm.baseband.capability";
            if (mInstanceId > PhoneConstants.SIM_ID_1) {
                property_name = property_name + (mInstanceId + 1) ;
            }

            int basebandCapability = SystemProperties.getInt(property_name, 3);
            Rlog.d(RILJ_LOG_TAG, "property_name=" + property_name + ",basebandCapability=" + basebandCapability);
            if (3 < basebandCapability) {
                strings[i + 1] = strings[i + 1].concat(" " + actStr);
            }

            hnbName = new String(IccUtils.hexStringToBytes(strings[i + 5]));

            Rlog.d(RILJ_LOG_TAG, "FemtoCellInfo(" + strings[i + 3] + "," + strings[i + 4] + "," + strings[i + 5] + "," + strings[i + 0] + "," + strings[i + 1] + "," + rat + ")" + "hnbName=" + hnbName);

            ret.add(
                new FemtoCellInfo(
                    Integer.parseInt(strings[i + 3]),
                    Integer.parseInt(strings[i + 4]),
                    hnbName,
                    strings[i + 0],
                    strings[i + 1],
                    rat));
        }

        return ret;
    }
    //MTK-END Femtocell (CSG)

    // xen0n refactored
    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        if (SystemProperties.get("ro.mtk_tc1_feature").equals("1")) {
            byte[] result = android.util.Base64.decode(nonce, android.util.Base64.DEFAULT);
            StringBuilder mStringBuilder = new StringBuilder(result.length * 2);
            for (byte mByte: result)
               mStringBuilder.append(String.format("%02x", mByte & 0xff));
            nonce = mStringBuilder.toString();
            if (RILJ_LOGD) riljLog("requestIsimAuthentication - nonce = " + nonce);
        }

        super.requestIsimAuthentication(nonce, response);
    }

    /** M: add extra parameter */
    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Message result) {
        IaExtendParam param = new IaExtendParam();
        setInitialAttachApn(apn, protocol, authType, username, password, (Object) param, result);
    }

    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Object obj, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_INITIAL_ATTACH_APN, null);

        if (RILJ_LOGD) { riljLog("Set RIL_REQUEST_SET_INITIAL_ATTACH_APN"); }

        rr.mParcel.writeString(apn);
        rr.mParcel.writeString(protocol);
        rr.mParcel.writeInt(authType);
        rr.mParcel.writeString(username);
        rr.mParcel.writeString(password);

        /** M: start */
        IaExtendParam param = (IaExtendParam) obj;
        rr.mParcel.writeString(param.mOperatorNumeric);
        rr.mParcel.writeInt(param.mCanHandleIms ? 1 : 0);
        rr.mParcel.writeStringArray(param.mDualApnPlmnList);
        /* M: end */

        if (RILJ_LOGD) { riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + ", apn:" + apn + ", protocol:" + protocol + ", authType:" + authType
                + ", username:" + username + ", password:" + password + " ," + param);
        }

        send(rr);
    }

    // xen0n: MTK REQUEST_SIM_GET_ATR has a different format, thus the AOSP
    // impl has to be overriden.
    @Override
    public void getAtr(Message response) {
        riljLog("getAtr: using MTK impl");
        iccGetATR(response);
    }

    //MTK-START Support Multi-Application
    @Override
    public void openIccApplication(int application, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_OPEN_ICC_APPLICATION, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(application);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + ", application = " + application);
        send(rr);
    }

    @Override
    public void getIccApplicationStatus(int sessionId, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_ICC_APPLICATION_STATUS, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(sessionId);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + ", session = " + sessionId);
        send(rr);
    }

    //MTK-END Support Multi-Application

    @Override public void
    queryNetworkLock(int category, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_SIM_NETWORK_LOCK, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        riljLog("queryNetworkLock:" + category);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(category);

        send(rr);
    }

    @Override public void
    setNetworkLock(int catagory, int lockop, String password,
                        String data_imsi, String gid1, String gid2, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_SIM_NETWORK_LOCK, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        riljLog("setNetworkLock:" + catagory + ", " + lockop + ", " + password + ", " + data_imsi
                + ", " + gid1 + ", " + gid2);

        rr.mParcel.writeInt(6);
        rr.mParcel.writeString(Integer.toString(catagory));
        rr.mParcel.writeString(Integer.toString(lockop));
        if (null != password) {
            rr.mParcel.writeString(password);
        } else {
            rr.mParcel.writeString("");
        }
        rr.mParcel.writeString(data_imsi);
        rr.mParcel.writeString(gid1);
        rr.mParcel.writeString(gid2);

        send(rr);
    }

    @Override public void
    doGeneralSimAuthentication(int sessionId, int mode , int tag, String param1,
                                         String param2, Message response) {

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GENERAL_SIM_AUTH, response);

        rr.mParcel.writeInt(sessionId);
        rr.mParcel.writeInt(mode);

        // Calcuate param1 length in byte length
        if (param1 != null && param1.length() > 0) {
            String length = Integer.toHexString(param1.length() / 2);
            length = (((length.length() % 2 == 1) ? "0" : "") + length);
            // Session id is equal to 0, for backward compability, we use old AT command
            // old AT command no need to include param's length
            rr.mParcel.writeString(((sessionId == 0) ? param1 : (length + param1)));
        } else {
            rr.mParcel.writeString(param1);
        }

        // Calcuate param2 length in byte length
        if (param2 != null && param2.length() > 0) {
            String length = Integer.toHexString(param2.length() / 2);
            length = (((length.length() % 2 == 1) ? "0" : "") + length);
            // Session id is equal to 0, for backward compability, we use old AT command
            // old AT command no need to include param's length
            rr.mParcel.writeString(((sessionId == 0) ? param2 : (length + param2)));
        } else {
            rr.mParcel.writeString(param2);
        }

        if (mode == 1) {
            rr.mParcel.writeInt(tag);
        }


        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " +
            "session = " + sessionId + ",mode = " + mode + ",tag = " + tag + ", "  + param1 + ", " + param2);

        send(rr);
    }
    // Added by M begin
    @Override
    public void
    iccGetATR(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SIM_GET_ATR, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    iccOpenChannelWithSw(String AID, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SIM_OPEN_CHANNEL_WITH_SW, result);

        rr.mParcel.writeString(AID);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> iccOpenChannelWithSw: " + requestToString(rr.mRequest)
                + " " + AID);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void sendBTSIMProfile(int nAction, int nType, String strData, Message response) {
        if (RILJ_LOGD) riljLog(" sendBTSIMProfile nAction is " + nAction);
        switch (nAction) {
            case 0:
                requestConnectSIM(response);
                break;
            case 1:
                requestDisconnectOrPowerOffSIM(nAction, response);
                break;
            case 2:
                requestPowerOnOrResetSIM(nAction, nType, response);
                break;
            case 3:
                requestDisconnectOrPowerOffSIM(nAction, response);
                break;
            case 4:
                requestPowerOnOrResetSIM(nAction, nType, response);
                break;
            case 5:
                requestTransferApdu(nAction, nType, strData, response);
                break;
        }
    }

    //***** Private Methods
    /**
    * used only by sendBTSIMProfile
    */
    private void requestConnectSIM(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_BTSIM_CONNECT, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
    * used only by sendBTSIMProfile
    */
    private void requestDisconnectOrPowerOffSIM(int nAction, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_BTSIM_DISCONNECT_OR_POWEROFF, response);

         rr.mParcel.writeString(Integer.toString(nAction));

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + nAction);

        send(rr);
    }

    /**
    * used only by sendBTSIMProfile
    */
    private void requestPowerOnOrResetSIM(int nAction, int nType, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_BTSIM_POWERON_OR_RESETSIM, response);

        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(Integer.toString(nAction));
        rr.mParcel.writeString(Integer.toString(nType));

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": "
                + nAction + " nType: " + nType);

        send(rr);
    }

    /**
    * used only by sendBTSIMProfile
    */
    private void requestTransferApdu(int nAction, int nType, String strData, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_BTSIM_TRANSFERAPDU, response);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(Integer.toString(nAction));
        rr.mParcel.writeString(Integer.toString(nType));
        rr.mParcel.writeString(strData);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": "
                + nAction + " nType: " + nType + " data: " + strData);

        send(rr);
    }
    // Added by M end

    /**
     * {@inheritDoc}
     */
    public void queryPhbStorageInfo(int type, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_PHB_STORAGE_INFO, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + type);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void writePhbEntry(PhbEntry entry, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_WRITE_PHB_ENTRY, result);

        rr.mParcel.writeInt(entry.type);
        rr.mParcel.writeInt(entry.index);
        rr.mParcel.writeString(entry.number);
        rr.mParcel.writeInt(entry.ton);
        rr.mParcel.writeString(entry.alphaId);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + entry);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void ReadPhbEntry(int type, int bIndex, int eIndex, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_READ_PHB_ENTRY, response);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeInt(type);
        rr.mParcel.writeInt(bIndex);
        rr.mParcel.writeInt(eIndex);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": "
                + type + " begin: " + bIndex + " end: " + eIndex);

        send(rr);
    }

    private Object
    responsePhbEntries(Parcel p) {
        int numerOfEntries;
        PhbEntry[] response;

        numerOfEntries = p.readInt();
        response = new PhbEntry[numerOfEntries];

        Rlog.d(RILJ_LOG_TAG, "Number: " + numerOfEntries);

        for (int i = 0; i < numerOfEntries; i++) {
            response[i] = new PhbEntry();
            response[i].type = p.readInt();
            response[i].index = p.readInt();
            response[i].number = p.readString();
            response[i].ton = p.readInt();
            response[i].alphaId = p.readString();
        }

        return response;
    }

    public void queryUPBCapability(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_UPB_CAPABILITY, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void editUPBEntry(int entryType, int adnIndex, int entryIndex, String strVal, String tonForNum, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_EDIT_UPB_ENTRY, response);
        if (entryType == 0) {
            rr.mParcel.writeInt(5);
        } else {
            rr.mParcel.writeInt(4);
        }
        rr.mParcel.writeString(Integer.toString(entryType));
        rr.mParcel.writeString(Integer.toString(adnIndex));
        rr.mParcel.writeString(Integer.toString(entryIndex));
        rr.mParcel.writeString(strVal);

        if (entryType == 0) {
            rr.mParcel.writeString(tonForNum);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);

    }

    public void deleteUPBEntry(int entryType, int adnIndex, int entryIndex, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DELETE_UPB_ENTRY, response);
        rr.mParcel.writeInt(3);
        rr.mParcel.writeInt(entryType);
        rr.mParcel.writeInt(adnIndex);
        rr.mParcel.writeInt(entryIndex);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void readUPBGasList(int startIndex, int endIndex, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_READ_UPB_GAS_LIST, response);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(startIndex);
        rr.mParcel.writeInt(endIndex);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void readUPBGrpEntry(int adnIndex, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_READ_UPB_GRP, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(adnIndex);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void writeUPBGrpEntry(int adnIndex, int[] grpIds, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_WRITE_UPB_GRP, response);
        int nLen = grpIds.length;
        rr.mParcel.writeInt(nLen + 1);
        rr.mParcel.writeInt(adnIndex);
        for (int i = 0; i < nLen; i++) {
            rr.mParcel.writeInt(grpIds[i]);
        }
        if (RILJ_LOGD) riljLog("writeUPBGrpEntry nLen is " + nLen);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);

    }

    private Object responseGetPhbMemStorage(Parcel p) {
        /*
        PBMemStorage response = PBMemStorage.createFromParcel(p);
        riljLog("responseGetPhbMemStorage:" +  response);
        return response;
        */
        Rlog.e(RILJ_LOG_TAG, "responseGetPhbMemStorage: stub!");
        return null;
    }

    // ALPS01977595 - KOR MVNO Operator Support
    private Object responseReadPhbEntryExt(Parcel p) {
        /*
        int numerOfEntries;
        PBEntry[] response;

        numerOfEntries = p.readInt();
        response = new PBEntry[numerOfEntries];

        Rlog.d(RILJ_LOG_TAG, "responseReadPhbEntryExt Number: " + numerOfEntries);

        for (int i = 0; i < numerOfEntries; i++) {
            response[i] = new PBEntry();
            response[i].setIndex1(p.readInt());
            response[i].setNumber(p.readString());
            response[i].setType(p.readInt());
            response[i].setText(getAdnRecordFromPBEntry(p.readString()));
            response[i].setHidden(p.readInt());
            response[i].setGroup(p.readString());
            response[i].setAdnumber(p.readString());
            response[i].setAdtype(p.readInt());
            response[i].setSecondtext(p.readString());
            response[i].setEmail(getEmailRecordFromPBEntry(p.readString()));

            Rlog.d(RILJ_LOG_TAG, "responseReadPhbEntryExt[" + i + "] " + response[i].toString());
        }

        return response;
        */
        Rlog.e(RILJ_LOG_TAG, "responseReadPhbEntryExt: stub!");
        return null;
    }

    public static String convertKSC5601(String input) {
        Rlog.d(RILJ_LOG_TAG, "convertKSC5601");

        String output = "";
        try {
            int    ucslen = 0;
            byte[] inData = IccUtils.hexStringToBytes(input.substring(4));
            if (inData != null)
            {
                String strKSC = new String(inData , "KSC5601");

                if (strKSC != null) {
                    ucslen = strKSC.length();
                    while (ucslen > 0 && strKSC.charAt(ucslen - 1) == '\uF8F7')
                        ucslen--;

                    output = strKSC.substring(0, ucslen);
                }
            }
        } catch (UnsupportedEncodingException ex) {
            Rlog.d(RILJ_LOG_TAG, "Implausible UnsupportedEncodingException : " + ex);
        }

        return output;
    }

    public static String getEmailRecordFromPBEntry(String text) {
        if (text == null)
            return null;

        String email = "";

        if (text.trim().length() > 2 && text.startsWith("FEFE"))
            email = convertKSC5601(text);
        else
            email = text;

        Rlog.d(RILJ_LOG_TAG, "getEmailRecordFromPBEntry - email = " + email);

        return email;
    }

    public static String getAdnRecordFromPBEntry(String text) {
        if (text == null)
            return null;

        String alphaId = "";

        if (text.trim().length() > 2 && text.startsWith("FEFE"))
            alphaId = convertKSC5601(text);
        else
        {
            Rlog.d(RILJ_LOG_TAG, "getRecordFromPBEntry - Not KSC5601 Data");
            try {
                byte[] ba = IccUtils.hexStringToBytes(text);
                if (ba == null)
                    return null;

                alphaId = new String(ba, 0, text.length() / 2, "utf-16be");
            } catch (UnsupportedEncodingException ex) {
                Rlog.d(RILJ_LOG_TAG, "Implausible UnsupportedEncodingException : " + ex);
            }
        }

        Rlog.d(RILJ_LOG_TAG, "getRecordFromPBEntry - alphaId = " + alphaId);

        return alphaId;
    }
    // ALPS01977595 - KOR MVNO Operator Support

    /**
     * at+cpbr=?
     * @return  <nlength><tlength><glength><slength><elength>
     */
    public void getPhoneBookStringsLength(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_PHB_STRING_LENGTH, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> :::" + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * at+cpbs?
     * @return  PBMemStorage :: +cpbs:<storage>,<used>,<total>
     */
    public void getPhoneBookMemStorage(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_PHB_MEM_STORAGE, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> :::" + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * at+epin2=<p2>; at+cpbs=<storage>
     * @return
     */
    public void setPhoneBookMemStorage(String storage, String password, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_PHB_MEM_STORAGE, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(storage);
        rr.mParcel.writeString(password);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> :::" + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * M at+cpbr=<index1>,<index2>
     * +CPBR:<indexn>,<number>,<type>,<text>,<hidden>,<group>,<adnumber>,<adtype>,<secondtext>,<email>
     */
    public void readPhoneBookEntryExt(int index1, int index2, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_READ_PHB_ENTRY_EXT, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(index1);
        rr.mParcel.writeInt(index2);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> :::" + requestToString(rr.mRequest));
        send(rr);
    }

    // MTK TODO
    /**
     * M AT+CPBW=<index>,<number>,<type>,<text>,<hidden>,<group>,<adnumber>,<adtype>,<secondtext>,<email>
     */
    /*
    public void writePhoneBookEntryExt(PBEntry entry, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_WRITE_PHB_ENTRY_EXT, result);

        rr.mParcel.writeInt(entry.getIndex1());
        rr.mParcel.writeString(entry.getNumber());
        rr.mParcel.writeInt(entry.getType());
        rr.mParcel.writeString(entry.getText());
        rr.mParcel.writeInt(entry.getHidden());

        rr.mParcel.writeString(entry.getGroup());
        rr.mParcel.writeString(entry.getAdnumber());
        rr.mParcel.writeInt(entry.getAdtype());
        rr.mParcel.writeString(entry.getSecondtext());
        rr.mParcel.writeString(entry.getEmail());

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + entry);

        send(rr);
    }
    */

    // MTK-START, SMS part
    /**
     * {@inheritDoc}
     */
    public void getSmsParameters(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_SMS_PARAMS, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    private Object
    responseSmsParams(Parcel p) {
        /*
        int format = p.readInt();
        int vp = p.readInt();
        int pid = p.readInt();
        int dcs = p.readInt();

        return new SmsParameters(format, vp, pid, dcs);
        */
        Rlog.e(RILJ_LOG_TAG, "responseSmsParams: stub!");
        return null;
    }

    // MTK TODO
    /**
     * {@inheritDoc}
     */
    /*
    public void setSmsParameters(SmsParameters params, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_SMS_PARAMS, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mParcel.writeInt(4);
        rr.mParcel.writeInt(params.format);
        rr.mParcel.writeInt(params.vp);
        rr.mParcel.writeInt(params.pid);
        rr.mParcel.writeInt(params.dcs);

        send(rr);
    }
    */

    /**
     * {@inheritDoc}
     */
    public void getSmsSimMemoryStatus(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_SMS_SIM_MEM_STATUS, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    private Object responseSimSmsMemoryStatus(Parcel p) {
        IccSmsStorageStatus response;

        response = new IccSmsStorageStatus();
        response.mUsed = p.readInt();
        response.mTotal = p.readInt();
        return response;
    }

    /**
     * {@inheritDoc}
     */
    public void setEtws(int mode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_ETWS, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(mode);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " +
                mode);

        send(rr);
    }

    public void setCellBroadcastChannelConfigInfo(String config, int cb_set_type,
            Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_CB_CHANNEL_CONFIG_INFO, response);

        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(config);
        rr.mParcel.writeString(Integer.toString(cb_set_type));
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setCellBroadcastLanguageConfigInfo(String config, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_CB_LANGUAGE_CONFIG_INFO, response);

        rr.mParcel.writeString(config);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryCellBroadcastConfigInfo(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_CB_CONFIG_INFO, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    private Object responseCbConfig(Parcel p) {
        /*
        int mode            = p.readInt();
        String channels     = p.readString();
        String languages    = p.readString();
        boolean allOn       = (p.readInt() == 1) ? true : false;

        return new CellBroadcastConfigInfo(mode, channels, languages, allOn);
        */
        Rlog.e(RILJ_LOG_TAG, "responseCbConfig: stub!");
        return null;
    }

    public void removeCellBroadcastMsg(int channelId, int serialId, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_REMOVE_CB_MESSAGE, response);

        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(channelId);
        rr.mParcel.writeInt(serialId);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " +
            channelId + ", " + serialId);

        send(rr);
    }

    private Object responseEtwsNotification(Parcel p) {
        // MTK TODO
        /*
        EtwsNotification response = new EtwsNotification();

        response.warningType = p.readInt();
        response.messageId = p.readInt();
        response.serialNumber = p.readInt();
        response.plmnId = p.readString();
        response.securityInfo = p.readString();

        return response;
        */
        Rlog.e(RILJ_LOG_TAG, "responseEtwsNotification: stub!");
        return null;
    }
    // MTK-END, SMS part

    public void setTrm(int mode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_TRM, null);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(mode);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void queryModemType(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_MODEM_TYPE, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void storeModemType(int modemType, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_STORE_MODEM_TYPE, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(modemType);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void reloadModemType(int modemType, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RELOAD_MODEM_TYPE, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(modemType);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setStkEvdlCallByAP(int enabled, Message response) {
        RILRequest rr =
                //RILRequest.obtain(RIL_REQUEST_STK_EVDL_CALL_BY_AP, response, mySimId);
                RILRequest.obtain(RIL_REQUEST_STK_EVDL_CALL_BY_AP, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + ">>> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enabled);
        send(rr);
    }

    /// M: CC053: MoMS [Mobile Managerment] @{
    // 3. Permission Control for Conference call
    /**
    * To check sub-permission for MoMS before using API.
    *
    * @param subPermission  The permission to be checked.
    *
    * @return Return true if the permission is granted else return false.
    */
    private boolean checkMoMSSubPermission(String subPermission) {
        riljLog("MoMS: no-op!");
        /*
        try {
            IMobileManagerService mMobileManager;
            IBinder binder = ServiceManager.getService(Context.MOBILE_SERVICE);
            mMobileManager = IMobileManagerService.Stub.asInterface(binder);
            int result = mMobileManager.checkPermission(subPermission, Binder.getCallingUid());
            if (result != PackageManager.PERMISSION_GRANTED) {
                riljLog("[Error]Subpermission is not granted!!");
                return false;
            }
        } catch (Exception e) {
            riljLog("[Error]Failed to chcek permission: " +  subPermission);
            return false;
        }
        */

        return true;
    }
    /// @}

    /// M: CC010: Add RIL interface @{
    private Object
    responseCrssNotification(Parcel p) {
        // MTK TODO
        /*
        SuppCrssNotification notification = new SuppCrssNotification();

        notification.code = p.readInt();
        notification.type = p.readInt();
        notification.number = p.readString();
        notification.alphaid = p.readString();
        notification.cli_validity = p.readInt();

        return notification;
        */
        Rlog.e(RILJ_LOG_TAG, "responseCrssNotification: stub!");
        return null;
    }
    /// @}

    /// M: CC010: Add RIL interface @{
    public void
    hangupAll(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_HANGUP_ALL,
                                        result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void forceReleaseCall(int index, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_FORCE_RELEASE_CALL, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + index);

        send(rr);
    }

    public void setCallIndication(int mode, int callId, int seqNumber, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_CALL_INDICATION, result);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeInt(mode);
        rr.mParcel.writeInt(callId);
        rr.mParcel.writeInt(seqNumber);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + mode + ", " + callId + ", " + seqNumber);

        send(rr);
    }

    // ported from CM12.1 MediaTekRIL
    private void setCallIndication(final String[] incomingCallInfo) {
        final int callId = Integer.parseInt(incomingCallInfo[0]);
        final int callMode = Integer.parseInt(incomingCallInfo[3]);
        final int seqNumber = Integer.parseInt(incomingCallInfo[4]);
        // just call into the MTK impl
        setCallIndication(callMode, callId, seqNumber, null);
    }

    public void
    emergencyDial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_EMERGENCY_DIAL, result);

        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        rr.mParcel.writeInt(0); // UUS information is absent

        if (uusInfo == null) {
            rr.mParcel.writeInt(0); // UUS information is absent
        } else {
            rr.mParcel.writeInt(1); // UUS information is present
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /* M: IMS VoLTE conference dial feature start*/
    /**
     * Dial conference call.
     * @param participants participants' dailing number.
     * @param clirMode indication to present the dialing number or not.
     * @param isVideoCall indicate the call is belong to video call or voice call.
     * @param result the command result.
     */
    public void
    conferenceDial(String[] participants, int clirMode, boolean isVideoCall, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CONFERENCE_DIAL, result);

        int numberOfParticipants = participants.length;
        /* numberOfStrings is including
         * 1. isvideoCall
         * 2. numberofparticipants
         * 3. participants numbers
         * 4. clirmod
         */
        int numberOfStrings = 1 + 1 + numberOfParticipants + 1 ;
        List<String> participantList = Arrays.asList(participants);

        if (RILJ_LOGD) {
            Rlog.d(RILJ_LOG_TAG, "conferenceDial: numberOfParticipants "
                    + numberOfParticipants + "numberOfStrings:" + numberOfStrings);
        }

        rr.mParcel.writeInt(numberOfStrings);

        if (isVideoCall) {
            rr.mParcel.writeString(Integer.toString(1));
        } else {
            rr.mParcel.writeString(Integer.toString(0));
        }

        rr.mParcel.writeString(Integer.toString(numberOfParticipants));

        for (String dialNumber : participantList) {
            rr.mParcel.writeString(dialNumber);
            if (RILJ_LOGD) {
                Rlog.d(RILJ_LOG_TAG, "conferenceDial: dialnumber " + dialNumber);
            }
        }
        rr.mParcel.writeString(Integer.toString(clirMode));
        if (RILJ_LOGD) {
            Rlog.d(RILJ_LOG_TAG, "conferenceDial: clirMode " + clirMode);
        }

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);

    }
    /* IMS VoLTE conference dial feature end*/

    public void setEccServiceCategory(int serviceCategory) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_ECC_SERVICE_CATEGORY, null);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(serviceCategory);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
            + " " + serviceCategory);

        send(rr);
    }

    private void setEccList() {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_ECC_LIST, null);
        ArrayList<PhoneNumberUtils.EccEntry> eccList = PhoneNumberUtils.getEccList();

        rr.mParcel.writeInt(eccList.size() * 3);
        for (PhoneNumberUtils.EccEntry entry : eccList) {
            rr.mParcel.writeString(entry.getEcc());
            rr.mParcel.writeString(entry.getCategory());
            String strCondition = entry.getCondition();
            if (strCondition.equals(PhoneNumberUtils.EccEntry.ECC_FOR_MMI))
                strCondition = PhoneNumberUtils.EccEntry.ECC_NO_SIM;
            rr.mParcel.writeString(strCondition);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    public void setSpeechCodecInfo(boolean enable, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_SPEECH_CODEC_INFO,
                response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enable ? 1 : 0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
            + " " + enable);
        send(rr);
    }
    /// @}

    /// M: For 3G VT only @{
    public void
    vtDial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VT_DIAL, result);

        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);

        if (uusInfo == null) {
            rr.mParcel.writeInt(0); // UUS information is absent
        } else {
            rr.mParcel.writeInt(1); // UUS information is present
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    acceptVtCallWithVoiceOnly(int callId, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VOICE_ACCEPT, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " +
                requestToString(rr.mRequest) + " " + callId);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(callId);

        send(rr);
    }

    public void replaceVtCall(int index, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_REPLACE_VT_CALL, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(index);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }
    /// @}

    /// M: IMS feature. @{
    public void addConferenceMember(int confCallId, String address, int callIdToAdd, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ADD_IMS_CONFERENCE_CALL_MEMBER, response);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(Integer.toString(confCallId));
        rr.mParcel.writeString(address);
        rr.mParcel.writeString(Integer.toString(callIdToAdd));

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void removeConferenceMember(int confCallId, String address, int callIdToRemove, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_REMOVE_IMS_CONFERENCE_CALL_MEMBER, response);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(Integer.toString(confCallId));
        rr.mParcel.writeString(address);
        rr.mParcel.writeString(Integer.toString(callIdToRemove));

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    /**
     * To resume the call.
     * @param callIdToResume toIndicate which call session to resume.
     * @param response command response.
     */
    public void resumeCall(int callIdToResume, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RESUME_CALL, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(callIdToResume);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    /**
     * To hold the call.
     * @param callIdToHold toIndicate which call session to hold.
     * @param response command response.
     */
    public void holdCall(int callIdToHold, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_HOLD_CALL, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(callIdToHold);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }
    /// @}

    /* M: SS part */
    public void
    changeBarringPassword(String facility, String oldPwd, String newPwd,
        String newCfm, Message result) {
        //RILRequest rr = RILRequest.obtain(RIL_REQUEST_CHANGE_BARRING_PASSWORD, result, mySimId);
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CHANGE_BARRING_PASSWORD, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mParcel.writeInt(4);
        rr.mParcel.writeString(facility);
        rr.mParcel.writeString(oldPwd);
        rr.mParcel.writeString(newPwd);
        rr.mParcel.writeString(newCfm);
        send(rr);
    }

    public void setCLIP(boolean enable, Message result) {
        //RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_CLIP, result, mySimId);
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_CLIP, result);

        // count ints
        rr.mParcel.writeInt(1);

        if (enable) {
            rr.mParcel.writeInt(1);
        } else {
            rr.mParcel.writeInt(0);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + enable);

        send(rr);
    }
    /* M: SS part end */

    /* M: Network part start */
    public String lookupOperatorNameFromNetwork(long subId, String numeric, boolean desireLongName) {
        int phoneId = SubscriptionManager.getPhoneId((int) subId);
        String nitzOperatorNumeric = null;
        String nitzOperatorName = null;

        nitzOperatorNumeric = TelephonyManager.getTelephonyProperty(phoneId, TelephonyProperties.PROPERTY_NITZ_OPER_CODE, "");
        if ((numeric != null) && (numeric.equals(nitzOperatorNumeric))) {
            if (desireLongName == true) {
                nitzOperatorName = TelephonyManager.getTelephonyProperty(phoneId, TelephonyProperties.PROPERTY_NITZ_OPER_LNAME, "");
            } else {
                nitzOperatorName = TelephonyManager.getTelephonyProperty(phoneId, TelephonyProperties.PROPERTY_NITZ_OPER_SNAME, "");
            }
        }

        /* ALPS00273663 handle UCS2 format name : prefix + hex string ex: "uCs2806F767C79D1" */
        if ((nitzOperatorName != null) && (nitzOperatorName.startsWith("uCs2") == true))
        {
            riljLog("lookupOperatorNameFromNetwork handling UCS2 format name");
            try {
                nitzOperatorName = new String(IccUtils.hexStringToBytes(nitzOperatorName.substring(4)), "UTF-16");
            } catch (UnsupportedEncodingException ex) {
                riljLog("lookupOperatorNameFromNetwork UnsupportedEncodingException");
            }
        }

        riljLog("lookupOperatorNameFromNetwork numeric= " + numeric + ",subId= " + subId + ",nitzOperatorNumeric= " + nitzOperatorNumeric + ",nitzOperatorName= " + nitzOperatorName);

        return nitzOperatorName;
    }

    @Override
    public void
    setNetworkSelectionModeManualWithAct(String operatorNumeric, String act, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL_WITH_ACT,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + operatorNumeric + "" + act);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(operatorNumeric);
        rr.mParcel.writeString(act);
        rr.mParcel.writeString("0"); //the 3rd parameter is for MTK RIL to identify it shall be processed as semi auto network selection mode or not

        send(rr);
    }

    private Object
    responseNetworkInfoWithActs(Parcel p) {
        String strings[] = (String []) responseStrings(p);
        ArrayList<NetworkInfoWithAcT> ret;

        if (strings.length % 4 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_GET_POL_LIST: invalid response. Got "
                + strings.length + " strings, expected multible of 5");
        }

        ret = new ArrayList<NetworkInfoWithAcT>(strings.length / 4);

        String strOperName = null;
        String strOperNumeric = null;
        int nAct = 0;
        int nIndex = 0;

        for (int i = 0 ; i < strings.length ; i += 4) {
            strOperName = null;
            strOperNumeric = null;
            if (strings[i] != null) {
                nIndex = Integer.parseInt(strings[i]);
            } else {
                Rlog.d(RILJ_LOG_TAG, "responseNetworkInfoWithActs: no invalid index. i is " + i);
            }

            if (strings[i + 1] != null) {
                int format = Integer.parseInt(strings[i + 1]);
                switch (format) {
                    case 0:
                    case 1:
                        strOperName = strings[i + 2];
                        break;
                    case 2:
                        if (strings[i + 2] != null) {
                            strOperNumeric = strings[i + 2];
                            strOperName = SpnOverride.getInstance().lookupOperatorName(SubscriptionManager.getSubIdUsingPhoneId(mInstanceId), strings[i + 2], true, mContext);
                        }
                        break;
                    default:
                        break;
                }
            }

            if (strings[i + 3] != null) {
                nAct = Integer.parseInt(strings[i + 3]);
            } else {
                Rlog.d(RILJ_LOG_TAG, "responseNetworkInfoWithActs: no invalid Act. i is " + i);
            }
            if (strOperNumeric != null && !strOperNumeric.equals("?????")) {
                ret.add(
                    new NetworkInfoWithAcT(
                        strOperName,
                        strOperNumeric,
                        nAct,
                        nIndex));
            } else {
                Rlog.d(RILJ_LOG_TAG, "responseNetworkInfoWithActs: invalid oper. i is " + i);
            }
        }

        return ret;
    }

    public void
    setNetworkSelectionModeSemiAutomatic(String operatorNumeric, String act, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL_WITH_ACT,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + operatorNumeric + "" + act);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(operatorNumeric);
        rr.mParcel.writeString(act);
        rr.mParcel.writeString("1"); //the 3rd parameter is for MTK RIL to identify it shall be processed as semi auto network selection mode

        send(rr);
    }

    public void getPOLCapabilty(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_POL_CAPABILITY, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void getCurrentPOLList(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_POL_LIST, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void setPOLEntry(int index, String numeric, int nAct, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_POL_ENTRY, response);
        if (numeric == null || (numeric.length() == 0)) {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeString(Integer.toString(index));
        } else {
            rr.mParcel.writeInt(3);
            rr.mParcel.writeString(Integer.toString(index));
            rr.mParcel.writeString(numeric);
            rr.mParcel.writeString(Integer.toString(nAct));
        }
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    // Femtocell (CSG) feature START
    public void getFemtoCellList(String operatorNumeric, int rat, Message response) {
        RILRequest rr
        = RILRequest.obtain(RIL_REQUEST_GET_FEMTOCELL_LIST,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(operatorNumeric);
        rr.mParcel.writeString(Integer.toString(rat));
        send(rr);
    }

    public void abortFemtoCellList(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ABORT_FEMTOCELL_LIST, response);
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    public void selectFemtoCell(FemtoCellInfo femtocell, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SELECT_FEMTOCELL,
                                    response);
        int act = femtocell.getCsgRat();

        if (act == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
            act = 7;
        } else if (act == ServiceState.RIL_RADIO_TECHNOLOGY_UMTS) {
            act = 2;
        } else {
            act = 0;
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " csgId=" + femtocell.getCsgId() + " plmn=" + femtocell.getOperatorNumeric() + " rat=" + femtocell.getCsgRat() + " act=" + act);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeString(femtocell.getOperatorNumeric());
        rr.mParcel.writeString(Integer.toString(act));
        rr.mParcel.writeString(Integer.toString(femtocell.getCsgId()));

        send(rr);
    }
    // Femtocell (CSG) feature END

    // M: CC33 LTE.
    @Override
    public void
    setDataOnToMD(boolean enable, Message result) {
        //AT+EDSS = <on/off>
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_DATA_ON_TO_MD, result);
        int type = enable ? 1 : 0;
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                                + requestToString(rr.mRequest) + ": " + type);
        send(rr);
    }

    @Override
    public void
    setRemoveRestrictEutranMode(boolean enable, Message result) {
        //AT+ECODE33 = <on/off>
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_REMOVE_RESTRICT_EUTRAN_MODE, result);
        int type = enable ? 1 : 0;
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                                + requestToString(rr.mRequest) + ": " + type);
        send(rr);
    }

    // M: [LTE][Low Power][UL traffic shaping] Start
    @Override
    public void
    setLteAccessStratumReport(boolean enable, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_LTE_ACCESS_STRATUM_REPORT, result);
        int type = enable ? 1 : 0;
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(type);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                                + requestToString(rr.mRequest) + ": " + type);
        send(rr);
    }

    @Override
    public void
    setLteUplinkDataTransfer(int state, int interfaceId, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_LTE_UPLINK_DATA_TRANSFER, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(state);
        rr.mParcel.writeInt(interfaceId);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                                + requestToString(rr.mRequest)
                                + " state = " + state
                                + ", interfaceId = " + interfaceId);
        send(rr);
    }
    // M: [LTE][Low Power][UL traffic shaping] End

    public boolean isGettingAvailableNetworks() {
        synchronized (mRequestList) {
            for (int i = 0, s = mRequestList.size() ; i < s ; i++) {
                RILRequest rr = mRequestList.valueAt(i);
                if (rr != null &&
                    (rr.mRequest == RIL_REQUEST_QUERY_AVAILABLE_NETWORKS ||
                     rr.mRequest == RIL_REQUEST_QUERY_AVAILABLE_NETWORKS_WITH_ACT)) {
                    return true;
                }
            }
        }

        return false;
    }

    /* M: Network part end */
    // IMS
    public void setIMSEnabled(boolean enable, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_IMS_ENABLE, response);

        rr.mParcel.writeInt(1);
        if (enable) {
            rr.mParcel.writeInt(1);
        } else {
            rr.mParcel.writeInt(0);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    // M: Fast Dormancy
    public void setScri(boolean forceRelease, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_SCRI, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(forceRelease ? 1 : 0);

        send(rr);

    }

    //[New R8 modem FD]
    public void setFDMode(int mode, int parameter1, int parameter2, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_FD_MODE, response);

        //AT+EFD=<mode>[,<param1>[,<param2>]]
        //mode=0:disable modem Fast Dormancy; mode=1:enable modem Fast Dormancy
        //mode=3:inform modem the screen status; parameter1: screen on or off
        //mode=2:Fast Dormancy inactivity timer; parameter1:timer_id; parameter2:timer_value
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        if (mode == 0 || mode == 1) {
            rr.mParcel.writeInt(1);
            rr.mParcel.writeInt(mode);
        } else if (mode == 3) {
            rr.mParcel.writeInt(2);
            rr.mParcel.writeInt(mode);
            rr.mParcel.writeInt(parameter1);
        } else if (mode == 2) {
            rr.mParcel.writeInt(3);
            rr.mParcel.writeInt(mode);
            rr.mParcel.writeInt(parameter1);
            rr.mParcel.writeInt(parameter2);
        }

        send(rr);

    }

    // @argument:
    // enable: yes   -> data centric
    //         false -> voice centric
    public void setDataCentric(boolean enable, Message response) {
        if (RILJ_LOGD) riljLog("setDataCentric");
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_DATA_CENTRIC, response);

        rr.mParcel.writeInt(1);
        if (enable) {
            rr.mParcel.writeInt(1);
        } else {
            rr.mParcel.writeInt(0);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }


    /// M: CC010: Add RIL interface @{
    /**
     * Notify modem about IMS call status.
     * @param existed True if there is at least one IMS call existed, else return false.
     * @param response User-defined message code.
     */
    @Override
    public void setImsCallStatus(boolean existed, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_IMS_CALL_STATUS, null);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(existed ? 1 : 0);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }
    /// @}

    /// M: CC072: Add Customer proprietary-IMS RIL interface. @{
    /**
     * Transfer IMS call to CS modem.
     *
     * @param numberOfCall The number of call
     * @param callList IMS call context
     */
     @Override
     public void setSrvccCallContextTransfer(int numberOfCall, SrvccCallContext[] callList) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_SRVCC_CALL_CONTEXT_TRANSFER, null);

        if ((numberOfCall <= 0) || (callList == null)) {
              return;
        }

        rr.mParcel.writeInt(numberOfCall * 9 + 1);
        rr.mParcel.writeString(Integer.toString(numberOfCall));
        for (int i = 0; i < numberOfCall; i++) {
            rr.mParcel.writeString(Integer.toString(callList[i].getCallId()));
            rr.mParcel.writeString(Integer.toString(callList[i].getCallMode()));
            rr.mParcel.writeString(Integer.toString(callList[i].getCallDirection()));
            rr.mParcel.writeString(Integer.toString(callList[i].getCallState()));
            rr.mParcel.writeString(Integer.toString(callList[i].getEccCategory()));
            rr.mParcel.writeString(Integer.toString(callList[i].getNumberType()));
            rr.mParcel.writeString(callList[i].getNumber());
            rr.mParcel.writeString(callList[i].getName());
            rr.mParcel.writeString(Integer.toString(callList[i].getCliValidity()));
        }

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
     }

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
     public void updateImsRegistrationStatus(int regState, int regType, int reason) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_UPDATE_IMS_REGISTRATION_STATUS, null);

        rr.mParcel.writeInt(3);
        rr.mParcel.writeInt(regState);
        rr.mParcel.writeInt(regType);
        rr.mParcel.writeInt(reason);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
     }
     /// @}

    /* M: C2K part start */
    @Override
    public void setViaTRM(int mode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_VIA_TRM, null);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(mode);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void getNitzTime(Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_GET_NITZ_TIME, result);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void requestSwitchHPF(boolean enableHPF, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SWITCH_HPF, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + enableHPF);
        }

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enableHPF ? 1 : 0);

        send(rr);
    }

    @Override
    public void setAvoidSYS(boolean avoidSYS, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_AVOID_SYS, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + avoidSYS);
        }

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(avoidSYS ? 1 : 0);

        send(rr);
    }

    @Override
    public void getAvoidSYSList(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_AVOID_SYS, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void queryCDMANetworkInfo(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_CDMA_NETWORK_INFO, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void setOplmn(String oplmnInfo, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SEND_OPLMN, response);
        rr.mParcel.writeString(oplmnInfo);
        riljLog("sendOplmn, OPLMN is" + oplmnInfo);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void getOplmnVersion(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_OPLMN_VERSION, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void requestAGPSTcpConnected(int connected, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_AGPS_TCP_CONNIND, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(connected);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + connected);
        }
        send(rr);
    }

    @Override
    public void requestAGPSSetMpcIpPort(String ip, String port, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_AGPS_SET_MPC_IPPORT, result);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeString(ip);
        rr.mParcel.writeString(port);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " : " + ip + ", " + port);
        }
        send(rr);
    }

    @Override
    public void requestAGPSGetMpcIpPort(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_AGPS_GET_MPC_IPPORT, result);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void requestSetEtsDev(int dev, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_ETS_DEV, result);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(dev);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + dev);
        }
        send(rr);
    }

    @Override
    public void setArsiReportThreshold(int threshold, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_SET_ARSI_THRESHOLD, response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(threshold);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " : " + threshold);
        }

        send(rr);
    }

    @Override
    public void queryCDMASmsAndPBStatus(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_SMS_AND_PHONEBOOK_STATUS, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void queryCDMANetWorkRegistrationState(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_NETWORK_REGISTRATION, response);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void setMeid(String meid, Message response) {
        RILRequest rr
               = RILRequest.obtain(RIL_REQUEST_SET_MEID, response);

       rr.mParcel.writeString(meid);
       if (RILJ_LOGD) {
           riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + meid);
       }

       send(rr);
   }

    @Override
    public void setMdnNumber(String mdn, Message response) {
         RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_WRITE_MDN, response);

        rr.mParcel.writeString(mdn);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + mdn);
        }

        send(rr);
    }

    private Object responseGetNitzTime(Parcel p) {
        Object[] result = new Object[2];
        String response;

        response = p.readString();
        long nitzReceiveTime = p.readLong();
        result[0] = response;
        result[1] = Long.valueOf(nitzReceiveTime);

        return result;
    }

    /// M: UTK started @{
    @Override
    public void getUtkLocalInfo(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_LOCAL_INFO, result);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void requestUtkRefresh(int refreshType, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_UTK_REFRESH, result);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(refreshType);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void reportUtkServiceIsRunning(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING, result);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void profileDownload(String profile, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_STK_SET_PROFILE, response);

        rr.mParcel.writeString(profile);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }

        send(rr);
    }

    @Override
    public void handleCallSetupRequestFromUim(boolean accept, Message response) {
        RILRequest rr = RILRequest.obtain(
            RILConstants.RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM,
            response);

        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(accept ? 1 : 0);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + (accept ? 1 : 0));
        }

        send(rr);
    }
    /// UTK end @}

    ///M: [C2K][SVLTE] Removt SIM access feature @{
    @Override
    public void configModemStatus(int modemStatus, int remoteSimProtocol, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CONFIG_MODEM_STATUS, result);

        // count ints
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(modemStatus);
        rr.mParcel.writeInt(remoteSimProtocol);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + modemStatus + ", " + remoteSimProtocol);
        }

        send(rr);
    }
    /// @}

    /// M: [C2K][SVLTE] C2K SVLTE CDMA eHPRD control @{
    @Override
    public void configEvdoMode(int evdoMode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CONFIG_EVDO_MODE, result);

        // count ints
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(evdoMode);

        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + evdoMode);
        }

        send(rr);
    }
    /// @}

    ///M: [C2K][IRAT] code start @{
    @Override
    public void confirmIratChange(int apDecision, Message response) {
        RILRequest rr = RILRequest.obtain(RILConstants.RIL_REQUEST_CONFIRM_INTER_3GPP_IRAT_CHANGE,
                response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(apDecision);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + apDecision);
        }
        send(rr);
    }

    @Override
    public void requestSetPsActiveSlot(int psSlot, Message response) {
        RILRequest rr = RILRequest.obtain(RILConstants.RIL_REQUEST_SET_ACTIVE_PS_SLOT, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(psSlot);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + psSlot);
        }
        send(rr);
    }

    @Override
    public void syncNotifyDataCallList(AsyncResult dcList) {
        riljLog("[C2K_IRAT_RIL] notify data call list!");
        mDataNetworkStateRegistrants.notifyRegistrants(dcList);
    }

    @Override
    public void requestDeactivateLinkDownPdn(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_DEACTIVATE_LINK_DOWN_PDN, response);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    private Object responseIratStateChange(Parcel p) {
        // MTK TODO
        /*
        MdIratInfo pdnIratInfo = new MdIratInfo();
        pdnIratInfo.sourceRat = p.readInt();
        pdnIratInfo.targetRat = p.readInt();
        pdnIratInfo.action = p.readInt();
        pdnIratInfo.type = IratType.getIratTypeFromInt(p.readInt());
        riljLog("[C2K_IRAT_RIL]responseIratStateChange: pdnIratInfo = " + pdnIratInfo);
        return pdnIratInfo;
        */
        Rlog.e(RILJ_LOG_TAG, "responseIratStateChange: stub!");
        return null;
    }
    ///@} [C2K] IRAT code end

    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @{
    @Override
    public void setSvlteRatMode(int radioTechMode, int preSvlteMode, int svlteMode,
            int preRoamingMode, int roamingMode, boolean is3GDualModeCard, Message response) {
        RILRequest rr = RILRequest.obtain(RILConstants.RIL_REQUEST_SET_SVLTE_RAT_MODE, response);
        rr.mParcel.writeInt(6);
        rr.mParcel.writeInt(radioTechMode);
        rr.mParcel.writeInt(preSvlteMode);
        rr.mParcel.writeInt(svlteMode);
        rr.mParcel.writeInt(preRoamingMode);
        rr.mParcel.writeInt(roamingMode);
        rr.mParcel.writeInt(is3GDualModeCard ? 1 : 0);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " radioTechMode: " + radioTechMode
                    + " preSvlteMode: " + preSvlteMode + " svlteMode: " + svlteMode
                    + " preRoamingMode: " + preRoamingMode + " roamingMode: " + roamingMode
                    + " is3GDualModeCard: " + is3GDualModeCard);
        }
        send(rr);
    }
    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @}

    /// M: [C2K][SVLTE] Set the STK UTK mode. @}
    @Override
    public void setStkUtkMode(int stkUtkMode, Message response) {
        RILRequest rr = RILRequest.obtain(RILConstants.RIL_REQUEST_SET_STK_UTK_MODE, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(stkUtkMode);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " stkUtkMode: " + stkUtkMode);
        }
        send(rr);
    }
    /// M: [C2K][SVLTE] Set the STK UTK mode. @}

    /// M: [C2K][SVLTE] Update RIL instance id for SVLTE switch ActivePhone. @{
    @Override
    public void setInstanceId(int instanceId) {
        mInstanceId = instanceId;
    }
    /// @}

    /// M: [C2K][IR] Support SVLTE IR feature. @{

    @Override
    public void setRegistrationSuspendEnabled(int enabled, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_REG_SUSPEND_ENABLED, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enabled);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void setResumeRegistration(int sessionId, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RESUME_REGISTRATION, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(sessionId);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    @Override
    public void setCdmaRegistrationSuspendEnabled(boolean enabled, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_REG_SUSPEND_ENABLED_CDMA, response);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enabled ? 1 : 0);
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " enable=" + enabled);
        }
        send(rr);
    }

    @Override
    public void setResumeCdmaRegistration(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RESUME_REGISTRATION_CDMA, response);
        mVoiceNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, null, null));
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        }
        send(rr);
    }

    /// M: [C2K][IR] Support SVLTE IR feature. @}

    /* M: C2K part end */

    //[ALPS01810775,ALPS01868743]-Start
    public int getDisplayState() {
        return mDefaultDisplayState;
    }
    //[ALPS01810775,ALPS01868743]-End

    // M: [C2K] SVLTE Remote SIM Access start.
    private int getFullCardType(int slot) {
        String cardType;
        if (slot == 0) {
            Rlog.d(RILJ_LOG_TAG, "getFullCardType slot0");
            cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[0]);
        } else if (slot == 1) {
            Rlog.d(RILJ_LOG_TAG, "getFullCardType slot1");
            cardType = SystemProperties.get(PROPERTY_RIL_FULL_UICC_TYPE[1]);
        } else {
            Rlog.d(RILJ_LOG_TAG, "getFullCardType invalid slotId = " + slot);
            return 0;
        }
        Rlog.d(RILJ_LOG_TAG, "getFullCardType=" + cardType);
        String appType[] = cardType.split(",");
        int fullType = 0;
        for (int i = 0; i < appType.length; i++) {
            if ("USIM".equals(appType[i])) {
                fullType = fullType | CARD_TYPE_USIM;
            } else if ("SIM".equals(appType[i])) {
                fullType = fullType | CARD_TYPE_SIM;
            } else if ("CSIM".equals(appType[i])) {
                fullType = fullType | CARD_TYPE_CSIM;
            } else if ("RUIM".equals(appType[i])) {
                fullType = fullType | CARD_TYPE_RUIM;
            }
        }
        Rlog.d(RILJ_LOG_TAG, "fullType=" + fullType);
        return fullType;
    }

    /**
     * Set the xTK mode.
     * @param mode The xTK mode.
     */
    public void setStkSwitchMode(int mode) { // Called by SvlteRatController
        if (RILJ_LOGD) {
            riljLog("setStkSwitchMode=" + mode + " old value=" + mStkSwitchMode);
        }
        mStkSwitchMode = mode;
    }

    /**
     * Set the UTK Bip Ps type .
     * @param mBipPsType The Bip type.
     */
    public void setBipPsType(int type) { // Called by SvltePhoneProxy
        if (RILJ_LOGD) {
            riljLog("setBipPsType=" + type + " old value=" + mBipPsType);
        }
        mBipPsType = type;
    }
    // M: [C2K] SVLTE Remote SIM Access end.

    /**
     * Switch antenna.
     * @param callState call state, 0 means call disconnected and 1 means call established.
     * @param ratMode RAT mode, 0 means GSM and 7 means C2K.
     */
    @Override
    public void switchAntenna(int callState, int ratMode) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SWITCH_ANTENNA, null);
        rr.mParcel.writeInt(2);
        rr.mParcel.writeInt(callState);
        rr.mParcel.writeInt(ratMode);

        if (RILJ_LOGD) {
            riljLog(rr.serialString()
                + "> " + requestToString(rr.mRequest) + " callState: " + callState
                + ", ratMode:" + ratMode);
        }

        send(rr);
    }

    /**
     * Switch RUIM card to SIM or switch SIM to RUIM.
     * @param cardtype that to be switched.
     */
    public void switchCardType(int cardtype) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SWITCH_CARD_TYPE, null);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(cardtype);
        if (RILJ_LOGD) {
            riljLog(rr.serialString()
                + "> " + requestToString(rr.mRequest) + " cardtype: " + cardtype);
        }
        send(rr);
    }

    /**
     * Enable or disable MD3 Sleep.
     * @param enable MD3 sleep.
     */
    public void enableMd3Sleep(int enable) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENABLE_MD3_SLEEP, null);
        rr.mParcel.writeInt(1);
        rr.mParcel.writeInt(enable);
        if (RILJ_LOGD) {
            riljLog(rr.serialString()
                + "> " + requestToString(rr.mRequest) + " enable MD3 sleep: " + enable);
        }
        send(rr);
    }
}
