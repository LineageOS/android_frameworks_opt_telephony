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

package com.android.internal.telephony.cdma;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Telephony.Sms.Intents;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsCbMessage;

import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.WspTypeDecoder;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.HexDump;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subclass of {@link InboundSmsHandler} for 3GPP2 type messages.
 */
public class CdmaInboundSmsHandler extends InboundSmsHandler {

    private final CdmaSMSDispatcher mSmsDispatcher;
    private final CdmaServiceCategoryProgramHandler mServiceCategoryProgramHandler;

    private byte[] mLastDispatchedSmsFingerprint;
    private byte[] mLastAcknowledgedSmsFingerprint;

    private final boolean mCheckForDuplicatePortsInOmadmWapPush = Resources.getSystem().getBoolean(
            com.android.internal.R.bool.config_duplicate_port_omadm_wappush);

    private boolean mSprintMwiQuirk;

    /**
     * Create a new inbound SMS handler for CDMA.
     */
    private CdmaInboundSmsHandler(Context context, SmsStorageMonitor storageMonitor,
            Phone phone, CdmaSMSDispatcher smsDispatcher) {
        super("CdmaInboundSmsHandler", context, storageMonitor, phone,
                CellBroadcastHandler.makeCellBroadcastHandler(context, phone));
        mSmsDispatcher = smsDispatcher;
        mServiceCategoryProgramHandler = CdmaServiceCategoryProgramHandler.makeScpHandler(context,
                phone.mCi);
        phone.mCi.setOnNewCdmaSms(getHandler(), EVENT_NEW_SMS, null);

        CarrierConfigManager ccm = (CarrierConfigManager)
            context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        if (ccm != null) {
            PersistableBundle cc = ccm.getConfigForSubId(phone.getSubId());
            if (cc != null) {
                if (cc.getBoolean("sprint_mwi_quirk")) {
                    mSprintMwiQuirk = true;
                    String prefName = String.valueOf(phone.getSubId()) + "sprint_vm_count";
                    int voicemailCount = PreferenceManager.getDefaultSharedPreferences(context)
                            .getInt(prefName, 0);
                    phone.setVoiceMessageCount(voicemailCount);
                }
            }
        }
    }

    /**
     * Unregister for CDMA SMS.
     */
    @Override
    protected void onQuitting() {
        mPhone.mCi.unSetOnNewCdmaSms(getHandler());
        mCellBroadcastHandler.dispose();

        if (DBG) log("unregistered for 3GPP2 SMS");
        super.onQuitting();
    }

    /**
     * Wait for state machine to enter startup state. We can't send any messages until then.
     */
    public static CdmaInboundSmsHandler makeInboundSmsHandler(Context context,
            SmsStorageMonitor storageMonitor, Phone phone, CdmaSMSDispatcher smsDispatcher) {
        CdmaInboundSmsHandler handler = new CdmaInboundSmsHandler(context, storageMonitor,
                phone, smsDispatcher);
        handler.start();
        return handler;
    }

    /**
     * Return true if this handler is for 3GPP2 messages; false for 3GPP format.
     * @return true (3GPP2)
     */
    @Override
    protected boolean is3gpp2() {
        return true;
    }

