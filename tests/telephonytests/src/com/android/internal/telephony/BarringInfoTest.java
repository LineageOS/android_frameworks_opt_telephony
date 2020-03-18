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
package com.android.internal.telephony;

import static android.telephony.BarringInfo.BarringServiceInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.telephony.BarringInfo;
import android.util.SparseArray;

import org.junit.Test;

/** Unit test for {@link android.telephony.BarringInfo}. */
public class BarringInfoTest {

    public static final int CONDITIONAL_BARRING_TIME_SECONDS = 20;
    public static final int CONDITIONAL_BARRING_FACTOR_PERCENT = 50;


    private static final int[] sServices = new int[] {
            BarringInfo.BARRING_SERVICE_TYPE_CS_SERVICE,
            BarringInfo.BARRING_SERVICE_TYPE_PS_SERVICE,
            BarringInfo.BARRING_SERVICE_TYPE_CS_VOICE,
            BarringInfo.BARRING_SERVICE_TYPE_MO_SIGNALLING,
            BarringInfo.BARRING_SERVICE_TYPE_MO_DATA,
            BarringInfo.BARRING_SERVICE_TYPE_CS_FALLBACK,
            BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE,
            BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO,
            BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY,
            BarringInfo.BARRING_SERVICE_TYPE_SMS,
    };

    /** Return a dummy set of barring info */
    private static SparseArray<BarringServiceInfo> getBarringServiceInfos() {
        return getBarringServiceInfos(false);
    }

    /** Return a dummy set of barring info
     *
     * @param isConditionallyBarred set the flag for whether the conditionally barred service has
     *        been evaluated and is actually barred based on the conditional barring parameters.
     */
    private static SparseArray<BarringServiceInfo>
            getBarringServiceInfos(boolean isConditionallyBarred) {
        SparseArray<BarringServiceInfo> serviceInfos = new SparseArray<>();
        serviceInfos.put(BarringInfo.BARRING_SERVICE_TYPE_MO_DATA,
                new BarringServiceInfo(BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL, false, 0, 0));
        serviceInfos.put(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO,
                new BarringServiceInfo(
                        BarringServiceInfo.BARRING_TYPE_CONDITIONAL, isConditionallyBarred,
                        CONDITIONAL_BARRING_FACTOR_PERCENT, CONDITIONAL_BARRING_TIME_SECONDS));
        return serviceInfos;
    }

    /** Test that parceling works correctly */
    @Test
    public void testParcel() {
        BarringInfo info = new BarringInfo(null, getBarringServiceInfos());

        Parcel parcel = Parcel.obtain();
        info.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);

        BarringInfo fromParcel = BarringInfo.CREATOR.createFromParcel(parcel);

        assertEquals(fromParcel, info);
    }

    /** Test that an empty constructor returns valid barring service info that's all not barred */
    @Test
    public void testEmptyConstructor() {
        BarringInfo b = new BarringInfo();
        for (int service : sServices) {
            BarringServiceInfo bsi = b.getBarringServiceInfo(service);
            assertNotNull(bsi);
            assertEquals(bsi.getBarringType(), BarringServiceInfo.BARRING_TYPE_UNKNOWN);
            assertFalse(bsi.isBarred());
        }
    }

    /** Test that barring service info is stored properly by the constructor */
    @Test
    public void testBarringService() {
        BarringInfo b = new BarringInfo(null, getBarringServiceInfos());

        // Check that the MO data barring info matches the info provided in getBarringServiceInfos()
        BarringServiceInfo bsi = b.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MO_DATA);
        assertEquals(bsi.getBarringType(), BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL);

        // Check that the MMTEL barring info matches the info provided in getBarringServiceInfos()
        bsi = b.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO);
        assertEquals(bsi.getBarringType(), BarringServiceInfo.BARRING_TYPE_CONDITIONAL);
        assertFalse(bsi.isConditionallyBarred());
        assertEquals(bsi.getConditionalBarringFactor(), CONDITIONAL_BARRING_FACTOR_PERCENT);
        assertEquals(bsi.getConditionalBarringTimeSeconds(), CONDITIONAL_BARRING_TIME_SECONDS);

        // Because BarringInfo is available, services that aren't reported as barred are
        // automatically reported as unbarred.
        bsi = b.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_SMS);
        assertEquals(bsi.getBarringType(), BarringServiceInfo.BARRING_TYPE_NONE);
        assertFalse(bsi.isConditionallyBarred());
        assertEquals(bsi.getConditionalBarringFactor(), 0);
        assertEquals(bsi.getConditionalBarringTimeSeconds(), 0);
    }

    /** Test that equality checks are correctly implemented */
    @Test
    public void testEquals() {
        BarringInfo lhs = new BarringInfo(null, getBarringServiceInfos(false));
        BarringInfo rhs = new BarringInfo(null, getBarringServiceInfos(false));
        assertEquals(lhs, rhs);

        rhs = new BarringInfo(null, getBarringServiceInfos(true));
        assertNotEquals(lhs, rhs);
    }

    /** Test that when conditional barring is active, the service is considered barred */
    @Test
    public void testConditionalBarringCheck() {
        BarringInfo condInfo = new BarringInfo(null, getBarringServiceInfos(false));
        assertFalse(condInfo.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO)
                .isBarred());

        condInfo = new BarringInfo(null, getBarringServiceInfos(true));
        assertTrue(condInfo.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VIDEO)
                .isBarred());
    }
}
