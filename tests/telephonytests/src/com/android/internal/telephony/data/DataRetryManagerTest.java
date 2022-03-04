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

import static com.android.internal.telephony.data.DataRetryManager.DataRetryEntry;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Looper;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataRetryManager.DataRetryManagerCallback;
import com.android.internal.telephony.data.DataRetryManager.DataRetryRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataRetryManagerTest extends TelephonyTest {
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

    private DataProfile mDataProfile2 = new DataProfile.Builder()
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

    private DataProfile mDataProfile3 = new DataProfile.Builder()
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

    @Mock
    private DataRetryManagerCallback mDataRetryManagerCallbackMock;

    private DataRetryManager mDataRetryManagerUT;

    @Before
    public void setUp() throws Exception {
        logd("DataRetryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mDataRetryManagerCallbackMock).invokeFromExecutor(any(Runnable.class));
        mDataRetryManagerUT = new DataRetryManager(mPhone, mDataNetworkController,
                Looper.myLooper(), mDataRetryManagerCallbackMock);

        logd("DataRetryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    // The purpose of this test is to ensure retry rule in string format can be correctly parsed.
    @Test
    public void testRetryRulesParsingFromString() {
        String ruleString = "  capabilities   =    eims,     retry_interval = 1000   ";
        DataRetryRule rule = new DataRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_EIMS);
        assertThat(rule.getMaxRetries()).isEqualTo(10);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).containsExactly(1000L);

        ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3|2253|"
                + "2254, maximum_retries=0  ";
        rule = new DataRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).isEmpty();
        assertThat(rule.getMaxRetries()).isEqualTo(0);
        assertThat(rule.getFailCauses()).containsExactly(8, 27, 28, 29, 30, 32, 33, 35, 50,
                51, 111, -5, -6, 65537, 65538, -3, 2253, 2254);
        assertThat(rule.getRetryIntervalsMillis()).isEmpty();

        ruleString = "capabilities=internet|enterprise|dun|ims|fota, retry_interval=2500|  3000|"
                + "    5000|  10000 | 15000|        20000|40000|60000|  120000|240000  |"
                + "600000| 1200000|        1800000, maximum_retries=20";
        rule = new DataRetryRule(ruleString);
        assertThat(rule.getNetworkCapabilities()).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
                NetworkCapabilities.NET_CAPABILITY_ENTERPRISE,
                NetworkCapabilities.NET_CAPABILITY_DUN,
                NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_FOTA
        );
        assertThat(rule.getMaxRetries()).isEqualTo(20);
        assertThat(rule.getFailCauses()).isEmpty();
        assertThat(rule.getRetryIntervalsMillis()).isEqualTo(Arrays.asList(2500L, 3000L, 5000L,
                10000L, 15000L, 20000L, 40000L, 60000L, 120000L, 240000L, 600000L, 1200000L,
                1800000L));

        ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ";
        rule = new DataRetryRule(ruleString);
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
    public void testInvalidRetryRulesFromString() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataRetryRule("V2hhdCBUaGUgRnVjayBpcyB0aGlzIQ=="));

        assertThrows(IllegalArgumentException.class,
                () -> new DataRetryRule(
                        " capabilities = mms   |supl |  cbs, retry_interval =  20kkj00  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataRetryRule(
                        " capabilities = mms   |supl |  cbs, retry_interval =  -100  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataRetryRule(
                        " capabilities = mms   |supl |  cbs, maximum_retries =  -100  "));

        assertThrows(IllegalArgumentException.class,
                () -> new DataRetryRule(
                        " retry_interval=100, maximum_retries =  100  "));
    }

    @Test
    public void testRetryRuleMatchingByFailCause() {
        String ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3"
                + "|2253|2254, maximum_retries=0  ";
        DataRetryRule rule = new DataRetryRule(ruleString);
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_IMS), 111)).isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_IMS,
                NetworkCapabilities.NET_CAPABILITY_MMS), 65537)).isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_MMS), 12345))
                .isFalse();
    }

    @Test
    public void testRetryRuleMatchingByNetworkCapabilities() {
        String ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ";
        DataRetryRule rule = new DataRetryRule(ruleString);

        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_MMS), 123456))
                .isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_SUPL,
                NetworkCapabilities.NET_CAPABILITY_CBS), 1345)).isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_FOTA), 12345))
                .isFalse();
    }

    @Test
    public void testRetryRuleMatchingByBothFailCauseAndNetworkCapabilities() {
        String ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ,  "
                + "fail_causes=8|27|28|29|30| 32| 3";
        DataRetryRule rule = new DataRetryRule(ruleString);
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_MMS), 3)).isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_CBS), 28)).isTrue();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_SUPL), 4)).isFalse();
        assertThat(rule.canBeMatched(Set.of(NetworkCapabilities.NET_CAPABILITY_IMS), 3)).isFalse();
    }

    @Test
    public void testDataRetryNetworkSuggestedRetry() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile1, networkRequestList, 123, 1000);
        processAllFutureMessages();

        ArgumentCaptor<DataRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
        DataRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_DATA_PROFILE);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile1);
        assertThat(entry.retryDelayMillis).isEqualTo(1000);
        assertThat(entry.networkCapabilities).isEmpty();
        assertThat(entry.appliedDataRetryRule).isNull();
    }

    @Test
    public void testDataRetryNetworkSuggestedNeverRetry() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile1, networkRequestList, 123,
                Long.MAX_VALUE);
        processAllFutureMessages();

        verify(mDataRetryManagerCallbackMock, never()).onDataRetry(any(DataRetryEntry.class));
    }

    @Test
    public void testDataRetryPermanentFailure() {
        DataRetryRule retryRule = new DataRetryRule(
                "fail_causes=8|27|28|29|30|32|33|35|50|51|111|-5|-6|65537|65538|-3|2253|"
                        + "2254, maximum_retries=0");
        doReturn(Collections.singletonList(retryRule)).when(mDataConfigManager).getDataRetryRules();
        mDataRetryManagerUT.obtainMessage(1/*EVENT_DATA_CONFIG_UPDATED*/).sendToTarget();
        processAllMessages();


        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile1, networkRequestList, 2253,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        verify(mDataRetryManagerCallbackMock, never()).onDataRetry(any(DataRetryEntry.class));
    }

    @Test
    public void testDataRetryMaximumRetries() {
        DataRetryRule retryRule = new DataRetryRule(
                "capabilities=internet, retry_interval=2000, maximum_retries=2");
        doReturn(Collections.singletonList(retryRule)).when(mDataConfigManager).getDataRetryRules();
        mDataRetryManagerUT.obtainMessage(1/*EVENT_DATA_CONFIG_UPDATED*/).sendToTarget();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st failed and retry.
        doReturn(List.of(mDataProfile2, mDataProfile1)).when(mDataProfileManager)
                .getDataProfilesForNetworkCapabilities((int[]) any());
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile1, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        ArgumentCaptor<DataRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
        DataRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile2);
        assertThat(entry.retryDelayMillis).isEqualTo(2000);
        assertThat(entry.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule);
        entry.setState(DataRetryEntry.RETRY_STATE_FAILED);
        logd("retry entry: (" + entry.hashCode() + ")=" + entry);

        // 2nd failed and retry.
        doReturn(List.of(mDataProfile1, mDataProfile2)).when(mDataProfileManager)
                .getDataProfilesForNetworkCapabilities((int[]) any());
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile2, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        retryEntryCaptor = ArgumentCaptor.forClass(DataRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
        entry = retryEntryCaptor.getValue();

        assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile1);
        assertThat(entry.retryDelayMillis).isEqualTo(2000);
        assertThat(entry.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule);
        entry.setState(DataRetryEntry.RETRY_STATE_FAILED);
        logd("retry entry: (" + entry.hashCode() + ")=" + entry);

        // 3rd failed and never retry.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile1, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        // Verify there is no retry.
        verify(mDataRetryManagerCallbackMock, never()).onDataRetry(any(DataRetryEntry.class));
    }

    @Test
    public void testDataRetryMaximumRetriesReset() {
        DataRetryRule retryRule1 = new DataRetryRule(
                "capabilities=eims, retry_interval=1000, maximum_retries=20");
        DataRetryRule retryRule2 = new DataRetryRule(
                "capabilities=ims|mms|fota, retry_interval=3000, maximum_retries=1");
        doReturn(List.of(retryRule1, retryRule2)).when(mDataConfigManager).getDataRetryRules();
        mDataRetryManagerUT.obtainMessage(1/*EVENT_DATA_CONFIG_UPDATED*/).sendToTarget();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st failed and retry.
        doReturn(List.of(mDataProfile3)).when(mDataProfileManager)
                .getDataProfilesForNetworkCapabilities((int[]) any());
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile3, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        ArgumentCaptor<DataRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
        DataRetryEntry entry = retryEntryCaptor.getValue();

        assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile3);
        assertThat(entry.retryDelayMillis).isEqualTo(3000L);
        assertThat(entry.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule2);

        // Succeeded. This should clear the retry count.
        entry.setState(DataRetryEntry.RETRY_STATE_SUCCEEDED);

        // Failed again. Retry should happen.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile3, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        retryEntryCaptor = ArgumentCaptor.forClass(DataRetryEntry.class);
        verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
        entry = retryEntryCaptor.getValue();

        assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES);
        assertThat(entry.dataProfile).isEqualTo(mDataProfile3);
        assertThat(entry.retryDelayMillis).isEqualTo(3000L);
        assertThat(entry.networkCapabilities).containsExactly(
                NetworkCapabilities.NET_CAPABILITY_IMS);
        assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule2);
    }

    @Test
    public void testDataRetryBackOffTimer() {
        DataRetryRule retryRule1 = new DataRetryRule(
                "capabilities=eims, retry_interval=1000, maximum_retries=20");
        DataRetryRule retryRule2 = new DataRetryRule(
                "capabilities=internet|mms|fota, retry_interval=3000, maximum_retries=1");
        DataRetryRule retryRule3 = new DataRetryRule(
                "capabilities=ims, retry_interval=2000|4000|8000, "
                        + "maximum_retries=4");
        doReturn(List.of(retryRule1, retryRule2, retryRule3)).when(mDataConfigManager)
                .getDataRetryRules();

        mDataRetryManagerUT.obtainMessage(1/*EVET_DATA_CONFIG_UPDATED*/).sendToTarget();
        processAllMessages();

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_IMS)
                .build();
        TelephonyNetworkRequest tnr = new TelephonyNetworkRequest(request, mPhone);
        DataNetworkController.NetworkRequestList
                networkRequestList = new DataNetworkController.NetworkRequestList(tnr);

        // 1st/2nd/3rd/4th failed and retry.
        doReturn(List.of(mDataProfile3)).when(mDataProfileManager)
                .getDataProfilesForNetworkCapabilities((int[]) any());
        for (long delay : List.of(2000, 4000, 8000, 8000)) {
            Mockito.clearInvocations(mDataRetryManagerCallbackMock);
            mDataRetryManagerUT.evaluateDataRetry(mDataProfile3, networkRequestList, 123,
                    DataCallResponse.RETRY_DURATION_UNDEFINED);
            processAllFutureMessages();

            ArgumentCaptor<DataRetryEntry> retryEntryCaptor =
                    ArgumentCaptor.forClass(DataRetryEntry.class);
            verify(mDataRetryManagerCallbackMock).onDataRetry(retryEntryCaptor.capture());
            DataRetryEntry entry = retryEntryCaptor.getValue();

            assertThat(entry.retryType).isEqualTo(DataRetryEntry.RETRY_TYPE_NETWORK_CAPABILITIES);
            assertThat(entry.dataProfile).isEqualTo(mDataProfile3);
            assertThat(entry.retryDelayMillis).isEqualTo(delay);
            assertThat(entry.networkCapabilities).containsExactly(
                    NetworkCapabilities.NET_CAPABILITY_IMS);
            assertThat(entry.appliedDataRetryRule).isEqualTo(retryRule3);
        }

        // The last fail should not trigger any retry.
        Mockito.clearInvocations(mDataRetryManagerCallbackMock);
        mDataRetryManagerUT.evaluateDataRetry(mDataProfile3, networkRequestList, 123,
                DataCallResponse.RETRY_DURATION_UNDEFINED);
        processAllFutureMessages();

        ArgumentCaptor<DataRetryEntry> retryEntryCaptor =
                ArgumentCaptor.forClass(DataRetryEntry.class);

        // Verify there is no retry.
        verify(mDataRetryManagerCallbackMock, never()).onDataRetry(any(DataRetryEntry.class));
    }
}
