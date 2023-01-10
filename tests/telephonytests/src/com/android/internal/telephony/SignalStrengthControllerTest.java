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

import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
import static android.telephony.SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SignalStrength;
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;
import android.test.suitebuilder.annotation.MediumTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.util.ArrayUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit test for {@link SignalStrengthController}.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SignalStrengthControllerTest extends TelephonyTest {
    private static final String TAG = "SignalStrengthControllerTest";

    private static final int ACTIVE_SUB_ID = 0;
    private static final int INVALID_SUB_ID = 1000;
    private static final int CALLING_UID = 12345;
    private static final int PHONE_ID = 0;
    private static final String HOME_PLMN = "310260";
    private static final String PLMN1 = "480123";
    private static final String PLMN2 = "586111";
    private static final String HOME_PNN = "home pnn";
    private static final String[] CARRIER_CONFIG_SPDI = new String[] {HOME_PLMN, PLMN2};
    private static final String[] CARRIER_CONFIG_EHPLMN = new String[] {HOME_PLMN, PLMN1};
    private static final String[] CARRIER_CONFIG_PNN = new String[] {
            String.format("%s,%s", HOME_PNN, "short"), "f2,s2"
    };

    // Mocked classes
    private Handler mHandler;

    private SignalStrengthController mSsc;
    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        mHandler = Mockito.mock(Handler.class);

        when(mPhone.getSubId()).thenReturn(ACTIVE_SUB_ID);
        mSsc = new SignalStrengthController(mPhone);
        replaceInstance(Handler.class, "mLooper", mHandler, mSsc.getLooper());
        replaceInstance(Phone.class, "mLooper", mPhone, mSsc.getLooper());

        // Config a fixed supported RAN/MeasurementTypes to make the test more stable
        mBundle = mContextFixture.getCarrierConfigBundle();
        // Support GERAN with RSSI
        mBundle.putIntArray(CarrierConfigManager.KEY_GSM_RSSI_THRESHOLDS_INT_ARRAY,
                new int[] {
                        -109, /* SIGNAL_STRENGTH_POOR */
                        -103, /* SIGNAL_STRENGTH_MODERATE */
                        -97, /* SIGNAL_STRENGTH_GOOD */
                        -89,  /* SIGNAL_STRENGTH_GREAT */
                });
        // Support EUTRAN with RSRP
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USED_FOR_LTE_SIGNAL_BAR_INT,
                1 /* USE_RSRP */);
        mBundle.putIntArray(CarrierConfigManager.KEY_LTE_RSRP_THRESHOLDS_INT_ARRAY,
                new int[] {
                        -115, /* SIGNAL_STRENGTH_POOR */
                        -105, /* SIGNAL_STRENGTH_MODERATE */
                        -95, /* SIGNAL_STRENGTH_GOOD */
                        -85,  /* SIGNAL_STRENGTH_GREAT */
                });
        // Support NR with SSRSRP
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                1 /* USE_SSRSRP */);
        mBundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                new int[] {
                        -110, /* SIGNAL_STRENGTH_POOR */
                        -90, /* SIGNAL_STRENGTH_MODERATE */
                        -80, /* SIGNAL_STRENGTH_GOOD */
                        -64,  /* SIGNAL_STRENGTH_GREAT */
                });
        // By default, NR with SSRSRQ and SSSINR is not supported
        mBundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
                new int[] {
                        -31, /* SIGNAL_STRENGTH_POOR */
                        -19, /* SIGNAL_STRENGTH_MODERATE */
                        -7, /* SIGNAL_STRENGTH_GOOD */
                        6  /* SIGNAL_STRENGTH_GREAT */
                });
        mBundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSSINR_THRESHOLDS_INT_ARRAY,
                new int[] {
                        -5, /* SIGNAL_STRENGTH_POOR */
                        5, /* SIGNAL_STRENGTH_MODERATE */
                        15, /* SIGNAL_STRENGTH_GOOD */
                        30  /* SIGNAL_STRENGTH_GREAT */
                });
        processAllMessages();
        reset(mSimulatedCommandsVerifier);
    }

    @After
    public void tearDown() throws Exception {
        mSsc = null;
        mBundle = null;
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

        mSsc.setSignalStrengthUpdateRequest(INVALID_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

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

        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

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

        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        verify(mPhone).setAlwaysReportSignalStrength(eq(true));
    }

    /**
     * Verify that when device is high powered, shouldHonorSystemThresholds should return true.
     */
    @Test
    public void shouldHonorSystemThresholds_deviceIsHighPowered_returnTrue() {
        when(mPhone.isDeviceIdle()).thenReturn(false);

        assertThat(mSsc.shouldHonorSystemThresholds()).isTrue();
    }

    /**
     * Verify that when device is idle and no SignalUpdateRequest received before,
     * shouldHonorSystemThresholds should return false.
     */
    @Test
    public void shouldHonorSystemThresholds_deviceIdle_noSignalRequest_returnTrue() {
        when(mPhone.isDeviceIdle()).thenReturn(true);

        assertThat(mSsc.shouldHonorSystemThresholds()).isFalse();
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
        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        assertThat(mSsc.shouldHonorSystemThresholds()).isTrue();
    }

    /**
     * Verify that when no SignalUpdateRequest received, shouldEnableSignalThresholdForAppRequest
     * should return false.
     */
    @Test
    public void shouldEnableSignalThresholdForAppRequest_noRequest_returnFalse() {
        assertThat(mSsc.shouldEnableSignalThresholdForAppRequest(
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
        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        assertThat(mSsc.shouldEnableSignalThresholdForAppRequest(
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
        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        assertThat(mSsc.shouldEnableSignalThresholdForAppRequest(
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
        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        assertThat(mSsc.shouldEnableSignalThresholdForAppRequest(
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
        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        assertThat(mSsc.shouldEnableSignalThresholdForAppRequest(
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
            mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                    request, Message.obtain(mHandler));
            processAllMessages();

            assertThat(mSsc.getConsolidatedSignalThresholds(
                    ran, measurement, systemThresholds, hysteresis
            )).isEqualTo(target);

            // Each pair in the Map is tested separately (instead of cumulatively).
            // Remove the request once it is done.
            mSsc.clearSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                    request, Message.obtain(mHandler));
            processAllMessages();
        }
    }

    @Test
    @MediumTest
    public void testSignalStrength() {
        // Send in GSM Signal Strength Info and expect isGsm == true
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(-53, 0, SignalStrength.INVALID),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr());

        sendSignalStrength(ss);
        assertEquals(mSsc.getSignalStrength(), ss);
        assertEquals(mSsc.getSignalStrength().isGsm(), true);

        // Send in CDMA+LTE Signal Strength Info and expect isGsm == true
        ss = new SignalStrength(
                new CellSignalStrengthCdma(-90, -12,
                        SignalStrength.INVALID, SignalStrength.INVALID, SignalStrength.INVALID),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(
                        -110, -114, -5, 0, SignalStrength.INVALID, SignalStrength.INVALID),
                new CellSignalStrengthNr());

        sendSignalStrength(ss);
        assertEquals(mSsc.getSignalStrength(), ss);
        assertEquals(mSsc.getSignalStrength().isGsm(), true);

        // Send in CDMA-only Signal Strength Info and expect isGsm == false
        ss = new SignalStrength(
                new CellSignalStrengthCdma(-90, -12,
                        SignalStrength.INVALID, SignalStrength.INVALID, SignalStrength.INVALID),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr());

        sendSignalStrength(ss);
        assertEquals(mSsc.getSignalStrength(), ss);
        assertEquals(mSsc.getSignalStrength().isGsm(), false);
    }

    @Test
    public void testLteSignalStrengthReportingCriteria() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(
                        -110, /* rssi */
                        -114, /* rsrp */
                        -5, /* rsrq */
                        0, /* rssnr */
                        SignalStrength.INVALID, /* cqi */
                        SignalStrength.INVALID /* ta */),
                new CellSignalStrengthNr());

        mBundle.putBoolean(CarrierConfigManager.KEY_USE_ONLY_RSRP_FOR_LTE_SIGNAL_BAR_BOOL,
                true);

        sendCarrierConfigUpdate();

        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        // Default thresholds are POOR=-115 MODERATE=-105 GOOD=-95 GREAT=-85
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_POOR, mSsc.getSignalStrength().getLevel());

        int[] lteThresholds = {
                -130, // SIGNAL_STRENGTH_POOR
                -120, // SIGNAL_STRENGTH_MODERATE
                -110, // SIGNAL_STRENGTH_GOOD
                -100,  // SIGNAL_STRENGTH_GREAT
        };
        mBundle.putIntArray(CarrierConfigManager.KEY_LTE_RSRP_THRESHOLDS_INT_ARRAY,
                lteThresholds);
        sendCarrierConfigUpdate();

        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(mSsc.getSignalStrength().getLevel(),
                CellSignalStrength.SIGNAL_STRENGTH_MODERATE);
    }

    @Test
    public void testLteSignalStrengthReportingCriteria_convertRssnrUnitFromTenDbToDB() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(
                        -110, /* rssi */
                        -114, /* rsrp */
                        -5, /* rsrq */
                        CellSignalStrengthLte.convertRssnrUnitFromTenDbToDB(-34), /* rssnr */
                        SignalStrength.INVALID, /* cqi */
                        SignalStrength.INVALID /* ta */),
                new CellSignalStrengthNr());

        int[] lteThresholds = {
                -3, // SIGNAL_STRENGTH_POOR
                1, // SIGNAL_STRENGTH_MODERATE
                5, // SIGNAL_STRENGTH_GOOD
                13,  // SIGNAL_STRENGTH_GREAT
        };
        mBundle.putIntArray(CarrierConfigManager.KEY_LTE_RSSNR_THRESHOLDS_INT_ARRAY ,
                lteThresholds);
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USED_FOR_LTE_SIGNAL_BAR_INT,
                CellSignalStrengthLte.USE_RSSNR);
        sendCarrierConfigUpdate();
        sendSignalStrength(ss);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN,
                mSsc.getSignalStrength().getLevel());

        ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(
                        -110, /* rssi */
                        -114, /* rsrp */
                        -5, /* rsrq */
                        CellSignalStrengthLte.convertRssnrUnitFromTenDbToDB(129), /* rssnr */
                        SignalStrength.INVALID, /* cqi */
                        SignalStrength.INVALID /* ta */),
                new CellSignalStrengthNr());
        sendSignalStrength(ss);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_GOOD, mSsc.getSignalStrength().getLevel());
    }

    @Test
    public void test5gNrSignalStrengthReportingCriteria_UseSsRsrp() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr(
                        -139, /** csiRsrp NONE */
                        -20, /** csiRsrq NONE */
                        -23, /** CsiSinr NONE */
                        -44, /** SsRsrp SIGNAL_STRENGTH_GREAT */
                        -20, /** SsRsrq NONE */
                        -23) /** SsSinr NONE */
        );

        // SSRSRP = 1 << 0
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                CellSignalStrengthNr.USE_SSRSRP);
        sendCarrierConfigUpdate();
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_GREAT, mSsc.getSignalStrength().getLevel());
    }

    @Test
    public void test5gNrSignalStrengthReportingCriteria_UseSsRsrpAndSsRsrq() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr(
                        -139, /** csiRsrp NONE */
                        -20, /** csiRsrq NONE */
                        -23, /** CsiSinr NONE */
                        -44, /** SsRsrp SIGNAL_STRENGTH_GREAT */
                        -32, /** SsRsrq NONE */
                        -23) /** SsSinr NONE */
        );

        // SSRSRP = 1 << 0 | SSSINR = 1 << 2
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                CellSignalStrengthNr.USE_SSRSRP | CellSignalStrengthNr.USE_SSRSRQ);
        sendCarrierConfigUpdate();
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN,
                mSsc.getSignalStrength().getLevel());
    }

    @Test
    public void test5gNrSignalStrengthReportingCriteria_ConfiguredThresholds() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr(
                        -139, /** csiRsrp NONE */
                        -20, /** csiRsrq NONE */
                        -23, /** CsiSinr NONE */
                        -64, /** SsRsrp SIGNAL_STRENGTH_GREAT */
                        -20, /** SsRsrq NONE */
                        -23) /** SsSinr NONE */
        );

        // SSRSRP = 1 << 0
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                CellSignalStrengthNr.USE_SSRSRP);
        sendCarrierConfigUpdate();
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_GREAT, mSsc.getSignalStrength().getLevel());

        int[] nrSsRsrpThresholds = {
                -110, /* SIGNAL_STRENGTH_POOR */
                -60, /* SIGNAL_STRENGTH_MODERATE */
                -55, /* SIGNAL_STRENGTH_GOOD */
                -45,  /* SIGNAL_STRENGTH_GREAT */
        };
        mBundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                nrSsRsrpThresholds);
        mBundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                CellSignalStrengthNr.USE_SSRSRP);
        sendCarrierConfigUpdate();
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_POOR,
                mSsc.getSignalStrength().getLevel());
    }

    @Test
    public void testWcdmaSignalStrengthReportingCriteria() {
        SignalStrength ss = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(-79, 0, -85, -5),
                new CellSignalStrengthTdscdma(),
                new CellSignalStrengthLte(),
                new CellSignalStrengthNr());

        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(mSsc.getSignalStrength().getLevel(), CellSignalStrength.SIGNAL_STRENGTH_GOOD);

        int[] wcdmaThresholds = {
                -110, // SIGNAL_STRENGTH_POOR
                -100, // SIGNAL_STRENGTH_MODERATE
                -90, // SIGNAL_STRENGTH_GOOD
                -80  // SIGNAL_STRENGTH_GREAT
        };
        mBundle.putIntArray(CarrierConfigManager.KEY_WCDMA_RSCP_THRESHOLDS_INT_ARRAY,
                wcdmaThresholds);
        mBundle.putString(
                CarrierConfigManager.KEY_WCDMA_DEFAULT_SIGNAL_STRENGTH_MEASUREMENT_STRING,
                "rscp");
        sendCarrierConfigUpdate();
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
        assertEquals(mSsc.getSignalStrength().getLevel(), CellSignalStrength.SIGNAL_STRENGTH_GOOD);
    }

    /**
     * Verify on both high-power and idle modes. All SignalThresholdInfo should be disabled if the
     * threshold array is empty when calling CI#setSignalStrengthReportingCriteria.
     */
    @Test
    public void consolidateAndSetReportingCriteria_allEmptyThresholdShouldBeDisabled() {
        // Firstly, test on high-power mode
        when(mPhone.isDeviceIdle()).thenReturn(false);
        SignalThresholdInfo info = new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SIGNAL_MEASUREMENT_TYPE_RSRP)
                .setThresholds(new int[]{-112}, true /* isSystem */)
                .build();
        SignalStrengthUpdateRequest request = createTestSignalStrengthUpdateRequest(
                info,
                true /* shouldReportWhileIdle*/,
                false /* shouldReportSystemWhileIdle */
        );

        mSsc.setSignalStrengthUpdateRequest(ACTIVE_SUB_ID, CALLING_UID,
                request, Message.obtain(mHandler));
        processAllMessages();

        // Expect 3 non-empty thresholds (GERAN/RSSI, EUTRAN/RSRP, NR/SSRSRP)
        // if Radio HAL ver is >= 1.5
        verifyAllEmptyThresholdAreDisabledWhenSetSignalStrengthReportingCriteria(
                3 /*expectedNonEmptyThreshold*/);

        // Then, test when device turns into idle mode in which all system thresholds are emptied
        // (shouldReportSystemWhileIdle is false)
        reset(mSimulatedCommandsVerifier);
        when(mPhone.isDeviceIdle()).thenReturn(true);
        mSsc.onDeviceIdleStateChanged(true /* isDeviceIdle */);
        processAllMessages();

        // Expect 1 non-empty threshold left (EUTRAN/RSRP set by the SignalStrengthUpdateRequest)
        verifyAllEmptyThresholdAreDisabledWhenSetSignalStrengthReportingCriteria(
                1 /*expectedNonEmptyThreshold*/);
    }

    private void verifyAllEmptyThresholdAreDisabledWhenSetSignalStrengthReportingCriteria(
            int expectedNonEmptyThreshold) {
        ArgumentCaptor<List<SignalThresholdInfo>> signalThresholdInfoCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mSimulatedCommandsVerifier).setSignalStrengthReportingCriteria(
                signalThresholdInfoCaptor.capture(), isNull());
        List<SignalThresholdInfo> capturedInfos = signalThresholdInfoCaptor.getAllValues().get(0);
        assertThat(capturedInfos).isNotEmpty();
        int actualNonEmptyThreshold = 0;
        for (SignalThresholdInfo signalThresholdInfo: capturedInfos) {
            if (ArrayUtils.isEmpty(signalThresholdInfo.getThresholds())) {
                assertThat(signalThresholdInfo.isEnabled()).isFalse();
            } else {
                actualNonEmptyThreshold++;
            }
        }
        // Only check on RADIO hal 1.5 and above to make it less flaky
        if (mPhone.getHalVersion().greaterOrEqual(RIL.RADIO_HAL_VERSION_1_5)) {
            assertThat(expectedNonEmptyThreshold).isEqualTo(actualNonEmptyThreshold);
        }
    }

    private void sendCarrierConfigUpdate() {
        CarrierConfigManager mockConfigManager = Mockito.mock(CarrierConfigManager.class);
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mockConfigManager);
        when(mockConfigManager.getConfigForSubId(anyInt())).thenReturn(mBundle);

        Intent intent = new Intent().setAction(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, PHONE_ID);
        mContext.sendBroadcast(intent);
        processAllMessages();
    }

    private void sendSignalStrength(SignalStrength ss) {
        mSimulatedCommands.setSignalStrength(ss);
        mSimulatedCommands.notifySignalStrength();
        processAllMessages();
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
