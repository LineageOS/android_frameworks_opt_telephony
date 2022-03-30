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
import android.telephony.PhoneCapability;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
    PhoneConfigurationManager mPcm;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mHandler = mock(Handler.class);
        mMockCi0 = mock(CommandsInterface.class);
        mMockCi1 = mock(CommandsInterface.class);
        mPhone1 = mock(Phone.class);
        mMi = mock(PhoneConfigurationManager.MockableInterface.class);
        mPhone.mCi = mMockCi0;
        mCT.mCi = mMockCi0;
        mPhone1.mCi = mMockCi1;
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
        mPcm = PhoneConfigurationManager.init(mContext);
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

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).getPhoneCapability(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, PhoneCapability.DEFAULT_DSDS_CAPABILITY, null);
        msg.sendToTarget();
        processAllMessages();

        // Not static capability should indicate DSDS capable.
        assertEquals(PhoneCapability.DEFAULT_DSDS_CAPABILITY, mPcm.getStaticPhoneCapability());
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
        verify(mSubscriptionController).clearSubInfoRecord(1);
        verify(mMockCi1).onSlotActiveStatusChange(anyBoolean());
        verify(mSubscriptionController, never()).clearSubInfoRecord(0);
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

        // setup mocks for  VOICE mSubscriptionController. getter/setter
        doAnswer(invocation -> {
            Integer value = (Integer) invocation.getArguments()[0];
            Mockito.when(mSubscriptionController.getDefaultVoiceSubId()).thenReturn(value);
            return null;
        }).when(mSubscriptionController).setDefaultVoiceSubId(anyInt());

        // start off the phone stat with 1 active sim. reset values for new test.
        init(1);

        mSubscriptionController.setDefaultVoiceSubId(startingDefaultSubscriptionId);

        // assert the mSubscriptionController registers the change
        assertEquals(startingDefaultSubscriptionId, mSubscriptionController.getDefaultVoiceSubId());

        // Perform the switch to DSDS mode and ensure all existing checks are not altered
        testSwitchFromSingleToDualSimModeNoReboot();

        // VOICE check
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID /* No CALL Preference value */,
                mSubscriptionController.getDefaultVoiceSubId()); //  Now, when the user goes to
        // place a CALL, they will be prompted on which sim to use.
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
}
