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
package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PortUtilsTest extends TelephonyTest {

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        doReturn(IccSlotStatus.MultipleEnabledProfilesMode.NONE)
                .when(mUiccController).getSupportedMepMode(anyInt());
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testConvertToHalPortIndex() {
        assertEquals(0, PortUtils.convertToHalPortIndex(0, 0));
        doReturn(IccSlotStatus.MultipleEnabledProfilesMode.MEP_A1)
                .when(mUiccController).getSupportedMepMode(anyInt());
        assertEquals(1, PortUtils.convertToHalPortIndex(0, 0));
    }

    @Test
    public void testConvertFromHalPortIndex() {
        assertEquals(0, PortUtils.convertFromHalPortIndex(0, 1,
                IccCardStatus.CardState.CARDSTATE_PRESENT,
                IccSlotStatus.MultipleEnabledProfilesMode.MEP_A1));
        assertEquals(1, PortUtils.convertFromHalPortIndex(0, 1,
                IccCardStatus.CardState.CARDSTATE_PRESENT,
                IccSlotStatus.MultipleEnabledProfilesMode.MEP_B));
        assertEquals(1, PortUtils.convertFromHalPortIndex(0, 1,
                IccCardStatus.CardState.CARDSTATE_ABSENT,
                IccSlotStatus.MultipleEnabledProfilesMode.MEP_A1));
        doReturn(IccSlotStatus.MultipleEnabledProfilesMode.MEP_A1)
                .when(mUiccController).getSupportedMepMode(anyInt());
        assertEquals(0, PortUtils.convertFromHalPortIndex(0, 1,
                IccCardStatus.CardState.CARDSTATE_ABSENT,
                IccSlotStatus.MultipleEnabledProfilesMode.NONE));
    }
}
