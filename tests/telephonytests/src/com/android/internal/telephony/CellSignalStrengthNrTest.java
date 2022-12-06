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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.hardware.radio.V1_6.NrSignalStrength;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrength;
import android.telephony.CellSignalStrengthNr;
import android.telephony.ServiceState;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CellSignalStrengthNrTest extends TelephonyTest {
    private static final int CSIRSRP = -123;
    private static final int CSIRSRQ = -11;
    private static final int ANOTHER_CSIRSRP = -111;
    private static final int ANOTHER_CSIRSRQ = -12;
    private static final int INVALID_CSIRSRP = Integer.MAX_VALUE;
    private static final int INVALID_SSRSRP = Integer.MAX_VALUE;
    private static final int CSISINR = 18;
    private static final int CSICQI_TABLE_INDEX = 1;
    private static final ArrayList<Byte> CSICQI_REPORT =
            new ArrayList<>(Arrays.asList((byte) 3, (byte) 2, (byte) 1));
    private static final int SSRSRP = -112;
    private static final int SSRSRQ = -13;
    private static final int SSSINR = 32;

    // Mocked classes
    ServiceState mSS;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mSS = mock(ServiceState.class);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private List<Integer> getCsiCqiList() {
        return CSICQI_REPORT.stream()
                .map(Byte::toUnsignedInt)
                .collect(Collectors.toList());
    }

    @Test
    public void testGetMethod() {
        // GIVEN an instance of CellSignalStrengthNr
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);

        // THEN the get method should return correct value
        assertThat(css.getCsiRsrp()).isEqualTo(CSIRSRP);
        assertThat(css.getCsiRsrq()).isEqualTo(CSIRSRQ);
        assertThat(css.getCsiSinr()).isEqualTo(CSISINR);
        assertThat(css.getCsiCqiTableIndex()).isEqualTo(CSICQI_TABLE_INDEX);
        assertThat(css.getCsiCqiReport()).isEqualTo(getCsiCqiList());
        assertThat(css.getSsRsrp()).isEqualTo(SSRSRP);
        assertThat(css.getSsRsrq()).isEqualTo(SSRSRQ);
        assertThat(css.getSsSinr()).isEqualTo(SSSINR);
        assertThat(css.getDbm()).isEqualTo(SSRSRP);
    }

    @Test
    public void testGetMethodWithHal() {
        // GIVEN an instance of NrSignalStrength with some positive values
        NrSignalStrength nrSignalStrength = new NrSignalStrength();
        nrSignalStrength.base.csiRsrp = -CSIRSRP;
        nrSignalStrength.base.csiRsrq = -CSIRSRQ;
        nrSignalStrength.base.csiSinr = CSISINR;
        nrSignalStrength.csiCqiTableIndex = CSICQI_TABLE_INDEX;
        nrSignalStrength.csiCqiReport = CSICQI_REPORT;
        nrSignalStrength.base.ssRsrp = -SSRSRP;
        nrSignalStrength.base.ssRsrq = -SSRSRQ;
        nrSignalStrength.base.ssSinr = SSSINR;

        // THEN the get method should return the correct value
        CellSignalStrengthNr css = RILUtils.convertHalNrSignalStrength(nrSignalStrength);
        assertThat(css.getCsiRsrp()).isEqualTo(CSIRSRP);
        assertThat(css.getCsiRsrq()).isEqualTo(CSIRSRQ);
        assertThat(css.getCsiSinr()).isEqualTo(CSISINR);
        assertThat(css.getCsiCqiTableIndex()).isEqualTo(CSICQI_TABLE_INDEX);
        assertThat(css.getCsiCqiReport()).isEqualTo(getCsiCqiList());
        assertThat(css.getSsRsrp()).isEqualTo(SSRSRP);
        assertThat(css.getSsRsrq()).isEqualTo(SSRSRQ);
        assertThat(css.getSsSinr()).isEqualTo(SSSINR);
        assertThat(css.getDbm()).isEqualTo(SSRSRP);
    }

    @Test
    public void testUnavailableValueWithHal() {
        // GIVEN an instance of NrSignalStrength
        NrSignalStrength nrSignalStrength = new NrSignalStrength();
        nrSignalStrength.base.csiRsrp = CellInfo.UNAVAILABLE;
        nrSignalStrength.base.csiRsrq = CellInfo.UNAVAILABLE;
        nrSignalStrength.base.csiSinr = CellInfo.UNAVAILABLE;
        nrSignalStrength.csiCqiTableIndex = CellInfo.UNAVAILABLE;
        nrSignalStrength.csiCqiReport = new ArrayList<Byte>();
        nrSignalStrength.base.ssRsrp = CellInfo.UNAVAILABLE;
        nrSignalStrength.base.ssRsrq = CellInfo.UNAVAILABLE;
        nrSignalStrength.base.ssSinr = CellInfo.UNAVAILABLE;

        // THEN the get method should return unavailable value
        CellSignalStrengthNr css = RILUtils.convertHalNrSignalStrength(nrSignalStrength);
        assertThat(css.getCsiRsrp()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getCsiRsrq()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getCsiSinr()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getCsiCqiTableIndex()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getCsiCqiReport()).isEqualTo(Collections.emptyList());
        assertThat(css.getSsRsrp()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getSsRsrq()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getSsSinr()).isEqualTo(CellInfo.UNAVAILABLE);
        assertThat(css.getDbm()).isEqualTo(CellInfo.UNAVAILABLE);
    }

    @Test
    public void testEquals_sameParameters() {
        // GIVEN an instance of CellSignalStrengthNr and another object with the same parameters
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);
        CellSignalStrengthNr anotherCss = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);

        // THEN this two objects are equivalent
        assertThat(css).isEqualTo(anotherCss);
    }

    @Test
    public void testEquals_differentParameters() {
        // GIVEN an instance of CellSignalStrengthNr and another object with some different
        // parameters
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);
        CellSignalStrengthNr anotherCss = new CellSignalStrengthNr(ANOTHER_CSIRSRP,
                ANOTHER_CSIRSRQ, CSISINR, CSICQI_TABLE_INDEX, CSICQI_REPORT,
                SSRSRP, SSRSRQ, SSSINR);

        // THEN this two objects are different
        assertThat(css).isNotEqualTo(anotherCss);
    }

    @Test
    public void testAusLevel_validValue() {
        // GIVEN an instance of CellSignalStrengthNr with valid csirsrp
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);

        // THEN the asu level is in range [0, 97]
        assertThat(css.getAsuLevel()).isIn(Range.range(0, BoundType.CLOSED, 97, BoundType.CLOSED));
    }

    @Test
    public void testAsuLevel_invalidValue() {
        // GIVEN an instance of CellSignalStrengthNr with invalid csirsrp
        CellSignalStrengthNr css = new CellSignalStrengthNr(INVALID_CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, INVALID_SSRSRP, SSRSRQ, SSSINR);

        // THEN the asu level is unknown
        assertThat(css.getAsuLevel()).isEqualTo(CellSignalStrengthNr.UNKNOWN_ASU_LEVEL);
    }

    @Test
    public void testSignalLevel_validValue() {
        for (int ssRsrp = -156; ssRsrp <= -31; ssRsrp++) {
            // GIVEN an instance of CellSignalStrengthNr with valid csirsrp
            CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                    CSICQI_TABLE_INDEX, CSICQI_REPORT, ssRsrp, SSRSRQ, SSSINR);

            // THEN the signal level is valid
            assertThat(css.getLevel()).isIn(Range.range(
                    CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN, BoundType.CLOSED,
                    CellSignalStrength.SIGNAL_STRENGTH_GREAT, BoundType.CLOSED));
        }
    }

    @Test
    public void testSignalLevel_invalidValue() {
        // GIVEN an instance of CellSignalStrengthNr with invalid csirsrp
        CellSignalStrengthNr css = new CellSignalStrengthNr(INVALID_CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);

        // THEN the signal level is unknown
        assertThat(css.getLevel()).isEqualTo(CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN);
    }

    @Test
    public void testParcel() {
        // GIVEN an instance of CellSignalStrengthNr
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR,
                CSICQI_TABLE_INDEX, CSICQI_REPORT, SSRSRP, SSRSRQ, SSSINR);

        // WHEN write the object to parcel and create another object with that parcel
        Parcel parcel = Parcel.obtain();
        css.writeToParcel(parcel, 0 /* type */);
        parcel.setDataPosition(0);
        CellSignalStrengthNr anotherCss = CellSignalStrengthNr.CREATOR.createFromParcel(parcel);

        // THEN the new object is equal to the old one
        assertThat(anotherCss).isEqualTo(css);
        assertThat(anotherCss.getCsiRsrp()).isEqualTo(CSIRSRP);
        assertThat(anotherCss.getCsiRsrq()).isEqualTo(CSIRSRQ);
        assertThat(anotherCss.getCsiSinr()).isEqualTo(CSISINR);
        assertThat(css.getCsiCqiTableIndex()).isEqualTo(CSICQI_TABLE_INDEX);
        assertThat(css.getCsiCqiReport()).isEqualTo(getCsiCqiList());
        assertThat(anotherCss.getSsRsrp()).isEqualTo(SSRSRP);
        assertThat(anotherCss.getSsRsrq()).isEqualTo(SSRSRQ);
        assertThat(anotherCss.getSsSinr()).isEqualTo(SSSINR);
    }

    @Test
    public void testLevel() {
        CellSignalStrengthNr css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR, SSRSRP,
                SSRSRQ, SSSINR);

        // No keys in the bundle - should use RSRP and default levels.
        css.updateLevel(null, null);
        assertEquals(0 /* NONE or UNKNOWN */, css.getLevel());

        doReturn(10).when(mSS).getArfcnRsrpBoost();
        // Add rsrp boost and level should change to 1 - POOR
        css.updateLevel(null, mSS);
        assertEquals(1 /* MODERATE */, css.getLevel());
    }

    @Test
    public void testSignalLevel_thresholdBoundaries() {
        int[] ssRsrpThresholds = {
                -110, /* SIGNAL_STRENGTH_POOR */
                -90,  /* SIGNAL_STRENGTH_MODERATE */
                -80,  /* SIGNAL_STRENGTH_GOOD */
                -65,  /* SIGNAL_STRENGTH_GREAT */
        };
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                CellSignalStrengthNr.USE_SSRSRP);
        bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY,
                ssRsrpThresholds);

        CellSignalStrengthNr css;

        css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR, ssRsrpThresholds[0], SSRSRQ,
                SSSINR);
        css.updateLevel(bundle, null);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_POOR, css.getLevel());

        css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR, ssRsrpThresholds[1], SSRSRQ,
                SSSINR);
        css.updateLevel(bundle, null);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_MODERATE, css.getLevel());

        css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR, ssRsrpThresholds[2], SSRSRQ,
                SSSINR);
        css.updateLevel(bundle, null);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_GOOD, css.getLevel());

        css = new CellSignalStrengthNr(CSIRSRP, CSIRSRQ, CSISINR, ssRsrpThresholds[3], SSRSRQ,
                SSSINR);
        css.updateLevel(bundle, null);
        assertEquals(CellSignalStrength.SIGNAL_STRENGTH_GREAT, css.getLevel());
    }
}
