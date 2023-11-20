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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncResult;
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

import com.android.internal.telephony.data.DataSettingsManager;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.test.SimulatedCommands;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

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
    private static final int DUAL_SIM = 2;
    private static final String PHONE_PACKAGE = "com.android.internal.telephony";
    private MultiSimSettingController mMultiSimSettingControllerUT;
    private Phone[] mPhones;
    private ParcelUuid mGroupUuid1 = new ParcelUuid(UUID.randomUUID());
    private CarrierConfigManager.CarrierConfigChangeListener mCarrierConfigChangeListener;

    // Mocked classes
    private Phone mPhoneMock1;
    private Phone mPhoneMock2;
    private DataSettingsManager mDataSettingsManagerMock1;
    private DataSettingsManager mDataSettingsManagerMock2;
    private CommandsInterface mMockCi;

    private final SubscriptionInfoInternal[] mSubInfo = new SubscriptionInfoInternal[10];

    private void initializeSubs() {
        mSubInfo[1] = new SubscriptionInfoInternal.Builder()
                .setId(1)
                .setIccId("subInfo1 IccId")
                .setSimSlotIndex(0)
                .setDisplayName("T-mobile")
                .setCarrierName("T-mobile")
                .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_CARRIER)
                .setIconTint(255)
                .setNumber("12345")
                .setMcc("310")
                .setMnc("260")
                .setCountryIso("us")
                .build();

        mSubInfo[2] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setId(2)
                .setIccId("subInfo2 IccId")
                .setSimSlotIndex(1)
                .setGroupUuid(mGroupUuid1.toString())
                .build();

        mSubInfo[3] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setId(3)
                .setIccId("subInfo3 IccId")
                .setSimSlotIndex(-1)
                .setGroupUuid(mGroupUuid1.toString())
                .build();

        mSubInfo[4] = new SubscriptionInfoInternal.Builder(
                mSubInfo[1])
                .setId(4)
                .setIccId("subInfo4 IccId")
                .setSimSlotIndex(-1)
                .setGroupUuid(mGroupUuid1.toString())
                .build();
    }

    private void setSimSlotIndex(int subId, int simSlotIndex) {
        mSubInfo[subId] = new SubscriptionInfoInternal.Builder(mSubInfo[subId])
                .setSimSlotIndex(simSlotIndex).build();
    }

    private void sendCarrierConfigChanged(int phoneId, int subId) {
        mCarrierConfigChangeListener.onCarrierConfigChanged(phoneId, subId,
                TelephonyManager.UNKNOWN_CARRIER_ID, TelephonyManager.UNKNOWN_CARRIER_ID);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        initializeSubs();
        mPhoneMock1 = mock(Phone.class);
        mPhoneMock2 = mock(Phone.class);
        mDataSettingsManagerMock1 = mock(DataSettingsManager.class);
        mDataSettingsManagerMock2 = mock(DataSettingsManager.class);
        mMockCi = mock(CommandsInterface.class);

        doReturn(mSubscriptionManagerService).when(mIBinder).queryLocalInterface(anyString());
        doReturn(mPhone).when(mPhone).getImsPhone();
        mServiceManagerMockedServices.put("isub", mIBinder);

        doReturn(mSubscriptionManagerService).when(mIBinder)
                .queryLocalInterface(anyString());

        // Default configuration:
        // DSDS device.
        // Sub 1 is the default sub.
        // Sub 1 is in slot 0; sub 2 is in slot 1.
        doReturn(DUAL_SIM).when(mTelephonyManager).getPhoneCount();
        doReturn(DUAL_SIM).when(mTelephonyManager).getActiveModemCount();
        doReturn(1).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(1).when(mSubscriptionManagerService).getDefaultVoiceSubId();
        doReturn(1).when(mSubscriptionManagerService).getDefaultSmsSubId();
        mPhoneMock1.mCi = mSimulatedCommands;
        mPhoneMock2.mCi = mSimulatedCommands;

        mPhones = new Phone[] {mPhoneMock1, mPhoneMock2};
        doReturn(mDataSettingsManagerMock1).when(mPhoneMock1).getDataSettingsManager();
        doReturn(mDataSettingsManagerMock2).when(mPhoneMock2).getDataSettingsManager();

        doAnswer(invocation -> {
            final int subId = (int) invocation.getArguments()[0];
            if (subId < 0 || subId >= mSubInfo.length) return null;
            return mSubInfo[subId].toSubscriptionInfo();
        }).when(mSubscriptionManagerService).getSubscriptionInfo(anyInt());

        doAnswer(invocation -> {
            final int subId = (int) invocation.getArguments()[0];
            if (subId < 0 || subId >= mSubInfo.length) return null;
            return mSubInfo[subId];
        }).when(mSubscriptionManagerService).getSubscriptionInfoInternal(anyInt());

        doAnswer(invocation -> {
            List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
            for (int i = 1; i < mSubInfo.length; i++) {
                if (mSubInfo[i] != null && mSubInfo[i].isActive()) {
                    subscriptionInfoList.add(mSubInfo[i].toSubscriptionInfo());
                }
            }
            return subscriptionInfoList;
        }).when(mSubscriptionManagerService).getActiveSubscriptionInfoList(
                anyString(), nullable(String.class), anyBoolean());

        doAnswer(invocation -> {
            final boolean visibleOnly = (boolean) invocation.getArguments()[0];
            List<Integer> subIdList = new ArrayList<>();
            for (int i = 1; i < mSubInfo.length; i++) {
                if (mSubInfo[i] != null && mSubInfo[i].isActive()
                        && (!visibleOnly || mSubInfo[i].isVisible())) {
                    subIdList.add(i);
                }
            }
            return subIdList.stream().mapToInt(i -> i).toArray();
        }).when(mSubscriptionManagerService).getActiveSubIdList(anyBoolean());

        doAnswer(invocation -> {
            final String uuid = (String) invocation.getArguments()[1];
            List<SubscriptionInfo> subscriptionInfoList = new ArrayList<>();
            for (int i = 1; i < mSubInfo.length; i++) {
                if (mSubInfo[i] != null && mSubInfo[i].getGroupUuid().equals(uuid)) {
                    subscriptionInfoList.add(mSubInfo[i].toSubscriptionInfo());
                }
            }
            return subscriptionInfoList;
        }).when(mSubscriptionManagerService).getSubscriptionsInGroup(
                any(), anyString(), nullable(String.class));

        doAnswer(invocation -> {
            final int subId = (int) invocation.getArguments()[0];
            if (!SubscriptionManager.isValidSubscriptionId(subId)) {
                return SubscriptionManager.INVALID_PHONE_INDEX;
            }
            if (mSubInfo[subId] == null) return SubscriptionManager.INVALID_PHONE_INDEX;
            return mSubInfo[subId].getSimSlotIndex();
        }).when(mSubscriptionManagerService).getPhoneId(anyInt());

        doAnswer(invocation -> {
            for (int i = 1; i < mSubInfo.length; i++) {
                if (mSubInfo[i] != null && mSubInfo[i].getSimSlotIndex() == 0) return i;
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }).when(mPhoneMock1).getSubId();

        doAnswer(invocation -> {
            for (int i = 1; i < mSubInfo.length; i++) {
                if (mSubInfo[i] != null && mSubInfo[i].getSimSlotIndex() == 1) return i;
            }
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }).when(mPhoneMock2).getSubId();

        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        // Capture listener to emulate the carrier config change notification used later
        ArgumentCaptor<CarrierConfigManager.CarrierConfigChangeListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(CarrierConfigManager.CarrierConfigChangeListener.class);
        mMultiSimSettingControllerUT = new MultiSimSettingController(mContext);
        processAllMessages();
        verify(mCarrierConfigManager).registerCarrierConfigChangeListener(any(),
                listenerArgumentCaptor.capture());
        mCarrierConfigChangeListener = listenerArgumentCaptor.getAllValues().get(0);
        assertNotNull(mCarrierConfigChangeListener);
    }

    @After
    public void tearDown() throws Exception {
        mPhones = null;
        mGroupUuid1 = null;
        mMultiSimSettingControllerUT = null;
        super.tearDown();
    }

    private void markSubscriptionInactive(int subId) {
        setSimSlotIndex(subId, SubscriptionManager.INVALID_SIM_SLOT_INDEX);
    }

    @Test
    @SmallTest
    public void testSubInfoChangeBeforeAllSubReady() throws Exception {
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultDataSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultVoiceSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultSmsSubId();

        // Mark sub 2 as inactive.
        markSubscriptionInactive(2);

        // Mark subscription ready as false. The below sub info change should be ignored.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultSmsSubId(anyInt());

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubscriptionManagerService).setDefaultDataSubId(1);
        verify(mSubscriptionManagerService).setDefaultVoiceSubId(1);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(1);
        verifyDismissIntentSent();
    }

    @Test
    public void testSubInfoChangeAfterRadioUnavailable() throws Exception {
        int phone1SubId = 1;
        int phone2SubId = 2;
        // Mock DSDS, mock Phone 2
        SimulatedCommands simulatedCommands2 = mock(SimulatedCommands.class);
        mPhone2.mCi = simulatedCommands2;
        doReturn(mDataSettingsManagerMock2).when(mPhone2).getDataSettingsManager();
        mPhones = new Phone[]{mPhone, mPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        // Load carrier config for all subs
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, phone1SubId);
        sendCarrierConfigChanged(1, phone2SubId);
        processAllMessages();

        // Ensure all subscription loaded only updates state once
        clearInvocations(mSubscriptionManagerService);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultSmsSubId(anyInt());

        // DSDS -> single active modem, radio available on phone 0 but unavailable on phone 1
        doReturn(TelephonyManager.RADIO_POWER_UNAVAILABLE).when(simulatedCommands2).getRadioState();
        markSubscriptionInactive(phone2SubId);
        AsyncResult result = new AsyncResult(null, 1/*activeModemCount*/, null);
        clearInvocations(mSubscriptionManagerService);
        mMultiSimSettingControllerUT.obtainMessage(
                MultiSimSettingController.EVENT_MULTI_SIM_CONFIG_CHANGED, result).sendToTarget();
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();

        // Should still set defaults to the only remaining sub
        verify(mSubscriptionManagerService).setDefaultDataSubId(phone1SubId);
        verify(mSubscriptionManagerService).setDefaultVoiceSubId(phone1SubId);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(phone1SubId);

        // Notify radio unavailable on all subs.
        replaceInstance(BaseCommands.class, "mState", mSimulatedCommands,
                TelephonyManager.RADIO_POWER_UNAVAILABLE);
        mMultiSimSettingControllerUT.obtainMessage(
                MultiSimSettingController.EVENT_RADIO_STATE_CHANGED).sendToTarget();

        // Mark all subs as inactive.
        markSubscriptionInactive(1);
        clearInvocations(mSubscriptionManagerService);

        // The below sub info change should be ignored.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultVoiceSubId(anyInt());
        verify(mSubscriptionManagerService, never()).setDefaultSmsSubId(anyInt());

        // Send all sub ready notification
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();

        // Everything should be set to invalid since nothing is active.
        verify(mSubscriptionManagerService).setDefaultDataSubId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubscriptionManagerService)
                .setDefaultVoiceSubId(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    @Test
    @SmallTest
    public void testSingleActiveDsds() throws Exception {
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultDataSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultVoiceSubId();
        doReturn(SubscriptionManager.INVALID_SUBSCRIPTION_ID).when(mSubscriptionManagerService)
                .getDefaultSmsSubId();

        // Mark sub 2 as inactive.
        markSubscriptionInactive(2);

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        processAllMessages();
        verifyDismissIntentSent();
        clearInvocations(mContext);

        // Sub 1 should be default sub silently.
        // Sub 1 switches to sub 2 in the same slot.
        markSubscriptionInactive(1);
        setSimSlotIndex(2, 0);

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        sendCarrierConfigChanged(0, 2);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubscriptionManagerService).setDefaultDataSubId(2);
        verify(mSubscriptionManagerService).setDefaultVoiceSubId(2);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(2);
        verifyDismissIntentSent();
    }

    @Test
    @SmallTest
    public void testActivatingSecondSub() throws Exception {
        // Mark sub 2 as inactive.
        markSubscriptionInactive(2);

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        processAllMessages();

        // Sub 1 should be default sub silently.
        verify(mSubscriptionManagerService).setDefaultDataSubId(1);
        verify(mSubscriptionManagerService).setDefaultVoiceSubId(1);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(1);
        verifyDismissIntentSent();

        // Mark sub 2 as active in phone[1].
        setSimSlotIndex(2, 1);
        clearInvocations(mSubscriptionManagerService);
        clearInvocations(mContext);
        mSubInfo[2] = new SubscriptionInfoInternal.Builder().setId(2).setSimSlotIndex(1).build();

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        sendCarrierConfigChanged(1, 2);
        processAllMessages();

        // Intent should be broadcast to ask default data selection.
        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));

        clearInvocations(mContext);
        // Switch from sub 2 to sub 3 in phone[1]. This should again trigger default data selection
        // dialog.
        markSubscriptionInactive(2);
        setSimSlotIndex(3, 1);

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        sendCarrierConfigChanged(1, 3);
        processAllMessages();

        // Intent should be broadcast to ask default data selection.
        intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
    }

    @Test
    @SmallTest
    public void testSimpleDsds() throws Exception {
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // After initialization, sub 2 should have mobile data off.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        // Enable on non-default sub should trigger setDefaultDataSubId.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mSubscriptionManagerService).setDefaultDataSubId(2);

        // Changing default data to sub 2 should trigger disabling data on sub 1.
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataSettingsManagerMock1).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        doReturn(1).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(1).when(mSubscriptionManagerService).getDefaultSmsSubId();
        doReturn(2).when(mSubscriptionManagerService).getDefaultVoiceSubId();

        // Taking out SIM 1.
        clearInvocations(mSubscriptionManagerService);
        markSubscriptionInactive(1);
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        sendCarrierConfigChanged(1, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();

        verify(mSubscriptionManagerService).setDefaultDataSubId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        verify(mSubscriptionManagerService, never()).setDefaultVoiceSubId(anyInt());

        // Verify intent sent to select sub 2 as default for all types.
        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_ALL,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));
        assertEquals(2, intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1));
    }

    @Test
    @SmallTest
    public void testSimpleDsdsFirstBoot() throws Exception {
        // at first boot default is not set
        doReturn(-1).when(mSubscriptionManagerService).getDefaultDataSubId();

        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // After initialization, sub 2 should have mobile data off.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataSettingsManagerMock1).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        // as a result of the above calls, update new values to be returned
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();

        Intent intent = captureBroadcastIntent();
        assertEquals(ACTION_PRIMARY_SUBSCRIPTION_LIST_CHANGED, intent.getAction());
        assertEquals(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE_DATA,
                intent.getIntExtra(EXTRA_DEFAULT_SUBSCRIPTION_SELECT_TYPE, -1));

        // Setting default data should not trigger any more setDataEnabled().
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataSettingsManagerMock1, times(1))
                .setDataEnabled(anyInt(), anyBoolean(), anyString());
        verify(mDataSettingsManagerMock2, times(1))
                .setDataEnabled(anyInt(), anyBoolean(), anyString());
    }

    @Test
    @SmallTest
    public void testSimpleDsdsInSuW() throws Exception {
        // at first boot default is not set
        doReturn(-1).when(mSubscriptionManagerService).getDefaultDataSubId();

        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // setting DEVICE_PROVISIONED as 0 to indicate SuW is running.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0);
        // After initialization, sub 2 should have mobile data off.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataSettingsManagerMock1).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        // as a result of the above calls, update new values to be returned
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();

        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());
    }

    @Test
    @SmallTest
    public void testDsdsGrouping() throws Exception {
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();

        // Create subscription grouping.
        doReturn(Arrays.asList(mSubInfo[2].toSubscriptionInfo(), mSubInfo[3].toSubscriptionInfo(),
                mSubInfo[4].toSubscriptionInfo())).when(mSubscriptionManagerService)
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
        verify(mSubscriptionManagerService).setDataRoaming(/*enable*/0, /*subId*/2);
        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());

        // Making sub 1 default data sub should result in disabling data on sub 2, 3, 4.
        doReturn(1).when(mSubscriptionManagerService).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyDefaultDataSubChanged();
        processAllMessages();
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
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
        clearInvocations(mSubscriptionManagerService);
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(2).when(mSubscriptionManagerService).getDefaultSmsSubId();
        doReturn(1).when(mSubscriptionManagerService).getDefaultVoiceSubId();
        setSimSlotIndex(3, 1);
        markSubscriptionInactive(2);

        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        sendCarrierConfigChanged(1, 3);
        processAllMessages();

        verify(mSubscriptionManagerService).setDefaultDataSubId(3);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(3);
        verify(mSubscriptionManagerService, never()).setDefaultVoiceSubId(anyInt());
        // No user selection needed, no intent should be sent.
        verify(mContext, never()).sendBroadcast(any());
    }

    @Test
    @SmallTest
    public void testCbrs() throws Exception {
        mSubInfo[1] = new SubscriptionInfoInternal.Builder(mSubInfo[1]).setOpportunistic(1).build();

        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);

        // Notify subscriptions ready. Sub 2 should become the default. But shouldn't turn off
        // data of oppt sub 1.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubscriptionManagerService).setDefaultDataSubId(2);
        verify(mDataSettingsManagerMock1, never()).setDataEnabled(
                anyInt(), anyBoolean(), anyString());
        verifyDismissIntentSent();

        clearInvocations(mSubscriptionManagerService);
        clearInvocations(mDataSettingsManagerMock1);
        clearInvocations(mDataSettingsManagerMock2);
        doReturn(2).when(mSubscriptionManagerService).getDefaultDataSubId();
        // Toggle data on sub 1 or sub 2. Nothing should happen as they are independent.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, false);
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, true);
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, false);
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        verify(mDataSettingsManagerMock1, never()).setDataEnabled(
                eq(TelephonyManager.DATA_ENABLED_REASON_USER), anyBoolean(), anyString());
        verify(mDataSettingsManagerMock2, never()).setDataEnabled(
                eq(TelephonyManager.DATA_ENABLED_REASON_USER), eq(false), anyString());
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
        mSubInfo[1] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setOpportunistic(1).setGroupUuid(mGroupUuid1.toString()).build();
        // Make opportunistic sub 1 and sub 2 data enabled.
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 2, false);

        // Notify subscriptions ready. Sub 2 should become the default, as sub 1 is opportunistic.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubscriptionManagerService).setDefaultDataSubId(2);

        // Mark sub 2 as data off.
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, false);
        // Group sub 1 with sub 2.
        doReturn(Arrays.asList(mSubInfo[1].toSubscriptionInfo(), mSubInfo[2].toSubscriptionInfo()))
                .when(mSubscriptionManagerService)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // This should result in setting sync.
        verify(mDataSettingsManagerMock1).setDataEnabled(TelephonyManager.DATA_ENABLED_REASON_USER,
                false, PHONE_PACKAGE);
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 1, true));

        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        // Turning data on on sub 2. Sub 1 should also be turned on.
        mMultiSimSettingControllerUT.notifyUserDataEnabled(2, true);
        processAllMessages();
        verify(mDataSettingsManagerMock1).setDataEnabled(TelephonyManager.DATA_ENABLED_REASON_USER,
                true, PHONE_PACKAGE);
        verifyDismissIntentSent();
    }

    @Test
    @SmallTest
    public void testGroupedPrimaryRemoved() throws Exception {
        // Create subscription grouping of subs 1 and 2.
        mSubInfo[1] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setGroupUuid(mGroupUuid1.toString()).build();

        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();

        // Defaults not touched, sub 1 is already default.
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());

        // Take out SIM 1.
        clearInvocations(mSubscriptionManagerService);
        markSubscriptionInactive(1);
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        sendCarrierConfigChanged(0, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();

        // Sub 2 should be made the default sub silently.
        verify(mSubscriptionManagerService).setDefaultDataSubId(2);
        verify(mSubscriptionManagerService).setDefaultVoiceSubId(2);
        verify(mSubscriptionManagerService).setDefaultSmsSubId(2);
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
        doReturn(1).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(false).when(mPhoneMock2).isUserDataEnabled();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 1, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.DATA_ROAMING, 1, false);
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();

        // Create subscription grouping.
        mSubInfo[1] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setGroupUuid(mGroupUuid1.toString()).build();
        doReturn(Arrays.asList(mSubInfo[1].toSubscriptionInfo(), mSubInfo[2].toSubscriptionInfo()))
                .when(mSubscriptionManagerService).getSubscriptionsInGroup(any(), anyString(),
                        nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // This should result in setting sync.
        verify(mDataSettingsManagerMock2).setDataEnabled(TelephonyManager.DATA_ENABLED_REASON_USER,
                true, PHONE_PACKAGE);
        assertFalse(GlobalSettingsHelper.getBoolean(
                mContext, Settings.Global.DATA_ROAMING, 2, true));
        verify(mSubscriptionManagerService).setDataRoaming(/*enable*/0, /*subId*/1);

        // Turning off user data on sub 1.
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        mMultiSimSettingControllerUT.notifyUserDataEnabled(1, false);
        processAllMessages();
        verify(mDataSettingsManagerMock2).setDataEnabled(TelephonyManager.DATA_ENABLED_REASON_USER,
                false, PHONE_PACKAGE);
    }

    @Test
    @SmallTest
    public void testCarrierConfigLoading() throws Exception {
        doReturn(true).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        mSubInfo[2] = new SubscriptionInfoInternal.Builder(mSubInfo[2]).setGroupUuid("").build();
        mSubInfo[3] = new SubscriptionInfoInternal.Builder(mSubInfo[3]).setGroupUuid("").build();
        // Sub 2 should have mobile data off, but it shouldn't happen until carrier configs are
        // loaded on both subscriptions.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        processAllMessages();
        verify(mDataSettingsManagerMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
        sendCarrierConfigChanged(0, 1);
        processAllMessages();
        verify(mDataSettingsManagerMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        // Switch from sub 2 to sub 3 in phone[1].
        clearInvocations(mSubscriptionManagerService);
        markSubscriptionInactive(2);
        setSimSlotIndex(3, 1);

        // Nothing should happen until carrier config change is notified on sub 3.
        mMultiSimSettingControllerUT.notifySubscriptionInfoChanged();
        processAllMessages();
        verify(mContext, never()).sendBroadcast(any());

        sendCarrierConfigChanged(1, 3);
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
        markSubscriptionInactive(2);
        setSimSlotIndex(3, 1);
        doReturn(Arrays.asList(mSubInfo[2].toSubscriptionInfo(), mSubInfo[3].toSubscriptionInfo(),
                mSubInfo[4].toSubscriptionInfo())).when(mSubscriptionManagerService)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));

        // Sub 3 and sub 2's mobile data are enabled, and sub 3 is the default data sub.
        doReturn(3).when(mSubscriptionManagerService).getDefaultDataSubId();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 1, false);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 2, true);
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 3, true);
        doReturn(false).when(mPhoneMock1).isUserDataEnabled();
        doReturn(true).when(mPhoneMock2).isUserDataEnabled();
        // Sub 2 should have mobile data off, but it shouldn't happen until carrier configs are
        // loaded on both subscriptions.
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 3);
        processAllMessages();

        // Mark sub3 as oppt and notify grouping
        mSubInfo[3] = new SubscriptionInfoInternal.Builder(mSubInfo[3]).setOpportunistic(1).build();
        setSimSlotIndex(3, 0);
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // Shouldn't mark sub 2 as default data, as sub 2 is in active.
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(2);
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
        sendCarrierConfigChanged(0, 1);
        // Notify carrier config change on phone1 without specifying subId.
        sendCarrierConfigChanged(1, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        // Nothing should happen as carrier config is not ready for sub 2.
        verify(mDataSettingsManagerMock2, never()).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);

        // Still notify carrier config without specifying subId2, but this time subController
        // and CarrierConfigManager have subId 2 active and ready.
        doReturn(2).when(mSubscriptionManagerService).getSubId(1);
        CarrierConfigManager cm = (CarrierConfigManager) mContext.getSystemService(
                mContext.CARRIER_CONFIG_SERVICE);
        doReturn(new PersistableBundle()).when(cm).getConfigForSubId(2);
        sendCarrierConfigChanged(1, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        processAllMessages();
        // This time user data should be disabled on phone1.
        verify(mDataSettingsManagerMock2).setDataEnabled(
                TelephonyManager.DATA_ENABLED_REASON_USER, false, PHONE_PACKAGE);
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
        doReturn(1).when(mSubscriptionManagerService).getDefaultDataSubId();
        mMultiSimSettingControllerUT.notifyAllSubscriptionLoaded();
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(2, 2);
        processAllMessages();
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
        verify(mSubscriptionManagerService, never()).getActiveSubInfoCountMax();
        doReturn(2).when(mSubscriptionManagerService).getActiveSubInfoCountMax();
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
        sendCarrierConfigChanged(0, 1);
        sendCarrierConfigChanged(1, 2);
        processAllMessages();
        verify(mSubscriptionManagerService).getActiveSubInfoCountMax();
        verify(mSubscriptionManagerService).setDefaultDataSubId(anyInt());
    }

    @Test
    public void onSubscriptionGroupChanged_hasActiveSubNotPartOfGroup() {
        // sub1 and sub2 are active subs already
        // Create a subscription group with only sub2
        doReturn(Arrays.asList(mSubInfo[2].toSubscriptionInfo())).when(mSubscriptionManagerService)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));
        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // Default data is not modified as sub1 is active sub not part of this groupUuid
        verify(mSubscriptionManagerService, never()).setDefaultDataSubId(anyInt());
    }

    @Test
    public void onSubscriptionGroupChanged_allActiveSubArePartOfGroup() throws Exception {
        doReturn(3).when(mSubscriptionManagerService).getDefaultDataSubId();
        // Create subscription grouping of subs 1 and 2.
        mSubInfo[1] = new SubscriptionInfoInternal.Builder(mSubInfo[1])
                .setGroupUuid(mGroupUuid1.toString()).build();
        GlobalSettingsHelper.setBoolean(mContext, Settings.Global.MOBILE_DATA, 1, true);
        doReturn(Arrays.asList(mSubInfo[1].toSubscriptionInfo(), mSubInfo[2].toSubscriptionInfo()))
                .when(mSubscriptionManagerService)
                .getSubscriptionsInGroup(any(), anyString(), nullable(String.class));

        mMultiSimSettingControllerUT.notifySubscriptionGroupChanged(mGroupUuid1);
        processAllMessages();
        // Default data is set to sub1
        verify(mSubscriptionManagerService).syncGroupedSetting(1);
    }
}
