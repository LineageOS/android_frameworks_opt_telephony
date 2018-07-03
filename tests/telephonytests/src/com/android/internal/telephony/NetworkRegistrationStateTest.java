/*
 * Copyright 2018 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.CellIdentityLte;
import android.telephony.NetworkRegistrationState;
import android.telephony.TelephonyManager;

import org.junit.Test;

/** Unit tests for {@link NetworkRegistrationState}. */

public class NetworkRegistrationStateTest {

    @Test
    @SmallTest
    public void testParcel() {
        NetworkRegistrationState nrs = new NetworkRegistrationState(
                NetworkRegistrationState.DOMAIN_CS,
                TransportType.WWAN,
                NetworkRegistrationState.REG_STATE_HOME,
                TelephonyManager.NETWORK_TYPE_LTE,
                0,
                false,
                new int[]{NetworkRegistrationState.SERVICE_TYPE_DATA},
                new CellIdentityLte());

        Parcel p = Parcel.obtain();
        nrs.writeToParcel(p, 0);
        p.setDataPosition(0);

        NetworkRegistrationState newNrs = NetworkRegistrationState.CREATOR.createFromParcel(p);
        assertEquals(nrs, newNrs);
    }
}
