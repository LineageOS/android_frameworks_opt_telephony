/*
 * Copyright 2021 The Android Open Source Project
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.DataFailCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.SparseArray;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetwork.DataNetworkCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkTest extends TelephonyTest {
    private static final String IPV4_ADDRESS = "10.0.2.15";
    private static final String IPV6_ADDRESS = "2607:fb90:a620:651d:eabe:f8da:c107:44be";

    private DataNetwork mDataNetworkUT;

    @Mock
    private DataServiceManager mWwanDataServiceManager;

    @Mock
    private DataServiceManager mWlanDataServiceManager;

    private SparseArray<DataServiceManager> mDataServiceManagers = new SparseArray<>();

    private final ApnSetting mInternetApnSetting = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_apn")
            .setApnName("fake_apn")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    private final ApnSetting mImsApnSetting = new ApnSetting.Builder()
            .setId(2163)
            .setOperatorNumeric("12345")
            .setEntryName("fake_ims")
            .setApnName("fake_ims")
            .setUser("user")
            .setPassword("passwd")
            .setApnTypeBitmask(ApnSetting.TYPE_IMS)
            .setProtocol(ApnSetting.PROTOCOL_IPV6)
            .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
            .setCarrierEnabled(true)
            .setNetworkTypeBitmask(0)
            .setProfileId(1234)
            .setMaxConns(321)
            .setWaitTime(456)
            .setMaxConnsTime(789)
            .build();

    private final DataProfile mInternetDataProfile = new DataProfile.Builder()
            .setApnSetting(mInternetApnSetting)
            .setTrafficDescriptor(new TrafficDescriptor("fake_apn", null))
            .build();

    private final DataProfile mImsDataProfile = new DataProfile.Builder()
            .setApnSetting(mImsApnSetting)
            .setTrafficDescriptor(new TrafficDescriptor("fake_apn", null))
            .build();

    @Mock
    private DataNetworkCallback mDataNetworkCallback;

    private final NetworkRegistrationInfo mIwlanNetworkRegistrationInfo =
            new NetworkRegistrationInfo.Builder()
                    .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                    .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                    .build();

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = new DataCallResponse.Builder()
                    .setCause(0)
                    .setRetryDurationMillis(-1L)
                    .setId(cid)
                    .setLinkStatus(2)
                    .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                    .setInterfaceName("ifname")
                    .setAddresses(Arrays.asList(
                            new LinkAddress(InetAddresses.parseNumericAddress(IPV4_ADDRESS), 32),
                            new LinkAddress(IPV6_ADDRESS + "/64")))
                    .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                            InetAddresses.parseNumericAddress("fd00:976a::9")))
                    .setGatewayAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("10.0.2.15"),
                            InetAddresses.parseNumericAddress("fe80::2")))
                    .setPcscfAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                            InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                    .setMtu(1500)
                    .setMtuV4(1500)
                    .setMtuV6(1500)
                    .setPduSessionId(1)
                    .setQosBearerSessions(new ArrayList<>())
                    .setTrafficDescriptors(new ArrayList<>())
                    .build();
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();
            return null;
        }).when(dsm).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    private void setFailedSetupDataResponse(DataServiceManager dsm,
            @DataServiceCallback.ResultCode int resultCode) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];
            msg.arg1 = resultCode;
            msg.sendToTarget();
            return null;
        }).when(dsm).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mWwanDataServiceManager);
        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mWlanDataServiceManager);
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void sendTearDownEvent(@TransportType int transport, int cid,
            @DataFailureCause int cause) {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(cause)
                .setRetryDurationMillis(DataCallResponse.RETRY_DURATION_UNDEFINED)
                .setId(cid)
                .setLinkStatus(DataCallResponse.LINK_STATUS_INACTIVE)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(new ArrayList<>())
                .build();
        mDataNetworkUT.sendMessage(7/*EVENT_TEAR_DOWN_NETWORK*/,
                1/*TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED*/);
        mDataNetworkUT.sendMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(transport, new ArrayList<>(Arrays.asList(response)), null));
        processAllMessages();
    }

    // The purpose of this test is to make sure the network request insertion/removal works as
    // expected, and make sure it is always sorted.
    @Test
    public void testCreateDataNetwork() {
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList, mDataNetworkCallback);
        mDataNetworkUT.sendMessage(9/*EVENT_SERVICE_STATE_CHANGED*/);

        processAllMessages();
        verify(mSimulatedCommandsVerifier, never()).allocatePduSessionId(any(Message.class));
        verify(mWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mInternetDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(true), any(Message.class));
        assertThat(mDataNetworkUT.getId()).isEqualTo(123);
        assertThat(networkRequestList.get(0).getState())
                .isEqualTo(TelephonyNetworkRequest.REQUEST_STATE_SATISFIED);
        LinkProperties lp = mDataNetworkUT.getLinkProperties();
        List<InetAddress> addresses = lp.getAddresses();
        assertThat(lp.getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(2)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList.get(0).getApnSetting()).isEqualTo(mInternetApnSetting);
        assertThat(pdcsList.get(0).getState()).isEqualTo(TelephonyManager.DATA_CONNECTING);
        assertThat(pdcsList.get(0).getId()).isEqualTo(-1);
        assertThat(pdcsList.get(0).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(0).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(pdcsList.get(0).getLinkProperties()).isEqualTo(new LinkProperties());

        assertThat(pdcsList.get(1).getApnSetting()).isEqualTo(mInternetApnSetting);
        assertThat(pdcsList.get(1).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(1).getId()).isEqualTo(123);
        assertThat(pdcsList.get(1).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(1).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(pdcsList.get(1).getLinkProperties().getAddresses().get(0))
                .isEqualTo(InetAddresses.parseNumericAddress(IPV4_ADDRESS));
        assertThat(pdcsList.get(1).getLinkProperties().getAddresses().get(1))
                .isEqualTo(InetAddresses.parseNumericAddress(IPV6_ADDRESS));
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_SUPL)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isTrue();

        verify(mDataNetworkCallback).onConnected(eq(mDataNetworkUT));
    }

    // The purpose of this test is to make sure data could be torn down properly.
    @Test
    public void testTearDown() {
        testCreateDataNetwork();
        sendTearDownEvent(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 123,
                DataFailCause.EMM_DETACHED);

        verify(mSimulatedCommandsVerifier, never()).releasePduSessionId(nullable(Message.class),
                anyInt());
        verify(mWwanDataServiceManager).deactivateDataCall(eq(123),
                eq(DataService.REQUEST_REASON_NORMAL), eq(null));
        verify(mDataNetworkCallback).onDisconnected(eq(mDataNetworkUT), eq(
                DataFailCause.EMM_DETACHED));

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(4)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList.get(2).getApnSetting()).isEqualTo(mInternetApnSetting);
        assertThat(pdcsList.get(2).getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTING);
        assertThat(pdcsList.get(2).getId()).isEqualTo(123);
        assertThat(pdcsList.get(2).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(2).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        assertThat(pdcsList.get(3).getApnSetting()).isEqualTo(mInternetApnSetting);
        assertThat(pdcsList.get(3).getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTED);
        assertThat(pdcsList.get(3).getId()).isEqualTo(123);
        assertThat(pdcsList.get(3).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(3).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testCreateDataNetworkOnIwlan() {
        doReturn(mIwlanNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mWlanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mImsDataProfile, networkRequestList, mDataNetworkCallback);
        mDataNetworkUT.sendMessage(9/*EVENT_SERVICE_STATE_CHANGED*/);

        processAllMessages();
        verify(mSimulatedCommandsVerifier).allocatePduSessionId(any(Message.class));
        verify(mWlanDataServiceManager).setupDataCall(eq(AccessNetworkType.IWLAN),
                eq(mImsDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(1), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(true), any(Message.class));
        assertThat(mDataNetworkUT.getId()).isEqualTo(123);
        assertThat(networkRequestList.get(0).getState())
                .isEqualTo(TelephonyNetworkRequest.REQUEST_STATE_SATISFIED);
        LinkProperties lp = mDataNetworkUT.getLinkProperties();
        assertThat(lp.getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(2)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList.get(0).getApnSetting()).isEqualTo(mImsApnSetting);
        assertThat(pdcsList.get(0).getState()).isEqualTo(TelephonyManager.DATA_CONNECTING);
        assertThat(pdcsList.get(0).getId()).isEqualTo(-1);
        assertThat(pdcsList.get(0).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_IWLAN);
        assertThat(pdcsList.get(0).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(pdcsList.get(0).getLinkProperties()).isEqualTo(new LinkProperties());

        assertThat(pdcsList.get(1).getApnSetting()).isEqualTo(mImsApnSetting);
        assertThat(pdcsList.get(1).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(1).getId()).isEqualTo(123);
        assertThat(pdcsList.get(1).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_IWLAN);
        assertThat(pdcsList.get(1).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(pdcsList.get(1).getLinkProperties().getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isFalse();

        verify(mDataNetworkCallback).onConnected(eq(mDataNetworkUT));
    }

    @Test
    public void testTearDownIwlan() {
        testCreateDataNetworkOnIwlan();
        sendTearDownEvent(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, 123,
                DataFailCause.EMM_DETACHED);

        verify(mSimulatedCommandsVerifier).releasePduSessionId(nullable(Message.class), eq(1));
        verify(mWlanDataServiceManager).deactivateDataCall(eq(123),
                eq(DataService.REQUEST_REASON_NORMAL), eq(null));
        verify(mDataNetworkCallback).onDisconnected(eq(mDataNetworkUT), eq(
                DataFailCause.EMM_DETACHED));

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(4)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList.get(2).getApnSetting()).isEqualTo(mImsApnSetting);
        assertThat(pdcsList.get(2).getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTING);
        assertThat(pdcsList.get(2).getId()).isEqualTo(123);
        assertThat(pdcsList.get(2).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_IWLAN);
        assertThat(pdcsList.get(2).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        assertThat(pdcsList.get(3).getApnSetting()).isEqualTo(mImsApnSetting);
        assertThat(pdcsList.get(3).getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTED);
        assertThat(pdcsList.get(3).getId()).isEqualTo(123);
        assertThat(pdcsList.get(3).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_IWLAN);
        assertThat(pdcsList.get(3).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }
}
