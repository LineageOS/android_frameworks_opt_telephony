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

import static android.telephony.satellite.NtnSignalStrength.NTN_SIGNAL_STRENGTH_GOOD;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static com.android.internal.telephony.satellite.SatelliteConstants.CONFIG_DATA_SOURCE_DEVICE_CONFIG;
import static com.android.internal.telephony.satellite.SatelliteConstants.ACCESS_CONTROL_TYPE_CACHED_COUNTRY_CODE;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.SatelliteProtoEnums;
import android.telephony.TelephonyProtoEnums;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.CarrierRoamingSatelliteControllerStats;
import com.android.internal.telephony.nano.PersistAtomsProto.CarrierRoamingSatelliteSession;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteAccessController;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteConfigUpdater;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteController;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteEntitlement;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteIncomingDatagram;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteOutgoingDatagram;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteProvision;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteSession;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteSosMessageRecommender;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;

public class SatelliteStatsTest extends TelephonyTest {
    private static final String TAG = SatelliteStatsTest.class.getSimpleName();

    private TestableSatelliteStats mSatelliteStats;

    private class TestableSatelliteStats extends SatelliteStats {
        TestableSatelliteStats() {
            super();
        }
    }

    @Before
    public void setup() throws Exception {
        super.setUp(getClass().getSimpleName());

        mSatelliteStats = new TestableSatelliteStats();
    }

    @After
    public void tearDown() throws Exception {
        mSatelliteStats = null;
        super.tearDown();
    }

    @Test
    public void onSatelliteControllerMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteControllerParams param =
                new SatelliteStats.SatelliteControllerParams.Builder()
                        .setCountOfSatelliteServiceEnablementsSuccess(2)
                        .setCountOfSatelliteServiceEnablementsFail(2)
                        .setCountOfOutgoingDatagramSuccess(8)
                        .setCountOfOutgoingDatagramFail(9)
                        .setCountOfIncomingDatagramSuccess(10)
                        .setCountOfIncomingDatagramFail(11)
                        .setCountOfDatagramTypeSosSmsSuccess(5)
                        .setCountOfDatagramTypeSosSmsFail(5)
                        .setCountOfDatagramTypeLocationSharingSuccess(6)
                        .setCountOfDatagramTypeLocationSharingFail(6)
                        .setCountOfProvisionSuccess(3)
                        .setCountOfProvisionFail(4)
                        .setCountOfDeprovisionSuccess(5)
                        .setCountOfDeprovisionFail(6)
                        .setTotalServiceUptimeSec(60 * 60 * 24 * 7)
                        .setTotalBatteryConsumptionPercent(7)
                        .setTotalBatteryChargedTimeSec(60 * 60 * 3)
                        .setCountOfDemoModeSatelliteServiceEnablementsSuccess(3)
                        .setCountOfDemoModeSatelliteServiceEnablementsFail(1)
                        .setCountOfDemoModeOutgoingDatagramSuccess(4)
                        .setCountOfDemoModeOutgoingDatagramFail(2)
                        .setCountOfDemoModeIncomingDatagramSuccess(3)
                        .setCountOfDemoModeIncomingDatagramFail(2)
                        .setCountOfDatagramTypeKeepAliveSuccess(1)
                        .setCountOfDatagramTypeKeepAliveFail(2)
                        .build();

        mSatelliteStats.onSatelliteControllerMetrics(param);

