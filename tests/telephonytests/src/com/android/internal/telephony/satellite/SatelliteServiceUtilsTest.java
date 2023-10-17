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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteServiceUtilsTest extends TelephonyTest {
    private static final String TAG = "SatelliteServiceUtilsTest";

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testParseSupportedSatelliteServicesFromStringArray() {
        // Parse correct format input string
        int[] expectedServices1 = {2, 3};
        int[] expectedServices2 = {3};
        String[] supportedServicesStrArr1 = {"10011:2,3", "10112:3"};
        Map<String, Set<Integer>> supportedServiceMap =
                SatelliteServiceUtils.parseSupportedSatelliteServices(supportedServicesStrArr1);

        assertTrue(supportedServiceMap.containsKey("10011"));
        Set<Integer> supportedServices = supportedServiceMap.get("10011");
        assertTrue(Arrays.equals(expectedServices1,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        assertTrue(supportedServiceMap.containsKey("10112"));
        supportedServices = supportedServiceMap.get("10112");
        assertTrue(Arrays.equals(expectedServices2,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        // Parse correct mixed with incorrect format input string
        String[] supportedServicesStrArr2 = {"10011:2,3,1xy", "10112:3,70", "10012:"};
        supportedServiceMap = SatelliteServiceUtils.parseSupportedSatelliteServices(
                supportedServicesStrArr2);

        assertTrue(supportedServiceMap.containsKey("10011"));
        supportedServices = supportedServiceMap.get("10011");
        assertTrue(Arrays.equals(expectedServices1,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        assertTrue(supportedServiceMap.containsKey("10112"));
        supportedServices = supportedServiceMap.get("10112");
        assertTrue(Arrays.equals(expectedServices2,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        assertTrue(supportedServiceMap.containsKey("10012"));
        assertTrue(supportedServiceMap.get("10012").isEmpty());

        // Parse an empty input string
        String[] supportedServicesStrArr3 = {};
        supportedServiceMap = SatelliteServiceUtils.parseSupportedSatelliteServices(
                supportedServicesStrArr3);
        assertTrue(supportedServiceMap.isEmpty());
    }

    @Test
    public void testParseSupportedSatelliteServicesFromPersistableBundle() {
        PersistableBundle supportedServicesBundle = new PersistableBundle();
        String plmn1 = "10101";
        String plmn2 = "10102";
        String plmn3 = "10103";
        int[] supportedServicesForPlmn1 = {1, 2, 3};
        int[] supportedServicesForPlmn2 = {3, 4, 100};
        int[] expectedServicesForPlmn1 = {1, 2, 3};
        int[] expectedServicesForPlmn2 = {3, 4};

        // Parse an empty bundle
        Map<String, Set<Integer>> supportedServiceMap =
                SatelliteServiceUtils.parseSupportedSatelliteServices(supportedServicesBundle);
        assertTrue(supportedServiceMap.isEmpty());

        // Add some more fields
        supportedServicesBundle.putIntArray(plmn1, supportedServicesForPlmn1);
        supportedServicesBundle.putIntArray(plmn2, supportedServicesForPlmn2);
        supportedServicesBundle.putIntArray(plmn3, new int[0]);

        supportedServiceMap =
                SatelliteServiceUtils.parseSupportedSatelliteServices(supportedServicesBundle);
        assertEquals(3, supportedServiceMap.size());

        assertTrue(supportedServiceMap.containsKey(plmn1));
        Set<Integer> supportedServices = supportedServiceMap.get(plmn1);
        assertTrue(Arrays.equals(expectedServicesForPlmn1,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        assertTrue(supportedServiceMap.containsKey(plmn2));
        supportedServices = supportedServiceMap.get(plmn2);
        assertTrue(Arrays.equals(expectedServicesForPlmn2,
                supportedServices.stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        assertTrue(supportedServiceMap.containsKey(plmn3));
        supportedServices = supportedServiceMap.get(plmn3);
        assertTrue(supportedServices.isEmpty());
    }

    @Test
    public void testMergeSupportedSatelliteServices() {
        String plmn1 = "00101";
        String plmn2 = "00102";
        String plmn3 = "00103";

        Integer[] providerSupportedServicesForPlmn1 = {1, 2, 3};
        Integer[] providerSupportedServicesForPlmn2 = {3, 4};
        Map<String, Set<Integer>> providerSupportedServicesMap = new HashMap<>();
        providerSupportedServicesMap.put(
                plmn1, new HashSet<>(Arrays.asList(providerSupportedServicesForPlmn1)));
        providerSupportedServicesMap.put(
                plmn2, new HashSet<>(Arrays.asList(providerSupportedServicesForPlmn2)));

        Integer[] carrierSupportedServicesForPlmn2 = {3};
        Integer[] carrierSupportedServicesForPlmn3 = {1, 3, 4};
        Map<String, Set<Integer>> carrierSupportedServicesMap = new HashMap<>();
        carrierSupportedServicesMap.put(
                plmn2, new HashSet<>(Arrays.asList(carrierSupportedServicesForPlmn2)));
        carrierSupportedServicesMap.put(
                plmn3, new HashSet<>(Arrays.asList(carrierSupportedServicesForPlmn3)));

        // {@code plmn1} is present in only provider support services.
        int[] expectedSupportedServicesForPlmn1 = {1, 2, 3};
        // Intersection of {3,4} and {3}.
        int[] expectedSupportedServicesForPlmn2 = {3};
        Map<String, Set<Integer>> supportedServicesMap =
                SatelliteServiceUtils.mergeSupportedSatelliteServices(
                        providerSupportedServicesMap, carrierSupportedServicesMap);

        assertEquals(2, supportedServicesMap.size());
        assertTrue(supportedServicesMap.containsKey(plmn1));
        assertTrue(supportedServicesMap.containsKey(plmn2));
        assertFalse(supportedServicesMap.containsKey(plmn3));
        assertTrue(Arrays.equals(expectedSupportedServicesForPlmn1,
                supportedServicesMap.get(plmn1).stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        assertTrue(Arrays.equals(expectedSupportedServicesForPlmn2,
                supportedServicesMap.get(plmn2).stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
    }
}
