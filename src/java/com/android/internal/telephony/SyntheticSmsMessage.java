/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import org.json.JSONObject;

import com.android.internal.telephony.SmsConstants.MessageClass;

public class SyntheticSmsMessage extends SmsMessageBase {
    public static class SyntheticAddress extends SmsAddress {
    }

    public static boolean isSyntheticPdu(byte[] pdu) {
        try {
            JSONObject json = new JSONObject(new String(pdu));
            return json.optBoolean("synthetic", false);
        }
        catch (Exception e) {
        }
        return false;
    }

    public static SyntheticSmsMessage createFromPdu(byte[] pdu) {
        try {
            // TODO: use Parcelable or Bundle or something that serializes?
            JSONObject json = new JSONObject(new String(pdu));
            SyntheticSmsMessage message = new SyntheticSmsMessage(
                    json.getString("originatingAddress"),
                    json.optString("scAddress", null),
                    json.getString("messageBody"),
                    json.getLong("timestampMillis"));
            return message;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public SyntheticSmsMessage(String originatingAddress, String scAddress, String messageBody, long timestampMillis) {
        this.mOriginatingAddress = new SyntheticAddress();
        this.mOriginatingAddress.address = originatingAddress;

        this.mMessageBody = messageBody;
        this.mScTimeMillis = timestampMillis;
        this.mScAddress = scAddress;

        try {
            JSONObject json = new JSONObject();
            json.put("originatingAddress", originatingAddress);
            json.put("scAddress", scAddress);
            json.put("messageBody", messageBody);
            json.put("timestampMillis", timestampMillis);
            json.put("synthetic", true);
            this.mPdu = json.toString().getBytes();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public MessageClass getMessageClass() {
        return SmsConstants.MessageClass.UNKNOWN;
    }

    @Override
    public int getProtocolIdentifier() {
        return 0;
    }

    @Override
    public boolean isReplace() {
        return false;
    }

    @Override
    public boolean isCphsMwiMessage() {
        return false;
    }

    @Override
    public boolean isMWIClearMessage() {
        return false;
    }

    @Override
    public boolean isMWISetMessage() {
        return false;
    }

    @Override
    public boolean isMwiDontStore() {
        return false;
    }

    @Override
    public int getStatus() {
        return 0;
    }

    @Override
    public boolean isStatusReportMessage() {
        return false;
    }

    @Override
    public boolean isReplyPathPresent() {
        return false;
    }
}