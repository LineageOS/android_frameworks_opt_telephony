/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static org.mockito.Mockito.when;

import android.telephony.CallQuality;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SignalStrength;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.nano.TelephonyProto.TelephonyCallSession.Event.CallQualitySummary;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CallQualityMetricsTest extends TelephonyTest {

    private CallQualityMetrics mCallQualityMetrics;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mCallQualityMetrics = new CallQualityMetrics(mPhone);

        // the ImsPhone does not return a ServiceStateTracker, so CallQualityMetrics gets the
        // default phone from the ImsPhone and uses that to get the ServiceStateTracker, therefore
        // we need to mock the default phone as well.
        when(mPhone.getDefaultPhone()).thenReturn(mPhone);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private CallQuality constructCallQuality(int dlQuality, int ulQuality, int durationMs) {
        return new CallQuality(
                dlQuality,
                ulQuality,
                durationMs,
                0, 0, 0, 0, 0, 0, 0, 0); // packets, jitter and codec (okay to ignore for testing)
    }

    /**
     * Verify that good/bad quality and total duration stats are correct.
     */
    @Test
    public void testTotalDurations() {
        // Call quality in the following sequence:
        //
        // DL: GOOD       GOOD      BAD
        // UL: GOOD       BAD       GOOD
        // |----------|----------|--------|
        // 0          5          10       14
        //
        // 0s = Start of call. Assumed to be good quality
        // 5s = Switches to UL bad quality
        // 10s = Switches to UL good quality, DL bad quality
        // 14s = End of call. Switches to UL bad quality, DL good quality
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        CallQuality cq3 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 14000);

        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq2);
        mCallQualityMetrics.saveCallQuality(cq3);

        // verify UL quality durations
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(9, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(5, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, dlSummary.totalDurationWithQualityInformationInSeconds);

        // verify DL quality durations
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(5, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(9, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, ulSummary.totalDurationWithQualityInformationInSeconds);
    }

    /**
     * Verify that good/bad quality and total duration stats are correct.
     *
     * Similar to testTotalDurations, but getCallQualitySummaryUl/Dl will be called multiple times,
     * so verify that it continues to work after the first call.
     */
    @Test
    public void testTotalDurations_MultipleChecks() {
        // Call quality in the following sequence:
        //
        // DL: GOOD       GOOD      BAD
        // UL: GOOD       BAD       GOOD
        // |----------|----------|--------|
        // 0          5          10       14
        //
        // 0s = Start of call. Assumed to be good quality
        // 5s = Switches to UL bad quality
        // 10s = Switches to UL good quality, DL bad quality
        // 14s = End of call. Switches to UL bad quality, DL good quality
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        CallQuality cq3 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 14000);

        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq2);
        mCallQualityMetrics.saveCallQuality(cq3);

        // verify UL quality durations
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(9, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(5, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, dlSummary.totalDurationWithQualityInformationInSeconds);

        // verify DL quality durations
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(5, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(9, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, ulSummary.totalDurationWithQualityInformationInSeconds);

        // verify UL quality durations
        dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(9, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(5, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, dlSummary.totalDurationWithQualityInformationInSeconds);

        // verify DL quality durations
        ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(5, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(9, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, ulSummary.totalDurationWithQualityInformationInSeconds);
    }


    /**
     * Verify that good/bad quality and total duration stats are correct.
     *
     * Similar to testTotalDurations but we report the call quality out of order.
     */
    @Test
    public void testTotalDurations_ReportedOutOfOrder() {
        // Call quality in the following sequence:
        //
        // DL: GOOD       GOOD      BAD
        // UL: GOOD       BAD       GOOD
        // |----------|----------|--------|
        // 0          5          10       14
        //
        // 0s = Start of call. Assumed to be good quality
        // 5s = Switches to UL bad quality
        // 10s = Switches to UL good quality, DL bad quality
        // 14s = End of call. Switches to UL bad quality, DL good quality
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        CallQuality cq3 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 14000);

        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq3);
        mCallQualityMetrics.saveCallQuality(cq2);

        // verify UL quality durations
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(9, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(5, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, dlSummary.totalDurationWithQualityInformationInSeconds);

        // verify DL quality durations
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(5, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(9, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(14, ulSummary.totalDurationWithQualityInformationInSeconds);
    }

    /**
     * Verify that a new CallQualityMetrics object is able to return empty summaries if no
     * CallQuality is reported for the duration of a call.
     */
    @Test
    public void testNoQualityReported() {
        // getting the summary for a new CallQualityMetrics object should not fail, and all
        // durations should be 0
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(0, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(0, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(0, dlSummary.totalDurationWithQualityInformationInSeconds);
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(0, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(0, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(0, ulSummary.totalDurationWithQualityInformationInSeconds);
    }

    /**
     * Verify that if either UL or DL call quality level is not available, the CallQuality update is
     * ignored.
     */
    @Test
    public void testNotAvailableIsIgnored() {
        // CallQuality updates from the IMS service with CALL_QUALITY_NOT_AVAILABLE should be
        // ignored
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_NOT_AVAILABLE,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_NOT_AVAILABLE, 10000);
        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq2);

        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(0, dlSummary.totalGoodQualityDurationInSeconds);
        assertEquals(0, dlSummary.totalBadQualityDurationInSeconds);
        assertEquals(0, dlSummary.totalDurationWithQualityInformationInSeconds);
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(0, ulSummary.totalGoodQualityDurationInSeconds);
        assertEquals(0, ulSummary.totalBadQualityDurationInSeconds);
        assertEquals(0, ulSummary.totalDurationWithQualityInformationInSeconds);
    }

    /**
     * Test that the best and worst SignalStrength (currently just LTE RSSNR) is correctly kept
     * track of. CallQualityMetrics should log the best and worst SS for good and bad quality, but
     * this just tests for good quality since the logic is the same.
     */
    @Test
    public void testBestAndWorstSs() {
        // save good quality with high rssnr
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_EXCELLENT, 5000);
        int rssnr1 = 30;
        // ignore everything except rssnr
        CellSignalStrengthLte lteSs1 = new CellSignalStrengthLte(0, 0, 0, rssnr1, 0, 0);
        SignalStrength ss1 = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                lteSs1,
                new CellSignalStrengthNr());
        when(mSST.getSignalStrength()).thenReturn(ss1);
        mCallQualityMetrics.saveCallQuality(cq1);

        // save good quality with low rssnr
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        int rssnr2 = -20;
        // ignore everything except rssnr
        CellSignalStrengthLte lteSs2 = new CellSignalStrengthLte(0, 0, 0, rssnr2, 0, 0);
        SignalStrength ss2 = new SignalStrength(
                new CellSignalStrengthCdma(),
                new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(),
                new CellSignalStrengthTdscdma(),
                lteSs2,
                new CellSignalStrengthNr());
        when(mSST.getSignalStrength()).thenReturn(ss2);
        mCallQualityMetrics.saveCallQuality(cq1);

        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(rssnr1, dlSummary.bestSsWithGoodQuality.lteSnr);
        assertEquals(rssnr2, dlSummary.worstSsWithGoodQuality.lteSnr);
    }

    /**
     * Verifies that the snapshot of the end (the last reported call quality) is correct.
     * Currently this just checks the duration since the logic is all the same and it doesn't seem
     * likely that one field would be preserved and others would be lost.
     */
    @Test
    public void testSnapshotOfEndDuration() {
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        CallQuality cq3 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 14000);

        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq2);
        mCallQualityMetrics.saveCallQuality(cq3);

        // verify snapshot of end
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(14, dlSummary.snapshotOfEnd.durationInSeconds);
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(14, ulSummary.snapshotOfEnd.durationInSeconds);
    }

    /**
     * Verifies that the snapshot of the end (the last reported call quality) is correct.
     * Currently this just checks the duration since the logic is all the same and it doesn't seem
     * likely that one field would be preserved and others would be lost.
     *
     * Similar to testSnapshotOfEndDuration but we report the call quality out of order
     */
    @Test
    public void testSnapshotOfEndDuration_ReportedOutOfOrder() {
        CallQuality cq1 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 5000);
        CallQuality cq2 = constructCallQuality(CallQuality.CALL_QUALITY_BAD,
                CallQuality.CALL_QUALITY_EXCELLENT, 10000);
        CallQuality cq3 = constructCallQuality(CallQuality.CALL_QUALITY_EXCELLENT,
                CallQuality.CALL_QUALITY_BAD, 14000);

        mCallQualityMetrics.saveCallQuality(cq1);
        mCallQualityMetrics.saveCallQuality(cq3);
        mCallQualityMetrics.saveCallQuality(cq2);

        // verify snapshot of end
        CallQualitySummary dlSummary = mCallQualityMetrics.getCallQualitySummaryDl();
        assertEquals(14, dlSummary.snapshotOfEnd.durationInSeconds);
        CallQualitySummary ulSummary = mCallQualityMetrics.getCallQualitySummaryUl();
        assertEquals(14, ulSummary.snapshotOfEnd.durationInSeconds);
    }
}
