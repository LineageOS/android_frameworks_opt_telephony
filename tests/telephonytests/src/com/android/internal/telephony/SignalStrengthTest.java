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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthTdscdma;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SignalStrength;

import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IpSecConfig}. */
@SmallTest
@RunWith(JUnit4.class)
public class SignalStrengthTest {

    @Test
    public void testDefaults() throws Exception {
        SignalStrength s = new SignalStrength();
        assertEquals(CellInfo.UNAVAILABLE, s.getCdmaDbm());
        assertEquals(CellInfo.UNAVAILABLE, s.getCdmaEcio());
        assertEquals(CellInfo.UNAVAILABLE, s.getEvdoDbm());
        assertEquals(CellInfo.UNAVAILABLE, s.getEvdoEcio());
        assertEquals(CellInfo.UNAVAILABLE, s.getEvdoSnr());
        assertEquals(CellInfo.UNAVAILABLE, s.getGsmBitErrorRate());
        // getGsmSignalStrength is an oddball because internally it actually returns an AsuLevel
        // rather than a dBm value. For backwards compatibility reasons, this is left as the
        // RSSI ASU value [0-31], 99.
        assertEquals(99, s.getGsmSignalStrength());
        assertEquals(true, s.isGsm());
    }

    @Test
    public void testParcelUnparcel() throws Exception {
        assertParcelingIsLossless(new SignalStrength());

        SignalStrength s = new SignalStrength(
                // Accepts HAL inputs (neg of actual) directly and -1 as invalid
                new CellSignalStrengthCdma(40, 5, 45, 3, 5),
                // Accepts ASU values or UNAVAILABLE
                new CellSignalStrengthGsm(2, 4, 68),
                // Accepts ASU or UNAVAILABLE
                new CellSignalStrengthWcdma(12, 4, 22, 18),
                // Accepts ASU values or UNAVAILABLE
                new CellSignalStrengthTdscdma(13, 2, 34),
                // Accepts actual values (HAL values must be converted, except RSSI) or UNAVAILABLE
                new CellSignalStrengthLte(-85, -91, -6, -10, 12, 1));
        assertParcelingIsLossless(s);
    }

    private void assertParcelingIsLossless(SignalStrength ssi) throws Exception {
        Parcel p = Parcel.obtain();
        ssi.writeToParcel(p, 0);
        p.setDataPosition(0);
        SignalStrength sso = SignalStrength.CREATOR.createFromParcel(p);
        assertTrue(sso.equals(ssi));
    }
}

