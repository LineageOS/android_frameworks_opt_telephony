/*
 * Copyright 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class NitzSignalTest {

    /** Sample cases for equals() and hashCode(). Not exhaustive. */
    @Test
    public void testEqualsAndHashCode() {
        long receiptElapsedMillis1 = 1111;
        NitzData nitzData1 = NitzData.createForTests(0, 0, 1234, null);
        long ageMillis1 = 11;
        NitzSignal nitzSignal1 = new NitzSignal(receiptElapsedMillis1, nitzData1, ageMillis1);
        assertEquals(nitzSignal1, nitzSignal1);
        assertEquals(nitzSignal1.hashCode(), nitzSignal1.hashCode());

        NitzSignal nitzSignal1v2 = new NitzSignal(receiptElapsedMillis1, nitzData1, ageMillis1);
        assertEquals(nitzSignal1, nitzSignal1v2);
        assertEquals(nitzSignal1v2, nitzSignal1);
        assertEquals(nitzSignal1.hashCode(), nitzSignal1v2.hashCode());

        long receiptElapsedMillis2 = 2222;
        NitzData nitzData2 = NitzData.createForTests(0, 0, 2345, null);
        long ageMillis2 = 11;
        NitzSignal nitzSignal2 = new NitzSignal(receiptElapsedMillis2, nitzData2, ageMillis2);
        assertNotEquals(nitzSignal1, nitzSignal2);
        assertNotEquals(nitzSignal2, nitzSignal1);
    }

    @Test
    public void testGetAgeAdjustedRealtimeMillis_zeroAge() {
        NitzData nitzData = NitzData.createForTests(0, 0, 1234, null);
        long receiptElapsedRealtimeMillis = 1111;
        long ageMillis = 0;
        NitzSignal nitzSignal =
                new NitzSignal(receiptElapsedRealtimeMillis, nitzData, ageMillis);
        assertEquals(receiptElapsedRealtimeMillis,
                nitzSignal.getReceiptElapsedRealtimeMillis());
        assertEquals(ageMillis, nitzSignal.getAgeMillis());
        assertEquals(receiptElapsedRealtimeMillis - ageMillis,
                nitzSignal.getAgeAdjustedElapsedRealtimeMillis());
    }

    @Test
    public void testGetAgeAdjustedRealtimeMillis_withAge() {
        NitzData nitzData = NitzData.createForTests(0, 0, 1234, null);
        long receiptElapsedRealtimeMillis = 1111;
        long ageMillis = 5000;
        NitzSignal nitzSignal =
                new NitzSignal(receiptElapsedRealtimeMillis, nitzData, ageMillis);
        assertEquals(receiptElapsedRealtimeMillis,
                nitzSignal.getReceiptElapsedRealtimeMillis());
        assertEquals(ageMillis, nitzSignal.getAgeMillis());
        assertEquals(receiptElapsedRealtimeMillis - ageMillis,
                nitzSignal.getAgeAdjustedElapsedRealtimeMillis());
    }
}
