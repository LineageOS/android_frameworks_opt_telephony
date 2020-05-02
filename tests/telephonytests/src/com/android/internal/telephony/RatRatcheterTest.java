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
package com.android.internal.telephony;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/** Tests for RatRatcheter. */
public class RatRatcheterTest extends TelephonyTest {

    private ServiceState mServiceState;
    private PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mServiceState = new ServiceState();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testUpdateBandwidthsSuccess() {
        int[] bandwidths = new int[] {1400, 5000};
        mServiceState.setCellBandwidths(new int[] {5000});

        boolean updated = RatRatcheter.updateBandwidths(bandwidths, mServiceState);

        assertTrue(updated);
        assertTrue(Arrays.equals(mServiceState.getCellBandwidths(), bandwidths));
    }

    @Test
    public void testUpdateBandwidthsFailure() {
        int[] originalBandwidths = {5000, 10000};
        int[] newBandwidths = {1400, 5000};
        mServiceState.setCellBandwidths(originalBandwidths);

        boolean updated = RatRatcheter.updateBandwidths(newBandwidths, mServiceState);

        assertFalse(updated);
        assertTrue(Arrays.equals(mServiceState.getCellBandwidths(), originalBandwidths));
    }

    @Test
    public void testUpdateBandwidthsNull() {
        int[] originalBandwidths = {5000, 10000};
        mServiceState.setCellBandwidths(originalBandwidths);

        boolean updated = RatRatcheter.updateBandwidths(null, mServiceState);

        assertFalse(updated);
        assertTrue(Arrays.equals(mServiceState.getCellBandwidths(), originalBandwidths));
    }

    private NetworkRegistrationInfo createNetworkRegistrationInfo(
            int domain, int accessNetworkTechnology, boolean isUsingCarrierAggregation) {

        LteVopsSupportInfo lteVopsSupportInfo =
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED);

        return new NetworkRegistrationInfo(
                domain,  // domain
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,  // transportType
                0,  // registrationState
                accessNetworkTechnology,  // accessNetworkTechnology
                0,  // rejectCause
                false,  // emergencyOnly
                null,  // availableServices
                null,  // cellIdentity
                null,  // rplmn
                0,  // maxDataCalls
                false,  // isDcNrRestricted
                false,  // isNrAvailable
                false,  // isEndcAvailable
                lteVopsSupportInfo,  // lteVopsSupportInfo
                isUsingCarrierAggregation);  // isUsingCarrierAggregation
    }

    private void setNetworkRegistrationInfo(ServiceState ss, int accessNetworkTechnology) {

        NetworkRegistrationInfo nri1;
        NetworkRegistrationInfo nri2;

        boolean isUsingCarrierAggregation = false;

        if (accessNetworkTechnology == TelephonyManager.NETWORK_TYPE_LTE_CA) {
            isUsingCarrierAggregation = true;
        }

        nri1 = createNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                accessNetworkTechnology, isUsingCarrierAggregation);
        nri2 = createNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                accessNetworkTechnology, isUsingCarrierAggregation);

        ss.addNetworkRegistrationInfo(nri1);
        ss.addNetworkRegistrationInfo(nri2);
    }

    @Test
    public void testRatchetIsFamily() {
        ServiceState oldSS = new ServiceState();
        ServiceState newSS = new ServiceState();

        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putStringArray(CarrierConfigManager.KEY_RATCHET_RAT_FAMILIES,
                new String[]{"14,19"});

        setNetworkRegistrationInfo(oldSS, TelephonyManager.NETWORK_TYPE_LTE_CA);
        setNetworkRegistrationInfo(newSS, TelephonyManager.NETWORK_TYPE_LTE);

        RatRatcheter ratRatcheter = new RatRatcheter(mPhone);
        ratRatcheter.ratchet(oldSS, newSS, false);

        assertTrue(newSS.isUsingCarrierAggregation());
    }

    @Test
    public void testRatchetIsNotFamily() {
        ServiceState oldSS = new ServiceState();
        ServiceState newSS = new ServiceState();

        mBundle = mContextFixture.getCarrierConfigBundle();
        mBundle.putStringArray(CarrierConfigManager.KEY_RATCHET_RAT_FAMILIES, new String[]{});

        setNetworkRegistrationInfo(oldSS, TelephonyManager.NETWORK_TYPE_LTE_CA);
        setNetworkRegistrationInfo(newSS, TelephonyManager.NETWORK_TYPE_LTE);

        RatRatcheter ratRatcheter = new RatRatcheter(mPhone);
        ratRatcheter.ratchet(oldSS, newSS, false);

        assertFalse(newSS.isUsingCarrierAggregation());
    }
}