    /**
     * Process Cell Broadcast, Voicemail Notification, and other 3GPP/3GPP2-specific messages.
     * @param smsb the SmsMessageBase object from the RIL
     * @return true if the message was handled here; false to continue processing
     */
    @Override
    protected int dispatchMessageRadioSpecific(SmsMessageBase smsb) {
        SmsMessage sms = (SmsMessage) smsb;
        boolean isBroadcastType = (SmsEnvelope.MESSAGE_TYPE_BROADCAST == sms.getMessageType());

        // Handle CMAS emergency broadcast messages.
        if (isBroadcastType) {
            log("Broadcast type message");
            SmsCbMessage cbMessage = sms.parseBroadcastSms();
            if (cbMessage != null) {
                mCellBroadcastHandler.dispatchSmsMessage(cbMessage);
            } else {
                loge("error trying to parse broadcast SMS");
            }
            return Intents.RESULT_SMS_HANDLED;
        }

        // Initialize fingerprint field, and see if we have a network duplicate SMS.
        mLastDispatchedSmsFingerprint = sms.getIncomingSmsFingerprint();
        if (mLastAcknowledgedSmsFingerprint != null &&
                Arrays.equals(mLastDispatchedSmsFingerprint, mLastAcknowledgedSmsFingerprint)) {
            return Intents.RESULT_SMS_HANDLED;
        }

        // Decode BD stream and set sms variables.
        sms.parseSms();
        int teleService = sms.getTeleService();

        // We handle Sprint's VVM SMS messages instead of their
        // normal MWI messages, which get stuck in their system
        // This has the limitation of only keeping track of
        // voicemails received after a fresh install, but /shrug
        if (mSprintMwiQuirk) {
            String address = sms.getOriginatingAddress();
            if (address != null && address.equals("9016")) {
                String message = sms.getMessageBody();
                if (message != null) {
                    // Sprint's VVM app on stock intercepts SMS messages that notify it
                    // of changes in the voicemail system, like receiving a new message
                    // or marking a message as read, and the SMS is hidden from the user
                    // On Lineage, these texts show up as normal SMS messages from the
                    // number 9016, but there is a pattern to them that we can interpret
                    // On receiving a new message, the system sends out two messages
                    // The first one looks something like:
                    // "//ANDROID:Message from 16125648499. MMSN,dbbd64a3b92,1806,V67r6VjpJab//CM"
                    // The second one looks something like:
                    // "//ANDROID:Message from 16125648499. MMSN,dbbd64ab2ca,400,V68kYv7wC04//CM"
                    // On marking a message as read, the system sends out one messge
                    // The message looks something like:
                    // "//ANDROID:Message from *@vvm.coremobility.com. MMSN,dbbd653a3ca,41f,M68r4hSjahF//CM"
                    // The pattern is described below, using the second message that
                    // is received when receiving a new voicemail as the example
                    // "//ANDROID:Message from" - The message header, present
                    //                            in all messages from the system
                    //                            This is consumed by the regex
                    // " " - This is consumed by the regex
                    // "16125648499" - The origin address, this is the phone number
                    //                 that left the voicemail if it's a new message,
                    //                 or "*@vvm.coremobility.com" for "service"
                    //                 messages like marking a message as read
                    //                 This is available as group 1 of the matcher
                    // ". " - This is consumed by the regex
                    // "MMSN" - This is a constant in all the messages, the meaning
                    //          of which is unclear at this time of this writing
                    //          This is consumed by the regex
                    // "," - This is consumed by the regex
                    // "dbbd64ab2ca" - The timestamp, this is a unix timestamp
                    //                 encoded as a hex string, that is multiplied
                    //                 by 10000, and offset by 8 hours
                    //                 The offset seems to represent the PST timezone,
                    //                 which is used in California, where the HQ of the
                    //                 the company who wrote the VVM app, CoreMobilty, is
                    //                 This is available as group 2 of the matcher
                    // "," - This is consumed by the regex
                    // "400" - The command block, this is a 3 digit hex string for
                    //         the second message received on a new voicemail, and
                    //         for the message received on a voicemail read message,
                    //         while it's a 4 digit hex string for the first message
                    //         received on receiving a new voicemail message
                    //         For the first message received on a new voicemail,
                    //         this is seemingly random, but for the second one
                    //         it's always "400", and for the message received on
                    //         a voicemail that has been read, it's always "41f"
                    //         This is available as group 4 of the matcher
                    // "," - This is consumed by the regex
                    // "V68" - The second command block, the 3 chars after the first
                    //         command block are seemingly related to first command
                    //         When the first command is the "random" 4 digit hex,
                    //         this block is "V67", then when the command is "400",
                    //         this block is "V68", and when the command is "41f",
                    //         this block is "M68"
                    //         This is available as group 4 of the matcher
                    // "kYv7wC04" - The "garbage" block, the chars after the second
                    //              command block to the beginning of the footer
                    //              have seemingly no pattern of significance
                    //              This is available as group 5 of the matcher
                    // "//CM" - The footer block, this is present in all messages,
                    //          and represents the abbreviation of "CoreMobility",
                    //          which is the company that wrote Sprint's VVM app
                    //          This is consumed by the regex
                    Pattern sprintVvmPattern = Pattern.compile(
                            "//ANDROID:Message from (.*)\\. MMSN,(.*),(.*),(...)(.*)//CM");
                    Matcher sprintVvmMatcher = sprintVvmPattern.matcher(message);

                    if (sprintVvmMatcher.matches()) {
                        String prefName = String.valueOf(mPhone.getSubId()) + "sprint_vm_count";
                        int voicemailCount = PreferenceManager.getDefaultSharedPreferences(mContext)
                                .getInt(prefName, 0);

                        if (sprintVvmMatcher.group(3).equals("400") && voicemailCount < 99) {
                            voicemailCount++;
                        } else if (sprintVvmMatcher.group(3).equals("41f") && voicemailCount > 0) {
                            voicemailCount--;
                        }

                        mPhone.setVoiceMessageCount(voicemailCount);
                        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                                .putInt(prefName, voicemailCount).apply();
                        return Intents.RESULT_SMS_HANDLED;
                    }
                }
            }
        }


        switch (teleService) {
            case SmsEnvelope.TELESERVICE_VMN:
            case SmsEnvelope.TELESERVICE_MWI:
                // handle voicemail indication
                if (!mSprintMwiQuirk) {
                    handleVoicemailTeleservice(sms);
                }
                return Intents.RESULT_SMS_HANDLED;

            case SmsEnvelope.TELESERVICE_WMT:
            case SmsEnvelope.TELESERVICE_WEMT:
                if (sms.isStatusReportMessage()) {
                    mSmsDispatcher.sendStatusReportMessage(sms);
                    return Intents.RESULT_SMS_HANDLED;
                }
                break;

            case SmsEnvelope.TELESERVICE_SCPT:
                mServiceCategoryProgramHandler.dispatchSmsMessage(sms);
                return Intents.RESULT_SMS_HANDLED;

            case SmsEnvelope.TELESERVICE_WAP:
                // handled below, after storage check
                break;

            case SmsEnvelope.TELESERVICE_CT_WAP:
                // handled below, after TELESERVICE_WAP
                break;

            default:
                loge("unsupported teleservice 0x" + Integer.toHexString(teleService));
                return Intents.RESULT_SMS_UNSUPPORTED;
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
                    sms.getOriginatingAddress(), sms.getDisplayOriginatingAddress(),
                    sms.getTimestampMillis());
        } else if (SmsEnvelope.TELESERVICE_CT_WAP == teleService) {
            /* China Telecom WDP header contains Message identifier
               and User data subparametrs extract these fields */
            if (!sms.processCdmaCTWdpHeader(sms)) {
                return Intents.RESULT_SMS_HANDLED;
            }
            return processCdmaWapPdu(sms.getUserData(), sms.mMessageRef,
                    sms.getOriginatingAddress(), sms.getDisplayOriginatingAddress(),
                    sms.getTimestampMillis());
        }

