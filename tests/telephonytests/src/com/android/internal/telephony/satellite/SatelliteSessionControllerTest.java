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

import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.CarrierConfigManager.KEY_SATELLITE_ROAMING_SCREEN_OFF_INACTIVITY_TIMEOUT_SEC_INT;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_SMS;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_UNKNOWN;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.satellite.ISatelliteModemStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.R;
import com.android.internal.telephony.IIntegerConsumer;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.satellite.metrics.SessionMetricsStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for SatelliteSessionController
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SatelliteSessionControllerTest extends TelephonyTest {
    private static final String TAG = "SatelliteSessionControllerTest";
    private static final int TEST_SATELLITE_TIMEOUT_MILLIS = 200;
    private static final int EVENT_PROCESSING_TIME_MILLIS = 100;

    private static final String STATE_UNAVAILABLE = "UnavailableState";
    private static final String STATE_POWER_OFF = "PowerOffState";
    private static final String STATE_ENABLING_SATELLITE = "EnablingState";
    private static final String STATE_DISABLING_SATELLITE = "DisablingState";
    private static final String STATE_IDLE = "IdleState";
    private static final String STATE_TRANSFERRING = "TransferringState";
    private static final String STATE_LISTENING = "ListeningState";
    private static final String STATE_NOT_CONNECTED = "NotConnectedState";
    private static final String STATE_CONNECTED = "ConnectedState";
    private static final int SCREEN_OFF_INACTIVITY_TIMEOUT_SEC = 30;
    private static final int P2P_SMS_INACTIVITY_TIMEOUT_SEC = 180;
    private static final int ESOS_INACTIVITY_TIMEOUT_SEC = 600;
    private TestSatelliteModemInterface mSatelliteModemInterface;
    private TestSatelliteSessionController mTestSatelliteSessionController;
    private TestSatelliteModemStateCallback mTestSatelliteModemStateCallback;

    @Mock private SatelliteController mMockSatelliteController;
    @Mock private DatagramReceiver mMockDatagramReceiver;
    @Mock private DatagramDispatcher mMockDatagramDispatcher;
    @Mock private DatagramController mMockDatagramController;
    @Mock private ServiceState mMockServiceState;
    @Mock private SessionMetricsStats mMockSessionMetricsStats;
    @Mock private AlarmManager mAlarmManager;

    @Captor ArgumentCaptor<Handler> mHandlerCaptor;
    @Captor ArgumentCaptor<Integer> mMsgCaptor;
    @Captor ArgumentCaptor<Executor> mExecutorArgumentCaptor;
    @Captor ArgumentCaptor<AlarmManager.OnAlarmListener> mOnAlarmListenerArgumentCaptor;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);

        replaceInstance(DatagramReceiver.class, "sInstance", null,
                mMockDatagramReceiver);
        replaceInstance(DatagramDispatcher.class, "sInstance", null,
                mMockDatagramDispatcher);
        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        replaceInstance(DatagramController.class, "sInstance", null,
                mMockDatagramController);
        replaceInstance(SessionMetricsStats.class, "sInstance", null,
                mMockSessionMetricsStats);

        Resources resources = mContext.getResources();
        when(resources.getInteger(anyInt())).thenReturn(TEST_SATELLITE_TIMEOUT_MILLIS);
        when(resources.getBoolean(
                 R.bool.config_satellite_modem_support_concurrent_tn_scanning))
            .thenReturn(false);
        when(resources.getBoolean(
                 R.bool.config_satellite_allow_tn_scanning_during_satellite_session))
            .thenReturn(true);
        when(mFeatureFlags.satellitePersistentLogging()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(false);
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(false);
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(false);
        when(mMockSatelliteController.getSatellitePhone()).thenReturn(mPhone);
        when(mMockSessionMetricsStats.addCountOfAutoExitDueToScreenOff()).thenReturn(
                mMockSessionMetricsStats);
        when(mMockSessionMetricsStats.addCountOfAutoExitDueToTnNetwork()).thenReturn(
                mMockSessionMetricsStats);
        mSatelliteModemInterface = new TestSatelliteModemInterface(
                mContext, mMockSatelliteController, Looper.myLooper(), mFeatureFlags);
        mTestSatelliteSessionController = new TestSatelliteSessionController(mContext,
                Looper.myLooper(), mFeatureFlags, true, mSatelliteModemInterface);
        processAllMessages();

        mTestSatelliteModemStateCallback = new TestSatelliteModemStateCallback();
        mTestSatelliteSessionController.registerForSatelliteModemStateChanged(
                mTestSatelliteModemStateCallback);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        mTestSatelliteSessionController.setAlarmManager(mAlarmManager);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testInitialState() {
        /**
         * Since satellite is not supported, SatelliteSessionController should move to UNAVAILABLE
         * state.
         */
        TestSatelliteSessionController sessionController1 = new TestSatelliteSessionController(
                mContext, Looper.myLooper(), mFeatureFlags, false, mSatelliteModemInterface);
        assertNotNull(sessionController1);
        processAllMessages();
        assertEquals(STATE_UNAVAILABLE, sessionController1.getCurrentStateName());

        /**
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        TestSatelliteSessionController sessionController2 = new TestSatelliteSessionController(
                mContext, Looper.myLooper(), mFeatureFlags, true, mSatelliteModemInterface);
        assertNotNull(sessionController2);
        processAllMessages();
        assertEquals(STATE_POWER_OFF, sessionController2.getCurrentStateName());
    }

    @Test
    public void testUnavailableState() throws Exception {
        /**
         * Since satellite is not supported, SatelliteSessionController should move to UNAVAILABLE
         * state.
         */
        TestSatelliteSessionController sessionController = new TestSatelliteSessionController(
                mContext, Looper.myLooper(), mFeatureFlags, false, mSatelliteModemInterface);
        assertNotNull(sessionController);
        processAllMessages();
        assertEquals(STATE_UNAVAILABLE, sessionController.getCurrentStateName());

        /**
         *  SatelliteSessionController should stay at UNAVAILABLE state even after it receives the
         *  satellite radio powered-on state changed event.
         */
        sessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();
        assertEquals(STATE_UNAVAILABLE, sessionController.getCurrentStateName());
    }

    @Test
    public void testScreenOffInactivityTimer() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});
        doNothing().when(mDeviceStateMonitor).registerForScreenStateChanged(
                eq(mTestSatelliteSessionController.getHandler()), anyInt(), any());
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_SCREEN_OFF_INACTIVITY_TIMEOUT_SEC_INT,
                SCREEN_OFF_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);
        when(mMockSatelliteController.isInCarrierRoamingNbIotNtn()).thenReturn(false);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveToIdleState();

        // Even if the device is not in CarrierRoamingNbIotNtn
        // SatelliteSessionController should call registerForScreenStateChanged.
        verify(mDeviceStateMonitor).registerForScreenStateChanged(mHandlerCaptor.capture(),
                mMsgCaptor.capture(), any());

        when(mMockSatelliteController.isInCarrierRoamingNbIotNtn()).thenReturn(true);

        // Notify Screen off
        sendScreenStateChanged(mHandlerCaptor.getValue(), mMsgCaptor.getValue(), false);
        processAllMessages();
        clearInvocations(mMockSatelliteController);

        // Verify that the screen off inactivity timer is set.
        verify(mAlarmManager).setExact(
                eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(),
                anyString(),
                mExecutorArgumentCaptor.capture(),
                any(),
                mOnAlarmListenerArgumentCaptor.capture()
        );
        // Notify alarm expired
        mExecutorArgumentCaptor.getValue().execute(
                () -> mOnAlarmListenerArgumentCaptor.getValue().onAlarm());
        processAllMessages();

        // Verify that SatelliteController#requestSatelliteEnabled() was called.
        verify(mMockSatelliteController).requestSatelliteEnabled(
                eq(false), eq(false), eq(false), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testScreenOffInactivityTimerStop() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});
        doNothing().when(mDeviceStateMonitor).registerForScreenStateChanged(
                eq(mTestSatelliteSessionController.getHandler()), anyInt(), any());
        // Satellite enabling request is for an emergency.
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_SCREEN_OFF_INACTIVITY_TIMEOUT_SEC_INT,
                SCREEN_OFF_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);
        when(mMockSatelliteController.isInCarrierRoamingNbIotNtn()).thenReturn(true);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveToIdleState();

         // SatelliteSessionController should call registerForScreenStateChanged.
        verify(mDeviceStateMonitor).registerForScreenStateChanged(mHandlerCaptor.capture(),
                mMsgCaptor.capture(), any());

        // Notify Screen off
        sendScreenStateChanged(mHandlerCaptor.getValue(), mMsgCaptor.getValue(), false);
        processAllMessages();

        // Verify that the screen off inactivity timer is set.
        verify(mAlarmManager).setExact(
                eq(AlarmManager.ELAPSED_REALTIME_WAKEUP),
                anyLong(),
                anyString(),
                mExecutorArgumentCaptor.capture(),
                any(),
                mOnAlarmListenerArgumentCaptor.capture()
        );

        // Notify Screen on
        sendScreenStateChanged(mHandlerCaptor.getValue(), mMsgCaptor.getValue(), true);

        processAllMessages();

        // Verify that the screen off inactivity timer is clear.
        verify(mAlarmManager).cancel(eq(mOnAlarmListenerArgumentCaptor.getValue()));
    }

    @Test
    public void testP2pSmsInactivityTimer() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});
        when(mMockSatelliteController.isInCarrierRoamingNbIotNtn()).thenReturn(true);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Verify that the P2P SMS inactivity timer is started.
        assertTrue(mTestSatelliteSessionController.isP2pSmsInActivityTimerStarted());

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        // Verify that the P2P SMS inactivity timer is stopped.
        assertFalse(mTestSatelliteSessionController.isP2pSmsInActivityTimerStarted());

        moveNotConnectedToConnectedState();

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(false);

        // Verify that the P2P SMS inactivity timer is started in CONNECTED state.
        assertTrue(mTestSatelliteSessionController.isP2pSmsInActivityTimerStarted());

        // Time shift to cause timeout
        moveTimeForward(P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
    }

    @Test
    public void testEsosInactivityTimer() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Verify that the ESOS inactivity timer is started.
        assertTrue(mTestSatelliteSessionController.isEsosInActivityTimerStarted());

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        // Verify that the ESOS inactivity timer is stopped.
        assertFalse(mTestSatelliteSessionController.isEsosInActivityTimerStarted());

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(false);

        // Verify that the ESOS inactivity timer is started.
        assertTrue(mTestSatelliteSessionController.isEsosInActivityTimerStarted());

        // Time shift to cause timeout
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
    }

    @Test
    public void testEsosP2pSmsInactivityTimerCase1() {
        // Send eSOS and SMS
        // After 10 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and don't auto exit

        long passedTime = 0;
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Notify datagram controller is in WAITING_TO_CONNECT.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // Verify that ESOS, P2P_SMS timer are not started.
        verifyEsosP2pSmsInactivityTimer(false, false);

        // Sent ESOS
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);

        // Verify that ESOS, P2P_SMS timer are started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent SMS
        sendMessage(DATAGRAM_TYPE_SMS);

        // Verify that ESOS, P2P_SMS timer are started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Time shift to cause P2P_SMS timeout
        passedTime = P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000;
        moveTimeForward(P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // Verify that keep ESOS timer, expired P2P_SMS timer.
        // NOT_CONNECTED state, satellite disabling not called.
        verifyEsosP2pSmsInactivityTimer(true, false);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(true), any(IIntegerConsumer.Stub.class));

        // Time shift to cause ESOS timeout
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000 - passedTime);
        processAllMessages();

        // Verify that expired ESOS and P2P_SMS timer
        // reported IDLE state, not called satellite disabling.
        verifyEsosP2pSmsInactivityTimer(false, false);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(true), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testEsosP2pSmsInactivityTimerCase2() {
        // Send eSOS and SMS
        // Send SMS after 3 mins
        // Send SMS after 1 mins
        // After 10 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and don't auto exit

        long passedTime = 0;
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Notify datagram controller is in WAITING_TO_CONNECT.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // Verify that ESOS, P2P_SMS timer are not started.
        verifyEsosP2pSmsInactivityTimer(false, false);

        // Sent ESOS, SMS
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);
        sendMessage(DATAGRAM_TYPE_SMS);
        // Verify that ESOS, P2P_SMS timer are started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent SMS again after 3 mins
        passedTime = 3 * 60 * 1000;
        moveTimeForward(3 * 60 * 1000);
        processAllMessages();
        sendMessage(DATAGRAM_TYPE_SMS);

        // Sent SMS again after 1 mins
        passedTime += 1 * 60 * 1000;
        moveTimeForward(1 * 60 * 1000);
        processAllMessages();
        sendMessage(DATAGRAM_TYPE_SMS);

        // Time shift to cause ESOS timeout
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000 - passedTime);
        processAllMessages();

        // Verify that expired ESOS and P2P_SMS timer
        // reported IDLE state, not called satellite disabling.
        verifyEsosP2pSmsInactivityTimer(false, false);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(true), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testEsosP2pSmsInactivityTimerCase3() {
        // Send eSOS and SMS
        // Send eSOS after 5 mins
        // After 15 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and don't auto exit

        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Notify datagram controller is in WAITING_TO_CONNECT.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // Verify that ESOS, P2P_SMS timer are not started.
        verifyEsosP2pSmsInactivityTimer(false, false);

        // Sent ESOS
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);
        // Verify that ESOS, P2P_SMS timer are started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent ESOS after 5 mins
        moveTimeForward(3 * 60 * 1000);
        processAllMessages();
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);

        // Time shift to cause ESOS timeout
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // Verify that expired ESOS and P2P_SMS timer
        // reported IDLE state, not called satellite disabling.
        verifyEsosP2pSmsInactivityTimer(false, false);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(true), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testEsosP2pSmsInactivityTimerCase4() {
        // Send SMS
        // Send SMS after 2 mins
        // After 3 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and auto exit

        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS, Satellite is not in emergency mode
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Notify datagram controller is in WAITING_TO_CONNECT.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // Verify that ESOS, P2P_SMS timer are not started.
        verifyEsosP2pSmsInactivityTimer(false, false);

        // Sent SMS
        sendMessage(DATAGRAM_TYPE_SMS);

        // Verify that ESOS is not started, P2P_SMS timer is started.
        verifyEsosP2pSmsInactivityTimer(false, true);

        // Sent SMS again after 2 mins
        moveTimeForward(2 * 60 * 1000);
        processAllMessages();
        sendMessage(DATAGRAM_TYPE_SMS);

        // Verify that ESOS is not started, P2P_SMS timer is started.
        verifyEsosP2pSmsInactivityTimer(false, true);

        // Time shift to cause P2P_SMS timeout
        moveTimeForward(P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();
    }

    @Test
    public void testEsosP2pSmsInactivityTimerCase5() {
        // Send SMS
        // Send ESOS after 2 mins
        // After 12 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and don'tauto exit

        long passedTime = 0;
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Notify datagram controller is in WAITING_TO_CONNECT.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // Verify that ESOS, P2P_SMS timer are not started.
        verifyEsosP2pSmsInactivityTimer(false, false);

        // Sent SMS
        sendMessage(DATAGRAM_TYPE_SMS);

        // Verify that ESOS is not started, P2P_SMS timer is started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent ESOS again after 2 mins
        passedTime = 2 * 60 * 1000;
        moveTimeForward(2 * 60 * 1000);
        processAllMessages();
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);

        // Verify that ESOS is not started, P2P_SMS timer is started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Time shift
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000 - passedTime);
        processAllMessages();
        verifyEsosP2pSmsInactivityTimer(true, false);

        // Time shift
        moveTimeForward(passedTime);
        processAllMessages();

        // Verify that expired P2P_SMS timer
        // reported IDLE state, called satellite disabling.
        verifyEsosP2pSmsInactivityTimer(false, false);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(true), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testEsosP2pSmsInactivityTimerInConnectedState() {
        // Send eSOS and SMS
        // After 10 minutes SatelliteSessionController moves to idle
        // TN network reports IN_SERVICE
        // Report the callback only and don't auto exit

        long passedTime = 0;
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        // Support ESOS
        when(mMockSatelliteController.isSatelliteEsosSupported(anyInt())).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(true);
        // Support P2P_SMS
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});

        // Setup carrier config for timer values
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_ESOS_INACTIVITY_TIMEOUT_SEC_INT,
                ESOS_INACTIVITY_TIMEOUT_SEC);
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        // Set up Datagram SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE
        setupDatagramTransferringState(true);

        moveToNotConnectedState();
        moveNotConnectedToConnectedState();

        // Verify that ESOS, P2P_SMS timer are started.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent ESOS
        // CONNECTED -> TRANSFERRING -> CONNECTED
        sendMessage(DATAGRAM_TYPE_SOS_MESSAGE);

        // Verify that ESOS, P2P_SMS timer are restarted.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Sent SMS
        // CONNECTED -> TRANSFERRING -> CONNECTED
        sendMessage(DATAGRAM_TYPE_SMS);

        // Verify that ESOS, P2P_SMS timer are restarted.
        verifyEsosP2pSmsInactivityTimer(true, true);

        // Time shift to cause P2P_SMS timeout
        passedTime = P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000;
        moveTimeForward(P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // Verify that keep ESOS timer, expired P2P_SMS timer.
        // CONNECTED state
        verifyEsosP2pSmsInactivityTimer(true, false);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Time shift to cause ESOS timeout
        moveTimeForward(ESOS_INACTIVITY_TIMEOUT_SEC * 1000 - passedTime);
        processAllMessages();

        // Verify that expired ESOS and P2P_SMS timer
        // reported IDLE state.
        verifyEsosP2pSmsInactivityTimer(false, false);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
    }

    @Test
    public void testDisableSatelliteWhenCellularModemEnabledInIdleMode() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        doNothing().when(mDeviceStateMonitor).registerForScreenStateChanged(
                eq(mTestSatelliteSessionController.getHandler()), anyInt(), any());
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(false);
        when(mPhone.getServiceState()).thenReturn(mMockServiceState);
        setUpResponseForRequestSatelliteEnabled(SATELLITE_RESULT_SUCCESS);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Conditions for operation
        boolean isEmergency = true;
        // Cellular network is not IN_SERVICE and emergency only.
        // Satellite request is emergency and emergency communication was established.
        // Disabling satellite was not allowed
        when(mMockServiceState.getVoiceRegState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mMockServiceState.getDataRegState()).thenReturn(ServiceState.STATE_OUT_OF_SERVICE);
        when(mMockServiceState.isEmergencyOnly()).thenReturn(false);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(isEmergency);
        when(mMockDatagramController.isEmergencyCommunicationEstablished()).thenReturn(true);
        when(mMockSatelliteController.turnOffSatelliteSessionForEmergencyCall(
                anyInt())).thenReturn(false);

        moveToIdleState();

        // Cellular network is not in STATE_IN_SERVICE or emergency only.
        // Should not disable satellite
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(isEmergency), any(IIntegerConsumer.Stub.class));
        verify(mMockSessionMetricsStats, never()).addCountOfAutoExitDueToTnNetwork();

        // Notify cellular service is in STATE_IN_SERVICE.
        ServiceState serviceState = new ServiceState();
        serviceState.setVoiceRegState(ServiceState.STATE_IN_SERVICE);
        serviceState.setDataRegState(ServiceState.STATE_OUT_OF_SERVICE);
        serviceState.setEmergencyOnly(false);
        mTestSatelliteSessionController.onCellularServiceStateChanged(serviceState);
        processAllMessages();

        // Satellite is in emergency mode and emergency communication was established.
        // Should not disable satellite
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(isEmergency), any(IIntegerConsumer.Stub.class));
        verify(mMockSessionMetricsStats, never()).addCountOfAutoExitDueToTnNetwork();

        // Satellite is in emergency mode but emergency communication was not established.
        // Disabling satellite was not allowed
        when(mMockDatagramController.isEmergencyCommunicationEstablished()).thenReturn(false);
        when(mMockSatelliteController.turnOffSatelliteSessionForEmergencyCall(
                anyInt())).thenReturn(false);
        mTestSatelliteSessionController.onCellularServiceStateChanged(serviceState);
        processAllMessages();

        // Should not disable satellite
        verify(mMockSatelliteController, never()).requestSatelliteEnabled(
                eq(false), eq(false), eq(isEmergency), any(IIntegerConsumer.Stub.class));
        verify(mMockSessionMetricsStats, never()).addCountOfAutoExitDueToTnNetwork();
        // Satellite is in emergency mode but emergency communication was not established.
        // Disabling satellite was allowed
        when(mMockSatelliteController.turnOffSatelliteSessionForEmergencyCall(
                anyInt())).thenReturn(true);
        mTestSatelliteSessionController.onCellularServiceStateChanged(serviceState);
        processAllMessages();

        // Should disable satellite
        verify(mMockSatelliteController).requestSatelliteEnabled(
                eq(false), eq(false), eq(isEmergency), any(IIntegerConsumer.Stub.class));
        verify(mMockSessionMetricsStats, times(1)).addCountOfAutoExitDueToTnNetwork();
    }

    @Test
    public void testStateTransition() {
        /**
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        powerOnSatelliteModem();

        // SatelliteSessionController should move to IDLE state after the modem is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move back to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        powerOnSatelliteModem();

        // SatelliteSessionController should move to IDLE state after radio is turned on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams failed
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(1, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(1, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(2, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(2, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(3, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state after timeout
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(3, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state instead of moving to IDLE
        // state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state instead of moving to IDLE
        // state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());
    }

    @Test
    public void testStateTransitionForNbIot() {
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        /**
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(false);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();
        // SatelliteSessionController should stay at NOT_CONNECTED state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        setupDatagramTransferringState(true);

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move back to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        clearInvocations(mMockDatagramController);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after radio is turned on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // The datagram sending event should be ignored.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Sending datagrams failed
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Sending datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start receiving datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start receiving datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_SUCCESS,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is disconnected from the satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        clearInvocations(mMockDatagramController);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state because NB-IOT inactivity timer has
        // timed out.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Start sending datagrams and the NB-IOT inactivity timer should be stopped.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should stay at NOT_CONNECTED state because.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Transferring datagram failed because satellite failed to connect to a satellite network.
        // The NB-IOT inactivity timer should be started.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state because NB-IOT inactivity timer has
        // timed out.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveSatelliteToEnablingState();

        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        processAllMessages();

        // The modem state changed events should be deferred
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(
                STATE_ENABLING_SATELLITE, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isEventDeferred(
                4 /* EVENT_SATELLITE_MODEM_STATE_CHANGED */));

        // Modem is powered on
        mTestSatelliteModemStateCallback.clearModemStates();
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on. Then, it should move to CONNECTED and then back to NOT_CONNECTED state
        // because of the above deferred events.
        assertEquals(3, mTestSatelliteModemStateCallback.getNumberOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                mTestSatelliteModemStateCallback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED,
                mTestSatelliteModemStateCallback.getModemState(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                mTestSatelliteModemStateCallback.getModemState(2));
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveSatelliteToEnablingState();

        mTestSatelliteModemStateCallback.clearSemaphorePermits();
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        processAllMessages();

        // The modem state changed events should be deferred
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(
                STATE_ENABLING_SATELLITE, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isEventDeferred(
                4 /* EVENT_SATELLITE_MODEM_STATE_CHANGED */));

        // Modem got reset. The deferred messages should be removed.
        mTestSatelliteModemStateCallback.clearSemaphorePermits();
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isEventDeferred(
                4 /* EVENT_SATELLITE_MODEM_STATE_CHANGED */));

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(1, mTestSatelliteModemStateCallback.getNumberOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                mTestSatelliteModemStateCallback.getModemState(0));
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
    }

    @Test
    public void testEnablingSatellite() {
        /*
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Power off satellite
        mTestSatelliteSessionController.onSatelliteEnablementStarted(false);
        processAllMessages();

        // Satellite should stay at POWER_OFF state
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveSatelliteToEnablingState();

        // Satellite enablement has failed
        mTestSatelliteSessionController.onSatelliteEnablementFailed(true);
        processAllMessages();

        // Satellite should move back to POWER_OFF state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        moveSatelliteToEnablingState();

        // Modem reset
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // Satellite should move back to POWER_OFF state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        powerOnSatelliteModem();
    }

    @Test
    public void testDisablingSatellite() {
        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // IDLE -> DISABLING request failed -> NOT_CONNECTED
        moveToIdleState();
        moveSatelliteToDisablingRequestFailed(SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED,
                STATE_NOT_CONNECTED);

        // NOT_CONNECTED -> DISABLING -> POWER_OFF
        moveSatelliteToDisablingState();
        moveToPowerOffState();

        // IDLE -> DISABLING -> POWER_OFF
        moveToIdleState();
        moveSatelliteToDisablingState();
        moveToPowerOffState();

        // TRANSFERRING -> DISABLING request failed -> CONNECTED
        moveToIdleState();
        moveIdleToTransferringState();
        moveSatelliteToDisablingRequestFailed(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED, STATE_CONNECTED);

        // CONNECTED -> DISABLING -> POWER_OFF
        moveSatelliteToDisablingState();
        moveToPowerOffState();

        // TRANSFERRING -> DISABLING -> POWER_OFF
        moveToIdleState();
        moveIdleToTransferringState();
        moveSatelliteToDisablingState();
        moveToPowerOffState();

        // LISTENING -> DISABLING -> POWER_OFF
        moveToIdleState();
        moveIdleToTransferringState();
        moveTransferringToListeningState();
        moveSatelliteToDisablingState();
        moveToPowerOffState();
    }

    @Test
    public void testDisablingSatelliteForNblot() {
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(false);

        // NOT_CONNECTED -> DISABLING
        moveToNotConnectedState();
        moveSatelliteToDisablingState();

        // DISABLING -> POWER_OFF
        moveToPowerOffState();

        // CONNECTED -> DISABLING
        moveToNotConnectedState();
        moveNotConnectedToConnectedState();
        moveSatelliteToDisablingState();

        // DISABLING -> POWER_OFF
        moveToPowerOffState();
    }

    @Test
    public void testEmergencyModeChanged() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);

        // Unregister exist callback
        mTestSatelliteSessionController.unregisterForSatelliteModemStateChanged(
                mTestSatelliteModemStateCallback);

        // Register callback
        mTestSatelliteSessionController.registerForSatelliteModemStateChanged(
                mTestSatelliteModemStateCallback);

        // Verify initial notification
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertSuccessfulEmergencyModeChangedCallback(
                mTestSatelliteModemStateCallback, false);

        mTestSatelliteSessionController.onEmergencyModeChanged(true);

        assertSuccessfulEmergencyModeChangedCallback(
                mTestSatelliteModemStateCallback, true);

        mTestSatelliteSessionController.onEmergencyModeChanged(false);

        assertSuccessfulEmergencyModeChangedCallback(
                mTestSatelliteModemStateCallback, false);

        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);
        mTestSatelliteSessionController.onEmergencyModeChanged(false);

        assertEmergencyModeChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
    }

    @Test
    public void testNotConnectedToIdleToNotConnectedStateTransition() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        when(mContext.getResources().getBoolean(
                 R.bool.config_satellite_modem_support_concurrent_tn_scanning)).thenReturn(true);

        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(true);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be started.
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state, but the state transition will
        // be hidden because device does not support satellite modem IDLE state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be stopped.
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        // The transition is hidden and thus DatagramController is not notified.
        verify(mMockDatagramController, never()).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);
    }

    @Test
    public void testNotConnectedToIdleToTransferringStateTransition() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        when(mContext.getResources().getBoolean(
                 R.bool.config_satellite_modem_support_concurrent_tn_scanning)).thenReturn(true);

        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(true);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be started.
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state, but the state transition will
        // be hidden because device does not support satellite modem IDLE state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be stopped.
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        // The transition is hidden and thus DatagramController is not notified.
        verify(mMockDatagramController, never()).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Modem report CONNECTED state
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should stay in IDLE state, but clients should be
        // notified that modem has moved to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);
    }

    @Test
    public void testConnectedToIdleToTransferringStateTransition() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        when(mContext.getResources().getBoolean(
                 R.bool.config_satellite_modem_support_concurrent_tn_scanning)).thenReturn(true);

        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(false);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Modem report CONNECTED state
        setupDatagramTransferringState(true);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be started.
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state, but the state transition will
        // be hidden because device does not support satellite modem IDLE state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be stopped.
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        // The transition is hidden and thus DatagramController is not notified.
        verify(mMockDatagramController, never()).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);
    }

    @Test
    public void testConnectedToIdleToNotConnectedStateTransition() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        when(mContext.getResources().getBoolean(
                 R.bool.config_satellite_modem_support_concurrent_tn_scanning)).thenReturn(true);

        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(false);

        powerOnSatelliteModem();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Modem report CONNECTED state
        setupDatagramTransferringState(true);
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be started.
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state, but the state transition will
        // be hidden because device does not support satellite modem IDLE state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteModemStateCallback);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        // The inactivity timer should be stopped.
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        // The transition is hidden and thus DatagramController is not notified.
        verify(mMockDatagramController, never()).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Modem report NOT_CONNECTED state
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should stay in IDLE state, but the clients
        // should be notified that modem has moved to NOT_CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);
    }

    @Test
    public void testP2pSmsInactivityTimerTimedOut_tnScanningNotSupported() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        when(mMockSatelliteController.getRequestIsEmergency()).thenReturn(false);
        when(mMockSatelliteController.isSatelliteRoamingP2pSmSSupported(
                anyInt())).thenReturn(true);
        when(mMockSatelliteController.getSupportedServicesOnCarrierRoamingNtn(anyInt()))
                .thenReturn(new int[]{
                        NetworkRegistrationInfo.SERVICE_TYPE_SMS,
                        NetworkRegistrationInfo.SERVICE_TYPE_EMERGENCY});
        when(mMockSatelliteController.isInCarrierRoamingNbIotNtn()).thenReturn(true);
        Resources resources = mContext.getResources();
        when(resources.getBoolean(
                R.bool.config_satellite_allow_tn_scanning_during_satellite_session))
                .thenReturn(false);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_SATELLITE_ROAMING_P2P_SMS_INACTIVITY_TIMEOUT_SEC_INT,
                P2P_SMS_INACTIVITY_TIMEOUT_SEC);
        when(mMockSatelliteController.getPersistableBundle(anyInt())).thenReturn(bundle);

        // Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
        assertNotNull(mTestSatelliteSessionController);
        mTestSatelliteSessionController.setSatelliteEnabledForNtnOnlySubscription(false);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        setupDatagramTransferringState(true);

        moveToNotConnectedState();

        // Verify that the P2P SMS inactivity timer is started.
        assertTrue(mTestSatelliteSessionController.isP2pSmsInActivityTimerStarted());

        // Time shift to cause timeout
        moveTimeForward(P2P_SMS_INACTIVITY_TIMEOUT_SEC * 1000);
        processAllMessages();

        // Should disable satellite
        verify(mMockSatelliteController).requestSatelliteEnabled(
                eq(false), eq(false), eq(false), any(IIntegerConsumer.Stub.class));
    }

    @Test
    public void testSetDeviceAlignedWithSatellite_updatesMaxInactivityDuration() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        setUserInactivityStart();

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        verify(mMockSessionMetricsStats, times(1)).updateMaxInactivityDurationSec(anyInt());
    }

    @Test
    public void
            testSetDeviceAlignedWithSatellite_setsInactivityStartTimestampUndefinedAfterUpdate() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        setUserInactivityStart();
        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        // There should be only one call to updateMaxInactivityDurationSec since the inactivity
        // start timestamp is reset to undefined.
        verify(mMockSessionMetricsStats, times(1)).updateMaxInactivityDurationSec(anyInt());
    }

    @Test
    public void
            testSetDeviceAlignedWithSatellite_noInactivityStart_noUpdateMaxInactivityDuration() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        verify(mMockSessionMetricsStats, times(0)).updateMaxInactivityDurationSec(anyInt());
    }

    @Test
    public void testSetDeviceAlignedWithSatellite_flagOff_noUpdateMaxInactivityDuration() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(false);
        setUserInactivityStart();

        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(true);

        verify(mMockSessionMetricsStats, times(0)).updateMaxInactivityDurationSec(anyInt());
    }

    @Test
    public void testOnDatagramTransferStateChanged_notIdle_updatesMaxInactivityDuration() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        setUserInactivityStart();

        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);

        // Since both the send and receive datagram transfer state is not idle, the max inactivity
        // duration should be updated.
        verify(mMockSessionMetricsStats, times(1)).updateMaxInactivityDurationSec(anyInt());
    }

    @Test
    public void testOnDatagramTransferStateChanged_idle_updatesMaxInactivityDuration() {
        when(mFeatureFlags.carrierRoamingNbIotNtn()).thenReturn(true);
        setUserInactivityStart();
        moveToIdleState();

        moveToPowerOffState();

        verify(mMockSessionMetricsStats, times(1)).updateMaxInactivityDurationSec(anyInt());
    }

    private void setUserInactivityStart() {
        // Set DatagramTransferState to idle and unaligned with satellite to define inactivity start
        // timestamp
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        mTestSatelliteSessionController.setDeviceAlignedWithSatellite(false);
    }

    private void verifyEsosP2pSmsInactivityTimer(boolean esosTimer, boolean p2pSmsTimer) {
        assertEquals(mTestSatelliteSessionController.isEsosInActivityTimerStarted(), esosTimer);
        assertEquals(mTestSatelliteSessionController.isP2pSmsInActivityTimerStarted(),
                p2pSmsTimer);
    }

    private void sendMessage(@SatelliteManager.DatagramType int datagramType) {
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, datagramType);
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_SUCCESS,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, datagramType);
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, datagramType);

        processAllMessages();
    }

    private void setupDatagramTransferringState(boolean isIdle) {
        when(mMockDatagramController.isSendingInIdleState()).thenReturn(isIdle);
        when(mMockDatagramController.isPollingInIdleState()).thenReturn(isIdle);
    }

    private void powerOnSatelliteModem() {
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnablementStarted(true);
        processAllMessages();

        // SatelliteSessionController should move to ENABLING state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE);
        assertEquals(
                STATE_ENABLING_SATELLITE, mTestSatelliteSessionController.getCurrentStateName());

        // Satellite is powered on
        mTestSatelliteModemStateCallback.clearModemStates();
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();
    }

    private void moveSatelliteToEnablingState() {
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        mTestSatelliteModemStateCallback.clearModemStates();

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnablementStarted(true);
        processAllMessages();

        // SatelliteSessionController should move to ENABLING state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_ENABLING_SATELLITE);
        assertEquals(
                STATE_ENABLING_SATELLITE, mTestSatelliteSessionController.getCurrentStateName());
        mTestSatelliteModemStateCallback.clearModemStates();
    }

    private void moveToPowerOffState() {
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
    }

    private void moveToIdleState() {
        powerOnSatelliteModem();

        // SatelliteSessionController should move to IDLE state after the modem is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteModemStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());
    }

    private void moveIdleToTransferringState() {
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());
    }

    private void moveTransferringToListeningState() {
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        // Sending datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                DATAGRAM_TYPE_UNKNOWN);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
    }

    private void moveToNotConnectedState() {
        powerOnSatelliteModem();
        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);
    }

    private void moveNotConnectedToConnectedState() {
        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);
    }

    private void moveSatelliteToDisablingState() {
        mTestSatelliteSessionController.onSatelliteEnablementStarted(false);
        processAllMessages();

        // SatelliteSessionController should move to DISABLING state
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DISABLING_SATELLITE);
        assertEquals(
                STATE_DISABLING_SATELLITE, mTestSatelliteSessionController.getCurrentStateName());
    }

    private void moveSatelliteToDisablingRequestFailed(int state, String stateName) {
        moveSatelliteToDisablingState();

        // Satellite disabled request failed
        mTestSatelliteSessionController.onSatelliteEnablementFailed(false);
        processAllMessages();

        // Satellite should stay in previous state as satellite disable request failed
        assertSuccessfulModemStateChangedCallback(mTestSatelliteModemStateCallback, state);
        assertEquals(stateName, mTestSatelliteSessionController.getCurrentStateName());
    }

    private static class TestSatelliteModemInterface extends SatelliteModemInterface {
        private final AtomicInteger mListeningEnabledCount = new AtomicInteger(0);
        private final AtomicInteger mListeningDisabledCount = new AtomicInteger(0);
        @SatelliteManager.SatelliteResult
        private int mErrorCode = SatelliteManager.SATELLITE_RESULT_SUCCESS;

        TestSatelliteModemInterface(@NonNull Context context,
                SatelliteController satelliteController, @NonNull Looper looper,
                @NonNull FeatureFlags featureFlags) {
            super(context, satelliteController, looper, featureFlags);
            mExponentialBackoff.stop();
        }

        @Override
        protected void bindService() {
            logd("TestSatelliteModemInterface: bindService");
        }

        @Override
        protected void unbindService() {
            logd("TestSatelliteModemInterface: unbindService");
        }

        @Override
        public void requestSatelliteListeningEnabled(boolean enable, int timeout,
                @Nullable Message message) {
            if (enable) mListeningEnabledCount.incrementAndGet();
            else mListeningDisabledCount.incrementAndGet();
        }

        @Override
        public void enableCellularModemWhileSatelliteModeIsOn(boolean enabled,
                @Nullable Message message) {
            if (message != null) {
                sendMessageWithResult(message, null, mErrorCode);
            }
        }

        public int getListeningEnabledCount() {
            return mListeningEnabledCount.get();
        }

        public int getListeningDisabledCount() {
            return mListeningDisabledCount.get();
        }

        public void setErrorCode(@SatelliteManager.SatelliteResult int errorCode) {
            mErrorCode = errorCode;
        }
    }

    private static class TestSatelliteSessionController extends SatelliteSessionController {
        boolean mSatelliteEnabledForNtnOnlySubscription = true;

        TestSatelliteSessionController(Context context, Looper looper, FeatureFlags featureFlags,
                boolean isSatelliteSupported,
                SatelliteModemInterface satelliteModemInterface) {
            super(context, looper, featureFlags, isSatelliteSupported, satelliteModemInterface);
        }

        String getCurrentStateName() {
            return getCurrentState().getName();
        }

        boolean isSendingTriggeredDuringTransferringState() {
            return mIsSendingTriggeredDuringTransferringState.get();
        }

        boolean isNbIotInactivityTimerStarted() {
            return hasMessages(EVENT_NB_IOT_INACTIVITY_TIMER_TIMED_OUT);
        }

        boolean isEventDeferred(int event) {
            return hasDeferredMessages(event);
        }

        protected boolean isSatelliteEnabledForNtnOnlySubscription() {
            return mSatelliteEnabledForNtnOnlySubscription;
        }

        void setSatelliteEnabledForNtnOnlySubscription(boolean enabled) {
            mSatelliteEnabledForNtnOnlySubscription = false;
        }
    }

    private static class TestSatelliteModemStateCallback extends ISatelliteModemStateCallback.Stub {
        private final AtomicInteger mModemState = new AtomicInteger(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        private final AtomicBoolean mIsEmergency = new AtomicBoolean(false);
        private final Semaphore mSemaphoreForModemStateChanged = new Semaphore(0);
        private final Semaphore mSemaphoreForEmergencyModeChanged = new Semaphore(0);
        private final Object mLock = new Object();
        private final List<Integer> mModemStates = new ArrayList<>();

        @Override
        public void onSatelliteModemStateChanged(int state) {
            logd("onSatelliteModemStateChanged: state=" + state);
            mModemState.set(state);
            synchronized (mLock) {
                mModemStates.add(state);
            }
            try {
                mSemaphoreForModemStateChanged.release();
            } catch (Exception ex) {
                logd("onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }
        }

        @Override
        public void onEmergencyModeChanged(boolean isEmergency) {
            logd("onEmergencyModeChanged: state=" + isEmergency);
            mIsEmergency.set(isEmergency);
            try {
                mSemaphoreForEmergencyModeChanged.release();
            } catch (Exception ex) {
                logd("onEmergencyModeChanged: Got exception, ex=" + ex);
            }
        }

        @Override
        public void onRegistrationFailure(int causeCode) {
            logd("onRegistrationFailure: causeCode=" + causeCode);
        }

        @Override
        public void onTerrestrialNetworkAvailableChanged(boolean isAvailable) {
            logd("onTerrestrialNetworkAvailableChanged: isAvailable=" + isAvailable);
        }

        public boolean waitUntilResultForModemStateChanged() {
            try {
                if (!mSemaphoreForModemStateChanged.tryAcquire(EVENT_PROCESSING_TIME_MILLIS,
                        TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive onSatelliteModemStateChanged");
                    return false;
                }
                return true;
            } catch (Exception ex) {
                logd("onSatelliteModemStateChanged: Got exception=" + ex);
                return false;
            }
        }

        public boolean waitUntilResultForEmergencyModeChanged() {
            try {
                if (!mSemaphoreForEmergencyModeChanged.tryAcquire(EVENT_PROCESSING_TIME_MILLIS,
                        TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive onEmergencyModeChanged");
                    return false;
                }
                return true;
            } catch (Exception ex) {
                logd("onEmergencyModeChanged: Got exception=" + ex);
                return false;
            }
        }

        public int getModemState() {
            return mModemState.get();
        }

        public int getModemState(int index) {
            synchronized (mLock) {
                return mModemStates.get(index);
            }
        }

        public void clearModemStates() {
            synchronized (mLock) {
                mModemStates.clear();
            }
        }

        public int getNumberOfModemStates() {
            synchronized (mLock) {
                return mModemStates.size();
            }
        }

        public boolean getEmergencyMode() {
            return mIsEmergency.get();
        }

        public void clearSemaphorePermits() {
            mSemaphoreForModemStateChanged.drainPermits();
        }
    }

    private static void assertSuccessfulModemStateChangedCallback(
            TestSatelliteModemStateCallback callback,
            @SatelliteManager.SatelliteModemState int expectedModemState) {
        boolean successful = callback.waitUntilResultForModemStateChanged();
        assertTrue(successful);
        assertEquals(expectedModemState, callback.getModemState());
    }

    private static void assertModemStateChangedCallbackNotCalled(
            TestSatelliteModemStateCallback callback) {
        boolean successful = callback.waitUntilResultForModemStateChanged();
        assertFalse(successful);
    }

    private static void assertSuccessfulEmergencyModeChangedCallback(
            TestSatelliteModemStateCallback callback,
            boolean isEmergency) {
        boolean successful = callback.waitUntilResultForEmergencyModeChanged();
        assertTrue(successful);
        assertEquals(isEmergency, callback.getEmergencyMode());
    }

    private static void assertEmergencyModeChangedCallbackNotCalled(
            TestSatelliteModemStateCallback callback) {
        boolean successful = callback.waitUntilResultForEmergencyModeChanged();
        assertFalse(successful);
    }

    private void sendScreenStateChanged(Handler h, int what, boolean screenOn) {
        Message msg = Message.obtain();

        msg.what = what;
        msg.obj = new AsyncResult(null, screenOn, null);
        h.sendMessage(msg);
    }

    private void setUpResponseForRequestSatelliteEnabled(
            @SatelliteManager.SatelliteResult int expectedResult) {
        doAnswer(invocation -> {
            IIntegerConsumer integerConsumer = invocation.getArgument(3);
            integerConsumer.accept(expectedResult);
            return null;
        }).when(mMockSatelliteController).requestSatelliteEnabled(anyBoolean(), anyBoolean(),
                anyBoolean(), any(IIntegerConsumer.class));
    }
}
