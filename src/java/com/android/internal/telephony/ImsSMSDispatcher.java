/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony.Sms.Intents;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.TelephonyManager;
import android.telephony.SmsMessage.SubmitPdu;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.gsm.GsmInboundSmsHandler;
import com.android.internal.telephony.cdma.CdmaInboundSmsHandler;
import com.android.internal.telephony.SmsBroadcastUndelivered;

public class ImsSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "RIL_ImsSms";

    protected SMSDispatcher mCdmaDispatcher;
    protected SMSDispatcher mGsmDispatcher;
    protected GsmInboundSmsHandler mGsmInboundSmsHandler;
    protected CdmaInboundSmsHandler mCdmaInboundSmsHandler;

    private MockSmsDispatcher mMockSmsDispatcher;


    /** true if IMS is registered and sms is supported, false otherwise.*/
    private boolean mIms = false;
    private String mImsSmsFormat = SmsConstants.FORMAT_UNKNOWN;

    /**
     * true if MO SMS over IMS is enabled. Default value is true. false for
     * carriers with config_send_sms1x_on_voice_call = true when attached to
     * eHRPD and during active 1x voice call
     */
    private boolean mImsSmsEnabled = true;

    public ImsSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor) {
        super(phone, usageMonitor, null);
        Rlog.d(TAG, "ImsSMSDispatcher created");

        initDispatchers(phone, storageMonitor, usageMonitor);

        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.registerForImsNetworkStateChanged(this, EVENT_IMS_STATE_CHANGED, null);
    }

    protected void initDispatchers(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor) {
        // Create dispatchers, inbound SMS handlers and
        // broadcast undelivered messages in raw table.
        mCdmaDispatcher = new CdmaSMSDispatcher(phone, usageMonitor, this);
        mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(phone.getContext(),
                storageMonitor, phone);
        mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(phone.getContext(),
                storageMonitor, phone, (CdmaSMSDispatcher) mCdmaDispatcher);
        mGsmDispatcher = new GsmSMSDispatcher(phone, usageMonitor, this, mGsmInboundSmsHandler);
        Thread broadcastThread = new Thread(new SmsBroadcastUndelivered(phone.getContext(),
                mGsmInboundSmsHandler, mCdmaInboundSmsHandler));
        broadcastThread.start();

        // Register the mock SMS receiver to simulate the reception of SMS
        mMockSmsDispatcher = new MockSmsDispatcher();
        mMockSmsDispatcher.registerReceiver();
    }


    /* Updates the phone object when there is a change */
    @Override
    protected void updatePhoneObject(PhoneBase phone) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phone);
        mCdmaDispatcher.updatePhoneObject(phone);
        mGsmDispatcher.updatePhoneObject(phone);
        mGsmInboundSmsHandler.updatePhoneObject(phone);
        mCdmaInboundSmsHandler.updatePhoneObject(phone);
    }

    public void dispose() {
        mCi.unregisterForOn(this);
        mCi.unregisterForImsNetworkStateChanged(this);
        mGsmDispatcher.dispose();
        mCdmaDispatcher.dispose();
        mGsmInboundSmsHandler.dispose();
        mCdmaInboundSmsHandler.dispose();
        mMockSmsDispatcher.unregisterReceiver();
    }

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
        case EVENT_RADIO_ON:
        case EVENT_IMS_STATE_CHANGED: // received unsol
            mCi.getImsRegistrationState(this.obtainMessage(EVENT_IMS_STATE_DONE));
            break;

        case EVENT_IMS_STATE_DONE:
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                updateImsInfo(ar);
            } else {
                Rlog.e(TAG, "IMS State query failed with exp "
                        + ar.exception);
            }
            break;

        default:
            super.handleMessage(msg);
        }
    }

    private void setImsSmsFormat(int format) {
        // valid format?
        switch (format) {
            case PhoneConstants.PHONE_TYPE_GSM:
                mImsSmsFormat = "3gpp";
                break;
            case PhoneConstants.PHONE_TYPE_CDMA:
                mImsSmsFormat = "3gpp2";
                break;
            default:
                mImsSmsFormat = "unknown";
                break;
        }
    }

    private void updateImsInfo(AsyncResult ar) {
        int[] responseArray = (int[])ar.result;

        mIms = false;
        if (responseArray[0] == 1) {  // IMS is registered
            Rlog.d(TAG, "IMS is registered!");
            mIms = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
        }

        setImsSmsFormat(responseArray[1]);

        if (("unknown".equals(mImsSmsFormat))) {
            Rlog.e(TAG, "IMS format was unknown!");
            // failed to retrieve valid IMS SMS format info, set IMS to unregistered
            mIms = false;
        }
    }

    @Override
    protected void sendData(String destAddr, String scAddr, int destPort, int origPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isCdmaMo()) {
            mCdmaDispatcher.sendData(destAddr, scAddr, destPort, origPort,
                    data, sentIntent, deliveryIntent);
        } else {
            mGsmDispatcher.sendData(destAddr, scAddr, destPort, origPort,
                    data, sentIntent, deliveryIntent);
        }
    }

    @Override
    protected void sendMultipartText(String destAddr, String scAddr,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents, int priority) {
        if (isCdmaMo()) {
            mCdmaDispatcher.sendMultipartText(destAddr, scAddr,
                    parts, sentIntents, deliveryIntents, priority);
        } else {
            mGsmDispatcher.sendMultipartText(destAddr, scAddr,
                    parts, sentIntents, deliveryIntents, priority);
        }
    }

    @Override
    protected void sendSms(SmsTracker tracker) {
        //  sendSms is a helper function to other send functions, sendText/Data...
        //  it is not part of ISms.stub
        Rlog.e(TAG, "sendSms should never be called from here!");
    }

    @Override
    protected void sendText(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, int priority) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            mCdmaDispatcher.sendText(destAddr, scAddr,
                    text, sentIntent, deliveryIntent, priority);
        } else {
            mGsmDispatcher.sendText(destAddr, scAddr,
                    text, sentIntent, deliveryIntent, priority);
        }
    }

    @Override
    public void sendRetrySms(SmsTracker tracker) {
        String oldFormat = tracker.mFormat;

        // newFormat will be based on voice technology
        String newFormat =
            (PhoneConstants.PHONE_TYPE_CDMA == mPhone.getPhoneType()) ?
                    mCdmaDispatcher.getFormat() :
                        mGsmDispatcher.getFormat();

        // was previously sent sms format match with voice tech?
        if (oldFormat.equals(newFormat)) {
            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format matched new format (cdma)");
                mCdmaDispatcher.sendSms(tracker);
                return;
            } else {
                Rlog.d(TAG, "old format matched new format (gsm)");
                mGsmDispatcher.sendSms(tracker);
                return;
            }
        }

        // format didn't match, need to re-encode.
        HashMap map = tracker.mData;

        // to re-encode, fields needed are:  scAddr, destAddr, and
        //   text if originally sent as sendText or
        //   data and destPort if originally sent as sendData.
        if (!( map.containsKey("scAddr") && map.containsKey("destAddr") &&
               ( map.containsKey("text") ||
                       (map.containsKey("data") && map.containsKey("destPort"))))) {
            // should never come here...
            Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
            if (tracker.mSentIntent != null) {
                int error = RESULT_ERROR_GENERIC_FAILURE;
                // Done retrying; return an error to the app.
                try {
                    tracker.mSentIntent.send(mContext, error, null);
                } catch (CanceledException ex) {}
            }
            return;
        }
        String scAddr = (String)map.get("scAddr");
        String destAddr = (String)map.get("destAddr");

        SmsMessageBase.SubmitPduBase pdu = null;
        //    figure out from tracker if this was sendText/Data
        if (map.containsKey("text")) {
            Rlog.d(TAG, "sms failed was text");
            String text = (String)map.get("text");

            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(
                        scAddr, destAddr, text, (tracker.mDeliveryIntent != null), null);
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(
                        scAddr, destAddr, text, (tracker.mDeliveryIntent != null), null);
            }
        } else if (map.containsKey("data")) {
            Rlog.d(TAG, "sms failed was data");
            byte[] data = (byte[])map.get("data");
            Integer destPort = (Integer)map.get("destPort");

            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(
                            scAddr, destAddr, destPort.intValue(), data,
                            (tracker.mDeliveryIntent != null));
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(
                            scAddr, destAddr, destPort.intValue(), data,
                            (tracker.mDeliveryIntent != null));
            }
        }

        // replace old smsc and pdu with newly encoded ones
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);

        SMSDispatcher dispatcher = (isCdmaFormat(newFormat)) ?
                mCdmaDispatcher : mGsmDispatcher;

        tracker.mFormat = dispatcher.getFormat();
        dispatcher.sendSms(tracker);
    }

    @Override
    protected String getFormat() {
        // this function should be defined in Gsm/CdmaDispatcher.
        Rlog.e(TAG, "getFormat should never be called from here!");
        return "unknown";
    }

    @Override
    protected GsmAlphabet.TextEncodingDetails calculateLength(
            CharSequence messageBody, boolean use7bitOnly) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    @Override
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message,
            SmsHeader smsHeader, int format, PendingIntent sentIntent,
            PendingIntent deliveryIntent, boolean lastPart, int priority) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
    }

    @Override
    public boolean isIms() {
        return mIms;
    }

    @Override
    public String getImsSmsFormat() {
        return mImsSmsFormat;
    }

    /**
     * Determines whether or not to use CDMA format for MO SMS.
     * If SMS over IMS is supported, then format is based on IMS SMS format,
     * otherwise format is based on current phone type.
     *
     * @return true if Cdma format should be used for MO SMS, false otherwise.
     */
    private boolean isCdmaMo() {
        if (!isIms() || !shouldSendSmsOverIms()) {
            // Either IMS is not registered or there is an active 1x voice call
            // while on eHRPD, use Voice technology to determine SMS format.
            return (PhoneConstants.PHONE_TYPE_CDMA == mPhone.getPhoneType());
        }
        // IMS is registered with SMS support
        return isCdmaFormat(mImsSmsFormat);
    }

    /**
     * Determines whether or not format given is CDMA format.
     *
     * @param format
     * @return true if format given is CDMA format, false otherwise.
     */
    private boolean isCdmaFormat(String format) {
        return (mCdmaDispatcher.getFormat().equals(format));
    }

    /**
     * Enables MO SMS over IMS
     *
     * @param enable
     */
    public void enableSendSmsOverIms(boolean enable) {
        mImsSmsEnabled = enable;
    }

    /**
     * Determines whether MO SMS over IMS is currently enabled.
     *
     * @return true if MO SMS over IMS is enabled, false otherwise.
     */
    public boolean isImsSmsEnabled() {
        return mImsSmsEnabled;
    }

    /**
     * Determines whether SMS should be sent over IMS if UE is attached to eHRPD
     * and there is an active voice call
     *
     * @return true if SMS should be sent over IMS based on value in config.xml
     *         or system property false otherwise
     */
    public boolean shouldSendSmsOverIms() {
        boolean sendSmsOn1x = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_send_sms1x_on_voice_call);
        int currentCallState = mTelephonyManager.getCallState();
        int currentVoiceNetwork = mTelephonyManager.getVoiceNetworkType();
        int currentDataNetwork = mTelephonyManager.getDataNetworkType();

        Rlog.d(TAG, "data = " + currentDataNetwork + " voice = " + currentVoiceNetwork
                + " call state = " + currentCallState);

        if (sendSmsOn1x) {
            // The UE shall use 1xRTT for SMS if the UE is attached to an eHRPD
            // network and there is an active 1xRTT voice call.
            if (currentDataNetwork == TelephonyManager.NETWORK_TYPE_EHRPD
                    && currentVoiceNetwork == TelephonyManager.NETWORK_TYPE_1xRTT
                    && currentCallState != mTelephonyManager.CALL_STATE_IDLE) {
                enableSendSmsOverIms(false);
                return false;
            }
        }
        return true;
    }


    /**
     * A private class that allow simulating the receive of SMS.<br/>
     * <br/>
     * A developer must use {@link Context#sendBroadcast(Intent)}, using the action
     * {@link Intents#MOCK_SMS_RECEIVED_ACTION}. The application requires
     * {@linkplain "android.permission.SEND_MOCK_SMS"} permission.<br/>
     * <br/>
     * This receiver should be used in the next way:<br/>
     * <pre>
     * Intent in = new Intent(Intents.MOCK_SMS_RECEIVED_ACTION);
     * in.putExtra("scAddr", "+01123456789");
     * in.putExtra("senderAddr", "+01123456789");
     * in.putExtra("msg", "This is a mock SMS message.");
     * sendBroadcast(in);
     * </pre><br/>
     * or<br/>
     * <pre>
     * String pdu = "07914151551512f2040B916105551511f100006060605130308A04D4F29C0E";
     * byte[][] pdus = new byte[1][];
     * pdus[0] = HexDump.hexStringToByteArray(pdu);
     * Intent in = new Intent(Intents.MOCK_SMS_RECEIVED_ACTION);
     * intent.putExtra("pdus", pdus);
     * sendBroadcast(in);
     * </pre><br/>
     */
    private final class MockSmsDispatcher extends BroadcastReceiver {
        private static final String TAG = "MockSmsReceiver";

        private static final String MOCK_ADDRESS = "+01123456789";

        private static final String SEND_MOCK_SMS_PERMISSION =
                                        "android.permission.SEND_MOCK_SMS";

        /**
         * Method that register the MockSmsReceiver class as a BroadcastReceiver
         */
        public final void registerReceiver() {
            try {
                Handler handler = new Handler();
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intents.MOCK_SMS_RECEIVED_ACTION);
                mContext.registerReceiver(this, filter, SEND_MOCK_SMS_PERMISSION, handler);
                Log.d(TAG, "Registered MockSmsReceiver");
            } catch (Exception ex) {
                Log.e(TAG, "Failed to register MockSmsReceiver", ex);
            }
        }

        /**
         * Method that unregister the MockSmsReceiver class as a BroadcastReceiver
         */
        public final void unregisterReceiver() {
            try {
                mContext.unregisterReceiver(this);
            } catch (Exception ex) {
                Log.e(TAG, "Failed to unregister MockSmsReceiver", ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public final void onReceive(Context context, Intent intent) {
            Log.d(TAG, "New mock SMS reception request. Intent: " + intent);
            String action = intent.getAction();
            if (!Intents.MOCK_SMS_RECEIVED_ACTION.equals(action)) {
                return;
            }

            try {
                // Check that developer option is enabled, and mock
                // messages are allowed
                boolean allowMockSMS = Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.ALLOW_MOCK_SMS, 0) == 1;
                if (!allowMockSMS) {
                    // Mock SMS is not allowed.
                    Log.w(TAG, "Mock SMS is not allowed. Enable Mock SMS on " +
                            "Settings/Delevelopment.");
                    return;
                }

                // Check that the device supports the telephony subsystem
                PackageManager packageManager = mContext.getPackageManager();
                if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                    // Telephony is not supported
                    Log.w(TAG, "Mock SMS is not allowed because telephony is not supported.");
                    return;
                }

                // Extract PDUs
                List<byte[][]> msgs = new ArrayList<byte[][]>();
                Object[] messages = (Object[]) intent.getSerializableExtra("pdus");
                if (messages != null && messages.length > 0) {
                    // Use the PDUs from the intent
                    byte[][] pdus = new byte[messages.length][];
                    for (int i = 0; i < messages.length; i++) {
                        pdus[i] = (byte[]) messages[i];
                    }
                    msgs.add(pdus);

                } else {
                    // Build the PDUs from SMS data
                    String scAddress = intent.getStringExtra("scAddr");
                    String senderAddress = intent.getStringExtra("senderAddr");
                    String msg = intent.getStringExtra("msg");

                    // Check that values are valid. Otherwise fill will default values
                    if (TextUtils.isEmpty(scAddress)) {
                        scAddress = MOCK_ADDRESS;
                    }
                    if (TextUtils.isEmpty(senderAddress)) {
                        senderAddress = MOCK_ADDRESS;
                    }
                    if (TextUtils.isEmpty(msg)) {
                        msg = "This is a mock SMS message.";
                    }
                    Log.d(TAG,
                            String.format(
                                    "Mock SMS. scAddress: %s, senderAddress: %s, msg: %s",
                                    scAddress, senderAddress, msg));

                    // Fragment the text in message according to SMS length
                    List<String> fragmentMsgs = android.telephony.SmsMessage.fragmentText(msg);
                    for (String fragmentMsg : fragmentMsgs) {
                        msgs.add(getPdus(scAddress, senderAddress, fragmentMsg));
                    }
                }

                // How messages are going to send?
                Log.d(TAG, String.format("Mock SMS. Number of msg: %d", msgs.size()));

                // Send messages
                for (byte[][] pdus : msgs) {
                    dispatch(pdus, android.telephony.SmsMessage.FORMAT_3GPP);
                }

            } catch (Exception ex) {
                Log.e(TAG, "Failed to dispatch SMS", ex);
            }
        }

        private void dispatch(byte[][] pdus, String format) {
            Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);

            // Direct the intent to only the default SMS app. If we can't find a default SMS app
            // then sent it to all broadcast receivers.
            ComponentName componentName = SmsApplication.getDefaultSmsApplication(mContext, true);
            if (componentName != null) {
                // Deliver SMS message only to this receiver
                intent.setComponent(componentName);
                Log.w(TAG, "Delivering SMS to: " + componentName.getPackageName() +
                        " " + componentName.getClassName());
            }

            intent.putExtra("pdus", pdus);
            intent.putExtra("format", format);
            intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
            mContext.sendOrderedBroadcast(intent, Manifest.permission.RECEIVE_SMS,
                    AppOpsManager.OP_RECEIVE_SMS, null, null, Activity.RESULT_OK, null, null);
        }

        /**
         * Method that convert the basic SMS string data to a PDUs messages
         *
         * @param scAddress The mock the SC address
         * @param senderAddress The mock the sender address
         * @param msg The mock message body
         * @return byte[] The array of bytes of the PDU
         */
        private byte[][] getPdus(String scAddress, String senderAddress, String msg) {

            // Get a SubmitPdu (use a phone number to get a valid pdu)
            SubmitPdu submitPdu =
                    android.telephony.SmsMessage.getSubmitPdu(
                                                        scAddress,
                                                        MOCK_ADDRESS,
                                                        msg,
                                                        false);

            // Translate the submit data to a received PDU
            int dataLen = android.telephony.SmsMessage.calculateLength(msg, true)[1];

            // Locate protocol + data encoding scheme
            byte[] pds = {(byte)0, (byte)0, (byte)dataLen};
            int dataPos = new String(submitPdu.encodedMessage).indexOf(new String(pds), 4) + 2;

            // Set arrays dimension
            byte[] encSc = submitPdu.encodedScAddress;
            byte[] encMsg = new byte[submitPdu.encodedMessage.length - dataPos];
            System.arraycopy(
                    submitPdu.encodedMessage, dataPos,
                    encMsg, 0, submitPdu.encodedMessage.length - dataPos);
            byte[] encSender = null;
            // Check if the senderAddress is a vanish number
            if (!PhoneNumberUtils.isWellFormedSmsAddress(senderAddress)) {
                try {
                    byte[] sender7BitPacked = GsmAlphabet.stringToGsm7BitPacked(senderAddress);
                    encSender = new byte[2 + sender7BitPacked.length - 1];
                    encSender[0] = (byte)((sender7BitPacked.length - 1) * 2);
                    encSender[1] = (byte)0xD0; // Alphabetic sender
                    System.arraycopy(sender7BitPacked, 1, encSender, 2, sender7BitPacked.length - 1);
                } catch (EncodeException e) {
                    Log.e(TAG, "Failed to decode sender address. Using default.", e);
                    encSender = new byte[dataPos - 4];
                    System.arraycopy(
                            submitPdu.encodedMessage, 2,
                            encSender, 0, dataPos - 4);
                }
            } else {
                encSender = new byte[dataPos - 4];
                System.arraycopy(
                        submitPdu.encodedMessage, 2,
                        encSender, 0, dataPos - 4);
            }
            byte[] encTs = bcdTimestamp();
            byte[] pdu = new byte[
                                  encSc.length +
                                  1 +       /** SMS-DELIVER **/
                                  encSender.length +
                                  2 +       /** Protocol + Data Encoding Scheme **/
                                  encTs.length +
                                  encMsg.length];

            // Copy the SC address
            int c = 0;
            System.arraycopy(encSc, 0, pdu, c, encSc.length);
            c+=encSc.length;
            // SMS-DELIVER
            pdu[c] = 0x04;
            c++;
            // Sender
            System.arraycopy(encSender, 0, pdu, c, encSender.length);
            c+=encSender.length;
            // Protocol + Data encoding scheme
            pdu[c] = 0x00;
            c++;
            pdu[c] = 0x00;
            c++;
            // Timestamp
            System.arraycopy(encTs, 0, pdu, c, encTs.length);
            c+=encTs.length;
            // Message
            System.arraycopy(encMsg, 0, pdu, c, encMsg.length);

            // Return the PDUs
            return new byte[][]{pdu};
        }

        /**
         * Method that return the current timestamp in a BCD format
         *
         * @return byte[] The BCD timestamp
         */
        private byte[] bcdTimestamp() {
            Calendar c = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("yy"); //$NON-NLS-1$
            SimpleDateFormat sdf2 = new SimpleDateFormat("Z"); //$NON-NLS-1$
            byte year = (byte)Integer.parseInt(
                            String.valueOf(Integer.parseInt(sdf.format(c.getTime()))), 16);
            byte month = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.MONTH) + 1), 16);
            byte day = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.DAY_OF_MONTH)), 16);
            byte hour = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.HOUR)), 16);
            byte minute = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.MINUTE)), 16);
            byte second = (byte)Integer.parseInt(String.valueOf(c.get(Calendar.SECOND)), 16);
            String tz = sdf2.format(c.getTime()).substring(1);
            int timezone = Integer.parseInt(tz) / 100;
            if (timezone < 0) {
                timezone += 0x80;
            }
            byte[] data = {year, month, day, hour, minute, second, 0};
            byte[] ts = IccUtils.hexStringToBytes(IccUtils.bcdToString(data, 0, data.length));
            ts[6] = (byte)Integer.parseInt(String.valueOf(timezone), 16);
            return ts;
        }
    }
}
