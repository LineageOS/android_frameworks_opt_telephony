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
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteManager;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SatelliteCapabilitiesTest {

    private AntennaDirection mAntennaDirection = new AntennaDirection(1,1,1);

    @Test
    public void testParcel() {
        Set<Integer> satelliteRadioTechnologies = new HashSet<>();
        satelliteRadioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN);
        satelliteRadioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN);

        Map<Integer, AntennaPosition> antennaPositionMap = new HashMap<>();
        AntennaPosition antennaPosition = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
        antennaPositionMap.put(SatelliteManager.DISPLAY_MODE_OPENED, antennaPosition);

        SatelliteCapabilities capabilities = new SatelliteCapabilities(satelliteRadioTechnologies,
                true, 10, antennaPositionMap);

        Parcel p = Parcel.obtain();
        capabilities.writeToParcel(p, 0);
        p.setDataPosition(0);

        SatelliteCapabilities fromParcel = SatelliteCapabilities.CREATOR.createFromParcel(p);
        assertThat(capabilities).isEqualTo(fromParcel);
    }

    @Test
    public void testParcel_emptySatelliteRadioTechnologies() {
        Set<Integer> satelliteRadioTechnologies = new HashSet<>();

        Map<Integer, AntennaPosition> antennaPositionMap = new HashMap<>();
        AntennaPosition antennaPosition1 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
        AntennaPosition antennaPosition2 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT);
        antennaPositionMap.put(SatelliteManager.DISPLAY_MODE_OPENED, antennaPosition1);
        antennaPositionMap.put(SatelliteManager.DISPLAY_MODE_CLOSED, antennaPosition2);

        SatelliteCapabilities capabilities = new SatelliteCapabilities(satelliteRadioTechnologies,
                false, 100, antennaPositionMap);

        Parcel p = Parcel.obtain();
        capabilities.writeToParcel(p, 0);
        p.setDataPosition(0);

        SatelliteCapabilities fromParcel = SatelliteCapabilities.CREATOR.createFromParcel(p);
        assertThat(capabilities).isEqualTo(fromParcel);
    }


    @Test
    public void testParcel_emptyAntennaPosition() {
        Set<Integer> satelliteRadioTechnologies = new HashSet<>();
        satelliteRadioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_EMTC_NTN);

        SatelliteCapabilities capabilities = new SatelliteCapabilities(satelliteRadioTechnologies,
                true, 0, new HashMap<>());

        Parcel p = Parcel.obtain();
        capabilities.writeToParcel(p, 0);
        p.setDataPosition(0);

        SatelliteCapabilities fromParcel = SatelliteCapabilities.CREATOR.createFromParcel(p);
        assertThat(capabilities).isEqualTo(fromParcel);
    }

    @Test
    public void testEquals() {
        Set<Integer> satelliteRadioTechnologies = new HashSet<>();
        satelliteRadioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NB_IOT_NTN);
        satelliteRadioTechnologies.add(SatelliteManager.NT_RADIO_TECHNOLOGY_NR_NTN);

        AntennaPosition antennaPosition1 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_PORTRAIT);
        AntennaPosition antennaPosition2 = new AntennaPosition(mAntennaDirection,
                SatelliteManager.DEVICE_HOLD_POSITION_LANDSCAPE_LEFT);

        Map<Integer, AntennaPosition> antennaPositionMap1 = new HashMap<>();
        antennaPositionMap1.put(SatelliteManager.DISPLAY_MODE_OPENED, antennaPosition1);
        antennaPositionMap1.put(SatelliteManager.DISPLAY_MODE_CLOSED, antennaPosition2);

        SatelliteCapabilities satelliteCapabilities1 =
                new SatelliteCapabilities(satelliteRadioTechnologies, true, 10,
                        antennaPositionMap1);
        SatelliteCapabilities satelliteCapabilities2 =
                new SatelliteCapabilities(satelliteRadioTechnologies, true, 10,
                        antennaPositionMap1);
        assertEquals(satelliteCapabilities1, satelliteCapabilities2);

        Map<Integer, AntennaPosition> antennaPositionMap2 = new HashMap<>();
        antennaPositionMap2.put(SatelliteManager.DISPLAY_MODE_CLOSED, antennaPosition1);
        antennaPositionMap2.put(SatelliteManager.DISPLAY_MODE_OPENED, antennaPosition2);
        satelliteCapabilities2 =
                new SatelliteCapabilities(satelliteRadioTechnologies, true, 10,
                        antennaPositionMap2);
        assertNotEquals(satelliteCapabilities1, satelliteCapabilities2);
    }
}
