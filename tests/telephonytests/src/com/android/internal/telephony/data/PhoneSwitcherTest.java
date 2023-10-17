/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.telephony.CarrierConfigManager.KEY_DATA_SWITCH_VALIDATION_TIMEOUT_LONG;
import static android.telephony.TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED;
import static android.telephony.TelephonyManager.EXTRA_SIM_STATE;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_SUCCESS;
import static android.telephony.TelephonyManager.SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED;
import static android.telephony.TelephonyManager.SIM_STATE_LOADED;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_CROSS_SIM;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN;
import static android.telephony.ims.stub.ImsRegistrationImplBase.REGISTRATION_TECH_LTE;
import static com.android.internal.telephony.data.AutoDataSwitchController.EVALUATION_REASON_VOICE_CALL_END;
import static com.android.internal.telephony.data.PhoneSwitcher.ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneCapability;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.GsmCdmaCall;
import com.android.internal.telephony.ISetOpportunisticDataCallback;
import com.android.internal.telephony.ISub;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class PhoneSwitcherTest extends TelephonyTest {
    private static final int ACTIVE_PHONE_SWITCH = 1;
    private static final int EVENT_RADIO_ON = 108;
    private static final int EVENT_MODEM_COMMAND_DONE = 112;
    private static final int EVENT_EVALUATE_AUTO_SWITCH = 111;
    private static final int EVENT_IMS_RADIO_TECH_CHANGED = 120;
    private static final int EVENT_MULTI_SIM_CONFIG_CHANGED = 117;
    private static final int EVENT_PRECISE_CALL_STATE_CHANGED = 109;
    private static final int EVENT_SERVICE_STATE_CHANGED = 114;

    // Mocked classes
    CompletableFuture<Boolean> mFuturePhone;
    private CommandsInterface mCommandsInterface0;
    private CommandsInterface mCommandsInterface1;
    private ServiceStateTracker mSST2;
    private Phone mImsPhone;
    private DataSettingsManager mDataSettingsManager2;
    private Handler mActivePhoneSwitchHandler;
    private GsmCdmaCall mActiveCall;
    private GsmCdmaCall mHoldingCall;
    private GsmCdmaCall mInactiveCall;
    private GsmCdmaCall mDialCall;
    private GsmCdmaCall mIncomingCall;
    private GsmCdmaCall mAlertingCall;
    private ISetOpportunisticDataCallback mSetOpptDataCallback1;
    private ISetOpportunisticDataCallback mSetOpptDataCallback2;
    PhoneSwitcher.ImsRegTechProvider mMockImsRegTechProvider;
    private SubscriptionInfo mSubscriptionInfo;
    private ISub mMockedIsub;
    private AutoDataSwitchController mAutoDataSwitchController;

    private PhoneSwitcher mPhoneSwitcherUT;
    private SubscriptionManager.OnSubscriptionsChangedListener mSubChangedListener;
    private ConnectivityManager mConnectivityManager;
    // The messenger of PhoneSwitcher used to receive network requests.
    private Messenger mNetworkProviderMessenger = null;
    private Map<Integer, DataSettingsManager.DataSettingsManagerCallback>
            mDataSettingsManagerCallbacks;
    private int mDefaultDataSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private int[][] mSlotIndexToSubId;
    private boolean[] mDataAllowed;
    private int mActiveModemCount = 2;
    private int mSupportedModemCount = 2;
    private int mMaxDataAttachModemCount = 1;
    private AutoDataSwitchController.AutoDataSwitchControllerCallback mAutoDataSwitchCallback;
    private TelephonyDisplayInfo mTelephonyDisplayInfo = new TelephonyDisplayInfo(
            TelephonyManager.NETWORK_TYPE_NR,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false);

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mFuturePhone = mock(CompletableFuture.class);
        mCommandsInterface0 = mock(CommandsInterface.class);
        mCommandsInterface1 = mock(CommandsInterface.class);
        mSST2 = mock(ServiceStateTracker.class);
        mImsPhone = mock(Phone.class);
        mDataSettingsManager2 = mock(DataSettingsManager.class);
        mActivePhoneSwitchHandler = mock(Handler.class);
        mActiveCall = mock(GsmCdmaCall.class);
        mHoldingCall = mock(GsmCdmaCall.class);
        mInactiveCall = mock(GsmCdmaCall.class);
        mDialCall = mock(GsmCdmaCall.class);
        mIncomingCall = mock(GsmCdmaCall.class);
        mAlertingCall = mock(GsmCdmaCall.class);
        mSetOpptDataCallback1 = mock(ISetOpportunisticDataCallback.class);
        mSetOpptDataCallback2 = mock(ISetOpportunisticDataCallback.class);
        mMockImsRegTechProvider = mock(PhoneSwitcher.ImsRegTechProvider.class);
        mSubscriptionInfo = mock(SubscriptionInfo.class);
        mMockedIsub = mock(ISub.class);
        mAutoDataSwitchController = mock(AutoDataSwitchController.class);

        PhoneCapability phoneCapability = new PhoneCapability(1, 1, null, false, new int[0]);
        doReturn(phoneCapability).when(mPhoneConfigurationManager).getCurrentPhoneCapability();

        doReturn(Call.State.ACTIVE).when(mActiveCall).getState();
        doReturn(Call.State.IDLE).when(mInactiveCall).getState();
        doReturn(Call.State.HOLDING).when(mHoldingCall).getState();
        doReturn(Call.State.DIALING).when(mDialCall).getState();
        doReturn(Call.State.INCOMING).when(mIncomingCall).getState();
        doReturn(Call.State.ALERTING).when(mAlertingCall).getState();

        doReturn(true).when(mInactiveCall).isIdle();
        doReturn(false).when(mActiveCall).isIdle();
        doReturn(false).when(mHoldingCall).isIdle();

        replaceInstance(Phone.class, "mCi", mPhone, mCommandsInterface0);
        replaceInstance(Phone.class, "mCi", mPhone2, mCommandsInterface1);

        doReturn(1).when(mMockedIsub).getDefaultDataSubId();
        doReturn(mMockedIsub).when(mIBinder).queryLocalInterface(anyString());
        doReturn(mPhone).when(mPhone).getImsPhone();
        mServiceManagerMockedServices.put("isub", mIBinder);

        doReturn(mTelephonyDisplayInfo).when(mDisplayInfoController).getTelephonyDisplayInfo();
    }

    @After
    public void tearDown() throws Exception {
        mPhoneSwitcherUT = null;
        mSubChangedListener = null;
        mConnectivityManager = null;
        mNetworkProviderMessenger = null;
        mTelephonyDisplayInfo = null;
        super.tearDown();
    }

    /**
     * Test that a single phone case results in our phone being active and the RIL called
     */
    @Test
    @SmallTest
    public void testRegister() throws Exception {
        initialize();

        // verify nothing has been done while there are no inputs
        assertTrue("data should be always allowed for emergency", mDataAllowed[0]);
        assertFalse("data allowed initially", mDataAllowed[1]);

        NetworkRequest internetNetworkRequest = addInternetNetworkRequest(null, 50);

        assertFalse("phone active after request", mPhoneSwitcherUT
                .shouldApplyNetworkRequest(
                        new TelephonyNetworkRequest(internetNetworkRequest, mPhone), 0));

        // not registered yet - shouldn't inc
        verify(mActivePhoneSwitchHandler, never()).sendMessageAtTime(any(), anyLong());

        mPhoneSwitcherUT.registerForActivePhoneSwitch(mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);

        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);

        setDefaultDataSubId(0);

        verify(mActivePhoneSwitchHandler, never()).sendMessageAtTime(any(), anyLong());

        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);

        // now try various things that should cause the active phone to switch:
        // 1 lose default via default sub change
        // 2 gain default via default sub change
        // 3 lose default via sub->phone change
        // 4 gain default via sub->phone change
        // 5 lose default network request
        // 6 gain subscription-specific request
        // 7 lose via sub->phone change
        // 8 gain via sub->phone change
        // 9 lose subscription-specific request
        // 10 don't switch phones when in emergency mode

        // 1 lose default via default sub change
        setDefaultDataSubId(1);

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertFalse("data allowed", mDataAllowed[0]);

        setSlotIndexToSubId(1, 1);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertFalse("data allowed", mDataAllowed[0]);
        assertTrue("data not allowed", mDataAllowed[1]);

        // 2 gain default via default sub change
        setDefaultDataSubId(0);

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertFalse("data allowed", mDataAllowed[1]);
        assertTrue("data not allowed", mDataAllowed[0]);

        // 3 lose default via sub->phone change
        setSlotIndexToSubId(0, 2);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 4 gain default via sub->phone change
        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 5 lose default network request
        releaseNetworkRequest(internetNetworkRequest);

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 6 gain subscription-specific request
        NetworkRequest specificInternetRequest = addInternetNetworkRequest(0, 50);

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 7 lose via sub->phone change
        setSlotIndexToSubId(0, 1);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 8 gain via sub->phone change
        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        processAllMessages();

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 9 lose subscription-specific request
        releaseNetworkRequest(specificInternetRequest);

        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 10 don't switch phones when in emergency mode
        // not ready yet - Phone turns out to be hard to stub out
