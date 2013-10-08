/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
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

package com.android.internal.telephony.cdma;


import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsCbMessage;
import android.telephony.SmsManager;
import android.telephony.cdma.CdmaSmsCbProgramData;
import android.telephony.cdma.CdmaSmsCbProgramResults;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.util.BitwiseInputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


public class CdmaSMSDispatcher extends SMSDispatcher {
    protected static final String TAG = "CdmaSMSDispatcher";
    private static final boolean VDBG = false;
    private ImsSMSDispatcher mImsSMSDispatcher;

    private byte[] mLastDispatchedSmsFingerprint;
    private byte[] mLastAcknowledgedSmsFingerprint;

    private final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(
            com.android.internal.R.bool.config_duplicate_port_omadm_wappush);

    public CdmaSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor, ImsSMSDispatcher imsSMSDispatcher) {
        super(phone, storageMonitor, usageMonitor);
        mCi.setOnNewCdmaSms(this, EVENT_NEW_SMS, null);
        mImsSMSDispatcher = imsSMSDispatcher;
        Rlog.d(TAG, "CdmaSMSDispatcher created");
    }

    @Override
    public void dispose() {
        mCi.unSetOnNewCdmaSms(this);
    }

    @Override
    protected String getFormat() {
        return SmsConstants.FORMAT_3GPP2;
    }

    private void handleCdmaStatusReport(SmsMessage sms) {
        for (int i = 0, count = deliveryPendingList.size(); i < count; i++) {
            SmsTracker tracker = deliveryPendingList.get(i);
            if (tracker.mMessageRef == sms.mMessageRef) {
                // Found it.  Remove from list and broadcast.
                deliveryPendingList.remove(i);
                PendingIntent intent = tracker.mDeliveryIntent;
                Intent fillIn = new Intent();
                fillIn.putExtra("pdu", sms.getPdu());
                fillIn.putExtra("format", getFormat());
                try {
                    intent.send(mContext, Activity.RESULT_OK, fillIn);
                } catch (CanceledException ex) {}
                break;  // Only expect to see one tracker matching this message.
            }
        }
    }

    /**
     * Dispatch service category program data to the CellBroadcastReceiver app, which filters
     * the broadcast alerts to display.
     * @param sms the SMS message containing one or more
     * {@link android.telephony.cdma.CdmaSmsCbProgramData} objects.
     */
    private void handleServiceCategoryProgramData(SmsMessage sms) {
        ArrayList<CdmaSmsCbProgramData> programDataList = sms.getSmsCbProgramData();
        if (programDataList == null) {
            Rlog.e(TAG, "handleServiceCategoryProgramData: program data list is null!");
            return;
        }

        Intent intent = new Intent(Intents.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION);
        intent.putExtra("sender", sms.getOriginatingAddress());
        intent.putParcelableArrayListExtra("program_data", programDataList);
        dispatch(intent, RECEIVE_SMS_PERMISSION, AppOpsManager.OP_RECEIVE_SMS, mScpResultsReceiver);
    }

    /** {@inheritDoc} */
    @Override
    protected int dispatchMessage(SmsMessageBase smsb) {

        // If sms is null, means there was a parsing error.
        if (smsb == null) {
            Rlog.e(TAG, "dispatchMessage: message is null");
            return Intents.RESULT_SMS_GENERIC_ERROR;
        }

        String inEcm=SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE, "false");
        if (inEcm.equals("true")) {
            return Activity.RESULT_OK;
        }

        if (mSmsReceiveDisabled) {
            // Device doesn't support receiving SMS,
            Rlog.d(TAG, "Received short message on device which doesn't support "
                    + "receiving SMS. Ignored.");
            return Intents.RESULT_SMS_HANDLED;
        }

        SmsMessage sms = (SmsMessage) smsb;

        // Handle CMAS emergency broadcast messages.
        if (SmsEnvelope.MESSAGE_TYPE_BROADCAST == sms.getMessageType()) {
            Rlog.d(TAG, "Broadcast type message");
            SmsCbMessage message = sms.parseBroadcastSms();
            if (message != null) {
                dispatchBroadcastMessage(message);
            }
            return Intents.RESULT_SMS_HANDLED;
        }

        // See if we have a network duplicate SMS.
        mLastDispatchedSmsFingerprint = sms.getIncomingSmsFingerprint();
        if (mLastAcknowledgedSmsFingerprint != null &&
                Arrays.equals(mLastDispatchedSmsFingerprint, mLastAcknowledgedSmsFingerprint)) {
            return Intents.RESULT_SMS_HANDLED;
        }
        // Decode BD stream and set sms variables.
        sms.parseSms();
        int teleService = sms.getTeleService();
        boolean handled = false;

        if ((SmsEnvelope.TELESERVICE_VMN == teleService) ||
                (SmsEnvelope.TELESERVICE_MWI == teleService)) {
            // handling Voicemail
            int voicemailCount = sms.getNumOfVoicemails();
            updateMessageWaitingIndicator(sms.getNumOfVoicemails());
            handled = true;
        } else if (((SmsEnvelope.TELESERVICE_WMT == teleService) ||
                (SmsEnvelope.TELESERVICE_WEMT == teleService)) &&
                sms.isStatusReportMessage()) {
            handleCdmaStatusReport(sms);
            handled = true;
        } else if (SmsEnvelope.TELESERVICE_SCPT == teleService) {
            handleServiceCategoryProgramData(sms);
            handled = true;
        } else if ((sms.getUserData() == null)) {
            if (VDBG) {
                Rlog.d(TAG, "Received SMS without user data");
            }
            handled = true;
        }

        if (handled) {
            return Intents.RESULT_SMS_HANDLED;
        }

        if (!mStorageMonitor.isStorageAvailable() &&
                sms.getMessageClass() != SmsConstants.MessageClass.CLASS_0) {
            // It's a storable message and there's no storage available.  Bail.
            // (See C.S0015-B v2.0 for a description of "Immediate Display"
            // messages, which we represent as CLASS_0.)
            return Intents.RESULT_SMS_OUT_OF_MEMORY;
        }

        if (SmsEnvelope.TELESERVICE_WAP == teleService) {
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef,
                    sms.getOriginatingAddress());
        } else if (SmsEnvelope.TELESERVICE_CT_WAP == teleService) {
            /* China Telecom WDP header contains Message identifier
               and User data subparametrs extract these fields */
            if (!sms.processCdmaCTWdpHeader(sms)) {
                return Intents.RESULT_SMS_HANDLED;
            }
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef,
                    sms.getOriginatingAddress());
        }

        // Reject (NAK) any messages with teleservice ids that have
        // not yet been handled and also do not correspond to the two
        // kinds that are processed below.
        if ((SmsEnvelope.TELESERVICE_WMT != teleService) &&
                (SmsEnvelope.TELESERVICE_WEMT != teleService) &&
                (SmsEnvelope.MESSAGE_TYPE_BROADCAST != sms.getMessageType())) {
            return Intents.RESULT_SMS_UNSUPPORTED;
        }
        /*
         * Check to see if we have a Virgin Mobile MMS
         * Otherwise, dispatch normal message.
         */
        if (sms.getOriginatingAddress().equals("9999999999")) {
            Rlog.d(TAG, "Got a suspect SMS from the Virgin MMS originator");
                byte virginMMSPayload[] = null;
                try {
                    int[] ourMessageRef = new int[1];
                    virginMMSPayload = getVirginMMS(sms.getUserData(), ourMessageRef);
                    if (virginMMSPayload == null) {
                        Rlog.e(TAG, "Not a virgin MMS like we were expecting");
                        throw new Exception("Not a Virgin MMS like we were expecting");
                    } else {
                        Rlog.d(TAG, "Sending our deflowered MMS to processCdmaWapPdu");
                        return processCdmaWapPdu(virginMMSPayload, ourMessageRef[0], "9999999999");
                    }
                } catch (Exception ourException) {
                    Rlog.e(TAG, "Got an exception trying to get VMUS MMS data " + ourException);
                }
        }
        return dispatchNormalMessage(smsb);
    }

    private synchronized byte[] getVirginMMS(final byte[] someEncodedMMSData, int[] aMessageRef) throws Exception {
        if ((aMessageRef == null) || (aMessageRef.length != 1)) {
            throw new Exception("aMessageRef is not usable. Must be an int array with one element.");
        }
        BitwiseInputStream ourInputStream;
        int i1=0;
        int desiredBitLength;
        Rlog.d(TAG, "mmsVirginGetMsgId");
        Rlog.d(TAG, "EncodedMMS: " + someEncodedMMSData);
        try {
            ourInputStream = new BitwiseInputStream(someEncodedMMSData);
            ourInputStream.skip(20);
            final int j = ourInputStream.read(8) << 8;
            final int k = ourInputStream.read(8);
            aMessageRef[0] = j | k;
            Rlog.d(TAG, "MSGREF IS : " + aMessageRef[0]);
            ourInputStream.skip(12);
            i1 = ourInputStream.read(8) + -2;
            ourInputStream.skip(13);
            byte abyte1[] = new byte[i1];
            for (int j1 = 0; j1 < i1; j1++) {
                abyte1[j1] = 0;
            }
            desiredBitLength = i1 * 8;
            if (ourInputStream.available() < desiredBitLength) {
                int availableBitLength = ourInputStream.available();
                Rlog.e(TAG, "mmsVirginGetMsgId inStream.available() = " + availableBitLength + " wantedBits = " + desiredBitLength);
                throw new Exception("insufficient data (wanted " + desiredBitLength + " bits, but only have " + availableBitLength + ")");
            }
        } catch (com.android.internal.util.BitwiseInputStream.AccessException ourException) {
            final String ourExceptionText = "mmsVirginGetMsgId failed: " + ourException;
            Rlog.e(TAG, ourExceptionText);
            throw new Exception(ourExceptionText);
        }
        byte ret[] = null;
            try {
            ret = ourInputStream.readByteArray(desiredBitLength);
            Rlog.d(TAG, "mmsVirginGetMsgId user_length = " + i1 + " msgid = " + aMessageRef[0]);
                Rlog.d(TAG, "mmsVirginGetMsgId userdata = " + ret.toString());
            } catch (com.android.internal.util.BitwiseInputStream.AccessException ourException) {
                final String ourExceptionText = "mmsVirginGetMsgId failed: " + ourException;
                Rlog.e(TAG, ourExceptionText);
                throw new Exception(ourExceptionText);
            }
            return ret;
    }

    /*
     * This function is overloaded to send number of voicemails instead of
     * sending true/false
     */
    /* package */void updateMessageWaitingIndicator(int mwi) {
        // range check
        if (mwi < 0) {
            mwi = -1;
        } else if (mwi > 99) {
            // C.S0015-B v2, 4.5.12
            // range: 0-99
            mwi = 99;
        }
        // update voice mail count in phone
        ((PhoneBase)mPhone).setVoiceMessageCount(mwi);
        // store voice mail count in preferences
        storeVoiceMailCount();
    }

    /**
     * Processes inbound messages that are in the WAP-WDP PDU format. See
     * wap-259-wdp-20010614-a section 6.5 for details on the WAP-WDP PDU format.
     * WDP segments are gathered until a datagram completes and gets dispatched.
     *
     * @param pdu The WAP-WDP PDU segment
     * @return a result code from {@link android.provider.Telephony.Sms.Intents}, or
     *         {@link Activity#RESULT_OK} if the message has been broadcast
     *         to applications
     */
    protected int processCdmaWapPdu(byte[] pdu, int referenceNumber, String address) {
        int index = 0;

        int msgType = (0xFF & pdu[index++]);
        if (msgType != 0) {
            Rlog.w(TAG, "Received a WAP SMS which is not WDP. Discard.");
            return Intents.RESULT_SMS_HANDLED;
        }
        int totalSegments = (0xFF & pdu[index++]);   // >= 1
        int segment = (0xFF & pdu[index++]);         // >= 0

        if (segment >= totalSegments) {
            Rlog.e(TAG, "WDP bad segment #" + segment + " expecting 0-" + (totalSegments - 1));
            return Intents.RESULT_SMS_HANDLED;
        }

        // Only the first segment contains sourcePort and destination Port
        int sourcePort = 0;
        int destinationPort = 0;
        if (segment == 0) {
            //process WDP segment
            sourcePort = (0xFF & pdu[index++]) << 8;
            sourcePort |= 0xFF & pdu[index++];
            destinationPort = (0xFF & pdu[index++]) << 8;
            destinationPort |= 0xFF & pdu[index++];
            // Some carriers incorrectly send duplicate port fields in omadm wap pushes.
            // If configured, check for that here
            if (mCheckForDuplicatePortsInOmadmWapPush) {
                if (checkDuplicatePortOmadmWappush(pdu,index)) {
                    index = index + 4; // skip duplicate port fields
                }
            }
        }

        // Lookup all other related parts
        Rlog.i(TAG, "Received WAP PDU. Type = " + msgType + ", originator = " + address
                + ", src-port = " + sourcePort + ", dst-port = " + destinationPort
                + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);

        // pass the user data portion of the PDU to the shared handler in SMSDispatcher
        byte[] userData = new byte[pdu.length - index];
        System.arraycopy(pdu, index, userData, 0, pdu.length - index);

        return processMessagePart(userData, address, referenceNumber, segment, totalSegments,
                0L, destinationPort, true);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, destPort, data, (deliveryIntent != null));
        HashMap map =  SmsTrackerMapFactory(destAddr, scAddr, destPort, data, pdu);
        SmsTracker tracker = SmsTrackerFactory(map, sentIntent, deliveryIntent,
                getFormat());
        sendSubmitPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendText(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPdu(
                scAddr, destAddr, text, (deliveryIntent != null), null);
        HashMap map =  SmsTrackerMapFactory(destAddr, scAddr, text, pdu);
        SmsTracker tracker = SmsTrackerFactory(map, sentIntent,
                deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendTextWithPriority(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, int priority) {
        SmsMessage.SubmitPdu pdu = SmsMessage.getSubmitPduWithPriority(
                scAddr, destAddr, text, (deliveryIntent != null), null, priority);
        HashMap map = SmsTrackerMapFactory(destAddr, scAddr, text, pdu);
        SmsTracker tracker = SmsTrackerFactory(map, sentIntent,
                deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected GsmAlphabet.TextEncodingDetails calculateLength(CharSequence messageBody,
            boolean use7bitOnly) {
        return SmsMessage.calculateLength(messageBody, use7bitOnly);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress,
            String message, SmsHeader smsHeader, int encoding,
            PendingIntent sentIntent, PendingIntent deliveryIntent, boolean lastPart) {
        UserData uData = new UserData();
        uData.payloadStr = message;
        uData.userDataHeader = smsHeader;
        if (encoding == SmsConstants.ENCODING_7BIT) {
            uData.msgEncoding = UserData.ENCODING_GSM_7BIT_ALPHABET;
        } else { // assume UTF-16
            uData.msgEncoding = UserData.ENCODING_UNICODE_16;
        }
        uData.msgEncodingSet = true;

        /* By setting the statusReportRequested bit only for the
         * last message fragment, this will result in only one
         * callback to the sender when that last fragment delivery
         * has been acknowledged. */
        SmsMessage.SubmitPdu submitPdu = SmsMessage.getSubmitPdu(destinationAddress,
                uData, (deliveryIntent != null) && lastPart);

        HashMap map =  SmsTrackerMapFactory(destinationAddress, scAddress,
                message, submitPdu);
        SmsTracker tracker = SmsTrackerFactory(map, sentIntent,
                deliveryIntent, getFormat());
        sendSubmitPdu(tracker);
    }

    protected void sendSubmitPdu(SmsTracker tracker) {
        if (SystemProperties.getBoolean(TelephonyProperties.PROPERTY_INECM_MODE, false)) {
            if (tracker.mSentIntent != null) {
                try {
                    tracker.mSentIntent.send(SmsManager.RESULT_ERROR_NO_SERVICE);
                } catch (CanceledException ex) {}
            }
            if (VDBG) {
                Rlog.d(TAG, "Block SMS in Emergency Callback mode");
            }
            return;
        }
        sendRawPdu(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void sendSms(SmsTracker tracker) {
        HashMap<String, Object> map = tracker.mData;

        // byte smsc[] = (byte[]) map.get("smsc");  // unused for CDMA
        byte pdu[] = (byte[]) map.get("pdu");

        Message reply = obtainMessage(EVENT_SEND_SMS_COMPLETE, tracker);

        Rlog.d(TAG, "sendSms: "
                +" isIms()="+isIms()
                +" mRetryCount="+tracker.mRetryCount
                +" mImsRetry="+tracker.mImsRetry
                +" mMessageRef="+tracker.mMessageRef
                +" SS=" +mPhone.getServiceState().getState());

        // sms over cdma is used:
        //   if sms over IMS is not supported AND
        //   this is not a retry case after sms over IMS failed
        //     indicated by mImsRetry > 0
        if (0 == tracker.mImsRetry && !isIms()) {
            mCi.sendCdmaSms(pdu, reply);
        } else {
            mCi.sendImsCdmaSms(pdu, tracker.mImsRetry, tracker.mMessageRef, reply);
            // increment it here, so in case of SMS_FAIL_RETRY over IMS
            // next retry will be sent using IMS request again.
            tracker.mImsRetry++;
        }
    }

    @Override
    public void sendRetrySms(SmsTracker tracker) {
        //re-routing to ImsSMSDispatcher
        mImsSMSDispatcher.sendRetrySms(tracker);
    }

    /** {@inheritDoc} */
    @Override
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        String inEcm=SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE, "false");
        if (inEcm.equals("true")) {
            return;
        }

        int causeCode = resultToCause(result);
        mCi.acknowledgeLastIncomingCdmaSms(success, causeCode, response);

        if (causeCode == 0) {
            mLastAcknowledgedSmsFingerprint = mLastDispatchedSmsFingerprint;
        }
        mLastDispatchedSmsFingerprint = null;
    }

    private static int resultToCause(int rc) {
        switch (rc) {
        case Activity.RESULT_OK:
        case Intents.RESULT_SMS_HANDLED:
            // Cause code is ignored on success.
            return 0;
        case Intents.RESULT_SMS_OUT_OF_MEMORY:
            return CommandsInterface.CDMA_SMS_FAIL_CAUSE_RESOURCE_SHORTAGE;
        case Intents.RESULT_SMS_UNSUPPORTED:
            return CommandsInterface.CDMA_SMS_FAIL_CAUSE_INVALID_TELESERVICE_ID;
        case Intents.RESULT_SMS_GENERIC_ERROR:
        default:
            return CommandsInterface.CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM;
        }
    }

    /**
     * Optional check to see if the received WapPush is an OMADM notification with erroneous
     * extra port fields.
     * - Some carriers make this mistake.
     * ex: MSGTYPE-TotalSegments-CurrentSegment
     *       -SourcePortDestPort-SourcePortDestPort-OMADM PDU
     * @param origPdu The WAP-WDP PDU segment
     * @param index Current Index while parsing the PDU.
     * @return True if OrigPdu is OmaDM Push Message which has duplicate ports.
     *         False if OrigPdu is NOT OmaDM Push Message which has duplicate ports.
     */
    private static boolean checkDuplicatePortOmadmWappush(byte[] origPdu, int index) {
        index += 4;
        byte[] omaPdu = new byte[origPdu.length - index];
        System.arraycopy(origPdu, index, omaPdu, 0, omaPdu.length);

        WspTypeDecoder pduDecoder = new WspTypeDecoder(omaPdu);
        int wspIndex = 2;

        // Process header length field
        if (pduDecoder.decodeUintvarInteger(wspIndex) == false) {
            return false;
        }

        wspIndex += pduDecoder.getDecodedDataLength(); // advance to next field

        // Process content type field
        if (pduDecoder.decodeContentType(wspIndex) == false) {
            return false;
        }

        String mimeType = pduDecoder.getValueString();
        if (mimeType != null && mimeType.equals(WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI)) {
            return true;
        }
        return false;
    }

    /**
     * Receiver for Service Category Program Data results.
     * We already ACK'd the original SCPD SMS, so this sends a new response SMS.
     * TODO: handle retries if the RIL returns an error.
     * @hide
     */
    protected final BroadcastReceiver mScpResultsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int rc = getResultCode();
            boolean success = (rc == Activity.RESULT_OK) || (rc == Intents.RESULT_SMS_HANDLED);
            if (!success) {
                Rlog.e(TAG, "SCP results error: result code = " + rc);
                return;
            }
            Bundle extras = getResultExtras(false);
            if (extras == null) {
                Rlog.e(TAG, "SCP results error: missing extras");
                return;
            }
            String sender = extras.getString("sender");
            if (sender == null) {
                Rlog.e(TAG, "SCP results error: missing sender extra.");
                return;
            }
            ArrayList<CdmaSmsCbProgramResults> results
                    = extras.getParcelableArrayList("results");
            if (results == null) {
                Rlog.e(TAG, "SCP results error: missing results extra.");
                return;
            }

            BearerData bData = new BearerData();
            bData.messageType = BearerData.MESSAGE_TYPE_SUBMIT;
            bData.messageId = SmsMessage.getNextMessageId();
            bData.serviceCategoryProgramResults = results;
            byte[] encodedBearerData = BearerData.encode(bData);

            ByteArrayOutputStream baos = new ByteArrayOutputStream(100);
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                dos.writeInt(SmsEnvelope.TELESERVICE_SCPT);
                dos.writeInt(0); //servicePresent
                dos.writeInt(0); //serviceCategory
                CdmaSmsAddress destAddr = CdmaSmsAddress.parse(
                        PhoneNumberUtils.cdmaCheckAndProcessPlusCode(sender));
                dos.write(destAddr.digitMode);
                dos.write(destAddr.numberMode);
                dos.write(destAddr.ton); // number_type
                dos.write(destAddr.numberPlan);
                dos.write(destAddr.numberOfDigits);
                dos.write(destAddr.origBytes, 0, destAddr.origBytes.length); // digits
                // Subaddress is not supported.
                dos.write(0); //subaddressType
                dos.write(0); //subaddr_odd
                dos.write(0); //subaddr_nbr_of_digits
                dos.write(encodedBearerData.length);
                dos.write(encodedBearerData, 0, encodedBearerData.length);
                // Ignore the RIL response. TODO: implement retry if SMS send fails.
                mCi.sendCdmaSms(baos.toByteArray(), null);
            } catch (IOException e) {
                Rlog.e(TAG, "exception creating SCP results PDU", e);
            } finally {
                try {
                    dos.close();
                } catch (IOException ignored) {
                }
            }
        }
    };

    @Override
    public boolean isIms() {
        return mImsSMSDispatcher.isIms();
    }

    @Override
    public String getImsSmsFormat() {
        return mImsSMSDispatcher.getImsSmsFormat();
    }
}
