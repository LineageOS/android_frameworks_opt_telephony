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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.CellIdentityLte;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/** Unit tests for {@link CellIdentityLte}. */

public class CellIdentityLteTest extends AndroidTestCase {

    // Cell identity ranges from 0 to 268435456.
    private static final int CI = 268435456;
    // Physical cell id ranges from 0 to 503.
    private static final int PCI = 503;
    // Tracking area code ranges from 0 to 65535.
    private static final int TAC = 65535;
    // Absolute RF Channel Number ranges from 0 to 262140.
    private static final int EARFCN = 262140;
    private static final int MCC = 120;
    private static final int MNC = 260;
    private static final String MCC_STR = "120";
    private static final String MNC_STR = "260";
    private static final String ALPHA_LONG = "long";
    private static final String ALPHA_SHORT = "short";

    @SmallTest
    public void testDefaultConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(CI, ci.getCi());
        assertEquals(PCI, ci.getPci());
        assertEquals(TAC, ci.getTac());
        assertEquals(EARFCN, ci.getEarfcn());
        assertEquals(MCC, ci.getMcc());
        assertEquals(MNC, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(MNC_STR, ci.getMncStr());
        assertEquals(MCC_STR + MNC_STR, ci.getMobileNetworkOperator());
        assertEquals(ALPHA_LONG, ci.getOperatorAlphaLong());
        assertEquals(ALPHA_SHORT, ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testConstructorWithThreeDigitMnc() {
        final String mncWithThreeDigit = "061";
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, mncWithThreeDigit,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(mncWithThreeDigit, ci.getMncStr());
        assertEquals(MCC_STR + mncWithThreeDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithTwoDigitMnc() {
        final String mncWithTwoDigit = "61";
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, mncWithTwoDigit,
                        ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MCC, ci.getMcc());
        assertEquals(61, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(mncWithTwoDigit, ci.getMncStr());
        assertEquals(MCC_STR + mncWithTwoDigit, ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testConstructorWithEmptyMccMnc() {
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertNull(ci.getMccStr());
        assertNull(ci.getMncStr());
        assertNull(ci.getMobileNetworkOperator());

        ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, null, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MCC, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertNull(ci.getMncStr());
        assertNull(ci.getMobileNetworkOperator());

        ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertEquals(MNC, ci.getMnc());
        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(MNC_STR, ci.getMncStr());
        assertNull(ci.getMccStr());
        assertNull(ci.getMobileNetworkOperator());

        ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, "", "", ALPHA_LONG, ALPHA_SHORT);

        assertEquals(Integer.MAX_VALUE, ci.getMcc());
        assertEquals(Integer.MAX_VALUE, ci.getMnc());
        assertNull(ci.getMccStr());
        assertNull(ci.getMncStr());
        assertNull(ci.getMobileNetworkOperator());
    }

    @SmallTest
    public void testFormerConstructor() {
        CellIdentityLte ci =
                new CellIdentityLte(MCC, MNC, CI, PCI, TAC);

        assertEquals(CI, ci.getCi());
        assertEquals(PCI, ci.getPci());
        assertEquals(TAC, ci.getTac());
        assertEquals(Integer.MAX_VALUE, ci.getEarfcn());
        assertEquals(MCC, ci.getMcc());
        assertEquals(MNC, ci.getMnc());
        assertEquals(MCC_STR, ci.getMccStr());
        assertEquals(MNC_STR, ci.getMncStr());
        assertEquals(MCC_STR + MNC_STR, ci.getMobileNetworkOperator());
        assertNull(ci.getOperatorAlphaLong());
        assertNull(ci.getOperatorAlphaShort());
    }

    @SmallTest
    public void testEquals() {
        CellIdentityLte ciA = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);
        CellIdentityLte ciB = new CellIdentityLte(
                CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR, ALPHA_LONG, ALPHA_SHORT);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertTrue(ciA.equals(ciB));

        ciA = new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, null, ALPHA_LONG, ALPHA_SHORT);
        ciB = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, ALPHA_LONG, ALPHA_SHORT);

        assertFalse(ciA.equals(ciB));
    }

    @SmallTest
    public void testParcel() {
        CellIdentityLte ci =
                new CellIdentityLte(CI, PCI, TAC, EARFCN, MCC_STR, MNC_STR,
                        ALPHA_LONG, ALPHA_SHORT);

        Parcel p = Parcel.obtain();
        ci.writeToParcel(p, 0);
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testParcelWithUnknowMccMnc() {
        CellIdentityLte ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, null, null);

        Parcel p = Parcel.obtain();
        p.writeInt(CI);
        p.writeInt(PCI);
        p.writeInt(TAC);
        p.writeInt(EARFCN);
        p.writeString(String.valueOf(Integer.MAX_VALUE));
        p.writeString(String.valueOf(Integer.MAX_VALUE));
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }

    @SmallTest
    public void testParcelWithInvalidMccMnc() {
        final String invalidMcc = "randomStuff";
        final String invalidMnc = "randomStuff";
        CellIdentityLte ci = new CellIdentityLte(CI, PCI, TAC, EARFCN, null, null, null, null);

        Parcel p = Parcel.obtain();
        p.writeInt(CI);
        p.writeInt(PCI);
        p.writeInt(TAC);
        p.writeInt(EARFCN);
        p.writeString(invalidMcc);
        p.writeString(invalidMnc);
        p.setDataPosition(0);

        CellIdentityLte newCi = CellIdentityLte.CREATOR.createFromParcel(p);
        assertEquals(ci, newCi);
    }
}
