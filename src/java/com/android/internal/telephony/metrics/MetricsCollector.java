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

import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static com.android.internal.telephony.TelephonyStatsLog.CARRIER_ID_TABLE_VERSION;
import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_DATA_SERVICE_SWITCH;
import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_SERVICE_STATE;
import static com.android.internal.telephony.TelephonyStatsLog.DATA_CALL_SESSION;
import static com.android.internal.telephony.TelephonyStatsLog.IMS_REGISTRATION_STATS;
import static com.android.internal.telephony.TelephonyStatsLog.IMS_REGISTRATION_TERMINATION;
import static com.android.internal.telephony.TelephonyStatsLog.INCOMING_SMS;
import static com.android.internal.telephony.TelephonyStatsLog.OUTGOING_SMS;
import static com.android.internal.telephony.TelephonyStatsLog.SIM_SLOT_STATE;
import static com.android.internal.telephony.TelephonyStatsLog.SUPPORTED_RADIO_ACCESS_FAMILY;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_RAT_USAGE;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION;

import android.annotation.Nullable;
import android.app.StatsManager;
import android.content.Context;
import android.util.StatsEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationTermination;
import com.android.internal.telephony.nano.PersistAtomsProto.IncomingSms;
import com.android.internal.telephony.nano.PersistAtomsProto.OutgoingSms;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.internal.util.ConcurrentUtils;
import com.android.telephony.Rlog;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Implements statsd pullers for Telephony.
 *
 * <p>This class registers pullers to statsd, which will be called once a day to obtain telephony
 * statistics that cannot be sent to statsd in real time.
 */
public class MetricsCollector implements StatsManager.StatsPullAtomCallback {
    private static final String TAG = MetricsCollector.class.getSimpleName();

    /** Disables various restrictions to ease debugging during development. */
    private static final boolean DBG = false; // STOPSHIP if true

    /**
     * Sets atom pull cool down to 23 hours to help enforcing privacy requirement.
     *
     * <p>Applies to certain atoms. The interval of 23 hours leaves some margin for pull operations
     * that occur once a day.
     */
    private static final long MIN_COOLDOWN_MILLIS =
            DBG ? 10L * SECOND_IN_MILLIS : 23L * HOUR_IN_MILLIS;

    /**
     * Buckets with less than these many calls will be dropped.
     *
     * <p>Applies to metrics with duration fields. Currently used by voice call RAT usages.
     */
    private static final long MIN_CALLS_PER_BUCKET = DBG ? 0L : 5L;

    /** Bucket size in milliseconds to round call durations into. */
    private static final long DURATION_BUCKET_MILLIS =
            DBG ? 2L * SECOND_IN_MILLIS : 5L * MINUTE_IN_MILLIS;

    private static final StatsManager.PullAtomMetadata POLICY_PULL_DAILY =
            new StatsManager.PullAtomMetadata.Builder()
                    .setCoolDownMillis(MIN_COOLDOWN_MILLIS)
                    .build();

    private PersistAtomsStorage mStorage;
    private final StatsManager mStatsManager;
    private final AirplaneModeStats mAirplaneModeStats;
    private static final Random sRandom = new Random();

    public MetricsCollector(Context context) {
        mStorage = new PersistAtomsStorage(context);
        mStatsManager = (StatsManager) context.getSystemService(Context.STATS_MANAGER);
        if (mStatsManager != null) {
            registerAtom(CELLULAR_DATA_SERVICE_SWITCH, POLICY_PULL_DAILY);
            registerAtom(CELLULAR_SERVICE_STATE, POLICY_PULL_DAILY);
            registerAtom(SIM_SLOT_STATE, null);
            registerAtom(SUPPORTED_RADIO_ACCESS_FAMILY, null);
            registerAtom(VOICE_CALL_RAT_USAGE, POLICY_PULL_DAILY);
            registerAtom(VOICE_CALL_SESSION, POLICY_PULL_DAILY);
            registerAtom(INCOMING_SMS, POLICY_PULL_DAILY);
            registerAtom(OUTGOING_SMS, POLICY_PULL_DAILY);
            registerAtom(CARRIER_ID_TABLE_VERSION, null);
            registerAtom(DATA_CALL_SESSION, POLICY_PULL_DAILY);
            registerAtom(IMS_REGISTRATION_STATS, POLICY_PULL_DAILY);
            registerAtom(IMS_REGISTRATION_TERMINATION, POLICY_PULL_DAILY);

            Rlog.d(TAG, "registered");
        } else {
            Rlog.e(TAG, "could not get StatsManager, atoms not registered");
        }

        mAirplaneModeStats = new AirplaneModeStats(context);
    }

