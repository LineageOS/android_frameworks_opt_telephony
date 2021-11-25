/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TelephonyNetworkRequestTest extends TelephonyTest {

    @Before
    public void setUp() throws Exception {
        logd("TelephonyNetworkRequestTest +Setup!");
        super.setUp(getClass().getSimpleName());

        logd("TelephonyNetworkRequestTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetNativeRequest() {
        NetworkRequest nativeRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest internetRequest =
                new TelephonyNetworkRequest(nativeRequest, mPhone);
        assertThat(internetRequest.getNativeNetworkRequest()).isEqualTo(nativeRequest);
    }

    @Test
    public void testPriority() {
        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        TelephonyNetworkRequest imsRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone);

        assertThat(internetRequest.getPriority()).isEqualTo(20);
        assertThat(imsRequest.getPriority()).isEqualTo(40);
    }

    @Test
    public void testGetNetworkSpecifier() {
        TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier.Builder().setSubscriptionId(5).build();

        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .setNetworkSpecifier(telephonyNetworkSpecifier)
                        .build(), mPhone);
        assertThat(internetRequest.getNetworkSpecifier()).isEqualTo(telephonyNetworkSpecifier);
    }

    @Test
    public void testGetCapabilities() {
        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build(), mPhone);
        assertThat(internetRequest.getCapabilities()).isEqualTo(
                new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                        NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED,
                        NetworkCapabilities.NET_CAPABILITY_TRUSTED,
                        NetworkCapabilities.NET_CAPABILITY_NOT_VPN,
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED});
        assertThat(internetRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isTrue();
        assertThat(internetRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                .isTrue();
        assertThat(internetRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
                .isTrue();
        assertThat(internetRequest.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isTrue();
        assertThat(internetRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS))
                .isFalse();
    }

    @Test
    public void testGetHighestPriorityNetworkCapability() {
        TelephonyNetworkRequest request = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)
                        .build(), mPhone);
        assertThat(request.getHighestPriorityNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_SUPL);
    }

    @Test
    public void testCanBeSatisfiedBy() {
        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        NetworkCapabilities caps = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        assertThat(internetRequest.canBeSatisfiedBy(caps)).isTrue();
    }
}
