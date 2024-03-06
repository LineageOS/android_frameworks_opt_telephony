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

import static android.telephony.PhysicalChannelConfig.PHYSICAL_CELL_ID_UNKNOWN;
import static android.telephony.ServiceState.FREQUENCY_RANGE_LOW;
import static android.telephony.SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED;
import static android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID;
import static android.telephony.TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED;
import static android.telephony.TelephonyManager.RADIO_POWER_OFF;
import static android.telephony.TelephonyManager.RADIO_POWER_ON;
import static android.telephony.TelephonyManager.RADIO_POWER_UNAVAILABLE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.net.LinkProperties;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.telephony.AccessNetworkConstants;
import android.telephony.Annotation;
import android.telephony.BarringInfo;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellLocation;
import android.telephony.LinkCapacityEstimate;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.PhoneCapability;
import android.telephony.PhysicalChannelConfig;
import android.telephony.PreciseDataConnectionState;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.android.server.TelephonyRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TelephonyRegistryTest extends TelephonyTest {
    // Mocked classes
    private SubscriptionInfo mMockSubInfo;
    private TelephonyRegistry.ConfigurationProvider mMockConfigurationProvider;

    private TelephonyCallbackWrapper mTelephonyCallback;
    private List<LinkCapacityEstimate> mLinkCapacityEstimateList;
    private TelephonyRegistry mTelephonyRegistry;
    private PhoneCapability mPhoneCapability;
    private int mActiveSubId;
    private TelephonyDisplayInfo mTelephonyDisplayInfo;
    private int mSrvccState = -1;
    private ServiceState mServiceState = null;
    private int mRadioPowerState = RADIO_POWER_UNAVAILABLE;
    private int mDataConnectionState = TelephonyManager.DATA_UNKNOWN;
    private int mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
    private List<PhysicalChannelConfig> mPhysicalChannelConfigs;
    private CellLocation mCellLocation;
    private List<CellInfo> mCellInfo;
    private BarringInfo mBarringInfo = null;
    private CellIdentity mCellIdentityForRegiFail;
    private int mRegistrationFailReason;

    // All events contribute to TelephonyRegistry#isPhoneStatePermissionRequired
    private static final Set<Integer> READ_PHONE_STATE_EVENTS;
    static {
        READ_PHONE_STATE_EVENTS = new HashSet<>();
        READ_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_CALL_FORWARDING_INDICATOR_CHANGED);
        READ_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_MESSAGE_WAITING_INDICATOR_CHANGED);
        READ_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_EMERGENCY_NUMBER_LIST_CHANGED);
    }

    // All events contribute to TelephonyRegistry#isPrecisePhoneStatePermissionRequired
    private static final Set<Integer> READ_PRECISE_PHONE_STATE_EVENTS;
    static {
        READ_PRECISE_PHONE_STATE_EVENTS = new HashSet<>();
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_PRECISE_CALL_STATE_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_CALL_DISCONNECT_CAUSE_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_CALL_ATTRIBUTES_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_IMS_CALL_DISCONNECT_CAUSE_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_REGISTRATION_FAILURE);
        READ_PRECISE_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_BARRING_INFO_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED);
        READ_PRECISE_PHONE_STATE_EVENTS.add(TelephonyCallback.EVENT_DATA_ENABLED_CHANGED);
    }

    // All events contribute to TelephonyRegistry#isPrivilegedPhoneStatePermissionRequired
    // TODO: b/148021947 will create the permission group with PREVILIGED_STATE_PERMISSION_MASK
    private static final Set<Integer> READ_PRIVILEGED_PHONE_STATE_EVENTS;
    static {
        READ_PRIVILEGED_PHONE_STATE_EVENTS = new HashSet<>();
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add( TelephonyCallback.EVENT_SRVCC_STATE_CHANGED);
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add( TelephonyCallback.EVENT_OEM_HOOK_RAW);
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add( TelephonyCallback.EVENT_RADIO_POWER_STATE_CHANGED);
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_VOICE_ACTIVATION_STATE_CHANGED);
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_ALLOWED_NETWORK_TYPE_LIST_CHANGED);
        READ_PRIVILEGED_PHONE_STATE_EVENTS.add(
                TelephonyCallback.EVENT_EMERGENCY_CALLBACK_MODE_CHANGED);
    }

    // All events contribute to TelephonyRegistry#isActiveEmergencySessionPermissionRequired
    private static final Set<Integer> READ_ACTIVE_EMERGENCY_SESSION_EVENTS;
    static {
        READ_ACTIVE_EMERGENCY_SESSION_EVENTS = new HashSet<>();
        READ_ACTIVE_EMERGENCY_SESSION_EVENTS.add(
                TelephonyCallback.EVENT_OUTGOING_EMERGENCY_CALL);
        READ_ACTIVE_EMERGENCY_SESSION_EVENTS.add(
                TelephonyCallback.EVENT_OUTGOING_EMERGENCY_SMS);
    }

    public class TelephonyCallbackWrapper extends TelephonyCallback implements
            TelephonyCallback.SrvccStateListener,
            TelephonyCallback.PhoneCapabilityListener,
            TelephonyCallback.ActiveDataSubscriptionIdListener,
            TelephonyCallback.RadioPowerStateListener,
            TelephonyCallback.PreciseDataConnectionStateListener,
            TelephonyCallback.DataConnectionStateListener,
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.LinkCapacityEstimateChangedListener,
            TelephonyCallback.PhysicalChannelConfigListener,
            TelephonyCallback.CellLocationListener,
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.CellInfoListener,
            TelephonyCallback.BarringInfoListener,
            TelephonyCallback.RegistrationFailedListener,
            TelephonyCallback.DataActivityListener {
        // This class isn't mockable to get invocation counts because the IBinder is null and
        // crashes the TelephonyRegistry. Make a cheesy verify(times()) alternative.
        public AtomicInteger invocationCount = new AtomicInteger(0);

        @Override
        public void onSrvccStateChanged(int srvccState) {
            invocationCount.incrementAndGet();
            mSrvccState = srvccState;
        }

        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            invocationCount.incrementAndGet();
            mServiceState = serviceState;
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
        public void onDataConnectionStateChanged(int state, int networkType) {
            invocationCount.incrementAndGet();
            mDataConnectionState = state;
            mNetworkType = networkType;
        }
        @Override
        public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfo) {
            mTelephonyDisplayInfo = displayInfo;
        }

        @Override
        public void onLinkCapacityEstimateChanged(
                List<LinkCapacityEstimate> linkCapacityEstimateList) {
            mLinkCapacityEstimateList = linkCapacityEstimateList;
        }

        @Override
        public void onCellLocationChanged(CellLocation location) {
            mCellLocation = location;
        }

        @Override
        public void onPhysicalChannelConfigChanged(@NonNull List<PhysicalChannelConfig> configs) {
            mPhysicalChannelConfigs = configs;
        }

        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            invocationCount.incrementAndGet();
            mCellInfo = cellInfo;
        }

        @Override
        public void onBarringInfoChanged(BarringInfo barringInfo) {
            invocationCount.incrementAndGet();
            mBarringInfo = barringInfo;
        }

        public void onRegistrationFailed(@android.annotation.NonNull CellIdentity cellIdentity,
                @android.annotation.NonNull String chosenPlmn,
                @NetworkRegistrationInfo.Domain int domain,
                int causeCode, int additionalCauseCode) {
            invocationCount.incrementAndGet();
            mCellIdentityForRegiFail = cellIdentity;
            mRegistrationFailReason = causeCode;
        }

        public void onDataActivity(@Annotation.DataActivityType int direction) {
            invocationCount.incrementAndGet();
            mDataActivity = direction;
        }
    }

    private void addTelephonyRegistryService() {
        mServiceManagerMockedServices.put("telephony.registry", mTelephonyRegistry.asBinder());
        mTelephonyRegistry.systemRunning();
    }

    private final Executor mSimpleExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockSubInfo = mock(SubscriptionInfo.class);
        mMockConfigurationProvider = mock(TelephonyRegistry.ConfigurationProvider.class);
        when(mMockConfigurationProvider.getRegistrationLimit()).thenReturn(-1);
        when(mMockConfigurationProvider.isRegistrationLimitEnabledInPlatformCompat(anyInt()))
                .thenReturn(false);
        when(mMockConfigurationProvider.isCallStateReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(false);
        when(mMockConfigurationProvider.isActiveDataSubIdReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(false);
        when(mMockConfigurationProvider.isCellInfoReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(false);
        when(mMockConfigurationProvider.isDisplayInfoReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(false);
        when(mMockConfigurationProvider.isDisplayInfoNrAdvancedSupported(
                anyString(), any())).thenReturn(false);
        mTelephonyRegistry = new TelephonyRegistry(mContext, mMockConfigurationProvider);
        addTelephonyRegistryService();
        mTelephonyCallback = new TelephonyCallbackWrapper();
        mTelephonyCallback.init(mSimpleExecutor);
        mContextFixture.putStringArrayResource(
                com.android.internal.R.array.config_serviceStateLocationAllowedPackages,
                new String[0]);
        processAllMessages();
        assertEquals(mTelephonyRegistry.asBinder(),
                ServiceManager.getService("telephony.registry"));
    }

    @After
    public void tearDown() throws Exception {
        mTelephonyRegistry = null;
        mTelephonyCallback = null;
        if (mLinkCapacityEstimateList != null) {
            mLinkCapacityEstimateList.clear();
            mLinkCapacityEstimateList = null;
        }
        mTelephonyRegistry = null;
        mPhoneCapability = null;
        mTelephonyDisplayInfo = null;
        mServiceState = null;
        if (mPhysicalChannelConfigs != null) {
            mPhysicalChannelConfigs.clear();
            mPhysicalChannelConfigs = null;
        }
        mCellLocation = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testPhoneCapabilityChanged() {
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        PhoneCapability phoneCapability = new PhoneCapability(1, 2, null, false, new int[0]);
        int[] events = {TelephonyCallback.EVENT_PHONE_CAPABILITY_CHANGED};
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        mTelephonyRegistry.listenWithEventList(false, false, 0, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(phoneCapability, mPhoneCapability);

        // notifyPhoneCapabilityChanged with a new capability. Callback should be triggered.
        phoneCapability = new PhoneCapability(3, 2, null, false, new int[0]);
        mTelephonyRegistry.notifyPhoneCapabilityChanged(phoneCapability);
        processAllMessages();
        assertEquals(phoneCapability, mPhoneCapability);
    }

    @Test @SmallTest
    public void testActiveDataSubChanged() {
        // mTelephonyRegistry.listen with notifyNow = true should trigger callback immediately.
        int[] activeSubs = {0, 1, 2};
        int[] events = {TelephonyCallback.EVENT_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGED};
        when(mSubscriptionManager.getActiveSubscriptionIdList()).thenReturn(activeSubs);
        int activeSubId = 0;
        mTelephonyRegistry.notifyActiveDataSubIdChanged(activeSubId);
        mTelephonyRegistry.listenWithEventList(false, false, activeSubId,
                mContext.getOpPackageName(), mContext.getAttributionTag(),
                mTelephonyCallback.callback, events, true);
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
        int[] events = {TelephonyCallback.EVENT_SRVCC_STATE_CHANGED};
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        // Should receive callback when listen is called that contains the latest notify result.
        mTelephonyRegistry.listenWithEventList(false, false, 1 /*subId*/,
                mContext.getOpPackageName(), mContext.getAttributionTag(),
                mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);

        // trigger callback
        srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
    }

    /**
     * Test that we first receive a callback when listen(...) is called that contains the latest
     * notify(...) response and then that the callback is called correctly when notify(...) is
     * called.
     */
    @Test
    @SmallTest
    public void testSrvccStateChangedWithRenouncedLocationAccess() throws Exception {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        int srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
        int[] events = {TelephonyCallback.EVENT_SRVCC_STATE_CHANGED};
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        // Should receive callback when listen is called that contains the latest notify result.
        mTelephonyRegistry.listenWithEventList(true, true, 1 /*subId*/,
                mContext.getOpPackageName(), mContext.getAttributionTag(),
                mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
        assertServiceStateForLocationAccessSanitization(mServiceState);

        // trigger callback
        srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
    }

    /**
     * Test that we first receive a callback when listen(...) is called that contains the latest
     * notify(...) response and then that the callback is called correctly when notify(...) is
     * called.
     */
    @Test
    @SmallTest
    public void testSrvccStateChangedWithRenouncedFineLocationAccess() throws Exception {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        int srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
        int[] events = {TelephonyCallback.EVENT_SRVCC_STATE_CHANGED};
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        // Should receive callback when listen is called that contains the latest notify result.
        mTelephonyRegistry.listenWithEventList(false, true, 1 /*subId*/,
                mContext.getOpPackageName(), mContext.getAttributionTag(),
                mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
        assertServiceStateForFineAccessSanitization(mServiceState);

        // trigger callback
        srvccState = TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED;
        mTelephonyRegistry.notifySrvccStateChanged(1 /*subId*/, srvccState);
        processAllMessages();
        assertEquals(srvccState, mSrvccState);
    }

    private void assertServiceStateForFineAccessSanitization(ServiceState state) {
        if (state == null) return;

        if (state.getNetworkRegistrationInfoList() != null) {
            for (NetworkRegistrationInfo nrs : state.getNetworkRegistrationInfoList()) {
                assertEquals(nrs.getCellIdentity(), null);
            }
        }
    }

    private void assertServiceStateForLocationAccessSanitization(ServiceState state) {
        if (state == null) return;
        assertServiceStateForFineAccessSanitization(state);
        assertEquals(TextUtils.isEmpty(state.getOperatorAlphaLong()), true);
        assertEquals(TextUtils.isEmpty(state.getOperatorAlphaShort()), true);
        assertEquals(TextUtils.isEmpty(state.getOperatorNumeric()), true);
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
        int[] events = {TelephonyCallback.EVENT_SRVCC_STATE_CHANGED};
        mTelephonyRegistry.notifySrvccStateChanged(0 /*subId*/, srvccState);
        try {
            mTelephonyRegistry.listenWithEventList(false, false, 0 /*subId*/,
                    mContext.getOpPackageName(), mContext.getAttributionTag(),
                    mTelephonyCallback.callback, events, true);
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
        int[] events = {TelephonyCallback.EVENT_RADIO_POWER_STATE_CHANGED};
        mTelephonyRegistry.listenWithEventList(false, false, 1, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
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
     * Test {@link TelephonyCallback.DataConnectionStateListener}
     */
    @Test
    public void testDataConnectionStateChanged() {
        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_DATA_CONNECTION_STATE_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();

        assertEquals(TelephonyManager.DATA_UNKNOWN, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_DISCONNECTING, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_DISCONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);
    }

    /**
     * Test the scenario that multiple PDNs support default type APN scenario. Make sure it reports
     * aggregate data connection state.
     */
    @Test
    public void testDataConnectionStateChangedMultipleInternet() {
        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_DATA_CONNECTION_STATE_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();

        assertEquals(TelephonyManager.DATA_UNKNOWN, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_UNKNOWN, mNetworkType);

        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_CONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTING)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_DISCONNECTING, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_DISCONNECTING, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);

        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS)
                                .setApnName("default+mms")
                                .setEntryName("default+mms")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        assertEquals(TelephonyManager.DATA_DISCONNECTED, mDataConnectionState);
        assertEquals(TelephonyManager.NETWORK_TYPE_LTE, mNetworkType);
    }

    /**
     * Test multi sim config change.
     */
    @Test
    public void testPreciseDataConnectionStateChanged() {
        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        // Initialize the PSL with a PreciseDataConnection
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                                .setApnName("default")
                                .setEntryName("default")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        // Verify that the PDCS is reported for the only APN
        assertEquals(1, mTelephonyCallback.invocationCount.get());

        // Add IMS APN and verify that the listener is invoked for the IMS APN
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(2)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                .setApnName("ims")
                                .setEntryName("ims")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        processAllMessages();

        assertEquals(mTelephonyCallback.invocationCount.get(), 2);

        // Unregister the listener
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, new int[0], true);
        processAllMessages();

        // Re-register the listener and ensure that both APN types are reported
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(4, mTelephonyCallback.invocationCount.get());

        // Send a duplicate event to the TelephonyRegistry and verify that the listener isn't
        // invoked.
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*phoneId*/ 0, subId,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(2)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                .setApnName("ims")
                                .setEntryName("ims")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());
        processAllMessages();
        assertEquals(4, mTelephonyCallback.invocationCount.get());
    }

    @Test
    public void testPhysicalChannelConfigChanged() {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();

        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_PHYSICAL_CHANNEL_CONFIG_CHANGED};
        // Construct PhysicalChannelConfig with minimum fields set (The default value for
        // frequencyRange and band fields throw IAE)
        PhysicalChannelConfig config = new PhysicalChannelConfig.Builder()
                .setFrequencyRange(FREQUENCY_RANGE_LOW)
                .setBand(1)
                .setPhysicalCellId(2)
                .build();
        List<PhysicalChannelConfig> configs = new ArrayList<>(1);
        configs.add(config);

        mTelephonyRegistry.notifyPhysicalChannelConfigForSubscriber(0, subId, configs);
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();

        assertNotNull(mPhysicalChannelConfigs);
        assertEquals(PHYSICAL_CELL_ID_UNKNOWN, mPhysicalChannelConfigs.get(0).getPhysicalCellId());
    }

    @Test
    public void testBarringInfoChangedWithLocationFinePermission() throws Exception {
        checkBarringInfoWithLocationPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @Test
    public void testBarringInfoChangedLocationCoarsePermission() throws Exception {
        checkBarringInfoWithLocationPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Test
    public void testBarringInfoChangedWithoutLocationPermission() throws Exception {
        checkBarringInfoWithLocationPermission(null);
    }

    private void checkBarringInfoWithLocationPermission(String permission) throws Exception {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        doReturn(true).when(mLocationManager).isLocationEnabledForUser(any(UserHandle.class));

        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        mContextFixture.addCallingOrSelfPermission("");
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
        if (permission != null) {
            mContextFixture.addCallingOrSelfPermission(permission);
        }

        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_BARRING_INFO_CHANGED};
        SparseArray<BarringInfo.BarringServiceInfo> bsi = new SparseArray(1);
        bsi.set(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE,
                new BarringInfo.BarringServiceInfo(
                        BarringInfo.BarringServiceInfo.BARRING_TYPE_CONDITIONAL,
                        false /*isConditionallyBarred*/,
                        30 /*conditionalBarringFactor*/,
                        10 /*conditionalBarringTimeSeconds*/));
        BarringInfo info = new BarringInfo(
                new CellIdentityLte(777, 333, 12345, 222, 13579), bsi);
        // 1. Register listener which requires location access.
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(1, mTelephonyCallback.invocationCount.get());
        assertNotNull(mBarringInfo);

        // Updating the barring info causes Barring Info to be updated
        mTelephonyRegistry.notifyBarringInfoChanged(0, subId, info);
        processAllMessages();
        assertEquals(2, mTelephonyCallback.invocationCount.get());
        assertEquals(mBarringInfo
                        .getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE),
                info.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE));
        String log = mBarringInfo.toString();
        assertTrue(log.contains("777"));
        assertTrue(log.contains("333"));
        if (permission != null && permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            assertTrue(log.contains("12345"));
            assertTrue(log.contains("222"));
            assertTrue(log.contains("13579"));
        } else {
            assertFalse(log.contains("12345"));
            assertFalse(log.contains("222"));
            assertFalse(log.contains("13579"));
        }

        // Duplicate BarringInfo notifications do not trigger callback
        mTelephonyRegistry.notifyBarringInfoChanged(0, subId, info);
        processAllMessages();
        assertEquals(2, mTelephonyCallback.invocationCount.get());

        mTelephonyRegistry.listenWithEventList(true, true, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, new int[0], true);
        // 2. Register listener renounces location access.
        mTelephonyRegistry.listenWithEventList(true, true, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        // check receiving barring info without location info.
        assertEquals(3, mTelephonyCallback.invocationCount.get());
        assertNotNull(mBarringInfo);
        assertEquals(mBarringInfo
                        .getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE),
                info.getBarringServiceInfo(BarringInfo.BARRING_SERVICE_TYPE_MMTEL_VOICE));
        log = mBarringInfo.toString();
        assertTrue(log.contains("777"));
        assertTrue(log.contains("333"));
        assertFalse(log.contains("12345"));
        assertFalse(log.contains("222"));
        assertFalse(log.contains("13579"));
    }

    @Test
    public void testRegistrationFailedEventWithLocationFinePermission() throws Exception {
        checkRegistrationFailedEventWithLocationPermission(
                Manifest.permission.ACCESS_FINE_LOCATION);
    }
    @Test
    public void testRegistrationFailedEventWithLocationCoarsePermission() throws Exception {
        checkRegistrationFailedEventWithLocationPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Test
    public void testRegistrationFailedEventWithoutLocationPermission() throws Exception {
        checkRegistrationFailedEventWithLocationPermission(null);
    }

    private void checkRegistrationFailedEventWithLocationPermission(String permission)
            throws Exception {
        // Return a slotIndex / phoneId of 0 for all sub ids given.
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        doReturn(true).when(mLocationManager).isLocationEnabledForUser(any(UserHandle.class));

        mApplicationInfo.targetSdkVersion = Build.VERSION_CODES.TIRAMISU;
        doReturn(mApplicationInfo).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        mContextFixture.addCallingOrSelfPermission("");
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
        if (permission != null) {
            mContextFixture.addCallingOrSelfPermission(permission);
        }

        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_REGISTRATION_FAILURE};
        CellIdentity cellIdentity =
                new CellIdentityLte(777, 333, 12345, 227, 13579);

        // 1. Register listener which requires location access.
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        int invocationCount = mTelephonyCallback.invocationCount.get();
        // Updating the RegistrationFailed info to be updated
        mTelephonyRegistry.notifyRegistrationFailed(
                0, subId, cellIdentity, "88888", 1, 333, 22);
        processAllMessages();
        assertEquals(invocationCount + 1, mTelephonyCallback.invocationCount.get());
        if (permission != null && permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
            assertEquals(cellIdentity, mCellIdentityForRegiFail);
        } else {
            assertEquals(cellIdentity.sanitizeLocationInfo(), mCellIdentityForRegiFail);
        }
        assertEquals(333, mRegistrationFailReason);
        mTelephonyRegistry.listenWithEventList(true, true, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, new int[0], true);

        // 2. Register listener which renounces location access.
        mTelephonyRegistry.listenWithEventList(true, true, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        invocationCount = mTelephonyCallback.invocationCount.get();
        // Updating the RegistrationFailed info to be updated
        mTelephonyRegistry.notifyRegistrationFailed(
                0, subId, cellIdentity, "88888", 1, 555, 22);
        processAllMessages();
        assertEquals(invocationCount + 1, mTelephonyCallback.invocationCount.get());
        assertEquals(cellIdentity.sanitizeLocationInfo(), mCellIdentityForRegiFail);
        assertEquals(555, mRegistrationFailReason);
    }

    /**
     * Test listen to events that require READ_PHONE_STATE permission.
     */
    @Test
    public void testReadPhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        assertSecurityExceptionThrown(
                READ_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());

        // Grant permission
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE);
        assertSecurityExceptionNotThrown(
                READ_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());
    }

    /**
     * Test enforcement of READ_PHONE_STATE for call state related events.
     */
    @Test
    public void testCallStateChangedPermission() {
        int[] events = new int[] {TelephonyCallback.EVENT_CALL_STATE_CHANGED,
                TelephonyCallback.EVENT_LEGACY_CALL_STATE_CHANGED};
        // Disable change ID for READ_PHONE_STATE enforcement
        when(mMockConfigurationProvider.isCallStateReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(false);
        // Start without READ_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission("");
        assertSecurityExceptionNotThrown(events);
        // Grant permission
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE);
        assertSecurityExceptionNotThrown(events);
        //Enable READ_PHONE_STATE enforcement
        when(mMockConfigurationProvider.isCallStateReadPhoneStateEnforcedInPlatformCompat(
                anyString(), any())).thenReturn(true);
        assertSecurityExceptionNotThrown(events);
        // revoke READ_PHONE_STATE permission
        mContextFixture.removeCallingOrSelfPermission(android.Manifest.permission.READ_PHONE_STATE);
        assertSecurityExceptionThrown(events);

    }

    /**
     * Test listen to events that require READ_PRECISE_PHONE_STATE permission.
     */
    @Test
    public void testReadPrecisePhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        // Many of the events require LOCATION permission, but without READ_PHONE_STATE, they will
        // still throw exceptions. Since this test is testing READ_PRECISE_PHONE_STATE, all other
        // permissions should be granted up-front.
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_FINE_LOCATION);
        assertSecurityExceptionThrown(
                READ_PRECISE_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());

        // Grant permission
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE);
        assertSecurityExceptionNotThrown(
                READ_PRECISE_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());

    }

    /**
     * Test a bit-fiddling method in TelephonyRegistry
     */
    @Test
    public void testGetApnTypesStringFromBitmask() {
        {
            int mask = 0;
            assertEquals("", TelephonyRegistry.getApnTypesStringFromBitmask(mask));
        }

        {
            int mask = ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_MMS;
            assertEquals(String.join(
                    ",", ApnSetting.TYPE_DEFAULT_STRING, ApnSetting.TYPE_MMS_STRING),
                    TelephonyRegistry.getApnTypesStringFromBitmask(mask));
        }

        {
            int mask = 1 << 31;
            assertEquals("", TelephonyRegistry.getApnTypesStringFromBitmask(mask));
        }
    }

    /**
     * Test listen to events that require READ_PRIVILEGED_PHONE_STATE permission.
     */
    @Test
    public void testReadPrivilegedPhoneStatePermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        assertSecurityExceptionThrown(
                READ_PRIVILEGED_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());

        // Grant permission
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertSecurityExceptionNotThrown(
                READ_PRIVILEGED_PHONE_STATE_EVENTS.stream().mapToInt(i -> i).toArray());
    }

    /**
     * Test listen to events that require READ_ACTIVE_EMERGENCY_SESSION permission.
     */
    @Test
    public void testReadActiveEmergencySessionPermission() {
        // Clear all permission grants for test.
        mContextFixture.addCallingOrSelfPermission("");
        assertSecurityExceptionThrown(
                READ_ACTIVE_EMERGENCY_SESSION_EVENTS.stream().mapToInt(i -> i).toArray());

        // Grant permission
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_ACTIVE_EMERGENCY_SESSION);
        assertSecurityExceptionNotThrown(
                READ_ACTIVE_EMERGENCY_SESSION_EVENTS.stream().mapToInt(i -> i).toArray());
    }

    @Test
    public void testNotifyDisplayInfoChanged() {
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 12)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();
        int[] events = {TelephonyCallback.EVENT_DISPLAY_INFO_CHANGED};
        mTelephonyRegistry.listenWithEventList(false, false, 2, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, false);
        when(mMockConfigurationProvider.isDisplayInfoNrAdvancedSupported(
                anyString(), any())).thenReturn(true);
        TelephonyDisplayInfo displayInfo = new TelephonyDisplayInfo(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                false);

        // Notify with invalid subId on default phone. Should NOT trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, INVALID_SUBSCRIPTION_ID, displayInfo);
        processAllMessages();
        assertEquals(null, mTelephonyDisplayInfo);

        // Notify with the matching subId on default phone. Should trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, 2, displayInfo);
        processAllMessages();
        assertEquals(displayInfo, mTelephonyDisplayInfo);
    }

    @Test
    public void testDisplayInfoCompatibility() {
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 12)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();
        int[] events = {TelephonyCallback.EVENT_DISPLAY_INFO_CHANGED};
        mTelephonyRegistry.listenWithEventList(false, false, 2, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, false);
        when(mMockConfigurationProvider.isDisplayInfoNrAdvancedSupported(
                anyString(), any())).thenReturn(false);
        TelephonyDisplayInfo displayInfo = new TelephonyDisplayInfo(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                false);
        TelephonyDisplayInfo expectDisplayInfo = new TelephonyDisplayInfo(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                false);

        // Notify with invalid subId on default phone. Should NOT trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, INVALID_SUBSCRIPTION_ID, displayInfo);
        processAllMessages();
        assertEquals(null, mTelephonyDisplayInfo);

        // Notify with the matching subId on default phone. Should trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, 2, displayInfo);
        processAllMessages();
        assertEquals(expectDisplayInfo, mTelephonyDisplayInfo);
    }

    @Test
    public void testDisplayInfoCompatibility_moreCallingPackages() {
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 12)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();
        int[] events = {TelephonyCallback.EVENT_DISPLAY_INFO_CHANGED};
        TelephonyDisplayInfo displayInfo = new TelephonyDisplayInfo(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED,
                false);
        TelephonyDisplayInfo expectDisplayInfo = new TelephonyDisplayInfo(
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE,
                false);
        TelephonyCallback telephonyCallback2 = new TelephonyCallbackWrapper() {
            @Override
            public void onDisplayInfoChanged(TelephonyDisplayInfo displayInfoNotify) {
                assertEquals(displayInfo, displayInfoNotify);
            }
        };
        Executor mSimpleExecutor2 = new Executor() {
            @Override
            public void execute(Runnable r) {
                r.run();
            }
        };
        telephonyCallback2.init(mSimpleExecutor2);
        mTelephonyRegistry.listenWithEventList(false, false, 2, "pkg1",
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, false);
        mTelephonyRegistry.listenWithEventList(false, false, 2, "pkg2",
                mContext.getAttributionTag(), telephonyCallback2.callback, events, false);
        when(mMockConfigurationProvider.isDisplayInfoNrAdvancedSupported(
                eq("pkg1"), any())).thenReturn(false);
        when(mMockConfigurationProvider.isDisplayInfoNrAdvancedSupported(
                eq("pkg2"), any())).thenReturn(true);


        // Notify with invalid subId on default phone. Should NOT trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, INVALID_SUBSCRIPTION_ID, displayInfo);
        processAllMessages();
        assertEquals(null, mTelephonyDisplayInfo);

        // Notify with the matching subId on default phone. Should trigger callback.
        mTelephonyRegistry.notifyDisplayInfoChanged(0, 2, displayInfo);
        processAllMessages();
        assertEquals(expectDisplayInfo, mTelephonyDisplayInfo);
    }

    @Test
    public void testNotifyCellLocationForSubscriberByUserSwitched() throws RemoteException {
        final int phoneId = 0;
        final int subId = 1;

        // Return a slotIndex / phoneId of 0 for subId 1.
        doReturn(subId).when(mSubscriptionManagerService).getSubId(phoneId);
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(subId);
        doReturn(phoneId).when(mMockSubInfo).getSimSlotIndex();

        UserInfo userInfo = new UserInfo(UserHandle.myUserId(), "" /* name */, 0 /* flags */);
        doReturn(userInfo.id).when(mIActivityManager).getCurrentUserId();

        doReturn(true).when(mLocationManager).isLocationEnabledForUser(any(UserHandle.class));

        CellIdentity cellIdentity = new CellIdentityGsm(-1, -1, -1, -1, null, null, null, null,
                Collections.emptyList());
        mTelephonyRegistry.notifyCellLocationForSubscriber(subId, cellIdentity);
        processAllMessages();

        // Listen to EVENT_CELL_LOCATION_CHANGED for the current user Id.
        int[] events = {TelephonyCallback.EVENT_CELL_LOCATION_CHANGED};
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, false);

        // Broadcast ACTION_USER_SWITCHED for USER_SYSTEM. Callback should be triggered.
        mCellLocation = null;
        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_SWITCHED));

        processAllMessages();
        assertEquals(cellIdentity.asCellLocation(), mCellLocation);

        // Broadcast ACTION_USER_SWITCHED for the current user Id + 1. Callback shouldn't be
        // triggered.
        userInfo.id++;
        doReturn(userInfo.id).when(mIActivityManager).getCurrentUserId();
        mCellLocation = null;
        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_SWITCHED));

        processAllMessages();
        assertEquals(null, mCellLocation);
    }

    private void assertSecurityExceptionThrown(int[] event) {
        try {
            mTelephonyRegistry.listenWithEventList(
                    false, false, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                    mContext.getOpPackageName(), mContext.getAttributionTag(),
                    mTelephonyCallback.callback, event, true);
            fail("SecurityException should throw without permission");
        } catch (SecurityException expected) {
        }
    }

    private void assertSecurityExceptionNotThrown(int[] event) {
        try {
            mTelephonyRegistry.listenWithEventList(
                    false, false, SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                    mContext.getOpPackageName(), mContext.getAttributionTag(),
                    mTelephonyCallback.callback, event, true);
        } catch (SecurityException unexpected) {
            fail("SecurityException thrown with permission");
        }
    }

    @Test
    public void testNotifyLinkCapacityEstimateChanged() {
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 2)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();
        int[] events = {TelephonyCallback.EVENT_LINK_CAPACITY_ESTIMATE_CHANGED};
        mTelephonyRegistry.listenWithEventList(false, false, 2, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback,
                events, false);

        // Notify with invalid subId / phoneId on default phone. Should NOT trigger callback.
        List<LinkCapacityEstimate> lceList = new ArrayList<>();
        lceList.add(new LinkCapacityEstimate(LinkCapacityEstimate.LCE_TYPE_COMBINED, 4000,
                LinkCapacityEstimate.INVALID));
        mTelephonyRegistry.notifyLinkCapacityEstimateChanged(1, INVALID_SUBSCRIPTION_ID, lceList);
        processAllMessages();
        assertEquals(null, mLinkCapacityEstimateList);

        // Notify with invalid phoneId. Should NOT trigger callback.
        mTelephonyRegistry.notifyLinkCapacityEstimateChanged(2, 2, lceList);
        processAllMessages();
        assertEquals(null, mLinkCapacityEstimateList);

        // Notify with the matching subId on default phone. Should trigger callback.
        mTelephonyRegistry.notifyLinkCapacityEstimateChanged(0, 2, lceList);
        processAllMessages();
        assertEquals(lceList, mLinkCapacityEstimateList);
    }

    @Test
    public void testPreciseDataConnectionStateChangedForInvalidSubId() {
        //set dual sim
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        mContext.sendBroadcast(new Intent(ACTION_MULTI_SIM_CONFIG_CHANGED));
        // set default slot
        mContext.sendBroadcast(new Intent(ACTION_DEFAULT_SUBSCRIPTION_CHANGED)
                .putExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, 2)
                .putExtra(SubscriptionManager.EXTRA_SLOT_INDEX, 0));
        processAllMessages();

        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_PRECISE_DATA_CONNECTION_STATE_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(1).when(mMockSubInfo).getSimSlotIndex();

        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();

        // notify data connection with invalid sub id and default phone id
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*default phoneId*/ 0, /*invalid subId*/ -1,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(1)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                .setApnName("ims")
                                .setEntryName("ims")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        processAllMessages();

        assertEquals(0, mTelephonyCallback.invocationCount.get());

        // notify data connection with invalid sub id and default phone id
        mTelephonyRegistry.notifyDataConnectionForSubscriber(
                /*target phoneId*/ 1, /*invalid subId*/ -1,
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setId(2)
                        .setState(TelephonyManager.DATA_SUSPENDED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(new ApnSetting.Builder()
                                .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                .setApnName("ims")
                                .setEntryName("ims")
                                .build())
                        .setLinkProperties(new LinkProperties())
                        .setFailCause(0)
                        .build());

        processAllMessages();

        assertEquals(1, mTelephonyCallback.invocationCount.get());
    }

    @Test @SmallTest
    public void testCellInfoChanged() {
        final int subId = 1;
        final int[] events = {TelephonyCallback.EVENT_CELL_INFO_CHANGED};
        final List<CellInfo> dummyCellInfo = Arrays.asList(new CellInfoLte());

        mCellInfo = null; // null is an invalid value since the API is NonNull;

        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0 /*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();
        doReturn(true).when(mLocationManager).isLocationEnabledForUser(any(UserHandle.class));

        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);
        processAllMessages();
        assertEquals(1, mTelephonyCallback.invocationCount.get());
        assertNotNull(mCellInfo);

        mTelephonyRegistry.notifyCellInfoForSubscriber(subId, dummyCellInfo);
        processAllMessages();
        assertEquals(2, mTelephonyCallback.invocationCount.get());
        assertEquals(mCellInfo, dummyCellInfo);
    }

    @Test
    public void testNotifyDataActivityForSubscriberWithSlot() {
        final int subId = 1;
        int[] events = {TelephonyCallback.EVENT_DATA_ACTIVITY_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();

        assertEquals(TelephonyManager.DATA_ACTIVITY_NONE, mDataActivity);
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);

        mTelephonyRegistry.notifyDataActivityForSubscriberWithSlot(0/*phoneId*/, subId,
                TelephonyManager.DATA_ACTIVITY_INOUT);
        processAllMessages();
        assertEquals(TelephonyManager.DATA_ACTIVITY_INOUT, mDataActivity);
    }

    @Test
    public void testNotifyDataActivityForSubscriberWithSlotForInvalidSubId() {
        final int subId = INVALID_SUBSCRIPTION_ID;
        int[] events = {TelephonyCallback.EVENT_DATA_ACTIVITY_CHANGED};
        doReturn(mMockSubInfo).when(mSubscriptionManager).getActiveSubscriptionInfo(anyInt());
        doReturn(0/*slotIndex*/).when(mMockSubInfo).getSimSlotIndex();

        assertEquals(TelephonyManager.DATA_ACTIVITY_NONE, mDataActivity);
        mTelephonyRegistry.listenWithEventList(false, false, subId, mContext.getOpPackageName(),
                mContext.getAttributionTag(), mTelephonyCallback.callback, events, true);

        mTelephonyRegistry.notifyDataActivityForSubscriberWithSlot(0/*phoneId*/, subId,
                TelephonyManager.DATA_ACTIVITY_OUT);
        processAllMessages();
        assertEquals(TelephonyManager.DATA_ACTIVITY_OUT, mDataActivity);
    }
}
