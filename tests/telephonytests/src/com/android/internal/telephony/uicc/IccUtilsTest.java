/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.List;

public class IccUtilsTest extends AndroidTestCase {
    private static final int NUM_FPLMN = 3;
    private static final List<String> FPLMNS_SAMPLE = Arrays.asList("123456", "12345", "54321");
    private static final int DATA_LENGTH = 12;

    @SmallTest
    public void testEncodeFplmns() {
        byte[] encodedFplmns = IccUtils.encodeFplmns(FPLMNS_SAMPLE, DATA_LENGTH);
        int numValidPlmns = 0;
        for (int i = 0; i < NUM_FPLMN; i++) {
            String parsed = IccUtils.bcdPlmnToString(encodedFplmns, i * IccUtils.FPLMN_BYTE_SIZE);
            assertEquals(FPLMNS_SAMPLE.get(i), parsed);
            // we count the valid (non empty) records and only increment if valid
            if (!TextUtils.isEmpty(parsed)) numValidPlmns++;
        }
        assertEquals(NUM_FPLMN, numValidPlmns);
    }
}
