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

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class DataUtilsTest extends TelephonyTest {

    @Before
    public void setUp() throws Exception {
        logd("DataUtilsTest +Setup!");
        super.setUp(getClass().getSimpleName());
        logd("DataUtilsTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetGroupedNetworkRequestList() {
        NetworkRequestList requestList = new NetworkRequestList();

        int[] netCaps = new int[]{
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_EIMS
        };

        int requestId = 0;
        TelephonyNetworkRequest networkRequest;
        for (int netCap : netCaps) {
            networkRequest = new TelephonyNetworkRequest(new NetworkRequest(
                    new NetworkCapabilities.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                            .addCapability(netCap).build(), -1, requestId++,
                    NetworkRequest.Type.REQUEST), mPhone);
            requestList.add(networkRequest);
        }

        assertThat(requestList).hasSize(5);

        List<NetworkRequestList> requestListList =
                DataUtils.getGroupedNetworkRequestList(requestList);

        assertThat(requestListList).hasSize(3);
        requestList = requestListList.get(0);
        assertThat(requestList).hasSize(1);
        assertThat(requestList.get(0).hasCapability(
                NetworkCapabilities.NET_CAPABILITY_EIMS)).isTrue();

        requestList = requestListList.get(1);
        assertThat(requestList).hasSize(2);
        assertThat(requestList.get(0).hasCapability(
                NetworkCapabilities.NET_CAPABILITY_MMS)).isTrue();
        assertThat(requestList.get(1).hasCapability(
                NetworkCapabilities.NET_CAPABILITY_MMS)).isTrue();

        requestList = requestListList.get(2);
        assertThat(requestList).hasSize(2);
        assertThat(requestList.get(0).hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(requestList.get(1).hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
    }

}
