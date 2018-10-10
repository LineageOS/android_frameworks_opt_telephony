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

package com.android.internal.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.StringNetworkSpecifier;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class PhoneSwitcherTest extends TelephonyTest {
    private static final String[] sNetworkAttributes = new String[] {
            "mobile,0,0,0,-1,true", "mobile_mms,2,0,2,60000,true",
            "mobile_supl,3,0,2,60000,true", "mobile_dun,4,0,2,60000,true",
            "mobile_hipri,5,0,3,60000,true", "mobile_fota,10,0,2,60000,true",
            "mobile_ims,11,0,2,60000,true", "mobile_cbs,12,0,2,60000,true",
            "mobile_ia,14,0,2,-1,true", "mobile_emergency,15,0,2,-1,true"};

    private static final int ACTIVE_PHONE_SWITCH = 1;

    @Mock
    private ITelephonyRegistry.Stub mTelRegistryMock;
    @Mock
    private CommandsInterface mCommandsInterface0;
    @Mock
    private CommandsInterface mCommandsInterface1;
    @Mock
    private Phone mPhone2; // mPhone as phone 1 is already defined in TelephonyTest.
    @Mock
    private Handler mActivePhoneSwitchHandler;

    // The thread that mPhoneSwitcher will handle events in.
    private HandlerThread mHandlerThread;
    private PhoneSwitcher mPhoneSwitcher;
    private IOnSubscriptionsChangedListener mSubChangedListener;
    private ConnectivityManager mConnectivityManager;
    // The messenger of PhoneSwitcher used to receive network requests.
    private Messenger mNetworkFactoryMessenger = null;
    private int mDefaultDataSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private CommandsInterface[] mCommandsInterfaces;
    private int[][] mSlotIndexToSubId;
    private boolean[] mDataAllowed;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test that a single phone case results in our phone being active and the RIL called
     */
    @Test
    @SmallTest
    public void testRegister() throws Exception {
        final int numPhones = 2;
        final int maxActivePhones = 1;
        initialize(numPhones, maxActivePhones);

        // verify nothing has been done while there are no inputs
        assertFalse("data allowed initially", mDataAllowed[0]);
        assertFalse("data allowed initially", mDataAllowed[0]);
        assertFalse("phone active initially", mPhoneSwitcher.shouldApplySpecifiedRequests(0));

        NetworkRequest internetNetworkRequest = addInternetNetworkRequest(null, 50);
        waitABit();

        assertFalse("data allowed after request", mDataAllowed[0]);
        assertFalse("phone active after request", mPhoneSwitcher.shouldApplySpecifiedRequests(0));

        // not registered yet - shouldn't inc
        verify(mActivePhoneSwitchHandler, never()).sendMessageAtTime(any(), anyLong());

        boolean threw = false;
        try {
            // should throw
            mPhoneSwitcher.registerForActivePhoneSwitch(2, mActivePhoneSwitchHandler,
                    ACTIVE_PHONE_SWITCH, null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        assertTrue("register with bad phoneId didn't throw", threw);

        mPhoneSwitcher.registerForActivePhoneSwitch(0, mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);

        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());


        setDefaultDataSubId(0);

        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);

        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
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
        waitABit();

        verify(mActivePhoneSwitchHandler, times(3)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);

        setSlotIndexToSubId(1, 1);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(3)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertTrue("data not allowed", mDataAllowed[1]);

        // 2 gain default via default sub change
        setDefaultDataSubId(0);
        waitABit();

        verify(mActivePhoneSwitchHandler, times(4)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[1]);
        assertTrue("data not allowed", mDataAllowed[0]);

        // 3 lose default via sub->phone change
        setSlotIndexToSubId(0, 2);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(5)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 4 gain default via sub->phone change
        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(6)).sendMessageAtTime(any(), anyLong());
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 5 lose default network request
        releaseNetworkRequest(internetNetworkRequest);
        waitABit();

        verify(mActivePhoneSwitchHandler, times(7)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 6 gain subscription-specific request
        NetworkRequest specificInternetRequest = addInternetNetworkRequest(0, 50);
        waitABit();

        verify(mActivePhoneSwitchHandler, times(8)).sendMessageAtTime(any(), anyLong());
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 7 lose via sub->phone change
        setSlotIndexToSubId(0, 1);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(9)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 8 gain via sub->phone change
        setSlotIndexToSubId(0, 0);
        mSubChangedListener.onSubscriptionsChanged();
        waitABit();

        verify(mActivePhoneSwitchHandler, times(10)).sendMessageAtTime(any(), anyLong());
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 9 lose subscription-specific request
        releaseNetworkRequest(specificInternetRequest);
        waitABit();

        verify(mActivePhoneSwitchHandler, times(11)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // 10 don't switch phones when in emergency mode
        // not ready yet - Phone turns out to be hard to stub out
//        phones[0].setInEmergencyCall(true);
//        connectivityServiceMock.addDefaultRequest();
//        waitABit();
//        if (testHandler.getActivePhoneSwitchCount() != 11) {
//            fail("after release of request, ActivePhoneSwitchCount not 11!");
//        }
//        if (commandsInterfaces[0].isDataAllowed()) fail("data allowed");
//        if (commandsInterfaces[1].isDataAllowed()) fail("data allowed");
//
//        phones[0].setInEmergencyCall(false);
//        connectivityServiceMock.addDefaultRequest();
//        waitABit();
//        if (testHandler.getActivePhoneSwitchCount() != 12) {
//            fail("after release of request, ActivePhoneSwitchCount not 11!");
//        }
//        if (commandsInterfaces[0].isDataAllowed()) fail("data allowed");
//        if (commandsInterfaces[1].isDataAllowed()) fail("data allowed");

        mHandlerThread.quit();
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
        final int numPhones = 2;
        final int maxActivePhones = 1;
        initialize(numPhones, maxActivePhones);

        addInternetNetworkRequest(null, 50);
        setSlotIndexToSubId(0, 0);
        setSlotIndexToSubId(1, 1);
        setDefaultDataSubId(0);
        waitABit();
        mPhoneSwitcher.registerForActivePhoneSwitch(0, mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        waitABit();
        // verify initial conditions
        verify(mActivePhoneSwitchHandler, times(1)).sendMessageAtTime(any(), anyLong());

        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        // now start a higher priority conneciton on the other sub
        addMmsNetworkRequest(1);
        waitABit();

        // After gain of network request, mActivePhoneSwitchHandler should be notified 2 times.
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertFalse("data allowed", mDataAllowed[0]);
        assertTrue("data not allowed", mDataAllowed[1]);

        mHandlerThread.quit();
    }

    /**
     * Verify we don't send spurious DATA_ALLOWED calls when another NetworkFactory
     * wins (ie, switch to wifi).
     */
    @Test
    @SmallTest
    public void testHigherPriorityDefault() throws Exception {
        final int numPhones = 2;
        final int maxActivePhones = 1;
        initialize(numPhones, maxActivePhones);

        addInternetNetworkRequest(null, 50);
        setSlotIndexToSubId(0, 0);
        setSlotIndexToSubId(1, 1);
        setDefaultDataSubId(0);
        waitABit();

        // Phone 0 should be active
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        addInternetNetworkRequest(null, 100);
        waitABit();

        // should be no change
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        addInternetNetworkRequest(null, 0);
        waitABit();

        // should be no change
        assertTrue("data not allowed", mDataAllowed[0]);
        assertFalse("data allowed", mDataAllowed[1]);

        mHandlerThread.quit();
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
        final int numPhones = 2;
        final int maxActivePhones = 1;
        initialize(numPhones, maxActivePhones);

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);

        // Notify phoneSwitcher about default data sub and default network request.
        addInternetNetworkRequest(null, 50);
        waitABit();
        // Phone 0 (sub 1) should be activated as it has default data sub.
        assertTrue(mDataAllowed[0]);

        // Set sub 2 as preferred sub should make phone 1 activated and phone 0 deactivated.
        mPhoneSwitcher.setPreferredData(2);
        waitABit();
        assertFalse(mDataAllowed[0]);
        assertTrue(mDataAllowed[1]);

        // Unset preferred sub should make default data sub (phone 0 / sub 1) activated again.
        mPhoneSwitcher.setPreferredData(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        waitABit();
        assertTrue(mDataAllowed[0]);
        assertFalse(mDataAllowed[1]);

        mHandlerThread.quit();
    }

    @Test
    @SmallTest
    public void testSetPreferredDataModemCommand() throws Exception {
        final int numPhones = 2;
        final int maxActivePhones = 1;
        doReturn(true).when(mMockRadioConfig).isSetPreferredDataCommandSupported();
        initialize(numPhones, maxActivePhones);
        mPhoneSwitcher.registerForActivePhoneSwitch(1, mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        mPhoneSwitcher.registerForActivePhoneSwitch(0, mActivePhoneSwitchHandler,
                ACTIVE_PHONE_SWITCH, null);
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        clearInvocations(mActivePhoneSwitchHandler);

        // Phone 0 has sub 1, phone 1 has sub 2.
        // Sub 1 is default data sub.
        // Both are active subscriptions are active sub, as they are in both active slots.
        setSlotIndexToSubId(0, 1);
        setSlotIndexToSubId(1, 2);
        setDefaultDataSubId(1);
        waitABit();
        // Phone 0 (sub 1) should preferredDataModem it has default data sub.
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(0));
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(1));
        assertTrue(mPhoneSwitcher.shouldApplyUnspecifiedRequests(0));
        assertFalse(mPhoneSwitcher.shouldApplyUnspecifiedRequests(1));

        clearInvocations(mMockRadioConfig);
        clearInvocations(mActivePhoneSwitchHandler);

        // Notify phoneSwitcher about default data sub and default network request.
        // It shouldn't change anything.
        addInternetNetworkRequest(null, 50);
        addMmsNetworkRequest(2);
        waitABit();
        verify(mMockRadioConfig, never()).setPreferredDataModem(anyInt(), any());
        verify(mActivePhoneSwitchHandler, never()).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(0));
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(1));
        assertTrue(mPhoneSwitcher.shouldApplyUnspecifiedRequests(0));
        assertFalse(mPhoneSwitcher.shouldApplyUnspecifiedRequests(1));

        // Set sub 2 as preferred sub should make phone 1 preferredDataModem
        mPhoneSwitcher.setPreferredData(2);
        waitABit();
        verify(mMockRadioConfig).setPreferredDataModem(eq(1), any());
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(0));
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(1));
        assertFalse(mPhoneSwitcher.shouldApplyUnspecifiedRequests(0));
        assertTrue(mPhoneSwitcher.shouldApplyUnspecifiedRequests(1));

        clearInvocations(mMockRadioConfig);
        clearInvocations(mActivePhoneSwitchHandler);

        // Unset preferred sub should make phone0 preferredDataModem again.
        mPhoneSwitcher.setPreferredData(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
        waitABit();
        verify(mMockRadioConfig).setPreferredDataModem(eq(0), any());
        verify(mActivePhoneSwitchHandler, times(2)).sendMessageAtTime(any(), anyLong());
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(0));
        assertTrue(mPhoneSwitcher.shouldApplySpecifiedRequests(1));
        assertTrue(mPhoneSwitcher.shouldApplyUnspecifiedRequests(0));
        assertFalse(mPhoneSwitcher.shouldApplyUnspecifiedRequests(1));

        // SetDataAllowed should never be triggered.
        verify(mCommandsInterface0, never()).setDataAllowed(anyBoolean(), any());
        verify(mCommandsInterface1, never()).setDataAllowed(anyBoolean(), any());

        mHandlerThread.quit();

    }

    /* Private utility methods start here */

    private void sendDefaultDataSubChanged() {
        final Intent intent = new Intent(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);
        mContext.sendBroadcast(intent);
    }

    private void initialize(int numPhones, int maxActivePhones) throws Exception {
        mContextFixture.putStringArrayResource(com.android.internal.R.array.networkAttributes,
                sNetworkAttributes);

        setNumPhones(numPhones);

        initializeSubControllerMock();
        initializeCommandInterfacesMock(numPhones);
        initializeTelRegistryMock();
        initializeConnManagerMock();

        mHandlerThread = new HandlerThread("PhoneSwitcherTestThread") {
            @Override
            public void onLooperPrepared() {
                mPhoneSwitcher = new PhoneSwitcher(maxActivePhones, numPhones,
                        mContext, mSubscriptionController, this.getLooper(),
                        mTelRegistryMock, mCommandsInterfaces, mPhones);
            }
        };

        mHandlerThread.start();
        waitABit();

        verify(mTelRegistryMock).addOnSubscriptionsChangedListener(
                eq(mContext.getOpPackageName()), any());
    }

    /**
     * Certain variables needs initialized depending on number of phones.
     */
    private void setNumPhones(int numPhones) {
        mDataAllowed = new boolean[numPhones];
        mSlotIndexToSubId = new int[numPhones][];
        for (int i = 0; i < numPhones; i++) {
            mSlotIndexToSubId[i] = new int[1];
            mSlotIndexToSubId[i][0] = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        if (numPhones == 1) {
            mCommandsInterfaces = new CommandsInterface[] {mCommandsInterface0};
            mPhones = new Phone[] {mPhone};
        } else if (numPhones == 2) {
            mCommandsInterfaces =
                    new CommandsInterface[] {mCommandsInterface0, mCommandsInterface1};
            mPhones = new Phone[] {mPhone, mPhone2};
        }
    }

    private void initializeCommandInterfacesMock(int numPhones) {
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

        if (numPhones == 2) {
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
            IOnSubscriptionsChangedListener subChangedListener =
                    (IOnSubscriptionsChangedListener) invocation.getArguments()[1];
            mSubChangedListener = subChangedListener;
            mSubChangedListener.onSubscriptionsChanged();
            return null;
        }).when(mTelRegistryMock).addOnSubscriptionsChangedListener(any(), any());
    }

    /**
     * Capture mNetworkFactoryMessenger so that testing can request or release
     * network requests on PhoneSwitcher.
     */
    private void initializeConnManagerMock() {
        mConnectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        doAnswer(invocation -> {
            mNetworkFactoryMessenger = invocation.getArgument(0);
            return null;
        }).when(mConnectivityManager).registerNetworkFactory(any(), any());
    }

    /**
     * Capture mNetworkFactoryMessenger so that testing can request or release
     * network requests on PhoneSwitcher.
     */
    private void initializeSubControllerMock() {
        doReturn(mDefaultDataSub).when(mSubscriptionController).getDefaultDataSubId();
        doAnswer(invocation -> {
            int phoneId = (int) invocation.getArguments()[0];
            return mSlotIndexToSubId[phoneId][0];
        }).when(mSubscriptionController).getSubIdUsingPhoneId(anyInt());

        doAnswer(invocation -> {
            int subId = (int) invocation.getArguments()[0];

            if (!SubscriptionManager.isUsableSubIdValue(subId)) return false;

            for (int i = 0; i < mSlotIndexToSubId.length; i++) {
                if (mSlotIndexToSubId[i][0] == subId) return true;
            }
            return false;
        }).when(mSubscriptionController).isActiveSubId(anyInt());
    }

    private void setDefaultDataSubId(int defaultDataSub) {
        mDefaultDataSub = defaultDataSub;
        doReturn(mDefaultDataSub).when(mSubscriptionController).getDefaultDataSubId();
        sendDefaultDataSubChanged();
    }

    private void setSlotIndexToSubId(int slotId, int subId) {
        mSlotIndexToSubId[slotId][0] = subId;
    }

    /**
     * Create an internet PDN network request and send it to PhoneSwitcher.
     */
    private NetworkRequest addInternetNetworkRequest(Integer subId, int score) throws Exception {
        NetworkCapabilities netCap = (new NetworkCapabilities())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        if (subId != null) {
            netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        }
        NetworkRequest networkRequest = new NetworkRequest(netCap, ConnectivityManager.TYPE_NONE,
                0, NetworkRequest.Type.REQUEST);

        Message message = Message.obtain();
        message.what = android.net.NetworkFactory.CMD_REQUEST_NETWORK;
        message.arg1 = score;
        message.obj = networkRequest;
        mNetworkFactoryMessenger.send(message);

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
        netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        if (subId != null) {
            netCap.setNetworkSpecifier(new StringNetworkSpecifier(Integer.toString(subId)));
        }
        NetworkRequest networkRequest = new NetworkRequest(netCap, ConnectivityManager.TYPE_NONE,
                1, NetworkRequest.Type.REQUEST);

        Message message = Message.obtain();
        message.what = android.net.NetworkFactory.CMD_REQUEST_NETWORK;
        message.arg1 = 50; // Score
        message.obj = networkRequest;
        mNetworkFactoryMessenger.send(message);

        return networkRequest;
    }

    /**
     * Tell PhoneSwitcher to release a network request.
     */
    private void releaseNetworkRequest(NetworkRequest networkRequest) throws Exception {
        Message message = Message.obtain();
        message.what = android.net.NetworkFactory.CMD_CANCEL_REQUEST;
        message.obj = networkRequest;
        mNetworkFactoryMessenger.send(message);
    }

    private void waitABit() {
        try {
            Thread.sleep(250);
        } catch (Exception e) {
        }
    }
}
