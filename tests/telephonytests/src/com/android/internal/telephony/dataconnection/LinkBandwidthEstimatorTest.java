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

import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_DEFAULT_NETWORK_CHANGED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_MODEM_ACTIVITY_RETURNED;
import static com.android.internal.telephony.dataconnection.LinkBandwidthEstimator.MSG_SCREEN_STATE_CHANGED;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.telephony.ModemActivityInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyFacade;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    private NetworkCapabilities mNetworkCapabilities;
    private long mElapsedTimeMs = 0;
    private long mTxBytes = 0;
    private long mRxBytes = 0;
    @Mock
    TelephonyFacade mTelephonyFacade;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .build();

        when(mTelephonyFacade.getElapsedSinceBootMillis()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        when(mTelephonyFacade.getMobileTxBytes()).thenReturn(0L);
        mLBE = new LinkBandwidthEstimator(mPhone, mTelephonyFacade);
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

    @After
    public void tearDown() throws Exception {
        mLBE = null;
        super.tearDown();
    }

    @Test
    public void testScreenOnTxTrafficHighOneModemPoll() throws Exception {
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();

        addElapsedTime(4_100);
        moveTimeForward(4_100);
        mLBE.obtainMessage(MSG_SCREEN_STATE_CHANGED, true).sendToTarget();
        addTxBytes(500_000L);
        addRxBytes(10_000L);
        addElapsedTime(2_100);
        moveTimeForward(2_100);
        processAllMessages();

        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());
        assertEquals(0, mLBE.getTxLinkBandwidthKbps());
        assertEquals(0, mLBE.getRxLinkBandwidthKbps());
    }

    @Test
    public void testScreenOnTxRxTrafficHighTwoModemPoll() throws Exception {
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();

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
        assertEquals(3_906, mLBE.getTxLinkBandwidthKbps());
        assertEquals(5_208, mLBE.getRxLinkBandwidthKbps());
    }

    @Test
    public void testScreenOnRxTrafficHighTwoModemPollRxTimeHigh() throws Exception {
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();

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
        assertEquals(0, mLBE.getTxLinkBandwidthKbps());
        assertEquals(15_625, mLBE.getRxLinkBandwidthKbps());
    }

    @Test
    public void testScreenOnTxRxTrafficLow() throws Exception {
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();
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
        mLBE.obtainMessage(MSG_DEFAULT_NETWORK_CHANGED, mNetworkCapabilities).sendToTarget();
        processAllMessages();
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
        verify(mTelephonyManager, times(1)).requestModemActivityInfo(any(), any());
    }
}
