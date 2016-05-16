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

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.VoicemailContract;
import android.telephony.TelephonyManager;
import android.util.Log;

import android.telephony.SmsMessage;

import com.android.internal.telephony.VisualVoicemailSmsParser.WrappedMessageData;

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
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);

        // TODO: select client package.
        String vvmClientPackage = SYSTEM_VVM_CLIENT_PACKAGE;

        if (telephonyManager.isVisualVoicemailSmsFilterEnabled(vvmClientPackage, subId) == false) {
            return false;
        }

        // TODO: filter base on originating number and destination port.

        String messageBody = getFullMessage(pdus, format);
        String clientPrefix = telephonyManager
                .getVisualVoicemailSmsFilterClientPrefix(vvmClientPackage, subId);

        WrappedMessageData messageData = VisualVoicemailSmsParser.parse(clientPrefix, messageBody);
        if (messageData != null) {
            Log.i(TAG, "VVM SMS received");
            Intent intent = new Intent(VoicemailContract.ACTION_VOICEMAIL_SMS_RECEIVED);
            intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_PREFIX, messageData.prefix);
            intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_FIELDS, messageData.fields);
            intent.putExtra(VoicemailContract.EXTRA_VOICEMAIL_SMS_SUBID, subId);
            intent.setPackage(vvmClientPackage);
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    private static String getFullMessage(byte[][] pdus, String format) {
        StringBuilder builder = new StringBuilder();
        for (byte pdu[] : pdus) {
            String body = SmsMessage.createFromPdu(pdu, format).getMessageBody();
            if (body != null) {
                builder.append(body);
            }
        }
        return builder.toString();
    }
}
