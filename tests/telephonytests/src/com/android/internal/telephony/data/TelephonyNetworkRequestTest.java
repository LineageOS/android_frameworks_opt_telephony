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

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.TrafficDescriptor;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TelephonyNetworkRequestTest extends TelephonyTest {

    private static final ApnSetting INTERNET_APN_SETTING = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("internet")
            .setApnName("internet")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    private static final ApnSetting MMS_APN_SETTING = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("mms")
            .setApnName("mms")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_MMS)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();
    private static final ApnSetting ENTERPRISE_APN_SETTING = new ApnSetting.Builder()
            .setId(2164)
            .setOperatorNumeric("12345")
            .setEntryName("enterprise")
            .setApnName("enterprise")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_ENTERPRISE)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

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
    public void testGetApnTypeNetworkCapability() {
        TelephonyNetworkRequest request = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)
                        .build(), mPhone);
        assertThat(request.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_SUPL);

        request = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                        .build(), mPhone);
        assertThat(request.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_FOTA);

        request = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                        .build(), mPhone);
        assertThat(request.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
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

    @Test
    public void testCanBeSatisfiedByApnDataProfile() {
        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        TelephonyNetworkRequest mmsRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .build(), mPhone);
        DataProfile internetDataProfile = new DataProfile.Builder()
                .setApnSetting(INTERNET_APN_SETTING)
                .build();
        DataProfile mmsDataProfile = new DataProfile.Builder()
                .setApnSetting(MMS_APN_SETTING)
                .build();

        assertThat(internetRequest.canBeSatisfiedBy(internetDataProfile)).isTrue();
        assertThat(internetRequest.canBeSatisfiedBy(mmsDataProfile)).isFalse();
        assertThat(mmsRequest.canBeSatisfiedBy(internetDataProfile)).isFalse();
        assertThat(mmsRequest.canBeSatisfiedBy(mmsDataProfile)).isTrue();
    }

    @Test
    public void testCanBeSatisfiedByEnterpriseDataProfile() {
        TelephonyNetworkRequest enterpriseRequest1 = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .build(), mPhone);
        TelephonyNetworkRequest enterpriseRequest2 = new TelephonyNetworkRequest(
                new NetworkRequest(new NetworkCapabilities()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .addEnterpriseId(2), ConnectivityManager.TYPE_NONE,
                        0, NetworkRequest.Type.REQUEST), mPhone);

        DataProfile enterpriseDataProfile1 = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1).getBytes()))
                .build();
        DataProfile enterpriseDataProfile2 = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 2).getBytes()))
                .build();

        assertThat(enterpriseRequest1.canBeSatisfiedBy(enterpriseDataProfile1)).isTrue();
        assertThat(enterpriseRequest1.canBeSatisfiedBy(enterpriseDataProfile2)).isFalse();
        assertThat(enterpriseRequest2.canBeSatisfiedBy(enterpriseDataProfile1)).isFalse();
        assertThat(enterpriseRequest2.canBeSatisfiedBy(enterpriseDataProfile2)).isTrue();
    }

    @Test
    public void testCanBeSatisfiedByEnterpriseApnDataProfile() {
        TelephonyNetworkRequest enterpriseRequest1 = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone);
        TelephonyNetworkRequest enterpriseRequest2 = new TelephonyNetworkRequest(
                new NetworkRequest(new NetworkCapabilities()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .addEnterpriseId(2), ConnectivityManager.TYPE_NONE,
                        0, NetworkRequest.Type.REQUEST), mPhone);
        TelephonyNetworkRequest internetRequest = new TelephonyNetworkRequest(
                new NetworkRequest(new NetworkCapabilities()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                        ConnectivityManager.TYPE_NONE,
                        0, NetworkRequest.Type.REQUEST), mPhone);

        DataProfile enterpriseDataProfile = new DataProfile.Builder()
                .setApnSetting(ENTERPRISE_APN_SETTING)
                .build();
        DataProfile internetDataProfile = new DataProfile.Builder()
                .setApnSetting(INTERNET_APN_SETTING)
                .build();

        assertThat(enterpriseRequest1.canBeSatisfiedBy(enterpriseDataProfile)).isTrue();
        assertThat(enterpriseRequest1.canBeSatisfiedBy(internetDataProfile)).isFalse();
        assertThat(enterpriseRequest2.canBeSatisfiedBy(enterpriseDataProfile)).isTrue();
        assertThat(enterpriseRequest2.canBeSatisfiedBy(internetDataProfile)).isFalse();
        assertThat(internetRequest.canBeSatisfiedBy(enterpriseDataProfile)).isFalse();
        assertThat(internetRequest.canBeSatisfiedBy(internetDataProfile)).isTrue();
    }

    @Test
    public void testCanBeSatisfiedByUrllcDataProfile() {
        TelephonyNetworkRequest urllcRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .build(), mPhone);

        TelephonyNetworkRequest embbRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .build(), mPhone);

        DataProfile urllcDataProfile = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "PRIORITIZE_LATENCY", 1)
                        .getBytes()))
                .build();

        assertThat(urllcRequest.canBeSatisfiedBy(urllcDataProfile)).isTrue();
        assertThat(embbRequest.canBeSatisfiedBy(urllcDataProfile)).isFalse();
    }

    @Test
    public void testCanBeSatisfiedByEmbbDataProfile() {
        TelephonyNetworkRequest urllcRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                        .build(), mPhone);

        TelephonyNetworkRequest embbRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .build(), mPhone);

        DataProfile embbDataProfile = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "PRIORITIZE_BANDWIDTH", 1)
                        .getBytes()))
                .build();

        assertThat(embbRequest.canBeSatisfiedBy(embbDataProfile)).isTrue();
        assertThat(urllcRequest.canBeSatisfiedBy(embbDataProfile)).isFalse();
    }

    @Test
    public void testCanBeSatisfiedByCbsDataProfile() {
        TelephonyNetworkRequest cbsRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_CBS)
                        .build(), mPhone);

        TelephonyNetworkRequest embbRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                        .build(), mPhone);

        DataProfile cbsDataProfile = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "CBS")
                        .getBytes()))
                .build();

        assertThat(cbsRequest.canBeSatisfiedBy(cbsDataProfile)).isTrue();
        assertThat(embbRequest.canBeSatisfiedBy(cbsDataProfile)).isFalse();
    }
}
