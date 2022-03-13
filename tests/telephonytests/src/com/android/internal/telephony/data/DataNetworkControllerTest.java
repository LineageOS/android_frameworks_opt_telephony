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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Annotation.NetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataCallResponse.LinkStatus;
import android.telephony.data.DataProfile;
import android.telephony.data.DataServiceCallback;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetworkController.HandoverRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkControllerTest extends TelephonyTest {
    private static final String IPV4_ADDRESS = "10.0.2.15";
    private static final String IPV6_ADDRESS = "2607:fb90:a620:651d:eabe:f8da:c107:44be";

    @Mock
    private PhoneSwitcher mMockedPhoneSwitcher;
    @Mock
    protected ISub mIsub;

    private final SparseArray<DataServiceManager> mMockedDataServiceManagers = new SparseArray<>();
    private final SparseArray<RegistrantList> mDataCallListChangedRegistrants = new SparseArray<>();
    private DataNetworkController mDataNetworkControllerUT;
    private PersistableBundle mCarrierConfig;
    @Mock
    private DataNetworkControllerCallback mMockedDataNetworkControllerCallback;

    private DataProfile mGeneralPurposeDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2163)
                    .setOperatorNumeric("12345")
                    .setEntryName("internet_supl_apn")
                    .setApnName("internet_supl_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL
                            | ApnSetting.TYPE_MMS)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE)
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                            | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                    .setProfileId(1234)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    private DataProfile mImsDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2164)
                    .setOperatorNumeric("12345")
                    .setEntryName("ims_apn")
                    .setApnName("ims_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE)
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                            | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                    .setProfileId(1235)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    /** Data call response map. The first key is the transport type, the second key is the cid. */
    private final Map<Integer, Map<Integer, DataCallResponse>> mDataCallResponses = new HashMap<>();

    private @NonNull DataCallResponse createDataCallResponse(int cid, @LinkStatus int linkStatus) {
        return new DataCallResponse.Builder()
                .setCause(0)
                .setRetryDurationMillis(-1L)
                .setId(cid)
                .setLinkStatus(linkStatus)
                .setProtocolType(ApnSetting.PROTOCOL_IPV4V6)
                .setInterfaceName("ifname" + cid)
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
    }

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = createDataCallResponse(cid,
                    DataCallResponse.LINK_STATUS_ACTIVE);
            int transport = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
            if (dsm == mMockedWwanDataServiceManager) {
                transport = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
            } else if (dsm == mMockedWlanDataServiceManager) {
                transport = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
            }
            mDataCallResponses.computeIfAbsent(transport, v -> new HashMap<>());
            mDataCallResponses.get(transport).put(cid, response);
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();

            mDataCallListChangedRegistrants.get(transport).notifyRegistrants(
                    new AsyncResult(transport, new ArrayList<>(mDataCallResponses.get(
                            transport).values()), null));
            return null;
        }).when(dsm).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
    }

    private void clearCallbacks() throws Exception {
        Field field = DataNetworkController.class
                .getDeclaredField("mDataNetworkControllerCallbacks");
        field.setAccessible(true);
        ((Set<DataNetworkControllerCallback>) field.get(mDataNetworkControllerUT)).clear();
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int regState) {
        ServiceState ss = new ServiceState();
        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(regState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
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
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();
    }

    @Before
    public void setUp() throws Exception {
        logd("DataNetworkControllerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        doReturn(true).when(mPhone).isUsingNewDataStack();
        mMockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mMockedWwanDataServiceManager);
        mMockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mMockedWlanDataServiceManager);

        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mMockedPhoneSwitcher);
        doReturn(1).when(mIsub).getDefaultDataSubId();
        doReturn(mIsub).when(mIBinder).queryLocalInterface(anyString());
        doReturn(mPhone).when(mPhone).getImsPhone();
        mServiceManagerMockedServices.put("isub", mIBinder);
        doReturn(new SubscriptionPlan[]{}).when(mNetworkPolicyManager)
                .getSubscriptionPlans(anyInt(), any());

        mCarrierConfig = mContextFixture.getCarrierConfigBundle();
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_NETWORK_CAPABILITY_PRIORITIES_STRING_ARRAY,
                new String[] {
                        "eims:90", "supl:80", "mms:70", "xcap:70", "cbs:50", "mcx:50", "fota:50",
                        "ims:40", "dun:30", "enterprise:20", "internet:20"
                });
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true);
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{"default", "mms", "dun", "supl"});
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{"default", "mms", "dun", "supl"});
        doReturn(true).when(mSST).getDesiredPowerState();
        doReturn(true).when(mSST).getPowerStateFromCarrier();
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        doReturn("").when(mSubscriptionController).getDataEnabledOverrideRules(anyInt());

        for (int transport : new int[]{AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN}) {
            mDataCallListChangedRegistrants.put(transport, new RegistrantList());
            setSuccessfulSetupDataResponse(mMockedDataServiceManagers.get(transport), 1);
            doAnswer(invocation -> {
                int cid = (int) invocation.getArguments()[0];
                mDataCallResponses.get(transport).remove(cid);
                mDataCallListChangedRegistrants.get(transport).notifyRegistrants(
                        new AsyncResult(transport, new ArrayList<>(mDataCallResponses.get(
                                transport).values()), null));
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).deactivateDataCall(
                    anyInt(), anyInt(), eq(null));

            doAnswer(invocation -> {
                Handler h = (Handler) invocation.getArguments()[0];
                int what = (int) invocation.getArguments()[1];
                mDataCallListChangedRegistrants.get(transport).addUnique(h, what, transport);
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).registerForDataCallListChanged(any(
                    Handler.class), anyInt());
        }

        mDataNetworkControllerUT = new DataNetworkController(mPhone, Looper.myLooper());
        processAllMessages();
        // Clear the callbacks created by the real sub-modules created by DataNetworkController.
        clearCallbacks();
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

        doAnswer(invocation -> {
            TelephonyNetworkRequest networkRequest =
                    (TelephonyNetworkRequest) invocation.getArguments()[0];
            if (mGeneralPurposeDataProfile.canSatisfy(networkRequest.getCapabilities())) {
                return mGeneralPurposeDataProfile;
            } else if (mImsDataProfile.canSatisfy(networkRequest.getCapabilities())) {
                return mImsDataProfile;
            }
            logd("Cannot find data profile to satisfy " + networkRequest);
            return null;
        }).when(mDataProfileManager).getDataProfileForNetworkRequest(
                any(TelephonyNetworkRequest.class), anyInt());

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
        doReturn(true).when(mDataProfileManager).isDataProfilePreferred(any(DataProfile.class));
        doReturn(true).when(mDataProfileManager).isDataProfileValid(any(DataProfile.class));

        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedDataNetworkControllerCallback).invokeFromExecutor(any(Runnable.class));
        mDataNetworkControllerUT.registerDataNetworkControllerCallback(
                mMockedDataNetworkControllerCallback);

        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                10/*SIM_STATE_LOADED*/, 0).sendToTarget();
        mDataNetworkControllerUT.obtainMessage(8/*EVENT_DATA_SERVICE_BINDING_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, true, null))
                .sendToTarget();
        mDataNetworkControllerUT.obtainMessage(8/*EVENT_DATA_SERVICE_BINDING_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WLAN, true, null))
                .sendToTarget();
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        processAllMessages();

        logd("DataNetworkControllerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private @NonNull TelephonyNetworkRequest createNetworkRequest(Integer... capabilities) {
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        for (int networkCapability : capabilities) {
            builder.addCapability(networkCapability);
        }
        return new TelephonyNetworkRequest(builder.build(), mPhone);
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
            networkRequestList.add(createNetworkRequest(netCap));
        }

        // Check if emergency has the highest priority, then mms, then internet.
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Add IMS
        assertThat(networkRequestList.add(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS))).isTrue();

        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(3).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Add IMS again
        assertThat(networkRequestList.add(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS))).isFalse();
        assertThat(networkRequestList.size()).isEqualTo(4);

        // Remove MMS
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove EIMS
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_EIMS))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove Internet
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET))).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove XCAP (which does not exist)
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_XCAP))).isFalse();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove IMS
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS))).isTrue();
        assertThat(networkRequestList).isEmpty();
    }

    private @NonNull List<DataNetwork> getDataNetworks() throws Exception {
        Field field = DataNetworkController.class.getDeclaredField("mDataNetworkList");
        field.setAccessible(true);
        return (List<DataNetwork>) field.get(mDataNetworkControllerUT);
    }

    private void verifyInternetConnected() throws Exception {
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        assertThat(dataNetworkList.get(0).isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkConnected(any());
    }

    private void verifyNoInternetSetup() throws Exception {
        // Make sure internet is not connected.
        verify(mMockedDataNetworkControllerCallback, never()).onInternetDataNetworkConnected(any());
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).isEmpty();
    }

    private void verifyAllDataDisconnected() throws Exception {
        processAllMessages();

        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).isEmpty();

        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkDisconnected();
    }

    // To test the basic data setup. Copy this as example for other tests.
    @Test
    public void testSetupDataNetwork() throws Exception {
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        DataNetwork dataNetwork = dataNetworkList.get(0);
        assertThat(dataNetwork.getDataProfile()).isEqualTo(mGeneralPurposeDataProfile);
        assertThat(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
        assertThat(dataNetwork.isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getLinkProperties().getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));

        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkConnected(any());
    }

    @Test
    public void testSetupImsDataNetwork() throws Exception {
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        DataNetwork dataNetwork = dataNetworkList.get(0);
        assertThat(dataNetwork.getDataProfile()).isEqualTo(mImsDataProfile);
        assertThat(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(dataNetwork.isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getLinkProperties().getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));
    }

    @Test
    public void testDataNetworkControllerCallback() throws Exception {
        mDataNetworkControllerUT.registerDataNetworkControllerCallback(
                mMockedDataNetworkControllerCallback);
        processAllMessages();
        testSetupDataNetwork();
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(true));
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkConnected(any());

        mDataNetworkControllerUT.unregisterDataNetworkControllerCallback(
                mMockedDataNetworkControllerCallback);
        processAllMessages();
    }

    @Test
    public void testSimRemovalDataTearDown() throws Exception {
        testSetupDataNetwork();

        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                TelephonyManager.SIM_STATE_ABSENT, 0).sendToTarget();
        verifyAllDataDisconnected();
    }

    @Test
    public void testSimRemovalAndThenInserted() throws Exception {
        testSimRemovalDataTearDown();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // Insert the SIM again.
        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                TelephonyManager.SIM_STATE_LOADED, 0).sendToTarget();
        processAllMessages();

        verifyInternetConnected();
    }

    @Test
    public void testMovingFromNoServiceToInService() throws Exception {
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        verifyNoInternetSetup();

        // Network becomes in-service.
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        verifyInternetConnected();
    }

    @Test
    public void testMovingFromInServiceToNoService() throws Exception {
        testSetupDataNetwork();

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);
        // Verify we don't tear down the data network.
        verifyInternetConnected();

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);
        // Verify we don't tear down the data network.
        verifyInternetConnected();
    }

    @Test
    public void testPsRestrictedAndLifted() throws Exception {
        testSetupDataNetwork();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // PS restricted.
        mDataNetworkControllerUT.obtainMessage(6/*EVENT_PS_RESTRICT_ENABLED*/).sendToTarget();
        processAllMessages();

        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).isEmpty();
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkDisconnected();

        // PS unrestricted.
        mDataNetworkControllerUT.obtainMessage(7/*EVENT_PS_RESTRICT_DISABLED*/).sendToTarget();
        processAllMessages();

        verifyInternetConnected();
    }

    @Test
    public void testRatChanges() throws Exception {
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        testSetupDataNetwork();

        // Now RAT changes from LTE to UMTS, make sure the network is lingered.
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UMTS,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        verifyInternetConnected();

        // Now RAT changes from UMTS to GSM
        doReturn(null).when(mDataProfileManager).getDataProfileForNetworkRequest(
                any(TelephonyNetworkRequest.class), eq(TelephonyManager.NETWORK_TYPE_GSM));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_GSM,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        verifyAllDataDisconnected();

        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);
        // Now RAT changes from GSM to UMTS
        doReturn(null).when(mDataProfileManager).getDataProfileForNetworkRequest(
                any(TelephonyNetworkRequest.class), eq(TelephonyManager.NETWORK_TYPE_UMTS));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UMTS,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        verifyNoInternetSetup();

        doReturn(mGeneralPurposeDataProfile).when(mDataProfileManager)
                .getDataProfileForNetworkRequest(any(TelephonyNetworkRequest.class), anyInt());
        // Now RAT changes from UMTS to LTE
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        verifyInternetConnected();
    }

    @Test
    public void testVoiceCallEndedOnVoiceDataNonConcurrentNetwork() throws Exception {
        doReturn(false).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(PhoneConstants.State.OFFHOOK).when(mCT).getState();

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when voice/data concurrent is not supported.
        verifyNoInternetSetup();

        // Call ended.
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        mDataNetworkControllerUT.obtainMessage(18/*EVENT_VOICE_CALL_ENDED*/).sendToTarget();
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
    }

    @Test
    public void testEmergencyCallChanged() throws Exception {
        doReturn(PhoneConstants.PHONE_TYPE_CDMA).when(mPhone).getPhoneType();
        doReturn(true).when(mPhone).isInEcm();
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when the device is in an emergency call.
        verifyNoInternetSetup();

        // Emergency call ended
        doReturn(false).when(mPhone).isInEcm();
        mDataNetworkControllerUT.obtainMessage(20/*EVENT_EMERGENCY_CALL_CHANGED*/).sendToTarget();
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
    }

    @Test
    public void testRoamingDataChanged() throws Exception {
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(false).when(mDataConfigManager).isDataRoamingEnabledByDefault();
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when roaming data is disabled.
        verifyNoInternetSetup();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // Roaming data enabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(true);
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // Roaming data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
        processAllMessages();

        // Verify data is torn down.
        verifyNoInternetSetup();
    }

    @Test
    public void testDataEnabledChanged() throws Exception {
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when user data is disabled.
        verifyNoInternetSetup();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data enabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, true);
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        processAllMessages();

        // Verify data is torn down.
        verifyNoInternetSetup();
    }

    @Test
    public void testMmsAlwaysAllowed() throws Exception {
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(false).when(mDataConfigManager).isDataRoamingEnabledByDefault();
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        // Data should not be allowed when roaming + user data are disabled (soft failure reasons)
        verifyNoInternetSetup();

        // Always allow MMS
        mDataNetworkControllerUT.getDataSettingsManager().setAlwaysAllowMmsData(true);
        // Enable user data to trigger data enabled changed and data reevaluation
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, true);
        processAllMessages();

        // Verify data is allowed
        verifyInternetConnected();
    }

    @Test
    public void testUnmeteredRequest() throws Exception {
        doReturn(true).when(mServiceState).getDataRoaming();
        doReturn(false).when(mDataConfigManager).isDataRoamingEnabledByDefault();
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when roaming + user data are disabled (soft failure reasons)
        verifyNoInternetSetup();

        // Set transport to WLAN (unmetered)
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
        // Data remain disabled, but trigger the preference evaluation.
        mDataNetworkControllerUT.obtainMessage(21 /*EVENT_PREFERRED_TRANSPORT_CHANGED*/,
                NetworkCapabilities.NET_CAPABILITY_INTERNET, 0).sendToTarget();
        mDataNetworkControllerUT.obtainMessage(5 /*EVENT_REEVALUATE_UNSATISFIED_NETWORK_REQUESTS*/,
                DataEvaluation.DataEvaluationReason.PREFERRED_TRANSPORT_CHANGED).sendToTarget();
        processAllMessages();

        // Verify data is allowed even if data is disabled.
        verifyInternetConnected();
    }

    @Test
    public void testHandoverRuleFromString() {
        HandoverRule handoverRule = new HandoverRule("source=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, "
                + "target=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, type=allowed");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.GERAN,
                AccessNetworkType.UTRAN, AccessNetworkType.EUTRAN, AccessNetworkType.NGRAN,
                AccessNetworkType.IWLAN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.GERAN,
                AccessNetworkType.UTRAN, AccessNetworkType.EUTRAN, AccessNetworkType.NGRAN,
                AccessNetworkType.IWLAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_ALLOWED);
        assertThat(handoverRule.isOnlyForRoaming).isFalse();

        handoverRule = new HandoverRule("source=   NGRAN|     IWLAN, "
                + "target  =    EUTRAN,    type  =    disallowed");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.NGRAN,
                AccessNetworkType.IWLAN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.EUTRAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_DISALLOWED);
        assertThat(handoverRule.isOnlyForRoaming).isFalse();

        handoverRule = new HandoverRule("source=   IWLAN, "
                + "target  =    EUTRAN,    type  =    disallowed, roaming = true");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.IWLAN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.EUTRAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_DISALLOWED);
        assertThat(handoverRule.isOnlyForRoaming).isTrue();

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("V2hhdCBUaGUgRnVjayBpcyB0aGlzIQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("target=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=IWLAN, type=wtf"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=NGRAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=IWLAN, target=WTFRAN, type=allowed"));
    }

    @Test
    public void testIsNetworkTypeCongested() throws Exception {
        Set<Integer> congestedNetworkTypes = new ArraySet<>();
        doReturn(congestedNetworkTypes).when(mDataNetworkController)
                .getCongestedOverrideNetworkTypes();
        testSetupDataNetwork();
        DataNetwork dataNetwork = getDataNetworks().get(0);

        // Set 5G unmetered
        congestedNetworkTypes.add(TelephonyManager.NETWORK_TYPE_NR);
        mDataNetworkControllerUT.obtainMessage(23/*EVENT_SUBSCRIPTION_OVERRIDE*/,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_CONGESTED,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_CONGESTED,
                new int[]{TelephonyManager.NETWORK_TYPE_NR}).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertEquals(congestedNetworkTypes,
                mDataNetworkControllerUT.getCongestedOverrideNetworkTypes());
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED));

        // Change data network type to NR
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_NR,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        dataNetwork.sendMessage(13/*EVENT_DISPLAY_INFO_CHANGED*/);
        processAllMessages();
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED));

        // Set all network types metered
        congestedNetworkTypes.clear();
        mDataNetworkControllerUT.obtainMessage(23/*EVENT_SUBSCRIPTION_OVERRIDE*/,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_CONGESTED, 0,
                TelephonyManager.getAllNetworkTypes()).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertTrue(mDataNetworkControllerUT.getCongestedOverrideNetworkTypes().isEmpty());
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED));
    }

    @Test
    public void testIsNetworkTypeUnmeteredViaSubscriptionOverride() throws Exception {
        doReturn(true).when(mDataConfigManager).isTempNotMeteredSupportedByCarrier();
        Set<Integer> unmeteredNetworkTypes = new ArraySet<>();
        doReturn(unmeteredNetworkTypes).when(mDataNetworkController)
                .getUnmeteredOverrideNetworkTypes();
        testSetupDataNetwork();
        DataNetwork dataNetwork = getDataNetworks().get(0);

        // Set 5G unmetered
        unmeteredNetworkTypes.add(TelephonyManager.NETWORK_TYPE_NR);
        mDataNetworkControllerUT.obtainMessage(23/*EVENT_SUBSCRIPTION_OVERRIDE*/,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_UNMETERED,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_UNMETERED,
                new int[]{TelephonyManager.NETWORK_TYPE_NR}).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertEquals(unmeteredNetworkTypes,
                mDataNetworkControllerUT.getUnmeteredOverrideNetworkTypes());
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // Change data network type to NR
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_NR,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        dataNetwork.sendMessage(13/*EVENT_DISPLAY_INFO_CHANGED*/);
        processAllMessages();
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // Set all network types metered
        unmeteredNetworkTypes.clear();
        mDataNetworkControllerUT.obtainMessage(23/*EVENT_SUBSCRIPTION_OVERRIDE*/,
                NetworkPolicyManager.SUBSCRIPTION_OVERRIDE_UNMETERED, 0,
                TelephonyManager.getAllNetworkTypes()).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertTrue(mDataNetworkControllerUT.getUnmeteredOverrideNetworkTypes().isEmpty());
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));
    }

    @Test
    public void testIsNetworkTypeUnmeteredViaSubscriptionPlans() throws Exception {
        doReturn(true).when(mDataConfigManager).isTempNotMeteredSupportedByCarrier();
        List<SubscriptionPlan> subscriptionPlans = new ArrayList<>();
        doReturn(subscriptionPlans).when(mDataNetworkController).getSubscriptionPlans();
        testSetupDataNetwork();
        DataNetwork dataNetwork = getDataNetworks().get(0);

        // Set 5G unmetered
        SubscriptionPlan unmetered5GPlan = SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                        SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                .setNetworkTypes(new int[]{TelephonyManager.NETWORK_TYPE_NR})
                .build();
        SubscriptionPlan generalMeteredPlan = SubscriptionPlan.Builder
                .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                        Period.ofMonths(1))
                .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(500_000_000, System.currentTimeMillis())
                .build();
        subscriptionPlans.add(generalMeteredPlan);
        subscriptionPlans.add(unmetered5GPlan);
        mDataNetworkControllerUT.obtainMessage(22/*EVENT_SUBSCRIPTION_PLANS_CHANGED*/,
                new SubscriptionPlan[]{generalMeteredPlan, unmetered5GPlan}).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertEquals(subscriptionPlans, mDataNetworkControllerUT.getSubscriptionPlans());
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // Change data network type to NR
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_NR,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        dataNetwork.sendMessage(13/*EVENT_DISPLAY_INFO_CHANGED*/);
        processAllMessages();
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));

        // Set all network types metered
        subscriptionPlans.clear();
        mDataNetworkControllerUT.obtainMessage(22/*EVENT_SUBSCRIPTION_PLANS_CHANGED*/,
                new SubscriptionPlan[]{}).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertTrue(mDataNetworkControllerUT.getSubscriptionPlans().isEmpty());
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));
    }

    @Test
    public void testLinkStatusChanged() throws Exception {
        testSetupDataNetwork();
        verify(mMockedDataNetworkControllerCallback).onPhysicalLinkStatusChanged(
                eq(DataCallResponse.LINK_STATUS_ACTIVE));

        DataNetwork dataNetwork = getDataNetworks().get(0);

        DataCallResponse response = createDataCallResponse(1, DataCallResponse.LINK_STATUS_DORMANT);
        dataNetwork.obtainMessage(8 /*EVENT_DATA_STATE_CHANGED */,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        List.of(response), null)).sendToTarget();

        processAllMessages();
        verify(mMockedDataNetworkControllerCallback).onPhysicalLinkStatusChanged(
                eq(DataCallResponse.LINK_STATUS_DORMANT));
    }

    @Test
    public void testHandoverDataNetwork() throws Exception {
        testSetupImsDataNetwork();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.obtainMessage(21/*EVENT_EVALUATE_PREFERRED_TRANSPORT*/,
                NetworkCapabilities.NET_CAPABILITY_IMS, 0).sendToTarget();
        processAllMessages();

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify that IWLAN handover succeeded.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testHandoverDataNetworkNotAllowedByPolicy() throws Exception {
        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY,
                new String[]{"source=EUTRAN, target=IWLAN, type=disallowed"});
        // Force data config manager to reload the carrier config.
        mDataNetworkControllerUT.getDataConfigManager().obtainMessage(
                1/*EVENT_CARRIER_CONFIG_CHANGED*/).sendToTarget();
        processAllMessages();

        testSetupImsDataNetwork();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.obtainMessage(21/*EVENT_PREFERRED_TRANSPORT_CHANGED*/,
                NetworkCapabilities.NET_CAPABILITY_IMS, 0).sendToTarget();
        // After this, IMS data network should be disconnected, and DNC should attempt to
        // establish a new one on IWLAN
        processAllMessages();

        // Verify all data disconnected.
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));

        // A new data network should be connected on IWLAN
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        assertThat(dataNetworkList.get(0).isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(dataNetworkList.get(0).getTransport())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }
}
