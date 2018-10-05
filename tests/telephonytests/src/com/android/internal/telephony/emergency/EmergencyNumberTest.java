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

package com.android.internal.telephony.emergency;

import android.telephony.emergency.EmergencyNumber;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EmergencyNumberTest extends TestCase {
    public void testEmergencyNumberUnspecified() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "911",
                "us",
                // EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED
                0,
                // EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALLING
                1);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getCountryIso(), "us");
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(0, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                (int) categories.get(0));

        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT));
        assertEquals(1, number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(1, sources.size());
        assertEquals(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                (int) sources.get(0));
    }

    public void testEmergencyNumberSpecificService() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "911",
                "us",
                // EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD
                8,
                // EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALLING
                // EMERGENCY_NUMBER_SOURCE_MODEM
                5);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getCountryIso(), "us");
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(8, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
                (int) categories.get(0));

        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM));
        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT));
        assertEquals(5, number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(2, sources.size());
        Collections.sort(sources);
        List<Integer> sourcesToVerify = new ArrayList<Integer>();
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG);
        Collections.sort(sourcesToVerify);
        assertTrue(sourcesToVerify.equals(sources));
    }

    public void testEmergencyNumberMultipleServices() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "110",
                "jp",
                // EMERGENCY_SERVICE_CATEGORY_POLICE
                // EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                // EMERGENCY_SERVICE_CATEGORY_MIEC
                35,
                // EMERGENCY_NUMBER_SOURCE_NETWORK_SINGALING
                // EMERGENCY_NUMBER_SOURCE_SIM
                // EMERGENCY_NUMBER_SOURCE_DEFAULT
                11);
        assertEquals(number.getNumber(), "110");
        assertEquals(number.getCountryIso(), "jp");
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertTrue(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertTrue(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertTrue(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.isInEmergencyServiceCategories(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(35, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(3, categories.size());
        Collections.sort(categories);
        List<Integer> categoriesToVerify = new ArrayList<Integer>();
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE);
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE);
        categoriesToVerify.add(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC);
        Collections.sort(categoriesToVerify);
        assertTrue(categoriesToVerify.equals(categories));

        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING));
        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG));
        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT));
        assertEquals(11, number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(3, sources.size());
        Collections.sort(sources);
        List<Integer> sourcesToVerify = new ArrayList<Integer>();
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
        Collections.sort(sourcesToVerify);
        assertTrue(sourcesToVerify.equals(sources));
    }
}
