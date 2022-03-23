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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkPolicyManager;
import android.net.NetworkRequest;
import android.net.vcn.VcnManager.VcnNetworkPolicyChangeListener;
import android.net.vcn.VcnNetworkPolicyResult;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RegistrantList;
import android.provider.Telephony;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.LteVopsSupportInfo;
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
import android.telephony.data.ThrottleStatus;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataNetworkController.HandoverRule;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataNetworkControllerTest extends TelephonyTest {
    private static final String IPV4_ADDRESS = "10.0.2.15";
    private static final String IPV6_ADDRESS = "2607:fb90:a620:651d:eabe:f8da:c107:44be";

    @Mock
    private PhoneSwitcher mMockedPhoneSwitcher;
    @Mock
    protected ISub mIsub;

    private int mNetworkRequestId = 0;

    private final SparseArray<DataServiceManager> mMockedDataServiceManagers = new SparseArray<>();
    private final SparseArray<RegistrantList> mDataCallListChangedRegistrants = new SparseArray<>();
    private DataNetworkController mDataNetworkControllerUT;
    private PersistableBundle mCarrierConfig;
    @Mock
    private DataNetworkControllerCallback mMockedDataNetworkControllerCallback;
    @Mock
    private DataRetryManagerCallback mMockedDataRetryManagerCallback;

    private final DataProfile mGeneralPurposeDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2163)
                    .setOperatorNumeric("12345")
                    .setEntryName("internet_supl_mms_apn")
                    .setApnName("internet_supl_mms_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL
                            | ApnSetting.TYPE_MMS)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT))
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT
                            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                            | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                    .setProfileId(1234)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    private final DataProfile mImsDataProfile = new DataProfile.Builder()
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
                    .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN))
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN
                            | TelephonyManager.NETWORK_TYPE_BITMASK_UMTS
                            | TelephonyManager.NETWORK_TYPE_BITMASK_NR))
                    .setProfileId(1235)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    private final DataProfile mEmergencyDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setEntryName("DEFAULT EIMS")
                    .setId(2165)
                    .setProtocol(ApnSetting.PROTOCOL_IPV4V6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IPV4V6)
                    .setApnName("sos")
                    .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                    .setCarrierEnabled(true)
                    .setApnSetId(Telephony.Carriers.MATCH_ALL_APN_SET_ID)
                    .build())
            .build();

    private final DataProfile mFotaDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2166)
                    .setOperatorNumeric("12345")
                    .setEntryName("fota_apn")
                    .setApnName("fota_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_FOTA)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | (int) TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT)
                    .setProfileId(1236)
                    .setMaxConns(321)
                    .setWaitTime(456)
                    .setMaxConnsTime(789)
                    .build())
            .setPreferred(false)
            .build();

    private final DataProfile mTetheringDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2167)
                    .setOperatorNumeric("12345")
                    .setEntryName("dun_apn")
                    .setApnName("dun_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_DUN)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IP)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | (int) TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT)
                    .setProfileId(1236)
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

    private void setFailedSetupDataResponse(DataServiceManager dsm, @DataFailureCause int cause,
            long retryMillis) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = new DataCallResponse.Builder()
                    .setCause(cause)
                    .setRetryDurationMillis(retryMillis)
                    .build();
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.sendToTarget();
            return null;
        }).when(dsm).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));
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

    private void carrierConfigChanged() {
        // Trigger carrier config reloading
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.putExtra(CarrierConfigManager.EXTRA_SLOT_INDEX, 0);
        mContext.sendBroadcast(intent);
        processAllMessages();
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int regState) {
        serviceStateChanged(networkType, regState, null);
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int regState, DataSpecificRegistrationInfo dsri) {
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
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();
    }

    private void setVcnManagerPolicy(boolean vcnManaged, boolean shouldTearDown) {
        doAnswer(invocation -> {
            final NetworkCapabilities networkCapabilities =
                    (NetworkCapabilities) invocation.getArguments()[0];
            if (vcnManaged) {
                networkCapabilities.removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
            } else {
                networkCapabilities.addCapability(
                        NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
            }
            return new VcnNetworkPolicyResult(
                    shouldTearDown, networkCapabilities);
        }).when(mVcnManager).applyVcnNetworkPolicy(any(NetworkCapabilities.class),
                any(LinkProperties.class));
    }

    private void initializeConfig() {
        mCarrierConfig = mContextFixture.getCarrierConfigBundle();
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_NETWORK_CAPABILITY_PRIORITIES_STRING_ARRAY,
                new String[]{
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

        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_DATA_SETUP_RETRY_RULES_STRING_ARRAY,
                new String[]{
                        "capabilities=eims, retry_interval=1000, maximum_retries=20",
                        "fail_causes=8|27|28|29|30|32|33|35|50|51|111|-5|-6|65537|65538|-3|2253|"
                                + "2254, maximum_retries=0", // No retry for those causes
                        "capabilities=mms|supl|cbs, retry_interval=2000",
                        "capabilities=internet|enterprise|dun|ims|fota, retry_interval=2500|3000|"
                                + "5000|10000|15000|20000|40000|60000|120000|240000|"
                                + "600000|1200000|1800000, maximum_retries=20"
                });
        mCarrierConfig.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 1234);

        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_NETWORK_TEMP_NOT_METERED_SUPPORTED_BOOL,
                true);

        mCarrierConfig.putIntArray(CarrierConfigManager.KEY_ONLY_SINGLE_DC_ALLOWED_INT_ARRAY,
                new int[] {TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B});

        mContextFixture.putResource(com.android.internal.R.string.config_bandwidthEstimateSource,
                "bandwidth_estimator");
    }

    @Before
    public void setUp() throws Exception {
        logd("DataNetworkControllerTest +Setup!");
        super.setUp(getClass().getSimpleName());

        initializeConfig();
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

        // Note that creating a "real" data network controller will also result in creating
        // real DataRetryManager, DataConfigManager, etc...Normally in unit test we should isolate
        // other modules and make them mocked, but only focusing on testing the unit we would like
        // to test, in this case, DataNetworkController. But since there are too many interactions
        // between DataNetworkController and its sub-modules, we intend to make those modules "real"
        // as well, except some modules below we replaced with mocks.
        mDataNetworkControllerUT = new DataNetworkController(mPhone, Looper.myLooper());
        doReturn(mDataNetworkControllerUT).when(mPhone).getDataNetworkController();
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
            int networkType = (int) invocation.getArguments()[1];
            List<DataProfile> profiles = List.of(mGeneralPurposeDataProfile, mImsDataProfile,
                    mEmergencyDataProfile, mFotaDataProfile, mTetheringDataProfile);
            for (DataProfile dataProfile : profiles) {
                if (dataProfile.canSatisfy(networkRequest.getCapabilities())
                        && (dataProfile.getApnSetting().getNetworkTypeBitmask() == 0
                        || (dataProfile.getApnSetting().getNetworkTypeBitmask()
                        & ServiceState.getBitmaskForTech(networkType)) != 0)) {
                    return dataProfile;
                }
            }
            logd("Cannot find data profile to satisfy " + networkRequest + ", network type="
                    + TelephonyManager.getNetworkTypeName(networkType));
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
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedDataRetryManagerCallback).invokeFromExecutor(any(Runnable.class));

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

        carrierConfigChanged();

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        processAllMessages();

        logd("DataNetworkControllerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        super.tearDown();
    }

    private @NonNull TelephonyNetworkRequest createNetworkRequest(Integer... capabilities) {
        NetworkCapabilities netCaps = new NetworkCapabilities();
        for (int networkCapability : capabilities) {
            netCaps.addCapability(networkCapability);
        }

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);

        return new TelephonyNetworkRequest(nativeNetworkRequest, mPhone);
    }

    // The purpose of this test is to make sure the network request insertion/removal works as
    // expected, and make sure it is always sorted.
    @Test
    public void testNetworkRequestList() {
        NetworkRequestList networkRequestList = new NetworkRequestList();

        TelephonyNetworkRequest internetNetworkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        TelephonyNetworkRequest eimsNetworkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_EIMS);
        TelephonyNetworkRequest mmsNetworkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_MMS);
        networkRequestList.add(internetNetworkRequest);
        networkRequestList.add(eimsNetworkRequest);
        networkRequestList.add(mmsNetworkRequest);

        // Check if emergency has the highest priority, then mms, then internet.
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Add IMS
        TelephonyNetworkRequest imsNetworkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.add(imsNetworkRequest)).isTrue();

        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_MMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(3).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Add IMS again
        assertThat(networkRequestList.add(imsNetworkRequest)).isFalse();
        assertThat(networkRequestList.size()).isEqualTo(4);

        // Remove MMS
        assertThat(networkRequestList.remove(mmsNetworkRequest)).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(2).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove EIMS
        assertThat(networkRequestList.remove(eimsNetworkRequest)).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(networkRequestList.get(1).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Remove Internet
        assertThat(networkRequestList.remove(internetNetworkRequest)).isTrue();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove XCAP (which does not exist)
        assertThat(networkRequestList.remove(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_XCAP))).isFalse();
        assertThat(networkRequestList.get(0).getCapabilities()[0])
                .isEqualTo(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Remove IMS
        assertThat(networkRequestList.remove(imsNetworkRequest)).isTrue();
        assertThat(networkRequestList).isEmpty();
    }

    private @NonNull List<DataNetwork> getDataNetworks() throws Exception {
        Field field = DataNetworkController.class.getDeclaredField("mDataNetworkList");
        field.setAccessible(true);
        return (List<DataNetwork>) field.get(mDataNetworkControllerUT);
    }

    private void verifyInternetConnected() throws Exception {
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkConnected(any());
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void verifyConnectedNetworkHasCapabilities(@NetCapability int... networkCapabilities)
            throws Exception {
        List<DataNetwork> dataNetworkList = getDataNetworks();
        for (DataNetwork dataNetwork : getDataNetworks()) {
            if (dataNetwork.isConnected() && Arrays.stream(networkCapabilities).boxed()
                    .allMatch(dataNetwork.getNetworkCapabilities()::hasCapability)) {
                return;
            }
        }
        fail("No network with " + DataUtils.networkCapabilitiesToString(networkCapabilities)
                + " is connected. dataNetworkList=" + dataNetworkList);
    }

    private void verifyNoConnectedNetworkHasCapability(@NetCapability int networkCapability)
            throws Exception {
        for (DataNetwork dataNetwork : getDataNetworks()) {
            assertWithMessage("Network " + dataNetwork + " should not be connected.")
                    .that(dataNetwork.isConnected() && dataNetwork.getNetworkCapabilities()
                            .hasCapability(networkCapability)).isFalse();
        }
    }

    private void verifyConnectedNetworkHasDataProfile(@NonNull DataProfile dataProfile)
            throws Exception {
        List<DataNetwork> dataNetworkList = getDataNetworks();
        for (DataNetwork dataNetwork : getDataNetworks()) {
            if (dataNetwork.isConnected() && dataNetwork.getDataProfile().equals(dataProfile)) {
                return;
            }
        }
        fail("No network with " + dataProfile + " is connected. dataNetworkList="
                + dataNetworkList);
    }

    private void verifyAllDataDisconnected() throws Exception {
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertWithMessage("All data should be disconnected but it's not. " + dataNetworkList)
                .that(dataNetworkList).isEmpty();
    }

    // To test the basic data setup. Copy this as example for other tests.
    @Test
    public void testSetupDataNetwork() throws Exception {
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);

        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        DataNetwork dataNetwork = dataNetworkList.get(0);
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
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);
        verifyConnectedNetworkHasDataProfile(mImsDataProfile);
        List<DataNetwork> dataNetworkList = getDataNetworks();
        DataNetwork dataNetwork = dataNetworkList.get(0);
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
        processAllMessages();
        verifyAllDataDisconnected();
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkDisconnected();

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

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

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
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkDisconnected();


        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);
        // Now RAT changes from GSM to UMTS
        doReturn(null).when(mDataProfileManager).getDataProfileForNetworkRequest(
                any(TelephonyNetworkRequest.class), eq(TelephonyManager.NETWORK_TYPE_UMTS));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UMTS,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

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
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

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
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

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
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when roaming data is disabled.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testDataEnabledChanged() throws Exception {
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when user data is disabled.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testMmsAlwaysAllowedDataDisabled() throws Exception {
        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        // Always allow MMS
        mDataNetworkControllerUT.getDataSettingsManager().setAlwaysAllowMmsData(true);
        processAllMessages();
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        // Make sure MMS is the only capability advertised, but not internet or SUPL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
    }

    @Test
    public void testMmsAlwaysAllowedRoamingDisabled() throws Exception {
        // Data roaming disabled
        doReturn(true).when(mServiceState).getDataRoaming();
        // Device is roaming
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        // Always allow MMS
        mDataNetworkControllerUT.getDataSettingsManager().setAlwaysAllowMmsData(true);
        processAllMessages();

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        // Make sure MMS is not allowed. MMS always allowed should be only applicable to data
        // disabled case.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
    }

    @Test
    public void testUnmeteredRequestPreferredOnIwlan() throws Exception {
        // Preferred on cellular
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when roaming + user data are disabled (soft failure reasons)
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Set transport to WLAN (unmetered)
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(
                        eq(NetworkCapabilities.NET_CAPABILITY_INTERNET));
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
    public void testUnmeteredRequestDataRoamingDisabled() throws Exception {
        // Data roaming disabled
        doReturn(true).when(mServiceState).getDataRoaming();
        // MMS is unmetered
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();
        // Device is roaming
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testUnmeteredRequestDataDisabled() throws Exception {
        // MMS is unmetered
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();

        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));

        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testEmergencyRequest() throws Exception {
        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_EIMS));
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_EIMS);
        verifyConnectedNetworkHasDataProfile(mEmergencyDataProfile);
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

    @Test
    public void testSetupDataNetworkRetrySuggestedByNetwork() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllFutureMessages();

        // Should retried 20 times, which is the maximum based on the retry config rules.
        verify(mMockedWwanDataServiceManager, times(21)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetupDataNetworkRetryFailed() {
        mDataNetworkControllerUT.getDataRetryManager()
                .registerCallback(mMockedDataRetryManagerCallback);
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));

        // Process first retry
        moveTimeForward(2500);
        processAllMessages();
        verify(mMockedWwanDataServiceManager, times(2)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
        ArgumentCaptor<DataRetryManager.DataSetupRetryEntry> retryEntry =
                ArgumentCaptor.forClass(DataRetryManager.DataSetupRetryEntry.class);
        verify(mMockedDataRetryManagerCallback, times(1))
                .onDataNetworkSetupRetry(retryEntry.capture());
        assertThat(retryEntry.getValue().getState()).isEqualTo(
                DataRetryManager.DataRetryEntry.RETRY_STATE_FAILED);

        // Cause data network setup failed due to RADIO_DISABLED_BY_CARRIER
        doReturn(false).when(mSST).getPowerStateFromCarrier();

        // Process second retry and ensure data network setup failed
        moveTimeForward(3000);
        processAllMessages();
        verify(mMockedWwanDataServiceManager, times(2)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
        verify(mMockedDataRetryManagerCallback, times(2))
                .onDataNetworkSetupRetry(retryEntry.capture());
        assertThat(retryEntry.getValue().getState()).isEqualTo(
                DataRetryManager.DataRetryEntry.RETRY_STATE_FAILED);

        // Data network setup allowed again
        doReturn(true).when(mSST).getPowerStateFromCarrier();

        // Should not retry again after retry failure
        processAllFutureMessages();
        verify(mMockedWwanDataServiceManager, times(2)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetupDataNetworkPermanentFailure() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.PROTOCOL_ERRORS,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllFutureMessages();

        // There should be only one attempt, and no retry should happen because it's a permanent
        // failure.
        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetupDataNetworkNetworkSuggestedNeverRetry() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.PROTOCOL_ERRORS,
                Long.MAX_VALUE);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllFutureMessages();

        // There should be only one attempt, and no retry should happen because it's a permanent
        // failure.
        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetupDataNetworkNetworkSuggestedRetryTimerDataThrottled() {
        mDataNetworkControllerUT.getDataRetryManager()
                .registerCallback(mMockedDataRetryManagerCallback);

        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.PROTOCOL_ERRORS,
                10000);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();

        // There should be only one attempt, and no retry should happen because the second one
        // was throttled.
        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mMockedDataRetryManagerCallback)
                .onThrottleStatusChanged(throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType())
                .isEqualTo(ThrottleStatus.RETRY_TYPE_NEW_CONNECTION);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testNrAdvancedByPco() throws Exception {
        testSetupDataNetwork();
        verify(mMockedDataNetworkControllerCallback, never())
                .onNrAdvancedCapableByPcoChanged(anyBoolean());
        mSimulatedCommands.triggerPcoData(1, "IPV6", 1234, new byte[]{1});
        processAllMessages();
        verify(mMockedDataNetworkControllerCallback).onNrAdvancedCapableByPcoChanged(eq(true));

        mSimulatedCommands.triggerPcoData(1, "IPV6", 1234, new byte[]{0});
        processAllMessages();
        verify(mMockedDataNetworkControllerCallback).onNrAdvancedCapableByPcoChanged(eq(false));
    }

    @Test
    public void testSetupDataNetworkVcnManaged() throws Exception {
        // VCN managed
        setVcnManagerPolicy(true, false);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);

        mDataNetworkControllerUT.addNetworkRequest(tnr);
        processAllMessages();

        // VCN managed network won't trigger onInternetDataNetworkConnected.
        // DataNetwork.isInternetSupported() is false for VCN managed network.
        verify(mMockedDataNetworkControllerCallback, never())
                .onInternetDataNetworkConnected(any());
        List<DataNetwork> dataNetworks = getDataNetworks();
        assertThat(dataNetworks).hasSize(1);
        assertThat(dataNetworks.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isFalse();
        assertThat(dataNetworks.get(0).isInternetSupported()).isFalse();
        assertThat(dataNetworks.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue();
    }

    @Test
    public void testSetupDataNetworkVcnRequestedTeardown() throws Exception {
        // VCN managed, tear down on setup.
        setVcnManagerPolicy(true, true);
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);

        mDataNetworkControllerUT.addNetworkRequest(tnr);
        processAllMessages();

        // Should not be any data network created.
        List<DataNetwork> dataNetworks = getDataNetworks();
        assertThat(dataNetworks).hasSize(0);
    }

    @Test
    public void testVcnManagedNetworkPolicyChanged() throws Exception {
        testSetupDataNetworkVcnManaged();

        setVcnManagerPolicy(true, true);
        ArgumentCaptor<VcnNetworkPolicyChangeListener> listenerCaptor =
                ArgumentCaptor.forClass(VcnNetworkPolicyChangeListener.class);
        verify(mVcnManager).addVcnNetworkPolicyChangeListener(any(Executor.class),
                listenerCaptor.capture());

        // Trigger policy changed event
        VcnNetworkPolicyChangeListener listener = listenerCaptor.getValue();
        listener.onPolicyChanged();
        processAllMessages();

        List<DataNetwork> dataNetworks = getDataNetworks();
        assertThat(dataNetworks).hasSize(0);
    }

    @Test
    public void testDataDisableNotTearingDownUnmetered() throws Exception {
        // User data enabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, true);
        processAllMessages();

        testSetupImsDataNetwork();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        processAllMessages();

        // There shouldn't be all data disconnected event.
        verify(mMockedDataNetworkControllerCallback, never())
                .onAnyDataNetworkExistingChanged(anyBoolean());

        // Verify IMS is still alive.
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        assertThat(dataNetworkList.get(0).getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(dataNetworkList.get(0).isConnected()).isTrue();
    }

    @Test
    public void testVoPSIMSSetup() throws Exception {
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testNonVoPSNoIMSSetup() throws Exception {
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        verifyAllDataDisconnected();
    }

    @Test
    public void testVoPStoNonVoPSImsTearDown() throws Exception {
        // VoPS supported
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);

        // VOPS not supported
        dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testNonVoPStoVoPSImsSetup() throws Exception {
        // VOPS not supported
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        // VoPS supported
        dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testVoPStoNonVoPSDelayImsTearDown() throws Exception {
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_DELAY_IMS_TEAR_DOWN_UNTIL_CALL_END_BOOL,
                true);
        carrierConfigChanged();

        // VoPS supported
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);

        doReturn(PhoneConstants.State.OFFHOOK).when(mCT).getState();

        dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);

        // Make sure IMS is still connected.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Call ends
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        mDataNetworkControllerUT.obtainMessage(18/*EVENT_VOICE_CALL_ENDED*/).sendToTarget();
        processAllMessages();

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testUnmeteredMmsWhenDataDisabled() throws Exception {
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        processAllMessages();

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        // Make sure MMS is the only capability advertised, but not internet or SUPL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
    }

    @Test
    public void testUnmeteredMmsWhenRoamingDisabled() throws Exception {
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();

        // Roaming data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
        processAllMessages();

        // Device is roaming
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        processAllMessages();

        // Make sure MMS is the only capability advertised, but not internet or SUPL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
    }

    @Test
    public void testRestrictedNetworkRequestDataDisabled() throws Exception {
        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        processAllMessages();

        // Create a restricted network request.
        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        netCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);

        mDataNetworkControllerUT.addNetworkRequest(
                new TelephonyNetworkRequest(nativeNetworkRequest, mPhone));
        processAllMessages();

        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_SUPL, NetworkCapabilities.NET_CAPABILITY_MMS);

        List<DataNetwork> dataNetworks = getDataNetworks();
        // Make sure the network is restricted.
        assertThat(dataNetworks.get(0).getNetworkCapabilities()
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)).isFalse();
    }

    @Test
    public void testRestrictedNetworkRequestDataEnabled() throws Exception {
        // Create a restricted network request.
        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        netCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);

        mDataNetworkControllerUT.addNetworkRequest(
                new TelephonyNetworkRequest(nativeNetworkRequest, mPhone));
        processAllMessages();

        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_SUPL, NetworkCapabilities.NET_CAPABILITY_MMS,
                // Because data is enabled, even though the network request is restricted, the
                // network should still be not-restricted.
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
    }

    @Test
    public void testSinglePdnArbitration() throws Exception {
        // On old 1x network, only one data network is allowed.
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_1xRTT,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_DUN));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_DUN);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllFutureMessages();
        // Lower priority network should not trump the higher priority network.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_DUN);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Now send a higher priority network request
        TelephonyNetworkRequest fotaRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_FOTA);
        mDataNetworkControllerUT.addNetworkRequest(fotaRequest);
        processAllFutureMessages();
        // The existing internet data network should be torn down.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        // The higher priority emergency data network should be established.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_FOTA);

        // Now remove the fota request and tear down fota network.
        mDataNetworkControllerUT.removeNetworkRequest(fotaRequest);
        processAllMessages();
        List<DataNetwork> dataNetworks = getDataNetworks();
        dataNetworks.get(0).tearDown(DataNetwork.TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED);
        processAllMessages();

        // The tethering data network should come back since now it has the highest priority after
        // fota is gone.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_DUN);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
    }
}
