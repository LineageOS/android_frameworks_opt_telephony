/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2011-2013 The Linux Foundation. All rights reserved.
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

package android.telephony;

import android.app.ActivityThread;
import android.app.PendingIntent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;

import com.android.internal.telephony.msim.ISmsMSim;
import com.android.internal.telephony.SmsRawData;
import com.android.internal.telephony.uicc.IccConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * TODO(code review): Curious question... Why are a lot of these
 * methods not declared as static, since they do not seem to require
 * any local object state?  Presumably this cannot be changed without
 * interfering with the API...
 */

/**
 * Manages SMS operations such as sending data, text, and pdu SMS messages.
 * Get this object by calling the static method MSimSmsManager.getDefault().
 * @hide
 */
public class MSimSmsManager {
    /** Singleton object constructed during class initialization. */
    private static final MSimSmsManager sInstance = new MSimSmsManager();
    protected static boolean isMultiSimEnabled = MSimTelephonyManager.
            getDefault().isMultiSimEnabled();
    private final int DEFAULT_SUB = 0;

    /**
     * Send a text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param subscription on which the SMS has to be sent.
     *
     * @throws IllegalArgumentException if destinationAddress or text are empty
     *
     * {@hide}
     */
    public void sendTextMessage(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent,
            int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendText(ActivityThread.currentPackageName(), destinationAddress,
                        scAddress, text, sentIntent, deliveryIntent, subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Send a text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param text the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     * @param priority Priority level of the message
     * @param subscription on which the SMS has to be sent.
     *
     * @throws IllegalArgumentException if destinationAddress or text are empty
     *
     * {@hide}
     */
    public void sendTextMessageWithPriority(
            String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent,
            int priority, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (TextUtils.isEmpty(text)) {
            throw new IllegalArgumentException("Invalid message body");
        }

        if (priority < 0 || priority > 3) {
            throw new IllegalArgumentException("Invalid priority");
        }

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendTextWithPriority(destinationAddress, scAddress, text, sentIntent,
                        deliveryIntent, subscription, priority);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Divide a message text into several fragments, none bigger than
     * the maximum SMS message size.
     *
     * @param text the original message.  Must not be null.
     * @return an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     *
     * @throws IllegalArgumentException if text is null
     */
    public ArrayList<String> divideMessage(String text) {
        if (null == text) {
            throw new IllegalArgumentException("text is null");
        }
        return SmsMessage.fragmentText(text);
    }

    /**
     * Send a multi-part text based SMS.  The callee should have already
     * divided the message into correctly sized parts by calling
     * <code>divideMessage</code>.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK</code> for success,
     *   or one of these errors:<br>
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *   <code>RESULT_ERROR_RADIO_OFF</code><br>
     *   <code>RESULT_ERROR_NULL_PDU</code><br>
     *   For <code>RESULT_ERROR_GENERIC_FAILURE</code> each sentIntent may include
     *   the extra "errorCode" containing a radio technology specific value,
     *   generally only useful for troubleshooting.<br>
     *   The per-application based SMS control checks sentIntent. If sentIntent
     *   is NULL the caller will be checked against all unknown applications,
     *   which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     *   @param subscription on which the SMS has to be sent.
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.size() < 1) {
            throw new IllegalArgumentException("Invalid message body");
        }

        if (parts.size() > 1) {
            try {
                ISmsMSim iccISms = ISmsMSim.Stub.asInterface(
                        ServiceManager.getService("isms_msim"));
                if (iccISms != null) {
                    iccISms.sendMultipartText(ActivityThread.currentPackageName(),
                            destinationAddress, scAddress, parts, sentIntents, deliveryIntents,
                            subscription);
                }
            } catch (RemoteException ex) {
                // ignore it
            }
        } else {
            PendingIntent sentIntent = null;
            PendingIntent deliveryIntent = null;
            if (sentIntents != null && sentIntents.size() > 0) {
                sentIntent = sentIntents.get(0);
            }
            if (deliveryIntents != null && deliveryIntents.size() > 0) {
                deliveryIntent = deliveryIntents.get(0);
            }
            sendTextMessage(destinationAddress, scAddress, parts.get(0),
                    sentIntent, deliveryIntent, subscription);
        }
    }

    /**
     * Send a data based SMS to a specific application port.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *  the current default SMSC
     * @param destinationPort the port to deliver the message to
     * @param data the body of the message to send
     * @param sentIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is successfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK</code> for success,
     *  or one of these errors:<br>
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code><br>
     *  <code>RESULT_ERROR_RADIO_OFF</code><br>
     *  <code>RESULT_ERROR_NULL_PDU</code><br>
     *  For <code>RESULT_ERROR_GENERIC_FAILURE</code> the sentIntent may include
     *  the extra "errorCode" containing a radio technology specific value,
     *  generally only useful for troubleshooting.<br>
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applications,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>PendingIntent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     *  @param subscription on which the SMS has to be sent.
     *
     * @throws IllegalArgumentException if destinationAddress or data are empty
     */
    public void sendDataMessage(
            String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent, int subscription) {
        if (TextUtils.isEmpty(destinationAddress)) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                iccISms.sendData(ActivityThread.currentPackageName(), destinationAddress,
                        scAddress, destinationPort & 0xFFFF, data, sentIntent, deliveryIntent,
                        subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Get the default instance of the MSimSmsManager
     *
     * @return the default instance of the MSimSmsManager
     */
    public static MSimSmsManager getDefault() {
        return sInstance;
    }

    private MSimSmsManager() {
        //nothing
    }

    /**
     * Copy a raw SMS PDU to the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param smsc the SMSC for this message, or NULL for the default SMSC
     * @param pdu the raw PDU to store
     * @param status message status (STATUS_ON_ICC_READ, STATUS_ON_ICC_UNREAD,
     *               STATUS_ON_ICC_SENT, STATUS_ON_ICC_UNSENT)
     * @return true for success
     * @throws IllegalArgumentException if pdu is NULL
     *
     * {@hide}
     */
    public boolean copyMessageToIcc(byte[] smsc, byte[] pdu, int status,
            int subscription) {
        boolean success = false;

        if (null == pdu) {
            throw new IllegalArgumentException("pdu is NULL");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.copyMessageToIccEf(ActivityThread.currentPackageName(), status,
                        pdu, smsc, subscription);
           }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Delete the specified message from the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex is the record index of the message on ICC
     * @return true for success
     *
     * {@hide}
     */
    public boolean
    deleteMessageFromIcc(int messageIndex, int subscription) {
        boolean success = false;
        byte[] pdu = new byte[IccConstants.SMS_RECORD_LENGTH-1];
        Arrays.fill(pdu, (byte)0xff);

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, STATUS_ON_ICC_FREE, pdu, subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

   /**
     * Update the specified message on the ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @param messageIndex record index of message to update
     * @param newStatus new message status (STATUS_ON_ICC_READ,
     *                  STATUS_ON_ICC_UNREAD, STATUS_ON_ICC_SENT,
     *                  STATUS_ON_ICC_UNSENT, STATUS_ON_ICC_FREE)
     * @param pdu the raw PDU to store
     * @return true for success
     *
     * {@hide}
     */
    public boolean updateMessageOnIcc(int messageIndex, int newStatus,
                           byte[] pdu, int subscription) {
        boolean success = false;

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.updateMessageOnIccEf(ActivityThread.currentPackageName(),
                        messageIndex, newStatus, pdu, subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

   /**
     * Retrieves all messages currently stored on ICC.
     * ICC (Integrated Circuit Card) is the card of the device.
     * For example, this can be the SIM or USIM for GSM.
     *
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects
     *
     * {@hide}
     */
    public ArrayList<SmsMessage> getAllMessagesFromIcc(int subscription) {
        List<SmsRawData> records = null;

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                records = iccISms.getAllMessagesFromIccEf(ActivityThread.currentPackageName(),
                       subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return createMessageListFromRawRecords(records);
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier on a particular subscription.
     * Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041 (3GPP)
     * or C.R1001-G (3GPP2)
     * @return true if successful, false otherwise
     * @see #disableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean enableCellBroadcast(int messageIdentifier, int subscription) {
        boolean success = false;

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.enableCellBroadcast(messageIdentifier,
                                  subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier on a particular subscription.
     * Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param messageIdentifier Message identifier as specified in TS 23.041
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcast(int)
     *
     * {@hide}
     */
    public boolean disableCellBroadcast(int messageIdentifier, int subscription) {
        boolean success = false;

        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.disableCellBroadcast(messageIdentifier,
                                  subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Enable reception of cell broadcast (SMS-CB) messages with the given
     * message identifier range on a particular subscription.
     * Note that if two different clients enable the same
     * message identifier, they must both disable it for the device to stop
     * receiving those messages. All received messages will be broadcast in an
     * intent with the action "android.provider.Telephony.SMS_CB_RECEIVED".
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041
     * @param endMessageId last message identifier as specified in TS 23.041
     * @return true if successful, false otherwise
     * @see #disableCellBroadcastRange(int, int)
     * @throws IllegalArgumentException if endMessageId < startMessageId
     *
     * {@hide}
     */
    public boolean enableCellBroadcastRange(int startMessageId, int endMessageId,
            int subscription) {
        boolean success = false;

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.enableCellBroadcastRange(startMessageId,
                                  endMessageId, subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Disable reception of cdma broadcast messages with the given
     * message identifier range on a particular subscription.
     * Note that if two different clients enable the same
     * message identifier range, they must both disable it for the device to stop
     * receiving those messages.
     * Note: This call is blocking, callers may want to avoid calling it from
     * the main thread of an application.
     *
     * @param startMessageId first message identifier as specified in TS 23.041
     * @param endMessageId last message identifier as specified in TS 23.041
     * @return true if successful, false otherwise
     *
     * @see #enableCellBroadcastRange(int, int)
     * @throws IllegalArgumentException if endMessageId < startMessageId
     *
     * {@hide}
     */
    public boolean disableCellBroadcastRange(int startMessageId, int endMessageId,
            int subscription) {
        boolean success = false;

        if (endMessageId < startMessageId) {
            throw new IllegalArgumentException("endMessageId < startMessageId");
        }
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                success = iccISms.disableCellBroadcastRange(startMessageId,
                                  endMessageId, subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }

        return success;
    }

    /**
     * Create a list of <code>SmsMessage</code>s from a list of RawSmsData
     * records returned by <code>getAllMessagesFromIcc()</code>
     *
     * @param records SMS EF records, returned by
     *   <code>getAllMessagesFromIcc</code>
     * @return <code>ArrayList</code> of <code>SmsMessage</code> objects.
     */
    private static ArrayList<SmsMessage> createMessageListFromRawRecords(List<SmsRawData> records) {
        ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
        if (records != null) {
            int count = records.size();
            for (int i = 0; i < count; i++) {
                SmsRawData data = records.get(i);
                // List contains all records, including "free" records (null)
                if (data != null) {
                    SmsMessage sms = SmsMessage.createFromEfRecord(i+1, data.getBytes());
                    if (sms != null) {
                        messages.add(sms);
                    }
                }
            }
        }
        return messages;
    }

    /**
     * SMS over IMS is supported if IMS is registered and SMS is supported
     * on IMS.
     *
     * @return true if SMS over IMS is supported, false otherwise
     *
     * @see #getImsSmsFormat()
     *
     * @hide
     */
    boolean isImsSmsSupported(int subscription) {
        boolean boSupported = false;
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                boSupported = iccISms.isImsSmsSupported(subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return boSupported;
    }

    /**
     * Gets SMS format supported on IMS.  SMS over IMS format is
     * either 3GPP or 3GPP2.
     *
     * @return SmsMessage.FORMAT_3GPP,
     *         SmsMessage.FORMAT_3GPP2
     *      or SmsMessage.FORMAT_UNKNOWN
     *
     * @see #isImsSmsSupported()
     *
     * @hide
     */
    String getImsSmsFormat(int subscription) {
        String format = com.android.internal.telephony.SmsConstants.FORMAT_UNKNOWN;
        try {
            ISmsMSim iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            if (iccISms != null) {
                format = iccISms.getImsSmsFormat(subscription);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return format;
    }
    /**
     * Get the preferred sms subscription
     *
     * @return the preferred subscription
     * @hide
     */
    public int getPreferredSmsSubscription() {
        ISmsMSim iccISms = null;
        try {
            iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            return iccISms.getPreferredSmsSubscription();
        } catch (RemoteException ex) {
            return DEFAULT_SUB;
        } catch (NullPointerException ex) {
            return DEFAULT_SUB;
        }
    }

   /**
     * Get SMS prompt property,  enabled or not
     *
     * @return true if enabled, false otherwise
     * @hide
     */
    public boolean isSMSPromptEnabled() {
        ISmsMSim iccISms = null;
        try {
            iccISms = ISmsMSim.Stub.asInterface(ServiceManager.getService("isms_msim"));
            return iccISms.isSMSPromptEnabled();
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    // see SmsMessage.getStatusOnIcc

    /** Free space (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_FREE      = 0;

    /** Received and read (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_READ      = 1;

    /** Received and unread (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNREAD    = 3;

    /** Stored and sent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_SENT      = 5;

    /** Stored and unsent (TS 51.011 10.5.3 / 3GPP2 C.S0023 3.4.27). */
    static public final int STATUS_ON_ICC_UNSENT    = 7;

    // SMS send failure result codes

    /** Generic failure cause */
    static public final int RESULT_ERROR_GENERIC_FAILURE    = 1;
    /** Failed because radio was explicitly turned off */
    static public final int RESULT_ERROR_RADIO_OFF          = 2;
    /** Failed because no pdu provided */
    static public final int RESULT_ERROR_NULL_PDU           = 3;
    /** Failed because service is currently unavailable */
    static public final int RESULT_ERROR_NO_SERVICE         = 4;
    /** Failed because we reached the sending queue limit.
     * {@hide}
     */
    static public final int RESULT_ERROR_LIMIT_EXCEEDED     = 5;
    /** Failed because FDN is enabled.
      * {@hide}
      */
    static public final int RESULT_ERROR_FDN_CHECK_FAILURE  = 6;
}
