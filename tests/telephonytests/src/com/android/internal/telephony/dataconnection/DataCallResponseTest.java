/*
 * Copyright 2017 The Android Open Source Project
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

import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_ADDRESS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_DNS;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_GATEWAY;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_IFNAME;
import static com.android.internal.telephony.dataconnection.DcTrackerTest.FAKE_PCSCF_ADDRESS;

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.os.Parcel;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.EpsQos;
import android.telephony.data.TrafficDescriptor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;

public class DataCallResponseTest extends AndroidTestCase {
    public static final String FAKE_DNN = "FAKE_DNN";
    public static final byte[] FAKE_OS_APP_ID = {1, 2, 3, 4};
    public static final byte[] FAKE_OS_APP_ID_2 = {5, 6, 8, 9};

    @SmallTest
    public void testParcel() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(1)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1440)
                .setDefaultQos(new EpsQos())
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(
                        Arrays.asList(new TrafficDescriptor(FAKE_DNN, FAKE_OS_APP_ID)))
                .build();

        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        DataCallResponse newResponse = new DataCallResponse(p);
        assertEquals(response, newResponse);
    }

    @SmallTest
    public void testEquals() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(1)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1400)
                .setTrafficDescriptors(
                        Arrays.asList(new TrafficDescriptor(FAKE_DNN, FAKE_OS_APP_ID)))
                .build();

        DataCallResponse response1 = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(1)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(
                        Arrays.asList(InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1440)
                .setMtuV6(1400)
                .setTrafficDescriptors(
                        Arrays.asList(new TrafficDescriptor(FAKE_DNN, FAKE_OS_APP_ID)))
                .build();

        assertEquals(response, response);
        assertEquals(response, response1);

        DataCallResponse response2 = new DataCallResponse.Builder()
                .setCause(1)
                .setRetryDurationMillis(-1L)
                .setId(1)
                .setLinkStatus(3)
                .setProtocolType(ApnSetting.PROTOCOL_IP)
                .setInterfaceName(FAKE_IFNAME)
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(FAKE_ADDRESS), 0)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_DNS),
                        InetAddresses.parseNumericAddress(FAKE_DNS)))
                .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress(FAKE_GATEWAY)))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS),
                        InetAddresses.parseNumericAddress(FAKE_PCSCF_ADDRESS)))
                .setMtuV4(1441)
                .setMtuV6(1440)
                .setTrafficDescriptors(
                        Arrays.asList(new TrafficDescriptor("FAKE_DNN_2", FAKE_OS_APP_ID_2)))
                .build();

        assertNotSame(response1, response2);
        assertNotSame(response1, null);
        assertNotSame(response1, new String[1]);
    }
}
