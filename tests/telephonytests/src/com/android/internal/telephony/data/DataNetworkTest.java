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
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.vcn.VcnManager.VcnNetworkPolicyChangeListener;
import android.net.vcn.VcnNetworkPolicyResult;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.DataFailCause;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
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
import android.util.Pair;
import android.util.SparseArray;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataConfigManager.DataConfigManagerCallback;
import com.android.internal.telephony.data.DataEvaluation.DataAllowedReason;
import com.android.internal.telephony.data.DataNetwork.DataNetworkCallback;
import com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;
import com.android.internal.telephony.data.LinkBandwidthEstimator.LinkBandwidthEstimatorCallback;
import com.android.internal.telephony.metrics.DataCallSessionStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkTest extends TelephonyTest {
    private static final String IPV4_ADDRESS = "10.0.2.15";
    private static final String IPV4_ADDRESS1 = "10.0.2.16";
    private static final String IPV6_ADDRESS = "2607:fb90:a620:651d:eabe:f8da:c107:44be";
    private static final String IPV6_ADDRESS1 = "2607:fb90:a620:651d:eabe:f8da:c107:44bf";

    private static final int ADMIN_UID1 = 1234;
    private static final int ADMIN_UID2 = 5678;

    private static final int DEFAULT_MTU = 1501;

    private static final String FAKE_IMSI = "123456789";

    private DataNetwork mDataNetworkUT;

    private final SparseArray<DataServiceManager> mDataServiceManagers = new SparseArray<>();

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

    private final DataProfile mEnterpriseDataProfile = new DataProfile.Builder()
            .setTrafficDescriptor(new TrafficDescriptor(null,
                    new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                            "ENTERPRISE", 1).getBytes()))
            .build();

    private final DataProfile mUrlccDataProfile = new DataProfile.Builder()
            .setTrafficDescriptor(new TrafficDescriptor(null,
                    new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                            "PRIORITIZE_LATENCY", 1).getBytes()))
            .build();

    private final DataProfile mEmbbDataProfile = new DataProfile.Builder()
            .setTrafficDescriptor(new TrafficDescriptor(null,
                    new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                            "PRIORITIZE_BANDWIDTH", 1).getBytes()))
            .build();

    private final DataProfile mCbsDataProfile = new DataProfile.Builder()
            .setTrafficDescriptor(new TrafficDescriptor(null,
                    new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                            "CBS", 1).getBytes()))
            .build();

    // Mocked classes
    private DataNetworkCallback mDataNetworkCallback;
    private DataCallSessionStats mDataCallSessionStats;
    private PhoneSwitcher mMockedPhoneSwitcher;

    private final NetworkRegistrationInfo mIwlanNetworkRegistrationInfo =
            new NetworkRegistrationInfo.Builder()
                    .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                    .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                    .build();

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid) {
        setSuccessfulSetupDataResponse(dsm, cid, Collections.emptyList());
    }

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid,
            List<TrafficDescriptor> tds) {
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
                    .setMtuV4(1234)
                    .setPduSessionId(1)
                    .setQosBearerSessions(new ArrayList<>())
                    .setTrafficDescriptors(tds)
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

    private void sendServiceStateChangedEvent(@ServiceState.RegState int dataRegState,
            @ServiceState.RilRadioTechnology int rat) {
        mDataNetworkUT.obtainMessage(9/*EVENT_SERVICE_STATE_CHANGED*/,
                new AsyncResult(null, new Pair<>(dataRegState, rat), null)).sendToTarget();
    }

    private void serviceStateChanged(@Annotation.NetworkType int networkType,
            @NetworkRegistrationInfo.RegistrationState int regState) {
        serviceStateChanged(networkType, regState, null);
    }

    private void serviceStateChanged(@Annotation.NetworkType int networkType,
            @NetworkRegistrationInfo.RegistrationState int regState,
            DataSpecificRegistrationInfo dsri) {
        ServiceState ss = new ServiceState();
        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(regState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setDataSpecificInfo(dsri)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(regState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        ss.setDataRoamingFromRegistration(regState
                == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        if (mDataNetworkUT != null) {
            mDataNetworkUT.obtainMessage(9/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
            processAllMessages();
        }
    }


    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(mImsCT).when(mImsPhone).getCallTracker();
        doReturn(PhoneConstants.State.IDLE).when(mImsCT).getState();

        mDataNetworkCallback = Mockito.mock(DataNetworkCallback.class);
        mDataCallSessionStats = Mockito.mock(DataCallSessionStats.class);
        mMockedPhoneSwitcher = Mockito.mock(PhoneSwitcher.class);
        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mMockedPhoneSwitcher);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataNetworkCallback).invokeFromExecutor(any(Runnable.class));
        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mMockedWwanDataServiceManager);
        mDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mMockedWlanDataServiceManager);

        for (int transport : new int[]{AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN}) {
            doAnswer(invocation -> {
                Message msg = (Message) invocation.getArguments()[1];
                msg.sendToTarget();
                return null;
            }).when(mDataServiceManagers.get(transport)).startHandover(anyInt(),
                    any(Message.class));

            doAnswer(invocation -> {
                Message msg = (Message) invocation.getArguments()[1];
                msg.sendToTarget();
                return null;
            }).when(mDataServiceManagers.get(transport)).cancelHandover(anyInt(),
                    any(Message.class));
        }

        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
        doReturn(DataNetwork.BANDWIDTH_SOURCE_BANDWIDTH_ESTIMATOR)
                .when(mDataConfigManager).getBandwidthEstimateSource();
        doReturn(true).when(mDataConfigManager).isTempNotMeteredSupportedByCarrier();
        doReturn(true).when(mDataConfigManager).isImsDelayTearDownEnabled();
        doReturn(DEFAULT_MTU).when(mDataConfigManager).getDefaultMtu();
        doReturn(FAKE_IMSI).when(mPhone).getSubscriberId();
        doReturn(true).when(mDataNetworkController)
                .isNetworkRequestExisting(any(TelephonyNetworkRequest.class));

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
    }

    @After
    public void tearDown() throws Exception {
        mDataNetworkUT = null;
        mDataServiceManagers.clear();
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
    public void testCreateDataNetwork() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        ArgumentCaptor<LinkProperties> linkPropertiesCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mConnectivityManager).registerNetworkAgent(any(), any(NetworkInfo.class),
                linkPropertiesCaptor.capture(), networkCapabilitiesCaptor.capture(), any(), any(),
                anyInt());
        // The very first link properties from telephony is an empty link properties. It will be
        // updated later.
        assertThat(linkPropertiesCaptor.getValue()).isEqualTo(new LinkProperties());

        verify(mSimulatedCommandsVerifier, never()).allocatePduSessionId(any(Message.class));
        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mInternetDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(true), any(Message.class));
        assertThat(mDataNetworkUT.getId()).isEqualTo(123);
        assertThat(networkRequestList.get(0).getState())
                .isEqualTo(TelephonyNetworkRequest.REQUEST_STATE_SATISFIED);
        LinkProperties lp = mDataNetworkUT.getLinkProperties();
        assertThat(lp.getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        assertThat(lp.getRoutes()).hasSize(2);
        assertThat(lp.getRoutes().get(0).getGateway()).isEqualTo(
                InetAddresses.parseNumericAddress("10.0.2.15"));
        assertThat(lp.getRoutes().get(0).getMtu()).isEqualTo(1234);
        assertThat(lp.getRoutes().get(1).getGateway()).isEqualTo(
                InetAddresses.parseNumericAddress("fe80::2"));
        // The default from carrier configs should be used if MTU is not set.
        assertThat(lp.getRoutes().get(1).getMtu()).isEqualTo(DEFAULT_MTU);
        // The higher value of v4 and v6 should be used.
        assertThat(lp.getMtu()).isEqualTo(DEFAULT_MTU);

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
        assertThat(mDataNetworkUT.getNetworkCapabilities()).isEqualTo(
                networkCapabilitiesCaptor.getValue());
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_SUPL)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isTrue();
        verify(mVcnManager, atLeastOnce()).applyVcnNetworkPolicy(
                argThat(caps -> caps.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)),
                any());

        verify(mDataNetworkCallback).onConnected(eq(mDataNetworkUT));
    }

    @Test
    public void testCreateDataNetworkWhenOos() throws Exception {
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        // Out of service
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING, dsri);

        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mConnectivityManager).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), networkCapabilitiesCaptor.capture(), any(), any(),
                anyInt());

        // Make sure the initial network capability has NOT_SUSPENDED
        assertThat(networkCapabilitiesCaptor.getValue().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isTrue();

        // The final network should not have NOT_SUSPENDED because the device is OOS.
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isFalse();
    }

    @Test
    public void testRecreateAgentWhenOos() throws Exception {
        testCreateDataNetwork();

        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        // Out of service
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING, dsri);

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(IPV4_ADDRESS1), 32),
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
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        // Agent re-created, so register should be called twice.
        verify(mConnectivityManager, times(2)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), networkCapabilitiesCaptor.capture(), any(), any(),
                anyInt());

        // Make sure the 2nd network agent was created with NOT_SUSPENDED.
        assertThat(networkCapabilitiesCaptor.getValue().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isTrue();

        // The final network should not have NOT_SUSPENDED because the device is OOS.
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isFalse();
    }

    @Test
    public void testRilCrash() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));
        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        processAllMessages();

        // verify connected
        verify(mDataNetworkCallback).onConnected(eq(mDataNetworkUT));

        // RIL crash
        mDataNetworkUT.sendMessage(4/*EVENT_RADIO_NOT_AVAILABLE*/);
        processAllMessages();

        verify(mDataNetworkCallback).onDisconnected(eq(mDataNetworkUT),
                eq(DataFailCause.RADIO_NOT_AVAILABLE));
    }

    @Test
    public void testCreateImsDataNetwork() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mImsDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        ArgumentCaptor<LinkProperties> linkPropertiesCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);
        ArgumentCaptor<NetworkCapabilities> networkCapabilitiesCaptor =
                ArgumentCaptor.forClass(NetworkCapabilities.class);

        verify(mConnectivityManager).registerNetworkAgent(any(), any(NetworkInfo.class),
                linkPropertiesCaptor.capture(), networkCapabilitiesCaptor.capture(), any(), any(),
                anyInt());
        // The very first link properties from telephony is an empty link properties. It will be
        // updated later.
        assertThat(linkPropertiesCaptor.getValue()).isEqualTo(new LinkProperties());

        verify(mSimulatedCommandsVerifier, never()).allocatePduSessionId(any(Message.class));
        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mImsDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
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
        assertThat(pdcsList.get(0).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(0).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(pdcsList.get(0).getLinkProperties()).isEqualTo(new LinkProperties());

        assertThat(pdcsList.get(1).getApnSetting()).isEqualTo(mImsApnSetting);
        assertThat(pdcsList.get(1).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(1).getId()).isEqualTo(123);
        assertThat(pdcsList.get(1).getNetworkType()).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(pdcsList.get(1).getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(pdcsList.get(1).getLinkProperties().getAddresses().get(0))
                .isEqualTo(InetAddresses.parseNumericAddress(IPV4_ADDRESS));
        assertThat(pdcsList.get(1).getLinkProperties().getAddresses().get(1))
                .isEqualTo(InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        assertThat(mDataNetworkUT.getNetworkCapabilities()).isEqualTo(
                networkCapabilitiesCaptor.getValue());
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isFalse();

        verify(mDataNetworkCallback).onConnected(eq(mDataNetworkUT));
    }

    @Test
    public void testCreateDataNetworkOnEnterpriseSlice() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                .build(), mPhone));

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1).getBytes()),
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 5).getBytes())
        );
        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123, tds);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mEnterpriseDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, DataAllowedReason.NORMAL,
                mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mEnterpriseDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(false), any(Message.class));

        NetworkCapabilities nc = mDataNetworkUT.getNetworkCapabilities();
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)).isTrue();
        assertThat(nc.getEnterpriseIds()).asList().containsExactly(1, 5);
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
    }

    @Test
    public void testCreateDataNetworkOnUrllcSlice() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY)
                .build(), mPhone));

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "PRIORITIZE_LATENCY", 1)
                        .getBytes())
        );
        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123, tds);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mUrlccDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, DataAllowedReason.NORMAL,
                mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mUrlccDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(false), any(Message.class));

        NetworkCapabilities nc = mDataNetworkUT.getNetworkCapabilities();
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY))
                .isTrue();
    }

    @Test
    public void testCreateDataNetworkOnEmbbSlice() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                .build(), mPhone));

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "PRIORITIZE_BANDWIDTH", 1)
                        .getBytes())
        );
        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123, tds);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mEmbbDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, DataAllowedReason.NORMAL,
                mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mEmbbDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(false), any(Message.class));

        NetworkCapabilities nc = mDataNetworkUT.getNetworkCapabilities();
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH))
                .isTrue();
    }

    @Test
    public void testCreateDataNetworkOnCbsSlice() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CBS)
                .build(), mPhone));

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "CBS", 1)
                        .getBytes())
        );

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123, tds);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mCbsDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, DataAllowedReason.NORMAL,
                mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).setupDataCall(eq(AccessNetworkType.EUTRAN),
                eq(mCbsDataProfile), eq(false), eq(false),
                eq(DataService.REQUEST_REASON_NORMAL), nullable(LinkProperties.class),
                eq(DataCallResponse.PDU_SESSION_ID_NOT_SET), nullable(NetworkSliceInfo.class),
                any(TrafficDescriptor.class), eq(false), any(Message.class));

        NetworkCapabilities nc = mDataNetworkUT.getNetworkCapabilities();
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS))
                .isTrue();
    }

    @Test
    public void testSlicingDataNetworkHasSlicingCapabilitiesBeforeConnected() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_CBS)
                .build(), mPhone));

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "CBS", 1)
                        .getBytes())
        );

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mCbsDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, DataAllowedReason.NORMAL,
                mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        // Didn't call setSuccessfulSetupDataResponse, so data network should stuck in connecting.

        // Verify the network has the right capability at beginning.
        NetworkCapabilities nc = mDataNetworkUT.getNetworkCapabilities();
        assertThat(nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS))
                .isTrue();

        // Verify the network was not detached due to not satisfied.
        assertThat(networkRequestList).isEqualTo(mDataNetworkUT.getAttachedNetworkRequestList());
    }

    // The purpose of this test is to make sure data could be torn down properly.
    @Test
    public void testTearDown() throws Exception {
        setupDataNetwork();
        sendTearDownEvent(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, 123,
                DataFailCause.EMM_DETACHED);

        verify(mSimulatedCommandsVerifier, never()).releasePduSessionId(nullable(Message.class),
                anyInt());
        verify(mMockedWwanDataServiceManager).deactivateDataCall(eq(123),
                eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
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
    public void testAirplaneModeShutdownDeactivateData() throws Exception {
        setupDataNetwork();

        mDataNetworkUT.tearDown(DataNetwork.TEAR_DOWN_REASON_AIRPLANE_MODE_ON);
        processAllMessages();

        // Make sure REQUEST_REASON_SHUTDOWN is sent when tear down reason is APM.
        verify(mMockedWwanDataServiceManager).deactivateDataCall(eq(123),
                eq(DataService.REQUEST_REASON_SHUTDOWN), any(Message.class));
    }


    @Test
    public void testCreateDataNetworkOnIwlan() throws Exception {
        doReturn(mIwlanNetworkRegistrationInfo).when(mServiceState).getNetworkRegistrationInfo(
                eq(NetworkRegistrationInfo.DOMAIN_PS),
                eq(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mImsDataProfile, networkRequestList, AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        processAllMessages();
        verify(mSimulatedCommandsVerifier).allocatePduSessionId(any(Message.class));
        verify(mMockedWlanDataServiceManager).setupDataCall(eq(AccessNetworkType.IWLAN),
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
    public void testTearDownIwlan() throws Exception {
        testCreateDataNetworkOnIwlan();
        sendTearDownEvent(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, 123,
                DataFailCause.EMM_DETACHED);

        verify(mSimulatedCommandsVerifier).releasePduSessionId(nullable(Message.class), eq(1));
        verify(mMockedWlanDataServiceManager).deactivateDataCall(eq(123),
                eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));
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

    @Test
    public void testHandover() throws Exception {
        setupDataNetwork();

        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 456);
        // Now handover to IWLAN
        mDataNetworkUT.startHandover(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, null);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).startHandover(eq(123), any(Message.class));
        verify(mLinkBandwidthEstimator).unregisterCallback(any(
                LinkBandwidthEstimatorCallback.class));
        assertThat(mDataNetworkUT.getTransport())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(mDataNetworkUT.getId()).isEqualTo(456);
        verify(mDataNetworkCallback).onHandoverSucceeded(eq(mDataNetworkUT));

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(4)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList).hasSize(4);
        assertThat(pdcsList.get(0).getState()).isEqualTo(TelephonyManager.DATA_CONNECTING);
        assertThat(pdcsList.get(1).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(2).getState())
                .isEqualTo(TelephonyManager.DATA_HANDOVER_IN_PROGRESS);
        assertThat(pdcsList.get(3).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);

        // Now handover back to cellular
        Mockito.clearInvocations(mDataNetworkCallback);
        Mockito.clearInvocations(mLinkBandwidthEstimator);
        mDataNetworkUT.startHandover(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, null);
        processAllFutureMessages();

        verify(mMockedWlanDataServiceManager).startHandover(eq(456), any(Message.class));
        verify(mLinkBandwidthEstimator).registerCallback(
                any(LinkBandwidthEstimatorCallback.class));
        assertThat(mDataNetworkUT.getTransport())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mDataNetworkUT.getId()).isEqualTo(123);
        verify(mDataNetworkCallback).onHandoverSucceeded(eq(mDataNetworkUT));
    }

    @Test
    public void testHandoverFailed() throws Exception {
        setupDataNetwork();

        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE);
        // Now attempt to handover to IWLAN but fail it.
        mDataNetworkUT.startHandover(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, null);
        processAllMessages();

        verify(mMockedWwanDataServiceManager).startHandover(eq(123), any(Message.class));
        verify(mMockedWwanDataServiceManager).cancelHandover(eq(123), any(Message.class));
        verify(mDataNetworkCallback).onHandoverFailed(eq(mDataNetworkUT),
                eq(DataFailCause.SERVICE_TEMPORARILY_UNAVAILABLE), eq(-1L),
                eq(DataCallResponse.HANDOVER_FAILURE_MODE_UNKNOWN));
        verify(mLinkBandwidthEstimator, never()).unregisterForBandwidthChanged(
                eq(mDataNetworkUT.getHandler()));
        assertThat(mDataNetworkUT.getTransport())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(mDataNetworkUT.getId()).isEqualTo(123);

        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone, times(4)).notifyDataConnection(pdcsCaptor.capture());
        List<PreciseDataConnectionState> pdcsList = pdcsCaptor.getAllValues();

        assertThat(pdcsList).hasSize(4);
        assertThat(pdcsList.get(0).getState()).isEqualTo(TelephonyManager.DATA_CONNECTING);
        assertThat(pdcsList.get(1).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(2).getState())
                .isEqualTo(TelephonyManager.DATA_HANDOVER_IN_PROGRESS);
        assertThat(pdcsList.get(3).getState()).isEqualTo(TelephonyManager.DATA_CONNECTED);
        assertThat(pdcsList.get(3).getLastCauseCode())
                .isEqualTo(DataFailCause.SERVICE_TEMPORARILY_UNAVAILABLE);

        // Test source PDN lost during the HO, expect tear down after HO
        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE);
        mDataNetworkUT.startHandover(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, null);
        mDataNetworkUT.sendMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        Collections.emptyList(), null)); // the source transport report PDN lost
        processAllMessages();

        assertThat(mDataNetworkUT.isConnected()).isFalse();
    }

    @Test
    public void testAdminAndOwnerUids() throws Exception {
        doReturn(ADMIN_UID2).when(mCarrierPrivilegesTracker).getCarrierServicePackageUid();
        setupDataNetwork();

        assertThat(mDataNetworkUT.getNetworkCapabilities().getAdministratorUids()).asList()
                .containsExactly(ADMIN_UID1, ADMIN_UID2);
        assertThat(mDataNetworkUT.getNetworkCapabilities().getOwnerUid()).isEqualTo(ADMIN_UID2);
    }

    private void setupDataNetwork() throws Exception {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 123);

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        mDataNetworkUT.sendMessage(18/*EVENT_CARRIER_PRIVILEGED_UIDS_CHANGED*/,
                new AsyncResult(null, new int[]{ADMIN_UID1, ADMIN_UID2}, null));
        processAllMessages();
    }

    // Make sure data network register all the required events and unregister all of them at the
    // end.
    @Test
    public void testRegisterUnregisterEvents() throws Exception {
        // Setup a new data network and tear down.
        testTearDown();

        // Verify register all events.
        verify(mDataConfigManager).registerCallback(any(DataConfigManagerCallback.class));
        verify(mDisplayInfoController).registerForTelephonyDisplayInfoChanged(
                any(Handler.class), anyInt(), eq(null));
        verify(mMockedWwanDataServiceManager).registerForDataCallListChanged(
                any(Handler.class), anyInt());
        verify(mMockedWlanDataServiceManager).registerForDataCallListChanged(
                any(Handler.class), anyInt());
        verify(mCarrierPrivilegesTracker).registerCarrierPrivilegesListener(any(Handler.class),
                anyInt(), eq(null));
        verify(mLinkBandwidthEstimator).registerCallback(
                any(LinkBandwidthEstimatorCallback.class));
        verify(mSimulatedCommandsVerifier).registerForNattKeepaliveStatus(any(Handler.class),
                anyInt(), eq(null));
        verify(mSimulatedCommandsVerifier).registerForPcoData(any(Handler.class), anyInt(),
                eq(null));
        verify(mSimulatedCommandsVerifier).registerForNotAvailable(any(Handler.class), anyInt(),
                eq(null));
        verify(mVcnManager).addVcnNetworkPolicyChangeListener(any(Executor.class),
                any(VcnNetworkPolicyChangeListener.class));
        verify(mSST).registerForCssIndicatorChanged(any(Handler.class), anyInt(), eq(null));
        verify(mSST).registerForServiceStateChanged(any(Handler.class), anyInt());
        verify(mCT).registerForVoiceCallStarted(any(Handler.class), anyInt(), eq(null));
        verify(mCT).registerForVoiceCallEnded(any(Handler.class), anyInt(), eq(null));
        verify(mImsCT).registerForVoiceCallStarted(any(Handler.class), anyInt(), eq(null));
        verify(mImsCT).registerForVoiceCallEnded(any(Handler.class), anyInt(), eq(null));

        // Verify unregister all events.
        verify(mDataConfigManager).unregisterCallback(any(DataConfigManagerCallback.class));
        verify(mDisplayInfoController).unregisterForTelephonyDisplayInfoChanged(any(Handler.class));
        verify(mMockedWwanDataServiceManager).unregisterForDataCallListChanged(any(Handler.class));
        verify(mMockedWlanDataServiceManager).unregisterForDataCallListChanged(any(Handler.class));
        verify(mCarrierPrivilegesTracker).unregisterCarrierPrivilegesListener(any(Handler.class));
        verify(mLinkBandwidthEstimator).unregisterCallback(
                any(LinkBandwidthEstimatorCallback.class));
        verify(mSimulatedCommandsVerifier).unregisterForNattKeepaliveStatus(any(Handler.class));
        verify(mSimulatedCommandsVerifier).unregisterForPcoData(any(Handler.class));
        verify(mSimulatedCommandsVerifier).unregisterForNotAvailable(any(Handler.class));
        verify(mVcnManager).removeVcnNetworkPolicyChangeListener(
                any(VcnNetworkPolicyChangeListener.class));
        verify(mSST).unregisterForCssIndicatorChanged(any(Handler.class));
        verify(mSST).unregisterForServiceStateChanged(any(Handler.class));
        verify(mImsCT).unregisterForVoiceCallStarted(any(Handler.class));
        verify(mImsCT).unregisterForVoiceCallEnded(any(Handler.class));
        verify(mCT).unregisterForVoiceCallStarted(any(Handler.class));
        verify(mCT).unregisterForVoiceCallEnded(any(Handler.class));
    }

    @Test
    public void testVcnPolicy() throws Exception {
        doAnswer(invocation -> {
            NetworkCapabilities nc = invocation.getArgument(0);
            NetworkCapabilities policyNc = new NetworkCapabilities.Builder(nc)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .build();

            return new VcnNetworkPolicyResult(
                    false /* isTearDownRequested */, policyNc);
        }).when(mVcnManager).applyVcnNetworkPolicy(any(), any());

        setupDataNetwork();

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isFalse();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isFalse();
    }

    @Test
    public void testVcnPolicyUpdated() throws Exception {
        setupDataNetwork();

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isTrue();
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isTrue();

        doAnswer(invocation -> {
            NetworkCapabilities nc = invocation.getArgument(0);
            NetworkCapabilities policyNc = new NetworkCapabilities.Builder(nc)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                    .build();

            return new VcnNetworkPolicyResult(
                    false /* isTearDownRequested */, policyNc);
        }).when(mVcnManager).applyVcnNetworkPolicy(any(), any());
        triggerVcnNetworkPolicyChanged();

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isFalse();
    }

    @Test
    public void testVcnPolicyTeardownRequested() throws Exception {
        setupDataNetwork();

        doAnswer(invocation -> {
            NetworkCapabilities nc = invocation.getArgument(0);

            return new VcnNetworkPolicyResult(
                    true /* isTearDownRequested */, nc);
        }).when(mVcnManager).applyVcnNetworkPolicy(any(), any());
        triggerVcnNetworkPolicyChanged();

        assertThat(mDataNetworkUT.isConnected()).isFalse();
    }

    private void triggerVcnNetworkPolicyChanged() {
        ArgumentCaptor<VcnNetworkPolicyChangeListener> captor =
                ArgumentCaptor.forClass(VcnNetworkPolicyChangeListener.class);

        verify(mVcnManager).addVcnNetworkPolicyChangeListener(any(), captor.capture());
        captor.getValue().onPolicyChanged();
        processAllMessages();
    }

    @Test
    public void testDeactivateDataCallRadioNotAvailable() throws Exception {
        setupDataNetwork();

        assertThat(mDataNetworkUT.isConnected()).isTrue();
        mDataNetworkUT.sendMessage(19/*EVENT_DEACTIVATE_DATA_NETWORK_RESPONSE*/,
                4/*RESULT_ERROR_ILLEGAL_STATE*/);
        processAllMessages();

        verify(mDataNetworkCallback).onDisconnected(eq(mDataNetworkUT), eq(
                DataFailCause.RADIO_NOT_AVAILABLE));
        assertThat(mDataNetworkUT.isConnected()).isFalse();
    }

    @Test
    public void testNetworkAgentConfig() throws Exception {
        testCreateImsDataNetwork();

        ArgumentCaptor<NetworkAgentConfig> captor = ArgumentCaptor
                .forClass(NetworkAgentConfig.class);

        verify(mConnectivityManager).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), captor.capture(),
                anyInt());

        NetworkAgentConfig networkAgentConfig = captor.getValue();

        assertThat(networkAgentConfig.getSubscriberId()).isEqualTo(FAKE_IMSI);
        assertThat(networkAgentConfig.getLegacyExtraInfo()).isEqualTo(
                mImsApnSetting.getApnName());
        assertThat(networkAgentConfig.getLegacyType()).isEqualTo(ConnectivityManager.TYPE_MOBILE);
        assertThat(networkAgentConfig.getLegacyTypeName()).isEqualTo("MOBILE");
        assertThat(networkAgentConfig.legacySubType).isEqualTo(TelephonyManager.NETWORK_TYPE_LTE);
        assertThat(networkAgentConfig.skip464xlat).isTrue();
    }

    @Test
    public void testDataNetworkHasCapabilitiesAtBeginning() {
        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));
        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        NetworkCapabilities caps = mDataNetworkUT.getNetworkCapabilities();
        assertThat(caps).isNotNull();
        assertThat(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)).isTrue();
    }

    @Test
    public void testBandwidthUpdate() throws Exception {
        setupDataNetwork();

        ArgumentCaptor<LinkBandwidthEstimatorCallback> linkBandwidthCallbackCaptor =
                ArgumentCaptor.forClass(LinkBandwidthEstimatorCallback.class);
        verify(mLinkBandwidthEstimator).registerCallback(linkBandwidthCallbackCaptor.capture());
        LinkBandwidthEstimatorCallback linkBandwidthEstimatorCallback =
                linkBandwidthCallbackCaptor.getValue();

        linkBandwidthEstimatorCallback.onBandwidthChanged(12345, 67890);
        processAllMessages();

        assertThat(mDataNetworkUT.getNetworkCapabilities().getLinkUpstreamBandwidthKbps())
                .isEqualTo(12345);
        assertThat(mDataNetworkUT.getNetworkCapabilities().getLinkDownstreamBandwidthKbps())
                .isEqualTo(67890);

        linkBandwidthEstimatorCallback.onBandwidthChanged(123, 456);
        processAllMessages();

        assertThat(mDataNetworkUT.getNetworkCapabilities().getLinkUpstreamBandwidthKbps())
                .isEqualTo(123);
        assertThat(mDataNetworkUT.getNetworkCapabilities().getLinkDownstreamBandwidthKbps())
                .isEqualTo(456);
    }

    @Test
    public void testChangingImmutableCapabilities() throws Exception {
        setupDataNetwork();

        List<TrafficDescriptor> tds = List.of(
                new TrafficDescriptor(null, new TrafficDescriptor.OsAppId(
                        TrafficDescriptor.OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1).getBytes())
                );

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(DataFailCause.NONE)
                .setRetryDurationMillis(DataCallResponse.RETRY_DURATION_UNDEFINED)
                .setId(123)
                .setLinkStatus(DataCallResponse.LINK_STATUS_ACTIVE)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(tds)
                .build();
        // Sending the data call list changed event which has enterprise traffic descriptor added.
        mDataNetworkUT.sendMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        new ArrayList<>(Arrays.asList(response)), null));
        processAllMessages();

        // Agent re-created, so register should be called twice.
        verify(mConnectivityManager, times(2)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), any(),
                anyInt());

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)).isTrue();
    }

    @Test
    public void testChangingMutableCapabilities() throws Exception {
        setupDataNetwork();

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)).isFalse();

        doReturn(Set.of(TelephonyManager.NETWORK_TYPE_LTE)).when(mDataNetworkController)
                .getUnmeteredOverrideNetworkTypes();
        mDataNetworkUT.sendMessage(1/*EVENT_DATA_CONFIG_UPDATED*/);

        processAllMessages();

        // Agent not re-created, so register should be called once.
        verify(mConnectivityManager, times(1)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), any(),
                anyInt());

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)).isTrue();
    }

    @Test
    public void testMovingToNonVops() throws Exception {
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);
        testCreateImsDataNetwork();

        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_MMTEL)).isTrue();

        dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));
        logd("Trigger non VoPS");
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);
        // MMTEL should not be removed.
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_MMTEL)).isTrue();
    }

    @Test
    public void testCssIndicatorChanged() throws Exception {
        setupDataNetwork();

        mDataNetworkUT.sendMessage(24/*EVENT_CSS_INDICATOR_CHANGED*/);
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        processAllMessages();

        // Suspended
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isFalse();

        mDataNetworkUT.sendMessage(24/*EVENT_CSS_INDICATOR_CHANGED*/);
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        processAllMessages();

        // Not suspended
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isTrue();
    }

    @Test
    public void testNonConcurrentDataSuspended() throws Exception {
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        setupDataNetwork();

        // Voice call start.
        doReturn(PhoneConstants.State.OFFHOOK).when(mCT).getState();
        mDataNetworkUT.sendMessage(22/*EVENT_VOICE_CALL_STARTED*/);
        processAllMessages();

        // Suspended
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isFalse();

        // Voice call ended.
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        mDataNetworkUT.sendMessage(23/*EVENT_VOICE_CALL_ENDED*/);
        processAllMessages();

        // Not suspended
        assertThat(mDataNetworkUT.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)).isTrue();
    }

    @Test
    public void testGetApnTypeCapability() throws Exception {
        testCreateDataNetwork();
        assertThat(mDataNetworkUT.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        TelephonyNetworkRequest networkRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder().addCapability(
                        NetworkCapabilities.NET_CAPABILITY_SUPL).build(), mPhone);
        mDataNetworkUT.attachNetworkRequests(new NetworkRequestList(networkRequest));
        processAllMessages();

        assertThat(mDataNetworkUT.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_SUPL);

        mDataNetworkUT.detachNetworkRequest(networkRequest);
        processAllMessages();

        assertThat(mDataNetworkUT.getApnTypeNetworkCapability())
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testGetPriority() throws Exception {
        testCreateDataNetwork();

        // Internet priority is 20
        assertThat(mDataNetworkUT.getPriority()).isEqualTo(20);

        TelephonyNetworkRequest networkRequest = new TelephonyNetworkRequest(
                new NetworkRequest.Builder().addCapability(
                        NetworkCapabilities.NET_CAPABILITY_SUPL).build(), mPhone);
        mDataNetworkUT.attachNetworkRequests(new NetworkRequestList(networkRequest));
        processAllMessages();

        // SUPL priority is 80
        assertThat(mDataNetworkUT.getPriority()).isEqualTo(80);

        mDataNetworkUT.detachNetworkRequest(networkRequest);
        processAllMessages();

        // Internet priority is 20
        assertThat(mDataNetworkUT.getPriority()).isEqualTo(20);
    }

    @Test
    public void testIpChangedV4() throws Exception {
        testCreateDataNetwork();

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(
                        new LinkAddress(InetAddresses.parseNumericAddress(IPV4_ADDRESS1), 32),
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
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        ArgumentCaptor<LinkProperties> linkPropertiesCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);

        // Agent re-created, so register should be called twice.
        verify(mConnectivityManager, times(2)).registerNetworkAgent(any(), any(NetworkInfo.class),
                linkPropertiesCaptor.capture(), any(NetworkCapabilities.class), any(), any(),
                anyInt());
        // The new agent should have the new IP address.
        assertThat(linkPropertiesCaptor.getValue().getAllAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS1),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        assertThat(linkPropertiesCaptor.getValue()).isEqualTo(mDataNetworkUT.getLinkProperties());
    }

    @Test
    public void testIpChangedV6() throws Exception {
        testCreateDataNetwork();

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(new LinkAddress(IPV6_ADDRESS1 + "/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        ArgumentCaptor<LinkProperties> linkPropertiesCaptor =
                ArgumentCaptor.forClass(LinkProperties.class);

        // Agent re-created, so register should be called twice.
        verify(mConnectivityManager, times(2)).registerNetworkAgent(any(), any(NetworkInfo.class),
                linkPropertiesCaptor.capture(), any(NetworkCapabilities.class), any(), any(),
                anyInt());
        // The new agent should have the new IP address.
        assertThat(linkPropertiesCaptor.getValue().getAllAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV6_ADDRESS1));

        assertThat(linkPropertiesCaptor.getValue()).isEqualTo(mDataNetworkUT.getLinkProperties());
    }

    @Test
    public void testIpChangedFromV4ToV6() throws Exception {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = new DataCallResponse.Builder()
                    .setCause(0)
                    .setRetryDurationMillis(-1L)
                    .setId(123)
                    .setLinkStatus(2)
                    .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                    .setInterfaceName("ifname")
                    .setAddresses(Arrays.asList(
                            new LinkAddress(InetAddresses.parseNumericAddress(IPV4_ADDRESS), 32)))
                    .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                            InetAddresses.parseNumericAddress("fd00:976a::9")))
                    .setGatewayAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("10.0.2.15"),
                            InetAddresses.parseNumericAddress("fe80::2")))
                    .setPcscfAddresses(Arrays.asList(
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                            InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                            InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                    .setMtuV4(1234)
                    .setMtuV6(5678)
                    .setPduSessionId(1)
                    .setQosBearerSessions(new ArrayList<>())
                    .setTrafficDescriptors(Collections.emptyList())
                    .build();
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();
            return null;
        }).when(mMockedWwanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));

        NetworkRequestList networkRequestList = new NetworkRequestList();
        networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), mPhone));

        mDataNetworkUT = new DataNetwork(mPhone, Looper.myLooper(), mDataServiceManagers,
                mInternetDataProfile, networkRequestList,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                DataAllowedReason.NORMAL, mDataNetworkCallback);
        replaceInstance(DataNetwork.class, "mDataCallSessionStats",
                mDataNetworkUT, mDataCallSessionStats);
        processAllMessages();

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(new LinkAddress(IPV6_ADDRESS + "/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        // Agent should not be re-created, so register should be called ony once.
        verify(mConnectivityManager, times(1)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), any(),
                anyInt());

        // The network should have IPv6 address now
        assertThat(mDataNetworkUT.getLinkProperties().getAllAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));
    }

    @Test
    public void testIpChangedV4Removed() throws Exception {
        testCreateDataNetwork();

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(new LinkAddress(IPV6_ADDRESS + "/64")))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        // Agent should not be re-created, so register should be called ony once.
        verify(mConnectivityManager, times(1)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), any(),
                anyInt());

        // The network should have IPv6 address now
        assertThat(mDataNetworkUT.getLinkProperties().getAllAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));
    }

    @Test
    public void testIpChangedV6Removed() throws Exception {
        testCreateDataNetwork();

        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(123)
                .setLinkStatus(2)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname")
                .setAddresses(Arrays.asList(new LinkAddress(
                        InetAddresses.parseNumericAddress(IPV4_ADDRESS), 32)))
                .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("10.0.2.3"),
                        InetAddresses.parseNumericAddress("fd00:976a::9")))
                .setGatewayAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("10.0.2.15"),
                        InetAddresses.parseNumericAddress("fe80::2")))
                .setPcscfAddresses(Arrays.asList(
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::8"),
                        InetAddresses.parseNumericAddress("fd00:976a:c202:1d::7"),
                        InetAddresses.parseNumericAddress("fd00:976a:c305:1d::5")))
                .setMtuV4(1234)
                .setMtuV6(5678)
                .setPduSessionId(1)
                .setQosBearerSessions(new ArrayList<>())
                .setTrafficDescriptors(Collections.emptyList())
                .build();

        // IP changes
        mDataNetworkUT.obtainMessage(8/*EVENT_DATA_STATE_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();
        processAllMessages();

        // Agent should not be re-created, so register should be called ony once.
        verify(mConnectivityManager, times(1)).registerNetworkAgent(any(), any(NetworkInfo.class),
                any(LinkProperties.class), any(NetworkCapabilities.class), any(), any(),
                anyInt());

        // The network should have IPv6 address now
        assertThat(mDataNetworkUT.getLinkProperties().getAllAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS));
    }
}
