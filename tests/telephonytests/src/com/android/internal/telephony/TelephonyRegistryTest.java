/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.telephony.PhoneStateListener.LISTEN_PHONE_CAPABILITY_CHANGE;
import static android.telephony.PhoneStateListener.LISTEN_PREFERRED_DATA_SUBID_CHANGE;

import static org.junit.Assert.assertEquals;

import android.os.HandlerThread;
import android.os.ServiceManager;
import android.telephony.PhoneCapability;
import android.telephony.PhoneStateListener;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.TelephonyRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class TelephonyRegistryTest extends TelephonyTest {
    @Mock
    private ISub.Stub mISubStub;
    private PhoneStateListener mPhoneStateListener;
    private TelephonyRegistry mTelephonyRegistry;
    private PhoneCapability mPhoneCapability;
    private int mPreferredSubId;

    public class PhoneStateListenerWrapper extends PhoneStateListener {
        @Override
        public void onPhoneCapabilityChanged(PhoneCapability capability) {
            mPhoneCapability = capability;
            setReady(true);
        }
        @Override
        public void onPreferredDataSubIdChanged(int preferredSubId) {
            mPreferredSubId = preferredSubId;
            setReady(true);
        }
    }

    private void addTelephonyRegistryService() {
        mServiceManagerMockedServices.put("telephony.registry", mTelephonyRegistry.asBinder());
    }

    private HandlerThread mHandlerThread = new HandlerThread("ListenerThread") {
        @Override
        public void onLooperPrepared() {
            mTelephonyRegistry = new TelephonyRegistry(mContext);
            addTelephonyRegistryService();
            mPhoneStateListener = new PhoneStateListenerWrapper();
            setReady(true);
        }
    };

    @Before
    public void setUp() throws Exception {
        super.setUp("TelephonyRegistryTest");
        mServiceManagerMockedServices.put("isub", mISubStub);
        mHandlerThread.start();
        waitUntilReady();
        assertEquals(mTelephonyRegistry.asBinder(),
                ServiceManager.getService("telephony.registry"));
    }

    @After
    public void tearDown() throws Exception {
        mTelephonyRegistry = null;
        mHandlerThread.quit();
        super.tearDown();
    }

    @Test @SmallTest
    public void testPhoneCapabilityChanged() {
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        setReady(false);
        PhoneCapability phoneCapability = new PhoneCapability(1, 2, 3, null);
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        mTelephonyRegistry.listen(mContext.getOpPackageName(),
                mPhoneStateListener.callback,
                LISTEN_PHONE_CAPABILITY_CHANGE, true);
        waitUntilReady();
        assertEquals(phoneCapability, mPhoneCapability);

        // notifyPhoneCapabilityChanged with a new capability. Callback should be triggered.
        setReady(false);
        phoneCapability = new PhoneCapability(3, 2, 2, null);
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        waitUntilReady();
        assertEquals(phoneCapability, mPhoneCapability);
    }


    @Test @SmallTest
    public void testPreferredDataSubChanged() {
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        setReady(false);
        int preferredSubId = 0;
        mTelephonyRegistry.notifyPreferredDataSubIdChanged(preferredSubId);
        mTelephonyRegistry.listen(mContext.getOpPackageName(),
                mPhoneStateListener.callback,
                LISTEN_PREFERRED_DATA_SUBID_CHANGE, true);
        waitUntilReady();
        assertEquals(preferredSubId, mPreferredSubId);

        // notifyPhoneCapabilityChanged with a new capability. Callback should be triggered.
        setReady(false);
        mPreferredSubId = 1;
        mTelephonyRegistry.notifyPreferredDataSubIdChanged(preferredSubId);
        waitUntilReady();
        assertEquals(preferredSubId, mPreferredSubId);
    }
}
