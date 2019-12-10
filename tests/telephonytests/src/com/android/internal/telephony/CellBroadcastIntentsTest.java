/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static junit.framework.TestCase.assertEquals;

import android.content.Intent;
import android.os.UserHandle;
import android.telephony.CellBroadcastIntents;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CellBroadcastIntentsTest extends TelephonyTest {

    private static final String TEST_ACTION = "test_action";

    @Before
    public void setUp() throws Exception {
        super.setUp("CellBroadcastIntentsTest");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Verifies that the intent we pass into CellBroadcastIntents
     * .sendOrderedBroadcastForBackgroundReceivers
     * is not modified within the function.
     */
    @Test
    public void testIntentIsNotMutated() {
        Intent originalIntent = new Intent(TEST_ACTION);
        Intent intentToPass = new Intent(TEST_ACTION);
        CellBroadcastIntents.sendOrderedBroadcastForBackgroundReceivers(
                InstrumentationRegistry.getContext(), UserHandle.ALL, intentToPass,
                null, null, null, null, 0, null, null);
        assertEquals(originalIntent.getFlags(), intentToPass.getFlags());
    }
}
