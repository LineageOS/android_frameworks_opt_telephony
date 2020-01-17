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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.emergency.EmergencyNumber;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PhoneStateListenerTest extends TelephonyTest {

    private PhoneStateListener mPhoneStateListenerUT;
    private boolean mUserMobileDataState = false;
    private EmergencyNumber mCalledEmergencyNumber;
    private EmergencyNumber mTextedEmergencyNumber;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mPhoneStateListenerUT = new PhoneStateListener() {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {
                logd("Service State Changed");
                mServiceState.setVoiceRegState(serviceState.getState());
                mServiceState.setDataRegState(serviceState.getDataRegistrationState());
            }

            @Override
            public void onUserMobileDataStateChanged(boolean state) {
                logd("User Mobile Data State Changed");
                mUserMobileDataState = true;
            }

            public void onOutgoingEmergencyCall(EmergencyNumber emergencyNumber) {
                logd("OutgoingCallEmergencyNumber Changed");
                mCalledEmergencyNumber = emergencyNumber;
            }

            public void onOutgoingEmergencySms(EmergencyNumber emergencyNumber) {
                logd("OutgoingSmsEmergencyNumber Changed");
                mTextedEmergencyNumber = emergencyNumber;
            }
        };
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test @SmallTest
    public void testTriggerServiceStateChanged() throws Exception {
        Field field = PhoneStateListener.class.getDeclaredField("callback");
        field.setAccessible(true);

        ServiceState ss = new ServiceState();
        ss.setDataRegState(ServiceState.STATE_IN_SERVICE);
        ss.setVoiceRegState(ServiceState.STATE_EMERGENCY_ONLY);

        ((IPhoneStateListener) field.get(mPhoneStateListenerUT)).onServiceStateChanged(ss);
        processAllMessages();

        verify(mServiceState).setDataRegState(ServiceState.STATE_IN_SERVICE);
        verify(mServiceState).setVoiceRegState(ServiceState.STATE_EMERGENCY_ONLY);
    }

    @Test @SmallTest
    public void testTriggerUserMobileDataStateChanged() throws Exception {
        Field field = PhoneStateListener.class.getDeclaredField("callback");
        field.setAccessible(true);

        assertFalse(mUserMobileDataState);

        ((IPhoneStateListener) field.get(mPhoneStateListenerUT)).onUserMobileDataStateChanged(true);
        processAllMessages();

        assertTrue(mUserMobileDataState);
    }

    @Test @SmallTest
    public void testTriggerOutgoingCallEmergencyNumberChanged() throws Exception {
        Field field = PhoneStateListener.class.getDeclaredField("callback");
        field.setAccessible(true);

        assertNull(mCalledEmergencyNumber);

        EmergencyNumber emergencyNumber = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        ((IPhoneStateListener) field.get(mPhoneStateListenerUT)).onOutgoingEmergencyCall(
                emergencyNumber);
        processAllMessages();

        assertTrue(mCalledEmergencyNumber.equals(emergencyNumber));
    }

    @Test @SmallTest
    public void testTriggerOutgoingSmsEmergencyNumberChanged() throws Exception {
        Field field = PhoneStateListener.class.getDeclaredField("callback");
        field.setAccessible(true);

        assertNull(mTextedEmergencyNumber);

        EmergencyNumber emergencyNumber = new EmergencyNumber(
                "911",
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<String>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);

        ((IPhoneStateListener) field.get(mPhoneStateListenerUT)).onOutgoingEmergencySms(
                emergencyNumber);
        processAllMessages();

        assertTrue(mTextedEmergencyNumber.equals(emergencyNumber));
    }
}
