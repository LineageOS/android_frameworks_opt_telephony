/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;

import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.BW_STATS_COUNT_THRESHOLD;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.LINK_RX;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.LINK_TX;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_ACTIVE_PHONE_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_DEFAULT_NETWORK_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_MODEM_ACTIVITY_RETURNED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_NR_FREQUENCY_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_SCREEN_STATE_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_SIGNAL_STRENGTH_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.UNKNOWN_TAC;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellIdentityLte;
import android.telephony.ModemActivityInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Pair;

import com.android.internal.telephony.TelephonyFacade;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class LinkBandwidthEstimatorTest extends TelephonyTest {
    private LinkBandwidthEstimator mLBE;
    private static final int [] TX_TIME_1_MS = new int[]{0, 0, 0, 0, 0};
    private static final int [] TX_TIME_2_MS = new int[]{100, 0, 0, 0, 100};
    private static final int RX_TIME_1_MS = 100;
    private static final int RX_TIME_2_MS = 200;
    private static final ModemActivityInfo MAI_INIT =
            new ModemActivityInfo(0, 0, 0, TX_TIME_1_MS, RX_TIME_1_MS);
    private static final ModemActivityInfo MAI_TX_RX_TIME_HIGH =
            new ModemActivityInfo(100L, 0, 0, TX_TIME_2_MS, RX_TIME_2_MS);
    private static final ModemActivityInfo MAI_RX_TIME_HIGH =
            new ModemActivityInfo(100L, 0, 0, TX_TIME_1_MS, RX_TIME_2_MS);
    private static final int EVENT_BANDWIDTH_ESTIMATOR_UPDATE = 1;
    private NetworkCapabilities mNetworkCapabilities;
    private CellIdentityLte mCellIdentity;
    private long mElapsedTimeMs = 0;
    private long mTxBytes = 0;
    private long mRxBytes = 0;
    @Mock
    TelephonyFacade mTelephonyFacade;
    @Mock
    private Handler mTestHandler;
    private NetworkRegistrationInfo mNri;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();

        mCellIdentity = new CellIdentityLte(310, 260, 1234, 123456, 366);
        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        when(mServiceState.getOperatorNumeric()).thenReturn("310260");
        when(mTelephonyFacade.getElapsedSinceBootMillis()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        when(mPhone.getSubId()).thenReturn(1);
        when(mSignalStrength.getDbm()).thenReturn(-100);
        when(mSignalStrength.getLevel()).thenReturn(1);
        mLBE = new LinkBandwidthEstimator(mPhone, mTelephonyFacade);
        mLBE.registerForBandwidthChanged(mTestHandler, EVENT_BANDWIDTH_ESTIMATOR_UPDATE, null);
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, false).sendToTarget();
        mLBE.obtainMessage(MSG_ACTIVE_PHONE_CHANGED, 1).sendToTarget();
        processAllMessages();
    }

    private void addElapsedTime(long timeMs) {
        mElapsedTimeMs += timeMs;
        when(mTelephonyFacade.getElapsedSinceBootMillis()).thenReturn(mElapsedTimeMs);
    }

    private void addTxBytes(long txBytes) {
        mTxBytes += txBytes;
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(mTxBytes);
    }

    private void addRxBytes(long rxBytes) {
        mRxBytes += rxBytes;
        when(mTelephonyFacade.getMobileRxBytes()).thenReturn(mRxBytes);
    }

    private void subtractRxBytes(long rxBytes) {
        mRxBytes -= rxBytes;
        when(mTelephonyFacade.getMobileRxBytes()).thenReturn(mRxBytes);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testScreenOnTxTrafficHighOneModemPoll() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(500_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();

        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnTxTrafficHighNotActivePhoneNoModemPoll() throws Exception {
        mLBE.obtainMessage(MSG_ACTIVE_PHONE_CHANGED, 0).sendToTarget();
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        processAllMessages();

        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(500_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();

        verify(mTelephonyManager, times(0)).requestModemActivityInfo(any(), any());
    }

    private void verifyUpdateBandwidth(int txKbps, int rxKbps) {
        ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mTestHandler, atLeast(1))
                .sendMessageAtTime(messageArgumentCaptor.capture(), anyLong());
        assertEquals(EVENT_BANDWIDTH_ESTIMATOR_UPDATE, messageArgumentCaptor.getValue().what);
        assertEquals(new Pair<Integer, Integer>(txKbps, rxKbps),
                ((AsyncResult) messageArgumentCaptor.getValue().obj).result);
    }

    @Test
    public void testScreenOnTxRxTrafficHighTwoModemPoll() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(20_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_INIT).sendToTarget();
        processAllMessages();

        addTxBytes(100_000L);
        addRxBytes(200_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_TX_RX_TIME_HIGH).sendToTarget();
        processAllMessages();

        verify(mTelephonyManager, times(2)).requestModemActivityInfo(any(), any());
        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testScreenOnRxTrafficHighTwoModemPollRxTimeHigh() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(20_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_INIT).sendToTarget();
        processAllMessages();

        addTxBytes(100_000L);
        addRxBytes(200_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, MAI_RX_TIME_HIGH).sendToTarget();
        processAllMessages();

        verify(mTelephonyManager, times(2)).requestModemActivityInfo(any(), any());
        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testScreenOnTxRxTrafficLow() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();
        verify(mTelephonyManager, never()).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnTrafficLowSampleHighAcc() throws Exception {
        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        for (int i = 0; i < 30; i++) {
            addTxBytes(10_000L);
            addRxBytes(19_000L);
            addElapsedTime(1_100);
            moveTimeForward(1_100);
            processAllMessages();
        }
        verify(mTelephonyManager, times(2)).requestModemActivityInfo(any(), any());
    }

    @Test
    public void testScreenOnDefaultNetworkToggleNoExtraTrafficPoll() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addElapsedTime(500);
        moveTimeForward(500);
        processAllMessages();
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, null).sendToTarget();
        addElapsedTime(500);
        moveTimeForward(500);
        processAllMessages();
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        for (int i = 0; i < 3; i++) {
            addElapsedTime(1_100);
            moveTimeForward(1_100);
            processAllMessages();
        }

        verify(mTelephonyFacade, times(4)).getMobileTxBytes();
    }

    @Test
    public void testRatChangeTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(19_000L);
        addElapsedTime(2000);
        moveTimeForward(2000);
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(19_000L);
        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_NR)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verify(mTelephonyManager, times(0)).requestModemActivityInfo(any(), any());
        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testSignalLevelChangeTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verifyUpdateBandwidth(-1, 19_597);

        addTxBytes(20_000L);
        addRxBytes(50_000L);
        when(mSignalStrength.getDbm()).thenReturn(-110);
        mLBE.obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, mSignalStrength).sendToTarget();
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testAvgBwForAllPossibleRat() throws Exception {
        Pair<Integer, Integer> values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_GPRS);
        assertEquals(24, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_EDGE);
        assertEquals(18, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_UMTS);
        assertEquals(115, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_CDMA);
        assertEquals(14, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_1xRTT);
        assertEquals(30, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_EVDO_0);
        assertEquals(48, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_EVDO_A);
        assertEquals(550, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_HSDPA);
        assertEquals(620, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_HSUPA);
        assertEquals(1800, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_HSPA);
        assertEquals(1800, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_EVDO_B);
        assertEquals(550, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_EHRPD);
        assertEquals(750, (int) values.first);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_HSPAP);
        assertEquals(3400, (int) values.second);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_TD_SCDMA);
        assertEquals(115, (int) values.first);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_LTE);
        assertEquals(15000, (int) values.second);
        when(mServiceState.getNrState()).thenReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED);
        when(mServiceState.getNrFrequencyRange()).thenReturn(ServiceState.FREQUENCY_RANGE_MMWAVE);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_LTE);
        assertEquals(145000, (int) values.first);
        when(mServiceState.getNrFrequencyRange()).thenReturn(ServiceState.FREQUENCY_RANGE_UNKNOWN);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_LTE);
        assertEquals(47000, (int) values.first);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_NR);
        assertEquals(145_000, (int) values.first);
        when(mServiceState.getNrFrequencyRange()).thenReturn(ServiceState.FREQUENCY_RANGE_MMWAVE);
        values = mLBE.getStaticAvgBw(TelephonyManager.NETWORK_TYPE_NR);
        assertEquals("NR_MMWAVE", mLBE.getDataRatName(TelephonyManager.NETWORK_TYPE_NR));
        assertEquals(145_000, (int) values.first);
    }

    @Test
    public void testSwitchToNrMmwaveTriggerBandwidthUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(10_000L);
        addRxBytes(19_000L);
        addElapsedTime(2000);
        moveTimeForward(2000);
        processAllMessages();

        addTxBytes(10_000L);
        addRxBytes(19_000L);
        when(mServiceState.getNrState()).thenReturn(NetworkRegistrationInfo.NR_STATE_CONNECTED);
        when(mServiceState.getNrFrequencyRange()).thenReturn(ServiceState.FREQUENCY_RANGE_MMWAVE);
        when(mSignalStrength.getLevel()).thenReturn(2);
        mLBE.obtainMessage(MSG_NR_FREQUENCY_CHANGED).sendToTarget();
        addElapsedTime(6000);
        moveTimeForward(6000);
        processAllMessages();

        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testEnoughModemPollTriggerBwUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mTelephonyManager, times(BW_STATS_COUNT_THRESHOLD + 2))
                .requestModemActivityInfo(any(), any());
        verifyUpdateBandwidth(-1, 19_597);
    }

    @Test
    public void testAbnormalTrafficCountTriggerLessBwUpdate() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 2; i++) {
            if (i == 1) {
                addTxBytes(10_000L);
                subtractRxBytes(500_000L);
            } else {
                addTxBytes(10_000L);
                addRxBytes(500_000L);
            }
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verify(mTelephonyManager, times(BW_STATS_COUNT_THRESHOLD))
                .requestModemActivityInfo(any(), any());
        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testUseCurrentTacStatsWithEnoughData() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        mCellIdentity = new CellIdentityLte(310, 260, 1235, 123457, 367);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        for (int i = BW_STATS_COUNT_THRESHOLD; i < 3 * BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verifyUpdateBandwidth(-1, 19_597);
    }

    @Test
    public void testUseAllTacStatsIfNoEnoughDataWithCurrentTac() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();
        mLBE.obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, mSignalStrength).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD; i++) {
            addTxBytes(10_000L);
            addRxBytes(900_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        mCellIdentity = new CellIdentityLte(310, 260, 1234, 123456, 367);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        for (int i = BW_STATS_COUNT_THRESHOLD; i < BW_STATS_COUNT_THRESHOLD * 3 / 2; i++) {
            addTxBytes(10_000L);
            addRxBytes(1_000_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        LinkBandwidthEstimator.NetworkBandwidth network = mLBE.lookupNetwork("310260", 366, "LTE");
        assertEquals(BW_STATS_COUNT_THRESHOLD - 1, network.getCount(LINK_RX, 1));
        assertEquals(900_000L * 8 * 1000 / 200 / 1024 * (BW_STATS_COUNT_THRESHOLD - 1),
                network.getValue(LINK_RX, 1));
        network = mLBE.lookupNetwork("310260", 367, "LTE");
        assertEquals(1, network.getCount(LINK_RX, 1));
        assertEquals(1_000_000L * 8 * 1000 / 200 / 1024,
                network.getValue(LINK_RX, 1));
        network = mLBE.lookupNetwork("310260", UNKNOWN_TAC, "LTE");
        assertEquals(BW_STATS_COUNT_THRESHOLD * 3 / 2 - 2, network.getCount(LINK_RX, 1));
        assertEquals(179_686, network.getValue(LINK_RX, 1));
        verifyUpdateBandwidth(-1, 37_350);
    }

    @Test
    public void testSwitchCarrierFallbackToColdStartValue() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 5; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS)).sendToTarget();
            processAllMessages();
        }

        verifyUpdateBandwidth(-1, 19_597);

        mCellIdentity = new CellIdentityLte(320, 265, 1234, 123456, 366);
        when(mPhone.getCurrentCellIdentity()).thenReturn(mCellIdentity);
        when(mServiceState.getOperatorNumeric()).thenReturn("320265");

        addTxBytes(10_000L);
        addRxBytes(10_000L);
        addElapsedTime(5_100);
        moveTimeForward(5_100);
        processAllMessages();

        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testIgnoreLowTxRxTime() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();

        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 5; i++) {
            addTxBytes(10_000L);
            addRxBytes(500_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * 80)).sendToTarget();
            processAllMessages();
        }

        verifyUpdateBandwidth(-1, -1);
    }

    @Test
    public void testEdgeThenLteShouldIgnoreTransitionStats() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();
        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_EDGE)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        mLBE.obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, mSignalStrength).sendToTarget();
        processAllMessages();
        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD * 2; i++) {
            addTxBytes(12_000L);
            addRxBytes(24_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS * 5)).sendToTarget();
            processAllMessages();
        }

        LinkBandwidthEstimator.NetworkBandwidth network = mLBE.lookupNetwork("310260", 366, "EDGE");

        assertEquals(0, network.getCount(LINK_TX, 1));
        assertEquals(BW_STATS_COUNT_THRESHOLD * 2 - 1, network.getCount(LINK_RX, 1));
        assertEquals(24_000L * 8 / 1024 * (BW_STATS_COUNT_THRESHOLD * 2 - 1),
                network.getValue(LINK_RX, 1));

        mNri = new NetworkRegistrationInfo.Builder()
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .build();
        when(mServiceState.getNetworkRegistrationInfo(anyInt(), anyInt())).thenReturn(mNri);
        for (int i = BW_STATS_COUNT_THRESHOLD * 2; i < BW_STATS_COUNT_THRESHOLD * 4; i++) {
            addTxBytes(1_200_000L);
            addRxBytes(2_400_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS * 10)).sendToTarget();
            processAllMessages();
        }
        network = mLBE.lookupNetwork("310260", 366, "LTE");

        assertEquals(BW_STATS_COUNT_THRESHOLD * 2 - 2, network.getCount(LINK_RX, 1));
        assertEquals(0, network.getCount(LINK_TX, 1));
    }


    @Test
    public void testVeryHighRxLinkBandwidthEstimationIgnored() throws Exception {
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        processAllMessages();
        mLBE.obtainMessage(MSG_SIGNAL_STRENGTH_CHANGED, mSignalStrength).sendToTarget();
        processAllMessages();
        for (int i = 0; i < BW_STATS_COUNT_THRESHOLD + 5; i++) {
            addTxBytes(8_000_000_000L);
            addRxBytes(16_000_000_000L);
            addElapsedTime(5_100);
            moveTimeForward(5_100);
            processAllMessages();
            mLBE.obtainMessage(MSG_MODEM_ACTIVITY_RETURNED, new ModemActivityInfo(
                    i * 5_100L, 0, 0, TX_TIME_2_MS, i * RX_TIME_2_MS * 5)).sendToTarget();
            processAllMessages();
        }

        // This will result in link bandwidth estimation value 128Gbps which is too high for LTE.
        // So it will be ignored by the estimator.
        LinkBandwidthEstimator.NetworkBandwidth network = mLBE.lookupNetwork("310260", 366, "LTE");

        assertEquals(0, network.getCount(LINK_RX, 1));
        assertEquals(0, network.getValue(LINK_TX, 1));
        assertEquals(0, network.getValue(LINK_RX, 1));
    }
}
