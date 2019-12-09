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
package com.android.internal.telephony;

import android.telephony.SignalThresholdInfo;

import junit.framework.TestCase;

import java.util.Arrays;

public class SignalThresholdInfoTest extends TestCase {
    private static final int HYSTERESIS_DB = 2;
    private static final int HYSTERESIS_MS = 30;
    private static final int[] SSRSRP_THRESHOLDS = new int[] {-30, 10, 45, 130};

    public void testSignalThresholdInfo() throws Exception {
        SignalThresholdInfo signalThresholdInfo = new SignalThresholdInfo(
                SignalThresholdInfo.SIGNAL_SSRSRP,
                HYSTERESIS_MS,
                HYSTERESIS_DB,
                SSRSRP_THRESHOLDS,
                false);

        assertEquals(SignalThresholdInfo.SIGNAL_SSRSRP,
                signalThresholdInfo.getSignalMeasurement());
        assertEquals(HYSTERESIS_MS, signalThresholdInfo.getHysteresisMs());
        assertEquals(HYSTERESIS_DB, signalThresholdInfo.getHysteresisDb());
        assertEquals(Arrays.toString(SSRSRP_THRESHOLDS), Arrays.toString(
                signalThresholdInfo.getThresholds()));
        assertFalse(signalThresholdInfo.isEnabled());
    }
}
