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

package com.android.internal.telephony;

import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.testing.AndroidTestingRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit test for {@link SignalStrengthUpdateRequest}.
 */
@RunWith(AndroidTestingRunner.class)
public class SignalStrengthControllerTest extends TelephonyTest {

    private static final String TAG = "SignalStrengthControllerTest";

    private static final int ACTIVE_SUB_ID = 0;
    private static final int INVALID_SUB_ID = 1000;
    private static final int CALLING_UID = 12345;

    private SignalStrengthController mSignalStrengthController;
    private TestHandlerThread mTestHandlerThread;
    private Handler mHandler;

    private class TestHandlerThread extends HandlerThread {
        private TestHandlerThread(String name) {
            super(name);
        }

        @Override
        protected void onLooperPrepared() {
            mSignalStrengthController = new SignalStrengthController(mPhone);
            when(mPhone.getSignalStrengthController()).thenReturn(mSignalStrengthController);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("SignalStrengthControllerTest setUp.");
        super.setUp(TAG);

        when(mPhone.getSubId()).thenReturn(ACTIVE_SUB_ID);

        mTestHandlerThread = new TestHandlerThread(TAG);
        mTestHandlerThread.start();
        mHandler = mTestHandlerThread.getThreadHandler();
        waitUntilReady();
        waitForLastHandlerAction(mHandler);
    }


    @After
    public void tearDown() throws Exception {
        mSignalStrengthController = null;
        if (mTestHandlerThread != null) {
            mTestHandlerThread.quit();
            mTestHandlerThread.join();
        }

        super.tearDown();
    }

    /**
     * Verify that SignalStrengthUpdateRequest with invalid subId should trigger
     * setAlwaysReportSignalStrength with false.
     */
    @Test
    public void updateAlwaysReportSignalStrength_requestWithInvalidSubId_shouldBeFalse() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                true /* shouldReportWhileIdle*/,
                true /* shouldReportSystemWhileIdle */
        );

        mSignalStrengthController.setSignalStrengthUpdateRequest(INVALID_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        verify(mPhone).setAlwaysReportSignalStrength(eq(false));
    }

