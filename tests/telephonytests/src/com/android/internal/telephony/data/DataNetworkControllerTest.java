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

import static com.android.internal.telephony.data.DataNetworkController.DataNetworkControllerCallback;
import static com.android.internal.telephony.data.DataNetworkController.NetworkRequestList;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataServiceCallback;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.SparseArray;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneSwitcher;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkControllerTest extends TelephonyTest {
    private static final String IPV4_ADDRESS = "10.0.2.15";
    private static final String IPV6_ADDRESS = "2607:fb90:a620:651d:eabe:f8da:c107:44be";

    @Mock
    private PhoneSwitcher mMockedPhoneSwitcher;
    @Mock
    private DataServiceManager mMockedWwanDataServiceManager;
    @Mock
    private DataServiceManager mMockedWlanDataServiceManager;
    @Mock
    protected ISub mIsub;

    private DataNetworkController mDataNetworkControllerUT;
    private PersistableBundle mCarrierConfig;

    private DataProfile mDataProfile1 = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2163)
                    .setOperatorNumeric("12345")
                    .setEntryName("fake_apn1")
                    .setApnName("fake_apn1")
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
                    .build())
            .setPreferred(false)
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

    @Before
    public void setUp() throws Exception {
        logd("DataNetworkControllerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mMockedPhoneSwitcher);
        doReturn(1).when(mIsub).getDefaultDataSubId();
        doReturn(mIsub).when(mIBinder).queryLocalInterface(anyString());
        mServiceManagerMockedServices.put("isub", mIBinder);

        mCarrierConfig = mContextFixture.getCarrierConfigBundle();
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_NETWORK_CAPABILITY_PRIORITIES_STRING_ARRAY,
                new String[] {
                        "eims:90", "supl:80", "mms:70", "xcap:70", "cbs:50", "mcx:50", "fota:50",
                        "ims:40", "dun:30", "enterprise:20", "internet:20"
                });
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        doReturn(true).when(mSST).getDesiredPowerState();
        doReturn(true).when(mSST).getPowerStateFromCarrier();
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 1);
        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 1);

        mDataNetworkControllerUT = Mockito.spy(new DataNetworkController(
                mPhone, Looper.myLooper()));
        SparseArray<DataServiceManager> dataServiceManagers = new SparseArray<>();
        dataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mMockedWwanDataServiceManager);
        dataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mMockedWlanDataServiceManager);
        replaceInstance(DataNetworkController.class, "mDataServiceManagers",
                mDataNetworkControllerUT, dataServiceManagers);
        replaceInstance(DataNetworkController.class, "mDataProfileManager",
                mDataNetworkControllerUT, mDataProfileManager);
        replaceInstance(DataNetworkController.class, "mAccessNetworksManager",
                mDataNetworkControllerUT, mAccessNetworksManager);
        doReturn(mDataProfile1).when(mDataProfileManager).getDataProfileForNetworkRequest(
                any(TelephonyNetworkRequest.class));

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());

        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                10/*SIM_STATE_LOADED*/, 0).sendToTarget();
        mDataNetworkControllerUT.obtainMessage(8/*EVENT_DATA_SERVICE_BINDING_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, true, null))
                .sendToTarget();
        mDataNetworkControllerUT.obtainMessage(8/*EVENT_DATA_SERVICE_BINDING_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, true, null))
                .sendToTarget();

        processAllMessages();

        logd("DataNetworkControllerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    // The purpose of this test is to make sure the network request insertion/removal works as
    // expected, and make sure it is always sorted.
    @Test
    public void testNetworkRequestList() {
        doReturn(mDataNetworkControllerUT.getDataConfigManager())
                .when(mDataNetworkController).getDataConfigManager();
        NetworkRequestList networkRequestList = new NetworkRequestList();

        int[] netCaps = new int[]{NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_EIMS,
                NetworkCapabilities.NET_CAPABILITY_MMS};
        for (int netCap : netCaps) {
            networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                    .addCapability(netCap)
                    .build(), mPhone));
        }

        // Check if emergency has the highest priority, then mms, then internet.
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);


        // Add IMS
        assertThat(networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone))).isTrue();

        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(3).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Add IMS again
        assertThat(networkRequestList.add(new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build(), mPhone))).isFalse();
        assertThat(networkRequestList.size()).isEqualTo(4);

        // Remove MMS
        assertThat(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                        .build(), mPhone))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove EIMS
        assertThat(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)
                        .build(), mPhone))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove Internet
        assertThat(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build(), mPhone))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove XCAP (which does not exist)
        assertThat(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP)
                        .build(), mPhone))).isFalse();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove IMS
        assertThat(networkRequestList.remove(new TelephonyNetworkRequest(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                        .build(), mPhone))).isTrue();
        assertThat(networkRequestList).isEmpty();
    }

    private @NonNull List<DataNetwork> getDataNetworks() throws Exception {
        Field field = DataNetworkController.class.getDeclaredField("mDataNetworkList");
        field.setAccessible(true);
        return (List<DataNetwork>) field.get(mDataNetworkControllerUT);
    }

    // To test the basic data setup.
    @Test
    public void testSetupDataNetwork() throws Exception {
        mDataNetworkControllerUT.addNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build());
        processAllMessages();
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        DataNetwork dataNetwork = dataNetworkList.get(0);
        assertThat(dataNetwork.getDataProfile()).isEqualTo(mDataProfile1);
        assertThat(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(dataNetwork.isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getLinkProperties().getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));
    }

    public static class MyDataNetworkControllerCallback extends DataNetworkControllerCallback {
        @Override
        public void onAllDataNetworksDisconnected() {
        }
    }

    @Test
    public void testDataNetworkControllerCallback() throws Exception {
        DataNetworkControllerCallback callback = Mockito.spy(new MyDataNetworkControllerCallback());
        mDataNetworkControllerUT.registerDataNetworkControllerCallback(
                Runnable::run, callback, false);
        processAllMessages();
        verify(callback).onAllDataNetworksDisconnected();

        testSetupDataNetwork();
        verify(callback).onInternetDataNetworkConnected();
    }

    @Test
    public void testDataNetworkControllerCallbackAutoUnregister() throws Exception {
        DataNetworkControllerCallback callback = Mockito.spy(new MyDataNetworkControllerCallback());
        mDataNetworkControllerUT.registerDataNetworkControllerCallback(
                Runnable::run, callback, true);
        processAllMessages();
        verify(callback).onAllDataNetworksDisconnected();

        testSetupDataNetwork();
        // Because the callback was auto-unregistered, there shouldn't be any further invocation.
        verify(callback, never()).onInternetDataNetworkConnected();
    }
}
