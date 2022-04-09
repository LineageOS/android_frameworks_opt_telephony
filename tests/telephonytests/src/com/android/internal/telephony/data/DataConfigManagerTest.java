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

import android.os.Looper;
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

    @Before
    public void setUp() throws Exception {
        logd("DataConfigManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
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
}
