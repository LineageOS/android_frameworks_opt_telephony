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

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.DomainSelectionService.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.telecom.PhoneAccount;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.DomainSelectionService;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsReasonInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.IDomainSelector;
import com.android.internal.telephony.ITransportSelectorCallback;
import com.android.internal.telephony.ITransportSelectorResultCallback;
import com.android.internal.telephony.IWwanSelectorCallback;
import com.android.internal.telephony.IWwanSelectorResultCallback;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager.QualifiedNetworks;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DomainSelectionConnectionTest extends TelephonyTest {

    private static final String TELECOM_CALL_ID1 = "TC1";

    private DomainSelectionController mDomainSelectionController;
    private DomainSelectionConnection.DomainSelectionConnectionCallback mConnectionCallback;
    private DomainSelectionConnection mDsc;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        mDomainSelectionController = Mockito.mock(DomainSelectionController.class);
        doReturn(true).when(mDomainSelectionController).selectDomain(any(), any());
        mConnectionCallback =
                Mockito.mock(DomainSelectionConnection.DomainSelectionConnectionCallback.class);
    }

    @After
    public void tearDown() throws Exception {
        mDsc.finishSelection();
        mDsc = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testTransportSelectorCallback() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);
    }

    @Test
    @SmallTest
    public void testSelectDomain() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        verify(mDomainSelectionController).selectDomain(eq(attr), eq(transportCallback));
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackAsync() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScan() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                false, resultCallback);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> eventCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(mPhone).registerForEmergencyNetworkScan(
                handlerCaptor.capture(), eventCaptor.capture(), any());

        int[] expectedPreferredNetworks = new int[] { EUTRAN, UTRAN };

        verify(mPhone).triggerEmergencyNetworkScan(eq(expectedPreferredNetworks),
                eq(scanType), any());

        Handler handler = handlerCaptor.getValue();
        int event = eventCaptor.getValue();

        assertNotNull(handler);

        EmergencyRegistrationResult regResult =
                new EmergencyRegistrationResult(UTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        verify(resultCallback).onComplete(eq(regResult));
        verify(mPhone, times(0)).cancelEmergencyNetworkScan(anyBoolean(), any());
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScanAndCancel() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, null, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        wwanCallback.onRequestEmergencyNetworkScan(new int[] { }, SCAN_TYPE_NO_PREFERENCE,
                false, Mockito.mock(IWwanSelectorResultCallback.class));
        processAllMessages();

        verify(mPhone).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone).triggerEmergencyNetworkScan(any(), anyInt(), any());

        wwanCallback.onCancel();
        processAllMessages();

        verify(mPhone).cancelEmergencyNetworkScan(eq(false), any());
    }

    @Test
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScanWithResetScan()
            throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                true, resultCallback);
        processAllMessages();

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        verify(mPhone).cancelEmergencyNetworkScan(eq(true), msgCaptor.capture());

        verify(mPhone, times(0)).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone, times(0)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        Message msg = msgCaptor.getValue();

        assertNotNull(msg);

        AsyncResult unused = AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> eventCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(mPhone).registerForEmergencyNetworkScan(
                handlerCaptor.capture(), eventCaptor.capture(), any());

        int[] expectedPreferredNetworks = new int[] { EUTRAN, UTRAN };

        verify(mPhone).triggerEmergencyNetworkScan(eq(expectedPreferredNetworks),
                eq(scanType), any());

        Handler handler = handlerCaptor.getValue();
        int event = eventCaptor.getValue();

        assertNotNull(handler);

        EmergencyRegistrationResult regResult =
                new EmergencyRegistrationResult(UTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        verify(resultCallback).onComplete(eq(regResult));
    }

    @Test
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScanWithResetScanDoneAndCancel()
            throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                true, resultCallback);
        processAllMessages();

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        verify(mPhone).cancelEmergencyNetworkScan(eq(true), msgCaptor.capture());
        verify(mPhone, times(0)).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone, times(0)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        Message msg = msgCaptor.getValue();

        assertNotNull(msg);

        // Reset completes.
        AsyncResult unused = AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        // Verify that scan is requested.
        verify(mPhone).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone).triggerEmergencyNetworkScan(any(), anyInt(), any());

        // Cancele scan after reset completes.
        wwanCallback.onCancel();
        processAllMessages();

        // Verify scan request is canceled.
        verify(mPhone).cancelEmergencyNetworkScan(eq(false), any());
        verify(mPhone, times(2)).cancelEmergencyNetworkScan(anyBoolean(), any());
    }

    @Test
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScanWithResetScanAndCancel()
            throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                true, resultCallback);
        processAllMessages();

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);

        verify(mPhone).cancelEmergencyNetworkScan(eq(true), msgCaptor.capture());
        verify(mPhone, times(0)).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone, times(0)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        Message msg = msgCaptor.getValue();

        assertNotNull(msg);

        // Canceled before reset completes.
        wwanCallback.onCancel();
        processAllMessages();

        // Verify there is no additional cancel.
        verify(mPhone, times(1)).cancelEmergencyNetworkScan(anyBoolean(), any());

        // Reset completes
        AsyncResult unused = AsyncResult.forMessage(msg);
        msg.sendToTarget();
        processAllMessages();

        // Verify there is no scan request after reset completes.
        verify(mPhone, times(0)).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone, times(0)).triggerEmergencyNetworkScan(any(), anyInt(), any());
    }

    @Test
    @SmallTest
    public void testDomainSelectorCancelSelection() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        mDsc.cancelSelection();

        verify(domainSelector).finishSelection();
    }

    @Test
    @SmallTest
    public void testDomainSelectorReselectDomain() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, CallFailCause.ERROR_UNSPECIFIED, TELECOM_CALL_ID1, null, null, null);

        CompletableFuture<Integer> future = mDsc.reselectDomain(attr);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(domainSelector).reselectDomain(eq(attr));
    }

    @Test
    @SmallTest
    public void testDomainSelectorFinishSelection() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        mDsc.finishSelection();

        verify(domainSelector).finishSelection();
    }

    @Test
    @SmallTest
    public void testQualifiedNetworkTypesChanged() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        List<QualifiedNetworks> networksList = new ArrayList<>();

        assertThat(mDsc.getPreferredTransport(ApnSetting.TYPE_EMERGENCY, networksList))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.IWLAN }));

        assertThat(mDsc.getPreferredTransport(ApnSetting.TYPE_EMERGENCY, networksList))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        networksList.clear();
        networksList.add(new QualifiedNetworks(ApnSetting.TYPE_EMERGENCY,
                new int[]{ AccessNetworkType.EUTRAN }));

        assertThat(mDsc.getPreferredTransport(ApnSetting.TYPE_EMERGENCY, networksList))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    @SmallTest
    public void testRemoteDomainSelectorRebindingService() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, null, null, null, null);

        mDsc.selectDomain(attr);

        verify(mDomainSelectionController, times(1)).selectDomain(eq(attr), eq(transportCallback));

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        // Detect failure
        mDsc.onServiceDisconnected();

        verify(mDomainSelectionController, times(1)).selectDomain(any(), eq(transportCallback));

        // Waiting for timeout
        processAllFutureMessages();

        verify(mDomainSelectionController).removeConnection(eq(mDsc));
    }

    @Test
    @SmallTest
    public void testRemoteDomainSelectorRebindingServiceWhenScanning() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, TELECOM_CALL_ID1, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        // 1st scan request from remote service
        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                false, resultCallback);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> eventCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(mPhone).registerForEmergencyNetworkScan(
                handlerCaptor.capture(), eventCaptor.capture(), any());

        int[] expectedPreferredNetworks = new int[] { EUTRAN, UTRAN };

        verify(mPhone, times(1)).triggerEmergencyNetworkScan(eq(expectedPreferredNetworks),
                eq(scanType), any());
        verify(mPhone, times(1)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        Handler handler = handlerCaptor.getValue();
        int event = eventCaptor.getValue();

        assertNotNull(handler);

        mDsc.onServiceDisconnected();

        assertTrue(mDsc.isWaitingForServiceBinding());

        verify(mDomainSelectionController, times(1)).selectDomain(eq(attr), eq(transportCallback));

        // reconnected to service
        transportCallback.onCreated(domainSelector);

        wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        // 2nd scan request
        IWwanSelectorResultCallback resultCallback2 =
                Mockito.mock(IWwanSelectorResultCallback.class);
        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                false, resultCallback2);
        processAllMessages();

        // Verify that triggerEmergencyNetworkScan isn't called
        verify(mPhone, times(1)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        // Result received
        EmergencyRegistrationResult regResult =
                new EmergencyRegistrationResult(UTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        // Verify that triggerEmergencyNetworkScan isn't called
        verify(mPhone, times(1)).triggerEmergencyNetworkScan(any(), anyInt(), any());
        verify(resultCallback, times(0)).onComplete(any());
        verify(resultCallback2, times(1)).onComplete(eq(regResult));
    }

    @Test
    @SmallTest
    public void testRemoteDomainSelectorRebindingServiceAfterScanCompleted() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, null, null, null, null);

        mDsc.selectDomain(attr);

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        transportCallback.onCreated(domainSelector);

        IWwanSelectorCallback wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        int[] preferredNetworks = new int[] { EUTRAN, UTRAN };
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        IWwanSelectorResultCallback resultCallback =
                Mockito.mock(IWwanSelectorResultCallback.class);

        // 1st scan request from remote service
        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                false, resultCallback);
        processAllMessages();

        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> eventCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(mPhone).registerForEmergencyNetworkScan(
                handlerCaptor.capture(), eventCaptor.capture(), any());

        int[] expectedPreferredNetworks = new int[] { EUTRAN, UTRAN };

        verify(mPhone, times(1)).triggerEmergencyNetworkScan(eq(expectedPreferredNetworks),
                eq(scanType), any());

        Handler handler = handlerCaptor.getValue();
        int event = eventCaptor.getValue();

        assertNotNull(handler);

        mDsc.onServiceDisconnected();

        verify(mDomainSelectionController, times(1)).selectDomain(eq(attr), eq(transportCallback));

        // Result received
        EmergencyRegistrationResult regResult =
                new EmergencyRegistrationResult(UTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        verify(resultCallback, times(0)).onComplete(any());

        // reconnected to service
        transportCallback.onCreated(domainSelector);

        wwanCallback = onWwanSelected(transportCallback);

        assertNotNull(wwanCallback);

        // 2nd scan request
        IWwanSelectorResultCallback resultCallback2 =
                Mockito.mock(IWwanSelectorResultCallback.class);
        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType,
                false, resultCallback2);
        processAllMessages();

        // Verify that triggerEmergencyNetworkScan is called
        verify(mPhone, times(2)).triggerEmergencyNetworkScan(any(), anyInt(), any());

        // Result received
        regResult =
                new EmergencyRegistrationResult(EUTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        verify(resultCallback, times(0)).onComplete(any());
        verify(resultCallback2, times(1)).onComplete(eq(regResult));
    }

    @Test
    @SmallTest
    public void testRemoteDomainSelectorRebindServiceWhenReselect() throws Exception {
        mDsc = createConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        ITransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, null, null, null, null);

        mDsc.selectDomain(attr);

        verify(mDomainSelectionController, times(1)).selectDomain(eq(attr), eq(transportCallback));

        IDomainSelector domainSelector = Mockito.mock(IDomainSelector.class);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                throw new RemoteException();
            }
        }).when(domainSelector).reselectDomain(any());
        transportCallback.onCreated(domainSelector);

        attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, CallFailCause.ERROR_UNSPECIFIED, null, null, null, null);

        CompletableFuture<Integer> future = mDsc.reselectDomain(attr);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(domainSelector).reselectDomain(eq(attr));
        verify(mDomainSelectionController, times(1)).selectDomain(any(), eq(transportCallback));
    }

    private DomainSelectionConnection createConnection(Phone phone, int selectorType,
            boolean isEmergency, DomainSelectionController controller) throws Exception {
        DomainSelectionConnection dsc = new DomainSelectionConnection(phone,
                selectorType, isEmergency, controller);
        dsc.setTestMode(true);
        replaceInstance(DomainSelectionConnection.class, "mLooper",
                dsc, mTestableLooper.getLooper());
        return dsc;
    }

    private DomainSelectionService.SelectionAttributes getSelectionAttributes(
            int slotId, int subId, int selectorType, boolean isEmergency,
            boolean exited, int callFailCause, String callId, String number,
            ImsReasonInfo imsReasonInfo, EmergencyRegistrationResult regResult) {
        DomainSelectionService.SelectionAttributes.Builder builder =
                new DomainSelectionService.SelectionAttributes.Builder(
                        slotId, subId, selectorType)
                .setEmergency(isEmergency)
                .setExitedFromAirplaneMode(exited)
                .setCsDisconnectCause(callFailCause);

        if (callId != null) builder.setCallId(callId);
        if (number != null) {
            builder.setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null));
        }
        if (imsReasonInfo != null) builder.setPsDisconnectCause(imsReasonInfo);
        if (regResult != null) builder.setEmergencyRegistrationResult(regResult);

        return builder.build();
    }

    private IWwanSelectorCallback onWwanSelected(ITransportSelectorCallback transportCallback)
            throws Exception {
        ITransportSelectorResultCallback cb = Mockito.mock(ITransportSelectorResultCallback.class);
        transportCallback.onWwanSelectedAsync(cb);
        processAllMessages();

        ArgumentCaptor<IWwanSelectorCallback> callbackCaptor =
                ArgumentCaptor.forClass(IWwanSelectorCallback.class);

        verify(cb).onCompleted(callbackCaptor.capture());

        return callbackCaptor.getValue();
    }
}
