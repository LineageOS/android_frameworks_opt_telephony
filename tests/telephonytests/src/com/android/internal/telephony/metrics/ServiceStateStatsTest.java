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

import static android.telephony.TelephonyManager.DATA_CONNECTED;
import static android.telephony.TelephonyManager.DATA_UNKNOWN;

import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_CLOSED;
import static com.android.internal.telephony.TelephonyStatsLog.CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_UNKNOWN;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS;
import static com.android.internal.telephony.TelephonyStatsLog.VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.NetworkType;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularDataServiceSwitch;
import com.android.internal.telephony.nano.PersistAtomsProto.CellularServiceState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.UiccSlot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ServiceStateStatsTest extends TelephonyTest {
    private static final long START_TIME_MILLIS = 2000L;
    private static final int CARRIER1_ID = 1;
    private static final int CARRIER2_ID = 1187;

    // Mocked classes
    private UiccSlot mPhysicalSlot0;
    private UiccSlot mPhysicalSlot1;
    private Phone mSecondPhone;

    private TestableServiceStateStats mServiceStateStats;

    private static class TestableServiceStateStats extends ServiceStateStats {
        private long mTimeMillis = START_TIME_MILLIS;

        TestableServiceStateStats(Phone phone) {
            super(phone);
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

        doReturn(CARRIER1_ID).when(mPhone).getCarrierId();
        doReturn(mImsPhone).when(mPhone).getImsPhone();

        // Single physical SIM
        doReturn(true).when(mPhysicalSlot0).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot0).getCardState();
        doReturn(false).when(mPhysicalSlot0).isEuicc();
        doReturn(new UiccSlot[] {mPhysicalSlot0}).when(mUiccController).getUiccSlots();
        doReturn(mPhysicalSlot0).when(mUiccController).getUiccSlot(0);
        doReturn(mPhysicalSlot0).when(mUiccController).getUiccSlotForPhone(0);

        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();

        doReturn(DATA_CONNECTED).when(mDataNetworkController).getInternetDataNetworkState();
        doReturn(mDataNetworkController).when(mSecondPhone).getDataNetworkController();

        mServiceStateStats = new TestableServiceStateStats(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        mServiceStateStats = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void conclude_inService() throws Exception {
        // Using default service state for LTE
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // Duration should be counted and there should not be any switch
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_outOfService() throws Exception {
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(DATA_UNKNOWN).when(mDataNetworkController).getInternetDataNetworkState();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // Duration should be counted and there should not be any switch
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(false, state.isInternetPdnUp);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_airplaneMode() throws Exception {
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // There should be no new switches, service states, or added durations
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_airplaneModeWithWifiCalling() throws Exception {
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // There should be no new switches, service states, or added durations
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_noSimCardEmergencyOnly() throws Exception {
        doReturn(CardState.CARDSTATE_ABSENT).when(mPhysicalSlot0).getCardState();
        mockLimitedService(TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(-1).when(mPhone).getCarrierId();
        doReturn(DATA_UNKNOWN).when(mDataNetworkController).getInternetDataNetworkState();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // Duration should be counted and there should not be any switch
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(-1, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(true, state.isEmergencyOnly);
        assertEquals(false, state.isInternetPdnUp);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_noSimCardOutOfService() throws Exception {
        doReturn(CardState.CARDSTATE_ABSENT).when(mPhysicalSlot0).getCardState();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
        doReturn(DATA_UNKNOWN).when(mDataNetworkController).getInternetDataNetworkState();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // Duration should be counted and there should not be any switch
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(-1, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(false, state.isInternetPdnUp);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_longOnGoingServiceState() throws Exception {
        // Using default service state for LTE
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();
        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // There should be 2 separate service state updates, which should be different objects
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsVoiceRegistrationChanged_wifiCallingWhileOos() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        mServiceStateStats.onServiceStateChanged(mServiceState);
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.onImsVoiceRegistrationChanged();
        mServiceStateStats.incTimeMillis(200L);
        mServiceStateStats.conclude();

        // There should be 2 separate service state updates, which should be different objects
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onImsVoiceRegistrationChanged_crossSimCalling() throws Exception {
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();
        doReturn(ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM).when(mImsPhone)
                .getImsRegistrationTech();
        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.onImsVoiceRegistrationChanged();
        mServiceStateStats.incTimeMillis(200L);
        mServiceStateStats.conclude();

        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(1);

        assertEquals(200L, state.totalTimeMillis);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, state.voiceRat);
        assertTrue(state.isIwlanCrossSim);
    }

    @Test
    @SmallTest
    public void onInternetDataNetworkDisconnected() throws Exception {
         // Using default service state for LTE
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.onInternetDataNetworkDisconnected();
        mServiceStateStats.incTimeMillis(200L);
        mServiceStateStats.conclude();

        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(false, state.isInternetPdnUp);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_sameRats() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.onServiceStateChanged(mServiceState);

        // Should produce 1 service state atom with LTE and no data service switch
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_differentDataRats() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);

        // There should be 2 service states and a data service switch
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> serviceStateCaptor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        ArgumentCaptor<CellularDataServiceSwitch> serviceSwitchCaptor =
                ArgumentCaptor.forClass(CellularDataServiceSwitch.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(
                        serviceStateCaptor.capture(), serviceSwitchCaptor.capture());
        CellularServiceState state = serviceStateCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        CellularDataServiceSwitch serviceSwitch = serviceSwitchCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, serviceSwitch.ratFrom);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, serviceSwitch.ratTo);
        assertEquals(0, serviceSwitch.simSlotIndex);
        assertFalse(serviceSwitch.isMultiSim);
        assertEquals(CARRIER1_ID, serviceSwitch.carrierId);
        assertEquals(1, serviceSwitch.switchCount);
        assertNull(serviceSwitchCaptor.getAllValues().get(1)); // produced by conclude()
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_differentVoiceRats() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice RAT changes to IWLAN and data RAT stays in LTE according to WWAN PS RAT
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mImsStats).getImsVoiceRadioTech();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);

        // There should be 2 service states but no data service switch
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_iwlanButNotWifiCalling() throws Exception {
        // Using default service state for LTE as WWAN PS RAT
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();

        mServiceStateStats.onServiceStateChanged(mServiceState);

        // Should produce 1 service state atom with voice and data (WWAN PS) RAT as LTE
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage)
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getValue();
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(0L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_endc() throws Exception {
        // Using default service state for LTE without ENDC

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // ENDC should stay false
        doReturn(NetworkRegistrationInfo.NR_STATE_RESTRICTED).when(mServiceState).getNrState();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);
        // ENDC should become true
        doReturn(NetworkRegistrationInfo.NR_STATE_NOT_RESTRICTED).when(mServiceState).getNrState();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(400L);
        // ENDC should stay true
        doReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED).when(mServiceState).getNrState();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(800L);

        // There should be 4 service state updates (2 distinct service states) but no service switch
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(4))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(2);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertTrue(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(400L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(3);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertTrue(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(800L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_simSwapSameRat() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // SIM removed, emergency call only
        doReturn(CardState.CARDSTATE_ABSENT).when(mPhysicalSlot0).getCardState();
        mockLimitedService(TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(-1).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(5000L);
        // New SIM inserted
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot0).getCardState();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        doReturn(CARRIER2_ID).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);

        // There should be 3 service states, but there should be no switches due to carrier change
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(3))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(-1, state.carrierId);
        assertEquals(5000L, state.totalTimeMillis);
        assertEquals(true, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(2);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER2_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_roaming() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice roaming
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        NetworkRegistrationInfo voiceNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        voiceNri.setRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
        doReturn(voiceNri)
                .when(mServiceState)
                .getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);
        // Voice and data roaming
        NetworkRegistrationInfo dataNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        dataNri.setRoamingType(ServiceState.ROAMING_TYPE_INTERNATIONAL);
        doReturn(dataNri)
                .when(mServiceState)
                .getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(400L);

        // There should be 3 service states and 1 data service switch (LTE to UMTS)
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> serviceStateCaptor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        ArgumentCaptor<CellularDataServiceSwitch> serviceSwitchCaptor =
                ArgumentCaptor.forClass(CellularDataServiceSwitch.class);
        verify(mPersistAtomsStorage, times(3))
                .addCellularServiceStateAndCellularDataServiceSwitch(
                        serviceStateCaptor.capture(), serviceSwitchCaptor.capture());
        CellularServiceState state = serviceStateCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_INTERNATIONAL, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(2);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_INTERNATIONAL, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_INTERNATIONAL, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(400L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        CellularDataServiceSwitch serviceSwitch = serviceSwitchCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, serviceSwitch.ratFrom);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, serviceSwitch.ratTo);
        assertEquals(0, serviceSwitch.simSlotIndex);
        assertFalse(serviceSwitch.isMultiSim);
        assertEquals(CARRIER1_ID, serviceSwitch.carrierId);
        assertEquals(1, serviceSwitch.switchCount);
        assertNull(serviceSwitchCaptor.getAllValues().get(1));
        assertNull(serviceSwitchCaptor.getAllValues().get(2)); // produced by conclude()
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_roamingWithOverride() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice roaming
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getDataNetworkType();
        NetworkRegistrationInfo roamingNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        roamingNri.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(roamingNri).when(mServiceState)
                .getNetworkRegistrationInfo(
                        anyInt(), eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(400L);

        // There should be 2 service states and 1 data service switch (LTE to UMTS)
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> serviceStateCaptor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        ArgumentCaptor<CellularDataServiceSwitch> serviceSwitchCaptor =
                ArgumentCaptor.forClass(CellularDataServiceSwitch.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(
                        serviceStateCaptor.capture(), serviceSwitchCaptor.capture());
        CellularServiceState state = serviceStateCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.dataRat);
        // Atom should show roaming, despite type being unknown
        assertEquals(ServiceState.ROAMING_TYPE_UNKNOWN, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_UNKNOWN, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(400L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        CellularDataServiceSwitch serviceSwitch = serviceSwitchCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, serviceSwitch.ratFrom);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, serviceSwitch.ratTo);
        assertEquals(0, serviceSwitch.simSlotIndex);
        assertFalse(serviceSwitch.isMultiSim);
        assertEquals(CARRIER1_ID, serviceSwitch.carrierId);
        assertEquals(1, serviceSwitch.switchCount);
        assertNull(serviceSwitchCaptor.getAllValues().get(1)); // produced by conclude()
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_dualSim() throws Exception {
        // Using default service state for LTE
        // Only difference between the 2 slots is slot index
        mockDualSim(CARRIER1_ID);
        TestableServiceStateStats mSecondServiceStateStats =
                new TestableServiceStateStats(mSecondPhone);

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        mSecondServiceStateStats.onServiceStateChanged(mServiceState);
        mSecondServiceStateStats.incTimeMillis(100L);
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getDataNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);
        mSecondServiceStateStats.onServiceStateChanged(mServiceState);
        mSecondServiceStateStats.incTimeMillis(200L);

        // There should be 4 service states and 2 data service switches
        mServiceStateStats.conclude();
        mSecondServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> serviceStateCaptor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        ArgumentCaptor<CellularDataServiceSwitch> serviceSwitchCaptor =
                ArgumentCaptor.forClass(CellularDataServiceSwitch.class);
        verify(mPersistAtomsStorage, times(4))
                .addCellularServiceStateAndCellularDataServiceSwitch(
                        serviceStateCaptor.capture(), serviceSwitchCaptor.capture());
        CellularServiceState state = serviceStateCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertTrue(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(1, state.simSlotIndex);
        assertTrue(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(2);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertTrue(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = serviceStateCaptor.getAllValues().get(3);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(1, state.simSlotIndex);
        assertTrue(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        CellularDataServiceSwitch serviceSwitch = serviceSwitchCaptor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, serviceSwitch.ratFrom);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, serviceSwitch.ratTo);
        assertEquals(0, serviceSwitch.simSlotIndex);
        assertTrue(serviceSwitch.isMultiSim);
        assertEquals(CARRIER1_ID, serviceSwitch.carrierId);
        assertEquals(1, serviceSwitch.switchCount);
        serviceSwitch = serviceSwitchCaptor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, serviceSwitch.ratFrom);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, serviceSwitch.ratTo);
        assertEquals(1, serviceSwitch.simSlotIndex);
        assertTrue(serviceSwitch.isMultiSim);
        assertEquals(CARRIER1_ID, serviceSwitch.carrierId);
        assertEquals(1, serviceSwitch.switchCount);
        assertNull(serviceSwitchCaptor.getAllValues().get(2)); // produced by conclude()
        assertNull(serviceSwitchCaptor.getAllValues().get(3)); // produced by conclude()
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onServiceStateChanged_airplaneMode() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(5000L);
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_IN_SERVICE).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(CARRIER1_ID).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);

        // There should be 2 service state updates (1 distinct service state) and no switches
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(200L, state.totalTimeMillis);
        assertEquals(false, state.isEmergencyOnly);
        assertEquals(true, state.isInternetPdnUp);
        assertEquals(true, state.isDataEnabled);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void getVoiceRat_bearer() throws Exception {
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_LTE);
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mImsStats).getImsVoiceRadioTech();
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS));
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS));
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_UNKNOWN));
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        mockWwanCsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mImsStats).getImsVoiceRadioTech();
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_IMS));
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_CS));
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mServiceStateStats.getVoiceRat(
                mPhone, mServiceState, VOICE_CALL_SESSION__BEARER_AT_END__CALL_BEARER_UNKNOWN));
    }

    @Test
    @SmallTest
    public void onFoldStateChanged_modemOff() throws Exception {
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_POWER_OFF).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);

        mServiceStateStats.onFoldStateChanged(CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_CLOSED);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void onFoldStateChanged_LTEMode() throws Exception {
        // Using default service state for LTE with fold state unknown
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.onFoldStateChanged(CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_CLOSED);
        mServiceStateStats.incTimeMillis(1000L);
        // Same fold state as before should not generate a new atom
        mServiceStateStats.onFoldStateChanged(CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_CLOSED);
        mServiceStateStats.incTimeMillis(1000L);

        // There should be 2 service state updates
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(2))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_UNKNOWN, state.foldState);
        state = captor.getAllValues().get(1);
        assertEquals(CELLULAR_SERVICE_STATE__FOLD_STATE__STATE_CLOSED, state.foldState);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void isNetworkRoaming_nullServiceState() throws Exception {
        boolean result = ServiceStateStats.isNetworkRoaming(null);

        assertEquals(false, result);
    }

    @Test
    @SmallTest
    public void isNetworkRoaming_notRoaming() throws Exception {
        NetworkRegistrationInfo nri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .build();
        nri.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        doReturn(nri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        boolean result = ServiceStateStats.isNetworkRoaming(mServiceState);
        boolean resultCs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_CS);
        boolean resultPs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_PS);

        assertEquals(false, result);
        assertEquals(false, resultCs);
        assertEquals(false, resultPs);
    }

    @Test
    @SmallTest
    public void isNetworkRoaming_csRoaming() throws Exception {
        NetworkRegistrationInfo roamingNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        roamingNri.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(roamingNri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        boolean result = ServiceStateStats.isNetworkRoaming(mServiceState);
        boolean resultCs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_CS);
        boolean resultPs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_PS);

        assertEquals(true, result);
        assertEquals(true, resultCs);
        assertEquals(false, resultPs);
    }

    @Test
    @SmallTest
    public void isNetworkRoaming_psRoaming() throws Exception {
        NetworkRegistrationInfo roamingNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        roamingNri.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(roamingNri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        boolean result = ServiceStateStats.isNetworkRoaming(mServiceState);
        boolean resultCs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_CS);
        boolean resultPs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_PS);

        assertEquals(true, result);
        assertEquals(false, resultCs);
        assertEquals(true, resultPs);
    }

    @Test
    @SmallTest
    public void isNetworkRoaming_bothRoaming() throws Exception {
        NetworkRegistrationInfo roamingNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_UMTS)
                // This sets mNetworkRegistrationState
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                .build();
        roamingNri.setRoamingType(ServiceState.ROAMING_TYPE_NOT_ROAMING);
        doReturn(roamingNri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_CS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        doReturn(roamingNri).when(mServiceState).getNetworkRegistrationInfo(
                NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        boolean result = ServiceStateStats.isNetworkRoaming(mServiceState);
        boolean resultCs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_CS);
        boolean resultPs = ServiceStateStats.isNetworkRoaming(
                mServiceState, NetworkRegistrationInfo.DOMAIN_PS);

        assertEquals(true, result);
        assertEquals(true, resultCs);
        assertEquals(true, resultPs);
    }

    @Test
    @SmallTest
    public void onVoiceServiceStateOverrideChanged_voiceCallingCapabilityChange() {
        // Using default service state for LTE
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice Calling registered
        mServiceStateStats.onVoiceServiceStateOverrideChanged(true);
        mServiceStateStats.incTimeMillis(100L);
        // Voice Calling unregistered
        mServiceStateStats.onVoiceServiceStateOverrideChanged(false);
        mServiceStateStats.incTimeMillis(100L);
        // Voice Calling unregistered again. Same state should not generate a new atom
        mServiceStateStats.onVoiceServiceStateOverrideChanged(false);
        mServiceStateStats.incTimeMillis(100L);

        // There should be 3 service state updates
        mServiceStateStats.conclude();
        ArgumentCaptor<CellularServiceState> captor =
                ArgumentCaptor.forClass(CellularServiceState.class);
        verify(mPersistAtomsStorage, times(3))
                .addCellularServiceStateAndCellularDataServiceSwitch(captor.capture(), eq(null));
        CellularServiceState state = captor.getAllValues().get(0);
        assertEquals(false, state.overrideVoiceService);
        state = captor.getAllValues().get(1);
        assertEquals(true, state.overrideVoiceService);
        state = captor.getAllValues().get(2);
        assertEquals(false, state.overrideVoiceService);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    private void mockWwanPsRat(@NetworkType int rat) {
        mockWwanRat(
                NetworkRegistrationInfo.DOMAIN_PS,
                rat,
                rat == TelephonyManager.NETWORK_TYPE_UNKNOWN
                        ? NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING
                        : NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
    }

    private void mockWwanCsRat(@NetworkType int rat) {
        mockWwanRat(
                NetworkRegistrationInfo.DOMAIN_CS,
                rat,
                rat == TelephonyManager.NETWORK_TYPE_UNKNOWN
                        ? NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING
                        : NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
    }

    private void mockWwanRat(
            @NetworkRegistrationInfo.Domain int domain,
            @NetworkType int rat,
            @NetworkRegistrationInfo.RegistrationState int regState) {
        doReturn(
                        new NetworkRegistrationInfo.Builder()
                                .setAccessNetworkTechnology(rat)
                                .setRegistrationState(regState)
                                .build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    private void mockLimitedService(@NetworkType int rat) {
        doReturn(rat).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(
                        new NetworkRegistrationInfo.Builder()
                                .setAccessNetworkTechnology(rat)
                                .setRegistrationState(
                                        NetworkRegistrationInfo.REGISTRATION_STATE_DENIED)
                                .setEmergencyOnly(true)
                                .build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_CS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    private void mockDualSim(int carrierId) {
        doReturn(1).when(mSecondPhone).getPhoneId();
        doReturn(1).when(mUiccController).getSlotIdFromPhoneId(1);
        doReturn(carrierId).when(mSecondPhone).getCarrierId();
        doReturn(mDataSettingsManager).when(mSecondPhone).getDataSettingsManager();

        doReturn(true).when(mPhysicalSlot1).isActive();
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot1).getCardState();
        doReturn(false).when(mPhysicalSlot1).isEuicc();
        doReturn(new UiccSlot[] {mPhysicalSlot0, mPhysicalSlot1})
                .when(mUiccController)
                .getUiccSlots();
        doReturn(mPhysicalSlot1).when(mUiccController).getUiccSlot(1);
        doReturn(mPhysicalSlot1).when(mUiccController).getUiccSlotForPhone(1);
    }
}
