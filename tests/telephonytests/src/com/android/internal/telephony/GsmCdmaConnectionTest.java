/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.os.Handler;
import android.os.Looper;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.UiccController;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GsmCdmaConnectionTest {
    private static final String TAG = "GsmCdmaConnectionTest";

    @Mock
    GsmCdmaPhone mPhone;
    @Mock
    GsmCdmaCallTracker mCT;
    @Mock
    GsmCdmaCall mCall;

    private ContextFixture contextFixture;
    private GsmCdmaConnection connection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        contextFixture = new ContextFixture();

        Field field = Handler.class.getDeclaredField("mLooper");
        field.setAccessible(true);
        field.set(mCT, Looper.getMainLooper());

        doReturn(contextFixture.getTestDouble()).when(mPhone).getContext();
        doReturn(mPhone).when(mCT).getPhone();
        doReturn(PhoneConstants.PHONE_TYPE_GSM).when(mPhone).getPhoneType();

        connection = new GsmCdmaConnection(mPhone, "1234", mCT, mCall);
    }

    @After
    public void tearDown() throws Exception {
        connection = null;
    }

    @Test @SmallTest
    public void testOrigDialString() {
        assertEquals("1234", connection.getOrigDialString());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
