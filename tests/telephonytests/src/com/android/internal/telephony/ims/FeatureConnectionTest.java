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

package com.android.internal.telephony.ims;

import junit.framework.AssertionFailedError;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsRegistration;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.FeatureConnection;
import com.android.ims.ImsManager;
import com.android.ims.internal.IImsServiceFeatureCallback;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;

public class FeatureConnectionTest extends TelephonyTest {

    private Executor mSimpleExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private class TestFeatureConnection extends FeatureConnection {
        private Integer mFeatureState = ImsFeature.STATE_READY;

        public boolean isFeatureCreatedCalled = false;
        public boolean isFeatureRemovedCalled = false;
        public int mNewStatus = ImsFeature.STATE_UNAVAILABLE;

        TestFeatureConnection(Context context, int slotId) {
            super(context, slotId);
            if (!ImsManager.isImsSupportedOnDevice(context)) {
                sImsSupportedOnDevice = false;
            }
        }

        @Override
        public void checkServiceIsReady() throws RemoteException {
            super.checkServiceIsReady();
        }

        @Override
        protected void handleImsFeatureCreatedCallback(int slotId, int feature) {
            isFeatureCreatedCalled = true;
        }

        @Override
        protected void handleImsFeatureRemovedCallback(int slotId, int feature) {
            isFeatureRemovedCalled = true;
        }

        @Override
        protected void handleImsStatusChangedCallback(int slotId, int feature, int status) {
            mNewStatus = status;
        }

        @Override
        protected Integer retrieveFeatureState() {
            return mFeatureState;
        }

        @Override
        protected IImsRegistration getRegistrationBinder() {
            return getTestRegistrationBinder();
        }

        public void setFeatureState(int state) {
            mFeatureState = state;
        }
    };

    private int mPhoneId;
    private TestFeatureConnection mTestFeatureConnection;
    @Mock IBinder mBinder;
    @Mock IImsRegistration mRegistrationBinder;

    @Before
    public void setUp() throws Exception {
        super.setUp("FeatureConnectionTest");
        mPhoneId = mPhone.getPhoneId();

        doReturn(null).when(mContext).getMainLooper();
        doReturn(true).when(mPackageManager).hasSystemFeature(PackageManager.FEATURE_TELEPHONY_IMS);

        mTestFeatureConnection = new TestFeatureConnection(mContext, mPhoneId);
        mTestFeatureConnection.mExecutor = mSimpleExecutor;
        mTestFeatureConnection.setBinder(mBinder);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test service is ready when binder is alive and IMS status is ready.
     */
    @Test
    @SmallTest
    public void testServiceIsReady() {
        when(mBinder.isBinderAlive()).thenReturn(true);
        mTestFeatureConnection.setFeatureState(ImsFeature.STATE_READY);

        try {
            mTestFeatureConnection.checkServiceIsReady();
        } catch (RemoteException e) {
            throw new AssertionFailedError("Exception in testServiceIsReady: " + e);
        }
    }

    /**
     * Test service is not ready when binder is not alive or status is not ready.
     */
    @Test
    @SmallTest
    public void testServiceIsNotReady() {
        // Binder is not alive
        when(mBinder.isBinderAlive()).thenReturn(false);

        try {
            mTestFeatureConnection.checkServiceIsReady();
            throw new AssertionFailedError("testServiceIsNotReady: binder isn't alive");
        } catch (RemoteException e) {
            // expected result
        }

        // IMS feature status is unavailable
        when(mBinder.isBinderAlive()).thenReturn(true);
        mTestFeatureConnection.setFeatureState(ImsFeature.STATE_UNAVAILABLE);

        try {
            mTestFeatureConnection.checkServiceIsReady();
            throw new AssertionFailedError("testServiceIsNotReady: status unavailable");
        } catch (RemoteException e) {
            // expected result
        }
    }

    /**
     * Test registration tech callbacks.
     */
    @Test
    @SmallTest
    public void testRegistrationTech() throws Exception {
        when(mRegistrationBinder.getRegistrationTechnology()).thenReturn(
                ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);

        assertEquals(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                mTestFeatureConnection.getRegistrationTech());

    }

    /**
     * Test callback is called when IMS feature created/removed/changed.
     */
    @Test
    @SmallTest
    public void testListenerCallback() {
        IImsServiceFeatureCallback featureCallback = mTestFeatureConnection.getListener();

        try {
            featureCallback.imsFeatureCreated(anyInt(), anyInt());
            assertTrue(mTestFeatureConnection.isFeatureCreatedCalled);
        } catch (RemoteException e) {
            throw new AssertionFailedError("testListenerCallback(Created): " + e);
        }

        try {
            featureCallback.imsFeatureRemoved(anyInt(), anyInt());
            assertTrue(mTestFeatureConnection.isFeatureRemovedCalled);
        } catch (RemoteException e) {
            throw new AssertionFailedError("testListenerCallback(Removed): " + e);
        }

        try {
            featureCallback.imsStatusChanged(anyInt(), anyInt(), ImsFeature.STATE_READY);
            assertEquals(mTestFeatureConnection.mNewStatus, ImsFeature.STATE_READY);
        } catch (RemoteException e) {
            throw new AssertionFailedError("testListenerCallback(Changed): " + e);
        }
    }

    private IImsRegistration getTestRegistrationBinder() {
        return mRegistrationBinder;
    }
}
