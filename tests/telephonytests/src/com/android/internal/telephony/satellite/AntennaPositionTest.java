/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.satellite.AntennaDirection;
import android.telephony.satellite.AntennaPosition;
import android.telephony.satellite.SatelliteManager;

import org.junit.Test;

public class AntennaPositionTest {

    private AntennaDirection mAntennaDirection = new AntennaDirection(1,1,1);

    @Test
    public void testParcel() {
        AntennaPosition antennaPosition = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);

        Parcel p = Parcel.obtain();
        antennaPosition.writeToParcel(p, 0);
        p.setDataPosition(0);

        AntennaPosition fromParcel = AntennaPosition.CREATOR.createFromParcel(p);
        assertThat(antennaPosition).isEqualTo(fromParcel);
    }

    @Test
    public void testEquals() {
        AntennaPosition antennaPosition1 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
        AntennaPosition antennaPosition2 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
        assertEquals(antennaPosition1, antennaPosition2);

        AntennaPosition antennaPosition3 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT);
        assertNotEquals(antennaPosition1, antennaPosition3);
    }
}