    /**
     * Verify that with a valid subId, SignalStrengthUpdateRequest asking to report signal while
     * idle should trigger setAlwaysReportSignalStrength with true.
     */
    @Test
    public void updateAlwaysReportSignalStrength_requestReportWhileIdle_shouldBeTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                true /* shouldReportWhileIdle*/,
                false /* shouldReportSystemWhileIdle */
        );

        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        verify(mPhone).setAlwaysReportSignalStrength(eq(true));
    }

    /**
     * Verify that with a valid subId, SignalStrengthUpdateRequest asking to report system signal
     * while idle should trigger setAlwaysReportSignalStrength with true.
     */
    @Test
    public void updateAlwaysReportSignalStrength_requestReportSystemWhileIdle_shouldBeTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                false /* shouldReportWhileIdle*/,
                true /* shouldReportSystemWhileIdle */
        );

        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        verify(mPhone).setAlwaysReportSignalStrength(eq(true));
    }

    /**
     * Verify that when device is high powered, shouldHonorSystemThresholds should return true.
     */
    @Test
    public void shouldHonorSystemThresholds_deviceIsHighPowered_returnTrue() {
        when(mPhone.isDeviceIdle()).thenReturn(false);

        assertThat(mSignalStrengthController.shouldHonorSystemThresholds()).isTrue();
    }

    /**
     * Verify that when device is idle and no SignalUpdateRequest received before,
     * shouldHonorSystemThresholds should return false.
     */
    @Test
    public void shouldHonorSystemThresholds_deviceIdle_noSignalRequest_returnTrue() {
        when(mPhone.isDeviceIdle()).thenReturn(true);

        assertThat(mSignalStrengthController.shouldHonorSystemThresholds()).isFalse();
    }

    /**
     * Verify that when device is idle and with SignalUpdateRequest to report system threshold
     * received before, shouldHonorSystemThresholds should return false.
     */
    @Test
    public void shouldHonorSystemThresholds_deviceIdle_systemSignalRequest_returnTrue() {
        when(mPhone.isDeviceIdle()).thenReturn(true);

        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                false /* shouldReportWhileIdle*/,
                true /* shouldReportSystemWhileIdle */
        );
        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        assertThat(mSignalStrengthController.shouldHonorSystemThresholds()).isTrue();
    }

    /**
     * Verify that when no SignalUpdateRequest received, shouldEnableSignalThresholdForAppRequest
     * should return false.
     */
    @Test
    public void shouldEnableSignalThresholdForAppRequest_noRequest_returnFalse() {
        assertThat(mSignalStrengthController.shouldEnableSignalThresholdForAppRequest(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SIGNAL_MEASUREMENT_TYPE_RSSI,
                ACTIVE_SUB_ID,
                false /* isDeviceIdle */
        )).isFalse();
    }

    /**
     * Verify that in high power mode, the shouldEnableSignalThresholdForAppRequest should return
     * true if the queried ran/measurement/subId parameters match exist SignalUpdateRecord.
     */
    @Test
    public void shouldEnableSignalThresholdForAppRequest_highPowered_matchedRequest_returnTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                false /* shouldReportWhileIdle*/,
                false /* shouldReportSystemWhileIdle */
        );
        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        assertThat(mSignalStrengthController.shouldEnableSignalThresholdForAppRequest(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SIGNAL_MEASUREMENT_TYPE_RSSI,
                ACTIVE_SUB_ID,
                false /* isDeviceIdle */
        )).isTrue();
    }

    /**
     * Verify that in idle mode, the shouldEnableSignalThresholdForAppRequest should return
     * false if the queried ran/measurement/subId parameters match exist SignalUpdateRequest which
     * did not ask to report signal while idle.
     */
    @Test
    public void enableSignalThresholdForAppRequest_idle_noReportInIdle_returnTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                false /* shouldReportWhileIdle*/,
                false /* shouldReportSystemWhileIdle */
        );
        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        assertThat(mSignalStrengthController.shouldEnableSignalThresholdForAppRequest(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SIGNAL_MEASUREMENT_TYPE_RSSI,
                ACTIVE_SUB_ID,
                true /* isDeviceIdle */
        )).isFalse();
    }

    /**
     * Verify that in idle mode, the shouldEnableSignalThresholdForAppRequest should return
     * true if the queried ran/measurement/subId parameters match exist SignalUpdateRecord which
     * request to report signal while idle.
     */
    @Test
    public void shouldEnableSignalThresholdForAppRequest_idle_reportInIdle_returnTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                true /* shouldReportWhileIdle*/,
                false /* shouldReportSystemWhileIdle */
        );
        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        assertThat(mSignalStrengthController.shouldEnableSignalThresholdForAppRequest(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SIGNAL_MEASUREMENT_TYPE_RSSI,
                ACTIVE_SUB_ID,
                true /* isDeviceIdle */
        )).isTrue();
    }

    /**
     * Verify that in idle mode, the shouldEnableSignalThresholdForAppRequest should return
     * true if the queried ran/measurement/subId parameters match exist SignalUpdateRecord which
     * request to report system signal while idle.
     */
    @Test
    public void shouldEnableSignalThresholdForAppRequest_idle_reportSystemInIdle_returnTrue() {
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                createTestSignalThresholdInfo(),
                false /* shouldReportWhileIdle*/,
                true /* shouldReportSystemWhileIdle */
        );
        mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        waitForLastHandlerAction(mHandler);

        assertThat(mSignalStrengthController.shouldEnableSignalThresholdForAppRequest(
                AccessNetworkConstants.AccessNetworkType.GERAN,
                SIGNAL_MEASUREMENT_TYPE_RSSI,
                ACTIVE_SUB_ID,
                true /* isDeviceIdle */
        )).isTrue();
    }

    @Test
    public void getConsolidatedSignalThresholds_consolidateAppsThresholdsWithSystem() {
        when(mPhone.isDeviceIdle()).thenReturn(false);

        final int ran = AccessNetworkConstants.AccessNetworkType.NGRAN;
        final int measurement = SIGNAL_MEASUREMENT_TYPE_SSSINR;
        final int[] systemThresholds = new int[]{0, 10, 20, 30};
        final int hysteresis = 2;

        // Map key is the candidate thresholds from application, map value is the expected
        // consolidated thresholds with systemThresholds.
        Map<int[], int[]> cases = Map.of(
                new int[]{-3, -6}, new int[]{-6, -3, 0, 10, 20, 30},
                new int[]{34, 39}, new int[]{0, 10, 20, 30, 34, 39},
                new int[]{-5, 4, 13, 23, 33}, new int[]{-5, 0, 4, 10, 13, 20, 23, 30, 33},
                new int[]{9, 10, 11, 12}, new int[]{0, 10, 20, 30},
                new int[]{1, 3, 5, 7, 8}, new int[]{0, 3, 7, 10, 20, 30},
                new int[]{17, 12, 16, 14, 17}, new int[]{0, 10, 14, 17, 20, 30}
        );

        for (int[] candidate : cases.keySet()) {
            int[] target = cases.get(candidate);

            SignalThresholdInfo info = new SignalThresholdInfo.Builder()
                    .setRadioAccessNetworkType(ran)
                    .setSignalMeasurementType(measurement)
                    .setThresholds(candidate, true /* isSystem */)
                    .build();
            SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                    info,
                    false /* shouldReportWhileIdle*/,
                    false /* shouldReportSystemWhileIdle */
            );
            mSignalStrengthController.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                    request, Message.obtain(mHandler));
            waitForLastHandlerAction(mHandler);

            assertThat(mSignalStrengthController.getConsolidatedSignalThresholds(
                    ran, measurement, systemThresholds, hysteresis
            )).isEqualTo(target);

            // Each pair in the Map is tested separately (instead of cumulatively).
            // Remove the request once it is done.
            mSignalStrengthController.clearSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                    request, Message.obtain(mHandler));
            waitForLastHandlerAction(mHandler);
        }
    }

    private SignalThresholdInfo createTestSignalThresholdInfo() {
        SignalThresholdInfo info = new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                .setSignalMeasurementType(SIGNAL_MEASUREMENT_TYPE_RSSI)
                .setThresholds(new int[]{-100, -90})
                .build();
        return info;
    }

    private SignalStrengthUpdateRequest createTestSignalStrengthUpdateRequest(
            SignalThresholdInfo info, boolean shouldReportWhileIdle,
            boolean shouldReportSystemWhileIdle) {
        List<SignalThresholdInfo> infoList = new ArrayList<>();
        infoList.add(info);

        SignalStrengthUpdateRequest.Builder builder = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infoList);
        if (shouldReportWhileIdle) {
            builder.setReportingRequestedWhileIdle(true);
        }
        if (shouldReportSystemWhileIdle) {
            builder.setSystemThresholdReportingRequestedWhileIdle(true);
        }
        return builder.build();
    }
}
