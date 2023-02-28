/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionInfoTest {
    private SubscriptionInfo mSubscriptionInfoUT;
    private static final String[] EHPLMNS = new String[] {"310999", "310998"};
    private static final String[] HPLMNS = new String[] {"310001"};

    @After
    public void tearDown() throws Exception {
        mSubscriptionInfoUT = null;
    }

    @Before
    public void setUp() throws Exception {
        mSubscriptionInfoUT = new SubscriptionInfo.Builder()
                .setId(1)
                .setIccId("890126042XXXXXXXXXXX")
                .setSimSlotIndex(0)
                .setDisplayName("T-mobile")
                .setCarrierName("T-mobile")
                .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_CARRIER_ID)
                .setIconTint(255)
                .setNumber("12345")
                .setDataRoaming(SubscriptionManager.DATA_ROAMING_DISABLE)
                .setMcc("310")
                .setMnc("260")
                .setEhplmns(EHPLMNS)
                .setHplmns(HPLMNS)
                .setCountryIso("us")
                .build();
    }

    @Test
    public void testSubProperties() {
        assertThat(mSubscriptionInfoUT.getMcc()).isEqualTo(310);
        assertThat(mSubscriptionInfoUT.getMccString()).isEqualTo("310");
        assertThat(mSubscriptionInfoUT.getMnc()).isEqualTo(260);
        assertThat(mSubscriptionInfoUT.getMncString()).isEqualTo("260");
        assertThat(mSubscriptionInfoUT.getNumber()).isEqualTo("12345");
        assertThat(mSubscriptionInfoUT.getDataRoaming()).isEqualTo(0);
        assertThat(mSubscriptionInfoUT.getDisplayName().toString()).isEqualTo("T-mobile");
        assertThat(mSubscriptionInfoUT.getCarrierName().toString()).isEqualTo("T-mobile");
        assertThat(mSubscriptionInfoUT.getCountryIso()).isEqualTo("us");
        assertThat(mSubscriptionInfoUT.getIconTint()).isEqualTo(255);
        assertThat(mSubscriptionInfoUT.getDisplayNameSource()).isEqualTo(0);
        assertThat(mSubscriptionInfoUT.getSubscriptionId()).isEqualTo(1);
        assertThat(mSubscriptionInfoUT.getSimSlotIndex()).isEqualTo(0);
        assertThat(mSubscriptionInfoUT.getIccId()).isEqualTo("890126042XXXXXXXXXXX");
    }

    @Test
    public void testParcelUnparcel() {
        Parcel p = Parcel.obtain();
        mSubscriptionInfoUT.writeToParcel(p, 0);
        p.setDataPosition(0);
        SubscriptionInfo copy = SubscriptionInfo.CREATOR.createFromParcel(p);
        assertThat(mSubscriptionInfoUT).isEqualTo(copy);
    }

    @Test
    public void testEquals() {
        SubscriptionInfo copiedInfo = new SubscriptionInfo.Builder(mSubscriptionInfoUT).build();
        SubscriptionInfo differentDisplayName = new SubscriptionInfo.Builder(mSubscriptionInfoUT)
                .setDisplayName("Different display name")
                .build();
        SubscriptionInfo differentSubId = new SubscriptionInfo.Builder(mSubscriptionInfoUT)
                .setId(1234)
                .build();

        assertThat(mSubscriptionInfoUT).isEqualTo(copiedInfo);
        assertThat(mSubscriptionInfoUT).isNotEqualTo(differentDisplayName);
        assertThat(mSubscriptionInfoUT).isNotEqualTo(differentSubId);
    }
}
