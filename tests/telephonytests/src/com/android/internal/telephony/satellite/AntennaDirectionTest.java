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

import org.junit.Test;

public class AntennaDirectionTest {

    @Test
    public void testParcel() {
        AntennaDirection antennaDirection = new AntennaDirection(1, 2, 3);

        Parcel p = Parcel.obtain();
        antennaDirection.writeToParcel(p, 0);
        p.setDataPosition(0);

        AntennaDirection fromParcel = AntennaDirection.CREATOR.createFromParcel(p);
        assertThat(antennaDirection).isEqualTo(fromParcel);
    }

    @Test
    public void testEquals() {
        AntennaDirection antennaDirection1 = new AntennaDirection(1.12f, 1.13f, 1.14f);
        AntennaDirection antennaDirection2 = new AntennaDirection(1.12f, 1.13f, 1.14f);
        assertEquals(antennaDirection1, antennaDirection2);

        AntennaDirection antennaDirection3 = new AntennaDirection(1.121f, 1.131f, 1.141f);
        assertNotEquals(antennaDirection1, antennaDirection3);
    }
}
