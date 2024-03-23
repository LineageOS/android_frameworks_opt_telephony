/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.PreciseDataConnectionState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyStatsLog;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/** Test DataNetworkValidationStats */
public class DataNetworkValidationStatsTest extends TelephonyTest {

    /** Initial time at starting tests */
    private static final Long STARTED_TIME_IN_MILLIS = 5000000L;

    private TestableDataNetworkValidationStats mDataNetworkValidationStats;

    /** Test Class for override elapsed time */
    private static class TestableDataNetworkValidationStats extends DataNetworkValidationStats {
        private long mSystemClockInMillis;
        TestableDataNetworkValidationStats(Phone phone) {
            super(phone);
            mSystemClockInMillis = STARTED_TIME_IN_MILLIS;
        }

        @Override
        protected long getTimeMillis() {
            return mSystemClockInMillis;
        }

        public void elapseTimeInMillis(long elapse) {
            mSystemClockInMillis += elapse;
        }
    }

    @Before
    public void setup() throws Exception {
        super.setUp(getClass().getSimpleName());

        mDataNetworkValidationStats = new TestableDataNetworkValidationStats(mPhone);
        doReturn(SignalStrength.SIGNAL_STRENGTH_GREAT).when(mSignalStrength).getLevel();
    }

    @After
    public void tearDown() throws Exception {
        mDataNetworkValidationStats = null;
        super.tearDown();
    }

