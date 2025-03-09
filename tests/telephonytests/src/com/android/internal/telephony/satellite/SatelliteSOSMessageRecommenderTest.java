/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import static android.telephony.ServiceState.STATE_OUT_OF_SERVICE;
import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.telecom.Connection;
import android.telecom.TelecomManager;
import android.telephony.BinderCacheManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.R;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.flags.Flags;
import com.android.internal.telephony.metrics.SatelliteStats;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Unit tests for SatelliteSOSMessageRecommender
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteSOSMessageRecommenderTest extends TelephonyTest {
    private static final String TAG = "SatelliteSOSMessageRecommenderTest";
    private static final int TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS = 500;
    private static final int TEST_EMERGENCY_CALL_TO_T911_MSG_HYSTERESIS_TIMEOUT_MILLIS = 1000;
    private static final int PHONE_ID = 0;
    private static final int PHONE_ID2 = 1;
    private static final int SUB_ID = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    private static final int SUB_ID1 = 1;
    private static final String CALL_ID = "CALL_ID";
    private static final String WRONG_CALL_ID = "WRONG_CALL_ID";
    private static final String DEFAULT_SATELLITE_MESSAGING_PACKAGE = "android.com.google.default";
    private static final String DEFAULT_SATELLITE_MESSAGING_CLASS =
            "android.com.google.default.SmsMmsApp";
    private static final String DEFAULT_HANDOVER_INTENT_ACTION =
            "android.com.vendor.action.EMERGENCY_MESSAGING";
    private static final String DEFAULT_SOS_HANDOVER_APP =
            "android.com.vendor.message;android.com.vendor.message.SosHandoverApp";
    private static final String DEFAULT_SATELLITE_SOS_HANDOVER_PACKAGE =
        "android.com.vendor.message";
    private static final String DEFAULT_SATELLITE_SOS_HANDOVER_CLASS =
            "android.com.vendor.message.SosHandoverApp";
    private static final String DEFAULT_T911_HANDOVER_INTENT_ACTION = Intent.ACTION_SENDTO;
    private TestSatelliteController mTestSatelliteController;
    private TestImsManager mTestImsManager;
    @Mock
    private Resources mResources;
    @Mock
    private ImsManager.MmTelFeatureConnectionFactory mMmTelFeatureConnectionFactory;
    @Mock
    private FeatureFlags mFeatureFlags;
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private TestConnection mTestConnection;
    private Uri mTestConnectionAddress = Uri.parse("tel:1234");
    private TestSOSMessageRecommender mTestSOSMessageRecommender;
    private ServiceState mServiceState2;
    @Mock
    private SatelliteStats mMockSatelliteStats;
    @Mock private SubscriptionManagerService mMockSubscriptionManagerService;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(R.string.config_satellite_service_package))
                .thenReturn("");
        when(mResources.getString(R.string.config_satellite_emergency_handover_intent_action))
                .thenReturn(DEFAULT_HANDOVER_INTENT_ACTION);
        when(mResources.getInteger(
                R.integer.config_emergency_call_wait_for_connection_timeout_millis))
                .thenReturn(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn(DEFAULT_SOS_HANDOVER_APP);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        mTestSatelliteController = new TestSatelliteController(mContext,
                Looper.myLooper(), mFeatureFlags);
        mTestImsManager = new TestImsManager(
                mContext, PHONE_ID, mMmTelFeatureConnectionFactory, null, null, null);
        mTestConnection = new TestConnection(CALL_ID);
        mTestConnection.setAddress(mTestConnectionAddress, TelecomManager.PRESENTATION_ALLOWED);
        mPhones = new Phone[] {mPhone, mPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        mServiceState2 = Mockito.mock(ServiceState.class);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        when(mPhone.getPhoneId()).thenReturn(PHONE_ID);
        when(mPhone.getSignalStrengthController()).thenReturn(mSignalStrengthController);
        when(mPhone2.getServiceState()).thenReturn(mServiceState2);
        when(mPhone2.getPhoneId()).thenReturn(PHONE_ID2);
        when(mPhone2.getSignalStrengthController()).thenReturn(mSignalStrengthController);
        mTestSOSMessageRecommender = new TestSOSMessageRecommender(mContext, Looper.myLooper(),
                mTestSatelliteController, mTestImsManager);
        when(mServiceState.getState()).thenReturn(STATE_OUT_OF_SERVICE);
        when(mServiceState2.getState()).thenReturn(STATE_OUT_OF_SERVICE);
        when(mPhone.isImsRegistered()).thenReturn(false);
        when(mPhone2.isImsRegistered()).thenReturn(false);
        replaceInstance(SatelliteStats.class, "sInstance", null,
                mMockSatelliteStats);
        replaceInstance(SubscriptionManagerService.class, "sInstance", null,
                mMockSubscriptionManagerService);
        doNothing().when(mMockSatelliteStats).onSatelliteSosMessageRecommender(
                any(SatelliteStats.SatelliteSosMessageRecommenderParams.class));
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            false, -1);
        mTestSOSMessageRecommender.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            false, -1);
        mTestSatelliteController.selectedSatelliteSubId = SUB_ID1;
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo.Builder()
                .setId(SUB_ID1).setOnlyNonTerrestrialNetwork(true).build();
        when(mMockSubscriptionManagerService.getSubscriptionInfo(eq(SUB_ID1)))
            .thenReturn(subscriptionInfo);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_T911() {
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        testTimeoutBeforeEmergencyCallEnd(
            TEST_EMERGENCY_CALL_TO_T911_MSG_HYSTERESIS_TIMEOUT_MILLIS,
            EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
            DEFAULT_SATELLITE_MESSAGING_PACKAGE,
            DEFAULT_SATELLITE_MESSAGING_CLASS,
            DEFAULT_T911_HANDOVER_INTENT_ACTION);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithValidHandoverAppConfigured() {
        String satelliteHandoverApp =
                "android.com.vendor.message;android.com.vendor.message.SmsApp";
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn(satelliteHandoverApp);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        testTimeoutBeforeEmergencyCallEnd(
            TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS,
            EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
            "android.com.vendor.message", "android.com.vendor.message.SmsApp",
            DEFAULT_HANDOVER_INTENT_ACTION);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithInValidHandoverAppConfigured() {
        String satelliteHandoverApp =
                "android.com.vendor.message;android.com.vendor.message.SmsApp;abc";
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn(satelliteHandoverApp);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        testTimeoutBeforeEmergencyCallEnd(
            TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS,
            EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, "", "",
            DEFAULT_HANDOVER_INTENT_ACTION);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithoutHandoverAppConfigured() {
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn("");
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        testTimeoutBeforeEmergencyCallEnd(
            TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS,
            EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, "", "",
            DEFAULT_HANDOVER_INTENT_ACTION);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testStartTimer_emergencyCallNull() {
        when(mServiceState.getState()).thenReturn(STATE_OUT_OF_SERVICE);
        when(mServiceState2.getState()).thenReturn(STATE_OUT_OF_SERVICE);
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(null, false);
        processAllMessages();
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());
    }

    private void testTimeoutBeforeEmergencyCallEnd(int timeoutMillis, int expectedHandoverType,
            String expectedPackageName, String expectedClassName, String expectedAction) {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1,  1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Wait for the timeout to expires
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(timeoutMillis);
        processAllMessages();
        if (TextUtils.isEmpty(expectedPackageName) || TextUtils.isEmpty(expectedClassName)) {
            assertTrue(mTestConnection.isEventWithoutLaunchIntentSent(
                    TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE, expectedHandoverType));
        } else {
            assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                    expectedHandoverType, expectedPackageName, expectedClassName, expectedAction));
        }
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_EventDisplayEmergencyMessageNotSent() {
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSatelliteController.setDeviceProvisioned(false);
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Wait for the timeout to expires
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();
        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_T911_FromNotConnectedToConnected() {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSatelliteController.isOemEnabledSatelliteSupported = false;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                DEFAULT_SATELLITE_MESSAGING_PACKAGE, DEFAULT_SATELLITE_MESSAGING_CLASS,
                DEFAULT_T911_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        mTestSatelliteController.isOemEnabledSatelliteSupported = true;
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionActive() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_ACTIVE);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionDisconnected() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_DISCONNECTED);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testNetworkStateChangedBeforeTimeout() {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Wait for the timeout to expires
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                DEFAULT_SATELLITE_SOS_HANDOVER_PACKAGE,
                DEFAULT_SATELLITE_SOS_HANDOVER_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testSatelliteProvisionStateChangedBeforeTimeout() {
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false);
        processAllMessages();

        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
        reset(mMockSatelliteStats);

        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 2, 2);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 2, 2);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true);

        // Wait for the timeout to expires
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                DEFAULT_SATELLITE_SOS_HANDOVER_PACKAGE,
                DEFAULT_SATELLITE_SOS_HANDOVER_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 2, 2);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 2, 2);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testEmergencyCallRedialBeforeTimeout() {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Wait for the timeout to expires and satellite access restriction checking result
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                DEFAULT_SATELLITE_SOS_HANDOVER_PACKAGE,
                DEFAULT_SATELLITE_SOS_HANDOVER_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, STATE_OUT_OF_SERVICE);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, ServiceState.STATE_POWER_OFF);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, STATE_OUT_OF_SERVICE);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_POWER_OFF);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertTrue(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testOnEmergencyCallConnectionStateChangedWithWrongCallId() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                WRONG_CALL_ID, Connection.STATE_ACTIVE);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testSatelliteNotAllowedInCurrentLocation() {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(false);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        verify(mMockSatelliteStats, times(1)).onSatelliteSosMessageRecommender(any());
        assertFalse(mTestSOSMessageRecommender.isDialerNotified());
    }

    @Test
    public void testOnEmergencyCallStarted() {
        SatelliteController satelliteController = new MinimalSatelliteControllerWrapper(mContext,
                Looper.myLooper(), mFeatureFlags);
        TestSOSMessageRecommender testSOSMessageRecommender = new TestSOSMessageRecommender(
                mContext,
                Looper.myLooper(),
                satelliteController, mTestImsManager);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        testSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();

        assertFalse(testSOSMessageRecommender.isTimerStarted());
        assertEquals(0, testSOSMessageRecommender.getCountOfTimerStarted());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());
    }

    @Test
    public void testIsDeviceProvisioned() {
        Boolean originalIsSatelliteViaOemProvisioned =
                mTestSatelliteController.mIsDeviceProvisionedForTest;

        mTestSatelliteController.mIsDeviceProvisionedForTest = null;
        assertFalse(mTestSOSMessageRecommender.isDeviceProvisioned());

        mTestSatelliteController.mIsDeviceProvisionedForTest = true;
        assertTrue(mTestSOSMessageRecommender.isDeviceProvisioned());

        mTestSatelliteController.mIsDeviceProvisionedForTest = false;
        assertFalse(mTestSOSMessageRecommender.isDeviceProvisioned());

        mTestSatelliteController.mIsDeviceProvisionedForTest =
                originalIsSatelliteViaOemProvisioned;
    }

    @Test
    public void testSelectEmergencyCallWaitForConnectionTimeoutDuration() {
        // Both OEM and carrier don't support satellite
        mTestSatelliteController.isSatelliteEmergencyMessagingSupportedViaCarrier = false;
        mTestSatelliteController.isOemEnabledSatelliteSupported = false;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(0, mTestSOSMessageRecommender.getTimeOutMillis());

        // Only OEM support satellite
        mTestSatelliteController.isOemEnabledSatelliteSupported = true;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS,
                mTestSOSMessageRecommender.getTimeOutMillis());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        // Both OEM and carrier support satellite, but device is not connected to carrier satellite
        // within hysteresis time. Thus, OEM timer will be used.
        mTestSatelliteController.isSatelliteEmergencyMessagingSupportedViaCarrier = true;
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS,
                mTestSOSMessageRecommender.getTimeOutMillis());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        // Both OEM and carrier support satellite, and device is connected to carrier satellite
        // within hysteresis time. Thus, carrier timer will be used.
        int carrierTimeoutMillis = 1000;
        mTestSatelliteController.isSatelliteEmergencyMessagingSupportedViaCarrier = true;
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        mTestSatelliteController.carrierEmergencyCallWaitForConnectionTimeoutMillis =
                carrierTimeoutMillis;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(carrierTimeoutMillis, mTestSOSMessageRecommender.getTimeOutMillis());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        // Both OEM and carrier support satellite, device is not connected to carrier satellite
        // within hysteresis time, but selected satellite subId is not NTN only. Thus, carrier
        // timer will be used.
        carrierTimeoutMillis = 2000;
        SubscriptionInfo subscriptionInfo = new SubscriptionInfo.Builder()
                .setId(SUB_ID1).setOnlyNonTerrestrialNetwork(false).build();
        when(mMockSubscriptionManagerService.getSubscriptionInfo(eq(SUB_ID1)))
            .thenReturn(subscriptionInfo);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSatelliteController.carrierEmergencyCallWaitForConnectionTimeoutMillis =
                carrierTimeoutMillis;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertEquals(carrierTimeoutMillis, mTestSOSMessageRecommender.getTimeOutMillis());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());
    }

    @Test
    public void testGetEmergencyCallToSatelliteHandoverType_SatelliteViaCarrierAndOemAvailable() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        mTestSatelliteController.mIsDeviceProvisionedForTest = true;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        assertEquals(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                mTestSOSMessageRecommender.getEmergencyCallToSatelliteHandoverType());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        mSetFlagsRule.disableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);
    }

    @Test
    public void testGetEmergencyCallToSatelliteHandoverType_OnlySatelliteViaCarrierAvailable() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(
            true, SUB_ID1);
        mTestSatelliteController.mIsDeviceProvisionedForTest = false;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        assertEquals(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                mTestSOSMessageRecommender.getEmergencyCallToSatelliteHandoverType());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        mSetFlagsRule.disableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);
    }

    @Test
    public void testGetEmergencyCallToSatelliteHandoverType_OemAndCarrierNotAvailable() {
        mSetFlagsRule.enableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSatelliteController.mIsDeviceProvisionedForTest = true;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        assertEquals(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                mTestSOSMessageRecommender.getEmergencyCallToSatelliteHandoverType());

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false, -1);
        mTestSatelliteController.mIsDeviceProvisionedForTest = false;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        assertEquals(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                mTestSOSMessageRecommender.getEmergencyCallToSatelliteHandoverType());
        verify(mMockSatelliteStats, never()).onSatelliteSosMessageRecommender(any());

        mSetFlagsRule.disableFlags(Flags.FLAG_CARRIER_ROAMING_NB_IOT_NTN);
    }

    private void testStopTrackingCallBeforeTimeout(
            @Connection.ConnectionState int connectionState) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(CALL_ID, connectionState);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
    }

    private void testCellularServiceStateChangedBeforeTimeout(
            @ServiceState.RegState int availableServiceState,
            @ServiceState.RegState int unavailableServiceState) {
        mTestSOSMessageRecommender.isSatelliteAllowedCallback = null;
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection, false);
        processAllMessages();
        assertNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 1);

        when(mServiceState.getState()).thenReturn(availableServiceState);
        mTestSOSMessageRecommender.sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 0, 0);

        when(mServiceState.getState()).thenReturn(unavailableServiceState);
        when(mServiceState2.getState()).thenReturn(availableServiceState);
        mTestSOSMessageRecommender.sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());

        when(mServiceState2.getState()).thenReturn(unavailableServiceState);
        mTestSOSMessageRecommender.sendServiceStateChangedEvent();
        processAllMessages();
        assertEquals(2, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());

        // Move Location service to emergency mode
        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                mTestConnection.getTelecomCallId(), Connection.STATE_DIALING);
        processAllMessages();
        assertNotNull(mTestSOSMessageRecommender.isSatelliteAllowedCallback);

        // Wait for the timeout to expires
        mTestSOSMessageRecommender.isSatelliteAllowedCallback.onResult(true);
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                DEFAULT_SATELLITE_SOS_HANDOVER_PACKAGE,
                DEFAULT_SATELLITE_SOS_HANDOVER_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
    }

    private void assertRegisterForStateChangedEventsTriggered(Phone phone,
            int registerForProvisionCount, int registerForCellularCount) {
        assertEquals(registerForProvisionCount,
                mTestSatelliteController.getRegisterForSatelliteProvisionStateChangedCalls());
        verify(phone, times(registerForCellularCount))
                .registerForServiceStateChanged(any(), anyInt(), any());
    }

    private void assertUnregisterForStateChangedEventsTriggered(
            Phone phone, int unregisterForProvisionCount, int unregisterForCellularCount) {
        assertEquals(unregisterForProvisionCount,
                mTestSatelliteController.getUnregisterForSatelliteProvisionStateChangedCalls());
        verify(phone, times(unregisterForCellularCount)).unregisterForServiceStateChanged(any());
    }

    private static class TestSatelliteController extends SatelliteController {

        private static final String TAG = "TestSatelliteController";
        private final Map<Integer, Set<ISatelliteProvisionStateCallback>>
                mProvisionStateChangedCallbacks;
        private int mRegisterForSatelliteProvisionStateChangedCalls = 0;
        private int mUnregisterForSatelliteProvisionStateChangedCalls = 0;
        private Boolean mIsDeviceProvisionedForTest = true;
        private boolean mIsSatelliteConnectedViaCarrierWithinHysteresisTime = true;
        private int mSubIdOfSatelliteConnectedViaCarrierWithinHysteresisTime = -1;
        public boolean isOemEnabledSatelliteSupported = true;
        public boolean isCarrierEnabledSatelliteSupported = true;
        public boolean isSatelliteEmergencyMessagingSupportedViaCarrier = true;
        public int carrierEmergencyCallWaitForConnectionTimeoutMillis =
                TEST_EMERGENCY_CALL_TO_T911_MSG_HYSTERESIS_TIMEOUT_MILLIS;
        public int overrideEmergencyCallToSatelliteHandoverType =
            SatelliteController.INVALID_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
        public boolean isSatelliteEsosSupported = false;
        public int carrierRoamingNtnEmergencyCallToSatelliteHandoverType =
            EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;
        public int selectedSatelliteSubId = -1;

        /**
         * Create a SatelliteController to act as a backend service of
         * {@link SatelliteManager}
         *
         * @param context The Context for the SatelliteController.
         */
        protected TestSatelliteController(
                Context context, Looper looper, FeatureFlags featureFlags) {
            super(context, looper, featureFlags);
            mProvisionStateChangedCallbacks = new HashMap<>();
        }

        @Override
        public Boolean isDeviceProvisioned() {
            return mIsDeviceProvisionedForTest;
        }

        @Override
        public boolean isSatelliteSupportedViaOem() {
            return isOemEnabledSatelliteSupported;
        }

        @Override
        public boolean isSatelliteSupportedViaCarrier() {
            return isCarrierEnabledSatelliteSupported;
        }

        @Override
        protected void registerForSatelliteCommunicationAllowedStateChanged() {
            logd("registerForSatelliteCommunicationAllowedStateChanged");
        }

        @Override
        @SatelliteManager.SatelliteResult public int registerForSatelliteProvisionStateChanged(
                @NonNull ISatelliteProvisionStateCallback callback) {
            mRegisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.getOrDefault(SUB_ID, new HashSet<>());
            perSubscriptionCallbacks.add(callback);
            mProvisionStateChangedCallbacks.put(SUB_ID, perSubscriptionCallbacks);
            return SatelliteManager.SATELLITE_RESULT_SUCCESS;
        }

        @Override
        public void unregisterForSatelliteProvisionStateChanged(
                @NonNull ISatelliteProvisionStateCallback callback) {
            mUnregisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(SUB_ID);
            if (perSubscriptionCallbacks != null) {
                perSubscriptionCallbacks.remove(callback);
            }
        }

        @Override
        public Pair<Boolean,Integer> isSatelliteConnectedViaCarrierWithinHysteresisTime() {
            return new Pair(mIsSatelliteConnectedViaCarrierWithinHysteresisTime,
                mSubIdOfSatelliteConnectedViaCarrierWithinHysteresisTime);
        }

        @Override
        protected int getEnforcedEmergencyCallToSatelliteHandoverType() {
            return overrideEmergencyCallToSatelliteHandoverType;
        }

        @Override
        public boolean isSatelliteEmergencyMessagingSupportedViaCarrier() {
            return isSatelliteEmergencyMessagingSupportedViaCarrier;
        }

        @Override
        public long getCarrierEmergencyCallWaitForConnectionTimeoutMillis() {
            return carrierEmergencyCallWaitForConnectionTimeoutMillis;
        }

        @Override
        public int getCarrierEmergencyCallWaitForConnectionTimeoutMillis(int subId) {
            return carrierEmergencyCallWaitForConnectionTimeoutMillis;
        }

        @Override
        protected List<DeviceState> getSupportedDeviceStates() {
            return List.of(new DeviceState(new DeviceState.Configuration.Builder(0 /* identifier */,
                    "DEFAULT" /* name */).build()));
        }

        @Override
        public boolean isSatelliteEsosSupported(int subId) {
            return isSatelliteEsosSupported;
        }

        @Override
        public int getCarrierRoamingNtnEmergencyCallToSatelliteHandoverType(int subId) {
            return carrierRoamingNtnEmergencyCallToSatelliteHandoverType;
        }

        @Override
        public int getSelectedSatelliteSubId() {
            return selectedSatelliteSubId;
        }

        public void setSatelliteConnectedViaCarrierWithinHysteresisTime(
                boolean connectedViaCarrier, int subId) {
            mIsSatelliteConnectedViaCarrierWithinHysteresisTime = connectedViaCarrier;
            mSubIdOfSatelliteConnectedViaCarrierWithinHysteresisTime = subId;
        }

        public int getRegisterForSatelliteProvisionStateChangedCalls() {
            return mRegisterForSatelliteProvisionStateChangedCalls;
        }

        public int getUnregisterForSatelliteProvisionStateChangedCalls() {
            return mUnregisterForSatelliteProvisionStateChangedCalls;
        }

        public void setDeviceProvisioned(boolean provisioned) {
            mIsDeviceProvisionedForTest = provisioned;
        }

        public void sendProvisionStateChangedEvent(int subId, boolean provisioned) {
            mIsDeviceProvisionedForTest = provisioned;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(SUB_ID);
            if (perSubscriptionCallbacks != null) {
                for (ISatelliteProvisionStateCallback callback : perSubscriptionCallbacks) {
                    try {
                        callback.onSatelliteProvisionStateChanged(provisioned);
                    } catch (RemoteException ex) {
                        Log.e(TAG, "sendProvisionStateChangedEvent: ex=" + ex);
                    }
                }
            }
        }
    }

    /**
     * Now that {@link SatelliteController} uses
     * {@link android.hardware.devicestate.DeviceStateManager} to determine if a device is a
     * foldable or not, we have to provide a minimal wrapper for {@link SatelliteController} for
     * tests that want to use a non-fake {@link SatelliteController}.
     */
    private static class MinimalSatelliteControllerWrapper extends SatelliteController {

        protected MinimalSatelliteControllerWrapper(
                Context context, Looper looper, FeatureFlags featureFlags) {
            super(context, looper, featureFlags);
        }

        @Override
        protected List<DeviceState> getSupportedDeviceStates() {
            return List.of(new DeviceState(new DeviceState.Configuration.Builder(0 /* identifier */,
                    "DEFAULT" /* name */).build()));
        }

        @Override
        protected void registerForSatelliteCommunicationAllowedStateChanged() {
            logd("registerForSatelliteCommunicationAllowedStateChanged");
        }
    }

    private static class TestImsManager extends ImsManager {

        private final List<RegistrationManager.RegistrationCallback> mCallbacks;
        private int mAddRegistrationCallbackCalls = 0;
        private int mRemoveRegistrationListenerCalls = 0;

        /**
         * Used for testing only to inject dependencies.
         */
        TestImsManager(Context context, int phoneId, MmTelFeatureConnectionFactory factory,
                SubscriptionManagerProxy subManagerProxy, SettingsProxy settingsProxy,
                BinderCacheManager binderCacheManager) {
            super(context, phoneId, factory, subManagerProxy, settingsProxy, binderCacheManager);
            mCallbacks = new ArrayList<>();
        }

        @Override
        public void addRegistrationCallback(RegistrationManager.RegistrationCallback callback,
                Executor executor)
                throws ImsException {
            mAddRegistrationCallbackCalls++;

            if (callback == null) {
                throw new NullPointerException("registration callback can't be null");
            }
            if (executor == null) {
                throw new NullPointerException("registration executor can't be null");
            }

            callback.setExecutor(executor);
            if (!mCallbacks.contains(callback)) {
                mCallbacks.add(callback);
            }
        }

        @Override
        public void removeRegistrationListener(RegistrationManager.RegistrationCallback callback) {
            mRemoveRegistrationListenerCalls++;

            if (callback == null) {
                throw new NullPointerException("registration callback can't be null");
            }
            mCallbacks.remove(callback);
        }

        public int getAddRegistrationCallbackCalls() {
            return mAddRegistrationCallbackCalls;
        }

        public int getRemoveRegistrationListenerCalls() {
            return mRemoveRegistrationListenerCalls;
        }

        public void sendImsRegistrationStateChangedEvent(int callbackIndex, boolean registered) {
            if (callbackIndex < 0 || callbackIndex >= mCallbacks.size()) {
                throw new IndexOutOfBoundsException("sendImsRegistrationStateChangedEvent: invalid"
                        + "callbackIndex=" + callbackIndex
                        + ", mCallbacks.size=" + mCallbacks.size());
            }
            RegistrationManager.RegistrationCallback callback = mCallbacks.get(callbackIndex);
            if (registered) {
                callback.onRegistered(null);
            } else {
                callback.onUnregistered(null);
            }
        }
    }

    private static class TestSOSMessageRecommender extends SatelliteSOSMessageRecommender {
        public OutcomeReceiver<Boolean, SatelliteManager.SatelliteException>
                isSatelliteAllowedCallback = null;
        private ComponentName mSmsAppComponent = new ComponentName(
                DEFAULT_SATELLITE_MESSAGING_PACKAGE, DEFAULT_SATELLITE_MESSAGING_CLASS);
        private boolean mIsDialerNotified;
        private boolean mProvisionState = true;
        public boolean isSatelliteAllowedByReasons = true;

        /**
         * Create an instance of SatelliteSOSMessageRecommender.
         *
         * @param looper              The looper used with the handler of this class.
         * @param satelliteController The SatelliteController singleton instance.
         * @param imsManager          The ImsManager instance associated with the phone, which is
         *                            used for making the emergency call. This argument is not
         *                            null only in unit tests.
         */
        TestSOSMessageRecommender(Context context, Looper looper,
                SatelliteController satelliteController, ImsManager imsManager) {
            super(context, looper, satelliteController, imsManager);
        }

        @Override
        protected ComponentName getDefaultSmsApp() {
            return mSmsAppComponent;
        }

        @Override
        protected void requestIsSatelliteCommunicationAllowedForCurrentLocation(
                @NonNull OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> callback) {
            logd("requestIsSatelliteCommunicationAllowedForCurrentLocation: callback="
                    + callback);
            isSatelliteAllowedCallback = callback;
        }

        @Override
        protected void reportESosRecommenderDecision(boolean isDialerNotified) {
            super.reportESosRecommenderDecision(isDialerNotified);
            mIsDialerNotified = isDialerNotified;
        }

        @Override
        protected boolean updateAndGetProvisionState() {
            return mProvisionState;
        }

        @Override
        protected boolean isSatelliteAllowedByReasons() {
            return isSatelliteAllowedByReasons;
        }

        public boolean isTimerStarted() {
            return hasMessages(EVENT_TIME_OUT);
        }

        public int getCountOfTimerStarted() {
            return mCountOfTimerStarted;
        }

        public void sendServiceStateChangedEvent() {
            sendMessage(obtainMessage(EVENT_SERVICE_STATE_CHANGED));
        }

        public long getTimeOutMillis() {
            return mTimeoutMillis;
        }

        public boolean isDialerNotified() {
            return mIsDialerNotified;
        }

        public void setSatelliteConnectedViaCarrierWithinHysteresisTime(
                boolean connectedViaCarrier, int subId) {
            mIsSatelliteConnectedViaCarrierWithinHysteresisTime.set(connectedViaCarrier);
            mSubIdOfSatelliteConnectedViaCarrierWithinHysteresisTime.set(subId);
        }
    }

    private static class TestConnection extends Connection {
        private String mSentEvent = null;
        private Bundle mExtras = null;
        TestConnection(String callId) {
            setTelecomCallId(callId);
        }

        @Override
        public void sendConnectionEvent(String event, Bundle extras) {
            mSentEvent = event;
            mExtras = extras;
        }

        public boolean isEventSent(String event, int handoverType, String packageName,
                String className, String action) {
            if (mSentEvent == null || mExtras == null) {
                return false;
            }

            PendingIntent pendingIntent = mExtras.getParcelable(
                    EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT, PendingIntent.class);
            Intent intent = pendingIntent.getIntent();
            if (!TextUtils.equals(event, mSentEvent) || handoverType != mExtras.getInt(
                    EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE)
                    || !TextUtils.equals(packageName, intent.getComponent().getPackageName())
                    || !TextUtils.equals(className, intent.getComponent().getClassName())
                    || !TextUtils.equals(action, intent.getAction())) {
                return false;
            }
            return true;
        }

        public boolean isEventWithoutLaunchIntentSent(String event, int handoverType) {
            if (mSentEvent == null || mExtras == null) {
                return false;
            }

            PendingIntent pendingIntent = mExtras.getParcelable(
                    EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT, PendingIntent.class);
            if (!TextUtils.equals(event, mSentEvent) || handoverType != mExtras.getInt(
                    EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE) || pendingIntent != null) {
                return false;
            }

            return true;
        }

        public boolean isEventSent(String event) {
            if (mSentEvent == null) {
                return false;
            }
            if (!TextUtils.equals(event, mSentEvent)) {
                return false;
            }
            return true;
        }
    }
}
