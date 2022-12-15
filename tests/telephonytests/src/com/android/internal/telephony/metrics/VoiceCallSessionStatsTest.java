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
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_FIVE_MINUTES;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_ONE_HOUR;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_ONE_MINUTE;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_TEN_MINUTES;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_THIRTY_MINUTES;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_MORE_THAN_ONE_HOUR;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_SUPER_WIDEBAND;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.NetworkType;
import android.telephony.DisconnectCause;
import android.telephony.NetworkRegistrationInfo;
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
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallRatUsage;
import com.android.internal.telephony.nano.PersistAtomsProto.VoiceCallSession;
import com.android.internal.telephony.nano.TelephonyProto.TelephonyCallSession.Event.AudioCodec;
import com.android.internal.telephony.protobuf.nano.MessageNano;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccPort;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class VoiceCallSessionStatsTest extends TelephonyTest {
    private static final int CARRIER_ID_SLOT_0 = 1;
    private static final int CARRIER_ID_SLOT_1 = 1187;

    // Mocked classes
    private Phone mSecondPhone;
    private ServiceStateTracker mSecondServiceStateTracker;
    private ServiceState mSecondServiceState;
    private UiccSlot mPhysicalSlot;
    private UiccSlot mEsimSlot;
    private UiccSlot mEmptySlot;
    private UiccCard mInactiveCard;
    private UiccCard mActiveCard;
    private UiccPort mInactivePort;
    private UiccPort mActivePort;
    private ImsPhoneConnection mImsConnection0;
    private ImsPhoneConnection mImsConnection1;
    private GsmCdmaConnection mGsmConnection0;
    private GsmCdmaConnection mGsmConnection1;
    private GsmCdmaCall mCsCall0;
    private GsmCdmaCall mCsCall1;
    private ImsPhoneCall mImsCall0;
    private ImsPhoneCall mImsCall1;

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
        mSecondPhone = mock(Phone.class);
        mSecondServiceStateTracker = mock(ServiceStateTracker.class);
        mSecondServiceState = mock(ServiceState.class);
        mPhysicalSlot = mock(UiccSlot.class);
        mEsimSlot = mock(UiccSlot.class);
        mEmptySlot = mock(UiccSlot.class);
        mInactiveCard = mock(UiccCard.class);
        mActiveCard = mock(UiccCard.class);
        mInactivePort = mock(UiccPort.class);
        mActivePort = mock(UiccPort.class);
        mImsConnection0 = mock(ImsPhoneConnection.class);
        mImsConnection1 = mock(ImsPhoneConnection.class);
        mGsmConnection0 = mock(GsmCdmaConnection.class);
        mGsmConnection1 = mock(GsmCdmaConnection.class);
        mCsCall0 = mock(GsmCdmaCall.class);
        mCsCall1 = mock(GsmCdmaCall.class);
        mImsCall0 = mock(ImsPhoneCall.class);
        mImsCall1 = mock(ImsPhoneCall.class);

        replaceInstance(PhoneFactory.class, "sPhones", null, new Phone[] {mPhone, mSecondPhone});
        doReturn(CARRIER_ID_SLOT_0).when(mPhone).getCarrierId();
        // mPhone's mSST/mServiceState has been set up by TelephonyTest
        doReturn(CARRIER_ID_SLOT_1).when(mSecondPhone).getCarrierId();
        doReturn(mSignalStrength).when(mSecondPhone).getSignalStrength();
        doReturn(mSecondServiceStateTracker).when(mSecondPhone).getServiceStateTracker();
        doReturn(mSecondServiceState).when(mSecondServiceStateTracker).getServiceState();

        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(false).when(mServiceState).getVoiceRoaming();
        setServiceState(mSecondServiceState, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(false).when(mSecondServiceState).getVoiceRoaming();

        doReturn(true).when(mPhysicalSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot).getCardState();
        doReturn(false).when(mPhysicalSlot).isEuicc();
        doReturn(true).when(mEmptySlot).isActive();
        doReturn(CardState.CARDSTATE_ABSENT).when(mEmptySlot).getCardState();
        doReturn(true).when(mEsimSlot).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mEsimSlot).getCardState();
        doReturn(true).when(mEsimSlot).isEuicc();
        doReturn(0).when(mInactivePort).getNumApplications();
        doReturn(4).when(mActivePort).getNumApplications();

        doReturn(new UiccSlot[] {mPhysicalSlot}).when(mUiccController).getUiccSlots();
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlot(eq(0));
        doReturn(mPhysicalSlot).when(mUiccController).getUiccSlotForPhone(eq(0));

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
        mVoiceCallSessionStats0 = null;
        mVoiceCallSessionStats1 = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void singleImsCall_moRejected() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(0L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_REMOTE_CALL_DECLINE);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 200;
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_SUPER_WIDEBAND;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.callDuration = VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(0L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_SIP_FORBIDDEN);
        expectedCall.setupFailed = true;
        expectedCall.setupDurationMillis = 200;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.callDuration = VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 2200L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
    public void singleImsCall_moStartFailed() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(0L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_SIP_REQUEST_CANCELLED);
        expectedCall.setupFailed = true;
        expectedCall.setupDurationMillis = 200;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.callDuration = VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 2200L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.DIALING).when(mImsCall0).getState();
        doReturn(Call.State.DIALING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsDial(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2200L);
        mVoiceCallSessionStats0.onImsCallStartFailed(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_SIP_REQUEST_CANCELLED, 0));

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(false).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(1000L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MO,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 200;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_SUPER_WIDEBAND;
        expectedCall.disconnectExtraMessage = "normal call clearing";
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_ONE_MINUTE;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 100000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(0L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.callDuration = VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 8000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
    public void singleImsCall_mtStartFailed() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(0L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_SIP_REQUEST_CANCELLED);
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.callDuration = VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_UNKNOWN;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 8000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        doReturn(Call.State.INCOMING).when(mImsCall0).getState();
        doReturn(Call.State.INCOMING).when(mImsConnection0).getState();
        mVoiceCallSessionStats0.onImsCallReceived(mImsConnection0);
        mVoiceCallSessionStats0.setTimeMillis(2100L);
        mVoiceCallSessionStats0.onAudioCodecChanged(
                mImsConnection0, ImsStreamMediaProfile.AUDIO_QUALITY_AMR);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        mVoiceCallSessionStats0.onImsCallStartFailed(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_SIP_REQUEST_CANCELLED, 0));

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(60006L).when(mImsConnection0).getDurationMillis();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_FIVE_MINUTES;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        doReturn(new UiccPort[]{mInactivePort}).when(mInactiveCard).getUiccPortList();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(1));
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
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
        doReturn(new UiccPort[]{mActivePort}).when(mActiveCard).getUiccPortList();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(1));
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
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
        doReturn(new UiccPort[]{mActivePort}).when(mActiveCard).getUiccPortList();
        doReturn(new UiccSlot[] {mPhysicalSlot, mEsimSlot}).when(mUiccController).getUiccSlots();
        doReturn(mEsimSlot).when(mUiccController).getUiccSlot(eq(1));
        doReturn(mEsimSlot).when(mUiccController).getUiccSlotForPhone(eq(1));
        setServiceState(mSecondServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mSecondPhone).getImsPhone();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(300000L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask =
                1L << AudioCodec.AUDIO_CODEC_AMR | 1L << AudioCodec.AUDIO_CODEC_EVS_SWB;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_TEN_MINUTES;

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(600001L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.ratSwitchCount = 2L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.bandAtEnd = 0;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_THIRTY_MINUTES;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 4000L, 1L);
        VoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 4000L, 6000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 6000L, 8000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_HSPA);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(6000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleImsCall_ratSwitchToUnknown() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(600001L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration = 1;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.ratSwitchCount = 3L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.bandAtEnd = 0;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_THIRTY_MINUTES;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 4000L, 1L);
        VoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 4000L, 6000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 6000L, 8000L, 1L);
        VoiceCallRatUsage expectedRatUsageUnknown =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0,
                        TelephonyManager.NETWORK_TYPE_UNKNOWN,
                        8000L,
                        10000L,
                        1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_HSPA);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(6000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(8000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(10000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0, new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));

        ArgumentCaptor<VoiceCallSession> callCaptor =
                ArgumentCaptor.forClass(VoiceCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addVoiceCallSession(callCaptor.capture());
        verify(mPersistAtomsStorage, times(1)).addVoiceCallRatUsage(any());
        verifyNoMoreInteractions(mPersistAtomsStorage);
        assertProtoEquals(expectedCall, callCaptor.getValue());
        assertSortedProtoArrayEquals(
                new VoiceCallRatUsage[] {
                    expectedRatUsageLte,
                    expectedRatUsageHspa,
                    expectedRatUsageUmts,
                    expectedRatUsageUnknown
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleImsCall_rttOnDial() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(true).when(mImsConnection0).hasRttTextStream();
        doReturn(1800001L).when(mImsConnection0).getDurationMillis();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_LOCAL_CALL_DECLINE);
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.rttEnabled = true;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_LESS_THAN_ONE_HOUR;

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mImsConnection0).isIncoming();
        doReturn(2000L).when(mImsConnection0).getCreateTime();
        doReturn(mImsCall0).when(mImsConnection0).getCall();
        doReturn(3600005L).when(mImsConnection0).getDurationMillis();
        doReturn(new ArrayList(List.of(mImsConnection0))).when(mImsCall0).getConnections();
        VoiceCallSession expectedCall =
                makeSlot0CallProto(
                        VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS,
                        VOICE_CALL_SESSION__DIRECTION__CALL_DIRECTION_MT,
                        TelephonyManager.NETWORK_TYPE_LTE,
                        ImsReasonInfo.CODE_USER_TERMINATED);
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.rttEnabled = true;
        expectedCall.callDuration =
                VOICE_CALL_SESSION__CALL_DURATION__CALL_DURATION_MORE_THAN_ONE_HOUR;

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall0.setupDurationMillis = 80;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 1;
        expectedCall0.ratSwitchCount = 1L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        expectedCall0.bandAtEnd = 0;
        expectedCall0.lastKnownRat = TelephonyManager.NETWORK_TYPE_HSPA;
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
        expectedCall1.setupDurationMillis = 20;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 2L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall1.bandAtEnd = 0;
        expectedCall1.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        VoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 100000L, 2L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        100000L,
                        120000L,
                        1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_HSPA);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 0 hangup by remote
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection0,
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        // RAT change, HSPA to UMTS
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentImsCalls_firstCallHangupLast() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall0.setupDurationMillis = 80;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 0;
        expectedCall0.ratSwitchCount = 2L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall0.bandAtEnd = 0;
        expectedCall0.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
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
        expectedCall1.setupDurationMillis = 20;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 1;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        expectedCall1.bandAtEnd = 0;
        expectedCall1.lastKnownRat = TelephonyManager.NETWORK_TYPE_HSPA;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        VoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 100000L, 2L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0,
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        100000L,
                        120000L,
                        1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_HSPA);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        // call 1 hangup by remote
        mVoiceCallSessionStats0.setTimeMillis(90000L);
        mVoiceCallSessionStats0.onImsCallTerminated(
                mImsConnection1,
                new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE, 0));
        // RAT change, HSPA to UMTS
        mVoiceCallSessionStats0.setTimeMillis(100000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {
                    expectedRatUsageLte, expectedRatUsageHspa, expectedRatUsageUmts
                },
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentImsCalls_firstCallHangupDuringSecondCallSetup() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall0.setupDurationMillis = 80;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
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
        expectedCall1.setupDurationMillis = 20;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_HSPA;
        expectedCall1.bandAtEnd = 0;
        expectedCall1.lastKnownRat = TelephonyManager.NETWORK_TYPE_HSPA;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        VoiceCallRatUsage expectedRatUsageHspa =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_HSPA, 80000L, 90000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_HSPA);
        doReturn(TelephonyManager.NETWORK_TYPE_HSPA).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageHspa},
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
        expectedCall.bandAtEnd = 0;
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;
        expectedCall.setupDurationMillis = 5000;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.CALL_REJECTED;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 15000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
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
        expectedCall.bandAtEnd = 0;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = true;
        expectedCall.setupDurationMillis = 13000;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 0L;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_UNKNOWN;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 15000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.bandAtEnd = 0;
        expectedCall.setupDuration =
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_VERY_SLOW;
        expectedCall.setupDurationMillis = 5000;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 3000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 3000L, 100000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(Call.State.DIALING).when(mCsCall0).getState();
        doReturn(Call.State.DIALING).when(mGsmConnection0).getState();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilDial(mGsmConnection0);
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCsCall_mtRejected() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
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
        expectedCall.setupDurationMillis = 0;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.CALL_REJECTED;
        expectedCall.setupFailed = true;
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.bandAtEnd = 0;
        expectedCall.codecBitmask =
                (1L << AudioCodec.AUDIO_CODEC_AMR) | (1L << AudioCodec.AUDIO_CODEC_AMR_WB);
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 2500L, 15000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

        mVoiceCallSessionStats0.setTimeMillis(2000L);
        mVoiceCallSessionStats0.onServiceStateChanged(mServiceState);
        mVoiceCallSessionStats0.setTimeMillis(2500L);
        doReturn(Call.State.INCOMING).when(mCsCall0).getState();
        doReturn(Call.State.INCOMING).when(mGsmConnection0).getState();
        doReturn(DriverCall.AUDIO_QUALITY_AMR_WB).when(mGsmConnection0).getAudioCodec();
        doReturn(DisconnectCause.NOT_DISCONNECTED).when(mGsmConnection0).getDisconnectCause();
        mVoiceCallSessionStats0.onRilCallListChanged(List.of(mGsmConnection0));
        mVoiceCallSessionStats0.setTimeMillis(3000L);
        mVoiceCallSessionStats0.onAudioCodecChanged(mGsmConnection0, DriverCall.AUDIO_QUALITY_AMR);
        doReturn(DriverCall.AUDIO_QUALITY_AMR).when(mGsmConnection0).getAudioCodec();
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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_ULTRA_FAST;
        expectedCall.setupDurationMillis = 500;
        expectedCall.disconnectExtraCode = PreciseDisconnectCause.NORMAL;
        expectedCall.setupFailed = false;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.bandAtEnd = 0;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 2500L, 100000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.srvccFailureCount = 2L;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.bandAtEnd = 0;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 10000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 10000L, 12000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleCall_srvccCanceled() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.srvccCancellationCount = 2L;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 12000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall.setupDurationMillis = 80;
        expectedCall.setupFailed = false;
        expectedCall.srvccCancellationCount = 1L;
        expectedCall.srvccFailureCount = 1L;
        expectedCall.srvccCompleted = true;
        expectedCall.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        expectedCall.ratSwitchCount = 1L;
        expectedCall.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall.bandAtEnd = 0;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 7000L, 1L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 7000L, 12000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void concurrentCalls_srvcc() {
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(new ArrayList(List.of(mImsConnection0, mImsConnection1)))
                .when(mImsPhone)
                .getHandoverConnection();
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
                VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_EXTREMELY_FAST;
        expectedCall0.setupDurationMillis = 80;
        expectedCall0.setupFailed = false;
        expectedCall0.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall0.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall0.concurrentCallCountAtStart = 0;
        expectedCall0.concurrentCallCountAtEnd = 1;
        expectedCall0.ratSwitchCount = 1L;
        expectedCall0.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall0.bandAtEnd = 0;
        expectedCall0.srvccCompleted = true;
        expectedCall0.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        expectedCall0.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
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
        expectedCall1.setupDurationMillis = 20;
        expectedCall1.setupFailed = false;
        expectedCall1.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall1.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall1.concurrentCallCountAtStart = 1;
        expectedCall1.concurrentCallCountAtEnd = 0;
        expectedCall1.ratSwitchCount = 1L;
        expectedCall1.ratAtEnd = TelephonyManager.NETWORK_TYPE_UMTS;
        expectedCall1.bandAtEnd = 0;
        expectedCall1.srvccCompleted = true;
        expectedCall1.bearerAtEnd = VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
        expectedCall1.lastKnownRat = TelephonyManager.NETWORK_TYPE_UMTS;
        VoiceCallRatUsage expectedRatUsageLte =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_LTE, 2000L, 80000L, 2L);
        VoiceCallRatUsage expectedRatUsageUmts =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_UMTS, 80000L, 120000L, 2L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceState(mServiceState, TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mImsStats).getImsVoiceRadioTech();
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
                new VoiceCallRatUsage[] {expectedRatUsageLte, expectedRatUsageUmts},
                ratUsage.get());
    }

    @Test
    @SmallTest
    public void singleWifiCall_preferred() {
        setServiceStateWithWifiCalling(mServiceState, TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        expectedCall.bandAtEnd = 0;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_IWLAN, 2000L, 8000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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
        setServiceStateWithWifiCalling(mServiceState, TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();
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
        expectedCall.ratAtConnected = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        expectedCall.bandAtEnd = 0;
        expectedCall.codecBitmask = 1L << AudioCodec.AUDIO_CODEC_AMR;
        expectedCall.mainCodecQuality =
                VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_NARROWBAND;
        VoiceCallRatUsage expectedRatUsage =
                makeRatUsageProto(
                        CARRIER_ID_SLOT_0, TelephonyManager.NETWORK_TYPE_IWLAN, 2000L, 8000L, 1L);
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = setupRatUsageCapture();

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

    private AtomicReference<VoiceCallRatUsage[]> setupRatUsageCapture() {
        final AtomicReference<VoiceCallRatUsage[]> ratUsage = new AtomicReference<>(null);
        doAnswer(
                invocation -> {
                        VoiceCallRatTracker tracker =
                                    (VoiceCallRatTracker) invocation.getArguments()[0];
                        ratUsage.set(tracker.toProto());
                        return null; // for void
                })
                .when(mPersistAtomsStorage)
                .addVoiceCallRatUsage(any());
        return ratUsage;
    }

    private static void setServiceState(ServiceState mock, @NetworkType int rat) {
        doReturn(rat).when(mock).getVoiceNetworkType();
        doReturn(rat).when(mock).getDataNetworkType();
        NetworkRegistrationInfo regInfo =
                new NetworkRegistrationInfo.Builder()
                        .setAccessNetworkTechnology(rat)
                        .setRegistrationState(
                                rat == TelephonyManager.NETWORK_TYPE_UNKNOWN
                                        ? NetworkRegistrationInfo
                                                .REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING
                                        : NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                        .build();
        doReturn(regInfo).when(mock)
                .getNetworkRegistrationInfo(
                        anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
    }

    private static void setServiceStateWithWifiCalling(ServiceState mock, @NetworkType int rat) {
        doReturn(rat).when(mock).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mock).getDataNetworkType();
        NetworkRegistrationInfo wwanRegInfo =
                new NetworkRegistrationInfo.Builder()
                        .setAccessNetworkTechnology(rat)
                        .setRegistrationState(
                                rat == TelephonyManager.NETWORK_TYPE_UNKNOWN
                                        ? NetworkRegistrationInfo
                                                .REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING
                                        : NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                        .build();
        doReturn(wwanRegInfo).when(mock)
                .getNetworkRegistrationInfo(
                        anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        NetworkRegistrationInfo wlanRegInfo =
                new NetworkRegistrationInfo.Builder()
                        .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN).build();
        doReturn(wlanRegInfo).when(mock)
                .getNetworkRegistrationInfo(
                        eq(NetworkRegistrationInfo.DOMAIN_PS),
                        eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
    }

    private static VoiceCallSession makeSlot0CallProto(
            int bearer, int direction, int rat, int reason) {
        VoiceCallSession call = new VoiceCallSession();
        call.bearerAtStart = bearer;
        call.bearerAtEnd = bearer;
        call.direction = direction;
        call.setupDuration = VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
        call.setupDurationMillis = 0;
        call.setupFailed = true;
        call.disconnectReasonCode = reason;
        call.disconnectExtraCode = 0;
        call.disconnectExtraMessage = "";
        call.ratAtStart = rat;
        call.ratAtConnected = rat;
        call.ratAtEnd = rat;
        call.lastKnownRat = rat;
        call.bandAtEnd = 1;
        call.ratSwitchCount = 0L;
        call.codecBitmask = 0L;
        call.mainCodecQuality = VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_UNKNOWN;
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
        call.signalStrengthAtEnd = 2;
        return call;
    }

    private static VoiceCallSession makeSlot1CallProto(
            int bearer, int direction, int rat, int reason) {
        VoiceCallSession call = new VoiceCallSession();
        call.bearerAtStart = bearer;
        call.bearerAtEnd = bearer;
        call.direction = direction;
        call.setupDuration = VOICE_CALL_SESSION__SETUP_DURATION__CALL_SETUP_DURATION_UNKNOWN;
        call.setupDurationMillis = 0;
        call.setupFailed = true;
        call.disconnectReasonCode = reason;
        call.disconnectExtraCode = 0;
        call.disconnectExtraMessage = "";
        call.ratAtStart = rat;
        call.ratAtConnected = rat;
        call.ratAtEnd = rat;
        call.lastKnownRat = rat;
        call.bandAtEnd = 1;
        call.ratSwitchCount = 0L;
        call.codecBitmask = 0L;
        call.mainCodecQuality = VOICE_CALL_SESSION__MAIN_CODEC_QUALITY__CODEC_QUALITY_UNKNOWN;
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
        call.signalStrengthAtEnd = 2;
        return call;
    }

    private static VoiceCallRatUsage makeRatUsageProto(
            int carrierId, int rat, long beginMillis, long endMillis, long callCount) {
        VoiceCallRatUsage usage = new VoiceCallRatUsage();
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