        ArgumentCaptor<SatelliteController> captor =
                ArgumentCaptor.forClass(SatelliteController.class);
        verify(mPersistAtomsStorage).addSatelliteControllerStats(captor.capture());
        SatelliteController stats = captor.getValue();
        assertEquals(param.getCountOfSatelliteServiceEnablementsSuccess(),
                stats.countOfSatelliteServiceEnablementsSuccess);
        assertEquals(param.getCountOfSatelliteServiceEnablementsFail(),
                stats.countOfSatelliteServiceEnablementsFail);
        assertEquals(param.getCountOfOutgoingDatagramSuccess(),
                stats.countOfOutgoingDatagramSuccess);
        assertEquals(param.getCountOfOutgoingDatagramFail(),
                stats.countOfOutgoingDatagramFail);
        assertEquals(param.getCountOfIncomingDatagramSuccess(),
                stats.countOfIncomingDatagramSuccess);
        assertEquals(param.getCountOfIncomingDatagramFail(),
                stats.countOfIncomingDatagramFail);
        assertEquals(param.getCountOfDatagramTypeSosSmsSuccess(),
                stats.countOfDatagramTypeSosSmsSuccess);
        assertEquals(param.getCountOfDatagramTypeSosSmsFail(),
                stats.countOfDatagramTypeSosSmsFail);
        assertEquals(param.getCountOfDatagramTypeLocationSharingSuccess(),
                stats.countOfDatagramTypeLocationSharingSuccess);
        assertEquals(param.getCountOfDatagramTypeLocationSharingFail(),
                stats.countOfDatagramTypeLocationSharingFail);
        assertEquals(param.getCountOfProvisionSuccess(),
                stats.countOfProvisionSuccess);
        assertEquals(param.getCountOfProvisionFail(),
                stats.countOfProvisionFail);
        assertEquals(param.getCountOfDeprovisionSuccess(),
                stats.countOfDeprovisionSuccess);
        assertEquals(param.getCountOfDeprovisionFail(),
                stats.countOfDeprovisionFail);
        assertEquals(param.getTotalServiceUptimeSec(),
                stats.totalServiceUptimeSec);
        assertEquals(param.getTotalBatteryConsumptionPercent(),
                stats.totalBatteryConsumptionPercent);
        assertEquals(param.getTotalBatteryChargedTimeSec(),
                stats.totalBatteryChargedTimeSec);
        assertEquals(param.getCountOfDemoModeSatelliteServiceEnablementsSuccess(),
                stats.countOfDemoModeSatelliteServiceEnablementsSuccess);
        assertEquals(param.getCountOfDemoModeSatelliteServiceEnablementsFail(),
                stats.countOfDemoModeSatelliteServiceEnablementsFail);
        assertEquals(param.getCountOfDemoModeOutgoingDatagramSuccess(),
                stats.countOfDemoModeOutgoingDatagramSuccess);
        assertEquals(param.getCountOfDemoModeOutgoingDatagramFail(),
                stats.countOfDemoModeOutgoingDatagramFail);
        assertEquals(param.getCountOfDemoModeIncomingDatagramSuccess(),
                stats.countOfDemoModeIncomingDatagramSuccess);
        assertEquals(param.getCountOfDemoModeIncomingDatagramFail(),
                stats.countOfDemoModeIncomingDatagramFail);
        assertEquals(param.getCountOfDatagramTypeKeepAliveSuccess(),
                stats.countOfDatagramTypeKeepAliveSuccess);
        assertEquals(param.getCountOfDatagramTypeKeepAliveFail(),
                stats.countOfDatagramTypeKeepAliveFail);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteSessionMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteSessionParams param =
                new SatelliteStats.SatelliteSessionParams.Builder()
                        .setSatelliteServiceInitializationResult(
                                SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setSatelliteTechnology(SatelliteProtoEnums.NT_RADIO_TECHNOLOGY_PROPRIETARY)
                        .setTerminationResult(SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setInitializationProcessingTime(100)
                        .setTerminationProcessingTime(200)
                        .setSessionDuration(3)
                        .setCountOfOutgoingDatagramSuccess(1)
                        .setCountOfOutgoingDatagramFailed(0)
                        .setCountOfIncomingDatagramSuccess(1)
                        .setCountOfIncomingDatagramFailed(0)
                        .setIsDemoMode(false)
                        .setMaxNtnSignalStrengthLevel(NTN_SIGNAL_STRENGTH_GOOD)
                        .build();

        mSatelliteStats.onSatelliteSessionMetrics(param);

        ArgumentCaptor<SatelliteSession> captor =
                ArgumentCaptor.forClass(SatelliteSession.class);
        verify(mPersistAtomsStorage).addSatelliteSessionStats(captor.capture());
        SatelliteSession stats = captor.getValue();
        assertEquals(param.getSatelliteServiceInitializationResult(),
                stats.satelliteServiceInitializationResult);
        assertEquals(param.getTerminationResult(), stats.satelliteServiceTerminationResult);
        assertEquals(param.getInitializationProcessingTime(),
                stats.initializationProcessingTimeMillis);
        assertEquals(param.getTerminationProcessingTime(), stats.terminationProcessingTimeMillis);
        assertEquals(param.getSessionDuration(), stats.sessionDurationSeconds);
        assertEquals(param.getCountOfOutgoingDatagramSuccess(),
                stats.countOfOutgoingDatagramSuccess);
        assertEquals(param.getCountOfOutgoingDatagramFailed(), stats.countOfOutgoingDatagramFailed);
        assertEquals(param.getCountOfIncomingDatagramSuccess(),
                stats.countOfIncomingDatagramSuccess);
        assertEquals(param.getCountOfIncomingDatagramFailed(), stats.countOfIncomingDatagramFailed);
        assertEquals(param.getIsDemoMode(), stats.isDemoMode);
        assertEquals(param.getMaxNtnSignalStrengthLevel(), stats.maxNtnSignalStrengthLevel);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteIncomingDatagramMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteIncomingDatagramParams param =
                new SatelliteStats.SatelliteIncomingDatagramParams.Builder()
                        .setResultCode(SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setDatagramSizeBytes(1 * 1024)
                        .setDatagramTransferTimeMillis(3 * 1000)
                        .setIsDemoMode(true)
                        .build();

        mSatelliteStats.onSatelliteIncomingDatagramMetrics(param);

        ArgumentCaptor<SatelliteIncomingDatagram> captor =
                ArgumentCaptor.forClass(SatelliteIncomingDatagram.class);
        verify(mPersistAtomsStorage).addSatelliteIncomingDatagramStats(captor.capture());
        SatelliteIncomingDatagram stats = captor.getValue();
        assertEquals(param.getResultCode(), stats.resultCode);
        assertEquals(param.getDatagramSizeBytes(), stats.datagramSizeBytes);
        assertEquals(param.getDatagramTransferTimeMillis(), stats.datagramTransferTimeMillis);
        assertEquals(param.getIsDemoMode(), stats.isDemoMode);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteOutgoingDatagramMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteOutgoingDatagramParams param =
                new SatelliteStats.SatelliteOutgoingDatagramParams.Builder()
                        .setDatagramType(SatelliteProtoEnums.DATAGRAM_TYPE_LOCATION_SHARING)
                        .setResultCode(SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setDatagramSizeBytes(1 * 1024)
                        .setDatagramTransferTimeMillis(3 * 1000)
                        .setIsDemoMode(true)
                        .build();

        mSatelliteStats.onSatelliteOutgoingDatagramMetrics(param);

        ArgumentCaptor<SatelliteOutgoingDatagram> captor =
                ArgumentCaptor.forClass(SatelliteOutgoingDatagram.class);
        verify(mPersistAtomsStorage).addSatelliteOutgoingDatagramStats(captor.capture());
        SatelliteOutgoingDatagram stats = captor.getValue();
        assertEquals(param.getDatagramType(), stats.datagramType);
        assertEquals(param.getResultCode(), stats.resultCode);
        assertEquals(param.getDatagramSizeBytes(), stats.datagramSizeBytes);
        assertEquals(param.getDatagramTransferTimeMillis(), stats.datagramTransferTimeMillis);
        assertEquals(param.getIsDemoMode(), stats.isDemoMode);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteProvisionMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteProvisionParams param =
                new SatelliteStats.SatelliteProvisionParams.Builder()
                        .setResultCode(
                                SatelliteProtoEnums.SATELLITE_SERVICE_PROVISION_IN_PROGRESS)
                        .setProvisioningTimeSec(5 * 1000)
                        .setIsProvisionRequest(true)
                        .setIsCanceled(false)
                        .build();

        mSatelliteStats.onSatelliteProvisionMetrics(param);

        ArgumentCaptor<SatelliteProvision> captor =
                ArgumentCaptor.forClass(SatelliteProvision.class);
        verify(mPersistAtomsStorage).addSatelliteProvisionStats(captor.capture());
        SatelliteProvision stats = captor.getValue();
        assertEquals(param.getResultCode(), stats.resultCode);
        assertEquals(param.getProvisioningTimeSec(), stats.provisioningTimeSec);
        assertEquals(param.getIsProvisionRequest(), stats.isProvisionRequest);
        assertEquals(param.getIsCanceled(), stats.isCanceled);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteSosMessageRecommenderMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteSosMessageRecommenderParams param =
                new SatelliteStats.SatelliteSosMessageRecommenderParams.Builder()
                        .setDisplaySosMessageSent(true)
                        .setCountOfTimerStarted(5)
                        .setImsRegistered(false)
                        .setCellularServiceState(TelephonyProtoEnums.SERVICE_STATE_OUT_OF_SERVICE)
                        .setIsMultiSim(false)
                        .setRecommendingHandoverType(0)
                        .setIsSatelliteAllowedInCurrentLocation(true)
                        .build();

        mSatelliteStats.onSatelliteSosMessageRecommender(param);

        ArgumentCaptor<SatelliteSosMessageRecommender> captor =
                ArgumentCaptor.forClass(SatelliteSosMessageRecommender.class);
        verify(mPersistAtomsStorage).addSatelliteSosMessageRecommenderStats(captor.capture());
        SatelliteSosMessageRecommender stats = captor.getValue();
        assertEquals(param.isDisplaySosMessageSent(),
                stats.isDisplaySosMessageSent);
        assertEquals(param.getCountOfTimerStarted(), stats.countOfTimerStarted);
        assertEquals(param.isImsRegistered(), stats.isImsRegistered);
        assertEquals(param.getCellularServiceState(), stats.cellularServiceState);
        assertEquals(param.isMultiSim(), stats.isMultiSim);
        assertEquals(param.getRecommendingHandoverType(), stats.recommendingHandoverType);
        assertEquals(param.isSatelliteAllowedInCurrentLocation(),
                stats.isSatelliteAllowedInCurrentLocation);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onCarrierRoamingSatelliteSessionMetrics_withAtoms() throws Exception {
        SatelliteStats.CarrierRoamingSatelliteSessionParams param =
                new SatelliteStats.CarrierRoamingSatelliteSessionParams.Builder()
                        .setCarrierId(100)
                        .setIsNtnRoamingInHomeCountry(true)
                        .setTotalSatelliteModeTimeSec(10 * 60)
                        .setNumberOfSatelliteConnections(5)
                        .setAvgDurationOfSatelliteConnectionSec(2 * 60)
                        .setSatelliteConnectionGapMinSec(30)
                        .setSatelliteConnectionGapAvgSec(300)
                        .setSatelliteConnectionGapMaxSec(500)
                        .setRsrpAvg(2)
                        .setRsrpMedian(3)
                        .setRssnrAvg(12)
                        .setRssnrMedian(18)
                        .setCountOfIncomingSms(6)
                        .setCountOfOutgoingSms(11)
                        .setCountOfIncomingMms(9)
                        .setCountOfOutgoingMms(14)
                        .build();

        mSatelliteStats.onCarrierRoamingSatelliteSessionMetrics(param);

        ArgumentCaptor<CarrierRoamingSatelliteSession> captor =
                ArgumentCaptor.forClass(CarrierRoamingSatelliteSession.class);
        verify(mPersistAtomsStorage).addCarrierRoamingSatelliteSessionStats(captor.capture());
        CarrierRoamingSatelliteSession stats = captor.getValue();
        assertEquals(param.getCarrierId(), stats.carrierId);
        assertEquals(param.getIsNtnRoamingInHomeCountry(), stats.isNtnRoamingInHomeCountry);
        assertEquals(param.getTotalSatelliteModeTimeSec(), stats.totalSatelliteModeTimeSec);
        assertEquals(param.getNumberOfSatelliteConnections(), stats.numberOfSatelliteConnections);
        assertEquals(param.getAvgDurationOfSatelliteConnectionSec(),
                stats.avgDurationOfSatelliteConnectionSec);
        assertEquals(param.getSatelliteConnectionGapMinSec(), stats.satelliteConnectionGapMinSec);
        assertEquals(param.getSatelliteConnectionGapAvgSec(), stats.satelliteConnectionGapAvgSec);
        assertEquals(param.getSatelliteConnectionGapMaxSec(), stats.satelliteConnectionGapMaxSec);
        assertEquals(param.getRsrpAvg(), stats.rsrpAvg);
        assertEquals(param.getRsrpMedian(), stats.rsrpMedian);
        assertEquals(param.getRssnrAvg(), stats.rssnrAvg);
        assertEquals(param.getRssnrMedian(), stats.rssnrMedian);
        assertEquals(param.getCountOfIncomingSms(), stats.countOfIncomingSms);
        assertEquals(param.getCountOfOutgoingSms(), stats.countOfOutgoingSms);
        assertEquals(param.getCountOfIncomingMms(), stats.countOfIncomingMms);
        assertEquals(param.getCountOfOutgoingMms(), stats.countOfOutgoingMms);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onCarrierRoamingSatelliteControllerStatsMetrics_withAtoms() throws Exception {
        SatelliteStats.CarrierRoamingSatelliteControllerStatsParams param =
                new SatelliteStats.CarrierRoamingSatelliteControllerStatsParams.Builder()
                        .setConfigDataSource(4)
                        .setCountOfEntitlementStatusQueryRequest(6)
                        .setCountOfSatelliteConfigUpdateRequest(2)
                        .setCountOfSatelliteNotificationDisplayed(1)
                        .setSatelliteSessionGapMinSec(15)
                        .setSatelliteSessionGapAvgSec(30)
                        .setSatelliteSessionGapMaxSec(45)
                        .build();

        mSatelliteStats.onCarrierRoamingSatelliteControllerStatsMetrics(param);

        ArgumentCaptor<CarrierRoamingSatelliteControllerStats> captor =
                ArgumentCaptor.forClass(CarrierRoamingSatelliteControllerStats.class);
        verify(mPersistAtomsStorage).addCarrierRoamingSatelliteControllerStats(captor.capture());
        CarrierRoamingSatelliteControllerStats stats = captor.getValue();
        assertEquals(param.getConfigDataSource(), stats.configDataSource);
        assertEquals(param.getCountOfEntitlementStatusQueryRequest(),
                stats.countOfEntitlementStatusQueryRequest);
        assertEquals(param.getCountOfSatelliteConfigUpdateRequest(),
                stats.countOfSatelliteConfigUpdateRequest);
        assertEquals(param.getCountOfSatelliteNotificationDisplayed(),
                stats.countOfSatelliteNotificationDisplayed);
        assertEquals(param.getSatelliteSessionGapMinSec(), stats.satelliteSessionGapMinSec);
        assertEquals(param.getSatelliteSessionGapAvgSec(), stats.satelliteSessionGapAvgSec);
        assertEquals(param.getSatelliteSessionGapMaxSec(), stats.satelliteSessionGapMaxSec);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteEntitlementMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteEntitlementParams param =
                new SatelliteStats.SatelliteEntitlementParams.Builder()
                        .setCarrierId(10)
                        .setResult(500)
                        .setEntitlementStatus(2)
                        .setIsRetry(true)
                        .setCount(5)
                        .build();

        mSatelliteStats.onSatelliteEntitlementMetrics(param);

        ArgumentCaptor<SatelliteEntitlement> captor =
                ArgumentCaptor.forClass(SatelliteEntitlement.class);
        verify(mPersistAtomsStorage).addSatelliteEntitlementStats(captor.capture());
        SatelliteEntitlement stats = captor.getValue();
        assertEquals(param.getCarrierId(), stats.carrierId);
        assertEquals(param.getResult(), stats.result);
        assertEquals(param.getEntitlementStatus(), stats.entitlementStatus);
        assertEquals(param.getIsRetry(), stats.isRetry);
        assertEquals(param.getCount(), stats.count);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteConfigUpdaterMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteConfigUpdaterParams param =
                new SatelliteStats.SatelliteConfigUpdaterParams.Builder()
                        .setConfigVersion(8)
                        .setOemConfigResult(9)
                        .setCarrierConfigResult(7)
                        .setCount(3)
                        .build();

        mSatelliteStats.onSatelliteConfigUpdaterMetrics(param);

        ArgumentCaptor<SatelliteConfigUpdater> captor =
                ArgumentCaptor.forClass(SatelliteConfigUpdater.class);
        verify(mPersistAtomsStorage).addSatelliteConfigUpdaterStats(captor.capture());
        SatelliteConfigUpdater stats = captor.getValue();
        assertEquals(param.getConfigVersion(), stats.configVersion);
        assertEquals(param.getOemConfigResult(), stats.oemConfigResult);
        assertEquals(param.getCarrierConfigResult(), stats.carrierConfigResult);
        assertEquals(param.getCount(), stats.count);

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }


    @Test
    public void onSatelliteAccessControllerMetrics_withAtoms() {
        SatelliteStats.SatelliteAccessControllerParams param =
                new SatelliteStats.SatelliteAccessControllerParams.Builder()
                        .setAccessControlType(ACCESS_CONTROL_TYPE_CACHED_COUNTRY_CODE)
                        .setLocationQueryTime(TimeUnit.SECONDS.toMillis(1))
                        .setOnDeviceLookupTime(TimeUnit.SECONDS.toMillis(2))
                        .setTotalCheckingTime(TimeUnit.SECONDS.toMillis(3))
                        .setIsAllowed(true)
                        .setIsEmergency(false)
                        .setResult(SATELLITE_RESULT_SUCCESS)
                        .setCountryCodes(new String[]{"AB", "CD"})
                        .setConfigDatasource(CONFIG_DATA_SOURCE_DEVICE_CONFIG)
                        .build();

        mSatelliteStats.onSatelliteAccessControllerMetrics(param);

        ArgumentCaptor<SatelliteAccessController> captor = ArgumentCaptor.forClass(
                SatelliteAccessController.class);
        verify(mPersistAtomsStorage).addSatelliteAccessControllerStats(captor.capture());
        SatelliteAccessController stats = captor.getValue();
        assertEquals(param.getAccessControlType(), stats.accessControlType);
        assertEquals(param.getLocationQueryTime(), stats.locationQueryTimeMillis);
        assertEquals(param.getOnDeviceLookupTime(), stats.onDeviceLookupTimeMillis);
        assertEquals(param.getTotalCheckingTime(), stats.totalCheckingTimeMillis);
        assertEquals(param.getIsAllowed(), stats.isAllowed);
        assertEquals(param.getIsEmergency(), stats.isEmergency);
        assertEquals(param.getResultCode(), stats.resultCode);
        assertEquals(param.getCountryCodes(), stats.countryCodes);
        assertEquals(param.getConfigDataSource(), stats.configDataSource);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }
}
