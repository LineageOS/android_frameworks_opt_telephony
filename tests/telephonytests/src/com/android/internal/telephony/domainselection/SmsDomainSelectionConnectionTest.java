/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelector;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TransportSelectorCallback;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.TestableLooper;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.Phone;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

/**
 * Unit tests for SmsDomainSelectionConnection.
 */
@RunWith(AndroidJUnit4.class)
public class SmsDomainSelectionConnectionTest {
    private static final int SLOT_ID = 0;
    private static final int SUB_ID = 1;

    @Mock private Phone mPhone;
    @Mock private DomainSelectionController mDsController;
    @Mock private DomainSelectionConnection.DomainSelectionConnectionCallback mDscCallback;
    @Mock private DomainSelector mDomainSelector;

    private Handler mHandler;
    private TestableLooper mTestableLooper;
    private DomainSelectionService.SelectionAttributes mDsAttr;
    private SmsDomainSelectionConnection mDsConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        HandlerThread handlerThread = new HandlerThread(
                SmsDomainSelectionConnectionTest.class.getSimpleName());
        handlerThread.start();

        mHandler = new Handler(handlerThread.getLooper());
        mDsConnection = new SmsDomainSelectionConnection(mPhone, mDsController);
        mDsConnection.getTransportSelectorCallback().onCreated(mDomainSelector);
        mDsAttr = new DomainSelectionService.SelectionAttributes.Builder(
                SLOT_ID, SUB_ID, DomainSelectionService.SELECTOR_TYPE_SMS).build();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestableLooper != null) {
            mTestableLooper.destroy();
            mTestableLooper = null;
        }

        if (mHandler != null) {
            mHandler.getLooper().quit();
            mHandler = null;
        }

        mDomainSelector = null;
        mDsAttr = null;
        mDsConnection = null;
        mDscCallback = null;
        mDsController = null;
        mPhone = null;
    }

    @Test
    @SmallTest
    public void testRequestDomainSelection() {
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);

        assertNotNull(future);
        verify(mDsController).selectDomain(eq(mDsAttr), any(TransportSelectorCallback.class));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnWlanSelected() throws Exception {
        setUpTestableLooper();
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onWlanSelected();
        processAllMessages();

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    public void testOnSelectionTerminated() {
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        mDsConnection.onSelectionTerminated(DisconnectCause.LOCAL);

        assertFalse(future.isDone());
        verify(mDscCallback).onSelectionTerminated(eq(DisconnectCause.LOCAL));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPs() throws Exception {
        setUpTestableLooper();
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS);
        processAllMessages();

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedCs() throws Exception {
        setUpTestableLooper();
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_CS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_CS);
        processAllMessages();

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testFinishSelection() throws Exception {
        setUpTestableLooper();
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS);
        processAllMessages();
        mDsConnection.finishSelection();

        verify(mDomainSelector).finishSelection();
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testCancelSelection() throws Exception {
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.finishSelection();

        verify(mDomainSelector).cancelSelection();
    }

    private void setUpTestableLooper() throws Exception {
        mTestableLooper = new TestableLooper(mHandler.getLooper());
    }

    private void processAllMessages() {
        while (!mTestableLooper.getLooper().getQueue().isIdle()) {
            mTestableLooper.processAllMessages();
        }
    }
}