//        phones[0].setInEmergencyCall(true);
//        connectivityServiceMock.addDefaultRequest();
//        processAllMessages();
//        if (testHandler.getActivePhoneSwitchCount() != 11) {
//            fail("after release of request, ActivePhoneSwitchCount not 11!");
//        }
//        if (commandsInterfaces[0].isDataAllowed()) fail("data allowed");
//        if (commandsInterfaces[1].isDataAllowed()) fail("data allowed");
//
//        phones[0].setInEmergencyCall(false);
//        connectivityServiceMock.addDefaultRequest();
//        processAllMessages();
//        if (testHandler.getActivePhoneSwitchCount() != 12) {
//            fail("after release of request, ActivePhoneSwitchCount not 11!");
//        }
//        if (commandsInterfaces[0].isDataAllowed()) fail("data allowed");
//        if (commandsInterfaces[1].isDataAllowed()) fail("data allowed");
    }

    /** Test Data Auto Switch **/
    @Test
    public void testAutoDataSwitch_retry() throws Exception {
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        mAutoDataSwitchCallback.onRequireValidation(1/*Phone2*/, true);
        processAllFutureMessages();

        // Mock validation failed, expect retry attempt
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 2/*Phone2*/);
        processAllMessages();

        verify(mAutoDataSwitchController).evaluateRetryOnValidationFailed();

        // Test clear failed count upon switch succeeded.
        mAutoDataSwitchCallback.onRequireValidation(1/*Phone2*/, true);
        processAllFutureMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 2/*Phone2*/);
        processAllMessages();

        verify(mAutoDataSwitchController).resetFailedCount();
    }

    @Test
    public void testAutoDataSwitch_setNotification() throws Exception {
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        // Verify no notification check if switch failed.
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, new AsyncResult(1/*phoneId*/,
                null, new Throwable())).sendToTarget();
        processAllMessages();
        verify(mAutoDataSwitchController, never()).displayAutoDataSwitchNotification(
                anyInt(), anyBoolean());

        // Verify for switch not due to auto
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, new AsyncResult(1/*phoneId*/,
                null, null)).sendToTarget();
        processAllMessages();
        verify(mAutoDataSwitchController).displayAutoDataSwitchNotification(1/*phoneId*/, false);

        // Verify for switch due to auto
        mAutoDataSwitchCallback.onRequireValidation(1/*Phone2*/, false);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 2/*Phone2*/);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, new AsyncResult(1/*phoneId*/,
                null, null)).sendToTarget();
        processAllMessages();

        verify(mAutoDataSwitchController).displayAutoDataSwitchNotification(1/*phoneId*/, true);
    }

    @Test
    @SmallTest
    public void testAutoDataSwitch_exemptPingTest() throws Exception {
        initialize();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);

        //Attempting to switch to nDDS, switch even if validation failed
        mAutoDataSwitchCallback.onRequireValidation(1/*Phone2*/, false);
        processAllFutureMessages();

        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 2/*Phone2*/);
        processAllMessages();

        assertEquals(2, mPhoneSwitcherUT.getActiveDataSubId()); // switch succeeds
    }

    /**
     * Test a multi-sim case with limited active phones:
     * - lose default via default sub change
     * - lose default via sub->phone change
     * - gain default via sub->phone change
     * - gain default via default sub change
     * - lose default network request
     * - gain subscription-specific request
     * - lose via sub->phone change
     * - gain via sub->phone change
     * - lose subscription-specific request
     * - tear down low priority phone when new request comes in
     * - tear down low priority phone when sub change causes split
     * - bring up low priority phone when sub change causes join
     * - don't switch phones when in emergency mode
     */
    @Test
    @SmallTest
    public void testPrioritization() throws Exception {
        initialize();

        addInternetNetworkRequest(null, 50);
        setSlotIndexToSubId(0, 0);
        setSlotIndexToSubId(1, 1);
        setDefaultDataSubId(0);
        mPhoneSwitcherUT.registerForActivePhoneSwitch(mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        processAllMessages();
        // verify initial conditions
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());

        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // now start a higher priority connection on the other sub
        addMmsNetworkRequest(1);

        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        // After gain of network request, mActivePhoneSwitchHandler should be notified 2 times.
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertTrue("data not allowed", mDataAllowed[1]);
    }

    /**
     * Verify we don't send spurious DATA_ALLOWED calls when another NetworkProvider
     * wins (ie, switch to wifi).
     */
    @Test
    @SmallTest
    public void testHigherPriorityDefault() throws Exception {
        initialize();

        addInternetNetworkRequest(null, 50);

        setSlotIndexToSubId(0, 0);
        setSlotIndexToSubId(1, 1);
        setDefaultDataSubId(0);

        // Phone 0 should be active
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        addInternetNetworkRequest(null, 100);

        // should be no change
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        addInternetNetworkRequest(null, 0);
        // should be no change
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);
    }

    /**
     * Verify testSetPreferredData.
     * When preferredData is set, it overwrites defaultData sub to be active sub in single
     * active phone mode. If it's unset (to DEFAULT_SUBSCRIPTION_ID), defaultData sub becomes
     * active one.
     */
    @Test
    @SmallTest
    public void testSetPreferredData() throws Exception {
        initialize();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        // Notify phoneSwitcher about default data sub and default network request.
        addInternetNetworkRequest(null, 50);
        // Phone 0 (sub 1) should be activated as it has default data sub.
        assertTrue(mDataAllowed[0]);

        // Set sub 2 as preferred sub should make phone 1 activated and phone 0 deactivated.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 2);
        processAllMessages();
        assertFalse(mDataAllowed[0]);
        assertTrue(mDataAllowed[1]);

        // Unset preferred sub should make default data sub (phone 0 / sub 1) activated again.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 1);
        processAllMessages();
        assertTrue(mDataAllowed[0]);
        assertFalse(mDataAllowed[1]);
    }

    /**
     * TestSetPreferredData in the event of different priorities.
     * The following events can set preferred data subId with priority in the order of
     * 1. Emergency call
     * 2. Voice call (when data during call feature is enabled).
     * 3. CBRS requests OR Auto switch requests - only one case applies at a time
     */
    @Test
    @SmallTest
    public void testSetPreferredDataCasePriority_CbrsWaitsForVoiceCall() throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        // single visible sub, as the other one is CBRS
        doReturn(new int[1]).when(mSubscriptionManagerService).getActiveSubIdList(true);
        setDefaultDataSubId(1);

        // Notify phoneSwitcher about default data sub and default network request.
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        // Phone 0 (sub 1) should be activated as it has default data sub.
        assertEquals(1, mPhoneSwitcherUT.getActiveDataSubId());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));

        // Set sub 2 as preferred sub should make phone 1 activated and phone 0 deactivated.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 2);
        // A higher priority event occurring E.g. Phone1 has active IMS call on LTE.
        doReturn(mImsPhone).when(mPhone).getImsPhone();
        doReturn(true).when(mPhone).isUserDataEnabled();
        doReturn(true).when(mPhone).isDataAllowed();
        mockImsRegTech(0, REGISTRATION_TECH_LTE);
        notifyPhoneAsInCall(mPhone);

        // switch shouldn't occur due to the higher priority event
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertEquals(1, mPhoneSwitcherUT.getActiveDataSubId());
        assertEquals(2, mPhoneSwitcherUT.getAutoSelectedDataSubId());

        // The higher priority event ends, time to switch to auto selected subId.
        notifyPhoneAsInactive(mPhone);

        assertEquals(2, mPhoneSwitcherUT.getActiveDataSubId());
        assertEquals(2, mPhoneSwitcherUT.getAutoSelectedDataSubId());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
    }

    @Test
    @SmallTest
    public void testSetPreferredData_NoAutoSwitchWhenCbrs() throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        clearInvocations(mCellularNetworkValidator);
        doReturn(new int[1]).when(mSubscriptionManagerService).getActiveSubIdList(true);
        prepareIdealAutoSwitchCondition();
        processAllFutureMessages();

        verify(mCellularNetworkValidator, never()).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        assertEquals(1, mPhoneSwitcherUT.getActiveDataSubId());
    }

    @Test
    @SmallTest
    public void testSetPreferredDataModemCommand() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        mPhoneSwitcherUT.registerForActivePhoneSwitch(mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        mPhoneSwitcherUT.registerForActivePhoneSwitch(mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mMockRadioConfig);
        clearInvocations(mActivePhoneSwitchHandler);

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        // Phone 0 (sub 1) should be preferred data phone as it has default data sub.
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mMockRadioConfig);
        clearInvocations(mActivePhoneSwitchHandler);

        // Notify phoneSwitcher about default data sub and default network request.
        // It shouldn't change anything.
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        NetworkRequest mmsRequest = addMmsNetworkRequest(2);
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());
        verify(mActivePhoneSwitchHandler, never()).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 1));

        // Set sub 2 as preferred sub should make phone 1 preferredDataModem
        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 2);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 0));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 1));

        clearInvocations(mMockRadioConfig);
        clearInvocations(mActivePhoneSwitchHandler);

        // Unset preferred sub should make phone0 preferredDataModem again.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 1);
        processAllMessages();

        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(mmsRequest, mPhone), 1));

        // SetDataAllowed should never be triggered.
        verify(mCommandsInterface0, never()).setDataAllowed(anyBoolean(), any());
        verify(mCommandsInterface1, never()).setDataAllowed(anyBoolean(), any());

        // Set preferred data modem should be triggered after radio on or available.
        clearInvocations(mMockRadioConfig);
        Message.obtain(mPhoneSwitcherUT, EVENT_RADIO_ON, res).sendToTarget();
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
    }

    @Test
    @SmallTest
    public void testSetPreferredDataWithValidation() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        // Phone 0 (sub 1) should be activated as it has default data sub.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Set sub 2 as preferred sub should make phone 1 activated and phone 0 deactivated.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, null);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        // Validation failed. Preferred data sub should remain 1, data phone should remain 0.
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 2);
        processAllMessages();
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Validation succeeds. Preferred data sub changes to 2, data phone changes to 1.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 2);
        processAllMessages();
        assertEquals(1, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Switching data back to primary (subId 1) with customized validation timeout.
        long timeout = 1234;
        mContextFixture.getCarrierConfigBundle().putLong(
                KEY_DATA_SWITCH_VALIDATION_TIMEOUT_LONG, timeout);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, null);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(1), eq(timeout), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 1);
        processAllMessages();
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }

    private void mockImsRegTech(int phoneId, int regTech) {
        doReturn(regTech).when(mMockImsRegTechProvider).get(any(), eq(phoneId));
        mPhoneSwitcherUT.mImsRegTechProvider = mMockImsRegTechProvider;
    }

    @Test
    @SmallTest
    public void testNonDefaultDataPhoneInCall_ImsCallOnLte_shouldSwitchDds() throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        processAllMessages();

        // Phone 0 should be the default data phoneId.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Phone2 has active IMS call on LTE. And data of DEFAULT apn is enabled. This should
        // trigger data switch.
        doReturn(mImsPhone).when(mPhone2).getImsPhone();
        doReturn(true).when(mPhone2).isDataAllowed();
        mockImsRegTech(1, REGISTRATION_TECH_LTE);
        notifyPhoneAsInCall(mImsPhone);

        // Phone 1 should become the preferred data phone.
        assertEquals(1, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }

    @Test
    @SmallTest
    public void testNonDefaultDataPhoneInCall_ImsCallDialingOnLte_shouldSwitchDds()
            throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        processAllMessages();

        // Phone 0 should be the default data phoneId.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Dialing shouldn't trigger switch because we give modem time to deal with the dialing call
        // first. Phone2 has active IMS call on LTE. And data of DEFAULT apn is enabled.
        doReturn(mImsPhone).when(mPhone2).getImsPhone();
        doReturn(true).when(mPhone2).isDataAllowed();
        mockImsRegTech(1, REGISTRATION_TECH_LTE);
        notifyPhoneAsInDial(mImsPhone);

        // Phone1 should remain as the preferred data phone
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Dialing -> Alert, should trigger phone switch
        notifyPhoneAsAlerting(mImsPhone);

        // Phone2 should be preferred data phone
        assertEquals(1, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }
    @Test
    @SmallTest
    public void testNonDefaultDataPhoneInCall_ImsCallIncomingOnLte_shouldSwitchDds()
            throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        processAllMessages();

        // Phone 0 should be the default data phoneId.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Phone2 has active IMS call on LTE. And data of DEFAULT apn is enabled. This should
        // trigger data switch.
        doReturn(mImsPhone).when(mPhone2).getImsPhone();
        doReturn(true).when(mPhone2).isDataAllowed();
        mockImsRegTech(1, REGISTRATION_TECH_LTE);
        notifyPhoneAsInIncomingCall(mImsPhone);

        // Phone 1 should become the preferred data phone.
        assertEquals(1, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }

    @Test
    @SmallTest
    public void testNonDefaultDataPhoneInCall_ImsCallOnWlan_shouldNotSwitchDds() throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        processAllMessages();

        // Phone 0 should be the default data phoneId.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Phone2 has active call, but data is turned off. So no data switching should happen.
        doReturn(mImsPhone).when(mPhone2).getImsPhone();
        doReturn(true).when(mPhone2).isDataAllowed();
        mockImsRegTech(1, REGISTRATION_TECH_IWLAN);
        notifyPhoneAsInCall(mImsPhone);

        // Phone 0 should remain the default data phone.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }

    @Test
    @SmallTest
    public void testNonDefaultDataPhoneInCall_ImsCallOnCrossSIM_HandoverToLTE() throws Exception {
        initialize();
        setAllPhonesInactive();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        processAllMessages();

        // Phone 0 should be the default data phoneId.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Phone 1 has active IMS call on CROSS_SIM. And data of DEFAULT apn is enabled. This should
        // not trigger data switch.
        doReturn(mImsPhone).when(mPhone2).getImsPhone();
        doReturn(true).when(mPhone).isUserDataEnabled();
        doReturn(true).when(mPhone2).isDataAllowed();
        mockImsRegTech(1, REGISTRATION_TECH_CROSS_SIM);
        notifyPhoneAsInCall(mImsPhone);

        // Phone 0 should remain the default data phone.
        assertEquals(0, mPhoneSwitcherUT.getPreferredDataPhoneId());

        // Phone 1 has has handed over the call to LTE. And data of DEFAULT apn is enabled.
        // This should trigger data switch.
        mockImsRegTech(1, REGISTRATION_TECH_LTE);
        notifyImsRegistrationTechChange(mPhone2);

        // Phone 1 should become the default data phone.
        assertEquals(1, mPhoneSwitcherUT.getPreferredDataPhoneId());
    }

    @Test
    public void testNonDefaultDataPhoneInCall() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        clearInvocations(mMockRadioConfig);
        setAllPhonesInactive();
        // Initialization done.

        // Phone2 has active call, but data is turned off. So no data switching should happen.
        notifyDataEnabled(false);
        notifyPhoneAsInCall(mPhone2);
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));

        // Phone2 has active call, and data is on. So data switch to it.
        doReturn(true).when(mPhone).isUserDataEnabled();
        notifyDataEnabled(true);
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        clearInvocations(mMockRadioConfig);

        // Phone2(nDDS) call ended. But Phone1 having cross-SIM call. Don't switch.
        mockImsRegTech(0, REGISTRATION_TECH_CROSS_SIM);
        notifyPhoneAsInIncomingCall(mPhone);
        notifyPhoneAsInactive(mPhone2);
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));

        // Phone(DDS) call ended.
        // Honor auto data switch's suggestion: if DDS is OOS, auto switch to Phone2(nDDS).
        notifyPhoneAsInactive(mPhone);
        verify(mAutoDataSwitchController).evaluateAutoDataSwitch(EVALUATION_REASON_VOICE_CALL_END);
        mAutoDataSwitchCallback.onRequireValidation(1 /*Phone2*/, true);

        // verify immediately switch back to DDS upon call ends
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));

        // verify the attempt to do auto data switch to Phone2(nDDS)
        processAllFutureMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));

        // Phone2 has holding call on VoWifi, no need to switch data
        clearInvocations(mMockRadioConfig);
        mockImsRegTech(1, REGISTRATION_TECH_IWLAN);
        notifyPhoneAsInHoldingCall(mPhone2);
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
    }

    @Test
    @SmallTest
    public void testDataEnabledChangedDuringVoiceCall() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        clearInvocations(mMockRadioConfig);
        setAllPhonesInactive();
        // Initialization done.

        // Phone2 has active call and data is on. So switch to nDDS Phone2
        notifyDataEnabled(true);
        notifyPhoneAsInCall(mPhone2);
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));

        // During the active call, user turns off data, should immediately switch back to DDS
        notifyDataEnabled(false);
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
    }

    @Test
    @SmallTest
    public void testNetworkRequestOnNonDefaultData() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        NetworkRequest internetRequest = addInternetNetworkRequest(2, 50);
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));

        // Restricted network request will should be applied.
        internetRequest = addInternetNetworkRequest(2, 50, true);
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideSuccessBeforeCallStarts() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        clearInvocations(mMockRadioConfig);

        // override the phone ID in prep for emergency call
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(1, 1, mFuturePhone);
        sendPreferredDataSuccessResult(1);
        processAllMessages();
        verify(mFuturePhone).complete(true);

        // Make sure the correct broadcast is sent out for the overridden phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(2));
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideNoDdsChange() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        clearInvocations(mMockRadioConfig);

        // override the phone ID in prep for emergency call
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(0, 1, mFuturePhone);
        processAllMessages();
        // The radio command should never be called because the DDS hasn't changed.
        verify(mMockRadioConfig, never()).setPreferredDataModem(eq(0), any());
        processAllMessages();
        verify(mFuturePhone).complete(true);
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideEndSuccess() throws Exception {
        PhoneSwitcher.ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS = 500;
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        setAllPhonesInactive();
        clearInvocations(mMockRadioConfig);
        clearInvocations(mTelephonyRegistryManager);

        // override the phone ID in prep for emergency call

        mPhoneSwitcherUT.overrideDefaultDataForEmergency(1, 1, mFuturePhone);
        sendPreferredDataSuccessResult(1);
        processAllMessages();
        verify(mFuturePhone).complete(true);

        // Start and end the emergency call, which will start override timer
        notifyPhoneAsInCall(mPhone2);
        notifyPhoneAsInactive(mPhone2);

        clearInvocations(mTelephonyRegistryManager);
        // Verify that the DDS is successfully switched back after 1 second + base ECBM timeout
        moveTimeForward(ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS + 1000);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        // Make sure the correct broadcast is sent out for the phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(1));
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideEcbmStartEnd() throws Exception {
        PhoneSwitcher.ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS = 500;
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        setAllPhonesInactive();
        clearInvocations(mMockRadioConfig);
        clearInvocations(mTelephonyRegistryManager);

        // override the phone ID in prep for emergency call
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(1, 1, mFuturePhone);
        sendPreferredDataSuccessResult(1);
        processAllMessages();
        verify(mFuturePhone).complete(true);

        // Start and end the emergency call, which will start override timer
        notifyPhoneAsInCall(mPhone2);
        notifyPhoneAsInactive(mPhone2);
        // Start ECBM
        Message ecbmMessage = getEcbmRegistration(mPhone2);
        notifyEcbmStart(mPhone2, ecbmMessage);

        // DDS should not be switched back until ECBM ends, make sure there is no further
        // interaction.
        moveTimeForward(ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS + 2000);
        processAllMessages();
        verify(mMockRadioConfig, never()).setPreferredDataModem(eq(0), any());
        // Make sure the correct broadcast is sent out for the phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(2));

        // End ECBM
        clearInvocations(mTelephonyRegistryManager);
        ecbmMessage = getEcbmRegistration(mPhone2);
        notifyEcbmEnd(mPhone2, ecbmMessage);
        // Verify that the DDS is successfully switched back after 1 second.
        moveTimeForward(1000);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();
        // Make sure the correct broadcast is sent out for the phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(1));
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideNoCallStart() throws Exception {
        PhoneSwitcher.DEFAULT_DATA_OVERRIDE_TIMEOUT_MS = 500;
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        setAllPhonesInactive();
        clearInvocations(mMockRadioConfig);
        clearInvocations(mTelephonyRegistryManager);

        // override the phone ID in prep for emergency call
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(1, 1, mFuturePhone);
        sendPreferredDataSuccessResult(1);
        processAllMessages();
        verify(mFuturePhone).complete(true);

        // Do not start the call and make sure the override is removed once the timeout expires
        moveTimeForward(PhoneSwitcher.DEFAULT_DATA_OVERRIDE_TIMEOUT_MS);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        // Make sure the correct broadcast is sent out for the phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(1));
    }

    @Test
    @SmallTest
    public void testEmergencyOverrideMultipleOverrideRequests() throws Exception {
        PhoneSwitcher.ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS = 500;
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setMsimDefaultDataSubId(1);
        setAllPhonesInactive();
        clearInvocations(mMockRadioConfig);
        clearInvocations(mTelephonyRegistryManager);

        // override the phone ID in prep for emergency call
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>();
        CompletableFuture<Boolean> futurePhone = new CompletableFuture<>();
        futurePhone.whenComplete((r, error) -> queue.offer(r));
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(1, 1, futurePhone);
        sendPreferredDataSuccessResult(1);
        processAllMessages();
        Boolean result = queue.poll();
        assertNotNull(result);
        assertTrue(result);

        // try override the phone ID again while there is an existing override for a different phone
        futurePhone = new CompletableFuture<>();
        futurePhone.whenComplete((r, error) -> queue.offer(r));
        mPhoneSwitcherUT.overrideDefaultDataForEmergency(0, 1, futurePhone);
        processAllMessages();
        result = queue.poll();
        assertNotNull(result);
        assertFalse(result);
        verify(mMockRadioConfig, never()).setPreferredDataModem(eq(0), any());

        // Start and end the emergency call, which will start override timer
        notifyPhoneAsInCall(mPhone2);
        notifyPhoneAsInactive(mPhone2);

        // Verify that the DDS is successfully switched back after 1 second + base ECBM timeout
        moveTimeForward(ECBM_DEFAULT_DATA_SWITCH_BASE_TIME_MS + 1000);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        AsyncResult res = new AsyncResult(1, null,  null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        // Make sure the correct broadcast is sent out for the phone ID
        verify(mTelephonyRegistryManager).notifyActiveDataSubIdChanged(eq(1));
    }

    @Test
    @SmallTest
    public void testSetPreferredDataCallback() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);

        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        // Switch to primary before a primary is selected/inactive.
        setDefaultDataSubId(-1);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, mSetOpptDataCallback1);
        processAllMessages();

        assertEquals(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                mPhoneSwitcherUT.getAutoSelectedDataSubId());
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION);

        // once the primary is selected, it becomes the active sub.
        setDefaultDataSubId(2);
        assertEquals(2, mPhoneSwitcherUT.getActiveDataSubId());

        setDefaultDataSubId(1);
        // Validating on sub 10 which is inactive.
        clearInvocations(mSetOpptDataCallback1);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(10, true, mSetOpptDataCallback1);
        processAllMessages();
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION);

        // Switch to active subId without validating. Should always succeed.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, mSetOpptDataCallback1);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 2);
        processAllMessages();
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_SUCCESS);

        // Validating on sub 1 and fails.
        clearInvocations(mSetOpptDataCallback1);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(1, true, mSetOpptDataCallback1);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 1);
        processAllMessages();
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);

        // Validating on sub 2 and succeeds.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, mSetOpptDataCallback2);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 2);
        processAllMessages();
        verify(mSetOpptDataCallback2).onComplete(SET_OPPORTUNISTIC_SUB_SUCCESS);

        // Switching data back to primary and validation fails.
        clearInvocations(mSetOpptDataCallback2);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, mSetOpptDataCallback2);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 1);
        processAllMessages();
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);

        // Switching data back to primary and succeeds.
        clearInvocations(mSetOpptDataCallback2);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, mSetOpptDataCallback2);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 1);
        processAllMessages();
        verify(mSetOpptDataCallback2).onComplete(SET_OPPORTUNISTIC_SUB_SUCCESS);

        // Back to back call on same subId.
        clearInvocations(mSetOpptDataCallback1);
        clearInvocations(mSetOpptDataCallback2);
        clearInvocations(mCellularNetworkValidator);
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, mSetOpptDataCallback1);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        doReturn(true).when(mCellularNetworkValidator).isValidating();
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, mSetOpptDataCallback2);
        processAllMessages();
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);
        verify(mSetOpptDataCallback2, never()).onComplete(anyInt());
        // Validation succeeds.
        doReturn(false).when(mCellularNetworkValidator).isValidating();
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(true, 2);
        processAllMessages();
        verify(mSetOpptDataCallback2).onComplete(SET_OPPORTUNISTIC_SUB_SUCCESS);

        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false, null);
        processAllMessages();
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 1);
        processAllMessages();
        clearInvocations(mSetOpptDataCallback1);
        clearInvocations(mSetOpptDataCallback2);
        clearInvocations(mCellularNetworkValidator);
        // Back to back call, call 1 to switch to subId 2, call 2 to switch back.
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, true, mSetOpptDataCallback1);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        doReturn(true).when(mCellularNetworkValidator).isValidating();
        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true, mSetOpptDataCallback2);
        processAllMessages();
        // Call 1 should be cancelled and failed. Call 2 return success immediately as there's no
        // change.
        verify(mSetOpptDataCallback1).onComplete(SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED);
        verify(mSetOpptDataCallback2).onComplete(SET_OPPORTUNISTIC_SUB_SUCCESS);
    }

    @Test
    @SmallTest
    public void testMultiSimConfigChange() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        mActiveModemCount = 1;
        initialize();
        sendPreferredDataSuccessResult(0);

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setDefaultDataSubId(1);

        setNumPhones(2, 2);
        AsyncResult result = new AsyncResult(null, 2, null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MULTI_SIM_CONFIG_CHANGED, result).sendToTarget();
        processAllMessages();

        verify(mPhone2).registerForEmergencyCallToggle(any(), anyInt(), any());
        verify(mPhone2).registerForPreciseCallStateChanged(any(), anyInt(), any());

        clearInvocations(mMockRadioConfig);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(2);
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
    }

    @Test
    @SmallTest
    public void testValidationOffSwitch_shouldSwitchOnNetworkAvailable() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        clearInvocations(mMockRadioConfig);
        setAllPhonesInactive();
        // Initialization done.

        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, mSetOpptDataCallback1);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        doReturn(true).when(mCellularNetworkValidator).isValidating();

        // Network available on different sub. Should do nothing.
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 1);
        processAllMessages();
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());

        // Network available on corresponding sub. Should confirm switch.
        mPhoneSwitcherUT.mValidationCallback.onNetworkAvailable(null, 2);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
    }

    @Test
    @SmallTest
    public void testValidationOffSwitch_shouldSwitchOnTimeOut() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();
        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 0));
        assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                new TelephonyNetworkRequest(internetRequest, mPhone), 1));
        clearInvocations(mMockRadioConfig);
        setAllPhonesInactive();
        // Initialization done.

        doReturn(new SubscriptionInfoInternal.Builder(mSubscriptionManagerService
                .getSubscriptionInfoInternal(2)).setOpportunistic(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(2);

        mPhoneSwitcherUT.trySetOpportunisticDataSubscription(2, false, mSetOpptDataCallback1);
        processAllMessages();
        verify(mCellularNetworkValidator).validate(eq(2), anyLong(), eq(false),
                eq(mPhoneSwitcherUT.mValidationCallback));
        doReturn(true).when(mCellularNetworkValidator).isValidating();

        // Validation failed on different sub. Should do nothing.
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 1);
        processAllMessages();
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());

        // Network available on corresponding sub. Should confirm switch.
        mPhoneSwitcherUT.mValidationCallback.onValidationDone(false, 2);
        processAllMessages();
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
    }

    @Test
    public void testRetry_DDS_switch_Failure() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        mActiveModemCount = 2;
        initialize();
        setSlotIndexToSubId(0, 1);
        setDefaultDataSubId(1);

        clearInvocations(mMockRadioConfig);
        // for exceptions OP_NOT_ALLOWED_DURING_VOICE_CALL and INVALID_SIM_STATE,
        // modem retry not invoked.
        AsyncResult res1 = new AsyncResult(0, null,
                new CommandException(CommandException.Error.INVALID_SIM_STATE));
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res1).sendToTarget();
        processAllMessages();
        moveTimeForward(5000);
        processAllMessages();
        verify(mMockRadioConfig, times(0)).setPreferredDataModem(eq(0), any());

        AsyncResult res2 = new AsyncResult(0, null,
                new CommandException(CommandException.Error.NETWORK_NOT_READY));
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res2).sendToTarget();
        processAllMessages();
        moveTimeForward(5000);
        processAllMessages();

        verify(mMockRadioConfig, times(1)).setPreferredDataModem(eq(0), any());

        clearInvocations(mMockRadioConfig);
        doReturn(mSubscriptionInfo).when(mSubscriptionManagerService)
                .getActiveSubscriptionInfoForSimSlotIndex(eq(0), any(), any());
        doReturn(true).when(mSubscriptionInfo).areUiccApplicationsEnabled();
        doReturn(mIccCard).when(mPhone).getIccCard();
        doReturn(true).when(mIccCard).isEmptyProfile();
        final Intent intent1 = new Intent(ACTION_SIM_APPLICATION_STATE_CHANGED);
        intent1.putExtra(EXTRA_SIM_STATE, SIM_STATE_LOADED);
        intent1.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0);
        mContext.sendBroadcast(intent1);
        processAllMessages();

        verify(mMockRadioConfig, times(0)).setPreferredDataModem(eq(0), any());

        doReturn(false).when(mIccCard).isEmptyProfile();
        final Intent intent2 = new Intent(ACTION_SIM_APPLICATION_STATE_CHANGED);
        intent2.putExtra(EXTRA_SIM_STATE, SIM_STATE_LOADED);
        intent2.putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0);
        mContext.sendBroadcast(intent2);
        processAllMessages();

        verify(mMockRadioConfig, times(1)).setPreferredDataModem(eq(0), any());
    }

    @Test
    public void testScheduledRetryWhileMultiSimConfigChange() throws Exception {
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize();

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);

        // for EVENT_MODEM_COMMAND_RETRY
        AsyncResult res = new AsyncResult(
                1, null,  new CommandException(CommandException.Error.GENERIC_FAILURE));
        Message.obtain(mPhoneSwitcherUT, EVENT_MODEM_COMMAND_DONE, res).sendToTarget();
        processAllMessages();

        // reduce count of phone
        setNumPhones(1, 1);
        AsyncResult result = new AsyncResult(null, 1, null);
        Message.obtain(mPhoneSwitcherUT, EVENT_MULTI_SIM_CONFIG_CHANGED, result).sendToTarget();
        processAllMessages();

        // fire retries
        moveTimeForward(5000);
        processAllMessages();

        verify(mCommandsInterface0, never()).setDataAllowed(anyBoolean(), any());
        verify(mCommandsInterface1, never()).setDataAllowed(anyBoolean(), any());
    }

    /* Private utility methods start here */

    private void prepareIdealAutoSwitchCondition() {
        // 1. service state changes
        serviceStateChanged(1, NetworkRegistrationInfo.REGISTRATION_STATE_HOME);
        serviceStateChanged(0, NetworkRegistrationInfo
                .REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING);

        // 2.1 User data enabled on primary SIM
        doReturn(true).when(mPhone).isUserDataEnabled();

        // 2.2 Auto switch feature is enabled
        doReturn(true).when(mPhone2).isDataAllowed();

        // 3.1 No default network
        doReturn(null).when(mConnectivityManager).getNetworkCapabilities(any());

        mPhoneSwitcherUT.sendEmptyMessage(EVENT_EVALUATE_AUTO_SWITCH);
    }

    private void serviceStateChanged(int phoneId,
            @NetworkRegistrationInfo.RegistrationState int dataRegState) {

        ServiceState ss = new ServiceState();

        ss.addNetworkRegistrationInfo(new NetworkRegistrationInfo.Builder()
                .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .setRegistrationState(dataRegState)
                .setDomain(NetworkRegistrationInfo.DOMAIN_PS)
                .build());

        ss.setDataRoamingFromRegistration(dataRegState
                == NetworkRegistrationInfo.REGISTRATION_STATE_ROAMING);

        doReturn(ss).when(mPhones[phoneId]).getServiceState();

        Message msg = mPhoneSwitcherUT.obtainMessage(EVENT_SERVICE_STATE_CHANGED);
        msg.obj = new AsyncResult(phoneId, null, null);
        mPhoneSwitcherUT.sendMessage(msg);
    }

    private void setAllPhonesInactive() {
        doReturn(mInactiveCall).when(mPhone).getForegroundCall();
        doReturn(mInactiveCall).when(mPhone).getBackgroundCall();
        doReturn(mInactiveCall).when(mPhone).getRingingCall();
        doReturn(mInactiveCall).when(mPhone2).getForegroundCall();
        doReturn(mInactiveCall).when(mPhone2).getBackgroundCall();
        doReturn(mInactiveCall).when(mPhone2).getRingingCall();
        doReturn(mInactiveCall).when(mImsPhone).getForegroundCall();
        doReturn(mInactiveCall).when(mImsPhone).getBackgroundCall();
        doReturn(mInactiveCall).when(mImsPhone).getRingingCall();
    }

    private void notifyPhoneAsInCall(Phone phone) {
        doReturn(mActiveCall).when(phone).getForegroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyPhoneAsInDial(Phone phone) {
        doReturn(mDialCall).when(phone).getForegroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyPhoneAsAlerting(Phone phone) {
        doReturn(mAlertingCall).when(phone).getForegroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyPhoneAsInIncomingCall(Phone phone) {
        doReturn(mIncomingCall).when(phone).getForegroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyPhoneAsInHoldingCall(Phone phone) {
        doReturn(mHoldingCall).when(phone).getBackgroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyPhoneAsInactive(Phone phone) {
        doReturn(mInactiveCall).when(phone).getForegroundCall();
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_PRECISE_CALL_STATE_CHANGED);
        processAllMessages();
    }

    private void notifyDataEnabled(boolean dataEnabled) {
        doReturn(true).when(mPhone).isUserDataEnabled();
        doReturn(dataEnabled).when(mDataSettingsManager).isDataEnabled();
        doReturn(dataEnabled).when(mPhone2).isDataAllowed();
        mDataSettingsManagerCallbacks.get(0).onDataEnabledChanged(dataEnabled, 123 , "");
        if (mDataSettingsManagerCallbacks.size() > 1) {
            mDataSettingsManagerCallbacks.get(1).onDataEnabledChanged(dataEnabled, 123, "");
        }
        processAllMessages();
    }

    private void notifyImsRegistrationTechChange(Phone phone) {
        mPhoneSwitcherUT.sendEmptyMessage(EVENT_IMS_RADIO_TECH_CHANGED);
        processAllMessages();
    }

    private Message getEcbmRegistration(Phone phone) {
        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        ArgumentCaptor<Integer> intCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(phone).registerForEmergencyCallToggle(handlerCaptor.capture(), intCaptor.capture(),
                any());
        assertNotNull(handlerCaptor.getValue());
        assertNotNull(intCaptor.getValue());
        Message message = Message.obtain(handlerCaptor.getValue(), intCaptor.getValue());
        return message;
    }

    private void notifyEcbmStart(Phone phone, Message ecmMessage) {
        doReturn(mInactiveCall).when(phone).getForegroundCall();
        doReturn(true).when(phone).isInEcm();
        ecmMessage.sendToTarget();
        processAllMessages();
    }

    private void notifyEcbmEnd(Phone phone, Message ecmMessage) {
        doReturn(false).when(phone).isInEcm();
        ecmMessage.sendToTarget();
        processAllMessages();
    }

    private void sendPreferredDataSuccessResult(int phoneId) {
        // make sure the radio command is called and then send a success result
        processAllMessages();
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig).setPreferredDataModem(eq(phoneId), msgCaptor.capture());
        assertNotNull(msgCaptor.getValue());
        // Send back successful result
        AsyncResult.forMessage(msgCaptor.getValue(), null, null);
        msgCaptor.getValue().sendToTarget();
        processAllMessages();
    }

    private void setMsimDefaultDataSubId(int defaultDataSub) throws Exception {
        for (int i = 0; i < mActiveModemCount; i++) {
            setSlotIndexToSubId(i, i + 1);
        }
        setDefaultDataSubId(defaultDataSub);
        NetworkRequest internetRequest = addInternetNetworkRequest(null, 50);
        for (int i = 0; i < mActiveModemCount; i++) {
            if (defaultDataSub == (i + 1)) {
                // sub id is always phoneId+1 for testing
                assertTrue(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                        new TelephonyNetworkRequest(internetRequest, mPhone), i));
            } else {
                assertFalse(mPhoneSwitcherUT.shouldApplyNetworkRequest(
                        new TelephonyNetworkRequest(internetRequest, mPhone), i));
            }
        }
    }

    private void sendDefaultDataSubChanged() {
        final Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.sendBroadcast(intent);
        processAllMessages();
    }

    private void initialize() throws Exception {
        setNumPhones(mActiveModemCount, mSupportedModemCount);

        initializeSubControllerMock();
        initializeCommandInterfacesMock();
        initializeTelRegistryMock();
        initializeConnManagerMock();
        initializeConfigMock();

        mPhoneSwitcherUT = new PhoneSwitcher(mMaxDataAttachModemCount, mContext, Looper.myLooper());

        Field field = PhoneSwitcher.class.getDeclaredField("mDataSettingsManagerCallbacks");
        field.setAccessible(true);
        mDataSettingsManagerCallbacks =
                (Map<Integer, DataSettingsManager.DataSettingsManagerCallback>)
                        field.get(mPhoneSwitcherUT);

        field = PhoneSwitcher.class.getDeclaredField("mAutoDataSwitchCallback");
        field.setAccessible(true);
        mAutoDataSwitchCallback = (AutoDataSwitchController.AutoDataSwitchControllerCallback)
                field.get(mPhoneSwitcherUT);

        replaceInstance(PhoneSwitcher.class, "mAutoDataSwitchController", mPhoneSwitcherUT,
                mAutoDataSwitchController);
        processAllMessages();

        verify(mTelephonyRegistryManager).addOnSubscriptionsChangedListener(any(), any());
    }

    /**
     * Certain variables needs initialized depending on number of phones.
     */
    private void setNumPhones(int activeModemCount, int supportedModemCount) throws Exception {
        mDataAllowed = new boolean[supportedModemCount];
        mSlotIndexToSubId = new int[supportedModemCount][];
        doReturn(0).when(mPhone).getPhoneId();
        doReturn(1).when(mPhone2).getPhoneId();
        doReturn(true).when(mPhone2).isUserDataEnabled();
        doReturn(mDataSettingsManager2).when(mPhone2).getDataSettingsManager();
        doReturn(mSST2).when(mPhone2).getServiceStateTracker();
        doReturn(mDisplayInfoController).when(mPhone2).getDisplayInfoController();
        doReturn(mSignalStrengthController).when(mPhone2).getSignalStrengthController();
        doReturn(mSignalStrength).when(mPhone2).getSignalStrength();
        for (int i = 0; i < supportedModemCount; i++) {
            mSlotIndexToSubId[i] = new int[1];
            mSlotIndexToSubId[i][0] = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        doReturn(activeModemCount).when(mTelephonyManager).getPhoneCount();
        doReturn(activeModemCount).when(mTelephonyManager).getActiveModemCount();
        doReturn(supportedModemCount).when(mTelephonyManager).getSupportedModemCount();

        if (activeModemCount == 1) {
            mPhones = new Phone[]{mPhone};
        } else if (activeModemCount == 2) {
            mPhones = new Phone[]{mPhone, mPhone2};
        }

        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
    }

    private void initializeCommandInterfacesMock() {
        // Tell PhoneSwitcher that radio is on.
        doAnswer(invocation -> {
            Handler handler = (Handler) invocation.getArguments()[0];
            int message = (int) invocation.getArguments()[1];
            Object obj = invocation.getArguments()[2];
            handler.obtainMessage(message, obj).sendToTarget();
            return null;
        }).when(mCommandsInterface0).registerForAvailable(any(), anyInt(), any());

        // Store values of dataAllowed in mDataAllowed[] for easier checking.
        doAnswer(invocation -> {
            mDataAllowed[0] = (boolean) invocation.getArguments()[0];
            return null;
        }).when(mCommandsInterface0).setDataAllowed(anyBoolean(), any());

        if (mSupportedModemCount > 1) {
            doAnswer(invocation -> {
                mDataAllowed[1] = (boolean) invocation.getArguments()[0];
                return null;
            }).when(mCommandsInterface1).setDataAllowed(anyBoolean(), any());
        }
    }

    /**
     * Store subChangedListener of PhoneSwitcher so that testing can notify
     * PhoneSwitcher of sub change.
     */
    private void initializeTelRegistryMock() throws Exception {
        doAnswer(invocation -> {
            SubscriptionManager.OnSubscriptionsChangedListener subChangedListener =
                    (SubscriptionManager.OnSubscriptionsChangedListener) invocation.getArguments()[0];
            mSubChangedListener = subChangedListener;
            mSubChangedListener.onSubscriptionsChanged();
            return null;
        }).when(mTelephonyRegistryManager).addOnSubscriptionsChangedListener(any(), any());
    }

    /**
     * Capture mNetworkProviderMessenger so that testing can request or release
     * network requests on PhoneSwitcher.
     */
    private void initializeConnManagerMock() {
        mConnectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        doAnswer(invocation -> {
            mNetworkProviderMessenger =
                    ((NetworkProvider) invocation.getArgument(0)).getMessenger();
            return null;
        }).when(mConnectivityManager).registerNetworkProvider(any());
    }

    /**
     * Capture mNetworkProviderMessenger so that testing can request or release
     * network requests on PhoneSwitcher.
     */
    private void initializeSubControllerMock() throws Exception {
        doReturn(mDefaultDataSub).when(mSubscriptionManagerService).getDefaultDataSubId();
        doReturn(mDefaultDataSub).when(mMockedIsub).getDefaultDataSubId();
        doReturn(0).when(mSubscriptionManagerService).getPhoneId(1);
        doReturn(0).when(mMockedIsub).getPhoneId(1);
        doReturn(1).when(mSubscriptionManagerService).getPhoneId(2);
        doReturn(1).when(mMockedIsub).getPhoneId(2);

        doAnswer(invocation -> {
            int phoneId = (int) invocation.getArguments()[0];
            if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            } else if (phoneId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
                return mSlotIndexToSubId[0][0];
            } else {
                return mSlotIndexToSubId[phoneId][0];
            }
        }).when(mMockedIsub).getSubId(anyInt());

        doAnswer(invocation -> {
            int phoneId = (int) invocation.getArguments()[0];
            if (phoneId == SubscriptionManager.INVALID_PHONE_INDEX) {
                return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            } else if (phoneId == SubscriptionManager.DEFAULT_PHONE_INDEX) {
                return mSlotIndexToSubId[0][0];
            } else {
                return mSlotIndexToSubId[phoneId][0];
            }
        }).when(mSubscriptionManagerService).getSubId(anyInt());

        doAnswer(invocation -> {
            int subId = (int) invocation.getArguments()[0];

            if (!SubscriptionManager.isUsableSubIdValue(subId)) return null;

            int slotIndex = -1;
            for (int i = 0; i < mSlotIndexToSubId.length; i++) {
                if (mSlotIndexToSubId[i][0] == subId) slotIndex = i;
            }
            return new SubscriptionInfoInternal.Builder()
                    .setSimSlotIndex(slotIndex).setId(subId).build();
        }).when(mSubscriptionManagerService).getSubscriptionInfoInternal(anyInt());

        doReturn(new int[mSlotIndexToSubId.length]).when(mSubscriptionManagerService)
                .getActiveSubIdList(true);
    }

    private void initializeConfigMock() {
        doReturn(mDataNetworkController).when(mPhone).getDataNetworkController();
        doReturn(mDataConfigManager).when(mDataNetworkController).getDataConfigManager();
        doReturn(1000L).when(mDataConfigManager)
                .getAutoDataSwitchAvailabilityStabilityTimeThreshold();
        doReturn(7).when(mDataConfigManager).getAutoDataSwitchValidationMaxRetry();
        doReturn(true).when(mDataConfigManager).isPingTestBeforeAutoDataSwitchRequired();
    }

    private void setDefaultDataSubId(int defaultDataSub) throws Exception {
        mDefaultDataSub = defaultDataSub;
        doReturn(mDefaultDataSub).when(mSubscriptionManagerService).getDefaultDataSubId();
        for (Phone phone : mPhones) {
            doAnswer(invocation -> phone.getSubId() == mDefaultDataSub)
                    .when(phone).isUserDataEnabled();
        }
        sendDefaultDataSubChanged();
    }

    private void setSlotIndexToSubId(int slotId, int subId) {
        mSlotIndexToSubId[slotId][0] = subId;
        Phone phone = slotId == 0 ? mPhone : mPhone2;
        doReturn(subId).when(phone).getSubId();
    }

    /**
     * Create an internet PDN network request and send it to PhoneSwitcher.
     */
    private NetworkRequest addInternetNetworkRequest(Integer subId, int score) throws Exception {
        return addInternetNetworkRequest(subId, score, false);
    }

    private NetworkRequest addInternetNetworkRequest(Integer subId, int score, boolean restricted)
            throws Exception {
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (restricted) {
            netCap.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }

        if (subId != null) {
            netCap.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                    .setSubscriptionId(subId).build());
        }
        NetworkRequest networkRequest = new NetworkRequest(netCap, ConnectivityManager.TYPE_NONE,
                0, NetworkRequest.Type.REQUEST);

        Message message = Message.obtain();
        message.what = android.net.NetworkProvider.CMD_REQUEST_NETWORK;
        message.arg1 = score;
        message.obj = networkRequest;
        mNetworkProviderMessenger.send(message);
        processAllMessages();

        return networkRequest;
    }

    /**
     * Create a mms PDN network request and send it to PhoneSwitcher.
     */
    private NetworkRequest addMmsNetworkRequest(Integer subId) throws Exception {
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (subId != null) {
            netCap.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                    .setSubscriptionId(subId).build());
        }
        NetworkRequest networkRequest = new NetworkRequest(netCap, ConnectivityManager.TYPE_NONE,
                1, NetworkRequest.Type.REQUEST);

        Message message = Message.obtain();
        message.what = android.net.NetworkProvider.CMD_REQUEST_NETWORK;
        message.arg1 = 50; // Score
        message.obj = networkRequest;
        mNetworkProviderMessenger.send(message);
        processAllMessages();

        return networkRequest;
    }

    /**
     * Tell PhoneSwitcher to release a network request.
     */
    private void releaseNetworkRequest(NetworkRequest networkRequest) throws Exception {
        Message message = Message.obtain();
        message.what = android.net.NetworkProvider.CMD_CANCEL_REQUEST;
        message.obj = networkRequest;
        mNetworkProviderMessenger.send(message);
        processAllMessages();
    }
}
