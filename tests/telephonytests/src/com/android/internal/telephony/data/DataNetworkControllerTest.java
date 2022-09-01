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
import static org.mockito.Mockito.when;

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
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.Annotation.DataFailureCause;
import android.telephony.Annotation.NetCapability;
import android.telephony.Annotation.NetworkType;
import android.telephony.CarrierConfigManager;
import android.telephony.DataFailCause;
import android.telephony.DataSpecificRegistrationInfo;
import android.telephony.LteVopsSupportInfo;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkRegistrationInfo.RegistrationState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyProtoEnums;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataCallResponse.LinkStatus;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.ThrottleStatus;
import android.telephony.data.TrafficDescriptor;
import android.telephony.data.TrafficDescriptor.OsAppId;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsRegistrationAttributes;
import android.telephony.ims.ImsStateCallback;
import android.telephony.ims.RegistrationManager.RegistrationCallback;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.telephony.ISub;
import com.android.internal.telephony.MultiSimSettingController;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RIL;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.AccessNetworksManager.AccessNetworksManagerCallback;
import com.android.internal.telephony.data.DataEvaluation.DataDisallowedReason;
import com.android.internal.telephony.data.DataNetworkController.HandoverRule;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;
import com.android.internal.telephony.data.LinkBandwidthEstimator.LinkBandwidthEstimatorCallback;
import com.android.internal.telephony.ims.ImsResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    private static final String FAKE_MMTEL_PACKAGE = "fake.mmtel.package";
    private static final String FAKE_RCS_PACKAGE = "fake.rcs.package";

    // Mocked classes
    private PhoneSwitcher mMockedPhoneSwitcher;
    protected ISub mMockedIsub;
    private DataNetworkControllerCallback mMockedDataNetworkControllerCallback;
    private DataRetryManagerCallback mMockedDataRetryManagerCallback;
    private ImsResolver mMockedImsResolver;

    private ImsManager mMockedImsManager;
    private ImsMmTelManager mMockedImsMmTelManager;
    private ImsRcsManager mMockedImsRcsManager;
    private ImsStateCallback mMmtelStateCallback;
    private ImsStateCallback mRcsStateCallback;
    private RegistrationCallback mMmtelRegCallback;
    private RegistrationCallback mRcsRegCallback;
    private SubscriptionInfo mMockSubInfo;

    private int mNetworkRequestId = 0;

    private final SparseArray<DataServiceManager> mMockedDataServiceManagers = new SparseArray<>();
    private final SparseArray<RegistrantList> mDataCallListChangedRegistrants = new SparseArray<>();
    private DataNetworkController mDataNetworkControllerUT;
    private PersistableBundle mCarrierConfig;

    private AccessNetworksManagerCallback mAccessNetworksManagerCallback;
    private LinkBandwidthEstimatorCallback mLinkBandwidthEstimatorCallback;

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

    private final DataProfile mImsCellularDataProfile = new DataProfile.Builder()
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
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT))
                    .setLingeringNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_LTE
                            | TelephonyManager.NETWORK_TYPE_BITMASK_1xRTT
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

    private final DataProfile mImsIwlanDataProfile = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2164)
                    .setOperatorNumeric("12345")
                    .setEntryName("ims_apn")
                    .setApnName("ims_apn")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                    .setProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setRoamingProtocol(ApnSetting.PROTOCOL_IPV6)
                    .setCarrierEnabled(true)
                    .setNetworkTypeBitmask((int) (TelephonyManager.NETWORK_TYPE_BITMASK_IWLAN))
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

    private final DataProfile mEnterpriseDataProfile = new DataProfile.Builder()
            .setTrafficDescriptor(new TrafficDescriptor(null,
                    new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                            "ENTERPRISE", 1).getBytes()))
            .build();

    /** Data call response map. The first key is the transport type, the second key is the cid. */
    private final Map<Integer, Map<Integer, DataCallResponse>> mDataCallResponses = new HashMap<>();

    private @NonNull DataCallResponse createDataCallResponse(int cid, @LinkStatus int linkStatus) {
        return createDataCallResponse(cid, linkStatus, Collections.emptyList());
    }

    private @NonNull DataCallResponse createDataCallResponse(int cid, @LinkStatus int linkStatus,
            @NonNull List<TrafficDescriptor> tdList) {
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
                .setTrafficDescriptors(tdList)
                .build();
    }

    private void setFailedSetupDataResponse(DataServiceManager dsm, @DataFailureCause int cause,
            long retryMillis, boolean forHandover) {
        setFailedSetupDataResponse(dsm, cause, retryMillis, forHandover, 0);
    }

    private void setFailedSetupDataResponse(DataServiceManager dsm, @DataFailureCause int cause,
            long retryMillis, boolean forHandover, long delay) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            DataCallResponse response = new DataCallResponse.Builder()
                    .setCause(cause)
                    .setRetryDurationMillis(retryMillis)
                    .setHandoverFailureMode(
                            DataCallResponse.HANDOVER_FAILURE_MODE_NO_FALLBACK_RETRY_HANDOVER)
                    .build();
            msg.getData().putParcelable("data_call_response", response);
            msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
            msg.getTarget().sendMessageDelayed(msg, delay);
            return null;
        }).when(dsm).setupDataCall(anyInt(), any(DataProfile.class), anyBoolean(),
                anyBoolean(), forHandover ? eq(DataService.REQUEST_REASON_HANDOVER)
                        : eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));
    }

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, DataCallResponse response) {
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];

            int transport = AccessNetworkConstants.TRANSPORT_TYPE_INVALID;
            if (dsm == mMockedWwanDataServiceManager) {
                transport = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
            } else if (dsm == mMockedWlanDataServiceManager) {
                transport = AccessNetworkConstants.TRANSPORT_TYPE_WLAN;
            }
            mDataCallResponses.computeIfAbsent(transport, v -> new HashMap<>());
            mDataCallResponses.get(transport).put(response.getId(), response);
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

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid) {
        setSuccessfulSetupDataResponse(dsm, cid, 0L);
    }

    private void setSuccessfulSetupDataResponse(DataServiceManager dsm, int cid, long delay) {
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
            msg.getTarget().sendMessageDelayed(msg, delay);

            final int t = transport;
            msg.getTarget().postDelayed(() -> {
                mDataCallListChangedRegistrants.get(t).notifyRegistrants(
                        new AsyncResult(t, new ArrayList<>(mDataCallResponses.get(
                                t).values()), null));

            }, delay + 100);
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

    private void setImsRegistered(boolean registered) {
        if (registered) {
            final ArraySet<String> features = new ArraySet<>();
            features.add("feature1");
            features.add("feature2");
            ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE).setFeatureTags(features).build();

            mMmtelRegCallback.onRegistered(attr);
        } else {
            ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE, -1, "");
            mMmtelRegCallback.onUnregistered(info);
        }
    }

    private void setRcsRegistered(boolean registered) {
        if (registered) {
            final ArraySet<String> features = new ArraySet<>();
            features.add("feature1");
            features.add("feature2");
            ImsRegistrationAttributes attr = new ImsRegistrationAttributes.Builder(
                    ImsRegistrationImplBase.REGISTRATION_TECH_LTE).setFeatureTags(features).build();

            mRcsRegCallback.onRegistered(attr);
        } else {
            ImsReasonInfo info = new ImsReasonInfo(ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE, -1, "");
            mRcsRegCallback.onUnregistered(info);
        }
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int regState) {
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));

        serviceStateChanged(networkType, regState, regState,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int regState, DataSpecificRegistrationInfo dsri) {
        serviceStateChanged(networkType, regState, regState,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, dsri);
    }

    private void serviceStateChanged(@NetworkType int networkType,
            @RegistrationState int dataRegState, @RegistrationState int voiceRegState,
            @RegistrationState int iwlanRegState, DataSpecificRegistrationInfo dsri) {
        if (dsri == null) {
            dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                    new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                            LteVopsSupportInfo.LTE_STATUS_SUPPORTED));
        }

        ServiceState ss = new ServiceState();

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(dataRegState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .setDataSpecificInfo(dsri)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(iwlanRegState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(networkType)
                .setRegistrationState(voiceRegState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        ss.setDataRoamingFromRegistration(dataRegState
                == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        processServiceStateRegStateForTest(ss);

        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();
    }

    // set SS reg state base on SST impl, where WLAN overrides WWAN's data reg.
    private void processServiceStateRegStateForTest(ServiceState ss) {
        int wlanRegState = ss.getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN).getRegistrationState();
        if (wlanRegState == NetworkRegistrationInfo.REGISTRATION_STATE_HOME) {
            ss.setDataRegState(ServiceState.STATE_IN_SERVICE);
        } else {
            int cellularRegState = ss.getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_PS,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN).getRegistrationState();
            int dataState = (cellularRegState == NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                    || cellularRegState == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                    ? ServiceState.STATE_IN_SERVICE : ServiceState.STATE_OUT_OF_SERVICE;
            ss.setDataRegState(dataState);
        }
        int voiceRegState = ss.getNetworkRegistrationInfo(NetworkRegistrationInfo.DOMAIN_CS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN).getRegistrationState();
        int voiceState = (voiceRegState == NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                || voiceRegState == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING)
                ? ServiceState.STATE_IN_SERVICE : ServiceState.STATE_OUT_OF_SERVICE;
        ss.setVoiceRegState(voiceState);
    }

    private void updateTransport(@NetCapability int capability, @TransportType int transport) {
        doReturn(transport).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(capability);
        mAccessNetworksManagerCallback.onPreferredTransportChanged(capability);
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
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_TELEPHONY_DATA_HANDOVER_RETRY_RULES_STRING_ARRAY,
                new String[] {"retry_interval=1000|2000|4000|8000|16000, maximum_retries=5"
                });

        mCarrierConfig.putInt(CarrierConfigManager.KEY_NR_ADVANCED_CAPABLE_PCO_ID_INT, 1234);

        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_NETWORK_TEMP_NOT_METERED_SUPPORTED_BOOL,
                true);

        mCarrierConfig.putIntArray(CarrierConfigManager.KEY_ONLY_SINGLE_DC_ALLOWED_INT_ARRAY,
                new int[]{TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_EVDO_B});

        mCarrierConfig.putIntArray(CarrierConfigManager
                        .KEY_CAPABILITIES_EXEMPT_FROM_SINGLE_DC_CHECK_INT_ARRAY,
                new int[]{NetworkCapabilities.NET_CAPABILITY_IMS});

        mContextFixture.putResource(com.android.internal.R.string.config_bandwidthEstimateSource,
                "bandwidth_estimator");

        mContextFixture.putIntResource(com.android.internal.R.integer
                        .config_delay_for_ims_dereg_millis, 3000);
        mContextFixture.putBooleanResource(com.android.internal.R.bool
                .config_enable_iwlan_handover_policy, true);
        mContextFixture.putBooleanResource(com.android.internal.R.bool
                .config_enhanced_iwlan_handover_check, true);
    }

    @Before
    public void setUp() throws Exception {
        logd("DataNetworkControllerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mMockedPhoneSwitcher = Mockito.mock(PhoneSwitcher.class);
        mMockedIsub = Mockito.mock(ISub.class);
        mMockedImsManager = mContext.getSystemService(ImsManager.class);
        mMockedImsMmTelManager = Mockito.mock(ImsMmTelManager.class);
        mMockedImsRcsManager = Mockito.mock(ImsRcsManager.class);
        mMockedImsResolver = Mockito.mock(ImsResolver.class);
        mMockedDataNetworkControllerCallback = Mockito.mock(DataNetworkControllerCallback.class);
        mMockedDataRetryManagerCallback = Mockito.mock(DataRetryManagerCallback.class);
        mMockSubInfo = Mockito.mock(SubscriptionInfo.class);
        when(mTelephonyComponentFactory.makeDataSettingsManager(any(Phone.class),
                any(DataNetworkController.class), any(Looper.class),
                any(DataSettingsManager.DataSettingsManagerCallback.class))).thenCallRealMethod();
        doReturn(mMockedImsMmTelManager).when(mMockedImsManager).getImsMmTelManager(anyInt());
        doReturn(mMockedImsRcsManager).when(mMockedImsManager).getImsRcsManager(anyInt());

        initializeConfig();
        mMockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mMockedWwanDataServiceManager);
        mMockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mMockedWlanDataServiceManager);

        replaceInstance(PhoneSwitcher.class, "sPhoneSwitcher", null, mMockedPhoneSwitcher);
        doReturn(1).when(mMockedIsub).getDefaultDataSubId();
        doReturn(mMockedIsub).when(mIBinder).queryLocalInterface(anyString());
        doReturn(mPhone).when(mPhone).getImsPhone();
        mServiceManagerMockedServices.put("isub", mIBinder);
        doReturn(new SubscriptionPlan[]{}).when(mNetworkPolicyManager)
                .getSubscriptionPlans(anyInt(), any());
        doReturn(true).when(mSST).getDesiredPowerState();
        doReturn(true).when(mSST).getPowerStateFromCarrier();
        doReturn(true).when(mSST).isConcurrentVoiceAndDataAllowed();
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        doReturn("").when(mSubscriptionController).getDataEnabledOverrideRules(anyInt());
        doReturn(true).when(mSubscriptionController).setDataEnabledOverrideRules(
                anyInt(), anyString());

        List<SubscriptionInfo> infoList = new ArrayList<>();
        infoList.add(mMockSubInfo);
        doReturn(infoList).when(mSubscriptionController).getSubscriptionsInGroup(
                any(), any(), any());
        doReturn(true).when(mSubscriptionController).isActiveSubId(anyInt());
        doReturn(0).when(mSubscriptionController).getPhoneId(anyInt());

        for (int transport : new int[]{AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN}) {
            mDataCallListChangedRegistrants.put(transport, new RegistrantList());
            setSuccessfulSetupDataResponse(mMockedDataServiceManagers.get(transport), 1);
            doAnswer(invocation -> {
                int cid = (int) invocation.getArguments()[0];
                Message msg = (Message) invocation.getArguments()[2];
                msg.sendToTarget();
                mDataCallResponses.get(transport).remove(cid);
                mDataCallListChangedRegistrants.get(transport).notifyRegistrants(
                        new AsyncResult(transport, new ArrayList<>(mDataCallResponses.get(
                                transport).values()), null));
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).deactivateDataCall(
                    anyInt(), anyInt(), any(Message.class));

            doAnswer(invocation -> {
                Handler h = (Handler) invocation.getArguments()[0];
                int what = (int) invocation.getArguments()[1];
                mDataCallListChangedRegistrants.get(transport).addUnique(h, what, transport);
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).registerForDataCallListChanged(any(
                    Handler.class), anyInt());

            doAnswer(invocation -> {
                Message msg = (Message) invocation.getArguments()[1];
                msg.sendToTarget();
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).startHandover(anyInt(),
                    any(Message.class));

            doAnswer(invocation -> {
                Message msg = (Message) invocation.getArguments()[1];
                msg.sendToTarget();
                return null;
            }).when(mMockedDataServiceManagers.get(transport)).cancelHandover(anyInt(),
                    any(Message.class));
        }

        doReturn(-1).when(mPhone).getSubId();

        // Note that creating a "real" data network controller will also result in creating
        // real DataRetryManager, DataConfigManager, etc...Normally in unit test we should isolate
        // other modules and make them mocked, but only focusing on testing the unit we would like
        // to test, in this case, DataNetworkController. But since there are too many interactions
        // between DataNetworkController and its sub-modules, we intend to make those modules "real"
        // as well, except some modules below we replaced with mocks.
        mDataNetworkControllerUT = new DataNetworkController(mPhone, Looper.myLooper());
        doReturn(mDataNetworkControllerUT).when(mPhone).getDataNetworkController();

        doReturn(1).when(mPhone).getSubId();
        mDataNetworkControllerUT.obtainMessage(15/*EVENT_SUBSCRIPTION_CHANGED*/).sendToTarget();

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
        replaceInstance(ImsResolver.class, "sInstance", null, mMockedImsResolver);

        ArgumentCaptor<AccessNetworksManagerCallback> callbackCaptor =
                ArgumentCaptor.forClass(AccessNetworksManagerCallback.class);
        verify(mAccessNetworksManager).registerCallback(callbackCaptor.capture());
        mAccessNetworksManagerCallback = callbackCaptor.getValue();

        ArgumentCaptor<LinkBandwidthEstimatorCallback> linkBandwidthEstimatorCallbackCaptor =
                ArgumentCaptor.forClass(LinkBandwidthEstimatorCallback.class);
        verify(mLinkBandwidthEstimator).registerCallback(
                linkBandwidthEstimatorCallbackCaptor.capture());
        mLinkBandwidthEstimatorCallback = linkBandwidthEstimatorCallbackCaptor.getValue();

        List<DataProfile> profiles = List.of(mGeneralPurposeDataProfile,
                mImsCellularDataProfile,
                mImsIwlanDataProfile, mEmergencyDataProfile, mFotaDataProfile,
                mTetheringDataProfile);

        doAnswer(invocation -> {
            DataProfile dp = (DataProfile) invocation.getArguments()[0];

            if (dp.getApnSetting() == null) return true;

            for (DataProfile dataProfile : profiles) {
                if (dataProfile.getApnSetting() != null
                        && dataProfile.getApnSetting().equals(dp.getApnSetting(), false)) {
                    return true;
                }
            }
            return null;
        }).when(mDataProfileManager).isDataProfileCompatible(any(DataProfile.class));

        doAnswer(invocation -> {
            TelephonyNetworkRequest networkRequest =
                    (TelephonyNetworkRequest) invocation.getArguments()[0];
            int networkType = (int) invocation.getArguments()[1];

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

        ArgumentCaptor<ImsStateCallback> imsCallbackCaptor =
                ArgumentCaptor.forClass(ImsStateCallback.class);
        verify(mMockedImsMmTelManager).registerImsStateCallback(any(Executor.class),
                imsCallbackCaptor.capture());
        mMmtelStateCallback = imsCallbackCaptor.getValue();

        verify(mMockedImsRcsManager).registerImsStateCallback(any(Executor.class),
                imsCallbackCaptor.capture());
        mRcsStateCallback = imsCallbackCaptor.getValue();

        carrierConfigChanged();

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);

        // IMS registration
        doReturn(FAKE_MMTEL_PACKAGE).when(mMockedImsResolver).getConfiguredImsServicePackageName(
                anyInt(), eq(ImsFeature.FEATURE_MMTEL));
        doReturn(FAKE_RCS_PACKAGE).when(mMockedImsResolver).getConfiguredImsServicePackageName(
                anyInt(), eq(ImsFeature.FEATURE_RCS));

        mMmtelStateCallback.onAvailable();
        mRcsStateCallback.onAvailable();

        ArgumentCaptor<RegistrationCallback> regCallbackCaptor =
                ArgumentCaptor.forClass(RegistrationCallback.class);

        verify(mMockedImsMmTelManager).registerImsRegistrationCallback(any(Executor.class),
                regCallbackCaptor.capture());
        mMmtelRegCallback = regCallbackCaptor.getValue();

        verify(mMockedImsRcsManager).registerImsRegistrationCallback(any(Executor.class),
                regCallbackCaptor.capture());
        mRcsRegCallback = regCallbackCaptor.getValue();

        processAllMessages();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        logd("DataNetworkControllerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        mMockedDataServiceManagers.clear();
        mDataCallListChangedRegistrants.clear();
        mDataNetworkControllerUT = null;
        mCarrierConfig = null;
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
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
        verifyConnectedNetworkHasDataProfile(mImsCellularDataProfile);
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList.get(0).getLinkProperties().getAddresses()).containsExactly(
                InetAddresses.parseNumericAddress(IPV4_ADDRESS),
                InetAddresses.parseNumericAddress(IPV6_ADDRESS));
    }

    @Test
    public void testSetupEnterpriseDataNetwork() throws Exception {
        List<TrafficDescriptor> tdList = new ArrayList<>();
        tdList.add(new TrafficDescriptor.Builder()
                .setOsAppId(new OsAppId(OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1).getBytes())
                .build());
        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager,
                createDataCallResponse(1, DataCallResponse.LINK_STATUS_ACTIVE, tdList));
        doReturn(mEnterpriseDataProfile).when(mDataProfileManager)
                .getDataProfileForNetworkRequest(any(TelephonyNetworkRequest.class), anyInt());

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE);
        List<DataNetwork> dataNetworkList = getDataNetworks();
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
    public void testDuplicateInterface() throws Exception {
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // The fota network request would result in duplicate interface.
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_FOTA));
        processAllFutureMessages();

        // There should be only one network.
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        assertThat(dataNetworkList.get(0).getDataProfile()).isEqualTo(mGeneralPurposeDataProfile);
        verifyInternetConnected();
        // Fota should not be connected.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);

        // There should be exactly 2 setup data call requests.
        verify(mMockedWwanDataServiceManager, times(2)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
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

        // PS restricted, existing PDN should stay.
        mDataNetworkControllerUT.obtainMessage(6/*EVENT_PS_RESTRICT_ENABLED*/).sendToTarget();
        processAllMessages();

        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // PS restricted, new setup NOT allowed
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 2);
        processAllMessages();
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);


        // PS unrestricted, new setup is allowed
        mDataNetworkControllerUT.obtainMessage(7/*EVENT_PS_RESTRICT_DISABLED*/).sendToTarget();
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testPsRestrictedAllowIwlan() throws Exception {
        // IMS preferred on IWLAN.
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(
                        eq(NetworkCapabilities.NET_CAPABILITY_IMS));

        // PS restricted
        mDataNetworkControllerUT.obtainMessage(6/*EVENT_PS_RESTRICT_ENABLED*/).sendToTarget();
        processAllMessages();

        // PS restricted, new setup NOT allowed
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 2);
        processAllMessages();
        verifyAllDataDisconnected();

        // Request IMS
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WLAN), 3);
        processAllMessages();

        // Make sure IMS on IWLAN.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(getDataNetworks()).hasSize(1);
        DataNetwork dataNetwork = getDataNetworks().get(0);
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
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
    public void testRatChangesLingeringNotSet() throws Exception {
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        TelephonyNetworkRequest fotaRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_FOTA);
        mDataNetworkControllerUT.addNetworkRequest(fotaRequest);
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_FOTA);

        // Now RAT changes from LTE to UMTS, since FOTA APN does not have lingering set, only
        // network type bitmask should be used. Fota network should be torn down.
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UMTS,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllMessages();

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
        verifyAllDataDisconnected();
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

        // It should have no internet setup at the beginning.
        verifyAllDataDisconnected();

        // But after some delays data should be restored.
        moveTimeForward(500);
        processAllMessages();
        verifyInternetConnected();
    }

    @Test
    public void testEcbmChanged() throws Exception {
        doReturn(true).when(mPhone).isInCdmaEcm();
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when the device is in ECBM.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        // Exit ECBM
        doReturn(false).when(mPhone).isInCdmaEcm();
        mDataNetworkControllerUT.obtainMessage(20/*EVENT_EMERGENCY_CALL_CHANGED*/).sendToTarget();
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
    }

    @Test
    public void testRoamingDataChanged() throws Exception {
        doReturn(true).when(mServiceState).getDataRoaming();

        // Roaming data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
        processAllMessages();

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
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Data should not be allowed when user data is disabled.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data enabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, true, mContext.getOpPackageName());
        processAllMessages();

        // Verify data is restored.
        verifyInternetConnected();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        processAllMessages();

        // Verify data is torn down.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Test
    public void testNotifyWhenSetDataEnabled() throws Exception {
        // Set a valid sub id, DEFAULT_SUBSCRIPTION_ID
        int subId = Integer.MAX_VALUE;
        Field field = DataSettingsManager.class.getDeclaredField("mSubId");
        field.setAccessible(true);
        field.setInt(mDataNetworkControllerUT.getDataSettingsManager(), subId);
        boolean isDataEnabled = mDataNetworkControllerUT.getDataSettingsManager().isDataEnabled();
        doReturn(mDataNetworkControllerUT.getDataSettingsManager())
                .when(mPhone).getDataSettingsManager();
        MultiSimSettingController instance = MultiSimSettingController.getInstance();
        MultiSimSettingController controller = Mockito.spy(
                new MultiSimSettingController(mContext, mSubscriptionController));
        doReturn(true).when(controller).isCarrierConfigLoadedForAllSub();
        replaceInstance(MultiSimSettingController.class, "sInstance", null, controller);

        controller.notifyAllSubscriptionLoaded();
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, !isDataEnabled,
                mContext.getOpPackageName());
        processAllMessages();

        // Verify not to notify MultiSimSettingController
        verify(controller, never()).notifyUserDataEnabled(anyInt(), anyBoolean());

        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, isDataEnabled,
                mContext.getOpPackageName());
        processAllMessages();

        // Verify not to notify MultiSimSettingController
        verify(controller, never()).notifyUserDataEnabled(anyInt(), anyBoolean());

        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, !isDataEnabled, "com.android.settings");
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, isDataEnabled, "com.android.settings");
        processAllMessages();

        // Verify to notify MultiSimSettingController exactly 2 times
        verify(controller, times(2)).notifyUserDataEnabled(anyInt(), anyBoolean());
    }

    @Test
    public void testMmsAlwaysAllowedDataDisabled() throws Exception {
        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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

        // Remove MMS data enabled override
        mDataNetworkControllerUT.getDataSettingsManager().setAlwaysAllowMmsData(false);
        processAllMessages();

        // Make sure MMS is torn down when the override is disabled.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
    }

    @Test
    public void testMmsAlwaysAllowedRoamingDisabled() throws Exception {
        // Data roaming disabled
        doReturn(true).when(mServiceState).getDataRoaming();
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
        processAllMessages();

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
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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
        mDataNetworkControllerUT.obtainMessage(21 /*EVENT_EVALUATE_PREFERRED_TRANSPORT*/,
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
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
        processAllMessages();

        // MMS is unmetered
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_ROAMING_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();
        // Manually set data roaming to false in case ro.com.android.dataroaming is true.
        // TODO(b/232575718): Figure out a way to mock ro.com.android.dataroaming for tests.
        mDataNetworkControllerUT.getDataSettingsManager().setDataRoamingEnabled(false);
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
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());

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
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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
        assertThat(handoverRule.networkCapabilities).isEmpty();

        handoverRule = new HandoverRule("source=   NGRAN|     IWLAN, "
                + "target  =    EUTRAN,    type  =    disallowed ");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.NGRAN,
                AccessNetworkType.IWLAN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.EUTRAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_DISALLOWED);
        assertThat(handoverRule.isOnlyForRoaming).isFalse();
        assertThat(handoverRule.networkCapabilities).isEmpty();

        handoverRule = new HandoverRule("source=   IWLAN, "
                + "target  =    EUTRAN,    type  =    disallowed, roaming = true,"
                + " capabilities = IMS | EIMS ");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.IWLAN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.EUTRAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_DISALLOWED);
        assertThat(handoverRule.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_IMS, NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(handoverRule.isOnlyForRoaming).isTrue();

        handoverRule = new HandoverRule("source=EUTRAN|NGRAN|IWLAN|UNKNOWN, "
                + "target=EUTRAN|NGRAN|IWLAN, type=disallowed, capabilities = IMS|EIMS");
        assertThat(handoverRule.sourceAccessNetworks).containsExactly(AccessNetworkType.EUTRAN,
                AccessNetworkType.NGRAN, AccessNetworkType.IWLAN, AccessNetworkType.UNKNOWN);
        assertThat(handoverRule.targetAccessNetworks).containsExactly(AccessNetworkType.EUTRAN,
                AccessNetworkType.NGRAN, AccessNetworkType.IWLAN);
        assertThat(handoverRule.type).isEqualTo(HandoverRule.RULE_TYPE_DISALLOWED);
        assertThat(handoverRule.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_IMS, NetworkCapabilities.NET_CAPABILITY_EIMS);

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("V2hhdCBUaGUgRnVjayBpcyB0aGlzIQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("target=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN|UTRAN|EUTRAN|NGRAN|IWLAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=UNKNOWN, type=disallowed, "
                        + "capabilities=IMS"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=UNKNOWN, target=IWLAN, type=allowed, "
                        + "capabilities=IMS"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=IWLAN, type=wtf"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=NGRAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=IWLAN, target=WTFRAN, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=IWLAN, target=|, type=allowed"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=IWLAN, type=allowed, capabilities=|"));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=IWLAN, type=allowed, capabilities="));

        assertThrows(IllegalArgumentException.class,
                () -> new HandoverRule("source=GERAN, target=IWLAN, type=allowed, "
                        + "capabilities=wtf"));
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
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_NR,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
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
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isFalse();

        // Change data network type to NR
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_NR,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        dataNetwork.sendMessage(13/*EVENT_DISPLAY_INFO_CHANGED*/);
        processAllMessages();
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isTrue();

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
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isFalse();
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
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isFalse();


        // Change data network type to NR
        doReturn(new TelephonyDisplayInfo(TelephonyManager.NETWORK_TYPE_NR,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE))
                .when(mDisplayInfoController).getTelephonyDisplayInfo();
        dataNetwork.sendMessage(13/*EVENT_DISPLAY_INFO_CHANGED*/);
        processAllMessages();
        assertTrue(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isTrue();

        // Set all network types metered
        subscriptionPlans.clear();
        mDataNetworkControllerUT.obtainMessage(22/*EVENT_SUBSCRIPTION_PLANS_CHANGED*/,
                new SubscriptionPlan[]{}).sendToTarget();
        dataNetwork.sendMessage(16/*EVENT_SUBSCRIPTION_PLAN_OVERRIDE*/);
        processAllMessages();
        assertTrue(mDataNetworkControllerUT.getSubscriptionPlans().isEmpty());
        assertFalse(dataNetwork.getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED));
        assertThat(mDataNetworkControllerUT.isInternetUnmetered()).isFalse();
    }

    @Test
    public void testOnSinglePdnArbitrationExemptIms() throws Exception {
        // On CDMA network, only one data network is allowed.
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_1xRTT,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        // Preferred on cellular
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(anyInt());
        // Add IMS
        TelephonyNetworkRequest ims = createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
        mDataNetworkControllerUT.addNetworkRequest(ims);
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Add internet, should be compatible
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 2);
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Both internet and IMS should be retained after network re-evaluation
        mDataNetworkControllerUT.obtainMessage(16/*EVENT_REEVALUATE_EXISTING_DATA_NETWORKS*/)
                .sendToTarget();
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Add MMS, whose priority > internet, internet should be town down, IMS left untouched
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_MMS));
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 3);
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Both internet and IMS should be retained after network re-evaluation
        mDataNetworkControllerUT.obtainMessage(16/*EVENT_REEVALUATE_EXISTING_DATA_NETWORKS*/)
                .sendToTarget();
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Temporarily remove IMS
        mDataNetworkControllerUT.removeNetworkRequest(ims);
        processAllMessages();
        List<DataNetwork> dataNetworks = getDataNetworks();
        dataNetworks.get(0).tearDown(DataNetwork.TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED);
        processAllMessages();

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        // Add IMS, should be compatible with the existing internet
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 4);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
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
        assertThat(mDataNetworkControllerUT.getDataActivity()).isEqualTo(
                TelephonyManager.DATA_ACTIVITY_DORMANT);
    }

    @Test
    public void testHandoverDataNetwork() throws Exception {
        testSetupImsDataNetwork();

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Before handover the data profile is the cellular IMS data profile
        verifyConnectedNetworkHasDataProfile(mImsCellularDataProfile);

        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Verify that IWLAN handover succeeded.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // After handover the data profile is the IWLAN IMS data profile
        verifyConnectedNetworkHasDataProfile(mImsIwlanDataProfile);
    }

    @Test
    public void testHandoverDataNetworkBackToBackPreferenceChanged() throws Exception {
        testSetupImsDataNetwork();

        Mockito.reset(mMockedWlanDataServiceManager);
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Capture the message for setup data call response. We want to delay it.
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockedWlanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                messageCaptor.capture());

        // Before setup data call response, change the preference back to cellular.
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);


        // Before setup data call response, change the preference back to IWLAN.
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Finally handover is completed.
        Message msg = messageCaptor.getValue();
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(DataFailCause.NONE)
                .build();
        msg.getData().putParcelable("data_call_response", response);
        msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
        msg.sendToTarget();
        processAllMessages();

        // Make sure handover request is only sent once.
        verify(mMockedWlanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), messageCaptor.capture());
    }

    @Test
    public void testHandoverDataNetworkNotAllowedByPolicy() throws Exception {
        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY,
                new String[]{"source=EUTRAN, target=IWLAN, type=disallowed, capabilities=MMS|IMS",
                        "source=IWLAN, target=EUTRAN, type=disallowed, capabilities=MMS"});
        // Force data config manager to reload the carrier config.
        mDataNetworkControllerUT.getDataConfigManager().obtainMessage(
                1/*EVENT_CARRIER_CONFIG_CHANGED*/).sendToTarget();
        processAllMessages();

        testSetupImsDataNetwork();

        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        // After this, IMS data network should be disconnected, and DNC should attempt to
        // establish a new one on IWLAN

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

        // test IWLAN -> EUTRAN no need to tear down because the disallowed rule only applies to MMS
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.obtainMessage(21/*EVENT_PREFERRED_TRANSPORT_CHANGED*/,
                NetworkCapabilities.NET_CAPABILITY_IMS, 0).sendToTarget();
        Mockito.clearInvocations(mMockedWwanDataServiceManager);
        processAllMessages();
        // Verify that IWWAN handover succeeded.
        assertThat(getDataNetworks().get(0).getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(
                anyInt(), any(), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_HANDOVER), any(), anyInt(), any(), any(), eq(true),
                any());
    }

    @Test
    public void testHandoverDataNetworkNotAllowedByRoamingPolicy() throws Exception {
        mCarrierConfig.putStringArray(CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY,
                new String[]{"source=EUTRAN|NGRAN|IWLAN, target=EUTRAN|NGRAN|IWLAN, roaming=true, "
                        + "type=disallowed, capabilities=IMS"});
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);
        // Force data config manager to reload the carrier config.
        mDataNetworkControllerUT.getDataConfigManager().obtainMessage(
                1/*EVENT_CARRIER_CONFIG_CHANGED*/).sendToTarget();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        processAllMessages();

        // Bring up IMS PDN on IWLAN
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();
        verifyConnectedNetworkHasDataProfile(mImsIwlanDataProfile);

        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Verify IMS PDN is connected.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // After this, IMS data network should be disconnected, and DNC should attempt to
        // establish a new one on cellular
        processAllMessages();

        // Verify all data disconnected.
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));

        // Should setup a new one instead of handover.
        verify(mMockedWwanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));


        // A new data network should be connected on IWLAN
        List<DataNetwork> dataNetworkList = getDataNetworks();
        assertThat(dataNetworkList).hasSize(1);
        assertThat(dataNetworkList.get(0).isConnected()).isTrue();
        assertThat(dataNetworkList.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_IMS)).isTrue();
        assertThat(dataNetworkList.get(0).getTransport())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testHandoverDataNetworkRetry() throws Exception {
        testSetupImsDataNetwork();

        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataFailCause.HANDOVER_FAILED, -1, true);
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify that data network is still on cellular
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 1);

        processAllFutureMessages();

        dataNetwork = getDataNetworks().get(0);
        // Verify that data network is handovered to IWLAN
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testHandoverDataNetworkDuplicateRetry() throws Exception {
        testSetupImsDataNetwork();
        DataNetwork dataNetwork = getDataNetworks().get(0);
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .when(mAccessNetworksManager).getPreferredTransportByNetworkCapability(anyInt());

        DataRetryManager.DataHandoverRetryEntry retry1 =
                new DataRetryManager.DataHandoverRetryEntry.Builder<>()
                        .setDataNetwork(dataNetwork)
                        .build();
        DataRetryManager.DataHandoverRetryEntry retry2 =
                new DataRetryManager.DataHandoverRetryEntry.Builder<>()
                        .setDataNetwork(dataNetwork)
                        .build();
        final Message msg1 = new Message();
        msg1.what = 4 /*EVENT_DATA_HANDOVER_RETRY*/;
        msg1.obj = retry1;

        final Message msg2 = new Message();
        msg2.what = 4 /*EVENT_DATA_HANDOVER_RETRY*/;
        msg2.obj = retry2;

        Field field = DataRetryManager.class.getDeclaredField("mDataRetryEntries");
        field.setAccessible(true);
        List<DataRetryManager.DataRetryEntry> dataRetryEntries =
                (List<DataRetryManager.DataRetryEntry>)
                        field.get(mDataNetworkControllerUT.getDataRetryManager());
        dataRetryEntries.add(retry1);
        dataRetryEntries.add(retry2);

        mDataNetworkControllerUT.getDataRetryManager().sendMessageDelayed(msg1, 0);
        mDataNetworkControllerUT.getDataRetryManager().sendMessageDelayed(msg2, 0);

        processAllFutureMessages();

        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 1);
        processAllMessages();

        dataNetwork = getDataNetworks().get(0);
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        verify(mMockedWlanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), eq(DataService.REQUEST_REASON_HANDOVER), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));
        assertThat(mDataNetworkControllerUT.getDataRetryManager()
                .isAnyHandoverRetryScheduled(dataNetwork)).isFalse();
    }

    @Test
    public void testHandoverDataNetworkRetryReachedMaximum() throws Exception {
        testSetupImsDataNetwork();

        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataFailCause.HANDOVER_FAILED, -1, true);
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        processAllFutureMessages();

        // Should retried 5 times, which is the maximum based on the retry config rules.
        verify(mMockedWlanDataServiceManager, times(6)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_HANDOVER), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify that data network is finally setup on IWLAN.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        verify(mMockedWlanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testHandoverDataNetworkRetryReachedMaximumNetworkRequestRemoved() throws Exception {
        TelephonyNetworkRequest networkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.addNetworkRequest(networkRequest);
        processAllMessages();

        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataFailCause.HANDOVER_FAILED, -1, true);
        mDataNetworkControllerUT.removeNetworkRequest(networkRequest);
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        processAllMessages();

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify that data network should remain on cellular.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // There shouldn't be any attempt to retry handover on IWLAN.
        verify(mMockedWlanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_HANDOVER), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));

        // There shouldn't be any attempt to bring up a new one on IWLAN as well.
        verify(mMockedWlanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));
    }

    @Test
    public void testHandoverDataNetworkRetryReachedMaximumDelayImsTearDown() throws Exception {
        // Voice call is ongoing
        doReturn(PhoneConstants.State.OFFHOOK).when(mCT).getState();
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_DELAY_IMS_TEAR_DOWN_UNTIL_CALL_END_BOOL,
                true);
        carrierConfigChanged();

        testSetupImsDataNetwork();

        setFailedSetupDataResponse(mMockedWlanDataServiceManager,
                DataFailCause.HANDOVER_FAILED, -1, true);
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        processAllFutureMessages();

        // Should retried 5 times, which is the maximum based on the retry config rules.
        verify(mMockedWlanDataServiceManager, times(6)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_HANDOVER), any(), anyInt(), any(), any(),
                anyBoolean(), any(Message.class));

        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify that data network is still on WWAN because voice call is still ongoing.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

    }

    // Test the device enters from 4G to 3G, and QNS switches the pref just before that happens.
    // Make sure we don't tear down the network and let it handover to IWLAN successfully.
    @Test
    public void testHandoverDataNetworkWhileSwitchTo3G() throws Exception {
        testSetupImsDataNetwork();

        // Before handover the data profile is the cellular IMS data profile
        verifyConnectedNetworkHasDataProfile(mImsCellularDataProfile);

        // Long delay handover
        setSuccessfulSetupDataResponse(mMockedWlanDataServiceManager, 1, 3000);
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mAccessNetworksManagerCallback.onPreferredTransportChanged(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UMTS,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        processAllMessages();

        // Move the time a little bit, handover still not responded.
        moveTimeForward(500);
        processAllMessages();
        DataNetwork dataNetwork = getDataNetworks().get(0);
        // Verify the network is still on cellular, waiting for handover, although already on 3G.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Now handover should complete.
        moveTimeForward(5000);
        processAllMessages();

        dataNetwork = getDataNetworks().get(0);
        // Verify that IWLAN handover succeeded.
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // After handover the data profile is the IWLAN IMS data profile
        verifyConnectedNetworkHasDataProfile(mImsIwlanDataProfile);
    }

    @Test
    public void testHandoverDataNetworkFailedNullResponse() throws Exception {
        testSetupImsDataNetwork();
        DataNetwork dataNetwork = getDataNetworks().get(0);

        // Set failed null response
        doAnswer(invocation -> {
            final Message msg = (Message) invocation.getArguments()[10];
            msg.getData().putParcelable("data_call_response", null);
            msg.arg1 = DataServiceCallback.RESULT_ERROR_TEMPORARILY_UNAVAILABLE;
            msg.getTarget().sendMessageDelayed(msg, 0);
            return null;
        }).when(mMockedWlanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), eq(DataService.REQUEST_REASON_HANDOVER), any(),
                anyInt(), any(), any(), anyBoolean(), any(Message.class));

        // Attempt handover
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        processAllMessages();

        // Verify that data network is still on cellular and data network was not torn down
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(dataNetwork.isConnected()).isTrue();

        // Process all handover retries and failures
        processAllFutureMessages();

        // Verify that original data network was torn down and new connection set up on cellular
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertThat(dataNetwork.isConnected()).isFalse();
        dataNetwork = getDataNetworks().get(0);
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertThat(dataNetwork.isConnected()).isTrue();
    }

    @Test
    public void testSetupDataNetworkRetrySuggestedByNetwork() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED, false);
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
                DataCallResponse.RETRY_DURATION_UNDEFINED, false);
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
    public void testSetupDataNetworkRetryFailedNetworkRequestRemoved() {
        mDataNetworkControllerUT.getDataRetryManager()
                .registerCallback(mMockedDataRetryManagerCallback);
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED, false);
        TelephonyNetworkRequest tnr = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mDataNetworkControllerUT.addNetworkRequest(tnr);

        processAllMessages();

        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        logd("Remove internet network request");
        mDataNetworkControllerUT.removeNetworkRequest(tnr);

        moveTimeForward(2500);
        processAllMessages();

        // There should be no retry since request has been removed.
        verify(mMockedWwanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        // Now send another IMS request
        tnr = createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.addNetworkRequest(tnr);
        processAllMessages();

        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
        Mockito.clearInvocations(mMockedWwanDataServiceManager);

        logd("Remove IMS network request");
        mDataNetworkControllerUT.removeNetworkRequest(tnr);

        // There should be no retry since request has been removed.
        verify(mMockedWwanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testSetupDataNetworkPermanentFailure() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.PROTOCOL_ERRORS,
                DataCallResponse.RETRY_DURATION_UNDEFINED, false);
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
                Long.MAX_VALUE, false);
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
                10000, false);
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
    public void testTacChangesClearThrottlingAndRetryHappens() throws Exception {
        testSetupDataNetworkNetworkSuggestedRetryTimerDataThrottled();
        processAllFutureMessages();

        setSuccessfulSetupDataResponse(mMockedWwanDataServiceManager, 1);
        logd("Sending TAC_CHANGED event");
        mDataNetworkControllerUT.obtainMessage(25/*EVENT_TAC_CHANGED*/).sendToTarget();
        mDataNetworkControllerUT.getDataRetryManager().obtainMessage(10/*EVENT_TAC_CHANGED*/)
                .sendToTarget();
        processAllFutureMessages();

        // TAC changes should clear the already-scheduled retry and throttling.
        assertThat(mDataNetworkControllerUT.getDataRetryManager().isAnySetupRetryScheduled(
                mImsCellularDataProfile, AccessNetworkConstants.TRANSPORT_TYPE_WWAN)).isFalse();

        // But DNC should re-evaluate unsatisfied request and setup IMS again.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
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
    public void testNrAdvancedByEarlyPco() {
        Mockito.reset(mMockedWwanDataServiceManager);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // PCO data arrives before data network entering connected state.
        mSimulatedCommands.triggerPcoData(1, "IPV6", 1234, new byte[]{1});
        processAllMessages();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockedWwanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                messageCaptor.capture());

        // Send setup data call complete message.
        Message msg = messageCaptor.getValue();
        msg.getData().putParcelable("data_call_response",
                createDataCallResponse(1, DataCallResponse.LINK_STATUS_ACTIVE));
        msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
        msg.sendToTarget();
        processAllMessages();

        verify(mMockedDataNetworkControllerCallback).onNrAdvancedCapableByPcoChanged(eq(true));
    }

    @Test
    public void testNrAdvancedByPcoMultipleNetworks() throws Exception {
        testSetupDataNetwork();
        setSuccessfulSetupDataResponse(mMockedDataServiceManagers
                .get(AccessNetworkConstants.TRANSPORT_TYPE_WWAN), 2);
        testSetupImsDataNetwork();

        verify(mMockedDataNetworkControllerCallback, never())
                .onNrAdvancedCapableByPcoChanged(anyBoolean());
        mSimulatedCommands.triggerPcoData(2, "IPV6", 1234, new byte[]{1});
        processAllMessages();
        verify(mMockedDataNetworkControllerCallback).onNrAdvancedCapableByPcoChanged(eq(true));
    }

    @Test
    public void testNrAdvancedByEarlyUnrelatedPco() {
        Mockito.reset(mMockedWwanDataServiceManager);
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        // Unrelated PCO data arrives before data network entering connected state.
        mSimulatedCommands.triggerPcoData(2, "IPV6", 1234, new byte[]{1});
        processAllMessages();

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockedWwanDataServiceManager).setupDataCall(anyInt(), any(DataProfile.class),
                anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(), any(), any(), anyBoolean(),
                messageCaptor.capture());

        // Send setup data call complete message.
        Message msg = messageCaptor.getValue();
        msg.getData().putParcelable("data_call_response",
                createDataCallResponse(1, DataCallResponse.LINK_STATUS_ACTIVE));
        msg.arg1 = DataServiceCallback.RESULT_SUCCESS;
        msg.sendToTarget();
        processAllMessages();

        verify(mMockedDataNetworkControllerCallback, never()).onNrAdvancedCapableByPcoChanged(
                anyBoolean());
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

        verify(mMockedDataNetworkControllerCallback)
                .onInternetDataNetworkConnected(any());
        List<DataNetwork> dataNetworks = getDataNetworks();
        assertThat(dataNetworks).hasSize(1);
        assertThat(dataNetworks.get(0).getNetworkCapabilities().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)).isFalse();
        assertThat(dataNetworks.get(0).isInternetSupported()).isTrue();
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
                TelephonyManager.DATA_ENABLED_REASON_USER, true, mContext.getOpPackageName());
        processAllMessages();

        testSetupImsDataNetwork();
        Mockito.clearInvocations(mMockedDataNetworkControllerCallback);

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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
    public void testDataDisableTearingDownTetheringNetwork() throws Exception {
        // User data enabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, true, mContext.getOpPackageName());
        processAllMessages();

        // Request the restricted tethering network.
        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        netCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);

        mDataNetworkControllerUT.addNetworkRequest(
                new TelephonyNetworkRequest(nativeNetworkRequest, mPhone));
        processAllMessages();

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_DUN);

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        processAllMessages();

        // Everything should be disconnected.
        verifyAllDataDisconnected();
    }

    @Test
    public void testDataDisableNotAllowingBringingUpTetheringNetwork() throws Exception {
        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        processAllMessages();

        // Request the restricted tethering network.
        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        netCaps.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);

        mDataNetworkControllerUT.addNetworkRequest(
                new TelephonyNetworkRequest(nativeNetworkRequest, mPhone));
        processAllMessages();

        // Everything should be disconnected.
        verifyAllDataDisconnected();

        // Telephony should not try to setup a data call for DUN.
        verify(mMockedWwanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
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

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
    }

    @Test
    public void testDelayImsTearDownCsRequestsToTearDown() throws Exception {
        mCarrierConfig.putBoolean(CarrierConfigManager.KEY_DELAY_IMS_TEAR_DOWN_UNTIL_CALL_END_BOOL,
                true);
        TelephonyNetworkRequest networkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        mDataNetworkControllerUT.addNetworkRequest(networkRequest);
        processAllMessages();

        // Call is ongoing
        doReturn(PhoneConstants.State.OFFHOOK).when(mCT).getState();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
        verifyConnectedNetworkHasDataProfile(mImsCellularDataProfile);
        List<DataNetwork> dataNetworks = getDataNetworks();
        assertThat(dataNetworks).hasSize(1);
        dataNetworks.get(0).tearDown(DataNetwork.TEAR_DOWN_REASON_RAT_NOT_ALLOWED);
        processAllMessages();

        // Make sure IMS network is still connected.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
        verifyConnectedNetworkHasDataProfile(mImsCellularDataProfile);

        // Now connectivity service requests to tear down the data network.
        mDataNetworkControllerUT.removeNetworkRequest(networkRequest);
        dataNetworks.get(0).tearDown(DataNetwork.TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED);
        processAllMessages();

        // All data (including IMS) should be torn down.
        verifyAllDataDisconnected();
    }

    @Test
    public void testUnmeteredMmsWhenDataDisabled() throws Exception {
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_CARRIER_METERED_APN_TYPES_STRINGS,
                new String[]{"default", "dun", "supl"});
        carrierConfigChanged();

        // User data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
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

    @Test
    public void testImsGracefulTearDown() throws Exception {
        setImsRegistered(true);
        setRcsRegistered(true);

        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        netCaps.setRequestorPackageName(FAKE_MMTEL_PACKAGE);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, ++mNetworkRequestId, NetworkRequest.Type.REQUEST);
        TelephonyNetworkRequest networkRequest = new TelephonyNetworkRequest(
                nativeNetworkRequest, mPhone);

        mDataNetworkControllerUT.addNetworkRequest(networkRequest);

        processAllMessages();
        Mockito.clearInvocations(mPhone);

        // SIM removal
        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                TelephonyManager.SIM_STATE_ABSENT, 0).sendToTarget();
        processAllMessages();

        // Make sure data network enters disconnecting state
        ArgumentCaptor<PreciseDataConnectionState> pdcsCaptor =
                ArgumentCaptor.forClass(PreciseDataConnectionState.class);
        verify(mPhone).notifyDataConnection(pdcsCaptor.capture());
        PreciseDataConnectionState pdcs = pdcsCaptor.getValue();
        assertThat(pdcs.getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTING);

        // IMS de-registered. Now data network is safe to be torn down.
        Mockito.clearInvocations(mPhone);
        setImsRegistered(false);
        setRcsRegistered(false);
        processAllMessages();

        // All data should be disconnected.
        verifyAllDataDisconnected();
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        verify(mPhone).notifyDataConnection(pdcsCaptor.capture());
        pdcs = pdcsCaptor.getValue();
        assertThat(pdcs.getState()).isEqualTo(TelephonyManager.DATA_DISCONNECTED);
    }

    @Test
    public void testNetworkRequestRemovedBeforeRetry() {
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED, false);
        TelephonyNetworkRequest networkRequest = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mDataNetworkControllerUT.addNetworkRequest(networkRequest);
        logd("Removing network request.");
        mDataNetworkControllerUT.removeNetworkRequest(networkRequest);
        processAllMessages();

        // There should be only one invocation, which is the original setup data request. There
        // shouldn't be more than 1 (i.e. should not retry).
        verify(mMockedWwanDataServiceManager, times(1)).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));
    }

    @Test
    public void testGetInternetDataDisallowedReasons() {
        List<DataDisallowedReason> reasons = mDataNetworkControllerUT
                .getInternetDataDisallowedReasons();
        assertThat(reasons).isEmpty();

        serviceStateChanged(TelephonyManager.NETWORK_TYPE_UNKNOWN,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        reasons = mDataNetworkControllerUT.getInternetDataDisallowedReasons();
        assertThat(reasons).containsExactly(DataDisallowedReason.NOT_IN_SERVICE,
                DataDisallowedReason.NO_SUITABLE_DATA_PROFILE);
    }

    @Test
    public void testEmergencySuplDataDisabled() throws Exception {
        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        processAllMessages();
        doReturn(true).when(mPhone).isInEmergencyCall();
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_SUPL));
        processAllMessages();

        // Make sure SUPL is the only capability advertised, but not internet or MMS.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_SUPL);
        verifyConnectedNetworkHasDataProfile(mGeneralPurposeDataProfile);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
    }

    @Test
    public void testEmergencyCallDataDisabled() throws Exception {
        doReturn(true).when(mPhone).isInEmergencyCall();
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        processAllMessages();

        verifyInternetConnected();

        // Data disabled
        mDataNetworkControllerUT.getDataSettingsManager().setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, mContext.getOpPackageName());
        processAllMessages();

        // Make sure internet is not connected. (Previously it has a bug due to incorrect logic
        // to determine it's for emergency SUPL).
        verifyAllDataDisconnected();
    }

    @Test
    public void testDataActivity() {
        doReturn(TelephonyManager.DATA_ACTIVITY_IN).when(mLinkBandwidthEstimator).getDataActivity();
        mLinkBandwidthEstimatorCallback.onDataActivityChanged(TelephonyManager.DATA_ACTIVITY_IN);
        processAllMessages();
        assertThat(mDataNetworkControllerUT.getDataActivity()).isEqualTo(
                TelephonyManager.DATA_ACTIVITY_IN);

        doReturn(TelephonyManager.DATA_ACTIVITY_OUT).when(mLinkBandwidthEstimator)
                .getDataActivity();
        mLinkBandwidthEstimatorCallback.onDataActivityChanged(TelephonyManager.DATA_ACTIVITY_OUT);
        processAllMessages();
        assertThat(mDataNetworkControllerUT.getDataActivity()).isEqualTo(
                TelephonyManager.DATA_ACTIVITY_OUT);

        doReturn(TelephonyManager.DATA_ACTIVITY_INOUT).when(mLinkBandwidthEstimator)
                .getDataActivity();
        mLinkBandwidthEstimatorCallback.onDataActivityChanged(TelephonyManager.DATA_ACTIVITY_INOUT);
        processAllMessages();
        assertThat(mDataNetworkControllerUT.getDataActivity()).isEqualTo(
                TelephonyManager.DATA_ACTIVITY_INOUT);

        doReturn(TelephonyManager.DATA_ACTIVITY_NONE).when(mLinkBandwidthEstimator)
                .getDataActivity();
        mLinkBandwidthEstimatorCallback.onDataActivityChanged(TelephonyManager.DATA_ACTIVITY_NONE);
        processAllMessages();
        assertThat(mDataNetworkControllerUT.getDataActivity()).isEqualTo(
                TelephonyManager.DATA_ACTIVITY_NONE);
    }

    @Test
    public void testHandoverDataNetworkOos() throws Exception {
        ServiceState ss = new ServiceState();
        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_IWLAN)
                .setRegistrationState(
                        NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        processServiceStateRegStateForTest(ss);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();

        testSetupImsDataNetwork();
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Verify that handover is not performed.
        verify(mMockedWlanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));

        // IMS network should be torn down.
        verifyAllDataDisconnected();
    }

    @Test
    public void testHandoverDataNetworkSourceOos() throws Exception {
        testSetupImsDataNetwork();
        // Configured handover is allowed from OOS to 4G/5G/IWLAN.
        mCarrierConfig.putStringArray(
                CarrierConfigManager.KEY_IWLAN_HANDOVER_POLICY_STRING_ARRAY,
                new String[]{
                        "source=EUTRAN|NGRAN|IWLAN|UNKNOWN, target=EUTRAN|NGRAN|IWLAN, "
                                + "type=disallowed, capabilities=IMS|EIMS|MMS|XCAP|CBS"
                });
        carrierConfigChanged();
        serviceStateChanged(TelephonyManager.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Verify IMS network was torn down on source first.
        verify(mMockedWwanDataServiceManager).deactivateDataCall(anyInt(),
                eq(DataService.REQUEST_REASON_NORMAL), any(Message.class));

        // Verify that IWLAN is brought up again on IWLAN.
        verify(mMockedWlanDataServiceManager).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));

        DataNetwork dataNetwork = getDataNetworks().get(0);
        assertThat(dataNetwork.getTransport()).isEqualTo(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
    }

    @Test
    public void testHandoverDataNetworkNonVops() throws Exception {
        ServiceState ss = new ServiceState();

        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
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
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        processServiceStateRegStateForTest(ss);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Change the preference to cellular
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Verify that handover is not performed.
        verify(mMockedWwanDataServiceManager, never()).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));

        // IMS network should be torn down.
        verifyAllDataDisconnected();
    }

    @Test
    public void testNonMmtelImsHandoverDataNetworkNonVops() throws Exception {
        ServiceState ss = new ServiceState();

        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
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
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        processServiceStateRegStateForTest(ss);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();

        // Bring up the IMS network that does not require MMTEL
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS));
        processAllMessages();

        // Even though the network request does not have MMTEL, but the network support it, so
        // the network capabilities should still have MMTEL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Change the preference to cellular
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        // Verify that handover is performed
        verify(mMockedWwanDataServiceManager).setupDataCall(anyInt(),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));

        // The IMS network should still have IMS and MMTEL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS);
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMTEL);
    }

    @Test
    public void testMmtelImsDataNetworkMovingToNonVops() throws Exception {
        ServiceState ss = new ServiceState();

        // VoPS network
        DataSpecificRegistrationInfo dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_SUPPORTED));

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
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
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        processServiceStateRegStateForTest(ss);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mAccessNetworksManager)
                .getPreferredTransportByNetworkCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();

        // Bring up the IMS network that does require MMTEL
        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();

        // the network capabilities should have IMS and MMTEL.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        ss = new ServiceState();
        // Non VoPS network
        dsri = new DataSpecificRegistrationInfo(8, false, true, true,
                new LteVopsSupportInfo(LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED,
                        LteVopsSupportInfo.LTE_STATUS_NOT_SUPPORTED));

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
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
                .setAccessNetworkTechnology(TelephonyManager.NETWORK_TYPE_LTE)
                .setRegistrationState(NetworkRegistrationInfo.REGISTRATION_STATE_HOME)
                .setDomain(NetworkRegistrationInfo.DOMAIN_CS)
                .build());
        processServiceStateRegStateForTest(ss);
        doReturn(ss).when(mSST).getServiceState();
        doReturn(ss).when(mPhone).getServiceState();

        mDataNetworkControllerUT.obtainMessage(17/*EVENT_SERVICE_STATE_CHANGED*/).sendToTarget();
        processAllMessages();

        // The IMS network should be torn down by data network controller.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_MMTEL);
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
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);

        // Call ends
        doReturn(PhoneConstants.State.IDLE).when(mCT).getState();
        mDataNetworkControllerUT.obtainMessage(18/*EVENT_VOICE_CALL_ENDED*/).sendToTarget();
        processAllMessages();

        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
    }

    @Test
    public void testDeactivateDataOnOldHal() throws Exception {
        doAnswer(invocation -> {
            // Only send the deactivation data response, no data call list changed event.
            Message msg = (Message) invocation.getArguments()[2];
            msg.sendToTarget();
            return null;
        }).when(mMockedWwanDataServiceManager).deactivateDataCall(
                anyInt(), anyInt(), any(Message.class));
        // Simulate old devices
        doReturn(RIL.RADIO_HAL_VERSION_1_6).when(mPhone).getHalVersion();

        testSetupDataNetwork();

        mDataNetworkControllerUT.obtainMessage(9/*EVENT_SIM_STATE_CHANGED*/,
                TelephonyManager.SIM_STATE_ABSENT, 0).sendToTarget();
        processAllMessages();
        verifyAllDataDisconnected();
        verify(mMockedDataNetworkControllerCallback).onAnyDataNetworkExistingChanged(eq(false));
        verify(mMockedDataNetworkControllerCallback).onInternetDataNetworkDisconnected();
    }

    @Test
    public void testHandoverWhileSetupDataCallInProgress() throws Exception {
        // Long delay setup failure
        setFailedSetupDataResponse(mMockedWwanDataServiceManager, DataFailCause.CONGESTION,
                DataCallResponse.RETRY_DURATION_UNDEFINED, false, 10000);

        mDataNetworkControllerUT.addNetworkRequest(
                createNetworkRequest(NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_MMTEL));
        processAllMessages();

        // Change the preference to IWLAN while setup data is still ongoing.
        updateTransport(NetworkCapabilities.NET_CAPABILITY_IMS,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN);

        // Data should not be connected.
        verifyNoConnectedNetworkHasCapability(NetworkCapabilities.NET_CAPABILITY_IMS);

        // There shouldn't be any attempt to bring up IMS on IWLAN even though the preference
        // has already changed, because the previous setup is still ongoing.
        verify(mMockedWlanDataServiceManager, never()).setupDataCall(eq(AccessNetworkType.IWLAN),
                any(DataProfile.class), anyBoolean(), anyBoolean(), anyInt(), any(), anyInt(),
                any(), any(), anyBoolean(), any(Message.class));

        processAllFutureMessages();

        // Should setup a new one instead of handover.
        verify(mMockedWlanDataServiceManager).setupDataCall(eq(AccessNetworkType.IWLAN),
                any(DataProfile.class), anyBoolean(), anyBoolean(),
                eq(DataService.REQUEST_REASON_NORMAL), any(), anyInt(), any(), any(), anyBoolean(),
                any(Message.class));

        // IMS should be connected.
        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMTEL);
    }

    @Test
    public void testRemoveNetworkRequest() throws Exception {
        NetworkCapabilities netCaps = new NetworkCapabilities();
        netCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        NetworkRequest nativeNetworkRequest = new NetworkRequest(netCaps,
                ConnectivityManager.TYPE_MOBILE, 0, NetworkRequest.Type.REQUEST);

        mDataNetworkControllerUT.addNetworkRequest(new TelephonyNetworkRequest(
                nativeNetworkRequest, mPhone));
        processAllMessages();

        // Intentionally create a new telephony request with the original native network request.
        TelephonyNetworkRequest request = new TelephonyNetworkRequest(nativeNetworkRequest, mPhone);

        mDataNetworkControllerUT.removeNetworkRequest(request);
        processAllFutureMessages();

        List<DataNetwork> dataNetworkList = getDataNetworks();
        // The data network should not be torn down after network request removal.
        assertThat(dataNetworkList).hasSize(1);
        // But should be detached from the data network.
        assertThat(dataNetworkList.get(0).getAttachedNetworkRequestList()).isEmpty();
        assertThat(dataNetworkList.get(0).isConnected()).isTrue();
    }

    @Test
    public void testTempDdsSwitchTearDown() throws Exception {
        TelephonyNetworkRequest request = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        mDataNetworkControllerUT.addNetworkRequest(request);
        processAllMessages();

        // Now DDS temporarily switched to phone 1
        doReturn(1).when(mMockedPhoneSwitcher).getPreferredDataPhoneId();

        // Simulate telephony network factory remove request due to switch.
        mDataNetworkControllerUT.removeNetworkRequest(request);
        processAllMessages();

        // Data should be torn down on this non-preferred sub.
        verifyAllDataDisconnected();
    }

    @Test
    public void testSetupDataOnNonDds() throws Exception {
        // Now DDS switched to phone 1
        doReturn(1).when(mMockedPhoneSwitcher).getPreferredDataPhoneId();
        TelephonyNetworkRequest request = createNetworkRequest(
                NetworkCapabilities.NET_CAPABILITY_MMS);

        // Test Don't allow setup if both data and voice OOS
        serviceStateChanged(TelephonyProtoEnums.NETWORK_TYPE_1XRTT,
                // data, voice, Iwlan reg state
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING, null);
        mDataNetworkControllerUT.addNetworkRequest(request);
        processAllMessages();

        verifyAllDataDisconnected();

        // Test Don't allow setup if CS is in service, but current RAT is already PS(e.g. LTE)
        serviceStateChanged(TelephonyProtoEnums.NETWORK_TYPE_LTE,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING, null);

        verifyAllDataDisconnected();

        // Test Allow if voice is in service if RAT is 2g/3g
        serviceStateChanged(TelephonyProtoEnums.NETWORK_TYPE_1XRTT,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING,
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING, null);

        verifyConnectedNetworkHasCapabilities(NetworkCapabilities.NET_CAPABILITY_MMS);
    }
}
