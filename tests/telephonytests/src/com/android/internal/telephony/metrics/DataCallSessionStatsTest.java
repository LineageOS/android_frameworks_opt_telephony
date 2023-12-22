/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.NetworkCapabilities;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.DataFailCause;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.DataCallSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DataCallSessionStatsTest extends TelephonyTest {

    private DataCallResponse mDefaultImsResponse = buildDataCallResponse("ims", 0);
    private static class TestableDataCallSessionStats extends DataCallSessionStats {
        private long mTimeMillis = 0L;

        TestableDataCallSessionStats(Phone phone) {
            super(phone);
        }

        @Override
        protected long getTimeMillis() {
            return mTimeMillis;
        }

        private void setTimeMillis(long timeMillis) {
            mTimeMillis = timeMillis;
        }
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private TestableDataCallSessionStats mDataCallSessionStats;
    private NetworkCapabilities mCellularNetworkCapabilities = new NetworkCapabilities.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build();

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        when(mServiceState.getDataRegistrationState()).thenReturn(ServiceState.STATE_IN_SERVICE);
        mDataCallSessionStats = new TestableDataCallSessionStats(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        mDataCallSessionStats = null;
        super.tearDown();
    }

    private DataCallResponse buildDataCallResponse(String apn, long retryDurationMillis) {
        return new DataCallResponse.Builder()
                .setId(apn.hashCode())
                .setRetryDurationMillis(retryDurationMillis)
                .build();
    }

    @Test
    @SmallTest
    public void testSetupDataCallOnCellularIms_success() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_LTE,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        mDataCallSessionStats.setTimeMillis(60000L);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(1, stats.durationMinutes);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.ratAtEnd);
        assertTrue(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testSetupDataCallOnIwlan_success() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        mDataCallSessionStats.setTimeMillis(120000L);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(2, stats.durationMinutes);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.ratAtEnd);
        assertFalse(stats.isIwlanCrossSim);
        assertTrue(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testSetupDataCallOnCrossSimCalling_success() {
        doReturn(mCellularNetworkCapabilities)
                .when(mDefaultNetworkMonitor).getNetworkCapabilities();
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        mDataCallSessionStats.setTimeMillis(60000L);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(1, stats.durationMinutes);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.ratAtEnd);
        assertTrue(stats.isIwlanCrossSim);
        assertTrue(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testSetupDataCallOnCellularIms_failure() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_LTE,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NETWORK_FAILURE);

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(0, stats.durationMinutes);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.ratAtEnd);
        assertFalse(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testHandoverFromCellularToIwlan_success() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_LTE,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        mDataCallSessionStats.onDrsOrRatChanged(TelephonyManager.NETWORK_TYPE_IWLAN);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.ratAtEnd);
        assertEquals(1, stats.ratSwitchCount);
        assertTrue(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testHandoverFromCellularToCrossSimCalling_success() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_LTE,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        doReturn(mCellularNetworkCapabilities)
                .when(mDefaultNetworkMonitor).getNetworkCapabilities();
        mDataCallSessionStats.onDrsOrRatChanged(TelephonyManager.NETWORK_TYPE_IWLAN);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.ratAtEnd);
        assertEquals(1, stats.ratSwitchCount);
        assertTrue(stats.isIwlanCrossSim);
        assertTrue(stats.ongoing);
    }

    @Test
    @SmallTest
    public void testHandoverFromCellularToIwlan_failure() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_LTE,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);

        mDataCallSessionStats.onHandoverFailure(DataFailCause.IWLAN_DNS_RESOLUTION_TIMEOUT,
                TelephonyManager.NETWORK_TYPE_LTE, TelephonyManager.NETWORK_TYPE_IWLAN);
        mDataCallSessionStats.conclude();

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, stats.ratAtEnd);
        assertTrue(stats.ongoing);
        assertEquals(DataFailCause.IWLAN_DNS_RESOLUTION_TIMEOUT,
                stats.handoverFailureCauses[0]);

        int cellularToIwlanFailureDirection = TelephonyManager.NETWORK_TYPE_LTE
                | (TelephonyManager.NETWORK_TYPE_IWLAN << 16);
        assertEquals(cellularToIwlanFailureDirection, stats.handoverFailureRat[0]);
    }

    @Test
    @SmallTest
    public void testSetupDataCallOnIwlan_success_thenOOS() {
        mDataCallSessionStats.onSetupDataCall(ApnSetting.TYPE_IMS);
        mDataCallSessionStats.onSetupDataCallResponse(
                mDefaultImsResponse,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                ApnSetting.TYPE_IMS,
                ApnSetting.PROTOCOL_IP,
                DataFailCause.NONE);
        when(mServiceState.getDataRegistrationState())
                .thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        mDataCallSessionStats.onDataCallDisconnected(DataFailCause.IWLAN_IKE_DPD_TIMEOUT);

        ArgumentCaptor<DataCallSession> callCaptor =
                ArgumentCaptor.forClass(DataCallSession.class);
        verify(mPersistAtomsStorage, times(1)).addDataCallSession(
                callCaptor.capture());
        DataCallSession stats = callCaptor.getValue();

        assertEquals(ApnSetting.TYPE_IMS, stats.apnTypeBitmask);
        assertEquals(TelephonyManager.NETWORK_TYPE_IWLAN, stats.ratAtEnd);
        assertTrue(stats.oosAtEnd);
        assertFalse(stats.ongoing);
    }
}
