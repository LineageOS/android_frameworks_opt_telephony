/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.internal.telephony.IMms;

import android.app.ActivityThread;
import android.os.RemoteException;
import android.os.ServiceManager;

/**
 * This class manages messaging (SMS or MMS) related, carrier-dependent configurations.
 * Some of the values are used by the system in processing SMS or MMS messages. Others
 * are provided for the convenience of SMS applications.
 *
 * All the configurations are loaded with pre-defined values at system startup. Developers
 * can override the value of a specific configuration at runtime by calling the set methods.
 * However, those changes are not persistent and will be discarded if the managing system
 * process restarts.
 * {@hide}
 */
public class MessagingConfigurationManager {
    /** Singleton object constructed during class initialization. */
    private static final MessagingConfigurationManager sInstance =
            new MessagingConfigurationManager();

    private MessagingConfigurationManager() {}

    public static MessagingConfigurationManager getDefault() {
        return sInstance;
    }

    /**
     * Get carrier-dependent messaging configuration value as a boolean
     *
     * @param name the name of the configuration value
     * @param defaultValue the default value to return if fail to find the value
     * @return the value of the configuration
     */
    public boolean getCarrierConfigBoolean(String name, boolean defaultValue) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getCarrierConfigBoolean(name, defaultValue);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return defaultValue;
    }

    /**
     * Get carrier-dependent messaging configuration value as an int
     *
     * @param name the name of the configuration value
     * @param defaultValue the default value to return if fail to find the value
     * @return the value of the configuration
     */
    public int getCarrierConfigInt(String name, int defaultValue) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getCarrierConfigInt(name, defaultValue);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return defaultValue;
    }

    /**
     * Get carrier-dependent messaging configuration value as a string
     *
     * @param name the name of the configuration value
     * @param defaultValue the default value to return if fail to find the value
     * @return the value of the configuration
     */
    public String getCarrierConfigString(String name, String defaultValue) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                return iMms.getCarrierConfigString(name, defaultValue);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
        return defaultValue;
    }

    /**
     * Set carrier-dependent messaging configuration value as a boolean
     *
     * @param name the name of the configuration
     * @param value the value of the configuration
     */
    public void setCarrierConfigBoolean(String name, boolean value) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.setCarrierConfigBoolean(ActivityThread.currentPackageName(), name, value);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Set carrier-dependent messaging configuration value as an int
     *
     * @param name the name of the configuration
     * @param value the value of the configuration
     */
    public void setCarrierConfigInt(String name, int value) {
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.setCarrierConfigInt(ActivityThread.currentPackageName(), name, value);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Set carrier-dependent messaging configuration value as a String
     *
     * @param name the name of the configuration
     * @param value the value of the configuration
     * @throws java.lang.IllegalArgumentException if value is empty
     */
    public void setCarrierConfigString(String name, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Empty value");
        }
        try {
            IMms iMms = IMms.Stub.asInterface(ServiceManager.getService("imms"));
            if (iMms != null) {
                iMms.setCarrierConfigString(ActivityThread.currentPackageName(), name, value);
            }
        } catch (RemoteException ex) {
            // ignore it
        }
    }

    /**
     * Whether to append transaction id to MMS WAP Push M-Notification.ind's content location URI
     * when constructing the download URL of a new MMS (boolean type)
     */
    public static final String CONF_APPEND_TRANSACTION_ID = "enabledTransID";
    /**
     * Whether MMS is enabled for the current carrier (boolean type)
     */
    public static final String CONF_MMS_ENABLED = "enabledMMS";
    /**
     * If this is enabled, M-NotifyResp.ind should be sent to the WAP Push content location
     * instead of the default MMSC (boolean type)
     */
    public static final String CONF_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    /**
     * Whether alias is enabled (boolean type)
     */
    public static final String CONF_ALIAS_ENABLED = "aliasEnabled";
    /**
     * Whether audio is allowed to be attached for MMS messages (boolean type)
     */
    public static final String CONF_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    /**
     * Whether multipart SMS is enabled (boolean type)
     */
    public static final String CONF_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    /**
     * Whether SMS delivery report is enabled (boolean type)
     */
    public static final String CONF_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    /**
     * Whether content-disposition field should be expected in an MMS PDU (boolean type)
     */
    public static final String CONF_SUPPORT_MMS_CONTENT_DISPOSITION =
            "supportMmsContentDisposition";
    /**
     * Whether multipart SMS should be sent as separate messages
     */
    public static final String CONF_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES =
            "sendMultipartSmsAsSeparateMessages";
    /**
     * Whether MMS read report is enabled (boolean type)
     */
    public static final String CONF_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    /**
     * Whether MMS delivery report is enabled (boolean type)
     */
    public static final String CONF_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    /**
     * Max MMS message size in bytes (int type)
     */
    public static final String CONF_MAX_MESSAGE_SIZE = "maxMessageSize";
    /**
     * Max MMS image width (int type)
     */
    public static final String CONF_MAX_IMAGE_WIDTH = "maxImageWidth";
    /**
     * Max MMS image height (int type)
     */
    public static final String CONF_MAX_IMAGE_HEIGHT = "maxImageHeight";
    /**
     * Limit of recipients of MMS messages (int type)
     */
    public static final String CONF_RECIPIENT_LIMIT = "recipientLimit";
    /**
     * Min alias character count (int type)
     */
    public static final String CONF_ALIAS_MIN_CHARS = "aliasMinChars";
    /**
     * Max alias character count (int type)
     */
    public static final String CONF_ALIAS_MAX_CHARS = "aliasMaxChars";
    /**
     * When the number of parts of a multipart SMS reaches this threshold, it should be
     * converted into an MMS (int type)
     */
    public static final String CONF_SMS_TO_MMS_TEXT_THRESHOLD = "smsToMmsTextThreshold";
    /**
     * Some carriers require SMS to be converted into MMS when text length reaches this threshold
     * (int type)
     */
    public static final String CONF_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD =
            "smsToMmsTextLengthThreshold";
    /**
     * Max message text size (int type)
     */
    public static final String CONF_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    /**
     * Max message subject length (int type)
     */
    public static final String CONF_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    /**
     * MMS HTTP socket timeout in milliseconds (int type)
     */
    public static final String CONF_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    /**
     * The name of the UA Prof URL HTTP header for MMS HTTP request (String type)
     */
    public static final String CONF_UA_PROF_TAG_NAME = "uaProfTagName";
    /**
     * The User-Agent header value for MMS HTTP request (String type)
     */
    public static final String CONF_USER_AGENT = "userAgent";
    /**
     * The UA Profile URL header value for MMS HTTP request (String type)
     */
    public static final String CONF_UA_PROF_URL = "uaProfUrl";
    /**
     * A list of HTTP headers to add to MMS HTTP request, separated by "|" (String type)
     */
    public static final String CONF_HTTP_PARAMS = "httpParams";
    /**
     * Email gateway number (String type)
     */
    public static final String CONF_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    /**
     * The suffix to append to the NAI header value for MMS HTTP request (String type)
     */
    public static final String CONF_NAI_SUFFIX = "naiSuffix";

}
