/*
 * Copyright (C) 2006 The Android Open Source Project
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

import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.uicc.IccCardStatus;

import com.mediatek.internal.telephony.FemtoCellInfo;
import com.mediatek.internal.telephony.SrvccCallContext;

import android.os.AsyncResult;
import android.os.Message;
import android.os.Handler;


/**
 * {@hide}
 */
public interface CommandsInterface {
    enum RadioState {
        RADIO_OFF,         /* Radio explicitly powered off (eg CFUN=0) */
        RADIO_UNAVAILABLE, /* Radio unavailable (eg, resetting or not booted) */
        RADIO_ON;          /* Radio is on */

        public boolean isOn() /* and available...*/ {
            return this == RADIO_ON;
        }

        public boolean isAvailable() {
            return this != RADIO_UNAVAILABLE;
        }
    }

    //***** Constants

    // Used as parameter to dial() and setCLIR() below
    static final int CLIR_DEFAULT = 0;      // "use subscription default value"
    static final int CLIR_INVOCATION = 1;   // (restrict CLI presentation)
    static final int CLIR_SUPPRESSION = 2;  // (allow CLI presentation)


    // Used as parameters for call forward methods below
    static final int CF_ACTION_DISABLE          = 0;
    static final int CF_ACTION_ENABLE           = 1;
//  static final int CF_ACTION_UNUSED           = 2;
    static final int CF_ACTION_REGISTRATION     = 3;
    static final int CF_ACTION_ERASURE          = 4;

    static final int CF_REASON_UNCONDITIONAL    = 0;
    static final int CF_REASON_BUSY             = 1;
    static final int CF_REASON_NO_REPLY         = 2;
    static final int CF_REASON_NOT_REACHABLE    = 3;
    static final int CF_REASON_ALL              = 4;
    static final int CF_REASON_ALL_CONDITIONAL  = 5;
    //MTK-START [MMTelSS]
    static final int CF_REASON_NOT_REGISTERED   = 6;
    //MTK-END [MMTelSS]

    // Used for call barring methods below
    static final String CB_FACILITY_BAOC         = "AO";
    static final String CB_FACILITY_BAOIC        = "OI";
    static final String CB_FACILITY_BAOICxH      = "OX";
    static final String CB_FACILITY_BAIC         = "AI";
    static final String CB_FACILITY_BAICr        = "IR";
    static final String CB_FACILITY_BA_ALL       = "AB";
    static final String CB_FACILITY_BA_MO        = "AG";
    static final String CB_FACILITY_BA_MT        = "AC";
    static final String CB_FACILITY_BA_SIM       = "SC";
    static final String CB_FACILITY_BA_FD        = "FD";


    // Used for various supp services apis
    // See 27.007 +CCFC or +CLCK
    static final int SERVICE_CLASS_NONE     = 0; // no user input
    static final int SERVICE_CLASS_VOICE    = (1 << 0);
    static final int SERVICE_CLASS_DATA     = (1 << 1); //synonym for 16+32+64+128
    static final int SERVICE_CLASS_FAX      = (1 << 2);
    static final int SERVICE_CLASS_SMS      = (1 << 3);
    static final int SERVICE_CLASS_DATA_SYNC = (1 << 4);
    static final int SERVICE_CLASS_DATA_ASYNC = (1 << 5);
    static final int SERVICE_CLASS_PACKET   = (1 << 6);
    static final int SERVICE_CLASS_PAD      = (1 << 7);
    // MTK
    static final int SERVICE_CLASS_MAX      = (1 << 9); // Max SERVICE_CLASS value
    /* M: SS part */
    //MTK-START [mtk04070][111118][ALPS00093395]MTK added
    static final int SERVICE_CLASS_LINE2    = (1 << 8); // Add for Line2
    /**
     * SERVICE_CLASS_VIDEO Service Supplementary Information codes for Video Telephony support.
     */
    static final int SERVICE_CLASS_VIDEO    = (1 << 9);
    //MTK-END [mtk04070][111118][ALPS00093395]MTK added
    /* M: SS part end */

    // Numeric representation of string values returned
    // by messages sent to setOnUSSD handler
    static final int USSD_MODE_NOTIFY        = 0;
    static final int USSD_MODE_REQUEST       = 1;
    static final int USSD_MODE_NW_RELEASE    = 2;
    static final int USSD_MODE_LOCAL_CLIENT  = 3;
    static final int USSD_MODE_NOT_SUPPORTED = 4;
    static final int USSD_MODE_NW_TIMEOUT    = 5;
    /* M: SS part */
    //MTK-START [mtk04070][111118][ALPS00093395]MTK added
    static final int USSD_SESSION_END               = 2;
    static final int USSD_HANDLED_BY_STK            = 3;
    static final int USSD_OPERATION_NOT_SUPPORTED   = 4;
    static final int USSD_NETWORK_TIMEOUT           = 5;
    //MTK-END [mtk04070][111118][ALPS00093395]MTK added
    /* M: SS part end */

    // GSM SMS fail cause for acknowledgeLastIncomingSMS. From TS 23.040, 9.2.3.22.
    static final int GSM_SMS_FAIL_CAUSE_MEMORY_CAPACITY_EXCEEDED    = 0xD3;
    static final int GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY       = 0xD4;
    static final int GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR    = 0xD5;
    static final int GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR           = 0xFF;

    // CDMA SMS fail cause for acknowledgeLastIncomingCdmaSms.  From TS N.S0005, 6.5.2.125.
    static final int CDMA_SMS_FAIL_CAUSE_INVALID_TELESERVICE_ID     = 4;
    static final int CDMA_SMS_FAIL_CAUSE_RESOURCE_SHORTAGE          = 35;
    static final int CDMA_SMS_FAIL_CAUSE_OTHER_TERMINAL_PROBLEM     = 39;
    static final int CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM           = 96;

    // MTK
    //MTK-START [mtk04070][111118][ALPS00093395]MTK added
    //MTK AT CMD +ESMLCK
    static final int CAT_NETWOEK                = 0;
    static final int CAT_NETOWRK_SUBSET         = 1;
    static final int CAT_SERVICE_PROVIDER       = 2;
    static final int CAT_CORPORATE              = 3;
    static final int CAT_SIM                    = 4;

    static final int OP_UNLOCK                  = 0;
    static final int OP_LOCK                    = 1;
    static final int OP_ADD                     = 2;
    static final int OP_REMOVE                  = 3;
    static final int OP_PERMANENT_UNLOCK        = 4;
    //MTK-END [mtk04070][111118][ALPS00093395]MTK added

    // UTK start
    // Command Qualifier values for refresh command
    static final int REFRESH_NAA_INIT_AND_FULL_FILE_CHANGE  = 0x00;
    static final int REFRESH_NAA_FILE_CHANGE                = 0x01;
    static final int REFRESH_NAA_INIT_AND_FILE_CHANGE       = 0x02;
    static final int REFRESH_NAA_INIT                       = 0x03;
    static final int REFRESH_UICC_RESET                     = 0x04;

    // Qualifier values for UTK Refresh command
    static final int UTK_REFRESH_SMS = 0;
    static final int UTK_REFRESH_PHB = 1;
    static final int UTK_REFRESH_SYS = 2;
    //UTKE end

    //***** Methods
    RadioState getRadioState();

    /**
     * response.obj.result is an int[2]
     *
     * response.obj.result[0] is IMS registration state
     *                        0 - Not registered
     *                        1 - Registered
     * response.obj.result[1] is of type RILConstants.GSM_PHONE or
     *                                    RILConstants.CDMA_PHONE
     */
    void getImsRegistrationState(Message result);

    /**
     * Fires on any RadioState transition
     * Always fires immediately as well
     *
     * do not attempt to calculate transitions by storing getRadioState() values
     * on previous invocations of this notification. Instead, use the other
     * registration methods
     */
    void registerForRadioStateChanged(Handler h, int what, Object obj);
    void unregisterForRadioStateChanged(Handler h);

    void registerForVoiceRadioTechChanged(Handler h, int what, Object obj);
    void unregisterForVoiceRadioTechChanged(Handler h);
    void registerForImsNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForImsNetworkStateChanged(Handler h);

    /**
     * Fires on any transition into RadioState.isOn()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForOn(Handler h, int what, Object obj);
    void unregisterForOn(Handler h);

    /**
     * Fires on any transition out of RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForAvailable(Handler h, int what, Object obj);
    void unregisterForAvailable(Handler h);

    /**
     * Fires on any transition into !RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForNotAvailable(Handler h, int what, Object obj);
    void unregisterForNotAvailable(Handler h);

    /**
     * Fires on any transition into RADIO_OFF or !RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForOffOrNotAvailable(Handler h, int what, Object obj);
    void unregisterForOffOrNotAvailable(Handler h);

    /**
     * Fires on any change in ICC status
     */
    void registerForIccStatusChanged(Handler h, int what, Object obj);
    void unregisterForIccStatusChanged(Handler h);

    void registerForCallStateChanged(Handler h, int what, Object obj);
    void unregisterForCallStateChanged(Handler h);
    void registerForVoiceNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForVoiceNetworkStateChanged(Handler h);
    void registerForDataNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForDataNetworkStateChanged(Handler h);

