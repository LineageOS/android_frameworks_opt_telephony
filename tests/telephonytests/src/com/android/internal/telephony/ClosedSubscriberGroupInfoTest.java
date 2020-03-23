/*
 * Copyright 2020 The Android Open Source Project
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

import android.os.Parcel;
import android.telephony.ClosedSubscriberGroupInfo;
import android.test.AndroidTestCase;

import org.junit.Test;

public class ClosedSubscriberGroupInfoTest extends AndroidTestCase {

    private static final boolean CSG_INDICATOR = true;
    private static final String HOME_NODEB_NAME = "MyHomeNodeB";
    private static final String HOME_NODEB_NAME_EMPTY = "";
    private static final int CSG_IDENTITY = 1234;

    @Test
    public void testConstructorNormalInput() {
        ClosedSubscriberGroupInfo csgInfo = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, HOME_NODEB_NAME, CSG_IDENTITY);

        assertEquals(CSG_INDICATOR, csgInfo.getCsgIndicator());
        assertEquals(HOME_NODEB_NAME, csgInfo.getHomeNodebName());
        assertEquals(CSG_IDENTITY, csgInfo.getCsgIdentity());
    }

    @Test
    public void testConstructorHomeNodebNameIsNull() {
        ClosedSubscriberGroupInfo csgInfo = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, null, CSG_IDENTITY);

        assertEquals(CSG_INDICATOR, csgInfo.getCsgIndicator());
        assertEquals(HOME_NODEB_NAME_EMPTY, csgInfo.getHomeNodebName());
        assertEquals(CSG_IDENTITY, csgInfo.getCsgIdentity());
    }

    @Test
    public void testEqualsSameValues() {
        ClosedSubscriberGroupInfo csgInfo1 = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, HOME_NODEB_NAME, CSG_IDENTITY);
        // homeNodebName is copied, same value with diff reference
        ClosedSubscriberGroupInfo csgInfo2 = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, new String(HOME_NODEB_NAME), CSG_IDENTITY);

        assertEquals(csgInfo1, csgInfo2);
    }

    @Test
    public void testEqualsHomeNodebNameIsNull() {
        ClosedSubscriberGroupInfo csgInfo1 = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, null /* homeNodebName */, CSG_IDENTITY);
        ClosedSubscriberGroupInfo csgInfo2 = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, ""/* homeNodebName */, CSG_IDENTITY);

        assertEquals(csgInfo1, csgInfo2);
    }

    @Test
    public void testParcel() {
        ClosedSubscriberGroupInfo csgInfo = new ClosedSubscriberGroupInfo(
                CSG_INDICATOR, HOME_NODEB_NAME, CSG_IDENTITY);
        Parcel p = Parcel.obtain();
        csgInfo.writeToParcel(p, 0);
        p.setDataPosition(0);

        ClosedSubscriberGroupInfo csgInfoFromParcel = ClosedSubscriberGroupInfo.CREATOR
                .createFromParcel(p);

        assertEquals(csgInfo, csgInfoFromParcel);
    }
}
