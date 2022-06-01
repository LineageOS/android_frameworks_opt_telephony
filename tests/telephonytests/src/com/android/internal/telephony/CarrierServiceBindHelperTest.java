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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Message;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class CarrierServiceBindHelperTest extends TelephonyTest {
    private static final int PHONE_ID_0 = 0;
    private static final int PHONE_ID_1 = 1;

    CarrierServiceBindHelper mCarrierServiceBindHelper;
    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
    }

    @After
    public void tearDown() throws Exception {
        mCarrierServiceBindHelper = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testMultiSimConfigChanged() throws Exception {
        clearInvocations(mPhoneConfigurationManager);
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));

        // Verify registration of EVENT_MULTI_SIM_CONFIG_CHANGED.
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(2);
        processAllMessages();

        assertEquals(2, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(2, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(1));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(1));

        // Switch back to single SIM.
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        assertEquals(1, mCarrierServiceBindHelper.mBindings.size());
        assertEquals(1, mCarrierServiceBindHelper.mLastSimState.size());
        assertNotNull(mCarrierServiceBindHelper.mBindings.get(0));
        assertNotNull(mCarrierServiceBindHelper.mLastSimState.get(0));
    }

    @Test
    public void testUnbindWhenNotBound() throws Exception {
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);

        // Try unbinding without binding and make sure we don't throw an Exception
        mCarrierServiceBindHelper.mHandler.handleMessage(
                Message.obtain(mCarrierServiceBindHelper.mHandler,
                        CarrierServiceBindHelper.EVENT_PERFORM_IMMEDIATE_UNBIND,
                        new Integer(0)));
    }

    @Test
    public void testCarrierPrivilegesCallbackRegistration() {
        // Device starts with DSDS mode
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        mCarrierServiceBindHelper = new CarrierServiceBindHelper(mContext);
        processAllMessages();

        // Verify that CarrierPrivilegesCallbacks are registered on both phones.
        // Capture the callbacks for further verification
        ArgumentCaptor<CarrierPrivilegesCallback> phone0CallbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_0), any(),
                phone0CallbackCaptor.capture());
        CarrierPrivilegesCallback phone0Callback = phone0CallbackCaptor.getAllValues().get(0);
        assertNotNull(phone0Callback);

        ArgumentCaptor<CarrierPrivilegesCallback> phone1CallbackCaptor = ArgumentCaptor.forClass(
                CarrierPrivilegesCallback.class);
        verify(mTelephonyManager).registerCarrierPrivilegesCallback(eq(PHONE_ID_1), any(),
                phone1CallbackCaptor.capture());
        CarrierPrivilegesCallback phone1Callback = phone1CallbackCaptor.getAllValues().get(0);
        assertNotNull(phone1Callback);

        // Switch back to single SIM.
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        PhoneConfigurationManager.notifyMultiSimConfigChange(1);
        processAllMessages();

        // Verify the callback for phone1 had been unregistered while phone0 didn't.
        verify(mTelephonyManager).unregisterCarrierPrivilegesCallback(eq(phone1Callback));
        verify(mTelephonyManager, never()).unregisterCarrierPrivilegesCallback(eq(phone0Callback));
    }
    // TODO (b/232461097): Add UT cases to cover more scenarios (user unlock, SIM state change...)
}
