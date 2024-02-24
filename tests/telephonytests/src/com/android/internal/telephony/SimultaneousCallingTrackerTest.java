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

package com.android.internal.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.telephony.AccessNetworkConstants;
import android.telephony.ModemInfo;
import android.telephony.PhoneCapability;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyRegistryManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.imsphone.ImsPhone;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SimultaneousCallingTrackerTest extends TelephonyTest {
    // Mocked classes
    Handler mHandler;
    CommandsInterface mMockCi0;
    CommandsInterface mMockCi1;
    CommandsInterface mMockCi2;
    private Phone mPhone1; // mPhone as phone 0 is already defined in TelephonyTest.
    private Phone mPhone2;
    private SubscriptionInfo mSubInfo;
    private ImsPhone mImsPhone;
    private ImsPhone mImsPhone1;
    private ImsPhone mImsPhone2;
    PhoneConfigurationManager.MockableInterface mMi;
    private static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 1;
    private static final PhoneCapability STATIC_DSDA_CAPABILITY;
    private static final Set<Integer> STATIC_SERVICE_CAPABILITIES;

    static {
        ModemInfo modemInfo1 = new ModemInfo(0, 0, true, true);
        ModemInfo modemInfo2 = new ModemInfo(1, 0, true, true);

        List<ModemInfo> logicalModemList = new ArrayList<>();
        logicalModemList.add(modemInfo1);
        logicalModemList.add(modemInfo2);
        int[] deviceNrCapabilities = new int[0];

        STATIC_DSDA_CAPABILITY = new PhoneCapability(2, 1, logicalModemList, false,
                deviceNrCapabilities);

        STATIC_SERVICE_CAPABILITIES = new HashSet<>(1);
        STATIC_SERVICE_CAPABILITIES.add(SubscriptionManager.SERVICE_CAPABILITY_VOICE);
    }
    PhoneConfigurationManager mPcm;
    SimultaneousCallingTracker mSct;

    private FeatureFlags mFeatureFlags;
    private TelephonyRegistryManager mMockRegistryManager;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mHandler = mock(Handler.class);
        mMockCi0 = mock(CommandsInterface.class);
        mMockCi1 = mock(CommandsInterface.class);
        mMockCi2 = mock(CommandsInterface.class);
        mFeatureFlags = mock(FeatureFlags.class);
        mPhone1 = mock(Phone.class);
        mPhone2 = mock(Phone.class);
        mImsPhone = mock(ImsPhone.class);
        mImsPhone1 = mock(ImsPhone.class);
        mImsPhone2 = mock(ImsPhone.class);
        mSubInfo = mock(SubscriptionInfo.class);
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(mImsPhone1).when(mPhone1).getImsPhone();
        doReturn(mImsPhone2).when(mPhone2).getImsPhone();
        mMi = mock(PhoneConfigurationManager.MockableInterface.class);
        mPhone.mCi = mMockCi0;
        mCT.mCi = mMockCi0;
        mPhone1.mCi = mMockCi1;
        mPhone2.mCi = mMockCi2;
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(10).when(mPhone).getSubId();
        doReturn(1).when(mPhone1).getPhoneId();
        // This will be updated to 11 during each test in order to trigger onSubscriptionChanged:
        doReturn(110).when(mPhone1).getSubId();
        doReturn(2).when(mPhone2).getPhoneId();
        doReturn(12).when(mPhone2).getSubId();
        doReturn(STATIC_SERVICE_CAPABILITIES).when(mSubInfo).getServiceCapabilities();
        doReturn(mSubInfo).when(mSubscriptionManagerService)
                .getSubscriptionInfo(any(Integer.class));
        doReturn(RIL.RADIO_HAL_VERSION_2_2).when(mMockRadioConfigProxy).getVersion();
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(true).when(mFeatureFlags).dataOnlyCellularService();
        mMockRegistryManager = mContext.getSystemService(TelephonyRegistryManager.class);
    }

    @After
    public void tearDown() throws Exception {
        mPcm = null;
        mSct = null;
        mPhone1 = null;
        mPhone2 = null;
        super.tearDown();
    }

    /**
     * @param numOfSim the current number of SIM subscriptions
     */
    private void init(int numOfSim) throws Exception {
        doReturn(numOfSim).when(mTelephonyManager).getActiveModemCount();
        replaceInstance(SimultaneousCallingTracker.class, "sInstance", null, null);
        replaceInstance(PhoneConfigurationManager.class, "sInstance", null, null);
        switch (numOfSim) {
            case 0 -> mPhones = new Phone[]{};
            case 1 -> mPhones = new Phone[]{mPhone};
            case 2 -> mPhones = new Phone[]{mPhone, mPhone1};
            case 3 -> mPhones = new Phone[]{mPhone, mPhone1, mPhone2};
        }
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        mPcm = PhoneConfigurationManager.init(mContext, mFeatureFlags);
        mSct = SimultaneousCallingTracker.init(mContext, mFeatureFlags);
        replaceInstance(PhoneConfigurationManager.class, "mMi", mPcm, mMi);
        processAllMessages();
    }

    private void setRebootRequiredForConfigSwitch(boolean rebootRequired) {
        doReturn(rebootRequired).when(mMi).isRebootRequiredForModemConfigChange();
    }

    private void setAndVerifyStaticCapability(PhoneCapability capability) {
        mPcm.getCurrentPhoneCapability();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).getPhoneCapability(captor.capture());
        Message msg = captor.getValue();
        AsyncResult.forMessage(msg, capability, null);
        msg.sendToTarget();
        processAllMessages();

        assertEquals(capability, mPcm.getStaticPhoneCapability());
        assertTrue(mSct.isDeviceSimultaneousCallingCapable);

    }

    private void setAndVerifySlotsSupportingSimultaneousCellularCalling(int[] enabledLogicalSlots) {
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

    private void updateSubId(Phone phone, int newSubId) {
        ArgumentCaptor<SubscriptionManager.OnSubscriptionsChangedListener> cBCaptorList =
                ArgumentCaptor.forClass(SubscriptionManager.OnSubscriptionsChangedListener.class);
        verify(mMockRegistryManager, times(2))
                .addOnSubscriptionsChangedListener(cBCaptorList.capture(), any());

        // Change sub ID mapping
        doReturn(newSubId).when(phone).getSubId();
        List<SubscriptionManager.OnSubscriptionsChangedListener> listeners =
                cBCaptorList.getAllValues();
        listeners.get(0).onSubscriptionsChanged();
        listeners.get(1).onSubscriptionsChanged();
        processAllMessages();
    }

    /**
     * Test that simultaneous calling is not supported when the device is only capable of a max
     * active voice count of 1.
     */
    @Test
    @SmallTest
    public void testDeviceNotSimultaneousCallingCapable() throws Exception {
        init(1);
        assertFalse(mSct.isDeviceSimultaneousCallingCapable);
        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(10).size());
    }

    /**
     * Test that simultaneous calling is not supported when the subscriptions have different user
     * associations.
     */
    @Test
    @SmallTest
    public void testDifferentUserAssociations_SimultaneousCallingDisabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();

        //Assign each phone to a different user which should disable simultaneous calling:
        doReturn(new UserHandle(123)).when(mPhone).getUserHandle();
        doReturn(new UserHandle(321)).when(mPhone1).getUserHandle();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(10).size());
        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(11).size());
    }

    /**
     * Test that simultaneous calling is not supported when IMS is not registered and cellular
     * simultaneous calling is only supported for one SIM subscription.
     */
    @Test
    @SmallTest
    public void testCellularDSDANotSupported_SimultaneousCallingDisabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        // Have the modem inform telephony that only phone slot 0 supports DSDA:
        int[] enabledLogicalSlots = {0};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(10).size());
        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(11).size());
    }

    /**
     * Test that simultaneous calling is supported when IMS is not registered and cellular
     * simultaneous calling is supported for both SIM subscription.
     */
    @Test
    @SmallTest
    public void testCellularDSDASupported_SimultaneousCallingEnabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(10).contains(11));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(11).contains(10));
    }

    /**
     * Test that simultaneous calling is supported when IMS is not registered and cellular
     * simultaneous calling is supported for both SIM subscription. Then test that simultaneous
     * calling is not supported after a multi SIM config change to single-SIM.
     */
    @Test
    @SmallTest
    public void testSingleSimSwitch_SimultaneousCallingDisabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(10).contains(11));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(11).contains(10));

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

        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(10).size());
        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(11).size());
    }

    /**
     * Test that simultaneous calling is not supported when IMS is registered, vDSDA is disabled,
     * but ImsService#CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING is not set.
     */
    @Test
    @SmallTest
    public void testImsDSDANotSupported_SimultaneousCallingDisabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(true).when(mPhone).isImsRegistered();
        doReturn(true).when(mPhone1).isImsRegistered();
        doReturn(false).when(mPhone)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));
        doReturn(false).when(mPhone1)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(10).size());
        assertEquals(0, mSct.getSubIdsSupportingSimultaneousCalling(11).size());
    }

    /**
     * Test that simultaneous calling is supported when IMS is registered, vDSDA is enabled,
     * and the IMS transport type of each SIM subscription is WLAN.
     */
    //TODO: Implement a way to set vDSDAEnabled to true and then re-enable this test
    @Ignore
    @Test
    @SmallTest
    public void testImsVDSDAEnabledTransportTypeWLAN_SimultaneousCallingEnabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(true).when(mPhone).isImsRegistered();
        doReturn(true).when(mPhone1).isImsRegistered();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mImsPhone).getTransportType();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mImsPhone1).getTransportType();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(10).contains(11));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(11).contains(10));
    }

    /**
     * Test that simultaneous calling is supported when IMS is registered, vDSDA is disabled,
     * ImsService#CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING is set and the IMS transport type of each
     * SIM subscription is WLAN.
     */
    @Test
    @SmallTest
    public void testImsVDSDADisabledTransportTypeWLAN_SimultaneousCallingEnabled()
            throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(true).when(mPhone).isImsRegistered();
        doReturn(true).when(mPhone1).isImsRegistered();
        doReturn(true).when(mPhone)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));
        doReturn(true).when(mPhone1)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mImsPhone).getTransportType();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mImsPhone1).getTransportType();

        init(2);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(10).contains(11));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(11).contains(10));
    }

    /**
     * Test that simultaneous calling is supported between all subs in the following 3-SIM case:
     * SIM A: IMS Unregistered, vDSDA Disabled
     * SIM B: IMS WWAN Registered, vDSDA Disabled, CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING not set
     * SIM C: IMS WLAN Registered, vDSDA Disabled, CAPABILITY_SUPPORTS_SIMULTANEOUS_CALLING set
     */
    @Test
    @SmallTest
    public void testThreeSimCase_SimultaneousCallingEnabled() throws Exception {
        doReturn(true).when(mFeatureFlags).simultaneousCallingIndications();
        doReturn(false).when(mPhone).isImsRegistered();
        doReturn(true).when(mPhone1).isImsRegistered();
        doReturn(true).when(mPhone2).isImsRegistered();
        doReturn(true).when(mPhone1)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));
        doReturn(true).when(mPhone2)
                .isImsServiceSimultaneousCallingSupportCapable(any(Context.class));
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN).when(mImsPhone1).getTransportType();
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN).when(mImsPhone2).getTransportType();

        init(3);
        setAndVerifyStaticCapability(STATIC_DSDA_CAPABILITY);

        int[] enabledLogicalSlots = {0, 1};
        setAndVerifySlotsSupportingSimultaneousCellularCalling(enabledLogicalSlots);

        // Trigger onSubscriptionsChanged by updating the subscription ID of a phone slot:
        updateSubId(mPhone1, 11);

        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(10)
                .containsAll(Arrays.asList(11,12)));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(11)
                .containsAll(Arrays.asList(10,12)));
        assertTrue(mSct.getSubIdsSupportingSimultaneousCalling(12)
                .containsAll(Arrays.asList(10,11)));
    }
}
