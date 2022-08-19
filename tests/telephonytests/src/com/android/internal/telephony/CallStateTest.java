/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.telephony.CallQuality;
import android.telephony.CallState;
import android.telephony.PreciseCallState;
import android.telephony.TelephonyManager;
import android.telephony.ims.ImsCallProfile;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.Test;

/**
 * Simple GTS test verifying the parceling and unparceling of CallAttributes.
 */
public class CallStateTest extends AndroidTestCase {


    @SmallTest
    public void testParcelUnparcelPreciseCallState() {
        CallState data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(null)
                .setCallClassification(CallState.CALL_CLASSIFICATION_RINGING)
                .setImsCallSessionId("1")
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NONE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_NONE).build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CallState unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("PreciseCallState is not equal after parceled/unparceled",
                data.getCallState(),
                unparceledData.getCallState());
    }

    @SmallTest
    public void testParcelUnparcelCallQuality() {
        CallQuality quality = new CallQuality();
        CallState data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_IDLE)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(null)
                .setCallClassification(CallState.CALL_CLASSIFICATION_FOREGROUND)
                .setImsCallSessionId(null)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NONE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_NONE).build();


        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CallState unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNull(unparceledData.getCallQuality());

        data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_IDLE)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(quality)
                .setCallClassification(CallState.CALL_CLASSIFICATION_FOREGROUND)
                .setImsCallSessionId(null)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NONE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_NONE).build();


        parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("CallQuality is not equal after parceled/unparceled",
                data.getCallQuality(),
                unparceledData.getCallQuality());
    }

    @SmallTest
    public void testParcelUnparcelNetworkTypeAndClassification() {
        CallQuality quality = new CallQuality();
        CallState data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DIALING)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(null)
                .setCallClassification(CallState.CALL_CLASSIFICATION_FOREGROUND)
                .setImsCallSessionId("3")
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NONE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_NONE).build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CallState unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("NetworkType is not equal after parceled/unparceled",
                data.getNetworkType(),
                unparceledData.getNetworkType());
        assertEquals("Call classification is not equal after parceled/unparceled",
                data.getCallClassification(),
                unparceledData.getCallClassification());
    }

    @Test
    public void testParcelUnparcelImsCallInfo() {
        CallQuality quality = new CallQuality();
        CallState data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DIALING)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(null)
                .setCallClassification(CallState.CALL_CLASSIFICATION_FOREGROUND)
                .setImsCallSessionId(null)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE).build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CallState unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNull(unparceledData.getImsCallSessionId());

        assertEquals("Ims call service type is not equal after parceled/unparceled",
                data.getImsCallServiceType(),
                unparceledData.getImsCallServiceType());

        assertEquals("Ims call type is not equal after parceled/unparceled",
                data.getImsCallType(),
                unparceledData.getImsCallType());

        data = new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                .setCallQuality(quality)
                .setCallClassification(CallState.CALL_CLASSIFICATION_FOREGROUND)
                .setImsCallSessionId("2")
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT).build();

        parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        unparceledData = CallState.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("Ims call session ID is not equal after parceled/unparceled",
                data.getImsCallSessionId(),
                unparceledData.getImsCallSessionId());

        assertEquals("Ims call service type is not equal after parceled/unparceled",
                data.getImsCallServiceType(),
                unparceledData.getImsCallServiceType());

        assertEquals("Ims call type is not equal after parceled/unparceled",
                data.getImsCallType(),
                unparceledData.getImsCallType());
    }
}
