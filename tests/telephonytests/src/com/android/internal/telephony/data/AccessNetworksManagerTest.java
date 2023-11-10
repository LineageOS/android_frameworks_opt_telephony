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

package com.android.internal.telephony.data;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.NetworkCapabilities;
import android.os.AsyncResult;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.NetworkService;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.IQualifiedNetworksService;
import android.telephony.data.IQualifiedNetworksServiceCallback;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager.AccessNetworksManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AccessNetworksManagerTest extends TelephonyTest {
    private AccessNetworksManager mAccessNetworksManager;

    // Mocked classes
    private IQualifiedNetworksService mMockedQns;
    private IBinder mMockedIBinder;
    private AccessNetworksManagerCallback mMockedCallback;
    private DataConfigManager mMockedDataConfigManager;

    // The real callback passed created by AccessNetworksManager.
    private IQualifiedNetworksServiceCallback.Stub mQnsCallback;
    private PersistableBundle mBundle;
    private List<Integer> mIIntegerConsumerResults =  new ArrayList<>();
    private Semaphore mIIntegerConsumerSemaphore = new Semaphore(0);
    private IIntegerConsumer mIIntegerConsumer = new IIntegerConsumer.Stub() {
        @Override
        public void accept(int result) {
            logd("mIIntegerConsumer: result=" + result);
            mIIntegerConsumerResults.add(result);
            try {
                mIIntegerConsumerSemaphore.release();
            } catch (Exception ex) {
                logd("mIIntegerConsumer: Got exception in releasing semaphore, ex=" + ex);
            }
        }
    };

    private boolean waitForIIntegerConsumerResult(int expectedNumberOfEvents) {
        for (int i = 0; i < expectedNumberOfEvents; i++) {
            try {
                if (!mIIntegerConsumerSemaphore.tryAcquire(500 /* Timeout */,
                        TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive IIntegerConsumer() callback");
                    return false;
                }
            } catch (Exception ex) {
                logd("waitForIIntegerConsumerResult: Got exception=" + ex);
                return false;
            }
        }
        return true;
    }

    private void addQnsService() throws Exception {
        ServiceInfo QnsInfo = new ServiceInfo();
        QnsInfo.packageName = "fake.qns";
        QnsInfo.name = "QualifiedNetworksService";
        QnsInfo.permission = "android.permission.BIND_TELEPHONY_NETWORK_SERVICE";
        IntentFilter qnsIntentfilter = new IntentFilter();
        doReturn(mMockedIBinder).when(mMockedQns).asBinder();
        doReturn(mMockedQns).when(mMockedIBinder).queryLocalInterface(anyString());

        doAnswer(invocation -> {
            mQnsCallback = (IQualifiedNetworksServiceCallback.Stub) invocation.getArguments()[1];
            return null;
        }).when(mMockedQns).createNetworkAvailabilityProvider(anyInt(),
                any(IQualifiedNetworksServiceCallback.Stub.class));
        mContextFixture.addService(
                NetworkService.SERVICE_INTERFACE,
                new ComponentName("fake.qns",
                        "fake.qns"),
                "fake.qns",
                mMockedQns,
                QnsInfo,
                qnsIntentfilter);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockedQns = mock(IQualifiedNetworksService.class);
        mMockedIBinder = mock(IBinder.class);

        mBundle = mContextFixture.getCarrierConfigBundle();
        when(mCarrierConfigManager.getConfigForSubId(anyInt(), any())).thenReturn(mBundle);

        addQnsService();
        mContextFixture.putResource(
                com.android.internal.R.string.config_qualified_networks_service_package,
                "fake.qns");

        mMockedCallback = Mockito.mock(AccessNetworksManagerCallback.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedCallback).invokeFromExecutor(any(Runnable.class));

        mMockedDataConfigManager = Mockito.mock(DataConfigManager.class);
        mAccessNetworksManager =
                new AccessNetworksManager(mPhone, Looper.myLooper(), mFeatureFlags);

        processAllMessages();
        replaceInstance(AccessNetworksManager.class, "mDataConfigManager",
                mAccessNetworksManager, mMockedDataConfigManager);

        logd("-setUp");
    }

    @After
    public void tearDown() throws Exception {
        mAccessNetworksManager = null;
        mBundle = null;
        super.tearDown();
    }

    @Test
    public void testBindService() {
        assertThat(mQnsCallback).isNotNull();
    }

    @Test
    public void testQualifiedNetworkTypesChanged() throws Exception {
        assertThat(mQnsCallback).isNotNull();
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();

        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[]{AccessNetworkType.IWLAN});
        processAllMessages();

        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isTrue();
    }

    @Test
    public void testGuideTransportTypeForEmergencyDataNetwork() throws Exception {
        doAnswer(invocation -> {
            int accessNetwork = AccessNetworkType.UNKNOWN;
            if (invocation.getArguments()[1].equals(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)) {
                accessNetwork = AccessNetworkType.IWLAN;
            } else if (invocation.getArguments()[1]
                    .equals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)) {
                accessNetwork = AccessNetworkType.EUTRAN;
            }
            mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_EMERGENCY,
                    new int[]{accessNetwork});
            return null;
        }).when(mMockedQns).reportEmergencyDataNetworkPreferredTransportChanged(anyInt(), anyInt());

        AsyncResult asyncResult =
                new AsyncResult(null, AccessNetworkConstants.TRANSPORT_TYPE_WLAN, null);
        Message msg = this.mAccessNetworksManager
                .obtainMessage(1 /* EVENT_GUIDE_TRANSPORT_TYPE_FOR_EMERGENCY */, asyncResult);
        mAccessNetworksManager.sendMessage(msg);
        processAllMessages();

        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_EMERGENCY))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testEmptyNetworkTypes() throws Exception {
        testQualifiedNetworkTypesChanged();

        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[0]);
        processAllMessages();

        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();
    }

    @Test
    public void testInvalidNetworkTypes() throws Exception {
        testQualifiedNetworkTypesChanged();

        // Input unknown would become a no-op
        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[]{AccessNetworkType.EUTRAN, AccessNetworkType.UNKNOWN});
        processAllMessages();

        // There shouldn't be any changes.
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isTrue();
    }

    @Test
    public void testEmptyList() throws Exception {
        testQualifiedNetworkTypesChanged();

        // Empty list input
        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[0]);
        processAllMessages();

        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();
    }

    @Test
    public void testCallback() throws Exception {
        mAccessNetworksManager.registerCallback(mMockedCallback);

        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[]{AccessNetworkType.IWLAN});
        processAllMessages();

        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_MMS));
        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_IMS));
        Mockito.clearInvocations(mMockedCallback);
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_MMS)).isEqualTo(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_XCAP,
                new int[]{AccessNetworkType.IWLAN});
        processAllMessages();

        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_XCAP));
        Mockito.clearInvocations(mMockedCallback);
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_XCAP)).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        mQnsCallback.onQualifiedNetworkTypesChanged(
                ApnSetting.TYPE_XCAP | ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[]{});
        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_IMS));
        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_MMS));
        verify(mMockedCallback).onPreferredTransportChanged(
                eq(NetworkCapabilities.NET_CAPABILITY_XCAP));
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_MMS)).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransportByNetworkCapability(
                NetworkCapabilities.NET_CAPABILITY_XCAP)).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testRequestNetworkValidation_WithFlagEnabled()  throws Exception {
        when(mFeatureFlags.networkValidation()).thenReturn(true);

        mQnsCallback.onNetworkValidationRequested(NetworkCapabilities.NET_CAPABILITY_IMS,
                mIIntegerConsumer);
        processAllMessages();
        assertThat(waitForIIntegerConsumerResult(1 /*numOfEvents*/)).isFalse();
    }

    @Test
    public void testRequestNetworkValidation_WithFlagDisabled() throws Exception {
        mIIntegerConsumerResults.clear();
        when(mFeatureFlags.networkValidation()).thenReturn(false);

        mQnsCallback.onNetworkValidationRequested(NetworkCapabilities.NET_CAPABILITY_IMS,
                mIIntegerConsumer);
        processAllMessages();

        assertThat(waitForIIntegerConsumerResult(1 /*numOfEvents*/)).isTrue();
        assertThat((long) mIIntegerConsumerResults.get(0))
                .isEqualTo(DataServiceCallback.RESULT_ERROR_UNSUPPORTED);
        verify(mDataNetworkController, never()).requestNetworkValidation(
                NetworkCapabilities.NET_CAPABILITY_IMS,
                mIntegerConsumer);
    }

}
