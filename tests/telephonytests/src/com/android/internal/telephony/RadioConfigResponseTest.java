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

import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertFalse;

import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidTestingRunner.class)
public class RadioConfigResponseTest extends TelephonyTest {
    @Before
    public void setUp() throws Exception {
        super.setUp(RadioConfigResponseTest.class.getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testVersion_1_5() {
        Set<String> caps = RadioConfigResponse.getCaps(RIL.RADIO_HAL_VERSION_1_5, false);
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK));
        assertFalse(
                caps.contains(
                        TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_SLICING_CONFIG_SUPPORTED));
    }

    @Test
    public void testReducedFeatureSet() {
        Set<String> caps = RadioConfigResponse.getCaps(RIL.RADIO_HAL_VERSION_1_6, true);
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK));
        assertFalse(
                caps.contains(
                        TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_SLICING_CONFIG_SUPPORTED));
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_SIM_PHONEBOOK_IN_MODEM));
    }

    @Test
    public void testNonReducedFeatureSet() {
        Set<String> caps = RadioConfigResponse.getCaps(RIL.RADIO_HAL_VERSION_1_6, false);
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK));
        assertTrue(
                caps.contains(
                        TelephonyManager.CAPABILITY_NR_DUAL_CONNECTIVITY_CONFIGURATION_AVAILABLE));
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_THERMAL_MITIGATION_DATA_THROTTLING));
        assertTrue(
                caps.contains(TelephonyManager.CAPABILITY_SLICING_CONFIG_SUPPORTED));
        assertFalse(
                caps.contains(TelephonyManager.CAPABILITY_SIM_PHONEBOOK_IN_MODEM));
    }
}
