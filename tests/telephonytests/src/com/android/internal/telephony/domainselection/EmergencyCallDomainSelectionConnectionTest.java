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
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
import static android.telephony.DisconnectCause.ERROR_UNSPECIFIED;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_PS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;

import static com.android.internal.telephony.PhoneConstants.DOMAIN_NON_3GPP_PS;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WLAN;
import static com.android.internal.telephony.emergency.EmergencyConstants.MODE_EMERGENCY_WWAN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.telephony.DomainSelectionService;
import android.telephony.EmergencyRegResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TransportSelectorCallback;
import android.telephony.WwanSelectorCallback;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager;
import com.android.internal.telephony.emergency.EmergencyStateTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class EmergencyCallDomainSelectionConnectionTest extends TelephonyTest {

    private static final String TELECOM_CALL_ID1 = "TC1";

    private DomainSelectionController mDomainSelectionController;
    private DomainSelectionConnection.DomainSelectionConnectionCallback mConnectionCallback;
    private EmergencyCallDomainSelectionConnection mEcDsc;
    private AccessNetworksManager mAnm;
    private TransportSelectorCallback mTransportCallback;
    private EmergencyStateTracker mEmergencyStateTracker;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());

        mDomainSelectionController = Mockito.mock(DomainSelectionController.class);
        mConnectionCallback =
                Mockito.mock(DomainSelectionConnection.DomainSelectionConnectionCallback.class);
        mEmergencyStateTracker = Mockito.mock(EmergencyStateTracker.class);
        mAnm = Mockito.mock(AccessNetworksManager.class);
        doReturn(mAnm).when(mPhone).getAccessNetworksManager();
        mEcDsc = new EmergencyCallDomainSelectionConnection(mPhone,
                mDomainSelectionController, mEmergencyStateTracker);
        mTransportCallback = mEcDsc.getTransportSelectorCallback();
    }

    @After
    public void tearDown() throws Exception {
        mEcDsc = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSelectDomainWifi() throws Exception {
        doReturn(TRANSPORT_TYPE_WLAN).when(mAnm).getPreferredTransport(anyInt());
        replaceInstance(EmergencyCallDomainSelectionConnection.class,
                "mEmergencyStateTracker", mEcDsc, mEmergencyStateTracker);

        EmergencyRegResult regResult = new EmergencyRegResult(
                EUTRAN, REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, false, 0, 0, "", "", "");

        DomainSelectionService.SelectionAttributes attr =
                EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                        mPhone.getPhoneId(), mPhone.getSubId(), false,
                        TELECOM_CALL_ID1, "911", 0, null, regResult);

        CompletableFuture<Integer> future =
                mEcDsc.createEmergencyConnection(attr, mConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mDomainSelectionController).selectDomain(any(), any());

        mTransportCallback.onWlanSelected(true);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_NON_3GPP_PS, (long) future.get());
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_CALL), eq(MODE_EMERGENCY_WLAN));
    }

    @Test
    @SmallTest
    public void testSelectDomainCs() throws Exception {
        doReturn(TRANSPORT_TYPE_WWAN).when(mAnm).getPreferredTransport(anyInt());
        replaceInstance(EmergencyCallDomainSelectionConnection.class,
                "mEmergencyStateTracker", mEcDsc, mEmergencyStateTracker);

        EmergencyRegResult regResult = new EmergencyRegResult(
                UTRAN, REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.DOMAIN_CS,
                true, false, 0, 0, "", "", "");

        DomainSelectionService.SelectionAttributes attr =
                EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                        mPhone.getPhoneId(), mPhone.getSubId(), false,
                        TELECOM_CALL_ID1, "911", 0, null, regResult);

        CompletableFuture<Integer> future =
                mEcDsc.createEmergencyConnection(attr, mConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mDomainSelectionController).selectDomain(any(), any());

        WwanSelectorCallback wwanCallback = null;
        wwanCallback = mTransportCallback.onWwanSelected();

        assertFalse(future.isDone());
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_CALL), eq(MODE_EMERGENCY_WWAN));

        wwanCallback.onDomainSelected(DOMAIN_CS, false);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_CS, (long) future.get());
    }

    @Test
    @SmallTest
    public void testSelectDomainPs() throws Exception {
        doReturn(TRANSPORT_TYPE_WWAN).when(mAnm).getPreferredTransport(anyInt());
        replaceInstance(EmergencyCallDomainSelectionConnection.class,
                "mEmergencyStateTracker", mEcDsc, mEmergencyStateTracker);

        EmergencyRegResult regResult = new EmergencyRegResult(
                EUTRAN, REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "", "");

        DomainSelectionService.SelectionAttributes attr =
                EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                        mPhone.getPhoneId(), mPhone.getSubId(), false,
                        TELECOM_CALL_ID1, "911", 0, null, regResult);

        CompletableFuture<Integer> future =
                mEcDsc.createEmergencyConnection(attr, mConnectionCallback);

        assertNotNull(future);
        assertFalse(future.isDone());

        verify(mDomainSelectionController).selectDomain(any(), any());

        WwanSelectorCallback wwanCallback = null;
        wwanCallback = mTransportCallback.onWwanSelected();

        assertFalse(future.isDone());
        verify(mEmergencyStateTracker).onEmergencyTransportChanged(
                eq(EmergencyStateTracker.EMERGENCY_TYPE_CALL), eq(MODE_EMERGENCY_WWAN));

        wwanCallback.onDomainSelected(DOMAIN_PS, true);

        assertTrue(future.isDone());
        assertEquals((long) DOMAIN_PS, (long) future.get());
    }

    @Test
    @SmallTest
    public void testOnSelectionTerminated() throws Exception {
        EmergencyRegResult regResult = new EmergencyRegResult(
                EUTRAN, REGISTRATION_STATE_UNKNOWN,
                NetworkRegistrationInfo.DOMAIN_PS,
                true, true, 0, 0, "", "", "");

        DomainSelectionService.SelectionAttributes attr =
                EmergencyCallDomainSelectionConnection.getSelectionAttributes(
                        mPhone.getPhoneId(), mPhone.getSubId(), false,
                        TELECOM_CALL_ID1, "911", 0, null, regResult);

        mEcDsc.createEmergencyConnection(attr, mConnectionCallback);
        mTransportCallback.onSelectionTerminated(ERROR_UNSPECIFIED);

        verify(mConnectionCallback).onSelectionTerminated(eq(ERROR_UNSPECIFIED));
    }

    @Test
    @SmallTest
    public void testCancelSelection() throws Exception {
        mEcDsc.cancelSelection();
        verify(mAnm).unregisterForQualifiedNetworksChanged(any());
    }
}
