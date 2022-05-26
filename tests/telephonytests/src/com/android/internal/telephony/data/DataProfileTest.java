/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.os.Parcel;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;
import android.telephony.data.TrafficDescriptor;

import org.junit.Test;

public class DataProfileTest {

    private ApnSetting mApn1 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
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

    private ApnSetting mApn2 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IP)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(111)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    private ApnSetting mApn3 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IP)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(276600)
            .setProfileId(1234)
            .setMaxConns(111)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    private ApnSetting mApn4 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IP)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(10360)
            .setProfileId(1234)
            .setMaxConns(111)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    // Disabled APN
    private ApnSetting mApn5 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(false)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();


    @Test
    public void testCreateFromApnSetting() throws Exception {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();
        assertThat(dp.getProfileId()).isEqualTo(mApn1.getProfileId());
        assertThat(dp.getApn()).isEqualTo(mApn1.getApnName());
        assertThat(dp.getProtocolType()).isEqualTo(mApn1.getProtocol());
        assertThat(dp.getAuthType()).isEqualTo(ApnSetting.AUTH_TYPE_PAP_OR_CHAP);
        assertThat(dp.getUserName()).isEqualTo(mApn1.getUser());
        assertThat(dp.getPassword()).isEqualTo(mApn1.getPassword());
        assertThat(dp.getType()).isEqualTo(DataProfile.TYPE_COMMON);
        assertThat(dp.getWaitTime()).isEqualTo(mApn1.getWaitTime());
        assertThat(dp.isEnabled()).isEqualTo(mApn1.isEnabled());
        assertThat(dp.isPersistent()).isFalse();
        assertThat(dp.isPreferred()).isFalse();
    }

    @Test
    public void testCreateFromApnSettingWithNetworkTypeBitmask() throws Exception {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn3)
                .setPreferred(false)
                .build();
        assertThat(dp.getProfileId()).isEqualTo(mApn3.getProfileId());
        assertThat(dp.getApn()).isEqualTo(mApn3.getApnName());
        assertThat(dp.getProtocolType()).isEqualTo(mApn3.getProtocol());
        assertThat(dp.getAuthType()).isEqualTo(ApnSetting.AUTH_TYPE_PAP_OR_CHAP);
        assertThat(dp.getUserName()).isEqualTo(mApn3.getUser());
        assertThat(dp.getPassword()).isEqualTo(mApn3.getPassword());
        assertThat(dp.getType()).isEqualTo(DataProfile.TYPE_COMMON);
        assertThat(dp.getWaitTime()).isEqualTo(mApn3.getWaitTime());
        assertThat(dp.isEnabled()).isEqualTo(mApn3.isEnabled());
        int expectedBearerBitmap = mApn3.getNetworkTypeBitmask();
        assertThat(dp.getBearerBitmask()).isEqualTo(expectedBearerBitmap);
        dp = new DataProfile.Builder()
                .setApnSetting(mApn4)
                .setPreferred(false)
                .build();
        assertThat(dp.getType()).isEqualTo(2);  // TYPE_3GPP2
    }

    @Test
    public void testCanSatisfy() {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();

        assertThat(dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(dp.canSatisfy(new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_SUPL})).isTrue();
        assertThat(dp.canSatisfy(new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_SUPL,
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED})).isTrue();
        assertThat(dp.canSatisfy(NetworkCapabilities.NET_CAPABILITY_MMS)).isFalse();
        assertThat(dp.canSatisfy(new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_MMS})).isFalse();
    }

    @Test
    public void testEquals() throws Exception {
        DataProfile dp1 = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();
        DataProfile dp2 = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();
        assertThat(dp1).isEqualTo(dp2);

        dp2 = new DataProfile.Builder()
                .setApnSetting(mApn2)
                .setPreferred(false)
                .build();
        assertThat(dp1).isNotEqualTo(dp2);
    }

    @Test
    public void testParcel() {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();

        Parcel parcel = Parcel.obtain();
        dp.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);

        DataProfile fromParcel = DataProfile.CREATOR.createFromParcel(parcel);

        assertThat(fromParcel).isEqualTo(dp);

        parcel.recycle();
    }

    @Test
    public void testIsEnabled() {
        TrafficDescriptor td = new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1).getBytes());

        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn5)
                .setPreferred(false)
                .build();
        assertThat(dp.isEnabled()).isFalse();

        dp = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .build();
        assertThat(dp.isEnabled()).isTrue();

        dp = new DataProfile.Builder()
                .setTrafficDescriptor(td)
                .build();
        assertThat(dp.isEnabled()).isTrue();
    }
}
