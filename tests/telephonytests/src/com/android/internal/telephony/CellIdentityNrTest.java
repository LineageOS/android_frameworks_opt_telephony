/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.test.AndroidTestCase;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CellIdentityNrTest extends AndroidTestCase {
    private static final String LOG_TAG = "CellIdentityNrTest";
    private static final String MCC_STR = "310";
    private static final String MNC_STR = "260";
    private static final String ANOTHER_MCC_STR = "134";
    private static final String ANOTHER_MNC_STR = "256";
    private static final String ALPHAL = "long operator name";
    private static final String ALPHAS = "lon";
    private static final int NRARFCN = 13456;
    private static final int PCI = 123;
    private static final int TAC = 32767;
    private static final int NCI = 8675309;
    private static final int[] BANDS = new int[] {
            AccessNetworkConstants.NgranBands.BAND_1,
            AccessNetworkConstants.NgranBands.BAND_2
    };

    @Test
    public void testGetMethod() {
        // GIVEN an instance of CellIdentityNr
        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI, ALPHAL, ALPHAS,
                        Collections.emptyList());

        // THEN the get method should return correct value
        assertThat(cellIdentityNr.getType()).isEqualTo(CellInfo.TYPE_NR);
        assertThat(cellIdentityNr.getNrarfcn()).isEqualTo(NRARFCN);
        assertThat(cellIdentityNr.getPci()).isEqualTo(PCI);
        assertThat(cellIdentityNr.getTac()).isEqualTo(TAC);
        assertThat(cellIdentityNr.getOperatorAlphaLong()).isEqualTo(ALPHAL);
        assertThat(cellIdentityNr.getOperatorAlphaShort()).isEqualTo(ALPHAS);
        assertThat(cellIdentityNr.getMccString()).isEqualTo(MCC_STR);
        assertThat(cellIdentityNr.getMncString()).isEqualTo(MNC_STR);
        assertThat(cellIdentityNr.getNci()).isEqualTo(NCI);

        String globalCi = MCC_STR + MNC_STR + "000" + Integer.toString(NCI, 16);
        assertEquals(globalCi, cellIdentityNr.getGlobalCellId());
    }

    @Test
    public void testEquals_sameParameters() {
        // GIVEN an instance of CellIdentityNr, and create another object with the same parameters
        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI,
                        ALPHAL, ALPHAS, Collections.emptyList());
        CellIdentityNr anotherCellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI,
                        ALPHAL, ALPHAS, Collections.emptyList());

        // THEN this two objects are equivalent
        assertThat(cellIdentityNr).isEqualTo(anotherCellIdentityNr);
    }

    @Test
    public void testEquals_differentParameters() {
        // GIVEN an instance of CellIdentityNr, and create another object with different parameters
        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI,
                        ALPHAL, ALPHAS, Collections.emptyList());
        CellIdentityNr anotherCellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI + 1,
                        ALPHAL, ALPHAS, Collections.emptyList());

        // THEN this two objects are different
        assertThat(cellIdentityNr).isNotEqualTo(anotherCellIdentityNr);
    }

    @Test
    public void testParcel() {
        // GIVEN an instance of CellIdentityNr
        CellIdentityNr cellIdentityNr =
                new CellIdentityNr(PCI, TAC, NRARFCN, BANDS, MCC_STR, MNC_STR, NCI,
                        ALPHAL, ALPHAS, Collections.emptyList());

        // WHEN write the object to parcel and create another object with that parcel
        Parcel parcel = Parcel.obtain();
        cellIdentityNr.writeToParcel(parcel, 0 /* type */);
        parcel.setDataPosition(0);
        CellIdentityNr anotherCellIdentityNr = CellIdentityNr.CREATOR.createFromParcel(parcel);

        // THEN the new object is equal to the old one
        assertThat(anotherCellIdentityNr).isEqualTo(cellIdentityNr);
        assertThat(anotherCellIdentityNr.getType()).isEqualTo(CellInfo.TYPE_NR);
        assertThat(anotherCellIdentityNr.getNrarfcn()).isEqualTo(NRARFCN);
        assertThat(anotherCellIdentityNr.getPci()).isEqualTo(PCI);
        assertThat(anotherCellIdentityNr.getTac()).isEqualTo(TAC);
        assertTrue(Arrays.equals(anotherCellIdentityNr.getBands(), BANDS));
        assertThat(anotherCellIdentityNr.getOperatorAlphaLong()).isEqualTo(ALPHAL);
        assertThat(anotherCellIdentityNr.getOperatorAlphaShort()).isEqualTo(ALPHAS);
        assertThat(anotherCellIdentityNr.getMccString()).isEqualTo(MCC_STR);
        assertThat(anotherCellIdentityNr.getMncString()).isEqualTo(MNC_STR);
        assertThat(anotherCellIdentityNr.getNci()).isEqualTo(NCI);
    }
}
