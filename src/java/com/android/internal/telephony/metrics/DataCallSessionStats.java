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

import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_HANDOVER;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_NORMAL;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_RADIO_OFF;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__IP_TYPE__APN_PROTOCOL_IPV4;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION__PROFILE__DATA_PROFILE_DEFAULT;

import android.telephony.Annotation.ApnType;
import android.telephony.Annotation.NetworkType;
import android.telephony.DataFailCause;
import android.telephony.ServiceState;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnSetting.ProtocolType;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataService;
import android.telephony.data.DataService.DeactivateDataReason;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.uicc.UiccSlot;
import com.android.telephony.Rlog;

import java.util.Random;

/** Collects data call change events per DcTracker for the pulled atom. */
public class DataCallSessionStats {
    private static final String TAG = DataCallSessionStats.class.getSimpleName();

    private final Phone mPhone;
    private long mStartTime;
    DataCallSession mOngoingDataCall;

    private final PersistAtomsStorage mAtomsStorage =
            PhoneFactory.getMetricsCollector().getAtomsStorage();

    private static final Random RANDOM = new Random();

    public DataCallSessionStats(Phone phone) {
        if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) {
            mPhone = phone.getDefaultPhone();
        } else {
            mPhone = phone;
        }
    }

    /**
     * create a new ongoing atom when data cal is set up
     */
    public synchronized void onSetupDataCall() {
        // there shouldn't be an ongoing dataCall here, if that's the case, it means that
        // deactivateDataCall hasn't been processed properly, so we save the previous atom here
        // and move on.
        if (mOngoingDataCall != null && mOngoingDataCall.ongoing) {
            mOngoingDataCall.failureCause = DataFailCause.UNKNOWN;
            mOngoingDataCall.durationMinutes =
                    convertMillisToMinutes(System.currentTimeMillis() - mStartTime);
            mOngoingDataCall.ongoing = false;
            mAtomsStorage.addDataCallSession(mOngoingDataCall);
        }
        mOngoingDataCall = getDefaultProto();
    }

    /**
     * update the ongoing dataCall's atom for data call response event
     *
     * @param response setup Data call response
     * @param radioTechnology The data call RAT
     * @param profileId Data profile id
     * @param apnTypeBitmask APN type bitmask
     * @param protocol Data connection protocol
     */
    public synchronized void onSetupDataCallResponse(DataCallResponse response,
            @NetworkType int radioTechnology, int profileId,
            @ApnType int apnTypeBitmask, @ProtocolType int protocol) {
        // there should've been another call to initiate the atom,
        // so this method is being called out of order -> no metric will be logged
        if (mOngoingDataCall == null) {
            loge("onSetupDataCallResponse: no DataCallSession atom has been initiated.");
            return;
        }
        mOngoingDataCall.ratAtEnd =
                ServiceState.rilRadioTechnologyToAccessNetworkType(radioTechnology);
        mOngoingDataCall.profile = profileId;
        mOngoingDataCall.apnTypeBitmask = apnTypeBitmask;
        mOngoingDataCall.ipType = protocol;
        mStartTime = System.currentTimeMillis();
        if (response != null) {
            if (response.getCause() == 0) {
                mOngoingDataCall.failureCause = DataFailCause.NONE;
            } else {
                mOngoingDataCall.failureCause = response.getCause();
                mOngoingDataCall.setupFailed = true;
                // set dataCall as inactive
                mOngoingDataCall.ongoing = false;
                // store it only if setup has failed
                mAtomsStorage.addDataCallSession(mOngoingDataCall);
            }
            mOngoingDataCall.suggestedRetryMillis = response.getSuggestedRetryTime();
        }
    }

    /**
     * update the ongoing dataCall's atom when data call is deactivated
     *
     * @param reason Deactivate reason
     */
    public void setDeactivateDataCallReason(@DeactivateDataReason int reason) {
        // there should've been another call to initiate the atom,
        // so this method is being called out of order -> no metric will be logged
        if (mOngoingDataCall == null) {
            loge("onSetupDataCallResponse: no DataCallSession atom has been initiated.");
            return;
        }
        switch (reason) {
            case DataService.REQUEST_REASON_NORMAL:
                mOngoingDataCall.deactivateReason =
                        DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_NORMAL;
                break;
            case DataService.REQUEST_REASON_SHUTDOWN:
                mOngoingDataCall.deactivateReason =
                        DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_RADIO_OFF;
                break;
            case DataService.REQUEST_REASON_HANDOVER:
                mOngoingDataCall.deactivateReason =
                        DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_HANDOVER;
                break;
            default:
                mOngoingDataCall.deactivateReason =
                        DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_UNKNOWN;
                mOngoingDataCall.oosAtEnd = true;
        }
    }

    /**
     * store the atom, when DataConnection reaches DISCONNECTED state
     * @param cid              Context Id, uniquely identifies the call
     */
    public void onDataCallDisconnected(int cid) {
        // there should've been another call to initiate the atom,
        // so this method is being called out of order -> no atom will be saved
        if (mOngoingDataCall == null) {
            loge("onSetupDataCallResponse: no DataCallSession atom has been initiated.");
            return;
        }
        mOngoingDataCall.carrierId = cid;
        mOngoingDataCall.ongoing = false;
        mOngoingDataCall.durationMinutes =
                convertMillisToMinutes(System.currentTimeMillis() - mStartTime);
        // store for the data call list event, after DataCall is disconnected and entered into
        // inactive mode
        mAtomsStorage.addDataCallSession(mOngoingDataCall);
    }

    /** Updates this RAT when it changes. */
    public synchronized void onRatChanged(@NetworkType int rat) {
        // if no data call is initiated, or we have a new data call while the last one has ended
        // because onRatChanged might be called before onSetupDataCall
        if (mOngoingDataCall == null || !mOngoingDataCall.ongoing) {
            mOngoingDataCall = getDefaultProto();
        }
        if (mOngoingDataCall.ratAtEnd != rat) {
            mOngoingDataCall.ratSwitchCount++;
            mOngoingDataCall.ratAtEnd = rat;
        }
    }

    private static long convertMillisToMinutes(long millis) {
        return Math.round(millis / 60000);
    }

    /** Creates a proto for a normal {@code DataCallSession} with default values. */
    private DataCallSession getDefaultProto() {
        DataCallSession proto = new DataCallSession();
        proto.dimension = RANDOM.nextInt();
        proto.isMultiSim = isMultiSim();
        proto.isEsim = isEsim();
        proto.profile = DATA_CALL_SESSION__PROFILE__DATA_PROFILE_DEFAULT;
        proto.apnTypeBitmask = ApnSetting.TYPE_NONE;
        proto.carrierId = getCarrierId();
        proto.isRoaming = getIsRoaming();
        proto.oosAtEnd = false;
        proto.ratSwitchCount = 0L;
        proto.isOpportunistic = getIsOpportunistic();
        proto.ipType = DATA_CALL_SESSION__IP_TYPE__APN_PROTOCOL_IPV4;
        proto.setupFailed = false;
        proto.failureCause = DataFailCause.NONE;
        proto.suggestedRetryMillis = 0;
        proto.deactivateReason = DATA_CALL_SESSION__DEACTIVATE_REASON__DEACTIVATE_REASON_UNKNOWN;
        proto.durationMinutes = 0;
        proto.ongoing = true;
        return proto;
    }

    private boolean isMultiSim() {
        return SimSlotState.getCurrentState().numActiveSims > 1;
    }

    private boolean isEsim() {
        UiccController uiccController = UiccController.getInstance();
        int slotId = uiccController.getSlotIdFromPhoneId(mPhone.getPhoneId());
        UiccSlot slot = uiccController.getUiccSlot(slotId);
        if (slot != null) {
            return slot.isEuicc();
        } else {
            // should not happen, but assume we are not using eSIM
            loge("isEsim: slot %d is null", slotId);
            return false;
        }
    }

    private boolean getIsRoaming() {
        ServiceStateTracker serviceStateTracker = mPhone.getServiceStateTracker();
        ServiceState serviceState =
                serviceStateTracker != null ? serviceStateTracker.getServiceState() : null;
        return serviceState != null ? serviceState.getRoaming() : false;
    }

    private boolean getIsOpportunistic() {
        SubscriptionController subController = SubscriptionController.getInstance();
        return subController != null ? subController.isOpportunistic(mPhone.getSubId()) : false;
    }

    private int getCarrierId() {
        return mPhone.getCarrierId();
    }

    private void loge(String format, Object... args) {
        Rlog.e(TAG, "[" + mPhone.getPhoneId() + "]" + String.format(format, args));
    }
}
