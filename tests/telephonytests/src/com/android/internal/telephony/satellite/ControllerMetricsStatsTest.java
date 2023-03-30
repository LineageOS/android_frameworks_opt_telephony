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

package com.android.internal.telephony.satellite;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.metrics.SatelliteStats;
import com.android.internal.telephony.satellite.metrics.ControllerMetricsStats;
import com.android.telephony.Rlog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ControllerMetricsStatsTest extends TelephonyTest {
    private static final String TAG = "ControllerMetricsStatsTest";

    private static final long MODEM_ENABLED_TIME = 1000L;

    private TestControllerMetricsStats mControllerMetricsStatsUT;
    private TestSatelliteStats mTestStats;

    @Mock private Context mMockContext;
    @Spy private ControllerMetricsStats mSpyControllerMetricsStats;
    @Mock private SatelliteStats mMockSatelliteStats;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        Rlog.d(TAG, "setUp()");
        mTestStats = new TestSatelliteStats();
        mControllerMetricsStatsUT =
                new TestControllerMetricsStats(mMockContext, mTestStats);
        mMockContext = mock(Context.class);
        mMockSatelliteStats = mock(SatelliteStats.class);
        mSpyControllerMetricsStats =
                Mockito.spy(ControllerMetricsStats.make(mMockContext, mMockSatelliteStats));
    }

    @After
    public void tearDown() throws Exception {
        Rlog.d(TAG, "tearDown()");
        mTestStats = null;
        mControllerMetricsStatsUT = null;
        mMockSatelliteStats = null;
        mSpyControllerMetricsStats = null;
        super.tearDown();
    }

    @Test
    public void testReportServiceEnablementSuccessCount() {
        mTestStats.initializeParams();
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportServiceEnablementSuccessCount();
        }
        assertEquals(10, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testReportServiceEnablementFailCount() {
        mTestStats.initializeParams();
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportServiceEnablementFailCount();
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(10, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testReportOutgoingDatagramSuccessCount() {
        mTestStats.initializeParams();
        int datagramType = SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramSuccessCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(10, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        datagramType = SatelliteManager.DATAGRAM_TYPE_LOCATION_SHARING;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramSuccessCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(10, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        datagramType = SatelliteManager.DATAGRAM_TYPE_UNKNOWN;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramSuccessCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void reportOutgoingDatagramFailCount() {
        mTestStats.initializeParams();
        int datagramType = SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramFailCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(10, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        datagramType = SatelliteManager.DATAGRAM_TYPE_LOCATION_SHARING;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramFailCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(10, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        datagramType = SatelliteManager.DATAGRAM_TYPE_UNKNOWN;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportOutgoingDatagramFailCount(datagramType);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(10, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testReportIncomingDatagramCount() {
        mTestStats.initializeParams();

        int result = SatelliteManager.SATELLITE_ERROR_NONE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportIncomingDatagramCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(10, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_SERVER_ERROR;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportIncomingDatagramCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(10, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportIncomingDatagramCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(10, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testReportProvisionCount() {
        mTestStats.initializeParams();

        int result = SatelliteManager.SATELLITE_ERROR_NONE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportProvisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(10, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_SERVER_ERROR;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportProvisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(10, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportProvisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(10, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testReportDeprovisionCount() {
        mTestStats.initializeParams();

        int result = SatelliteManager.SATELLITE_ERROR_NONE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportDeprovisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(10, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(0, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_SERVER_ERROR;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportDeprovisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(10, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();

        result = SatelliteManager.SATELLITE_INVALID_TELEPHONY_STATE;
        for (int i = 0; i < 10; i++) {
            mControllerMetricsStatsUT.reportDeprovisionCount(result);
        }
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsSuccess);
        assertEquals(0, mTestStats.mCountOfSatelliteServiceEnablementsFail);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfOutgoingDatagramFail);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramSuccess);
        assertEquals(0, mTestStats.mCountOfIncomingDatagramFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeSosSmsFail);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingSuccess);
        assertEquals(0, mTestStats.mCountOfDatagramTypeLocationSharingFail);
        assertEquals(0, mTestStats.mCountOfProvisionSuccess);
        assertEquals(0, mTestStats.mCountOfProvisionFail);
        assertEquals(0, mTestStats.mCountOfDeprovisionSuccess);
        assertEquals(10, mTestStats.mCountOfDeprovisionFail);
        assertEquals(0, mTestStats.mTotalServiceUptimeSec);
        assertEquals(0, mTestStats.mTotalBatteryConsumptionPercent);
        assertEquals(0, mTestStats.mTotalBatteryChargedTimeSec);
        mTestStats.initializeParams();
    }

    @Test
    public void testOnSatelliteEnabled() {
        // set precondition
        doReturn(false).when(mSpyControllerMetricsStats).isSatelliteModemOn();

        doNothing().when(mSpyControllerMetricsStats).startCaptureBatteryLevel();
        doReturn(MODEM_ENABLED_TIME).when(mSpyControllerMetricsStats).getCurrentTime();

        // test object
        mSpyControllerMetricsStats.onSatelliteEnabled();

        // verification
        verify(mSpyControllerMetricsStats).startCaptureBatteryLevel();
        verify(mSpyControllerMetricsStats).getCurrentTime();
    }

    @Test
    public void testOnSatelliteDisabled() {
        // set precondition
        doNothing().when(mMockSatelliteStats).onSatelliteControllerMetrics(any());

        doReturn(true).when(mSpyControllerMetricsStats).isSatelliteModemOn();

        doReturn(0).when(mSpyControllerMetricsStats).captureTotalServiceUpTimeSec();
        doReturn(0).when(mSpyControllerMetricsStats).captureTotalBatteryConsumptionPercent(any());
        doReturn(0).when(mSpyControllerMetricsStats).captureTotalBatteryChargeTimeSec();

        // test object
        mSpyControllerMetricsStats.onSatelliteDisabled();

        // verification
        verify(mSpyControllerMetricsStats).captureTotalServiceUpTimeSec();
        verify(mSpyControllerMetricsStats).captureTotalBatteryConsumptionPercent(any());
        verify(mSpyControllerMetricsStats).captureTotalBatteryChargeTimeSec();
    }

    static class TestControllerMetricsStats extends ControllerMetricsStats {
        TestControllerMetricsStats(Context context, SatelliteStats satelliteStats) {
            super(context, satelliteStats);
        }
    }

    static class TestSatelliteStats extends SatelliteStats {
        public int mCountOfSatelliteServiceEnablementsSuccess;
        public int mCountOfSatelliteServiceEnablementsFail;
        public int mCountOfOutgoingDatagramSuccess;
        public int mCountOfOutgoingDatagramFail;
        public int mCountOfIncomingDatagramSuccess;
        public int mCountOfIncomingDatagramFail;
        public int mCountOfDatagramTypeSosSmsSuccess;
        public int mCountOfDatagramTypeSosSmsFail;
        public int mCountOfDatagramTypeLocationSharingSuccess;
        public int mCountOfDatagramTypeLocationSharingFail;
        public int mCountOfProvisionSuccess;
        public int mCountOfProvisionFail;
        public int mCountOfDeprovisionSuccess;
        public int mCountOfDeprovisionFail;
        public int mTotalServiceUptimeSec;
        public int mTotalBatteryConsumptionPercent;
        public int mTotalBatteryChargedTimeSec;

        @Override
        public synchronized void onSatelliteControllerMetrics(SatelliteControllerParams param) {
            mCountOfSatelliteServiceEnablementsSuccess +=
                    param.getCountOfSatelliteServiceEnablementsSuccess();
            mCountOfSatelliteServiceEnablementsFail +=
                    param.getCountOfSatelliteServiceEnablementsFail();
            mCountOfOutgoingDatagramSuccess += param.getCountOfOutgoingDatagramSuccess();
            mCountOfOutgoingDatagramFail += param.getCountOfOutgoingDatagramFail();
            mCountOfIncomingDatagramSuccess += param.getCountOfIncomingDatagramSuccess();
            mCountOfIncomingDatagramFail += param.getCountOfIncomingDatagramFail();
            mCountOfDatagramTypeSosSmsSuccess += param.getCountOfDatagramTypeSosSmsSuccess();
            mCountOfDatagramTypeSosSmsFail += param.getCountOfDatagramTypeSosSmsFail();
            mCountOfDatagramTypeLocationSharingSuccess +=
                    param.getCountOfDatagramTypeLocationSharingSuccess();
            mCountOfDatagramTypeLocationSharingFail +=
                    param.getCountOfDatagramTypeLocationSharingFail();
            mCountOfProvisionSuccess += param.getCountOfProvisionSuccess();
            mCountOfProvisionFail += param.getCountOfProvisionFail();
            mCountOfDeprovisionSuccess += param.getCountOfDeprovisionSuccess();
            mCountOfDeprovisionFail += param.getCountOfDeprovisionFail();
            mTotalServiceUptimeSec += param.getTotalServiceUptimeSec();
            mTotalBatteryConsumptionPercent += param.getTotalBatteryConsumptionPercent();
            mTotalBatteryChargedTimeSec += param.getTotalBatteryChargedTimeSec();
        }

        public void initializeParams() {
            mCountOfSatelliteServiceEnablementsSuccess = 0;
            mCountOfSatelliteServiceEnablementsFail = 0;
            mCountOfOutgoingDatagramSuccess = 0;
            mCountOfOutgoingDatagramFail = 0;
            mCountOfIncomingDatagramSuccess = 0;
            mCountOfIncomingDatagramFail = 0;
            mCountOfDatagramTypeSosSmsSuccess = 0;
            mCountOfDatagramTypeSosSmsFail = 0;
            mCountOfDatagramTypeLocationSharingSuccess = 0;
            mCountOfDatagramTypeLocationSharingFail = 0;
            mCountOfProvisionSuccess = 0;
            mCountOfProvisionFail = 0;
            mCountOfDeprovisionSuccess = 0;
            mCountOfDeprovisionFail = 0;
            mTotalServiceUptimeSec = 0;
            mTotalBatteryConsumptionPercent = 0;
            mTotalBatteryChargedTimeSec = 0;
        }
    }
}
