/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.AccessNetworkConstants;
import android.telephony.SignalThresholdInfo;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class SignalThresholdInfoTest extends TestCase {
    private static final int HYSTERESIS_DB = 2;
    private static final int HYSTERESIS_MS = 30;
    private static final int[] SSRSRP_THRESHOLDS = new int[]{-120, -100, -80, -60};

    // Map of SignalMeasurementType to invalid thresholds edge values.
    // Each invalid value will be constructed with a thresholds array to test separately.
    private static final Map<Integer, List<Integer>> INVALID_THRESHOLDS_MAP = Map.of(
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
            List.of(SignalThresholdInfo.SIGNAL_RSSI_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSI_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP,
            List.of(SignalThresholdInfo.SIGNAL_RSCP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSCP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
            List.of(SignalThresholdInfo.SIGNAL_RSRP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSRP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
            List.of(SignalThresholdInfo.SIGNAL_RSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSRQ_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR,
            List.of(SignalThresholdInfo.SIGNAL_RSSNR_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_RSSNR_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
            List.of(SignalThresholdInfo.SIGNAL_SSRSRP_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRP_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
            List.of(SignalThresholdInfo.SIGNAL_SSRSRQ_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSRSRQ_MAX_VALUE + 1),
            SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR,
            List.of(SignalThresholdInfo.SIGNAL_SSSINR_MIN_VALUE - 1,
                    SignalThresholdInfo.SIGNAL_SSSINR_MAX_VALUE + 1)
    );

    // Map of RAN to allowed SignalMeasurementType set.
    // RAN/TYPE pair will be used to verify the validation of the combo
    private static final Map<Integer, Set<Integer>> VALID_RAN_TO_MEASUREMENT_TYPE_MAP = Map.of(
            AccessNetworkConstants.AccessNetworkType.GERAN,
            Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI),
            AccessNetworkConstants.AccessNetworkType.CDMA2000,
            Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI),
            AccessNetworkConstants.AccessNetworkType.UTRAN,
            Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP),
            AccessNetworkConstants.AccessNetworkType.EUTRAN,
            Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP,
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ,
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR),
            AccessNetworkConstants.AccessNetworkType.NGRAN,
            Set.of(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ,
                    SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR)
    );

    // Deliberately picking up the max/min value in each range to test the edge cases
    private final int[] mRssiThresholds = new int[]{-113, -103, -97, -51};
    private final int[] mRscpThresholds = new int[]{-120, -105, -95, -25};
    private final int[] mRsrpThresholds = new int[]{-140, -118, -108, -44};
    private final int[] mRsrqThresholds = new int[]{-34, -17, -14, 3};
    private final int[] mRssnrThresholds = new int[]{-20, 10, 20, 30};
    private final int[] mSsrsrpThresholds = new int[]{-140, -118, -98, -44};
    private final int[] mSsrsrqThresholds = new int[]{-43, -17, -14, 20};
    private final int[] mSssinrThresholds = new int[]{-23, -16, -10, 40};

    private final int[][] mThresholds = {mRssiThresholds, mRscpThresholds, mRsrpThresholds,
            mRsrqThresholds, mRssnrThresholds, mSsrsrpThresholds, mSsrsrqThresholds,
            mSssinrThresholds};

    @Test
    @SmallTest
    public void testSignalThresholdInfo() throws Exception {
        SignalThresholdInfo signalThresholdInfo =
                new SignalThresholdInfo.Builder()
                        .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                        .setSignalMeasurementType(
                                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP)
                        .setHysteresisMs(HYSTERESIS_MS)
                        .setHysteresisDb(HYSTERESIS_DB)
                        .setThresholds(SSRSRP_THRESHOLDS)
                        .setIsEnabled(false)
                        .build();

        assertEquals(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP,
                signalThresholdInfo.getSignalMeasurementType());
        assertEquals(HYSTERESIS_MS, signalThresholdInfo.getHysteresisMs());
        assertEquals(HYSTERESIS_DB, signalThresholdInfo.getHysteresisDb());
        assertEquals(Arrays.toString(SSRSRP_THRESHOLDS), Arrays.toString(
                signalThresholdInfo.getThresholds()));
        assertFalse(signalThresholdInfo.isEnabled());
    }

    @Test
    @SmallTest
    public void testBuilderWithAllFields() {
        ArrayList<SignalThresholdInfo> stList = buildSignalThresholdInfoWithAllFields();

        int count = 0;
        for (SignalThresholdInfo st : stList) {
            assertThat(st.getThresholds()).isEqualTo(mThresholds[count]);
            count++;
        }
    }

    @Test
    @SmallTest
    public void testDefaultThresholdsParcel() {
        ArrayList<SignalThresholdInfo> stList = buildSignalThresholdInfoWithAllFields();

        for (SignalThresholdInfo st : stList) {
            Parcel p = Parcel.obtain();
            st.writeToParcel(p, 0);
            p.setDataPosition(0);

            SignalThresholdInfo newSt = SignalThresholdInfo.CREATOR.createFromParcel(p);
            assertThat(newSt).isEqualTo(st);
        }
    }

    @Test
    @SmallTest
    public void testGetSignalThresholdInfo() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                .setHysteresisMs(0)
                .setHysteresisDb(0)
                .setThresholds(new int[]{}, true /*isSystem*/)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                .setHysteresisMs(HYSTERESIS_MS).setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRssiThresholds)
                .setIsEnabled(false)
                .build());

        assertThat(stList.get(0).getThresholds()).isEqualTo(new int[]{});
        assertThat(stList.get(1).getSignalMeasurementType()).isEqualTo(
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI);
        assertThat(stList.get(1).getThresholds()).isEqualTo(mRssiThresholds);
    }

    @Test
    @SmallTest
    public void testEqualsSignalThresholdInfo() {
        final int[] dummyThresholds = new int[]{-100, -90, -70, -60};
        final int[] dummyThreholdsDisordered = new int[]{-60, -90, -100, -70};
        SignalThresholdInfo st1 = new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(1).setSignalMeasurementType(1)
                .setHysteresisMs(HYSTERESIS_MS).setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRssiThresholds).setIsEnabled(false)
                .build();
        SignalThresholdInfo st2 = new SignalThresholdInfo.Builder().setRadioAccessNetworkType(2)
                .setSignalMeasurementType(2).setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB).setThresholds(mRssiThresholds).setIsEnabled(false)
                .build();
        SignalThresholdInfo st3 = new SignalThresholdInfo.Builder().setRadioAccessNetworkType(1)
                .setSignalMeasurementType(1).setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB).setThresholds(dummyThresholds).setIsEnabled(false)
                .build();
        SignalThresholdInfo st4 = new SignalThresholdInfo.Builder().setRadioAccessNetworkType(1)
                .setSignalMeasurementType(1).setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB).setThresholds(mRssiThresholds).setIsEnabled(false)
                .build();
        SignalThresholdInfo st5 = new SignalThresholdInfo.Builder().setRadioAccessNetworkType(1)
                .setSignalMeasurementType(1).setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB).setThresholds(dummyThreholdsDisordered)
                .setIsEnabled(false).build();

        //Return true if all SignalThresholdInfo values match.
        assertTrue(st1.equals(st1));
        assertFalse(st1.equals(st2));
        assertFalse(st1.equals(st3));
        assertTrue(st1.equals(st4));
        //Threshold values ordering doesn't matter
        assertTrue(st3.equals(st5));
        //Return false if the object of argument is other than SignalThresholdInfo.
        assertFalse(st1.equals(new String("test")));
    }

    @Test
    @SmallTest
    public void testBuilderWithValidParameters() {
        ArrayList<SignalThresholdInfo> stList = buildSignalThresholdInfoWithPublicFields();

        for (int i = 0; i < stList.size(); i++) {
            SignalThresholdInfo st = stList.get(i);
            assertThat(st.getThresholds()).isEqualTo(mThresholds[i]);
            assertThat(st.getHysteresisMs()).isEqualTo(SignalThresholdInfo.HYSTERESIS_MS_DISABLED);
            assertThat(st.getHysteresisDb()).isEqualTo(SignalThresholdInfo.HYSTERESIS_DB_DISABLED);
            assertFalse(st.isEnabled());
        }
    }

    @Test
    @SmallTest
    public void testBuilderWithInvalidParameter() {
        // Invalid signal measurement type
        int[] invalidSignalMeasurementTypes = new int[]{-1, 0, 9};
        for (int signalMeasurementType : invalidSignalMeasurementTypes) {
            buildWithInvalidParameterThrowException(
                    AccessNetworkConstants.AccessNetworkType.GERAN, signalMeasurementType,
                    new int[]{-1});
        }

        // Null thresholds array
        buildWithInvalidParameterThrowException(AccessNetworkConstants.AccessNetworkType.GERAN,
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI, null);

        // Empty thresholds
        buildWithInvalidParameterThrowException(AccessNetworkConstants.AccessNetworkType.GERAN,
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI, new int[]{});


        // Too long thresholds array
        buildWithInvalidParameterThrowException(AccessNetworkConstants.AccessNetworkType.GERAN,
                SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI,
                new int[]{-100, -90, -70, -60, -58});

        // Thresholds value out of range
        for (int signalMeasurementType : INVALID_THRESHOLDS_MAP.keySet()) {
            List<Integer> invalidThresholds = INVALID_THRESHOLDS_MAP.get(signalMeasurementType);
            for (int threshold : invalidThresholds) {
                buildWithInvalidParameterThrowException(getValidRan(signalMeasurementType),
                        signalMeasurementType, new int[]{threshold});
            }
        }

        // Invalid RAN/Measurement type combos
        for (int ran : VALID_RAN_TO_MEASUREMENT_TYPE_MAP.keySet()) {
            Set validTypes = VALID_RAN_TO_MEASUREMENT_TYPE_MAP.get(ran);
            for (int type = SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI;
                    type <= SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR; type++) {
                if (!validTypes.contains(type)) {
                    buildWithInvalidParameterThrowException(ran, type, new int[]{-1});
                }
            }
        }
    }

    private void buildWithInvalidParameterThrowException(int ran, int signalMeasurementType,
            int[] thresholds) {
        try {
            new SignalThresholdInfo.Builder()
                    .setRadioAccessNetworkType(ran)
                    .setSignalMeasurementType(signalMeasurementType)
                    .setThresholds(thresholds)
                    .build();
            fail("exception expected");
        } catch (IllegalArgumentException | NullPointerException expected) {
        }
    }

    private ArrayList<SignalThresholdInfo> buildSignalThresholdInfoWithAllFields() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();

        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                .setHysteresisMs(HYSTERESIS_MS).setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRssiThresholds).setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.UTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRscpThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRsrpThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRsrqThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mRssnrThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mSsrsrpThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mSsrsrqThresholds)
                .setIsEnabled(false)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR)
                .setHysteresisMs(HYSTERESIS_MS)
                .setHysteresisDb(HYSTERESIS_DB)
                .setThresholds(mSssinrThresholds)
                .setIsEnabled(false)
                .build());

        return stList;
    }

    private ArrayList<SignalThresholdInfo> buildSignalThresholdInfoWithPublicFields() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();

        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
                .setThresholds(mRssiThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.UTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP)
                .setThresholds(mRscpThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP)
                .setThresholds(mRsrpThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ)
                .setThresholds(mRsrqThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR)
                .setThresholds(mRssnrThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP)
                .setThresholds(mSsrsrpThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ)
                .setThresholds(mSsrsrqThresholds)
                .build());
        stList.add(new SignalThresholdInfo.Builder()
                .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.NGRAN)
                .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR)
                .setThresholds(mSssinrThresholds)
                .build());

        return stList;
    }

    /**
     * Return a possible valid RAN value for the measurement type. This is used to prevent the
     * invalid ran/type causing IllegalArgumentException when testing other invalid input cases.
     */
    private static int getValidRan(@SignalThresholdInfo.SignalMeasurementType int type) {
        switch (type) {
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI:
                return AccessNetworkConstants.AccessNetworkType.GERAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP:
                return AccessNetworkConstants.AccessNetworkType.UTRAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSNR:
                return AccessNetworkConstants.AccessNetworkType.EUTRAN;
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRP:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSRSRQ:
            case SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_SSSINR:
                return AccessNetworkConstants.AccessNetworkType.NGRAN;
            default:
                return AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        }
    }
}
