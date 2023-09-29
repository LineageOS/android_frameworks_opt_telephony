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
import java.util.List;
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
    public void testMergeStrLists() {
        List<String> l1 = Arrays.asList("1", "2", "2");
        List<String> l2 = Arrays.asList("1", "3", "3");
        List<String> expectedMergedList = Arrays.asList("1", "2", "3");
        List<String> mergedList = SatelliteServiceUtils.mergeStrLists(l1, l2);
        assertEquals(expectedMergedList, mergedList);
    }
}
