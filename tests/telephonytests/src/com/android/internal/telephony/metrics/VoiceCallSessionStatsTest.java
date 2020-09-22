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

import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.DisconnectCause;
import android.telephony.PreciseDisconnectCause;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsStreamMediaProfile;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.GsmCdmaCall;
import com.android.internal.telephony.GsmCdmaConnection;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.imsphone.ImsPhoneCall;
import com.android.internal.telephony.imsphone.ImsPhoneConnection;
import com.android.internal.telephony.nano.PersistAtomsProto.RawVoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.internal.telephony.nano.TelephonyProto.TelephonyCallSession.Event.AudioCodec;
import com.android.internal.telephony.protobuf.nano.MessageNano;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class VoiceCallSessionStatsTest extends TelephonyTest {
    private static final int CARRIER_ID_SLOT_0 = 1;
    private static final int CARRIER_ID_SLOT_1 = 1187;

    @Mock private Phone mSecondPhone;
    @Mock private ServiceStateTracker mSecondServiceStateTracker;
    @Mock private ServiceState mSecondServiceState;

    @Mock private UiccSlot mPhysicalSlot;
    @Mock private UiccSlot mEsimSlot;
    @Mock private UiccSlot mEmptySlot;
    @Mock private UiccCard mInactiveCard;
    @Mock private UiccCard mActiveCard;

    @Mock private ImsPhoneConnection mImsConnection0;
    @Mock private ImsPhoneConnection mImsConnection1;
    @Mock private GsmCdmaConnection mGsmConnection0;
    @Mock private GsmCdmaConnection mGsmConnection1;

    @Mock private GsmCdmaCall mCsCall0;
    @Mock private GsmCdmaCall mCsCall1;
    @Mock private ImsPhoneCall mImsCall0;
    @Mock private ImsPhoneCall mImsCall1;

    private static class TestableVoiceCallSessionStats extends VoiceCallSessionStats {
        private long mTimeMillis = 0L;

        TestableVoiceCallSessionStats(int phoneId, Phone phone) {
            super(phoneId, phone);
        }

        @Override
        protected long getTimeMillis() {
            return mTimeMillis;
        }

        private void setTimeMillis(long timeMillis) {
            mTimeMillis = timeMillis;
        }

        private void incTimeMillis(long timeMillis) {
            mTimeMillis += timeMillis;
        }
    }

    private TestableVoiceCallSessionStats mVoiceCallSessionStats0;
    private TestableVoiceCallSessionStats mVoiceCallSessionStats1;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone, mSecondPhone});
        doReturn(CARRIER_ID_SLOT_0).when(mPhone).getCarrierId();
        // mPhone's mSST/mServiceState has been set up by TelephonyTest
        doReturn(CARRIER_ID_SLOT_1).when(mSecondPhone).getCarrierId();
        doReturn(mSecondServiceStateTracker).when(mSecondPhone).getServiceStateTracker();
        doReturn(mSecondServiceState).when(mSecondServiceStateTracker).getServiceState();

        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(false).when(mServiceState).getVoiceRoaming();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN)
                .when(mSecondServiceState)
                .getDataNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN)
                .when(mSecondServiceState)
                .getVoiceNetworkType();
        doReturn(false).when(mSecondServiceState).getVoiceRoaming();

        doReturn(true).when(mPhysicalSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot).getCardState();
        doReturn(false).when(mPhysicalSlot).isEuicc();
        doReturn(true).when(mEmptySlot).isActive();
        doReturn(CardState.CARDSTATE_ABSENT).when(mEmptySlot).getCardState();
        doReturn(true).when(mEsimSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mEsimSlot).getCardState();
        doReturn(true).when(mEsimSlot).isEuicc();
        doReturn(0).when(mInactiveCard).getNumApplications();
        doReturn(4).when(mActiveCard).getNumApplications();

        doReturn(new UiccSlot[] {mPhysicalSlot}).when(mUiccController).getUiccSlots();
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlot(eq(0));

        doReturn(0).when(mUiccController).getSlotIdFromPhoneId(0);
        doReturn(1).when(mUiccController).getSlotIdFromPhoneId(1);

        doReturn(PhoneConstants.PHONE_TYPE_IMS).when(mImsConnection0).getPhoneType();
        doReturn(false).when(mImsConnection0).isEmergencyCall();
        doReturn(PhoneConstants.PHONE_TYPE_IMS).when(mImsConnection1).getPhoneType();
        doReturn(false).when(mImsConnection1).isEmergencyCall();
        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(mGsmConnection0).getPhoneType();
        doReturn(false).when(mGsmConnection0).isEmergencyCall();
        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(mGsmConnection1).getPhoneType();
        doReturn(false).when(mGsmConnection1).isEmergencyCall();

        mVoiceCallSessionStats0 = new TestableVoiceCallSessionStats(0, mPhone);
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats1 = new TestableVoiceCallSessionStats(1, mSecondPhone);
        mVoiceCallSessionStats1.onServiceStateChanged(mSecondServiceState);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void singleImsCall_moRejected() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_REMOTE_CALL_DECLINE);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_EVS_SWB);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_REMOTE_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleImsCall_moFailed() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_SIP_FORBIDDEN);
        expectedCall.setupFailed = true;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 2200L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_SIP_FORBIDDEN, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleImsCall_moAccepted() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_FAST;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;
        expectedCall.disconnectExtraMessage = "normal call clearing";
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 100000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_EVS_SWB);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0,
                new ImsReasonInfo(
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0, "normal call clearing"));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleImsCall_mtRejected() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 8000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleImsCall_mtAccepted() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleImsCall_dsdsModeSingleSim() {
        doReturn(mInactiveCard).when(mEsimSlot).getUiccCard();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.isMultiSim = false; // DSDS with one active SIM profile should not count

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_dsdsMode() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.isMultiSim = true;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_esim() {
        doReturn(mActiveCard).when(mEsimSlot).getUiccCard();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mSecondServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection1).isIncoming();
        doReturn(2000L).when(mImsConnection1).getCreateTime();
        doReturn(mImsCall1).when(mImsConnection1).getCall();
        doReturn(new ArrayList(List.of(mImsConnection1))).when(mImsCall1).getConnections();
        VoiceCallSession expectedCall =
                makeSlot1CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;

        mVoiceCallSessionStats1.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall1).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection1).getState();
        mVoiceCallSessionStats1.onImsCallReceived(mImsConnection1);
        mVoiceCallSessionStats1.setTimeMillis(2100L);
        mVoiceCallSessionStats1.onAudioCodecChanged(
                mImsConnection1, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats1.setTimeMillis(8000L);
        mVoiceCallSessionStats1.onImsCallTerminated(
                mImsConnection1, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_emergency() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(true).when(mImsConnection0).isEmergencyCall();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.isEmergency = true;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_roaming() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mServiceState).getVoiceRoaming();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.isRoaming = true;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_codecSwitch() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask =
                1L << AudioCodec.AUDIO_CODEC_AMR | 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_EVS_SWB);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_ratSwitch() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.ratSwitchCount = 2L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 4000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 4000L, 6000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 6000L, 8000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(6000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleImsCall_rttOnDial() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(true).when(mImsConnection0).hasRttTextStream();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.rttEnabled = true;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void singleImsCall_rttStartedMidCall() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.rttEnabled = true;

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        mVoiceCallSessionStats0.onRttStarted(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
    }

    @Test
    @SmallTest
    public void concurrentImsCalls_firstCallHangupFirst() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        // call 0 starts first, MO
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall0 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        expectedCall0.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 1;
        expectedCall0.ratSwitchCount = 1L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        // call 1 starts later, MT
        doReturn(true).when(mImsConnection1).isIncoming();
        doReturn(60000L).when(mImsConnection1).getCreateTime();
        doReturn(mImsCall1).when(mImsConnection1).getCall();
        doReturn(new ArrayList(List.of(mImsConnection1))).when(mImsCall1).getConnections();
        VoiceCallSession expectedCall1 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall1.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 2L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 100000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        100000L,
                        120000L,
                        1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        // call 0 dial
        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2020L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2080L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        // call 1 ring
        mVoiceCallSessionStats0.setTimeMillis(60000L);
        doReturn(Call.State.INCOMING).when(mImsCall1).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection1);
        mVoiceCallSessionStats0.setTimeMillis(60100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection1, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(65000L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection1));
        mVoiceCallSessionStats0.setTimeMillis(65020L);
        doReturn(Call.State.ACTIVE).when(mImsCall1).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall1);
        // RAT change, LTE to HSPA
        mVoiceCallSessionStats0.setTimeMillis(80000L);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 0 hangup by remote
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0,
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        // RAT change, HSPA to UMTS
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 1 hangup by local
        mVoiceCallSessionStats0.setTimeMillis(120000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection1, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(2)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertSortedProtoArrayEquals(
                new VoiceCallSession[] {expectedCall0, expectedCall1},
                callCaptor.getAllValues().stream().toArray(VoiceCallSession[]::new));
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentImsCalls_firstCallHangupLast() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        // call 0 starts first, MO
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall0 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall0.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 0;
        expectedCall0.ratSwitchCount = 2L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        // call 1 starts later, MT
        doReturn(true).when(mImsConnection1).isIncoming();
        doReturn(60000L).when(mImsConnection1).getCreateTime();
        doReturn(mImsCall1).when(mImsConnection1).getCall();
        doReturn(new ArrayList(List.of(mImsConnection1))).when(mImsCall1).getConnections();
        VoiceCallSession expectedCall1 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        expectedCall1.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 1;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 100000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        100000L,
                        120000L,
                        1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        // call 0 dial
        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2020L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2080L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        // call 1 ring
        mVoiceCallSessionStats0.setTimeMillis(60000L);
        doReturn(Call.State.INCOMING).when(mImsCall1).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection1);
        mVoiceCallSessionStats0.setTimeMillis(60100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection1, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(65000L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection1));
        mVoiceCallSessionStats0.setTimeMillis(65020L);
        doReturn(Call.State.ACTIVE).when(mImsCall1).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall1);
        // RAT change, LTE to HSPA
        mVoiceCallSessionStats0.setTimeMillis(80000L);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 1 hangup by remote
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection1,
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        // RAT change, HSPA to UMTS
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 0 hangup by local
        mVoiceCallSessionStats0.setTimeMillis(120000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(2)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertSortedProtoArrayEquals(
                new VoiceCallSession[] {expectedCall0, expectedCall1},
                callCaptor.getAllValues().stream().toArray(VoiceCallSession[]::new));
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentImsCalls_firstCallHangupDuringSecondCallSetup() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        // call 0 starts first, MO
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall0 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall0.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 1;
        expectedCall0.ratSwitchCount = 0L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_LTE;
        // call 1 starts later, MT
        doReturn(true).when(mImsConnection1).isIncoming();
        doReturn(60000L).when(mImsConnection1).getCreateTime();
        doReturn(mImsCall1).when(mImsConnection1).getCall();
        doReturn(new ArrayList(List.of(mImsConnection1))).when(mImsCall1).getConnections();
        VoiceCallSession expectedCall1 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        expectedCall1.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 90000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        // call 0 dial
        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2020L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2080L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        // call 1 ring
        mVoiceCallSessionStats0.setTimeMillis(60000L);
        doReturn(Call.State.INCOMING).when(mImsCall1).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection1);
        mVoiceCallSessionStats0.setTimeMillis(60100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection1, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        // call 0 hangup by local
        mVoiceCallSessionStats0.setTimeMillis(61000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));
        mVoiceCallSessionStats0.setTimeMillis(65000L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection1));
        mVoiceCallSessionStats0.setTimeMillis(65020L);
        doReturn(Call.State.ACTIVE).when(mImsCall1).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall1);
        // RAT change, LTE to HSPA
        mVoiceCallSessionStats0.setTimeMillis(80000L);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 1 hangup by remote
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection1,
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(2)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertSortedProtoArrayEquals(
                new VoiceCallSession[] {expectedCall0, expectedCall1},
                callCaptor.getAllValues().stream().toArray(VoiceCallSession[]::new));
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageHspa},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCsCall_moRejected() {
        doReturn(false).when(mGsmConnection0).isIncoming();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.NORMAL);
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.CALL_REJECTED;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 15000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(3100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(7000L);
        doReturn(Call.State.ALERTING).when(mCsCall0).getState();
        doReturn(Call.State.ALERTING).when(mGsmConnection0).getState();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(15000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.CALL_REJECTED)
                .when(mGsmConnection0)
                .getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCsCall_moFailed() {
        doReturn(false).when(mGsmConnection0).isIncoming();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.LOST_SIGNAL);
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 15000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(3100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(15000L);
        doReturn(DisconnectCause.LOST_SIGNAL).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCsCall_moAccepted() {
        doReturn(false).when(mGsmConnection0).isIncoming();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.NORMAL);
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 100000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(3100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(7000L);
        doReturn(Call.State.ALERTING).when(mCsCall0).getState();
        doReturn(Call.State.ALERTING).when(mGsmConnection0).getState();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(10000L);
        doReturn(Call.State.ACTIVE).when(mCsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mGsmConnection0).getState();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.NORMAL).when(mGsmConnection0).getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCsCall_mtRejected() {
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mGsmConnection0).isIncoming();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        DisconnectCause.NORMAL);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.CALL_REJECTED;
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 2500L, 15000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(2500L);
        doReturn(Call.State.INCOMING).when(mCsCall0).getState();
        doReturn(Call.State.INCOMING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(15000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.CALL_REJECTED)
                .when(mGsmConnection0)
                .getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleCsCall_mtAccepted() {
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mGsmConnection0).isIncoming();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        DisconnectCause.NORMAL);
        expectedCall.setupDuration = VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_FAST;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 2500L, 100000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(2500L);
        doReturn(Call.State.INCOMING).when(mCsCall0).getState();
        doReturn(Call.State.INCOMING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(15000L);
        mVoiceCallSessionStats0.onRilAcceptCall(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(15500L);
        doReturn(Call.State.ACTIVE).when(mCsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mGsmConnection0).getState();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.NORMAL).when(mGsmConnection0).getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleCall_srvccFailed() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsPhone).getHandoverConnection();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.srvccFailureCount = 2L;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 10000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 10000L, 12000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(9000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(10000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(11000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_FAILED);
        mVoiceCallSessionStats0.setTimeMillis(11100L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(11500L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_FAILED);
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_HO_NOT_FEASIBLE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCall_srvccCanceled() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsPhone).getHandoverConnection();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.srvccCancellationCount = 2L;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(9500L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(10000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED);
        mVoiceCallSessionStats0.setTimeMillis(10500L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(11000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED);
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleCall_srvccSuccess() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsPhone).getHandoverConnection();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.NORMAL);
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupFailed = false;
        expectedCall.srvccCancellationCount = 1L;
        expectedCall.srvccFailureCount = 1L;
        expectedCall.srvccCompleted = true;
        expectedCall.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 7000L, 1L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 7000L, 12000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        // IMS call created
        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection0));
        mVoiceCallSessionStats0.setTimeMillis(2280L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        // canceled SRVCC attempt
        mVoiceCallSessionStats0.setTimeMillis(4500L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(5000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED);
        // failed SRVCC attempt
        mVoiceCallSessionStats0.setTimeMillis(6500L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        mVoiceCallSessionStats0.setTimeMillis(7000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_FAILED);
        // successful SRVCC attempt
        mVoiceCallSessionStats0.setTimeMillis(9000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED);
        // CS call terminated
        mVoiceCallSessionStats0.setTimeMillis(12000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.NORMAL).when(mGsmConnection0).getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentCalls_srvcc() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(new ArrayList(List.of(mImsConnection0, mImsConnection1)))
                .when(mImsPhone).getHandoverConnection();
        // call 0 starts first, MO
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        doReturn(2000L).when(mGsmConnection0).getCreateTime();
        doReturn(mCsCall0).when(mGsmConnection0).getCall();
        VoiceCallSession expectedCall0 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.NORMAL);
        expectedCall0.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall0.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 1;
        expectedCall0.ratSwitchCount = 1L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall0.srvccCompleted = true;
        expectedCall0.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        // call 1 starts later, MT
        doReturn(true).when(mImsConnection1).isIncoming();
        doReturn(60000L).when(mImsConnection1).getCreateTime();
        doReturn(mImsCall1).when(mImsConnection1).getCall();
        doReturn(new ArrayList(List.of(mImsConnection1))).when(mImsCall1).getConnections();
        doReturn(60000L).when(mGsmConnection1).getCreateTime();
        doReturn(mCsCall1).when(mGsmConnection1).getCall();
        VoiceCallSession expectedCall1 =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        DisconnectCause.NORMAL);
        expectedCall1.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall1.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall1.srvccCompleted = true;
        expectedCall1.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        RawVoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        RawVoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 80000L, 120000L, 2L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        // call 0 dial
        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2020L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(2080L);
        doReturn(Call.State.ALERTING).when(mImsCall0).getState();
        doReturn(Call.State.ALERTING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        mVoiceCallSessionStats0.setTimeMillis(4000L);
        doReturn(Call.State.ACTIVE).when(mImsCall0).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall0);
        // call 1 ring
        mVoiceCallSessionStats0.setTimeMillis(60000L);
        doReturn(Call.State.INCOMING).when(mImsCall1).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection1);
        mVoiceCallSessionStats0.setTimeMillis(60100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection1, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(65000L);
        mVoiceCallSessionStats0.onImsAcceptCall(List.of(mImsConnection1));
        mVoiceCallSessionStats0.setTimeMillis(65020L);
        doReturn(Call.State.ACTIVE).when(mImsCall1).getState();
        doReturn(Call.State.ACTIVE).when(mImsConnection1).getState();
        mVoiceCallSessionStats0.onCallStateChanged(mImsCall1);
        // SRVCC affecting all IMS calls
        mVoiceCallSessionStats0.setTimeMillis(75000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        // RAT change, LTE to UMTS
        mVoiceCallSessionStats0.setTimeMillis(80000L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(85000L);
        mVoiceCallSessionStats0.onRilSrvccStateChanged(
                TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED);
        // call 0 hangup
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection0).getDisconnectCause();
        doReturn(PreciseDisconnectCause.NORMAL).when(mGsmConnection0).getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        // call 1 hangup
        mVoiceCallSessionStats0.setTimeMillis(120000L);
        doReturn(DisconnectCause.NORMAL).when(mGsmConnection1).getDisconnectCause();
        doReturn(PreciseDisconnectCause.NORMAL).when(mGsmConnection1).getPreciseDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection1));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(2)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertSortedProtoArrayEquals(
                new VoiceCallSession[] {expectedCall0, expectedCall1},
                callCaptor.getAllValues().stream().toArray(VoiceCallSession[]::new));
        assertSortedProtoArrayEquals(
                new RawVoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleWifiCall_preferred() {
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsPhone).isWifiCallingEnabled();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_IWLAN,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_IWLAN, 2000L, 8000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    @Test
    @SmallTest
    public void singleWifiCall_airPlaneMode() {
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsPhone).isWifiCallingEnabled();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_IWLAN,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        RawVoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_IWLAN, 2000L, 8000L, 1L);
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_CALL_DECLINE, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertThat(ratUsage.get()).hasLength(1);
        assertProtoEquals(expectedRatUsage, ratUsage.get()[0]);
    }

    private AtomicReference<RawVoiceCallRatUsage[]> setupRatUsageCapture() {
        final AtomicReference<RawVoiceCallRatUsage[]> ratUsage = new AtomicReference<>(null);
        doAnswer(invocation -> {
            VoiceCallRatTracker tracker = (VoiceCallRatTracker) invocation.getArguments()[0];
            ratUsage.set(tracker.toProto());
            return null; // for void
        }).when(mPersistAtomsStorage).addVoiceCallRatUsage(any());
        return ratUsage;
    }

    private static VoiceCallSession makeSlot0CallProto(
            int bearer, int direction, int rat, int reason) {
        VoiceCallSession call = new VoiceCallSession();
        call.bearerAtStart = bearer;
        call.bearerAtEnd = bearer;
        call.direction = direction;
        call.setupDuration = VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
        call.setupFailed = true;
        call.disconnectReasonCode = reason;
        call.disconnectExtraCode = 0;
        call.disconnectExtraMessage = "";
        call.ratAtStart = rat;
        call.ratAtEnd = rat;
        call.ratSwitchCount = 0L;
        call.codecBitmask = 0L;
        call.simSlotIndex = 0;
        call.isMultiSim = false;
        call.isEsim = false;
        call.carrierId = CARRIER_ID_SLOT_0;
        call.srvccCompleted = false;
        call.srvccFailureCount = 0L;
        call.srvccCancellationCount = 0L;
        call.rttEnabled = false;
        call.isEmergency = false;
        call.isRoaming = false;
        call.setupBeginMillis = 0L;
        return call;
    }

    private static VoiceCallSession makeSlot1CallProto(
            int bearer, int direction, int rat, int reason) {
        VoiceCallSession call = new VoiceCallSession();
        call.bearerAtStart = bearer;
        call.bearerAtEnd = bearer;
        call.direction = direction;
        call.setupDuration = VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
        call.setupFailed = true;
        call.disconnectReasonCode = reason;
        call.disconnectExtraCode = 0;
        call.disconnectExtraMessage = "";
        call.ratAtStart = rat;
        call.ratAtEnd = rat;
        call.ratSwitchCount = 0L;
        call.codecBitmask = 0L;
        call.simSlotIndex = 1;
        call.isMultiSim = true;
        call.isEsim = true;
        call.carrierId = CARRIER_ID_SLOT_1;
        call.srvccCompleted = false;
        call.srvccFailureCount = 0L;
        call.srvccCancellationCount = 0L;
        call.rttEnabled = false;
        call.isEmergency = false;
        call.isRoaming = false;
        call.setupBeginMillis = 0L;
        return call;
    }

    private static RawVoiceCallRatUsage makeRatUsageProto(
            int carrierId, int rat, long beginMillis, long endMillis, long callCount) {
        RawVoiceCallRatUsage usage = new RawVoiceCallRatUsage();
        usage.carrierId = carrierId;
        usage.rat = rat;
        usage.totalDurationMillis = endMillis - beginMillis;
        usage.callCount = callCount;
        return usage;
    }

    private static void assertProtoEquals(MessageNano expected, MessageNano actual) {
        assertWithMessage(
                        "  actual proto:\n"
                                + actual.toString()
                                + "  differs from expected:\n"
                                + expected.toString())
                .that(MessageNano.messageNanoEquals(expected, actual))
                .isTrue();
    }

    private static final Comparator<MessageNano> sProtoComparator =
            new Comparator<>() {
                @Override
                public int compare(MessageNano o1, MessageNano o2) {
                    if (o1 == o2) {
                        return 0;
                    }
                    if (o1 == null) {
                        return -1;
                    }
                    if (o2 == null) {
                        return 1;
                    }
                    assertThat(o1.getClass()).isEqualTo(o2.getClass());
                    return o1.toString().compareTo(o2.toString());
                }
            };

    private static void assertSortedProtoArrayEquals(MessageNano[] expected, MessageNano[] actual) {
        assertThat(expected).isNotNull();
        assertThat(actual).isNotNull();
        assertThat(actual.length).isEqualTo(expected.length);
        MessageNano[] sortedExpected = expected.clone();
        MessageNano[] sortedActual = actual.clone();
        Arrays.sort(sortedExpected, sProtoComparator);
        Arrays.sort(sortedActual, sProtoComparator);
        for (int i = 0; i < sortedExpected.length; i++) {
            assertProtoEquals(sortedExpected[i], sortedActual[i]);
        }
    }
}
