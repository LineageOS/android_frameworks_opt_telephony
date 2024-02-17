/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.domainselection;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.DomainSelectionService;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.IDomainSelectionServiceController;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for DomainSelectionController
 */
@RunWith(AndroidJUnit4.class)
public class DomainSelectionControllerTest extends TelephonyTest {

    private static final DomainSelectionController.BindRetry BIND_RETRY =
            new DomainSelectionController.BindRetry() {
                @Override
                public long getStartDelay() {
                    return 50;
                }

                @Override
                public long getMaximumDelay() {
                    return 1000;
                }
            };

    // Mocked classes
    IDomainSelectionServiceController mMockServiceControllerBinder;
    Context mMockContext;

    private final ComponentName mTestComponentName = new ComponentName("TestPkg",
            "DomainSelectionControllerTest");
    private Handler mHandler;
    private DomainSelectionController mTestController;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        mMockContext = mock(Context.class);
        mMockServiceControllerBinder = mock(IDomainSelectionServiceController.class);
        mTestController = new DomainSelectionController(mMockContext,
                Looper.getMainLooper(), BIND_RETRY);
        mHandler = mTestController.getHandlerForTest();

        when(mMockContext.bindService(any(), any(), anyInt())).thenReturn(true);
    }


    @After
    public void tearDown() throws Exception {
        mTestController.stopBackoffTimer();
        waitForHandlerAction(mHandler, 1000);
        mTestController = null;
        super.tearDown();
    }

    /**
     * Tests that Context.bindService is called with the correct parameters when we call bind.
     */
    @SmallTest
    @Test
    public void testBindService() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        assertTrue(mTestController.bind(mTestComponentName));

        int expectedFlags = Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE
                | Context.BIND_IMPORTANT;

        verify(mMockContext).bindService(intentCaptor.capture(), any(), eq(expectedFlags));

        Intent testIntent = intentCaptor.getValue();

        assertEquals(DomainSelectionService.SERVICE_INTERFACE, testIntent.getAction());
        assertEquals(mTestComponentName, testIntent.getComponent());
    }

    /**
     * Verify that if bind is called multiple times, we only call bindService once.
     */
    @SmallTest
    @Test
    public void testBindFailureWhenBound() {
        bindAndConnectService();

        // already bound, should return false
        assertFalse(mTestController.bind(mTestComponentName));

        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Tests that when unbind is called while the DomainSelectionService is disconnected,
     * we still handle unbinding to the service correctly.
     */
    @SmallTest
    @Test
    public void testBindServiceAndConnectedDisconnectedUnbind() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onServiceDisconnected(mTestComponentName);

        long delay = mTestController.getBindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);

        mTestController.unbind();
        verify(mMockContext).unbindService(eq(conn));
    }

    /**
     * Tests DomainSelectionController callbacks are properly called when a DomainSelectionService
     * is bound and subsequently unbound.
     */
    @SmallTest
    @Test
    public void testBindServiceBindUnbind() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        mTestController.unbind();

        verify(mMockContext).unbindService(eq(conn));
    }

    /**
     * Ensures that imsServiceFeatureRemoved is called when the binder dies in another process.
     */
    @SmallTest
    @Test
    public void testBindServiceAndBinderDied() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onBindingDied(null /*null*/);

        long delay = BIND_RETRY.getStartDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        verify(mMockContext).unbindService(eq(conn));
    }

    /**
     * Ensures that imsServiceBindPermanentError is called when the binder returns null.
     */
    @SmallTest
    @Test
    public void testBindServiceAndReturnedNull() throws RemoteException {
        bindAndNullServiceError();

        long delay = mTestController.getBindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
    }

    /**
     * Verifies that the DomainSelectionController automatically tries to bind again after
     * an untimely binder death.
     */
    @SmallTest
    @Test
    public void testAutoBindAfterBinderDied() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onBindingDied(null /*null*/);

        long delay = BIND_RETRY.getStartDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        // The service should autobind after rebind event occurs
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    /**
     * Due to a bug in ServiceConnection, we will sometimes receive a null binding after the binding
     * dies. Ignore null binding in this case.
     */
    @SmallTest
    @Test
    public void testAutoBindAfterBinderDiedIgnoreNullBinding() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onBindingDied(null);
        // null binding should be ignored in this case.
        conn.onNullBinding(null);

        long delay = BIND_RETRY.getStartDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        // The service should autobind after rebind event occurs
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that bindService has only been called once before automatic rebind occurs.
     */
    @SmallTest
    @Test
    public void testNoAutoBindBeforeTimeout() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onBindingDied(null /*null*/);

        // Be sure that there are no binds before the RETRY_TIMEOUT expires
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    /**
     * Ensure that calling unbind stops automatic rebind from occurring.
     */
    @SmallTest
    @Test
    public void testUnbindCauseAutoBindCancelAfterBinderDied() throws RemoteException {
        ServiceConnection conn = bindAndConnectService();

        conn.onBindingDied(null /*null*/);
        mTestController.unbind();

        long delay = mTestController.getBindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);

        // Unbind should stop the autobind from occurring.
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    private void bindAndNullServiceError() {
        ServiceConnection connection = bindService(mTestComponentName);
        connection.onNullBinding(mTestComponentName);
    }

    private ServiceConnection bindAndConnectService() {
        ServiceConnection connection = bindService(mTestComponentName);
        IDomainSelectionServiceController.Stub controllerStub =
                mock(IDomainSelectionServiceController.Stub.class);
        when(controllerStub.queryLocalInterface(any())).thenReturn(mMockServiceControllerBinder);
        connection.onServiceConnected(mTestComponentName, controllerStub);

        long delay = mTestController.getBindDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);
        return connection;
    }

    private ServiceConnection bindService(ComponentName testComponentName) {
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        assertTrue(mTestController.bind(testComponentName));
        verify(mMockContext).bindService(any(), serviceCaptor.capture(), anyInt());
        return serviceCaptor.getValue();
    }

    private void waitForHandlerActionDelayed(Handler h, long timeoutMillis, long delayMs) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.postDelayed(lock::countDown, delayMs);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }
}
