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

import static android.telephony.DisconnectCause.ERROR_UNSPECIFIED;
import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.telephony.DomainSelectionService;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.telephony.ims.ImsReasonInfo;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager;
import com.android.internal.telephony.domainselection.DomainSelectionConnection;
import com.android.internal.telephony.domainselection.DomainSelectionController;
import com.android.internal.telephony.domainselection.NormalCallDomainSelectionConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NormalCallDomainSelectionConnectionTest extends TelephonyTest {

    private static final String TELECOM_CALL_ID1 = "TC1";

    @Mock
    private DomainSelectionController mMockDomainSelectionController;
    @Mock
    private DomainSelectionConnection.DomainSelectionConnectionCallback mMockConnectionCallback;
    @Mock
    private AccessNetworksManager mMockAccessNetworksManager;

    private TransportSelectorCallback mTransportCallback;
    private NormalCallDomainSelectionConnection mNormalCallDomainSelectionConnection;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        doReturn(mMockAccessNetworksManager).when(mPhone).getAccessNetworksManager();
        mNormalCallDomainSelectionConnection =
                new NormalCallDomainSelectionConnection(mPhone, mMockDomainSelectionController);
        mTransportCallback = mNormalCallDomainSelectionConnection.getTransportSelectorCallback();
    }

    @After
    public void tearDown() throws Exception {
        mNormalCallDomainSelectionConnection = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSelectDomainWifi() throws Exception {
        DomainSelectionService.SelectionAttributes attributes =
                NormalCallDomainSelectionConnection.getSelectionAttributes(mPhone.getPhoneId(),
                        mPhone.getSubId(), TELECOM_CALL_ID1, "123", false, 0, null);

        CompletableFuture<Integer> future =
                mNormalCallDomainSelectionConnection
                        .createNormalConnection(attributes, mMockConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mMockDomainSelectionController).selectDomain(any(), any());

        mTransportCallback.onWlanSelected(false);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_PS, (long) future.get());
    }

    @Test
    @SmallTest
    public void testSelectDomainCs() throws Exception {
        DomainSelectionService.SelectionAttributes attributes =
                NormalCallDomainSelectionConnection.getSelectionAttributes(mPhone.getPhoneId(),
                        mPhone.getSubId(), TELECOM_CALL_ID1, "123", false, 0, null);

        CompletableFuture<Integer> future =
                mNormalCallDomainSelectionConnection
                        .createNormalConnection(attributes, mMockConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mMockDomainSelectionController).selectDomain(any(), any());

        WwanSelectorCallback wwanCallback = mTransportCallback.onWwanSelected();

        assertFalse(future.isDone());
        wwanCallback.onDomainSelected(DOMAIN_CS, false);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_CS, (long) future.get());
    }

    @Test
    @SmallTest
    public void testSelectDomainPs() throws Exception {
        DomainSelectionService.SelectionAttributes attributes =
                NormalCallDomainSelectionConnection.getSelectionAttributes(mPhone.getPhoneId(),
                        mPhone.getSubId(), TELECOM_CALL_ID1, "123", false, 0, null);

        CompletableFuture<Integer> future =
                mNormalCallDomainSelectionConnection
                        .createNormalConnection(attributes, mMockConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mMockDomainSelectionController).selectDomain(any(), any());

        WwanSelectorCallback wwanCallback = mTransportCallback.onWwanSelected();

        assertFalse(future.isDone());
        wwanCallback.onDomainSelected(DOMAIN_PS, false);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_PS, (long) future.get());
    }

    @Test
    @SmallTest
    public void testOnSelectionTerminated() throws Exception {
        DomainSelectionService.SelectionAttributes attributes =
                NormalCallDomainSelectionConnection.getSelectionAttributes(mPhone.getPhoneId(),
                        mPhone.getSubId(), TELECOM_CALL_ID1, "123", false, 0, null);

        CompletableFuture<Integer> future = mNormalCallDomainSelectionConnection
                .createNormalConnection(attributes, mMockConnectionCallback);
        mTransportCallback.onSelectionTerminated(ERROR_UNSPECIFIED);

        verify(mMockConnectionCallback).onSelectionTerminated(eq(ERROR_UNSPECIFIED));
        assertNotNull(future);
    }

    @Test
    public void testGetSelectionAttributes() throws Exception {
        ImsReasonInfo imsReasonInfo = new ImsReasonInfo();
        DomainSelectionService.SelectionAttributes attributes =
                NormalCallDomainSelectionConnection.getSelectionAttributes(1, 2,
                        TELECOM_CALL_ID1, "123", false, 10, imsReasonInfo);

        assertEquals(1, attributes.getSlotId());
        assertEquals(2, attributes.getSubId());
        assertEquals(TELECOM_CALL_ID1, attributes.getCallId());
        assertEquals("123", attributes.getNumber());
        assertEquals(false, attributes.isVideoCall());
        assertEquals(false, attributes.isEmergency());
        assertEquals(SELECTOR_TYPE_CALLING, attributes.getSelectorType());
        assertEquals(10, attributes.getCsDisconnectCause());
        assertEquals(imsReasonInfo, attributes.getPsDisconnectCause());
    }
}
