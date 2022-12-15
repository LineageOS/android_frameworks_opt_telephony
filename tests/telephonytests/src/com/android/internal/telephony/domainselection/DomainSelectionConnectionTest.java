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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.CancellationSignal;
import android.os.Handler;
import android.telephony.DomainSelectionService;
import android.telephony.DomainSelector;
import android.telephony.EmergencyRegResult;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.telephony.ims.ImsReasonInfo;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.CallFailCause;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

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
        mConnectionCallback =
                Mockito.mock(DomainSelectionConnection.DomainSelectionConnectionCallback.class);
    }

    @After
    public void tearDown() throws Exception {
        mDsc = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testTransportSelectorCallback() {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);
    }

    @Test
    @SmallTest
    public void testSelectDomain() {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, 0, null, null, null, null);

        mDsc.selectDomain(attr);

        verify(mDomainSelectionController).selectDomain(any(), eq(transportCallback));
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallback() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        WwanSelectorCallback wwanCallback = null;
        wwanCallback = transportCallback.onWwanSelected();

        assertNotNull(wwanCallback);
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackAsync() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);
        replaceInstance(DomainSelectionConnection.class, "mWwanSelectedExecutor",
                mDsc, new Executor() {
                    public void execute(Runnable command) {
                        command.run();
                    }
                });

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        Consumer<WwanSelectorCallback> consumer = Mockito.mock(Consumer.class);
        transportCallback.onWwanSelected(consumer);

        verify(consumer).accept(any());
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScan() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        WwanSelectorCallback wwanCallback = transportCallback.onWwanSelected();

        assertNotNull(wwanCallback);

        replaceInstance(DomainSelectionConnection.class, "mLooper",
                mDsc, mTestableLooper.getLooper());
        List<Integer> preferredNetworks = new ArrayList<>();
        preferredNetworks.add(EUTRAN);
        preferredNetworks.add(UTRAN);
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        Consumer<EmergencyRegResult> consumer = Mockito.mock(Consumer.class);

        wwanCallback.onRequestEmergencyNetworkScan(preferredNetworks, scanType, null, consumer);

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

        doReturn(new Executor() {
            public void execute(Runnable r) {
                r.run();
            }
        }).when(mDomainSelectionController).getDomainSelectionServiceExecutor();
        EmergencyRegResult regResult =
                new EmergencyRegResult(UTRAN, 0, 0, true, false, 0, 0, "", "", "");
        handler.sendMessage(handler.obtainMessage(event, new AsyncResult(null, regResult, null)));
        processAllMessages();

        verify(consumer).accept(eq(regResult));
    }

    @Test
    @SmallTest
    public void testWwanSelectorCallbackOnRequestEmergencyNetworkScanAndCancel() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        WwanSelectorCallback wwanCallback = transportCallback.onWwanSelected();

        assertNotNull(wwanCallback);

        replaceInstance(DomainSelectionConnection.class, "mLooper",
                mDsc, mTestableLooper.getLooper());
        CancellationSignal signal = new CancellationSignal();
        wwanCallback.onRequestEmergencyNetworkScan(new ArrayList<>(),
                SCAN_TYPE_NO_PREFERENCE, signal, Mockito.mock(Consumer.class));

        verify(mPhone).registerForEmergencyNetworkScan(any(), anyInt(), any());
        verify(mPhone).triggerEmergencyNetworkScan(any(), anyInt(), any());

        signal.cancel();

        verify(mPhone).cancelEmergencyNetworkScan(eq(false), any());
    }

    @Test
    @SmallTest
    public void testDomainSelectorCancelSelection() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelector domainSelector = Mockito.mock(DomainSelector.class);
        transportCallback.onCreated(domainSelector);

        mDsc.cancelSelection();

        verify(domainSelector).cancelSelection();
    }

    @Test
    @SmallTest
    public void testDomainSelectorReselectDomain() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelector domainSelector = Mockito.mock(DomainSelector.class);
        transportCallback.onCreated(domainSelector);

        DomainSelectionService.SelectionAttributes attr = getSelectionAttributes(
                mPhone.getPhoneId(), mPhone.getSubId(), SELECTOR_TYPE_CALLING, true,
                false, CallFailCause.ERROR_UNSPECIFIED, null, null, null, null);

        CompletableFuture<Integer> future = mDsc.reselectDomain(attr);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(domainSelector).reselectDomain(any());
    }

    @Test
    @SmallTest
    public void testDomainSelectorFinishSelection() throws Exception {
        mDsc = new DomainSelectionConnection(mPhone, SELECTOR_TYPE_CALLING, true,
                mDomainSelectionController);

        TransportSelectorCallback transportCallback = mDsc.getTransportSelectorCallback();

        assertNotNull(transportCallback);

        DomainSelector domainSelector = Mockito.mock(DomainSelector.class);
        transportCallback.onCreated(domainSelector);

        mDsc.finishSelection();

        verify(domainSelector).finishSelection();
    }

    private DomainSelectionService.SelectionAttributes getSelectionAttributes(
            int slotId, int subId, int selectorType, boolean isEmergency,
            boolean exited, int callFailCause, String callId, String number,
            ImsReasonInfo imsReasonInfo, EmergencyRegResult regResult) {
        DomainSelectionService.SelectionAttributes.Builder builder =
                new DomainSelectionService.SelectionAttributes.Builder(
                        slotId, subId, selectorType)
                .setEmergency(isEmergency)
                .setExitedFromAirplaneMode(exited)
                .setCsDisconnectCause(callFailCause);

        if (callId != null) builder.setCallId(callId);
        if (number != null) builder.setNumber(number);
        if (imsReasonInfo != null) builder.setPsDisconnectCause(imsReasonInfo);
        if (regResult != null) builder.setEmergencyRegResult(regResult);

        return builder.build();
    }
}
