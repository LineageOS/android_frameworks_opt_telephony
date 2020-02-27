/*
 * Copyright 2020 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;

import android.telephony.RadioAccessFamily;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
public class RadioAccessFamilyTest extends TelephonyTest {
    @Test
    @SmallTest
    public void testCompareSameFamily() throws Exception {
        // same family same number results in no clear winner
        assertEquals(0, RadioAccessFamily.compare(
                    TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA,
                    TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA));

        // same family, return the one with more total bits, in this case RHS,
        // so compare should be negative.
        assertTrue(0 > RadioAccessFamily.compare(
                TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA,
                TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA
                        | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA));
    }

    @Test
    @SmallTest
    public void testComparedGreatestUnique() throws Exception {
        // Because LHS supports a unique higher-generation RAT, prefer that to a large list of
        // older RATs. Since RHS is greater, compare should be positive.
        assertTrue(0 < RadioAccessFamily.compare(
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA,
                TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                        | TelephonyManager.NETWORK_TYPE_BITMASK_HSUPA
                        | TelephonyManager.NETWORK_TYPE_BITMASK_HSDPA
                        | TelephonyManager.NETWORK_TYPE_BITMASK_HSPA));
    }
}
