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

package com.android.internal.telephony.data;

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
import android.telephony.data.Qos;
import android.telephony.data.TrafficDescriptor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.ArrayList;
import java.util.Arrays;

public class DataCallResponseTest extends AndroidTestCase {
    public static final String FAKE_DNN = "FAKE_DNN";
    public static final String FAKE_DNN_2 = "FAKE_DNN_2";
    // 97a498e3fc925c9489860333d06e4e470a454e5445525052495345.
    // [OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1]
    public static final byte[] FAKE_OS_APP_ID = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 10, 69, 78, 84, 69,
            82, 80, 82, 73, 83, 69};
    // 97a498e3fc925c9489860333d06e4e470a454e544552505249534532.
    // [OsAppId.ANDROID_OS_ID, "ENTERPRISE", 2]
    public static final byte[] FAKE_OS_APP_ID_2 = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 10, 69, 78, 84, 69,
            82, 80, 82, 73, 83, 69, 50};

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
                .setDefaultQos(new EpsQos(
                        new Qos.QosBandwidth(-1, -1), new Qos.QosBandwidth(-1, -1), -1))
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
    public void testEqualsAndHashCode() {
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
        assertEquals(response.hashCode(), response1.hashCode());

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
                .setTrafficDescriptors(Arrays.asList(
                        new TrafficDescriptor(FAKE_DNN, FAKE_OS_APP_ID),
                        new TrafficDescriptor(FAKE_DNN_2, FAKE_OS_APP_ID_2)))
                .build();

        assertNotSame(response1, response2);
        assertNotSame(response1, null);
        assertNotSame(response1, new String[1]);
        assertNotSame(response1.hashCode(), response2.hashCode());

        DataCallResponse response3 = new DataCallResponse.Builder()
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
                .setTrafficDescriptors(Arrays.asList(
                        new TrafficDescriptor(FAKE_DNN_2, FAKE_OS_APP_ID_2),
                        new TrafficDescriptor(FAKE_DNN, FAKE_OS_APP_ID)))
                .build();

        assertEquals(response2, response3);
        assertEquals(response2.hashCode(), response3.hashCode());
    }
}
