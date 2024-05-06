/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.telephony.security;

import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_ENCRYPTED;
import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_NOTIFY_ENCRYPTED;
import static com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.safetycenter.SafetySourceData;
import android.util.Singleton;

import com.android.internal.R;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.TestApplication;
import com.android.internal.telephony.security.CellularNetworkSecuritySafetySource.SafetyCenterManagerWrapper;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

public final class CellularNetworkSecuritySafetySourceTest extends TelephonyTest {

    private SafetyCenterManagerWrapper mSafetyCenterManagerWrapper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());

        // unmock ActivityManager to be able to register receiver, create real PendingIntents.
        restoreInstance(Singleton.class, "mInstance", mIActivityManagerSingleton);
        restoreInstance(ActivityManager.class, "IActivityManagerSingleton", null);

        SubscriptionInfoInternal info0 = new SubscriptionInfoInternal.Builder()
                .setId(0)
                .setDisplayName("fake_name0")
                .build();
        doReturn(info0).when(mSubscriptionManagerService).getSubscriptionInfoInternal(eq(0));
        SubscriptionInfoInternal info1 = new SubscriptionInfoInternal.Builder()
                .setId(1)
                .setDisplayName("fake_name1")
                .build();
        doReturn(info1).when(mSubscriptionManagerService).getSubscriptionInfoInternal(eq(1));

        mContextFixture.putResource(R.string.scCellularNetworkSecurityTitle, "fake");
        mContextFixture.putResource(R.string.scCellularNetworkSecuritySummary, "fake");
        mContextFixture.putResource(R.string.scNullCipherIssueNonEncryptedTitle, "fake %1$s");
        mContextFixture.putResource(R.string.scNullCipherIssueNonEncryptedSummary, "fake");
        mContextFixture.putResource(R.string.scNullCipherIssueEncryptedTitle, "fake %1$s");
        mContextFixture.putResource(R.string.scNullCipherIssueEncryptedSummary, "fake");
        mContextFixture.putResource(R.string.scIdentifierDisclosureIssueTitle, "fake");
        mContextFixture.putResource(
                R.string.scIdentifierDisclosureIssueSummary, "fake %1$d %2$tr %3$tr %4$s");
        mContextFixture.putResource(R.string.scNullCipherIssueActionSettings, "fake");
        mContextFixture.putResource(R.string.scNullCipherIssueActionLearnMore, "fake");

        mSafetyCenterManagerWrapper = mock(SafetyCenterManagerWrapper.class);
        doAnswer(inv -> getActivityPendingIntent(inv.getArgument(1)))
                .when(mSafetyCenterManagerWrapper)
                .getActivityPendingIntent(any(Context.class), any(Intent.class));

        mSafetySource = new CellularNetworkSecuritySafetySource(mSafetyCenterManagerWrapper);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private PendingIntent getActivityPendingIntent(Intent intent) {
        Context context = TestApplication.getAppContext();
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Test
    public void disableNullCipherIssue_nullData() {
        mSafetySource.setNullCipherIssueEnabled(mContext, false);

        verify(mSafetyCenterManagerWrapper, times(1)).setSafetySourceData(isNull());
    }

    @Test
    public void enableNullCipherIssue_statusWithoutIssues() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);

        verify(mSafetyCenterManagerWrapper, times(1)).setSafetySourceData(data.capture());
        assertThat(data.getValue().getStatus()).isNotNull();
        assertThat(data.getValue().getIssues()).isEmpty();
    }

    @Test
    public void setNullCipherState_encrypted_statusWithoutIssue() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);
        mSafetySource.setNullCipherState(mContext, 0, NULL_CIPHER_STATE_ENCRYPTED);

        verify(mSafetyCenterManagerWrapper, times(2)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(1).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(1).getIssues()).isEmpty();
    }

    @Test
    public void setNullCipherState_notifyEncrypted_statusWithIssue() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);
        mSafetySource.setNullCipherState(mContext, 0, NULL_CIPHER_STATE_NOTIFY_ENCRYPTED);

        verify(mSafetyCenterManagerWrapper, times(2)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(1).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(1).getIssues()).hasSize(1);
    }

    @Test
    public void setNullCipherState_notifyNonEncrypted_statusWithIssue() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);
        mSafetySource.setNullCipherState(mContext, 0, NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED);

        verify(mSafetyCenterManagerWrapper, times(2)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(1).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(1).getIssues()).hasSize(1);
    }

    @Test
    public void setNullCipherState_multipleNonEncrypted_statusWithTwoIssues() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);
        mSafetySource.setNullCipherState(mContext, 0, NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED);
        mSafetySource.setNullCipherState(mContext, 1, NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED);

        verify(mSafetyCenterManagerWrapper, times(3)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(2).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(2).getIssues()).hasSize(2);
    }

    @Test
    public void disableIdentifierDisclosueIssue_nullData() {
        // We must first enable before disabling, since a standalone call to disable may result in
        // a no-op when the default for a new notifier is to be disabled.
        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);
        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, false);

        verify(mSafetyCenterManagerWrapper, times(1)).setSafetySourceData(isNull());
    }

    @Test
    public void enableIdentifierDisclosureIssue_enableTwice() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);
        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);
        mSafetySource.setIdentifierDisclosure(mContext, 0, 12, Instant.now(), Instant.now());
        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);

        // Two invocations because the initial enablement and the subsequent disclosure result in
        // updates to safety center
        verify(mSafetyCenterManagerWrapper, times(2)).setSafetySourceData(data.capture());
        // When we're enabled, enabling again should not clear our issue list.
        assertThat(data.getAllValues().get(1).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(1).getIssues()).hasSize(1);
    }

    @Test
    public void enableIdentifierDisclosueIssue_statusWithoutIssues() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);

        verify(mSafetyCenterManagerWrapper, times(1)).setSafetySourceData(data.capture());
        assertThat(data.getValue().getStatus()).isNotNull();
        assertThat(data.getValue().getIssues()).isEmpty();
    }

    @Test
    public void setIdentifierDisclosure_singleDisclosure_statusWithIssue() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);
        mSafetySource.setIdentifierDisclosure(mContext, 0, 12, Instant.now(), Instant.now());

        verify(mSafetyCenterManagerWrapper, times(2)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(1).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(1).getIssues()).hasSize(1);
    }

    @Test
    public void setIdentifierDisclosure_multipleDisclosures_statusWithTwoIssues() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);
        mSafetySource.setIdentifierDisclosure(mContext, 0, 12, Instant.now(), Instant.now());
        mSafetySource.setIdentifierDisclosure(mContext, 1, 3, Instant.now(), Instant.now());

        verify(mSafetyCenterManagerWrapper, times(3)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(2).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(2).getIssues()).hasSize(2);
    }

    @Test
    public void multipleIssuesKinds_statusWithTwoIssues() {
        ArgumentCaptor<SafetySourceData> data = ArgumentCaptor.forClass(SafetySourceData.class);

        mSafetySource.setNullCipherIssueEnabled(mContext, true);
        mSafetySource.setNullCipherState(mContext, 0, NULL_CIPHER_STATE_NOTIFY_NON_ENCRYPTED);
        mSafetySource.setIdentifierDisclosureIssueEnabled(mContext, true);
        mSafetySource.setIdentifierDisclosure(mContext, 0, 12, Instant.now(), Instant.now());

        verify(mSafetyCenterManagerWrapper, times(4)).setSafetySourceData(data.capture());
        assertThat(data.getAllValues().get(3).getStatus()).isNotNull();
        assertThat(data.getAllValues().get(3).getIssues()).hasSize(2);
    }
}