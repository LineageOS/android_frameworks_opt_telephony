/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.internal.telephony;

import static com.android.internal.telephony.TelephonyStatsLog.GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telephony.IBootstrapAuthenticationCallback;
import android.telephony.TelephonyManager;
import android.telephony.gba.GbaAuthRequest;
import android.telephony.gba.GbaService;
import android.telephony.gba.IGbaService;
import android.telephony.gba.UaSecurityProtocolIdentifier;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Log;

import com.android.internal.telephony.metrics.RcsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for GbaManager
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public final class GbaManagerTest extends TelephonyTest {
    private static final String LOG_TAG = "GbaManagerTest";

    private static final ComponentName TEST_DEFAULT_SERVICE_NAME = new ComponentName(
            "TestGbaPkg", "TestGbaService");
    private static final ComponentName TEST_SERVICE2_NAME = new ComponentName(
            "TestGbaPkg2", "TestGbaService2");
    private static final int RELEASE_NEVER = -1;
    private static final int RELEASE_IMMEDIATELY = 0;
    private static final int RELEASE_TIME_60S = 60 * 1000;
    private static final int TEST_SUB_ID = Integer.MAX_VALUE;

    // Mocked classes
    Context mMockContext;
    IBinder mMockBinder;
    IGbaService mMockGbaServiceBinder;
    IBootstrapAuthenticationCallback mMockCallback;
    RcsStats mMockRcsStats;

    private GbaManager mTestGbaManager;
    private Handler mHandler;
    private TestableLooper mLooper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        log("setUp");
        mMockContext = mock(Context.class);
        mMockBinder = mock(IBinder.class);
        mMockGbaServiceBinder = mock(IGbaService.class);
        mMockCallback = mock(IBootstrapAuthenticationCallback.class);
        mMockRcsStats = mock(RcsStats.class);
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any(UserHandle.class)))
                .thenReturn(true);
        when(mMockGbaServiceBinder.asBinder()).thenReturn(mMockBinder);
        mTestGbaManager = new GbaManager(mMockContext, TEST_SUB_ID, null, 0, mMockRcsStats);
        mHandler = mTestGbaManager.getHandler();
        try {
            mLooper = new TestableLooper(mHandler.getLooper());
        } catch (Exception e) {
            fail("Unable to create looper from handler.");
        }
        monitorTestableLooper(mLooper);
    }

    @After
    public void tearDown() throws Exception {
        log("tearDown");
        mTestGbaManager.destroy();
        super.tearDown();
    }

    @Test
    public void testFailOnRequest() throws Exception {
        GbaAuthRequest request = createDefaultRequest();

        mTestGbaManager.bootstrapAuthenticationRequest(request);
        processAllMessages();

        verify(mMockContext, never()).bindServiceAsUser(any(), any(), anyInt(),
                any(UserHandle.class));
        verify(mMockCallback).onAuthenticationFailure(anyInt(), anyInt());
        assertTrue(!mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testBindServiceOnRequest() throws Exception {
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        GbaAuthRequest request = createDefaultRequest();

        mTestGbaManager.bootstrapAuthenticationRequest(request);
        processAllMessages();
        bindAndConnectService(TEST_DEFAULT_SERVICE_NAME);
        processAllMessages();

        verify(mMockGbaServiceBinder).authenticationRequest(any());
        assertTrue(mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testFailAndRetryOnRequest() throws RemoteException {
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any(UserHandle.class)))
                .thenReturn(false);
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        GbaAuthRequest request = createDefaultRequest();

        mTestGbaManager.bootstrapAuthenticationRequest(request);

        for (int i = 0; i < GbaManager.MAX_RETRY; i++) {
            processAllMessages();
            verify(mMockContext, times(i + 1)).bindServiceAsUser(any(), any(), anyInt(),
                    any(UserHandle.class));
            moveTimeForward(GbaManager.REQUEST_TIMEOUT_MS);
        }
        assertTrue(!mTestGbaManager.isServiceConnected());
        processAllMessages();
        verify(mMockCallback).onAuthenticationFailure(anyInt(), anyInt());
    }

    @Test
    public void testBindServiceWhenPackageNameChanged() {
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        mTestGbaManager.overrideReleaseTime(RELEASE_TIME_60S);
        GbaAuthRequest request = createDefaultRequest();

        mTestGbaManager.bootstrapAuthenticationRequest(request);
        processAllMessages();
        ServiceConnection conn = bindAndConnectService(TEST_DEFAULT_SERVICE_NAME);
        mTestGbaManager.overrideServicePackage(TEST_SERVICE2_NAME.getPackageName(), 123);

        assertEquals(TEST_SERVICE2_NAME.getPackageName(), mTestGbaManager.getServicePackage());

        processAllMessages();
        unbindService(conn);
        bindAndConnectService(TEST_SERVICE2_NAME);
        assertTrue(mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testBindServiceWhenReleaseTimeChanged() {
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        mTestGbaManager.overrideReleaseTime(RELEASE_NEVER);

        assertEquals(RELEASE_NEVER, mTestGbaManager.getReleaseTime());
        processAllMessages();
        bindAndConnectService(TEST_DEFAULT_SERVICE_NAME);

        assertTrue(mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testDontBindServiceWhenPackageNameChanged() {
        mTestGbaManager.overrideServicePackage(TEST_SERVICE2_NAME.getPackageName(), 123);

        processAllMessages();

        verify(mMockContext, never()).bindServiceAsUser(any(), any(), anyInt(),
                any(UserHandle.class));
        assertTrue(!mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testDontBindServiceWhenReleaseTimeChanged() {
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        mTestGbaManager.overrideReleaseTime(RELEASE_TIME_60S);

        processAllMessages();

        verify(mMockContext, never()).bindServiceAsUser(any(), any(), anyInt(),
                any(UserHandle.class));
        assertTrue(!mTestGbaManager.isServiceConnected());
    }

    @Test
    public void testMetricsGbaEvent() throws Exception {
        mTestGbaManager.overrideServicePackage(TEST_DEFAULT_SERVICE_NAME.getPackageName(), 123);
        mTestGbaManager.overrideReleaseTime(RELEASE_NEVER);

        processAllMessages();
        bindAndConnectService(TEST_DEFAULT_SERVICE_NAME);
        GbaAuthRequest request = createDefaultRequest();

        // Failure case
        mTestGbaManager.bootstrapAuthenticationRequest(request);
        processAllMessages();

        ArgumentCaptor<GbaAuthRequest> captor = ArgumentCaptor.forClass(GbaAuthRequest.class);
        verify(mMockGbaServiceBinder, times(1)).authenticationRequest(captor.capture());

        GbaAuthRequest capturedRequest = captor.getValue();
        IBootstrapAuthenticationCallback callback = capturedRequest.getCallback();
        callback.onAuthenticationFailure(capturedRequest.getToken(),
                GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY);

        verify(mMockRcsStats).onGbaFailureEvent(anyInt(),
                eq(GBA_EVENT__FAILED_REASON__FEATURE_NOT_READY));

        // Success case
        mTestGbaManager.bootstrapAuthenticationRequest(request);
        processAllMessages();

        ArgumentCaptor<GbaAuthRequest> captor2 = ArgumentCaptor.forClass(GbaAuthRequest.class);
        verify(mMockGbaServiceBinder, times(2)).authenticationRequest(captor2.capture());

        GbaAuthRequest capturedRequest2 = captor2.getValue();
        IBootstrapAuthenticationCallback callback2 = capturedRequest2.getCallback();
        callback2.onKeysAvailable(capturedRequest2.getToken(), "".getBytes(), "");

        verify(mMockRcsStats).onGbaSuccessEvent(anyInt());
    }

    private ServiceConnection bindAndConnectService(ComponentName component) {
        ServiceConnection connection = bindService(component);
        IGbaService.Stub serviceStub = mock(IGbaService.Stub.class);
        when(mMockBinder.isBinderAlive()).thenReturn(true);
        when(serviceStub.queryLocalInterface(any())).thenReturn(mMockGbaServiceBinder);
        connection.onServiceConnected(component, serviceStub);
        return connection;
    }

    private ServiceConnection bindService(ComponentName component) {
        ArgumentCaptor<Intent> intentCaptor =
                ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mMockContext, atLeastOnce()).bindServiceAsUser(intentCaptor.capture(),
                serviceCaptor.capture(), eq(
                        Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE),
                any(UserHandle.class));
        Intent testIntent = intentCaptor.getValue();
        assertEquals(GbaService.SERVICE_INTERFACE, testIntent.getAction());
        assertEquals(component.getPackageName(), testIntent.getPackage());
        return serviceCaptor.getValue();
    }

    private void unbindService(ServiceConnection conn) {
        verify(mMockContext).unbindService(eq(conn));
    }

    private GbaAuthRequest createDefaultRequest() {
        final String naf = "3GPP-bootstrapping@naf1.operator.com";
        final UaSecurityProtocolIdentifier.Builder builder =
                new UaSecurityProtocolIdentifier.Builder();
        builder.setOrg(UaSecurityProtocolIdentifier.ORG_3GPP).setProtocol(
                UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_HTTP_BASED_MBMS);
        return new GbaAuthRequest(TEST_SUB_ID, TelephonyManager.APPTYPE_USIM,
                Uri.parse(naf), builder.build().toByteArray(), true, mMockCallback);
    }

    private void log(String str) {
        Log.d(LOG_TAG, str);
    }
}
