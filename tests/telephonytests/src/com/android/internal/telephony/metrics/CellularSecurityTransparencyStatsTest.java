/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.telephony.CellularIdentifierDisclosure;

import org.junit.Before;
import org.junit.Test;

public class CellularSecurityTransparencyStatsTest {

    private CellularSecurityTransparencyStats mCellularSecurityStats;

    @Before
    public void setUp() throws Exception {
        mCellularSecurityStats = spy(new CellularSecurityTransparencyStats());
    }

    @Test
    public void testLogIdentifierDisclosure_NullSimPlmn() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI, "123-456", true);

        mCellularSecurityStats.logIdentifierDisclosure(disclosure, null, null, true);

        verify(mCellularSecurityStats).writeIdentifierDisclosure(-1, -1, 123, 456,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, true,
                true);
    }

    @Test
    public void testLogIdentifierDisclosure_badSimPlmn() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI, "123-456", true);

        mCellularSecurityStats.logIdentifierDisclosure(disclosure, "INCORRECTLY", "FORMATTED",
                true);

        verify(mCellularSecurityStats).writeIdentifierDisclosure(-1, -1, 123, 456,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, true,
                true);
    }

    @Test
    public void testLogIdentifierDisclosure_badDisclosurePlmn() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI, "INCORRECT", true);

        mCellularSecurityStats.logIdentifierDisclosure(disclosure, "123", "456", true);

        verify(mCellularSecurityStats).writeIdentifierDisclosure(123, 456, -1, -1,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, true,
                true);
    }

    @Test
    public void testLogIdentifierDisclosure_expectedGoodData() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI, "999-666", true);

        mCellularSecurityStats.logIdentifierDisclosure(disclosure, "123", "456", true);

        verify(mCellularSecurityStats).writeIdentifierDisclosure(123, 456, 999, 666,
                CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, true,
                true);

    }
}
