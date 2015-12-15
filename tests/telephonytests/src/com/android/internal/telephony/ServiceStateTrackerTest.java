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

public class ServiceStateTrackerTest {
    private static final String TAG = "ServiceStateTrackerTest";

    @Mock
    private GsmCdmaPhone mPhone;
    @Mock
    private UiccController mUiccController;
    @Mock
    private SubscriptionController mSubscriptionController;

    private SimulatedCommands simulatedCommands;
    private ContextFixture contextFixture;
    private ServiceStateTracker sst;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        contextFixture = new ContextFixture();
        simulatedCommands = new SimulatedCommands();

        doReturn(contextFixture.getTestDouble()).when(mPhone).getContext();
        doReturn(true).when(mPhone).getUnitTestMode();
        doReturn(true).when(mPhone).isPhoneTypeGsm();

        //Use reflection to mock singleton
        Field field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        Looper.prepare();
        sst = new ServiceStateTracker(mPhone, simulatedCommands);
    }

    @After
    public void tearDown() throws Exception {
        sst = null;
    }

    @Test @SmallTest
    public void testSetRadioPower() {
        boolean oldState = simulatedCommands.getRadioState().isOn();
        sst.setRadioPower(!oldState);
        assertTrue(oldState != simulatedCommands.getRadioState().isOn());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
