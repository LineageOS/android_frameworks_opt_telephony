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

package android.telephony.ims;

import static android.telephony.ims.feature.ImsFeature.FEATURE_MMTEL;
import static android.telephony.ims.feature.ImsFeature.FEATURE_RCS;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.telephony.BinderCacheManager;
import android.telephony.ims.aidl.IImsRcsController;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.IImsStateCallback;
import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ImsStateCallbackTest extends TelephonyTest {
    private static final int ON_ERROR = Integer.MAX_VALUE;
    private static final int ON_AVAILABLE = 0;

    private static final int SUB_ID_ONE = 1;

    // Mocked classes
    ITelephony mMockTelephonyInterface;
    BinderCacheManager<ITelephony> mBinderCache;
    BinderCacheManager<IImsRcsController> mRcsBinderCache;

    public class LocalCallback extends ImsStateCallback {
        int mRegResult = -1;

        @Override
        public void onUnavailable(int reason) {
            mRegResult = reason;
        }

        @Override
        public void onAvailable() {
            mRegResult = ON_AVAILABLE;
        }

        @Override
        public void onError() {
            mRegResult = ON_ERROR;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("ImsStateCallbackTests");
        mMockTelephonyInterface = mock(ITelephony.class);
        mBinderCache = mock(BinderCacheManager.class);
        mRcsBinderCache = mock(BinderCacheManager.class);
        doReturn(mMockTelephonyInterface).when(mBinderCache)
                .listenOnBinder(any(), any(Runnable.class));
        doReturn(mMockTelephonyInterface).when(mBinderCache)
                .removeRunnable(any(ImsStateCallback.class));
        doReturn(mMockTelephonyInterface).when(mBinderCache).getBinder();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Ensure that the values of ITelephony#(un)registerImsStateCallback's parameters
     * for ImsMmTelManager are correct.
     */
    @SmallTest
    @Test
    public void testMmTelRegisterAndUnregisterImsStateCallbackValues() throws Exception {
        LocalCallback cb = new LocalCallback();

        ImsMmTelManager mmTelManager = ImsMmTelManager.createForSubscriptionId(SUB_ID_ONE);

        replaceInstance(ImsMmTelManager.class, "mBinderCache", mmTelManager, mBinderCache);

        mmTelManager.registerImsStateCallback(Runnable::run, cb);
        verify(mBinderCache).listenOnBinder(any(), any(Runnable.class));
        verify(mMockTelephonyInterface).registerImsStateCallback(
                eq(SUB_ID_ONE), eq(FEATURE_MMTEL), any(IImsStateCallback.class), any());

        mmTelManager.unregisterImsStateCallback(cb);
        verify(mMockTelephonyInterface).unregisterImsStateCallback(any(IImsStateCallback.class));
    }

    /**
     * Ensure that the values of ITelephony#(un)registerImsStateCallback's parameters
     * for ImsRcsManager are correct.
     */
    @SmallTest
    @Test
    public void testRcsRegisterAndUnregisterImsStateCallbackValues() throws Exception {
        LocalCallback cb = new LocalCallback();

        ImsRcsManager rcsManager =
                new ImsRcsManager(mContext, SUB_ID_ONE, mRcsBinderCache, mBinderCache);

        replaceInstance(ImsRcsManager.class, "mTelephonyBinderCache", rcsManager, mBinderCache);

        rcsManager.registerImsStateCallback(Runnable::run, cb);
        verify(mBinderCache).listenOnBinder(any(), any(Runnable.class));
        verify(mMockTelephonyInterface).registerImsStateCallback(
                eq(SUB_ID_ONE), eq(FEATURE_RCS), any(IImsStateCallback.class), any());

        rcsManager.unregisterImsStateCallback(cb);
        verify(mMockTelephonyInterface).unregisterImsStateCallback(any(IImsStateCallback.class));
    }

    /**
     * Ensure that the values of ITelephony#(un)registerImsStateCallback's parameters
     * for ImsRcsManager are correct.
     */
    @SmallTest
    @Test
    public void testSipDelegateRegisterAndUnregisterImsStateCallbackValues() throws Exception {
        LocalCallback cb = new LocalCallback();

        SipDelegateManager sipManager =
                new SipDelegateManager(mContext, SUB_ID_ONE, mRcsBinderCache, mBinderCache);

        replaceInstance(
                SipDelegateManager.class, "mTelephonyBinderCache", sipManager, mBinderCache);

        sipManager.registerImsStateCallback(Runnable::run, cb);
        verify(mBinderCache).listenOnBinder(any(), any(Runnable.class));
        verify(mMockTelephonyInterface).registerImsStateCallback(
                eq(SUB_ID_ONE), eq(FEATURE_RCS), any(IImsStateCallback.class), any());

        sipManager.unregisterImsStateCallback(cb);
        verify(mMockTelephonyInterface).unregisterImsStateCallback(any(IImsStateCallback.class));
    }

    /**
     * Ensure that onUnavailable, onAvailable, and onErr are called.
     */
    @SmallTest
    @Test
    public void testImsStateCallbacks() throws Exception {
        LocalCallback cb = new LocalCallback();

        ImsMmTelManager mmTelManager = ImsMmTelManager.createForSubscriptionId(SUB_ID_ONE);

        replaceInstance(ImsMmTelManager.class, "mBinderCache", mmTelManager, mBinderCache);

        // Capture the Runnable that was registered.
        ArgumentCaptor<Runnable> runnableCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        // Capture the IImsStateCallback that was registered.
        ArgumentCaptor<IImsStateCallback> callbackCaptor =
                ArgumentCaptor.forClass(IImsStateCallback.class);

        mmTelManager.registerImsStateCallback(Runnable::run, cb);
        verify(mBinderCache).listenOnBinder(any(), runnableCaptor.capture());

        verify(mMockTelephonyInterface).registerImsStateCallback(
                eq(SUB_ID_ONE), eq(FEATURE_MMTEL), callbackCaptor.capture(), any());

        IImsStateCallback cbBinder = callbackCaptor.getValue();

        // onUnavailable
        cbBinder.onUnavailable(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED);
        assertEquals(ImsStateCallback.REASON_IMS_SERVICE_DISCONNECTED, cb.mRegResult);

        // onAvailable
        cbBinder.onAvailable();
        assertEquals(ON_AVAILABLE, cb.mRegResult);

        Runnable runnable = runnableCaptor.getValue();
        // onError
        runnable.run();
        assertEquals(ON_ERROR, cb.mRegResult);
    }
}
