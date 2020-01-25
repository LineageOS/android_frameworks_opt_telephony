/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DcRequestTest extends TelephonyTest {

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void whenNetworkRequestInternetThenPriorityZero() {
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertEquals(0, dcRequest.priority);
    }

    @Test
    public void whenNetworkRequestMmsThenPriorityTwo() {
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertEquals(2, dcRequest.priority);
    }

    @Test
    public void whenNetworkRequestMcxThenPriorityThree() {
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                        //Testing out multiple transport types here
                        .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MCX)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertEquals(3, dcRequest.priority);
    }

    @Test
    public void whenNetworkRequestNotCellularThenDcRequestIsNull() {
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MCX)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertNull(dcRequest);
    }

    @Test
    public void whenNetworkRequestHasNoTransportThenPriorityStays() {
        //This seems like an invalid case that should be handled differently.
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MCX)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertEquals(3, dcRequest.priority);
    }

    @Test
    public void whenNetworkRequestNotCellularWithTelephonySpecifierThenDcRequestIsNull() {
        TelephonyNetworkSpecifier telephonyNetworkSpecifier =
                new TelephonyNetworkSpecifier.Builder().setSubscriptionId(5).build();

        //This seems like an invalid case that should be handled differently.
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                        .setNetworkSpecifier(telephonyNetworkSpecifier)
                        .build();

        DcRequest dcRequest = DcRequest.create(request);

        assertNull(dcRequest);
    }
}
