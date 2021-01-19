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

import android.os.SystemClock;
import android.telephony.Annotation.ApnType;
import android.telephony.Annotation.NetworkType;
import android.telephony.DataFailCause;
import android.telephony.ServiceState;
import android.telephony.ServiceState.RilRadioTechnology;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting.ProtocolType;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataService;
import android.telephony.data.DataService.DeactivateDataReason;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;
import com.android.telephony.Rlog;

import java.util.Random;

/** Collects data call change events per DataConnection for the pulled atom. */
public class DataCallSessionStats {
    private static final String TAG = DataCallSessionStats.class.getSimpleName();

    private final Phone mPhone;
    private long mStartTime;
    private boolean mOnRatChangedCalledBeforeSetup = false;
    DataCallSession mOngoingDataCall;

    private final PersistAtomsStorage mAtomsStorage =
            PhoneFactory.getMetricsCollector().getAtomsStorage();

    private static final Random RANDOM = new Random();

    public DataCallSessionStats(Phone phone) {
        mPhone = phone;
    }

    /** Creates a new ongoing atom when data call is set up. */
    public synchronized void onSetupDataCall(@ApnType int apnTypeBitMask) {
        if (!mOnRatChangedCalledBeforeSetup) {
            // there shouldn't be an ongoing data call here, if that's the case, it means that
            // deactivateDataCall hasn't been processed properly, so we save the previous atom here
            // and move on to create a new atom.
            if (mOngoingDataCall != null) {
                mOngoingDataCall.failureCause = DataFailCause.UNKNOWN;
                onDataCallDisconnected();
            }
            mOngoingDataCall = getDefaultProto(apnTypeBitMask);
            mStartTime = getTimeMillis();
        } else {
            // if onRatChanged was called before onSetupDataCall, the atom is already initialized
            // but apnTypeBitMask is initialized to 0, so we need to update it
            mOngoingDataCall.apnTypeBitmask = apnTypeBitMask;
        }
        mOnRatChangedCalledBeforeSetup = false;
    }

    /**
     * Updates the ongoing dataCall's atom for data call response event.
     *
     * @param response setup Data call response
     * @param radioTechnology The data call RAT
     * @param apnTypeBitmask APN type bitmask
     * @param protocol Data connection protocol
     * @param failureCause failure cause as per android.telephony.DataFailCause
     */
    public synchronized void onSetupDataCallResponse(
            DataCallResponse response,
            @RilRadioTechnology int radioTechnology,
            @ApnType int apnTypeBitmask,
            @ProtocolType int protocol,
            int failureCause) {
        // there should've been another call to initiate the atom,
        // so this method is being called out of order -> no metric will be logged
        if (mOngoingDataCall == null) {
            loge("onSetupDataCallResponse: no DataCallSession atom has been initiated.");
            return;
        }
        mOngoingDataCall.ratAtEnd = ServiceState.rilRadioTechnologyToNetworkType(radioTechnology);

        // only set if apn hasn't been set during setup
        if (mOngoingDataCall.apnTypeBitmask == 0) {
            mOngoingDataCall.apnTypeBitmask = apnTypeBitmask;
        }

        mOngoingDataCall.ipType = protocol;
        mOngoingDataCall.failureCause = failureCause;
        if (response != null) {
            mOngoingDataCall.suggestedRetryMillis =
                    (int) Math.min(response.getRetryDurationMillis(), Integer.MAX_VALUE);
            if (failureCause != DataFailCause.NONE) {
                mOngoingDataCall.failureCause = failureCause;
                mOngoingDataCall.setupFailed = true;
                // set dataCall as inactive
                mOngoingDataCall.ongoing = false;
                // store it only if setup has failed
                mAtomsStorage.addDataCallSession(mOngoingDataCall);
                mOngoingDataCall = null;
            }
        }
    }

    /**
     * Updates the ongoing dataCall's atom when data call is deactivated.
     *
     * @param reason Deactivate reason
     */
    public synchronized void setDeactivateDataCallReason(@DeactivateDataReason int reason) {
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
                break;
        }

        mOngoingDataCall.oosAtEnd = getIsOos();
    }

    /** Stores the atom when DataConnection reaches DISCONNECTED state. */
    public synchronized void onDataCallDisconnected() {
        // there should've been another call to initiate the atom,
        // so this method is being called out of order -> no atom will be saved
        if (mOngoingDataCall == null) {
            loge("onSetupDataCallResponse: no DataCallSession atom has been initiated.");
            return;
        }
        mOngoingDataCall.ongoing = false;
        mOngoingDataCall.durationMinutes = convertMillisToMinutes(getTimeMillis() - mStartTime);
        // store for the data call list event, after DataCall is disconnected and entered into
        // inactive mode
        mAtomsStorage.addDataCallSession(mOngoingDataCall);
        mOngoingDataCall = null;
    }

    /**
     * Updates the atom when data registration state or RAT changes.
     *
     * <p>NOTE: in {@link ServiceStateTracker}, change of channel number will trigger data
     * registration state change.
     */
    public synchronized void onDrsOrRatChanged(@RilRadioTechnology int radioTechnology) {
        @NetworkType int rat = ServiceState.rilRadioTechnologyToNetworkType(radioTechnology);
        // if no data call is initiated, or we have a new data call while the last one has ended
        // because onRatChanged might be called before onSetupDataCall
        if (mOngoingDataCall == null) {
            mOngoingDataCall = getDefaultProto(0);
            mOngoingDataCall.ratAtEnd = rat;
            mStartTime = getTimeMillis();
            mOnRatChangedCalledBeforeSetup = true;
        }
        if (rat != TelephonyManager.NETWORK_TYPE_UNKNOWN && mOngoingDataCall.ratAtEnd != rat) {
            mOngoingDataCall.ratSwitchCount++;
            mOngoingDataCall.ratAtEnd = rat;
        }
        mOngoingDataCall.bandAtEnd = ServiceStateStats.getBand(mPhone, rat);
    }

    private static long convertMillisToMinutes(long millis) {
        return Math.round(millis / 60000);
    }

    /** Creates a proto for a normal {@code DataCallSession} with default values. */
    private DataCallSession getDefaultProto(@ApnType int apnTypeBitmask) {
        DataCallSession proto = new DataCallSession();
        proto.dimension = RANDOM.nextInt();
        proto.isMultiSim = SimSlotState.isMultiSim();
        proto.isEsim = SimSlotState.isEsim(mPhone.getPhoneId());
        proto.apnTypeBitmask = apnTypeBitmask;
        proto.carrierId = mPhone.getCarrierId();
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

    private boolean getIsOos() {
        ServiceStateTracker serviceStateTracker = mPhone.getServiceStateTracker();
        ServiceState serviceState =
                serviceStateTracker != null ? serviceStateTracker.getServiceState() : null;
        return serviceState != null
                ? serviceState.getDataRegistrationState() == ServiceState.STATE_OUT_OF_SERVICE
                : false;
    }

    private void loge(String format, Object... args) {
        Rlog.e(TAG, "[" + mPhone.getPhoneId() + "]" + String.format(format, args));
    }

    @VisibleForTesting
    protected long getTimeMillis() {
        return SystemClock.elapsedRealtime();
    }
}
