/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.telephony;

import static android.telephony.TelephonyManager.ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA;
import static android.telephony.TelephonyManager.EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DISMISS;
import static android.telephony.TelephonyManager.EXTRA_SUBSCRIPTION_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.res.Resources;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.InstrumentationRegistry;

import com.android.internal.telephony.dataconnection.DataEnabledSettings;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class MultiSimSettingControllerTest extends TelephonyTest {
    private static final int SINGLE_SIM = 1;
    private static final int DUAL_SIM = 2;
    private MultiSimSettingController mMultiSimSettingControllerUT;
    @Mock
    private SubscriptionController mSubControllerMock;
    @Mock
    private Phone mPhoneMock1;
    @Mock
    private Phone mPhoneMock2;
    @Mock
    private DataEnabledSettings mDataEnabledSettingsMock1;
    @Mock
    private DataEnabledSettings mDataEnabledSettingsMock2;
    private Phone[] mPhones;
    @Mock
    private CommandsInterface mMockCi;

    ParcelUuid mGroupUuid1 = new ParcelUuid(UUID.randomUUID());

    private SubscriptionInfo mSubInfo1 = new SubscriptionInfo(1, "subInfo1 IccId", 0, "T-mobile",
            "T-mobile", 0, 255, "12345", 0, null, "310", "260",
            "156", false, null, null);

    private SubscriptionInfo mSubInfo2 = new SubscriptionInfo(2, "subInfo2 IccId", 1, "T-mobile",
            "T-mobile", 0, 255, "12345", 0, null, "310", "260",
            "156", false, null, null, -1, false, mGroupUuid1.toString(), false,
            TelephonyManager.UNKNOWN_CARRIER_ID, SubscriptionManager.PROFILE_CLASS_DEFAULT,
            SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM, null, null, true);

    private SubscriptionInfo mSubInfo3 = new SubscriptionInfo(3, "subInfo3 IccId", -1, "T-mobile",
            "T-mobile", 0, 255, "12345", 0, null, "310", "260",
            "156", false, null, null, -1, false, mGroupUuid1.toString(), false,
            TelephonyManager.UNKNOWN_CARRIER_ID, SubscriptionManager.PROFILE_CLASS_DEFAULT,
            SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM, null, null, true);

    private SubscriptionInfo mSubInfo4 = new SubscriptionInfo(4, "subInfo4 IccId", -1, "T-mobile",
            "T-mobile", 0, 255, "12345", 0, null, "310", "260",
            "156", false, null, null, -1, false, mGroupUuid1.toString(), false,
            TelephonyManager.UNKNOWN_CARRIER_ID, SubscriptionManager.PROFILE_CLASS_DEFAULT,
            SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM, null, null, true);

    @Before
    public void setUp() throws Exception {
        super.setUp("SubscriptionControllerTest");
        // Default configuration:
        // DSDS device.
        // Sub 1 is the default sub.
        // Sub 1 is in slot 0; sub 2 is in slot 1.
        doReturn(DUAL_SIM).when(mTelephonyManager).getPhoneCount();
        doReturn(DUAL_SIM).when(mTelephonyManager).getActiveModemCount();
        doReturn(1).when(mSubControllerMock).getDefaultDataSubId();
        doReturn(1).when(mSubControllerMock).getDefaultVoiceSubId();
        doReturn(1).when(mSubControllerMock).getDefaultSmsSubId();
        doReturn(true).when(mSubControllerMock).isActiveSubId(1);
        doReturn(true).when(mSubControllerMock).isActiveSubId(2);
        doReturn(0).when(mSubControllerMock).getPhoneId(1);
        doReturn(1).when(mSubControllerMock).getPhoneId(2);
        doReturn(true).when(mSubControllerMock).isOpportunistic(5);
        doReturn(1).when(mPhoneMock1).getSubId();
        doReturn(2).when(mPhoneMock2).getSubId();
        mPhoneMock1.mCi = mSimulatedCommands;
        mPhoneMock2.mCi = mSimulatedCommands;
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1, mSubInfo2);
        doReturn(infoList).when(mSubControllerMock)
                .getActiveSubscriptionInfoList(anyString(), nullable(String.class));
        doReturn(new int[]{1, 2}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mPhones = new Phone[] {mPhoneMock1, mPhoneMock2};
        doReturn(mDataEnabledSettingsMock1).when(mPhoneMock1).getDataEnabledSettings();
        doReturn(mDataEnabledSettingsMock2).when(mPhoneMock2).getDataEnabledSettings();

        doReturn(Arrays.asList(mSubInfo1)).when(mSubControllerMock).getSubInfo(
                eq(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 1), any());
        doReturn(Arrays.asList(mSubInfo2)).when(mSubControllerMock).getSubInfo(
                eq(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 2), any());
        doReturn(Arrays.asList(mSubInfo3)).when(mSubControllerMock).getSubInfo(
                eq(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 3), any());
        doReturn(Arrays.asList(mSubInfo4)).when(mSubControllerMock).getSubInfo(
                eq(SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 4), any());

        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        replaceInstance(SubscriptionController.class, "sInstance", null, mSubControllerMock);
        mMultiSimSettingControllerUT = new MultiSimSettingController(mContext, mSubControllerMock);
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSubInfoChangeBeforeAllSubReady() throws Exception {
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultDataSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultVoiceSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultSmsSubId();

        // Mark sub 2 as inactive.
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        // Mark subscription ready as false. The below sub info change should be ignored.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultSmsSubId(anyInt());

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubControllerMock).setDefaultDataSubId(1);
        verify(mSubControllerMock).setDefaultVoiceSubId(1);
        verify(mSubControllerMock).setDefaultSmsSubId(1);
        verifyDismissIntentSent();
    }

    @Test
    public void testSubInfoChangeAfterRadioUnavailable() throws Exception {
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();

        // Ensure all subscription loaded only updates state once
        clearInvocations(mSubControllerMock);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultSmsSubId(anyInt());

        // Notify radio unavailable.
        replaceInstance(BaseCommands.class, "mState", mSimulatedCommands,
                TelephonyManager.RADIO_POWER_UNAVAILABLE);
        mMultiSimSettingControllerUT.obtainMessage(
                MultiSimSettingController.EVENT_RADIO_STATE_CHANGED).sendToTarget();

        // Mark all subs as inactive.
        doReturn(false).when(mSubControllerMock).isActiveSubId(1);
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(1);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock1).getSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = new ArrayList<>();
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());
        clearInvocations(mSubControllerMock);

        // The below sub info change should be ignored.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubControllerMock, never()).setDefaultSmsSubId(anyInt());

        // Send all sub ready notification
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();

        // Everything should be set to invalid since nothing is active.
        verify(mSubControllerMock).setDefaultDataSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubControllerMock)
                .setDefaultVoiceSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubControllerMock).setDefaultSmsSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Test
    @SmallTest
    public void testSingleActiveDsds() throws Exception {
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultDataSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultVoiceSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubControllerMock)
                .getDefaultSmsSubId();

        // Mark sub 2 as inactive.
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        processAllMessages();
        verifyDismissIntentSent();
        clearInvocations(mContext);

        // Sub 1 should be default sub silently.
        // Sub 1 switches to sub 2 in the same slot.
        doReturn(false).when(mSubControllerMock).isActiveSubId(1);
        doReturn(true).when(mSubControllerMock).isActiveSubId(2);
        doReturn(0).when(mSubControllerMock).getPhoneId(2);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(1);
        doReturn(2).when(mPhoneMock1).getSubId();
        infoList = Arrays.asList(mSubInfo2);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{2}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 2);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubControllerMock).setDefaultDataSubId(2);
        verify(mSubControllerMock).setDefaultVoiceSubId(2);
        verify(mSubControllerMock).setDefaultSmsSubId(2);
        verifyDismissIntentSent();
    }

    @Test
    @SmallTest
    public void testActivatingSecondSub() throws Exception {
        // Mark sub 2 as inactive.
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubControllerMock).setDefaultDataSubId(1);
        verify(mSubControllerMock).setDefaultVoiceSubId(1);
        verify(mSubControllerMock).setDefaultSmsSubId(1);
        verifyDismissIntentSent();

        // Mark sub 2 as active in phone[1].
        clearInvocations(mSubControllerMock);
        clearInvocations(mContext);
        doReturn(true).when(mSubControllerMock).isActiveSubId(2);
        doReturn(1).when(mSubControllerMock).getPhoneId(2);
        doReturn(2).when(mPhoneMock2).getSubId();
        infoList = Arrays.asList(mSubInfo1, mSubInfo2);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1, 2}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();

        // Intent should be broadcast to ask default data selection.
        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));

        clearInvocations(mContext);
        // Switch from sub 2 to sub 3 in phone[1]. This should again trigger default data selection
        // dialog.
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(true).when(mSubControllerMock).isActiveSubId(3);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(1).when(mSubControllerMock).getPhoneId(3);
        doReturn(3).when(mPhoneMock2).getSubId();
        infoList = Arrays.asList(mSubInfo1, mSubInfo3);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1, 3}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 3);
        processAllMessages();

        // Intent should be broadcast to ask default data selection.
        intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
    }

    @Test
    @SmallTest
    public void testSimpleDsds() {
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // After initialization, sub 2 should have mobile data off.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataEnabledSettingsMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        // Enable on non-default sub should trigger setDefaultDataSubId.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mSubControllerMock).setDefaultDataSubId(2);

        // Changing default data to sub 2 should trigger disabling data on sub 1.
        doReturn(2).when(mSubControllerMock).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataEnabledSettingsMock1).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        doReturn(1).when(mSubControllerMock).getDefaultDataSubId();
        doReturn(1).when(mSubControllerMock).getDefaultSmsSubId();
        doReturn(2).when(mSubControllerMock).getDefaultVoiceSubId();

        // Taking out SIM 1.
        clearInvocations(mSubControllerMock);
        doReturn(false).when(mSubControllerMock).isActiveSubId(1);
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo2);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{2}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(
                1, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        verify(mSubControllerMock).setDefaultDataSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubControllerMock).setDefaultSmsSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubControllerMock, never()).setDefaultVoiceSubId(anyInt());

        // Verify intent sent to select sub 2 as default for all types.
        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
        assertEquals(2, intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1));
    }

    @Test
    @SmallTest
    public void testSimpleDsdsFirstBoot() {
        // at first boot default is not set
        doReturn(-1).when(mSubControllerMock).getDefaultDataSubId();

        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // After initialization, sub 2 should have mobile data off.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataEnabledSettingsMock1).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        verify(mDataEnabledSettingsMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        // as a result of the above calls, update new values to be returned
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();

        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));

        // Setting default data should not trigger any more setDataEnabled().
        doReturn(2).when(mSubControllerMock).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataEnabledSettingsMock1, times(1)).setDataEnabled(anyInt(), anyBoolean());
        verify(mDataEnabledSettingsMock2, times(1)).setDataEnabled(anyInt(), anyBoolean());
    }

    @Test
    @SmallTest
    public void testDsdsGrouping() {
        doReturn(2).when(mSubControllerMock).getDefaultDataSubId();
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();

        // Create subscription grouping.
        doReturn(mGroupUuid1).when(mSubControllerMock).getGroupUuid(2);
        doReturn(mGroupUuid1).when(mSubControllerMock).getGroupUuid(3);
        doReturn(mGroupUuid1).when(mSubControllerMock).getGroupUuid(4);
        doReturn(Arrays.asList(mSubInfo2, mSubInfo3, mSubInfo4)).when(mSubControllerMock)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // This should result in setting sync.
        assertTrue(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.MOBILE_DATA, 3, false));
        assertTrue(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.MOBILE_DATA, 4, false));
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 3, true));
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 4, true));
        verify(mSubControllerMock).setDataRoaming(/*enable*/0, /*subId*/2);
        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());

        // Making sub 1 default data sub should result in disabling data on sub 2, 3, 4.
        doReturn(1).when(mSubControllerMock).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataEnabledSettingsMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, false);
        processAllMessages();
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.MOBILE_DATA, 3, true));
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.MOBILE_DATA, 4, true));
        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());

        // Switch within group (from sub 2 to sub 3).
        // Default data and default sms should become subscription 3.
        clearInvocations(mSubControllerMock);
        doReturn(2).when(mSubControllerMock).getDefaultDataSubId();
        doReturn(2).when(mSubControllerMock).getDefaultSmsSubId();
        doReturn(1).when(mSubControllerMock).getDefaultVoiceSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1, mSubInfo3);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1, 3}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 3);
        processAllMessages();

        verify(mSubControllerMock).setDefaultDataSubId(3);
        verify(mSubControllerMock).setDefaultSmsSubId(3);
        verify(mSubControllerMock, never()).setDefaultVoiceSubId(anyInt());
        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());
    }

    @Test
    @SmallTest
    public void testCbrs() throws Exception {
        replaceInstance(SubscriptionInfo.class, "mIsOpportunistic", mSubInfo1, true);
        doReturn(true).when(mSubControllerMock).isOpportunistic(1);
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);

        // Notify subscriptions ready. Sub 2 should become the default. But shouldn't turn off
        // data of oppt sub 1.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubControllerMock).setDefaultDataSubId(2);
        verify(mDataEnabledSettingsMock1, never()).setDataEnabled(
                anyInt(), anyBoolean());
        verifyDismissIntentSent();

        clearInvocations(mSubControllerMock);
        clearInvocations(mDataEnabledSettingsMock1);
        clearInvocations(mDataEnabledSettingsMock2);
        doReturn(2).when(mSubControllerMock).getDefaultDataSubId();
        // Toggle data on sub 1 or sub 2. Nothing should happen as they are independent.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, false);
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, true);
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, false);
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        verify(mDataEnabledSettingsMock1, never()).setDataEnabled(
                eq(TelephonyManager.DATA_ENABLED_REASON_USER), anyBoolean());
        verify(mDataEnabledSettingsMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
    }

    private void verifyDismissIntentSent() {
        Intent intentSent = captureBroadcastIntent();
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DISMISS,
                intentSent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intentSent.getAction());
    }

    @Test
    @SmallTest
    public void testGroupedCbrs() throws Exception {
        // Mark sub 1 as opportunistic.
        replaceInstance(SubscriptionInfo.class, "mIsOpportunistic", mSubInfo1, true);
        replaceInstance(SubscriptionInfo.class, "mGroupUUID", mSubInfo1, mGroupUuid1);
        doReturn(true).when(mSubControllerMock).isOpportunistic(1);
        // Make opportunistic sub 1 and sub 2 data enabled.
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);

        // Notify subscriptions ready. Sub 2 should become the default, as sub 1 is opportunistic.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubControllerMock).setDefaultDataSubId(2);

        // Mark sub 2 as data off.
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, false);
        // Group sub 1 with sub 2.
        doReturn(Arrays.asList(mSubInfo1, mSubInfo2)).when(mSubControllerMock)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // This should result in setting sync.
        verify(mDataEnabledSettingsMock1).setUserDataEnabled(false, false);
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 1, true));

        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        // Turning data on on sub 2. Sub 1 should also be turned on.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mDataEnabledSettingsMock1).setUserDataEnabled(true, false);
        verifyDismissIntentSent();
    }

    @Test
    @SmallTest
    public void testGroupedPrimaryRemoved() throws Exception {
        // Create subscription grouping of subs 1 and 2.
        replaceInstance(SubscriptionInfo.class, "mGroupUUID", mSubInfo1, mGroupUuid1);
        doReturn(mGroupUuid1).when(mSubControllerMock).getGroupUuid(1);
        doReturn(mGroupUuid1).when(mSubControllerMock).getGroupUuid(2);
        doReturn(Arrays.asList(mSubInfo1, mSubInfo2)).when(mSubControllerMock)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();

        // Defaults not touched, sub 1 is already default.
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());

        // Take out SIM 1.
        clearInvocations(mSubControllerMock);
        doReturn(false).when(mSubControllerMock).isActiveSubId(1);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(1);
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mPhoneMock1).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo2);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{2}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(
                0, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();

        // Sub 2 should be made the default sub silently.
        verify(mSubControllerMock).setDefaultDataSubId(2);
        verify(mSubControllerMock).setDefaultVoiceSubId(2);
        verify(mSubControllerMock).setDefaultSmsSubId(2);
        verifyDismissIntentSent();
    }

    private Intent captureBroadcastIntent() {
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCapture.capture());
        return intentCapture.getValue();
    }

    @Test
    @SmallTest
    public void testGroupedPrimarySubscriptions() throws Exception {
        doReturn(1).when(mSubControllerMock).getDefaultDataSubId();
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 1, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 1, false);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();

        // Create subscription grouping.
        replaceInstance(SubscriptionInfo.class, "mGroupUUID", mSubInfo1, mGroupUuid1);
        doReturn(Arrays.asList(mSubInfo1, mSubInfo2)).when(mSubControllerMock)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // This should result in setting sync.
        verify(mDataEnabledSettingsMock2).setUserDataEnabled(true, false);
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 2, true));
        verify(mSubControllerMock).setDataRoaming(/*enable*/0, /*subId*/1);

        // Turning off user data on sub 1.
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, false);
        processAllMessages();
        verify(mDataEnabledSettingsMock2).setUserDataEnabled(false, false);
    }

    @Test
    @SmallTest
    public void testCarrierConfigLoading() throws Exception {
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // Sub 2 should have mobile data off, but it shouldn't happen until carrier configs are
        // loaded on both subscriptions.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();
        verify(mDataEnabledSettingsMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        processAllMessages();
        verify(mDataEnabledSettingsMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataEnabledSettingsMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        // Switch from sub 2 to sub 3 in phone[1].
        clearInvocations(mSubControllerMock);
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(true).when(mSubControllerMock).isActiveSubId(3);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(1).when(mSubControllerMock).getPhoneId(3);
        doReturn(3).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1, mSubInfo3);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1, 3}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());

        // Nothing should happen until carrier config change is notified on sub 3.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mContext, never()).sendBroadcast(any());

        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 3);
        processAllMessages();
        // Intent should be broadcast to ask default data selection.
        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
    }

    @Test
    @SmallTest
    // b/146446143
    public void testGroupChangeOnInactiveSub_shouldNotMarkAsDefaultDataSub() throws Exception {
        // Make sub1 and sub3 as active sub.
        doReturn(false).when(mSubControllerMock).isActiveSubId(2);
        doReturn(true).when(mSubControllerMock).isActiveSubId(3);
        doReturn(SubscriptionManager.INVALID_PHONE_INDEX).when(mSubControllerMock).getPhoneId(2);
        doReturn(1).when(mSubControllerMock).getPhoneId(3);
        doReturn(3).when(mPhoneMock2).getSubId();
        List<SubscriptionInfo> infoList = Arrays.asList(mSubInfo1, mSubInfo3);
        doReturn(infoList).when(mSubControllerMock).getActiveSubscriptionInfoList(anyString(),
                nullable(String.class));
        doReturn(new int[]{1, 3}).when(mSubControllerMock).getActiveSubIdList(anyBoolean());
        doReturn(Arrays.asList(mSubInfo2, mSubInfo3, mSubInfo4)).when(mSubControllerMock)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));

        // Sub 3 and sub 2's mobile data are enabled, and sub 3 is the default data sub.
        doReturn(3).when(mSubControllerMock).getDefaultDataSubId();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 1, false);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 3, true);
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // Sub 2 should have mobile data off, but it shouldn't happen until carrier configs are
        // loaded on both subscriptions.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 3);
        processAllMessages();

        // Mark sub3 as oppt and notify grouping
        doReturn(true).when(mSubControllerMock).isOpportunistic(3);
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // Shouldn't mark sub 2 as default data, as sub 2 is in active.
        verify(mSubControllerMock, never()).setDefaultDataSubId(2);
    }

    @Test
    @SmallTest
    // b/146446143
    public void testCarrierConfigChangeWithInvalidSubId_shouldAlwaysTryToGetSubId()
            throws Exception {
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0, 1);
        // Notify carrier config change on phone1 without specifying subId.
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        // Nothing should happen as carrier config is not ready for sub 2.
        verify(mDataEnabledSettingsMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);

        // Still notify carrier config without specifying subId2, but this time subController
        // and CarrierConfigManager have subId 2 active and ready.
        doReturn(new int[] {2}).when(mSubControllerMock).getSubId(1);
        CarrierConfigManager cm = (CarrierConfigManager) mContext.getSystemService(
                mContext.CARRIER_CONFIG_SERVICE);
        doReturn(new PersistableBundle()).when(cm).getConfigForSubId(2);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        // This time user data should be disabled on phone1.
        verify(mDataEnabledSettingsMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false);
    }

    @Test
    @SmallTest
    public void testOnPhoneRemoved() {
        try {
            mMultiSimSettingControllerUT.onPhoneRemoved();
        } catch (RuntimeException re) {
            Assert.fail("Exception not expected when calling from the same thread");
        }
    }

    @Test
    @SmallTest
    public void testOnPhoneRemoved_DifferentThread() {
        AtomicBoolean result = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        HandlerThread handlerThread = new HandlerThread("MultiSimSettingControllerTest") {
            public void onLooperPrepared() {
                try {
                    mMultiSimSettingControllerUT.onPhoneRemoved();
                } catch (RuntimeException re) {
                    result.set(true); // true to indicate that the test passed
                }
                latch.countDown();
            }
        };
        handlerThread.start();
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Assert.fail("CountDownLatch did not reach 0");
            } else if (!result.get()) {
                Assert.fail("Exception expected when not calling from the same thread");
            }
        } catch (InterruptedException ie) {
            Assert.fail("InterruptedException during latch.await");
        }
    }

    @Test
    @SmallTest
    public void testVoiceDataSmsAutoFallback() throws Exception {
        doReturn(1).when(mSubControllerMock).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0,1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(2, 3);
        processAllMessages();
        verify(mSubControllerMock, never()).setDefaultDataSubId(anyInt());
        verify(mSubControllerMock, never()).getActiveSubInfoCountMax();
        doReturn(2).when(mSubControllerMock).getActiveSubInfoCountMax();
        mPhoneMock1.mCi = mMockCi;
        mPhoneMock2.mCi = mMockCi;
        doReturn(TelephonyManager.RADIO_POWER_ON).when(mMockCi).getRadioState();
        doReturn(false).when(mPhoneMock1).isShuttingDown();
        doReturn(false).when(mPhoneMock2).isShuttingDown();
        android.provider.Settings.Global.putInt(InstrumentationRegistry.getTargetContext().
                 getContentResolver(), "user_preferred_data_sub", 2);
        Resources resources = mContext.getResources();
        doReturn(true).when(resources).getBoolean(
                com.android.internal.R.bool.config_voice_data_sms_auto_fallback);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(0,1);
        mMultiSimSettingControllerUT.notifyCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubControllerMock).getActiveSubInfoCountMax();
        verify(mSubControllerMock).setDefaultDataSubId(anyInt());
    }
}
