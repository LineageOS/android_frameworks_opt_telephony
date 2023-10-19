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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.telephony.SatelliteProtoEnums;
import android.telephony.TelephonyProtoEnums;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteController;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteIncomingDatagram;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteOutgoingDatagram;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteProvision;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteSession;
import com.android.internal.telephony.nano.PersistAtomsProto.SatelliteSosMessageRecommender;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

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

        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteSessionMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteSessionParams param =
                new SatelliteStats.SatelliteSessionParams.Builder()
                        .setSatelliteServiceInitializationResult(
                                SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setSatelliteTechnology(SatelliteProtoEnums.NT_RADIO_TECHNOLOGY_PROPRIETARY)
                        .build();

        mSatelliteStats.onSatelliteSessionMetrics(param);

        ArgumentCaptor<SatelliteSession> captor =
                ArgumentCaptor.forClass(SatelliteSession.class);
        verify(mPersistAtomsStorage).addSatelliteSessionStats(captor.capture());
        SatelliteSession stats = captor.getValue();
        assertEquals(param.getSatelliteServiceInitializationResult(),
                stats.satelliteServiceInitializationResult);
        assertEquals(param.getSatelliteTechnology(), stats.satelliteTechnology);
        verifyNoMoreInteractions(mPersistAtomsStorage);
    }

    @Test
    public void onSatelliteIncomingDatagramMetrics_withAtoms() throws Exception {
        SatelliteStats.SatelliteIncomingDatagramParams param =
                new SatelliteStats.SatelliteIncomingDatagramParams.Builder()
                        .setResultCode(SatelliteProtoEnums.SATELLITE_ERROR_NONE)
                        .setDatagramSizeBytes(1 * 1024)
                        .setDatagramTransferTimeMillis(3 * 1000)
                        .build();

        mSatelliteStats.onSatelliteIncomingDatagramMetrics(param);

        ArgumentCaptor<SatelliteIncomingDatagram> captor =
                ArgumentCaptor.forClass(SatelliteIncomingDatagram.class);
        verify(mPersistAtomsStorage).addSatelliteIncomingDatagramStats(captor.capture());
        SatelliteIncomingDatagram stats = captor.getValue();
        assertEquals(param.getResultCode(), stats.resultCode);
        assertEquals(param.getDatagramSizeBytes(), stats.datagramSizeBytes);
        assertEquals(param.getDatagramTransferTimeMillis(), stats.datagramTransferTimeMillis);
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
}
