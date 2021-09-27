/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.telephony.SignalStrengthUpdateRequest;
import android.telephony.SignalThresholdInfo;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;

import java.util.Collection;
import java.util.List;

public class SignalStrengthUpdateRequestTest extends TestCase {

    private SignalThresholdInfo mRssiInfoOnGERAN = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
            .setThresholds(new int[]{-109, -103, -97, -89})
            .build();

    private SignalThresholdInfo mRssiInfoOnGERAN2 = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.GERAN)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
            .setThresholds(new int[]{-108, -102, -96, -88})
            .build();

    private SignalThresholdInfo mRssiInfoOnCDMA2000 = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.CDMA2000)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSSI)
            .setThresholds(new int[]{-109, -103, -97, -89})
            .build();

    private SignalThresholdInfo mRscpInfoOnUTRAN = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.UTRAN)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSCP)
            .setThresholds(new int[]{-115, -105, -95, -85})
            .build();

    private SignalThresholdInfo mRsrpInfoOnEUTRAN = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRP)
            .setThresholds(new int[]{-115, -105, -95, -85})
            .build();

    private SignalThresholdInfo mRsrqInfoEUTRAN = new SignalThresholdInfo.Builder()
            .setRadioAccessNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
            .setSignalMeasurementType(SignalThresholdInfo.SIGNAL_MEASUREMENT_TYPE_RSRQ)
            .setThresholds(new int[]{-30, -20, -10, -1})
            .build();

    @Test
    @SmallTest
    public void testPublicConstructorWithInvalidParam() {
        // null Collection
        validateBuilderWithInvalidParam(null);

        // duplication of SignalMeasurementType for the same RAN in Collection
        validateBuilderWithInvalidParam(List.of(mRssiInfoOnGERAN, mRssiInfoOnGERAN2));

        // empty Collections
        validateBuilderWithInvalidParam(List.of());
    }

    @Test
    @SmallTest
    public void testPublicConstructorWithValidParam() {
        Collection<SignalThresholdInfo> infos = List.of(mRssiInfoOnGERAN, mRscpInfoOnUTRAN);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos).setReportingRequestedWhileIdle(false).build();
        assertFalse(request.isReportingRequestedWhileIdle());
        assertFalse(request.isSystemThresholdReportingRequestedWhileIdle());
        assertEquals(infos, request.getSignalThresholdInfos());
    }

    @Test
    @SmallTest
    public void testParcel() {
        Collection<SignalThresholdInfo> infos = List.of(mRssiInfoOnGERAN, mRscpInfoOnUTRAN);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos).setReportingRequestedWhileIdle(true).build();

        Parcel p = Parcel.obtain();
        request.writeToParcel(p, 0);
        p.setDataPosition(0);

        SignalStrengthUpdateRequest newRequest =
                SignalStrengthUpdateRequest.CREATOR.createFromParcel(p);
        assertThat(newRequest).isEqualTo(request);
    }

    @Test
    @SmallTest
    public void testEquals() {
        Collection<SignalThresholdInfo> infos1 = List.of(mRssiInfoOnGERAN, mRssiInfoOnCDMA2000);
        SignalStrengthUpdateRequest request1 = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos1).setReportingRequestedWhileIdle(false).build();

        assertTrue(request1.equals(request1));

        // Ordering does not matter
        Collection<SignalThresholdInfo> infos2 = List.of(mRssiInfoOnCDMA2000, mRssiInfoOnGERAN);
        SignalStrengthUpdateRequest request2 = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos2).setReportingRequestedWhileIdle(false).build();
        assertTrue(request1.equals(request2));

        SignalStrengthUpdateRequest request3 = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos1).setReportingRequestedWhileIdle(true).build();
        assertFalse(request1.equals(request3));

        SignalStrengthUpdateRequest request4 = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos1).setReportingRequestedWhileIdle(false)
                .setSystemThresholdReportingRequestedWhileIdle(true).build();
        assertFalse(request1.equals(request4));

        // return false if the object is not SignalStrengthUpdateRequest
        assertFalse(request1.equals("test"));
    }

    @Test
    @SmallTest
    public void testMultipleSignalMeasurementTypeOnSameRan() {
        Collection<SignalThresholdInfo> infos = List.of(mRsrpInfoOnEUTRAN, mRsrqInfoEUTRAN);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos).build();

        assertFalse(request.isReportingRequestedWhileIdle());
        assertFalse(request.isSystemThresholdReportingRequestedWhileIdle());
        assertEquals(infos, request.getSignalThresholdInfos());
    }

    @Test
    @SmallTest
    public void testSameSignalMeasurementTypeOnDifferentRan() {
        Collection<SignalThresholdInfo> infos = List.of(mRssiInfoOnGERAN, mRssiInfoOnCDMA2000);
        SignalStrengthUpdateRequest request = new SignalStrengthUpdateRequest.Builder()
                .setSignalThresholdInfos(infos).build();

        assertFalse(request.isReportingRequestedWhileIdle());
        assertFalse(request.isSystemThresholdReportingRequestedWhileIdle());
        assertEquals(infos, request.getSignalThresholdInfos());
    }

    private void validateBuilderWithInvalidParam(Collection<SignalThresholdInfo> infos) {
        try {
            new SignalStrengthUpdateRequest.Builder()
                    .setSignalThresholdInfos(infos).setReportingRequestedWhileIdle(false).build();
            fail("Exception expected");
        } catch (IllegalArgumentException | NullPointerException expected) {
        }
    }
}
