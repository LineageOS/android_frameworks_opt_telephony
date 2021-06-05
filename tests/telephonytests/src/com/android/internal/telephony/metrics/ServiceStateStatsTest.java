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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation.NetworkType;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
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
import org.mockito.Mock;

public class ServiceStateStatsTest extends TelephonyTest {
    private static final long START_TIME_MILLIS = 2000L;
    private static final int CARRIER1_ID = 1;
    private static final int CARRIER2_ID = 1187;

    @Mock private UiccSlot mPhysicalSlot0;
    @Mock private UiccSlot mPhysicalSlot1;
    @Mock private Phone mSecondPhone;

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

        mServiceStateStats = new TestableServiceStateStats(mPhone);
    }

    @After
    public void tearDown() throws Exception {
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_outOfService() throws Exception {
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_OUT_OF_SERVICE).when(mServiceState).getDataRegState();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
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
        doReturn(true).when(mImsPhone).isWifiCallingEnabled();
        mServiceStateStats.onServiceStateChanged(mServiceState);

        mServiceStateStats.incTimeMillis(100L);
        mServiceStateStats.conclude();

        // There should be no new switches, service states, or added durations
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void conclude_noSimCardEmergencyOnly() throws Exception {
        // Using default service state for LTE
        doReturn(CardState.CARDSTATE_ABSENT).when(mPhysicalSlot0).getCardState();
        doReturn(ServiceState.STATE_EMERGENCY_ONLY).when(mServiceState).getVoiceRegState();
        doReturn(ServiceState.STATE_EMERGENCY_ONLY).when(mServiceState).getDataRegState();
        doReturn(-1).when(mPhone).getCarrierId();
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
        assertEquals(-1, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
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
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_sameRats() throws Exception {
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_differentDataRats() throws Exception {
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
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
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(CARRIER1_ID, state.carrierId);
        assertEquals(100L, state.totalTimeMillis);
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
    public void update_differentVoiceRats() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice RAT changes to IWLAN and data RAT stays in LTE according to WWAN PS RAT
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        doReturn(true).when(mImsPhone).isWifiCallingEnabled();
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_iwlanButNotWifiCalling() throws Exception {
        // Using default service state for LTE as WWAN PS RAT
        doReturn(TelephonyManager.NETWORK_TYPE_IWLAN).when(mServiceState).getDataNetworkType();
        doReturn(false).when(mImsPhone).isWifiCallingEnabled();

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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_endc() throws Exception {
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_simSwapSameRat() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // SIM removed, emergency call only
        doReturn(CardState.CARDSTATE_ABSENT).when(mPhysicalSlot0).getCardState();
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UNKNOWN).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UNKNOWN);
        doReturn(-1).when(mPhone).getCarrierId();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(5000L);
        // New SIM inserted
        doReturn(CardState.CARDSTATE_PRESENT).when(mPhysicalSlot0).getCardState();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_LTE).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_LTE);
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
        state = captor.getAllValues().get(1);
        assertEquals(TelephonyManager.NETWORK_TYPE_UMTS, state.voiceRat);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, state.dataRat);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.voiceRoamingType);
        assertEquals(ServiceState.ROAMING_TYPE_NOT_ROAMING, state.dataRoamingType);
        assertFalse(state.isEndc);
        assertEquals(0, state.simSlotIndex);
        assertFalse(state.isMultiSim);
        assertEquals(-1, state.carrierId);
        assertEquals(5000L, state.totalTimeMillis);
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    @SmallTest
    public void update_roaming() throws Exception {
        // Using default service state for LTE

        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(100L);
        // Voice roaming
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getVoiceNetworkType();
        doReturn(TelephonyManager.NETWORK_TYPE_UMTS).when(mServiceState).getDataNetworkType();
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UMTS);
        doReturn(ServiceState.ROAMING_TYPE_INTERNATIONAL).when(mServiceState).getVoiceRoamingType();
        mServiceStateStats.onServiceStateChanged(mServiceState);
        mServiceStateStats.incTimeMillis(200L);
        // Voice and data roaming
        doReturn(ServiceState.ROAMING_TYPE_INTERNATIONAL).when(mServiceState).getDataRoamingType();
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
    public void update_dualSim() throws Exception {
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
        mockWwanPsRat(TelephonyManager.NETWORK_TYPE_UMTS);
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
    public void update_airplaneMode() throws Exception {
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
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    private void mockWwanPsRat(@NetworkType int rat) {
        doReturn(new NetworkRegistrationInfo.Builder().setAccessNetworkTechnology(rat).build())
                .when(mServiceState)
                .getNetworkRegistrationInfo(
                        NetworkRegistrationInfo.DOMAIN_PS,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    private void mockDualSim(int carrierId) {
        doReturn(1).when(mSecondPhone).getPhoneId();
        doReturn(1).when(mUiccController).getSlotIdFromPhoneId(1);
        doReturn(carrierId).when(mSecondPhone).getCarrierId();

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
