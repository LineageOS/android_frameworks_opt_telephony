/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.metrics;

import static com.android.internal.telephony.InboundSmsHandler.SOURCE_INJECTED_FROM_IMS;
import static com.android.internal.telephony.InboundSmsHandler.SOURCE_INJECTED_FROM_UNKNOWN;
import static com.android.internal.telephony.InboundSmsHandler.SOURCE_NOT_INJECTED;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__ERROR__SMS_ERROR_GENERIC;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__ERROR__SMS_ERROR_NOT_SUPPORTED;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__ERROR__SMS_ERROR_NO_MEMORY;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__ERROR__SMS_SUCCESS;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_FORMAT__SMS_FORMAT_3GPP;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_FORMAT__SMS_FORMAT_3GPP2;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TECH__SMS_TECH_CS_3GPP;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TECH__SMS_TECH_CS_3GPP2;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TECH__SMS_TECH_IMS;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TECH__SMS_TECH_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TYPE__SMS_TYPE_NORMAL;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TYPE__SMS_TYPE_SMS_PP;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TYPE__SMS_TYPE_VOICEMAIL_INDICATION;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TYPE__SMS_TYPE_WAP_PUSH;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS__SMS_TYPE__SMS_TYPE_ZERO;

import android.app.Activity;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Annotation.NetworkType;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.stub.ImsRegistrationImplBase;

import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.nano.PersistAtomsProto.IncomingSms;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccSlot;
import com.android.telephony.Rlog;


import java.util.Random;

/** Collects voice call events per phone ID for the pulled atom. */
public class SmsStats {
    private static final String TAG = SmsStats.class.getSimpleName();

    private final Phone mPhone;

    private final PersistAtomsStorage mAtomsStorage =
            PhoneFactory.getMetricsCollector().getAtomsStorage();

    private static final Random RANDOM = new Random();

    public SmsStats(Phone phone) {
        mPhone = phone;
    }