    /** Replaces the {@link PersistAtomsStorage} backing the puller. Used during unit tests. */
    @VisibleForTesting
    public void setPersistAtomsStorage(PersistAtomsStorage storage) {
        mStorage = storage;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link StatsManager#PULL_SUCCESS} with list of atoms (potentially empty) if pull
     *     succeeded, {@link StatsManager#PULL_SKIP} if pull was too frequent or atom ID is
     *     unexpected.
     */
    @Override
    public int onPullAtom(int atomTag, List<StatsEvent> data) {
        switch (atomTag) {
            case CELLULAR_DATA_SERVICE_SWITCH:
                return pullCellularDataServiceSwitch(data);
            case CELLULAR_SERVICE_STATE:
                return pullCellularServiceState(data);
            case SIM_SLOT_STATE:
                return pullSimSlotState(data);
            case SUPPORTED_RADIO_ACCESS_FAMILY:
                return pullSupportedRadioAccessFamily(data);
            case VOICE_CALL_RAT_USAGE:
                return pullVoiceCallRatUsages(data);
            case VOICE_CALL_SESSION:
                return pullVoiceCallSessions(data);
            case INCOMING_SMS:
                return pullIncomingSms(data);
            case OUTGOING_SMS:
                return pullOutgoingSms(data);
            case CARRIER_ID_TABLE_VERSION:
                return pullCarrierIdTableVersion(data);
            case DATA_CALL_SESSION:
                return pullDataCallSession(data);
            case IMS_REGISTRATION_STATS:
                return pullImsRegistrationStats(data);
            case IMS_REGISTRATION_TERMINATION:
                return pullImsRegistrationTermination(data);
            default:
                Rlog.e(TAG, String.format("unexpected atom ID %d", atomTag));
                return StatsManager.PULL_SKIP;
        }
    }

    /** Returns the {@link PersistAtomsStorage} backing the puller. */
    public PersistAtomsStorage getAtomsStorage() {
        return mStorage;
    }

    private static int pullSimSlotState(List<StatsEvent> data) {
        SimSlotState state;
        try {
            state = SimSlotState.getCurrentState();
        } catch (RuntimeException e) {
            // UiccController has not been made yet
            return StatsManager.PULL_SKIP;
        }

        StatsEvent e =
                StatsEvent.newBuilder()
                        .setAtomId(SIM_SLOT_STATE)
                        .writeInt(state.numActiveSlots)
                        .writeInt(state.numActiveSims)
                        .writeInt(state.numActiveEsims)
                        .build();
        data.add(e);
        return StatsManager.PULL_SUCCESS;
    }

    private static int pullSupportedRadioAccessFamily(List<StatsEvent> data) {
        Phone[] phones = getPhonesIfAny();
        if (phones.length == 0) {
            return StatsManager.PULL_SKIP;
        }

        // The bitmask is defined in android.telephony.TelephonyManager.NetworkTypeBitMask
        long rafSupported = 0L;
        for (Phone phone : PhoneFactory.getPhones()) {
            rafSupported |= phone.getRadioAccessFamily();
        }

        StatsEvent e =
                StatsEvent.newBuilder()
                        .setAtomId(SUPPORTED_RADIO_ACCESS_FAMILY)
                        .writeLong(rafSupported)
                        .build();
        data.add(e);
        return StatsManager.PULL_SUCCESS;
    }

    private static int pullCarrierIdTableVersion(List<StatsEvent> data) {
        Phone[] phones = getPhonesIfAny();
        if (phones.length == 0) {
            return StatsManager.PULL_SKIP;
        } else {
            // All phones should have the same version of the carrier ID table, so only query the
            // first one.
            int version = phones[0].getCarrierIdListVersion();
            data.add(
                    StatsEvent.newBuilder()
                            .setAtomId(CARRIER_ID_TABLE_VERSION)
                            .writeInt(version)
                            .build());
            return StatsManager.PULL_SUCCESS;
        }
    }

    private int pullVoiceCallRatUsages(List<StatsEvent> data) {
        VoiceCallRatUsage[] usages = mStorage.getVoiceCallRatUsages(MIN_COOLDOWN_MILLIS);
        if (usages != null) {
            // sort by carrier/RAT and remove buckets with insufficient number of calls
            Arrays.stream(usages)
                    .sorted(
                            Comparator.comparingLong(
                                    usage -> ((long) usage.carrierId << 32) | usage.rat))
                    .filter(usage -> usage.callCount >= MIN_CALLS_PER_BUCKET)
                    .forEach(usage -> data.add(buildStatsEvent(usage)));
            Rlog.d(
                    TAG,
                    String.format(
                            "%d out of %d VOICE_CALL_RAT_USAGE pulled",
                            data.size(), usages.length));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "VOICE_CALL_RAT_USAGE pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullVoiceCallSessions(List<StatsEvent> data) {
        VoiceCallSession[] calls = mStorage.getVoiceCallSessions(MIN_COOLDOWN_MILLIS);
        if (calls != null) {
            // call session list is already shuffled when calls were inserted
            Arrays.stream(calls).forEach(call -> data.add(buildStatsEvent(call)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "VOICE_CALL_SESSION pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullIncomingSms(List<StatsEvent> data) {
        IncomingSms[] smsList = mStorage.getIncomingSms(MIN_COOLDOWN_MILLIS);
        if (smsList != null) {
            // SMS list is already shuffled when SMS were inserted
            Arrays.stream(smsList).forEach(sms -> data.add(buildStatsEvent(sms)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "INCOMING_SMS pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullOutgoingSms(List<StatsEvent> data) {
        OutgoingSms[] smsList = mStorage.getOutgoingSms(MIN_COOLDOWN_MILLIS);
        if (smsList != null) {
            // SMS list is already shuffled when SMS were inserted
            Arrays.stream(smsList).forEach(sms -> data.add(buildStatsEvent(sms)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "OUTGOING_SMS pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullDataCallSession(List<StatsEvent> data) {
        DataCallSession[] dataCallSessions = mStorage.getDataCallSessions(MIN_COOLDOWN_MILLIS);
        if (dataCallSessions != null) {
            Arrays.stream(dataCallSessions)
                    .forEach(dataCall -> data.add(buildStatsEvent(dataCall)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "DATA_CALL_SESSION pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullCellularDataServiceSwitch(List<StatsEvent> data) {
        CellularDataServiceSwitch[] persistAtoms =
                mStorage.getCellularDataServiceSwitches(MIN_COOLDOWN_MILLIS);
        if (persistAtoms != null) {
            // list is already shuffled when instances were inserted
            Arrays.stream(persistAtoms)
                    .forEach(persistAtom -> data.add(buildStatsEvent(persistAtom)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "CELLULAR_DATA_SERVICE_SWITCH pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullCellularServiceState(List<StatsEvent> data) {
        // Include the latest durations
        for (Phone phone : getPhonesIfAny()) {
            phone.getServiceStateTracker().getServiceStateStats().conclude();
        }

        CellularServiceState[] persistAtoms =
                mStorage.getCellularServiceStates(MIN_COOLDOWN_MILLIS);
        if (persistAtoms != null) {
            // list is already shuffled when instances were inserted
            Arrays.stream(persistAtoms)
                    .forEach(persistAtom -> data.add(buildStatsEvent(persistAtom)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "CELLULAR_SERVICE_STATE pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullImsRegistrationStats(List<StatsEvent> data) {
        // Include the latest durations
        for (Phone phone : getPhonesIfAny()) {
            ImsPhone imsPhone = (ImsPhone) phone.getImsPhone();
            if (imsPhone != null) {
                imsPhone.getImsStats().conclude();
            }
        }

        ImsRegistrationStats[] persistAtoms = mStorage.getImsRegistrationStats(MIN_COOLDOWN_MILLIS);
        if (persistAtoms != null) {
            // list is already shuffled when instances were inserted
            Arrays.stream(persistAtoms)
                    .forEach(persistAtom -> data.add(buildStatsEvent(persistAtom)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "IMS_REGISTRATION_STATS pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    private int pullImsRegistrationTermination(List<StatsEvent> data) {
        ImsRegistrationTermination[] persistAtoms =
                mStorage.getImsRegistrationTerminations(MIN_COOLDOWN_MILLIS);
        if (persistAtoms != null) {
            // list is already shuffled when instances were inserted
            Arrays.stream(persistAtoms)
                    .forEach(persistAtom -> data.add(buildStatsEvent(persistAtom)));
            return StatsManager.PULL_SUCCESS;
        } else {
            Rlog.w(TAG, "IMS_REGISTRATION_TERMINATION pull too frequent, skipping");
            return StatsManager.PULL_SKIP;
        }
    }

    /** Registers a pulled atom ID {@code atomId} with optional {@code policy} for pulling. */
    private void registerAtom(int atomId, @Nullable StatsManager.PullAtomMetadata policy) {
        mStatsManager.setPullAtomCallback(atomId, policy, ConcurrentUtils.DIRECT_EXECUTOR, this);
    }

    private static StatsEvent buildStatsEvent(CellularDataServiceSwitch serviceSwitch) {
        return StatsEvent.newBuilder()
                .setAtomId(CELLULAR_DATA_SERVICE_SWITCH)
                .writeInt(serviceSwitch.ratFrom)
                .writeInt(serviceSwitch.ratTo)
                .writeInt(serviceSwitch.simSlotIndex)
                .writeBoolean(serviceSwitch.isMultiSim)
                .writeInt(serviceSwitch.carrierId)
                .writeInt(serviceSwitch.switchCount)
                .build();
    }

    private static StatsEvent buildStatsEvent(CellularServiceState state) {
        return StatsEvent.newBuilder()
                .setAtomId(CELLULAR_SERVICE_STATE)
                .writeInt(state.voiceRat)
                .writeInt(state.dataRat)
                .writeInt(state.voiceRoamingType)
                .writeInt(state.dataRoamingType)
                .writeBoolean(state.isEndc)
                .writeInt(state.simSlotIndex)
                .writeBoolean(state.isMultiSim)
                .writeInt(state.carrierId)
                .writeInt(
                        (int)
                                (round(state.totalTimeMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .build();
    }

    private static StatsEvent buildStatsEvent(VoiceCallRatUsage usage) {
        return StatsEvent.newBuilder()
                .setAtomId(VOICE_CALL_RAT_USAGE)
                .writeInt(usage.carrierId)
                .writeInt(usage.rat)
                .writeLong(
                        round(usage.totalDurationMillis, DURATION_BUCKET_MILLIS) / SECOND_IN_MILLIS)
                .writeLong(usage.callCount)
                .build();
    }

    private static StatsEvent buildStatsEvent(VoiceCallSession session) {
        return StatsEvent.newBuilder()
                .setAtomId(VOICE_CALL_SESSION)
                .writeInt(session.bearerAtStart)
                .writeInt(session.bearerAtEnd)
                .writeInt(session.direction)
                .writeInt(session.setupDuration)
                .writeBoolean(session.setupFailed)
                .writeInt(session.disconnectReasonCode)
                .writeInt(session.disconnectExtraCode)
                .writeString(session.disconnectExtraMessage)
                .writeInt(session.ratAtStart)
                .writeInt(session.ratAtEnd)
                .writeLong(session.ratSwitchCount)
                .writeLong(session.codecBitmask)
                .writeInt(session.concurrentCallCountAtStart)
                .writeInt(session.concurrentCallCountAtEnd)
                .writeInt(session.simSlotIndex)
                .writeBoolean(session.isMultiSim)
                .writeBoolean(session.isEsim)
                .writeInt(session.carrierId)
                .writeBoolean(session.srvccCompleted)
                .writeLong(session.srvccFailureCount)
                .writeLong(session.srvccCancellationCount)
                .writeBoolean(session.rttEnabled)
                .writeBoolean(session.isEmergency)
                .writeBoolean(session.isRoaming)
                // workaround: dimension required for keeping multiple pulled atoms
                .writeInt(sRandom.nextInt())
                // New fields introduced in Android S
                .writeInt(session.signalStrengthAtEnd)
                .writeInt(session.bandAtEnd)
                .writeInt(session.setupDurationMillis)
                .writeInt(session.mainCodecQuality)
                .writeBoolean(session.videoEnabled)
                .writeInt(session.ratAtConnected)
                .writeBoolean(session.isMultiparty)
                .build();
    }

    private static StatsEvent buildStatsEvent(IncomingSms sms) {
        return StatsEvent.newBuilder()
                .setAtomId(INCOMING_SMS)
                .writeInt(sms.smsFormat)
                .writeInt(sms.smsTech)
                .writeInt(sms.rat)
                .writeInt(sms.smsType)
                .writeInt(sms.totalParts)
                .writeInt(sms.receivedParts)
                .writeBoolean(sms.blocked)
                .writeInt(sms.error)
                .writeBoolean(sms.isRoaming)
                .writeInt(sms.simSlotIndex)
                .writeBoolean(sms.isMultiSim)
                .writeBoolean(sms.isEsim)
                .writeInt(sms.carrierId)
                .writeLong(sms.messageId)
                .build();
    }

    private static StatsEvent buildStatsEvent(OutgoingSms sms) {
        return StatsEvent.newBuilder()
                .setAtomId(OUTGOING_SMS)
                .writeInt(sms.smsFormat)
                .writeInt(sms.smsTech)
                .writeInt(sms.rat)
                .writeInt(sms.sendResult)
                .writeInt(sms.errorCode)
                .writeBoolean(sms.isRoaming)
                .writeBoolean(sms.isFromDefaultApp)
                .writeInt(sms.simSlotIndex)
                .writeBoolean(sms.isMultiSim)
                .writeBoolean(sms.isEsim)
                .writeInt(sms.carrierId)
                .writeLong(sms.messageId)
                .writeInt(sms.retryId)
                .build();
    }

    private static StatsEvent buildStatsEvent(DataCallSession dataCallSession) {
        return StatsEvent.newBuilder()
                .setAtomId(DATA_CALL_SESSION)
                .writeInt(dataCallSession.dimension)
                .writeBoolean(dataCallSession.isMultiSim)
                .writeBoolean(dataCallSession.isEsim)
                .writeInt(0) // profile is deprecated, so we default to 0
                .writeInt(dataCallSession.apnTypeBitmask)
                .writeInt(dataCallSession.carrierId)
                .writeBoolean(dataCallSession.isRoaming)
                .writeInt(dataCallSession.ratAtEnd)
                .writeBoolean(dataCallSession.oosAtEnd)
                .writeLong(dataCallSession.ratSwitchCount)
                .writeBoolean(dataCallSession.isOpportunistic)
                .writeInt(dataCallSession.ipType)
                .writeBoolean(dataCallSession.setupFailed)
                .writeInt(dataCallSession.failureCause)
                .writeInt(dataCallSession.suggestedRetryMillis)
                .writeInt(dataCallSession.deactivateReason)
                .writeLong(round(
                        dataCallSession.durationMinutes, DURATION_BUCKET_MILLIS / MINUTE_IN_MILLIS))
                .writeBoolean(dataCallSession.ongoing)
                .writeInt(dataCallSession.bandAtEnd)
                .build();
    }

    private static StatsEvent buildStatsEvent(ImsRegistrationStats stats) {
        return StatsEvent.newBuilder()
                .setAtomId(IMS_REGISTRATION_STATS)
                .writeInt(stats.carrierId)
                .writeInt(stats.simSlotIndex)
                .writeInt(stats.rat)
                .writeInt(
                        (int)
                                (round(stats.registeredMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.voiceCapableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.voiceAvailableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.smsCapableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.smsAvailableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.videoCapableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.videoAvailableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.utCapableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .writeInt(
                        (int)
                                (round(stats.utAvailableMillis, DURATION_BUCKET_MILLIS)
                                        / SECOND_IN_MILLIS))
                .build();
    }

    private static StatsEvent buildStatsEvent(ImsRegistrationTermination termination) {
        return StatsEvent.newBuilder()
                .setAtomId(IMS_REGISTRATION_TERMINATION)
                .writeInt(termination.carrierId)
                .writeBoolean(termination.isMultiSim)
                .writeInt(termination.ratAtEnd)
                .writeBoolean(termination.setupFailed)
                .writeInt(termination.reasonCode)
                .writeInt(termination.extraCode)
                .writeString(termination.extraMessage)
                .writeInt(termination.count)
                .build();
    }

    /** Returns all phones in {@link PhoneFactory}, or an empty array if phones not made yet. */
    private static Phone[] getPhonesIfAny() {
        try {
            return PhoneFactory.getPhones();
        } catch (IllegalStateException e) {
            // Phones have not been made yet
            return new Phone[0];
        }
    }

    /** Returns the value rounded to the bucket. */
    private static long round(long value, long bucket) {
        return bucket == 0 ? value : ((value + bucket / 2) / bucket) * bucket;
    }
}
