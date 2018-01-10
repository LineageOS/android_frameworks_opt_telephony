/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.internal.telephony.util;

import com.android.internal.telephony.ImsSmsDispatcher;
import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.GsmAlphabet.TextEncodingDetails;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;

/**
 * Utilities used by {@link com.android.internal.telephony.SMSDispatcher}'s subclasses.
 *
 * These methods can not be moved to {@link CdmaSMSDispatcher} and {@link GsmSMSDispatcher} because
 * they also need to be called from {@link ImsSmsDispatcher} and the utilities will invoke the cdma
 * or gsm version of the method based on the format.
 */
public final class SMSDispatcherUtil {
    // Prevent instantiation.
    private SMSDispatcherUtil() {}

    /**
     * Whether to block SMS or not.
     *
     * @param isCdma true if cdma format should be used.
     * @param phone the Phone to use
     * @return true to block sms; false otherwise.
     */
    public static boolean shouldBlockSms(boolean isCdma, Phone phone) {
        return isCdma && phone.isInEcm();
    }

    /**
     * Trigger the proper implementation for getting submit pdu for text sms based on format.
     *
     * @param isCdma true if cdma format should be used.
     * @param scAddr is the service center address or null to use the current default SMSC
     * @param destAddr the address to send the message to
     * @param message the body of the message.
     * @param statusReportRequested whether or not a status report is requested.
     * @param smsHeader message header.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPdu(boolean isCdma, String scAddr,
            String destAddr, String message, boolean statusReportRequested, SmsHeader smsHeader) {
        if (isCdma) {
            return getSubmitPduCdma(scAddr, destAddr, message, statusReportRequested, smsHeader);
        } else {
            return getSubmitPduGsm(scAddr, destAddr, message, statusReportRequested);
        }
    }

    /**
     * Gsm implementation for
     * {@link #getSubmitPdu(boolean, String, String, String, boolean, SmsHeader)}
     *
     * @param scAddr is the service center address or null to use the current default SMSC
     * @param destAddr the address to send the message to
     * @param message the body of the message.
     * @param statusReportRequested whether or not a status report is requested.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPduGsm(String scAddr, String destAddr,
            String message, boolean statusReportRequested) {
        return com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr, message,
                statusReportRequested);
    }

    /**
     * Cdma implementation for
     * {@link #getSubmitPdu(boolean, String, String, String, boolean, SmsHeader)}
     *
     *  @param scAddr is the service center address or null to use the current default SMSC
     * @param destAddr the address to send the message to
     * @param message the body of the message.
     * @param statusReportRequested whether or not a status report is requested.
     * @param smsHeader message header.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPduCdma(String scAddr, String destAddr,
            String message, boolean statusReportRequested, SmsHeader smsHeader) {
        return com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr,
                message, statusReportRequested, smsHeader);
    }

    /**
     * Trigger the proper implementation for getting submit pdu for data sms based on format.
     *
     * @param isCdma true if cdma format should be used.
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use the current default SMSC
     * @param destPort the port to deliver the message to
     * @param message the body of the message to send
     * @param statusReportRequested whether or not a status report is requested.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPdu(boolean isCdma, String scAddr,
            String destAddr, int destPort, byte[] message, boolean statusReportRequested) {
        if (isCdma) {
            return getSubmitPduCdma(scAddr, destAddr, destPort, message, statusReportRequested);
        } else {
            return getSubmitPduGsm(scAddr, destAddr, destPort, message, statusReportRequested);
        }
    }

    /**
     * Cdma implementation of {@link #getSubmitPdu(boolean, String, String, int, byte[], boolean)}

     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use the current default SMSC
     * @param destPort the port to deliver the message to
     * @param message the body of the message to send
     * @param statusReportRequested whether or not a status report is requested.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPduCdma(String scAddr, String destAddr,
            int destPort, byte[] message, boolean statusReportRequested) {
        return com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(scAddr, destAddr,
                destPort, message, statusReportRequested);
    }

    /**
     * Gsm implementation of {@link #getSubmitPdu(boolean, String, String, int, byte[], boolean)}
     *
     * @param destAddr the address to send the message to
     * @param scAddr is the service center address or null to use the current default SMSC
     * @param destPort the port to deliver the message to
     * @param message the body of the message to send
     * @param statusReportRequested whether or not a status report is requested.
     * @return the submit pdu.
     */
    public static SmsMessageBase.SubmitPduBase getSubmitPduGsm(String scAddr, String destAddr,
            int destPort, byte[] message, boolean statusReportRequested) {
        return com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(scAddr, destAddr,
                destPort, message, statusReportRequested);

    }

    /**
     * Calculate the number of septets needed to encode the message. This function should only be
     * called for individual segments of multipart message.
     *
     * @param isCdma  true if cdma format should be used.
     * @param messageBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    public static TextEncodingDetails calculateLength(boolean isCdma, CharSequence messageBody,
            boolean use7bitOnly) {
        if (isCdma) {
            return calculateLengthCdma(messageBody, use7bitOnly);
        } else {
            return calculateLengthGsm(messageBody, use7bitOnly);
        }
    }

    /**
     * Gsm implementation for {@link #calculateLength(boolean, CharSequence, boolean)}
     *
     * @param messageBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    public static TextEncodingDetails calculateLengthGsm(CharSequence messageBody,
            boolean use7bitOnly) {
        return com.android.internal.telephony.cdma.SmsMessage.calculateLength(messageBody,
                use7bitOnly, false);

    }

    /**
     * Cdma implementation for {@link #calculateLength(boolean, CharSequence, boolean)}
     *
     * @param messageBody the message to encode
     * @param use7bitOnly ignore (but still count) illegal characters if true
     * @return TextEncodingDetails
     */
    public static TextEncodingDetails calculateLengthCdma(CharSequence messageBody,
            boolean use7bitOnly) {
        return com.android.internal.telephony.gsm.SmsMessage.calculateLength(messageBody,
                use7bitOnly);
    }
}