    /** InCall voice privacy notifications */
    void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj);
    void unregisterForInCallVoicePrivacyOn(Handler h);
    void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj);
    void unregisterForInCallVoicePrivacyOff(Handler h);

    /** Single Radio Voice Call State progress notifications */
    void registerForSrvccStateChanged(Handler h, int what, Object obj);
    void unregisterForSrvccStateChanged(Handler h);

    /**
     * Handlers for subscription status change indications.
     *
     * @param h Handler for subscription status change messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSubscriptionStatusChanged(Handler h, int what, Object obj);
    void unregisterForSubscriptionStatusChanged(Handler h);

    /**
     * fires on any change in hardware configuration.
     */
    void registerForHardwareConfigChanged(Handler h, int what, Object obj);
    void unregisterForHardwareConfigChanged(Handler h);

    /**
     * unlike the register* methods, there's only one new 3GPP format SMS handler.
     * if you need to unregister, you should also tell the radio to stop
     * sending SMS's to you (via AT+CNMI)
     *
     * AsyncResult.result is a String containing the SMS PDU
     */
    void setOnNewGsmSms(Handler h, int what, Object obj);
    void unSetOnNewGsmSms(Handler h);

    /**
     * unlike the register* methods, there's only one new 3GPP2 format SMS handler.
     * if you need to unregister, you should also tell the radio to stop
     * sending SMS's to you (via AT+CNMI)
     *
     * AsyncResult.result is a String containing the SMS PDU
     */
    void setOnNewCdmaSms(Handler h, int what, Object obj);
    void unSetOnNewCdmaSms(Handler h);

    /**
     * Set the handler for SMS Cell Broadcast messages.
     *
     * AsyncResult.result is a byte array containing the SMS-CB PDU
     */
    void setOnNewGsmBroadcastSms(Handler h, int what, Object obj);
    void unSetOnNewGsmBroadcastSms(Handler h);

    /**
     * Register for NEW_SMS_ON_SIM unsolicited message
     *
     * AsyncResult.result is an int array containing the index of new SMS
     */
    void setOnSmsOnSim(Handler h, int what, Object obj);
    void unSetOnSmsOnSim(Handler h);

    /**
     * Register for NEW_SMS_STATUS_REPORT unsolicited message
     *
     * AsyncResult.result is a String containing the status report PDU
     */
    void setOnSmsStatus(Handler h, int what, Object obj);
    void unSetOnSmsStatus(Handler h);

    /**
     * unlike the register* methods, there's only one NITZ time handler
     *
     * AsyncResult.result is an Object[]
     * ((Object[])AsyncResult.result)[0] is a String containing the NITZ time string
     * ((Object[])AsyncResult.result)[1] is a Long containing the milliseconds since boot as
     *                                   returned by elapsedRealtime() when this NITZ time
     *                                   was posted.
     *
     * Please note that the delivery of this message may be delayed several
     * seconds on system startup
     */
    void setOnNITZTime(Handler h, int what, Object obj);
    void unSetOnNITZTime(Handler h);

    /**
     * unlike the register* methods, there's only one USSD notify handler
     *
     * Represents the arrival of a USSD "notify" message, which may
     * or may not have been triggered by a previous USSD send
     *
     * AsyncResult.result is a String[]
     * ((String[])(AsyncResult.result))[0] contains status code
     *      "0"   USSD-Notify -- text in ((const char **)data)[1]
     *      "1"   USSD-Request -- text in ((const char **)data)[1]
     *      "2"   Session terminated by network
     *      "3"   other local client (eg, SIM Toolkit) has responded
     *      "4"   Operation not supported
     *      "5"   Network timeout
     *
     * ((String[])(AsyncResult.result))[1] contains the USSD message
     * The numeric representations of these are in USSD_MODE_*
     */

    void setOnUSSD(Handler h, int what, Object obj);
    void unSetOnUSSD(Handler h);

    /**
     * unlike the register* methods, there's only one signal strength handler
     * AsyncResult.result is an int[2]
     * response.obj.result[0] is received signal strength (0-31, 99)
     * response.obj.result[1] is  bit error rate (0-7, 99)
     * as defined in TS 27.007 8.5
     */

    void setOnSignalStrengthUpdate(Handler h, int what, Object obj);
    void unSetOnSignalStrengthUpdate(Handler h);

    /**
     * Sets the handler for SIM/RUIM SMS storage full unsolicited message.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnIccSmsFull(Handler h, int what, Object obj);
    void unSetOnIccSmsFull(Handler h);

    /**
     * Sets the handler for SIM Refresh notifications.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForIccRefresh(Handler h, int what, Object obj);
    void unregisterForIccRefresh(Handler h);

    void setOnIccRefresh(Handler h, int what, Object obj);
    void unsetOnIccRefresh(Handler h);

    /**
     * Sets the handler for RING notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCallRing(Handler h, int what, Object obj);
    void unSetOnCallRing(Handler h);

    /**
     * Sets the handler for RESTRICTED_STATE changed notification,
     * eg, for Domain Specific Access Control
     * unlike the register* methods, there's only one signal strength handler
     *
     * AsyncResult.result is an int[1]
     * response.obj.result[0] is a bitmask of RIL_RESTRICTED_STATE_* values
     */

    void setOnRestrictedStateChanged(Handler h, int what, Object obj);
    void unSetOnRestrictedStateChanged(Handler h);

    /**
     * Sets the handler for Supplementary Service Notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnSuppServiceNotification(Handler h, int what, Object obj);
    void unSetOnSuppServiceNotification(Handler h);

    /**
     * Sets the handler for Session End Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatSessionEnd(Handler h, int what, Object obj);
    void unSetOnCatSessionEnd(Handler h);

    /**
     * Sets the handler for Proactive Commands for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatProactiveCmd(Handler h, int what, Object obj);
    void unSetOnCatProactiveCmd(Handler h);

    /**
     * Sets the handler for Event Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatEvent(Handler h, int what, Object obj);
    void unSetOnCatEvent(Handler h);

    /**
     * Sets the handler for Call Set Up Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatCallSetUp(Handler h, int what, Object obj);
    void unSetOnCatCallSetUp(Handler h);

    /**
     * Enables/disbables supplementary service related notifications from
     * the network.
     *
     * @param enable true to enable notifications, false to disable.
     * @param result Message to be posted when command completes.
     */
    void setSuppServiceNotifications(boolean enable, Message result);
    //void unSetSuppServiceNotifications(Handler h);

    /**
     * Sets the handler for Alpha Notification during STK Call Control.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatCcAlphaNotify(Handler h, int what, Object obj);
    void unSetOnCatCcAlphaNotify(Handler h);

    /**
     * Sets the handler for notifying Suplementary Services (SS)
     * Data during STK Call Control.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnSs(Handler h, int what, Object obj);
    void unSetOnSs(Handler h);

    /**
     * Sets the handler for Event Notifications for CDMA Display Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForDisplayInfo(Handler h, int what, Object obj);
    void unregisterForDisplayInfo(Handler h);

    /**
     * Sets the handler for Event Notifications for CallWaiting Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForCallWaitingInfo(Handler h, int what, Object obj);
    void unregisterForCallWaitingInfo(Handler h);

    /**
     * Sets the handler for Event Notifications for Signal Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSignalInfo(Handler h, int what, Object obj);
    void unregisterForSignalInfo(Handler h);

    /**
     * Registers the handler for CDMA number information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForNumberInfo(Handler h, int what, Object obj);
    void unregisterForNumberInfo(Handler h);

    /**
     * Registers the handler for CDMA redirected number Information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForRedirectedNumberInfo(Handler h, int what, Object obj);
    void unregisterForRedirectedNumberInfo(Handler h);

    /**
     * Registers the handler for CDMA line control information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForLineControlInfo(Handler h, int what, Object obj);
    void unregisterForLineControlInfo(Handler h);

    /**
     * Registers the handler for CDMA T53 CLIR information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerFoT53ClirlInfo(Handler h, int what, Object obj);
    void unregisterForT53ClirInfo(Handler h);

    /**
     * Registers the handler for CDMA T53 audio control information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForT53AudioControlInfo(Handler h, int what, Object obj);
    void unregisterForT53AudioControlInfo(Handler h);

    /**
     * Fires on if Modem enters Emergency Callback mode
     */
    void setEmergencyCallbackMode(Handler h, int what, Object obj);

     /**
      * Fires on any CDMA OTA provision status change
      */
     void registerForCdmaOtaProvision(Handler h,int what, Object obj);
     void unregisterForCdmaOtaProvision(Handler h);

     /**
      * Registers the handler when out-band ringback tone is needed.<p>
      *
      *  Messages received from this:
      *  Message.obj will be an AsyncResult
      *  AsyncResult.userObj = obj
      *  AsyncResult.result = boolean. <p>
      */
     void registerForRingbackTone(Handler h, int what, Object obj);
     void unregisterForRingbackTone(Handler h);

     /**
      * Registers the handler when mute/unmute need to be resent to get
      * uplink audio during a call.<p>
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForResendIncallMute(Handler h, int what, Object obj);
     void unregisterForResendIncallMute(Handler h);

     /**
      * Registers the handler for when Cdma subscription changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj);
     void unregisterForCdmaSubscriptionChanged(Handler h);

     /**
      * Registers the handler for when Cdma prl changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForCdmaPrlChanged(Handler h, int what, Object obj);
     void unregisterForCdmaPrlChanged(Handler h);

     /**
      * Registers the handler for when Cdma prl changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj);
     void unregisterForExitEmergencyCallbackMode(Handler h);

     /**
      * Registers the handler for RIL_UNSOL_RIL_CONNECT events.
      *
      * When ril connects or disconnects a message is sent to the registrant
      * which contains an AsyncResult, ar, in msg.obj. The ar.result is an
      * Integer which is the version of the ril or -1 if the ril disconnected.
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      */
     void registerForRilConnected(Handler h, int what, Object obj);
     void unregisterForRilConnected(Handler h);

    /**
     * Supply the ICC PIN to the ICC card
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin(String pin, Message result);

    /**
     * Supply the PIN for the app with this AID on the ICC card
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPinForApp(String pin, String aid, Message result);

    /**
     * Supply the ICC PUK and newPin to the ICC card
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk(String puk, String newPin, Message result);

    /**
     * Supply the PUK, new pin for the app with this AID on the ICC card
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPukForApp(String puk, String newPin, String aid, Message result);

    /**
     * Supply the ICC PIN2 to the ICC card
     * Only called following operation where ICC_PIN2 was
     * returned as a a failure from a previous operation
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin2(String pin2, Message result);

    /**
     * Supply the PIN2 for the app with this AID on the ICC card
     * Only called following operation where ICC_PIN2 was
     * returned as a a failure from a previous operation
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin2ForApp(String pin2, String aid, Message result);

    /**
     * Supply the SIM PUK2 to the SIM card
     * Only called following operation where SIM_PUK2 was
     * returned as a a failure from a previous operation
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk2(String puk2, String newPin2, Message result);

    /**
     * Supply the PUK2, newPin2 for the app with this AID on the ICC card
     * Only called following operation where SIM_PUK2 was
     * returned as a a failure from a previous operation
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result);

    // TODO: Add java doc and indicate that msg.arg1 contains the number of attempts remaining.
    void changeIccPin(String oldPin, String newPin, Message result);
    void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result);
    void changeIccPin2(String oldPin2, String newPin2, Message result);
    void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result);

    void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result);

    void supplyNetworkDepersonalization(String netpin, String type, Message result);
    // MTK
    void supplyNetworkDepersonalization(String netpin, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DriverCall
     *      The ar.result List is sorted by DriverCall.index
     */
    void getCurrentCalls (Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DataCallResponse
     *  @deprecated Do not use.
     */
    @Deprecated
    void getPDPContextList(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DataCallResponse
     */
    void getDataCallList(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    void dial (String address, int clirMode, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    void dial(String address, int clirMode, UUSInfo uusInfo, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMSI on success
     */
    void getIMSI(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMSI on success
     */
    void getIMSIForApp(String aid, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMEI on success
     */
    void getIMEI(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMEISV on success
     */
    void getIMEISV(Message result);

    /**
     * Hang up one individual connection.
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     *  3GPP 22.030 6.5.5
     *  "Releases a specific active call X"
     */
    void hangupConnection (int gsmIndex, Message result);

    /**
     * 3GPP 22.030 6.5.5
     *  "Releases all held calls or sets User Determined User Busy (UDUB)
     *   for a waiting call."
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void hangupWaitingOrBackground (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Releases all active calls (if any exist) and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void hangupForegroundResumeBackground (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls (if any exist) on hold and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void switchWaitingOrHoldingAndActive (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Adds a held call to the conversation"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void conference (Message result);

    /**
     * Set preferred Voice Privacy (VP).
     *
     * @param enable true is enhanced and false is normal VP
     * @param result is a callback message
     */
    void setPreferredVoicePrivacy(boolean enable, Message result);

    /**
     * Get currently set preferred Voice Privacy (VP) mode.
     *
     * @param result is a callback message
     */
    void getPreferredVoicePrivacy(Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls on hold except call X with which
     *  communication shall be supported."
     */
    void separateConnection (int gsmIndex, Message result);

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void acceptCall (Message result);

    /**
     *  also known as UDUB
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void rejectCall (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Connects the two calls and disconnects the subscriber from both calls"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void explicitCallTransfer (Message result);

    /**
     * cause code returned as int[0] in Message.obj.response
     * Returns integer cause code defined in TS 24.008
     * Annex H or closest approximation.
     * Most significant codes:
     * - Any defined in 22.001 F.4 (for generating busy/congestion)
     * - Cause 68: ACM >= ACMMax
     */
    void getLastCallFailCause (Message result);


    /**
     * Reason for last PDP context deactivate or failure to activate
     * cause code returned as int[0] in Message.obj.response
     * returns an integer cause code defined in TS 24.008
     * section 6.1.3.1.3 or close approximation
     * @deprecated Do not use.
     */
    @Deprecated
    void getLastPdpFailCause (Message result);

    /**
     * The preferred new alternative to getLastPdpFailCause
     * that is also CDMA-compatible.
     */
    void getLastDataCallFailCause (Message result);

    void setMute (boolean enableMute, Message response);

    void getMute (Message response);

    /**
     * response.obj is an AsyncResult
     * response.obj.result is an int[2]
     * response.obj.result[0] is received signal strength (0-31, 99)
     * response.obj.result[1] is  bit error rate (0-7, 99)
     * as defined in TS 27.007 8.5
     */
    void getSignalStrength (Message response);


    /**
     * response.obj.result is an int[3]
     * response.obj.result[0] is registration state 0-5 from TS 27.007 7.2
     * response.obj.result[1] is LAC if registered or -1 if not
     * response.obj.result[2] is CID if registered or -1 if not
     * valid LAC and CIDs are 0x0000 - 0xffff
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    void getVoiceRegistrationState (Message response);

    /**
     * response.obj.result is an int[3]
     * response.obj.result[0] is registration state 0-5 from TS 27.007 7.2
     * response.obj.result[1] is LAC if registered or -1 if not
     * response.obj.result[2] is CID if registered or -1 if not
     * valid LAC and CIDs are 0x0000 - 0xffff
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    void getDataRegistrationState (Message response);

    /**
     * response.obj.result is a String[3]
     * response.obj.result[0] is long alpha or null if unregistered
     * response.obj.result[1] is short alpha or null if unregistered
     * response.obj.result[2] is numeric or null if unregistered
     */
    void getOperator(Message response);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void sendDtmf(char c, Message result);


    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void startDtmf(char c, Message result);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void stopDtmf(Message result);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void sendBurstDtmf(String dtmfString, int on, int off, Message result);

    /**
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    void sendSMS (String smscPDU, String pdu, Message response);

    /**
     * Send an SMS message, Identical to sendSMS,
     * except that more messages are expected to be sent soon
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    void sendSMSExpectMore (String smscPDU, String pdu, Message response);

    /**
     * @param pdu is CDMA-SMS in internal pseudo-PDU format
     * @param response sent when operation completes
     */
    void sendCdmaSms(byte[] pdu, Message response);

    /**
     * send SMS over IMS with 3GPP/GSM SMS format
     * @param smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * @param pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     * @param retry indicates if this is a retry; 0 == not retry, nonzero = retry
     * @param messageRef valid field if retry is set to nonzero.
     *        Contains messageRef from RIL_SMS_Response corresponding to failed MO SMS
     * @param response sent when operation completes
     */
    void sendImsGsmSms (String smscPDU, String pdu, int retry, int messageRef,
            Message response);

    /**
     * send SMS over IMS with 3GPP2/CDMA SMS format
     * @param pdu is CDMA-SMS in internal pseudo-PDU format
     * @param response sent when operation completes
     * @param retry indicates if this is a retry; 0 == not retry, nonzero = retry
     * @param messageRef valid field if retry is set to nonzero.
     *        Contains messageRef from RIL_SMS_Response corresponding to failed MO SMS
     * @param response sent when operation completes
     */
    void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response);

    /**
     * Deletes the specified SMS record from SIM memory (EF_SMS).
     *
     * @param index index of the SMS record to delete
     * @param response sent when operation completes
     */
    void deleteSmsOnSim(int index, Message response);

    /**
     * Deletes the specified SMS record from RUIM memory (EF_SMS in DF_CDMA).
     *
     * @param index index of the SMS record to delete
     * @param response sent when operation completes
     */
    void deleteSmsOnRuim(int index, Message response);

    /**
     * Writes an SMS message to SIM memory (EF_SMS).
     *
     * @param status status of message on SIM.  One of:
     *                  SmsManger.STATUS_ON_ICC_READ
     *                  SmsManger.STATUS_ON_ICC_UNREAD
     *                  SmsManger.STATUS_ON_ICC_SENT
     *                  SmsManger.STATUS_ON_ICC_UNSENT
     * @param pdu message PDU, as hex string
     * @param response sent when operation completes.
     *                  response.obj will be an AsyncResult, and will indicate
     *                  any error that may have occurred (eg, out of memory).
     */
    void writeSmsToSim(int status, String smsc, String pdu, Message response);

    void writeSmsToRuim(int status, String pdu, Message response);

    void setRadioPower(boolean on, Message response);

    void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message response);

    void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response);

    /**
     * Acknowledge successful or failed receipt of last incoming SMS,
     * including acknowledgement TPDU to send as the RP-User-Data element
     * of the RP-ACK or RP-ERROR PDU.
     *
     * @param success true to send RP-ACK, false to send RP-ERROR
     * @param ackPdu the acknowledgement TPDU in hexadecimal format
     * @param response sent when operation completes.
     */
    void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response);

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.result will be an IccIoResult on success
     */
    void iccIO (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message response);

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.userObj will be a IccIoResult on success
     */
    void iccIOForApp (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, String aid, Message response);

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 1 for "CLIP is provisioned", and 0 for "CLIP is not provisioned".
     *
     * @param response is callback message
     */

    void queryCLIP(Message response);

    /**
     * response.obj will be a an int[2]
     *
     * response.obj[0] will be TS 27.007 +CLIR parameter 'n'
     *  0 presentation indicator is used according to the subscription of the CLIR service
     *  1 CLIR invocation
     *  2 CLIR suppression
     *
     * response.obj[1] will be TS 27.007 +CLIR parameter 'm'
     *  0 CLIR not provisioned
     *  1 CLIR provisioned in permanent mode
     *  2 unknown (e.g. no network, etc.)
     *  3 CLIR temporary mode presentation restricted
     *  4 CLIR temporary mode presentation allowed
     */

    void getCLIR(Message response);

    /**
     * clirMode is one of the CLIR_* constants above
     *
     * response.obj is null
     */

    void setCLIR(int clirMode, Message response);

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 0 for disabled, 1 for enabled.
     *
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void queryCallWaiting(int serviceClass, Message response);

    /**
     * @param enable is true to enable, false to disable
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void setCallWaiting(boolean enable, int serviceClass, Message response);

    /**
     * @param action is one of CF_ACTION_*
     * @param cfReason is one of CF_REASON_*
     * @param serviceClass is a sum of SERVICE_CLASSS_*
     */
    void setCallForward(int action, int cfReason, int serviceClass,
                String number, int timeSeconds, Message response);

    /**
     * cfReason is one of CF_REASON_*
     *
     * ((AsyncResult)response.obj).result will be an array of
     * CallForwardInfo's
     *
     * An array of length 0 means "disabled for all codes"
     */
    void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message response);

    void setNetworkSelectionModeAutomatic(Message response);

    void setNetworkSelectionModeManual(String operatorNumeric, Message response);

    /**
     * Queries whether the current network selection mode is automatic
     * or manual
     *
     * ((AsyncResult)response.obj).result  is an int[] with element [0] being
     * a 0 for automatic selection and a 1 for manual selection
     */

    void getNetworkSelectionMode(Message response);

    /**
     * Queries the currently available networks
     *
     * ((AsyncResult)response.obj).result  is a List of NetworkInfo objects
     */
    void getAvailableNetworks(Message response);

    void getBasebandVersion (Message response);


    /**
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled service classes (sum of SERVICE_CLASS_*)
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void queryFacilityLock (String facility, String password, int serviceClass,
        Message response);

    /**
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled service classes (sum of SERVICE_CLASS_*) for the
     * application with appId.
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param appId is application Id or null if none
     * @param response is callback message
     */

    void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId,
        Message response);

    /**
     * @param facility one of CB_FACILTY_*
     * @param lockState true means lock, false means unlock
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */
    void setFacilityLock (String facility, boolean lockState, String password,
        int serviceClass, Message response);

    /**
     * Set the facility lock for the app with this AID on the ICC card.
     *
     * @param facility one of CB_FACILTY_*
     * @param lockState true means lock, false means unlock
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param appId is application Id or null if none
     * @param response is callback message
     */
    void setFacilityLockForApp(String facility, boolean lockState, String password,
        int serviceClass, String appId, Message response);

    void sendUSSD (String ussdString, Message response);

    /**
     * Cancels a pending USSD session if one exists.
     * @param response callback message
     */
    void cancelPendingUssd (Message response);

    void resetRadio(Message result);

    /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param response is callback message
     */
    void setBandMode (int bandMode, Message response);

    /**
     * Query the list of band mode supported by RF.
     *
     * @param response is callback message
     *        ((AsyncResult)response.obj).result  is an int[] where int[0] is
     *        the size of the array and the rest of each element representing
     *        one available BM_*_BAND
     */
    void queryAvailableBandMode (Message response);

    /**
     *  Requests to set the preferred network type for searching and registering
     * (CS/PS domain, RAT, and operation mode)
     * @param networkType one of  NT_*_TYPE
     * @param response is callback message
     */
    void setPreferredNetworkType(int networkType , Message response);

     /**
     *  Query the preferred network type setting
     *
     * @param response is callback message to report one of  NT_*_TYPE
     */
    void getPreferredNetworkType(Message response);

    /**
     * Query neighboring cell ids
     *
     * @param response s callback message to cell ids
     */
    void getNeighboringCids(Message response);

    /**
     * Request to enable/disable network state change notifications when
     * location information (lac and/or cid) has changed.
     *
     * @param enable true to enable, false to disable
     * @param response callback message
     */
    void setLocationUpdates(boolean enable, Message response);

    /**
     * Gets the default SMSC address.
     *
     * @param result Callback message contains the SMSC address.
     */
    void getSmscAddress(Message result);

    /**
     * Sets the default SMSC address.
     *
     * @param address new SMSC address
     * @param result Callback message is empty on completion
     */
    void setSmscAddress(String address, Message result);

    /**
     * Indicates whether there is storage available for new SMS messages.
     * @param available true if storage is available
     * @param result callback message
     */
    void reportSmsMemoryStatus(boolean available, Message result);

    /**
     * Indicates to the vendor ril that StkService is running
     * and is ready to receive RIL_UNSOL_STK_XXXX commands.
     *
     * @param result callback message
     */
    void reportStkServiceIsRunning(Message result);

    void invokeOemRilRequestRaw(byte[] data, Message response);

    void invokeOemRilRequestStrings(String[] strings, Message response);

    /**
     * Fires when RIL_UNSOL_OEM_HOOK_RAW is received from the RIL.
     */
    void setOnUnsolOemHookRaw(Handler h, int what, Object obj);
    void unSetOnUnsolOemHookRaw(Handler h);

    /**
     * Send TERMINAL RESPONSE to the SIM, after processing a proactive command
     * sent by the SIM.
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with first byte of response data. See
     *                  TS 102 223 for details.
     * @param response  Callback message
     */
    public void sendTerminalResponse(String contents, Message response);

    /**
     * Send ENVELOPE to the SIM, after processing a proactive command sent by
     * the SIM.
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void sendEnvelope(String contents, Message response);

    /**
     * Send ENVELOPE to the SIM, such as an SMS-PP data download envelope
     * for a SIM data download message. This method has one difference
     * from {@link #sendEnvelope}: The SW1 and SW2 status bytes from the UICC response
     * are returned along with the response data.
     *
     * response.obj will be an AsyncResult
     * response.obj.result will be an IccIoResult on success
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void sendEnvelopeWithStatus(String contents, Message response);

    /**
     * Accept or reject the call setup request from SIM.
     *
     * @param accept   true if the call is to be accepted, false otherwise.
     * @param response Callback message
     */
    public void handleCallSetupRequestFromSim(boolean accept, Message response);

    /**
     * Activate or deactivate cell broadcast SMS for GSM.
     *
     * @param activate
     *            true = activate, false = deactivate
     * @param result Callback message is empty on completion
     */
    public void setGsmBroadcastActivation(boolean activate, Message result);

    /**
     * Configure cell broadcast SMS for GSM.
     *
     * @param response Callback message is empty on completion
     */
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response);

    /**
     * Query the current configuration of cell broadcast SMS of GSM.
     *
     * @param response
     *        Callback message contains the configuration from the modem
     *        on completion
     */
    public void getGsmBroadcastConfig(Message response);

    //***** new Methods for CDMA support

    /**
     * Request the device ESN / MEID / IMEI / IMEISV.
     * "response" is const char **
     *   [0] is IMEI if GSM subscription is available
     *   [1] is IMEISV if GSM subscription is available
     *   [2] is ESN if CDMA subscription is available
     *   [3] is MEID if CDMA subscription is available
     */
    public void getDeviceIdentity(Message response);

    /**
     * Request the device MDN / H_SID / H_NID / MIN.
     * "response" is const char **
     *   [0] is MDN if CDMA subscription is available
     *   [1] is a comma separated list of H_SID (Home SID) in decimal format
     *       if CDMA subscription is available
     *   [2] is a comma separated list of H_NID (Home NID) in decimal format
     *       if CDMA subscription is available
     *   [3] is MIN (10 digits, MIN2+MIN1) if CDMA subscription is available
     */
    public void getCDMASubscription(Message response);

    /**
     * Send Flash Code.
     * "response" is is NULL
     *   [0] is a FLASH string
     */
    public void sendCDMAFeatureCode(String FeatureCode, Message response);

    /** Set the Phone type created */
    void setPhoneType(int phoneType);

    /**
     *  Query the CDMA roaming preference setting
     *
     * @param response is callback message to report one of  CDMA_RM_*
     */
    void queryCdmaRoamingPreference(Message response);

    /**
     *  Requests to set the CDMA roaming preference
     * @param cdmaRoamingType one of  CDMA_RM_*
     * @param response is callback message
     */
    void setCdmaRoamingPreference(int cdmaRoamingType, Message response);

    /**
     *  Requests to set the CDMA subscription mode
     * @param cdmaSubscriptionType one of  CDMA_SUBSCRIPTION_*
     * @param response is callback message
     */
    void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response);

    /**
     *  Requests to get the CDMA subscription srouce
     * @param response is callback message
     */
    void getCdmaSubscriptionSource(Message response);

    /**
     *  Set the TTY mode
     *
     * @param ttyMode one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    void setTTYMode(int ttyMode, Message response);

    /**
     *  Query the TTY mode
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * tty mode:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    void queryTTYMode(Message response);

    /**
     * Setup a packet data connection On successful completion, the result
     * message will return a {@link com.android.internal.telephony.dataconnection.DataCallResponse}
     * object containing the connection information.
     *
     * @param radioTechnology
     *            indicates whether to setup connection on radio technology CDMA
     *            (0) or GSM/UMTS (1)
     * @param profile
     *            Profile Number or NULL to indicate default profile
     * @param apn
     *            the APN to connect to if radio technology is GSM/UMTS.
     *            Otherwise null for CDMA.
     * @param user
     *            the username for APN, or NULL
     * @param password
     *            the password for APN, or NULL
     * @param authType
     *            the PAP / CHAP auth type. Values is one of SETUP_DATA_AUTH_*
     * @param protocol
     *            one of the PDP_type values in TS 27.007 section 10.1.1.
     *            For example, "IP", "IPV6", "IPV4V6", or "PPP".
     * @param result
     *            Callback message
     */
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, Message result);

    /**
     * Deactivate packet data connection
     *
     * @param cid
     *            The connection ID
     * @param reason
     *            Data disconnect reason.
     * @param result
     *            Callback message is empty on completion
     */
    public void deactivateDataCall(int cid, int reason, Message result);

    /**
     * Activate or deactivate cell broadcast SMS for CDMA.
     *
     * @param activate
     *            true = activate, false = deactivate
     * @param result
     *            Callback message is empty on completion
     */
    public void setCdmaBroadcastActivation(boolean activate, Message result);

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response
     *            Callback message is empty on completion
     */
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response);

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param result
     *            Callback message contains the configuration from the modem on completion
     */
    public void getCdmaBroadcastConfig(Message result);

    /**
     *  Requests the radio's system selection module to exit emergency callback mode.
     *  This function should only be called from CDMAPHone.java.
     *
     * @param response callback message
     */
    public void exitEmergencyCallbackMode(Message response);

    /**
     * Request the status of the ICC and UICC cards.
     *
     * @param result
     *          Callback message containing {@link IccCardStatus} structure for the card.
     */
    public void getIccCardStatus(Message result);

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     */
    public int getLteOnCdmaMode();

    /**
     * Return if the current radio is LTE on GSM
     * @hide
     */
    public int getLteOnGsmMode();

    /**
     * Request the ISIM application on the UICC to perform the AKA
     * challenge/response algorithm for IMS authentication. The nonce string
     * and challenge response are Base64 encoded Strings.
     *
     * @param nonce the nonce string to pass with the ISIM authentication request
     * @param response a callback message with the String response in the obj field
     * @deprecated
     * @see requestIccSimAuthentication
     */
    public void requestIsimAuthentication(String nonce, Message response);

    /**
     * Request the SIM application on the UICC to perform authentication
     * challenge/response algorithm. The data string and challenge response are
     * Base64 encoded Strings.
     * Can support EAP-SIM, EAP-AKA with results encoded per 3GPP TS 31.102.
     *
     * @param authContext is the P2 parameter that specifies the authentication context per 3GPP TS
     *                    31.102 (Section 7.1.2)
     * @param data authentication challenge data
     * @param aid used to determine which application/slot to send the auth command to. See ETSI
     *            102.221 8.1 and 101.220 4
     * @param response a callback message with the String response in the obj field
     */
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response);

    /**
     * Get the current Voice Radio Technology.
     *
     * AsyncResult.result is an int array with the first value
     * being one of the ServiceState.RIL_RADIO_TECHNOLOGY_xxx values.
     *
     * @param result is sent back to handler and result.obj is a AsyncResult
     */
    void getVoiceRadioTechnology(Message result);

    /**
     * Return the current set of CellInfo records
     *
     * AsyncResult.result is a of Collection<CellInfo>
     *
     * @param result is sent back to handler and result.obj is a AsyncResult
     */
    void getCellInfoList(Message result);

    /**
     * Sets the minimum time in milli-seconds between when RIL_UNSOL_CELL_INFO_LIST
     * should be invoked.
     *
     * The default, 0, means invoke RIL_UNSOL_CELL_INFO_LIST when any of the reported
     * information changes. Setting the value to INT_MAX(0x7fffffff) means never issue
     * A RIL_UNSOL_CELL_INFO_LIST.
     *
     *

     * @param rateInMillis is sent back to handler and result.obj is a AsyncResult
     * @param response.obj is AsyncResult ar when sent to associated handler
     *                        ar.exception carries exception on failure or null on success
     *                        otherwise the error.
     */
    void setCellInfoListRate(int rateInMillis, Message response);

    /**
     * Fires when RIL_UNSOL_CELL_INFO_LIST is received from the RIL.
     */
    void registerForCellInfoList(Handler h, int what, Object obj);
    void unregisterForCellInfoList(Handler h);

    /**
     * Set Initial Attach Apn
     *
     * @param apn
     *            the APN to connect to if radio technology is GSM/UMTS.
     * @param protocol
     *            one of the PDP_type values in TS 27.007 section 10.1.1.
     *            For example, "IP", "IPV6", "IPV4V6", or "PPP".
     * @param authType
     *            authentication protocol used for this PDP context
     *            (None: 0, PAP: 1, CHAP: 2, PAP&CHAP: 3)
     * @param username
     *            the username for APN, or NULL
     * @param password
     *            the password for APN, or NULL
     * @param result
     *            callback message contains the information of SUCCESS/FAILURE
     */
    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Message result);

    /**
     * Set data profiles in modem
     *
     * @param dps
     *            Array of the data profiles set to modem
     * @param result
     *            callback message contains the information of SUCCESS/FAILURE
     */
    public void setDataProfile(DataProfile[] dps, Message result);

    /**
     * Notifiy that we are testing an emergency call
     */
    public void testingEmergencyCall();

    /**
     * Open a logical channel to the SIM.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHO command.
     *
     * @param AID Application id. See ETSI 102.221 and 101.220.
     * @param response Callback message. response.obj will be an int [1] with
     *            element [0] set to the id of the logical channel.
     */
    public void iccOpenLogicalChannel(String AID, Message response);

    /**
     * Open a logical channel to the SIM.
     *
     * @param p2 P2 parameter
     * @param AID application id.
     * @param response Callback message. response.obj will be an int [1]
                element [0] set to the id of the logical channel.
     */
    public void iccOpenLogicalChannel(String AID, byte p2, Message response);

    /**
     * Close a previously opened logical channel to the SIM.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHC command.
     *
     * @param channel Channel id. Id of the channel to be closed.
     * @param response Callback message.
     */
    public void iccCloseLogicalChannel(int channel, Message response);

    /**
     * Exchange APDUs with the SIM on a logical channel.
     *
     * Input parameters equivalent to TS 27.007 AT+CGLA command.
     *
     * @param channel Channel id of the channel to use for communication. Has to
     *            be greater than zero.
     * @param cla Class of the APDU command.
     * @param instruction Instruction of the APDU command.
     * @param p1 P1 value of the APDU command.
     * @param p2 P2 value of the APDU command.
     * @param p3 P3 value of the APDU command. If p3 is negative a 4 byte APDU
     *            is sent to the SIM.
     * @param data Data to be sent with the APDU.
     * @param response Callback message. response.obj.userObj will be
     *            an IccIoResult on success.
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
            int p1, int p2, int p3, String data, Message response);

    /**
     * Exchange APDUs with the SIM on a basic channel.
     *
     * Input parameters equivalent to TS 27.007 AT+CSIM command.
     *
     * @param cla Class of the APDU command.
     * @param instruction Instruction of the APDU command.
     * @param p1 P1 value of the APDU command.
     * @param p2 P2 value of the APDU command.
     * @param p3 P3 value of the APDU command. If p3 is negative a 4 byte APDU
     *            is sent to the SIM.
     * @param data Data to be sent with the APDU.
     * @param response Callback message. response.obj.userObj will be
     *            an IccIoResult on success.
     */
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
            int p3, String data, Message response);

    /**
     * Get ATR (Answer To Reset; as per ISO/IEC 7816-4) from SIM card
     *
     * @param response Callback message
     */
    public void getAtr(Message response);

    /**
     * Read one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param response callback message with the String response in the obj field
     */
    void nvReadItem(int itemID, Message response);

    /**
     * Write one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param itemValue the value to write, as a String
     * @param response Callback message.
     */
    void nvWriteItem(int itemID, String itemValue, Message response);

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL
     * @param response Callback message.
     */
    void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response);

    /**
     * Perform the specified type of NV config reset. The radio will be taken offline
     * and the device must be rebooted after erasing the NV. Used for device
     * configuration by some CDMA operators.
     *
     * @param resetType reset type: 1: reload NV reset, 2: erase NV reset, 3: factory NV reset
     * @param response Callback message.
     */
    void nvResetConfig(int resetType, Message response);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of HardwareConfig
     */
    void getHardwareConfig (Message result);

    /**
     * @return version of the ril.
     */
    int getRilVersion();

   /**
     * Sets user selected subscription at Modem.
     *
     * @param appIndex
     *          Application index in the card.
     * @param activate
     *          Whether to activate or deactivate the subscription
     * @param result
     *          Callback message contains the information of SUCCESS/FAILURE.
     */
    // FIXME Update the doc and consider modifying the request to make more generic.
    public void setUiccSubscription(int appIndex, boolean activate, Message result);

    /**
     * Tells the modem if data is allowed or not.
     *
     * @param allowed
     *          true = allowed, false = not alowed
     * @param result
     *          Callback message contains the information of SUCCESS/FAILURE.
     */
    // FIXME We may need to pass AID and slotid also
    public void setDataAllowed(boolean allowed, Message result);

    /**
     * Inform RIL that the device is shutting down
     *
     * @param result Callback message contains the information of SUCCESS/FAILURE
     */
    public void requestShutdown(Message result);

    /**
     *  Set phone radio type and access technology.
     *
     *  @param rc the phone radio capability defined in
     *         RadioCapability. It's a input object used to transfer parameter to logic modem
     *
     *  @param result Callback message.
     */
    public void setRadioCapability(RadioCapability rc, Message result);

    /**
     *  Get phone radio capability
     *
     *  @param result Callback message.
     */
    public void getRadioCapability(Message result);

    /**
     * Registers the handler when phone radio capability is changed.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when phone radio capability is changed.
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForRadioCapabilityChanged(Handler h);

    /**
     * Start LCE (Link Capacity Estimation) service with a desired reporting interval.
     *
     * @param reportIntervalMs
     *        LCE info reporting interval (ms).
     *
     * @param result Callback message contains the current LCE status.
     * {byte status, int actualIntervalMs}
     */
    public void startLceService(int reportIntervalMs, boolean pullMode, Message result);

    /**
     * Stop LCE service.
     *
     * @param result Callback message contains the current LCE status:
     * {byte status, int actualIntervalMs}
     *
     */
    public void stopLceService(Message result);

    /**
     * Pull LCE service for capacity data.
     *
     * @param result Callback message contains the capacity info:
     * {int capacityKbps, byte confidenceLevel, byte lceSuspendedTemporarily}
     */
    public void pullLceData(Message result);

    /**
     * Register a LCE info listener.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForLceInfo(Handler h, int what, Object obj);

    /**
     * Unregister the LCE Info listener.
     *
     * @param h handle to be removed.
     */
    void unregisterForLceInfo(Handler h);

    /**
     *
     * Get modem activity info and stats
     *
     * @param result Callback message contains the modem activity information
     */
    public void getModemActivityInfo(Message result);

    /**
     *
     * Set MAX transmit power state
     *
     * @param response Callback message contains the status from modem
     */
     public void setMaxTransmitPower(int state, Message response);

    /**
     * Request to update the current local call hold state.
     * @param lchStatus, true if call is in lch state
     */
    public void setLocalCallHold(boolean lchStatus);

    /**
     * @hide
     * CM-specific: Ask the RIL about the presence of back-compat flags
     */
    public boolean needsOldRilFeature(String feature);

    /**
     * @hide
     * samsung stk service implementation - set up registrant for sending
     * sms send result from modem(RIL) to catService
     */
    void setOnCatSendSmsResult(Handler h, int what, Object obj);

    /**
     * @hide
     */
    void unSetOnCatSendSmsResult(Handler h);

    // MTK
    // wow so content very media amuse

    void registerForEusimReady(Handler h, int what, Object obj);
    void unregisterForEusimReady(Handler h);

    /**
     * Sets the handler for event download of call notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnStkEvdlCall(Handler h, int what, Object obj);
    void unSetOnStkEvdlCall(Handler h);

    /**
     * Sets the handler for event download of call notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnStkSetupMenuReset(Handler h, int what, Object obj);
    void unSetOnStkSetupMenuReset(Handler h);

    /**
     * Sets the handler for call ccontrol response message.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnStkCallCtrl(Handler h, int what, Object obj);
    /**
     * Unsets the handler for call ccontrol response message.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     */
    void unSetOnStkCallCtrl(Handler h);

    /// M: BIP {
    /**
     * Sets the handler for Proactive Commands for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnBipProactiveCmd(Handler h, int what, Object obj);
    void unSetOnBipProactiveCmd(Handler h);
    /// M: BIP }

    //MTK-START [mtk06800] modem power on/off
    void setModemPower(boolean power, Message response);
    //MTK-END [mtk06800] modem power on/off

    void setNetworkSelectionModeManualWithAct(String operatorNumeric, String act, Message response);

    void setNetworkSelectionModeSemiAutomatic(String operatorNumeric, String act, Message response);

    /**
     * Cancel querie the currently available networks
     *
     * ((AsyncResult)response.obj).result  is a List of NetworkInfo objects
     */
    void cancelAvailableNetworks(Message response);

    /* M: SS part */
    ///M: For query CNAP
    void sendCNAPSS(String cnapssString, Message response);
    /* M: SS part end */

    /**
     * Indicates to the vendor ril that call connected and disconnected
     * event download will be handled by AP.
     * @param enabled '0' handles event download by AP; '1' handles event download by MODEM
     * @param response callback message
     */
    void setStkEvdlCallByAP(int enabled, Message response);

    /**
     * Query UTK menu from modem
     *
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void queryUtkSetupMenuFromMD(String contents, Message response);

    /**
     * Query STK menu from modem.
     *
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void queryStkSetUpMenuFromMD(String contents, Message response);

    /**
     * Accept or reject the call setup request from SIM.
     *
     * @param accept   true if the call is to be accepted, false otherwise.
     * @param response Callback message
     */
    public void handleCallSetupRequestFromSim(boolean accept, int resCode, Message response);

    /**
     * Sets user selected subscription at Modem.
     *
     * @param slotId
     *          Slot.
     * @param appIndex
     *          Application index in the card.
     * @param subId
     *          Indicates subscription 0 or subscription 1.
     * @param subStatus
     *          Activation status, 1 = activate and 0 = deactivate.
     * @param result
     *          Callback message contains the information of SUCCESS/FAILURE.
     */
    // FIXME Update the doc and consider modifying the request to make more generic.
    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus,
            Message result);

    /// M: CC010: Add RIL interface @{
    public void registerForCipherIndication(Handler h, int what, Object obj);
    public void unregisterForCipherIndication(Handler h);
    public void registerForCallForwardingInfo(Handler h, int what, Object obj);
    public void unregisterForCallForwardingInfo(Handler h);
    public void setOnCallRelatedSuppSvc(Handler h, int what, Object obj);
    public void unSetOnCallRelatedSuppSvc(Handler h);

    /**
     * used to register to +EAIC URC for call state change.
     *
     * msg.obj is an AsyncResult
     * ar.result is a String[]
     */
    public void setOnIncomingCallIndication(Handler h, int what, Object obj);
    public void unsetOnIncomingCallIndication(Handler h);

    //obsolete
    /*
    public void setCnapNotify(Handler h, int what, Object obj);
    public void unSetCnapNotify(Handler h);
    */
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    void setOnSpeechCodecInfo(Handler h, int what, Object obj);
    void unSetOnSpeechCodecInfo(Handler h);
    /// @}

    /// M: CC010: Add RIL interface @{
    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    public void hangupAll(Message result);

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    public void forceReleaseCall(int index, Message result);

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    public void setCallIndication(int mode, int callId, int seqNumber, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    public void emergencyDial(String address, int clirMode, UUSInfo uusInfo, Message result);

    public void setEccServiceCategory(int serviceCategory);
    /// @}

    /// M: CC077: 2/3G CAPABILITY_HIGH_DEF_AUDIO @{
    public void setSpeechCodecInfo(boolean enable, Message response);
    /// @}

    /// M: For 3G VT only @{
    /**
     * used to register to +EVTSTATUS URC for VT status.
     *
     * msg.obj is an AsyncResult
     * ar.result is a int[]
     * String[0] is on_off
     */
    void registerForVtStatusInfo(Handler h, int what, Object obj);
    void unregisterForVtStatusInfo(Handler h);

    /**
     * used to register to +CRING: VIDEO URC for MT VT call.
     *
     * msg.obj is an AsyncResult
     */
    void registerForVtRingInfo(Handler h, int what, Object obj);
    void unregisterForVtRingInfo(Handler h);
    /// @}

    /// M: For 3G VT only @{
    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    public void vtDial(String address, int clirMode, UUSInfo uusInfo, Message result);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    public void acceptVtCallWithVoiceOnly(int callId, Message result);

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    public void replaceVtCall(int index, Message result);
    /// @}

    /* M: SS part */
    void changeBarringPassword(String facility, String oldPwd, String newPwd, String newCfm,
         Message result);

    /**
     * response.obj will be a an int[2]
     *
     * response.obj[0] will be TS 27.007 +COLP parameter 'n'
     *  0 COLP disabled
     *  1 COLP enabled
     *
     * response.obj[1] will be TS 27.007 +COLP parameter 'm'
     *  0 COLP not provisioned
     *  1 COLP provisioned
     *  2 unknown (e.g. no network, etc.)
     */
    void getCOLP(Message response);

    /**
     * enable is true for enable / false for disable COLP (ONLY affect TE not NW)
     *
     * response.obj is null
     */
    void setCOLP(boolean enable, Message response);

    /**
     * response.obj will be a an int[1]
     *
     * response.obj[0] will be proprietary +COLR parameter 'n'
     *  0 COLR not provisioned
     *  1 COLR provisioned
     *  2 unknown (e.g. no network, etc.)
     */
    void getCOLR(Message response);

    /**
     * enable is true for enable / false for disable CLIP (ONLY affect TE not NW)
     *
     * response.obj is null
     */
    void setCLIP(boolean enable, Message response);
    /* M: SS part end */

    //MTK-START multiple application support
    /**
     * M: Open application in the UICC
     *
     * @param application: application ID
     * @param response The message to send.
     */
    public void openIccApplication(int application, Message response);

    /**
     * Query application status
     *
     * @param sessionId: The channel ID
     * @param response The message to send.
     */
    public void getIccApplicationStatus(int sessionId, Message result);


    /**
     * Register the handler for event notifications for sessionid of an application changed event.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSessionChanged(Handler h, int what, Object obj);

    /**
     * Unregister the handler for event notifications for sessionid of an application changed event.
     *
     * @param h Handler for notification message.
     */
    void unregisterForSessionChanged(Handler h);
    //MTK-END multiple application support

    /**
     * Query network lock status according to indicated category.
     *
     * @param categrory network lock category
     *                  0 for Network personalisation category
     *                  1 for Network subset personalisation category
     *                  2 for Service provider personalisation category
     *                  3 for Corporate(GID) personalisation category
     *                  4 for SIM/USIM(IMSI) personalisation category
     * @param response Callback message containing response structure.
     */
    void queryNetworkLock(int categrory, Message response);

    /**
     * Query network lock status according to indicated category.
     *
     * @param categrory network lock category
     *                  "0" for Network personalisation category
     *                  "1" for Network subset personalisation category
     *                  "2" for Service provider personalisation category
     *                  "3" for Corporate(GID) personalisation category
     *                  "4" for SIM/USIM(IMSI) personalisation category
     * @param lockop lock operation
     *               "0" for unlock opreation
     *               "1" for lock opreation
     *               "2" for add lock opreation
     *               "3" for remove lock opreation
     *               "4" for disable lock category opreation
     * @param password password of indicated network lock
     * @param data_imsi IMSI value used to setup lock
     * @param gid1 GID1 value used to setup lock
     * @param gid2 GID2 value used to setup lock
     * @param response Callback message containing response structure.
     */
    void setNetworkLock(int catagory, int lockop, String password,
            String data_imsi, String gid1, String gid2, Message response);


    /**
     * Request security context authentication for SIM/USIM/ISIM
     */
    public void doGeneralSimAuthentication(int sessionId, int mode , int tag, String param1,
                                                    String param2, Message response);

    // Added by M begin
    void iccGetATR(Message result);
    void iccOpenChannelWithSw(String AID, Message result);

    void registerForSimMissing(Handler h, int what, Object obj);
    void unregisterForSimMissing(Handler h);

    void registerForSimRecovery(Handler h, int what, Object obj);
    void unregisterForSimRecovery(Handler h);

    public void registerForVirtualSimOn(Handler h, int what, Object obj);
    public void unregisterForVirtualSimOn(Handler h);

    public void registerForVirtualSimOff(Handler h, int what, Object obj);
    public void unregisterForVirtualSimOff(Handler h);

    /**
     * Sets the handler for event notifications for SIM plug-out event.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSimPlugOut(Handler h, int what, Object obj);

    /**
     * Unregister the handler for event notifications for SIM plug-out event.
     *
     * @param h Handler for notification message.
     */
    void unregisterForSimPlugOut(Handler h);

    /**
     * Sets the handler for event notifications for SIM plug-in event.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSimPlugIn(Handler h, int what, Object obj);

    /**
     * Unregister the handler for event notifications for SIM plug-in event.
     *
     * @param h Handler for notification message.
     */
    void unregisterForSimPlugIn(Handler h);

    /**
     * Sets the handler for event notifications for Tray plug-in event in common slot project.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForTrayPlugIn(Handler h, int what, Object obj);

    /**
     * Unregister the handler for event notifications for Tray plug-in event in common slot project.
     *
     * @param h Handler for notification message.
     */
    void unregisterForTrayPlugIn(Handler h);

    /**
     * Sets the handler for event notifications for SIM common slot no changed.
     *
     */
    void registerForCommonSlotNoChanged(Handler h, int what, Object obj);

    /**
     * Unregister the handler for event notifications for SIM common slot no changed.
     *
     */
    void unregisterForCommonSlotNoChanged(Handler h);

    void registerSetDataAllowed(Handler h, int what, Object obj);
    void unregisterSetDataAllowed(Handler h);


    /**
     * Send BT SIM profile
     * @param nAction
     *          the type of the action
     *          0: Connect
     *          1: Disconnect
     *          2: Power On
     *          3: Power Off
     *          4: Reset
     *          5: APDU
     * @param nType
     *          Indicate which transport protocol is the preferred one
     *          0x00 : T=0
     *          0x01 : T=1
     * @param strData
     *          Only be used when action is APDU transfer
     * @param response
     *          Callback message containing response structure.
     */
    public void sendBTSIMProfile(int nAction, int nType, String strData, Message response);

    void registerForEfCspPlmnModeBitChanged(Handler h, int what, Object obj);
    void unregisterForEfCspPlmnModeBitChanged(Handler h);

    /**
     * Request the information of the given storage type
     *
     * @param type
     *          the type of the storage, refer to PHB_XDN defined in the RilConstants
     * @param response
     *          Callback message
     *          response.obj.result is an int[4]
     *          response.obj.result[0] is number of current used entries
     *          response.obj.result[1] is number of total entries in the storage
     *          response.obj.result[2] is maximum supported length of the number
     *          response.obj.result[3] is maximum supported length of the alphaId
     */
    public void queryPhbStorageInfo(int type, Message response);

    /**
     * Request update a PHB entry using the given {@link PhbEntry}
     *
     * @param entry a PHB entry strucutre {@link PhbEntry}
     *          when one of the following occurs, it means delete the entry.
     *          1. entry.number is NULL
     *          2. entry.number is empty and entry.ton = 0x91
     *          3. entry.alphaId is NULL
     *          4. both entry.number and entry.alphaId are empty.
     * @param result
     *          Callback message containing if the action is success or not.
     */
    public void writePhbEntry(PhbEntry entry, Message result);

    /**
     * Request read PHB entries from the given storage
     * @param type
     *          the type of the storage, refer to PHB_* defined in the RilConstants
     * @param bIndex
     *          the begin index of the entries to be read
     * @param eIndex
     *          the end index of the entries to be read, note that the (eIndex - bIndex +1)
     *          should not exceed the value RilConstants.PHB_MAX_ENTRY
     *
     * @param response
     *          Callback message containing an array of {@link PhbEntry} structure.
     */
    public void ReadPhbEntry(int type, int bIndex, int eIndex, Message response);

    /**
     * Sets the handler for PHB ready notification
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForPhbReady(Handler h, int what, Object obj);
    void unregisterForPhbReady(Handler h);

    void queryUPBCapability(Message response);
    void editUPBEntry(int entryType, int adnIndex, int entryIndex, String strVal,
         String tonForNum, Message response);
    void deleteUPBEntry(int entryType, int adnIndex, int entryIndex, Message response);
    void readUPBGasList(int startIndex, int endIndex, Message response);
    void readUPBGrpEntry(int adnIndex, Message response);
    void writeUPBGrpEntry(int adnIndex, int[] grpIds, Message response);

    void getPhoneBookStringsLength(Message result);
    void getPhoneBookMemStorage(Message result);
    void setPhoneBookMemStorage(String storage, String password, Message result);
    // xen0n: MTK TODO
    // void readPhoneBookEntryExt(int index1, int index2, Message result);
    // void writePhoneBookEntryExt(PBEntry entry, Message result);

    // Added by M end

    // xen0n: MTK TODO
    // MTK-START, SMS part
    /*
     * Get sms parameters from EFsmsp
     */
    // void getSmsParameters(Message response);

    /*
     * Set sms parameters into EFsmsp
     */
    // void setSmsParameters(SmsParameters params, Message response);

    /**
     * Get SMS SIM Card memory's total and used number
     *
     * @param result callback message
     */
    void getSmsSimMemoryStatus(Message result);

    void setEtws(int mode, Message result);
    void setOnEtwsNotification(Handler h, int what, Object obj);
    void unSetOnEtwsNotification(Handler h);

    /**
     * Sets the handler for ME SMS storage full unsolicited message.
     * Unlike the register methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnMeSmsFull(Handler h, int what, Object obj);
    void unSetOnMeSmsFull(Handler h);

    /**
     * Register the handler for SMS ready notification.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSmsReady(Handler h, int what, Object obj);

    /**
     * Unregister the handler for SMS ready notification.
     *
     * @param h Handler for notification message.
     */
    void unregisterForSmsReady(Handler h);

    void setCellBroadcastChannelConfigInfo(String config, int cb_set_type, Message response);
    void setCellBroadcastLanguageConfigInfo(String config, Message response);
    void queryCellBroadcastConfigInfo(Message response);
    void removeCellBroadcastMsg(int channelId, int serialId, Message response);
    // MTK-END, SMS part

    void getPOLCapabilty(Message response);
    void getCurrentPOLList(Message response);
    void setPOLEntry(int index, String numeric, int nAct, Message response);

    void registerForPsNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForPsNetworkStateChanged(Handler h);

    void registerForIMEILock(Handler h, int what, Object obj);
    void unregisterForIMEILock(Handler h);

   /**
     * Sets the handler for Invalid SIM unsolicited message.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setInvalidSimInfo(Handler h, int what, Object obj);
    void unSetInvalidSimInfo(Handler h);

    // get Available network informaitons API
    void registerForGetAvailableNetworksDone(Handler h, int what, Object obj);
    void unregisterForGetAvailableNetworksDone(Handler h);
    boolean isGettingAvailableNetworks();

  // Femtocell (CSG) feature START
  /**
     * Queries the currently available femtocells
     *
     * ((AsyncResult)response.obj).result  is a List of FemtoCellInfo objects
     */
    void getFemtoCellList(String operatorNumeric, int rat, Message response);

  /**
     * Abort quering available femtocells
     *
     * ((AsyncResult)response.obj).result  is a List of FemtoCellInfo objects
     */
    void abortFemtoCellList(Message response);

  /**
     * select femtocell
     *
     * @param femtocell info
     */
    void selectFemtoCell(FemtoCellInfo femtocell, Message response);

    public void registerForFemtoCellInfo(Handler h, int what, Object obj);
    public void unregisterForFemtoCellInfo(Handler h);
    // Femtocell (CSG) feature END

    /**
     * unlike the register* methods, there's only one Neighboring cell info handler
     *
     * AsyncResult.result is an Object[]
     * ((Object[])AsyncResult.result)[0] is a String containing the RAT
     * ((Object[])AsyncResult.result)[1] is a String containing the neighboring cell info raw data
     *
     * Please note that the delivery of this message may be delayed several
     * seconds on system startup
     */
    void registerForNeighboringInfo(Handler h, int what, Object obj);
    void unregisterForNeighboringInfo(Handler h);

    /**
     * unlike the register* methods, there's only one Network info handler
     *
     * AsyncResult.result is an Object[]
     * ((Object[])AsyncResult.result)[0] is a String containing the type
     * ((Object[])AsyncResult.result)[1] is a String contain the network info raw data
     *
     * Please note that the delivery of this message may be delayed several
     * seconds on system startup
     */
    void registerForNetworkInfo(Handler h, int what, Object obj);
    void unregisterForNetworkInfo(Handler h);

    // IMS
    public void registerForImsEnable(Handler h, int what, Object obj);
    public void unregisterForImsEnable(Handler h);
    public void registerForImsDisable(Handler h, int what, Object obj);
    public void unregisterForImsDisable(Handler h);
    public void setIMSEnabled(boolean enable, Message response);
    public void registerForImsDisableDone(Handler h, int what, Object obj);
    public void unregisterForImsDisableDone(Handler h);
    public void registerForImsRegistrationInfo(Handler h, int what, Object obj);
    public void unregisterForImsRegistrationInfo(Handler h);

    void setTrm(int mode, Message result);

    void setOnPlmnChangeNotification(Handler h, int what, Object obj);
    void unSetOnPlmnChangeNotification(Handler h);
    void setOnRegistrationSuspended(Handler h, int what, Object obj);
    void unSetOnRegistrationSuspended(Handler h);
    void storeModemType(int modemType, Message response);
    void reloadModemType(int modemType, Message response);
    void queryModemType(Message response);

    //Remote SIM ME lock related APIs [Start]
    void registerForMelockChanged(Handler h, int what, Object obj);
    void unregisterForMelockChanged(Handler h);
    //Remote SIM ME lock related APIs [End]

    /** M: start */
    void setupDataCall(String radioTechnology, String profile, String apn, String user,
            String password, String authType, String protocol, String interfaceId, Message result);

     /**
     * @param apn for apn name
     * @param protocol for IP type
     * @param authType for Auth type
     * @param username for username
     * @param password for password
     * @param obj for ia extend parameter
     * @param result for result
     */
    void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Object obj, Message result);

    // Fast Dormancy
    void setScri(boolean forceRelease, Message response);
    void setFDMode(int mode, int parameter1, int parameter2, Message response);
    public void setScriResult(Handler h, int what, Object obj);
    public void unSetScriResult(Handler h);
    /** M: end */

    /// M: IMS feature. @{
    /* Register for updating call ids for conference call after SRVCC is done. */
    public void registerForEconfSrvcc(Handler h, int what, Object obj);
    public void unregisterForEconfSrvcc(Handler h);

    /* Register for updating conference call merged/added result. */
    public void registerForEconfResult(Handler h, int what, Object obj);
    public void unregisterForEconfResult(Handler h);

    /* Register for updating call mode and pau */
    public void registerForCallInfo(Handler h, int what, Object obj);
    public void unregisterForCallInfo(Handler h);

    /* Add/Remove VoLTE(IMS) conference call member. */
    public void addConferenceMember(int confCallId, String address, int callIdToAdd, Message response);
    public void removeConferenceMember(int confCallId, String address, int callIdToRemove,
           Message response);

    /**
     * To resume the call.
     * @param callIdToResume toIndicate which call session to resume.
     * @param response command response.
     */
    public void resumeCall(int callIdToResume, Message response);

    /**
     * To hold the call.
     * @param callIdToHold toIndicate which call session to hold.
     * @param response command response.
     */
    public void holdCall(int callIdToHold, Message response);

    /**
     * M: notify screen state to RILD
     *
     * @param on The screen state
     */
    public void sendScreenState(boolean on);

    /// @}

    /**
     * M: CC33 LTE.
     */
    public void registerForRacUpdate(Handler h, int what, Object obj);
    public void unregisterForRacUpdate(Handler h);
    public void setDataOnToMD(boolean enable, Message result);
    public void setRemoveRestrictEutranMode(boolean enable, Message result);
    public void registerForRemoveRestrictEutran(Handler h, int what, Object obj);
    public void unregisterForRemoveRestrictEutran(Handler h);

    /**
     * M: Reset Attach Apn
     */
    public void registerForResetAttachApn(Handler h, int what, Object obj);
    public void unregisterForResetAttachApn(Handler h);

   /**
    * M: [LTE][Low Power][UL traffic shaping]
    */
    public void setLteAccessStratumReport(boolean enable, Message result);
    public void setLteUplinkDataTransfer(int state, int interfaceId, Message result);
    public void registerForLteAccessStratumState(Handler h, int what, Object obj);
    public void unregisterForLteAccessStratumState(Handler h);

    /**
     * IMS.
     * @param enable if true.
     * @param response User-defined message code.
     */

    public void setDataCentric(boolean enable, Message response);


    /// M: CC010: Add RIL interface @{
    /**
     * Notify modem about IMS call status.
     * @param existed True if there is at least one IMS call existed, else return false.
     * @param response User-defined message code.
     */
    public void setImsCallStatus(boolean existed, Message response);
    /// @}

    /// M: CC072: Add Customer proprietary-IMS RIL interface. @{
    /**
     * Transfer IMS call to CS modem.
     *
     * @param numberOfCall The number of call
     * @param callList IMS call context
     */
     public void setSrvccCallContextTransfer(int numberOfCall, SrvccCallContext[] callList);

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
     public void updateImsRegistrationStatus(int regState, int regType, int reason);
     /// @}

    /* C2K part start */
    /**
     * Request to recovery telephony.
     *
     * @param mode The recovery mode
     * @param result callback message
     */
    void setViaTRM(int mode, Message result);

    /**
     * Request to get NITZ time.
     *
     * @param result callback message
     */
    void getNitzTime(Message result);

    /**
     * Request to switch HPF.
     * @param enableHPF true if
     * @param response callback message
     */
    void requestSwitchHPF(boolean enableHPF, Message response);

    /**
     * Request to set avoid SYS.
     * @param avoidSYS true if
     * @param response callback message
     */
    void setAvoidSYS(boolean avoidSYS, Message response);

    /**
     * Request to get avoid SYS List.
     * @param response callback message
     */
    void getAvoidSYSList(Message response);

    /**
     * M: oplmn is the oplmn list download from the specific url.
     * @param oplmnInfo The info send to the modem
     * @param response The message to send.
     */
    void setOplmn(String oplmnInfo, Message response);

    /**
     * M: Get the oplmn updated version.
     * @param response the responding message.
     */
    void getOplmnVersion(Message response);

    /**
     * query CDMA Network Info.
     * @param response callback message
     */
    void queryCDMANetworkInfo(Message response);

    /**
     * Register the handler for call accepted.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForCallAccepted(Handler h, int what, Object obj);

    /**
     * Unregister the handler for call accepted.
     * @param h Handler for notification message.
     */
    void unregisterForCallAccepted(Handler h);

    /**
     * Sets the handler for meid.
     * @param meid meid string.
     * @param response callback message.
     */
    void setMeid(String meid, Message response);

    /**
     * Register for via gps event.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForViaGpsEvent(Handler h, int what, Object obj);

    /**
     * Unregister the handler for via gps event.
     * @param h Handler for notification message.
     */
    void unregisterForViaGpsEvent(Handler h);

    /**
     * Request to AGPScp connected.
     * @param connected connected number
     * @param result callback message
     */
    void requestAGPSTcpConnected(int connected, Message result);

    /**
     * request AGPS set mpc ip & port address.
     *
     * @param ip ip address
     * @param port port to use
     * @param result callback message
     */
    void requestAGPSSetMpcIpPort(String ip, String port, Message result);

    /**
     * request AGPS get mpc ip & port address.
     *
     * @param result callback message
     */
    void requestAGPSGetMpcIpPort(Message result);

    /**
     * request set ets device.
     *
     * @param dev 0-uart,1-usb,2-sdio
     * @param result callback message
     */
     void requestSetEtsDev(int dev, Message result);

     /**
      * For China Telecom auto-register sms.
      *
      * @param response The request's response
      */
     void queryCDMASmsAndPBStatus(Message response);

     /**
      * For China Telecom auto-register sms.
      *
      * @param response The request's response
      */
     void queryCDMANetWorkRegistrationState(Message response);

     /**
      * Register for network change callback.
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      */
     void registerForNetworkTypeChanged(Handler h, int what, Object obj);

     /**
      * Unregister for network change callback.
      *
      * @param h Handler for notification message.
      */
     void unregisterForNetworkTypeChanged(Handler h);

     /**
      * Set ARSI report threshold.
      *
      * @param threshold The threshold to set
      * @param response The request's response
      */
     void setArsiReportThreshold(int threshold, Message response);

     /**
      * Set MDN number.
      * @param mdn The mdn numer to set
      * @param response The request's response
      */
     void setMdnNumber(String mdn, Message response);

    // UTK start
    /**
     * set on utk session end.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnUtkSessionEnd(Handler h, int what, Object obj);

    /**
     * unset on utk session end.
     * @param h Handler for notification message.
     */
    void unSetOnUtkSessionEnd(Handler h);

    /**
     * set on utk proactive cmd.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnUtkProactiveCmd(Handler h, int what, Object obj);

    /**
     * unset on utk proactive cmd.
     * @param h Handler for notification message.
     */
    void unSetOnUtkProactiveCmd(Handler h);

    /**
     * set on utk event.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnUtkEvent(Handler h, int what, Object obj);

    /**
     * unset on utk event.
     * @param h Handler for notification message.
     */
    void unSetOnUtkEvent(Handler h);

    /**
     * handle call setup request from uim.
     * @param accept true if.
     * @param response callback message.
     */
    public void handleCallSetupRequestFromUim(boolean accept, Message response);

    /**
     * report utk service is running.
     * @param result callback message.
     */
    void reportUtkServiceIsRunning(Message result);
    /**
     * Query Local Info.
     *
     * @param result callback message
     */
    void getUtkLocalInfo(Message result);

    /**
     * Send a UTK refresh command.
     *
     * @param refreshType refresh type
     * @param result callback message
     */
    void requestUtkRefresh(int refreshType, Message result);

    /**
     * When Vendor UtkService is running, download profile to tell Ruim what capability phone has.
     *
     * @param response callback message
     *
     * @param profile  profile downloaded into Ruim
     */
    void profileDownload(String profile, Message response);
    //UTK end

    /**
     * C2K SVLTE remote SIM access.
     * @param modemStatus The Modem status: 0: Only MD1 active
     *                                      1: MD1's RF is closed, but MD1's SIM task is still
     *                                         working onlyfor MD3 SIM remove access and MD3 active
     *                                      2: Both MD1 and MD3 active
     * @param remoteSimProtocol MD3 decide to access SIM from which protocl of MD1
     *                          0: MD3 access local card
     *                          1: MD1 access MD1's SIM task1
     *                          2: MD1 access MD1's SIM task2
     * @param result callback message
     */
    void configModemStatus(int modemStatus, int remoteSimProtocol, Message result);

    /**
     * Disconnect RIL socket. For SVLTE C2K only mode to disable LTE functions.
     */
    void disconnectRilSocket();

    /**
     * Reconnect RIL socket.
     */
    void connectRilSocket();

    /* C2k part end */
    /**
     * C2K SVLTE CDMA eHPRD control.
     * Used to turn on/off eHPRD.
     * @param evdoMode The mode set to MD3: 0: turn off eHPRD.
     *                                      1: turn on eHPRD.
     * @param result callback message
     */
    void configEvdoMode(int evdoMode, Message result);
    /* C2k part end */

    void setBandMode(int[] bandMode, Message response);

    public void registerForAbnormalEvent(Handler h, int what, Object obj);

    public void unregisterForAbnormalEvent(Handler h);

    /**
      * Rregister for cdma card type.
      * @param h Handler for network information messages.
      * @param what User-defined message code.
      * @param obj User object.
      */
    void registerForCdmaCardType(Handler h, int what, Object obj);

    /**
      * Rregister for cdma card type.
      * @param h Handler for network information messages.
      */
    void unregisterForCdmaCardType(Handler h);

    /// M: [C2K] for eng mode start
    /**
     * M: Rregister on network information for eng mode.
     * @param h Handler for network information messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForEngModeNetworkInfo(Handler h, int what, Object obj);

    /**
     * M: Unrregister on network information for eng mode.
     * @param h Handler for network information messages.
     */
    void unregisterForEngModeNetworkInfo(Handler h);
    /// M: [C2K] for eng mode end

    /**
     * M: return display state to RILD
     *
     */
    public int getDisplayState();

    /**
     *  Query operator name from network
     * @param subId
     *          Indicates subscription 0 or subscription 1.
     * @param numeric for operator numeric
     * @param desireLongName
     *          Indicates longname or shortname
     */
    public String lookupOperatorNameFromNetwork(long subId, String numeric, boolean desireLongName);

    /* M: IMS VoLTE conference dial feature start*/
    /**
     * Dial conference call.
     * @param participants participants' dailing number.
     * @param clirMode indication to present the dialing number or not.
     * @param isVideoCall indicate the call is belong to video call or voice call.
     * @param result the command result.
     */
    void conferenceDial(String[] participants, int clirMode, boolean isVideoCall, Message result);
    /* IMS VoLTE conference dial feature end*/

    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @{
    /**
     * Register for GMSS RAT.
     * When boot the phone,AP can use this informaiton decide PS' type(LTE or C2K).
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForGmssRatChanged(Handler h, int what, Object obj);

    /**
     * Unregister GMSS RAT get GMSS RAT.
     * When boot the phone,AP can use this informaiton decide PS' type(LTE or C2K).
     * @param h Handler for notification message.
     */
    void unregisterForGmssRatChanged(Handler h);
    /// M: [C2K][IR][MD-IRAT] URC for GMSS RAT changed. @}

    /// M: [C2K] for ps type changed. @{
    /**
     * Register for ps type changed.
     * @param h Handler for ps type change messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForDataNetworkTypeChanged(Handler h, int what, Object obj);

    /**
     * Unregister for ps type changed.
     * @param h Handler for ps type change messages.
     */
    void unregisterForDataNetworkTypeChanged(Handler h);
    /// @}

    /// [C2K][IRAT] start @{
    /**
     * M: Fires on any change in inter-3GPP IRAT status change.
     * @param h Handler for IRAT status change messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForIratStateChanged(Handler h, int what, Object obj);

    /**
     * M: Unregister for inter-3GPP IRAT status change event.
     * @param h Handler for IRAT status change messages
     */
    void unregisterForIratStateChanged(Handler h);

    /**
     * M: Confirm inter-3GPP IRAT change, MD will perform IRAT process after
     * receive this command, AP need to make sure to suspend all PS requests
     * before call this function.
     * @param apDecision The decision of AP, need to be 1(accept) currently.
     * @param response A callback message with the String response in the obj field
     */
    void confirmIratChange(int apDecision, Message response);

    /**
     * M: Set PS active slot for Gemini LTE dual connection project, send
     * AT+EACTS=slotId to MD, the request can only send by main protocol.
     * @param psSlot Slot to be used for data connection.
     * @param response A callback message with the String response in the obj field
     */
    void requestSetPsActiveSlot(int psSlot, Message response);

    /**
     * Sync notify data call list after IRAT finished.
     * @param dcList Data call list.
     */
    void syncNotifyDataCallList(AsyncResult dcList);
    /// }@

    /**
     * Request to deactivate link down PDN to release IP address.
     * @param response callback message.
     */
    void requestDeactivateLinkDownPdn(Message response);

    /**
     * Register for CDMA imsi ready.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForCdmaImsiReady(Handler h, int what, Object obj);

    /**
     * Unregister for CDMA imsi ready.
     * @param h Handler for notification message.
     */
    public void unregisterForCdmaImsiReady(Handler h);

    /**
      * Register for imsi refresh done.
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      */
    public void registerForImsiRefreshDone(Handler h, int what, Object obj);
    /**
      * Unregister for imsi refresh done.
      * @param h Handler for notification message.
      */
    public void unregisterForImsiRefreshDone(Handler h) ;

    /**
      * To get RadioCapability stored in RILJ when phone object is not created
      */
    public RadioCapability getBootupRadioCapability();

    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @{
    /**
     * M: Request to set the SVLTE Mode (SVLTE_4G or SVLTE_3G or LTE_TDD_DATA_ONLY).
     *
     * @param radioTechMode The radio teck mode.
     * @param preSvlteMode The previous rat mode.
     * @param svlteMode The rat mode.
     * @param preRoamingMode The previous roaming mode.
     * @param roamingMode The roaming mode.
     * @param is3GDualModeCard Whether the SIM card is 3g dual mode card or not.
     * @param response A callback message with the String response in the obj field.
     */
    void setSvlteRatMode(int radioTechMode, int preSvlteMode, int svlteMode,
            int preRoamingMode, int roamingMode, boolean is3GDualModeCard, Message response);
    /// M: [C2K][SVLTE] Set the SVLTE RAT mode. @}

    /// M: [C2K][SVLTE] Set the STK UTK mode. @{
    /**
     * M: Request to set the STK/UTK Mode
     *
     * @param stkUtkMode the target STK/UTK mode
     * @param response A callback message with the String response in the obj field.
     */
    void setStkUtkMode(int stkUtkMode, Message response);
    /// M: [C2K][SVLTE] Set the STK UTK mode. @}

    /// M: [C2K][SVLTE] Update RIL instance id for SVLTE switch ActivePhone. @{
    /**
     * M: For SVLTE to update RIL instance id.
     * @param instanceId The new instance Id.
     */
    void setInstanceId(int instanceId);
    /// @}

    /// M: [C2K][IR] Support SVLTE IR feature. @{

    /**
     * Set GSM modem to suspend network registration.
     * @param enabled True to pause and false to resume.
     * @param response the responding message.
     */
    void setRegistrationSuspendEnabled(int enabled, Message response);

    /**
     * Request GSM modem to resume network registration.
     * @param sessionId the session index.
     * @param response the responding message.
     */
    void setResumeRegistration(int sessionId, Message response);

    /**
     * Set GSM modem to suspend network registration.
     * @param enabled True to pause and false to resume.
     * @param response the responding message.
     */
    void setCdmaRegistrationSuspendEnabled(boolean enabled, Message response);

    /**
     * Request C2K modem to resume network registration.
     * @param response the responding message.
     */
    void setResumeCdmaRegistration(Message response);

    /**
     * Register for mcc and mnc change.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForMccMncChange(Handler h, int what, Object obj);

    /**
     * Unregister for mcc and mnc change.
     * @param h Handler for notification message.
     */
    void unregisterForMccMncChange(Handler h);

    /**
     * Set the xTK mode.
     * @param mode The xTK mode.
     */
    void setStkSwitchMode(int mode);

    /**
     * Set the UTK Bip Ps type .
     * @param mBipPsType The Bip type.
     */
    void setBipPsType(int type);

    /// M: [C2K][IR] Support SVLTE IR feature. @}

    /**
     * Register for Signal Fade notification.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCdmaSignalFade(Handler h, int what, Object obj);

    /**
     * Unregister for Signal Fade notification.
     * @param h Handler for notification message.
     */
    void unSetOnCdmaSignalFade(Handler h);

    /**
     * Register for Tone Signal message.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCdmaToneSignal(Handler h, int what, Object obj);

    /**
     * Unregister for Tone Signal message.
     * @param h Handler for notification message.
     */
    void unSetOnCdmaToneSignal(Handler h);

    /**
     * Switch antenna.
     * @param callState call state, 0 means call disconnected and 1 means call established.
     * @param ratMode RAT mode, 0 means GSM and 7 means C2K.
     */
    void switchAntenna(int callState, int ratMode);

    /**
     * Switch RUIM card to SIM or switch SIM to RUIM.
     * @param cardtype that to be switched.
     */
    void switchCardType(int cardtype);

    /**
     * Enable or disable MD3 Sleep.
     * @param enable MD3 sleep.
     */
    void enableMd3Sleep(int enable);

    /**
     * Register for network exsit or not for ECC only.
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForNetworkExsit(Handler h, int what, Object obj);

    /**
     * Unregister for network exsit.
     * @param h Handler for notification message.
     */
    void unregisterForNetworkExsit(Handler h);
}
