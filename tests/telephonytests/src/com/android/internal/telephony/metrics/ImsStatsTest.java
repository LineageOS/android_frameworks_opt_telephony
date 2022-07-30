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

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO;
import static android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_NR;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ProvisioningManager;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities;
import android.telephony.ims.feature.MmTelFeature.MmTelCapabilities.MmTelCapability;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationStats;
import com.android.internal.telephony.nano.PersistAtomsProto.ImsRegistrationTermination;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccSlot;

import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

public class ImsStatsTest extends TelephonyTest {
    private static final long START_TIME_MILLIS = 2000L;
    private static final int CARRIER1_ID = 1;
    private static final int CARRIER2_ID = 1187;

    @MmTelCapability
    private static final int CAPABILITY_TYPE_ALL =
            MmTelCapabilities.CAPABILITY_TYPE_VOICE
                    | MmTelCapabilities.CAPABILITY_TYPE_VIDEO
                    | MmTelCapabilities.CAPABILITY_TYPE_SMS
                    | MmTelCapabilities.CAPABILITY_TYPE_UT;

    // Mocked classes
    private UiccSlot mPhysicalSlot0;
    private UiccSlot mPhysicalSlot1;
    private Phone mSecondPhone;
    private ImsPhone mSecondImsPhone;
    private ServiceStateStats mServiceStateStats;

    private TestableImsStats mImsStats;

    private static class TestableImsStats extends ImsStats {
        private long mTimeMillis = START_TIME_MILLIS;

        TestableImsStats(ImsPhone imsPhone) {
            super(imsPhone);
        }

        @Override
        protected long getTimeMillis() {
            // NOTE: super class constructor will be executed before private field is set, which
            // gives the wrong start time (mTimeMillis will have its default value of 0L)
            return mTimeMillis == 0L ? START_TIME_MILLIS : mTimeMillis;
        }

        private void setTimeMillis(long timeMillis) {
            mTimeMillis = timeMillis;
        }

        private void incTimeMillis(long timeMillis) {
            mTimeMillis += timeMillis;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mPhysicalSlot0 = mock(UiccSlot.class);
        mPhysicalSlot1 = mock(UiccSlot.class);
        mSecondPhone = mock(Phone.class);
        mSecondImsPhone = mock(ImsPhone.class);
        mServiceStateStats = mock(ServiceStateStats.class);

        doReturn(CARRIER1_ID).when(mPhone).getCarrierId();
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(mSST).when(mImsPhone).getServiceStateTracker();
        doReturn(mServiceStateStats).when(mSST).getServiceStateStats();

        // WWAN PS RAT is LTE
        doReturn(new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build())
                .when(mServiceState).getNetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN);

        // Single physical SIM
        doReturn(true).when(mPhysicalSlot0).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot0).getCardState();
        doReturn(false).when(mPhysicalSlot0).isEuicc();
        doReturn(new UiccSlot[] {mPhysicalSlot0}).when(mUiccController).getUiccSlots();
        doReturn(mPhysicalSlot0).when(mUiccController).getUiccSlot(0);
        doReturn(mPhysicalSlot0).when(mUiccController).getUiccSlotForPhone(0);

