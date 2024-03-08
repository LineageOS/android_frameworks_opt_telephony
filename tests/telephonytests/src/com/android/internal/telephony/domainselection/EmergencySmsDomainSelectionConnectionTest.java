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

import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WLAN;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelector;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager;
import com.android.internal.telephony.data.AccessNetworksManager.QualifiedNetworks;
import com.android.internal.telephony.emergency.EmergencyStateTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Unit tests for EmergencySmsDomainSelectionConnection.
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class EmergencySmsDomainSelectionConnectionTest extends TelephonyTest {
    private DomainSelectionController mDsController;
    private DomainSelectionConnection.DomainSelectionConnectionCallback mDscCallback;
    private DomainSelector mDomainSelector;
    private EmergencyStateTracker mEmergencyStateTracker;

    private Handler mHandler;
    private AccessNetworksManager mAnm;
    private DomainSelectionService.SelectionAttributes mDsAttr;
    private EmergencySmsDomainSelectionConnection mDsConnection;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        mHandler = new Handler(Looper.myLooper());
        mDsController = Mockito.mock(DomainSelectionController.class);
        mDscCallback = Mockito.mock(
                DomainSelectionConnection.DomainSelectionConnectionCallback.class);
        mDomainSelector = Mockito.mock(DomainSelector.class);
        mEmergencyStateTracker = Mockito.mock(EmergencyStateTracker.class);
        mAnm = Mockito.mock(AccessNetworksManager.class);
        when(mPhone.getAccessNetworksManager()).thenReturn(mAnm);

        mDsConnection = new EmergencySmsDomainSelectionConnection(
                mPhone, mDsController, mEmergencyStateTracker);
        mDsConnection.getTransportSelectorCallback().onCreated(mDomainSelector);
        mDsAttr = new DomainSelectionService.SelectionAttributes.Builder(
                mPhone.getPhoneId(), mPhone.getSubId(), DomainSelectionService.SELECTOR_TYPE_SMS)
                .setEmergency(true)
                .build();
    }

    @After
    public void tearDown() throws Exception {
        mDomainSelector = null;
        mDsAttr = null;
        mDsConnection = null;
        mDscCallback = null;
        mDsController = null;
        mEmergencyStateTracker = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnWlanSelected() throws Exception {
        when(mAnm.getPreferredTransport(anyInt()))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onWlanSelected(true);
        processAllMessages();

        assertTrue(future.isDone());
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_SMS), eq(MODE_EMERGENCY_WLAN));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnWlanSelectedWithDifferentTransportType() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onWlanSelected(true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_SMS), eq(MODE_EMERGENCY_WLAN));
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        List<QualifiedNetworks> networksList = new ArrayList<>();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.IWLAN }));
        AsyncResult ar = new AsyncResult(null, networksList, null);
        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue(), ar));
        processAllMessages();

        assertTrue(future.isDone());
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnWlanSelectedWithDifferentTransportTypeAndImsPdn() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onWlanSelected(false);
        processAllMessages();

        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_SMS), eq(MODE_EMERGENCY_WLAN));
        verify(mAnm, never()).registerForQualifiedNetworksChanged(any(Handler.class), anyInt());
        verify(mPhone, never()).notifyEmergencyDomainSelected(anyInt());

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnWlanSelectedWithDifferentTransportTypeWhilePreferredTransportChanged()
            throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onWlanSelected(true);
        // When onWlanSelected() is called again,
        // it will be ignored because the change of preferred transport is in progress.
        // => onEmergencyTransportChanged() is called only once.
        mDsConnection.onWlanSelected(true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_SMS), eq(MODE_EMERGENCY_WLAN));
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));

        List<QualifiedNetworks> networksList = new ArrayList<>();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.IWLAN }));
        AsyncResult ar = new AsyncResult(null, networksList, null);
        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue(), ar));
        processAllMessages();

        assertTrue(future.isDone());
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
    }

    @Test
    @SmallTest
    public void testOnWwanSelected() throws Exception {
        mDsConnection.onWwanSelected();

        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_SMS), eq(MODE_EMERGENCY_WWAN));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPs() throws Exception {
        when(mAnm.getPreferredTransport(anyInt()))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        processAllMessages();

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPsWithDifferentTransportType() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));

        List<QualifiedNetworks> networksList = new ArrayList<>();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.EUTRAN }));
        AsyncResult ar = new AsyncResult(null, networksList, null);
        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue(), ar));
        processAllMessages();

        assertTrue(future.isDone());
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPsWithDifferentTransportTypeAndImsPdn() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, false);
        processAllMessages();

        verify(mAnm, never()).registerForQualifiedNetworksChanged(any(Handler.class), anyInt());
        verify(mPhone, never()).notifyEmergencyDomainSelected(anyInt());

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPsWithDifferentTransportTypeAndNotChanged() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));

        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue()));
        processAllMessages();

        assertFalse(future.isDone());
        verify(mAnm, never()).unregisterForQualifiedNetworksChanged(any(Handler.class));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedPsWithDifferentTransportTypeWhilePreferredTransportChanged()
            throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        // When onDomainSelected() is called again with the different domain,
        // it will be ignored because the change of preferred transport is in progress.
        // => The domain selection result is DOMAIN_PS.
        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_CS, false);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));

        List<QualifiedNetworks> networksList = new ArrayList<>();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.EUTRAN }));
        AsyncResult ar = new AsyncResult(null, networksList, null);
        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue(), ar));
        processAllMessages();

        assertTrue(future.isDone());
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testOnDomainSelectedCs() throws Exception {
        when(mAnm.getPreferredTransport(anyInt()))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_CS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_CS, false);
        processAllMessages();

        assertTrue(future.isDone());
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testFinishSelection() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));

        mDsConnection.finishSelection();
        processAllMessages();

        assertFalse(future.isDone());
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
        verify(mDomainSelector).cancelSelection();
    }

    @Test
    @SmallTest
    @SuppressWarnings("FutureReturnValueIgnored")
    public void testFinishSelectionAfterDomainSelectionCompleted() throws Exception {
        when(mAnm.getPreferredTransport(anyInt())).thenReturn(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        CompletableFuture<Integer> future =
                mDsConnection.requestDomainSelection(mDsAttr, mDscCallback);
        future.thenAcceptAsync((domain) -> {
            assertEquals(Integer.valueOf(NetworkRegistrationInfo.DOMAIN_PS), domain);
        }, mHandler::post);

        mDsConnection.onDomainSelected(NetworkRegistrationInfo.DOMAIN_PS, true);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> msgCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mAnm).registerForQualifiedNetworksChanged(
                handlerCaptor.capture(), msgCaptor.capture());
        verify(mPhone).notifyEmergencyDomainSelected(
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));

        List<QualifiedNetworks> networksList = new ArrayList<>();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.EUTRAN }));
        AsyncResult ar = new AsyncResult(null, networksList, null);
        Handler handler = handlerCaptor.getValue();
        Integer msg = msgCaptor.getValue();
        handler.handleMessage(Message.obtain(handler, msg.intValue(), ar));
        processAllMessages();
        mDsConnection.finishSelection();

        assertTrue(future.isDone());
        // This method should be invoked one time.
        verify(mAnm).unregisterForQualifiedNetworksChanged(any(Handler.class));
        verify(mDomainSelector).finishSelection();
    }
}