    /** Create a new atom when multi-part incoming SMS is dropped due to missing parts. */
    public void onDroppedIncomingMultipartSms(boolean is3gpp2, int receivedCount, int totalCount) {
        IncomingSms proto = getDefaultProto(is3gpp2, SOURCE_NOT_INJECTED);
        // Keep SMS tech as unknown because it's possible that it changed overtime and is not
        // necessarily the current one. Similarly mark the RAT as unknown.
        proto.smsTech = INCOMING_SMS__SMS_TECH__SMS_TECH_UNKNOWN;
        proto.rat = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        proto.error = INCOMING_SMS__ERROR__SMS_ERROR_GENERIC;
        proto.totalParts = totalCount;
        proto.receivedParts = receivedCount;
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an SMS for the voicemail indicator is received. */
    public void onIncomingSmsVoicemail(boolean is3gpp2,
            @InboundSmsHandler.SmsSource int smsSource) {
        IncomingSms proto = getDefaultProto(is3gpp2, smsSource);
        proto.smsType = INCOMING_SMS__SMS_TYPE__SMS_TYPE_VOICEMAIL_INDICATION;
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an SMS of type zero is received. */
    public void onIncomingSmsTypeZero(@InboundSmsHandler.SmsSource int smsSource) {
        IncomingSms proto = getDefaultProto(false /* is3gpp2 */, smsSource);
        proto.smsType = INCOMING_SMS__SMS_TYPE__SMS_TYPE_ZERO;
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an SMS-PP for the SIM card is received. */
    public void onIncomingSmsPP(@InboundSmsHandler.SmsSource int smsSource, boolean success) {
        IncomingSms proto = getDefaultProto(false /* if3gpp2 */, smsSource);
        proto.smsType = INCOMING_SMS__SMS_TYPE__SMS_TYPE_SMS_PP;
        proto.error = getError(success);
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an SMS is received successfully. */
    public void onIncomingSmsSuccess(boolean is3gpp2,
            @InboundSmsHandler.SmsSource int smsSource, int messageCount,
            boolean blocked, long messageId) {
        IncomingSms proto = getDefaultProto(is3gpp2, smsSource);
        proto.totalParts = messageCount;
        proto.receivedParts = messageCount;
        proto.blocked = blocked;
        proto.messageId = messageId;
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an incoming SMS has an error. */
    public void onIncomingSmsError(boolean is3gpp2,
            @InboundSmsHandler.SmsSource int smsSource, int result) {
        IncomingSms proto = getDefaultProto(is3gpp2, smsSource);
        proto.error = getError(result);
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Create a new atom when an incoming WAP_PUSH SMS is received. */
    public void onIncomingSmsWapPush(@InboundSmsHandler.SmsSource int smsSource,
            int messageCount, int result, long messageId) {
        IncomingSms proto = getDefaultProto(false, smsSource);
        proto.smsType = INCOMING_SMS__SMS_TYPE__SMS_TYPE_WAP_PUSH;
        proto.totalParts = messageCount;
        proto.receivedParts = messageCount;
        proto.error = getError(result);
        proto.messageId = messageId;
        mAtomsStorage.addIncomingSms(proto);
    }

    /** Creates a proto for a normal single-part {@code IncomingSms} with default values. */
    private IncomingSms getDefaultProto(boolean is3gpp2,
            @InboundSmsHandler.SmsSource int smsSource) {
        IncomingSms proto = new IncomingSms();
        proto.smsFormat = getSmsFormat(is3gpp2);
        proto.smsTech = getSmsTech(smsSource, is3gpp2);
        proto.rat = getRat(smsSource);
        proto.smsType = INCOMING_SMS__SMS_TYPE__SMS_TYPE_NORMAL;
        proto.totalParts = 1;
        proto.receivedParts = 1;
        proto.blocked = false;
        proto.error = INCOMING_SMS__ERROR__SMS_SUCCESS;
        proto.isRoaming = getIsRoaming();
        proto.simSlotIndex = getSimSlotId();
        proto.isMultiSim = SimSlotState.getCurrentState().numActiveSims > 1;
        proto.isEsim = isEsim();
        proto.carrierId = getCarrierId();
        // Message ID is initialized with random number, as it is not available for all incoming
        // SMS messages (e.g. those handled by OS or error cases).
        proto.messageId = RANDOM.nextLong();
        return proto;
    }

    private static int getSmsFormat(boolean is3gpp2) {
        if (is3gpp2) {
            return INCOMING_SMS__SMS_FORMAT__SMS_FORMAT_3GPP2;
        } else {
            return INCOMING_SMS__SMS_FORMAT__SMS_FORMAT_3GPP;
        }
    }

    private int getSmsTech(@InboundSmsHandler.SmsSource int smsSource, boolean is3gpp2) {
        if (smsSource == SOURCE_INJECTED_FROM_IMS) {
            return INCOMING_SMS__SMS_TECH__SMS_TECH_IMS;
        } else if (smsSource == SOURCE_NOT_INJECTED) {
            if (is3gpp2) {
                return INCOMING_SMS__SMS_TECH__SMS_TECH_CS_3GPP2;
            } else {
                return INCOMING_SMS__SMS_TECH__SMS_TECH_CS_3GPP;
            }
        } else {
            return INCOMING_SMS__SMS_TECH__SMS_TECH_UNKNOWN;
        }
    }

    private static int getError(int result) {
        switch (result) {
            case Activity.RESULT_OK:
            case Intents.RESULT_SMS_HANDLED:
                return INCOMING_SMS__ERROR__SMS_SUCCESS;
            case Intents.RESULT_SMS_OUT_OF_MEMORY:
                return INCOMING_SMS__ERROR__SMS_ERROR_NO_MEMORY;
            case Intents.RESULT_SMS_UNSUPPORTED:
                return INCOMING_SMS__ERROR__SMS_ERROR_NOT_SUPPORTED;
            case Intents.RESULT_SMS_GENERIC_ERROR:
            default:
                return INCOMING_SMS__ERROR__SMS_ERROR_GENERIC;
        }
    }

    private static int getError(boolean success) {
        if (success) {
            return INCOMING_SMS__ERROR__SMS_SUCCESS;
        } else {
            return INCOMING_SMS__ERROR__SMS_ERROR_GENERIC;
        }
    }

    private boolean isEsim() {
        int slotId = getSimSlotId();
        UiccSlot slot = UiccController.getInstance().getUiccSlot(slotId);
        if (slot != null) {
            return slot.isEuicc();
        } else {
            // should not happen, but assume we are not using eSIM
            loge("isEsim: slot %d is null", slotId);
            return false;
        }
    }

    private int getSimSlotId() {
        // NOTE: UiccController's mapping hasn't be initialized when Phone was created
        return UiccController.getInstance().getSlotIdFromPhoneId(mPhone.getPhoneId());
    }

    private ServiceState getServiceState() {
        Phone phone = mPhone;
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
            phone = mPhone.getDefaultPhone();
        }
        ServiceStateTracker serviceStateTracker = phone.getServiceStateTracker();
        return serviceStateTracker != null ? serviceStateTracker.getServiceState() : null;
    }

    private @NetworkType int getRat(@InboundSmsHandler.SmsSource int smsSource) {
        if (smsSource == SOURCE_INJECTED_FROM_UNKNOWN) {
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } else if (smsSource == SOURCE_INJECTED_FROM_IMS) {
            if (mPhone.getImsRegistrationTech()
                    == ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN) {
                return TelephonyManager.NETWORK_TYPE_IWLAN;
            }
        }
        // TODO(b/168837897): Returns the RAT at the time the SMS was received..
        ServiceState serviceState = getServiceState();
        return serviceState != null ? serviceState.getVoiceNetworkType() : null;
    }

    private boolean getIsRoaming() {
        ServiceState serviceState = getServiceState();
        return serviceState != null ? serviceState.getRoaming() : false;
    }

    private int getCarrierId() {
        Phone phone = mPhone;
        if (mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
            phone = mPhone.getDefaultPhone();
        }
        return phone.getCarrierId();
    }

    private void loge(String format, Object... args) {
        Rlog.e(TAG, "[" + mPhone.getPhoneId() + "]" + String.format(format, args));
    }
}
