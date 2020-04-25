/*
 * Copyright 2017 The Android Open Source Project
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

import android.os.Parcel;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CellIdentityTest extends AndroidTestCase {

    // Cell identity ranges from 0 to 268435456.
    private static final int CI = 268435456;
    // Physical cell id ranges from 0 to 503.
    private static final int PCI = 503;
    // Tracking area code ranges from 0 to 65535.
    private static final int TAC = 65535;
    // Absolute RF Channel Number ranges from 0 to 262140.
    private static final int EARFCN = 262140;
    private static final int[] BANDS = new int[] {1, 2};
    private static final int BANDWIDTH = 5000;  // kHz
    private static final int MCC = 120;
    private static final int MNC = 260;
    private static final String MCC_STR = "120";
    private static final String MNC_STR = "260";
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";
    private static final int CPID = 127;
    private static final int UARFCN = 16383;
    private static final int PSC = 511;

    // Network Id ranges from 0 to 65535.
    private static final int NETWORK_ID  = 65535;
    // CDMA System Id ranges from 0 to 32767
    private static final int SYSTEM_ID = 32767;
    // Base Station Id ranges from 0 to 65535
    private static final int BASESTATION_ID = 65535;
    // Longitude ranges from -2592000 to 2592000.
    private static final int LONGITUDE = 2592000;
    // Latitude ranges from -1296000 to 1296000.
    private static final int LATITUDE = 1296000;

    private static final String PLMN_INVALID_SHORT = "1234";
    private static final String PLMN_INVALID_LONG = "1234567";
    private static final String PLMN_INVALID_NON_NUM = "12a45b";
    private static final String PLMN_VALID = "12345";

    private static final int MAX_LAC = 65535;
    private static final int MAX_CID = 65535;
    private static final int MAX_ARFCN = 65535;
    private static final int MAX_BSIC = 63;

    @SmallTest
    public void testConstructCellIdentityGsm() {
        // Test values below zero (these must all be non-negative)
        CellIdentityGsm gsm = new CellIdentityGsm(-1, -1, -1, -1, null, null, null, null,
                Collections.emptyList());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getLac());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getCid());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getArfcn());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getBsic());

        // Test max values of LAC, CID, ARFCN, and BSIC
        gsm = new CellIdentityGsm(MAX_LAC, MAX_CID, MAX_ARFCN, MAX_BSIC, null, null, null, null,
                Collections.emptyList());
        assertEquals(MAX_LAC, gsm.getLac());
        assertEquals(MAX_CID, gsm.getCid());
        assertEquals(MAX_ARFCN, gsm.getArfcn());
        assertEquals(MAX_BSIC, gsm.getBsic());

        // Test max values + 1 of LAC, CID, ARFCN, and BSIC
        gsm = new CellIdentityGsm(
                MAX_LAC + 1, MAX_CID + 1, MAX_ARFCN + 1, MAX_BSIC + 1, null, null, null, null,
                Collections.emptyList());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getLac());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getCid());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getArfcn());
        assertEquals(CellInfo.UNAVAILABLE, gsm.getBsic());
    }


    @SmallTest
    public void testEquals() {
        CellIdentity ciA = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        CellIdentity ciB = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, null, null, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, null, null, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, null,
                ALPHA_LONG, ALPHA_SHORT, Collections.emptyList(), null);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, null, null, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);

        assertFalse(ciA.equals(ciB));
    }

    @SmallTest
    public void testParcel() {
        CellIdentity ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR,
                MNC_STR, ALPHA_LONG, ALPHA_SHORT, Collections.emptyList(), null);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentity newCi = CellIdentity.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);

        ci = new CellIdentityCdma(NETWORK_ID, SYSTEM_ID, BASESTATION_ID, LONGITUDE, LATITUDE,
                        ALPHA_LONG, ALPHA_SHORT);

        p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        newCi = CellIdentity.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testIsValidPlmn() {
        assertTrue(CellIdentity.isValidPlmn(PLMN_VALID));
    }

    @SmallTest
    public void testIsValidPlmnInvalidPlmns() {
        assertFalse(CellIdentity.isValidPlmn(PLMN_INVALID_SHORT));
        assertFalse(CellIdentity.isValidPlmn(PLMN_INVALID_LONG));
        assertFalse(CellIdentity.isValidPlmn(PLMN_INVALID_NON_NUM));
    }

    @SmallTest
    public void testIsSameCell() {
        int curCi = 268435455;
        CellIdentity ciA = new CellIdentityLte(
                curCi, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);
        CellIdentity ciB = new CellIdentityLte(
                curCi, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList(), null);
        CellIdentity ciC = new CellIdentityLte(
                curCi, PCI, TAC, EARFCN, BANDS, BANDWIDTH, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        CellIdentity ciD = new CellIdentityLte(
                -1, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        assertTrue(ciA.isSameCell(ciB));
        assertFalse(ciA.isSameCell(null));
        assertFalse(ciA.isSameCell(ciC));
        assertFalse(ciA.isSameCell(ciD));

        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(PCI, TAC, EARFCN, BANDS, MCC_STR, MNC_STR, curCi, ALPHA_LONG,
                ALPHA_SHORT, Collections.emptyList());
        assertFalse(ciA.isSameCell(cellIdentityNr));
    }

    @SmallTest
    public void testGetMccMncString() {
        List<CellIdentity> identities = new ArrayList<>(5);

        CellIdentityGsm gsm = new CellIdentityGsm(MAX_LAC, MAX_CID, MAX_ARFCN, MAX_BSIC,
                MCC_STR, MNC_STR, null, null, Collections.emptyList());
        identities.add(gsm);

        CellIdentityCdma cdma = new CellIdentityCdma(NETWORK_ID, SYSTEM_ID, BASESTATION_ID,
                LONGITUDE, LATITUDE, ALPHA_LONG, ALPHA_SHORT);
        identities.add(cdma);

        CellIdentity lte = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, BANDS, BANDWIDTH, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList(), null);
        identities.add(lte);

        CellIdentityWcdma wcdma  = new CellIdentityWcdma(MAX_LAC, MAX_CID, PSC, UARFCN, MCC_STR,
                MNC_STR, ALPHA_LONG, ALPHA_SHORT, Collections.emptyList(), null);
        identities.add(wcdma);

        CellIdentityTdscdma tdscdma = new CellIdentityTdscdma(MCC_STR, MNC_STR, MAX_LAC, MAX_CID,
                CPID, UARFCN, ALPHA_LONG, ALPHA_SHORT, Collections.emptyList(), null);
        identities.add(tdscdma);

        CellIdentityNr nr = new CellIdentityNr(PCI, TAC, EARFCN, BANDS, MCC_STR, MNC_STR, CI,
                ALPHA_LONG, ALPHA_SHORT, Collections.emptyList());
        identities.add(nr);

        for (CellIdentity identity : identities) {
            final String mccStr = identity.getMccString();
            final String mncStr = identity.getMncString();
            if (identity instanceof CellIdentityCdma) {
                assertNull(mccStr);
                assertNull(mncStr);
            } else {
                assertEquals(MCC_STR, mccStr);
                assertEquals(MNC_STR, mncStr);
            }
        }
    }
}
