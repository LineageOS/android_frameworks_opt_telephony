/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

import org.junit.Test;

public class IccLogicalChannelRequestTest extends TestCase {

    private static final int SUB_ID = 0;
    private static final int SLOT_INDEX = 0;
    private static final int PORT_INDEX = 0;
    private static final String CALLING_PKG = "test_app";
    private static final String AID = "aid";
    private static final int P2 = 0;
    private static final int CHANNEL = 1;
    private static final IBinder BINDER = new Binder();

    @Test
    @SmallTest
    public void testDefaultConstructor_shouldReturnDefaultValues() {
        IccLogicalChannelRequest defaultRequest = new IccLogicalChannelRequest();

        assertThat(defaultRequest.subId).isEqualTo(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        assertThat(defaultRequest.slotIndex).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(defaultRequest.portIndex).isEqualTo(0 /* TelephonyManager.DEFAULT_PORT_INDEX */);
        assertThat(defaultRequest.callingPackage).isNull();
        assertThat(defaultRequest.aid).isNull();
        assertThat(defaultRequest.p2).isEqualTo(0);
        assertThat(defaultRequest.channel).isEqualTo(-1);
        assertThat(defaultRequest.binder).isNull();
    }

    @Test
    @SmallTest
    public void testConstructor_withValidParmas_shouldReturnValidValues() {
        IccLogicalChannelRequest request =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, BINDER);

        assertThat(request.subId).isEqualTo(SUB_ID);
        assertThat(request.slotIndex).isEqualTo(SLOT_INDEX);
        assertThat(request.portIndex).isEqualTo(PORT_INDEX);
        assertThat(request.callingPackage).isEqualTo(CALLING_PKG);
        assertThat(request.aid).isEqualTo(AID);
        assertThat(request.p2).isEqualTo(P2);
        assertThat(request.channel).isEqualTo(CHANNEL);
        assertThat(request.binder).isEqualTo(BINDER);
    }

    @Test
    @SmallTest
    public void testEquals_sameInput_shouldReturnTrue() {
        IccLogicalChannelRequest request1 =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, BINDER);
        IccLogicalChannelRequest request2 =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, BINDER);

        assertThat(request1).isEqualTo(request2);
    }

    @Test
    @SmallTest
    public void testEquals_differentBinder_shouldReturnFalse() {
        IccLogicalChannelRequest request1 =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, BINDER);
        // request2 has same input as request1 but new Binder object
        IccLogicalChannelRequest request2 =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, new Binder());

        assertThat(request1.equals(request2)).isFalse();
    }

    @Test
    @SmallTest
    public void testParcel() {
        IccLogicalChannelRequest oldRequest =
                createIccLogicalChannelRequest(SUB_ID, SLOT_INDEX, PORT_INDEX, CALLING_PKG, AID, P2,
                        CHANNEL, BINDER);

        Parcel p = Parcel.obtain();
        oldRequest.writeToParcel(p, /*flags=*/0);
        p.setDataPosition(0);
        IccLogicalChannelRequest newRequest =
                IccLogicalChannelRequest.CREATOR.createFromParcel(p);

        assertThat(newRequest).isEqualTo(oldRequest);
    }

    private static IccLogicalChannelRequest createIccLogicalChannelRequest(int subId, int slotIndex,
            int portIndex, String callingPackage, String aid, int p2, int channel, IBinder binder) {
        IccLogicalChannelRequest request = new IccLogicalChannelRequest();
        request.subId = subId;
        request.slotIndex = slotIndex;
        request.portIndex = portIndex;
        request.callingPackage = callingPackage;
        request.aid = aid;
        request.p2 = p2;
        request.channel = channel;
        request.binder = binder;
        return request;
    }
}
