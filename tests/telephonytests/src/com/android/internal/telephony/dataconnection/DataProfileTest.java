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

package com.android.internal.telephony.dataconnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import android.os.Parcel;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataProfile;

import com.android.internal.telephony.RILConstants;

import org.junit.Test;

public class DataProfileTest {

    private ApnSetting mApn1 = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("44010")
            .setEntryName("sp-mode")
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
            .setOperatorNumeric("44010")
            .setEntryName("sp-mode")
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
            .setOperatorNumeric("44010")
            .setEntryName("sp-mode")
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
            .setOperatorNumeric("44010")
            .setEntryName("sp-mode")
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

    @Test
    public void testCreateFromApnSetting() throws Exception {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn1)
                .setPreferred(false)
                .build();
        assertEquals(mApn1.getProfileId(), dp.getProfileId());
        assertEquals(mApn1.getApnName(), dp.getApn());
        assertEquals(mApn1.getProtocol(), dp.getProtocolType());
        assertEquals(RILConstants.SETUP_DATA_AUTH_PAP_CHAP, dp.getAuthType());
        assertEquals(mApn1.getUser(), dp.getUserName());
        assertEquals(mApn1.getPassword(), dp.getPassword());
        assertEquals(0, dp.getType());  // TYPE_COMMON
        assertEquals(mApn1.getWaitTime(), dp.getWaitTime());
        assertEquals(mApn1.isEnabled(), dp.isEnabled());
        assertFalse(dp.isPersistent());
        assertFalse(dp.isPreferred());
    }

    @Test
    public void testCreateFromApnSettingWithNetworkTypeBitmask() throws Exception {
        DataProfile dp = new DataProfile.Builder()
                .setApnSetting(mApn3)
                .setPreferred(false)
                .build();
        assertEquals(mApn3.getProfileId(), dp.getProfileId());
        assertEquals(mApn3.getApnName(), dp.getApn());
        assertEquals(mApn3.getProtocol(), dp.getProtocolType());
        assertEquals(RILConstants.SETUP_DATA_AUTH_PAP_CHAP, dp.getAuthType());
        assertEquals(mApn3.getUser(), dp.getUserName());
        assertEquals(mApn3.getPassword(), dp.getPassword());
        assertEquals(0, dp.getType());  // TYPE_COMMON
        assertEquals(mApn3.getWaitTime(), dp.getWaitTime());
        assertEquals(mApn3.isEnabled(), dp.isEnabled());
        int expectedBearerBitmap = mApn3.getNetworkTypeBitmask();
        assertEquals(expectedBearerBitmap, dp.getBearerBitmask());
        dp = new DataProfile.Builder()
                .setApnSetting(mApn4)
                .setPreferred(false)
                .build();
        assertEquals(2, dp.getType());  // TYPE_3GPP2
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
        assertEquals(dp1, dp2);

        dp2 = new DataProfile.Builder()
                .setApnSetting(mApn2)
                .setPreferred(false)
                .build();
        assertNotEquals(dp1, dp2);
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

        assertEquals(dp, fromParcel);

        parcel.recycle();
    }
}
