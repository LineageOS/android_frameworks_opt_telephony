/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.internal.telephony.rcs;

import static org.junit.Assert.assertEquals;

import com.android.internal.telephony.RcsController;
import com.android.internal.telephony.TelephonyTest;

import org.junit.Before;
import org.junit.Test;

public class RcsControllerTest extends TelephonyTest {

    private RcsController mRcsController;

    @Before
    public void setUp() {
        mRcsController = new RcsController(mContext, null);
    }

    /**
     * TODO(sahinc): fix the test once there is an implementation in place
     */
    @Test
    public void testGetMessageCount() {
        assertEquals(1018, mRcsController.getMessageCount(0));
    }
}
