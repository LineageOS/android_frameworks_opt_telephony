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

import static android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED;
import static android.telephony.TelephonyManager.EXTRA_ACTIVE_SIM_SUPPORTED_COUNT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PhoneConfigurationManagerTest extends TelephonyTest {
    // Mocked classes
    Handler mHandler;
    CommandsInterface mMockCi0;
    CommandsInterface mMockCi1;
    private Phone mPhone1; // mPhone as phone 0 is already defined in TelephonyTest.
    PhoneConfigurationManager.MockableInterface mMi;

    private static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 1;
    private static final PhoneCapability STATIC_DSDA_CAPABILITY;
    PhoneConfigurationManager mPcm;
    private FeatureFlags mFeatureFlags;
    private TelephonyRegistryManager mMockRegistryManager;

    static {
        ModemInfo modemInfo1 = new ModemInfo(0, 0, true, true);
        ModemInfo modemInfo2 = new ModemInfo(1, 0, true, true);

        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo1);
        logicalModemList.add(modemInfo2);
        int[] deviceNrCapabilities = new int[0];

        STATIC_DSDA_CAPABILITY = new PhoneCapability(2, 1, logicalModemList, false,
                deviceNrCapabilities);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mHandler = mock(Handler.class);
        mMockCi0 = mock(CommandsInterface.class);
        mMockCi1 = mock(CommandsInterface.class);
        mFeatureFlags = Mockito.mock(FeatureFlags.class);
        mPhone1 = mock(Phone.class);
        mMi = mock(PhoneConfigurationManager.MockableInterface.class);
        mPhone.mCi = mMockCi0;
        mCT.mCi = mMockCi0;
        mPhone1.mCi = mMockCi1;
        doReturn(RIL.RADIO_HAL_VERSION_2_2).when(mMockRadioConfigProxy).getVersion();
        mMockRegistryManager = mContext.getSystemService(TelephonyRegistryManager.class);
    }

    @After
    public void tearDown() throws Exception {
        mPcm = null;
        super.tearDown();
    }

    private void setRebootRequiredForConfigSwitch(boolean rebootRequired) {
        doReturn(rebootRequired).when(mMi).isRebootRequiredForModemConfigChange();
    }

    private void init(int numOfSim) throws Exception {
        doReturn(numOfSim).when(mTelephonyManager).getActiveModemCount();
        replaceInstance(PhoneConfigurationManager.class, "sInstance", null, null);
        mPcm = PhoneConfigurationManager.init(mContext, mFeatureFlags);
        replaceInstance(PhoneConfigurationManager.class, "mMi", mPcm, mMi);
        processAllMessages();
    }

    /**
     * Test that a single phone case results in our phone being active and the RIL called
     */
    @Test
    @SmallTest
    public void testGetPhoneCount() throws Exception {
        init(1);
        doReturn(1).when(mTelephonyManager).getActiveModemCount();
        assertEquals(1, mPcm.getPhoneCount());
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        assertEquals(2, mPcm.getPhoneCount());
    }

    @Test
    @SmallTest
    public void testEnablePhone() throws Exception {
        init(1);
        // Phone is null. No crash.
        mPcm.enablePhone(null, true, null);

        Message message = new Message();
        mPcm.enablePhone(mPhone, false, message);
        verify(mMockCi0).enableModem(eq(false), eq(message));
    }

    @Test
    @SmallTest
    public void testGetDsdsCapability() throws Exception {
        init(1);
        assertEquals(PhoneCapability.DEFAULT_SSSS_CAPABILITY, mPcm.getStaticPhoneCapability());

        setAndVerifyStaticCapability(PhoneCapability.DEFAULT_DSDS_CAPABILITY);
    }

    @Test
    @SmallTest
    public void testConfigureAndGetMaxActiveVoiceSubscriptions() throws Exception {
        init(2);
        assertEquals(1, mPcm.getStaticPhoneCapability().getMaxActiveVoiceSubscriptions());

        PhoneCapability dualActiveVoiceSubCapability = new PhoneCapability.Builder(
                PhoneCapability.DEFAULT_DSDS_CAPABILITY)
                .setMaxActiveVoiceSubscriptions(2)
                .build();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).getPhoneCapability(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, dualActiveVoiceSubCapability, null);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(2, mPcm.getStaticPhoneCapability().getMaxActiveVoiceSubscriptions());
    }

    @Test
    @SmallTest
    public void testUpdateSimultaneousCallingSupport() throws Exception {
        doReturn(false).when(mFeatureFlags).simultaneousCallingIndications();
        init(2);
        mPcm.updateSimultaneousCallingSupport();

        int[] enabledLogicalSlots = {0, 1};
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).updateSimultaneousCallingSupport(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, enabledLogicalSlots, null);
        msg.sendToTarget();
        processAllMessages();

        HashSet<Integer> expectedSlots = new HashSet<>();
        for (int i : enabledLogicalSlots) { expectedSlots.add(i); }
        assertEquals(expectedSlots, mPcm.getSlotsSupportingSimultaneousCellularCalls());
    }

    @Test
    @SmallTest
    public void testUpdateSimultaneousCallingSupport_invalidResponse_shouldFail() throws Exception {
        doReturn(false).when(mFeatureFlags).simultaneousCallingIndications();
        init(2);
        mPcm.updateSimultaneousCallingSupport();

        // Have the modem send invalid phone slots -1 and 5:
        int[] invalidEnabledLogicalSlots = {-1, 5};
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).updateSimultaneousCallingSupport(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, invalidEnabledLogicalSlots, null);
        msg.sendToTarget();
        processAllMessages();

        // We would expect to DSDA to be disabled and mSlotsSupportingSimultaneousCellularCalls to
        // have been cleared:
        assertTrue(mPcm.getSlotsSupportingSimultaneousCellularCalls().isEmpty());
    }

    /**
     * If the device uses the older "dsda" multi_sim_config setting, ensure that DSDA is set
     * statically for that device and subId updates work.
     */
    @Test
    @SmallTest
    public void testBkwdsCompatSimultaneousCallingDsda() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(RIL.RADIO_HAL_VERSION_2_1).when(mMockRadioConfigProxy).getVersion();
        doReturn(Optional.of("dsda")).when(mMi).getMultiSimProperty();
        final int phone0SubId = 2;
        final int phone1SubId = 3;
        mPhones = new Phone[]{mPhone, mPhone1};
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone1).getPhoneId();
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        init(2);
        doReturn(phone0SubId).when(mPhone).getSubId();
        doReturn(phone1SubId).when(mPhone1).getSubId();
        Set<Integer>[] cachedSimultaneousCallingSlots = new Set[]{Collections.emptySet()};
        mPcm.registerForSimultaneousCellularCallingSlotsChanged(newSlots ->
                cachedSimultaneousCallingSlots[0] = newSlots);

        mPcm.getStaticPhoneCapability();
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);
        ArgumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener> cBCaptor =
                ArgumentCaptor.forClass(SubscriptionManager.OnSubscriptionsChangedListener.class);
        verify(mMockRegistryManager).addOnSubscriptionsChangedListener(cBCaptor.capture(), any());
        processAllMessages();

        int[] enabledLogicalSlots = {0, 1};
        HashSet<Integer> expectedSlots = new HashSet<>(2);
        for (int i : enabledLogicalSlots) {
            expectedSlots.add(i);
        }
        HashSet<Integer> expectedSubIds = new HashSet<>(2);
        expectedSubIds.add(phone0SubId);
        expectedSubIds.add(phone1SubId);
        assertEquals(expectedSlots, mPcm.getSlotsSupportingSimultaneousCellularCalls());
        verify(mMockRegistryManager).notifySimultaneousCellularCallingSubscriptionsChanged(
                eq(expectedSubIds));
        assertEquals(expectedSlots, cachedSimultaneousCallingSlots[0]);

        // Change sub ID mapping
        final int phone1SubIdV2 = 4;
        expectedSubIds.clear();
        expectedSubIds.add(phone0SubId);
        expectedSubIds.add(phone1SubIdV2);
        doReturn(phone1SubIdV2).when(mPhone1).getSubId();
        cBCaptor.getValue().onSubscriptionsChanged();
        processAllMessages();
        verify(mMockRegistryManager, times(2))
                .notifySimultaneousCellularCallingSubscriptionsChanged(eq(expectedSubIds));
    }

    @Test
    @SmallTest
    public void testUpdateSimultaneousCallingSupportNotifications() throws Exception {
        // retry simultaneous calling tests, but with notifications enabled this time
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();

        final int phone0SubId = 2;
        final int phone1SubId = 3;
        mPhones = new Phone[]{mPhone, mPhone1};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        init(2);
        doReturn(phone0SubId).when(mPhone).getSubId();
        doReturn(phone1SubId).when(mPhone1).getSubId();
        Set<Integer>[] cachedSimultaneousCallingSlots = new Set[]{Collections.emptySet()};
        mPcm.registerForSimultaneousCellularCallingSlotsChanged(newSlots ->
                cachedSimultaneousCallingSlots[0] = newSlots);

        // Simultaneous calling enabled
        mPcm.updateSimultaneousCallingSupport();
        int[] enabledLogicalSlots = {0, 1};
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).updateSimultaneousCallingSupport(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, enabledLogicalSlots, null);
        msg.sendToTarget();
        processAllMessages();

        HashSet<Integer> expectedSlots = new HashSet<>(2);
        for (int i : enabledLogicalSlots) {
            expectedSlots.add(i);
        }
        HashSet<Integer> expectedSubIds = new HashSet<>(2);
        expectedSubIds.add(phone0SubId);
        expectedSubIds.add(phone1SubId);
        assertEquals(expectedSlots, mPcm.getSlotsSupportingSimultaneousCellularCalls());
        verify(mMockRegistryManager).notifySimultaneousCellularCallingSubscriptionsChanged(
                eq(expectedSubIds));
        assertEquals(expectedSlots, cachedSimultaneousCallingSlots[0]);

        // Simultaneous Calling Disabled
        mPcm.updateSimultaneousCallingSupport();
        int[] disabled = {};
        captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig, times(2)).updateSimultaneousCallingSupport(captor.capture());
        msg = captor.getAllValues().get(1);
        AsyncResult.forMessage(msg, disabled, null);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(Collections.emptySet(), mPcm.getSlotsSupportingSimultaneousCellularCalls());
        verify(mMockRegistryManager, times(2))
                .notifySimultaneousCellularCallingSubscriptionsChanged(eq(Collections.emptySet()));
        assertEquals(Collections.emptySet(), cachedSimultaneousCallingSlots[0]);
    }

    @Test
    @SmallTest
    public void testSimultaneousCallingSubIdMappingChanges() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        final int phone0SubId = 2;
        final int phone1SubId = 3;
        mPhones = new Phone[]{mPhone, mPhone1};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        init(2);
        doReturn(phone0SubId).when(mPhone).getSubId();
        doReturn(phone1SubId).when(mPhone1).getSubId();

        // Set the capability to DSDA mode to register listener, which will also trigger
        // simultaneous calling evaluation
        mPcm.getCurrentPhoneCapability();
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);
        ArgumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener> cBCaptor =
                ArgumentCaptor.forClass(SubscriptionManager.OnSubscriptionsChangedListener.class);
        verify(mMockRegistryManager).addOnSubscriptionsChangedListener(cBCaptor.capture(), any());

        int[] enabledLogicalSlots = {0, 1};
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).updateSimultaneousCallingSupport(captor.capture());
        Message msg = captor.getValue();
        // Simultaneous calling enabled
        AsyncResult.forMessage(msg, enabledLogicalSlots, null);
        msg.sendToTarget();
        processAllMessages();

        HashSet<Integer> expectedSlots = new HashSet<>(2);
        for (int i : enabledLogicalSlots) {
            expectedSlots.add(i);
        }
        HashSet<Integer> expectedSubIds = new HashSet<>(2);
        expectedSubIds.add(phone0SubId);
        expectedSubIds.add(phone1SubId);
        assertEquals(expectedSlots, mPcm.getSlotsSupportingSimultaneousCellularCalls());
        verify(mMockRegistryManager).notifySimultaneousCellularCallingSubscriptionsChanged(
                eq(expectedSubIds));

        // Change sub ID mapping
        final int phone1SubIdV2 = 4;
        expectedSubIds.clear();
        expectedSubIds.add(phone0SubId);
        expectedSubIds.add(phone1SubIdV2);
        doReturn(phone1SubIdV2).when(mPhone1).getSubId();
        cBCaptor.getValue().onSubscriptionsChanged();
        processAllMessages();
        verify(mMockRegistryManager, times(2))
                .notifySimultaneousCellularCallingSubscriptionsChanged(eq(expectedSubIds));
    }

    @Test
    @SmallTest
    public void testSwitchMultiSimConfig_notDsdsCapable_shouldFail() throws Exception {
        init(1);
        assertEquals(PhoneCapability.DEFAULT_SSSS_CAPABILITY, mPcm.getStaticPhoneCapability());

        // Try switching to dual SIM. Shouldn't work as we haven't indicated DSDS is supported.
        mPcm.switchMultiSimConfig(2);
        verify(mMockRadioConfig, never()).setNumOfLiveModems(anyInt(), any());
    }

    @Test
    @SmallTest
    public void testSwitchMultiSimConfig_dsdsCapable_noRebootRequired() throws Exception {
        init(1);
        testSwitchFromSingleToDualSimModeNoReboot();
    }

    @Test
    @SmallTest
    public void testSwitchMultiSimConfig_multiSimToSingleSim() throws Exception {
        mPhones = new Phone[]{mPhone, mPhone1};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        init(2);
        verify(mMockCi0, times(1)).registerForAvailable(any(), anyInt(), any());
        verify(mMockCi1, times(1)).registerForAvailable(any(), anyInt(), any());

        // Register for multi SIM config change.
        mPcm.registerForMultiSimConfigChange(mHandler, EVENT_MULTI_SIM_CONFIG_CHANGED, null);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());

        // Switch to single sim.
        setRebootRequiredForConfigSwitch(false);
        mPcm.switchMultiSimConfig(1);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).setNumOfLiveModems(eq(1), captor.capture());

        // Send message back to indicate switch success.
        Message message = captor.getValue();
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
        processAllMessages();

        // Verify set system property being called.
        verify(mMi).setMultiSimProperties(1);
        verify(mMi).notifyPhoneFactoryOnMultiSimConfigChanged(any(), eq(1));

        // Capture and verify registration notification.
        verify(mHandler).sendMessageAtTime(captor.capture(), anyLong());
        message = captor.getValue();
        assertEquals(EVENT_MULTI_SIM_CONFIG_CHANGED, message.what);
        assertEquals(1, ((AsyncResult) message.obj).result);

        // Capture and verify broadcast.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());
        Intent intent = intentCaptor.getValue();
        assertEquals(ACTION_MULTI_SIM_CONFIG_CHANGED, intent.getAction());
        assertEquals(1, intent.getIntExtra(
                EXTRA_ACTIVE_SIM_SUPPORTED_COUNT, 0));

        // Verify clearSubInfoRecord() and onSlotActiveStatusChange() are called for second phone,
        // and not for the first one
        verify(mSubscriptionManagerService).markSubscriptionsInactive(1);
        verify(mMockCi1).onSlotActiveStatusChange(anyBoolean());
        verify(mSubscriptionManagerService, never()).markSubscriptionsInactive(0);
        verify(mMockCi0, never()).onSlotActiveStatusChange(anyBoolean());

        // Verify onPhoneRemoved() gets called on MultiSimSettingController phone
        verify(mMultiSimSettingController).onPhoneRemoved();
    }

    @Test
    @SmallTest
    public void testNoCallPreferenceIsSetAfterSwitchToDsdsMode() throws Exception {
        final int startingDefaultSubscriptionId = 2; // arbitrary value (can't be -1 which
        // represents the "No Call Preference" value)

        /*
            TL;DR:  the following mockito code block dynamically changes the last call to the getter

            doAnswer(invocation -> {
                Integer value = (Integer) invocation.getArguments()[0];
                Mockito.when(object.getter()).thenReturn(value);
                return null;
            }).when(object).setter(anyInt());

            read the code block as, whenever we call the setter, change the Mock call
            of the getter to whatever argument value we last passed to the setter.

            ex.)    object.set( 2 )   --> next call to object.get() will return 2
         */

        // setup mocks for  VOICE mSubscriptionManagerService. getter/setter
        doAnswer(invocation -> {
            Integer value = (Integer) invocation.getArguments()[0];
            Mockito.when(mSubscriptionManagerService.getDefaultVoiceSubId()).thenReturn(value);
            return null;
        }).when(mSubscriptionManagerService).setDefaultVoiceSubId(anyInt());


        // start off the phone stat with 1 active sim. reset values for new test.
        init(1);

        mSubscriptionManagerService.setDefaultVoiceSubId(startingDefaultSubscriptionId);
        assertEquals(startingDefaultSubscriptionId,
                mSubscriptionManagerService.getDefaultVoiceSubId());

        // Perform the switch to DSDS mode and ensure all existing checks are not altered
        testSwitchFromSingleToDualSimModeNoReboot();

        // VOICE check
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID /* No CALL Preference value */,
                mSubscriptionManagerService.getDefaultVoiceSubId());
        // Now, when the user goes to place a CALL, they will be prompted on which sim to use.
    }

    /**
     * must call init(1) from the parent test before calling this helper test
     * @throws Exception
     */
    public void testSwitchFromSingleToDualSimModeNoReboot() throws Exception {
        verify(mMockCi0, times(1)).registerForAvailable(any(), anyInt(), any());

        // Register for multi SIM config change.
        mPcm.registerForMultiSimConfigChange(mHandler, EVENT_MULTI_SIM_CONFIG_CHANGED, null);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());

        // Try switching to dual SIM. Shouldn't work as we haven't indicated DSDS is supported.
        mPcm.switchMultiSimConfig(2);
        verify(mMockRadioConfig, never()).setNumOfLiveModems(anyInt(), any());

        // Send static capability back to indicate DSDS is supported.
        clearInvocations(mMockRadioConfig);
        testGetDsdsCapability();
        // testGetDsdsCapability leads to another call to registerForAvailable()
        verify(mMockCi0, times(2)).registerForAvailable(any(), anyInt(), any());

        // Try to switch to DSDS.
        setRebootRequiredForConfigSwitch(false);
        mPhones = new Phone[]{mPhone, mPhone1};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        mPcm.switchMultiSimConfig(2);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).setNumOfLiveModems(eq(2), captor.capture());

        // Send message back to indicate switch success.
        Message message = captor.getValue();
        AsyncResult.forMessage(message, null, null);
        message.sendToTarget();
        processAllMessages();

        // Verify set system property being called.
        verify(mMi).setMultiSimProperties(2);
        verify(mMi).notifyPhoneFactoryOnMultiSimConfigChanged(any(), eq(2));

        // Capture and verify registration notification.
        verify(mHandler).sendMessageAtTime(captor.capture(), anyLong());
        message = captor.getValue();
        assertEquals(EVENT_MULTI_SIM_CONFIG_CHANGED, message.what);
        assertEquals(2, ((AsyncResult) message.obj).result);

        // Capture and verify broadcast.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());
        Intent intent = intentCaptor.getValue();
        assertEquals(ACTION_MULTI_SIM_CONFIG_CHANGED, intent.getAction());
        assertEquals(2, intent.getIntExtra(
                EXTRA_ACTIVE_SIM_SUPPORTED_COUNT, 0));

        // Verify registerForAvailable() and onSlotActiveStatusChange() are called for the second
        // phone, and not for the first phone (registerForAvailable() was already called twice
        // earlier so verify that the count is still at 2)
        verify(mMockCi0, times(2)).registerForAvailable(any(), anyInt(), any());
        verify(mMockCi0, never()).onSlotActiveStatusChange(anyBoolean());
        verify(mMockCi1, times(1)).registerForAvailable(any(), anyInt(), any());
        verify(mMockCi1, times(1)).onSlotActiveStatusChange(anyBoolean());
    }

    private void setAndVerifyStaticCapability(PhoneCapability capability) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).getPhoneCapability(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, capability, null);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(capability, mPcm.getStaticPhoneCapability());
    }
}
