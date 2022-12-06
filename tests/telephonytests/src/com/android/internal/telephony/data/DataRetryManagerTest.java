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

import static com.android.internal.telephony.data.DataRetryManager.DataSetupRetryEntry;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Looper;
import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.ThrottleStatus;
import android.telephony.data.TrafficDescriptor;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.SparseArray;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataConfigManager.DataConfigManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataHandoverRetryRule;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataSetupRetryRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataRetryManagerTest extends TelephonyTest {
    private final DataProfile mDataProfile1 = new DataProfile.Builder()
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

    private final DataProfile mDataProfile2 = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setId(2163)
                    .setOperatorNumeric("12345")
                    .setEntryName("fake_apn2")
                    .setApnName("fake_apn2")
                    .setUser("user")
                    .setPassword("passwd")
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_SUPL
                            | ApnSetting.TYPE_FOTA)
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

    private final DataProfile mDataProfile3 = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
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
                    .build())
            .setPreferred(false)
            .build();

    private final List<DataProfile> mAllDataProfileList = List.of(mDataProfile1, mDataProfile2,
            mDataProfile3);

    // Mocked classes
    private DataRetryManagerCallback mDataRetryManagerCallbackMock;

    private DataRetryManager mDataRetryManagerUT;

    private DataConfigManagerCallback mDataConfigManagerCallback;

    @Before
    public void setUp() throws Exception {
        logd("DataRetryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mDataRetryManagerCallbackMock = Mockito.mock(DataRetryManagerCallback.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataRetryManagerCallbackMock).invokeFromExecutor(any(Runnable.class));
        SparseArray<DataServiceManager> mockedDataServiceManagers = new SparseArray<>();
        mockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                mMockedWwanDataServiceManager);
        mockedDataServiceManagers.put(AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                mMockedWlanDataServiceManager);
        mDataRetryManagerUT = new DataRetryManager(mPhone, mDataNetworkController,
                mockedDataServiceManagers, Looper.myLooper(), mDataRetryManagerCallbackMock);

        ArgumentCaptor<DataConfigManagerCallback> dataConfigManagerCallbackCaptor =
                ArgumentCaptor.forClass(DataConfigManagerCallback.class);
        verify(mDataConfigManager).registerCallback(dataConfigManagerCallbackCaptor.capture());
        mDataConfigManagerCallback = dataConfigManagerCallbackCaptor.getValue();

        logd("DataRetryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mDataRetryManagerUT = null;
        super.tearDown();
    }

    // The purpose of this test is to ensure retry rule in string format can be correctly parsed.
    @Test
    public void testDataSetupRetryRulesParsingFromString() {
        String ruleString = "  capabilities   =    eims,     retry_interval = 1000   ";
        DataSetupRetryRule rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(rule.getMaxRetries()).isEqualTo(10);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(1000L);

        ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3|2253|"
                + "2254, maximum_retries=0  ";
        rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).isEmpty();
        assertThat(rule.getMaxRetries()).isEqualTo(0);
        assertThat(rule.getFailCauses()).containsExactly(8, 27, 28, 29, 30, 32, 33, 35, 50,
                51, 111, -5, -6, 65537, 65538, -3, 2253, 2254);
        assertThat(rule.getRetryIntervalsMillis()).isEmpty();

        ruleString = "capabilities=internet|enterprise|dun|ims|fota, retry_interval=2500|  3000|"
                + "    5000|  10000 | 15000|        20000|40000|60000|  120000|240000  |"
                + "600000| 1200000|        1800000, maximum_retries=20";
        rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE,
                NetworkCapabilities.NET_CAPABILITY_DUN,
                NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_FOTA
        );
        assertThat(rule.getMaxRetries()).isEqualTo(20);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(2500L, 3000L, 5000L,
                10000L, 15000L, 20000L, 40000L, 60000L, 120000L, 240000L, 600000L, 1200000L,
                1800000L).inOrder();

        ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ";
        rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_SUPL,
                NetworkCapabilities.NET_CAPABILITY_CBS
        );
        assertThat(rule.getMaxRetries()).isEqualTo(10);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(2000L);
    }

    @Test
    public void testDataSetupRetryInvalidRulesFromString() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataSetupRetryRule("V2hhdCBUaGUgRnVjayBpcyB0aGlzIQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> new DataSetupRetryRule(
                        " capabilities = mms   |supl |  cbs, retry_interval =  20kkj00  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataSetupRetryRule(
                        " capabilities = mms   |supl |  cbs, retry_interval =  -100  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataSetupRetryRule(
                        " capabilities = mms   |supl |  cbs, maximum_retries =  -100  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataSetupRetryRule(
                        " retry_interval=100, maximum_retries =  100  "));
    }

    @Test
    public void testDataSetupRetryRuleMatchingByFailCause() {
        String ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3"
                + "|2253|2254, maximum_retries=0  ";
        DataSetupRetryRule rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_IMS, 111)).isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_MMS, 65537)).isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_MMS, 12345))
                .isFalse();
    }

    @Test
    public void testDataSetupRetryRuleMatchingByNetworkCapabilities() {
        String ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ";
        DataSetupRetryRule rule = new DataSetupRetryRule(ruleString);

        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_MMS, 123456))
                .isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_SUPL, 1345)).isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_FOTA, 12345))
                .isFalse();
    }

    @Test
    public void testDataSetupRetryRuleMatchingByBothFailCauseAndNetworkCapabilities() {
        String ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ,  "
                + "fail_causes=8|27|28|29|30| 32| 3";
        DataSetupRetryRule rule = new DataSetupRetryRule(ruleString);
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_MMS, 3)).isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_CBS, 28)).isTrue();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_SUPL, 4)).isFalse();
        assertThat(rule.canBeMatched(NetworkCapabilities.NET_CAPABILITY_IMS, 3)).isFalse();
    }

    @Test
    public void testDataSetupRetryNetworkSuggestedRetry() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile1,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123, 1000);
        processAllFutureMessages();

        ArgumentCaptor<DataSetupRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataNetworkSetupRetry(retryEntryCaptor.capture());
        DataSetupRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.setupRetryType).isEqualTo(DataSetupRetryEntry.RETRY_TYPE_DATA_PROFILE);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile1);
        assertThat(entry.retryDelayMillis).isEqualTo(1000);
        assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
        assertThat(entry.appliedDataRetryRule).isNull();
    }

    @Test
    public void testDataSetupRetryNetworkSuggestedNeverRetry() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile3,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                Long.MAX_VALUE);
        processAllFutureMessages();

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mDataRetryManagerCallbackMock).onThrottleStatusChanged(
                throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType()).isEqualTo(ThrottleStatus.RETRY_TYPE_NONE);
        assertThat(throttleStatus.getThrottleExpiryTimeMillis()).isEqualTo(Long.MAX_VALUE);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        verify(mDataRetryManagerCallbackMock, never())
                .onDataNetworkSetupRetry(any(DataSetupRetryEntry.class));
    }

    @Test
    public void testDataSetupUnthrottling() {
        testDataSetupRetryNetworkSuggestedNeverRetry();
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);

        mDataRetryManagerUT.obtainMessage(6/*EVENT_DATA_PROFILE_UNTHROTTLED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mDataProfile3, null))
                .sendToTarget();
        processAllMessages();

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mDataRetryManagerCallbackMock).onThrottleStatusChanged(
                throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType())
                .isEqualTo(ThrottleStatus.RETRY_TYPE_NEW_CONNECTION);
        assertThat(throttleStatus.getThrottleExpiryTimeMillis()).isEqualTo(-1);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);

        ArgumentCaptor<DataSetupRetryEntry> dataSetupRetryEntryCaptor =
                ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock)
                .onDataNetworkSetupRetry(dataSetupRetryEntryCaptor.capture());
        DataSetupRetryEntry entry = dataSetupRetryEntryCaptor.getValue();
        assertThat(entry.dataProfile).isEqualTo(mDataProfile3);
        assertThat(entry.retryDelayMillis).isEqualTo(0);
        assertThat(entry.transport).isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testEnterpriseUnthrottling() throws Exception {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        DataProfile enterpriseDataProfile = new DataProfile.Builder()
                .setTrafficDescriptor(new TrafficDescriptor(null,
                        new TrafficDescriptor.OsAppId(TrafficDescriptor.OsAppId.ANDROID_OS_ID,
                                "ENTERPRISE", 1).getBytes()))
                .build();

        mDataRetryManagerUT.evaluateDataSetupRetry(enterpriseDataProfile,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                Long.MAX_VALUE);
        processAllFutureMessages();

        mDataRetryManagerUT.obtainMessage(6/*EVENT_DATA_PROFILE_UNTHROTTLED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, enterpriseDataProfile,
                        null)).sendToTarget();
        processAllMessages();

        ArgumentCaptor<DataSetupRetryEntry> dataSetupRetryEntryCaptor =
                ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock)
                .onDataNetworkSetupRetry(dataSetupRetryEntryCaptor.capture());
        DataSetupRetryEntry entry = dataSetupRetryEntryCaptor.getValue();
        assertThat(entry.dataProfile).isEqualTo(enterpriseDataProfile);
        assertThat(entry.retryDelayMillis).isEqualTo(0);
        assertThat(entry.transport).isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testDataSetupRetryPermanentFailure() {
        DataSetupRetryRule retryRule = new DataSetupRetryRule(
                "fail_causes=8|27|28|29|30|32|33|35|50|51|111|-5|-6|65537|65538|-3|2253|"
                        + "2254, maximum_retries=0");
        doReturn(Collections.singletonList(retryRule)).when(mDataConfigManager)
                .getDataSetupRetryRules();
        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();


        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile1,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 2253,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        verify(mDataRetryManagerCallbackMock, never())
                .onDataNetworkSetupRetry(any(DataSetupRetryEntry.class));
    }

    @Test
    public void testDataSetupRetryMaximumRetries() {
        DataSetupRetryRule retryRule = new DataSetupRetryRule(
                "capabilities=internet, retry_interval=2000, maximum_retries=2");
        doReturn(Collections.singletonList(retryRule)).when(mDataConfigManager)
                .getDataSetupRetryRules();
        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st failed and retry.
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile1,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        ArgumentCaptor<DataSetupRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataNetworkSetupRetry(retryEntryCaptor.capture());
        DataSetupRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.setupRetryType)
                .isEqualTo(DataSetupRetryEntry.RETRY_TYPE_NETWORK_REQUESTS);
        assertThat(entry.dataProfile).isNull();
        assertThat(entry.retryDelayMillis).isEqualTo(2000);
        assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule);
        entry.setState(DataSetupRetryEntry.RETRY_STATE_FAILED);
        logd("retry entry: (" + entry.hashCode() + ")=" + entry);

        // 2nd failed and retry.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile2,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        retryEntryCaptor = ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataNetworkSetupRetry(retryEntryCaptor.capture());
        entry = retryEntryCaptor.getValue();

        assertThat(entry.setupRetryType)
                .isEqualTo(DataSetupRetryEntry.RETRY_TYPE_NETWORK_REQUESTS);
        assertThat(entry.dataProfile).isNull();
        assertThat(entry.retryDelayMillis).isEqualTo(2000);
        assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule);
        entry.setState(DataSetupRetryEntry.RETRY_STATE_FAILED);
        logd("retry entry: (" + entry.hashCode() + ")=" + entry);

        // 3rd failed and never retry.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile1,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        // Verify there is no retry.
        verify(mDataRetryManagerCallbackMock, never())
                .onDataNetworkSetupRetry(any(DataSetupRetryEntry.class));
    }

    @Test
    public void testDataSetupRetryMaximumRetriesReset() {
        DataSetupRetryRule retryRule1 = new DataSetupRetryRule(
                "capabilities=eims, retry_interval=1000, maximum_retries=20");
        DataSetupRetryRule retryRule2 = new DataSetupRetryRule(
                "capabilities=ims|mms|fota, retry_interval=3000, maximum_retries=1");
        doReturn(List.of(retryRule1, retryRule2)).when(mDataConfigManager).getDataSetupRetryRules();
        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st failed and retry.
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile3,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        ArgumentCaptor<DataSetupRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataNetworkSetupRetry(retryEntryCaptor.capture());
        DataSetupRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.setupRetryType)
                .isEqualTo(DataSetupRetryEntry.RETRY_TYPE_NETWORK_REQUESTS);
        assertThat(entry.dataProfile).isNull();
        assertThat(entry.retryDelayMillis).isEqualTo(3000L);
        assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule2);

        // Succeeded. This should clear the retry count.
        entry.setState(DataSetupRetryEntry.RETRY_STATE_SUCCEEDED);

        // Failed again. Retry should happen.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile3,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        retryEntryCaptor = ArgumentCaptor.forClass(DataSetupRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataNetworkSetupRetry(retryEntryCaptor.capture());
        entry = retryEntryCaptor.getValue();

        assertThat(entry.setupRetryType)
                .isEqualTo(DataSetupRetryEntry.RETRY_TYPE_NETWORK_REQUESTS);
        assertThat(entry.dataProfile).isNull();
        assertThat(entry.retryDelayMillis).isEqualTo(3000L);
        assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule2);
    }

    @Test
    public void testDataSetupRetryBackOffTimer() {
        DataSetupRetryRule retryRule1 = new DataSetupRetryRule(
                "capabilities=eims, retry_interval=1000, maximum_retries=20");
        DataSetupRetryRule retryRule2 = new DataSetupRetryRule(
                "capabilities=internet|mms|fota, retry_interval=3000, maximum_retries=1");
        DataSetupRetryRule retryRule3 = new DataSetupRetryRule(
                "capabilities=ims, retry_interval=2000|4000|8000, "
                        + "maximum_retries=4");
        doReturn(List.of(retryRule1, retryRule2, retryRule3)).when(mDataConfigManager)
                .getDataSetupRetryRules();

        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st/2nd/3rd/4th failed and retry.
        for (long delay : List.of(2000, 4000, 8000, 8000)) {
            Mockito.clearInvocations(mDataRetryManagerCallbackMock);
            mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile3,
                    AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                    DataCallResponse.RETRY_DURATION_UNDEFINED);
            processAllFutureMessages();

            ArgumentCaptor<DataSetupRetryEntry> retryEntryCaptor =
                    ArgumentCaptor.forClass(DataSetupRetryEntry.class);
            verify(mDataRetryManagerCallbackMock)
                    .onDataNetworkSetupRetry(retryEntryCaptor.capture());
            DataSetupRetryEntry entry = retryEntryCaptor.getValue();

            assertThat(entry.setupRetryType)
                    .isEqualTo(DataSetupRetryEntry.RETRY_TYPE_NETWORK_REQUESTS);
            assertThat(entry.dataProfile).isNull();
            assertThat(entry.retryDelayMillis).isEqualTo(delay);
            assertThat(entry.networkRequestList).isEqualTo(networkRequestList);
            assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule3);

            entry.setState(DataRetryManager.DataRetryEntry.RETRY_STATE_FAILED);
        }

        // The last fail should not trigger any retry.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile3,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        // Verify there is no retry.
        verify(mDataRetryManagerCallbackMock, never())
                .onDataNetworkSetupRetry(any(DataSetupRetryEntry.class));
    }

    @Test
    public void testDataHandoverRetryRulesParsingFromString() {
        String ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3"
                + "|2253|2254, maximum_retries=0  ";
        DataHandoverRetryRule rule = new DataHandoverRetryRule(ruleString);
        assertThat(rule.getMaxRetries()).isEqualTo(0);
        assertThat(rule.getFailCauses()).containsExactly(8, 27, 28, 29, 30, 32, 33, 35, 50,
                51, 111, -5, -6, 65537, 65538, -3, 2253, 2254);
        assertThat(rule.getRetryIntervalsMillis()).isEmpty();

        ruleString = "retry_interval=1000|2000|4000|8000|16000, maximum_retries=5";
        rule = new DataHandoverRetryRule(ruleString);
        assertThat(rule.getMaxRetries()).isEqualTo(5);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(1000L, 2000L, 4000L, 8000L,
                16000L).inOrder();

        ruleString = "retry_interval=1000|2000, maximum_retries=10";
        rule = new DataHandoverRetryRule(ruleString);
        assertThat(rule.getMaxRetries()).isEqualTo(10);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(1000L, 2000L).inOrder();

        ruleString = "retry_interval=1000";
        rule = new DataHandoverRetryRule(ruleString);
        assertThat(rule.getMaxRetries()).isEqualTo(10);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(1000L);

        ruleString = "maximum_retries=5";
        rule = new DataHandoverRetryRule(ruleString);
        assertThat(rule.getMaxRetries()).isEqualTo(5);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(5000L);
    }

    @Test
    public void testDataHandoverRetryInvalidRulesFromString() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataHandoverRetryRule("V2hhdCBUaGUgRnVjayBpcyB0aGlzIQ=="));
    }

    @Test
    public void testIsSimilarNetworkRequestRetryScheduled() {
        DataSetupRetryRule retryRule = new DataSetupRetryRule(
                "capabilities=internet, retry_interval=2000, maximum_retries=2");
        doReturn(Collections.singletonList(retryRule)).when(mDataConfigManager)
                .getDataSetupRetryRules();
        mDataConfigManagerCallback.onCarrierConfigChanged();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // failed and retry.
        mDataRetryManagerUT.evaluateDataSetupRetry(mDataProfile1,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        tnr = new TelephonyNetworkRequest(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(), mPhone);
        assertThat(mDataRetryManagerUT.isSimilarNetworkRequestRetryScheduled(tnr,
                AccessNetworkConstants.TRANSPORT_TYPE_WWAN)).isTrue();
        assertThat(mDataRetryManagerUT.isSimilarNetworkRequestRetryScheduled(tnr,
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN)).isFalse();
    }

    @Test
    public void testRilCrashedReset() {
        testDataSetupRetryNetworkSuggestedNeverRetry();
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);

        // RIL crashed and came back online.
        mDataRetryManagerUT.obtainMessage(8/*EVENT_RADIO_ON*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mDataProfile3, null))
                .sendToTarget();
        processAllMessages();

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mDataRetryManagerCallbackMock).onThrottleStatusChanged(
                throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType())
                .isEqualTo(ThrottleStatus.RETRY_TYPE_NEW_CONNECTION);
        assertThat(throttleStatus.getThrottleExpiryTimeMillis()).isEqualTo(-1);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testModemCrashedReset() {
        testDataSetupRetryNetworkSuggestedNeverRetry();
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);

        // RIL crashed and came back online.
        mDataRetryManagerUT.obtainMessage(10 /*EVENT_TAC_CHANGED*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mDataProfile3, null))
                .sendToTarget();
        processAllMessages();

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mDataRetryManagerCallbackMock).onThrottleStatusChanged(
                throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType())
                .isEqualTo(ThrottleStatus.RETRY_TYPE_NEW_CONNECTION);
        assertThat(throttleStatus.getThrottleExpiryTimeMillis()).isEqualTo(-1);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }

    @Test
    public void testTacChangedReset() {
        doReturn(true).when(mDataConfigManager).shouldResetDataThrottlingWhenTacChanges();

        testDataSetupRetryNetworkSuggestedNeverRetry();
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);

        // RIL crashed and came back online.
        mDataRetryManagerUT.obtainMessage(9/*EVENT_MODEM_RESET*/,
                new AsyncResult(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, mDataProfile3, null))
                .sendToTarget();
        processAllMessages();

        ArgumentCaptor<List<ThrottleStatus>> throttleStatusCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mDataRetryManagerCallbackMock).onThrottleStatusChanged(
                throttleStatusCaptor.capture());
        assertThat(throttleStatusCaptor.getValue()).hasSize(1);
        ThrottleStatus throttleStatus = throttleStatusCaptor.getValue().get(0);
        assertThat(throttleStatus.getApnType()).isEqualTo(ApnSetting.TYPE_IMS);
        assertThat(throttleStatus.getRetryType())
                .isEqualTo(ThrottleStatus.RETRY_TYPE_NEW_CONNECTION);
        assertThat(throttleStatus.getThrottleExpiryTimeMillis()).isEqualTo(-1);
        assertThat(throttleStatus.getTransportType())
                .isEqualTo(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
    }
}
