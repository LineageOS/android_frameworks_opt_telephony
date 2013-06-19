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
        this.originatingAddress = new SyntheticAddress();
        this.originatingAddress.address = originatingAddress;

        this.messageBody = messageBody;
        this.scTimeMillis = timestampMillis;
        this.scAddress = scAddress;

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
