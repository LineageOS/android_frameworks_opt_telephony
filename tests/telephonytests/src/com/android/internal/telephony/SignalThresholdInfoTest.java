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
import android.telephony.SignalThresholdInfo;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;

@RunWith(JUnit4.class)
public class SignalThresholdInfoTest extends TestCase {
    private static final int HYSTERESIS_DB = 2;
    private static final int HYSTERESIS_MS = 30;
    private static final int[] SSRSRP_THRESHOLDS = new int[] {-30, 10, 45, 130};

    private final int[] mRssiThresholds = new int[] {-109, -103, -97, -89};
    private final int[] mRscpThresholds = new int[] {-115, -105, -95, -85};
    private final int[] mRsrpThresholds = new int[] {-128, -118, -108, -98};
    private final int[] mRsrqThresholds = new int[] {-19, -17, -14, -12};
    private final int[] mRssnrThresholds = new int[] {-30, 10, 45, 130};
    private final int[][] mThresholds = new int[5][];

    @Test
    @SmallTest
    public void testSignalThresholdInfo() throws Exception {
        SignalThresholdInfo signalThresholdInfo = new SignalThresholdInfo(
                SignalThresholdInfo.SIGNAL_SSRSRP,
                HYSTERESIS_MS,
                HYSTERESIS_DB,
                SSRSRP_THRESHOLDS,
                false);

        assertEquals(SignalThresholdInfo.SIGNAL_SSRSRP,
                signalThresholdInfo.getSignalMeasurement());
        assertEquals(HYSTERESIS_MS, signalThresholdInfo.getHysteresisMs());
        assertEquals(HYSTERESIS_DB, signalThresholdInfo.getHysteresisDb());
        assertEquals(Arrays.toString(SSRSRP_THRESHOLDS), Arrays.toString(
                signalThresholdInfo.getThresholds()));
        assertFalse(signalThresholdInfo.isEnabled());
    }

    @Test
    @SmallTest
    public void testDefaultThresholdsConstruction() {
        setThresholds();
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoConstructor();

        int count = 0;
        for (SignalThresholdInfo st : stList) {
            assertThat(st.getThresholds()).isEqualTo(mThresholds[count]);
            count++;
        }
    }

    @Test
    @SmallTest
    public void testDefaultThresholdsParcel() {
        ArrayList<SignalThresholdInfo> stList = setSignalThresholdInfoConstructor();

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
        stList.add(new SignalThresholdInfo(0, 0, 0, null, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssiThresholds, false));

        assertThat(stList.get(0).getThresholds()).isEqualTo(null);
        assertThat(stList.get(1).getSignalMeasurement()).isEqualTo(SignalThresholdInfo.SIGNAL_RSSI);
        assertThat(stList.get(1).getThresholds()).isEqualTo(mRssiThresholds);
    }

    @Test
    @SmallTest
    public void testEqualsSignalThresholdInfo() {
        final int[] dummyThresholds = new int[] {-100, -1, 1, 100};
        SignalThresholdInfo st1 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);
        SignalThresholdInfo st2 = new SignalThresholdInfo(2, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);
        SignalThresholdInfo st3 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                dummyThresholds, false);
        SignalThresholdInfo st4 = new SignalThresholdInfo(1, HYSTERESIS_MS, HYSTERESIS_DB,
                mRssiThresholds, false);

        //Return true if all SignalThresholdInfo values match.
        assertTrue(st1.equals(st1));
        assertFalse(st1.equals(st2));
        assertFalse(st1.equals(st3));
        assertTrue(st1.equals(st4));
        //Return false if the object of argument is other than SignalThresholdInfo.
        assertFalse(st1.equals(new String("test")));
    }

    private void setThresholds() {
        mThresholds[0] = mRssiThresholds;
        mThresholds[1] = mRscpThresholds;
        mThresholds[2] = mRsrpThresholds;
        mThresholds[3] = mRsrqThresholds;
        mThresholds[4] = mRssnrThresholds;
    }

    private ArrayList<SignalThresholdInfo> setSignalThresholdInfoConstructor() {
        ArrayList<SignalThresholdInfo> stList = new ArrayList<>();
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSI, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssiThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSCP, HYSTERESIS_MS,
                HYSTERESIS_DB, mRscpThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRP, HYSTERESIS_MS,
                HYSTERESIS_DB, mRsrpThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSRQ, HYSTERESIS_MS,
                HYSTERESIS_DB, mRsrqThresholds, false));
        stList.add(new SignalThresholdInfo(SignalThresholdInfo.SIGNAL_RSSNR, HYSTERESIS_MS,
                HYSTERESIS_DB, mRssnrThresholds, false));

        return stList;
    }
}
