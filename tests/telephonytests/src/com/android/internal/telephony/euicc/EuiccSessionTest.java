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
package com.android.internal.telephony.euicc;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import android.app.PendingIntent;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.euicc.DownloadSubscriptionResult;
import android.service.euicc.EuiccService;
import android.service.euicc.GetDefaultDownloadableSubscriptionListResult;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccInfo;
import android.telephony.euicc.EuiccManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.euicc.EuiccConnector.GetOtaStatusCommandCallback;
import com.android.internal.telephony.euicc.EuiccConnector.OtaStatusChangedCallback;
import com.android.internal.telephony.uicc.euicc.apdu.ApduSender;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.uicc.UiccSlot;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class EuiccSessionTest extends TelephonyTest {
    @Rule
    public final TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    private static final String SESSION_ID_1 = "SESSION_ID_1";
    private static final String SESSION_ID_2 = "SESSION_ID_2";

    private EuiccSession mEuiccSession;
    @Mock private ApduSender mApduSender;
    @Mock private ApduSender mApduSender2;

    @Before
    public void setUp() throws Exception {
        mEuiccSession = new EuiccSession();
    }

    @Test
    @DisableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void startOneSession_featureDisabled_noop() throws Exception {
        mEuiccSession.startSession(SESSION_ID_1);
        mEuiccSession.noteChannelOpen(mApduSender);
        mEuiccSession.noteChannelOpen(mApduSender2);

        assertThat(mEuiccSession.hasSession()).isFalse();

        mEuiccSession.endSession(SESSION_ID_1);

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender, never()).closeAnyOpenChannel();
        verify(mApduSender2, never()).closeAnyOpenChannel();
    }

    @Test
    @EnableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void startOneSession_endSession_hasSession() throws Exception {
        mEuiccSession.startSession(SESSION_ID_1);
        mEuiccSession.noteChannelOpen(mApduSender);
        mEuiccSession.noteChannelOpen(mApduSender2);

        assertThat(mEuiccSession.hasSession()).isTrue();

        mEuiccSession.endSession(SESSION_ID_2);

        assertThat(mEuiccSession.hasSession()).isTrue();
        verify(mApduSender, never()).closeAnyOpenChannel();

        mEuiccSession.endSession(SESSION_ID_1);

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender).closeAnyOpenChannel();
        verify(mApduSender2).closeAnyOpenChannel();
    }

    @Test
    @EnableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void startTwoSession_endSession_hasSession() throws Exception {
        mEuiccSession.startSession(SESSION_ID_1);
        mEuiccSession.noteChannelOpen(mApduSender);
        mEuiccSession.startSession(SESSION_ID_2);

        assertThat(mEuiccSession.hasSession()).isTrue();

        mEuiccSession.endSession(SESSION_ID_1);
        verify(mApduSender, never()).closeAnyOpenChannel();

        assertThat(mEuiccSession.hasSession()).isTrue();

        mEuiccSession.endSession(SESSION_ID_2);

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender).closeAnyOpenChannel();
    }

    @Test
    @EnableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void startTwoSessions_endAllSessions_hasSession() throws Exception {
        mEuiccSession.startSession(SESSION_ID_1);
        mEuiccSession.noteChannelOpen(mApduSender);
        mEuiccSession.startSession(SESSION_ID_2);
        mEuiccSession.noteChannelOpen(mApduSender2);

        assertThat(mEuiccSession.hasSession()).isTrue();

        mEuiccSession.endAllSessions();

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender).closeAnyOpenChannel();
        verify(mApduSender2).closeAnyOpenChannel();
    }

    @Test
    @EnableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void noteChannelOpen_noSession_endSession_noop() throws Exception {
        // noteChannelOpen called without a session started
        mEuiccSession.noteChannelOpen(mApduSender);

        assertThat(mEuiccSession.hasSession()).isFalse();

        mEuiccSession.endSession(SESSION_ID_1);

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender, never()).closeAnyOpenChannel();
    }

    @Test
    @EnableFlags(Flags.FLAG_OPTIMIZATION_APDU_SENDER)
    public void endAllSessions_noSession_endAllSessions_noOp() throws Exception {
        // noteChannelOpen called without a session started
        mEuiccSession.noteChannelOpen(mApduSender);

        assertThat(mEuiccSession.hasSession()).isFalse();

        mEuiccSession.endAllSessions();

        assertThat(mEuiccSession.hasSession()).isFalse();
        verify(mApduSender, never()).closeAnyOpenChannel();
    }
}
