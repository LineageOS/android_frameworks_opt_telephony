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
 * limitations under the License
 */
package com.android.internal.telephony;

import android.content.Context;
import android.content.Intent;
import android.provider.VoicemailContract;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.telephony.VisualVoicemailSmsFilterSettings;
import android.util.Log;

import com.android.internal.telephony.VisualVoicemailSmsParser.WrappedMessageData;

import java.nio.charset.StandardCharsets;

public class VisualVoicemailSmsFilter {

    private static final String TAG = "VvmSmsFilter";

    private static final String SYSTEM_VVM_CLIENT_PACKAGE = "com.android.phone";

    /**
     * Attempt to parse the incoming SMS as a visual voicemail SMS. If the parsing succeeded, A
     * {@link VoicemailContract.ACTION_VOICEMAIL_SMS_RECEIVED} intent will be sent to the visual
     * voicemail client, and the SMS should be dropped.
     *
     * <p>The accepted format for a visual voicemail SMS is a generalization of the OMTP format:
     *
     * <p>[clientPrefix]:[prefix]:([key]=[value];)*
     *
     * @return true if the SMS has been parsed to be a visual voicemail SMS and should be dropped
     */
    public static boolean filter(Context context, byte[][] pdus, String format, int destPort,
            int subId) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        // TODO: select client package.
        String vvmClientPackage = SYSTEM_VVM_CLIENT_PACKAGE;

        VisualVoicemailSmsFilterSettings settings =
                telephonyManager.getVisualVoicemailSmsFilterSettings(vvmClientPackage, subId);
        if (settings == null) {
            return false;
        }
        // TODO: filter base on originating number and destination port.

        String messageBody = getFullMessage(pdus, format);

        if(messageBody == null){
            // Verizon WAP push SMS is not recognized by android, which has a ascii PDU.
            // Attempt to parse it.
            Log.i(TAG, "Unparsable SMS received");
            String asciiMessage = parseAsciiPduMessage(pdus);
            WrappedMessageData messageData = VisualVoicemailSmsParser
                .parseAlternativeFormat(asciiMessage);
            if (messageData != null) {
                sendVvmSmsBroadcast(context, vvmClientPackage, subId, messageData);
            }
            // Confidence for what the message actually is is low. Don't remove the message and let
            // system decide. Usually because it is not parsable it will be dropped.
            return false;
        } else {
            String clientPrefix = settings.clientPrefix;
            WrappedMessageData messageData = VisualVoicemailSmsParser
                .parse(clientPrefix, messageBody);
            if (messageData != null) {
                sendVvmSmsBroadcast(context, vvmClientPackage, subId, messageData);
                return true;
            }
        }
        return false;
    }

    private static void sendVvmSmsBroadcast(Context context, String vvmClientPackage, int subId,
        WrappedMessageData messageData) {
        Log.i(TAG, "VVM SMS received");
        Intent intent = new Intent(VoicemailContract.ACTION_VOICEMAIL_SMS_RECEIVED);
        intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_PREFIX, messageData.prefix);
        intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS, messageData.fields);
        intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID, subId);
        intent.setPackage(vvmClientPackage);
        context.sendBroadcast(intent);
    }

    private static String getFullMessage(byte[][] pdus, String format) {
        StringBuilder builder = new StringBuilder();
        for (byte pdu[] : pdus) {
            SmsMessage message =SmsMessage.createFromPdu(pdu, format);

            if (message == null) {
                // The PDU is not recognized by android
                return null;
            }
            String body = message.getMessageBody();
            if (body != null) {
                builder.append(body);
            }
        }
        return builder.toString();
    }

    private static String parseAsciiPduMessage(byte[][] pdus) {
        StringBuilder builder = new StringBuilder();
        for (byte pdu[] : pdus) {
            builder.append(new String(pdu, StandardCharsets.US_ASCII));
        }
        return builder.toString();
    }
}