        return dispatchNormalMessage(smsb);
    }

    /**
     * Send an acknowledge message.
     * @param success indicates that last message was successfully received.
     * @param result result code indicating any error
     * @param response callback message sent when operation completes.
     */
    @Override
    protected void acknowledgeLastIncomingSms(boolean success, int result, Message response) {
        int causeCode = resultToCause(result);
        mPhone.mCi.acknowledgeLastIncomingCdmaSms(success, causeCode, response);

        if (causeCode == 0) {
            mLastAcknowledgedSmsFingerprint = mLastDispatchedSmsFingerprint;
        }
        mLastDispatchedSmsFingerprint = null;
    }

    /**
     * Called when the phone changes the default method updates mPhone
     * mStorageMonitor and mCellBroadcastHandler.updatePhoneObject.
     * Override if different or other behavior is desired.
     *
     * @param phone
     */
    @Override
    protected void onUpdatePhoneObject(Phone phone) {
        super.onUpdatePhoneObject(phone);
        mCellBroadcastHandler.updatePhoneObject(phone);
    }

    /**
     * Convert Android result code to CDMA SMS failure cause.
     * @param rc the Android SMS intent result value
     * @return 0 for success, or a CDMA SMS failure cause value
     */
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
            return CommandsInterface.CDMA_SMS_FAIL_CAUSE_OTHER_TERMINAL_PROBLEM;
        }
    }

    /**
     * Handle {@link SmsEnvelope#TELESERVICE_VMN} and {@link SmsEnvelope#TELESERVICE_MWI}.
     * @param sms the message to process
     */
    private void handleVoicemailTeleservice(SmsMessage sms) {
        int voicemailCount = sms.getNumOfVoicemails();
        if (DBG) log("Voicemail count=" + voicemailCount);

        // range check
        if (voicemailCount < 0) {
            voicemailCount = -1;
        } else if (voicemailCount > 99) {
            // C.S0015-B v2, 4.5.12
            // range: 0-99
            voicemailCount = 99;
        }
        // update voice mail count in phone
        mPhone.setVoiceMessageCount(voicemailCount);
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
    private int processCdmaWapPdu(byte[] pdu, int referenceNumber, String address, String dispAddr,
            long timestamp) {
        int index = 0;

        int msgType = (0xFF & pdu[index++]);
        if (msgType != 0) {
            log("Received a WAP SMS which is not WDP. Discard.");
            return Intents.RESULT_SMS_HANDLED;
        }
        int totalSegments = (0xFF & pdu[index++]);   // >= 1
        int segment = (0xFF & pdu[index++]);         // >= 0

        if (segment >= totalSegments) {
            loge("WDP bad segment #" + segment + " expecting 0-" + (totalSegments - 1));
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
                if (checkDuplicatePortOmadmWapPush(pdu, index)) {
                    index = index + 4; // skip duplicate port fields
                }
            }
        }

        // Lookup all other related parts
        log("Received WAP PDU. Type = " + msgType + ", originator = " + address
                + ", src-port = " + sourcePort + ", dst-port = " + destinationPort
                + ", ID = " + referenceNumber + ", segment# = " + segment + '/' + totalSegments);

        // pass the user data portion of the PDU to the shared handler in SMSDispatcher
        byte[] userData = new byte[pdu.length - index];
        System.arraycopy(pdu, index, userData, 0, pdu.length - index);

        InboundSmsTracker tracker = TelephonyComponentFactory.getInstance().makeInboundSmsTracker(
                userData, timestamp, destinationPort, true, address, dispAddr, referenceNumber,
                segment, totalSegments, true, HexDump.toHexString(userData));

        // de-duping is done only for text messages
        return addTrackerToRawTableAndSendMessage(tracker, false /* don't de-dup */);
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
    private static boolean checkDuplicatePortOmadmWapPush(byte[] origPdu, int index) {
        index += 4;
        byte[] omaPdu = new byte[origPdu.length - index];
        System.arraycopy(origPdu, index, omaPdu, 0, omaPdu.length);

        WspTypeDecoder pduDecoder = new WspTypeDecoder(omaPdu);
        int wspIndex = 2;

        // Process header length field
        if (!pduDecoder.decodeUintvarInteger(wspIndex)) {
            return false;
        }

        wspIndex += pduDecoder.getDecodedDataLength();  // advance to next field

        // Process content type field
        if (!pduDecoder.decodeContentType(wspIndex)) {
            return false;
        }

        String mimeType = pduDecoder.getValueString();
        return (WspTypeDecoder.CONTENT_TYPE_B_PUSH_SYNCML_NOTI.equals(mimeType));
    }
}
