/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.security;

import static android.telephony.CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMEI;
import static android.telephony.CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI;
import static android.telephony.CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.CellularIdentifierDisclosure;

import org.junit.Test;

public class CellularIdentifierDisclosureTest {

    @Test
    public void testEqualsAndHash() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMSI, "001001", false);

        CellularIdentifierDisclosure anotherDislcosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMSI, "001001", false);
        assertEquals(disclosure, anotherDislcosure);
        assertEquals(disclosure.hashCode(), anotherDislcosure.hashCode());
    }

    @Test
    public void testNotEqualsAndHash() {
        CellularIdentifierDisclosure imsiDisclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMSI, "001001", false);

        CellularIdentifierDisclosure imeiDisclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMEI, "001001", false);

        assertNotEquals(imsiDisclosure, imeiDisclosure);
        assertNotEquals(imsiDisclosure.hashCode(), imeiDisclosure.hashCode());
    }

    @Test
    public void testGetters() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMSI, "001001", false);

        assertEquals(NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, disclosure.getNasProtocolMessage());
        assertEquals(CELLULAR_IDENTIFIER_IMSI, disclosure.getCellularIdentifier());
        assertEquals(false, disclosure.isEmergency());
        assertEquals("001001", disclosure.getPlmn());
    }

    @Test
    public void testParcel() {
        CellularIdentifierDisclosure disclosure = new CellularIdentifierDisclosure(
                NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, CELLULAR_IDENTIFIER_IMSI, "001001", false);

        Parcel p = Parcel.obtain();
        disclosure.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellularIdentifierDisclosure fromParcel =
                CellularIdentifierDisclosure.CREATOR.createFromParcel(p);
        assertEquals(disclosure, fromParcel);
    }
}
