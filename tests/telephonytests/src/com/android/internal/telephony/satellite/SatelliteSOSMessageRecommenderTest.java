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

import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
import static android.telephony.TelephonyManager.EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS;
import static android.telephony.satellite.SatelliteManager.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.telecom.Connection;
import android.telephony.BinderCacheManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.ims.RegistrationManager;
import android.telephony.satellite.ISatelliteProvisionStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.text.TextUtils;
import android.util.Log;

import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.R;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
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
    private static final long TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS = 500;
    private static final int PHONE_ID = 0;
    private static final int PHONE_ID2 = 1;
    private static final String CALL_ID = "CALL_ID";
    private static final String WRONG_CALL_ID = "WRONG_CALL_ID";
    private static final String DEFAULT_SATELLITE_MESSAGING_PACKAGE = "android.com.google.default";
    private static final String DEFAULT_SATELLITE_MESSAGING_CLASS =
            "android.com.google.default.SmsMmsApp";
    private static final String DEFAULT_HANDOVER_INTENT_ACTION =
            "android.com.vendor.action.EMERGENCY_MESSAGING";
    private TestSatelliteController mTestSatelliteController;
    private TestImsManager mTestImsManager;
    @Mock
    private Resources mResources;
    @Mock
    private ImsManager.MmTelFeatureConnectionFactory mMmTelFeatureConnectionFactory;
    @Mock
    private FeatureFlags mFeatureFlags;
    private TestConnection mTestConnection;
    private TestSOSMessageRecommender mTestSOSMessageRecommender;
    private ServiceState mServiceState2;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(R.string.config_satellite_service_package))
                .thenReturn("");
        when(mResources.getString(R.string.config_satellite_emergency_handover_intent_action))
                .thenReturn(DEFAULT_HANDOVER_INTENT_ACTION);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        mTestSatelliteController = new TestSatelliteController(mContext,
                Looper.myLooper(), mFeatureFlags);
        mTestImsManager = new TestImsManager(
                mContext, PHONE_ID, mMmTelFeatureConnectionFactory, null, null, null);
        mTestConnection = new TestConnection(CALL_ID);
        mPhones = new Phone[] {mPhone, mPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        mServiceState2 = Mockito.mock(ServiceState.class);
        when(mPhone.getServiceState()).thenReturn(mServiceState);
        when(mPhone.getPhoneId()).thenReturn(PHONE_ID);
        when(mPhone2.getServiceState()).thenReturn(mServiceState2);
        when(mPhone2.getPhoneId()).thenReturn(PHONE_ID2);
        mTestSOSMessageRecommender = new TestSOSMessageRecommender(mContext, Looper.myLooper(),
                mTestSatelliteController, mTestImsManager,
                TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        when(mServiceState.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mServiceState2.getState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mPhone.isImsRegistered()).thenReturn(false);
        when(mPhone2.isImsRegistered()).thenReturn(false);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_T911() {
        testTimeoutBeforeEmergencyCallEnd(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                DEFAULT_SATELLITE_MESSAGING_PACKAGE, DEFAULT_SATELLITE_MESSAGING_CLASS,
                DEFAULT_HANDOVER_INTENT_ACTION);
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithValidHandoverAppConfigured() {
        String satelliteHandoverApp =
                "android.com.vendor.message;android.com.vendor.message.SmsApp";
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn(satelliteHandoverApp);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false);
        testTimeoutBeforeEmergencyCallEnd(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS,
                "android.com.vendor.message", "android.com.vendor.message.SmsApp",
                DEFAULT_HANDOVER_INTENT_ACTION);
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithInValidHandoverAppConfigured() {
        String satelliteHandoverApp =
                "android.com.vendor.message;android.com.vendor.message.SmsApp;abc";
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn(satelliteHandoverApp);
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false);
        testTimeoutBeforeEmergencyCallEnd(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, "", "",
                DEFAULT_HANDOVER_INTENT_ACTION);
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_SOS_WithoutHandoverAppConfigured() {
        when(mResources.getString(R.string.config_oem_enabled_satellite_sos_handover_app))
                .thenReturn("");
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false);
        testTimeoutBeforeEmergencyCallEnd(EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_SOS, "", "",
                DEFAULT_HANDOVER_INTENT_ACTION);
    }

    private void testTimeoutBeforeEmergencyCallEnd(int expectedHandoverType,
            String expectedPackageName, String expectedClassName, String expectedAction) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();
        if (TextUtils.isEmpty(expectedPackageName) || TextUtils.isEmpty(expectedClassName)) {
            assertTrue(mTestConnection.isEventWithoutLaunchIntentSent(
                    TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE, expectedHandoverType));
        } else {
            assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                    expectedHandoverType, expectedPackageName, expectedClassName, expectedAction));
        }
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_EventDisplayEmergencyMessageNotSent() {
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false);
        mTestSatelliteController.setIsSatelliteViaOemProvisioned(false);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();
        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    @Test
    public void testTimeoutBeforeEmergencyCallEnd_T911_FromNotConnectedToConnected() {
        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(false);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSatelliteController.setSatelliteConnectedViaCarrierWithinHysteresisTime(true);
        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();
        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911,
                DEFAULT_SATELLITE_MESSAGING_PACKAGE, DEFAULT_SATELLITE_MESSAGING_CLASS,
                DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionActive() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_ACTIVE);
    }

    @Test
    public void testStopTrackingCallBeforeTimeout_ConnectionDisconnected() {
        testStopTrackingCallBeforeTimeout(Connection.STATE_DISCONNECTED);
    }

    @Test
    public void testImsRegistrationStateChangedBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        when(mPhone.isImsRegistered()).thenReturn(true);
        mTestImsManager.sendImsRegistrationStateChangedEvent(0, true);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0, 0);

        when(mPhone.isImsRegistered()).thenReturn(false);
        when(mPhone2.isImsRegistered()).thenReturn(true);
        mTestImsManager.sendImsRegistrationStateChangedEvent(1, true);
        processAllMessages();
        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0, 0);

        when(mPhone2.isImsRegistered()).thenReturn(false);
        mTestImsManager.sendImsRegistrationStateChangedEvent(1, false);
        processAllMessages();
        assertEquals(2, mTestSOSMessageRecommender.getCountOfTimerStarted());

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911, DEFAULT_SATELLITE_MESSAGING_PACKAGE,
                DEFAULT_SATELLITE_MESSAGING_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
    }

    @Test
    public void testSatelliteProvisionStateChangedBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, false);
        processAllMessages();

        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();
        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 2, 4, 2);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 2, 4, 2);

        mTestSatelliteController.sendProvisionStateChangedEvent(
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID, true);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911, DEFAULT_SATELLITE_MESSAGING_PACKAGE,
                DEFAULT_SATELLITE_MESSAGING_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 2, 4, 2);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 2, 4, 2);
    }

    @Test
    public void testEmergencyCallRedialBeforeTimeout() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911, DEFAULT_SATELLITE_MESSAGING_PACKAGE,
                DEFAULT_SATELLITE_MESSAGING_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_InServiceToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_IN_SERVICE, ServiceState.STATE_POWER_OFF);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToOutOfService() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_OUT_OF_SERVICE);
    }

    @Test
    public void testCellularServiceStateChangedBeforeTimeout_EmergencyOnlyToPowerOff() {
        testCellularServiceStateChangedBeforeTimeout(
                ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_POWER_OFF);
    }

    @Test
    public void testOnEmergencyCallConnectionStateChangedWithWrongCallId() {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(
                WRONG_CALL_ID, Connection.STATE_ACTIVE);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    @Test
    public void testSatelliteNotAllowedInCurrentLocation() {
        mTestSatelliteController.setIsSatelliteCommunicationAllowed(false);
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        /**
         * We should have registered for the state change events and started the timer when
         * receiving the event onEmergencyCallStarted. After getting the callback for the result of
         * the request requestIsSatelliteCommunicationAllowedForCurrentLocation, the resources
         * should be cleaned up.
         */
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    @Test
    public void testOnEmergencyCallStarted() {
        SatelliteController satelliteController = new SatelliteController(
                mContext, Looper.myLooper(), mFeatureFlags);
        TestSOSMessageRecommender testSOSMessageRecommender = new TestSOSMessageRecommender(
                mContext,
                Looper.myLooper(),
                satelliteController, mTestImsManager,
                TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        testSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertFalse(testSOSMessageRecommender.isTimerStarted());
        assertEquals(0, testSOSMessageRecommender.getCountOfTimerStarted());
    }

    private void testStopTrackingCallBeforeTimeout(
            @Connection.ConnectionState int connectionState) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        mTestSOSMessageRecommender.onEmergencyCallConnectionStateChanged(CALL_ID, connectionState);
        processAllMessages();

        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
    }

    private void testCellularServiceStateChangedBeforeTimeout(
            @ServiceState.RegState int availableServiceState,
            @ServiceState.RegState int unavailableServiceState) {
        mTestSOSMessageRecommender.onEmergencyCallStarted(mTestConnection);
        processAllMessages();

        assertTrue(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertRegisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertRegisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);

        when(mServiceState.getState()).thenReturn(availableServiceState);
        mTestSOSMessageRecommender.sendServiceStateChangedEvent();
        processAllMessages();
        assertFalse(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE));
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
        assertEquals(1, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertUnregisterForStateChangedEventsTriggered(mPhone, 0, 0, 0);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 0, 0, 0);

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

        // Wait for the timeout to expires
        moveTimeForward(TEST_EMERGENCY_CALL_TO_SOS_MSG_HYSTERESIS_TIMEOUT_MILLIS);
        processAllMessages();

        assertTrue(mTestConnection.isEventSent(TelephonyManager.EVENT_DISPLAY_EMERGENCY_MESSAGE,
                EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE_T911, DEFAULT_SATELLITE_MESSAGING_PACKAGE,
                DEFAULT_SATELLITE_MESSAGING_CLASS, DEFAULT_HANDOVER_INTENT_ACTION));
        assertUnregisterForStateChangedEventsTriggered(mPhone, 1, 2, 1);
        assertUnregisterForStateChangedEventsTriggered(mPhone2, 1, 2, 1);
        assertEquals(0, mTestSOSMessageRecommender.getCountOfTimerStarted());
        assertFalse(mTestSOSMessageRecommender.isTimerStarted());
    }

    private void assertRegisterForStateChangedEventsTriggered(
            Phone phone, int registerForProvisionCount, int registerForImsCount,
            int registerForCellularCount) {
        assertEquals(registerForProvisionCount,
                mTestSatelliteController.getRegisterForSatelliteProvisionStateChangedCalls());
        assertEquals(registerForImsCount, mTestImsManager.getAddRegistrationCallbackCalls());
        verify(phone, times(registerForCellularCount))
                .registerForServiceStateChanged(any(), anyInt(), any());
    }

    private void assertUnregisterForStateChangedEventsTriggered(
            Phone phone, int unregisterForProvisionCount, int unregisterForImsCount,
            int unregisterForCellularCount) {
        assertEquals(unregisterForProvisionCount,
                mTestSatelliteController.getUnregisterForSatelliteProvisionStateChangedCalls());
        assertEquals(unregisterForImsCount, mTestImsManager.getRemoveRegistrationListenerCalls());
        verify(phone, times(unregisterForCellularCount)).unregisterForServiceStateChanged(any());
    }

    private static class TestSatelliteController extends SatelliteController {

        private static final String TAG = "TestSatelliteController";
        private final Map<Integer, Set<ISatelliteProvisionStateCallback>>
                mProvisionStateChangedCallbacks;
        private int mRegisterForSatelliteProvisionStateChangedCalls = 0;
        private int mUnregisterForSatelliteProvisionStateChangedCalls = 0;
        private boolean mIsSatelliteViaOemProvisioned = true;
        private boolean mIsSatelliteCommunicationAllowed = true;
        private boolean mIsSatelliteConnectedViaCarrierWithinHysteresisTime = true;

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
        public Boolean isSatelliteViaOemProvisioned() {
            return mIsSatelliteViaOemProvisioned;
        }

        @Override
        public boolean isSatelliteSupportedViaOem() {
            return true;
        }

        @Override
        @SatelliteManager.SatelliteResult public int registerForSatelliteProvisionStateChanged(
                int subId, @NonNull ISatelliteProvisionStateCallback callback) {
            mRegisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.getOrDefault(subId, new HashSet<>());
            perSubscriptionCallbacks.add(callback);
            mProvisionStateChangedCallbacks.put(subId, perSubscriptionCallbacks);
            return SatelliteManager.SATELLITE_RESULT_SUCCESS;
        }

        @Override
        public void unregisterForSatelliteProvisionStateChanged(
                int subId, @NonNull ISatelliteProvisionStateCallback callback) {
            mUnregisterForSatelliteProvisionStateChangedCalls++;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(subId);
            if (perSubscriptionCallbacks != null) {
                perSubscriptionCallbacks.remove(callback);
            }
        }

        @Override
        public void requestIsSatelliteCommunicationAllowedForCurrentLocation(int subId,
                @NonNull ResultReceiver result) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(SatelliteManager.KEY_SATELLITE_COMMUNICATION_ALLOWED,
                    mIsSatelliteCommunicationAllowed);
            result.send(SatelliteManager.SATELLITE_RESULT_SUCCESS, bundle);
        }

        @Override
        public boolean isSatelliteConnectedViaCarrierWithinHysteresisTime() {
            return mIsSatelliteConnectedViaCarrierWithinHysteresisTime;
        }

        @Override
        protected int getEnforcedEmergencyCallToSatelliteHandoverType() {
            return INVALID_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE;
        }

        public void setIsSatelliteCommunicationAllowed(boolean allowed) {
            mIsSatelliteCommunicationAllowed = allowed;
        }

        public void setSatelliteConnectedViaCarrierWithinHysteresisTime(
                boolean connectedViaCarrier) {
            mIsSatelliteConnectedViaCarrierWithinHysteresisTime = connectedViaCarrier;
        }

        public int getRegisterForSatelliteProvisionStateChangedCalls() {
            return mRegisterForSatelliteProvisionStateChangedCalls;
        }

        public int getUnregisterForSatelliteProvisionStateChangedCalls() {
            return mUnregisterForSatelliteProvisionStateChangedCalls;
        }

        public void setIsSatelliteViaOemProvisioned(boolean provisioned) {
            mIsSatelliteViaOemProvisioned = provisioned;
        }

        public void sendProvisionStateChangedEvent(int subId, boolean provisioned) {
            mIsSatelliteViaOemProvisioned = provisioned;
            Set<ISatelliteProvisionStateCallback> perSubscriptionCallbacks =
                    mProvisionStateChangedCallbacks.get(subId);
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
        private ComponentName mSmsAppComponent = new ComponentName(
                DEFAULT_SATELLITE_MESSAGING_PACKAGE, DEFAULT_SATELLITE_MESSAGING_CLASS);

        /**
         * Create an instance of SatelliteSOSMessageRecommender.
         *
         * @param looper              The looper used with the handler of this class.
         * @param satelliteController The SatelliteController singleton instance.
         * @param imsManager          The ImsManager instance associated with the phone, which is
         *                            used for making the emergency call. This argument is not
         *                            null only in unit tests.
         * @param timeoutMillis       The timeout duration of the timer.
         */
        TestSOSMessageRecommender(Context context, Looper looper,
                SatelliteController satelliteController, ImsManager imsManager,
                long timeoutMillis) {
            super(context, looper, satelliteController, imsManager, timeoutMillis);
        }

        @Override
        protected ComponentName getDefaultSmsApp() {
            return mSmsAppComponent;
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
