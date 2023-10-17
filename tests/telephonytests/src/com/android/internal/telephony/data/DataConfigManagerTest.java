/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SignalStrength;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataConfigManagerTest extends TelephonyTest {
    private DataConfigManager mDataConfigManagerUT;
    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        logd("DataConfigManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mBundle = mContextFixture.getCarrierConfigBundle();
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(mBundle);
        mDataConfigManagerUT = new DataConfigManager(mPhone, Looper.myLooper());
        logd("DataConfigManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        mDataConfigManagerUT = null;
        super.tearDown();
    }

    @Test
    public void testParseSlidingWindowCounterThreshold() {
        long defaultTimeWindow = 0;
        int defaultOccurrence = 2;
        DataConfigManager.EventFrequency defaultValue = new DataConfigManager.EventFrequency(0, 2);

        DataConfigManager.EventFrequency normal =
                mDataConfigManagerUT.parseSlidingWindowCounterThreshold(Long.MAX_VALUE + ","
                        + Integer.MAX_VALUE, defaultTimeWindow, defaultOccurrence);
        DataConfigManager.EventFrequency expected =
                new DataConfigManager.EventFrequency(Long.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(normal.timeWindow).isEqualTo(expected.timeWindow);
        assertThat(normal.eventNumOccurrence).isEqualTo(expected.eventNumOccurrence);

        //allow " time , occurrences ," as we can infer even though format is not strictly valid
        DataConfigManager.EventFrequency invalidFormat = mDataConfigManagerUT
                .parseSlidingWindowCounterThreshold(
                        Long.MAX_VALUE + "," + Integer.MAX_VALUE + " ,",
                        defaultTimeWindow, defaultOccurrence);
        assertThat(invalidFormat.timeWindow).isEqualTo(Long.MAX_VALUE);
        assertThat(invalidFormat.eventNumOccurrence).isEqualTo(Integer.MAX_VALUE);

        DataConfigManager.EventFrequency invalidRange = mDataConfigManagerUT
                .parseSlidingWindowCounterThreshold(
                        Long.MAX_VALUE + "," + Long.MAX_VALUE, defaultTimeWindow,
                        defaultOccurrence);
        assertThat(invalidRange.timeWindow).isEqualTo(defaultValue.timeWindow);
        assertThat(invalidRange.eventNumOccurrence).isEqualTo(defaultValue.eventNumOccurrence);

        DataConfigManager.EventFrequency invalidFormat2 = mDataConfigManagerUT
                .parseSlidingWindowCounterThreshold("", defaultTimeWindow, defaultOccurrence);
        assertThat(invalidFormat2.timeWindow).isEqualTo(defaultValue.timeWindow);
        assertThat(invalidFormat2.eventNumOccurrence).isEqualTo(defaultValue.eventNumOccurrence);

        DataConfigManager.EventFrequency invalidFormat3 = mDataConfigManagerUT
                .parseSlidingWindowCounterThreshold(null, defaultTimeWindow, defaultOccurrence);
        assertThat(invalidFormat3.timeWindow).isEqualTo(defaultValue.timeWindow);
        assertThat(invalidFormat3.eventNumOccurrence).isEqualTo(defaultValue.eventNumOccurrence);
    }

    @Test
    public void testParseAutoDataSwitchScoreTable() {
        SignalStrength signalStrength = mock(SignalStrength.class);
        int tolerance = 100;
        PersistableBundle auto_data_switch_rat_signal_score_string_bundle = new PersistableBundle();
        auto_data_switch_rat_signal_score_string_bundle.putIntArray(
                "NR_NSA_MMWAVE", new int[]{10000, 10227, 12488, 15017, 15278});
        auto_data_switch_rat_signal_score_string_bundle.putIntArray(
                "LTE", new int[]{-3731, 5965, 8618, 11179, 13384});
        mBundle.putPersistableBundle(
                CarrierConfigManager.KEY_AUTO_DATA_SWITCH_RAT_SIGNAL_SCORE_BUNDLE,
                auto_data_switch_rat_signal_score_string_bundle);

        mContextFixture.putIntResource(com.android.internal.R.integer
                .auto_data_switch_score_tolerance, tolerance);

        mDataConfigManagerUT.sendEmptyMessage(1/*EVENT_CARRIER_CONFIG_CHANGED*/);
        processAllMessages();

        assertThat(mDataConfigManagerUT.getAutoDataSwitchScoreTolerance()).isEqualTo(tolerance);

        // Verify NSA_MMWAVE
        doReturn(SignalStrength.SIGNAL_STRENGTH_POOR).when(signalStrength).getLevel();
        assertThat(mDataConfigManagerUT.getAutoDataSwitchScore(new TelephonyDisplayInfo(
                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED, false/*isRoaming*/),
                signalStrength)).isEqualTo(10227);
        // Verify if entry contains any invalid negative scores, should yield -1.
        doReturn(SignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN).when(signalStrength).getLevel();
        assertThat(mDataConfigManagerUT.getAutoDataSwitchScore(new TelephonyDisplayInfo(
                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false/*isRoaming*/),
                signalStrength))
                .isEqualTo(-1/*INVALID_AUTO_DATA_SWITCH_SCORE*/);
        // Verify non-existent entry should yield -1
        doReturn(SignalStrength.SIGNAL_STRENGTH_POOR).when(signalStrength).getLevel();
        assertThat(mDataConfigManagerUT.getAutoDataSwitchScore(new TelephonyDisplayInfo(
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false/*isRoaming*/),
                signalStrength))
                .isEqualTo(-1/*INVALID_AUTO_DATA_SWITCH_SCORE*/);
    }
}
