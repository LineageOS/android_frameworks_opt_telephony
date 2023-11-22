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
import static android.telephony.CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_IDENTITY_RESPONSE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.hardware.radio.network.CellularIdentifier;
import android.hardware.radio.network.NasProtocolMessage;
import android.os.Parcel;

import com.android.internal.telephony.RILUtils;

import org.junit.Test;

public class CellularIdentifierDisclosureTest {

    @Test
    public void testEqualsAndHash() {
        android.telephony.CellularIdentifierDisclosure disclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);

        android.telephony.CellularIdentifierDisclosure anotherDislcosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);
        assertEquals(disclosure, anotherDislcosure);
        assertEquals(disclosure.hashCode(), anotherDislcosure.hashCode());
    }

    @Test
    public void testNotEqualsAndHash() {
        android.telephony.CellularIdentifierDisclosure imsiDisclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);

        android.telephony.CellularIdentifierDisclosure imeiDisclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMEI,
                        "001001",
                        false);

        assertNotEquals(imsiDisclosure, imeiDisclosure);
        assertNotEquals(imsiDisclosure.hashCode(), imeiDisclosure.hashCode());
    }

    @Test
    public void testGetters() {
        android.telephony.CellularIdentifierDisclosure disclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);

        assertEquals(NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST, disclosure.getNasProtocolMessage());
        assertEquals(CELLULAR_IDENTIFIER_IMSI, disclosure.getCellularIdentifier());
        assertEquals(false, disclosure.isEmergency());
        assertEquals("001001", disclosure.getPlmn());
    }

    @Test
    public void testParcel() {
        android.telephony.CellularIdentifierDisclosure disclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);

        Parcel p = Parcel.obtain();
        disclosure.writeToParcel(p, 0);
        p.setDataPosition(0);

        android.telephony.CellularIdentifierDisclosure fromParcel =
                android.telephony.CellularIdentifierDisclosure.CREATOR.createFromParcel(p);
        assertEquals(disclosure, fromParcel);
    }

    @Test
    public void testConvertCellularIdentifierDisclosure() {
        android.hardware.radio.network.CellularIdentifierDisclosure aidlDisclsoure =
                new android.hardware.radio.network.CellularIdentifierDisclosure();
        aidlDisclsoure.plmn = "001001";
        aidlDisclsoure.identifier = NasProtocolMessage.IDENTITY_RESPONSE;
        aidlDisclsoure.protocolMessage = CellularIdentifier.IMEI;
        aidlDisclsoure.isEmergency = true;

        android.telephony.CellularIdentifierDisclosure expectedDisclosure =
                new android.telephony.CellularIdentifierDisclosure(
                        NAS_PROTOCOL_MESSAGE_IDENTITY_RESPONSE,
                        CELLULAR_IDENTIFIER_IMEI,
                        "001001",
                        true);

        assertEquals(
                expectedDisclosure, RILUtils.convertCellularIdentifierDisclosure(aidlDisclsoure));
    }
}
