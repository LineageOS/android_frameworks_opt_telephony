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
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getCountryIso(), "us");
        assertEquals(number.getMnc(), "30");
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(0, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                (int) categories.get(0));

        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT));
        assertEquals(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(1, sources.size());
        assertEquals(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                (int) sources.get(0));

        assertEquals(EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL,
                number.getEmergencyCallRouting());
    }

    public void testEmergencyNumberSpecificService() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertEquals(number.getNumber(), "911");
        assertEquals(number.getCountryIso(), "us");
        assertEquals(number.getMnc(), "30");
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(8, number.getEmergencyServiceCategoryBitmask());

        List<Integer> categories = number.getEmergencyServiceCategories();
        assertEquals(1, categories.size());
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
                (int) categories.get(0));

        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM));
        assertTrue(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG));
        assertFalse(number.isFromSources(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT));
        assertEquals(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG,
                number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(2, sources.size());
        Collections.sort(sources);
        List<Integer> sourcesToVerify = new ArrayList<Integer>();
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG);
        Collections.sort(sourcesToVerify);
        assertTrue(sourcesToVerify.equals(sources));

        assertEquals(EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL,
                number.getEmergencyCallRouting());
    }

    public void testEmergencyNumberMultipleServices() throws Exception {
        EmergencyNumber number = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertEquals(number.getNumber(), "110");
        assertEquals(number.getCountryIso(), "jp");
        assertEquals(number.getMnc(), "30");
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE));
        assertTrue(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC));
        assertFalse(number.isInEmergencyServiceCategories(
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AIEC));
        assertEquals(EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC,
                number.getEmergencyServiceCategoryBitmask());

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
        assertEquals(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT,
                number.getEmergencyNumberSourceBitmask());

        List<Integer> sources = number.getEmergencyNumberSources();
        assertEquals(3, sources.size());
        Collections.sort(sources);
        List<Integer> sourcesToVerify = new ArrayList<Integer>();
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM);
        sourcesToVerify.add(EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT);
        Collections.sort(sourcesToVerify);
        assertTrue(sourcesToVerify.equals(sources));

        assertEquals(EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL,
                number.getEmergencyCallRouting());
    }

    public void testEmergencyNumberDisplayPriority() throws Exception {
        EmergencyNumber numberHighestDisplayPriority = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber numberHigherDisplayPriority = new EmergencyNumber(
                "922",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber numberLowestDisplayPriority = new EmergencyNumber(
                "110",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE
                        | EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG
                        | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DEFAULT,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertTrue(numberHighestDisplayPriority.compareTo(
                numberHigherDisplayPriority) < 0);
        assertTrue(numberHigherDisplayPriority.compareTo(
                numberLowestDisplayPriority) < 0);
    }

    public void testSameEmergencyNumberDifferentName() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "912",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }


    public void testSameEmergencyNumberDifferenCountry() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testSameEmergencyNumberDifferentMnc() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "20",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testSameEmergencyNumberDifferentCategories() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testSameEmergencyNumberDifferentUrns() throws Exception {

        List<String> urn1 = new ArrayList<>();
        urn1.add("sos");

        List<String> urn2 = new ArrayList<>();
        urn2.add("animal-control");

        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                urn1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testSameEmergencyNumberCallRouting() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        /* case 1: Check routing is not checked when comparing the same numbers. As routing will
        be unknown for all numbers apart from DB. Check merge when both are not from DB then
        routing value is merged from first number. */
        assertTrue(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
        assertEquals(num1, EmergencyNumber.mergeSameEmergencyNumbers(num1, num2));

        num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        /* case 1: Check routing is not checked when comparing the same numbers. Check merge when
        one of the number is from DB then routing value is merged from DB number. Along with
        source value is masked with both*/
        assertTrue(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));

        num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        assertEquals(num2, EmergencyNumber.mergeSameEmergencyNumbers(num1, num2));
    }

    public void testSameEmergencyNumberDifferentSource() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertTrue(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testSameEmergencyNumberDifferentSourceTestOrNot() throws Exception {
        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "911",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_TEST,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        assertFalse(EmergencyNumber.areSameEmergencyNumbers(num1, num2, false));
    }

    public void testMergeSameNumbersInEmergencyNumberListWithDifferentSources() throws Exception {
        List<EmergencyNumber> inputNumberList = new ArrayList<>();
        EmergencyNumber num1 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num3 = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        inputNumberList.add(num1);
        inputNumberList.add(num2);
        inputNumberList.add(num3);

        EmergencyNumber num4 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                    | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        List<EmergencyNumber> outputNumberList = new ArrayList<>();
        outputNumberList.add(num3);
        outputNumberList.add(num4);
        Collections.sort(outputNumberList);

        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(inputNumberList);
        assertEquals(outputNumberList, inputNumberList);
    }

    public void testMergeSameNumbersInEmergencyNumberListCornerCases() throws Exception {
        // Corner case 1: Empty List
        List<EmergencyNumber> inputNumberList = new ArrayList<>();
        List<EmergencyNumber> outputNumberList = new ArrayList<>();
        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(inputNumberList);
        assertEquals(outputNumberList, inputNumberList);

        // Corner case 2: Single element
        EmergencyNumber num = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        inputNumberList = new ArrayList<>();
        outputNumberList = new ArrayList<>();
        inputNumberList.add(num);
        outputNumberList.add(num);
        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(inputNumberList);
        assertEquals(outputNumberList, inputNumberList);

        // Corner case 3: multiple element not ordered
        inputNumberList = new ArrayList<>();
        outputNumberList = new ArrayList<>();

        EmergencyNumber num1 = new EmergencyNumber(
                "911",
                "us",
                "",
                0b111,
                new ArrayList<String>(),
                0b10000,
                0);

        EmergencyNumber num12 = new EmergencyNumber(
                "911",
                "us",
                "",
                0b111,
                new ArrayList<String>(),
                0b10000,
                0);

        EmergencyNumber num3 = new EmergencyNumber(
                "112",
                "",
                "",
                0,
                new ArrayList<String>(),
                0,
                0);

        EmergencyNumber num4 = new EmergencyNumber(
                "*911",
                "",
                "",
                0,
                new ArrayList<String>(),
                0,
                0);

        EmergencyNumber num13 = new EmergencyNumber(
                "911",
                "us",
                "",
                0b111,
                new ArrayList<String>(),
                0b10000,
                0);

        EmergencyNumber num6 = new EmergencyNumber(
                "#911",
                "",
                "",
                0,
                new ArrayList<String>(),
                0,
                0);

        EmergencyNumber num31 = new EmergencyNumber(
                "112",
                "",
                "",
                0,
                new ArrayList<String>(),
                0,
                0);

        EmergencyNumber num14 = new EmergencyNumber(
                "911",
                "us",
                "",
                0b111,
                new ArrayList<String>(),
                0b10000,
                0);

        inputNumberList.add(num1);
        inputNumberList.add(num12);
        inputNumberList.add(num3);
        inputNumberList.add(num4);
        inputNumberList.add(num13);
        inputNumberList.add(num6);
        inputNumberList.add(num31);
        inputNumberList.add(num14);
        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(inputNumberList);

        outputNumberList.add(num1);
        outputNumberList.add(num3);
        outputNumberList.add(num4);
        outputNumberList.add(num6);
        Collections.sort(outputNumberList);

        assertEquals(outputNumberList, inputNumberList);
    }

    public void testMergeSameNumbersEmergencyNumberListByDeterminingFields() throws Exception {
        List<String> urn1 = new ArrayList<>();
        urn1.add("sos");

        List<String> urn2 = new ArrayList<>();
        urn2.add("sos:ambulance");

        List<EmergencyNumber> inputNumberList = new ArrayList<>();
        EmergencyNumber num1 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num2 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                urn1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num3 = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num4 = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        EmergencyNumber num5 = new EmergencyNumber(
                "112",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber num6 = new EmergencyNumber(
                "112",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                urn1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num7 = new EmergencyNumber(
                "108",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num8 = new EmergencyNumber(
                "108",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num9 = new EmergencyNumber(
                "102",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num10 = new EmergencyNumber(
                "102",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE,
                urn1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber num11 = new EmergencyNumber(
                "100",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_MIEC,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        EmergencyNumber num12 = new EmergencyNumber(
                "100",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);


        EmergencyNumber num13 = new EmergencyNumber(
                "200",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        EmergencyNumber num14 = new EmergencyNumber(
                "200",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        inputNumberList.add(num1);
        inputNumberList.add(num2);
        inputNumberList.add(num3);
        inputNumberList.add(num4);
        inputNumberList.add(num5);
        inputNumberList.add(num6);
        inputNumberList.add(num7);
        inputNumberList.add(num8);
        inputNumberList.add(num9);
        inputNumberList.add(num10);
        inputNumberList.add(num11);
        inputNumberList.add(num12);
        inputNumberList.add(num13);
        inputNumberList.add(num14);

        EmergencyNumber mergeOfNum1AndNum2 = new EmergencyNumber(
                "110",
                "jp",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                urn1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber mergeOfNum3AndNum4 = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        List<String> mergedUrns1And2 = new ArrayList<>();
        mergedUrns1And2.add("sos");
        mergedUrns1And2.add("sos:ambulance");

        EmergencyNumber mergeOfNum5AndNum6 = new EmergencyNumber(
                "112",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                mergedUrns1And2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        EmergencyNumber mergeOfNum7AndNum8 = new EmergencyNumber(
                "108",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber mergeOfNum11AndNum12 = new EmergencyNumber(
                "100",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                urn2,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        List<String> mergedUrns2And1 = new ArrayList<>();
        mergedUrns2And1.add("sos:ambulance");
        mergedUrns2And1.add("sos");

        EmergencyNumber mergeOfNum9AndNum10 = new EmergencyNumber(
                "102",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE,
                mergedUrns2And1,
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_SIM,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_UNKNOWN);

        EmergencyNumber mergeOfNum13AndNum14 = new EmergencyNumber(
                "200",
                "in",
                "",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG
                | EmergencyNumber.EMERGENCY_NUMBER_SOURCE_DATABASE,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_EMERGENCY);

        List<EmergencyNumber> outputNumberList = new ArrayList<>();
        outputNumberList.add(mergeOfNum1AndNum2);
        outputNumberList.add(mergeOfNum3AndNum4);
        outputNumberList.add(mergeOfNum5AndNum6);
        outputNumberList.add(mergeOfNum7AndNum8);
        outputNumberList.add(mergeOfNum9AndNum10);
        outputNumberList.add(mergeOfNum11AndNum12);
        outputNumberList.add(mergeOfNum13AndNum14);
        Collections.sort(outputNumberList);

        EmergencyNumber.mergeSameNumbersInEmergencyNumberList(inputNumberList, true);
        assertEquals(outputNumberList, inputNumberList);
    }
}
