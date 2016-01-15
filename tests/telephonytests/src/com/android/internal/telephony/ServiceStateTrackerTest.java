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

import android.os.HandlerThread;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.UiccController;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

public class ServiceStateTrackerTest {
    private static final String TAG = "ServiceStateTrackerTest";

    @Mock
    private GsmCdmaPhone mPhone;
    @Mock
    private UiccController mUiccController;
    @Mock
    private SubscriptionController mSubscriptionController;
    @Mock
    private SparseArray<TelephonyEventLog> mLogInstances;
    @Mock
    private TelephonyEventLog mTelephonyEventLog;
    @Mock
    private DcTracker mDct;

    private SimulatedCommands simulatedCommands;
    private ContextFixture contextFixture;
    private ServiceStateTracker sst;

    private Object mLock = new Object();
    private boolean mReady = false;

    private class ServiceStateTrackerTestHandler extends HandlerThread {

        private ServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            sst = new ServiceStateTracker(mPhone, simulatedCommands);
            synchronized (mLock) {
                mReady = true;
            }
        }
    }

    private void waitUntilReady() {
        while(true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    @Before
    public void setUp() throws Exception {

        logd("ServiceStateTrackerTest +Setup!");
        MockitoAnnotations.initMocks(this);
        contextFixture = new ContextFixture();
        simulatedCommands = new SimulatedCommands();

        doReturn(contextFixture.getTestDouble()).when(mPhone).getContext();
        doReturn(true).when(mPhone).getUnitTestMode();
        doReturn(true).when(mPhone).isPhoneTypeGsm();
        mPhone.mDcTracker = mDct;

        //Use reflection to mock singleton
        Field field = SubscriptionController.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSubscriptionController);

        //Use reflection to mock singleton
        field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        doReturn(mTelephonyEventLog).when(mLogInstances).get(anyInt());

        // Use reflection to replace TelephonyEventLog.sInstances with our mocked mLogInstances
        field = TelephonyEventLog.class.getDeclaredField("sInstances");
        field.setAccessible(true);
        field.set(null, mLogInstances);

        contextFixture.putStringArrayResource(
                com.android.internal.R.array.config_sameNamedOperatorConsideredRoaming,
                new String[]{"123456"});

        contextFixture.putStringArrayResource(
                com.android.internal.R.array.config_operatorConsideredNonRoaming,
                new String[]{"123456"});

        new ServiceStateTrackerTestHandler(TAG).start();
        logd("ServiceStateTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        sst = null;
    }

    @Test @SmallTest
    public void testSetRadioPower() {
        waitUntilReady();
        boolean oldState = simulatedCommands.getRadioState().isOn();
        sst.setRadioPower(!oldState);
        assertTrue(oldState != simulatedCommands.getRadioState().isOn());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