    @Test
    public void testRequestDataNetworkValidation() {
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void testOnUpdateNetworkValidationStateWithSuccessStatus() {

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify that atom was logged
        ArgumentCaptor<PersistAtomsProto.DataNetworkValidation> captor =
                ArgumentCaptor.forClass(PersistAtomsProto.DataNetworkValidation.class);
        verify(mPersistAtomsStorage, times(1)).addDataNetworkValidation(
                captor.capture());
        PersistAtomsProto.DataNetworkValidation proto = captor.getValue();

        // Make sure variables
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__NETWORK_TYPE__NETWORK_TYPE_LTE,
                proto.networkType);
        assertEquals(ApnSetting.TYPE_IMS, proto.apnTypeBitmask);
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__SIGNAL_STRENGTH__SIGNAL_STRENGTH_GREAT,
                proto.signalStrength);
        assertEquals(TelephonyStatsLog
                        .DATA_NETWORK_VALIDATION__VALIDATION_RESULT__VALIDATION_RESULT_SUCCESS,
                proto.validationResult);
        assertEquals(100L, proto.elapsedTimeInMillis);
        assertFalse(proto.handoverAttempted);
        assertEquals(1, proto.networkValidationCount);
    }

    @Test
    public void testOnUpdateNetworkValidationStateWithFailureStatus() {

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_EMERGENCY);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onHandoverAttempted();
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS,
                TelephonyManager.NETWORK_TYPE_IWLAN);

        // Verify that atom was logged
        ArgumentCaptor<PersistAtomsProto.DataNetworkValidation> captor =
                ArgumentCaptor.forClass(PersistAtomsProto.DataNetworkValidation.class);
        verify(mPersistAtomsStorage, times(1)).addDataNetworkValidation(
                captor.capture());
        PersistAtomsProto.DataNetworkValidation proto = captor.getValue();

        // Make sure variables
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__NETWORK_TYPE__NETWORK_TYPE_IWLAN,
                proto.networkType);
        assertEquals(ApnSetting.TYPE_EMERGENCY, proto.apnTypeBitmask);
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__SIGNAL_STRENGTH__SIGNAL_STRENGTH_GREAT,
                proto.signalStrength);
        assertEquals(TelephonyStatsLog
                        .DATA_NETWORK_VALIDATION__VALIDATION_RESULT__VALIDATION_RESULT_SUCCESS,
                proto.validationResult);
        assertEquals(200L, proto.elapsedTimeInMillis);
        assertTrue(proto.handoverAttempted);
        assertEquals(1, proto.networkValidationCount);
    }

    @Test
    public void testOnUpdateNetworkValidationStateWithInProgressStatus() {

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_NOT_REQUESTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void testOnUpdateNetworkValidationStateWithNotRequestedStatus() {

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_NOT_REQUESTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void testOnUpdateNetworkValidationStateWithUnsupportedStatus() {

        // Set up
        doReturn(SignalStrength.SIGNAL_STRENGTH_POOR).when(mSignalStrength).getLevel();

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(300L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_UNSUPPORTED,
                TelephonyManager.NETWORK_TYPE_LTE);

        // Verify that atom was logged
        ArgumentCaptor<PersistAtomsProto.DataNetworkValidation> captor =
                ArgumentCaptor.forClass(PersistAtomsProto.DataNetworkValidation.class);
        verify(mPersistAtomsStorage, times(1)).addDataNetworkValidation(
                captor.capture());
        PersistAtomsProto.DataNetworkValidation proto = captor.getValue();

        // Make sure variables
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__NETWORK_TYPE__NETWORK_TYPE_LTE,
                proto.networkType);
        assertEquals(ApnSetting.TYPE_IMS, proto.apnTypeBitmask);
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__SIGNAL_STRENGTH__SIGNAL_STRENGTH_POOR,
                proto.signalStrength);
        assertEquals(TelephonyStatsLog
                .DATA_NETWORK_VALIDATION__VALIDATION_RESULT__VALIDATION_RESULT_NOT_SUPPORTED,
                proto.validationResult);
        assertEquals(300L, proto.elapsedTimeInMillis);
        assertFalse(proto.handoverAttempted);
        assertEquals(1, proto.networkValidationCount);
    }


    @Test
    public void testOnDataNetworkDisconnected() {

        // Set up
        doReturn(SignalStrength.SIGNAL_STRENGTH_POOR).when(mSignalStrength).getLevel();

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(300L);
        mDataNetworkValidationStats.onDataNetworkDisconnected(TelephonyManager.NETWORK_TYPE_LTE);

        // Verify that atom was logged
        ArgumentCaptor<PersistAtomsProto.DataNetworkValidation> captor =
                ArgumentCaptor.forClass(PersistAtomsProto.DataNetworkValidation.class);
        verify(mPersistAtomsStorage, times(1)).addDataNetworkValidation(
                captor.capture());
        PersistAtomsProto.DataNetworkValidation proto = captor.getValue();

        // Make sure variables
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__NETWORK_TYPE__NETWORK_TYPE_LTE,
                proto.networkType);
        assertEquals(ApnSetting.TYPE_IMS, proto.apnTypeBitmask);
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__SIGNAL_STRENGTH__SIGNAL_STRENGTH_POOR,
                proto.signalStrength);
        assertEquals(TelephonyStatsLog
                .DATA_NETWORK_VALIDATION__VALIDATION_RESULT__VALIDATION_RESULT_UNSPECIFIED,
                proto.validationResult);
        assertEquals(300L, proto.elapsedTimeInMillis);
        assertFalse(proto.handoverAttempted);
        assertEquals(1, proto.networkValidationCount);
    }

    @Test
    public void testOnUpdateNetworkValidationState_DupStatus() {

        // Test
        mDataNetworkValidationStats.onRequestNetworkValidation(ApnSetting.TYPE_IMS);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_IN_PROGRESS,
                TelephonyManager.NETWORK_TYPE_LTE);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS,
                TelephonyManager.NETWORK_TYPE_UMTS);
        mDataNetworkValidationStats.elapseTimeInMillis(100L);
        mDataNetworkValidationStats.onUpdateNetworkValidationState(
                PreciseDataConnectionState.NETWORK_VALIDATION_FAILURE,
                TelephonyManager.NETWORK_TYPE_NR);

        // Verify that atom was logged
        ArgumentCaptor<PersistAtomsProto.DataNetworkValidation> captor =
                ArgumentCaptor.forClass(PersistAtomsProto.DataNetworkValidation.class);
        verify(mPersistAtomsStorage, times(1)).addDataNetworkValidation(
                captor.capture());
        PersistAtomsProto.DataNetworkValidation proto = captor.getValue();

        // Make sure variables
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__NETWORK_TYPE__NETWORK_TYPE_UMTS,
                proto.networkType);
        assertEquals(ApnSetting.TYPE_IMS, proto.apnTypeBitmask);
        assertEquals(
                TelephonyStatsLog.DATA_NETWORK_VALIDATION__SIGNAL_STRENGTH__SIGNAL_STRENGTH_GREAT,
                proto.signalStrength);
        assertEquals(TelephonyStatsLog
                        .DATA_NETWORK_VALIDATION__VALIDATION_RESULT__VALIDATION_RESULT_SUCCESS,
                proto.validationResult);
        assertEquals(200L, proto.elapsedTimeInMillis);
        assertFalse(proto.handoverAttempted);
        assertEquals(1, proto.networkValidationCount);
    }
}
