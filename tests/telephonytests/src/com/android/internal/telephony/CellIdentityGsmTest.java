/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.telephony.CellIdentityGsm;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Collections;

/** Unit tests for {@link CellIdentityGsm}. */
public class CellIdentityGsmTest extends AndroidTestCase {
    private static final String LOG_TAG = "CellIdentityGsmTest";

    // Location Area Code ranges from 0 to 65535.
    private static final int LAC = 65535;
    // GSM Cell Identity ranges from 0 to 65535.
    private static final int CID = 65535;
    // GSM Absolute RF Channel Number ranges from 0 to 65535.
    private static final int ARFCN = 65535;
    // Base Station Identity Code ranges from 0 to 63.
    private static final int BSIC = 63;
    private static final int MCC = 120;
    private static final int MNC = 260;
    private static final String MCC_STR = "120";
    private static final String MNC_STR = "260";
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";


    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT, Collections.emptyList());

        assertEquals(LAC, ci.getLac());
        assertEquals(CID, ci.getCid());
        assertEquals(ARFCN, ci.getArfcn());
        assertEquals(ARFCN, ci.getChannelNumber());
        assertEquals(BSIC, ci.getBsic());
        assertEquals(MCC, ci.getMcc());
        assertEquals(MNC, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccString());
        assertEquals(MNC_STR, ci.getMncString());
        assertEquals(MCC_STR + MNC_STR, ci.getMobileNetworkOperator());
        assertEquals(ALPHA_LONG, ci.getOperatorAlphaLong());
        assertEquals(ALPHA_SHORT, ci.getOperatorAlphaShort());

        String globalCi = MCC_STR + MNC_STR + Integer.toString(LAC, 16)
                + Integer.toString(CID, 16);
        assertTrue(globalCi.equals(ci.getGlobalCellId()));
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        final String mncWithThreeDigit = "061";
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, MCC_STR, mncWithThreeDigit,
                        ALPHA_LONG, ALPHA_SHORT, Collections.emptyList());

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccString());
        assertEquals(mncWithThreeDigit, ci.getMncString());
        assertEquals(MCC_STR + mncWithThreeDigit, ci.getMobileNetworkOperator());

        String globalCi = MCC_STR + mncWithThreeDigit + Integer.toString(LAC, 16)
                + Integer.toString(CID, 16);
        assertEquals(globalCi, ci.getGlobalCellId());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        final String mncWithTwoDigit = "61";
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, MCC_STR, mncWithTwoDigit,
                        ALPHA_LONG, ALPHA_SHORT, Collections.emptyList());

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccString());
        assertEquals(mncWithTwoDigit, ci.getMncString());
        assertEquals(MCC_STR + mncWithTwoDigit, ci.getMobileNetworkOperator());

        String globalCi = MCC_STR + mncWithTwoDigit + Integer.toString(LAC, 16)
                + Integer.toString(CID, 16);
        assertEquals(globalCi, ci.getGlobalCellId());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, null, ALPHA_LONG, ALPHA_SHORT,
                        Collections.emptyList());

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertNull(ci.getMccString());
        assertNull(ci.getMncString());
        assertNull(ci.getMobileNetworkOperator());
        assertNull(ci.getGlobalCellId());

        ci = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, MCC_STR, null, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertEquals(MCC, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccString());
        assertNull(ci.getMncString());
        assertNull(ci.getMobileNetworkOperator());
        assertNull(ci.getGlobalCellId());

        ci = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertEquals(MNC, ci.getMnc());
        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(MNC_STR, ci.getMncString());
        assertNull(ci.getMccString());
        assertNull(ci.getMobileNetworkOperator());
        assertNull(ci.getGlobalCellId());

        ci = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, "", "", ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertNull(ci.getMccString());
        assertNull(ci.getMncString());
        assertNull(ci.getMobileNetworkOperator());
        assertNull(ci.getGlobalCellId());
    }

    @SmallTest
    public void testEquals() {
        CellIdentityGsm ciA = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        CellIdentityGsm ciB = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC,  MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        ciB = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityGsm(
                LAC, CID, ARFCN, BSIC, MCC_STR,  MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        ciB = new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());

        assertFalse(ciA.equals(ciB));
    }

    @SmallTest
    public void testParcel() {
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT, Collections.emptyList());

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityGsm newCi = CellIdentityGsm.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testParcelWithUnknowMccMnc() {
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, null, ALPHA_LONG, ALPHA_SHORT,
                        Collections.emptyList());

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityGsm newCi = CellIdentityGsm.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testParcelWithInvalidMccMnc() {
        final String invalidMcc = "randomStuff";
        final String invalidMnc = "randomStuff";
        CellIdentityGsm ci =
                new CellIdentityGsm(LAC, CID, ARFCN, BSIC, null, null, ALPHA_LONG, ALPHA_SHORT,
                        Collections.emptyList());

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityGsm newCi = CellIdentityGsm.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testgetGlobalCellId() {
        CellIdentityGsm ci = new CellIdentityGsm(
                LAC + 1, CID, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        assertNull(ci.getGlobalCellId());

        ci = new CellIdentityGsm(
                LAC, CID + 1, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        assertNull(ci.getGlobalCellId());

        ci = new CellIdentityGsm(
                LAC, -1, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        assertNull(ci.getGlobalCellId());

        // Test id with one digit and corresponding zero padding
        int cid = 1;
        ci = new CellIdentityGsm(
                LAC, cid, ARFCN, BSIC, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT,
                Collections.emptyList());
        String globalCi = MCC_STR + MNC_STR + Integer.toString(LAC, 16)
                + "000" + Integer.toString(cid, 16);
        assertEquals(globalCi, ci.getGlobalCellId());
    }
}
