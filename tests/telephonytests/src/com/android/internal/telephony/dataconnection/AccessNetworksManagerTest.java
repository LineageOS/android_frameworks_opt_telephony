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

package com.android.internal.telephony.dataconnection;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkService;
import android.telephony.data.ApnSetting;
import android.telephony.data.IQualifiedNetworksService;
import android.telephony.data.IQualifiedNetworksServiceCallback;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AccessNetworksManagerTest extends TelephonyTest {

    private AccessNetworksManager mAccessNetworksManager;

    @Mock
    private IQualifiedNetworksService mMockedQns;

    @Mock
    private IBinder mMockedIBinder;

    // The real callback passed created by AccessNetworksManager.
    private IQualifiedNetworksServiceCallback.Stub mQnsCallback;

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

        doReturn(true).when(mPhone).isUsingNewDataStack();

        addQnsService();
        mContextFixture.putResource(
                com.android.internal.R.string.config_qualified_networks_service_package,
                "fake.qns");

        mAccessNetworksManager = new AccessNetworksManager(mPhone);
        processAllMessages();
        logd("-setUp");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testBindService() {
        if (mAccessNetworksManager.isInLegacyMode()) return;
        assertThat(mQnsCallback).isNotNull();
    }

    @Test
    public void testQualifiedNetworkTypesChanged() throws Exception {
        if (mAccessNetworksManager.isInLegacyMode()) return;
        assertThat(mQnsCallback).isNotNull();
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isFalse();

        mQnsCallback.onQualifiedNetworkTypesChanged(ApnSetting.TYPE_IMS | ApnSetting.TYPE_MMS,
                new int[]{AccessNetworkConstants.AccessNetworkType.IWLAN});
        processAllMessages();

        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_IMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.getPreferredTransport(ApnSetting.TYPE_MMS))
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mAccessNetworksManager.isAnyApnOnIwlan()).isTrue();
    }
}
