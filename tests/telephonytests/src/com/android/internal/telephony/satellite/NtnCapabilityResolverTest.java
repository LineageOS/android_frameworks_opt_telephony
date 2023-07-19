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

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_DATA;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY;
import static android.telephony.NetworkRegistrationInfo.SERVICE_TYPE_SMS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.NetworkRegistrationInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NtnCapabilityResolverTest extends TelephonyTest {
    private static final String TAG = "NtnCapabilityResolverTest";
    private static final int SUB_ID = 0;
    private static final String VISITING_PLMN = "00102";
    private static final String SATELLITE_PLMN = "00103";
    private static final String[] SATELLITE_PLMN_ARRAY = {SATELLITE_PLMN};

    private final int[] mSatelliteSupportedServices = {SERVICE_TYPE_SMS, SERVICE_TYPE_EMERGENCY};
    private final List<Integer> mSatelliteSupportedServiceList =
            Arrays.stream(mSatelliteSupportedServices).boxed().collect(Collectors.toList());
    @Mock private SatelliteController mMockSatelliteController;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        doReturn(Arrays.asList(SATELLITE_PLMN_ARRAY))
                .when(mMockSatelliteController).getSatellitePlmnList(anyInt());
        doReturn(mSatelliteSupportedServiceList).when(mMockSatelliteController)
                .getSupportedSatelliteServices(SUB_ID, SATELLITE_PLMN);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testResolveNTNCapability() {
        // Test resolving a satellite NetworkRegistrationInfo.
        NetworkRegistrationInfo satelliteNri = createNetworkRegistrationInfo(SATELLITE_PLMN);
        NetworkRegistrationInfo originalNri = new NetworkRegistrationInfo(satelliteNri);

        assertEquals(satelliteNri, originalNri);
        assertFalse(satelliteNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                satelliteNri.getAvailableServices().stream()
                .mapToInt(Integer::intValue)
                .toArray()));
        NtnCapabilityResolver.resolveNtnCapability(satelliteNri, SUB_ID);
        assertNotEquals(satelliteNri, originalNri);
        assertTrue(satelliteNri.isNonTerrestrialNetwork());
        assertTrue(Arrays.equals(mSatelliteSupportedServices,
                satelliteNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));

        // Test resolving a non-satellite NetworkRegistrationInfo.
        NetworkRegistrationInfo cellularNri = createNetworkRegistrationInfo(VISITING_PLMN);
        originalNri = new NetworkRegistrationInfo(cellularNri);

        assertEquals(cellularNri, originalNri);
        assertFalse(cellularNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                cellularNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
        NtnCapabilityResolver.resolveNtnCapability(cellularNri, SUB_ID);
        assertEquals(cellularNri, originalNri);
        assertFalse(cellularNri.isNonTerrestrialNetwork());
        assertFalse(Arrays.equals(mSatelliteSupportedServices,
                cellularNri.getAvailableServices().stream()
                        .mapToInt(Integer::intValue)
                        .toArray()));
    }

    private NetworkRegistrationInfo createNetworkRegistrationInfo(@NonNull String registeredPlmn) {
        List<Integer> availableServices = new ArrayList<>();
        availableServices.add(SERVICE_TYPE_DATA);
        CellIdentity cellIdentity = new CellIdentityGsm(0, 0, 0,
                0, "mcc", "mnc", "", "", new ArraySet<>());
        return new NetworkRegistrationInfo(DOMAIN_PS, TRANSPORT_TYPE_WWAN,
                REGISTRATION_STATE_ROAMING, NETWORK_TYPE_LTE, 0, false, availableServices,
                cellIdentity, registeredPlmn, false, 0, 0, 0);
    }
}