        mImsStats = new TestableImsStats(mImsPhone);
    }

    @After
    public void tearDown() throws Exception {
        mImsStats = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void conclude_registered() throws Exception {
        // IMS over LTE
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VIDEO,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_UT,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_SMS,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_ALL));

        mImsStats.incTimeMillis(2000L);
        mImsStats.conclude();

        // Duration should be counted
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(2000L, stats.voiceAvailableMillis);
        assertEquals(2000L, stats.videoCapableMillis);
        assertEquals(2000L, stats.videoAvailableMillis);
        assertEquals(2000L, stats.utCapableMillis);
        assertEquals(2000L, stats.utAvailableMillis);
        assertEquals(2000L, stats.smsCapableMillis);
        assertEquals(2000L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_registeredPartialFeatures() throws Exception {
        // IMS over LTE
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VIDEO,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_UT,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_SMS,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        mImsStats.incTimeMillis(2000L);
        mImsStats.conclude();

        // Duration should be counted
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(2000L, stats.voiceAvailableMillis);
        assertEquals(2000L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(2000L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(2000L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_registeredVoiceOnly() throws Exception {
        // Wifi calling
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_IWLAN,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VIDEO,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_UT,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WLAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_IWLAN, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        mImsStats.incTimeMillis(2000L);
        mImsStats.conclude();

        // Duration should be counted
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(2000L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_notRegistered() throws Exception {
        // IMS over LTE
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VIDEO,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_UT,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_SMS,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_ALL));

        mImsStats.incTimeMillis(2000L);
        mImsStats.conclude();

        // No atom should be generated
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsCapabilitiesChanged_sameTech() throws Exception {
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);

        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        // Atom with previous feature availability should be generated
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(0L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
        // ServiceStateStats should be notified
        verify(mServiceStateStats).onImsVoiceRegistrationChanged();
    }

    @Test
    @SmallTest
    public void onImsCapabilitiesChanged_differentTech() throws Exception {
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        verify(mServiceStateStats).onImsVoiceRegistrationChanged();

        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_NR, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        // Atom with previous feature availability should be generated
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(2000L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
        // ServiceStateStats should be notified
        verify(mServiceStateStats, times(2)).onImsVoiceRegistrationChanged();
    }

    @Test
    @SmallTest
    public void onImsCapabilitiesChanged_differentTechNoVoice() throws Exception {
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_SMS,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_SMS));

        verify(mServiceStateStats, never()).onImsVoiceRegistrationChanged();

        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_NR, new MmTelCapabilities(CAPABILITY_TYPE_SMS));

        // Atom with previous feature availability should be generated
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(0L, stats.voiceCapableMillis);
        assertEquals(0L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(2000L, stats.smsCapableMillis);
        assertEquals(2000L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
        // ServiceStateStats should not be notified
        verify(mServiceStateStats, never()).onImsVoiceRegistrationChanged();
    }

    @Test
    @SmallTest
    public void onSetFeatureResponse_sameTech() throws Exception {
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VOICE,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);

        mImsStats.incTimeMillis(2000L);
        mImsStats.onSetFeatureResponse(
                CAPABILITY_TYPE_VIDEO,
                REGISTRATION_TECH_LTE,
                ProvisioningManager.PROVISIONING_VALUE_ENABLED);

        // Atom with previous capability should be generated
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(captor.capture());
        ImsRegistrationStats stats = captor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(2000L, stats.voiceCapableMillis);
        assertEquals(0L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsRegistered_differentTech() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WLAN);
        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);

        // At this point, the first 2 registrations should have their durations counted
        ArgumentCaptor<ImsRegistrationStats> captor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage, times(2)).addImsRegistrationStats(captor.capture());
        assertEquals(2, captor.getAllValues().size());
        ImsRegistrationStats statsLte = captor.getAllValues().get(0);
        assertEquals(CARRIER1_ID, statsLte.carrierId);
        assertEquals(0, statsLte.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, statsLte.rat);
        assertEquals(2000L, statsLte.registeredMillis);
        assertEquals(0L, statsLte.voiceCapableMillis);
        assertEquals(0L, statsLte.voiceAvailableMillis);
        assertEquals(0L, statsLte.videoCapableMillis);
        assertEquals(0L, statsLte.videoAvailableMillis);
        assertEquals(0L, statsLte.utCapableMillis);
        assertEquals(0L, statsLte.utAvailableMillis);
        assertEquals(0L, statsLte.smsCapableMillis);
        assertEquals(0L, statsLte.smsAvailableMillis);
        ImsRegistrationStats statsWifi = captor.getAllValues().get(1);
        assertEquals(CARRIER1_ID, statsWifi.carrierId);
        assertEquals(0, statsWifi.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, statsWifi.rat);
        assertEquals(2000L, statsWifi.registeredMillis);
        assertEquals(0L, statsWifi.voiceCapableMillis);
        assertEquals(0L, statsWifi.voiceAvailableMillis);
        assertEquals(0L, statsWifi.videoCapableMillis);
        assertEquals(0L, statsWifi.videoAvailableMillis);
        assertEquals(0L, statsWifi.utCapableMillis);
        assertEquals(0L, statsWifi.utAvailableMillis);
        assertEquals(0L, statsWifi.smsCapableMillis);
        assertEquals(0L, statsWifi.smsAvailableMillis);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsUnregistered_setupFailure() throws Exception {
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 999, "Timeout"));

        // Atom with termination info should be generated
        ArgumentCaptor<ImsRegistrationTermination> captor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(captor.capture());
        ImsRegistrationTermination termination = captor.getValue();
        assertEquals(CARRIER1_ID, termination.carrierId);
        assertFalse(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, termination.ratAtEnd);
        assertTrue(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(999, termination.extraCode);
        assertEquals("Timeout", termination.extraMessage);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsUnregistered_setupFailureWithProgress() throws Exception {
        mImsStats.onImsRegistering(REGISTRATION_TECH_LTE);
        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 999, "Timeout"));

        // Atom with termination info should be generated
        ArgumentCaptor<ImsRegistrationTermination> captor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(captor.capture());
        ImsRegistrationTermination termination = captor.getValue();
        assertEquals(CARRIER1_ID, termination.carrierId);
        assertFalse(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, termination.ratAtEnd);
        assertTrue(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(999, termination.extraCode);
        assertEquals("Timeout", termination.extraMessage);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsUnregistered_afterRegistered() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 999, "Timeout"));

        // Atom with termination info and durations should be generated
        ArgumentCaptor<ImsRegistrationStats> statsCaptor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(statsCaptor.capture());
        ImsRegistrationStats stats = statsCaptor.getValue();
        assertEquals(CARRIER1_ID, stats.carrierId);
        assertEquals(0, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(0L, stats.voiceCapableMillis);
        assertEquals(0L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        ArgumentCaptor<ImsRegistrationTermination> terminationCaptor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(terminationCaptor.capture());
        ImsRegistrationTermination termination = terminationCaptor.getValue();
        assertEquals(CARRIER1_ID, termination.carrierId);
        assertFalse(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, termination.ratAtEnd);
        assertFalse(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(999, termination.extraCode);
        assertEquals("Timeout", termination.extraMessage);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsUnregistered_nullMessage() throws Exception {
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 0, null));

        // Atom with termination info should be generated, null string should be sanitized
        ArgumentCaptor<ImsRegistrationTermination> captor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(captor.capture());
        ImsRegistrationTermination termination = captor.getValue();
        assertEquals(CARRIER1_ID, termination.carrierId);
        assertFalse(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, termination.ratAtEnd);
        assertTrue(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(0, termination.extraCode);
        assertEquals("", termination.extraMessage);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsUnregistered_longMessage() throws Exception {
        String longExtraMessage =
                "This message is too long -- it has more than 128 characters: "
                        + "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
                        + "ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ"
                        + " This is the end of the message.";
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 0, longExtraMessage));

        // Atom with termination info should be generated, null string should be sanitized
        ArgumentCaptor<ImsRegistrationTermination> captor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(captor.capture());
        ImsRegistrationTermination termination = captor.getValue();
        assertEquals(CARRIER1_ID, termination.carrierId);
        assertFalse(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, termination.ratAtEnd);
        assertTrue(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(0, termination.extraCode);
        assertEquals(128, termination.extraMessage.length());
        assertTrue(longExtraMessage.startsWith(termination.extraMessage));
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void filterExtraMessage_noNeedToFilter() throws Exception {
        final String[] messages = {
            "Q.850;cause=16",
            "SIP;cause=200",
            "q.850",
            "600",
            "Call ended during conference merge process.",
            "cc_term_noreply_tmr_expired",
            "[e] user triggered",
            "normal end of call",
            "cc_q850_017_user_busy",
            "service unavailable (1:223)",
            "IP Change",
            "rtp-rtcp timeout",
            "0x0000030b",
            "CD-021: ISP Problem",
            "IP;cause=487;text=\"Originator canceled;Canceled(t),iCode=CC_SIP_REQUEST_TERMINATED\"",
            "pt: asr: insufficient_bearer_resources",
            "po: aaa: result_code=0 exp_result_code=5065",
            "a peer released.total external peer:1. allowed:2. clear the remain parties(o),"
                    + "icode=cc_sip_request_timeout"
        };

        for (String message : messages) {
            assertEquals(message, ImsStats.filterExtraMessage(message));
        }
    }

    @Test
    @SmallTest
    public void filterExtraMessage_needToFilter() throws Exception {
        Map<String, String> originalAndExpectedMessages = ImmutableMap.<String, String>builder()
                // UUIDs
                .put(
                        "Q.850;cause=34;text=\"12345678-abcd-ef12-34ab-000000000012;"
                                + "User is busy and currently active on another call.\"",
                        "Q.850;cause=34;text=\"<UUID_REDACTED>;"
                                + "User is busy and currently active on another call.\"")
                .put(
                        "Q.850;cause=34;text=\"12345678-ABCD-EF12-34AB-000000000012;"
                                + "User is busy and currently active on another call.\"",
                        "Q.850;cause=34;text=\"<UUID_REDACTED>;"
                                + "User is busy and currently active on another call.\"")
                // URIs
                .put(
                        "SIP;cause=500;text=\"sip:+1234567890@irps.voip.telefonica.de;user=phone"
                                + " clear the call.;Canceled(t)\"",
                        "SIP;cause=500;text=\"sip:<REDACTED>;user=phone"
                                + " clear the call.;Canceled(t)\"")
                .put(
                        "SIP;cause=500;text=\"SIP:+1234567890@irps.voip.telefonica.de;user=phone"
                                + " clear the call.;Canceled(t)\"",
                        "SIP;cause=500;text=\"SIP:<REDACTED>;user=phone"
                                + " clear the call.;Canceled(t)\"")
                // IP addresses
                .put(
                        "dtls handshake error[timeout][2607:F8B0::1] and client disconnected",
                        "dtls handshake error[timeout][<IPV6_REDACTED>] and client disconnected")
                .put(
                        "dtls handshake error[timeout][2607:f8b0::1] and client disconnected",
                        "dtls handshake error[timeout][<IPV6_REDACTED>] and client disconnected")
                .put(
                        "dtls handshake error 2607:f8b0:1:2:3:4:56:789",
                        "dtls handshake error <IPV6_REDACTED>")
                .put(
                        "dtls handshake error[timeout][8.8.8.8] and client disconnected",
                        "dtls handshake error[timeout][<IPV4_REDACTED>] and client disconnected")
                .put("8.8.8.8 client disconnected", "<IPV4_REDACTED> client disconnected")
                // IMEIs/IMSIs
                .put(
                        "call completed elsewhere by instance 313460000000001",
                        "call completed elsewhere by instance <IMEI_IMSI_REDACTED>")
                .put(
                        "call completed elsewhere by instance 31346000-000000-1",
                        "call completed elsewhere by instance <IMEI_REDACTED>")
                .put(
                        "call completed elsewhere by instance 31-346000-000000-1",
                        "call completed elsewhere by instance <IMEI_REDACTED>")
                .put(
                        "call completed elsewhere by instance 31-346000-000000-12",
                        "call completed elsewhere by instance <IMEI_REDACTED>")
                .put(
                        "399 123.4567.89.ATS.blah.ims.mnc123.mcc456.3gppnetwork.org"
                                + " \"Failure cause code is sip status code.\"",
                        "399 <HOSTNAME_REDACTED> \"Failure cause code is sip status code.\"")
                // Unknown IDs
                .put(
                        "01200.30004.a.560.789.123.0.0.00045.00000006"
                                + " released the session because of netfail by no media",
                        // "123.0.0.0" looks like IPv4
                        "<ID_REDACTED><IPV4_REDACTED><ID_REDACTED>"
                                + " released the session because of netfail by no media")
                .put(
                        "example.cpp,1234,12-300-450-67-89123456:-12345678,"
                                + "tyringtimeout:timer b expired(t)",
                        "example.cpp,1234,<ID_REDACTED>:-<ID_REDACTED>,"
                                + "tyringtimeout:timer b expired(t)")
                .put(
                        "ss120000f123l1234 invite 2xx after cancel rsp has been received",
                        "ss<ID_REDACTED>l1234 invite 2xx after cancel rsp has been received")
                .put(
                        "X.int;reasoncode=0x00000123;add-info=0123.00AB.0001",
                        "X.int;reasoncode=0x00000123;add-info=<ID_REDACTED>")
                .put(
                        "X.int;reasoncode=0x00123abc;add-info=0123.00AB.0001",
                        "X.int;reasoncode=0x<ID_REDACTED>;add-info=<ID_REDACTED>")
                .put(
                        "Cx Unable To Comply 1203045067D8009",
                        "Cx Unable To Comply <ID_REDACTED>")
                .build();

        for (Map.Entry<String, String> entry : originalAndExpectedMessages.entrySet()) {
            assertEquals(entry.getValue(), ImsStats.filterExtraMessage(entry.getKey()));
        }
    }

    @Test
    @SmallTest
    public void onImsUnregistered_multiSim() throws Exception {
        doReturn(mSecondImsPhone).when(mSecondPhone).getImsPhone();
        doReturn(mSecondPhone).when(mSecondImsPhone).getDefaultPhone();
        doReturn(1).when(mSecondPhone).getPhoneId();
        doReturn(1).when(mSecondImsPhone).getPhoneId();
        doReturn(CARRIER2_ID).when(mSecondPhone).getCarrierId();
        doReturn(true).when(mPhysicalSlot1).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot1).getCardState();
        doReturn(false).when(mPhysicalSlot1).isEuicc();
        doReturn(new UiccSlot[] {mPhysicalSlot0, mPhysicalSlot1})
                .when(mUiccController)
                .getUiccSlots();
        doReturn(mPhysicalSlot1).when(mUiccController).getUiccSlot(1);
        doReturn(mPhysicalSlot1).when(mUiccController).getUiccSlotForPhone(1);
        // Reusing service state tracker from phone 0 for simplicity
        doReturn(mSST).when(mSecondPhone).getServiceStateTracker();
        doReturn(mSST).when(mSecondImsPhone).getServiceStateTracker();
        mImsStats = new TestableImsStats(mSecondImsPhone);
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.incTimeMillis(2000L);
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 999, "Timeout"));

        // Atom with termination info and durations should be generated
        ArgumentCaptor<ImsRegistrationStats> statsCaptor =
                ArgumentCaptor.forClass(ImsRegistrationStats.class);
        verify(mPersistAtomsStorage).addImsRegistrationStats(statsCaptor.capture());
        ImsRegistrationStats stats = statsCaptor.getValue();
        assertEquals(CARRIER2_ID, stats.carrierId);
        assertEquals(1, stats.simSlotIndex);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.rat);
        assertEquals(2000L, stats.registeredMillis);
        assertEquals(0L, stats.voiceCapableMillis);
        assertEquals(0L, stats.voiceAvailableMillis);
        assertEquals(0L, stats.videoCapableMillis);
        assertEquals(0L, stats.videoAvailableMillis);
        assertEquals(0L, stats.utCapableMillis);
        assertEquals(0L, stats.utAvailableMillis);
        assertEquals(0L, stats.smsCapableMillis);
        assertEquals(0L, stats.smsAvailableMillis);
        ArgumentCaptor<ImsRegistrationTermination> terminationCaptor =
                ArgumentCaptor.forClass(ImsRegistrationTermination.class);
        verify(mPersistAtomsStorage).addImsRegistrationTermination(terminationCaptor.capture());
        ImsRegistrationTermination termination = terminationCaptor.getValue();
        assertEquals(CARRIER2_ID, termination.carrierId);
        assertTrue(termination.isMultiSim);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, termination.ratAtEnd);
        assertFalse(termination.setupFailed);
        assertEquals(ImsReasonInfo.CODE_REGISTRATION_ERROR, termination.reasonCode);
        assertEquals(999, termination.extraCode);
        assertEquals("Timeout", termination.extraMessage);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_noRegistration() throws Exception {
        // Do nothing

        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_noVoiceRegistration() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_SMS));

        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_cellularRegistration() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_wifiRegistration() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WLAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_IWLAN, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));

        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_unregistered() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));
        mImsStats.onImsUnregistered(
                new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR, 999, "Timeout"));
        doReturn(
                        new NetworkRegistrationInfo.Builder()
                                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                                .build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN);


        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_serviceStateChanged() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_LTE, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));
        doReturn(
                        new NetworkRegistrationInfo.Builder()
                                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                                .setRegistrationState(
                                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                                .build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN);
        mImsStats.onServiceStateChanged(mServiceState);
        assertEquals(TelephonyManager.NETWORK_TYPE_NR, mImsStats.getImsVoiceRadioTech());
    }

    @Test
    @SmallTest
    public void getImsVoiceRadioTech_serviceStateChanged_wlan() throws Exception {
        mImsStats.onImsRegistered(TRANSPORT_TYPE_WWAN);
        mImsStats.onImsCapabilitiesChanged(
                REGISTRATION_TECH_IWLAN, new MmTelCapabilities(CAPABILITY_TYPE_VOICE));
        doReturn(
                        new NetworkRegistrationInfo.Builder()
                                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                                .setRegistrationState(
                                        NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                                .build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN);
        mImsStats.onServiceStateChanged(mServiceState);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, mImsStats.getImsVoiceRadioTech());
    }
}
