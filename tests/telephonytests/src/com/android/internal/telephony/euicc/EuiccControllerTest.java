/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.internal.telephony.euicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.service.euicc.DownloadResult;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class EuiccControllerTest extends TelephonyTest {
    private static final DownloadableSubscription SUBSCRIPTION =
            DownloadableSubscription.forActivationCode("abcde");

    @Mock private EuiccConnector mMockConnector;
    private EuiccController mController;

    @Before
    public void setUp() throws Exception {
        super.setUp("EuiccControllerTest");
        mController = new EuiccController(mContext, mMockConnector);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test(expected = SecurityException.class)
    public void testGetEid_noPrivileges() {
        setGetEidPermissions(false /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        callGetEid(true /* success */, "ABCDE" /* eid */);
    }

    @Test
    public void testGetEid_withPhoneStatePrivileged() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertEquals("ABCDE", callGetEid(true /* success */, "ABCDE" /* eid */));
    }

    @Test
    public void testGetEid_withCarrierPrivileges() {
        setGetEidPermissions(false /* hasPhoneStatePrivileged */, true /* hasCarrierPrivileges */);
        assertEquals("ABCDE", callGetEid(true /* success */, "ABCDE" /* eid */));
    }

    @Test
    public void testGetEid_failure() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertNull(callGetEid(false /* success */, null /* eid */));
    }

    @Test
    public void testGetEid_nullReturnValue() {
        setGetEidPermissions(true /* hasPhoneStatePrivileged */, false /* hasCarrierPrivileges */);
        assertNull(callGetEid(true /* success */, null /* eid */));
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callGetDownloadableSubscriptionMetadata(
                SUBSCRIPTION, false /* complete */, null /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                0 /* detailedCode */);
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_genericError() throws Exception {
        setHasWriteEmbeddedPermission(true);
        GetDownloadableSubscriptionMetadataResult result =
                GetDownloadableSubscriptionMetadataResult.genericError(42);
        callGetDownloadableSubscriptionMetadata(SUBSCRIPTION, true /* complete */, result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testGetDownloadableSubscriptionMetadata_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        DownloadableSubscription subscription = DownloadableSubscription.forActivationCode("abcde");
        subscription.setCarrierName("test name");
        GetDownloadableSubscriptionMetadataResult result =
                GetDownloadableSubscriptionMetadataResult.success(subscription);
        callGetDownloadableSubscriptionMetadata(SUBSCRIPTION, true /* complete */, result);
        Intent intent = verifyIntentSent(
                EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
        DownloadableSubscription receivedSubscription = intent.getParcelableExtra(
                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION);
        assertNotNull(receivedSubscription);
        assertEquals("test name", receivedSubscription.getCarrierName());
    }

    @Test
    public void testDownloadSubscription_serviceUnavailable() throws Exception {
        setHasWriteEmbeddedPermission(true);
        callDownloadSubscription(
                SUBSCRIPTION, true /* switchAfterDownload */, false /* complete */,
                null /* result */);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                0 /* detailedCode */);
    }

    @Test
    public void testDownloadSubscription_genericError() throws Exception {
        setHasWriteEmbeddedPermission(true);
        DownloadResult result = DownloadResult.genericError(42);
        callDownloadSubscription(SUBSCRIPTION, false /* switchAfterDownload */, true /* complete */,
                result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                42 /* detailedCode */);
    }

    @Test
    public void testDownloadSubscription_success() throws Exception {
        setHasWriteEmbeddedPermission(true);
        DownloadResult result = DownloadResult.success();
        callDownloadSubscription(SUBSCRIPTION, true /* switchAfterDownload */, true /* complete */,
                result);
        verifyIntentSent(EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK, 0 /* detailedCode */);
    }

    // TODO(b/33075886): Add security tests once carrier privilege checks are implemented.
    // TODO(b/33075886): Add resolvable error tests once resolvable errors are implemented.

    private void setGetEidPermissions(
            boolean hasPhoneStatePrivileged, boolean hasCarrierPrivileges) {
        doReturn(hasPhoneStatePrivileged
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED)
                .when(mContext)
                .checkCallingPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        when(mTelephonyManager.hasCarrierPrivileges()).thenReturn(hasCarrierPrivileges);
    }

    private void setHasWriteEmbeddedPermission(boolean hasPermission) {
        doReturn(hasPermission
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED)
                .when(mContext)
                .checkCallingPermission(Manifest.permission.WRITE_EMBEDDED_SUBSCRIPTIONS);
    }

    private String callGetEid(final boolean success, final @Nullable String eid) {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetEidCommandCallback cb = invocation.getArgument(0);
                if (success) {
                    cb.onGetEidComplete(eid);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getEid(Mockito.<EuiccConnector.GetEidCommandCallback>any());
        return mController.getEid();
    }

    private void callGetDownloadableSubscriptionMetadata(DownloadableSubscription subscription,
            final boolean complete, final GetDownloadableSubscriptionMetadataResult result) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.GetMetadataCommandCallback cb = invocation.getArgument(1);
                if (complete) {
                    cb.onGetMetadataComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).getDownloadableSubscriptionMetadata(
                Mockito.<DownloadableSubscription>any(),
                Mockito.<EuiccConnector.GetMetadataCommandCallback>any());
        mController.getDownloadableSubscriptionMetadata(subscription, resultCallback);
    }

    private void callDownloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, final boolean complete, final DownloadResult result) {
        PendingIntent resultCallback = PendingIntent.getBroadcast(mContext, 0, new Intent(), 0);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                EuiccConnector.DownloadCommandCallback cb = invocation.getArgument(2);
                if (complete) {
                    cb.onDownloadComplete(result);
                } else {
                    cb.onEuiccServiceUnavailable();
                }
                return null;
            }
        }).when(mMockConnector).downloadSubscription(
                Mockito.<DownloadableSubscription>any(),
                Mockito.eq(switchAfterDownload),
                Mockito.<EuiccConnector.DownloadCommandCallback>any());
        mController.downloadSubscription(subscription, switchAfterDownload, resultCallback);
    }

    private Intent verifyIntentSent(int resultCode, int detailedCode)
            throws RemoteException {
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        verify(mIActivityManager).sendIntentSender(Mockito.<IIntentSender>any(),
                eq(resultCode),
                intentCapture.capture(), Mockito.<String>any(), Mockito.<IIntentReceiver>any(),
                Mockito.<String>any(), Mockito.<Bundle>any());
        Intent capturedIntent = intentCapture.getValue();
        if (capturedIntent == null) {
            assertEquals(0, detailedCode);
        } else {
            assertEquals(detailedCode,
                    capturedIntent.getIntExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE, 0));
        }
        return capturedIntent;
    }
}
