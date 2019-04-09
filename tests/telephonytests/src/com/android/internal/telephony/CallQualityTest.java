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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.CallQuality;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Simple unit test verifying the parceling and unparceling of CallQuality.
 */
@RunWith(AndroidJUnit4.class)
public class CallQualityTest {

    @SmallTest
    @Test
    public void testParcelUnparcelCallQuality() {
        CallQuality quality = new CallQuality(
                1 /* downlinkCallQualityLevel */,
                2 /* uplinkCallQualityLevel */,
                4000 /* callDuration */,
                500 /* numRtpPacketsTransmitted */,
                600 /* numRtpPacketsReceived */,
                70 /* numRtpPacketsTransmittedLost */,
                42 /* numRtpPacketsNotReceived */,
                30 /* averageRelativeJitter */,
                40 /* maxRelativeJitter */,
                100 /* averageRoundTripTime */,
                1 /* codecType)  */);

        Parcel parcel = Parcel.obtain();
        quality.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CallQuality unparceledData = CallQuality.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("CallQuality is not equal after parceled/unparceled", quality, unparceledData);
    }
}
