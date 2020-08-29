/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.telephony.PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE;
import static android.telephony.PhoneStateListener.LISTEN_PHONE_CAPABILITY_CHANGE;
import static android.telephony.PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED;
import static android.telephony.PhoneStateListener.LISTEN_SRVCC_STATE_CHANGED;
import static android.telephony.SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED;
import static android.telephony.TelephonyManager.RADIO_POWER_OFF;
import static android.telephony.TelephonyManager.RADIO_POWER_ON;
import static android.telephony.TelephonyManager.RADIO_POWER_UNAVAILABLE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.net.LinkProperties;
import android.os.ServiceManager;
import android.telephony.Annotation;
import android.telephony.PhoneCapability;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseDataConnectionState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.server.TelephonyRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TelephonyRegistryTest extends TelephonyTest {
    @Mock
    private SubscriptionInfo mMockSubInfo;
    private PhoneStateListenerWrapper mPhoneStateListener;
    private TelephonyRegistry mTelephonyRegistry;
    private PhoneCapability mPhoneCapability;
    private int mActiveSubId;
    private TelephonyDisplayInfo mTelephonyDisplayInfo;
    private int mSrvccState = -1;
    private int mRadioPowerState = RADIO_POWER_UNAVAILABLE;
    // All events contribute to TelephonyRegistry.ENFORCE_PHONE_STATE_PERMISSION_MASK
    private static final Map<Integer, String> READ_PHONE_STATE_EVENTS = Map.of(
            PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR,
            "PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR",
            PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR,
            "PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR",
            PhoneStateListener.LISTEN_EMERGENCY_NUMBER_LIST,
            "PhoneStateListener.LISTEN_EMERGENCY_NUMBER_LIST");

    // All events contribute to TelephonyRegistry.PRECISE_PHONE_STATE_PERMISSION_MASK
    private static final Map<Integer, String> READ_PRECISE_PHONE_STATE_EVENTS = Map.of(
            PhoneStateListener.LISTEN_PRECISE_CALL_STATE,
            "PhoneStateListener.LISTEN_PRECISE_CALL_STATE",
            PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE,
            "PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE",
            PhoneStateListener.LISTEN_CALL_DISCONNECT_CAUSES,
            "PhoneStateListener.LISTEN_CALL_DISCONNECT_CAUSES",
            PhoneStateListener.LISTEN_CALL_ATTRIBUTES_CHANGED,
            "PhoneStateListener.LISTEN_CALL_ATTRIBUTES_CHANGED",
            PhoneStateListener.LISTEN_IMS_CALL_DISCONNECT_CAUSES,
            "PhoneStateListener.LISTEN_IMS_CALL_DISCONNECT_CAUSES",
            PhoneStateListener.LISTEN_REGISTRATION_FAILURE,
            "PhoneStateListener.LISTEN_REGISTRATION_FAILURE",
            PhoneStateListener.LISTEN_BARRING_INFO,
            "PhoneStateListener.LISTEN_BARRING_INFO");

    // All events contribute to TelephonyRegistry.PREVILEGED_PHONE_STATE_PERMISSION_MASK
    // TODO: b/148021947 will create the permission group with PREVILIGED_STATE_PERMISSION_MASK
    private static final Map<Integer, String> READ_PREVILIGED_PHONE_STATE_EVENTS = Map.of(
            PhoneStateListener.LISTEN_SRVCC_STATE_CHANGED,
            "PhoneStateListener.LISTEN_SRVCC_STATE_CHANGED",
            PhoneStateListener.LISTEN_OEM_HOOK_RAW_EVENT,
            "PhoneStateListener.LISTEN_OEM_HOOK_RAW_EVENT",
            PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED,
            "PhoneStateListener.LISTEN_RADIO_POWER_STATE_CHANGED",
            PhoneStateListener.LISTEN_VOICE_ACTIVATION_STATE,
            "PhoneStateListener.LISTEN_VOICE_ACTIVATION_STATE");

    // All events contribute to TelephonyRegistry.READ_ACTIVE_EMERGENCY_SESSION_PERMISSION_MASK
    private static final Map<Integer, String> READ_ACTIVE_EMERGENCY_SESSION_EVENTS = Map.of(
            PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_CALL,
            "PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_CALL",
            PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_SMS,
            "PhoneStateListener.LISTEN_OUTGOING_EMERGENCY_SMS");

    public class PhoneStateListenerWrapper extends PhoneStateListener {
        // This class isn't mockable to get invocation counts because the IBinder is null and
        // crashes the TelephonyRegistry. Make a cheesy verify(times()) alternative.
        public AtomicInteger invocationCount = new AtomicInteger(0);

        @Override
        public void onSrvccStateChanged(int srvccState) {
            invocationCount.incrementAndGet();
            mSrvccState = srvccState;
        }

        @Override
        public void onPhoneCapabilityChanged(PhoneCapability capability) {
            invocationCount.incrementAndGet();
            mPhoneCapability = capability;
        }
        @Override
        public void onActiveDataSubscriptionIdChanged(int activeSubId) {
            invocationCount.incrementAndGet();
            mActiveSubId = activeSubId;
        }
        @Override
        public void onRadioPowerStateChanged(@Annotation.RadioPowerState int state) {
            invocationCount.incrementAndGet();
            mRadioPowerState = state;
        }
        @Override
        public void onPreciseDataConnectionStateChanged(PreciseDataConnectionState preciseState) {
            invocationCount.incrementAndGet();
        }
        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            mTelephonyDisplayInfo = displayInfo;
        }
    }

    private void addTelephonyRegistryService() {
        mServiceManagerMockedServices.put("telephony.registry", mTelephonyRegistry.asBinder());
        mTelephonyRegistry.systemRunning();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("TelephonyRegistryTest");
        TelephonyRegistry.ConfigurationProvider mockConfigurationProvider =
                mock(TelephonyRegistry.ConfigurationProvider.class);
        when(mockConfigurationProvider.getRegistrationLimit()).thenReturn(-1);
        when(mockConfigurationProvider.isRegistrationLimitEnabledInPlatformCompat(anyInt()))
                .thenReturn(false);
        mTelephonyRegistry = new TelephonyRegistry(mContext, mockConfigurationProvider);
        addTelephonyRegistryService();
        mPhoneStateListener = new PhoneStateListenerWrapper();
        processAllMessages();
        assertEquals(mTelephonyRegistry.asBinder(),
                ServiceManager.getService("telephony.registry"));
    }

    @After
    public void tearDown() throws Exception {
        mTelephonyRegistry = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testPhoneCapabilityChanged() {
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        PhoneCapability phoneCapability = new PhoneCapability(1, 2, 3, null, false);
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        mTelephonyRegistry.listenWithFeature(mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                LISTEN_PHONE_CAPABILITY_CHANGE, true);
        processAllMessages();
        assertEquals(phoneCapability, mPhoneCapability);

        // notifyPhoneCapabilityChanged with a new capability. Callback should be triggered.
        phoneCapability = new PhoneCapability(3, 2, 2, null, false);
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        processAllMessages();
        assertEquals(phoneCapability, mPhoneCapability);
    }


    @Test @SmallTest
    public void testActiveDataSubChanged() {
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        int[] activeSubs = {0, 1, 2};
        when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(activeSubs);
        int activeSubId = 0;
        mTelephonyRegistry.notifyActiveDataSubIdChanged(activeSubId);
        mTelephonyRegistry.listenWithFeature(mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE, true);
        processAllMessages();
        assertEquals(activeSubId, mActiveSubId);

        // notifyPhoneCapabilityChanged with a new capability. Callback should be triggered.
        mActiveSubId = 1;
        mTelephonyRegistry.notifyActiveDataSubIdChanged(activeSubId);
        processAllMessages();
        assertEquals(activeSubId, mActiveSubId);
    }

    /**
     * Test that we first receive a callback when listen(...) is called that contains the latest
     * notify(...) response and then that the callback is called correctly when notify(...) is
     * called.
     */
    @Test
    @SmallTest
    public void testSrvccStateChanged() throws Exception {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        int srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        // Should receive callback when listen is called that contains the latest notify result.
        mTelephonyRegistry.listenForSubscriber(1 /*subId*/, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                LISTEN_SRVCC_STATE_CHANGED, true);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);

        // trigger callback
        srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
    }

    /**
     * Test that a SecurityException is thrown when we try to listen to a SRVCC state change without
     * READ_PRIVILEGED_PHONE_STATE.
     */
    @Test
    @SmallTest
    public void testSrvccStateChangedNoPermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        int srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
        mTelephonyRegistry.notifySrvccStateChanged(0 /*subId*/, srvccState);
        try {
            mTelephonyRegistry.listenForSubscriber(0 /*subId*/, mContext.getOpPackageName(),
                    mContext.getAttributionTag(), mPhoneStateListener.callback,
                    LISTEN_SRVCC_STATE_CHANGED, true);
            fail();
        } catch (SecurityException e) {
            // pass test!
        }
    }

    /**
     * Test multi sim config change.
     */
    @Test
    public void testMultiSimConfigChange() {
        mTelephonyRegistry.listenForSubscriber(1, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                LISTEN_RADIO_POWER_STATE_CHANGED, true);
        processAllMessages();
        assertEquals(RADIO_POWER_UNAVAILABLE, mRadioPowerState);

        // Notify RADIO_POWER_ON on invalid phoneId. Shouldn't go through.
        mTelephonyRegistry.notifyRadioPowerStateChanged(1, 1, RADIO_POWER_ON);
        processAllMessages();
        assertEquals(RADIO_POWER_UNAVAILABLE, mRadioPowerState);

        // Switch to DSDS and re-send RADIO_POWER_ON on phone 1. This time it should be notified.
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        mContext.sendBroadcast(new Intent(ACTION_MULTI_SIM_CONFIG_CHANGED));
        mTelephonyRegistry.notifyRadioPowerStateChanged(1, 1, RADIO_POWER_ON);
        processAllMessages();
        assertEquals(RADIO_POWER_ON, mRadioPowerState);

        // Switch back to single SIM mode and re-send on phone 0. This time it should be notified.
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        mContext.sendBroadcast(new Intent(ACTION_MULTI_SIM_CONFIG_CHANGED));
        mTelephonyRegistry.notifyRadioPowerStateChanged(0, 1, RADIO_POWER_OFF);
        processAllMessages();
        assertEquals(RADIO_POWER_OFF, mRadioPowerState);
    }

    /**
     * Test multi sim config change.
     */
    @Test
    public void testPreciseDataConnectionStateChanged() {
        final int subId = 1;
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        // Initialize the PSL with a PreciseDataConnection
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId, ApnSetting.TYPE_DEFAULT,
                new PreciseDataConnectionState(
                    0, 0, 0, "default", new LinkProperties(), 0, null));
        mTelephonyRegistry.listenForSubscriber(subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE, true);
        processAllMessages();
        // Verify that the PDCS is reported for the only APN
        assertEquals(mPhoneStateListener.invocationCount.get(), 1);

        // Add IMS APN and verify that the listener is invoked for the IMS APN
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId, ApnSetting.TYPE_IMS,
                new PreciseDataConnectionState(
                    0, 0, 0, "ims", new LinkProperties(), 0, null));
        processAllMessages();

        assertEquals(mPhoneStateListener.invocationCount.get(), 2);

        // Unregister the listener
        mTelephonyRegistry.listenForSubscriber(subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                PhoneStateListener.LISTEN_NONE, true);
        processAllMessages();

        // Re-register the listener and ensure that both APN types are reported
        mTelephonyRegistry.listenForSubscriber(subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE, true);
        processAllMessages();
        assertEquals(mPhoneStateListener.invocationCount.get(), 4);

        // Send a duplicate event to the TelephonyRegistry and verify that the listener isn't
        // invoked.
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId, ApnSetting.TYPE_IMS,
                new PreciseDataConnectionState(
                    0, 0, 0, "ims", new LinkProperties(), 0, null));
        processAllMessages();
        assertEquals(mPhoneStateListener.invocationCount.get(), 4);
    }

    /**
     * Test listen to events that require READ_PHONE_STATE permission.
     */
    @Test
    public void testReadPhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        for (Map.Entry<Integer, String> entry : READ_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionThrown(entry.getKey(), entry.getValue());
        }

        // Grant permssion
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE);
        for (Map.Entry<Integer, String> entry : READ_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionNotThrown(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Test listen to events that require READ_PRECISE_PHONE_STATE permission.
     */
    // FIXME(b/159082270) - Simply not granting location permission doesn't fix the test because
    // Location is soft-denied to apps that aren't in the foreground, and soft-denial currently
    // short-circuits the test.
    @Ignore("Skip due to b/159082270")
    @Test
    public void testReadPrecisePhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        for (Map.Entry<Integer, String> entry : READ_PRECISE_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionThrown(entry.getKey(), entry.getValue());
        }

        // Grant permssion
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
        for (Map.Entry<Integer, String> entry : READ_PRECISE_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionNotThrown(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Test listen to events that require READ_PRIVILEGED_PHONE_STATE permission.
     */
    @Test
    public void testReadPrivilegedPhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        for (Map.Entry<Integer, String> entry : READ_PREVILIGED_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionThrown(entry.getKey(), entry.getValue());
        }

        // Grant permssion
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        for (Map.Entry<Integer, String> entry : READ_PREVILIGED_PHONE_STATE_EVENTS.entrySet()) {
            assertSecurityExceptionNotThrown(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Test listen to events that require READ_ACTIVE_EMERGENCY_SESSION permission.
     */
    @Test
    public void testReadActiveEmergencySessionPermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        for (Map.Entry<Integer, String> entry : READ_ACTIVE_EMERGENCY_SESSION_EVENTS.entrySet()) {
            assertSecurityExceptionThrown(entry.getKey(), entry.getValue());
        }

        // Grant permssion
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_ACTIVE_EMERGENCY_SESSION);
        for (Map.Entry<Integer, String> entry : READ_ACTIVE_EMERGENCY_SESSION_EVENTS.entrySet()) {
            assertSecurityExceptionNotThrown(entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void testNotifyDisplayInfoChanged() {
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 12)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();
        mTelephonyRegistry.listenForSubscriber(2, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mPhoneStateListener.callback,
                PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED, false);

        // Notify with invalid subId on default phone. Should NOT trigger callback.
        TelephonyDisplayInfo displayInfo = new TelephonyDisplayInfo(0, 0);
        mTelephonyRegistry.notifyDisplayInfoChanged(0, INVALID_SUBSCRIPTION_ID, displayInfo);
        processAllMessages();
        assertEquals(null, mTelephonyDisplayInfo);

        // Notify with the matching subId on default phone. Should trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, 2, displayInfo);
        processAllMessages();
        assertEquals(displayInfo, mTelephonyDisplayInfo);
    }

    private void assertSecurityExceptionThrown(int event, String eventDesc) {
        try {
            mTelephonyRegistry.listen(mContext.getOpPackageName(),
                    mPhoneStateListener.callback, event, true);
            fail("SecurityException should throw when listen " + eventDesc + " without permission");
        } catch (SecurityException expected) {
        }
    }

    private void assertSecurityExceptionNotThrown(int event, String eventDesc) {
        try {
            mTelephonyRegistry.listen(mContext.getOpPackageName(),
                    mPhoneStateListener.callback, event, true);
        } catch (SecurityException unexpected) {
            fail("SecurityException thrown when listen " + eventDesc + " with permission");
        }
    }
}
