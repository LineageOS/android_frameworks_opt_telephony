/*
 * Copyright 2019 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.FeatureConnector;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;

public class FeatureConnectorTest extends TelephonyTest {

    private Executor mExecutor = new Executor() {
        @Override
        public void execute(Runnable r) {
            r.run();
        }
    };

    private HandlerThread mHandlerThread;
    private FeatureConnector<ImsManager> mFeatureConnector;
    @Mock
    ImsManager mImsManager;
    @Mock
    FeatureConnector.Listener<ImsManager> mListener;
    @Mock
    FeatureConnector.RetryTimeout mRetryTimeout;

    @Before
    public void setUp() throws Exception {
        super.setUp("FeatureConnectorTest");
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        int phoneId = mPhone.getPhoneId();
        mHandlerThread = new HandlerThread("ConnectorHandlerThread");
        mHandlerThread.start();

        mFeatureConnector = new FeatureConnector<>(mContext, phoneId,
            mListener, mExecutor, mHandlerThread.getLooper());
        mFeatureConnector.mListener = mListener;
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testConnect() {
        // ImsManager is supported on device
        setImsSupportedFeature(true);
        when(mListener.getFeatureManager()).thenReturn(mImsManager);

        mFeatureConnector.connect();
        waitForHandlerAction(mFeatureConnector, 1000);

        // Verify that mListener will retrieve feature manager
        verify(mListener).getFeatureManager();

        reset(mListener);

        // ImsManager is NOT supported on device
        setImsSupportedFeature(false);
        when(mListener.getFeatureManager()).thenReturn(mImsManager);

        mFeatureConnector.connect();
        waitForHandlerAction(mFeatureConnector, 1000);

        // Verify that mListener won't retrieve feature manager
        verify(mListener, never()).getFeatureManager();
    }

    @Test
    @SmallTest
    public void testDisconnect() {
        // Verify mListener will call connectionUnavailable if disconnect() is called.
        mFeatureConnector.disconnect();
        verify(mListener).connectionUnavailable();
    }

    @Test
    @SmallTest
    public void testNotifyStateChanged() {
        try {
            mFeatureConnector.mManager = mImsManager;
            when(mImsManager.getImsServiceState()).thenReturn(ImsFeature.STATE_READY);
            // Trigger status changed
            mFeatureConnector.mNotifyStatusChangedCallback.notifyStateChanged();
            // Verify NotifyReady is called
            verify(mListener).connectionReady(anyObject());
        } catch (ImsException e) {
            throw new AssertionFailedError("Exception in testNotifyStateChanged: " + e);
        }
    }

    @Test
    @SmallTest
    public void testRetryGetImsService() {
        mFeatureConnector.mManager = mImsManager;
        mFeatureConnector.mRetryTimeout = mRetryTimeout;

        when(mRetryTimeout.get()).thenReturn(1);
        when(mListener.getFeatureManager()).thenReturn(mImsManager);

        mFeatureConnector.retryGetImsService();
        waitForHandlerAction(mFeatureConnector, 2000);

        // Verify removeNotifyStatusChangedCallback will be called if ImsManager is not null.
        verify(mImsManager).removeNotifyStatusChangedCallback(anyObject());
    }

    private void setImsSupportedFeature(boolean isSupported) {
        doReturn(isSupported).when(mPackageManager).hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_IMS);
    }
}
