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

import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.os.Message;
import android.telephony.satellite.ISatelliteStateCallback;
import android.telephony.satellite.SatelliteManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private static final String STATE_IDLE = "IdleState";
    private static final String STATE_TRANSFERRING = "TransferringState";
    private static final String STATE_LISTENING = "ListeningState";
    private static final String STATE_NOT_CONNECTED = "NotConnectedState";
    private static final String STATE_CONNECTED = "ConnectedState";

    private TestSatelliteModemInterface mSatelliteModemInterface;
    private TestSatelliteSessionController mTestSatelliteSessionController;
    private TestSatelliteStateCallback mTestSatelliteStateCallback;

    @Mock private SatelliteController mMockSatelliteController;
    @Mock private DatagramReceiver mMockDatagramReceiver;
    @Mock private DatagramDispatcher mMockDatagramDispatcher;
    @Mock private DatagramController mMockDatagramController;

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

        Resources resources = mContext.getResources();
        when(resources.getInteger(anyInt())).thenReturn(TEST_SATELLITE_TIMEOUT_MILLIS);

        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(false);
        mSatelliteModemInterface = new TestSatelliteModemInterface(
                mContext, mMockSatelliteController, Looper.myLooper());
        mTestSatelliteSessionController = new TestSatelliteSessionController(mContext,
                Looper.myLooper(), true, mSatelliteModemInterface);
        processAllMessages();

        mTestSatelliteStateCallback = new TestSatelliteStateCallback();
        mTestSatelliteSessionController.registerForSatelliteModemStateChanged(
                mTestSatelliteStateCallback);
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
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
                mContext, Looper.myLooper(), false, mSatelliteModemInterface);
        assertNotNull(sessionController1);
        processAllMessages();
        assertEquals(STATE_UNAVAILABLE, sessionController1.getCurrentStateName());

        /**
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        TestSatelliteSessionController sessionController2 = new TestSatelliteSessionController(
                mContext, Looper.myLooper(), true, mSatelliteModemInterface);
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
                mContext, Looper.myLooper(), false, mSatelliteModemInterface);
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
    public void testStateTransition() {
        /**
         * Since satellite is supported, SatelliteSessionController should move to POWER_OFF state.
         */
        assertNotNull(mTestSatelliteSessionController);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state after the modem is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move back to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state after radio is turned on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams failed
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(1, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(1, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(2, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(2, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to LISTENING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);
        assertEquals(STATE_LISTENING, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(3, mSatelliteModemInterface.getListeningEnabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state after timeout
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertEquals(3, mSatelliteModemInterface.getListeningDisabledCount());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Receiving datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVE_FAILED);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state instead of moving to IDLE
        // state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Start receiving datagrams again.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Sending datagrams failed.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should stay at TRANSFERRING state instead of moving to IDLE
        // state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isSendingTriggeredDuringTransferringState());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
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

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();
        // SatelliteSessionController should stay at NOT_CONNECTED state.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        setupDatagramTransferringState(true);

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move back to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        clearInvocations(mMockDatagramController);

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after radio is turned on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // The datagram sending event should be ignored.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Sending datagrams failed
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams again
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Sending datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Start receiving datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        processAllMessages();

        // SatelliteSessionController should move to TRANSFERRING state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        assertEquals(STATE_TRANSFERRING, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
        clearInvocations(mMockDatagramController);

        // Receiving datagrams is successful and done.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
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
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
        assertFalse(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        clearInvocations(mMockDatagramController);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
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
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is disconnected from the satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF state.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        clearInvocations(mMockDatagramController);

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());
        verify(mMockDatagramController).onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        clearInvocations(mMockDatagramController);

        // Satellite modem is connected to a satellite network.
        mTestSatelliteSessionController.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        processAllMessages();

        // SatelliteSessionController should move to CONNECTED state
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
        assertEquals(STATE_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Wait for timeout
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        // Set up error response for the request to disable cellular scanning
        mSatelliteModemInterface.setErrorCode(SatelliteManager.SATELLITE_RESULT_MODEM_ERROR);

        // Start sending datagrams
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();

        // SatelliteSessionController should stay at IDLE state because it failed to disable
        // cellular scanning.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        mSatelliteModemInterface.setErrorCode(SatelliteManager.SATELLITE_RESULT_SUCCESS);

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state because NB-IOT inactivity timer has
        // timed out.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());

        // Power off the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(false);
        processAllMessages();

        // SatelliteSessionController should move to POWER_OFF
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        assertEquals(STATE_POWER_OFF, mTestSatelliteSessionController.getCurrentStateName());

        // Power on the modem.
        mTestSatelliteSessionController.onSatelliteEnabledStateChanged(true);
        processAllMessages();

        // SatelliteSessionController should move to NOT_CONNECTED state after the satellite modem
        // is powered on.
        assertSuccessfulModemStateChangedCallback(
                mTestSatelliteStateCallback, SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Start sending datagrams and the NB-IOT inactivity timer should be stopped.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_WAITING_TO_CONNECT,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should stay at NOT_CONNECTED state because.
        assertModemStateChangedCallbackNotCalled(mTestSatelliteStateCallback);
        assertEquals(STATE_NOT_CONNECTED, mTestSatelliteSessionController.getCurrentStateName());

        // Transferring datagram failed because satellite failed to connect to a satellite network.
        // The NB-IOT inactivity timer should be started.
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_SEND_FAILED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        mTestSatelliteSessionController.onDatagramTransferStateChanged(
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        processAllMessages();
        assertTrue(mTestSatelliteSessionController.isNbIotInactivityTimerStarted());

        moveTimeForward(TEST_SATELLITE_TIMEOUT_MILLIS);
        processAllMessages();

        // SatelliteSessionController should move to IDLE state because NB-IOT inactivity timer has
        // timed out.
        assertSuccessfulModemStateChangedCallback(mTestSatelliteStateCallback,
                SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
        assertEquals(STATE_IDLE, mTestSatelliteSessionController.getCurrentStateName());
    }

    private void setupDatagramTransferringState(boolean isTransferring) {
        when(mMockDatagramController.isSendingInIdleState()).thenReturn(isTransferring);
        when(mMockDatagramController.isPollingInIdleState()).thenReturn(isTransferring);
    }

    private static class TestSatelliteModemInterface extends SatelliteModemInterface {
        private final AtomicInteger mListeningEnabledCount = new AtomicInteger(0);
        private final AtomicInteger mListeningDisabledCount = new AtomicInteger(0);
        @SatelliteManager.SatelliteResult
        private int mErrorCode = SatelliteManager.SATELLITE_RESULT_SUCCESS;

        TestSatelliteModemInterface(@NonNull Context context,
                SatelliteController satelliteController, @NonNull Looper looper) {
            super(context, satelliteController, looper);
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
        TestSatelliteSessionController(Context context, Looper looper, boolean isSatelliteSupported,
                SatelliteModemInterface satelliteModemInterface) {
            super(context, looper, isSatelliteSupported, satelliteModemInterface);
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
    }

    private static class TestSatelliteStateCallback extends ISatelliteStateCallback.Stub {
        private final AtomicInteger mModemState = new AtomicInteger(
                SatelliteManager.SATELLITE_MODEM_STATE_OFF);
        private final Semaphore mSemaphore = new Semaphore(0);

        @Override
        public void onSatelliteModemStateChanged(int state) {
            logd("onSatelliteModemStateChanged: state=" + state);
            mModemState.set(state);
            try {
                mSemaphore.release();
            } catch (Exception ex) {
                logd("onSatelliteModemStateChanged: Got exception, ex=" + ex);
            }
        }

        public boolean waitUntilResult() {
            try {
                if (!mSemaphore.tryAcquire(EVENT_PROCESSING_TIME_MILLIS, TimeUnit.MILLISECONDS)) {
                    logd("Timeout to receive onSatelliteModemStateChanged");
                    return false;
                }
                return true;
            } catch (Exception ex) {
                logd("onSatelliteModemStateChanged: Got exception=" + ex);
                return false;
            }
        }

        public int getModemState() {
            return mModemState.get();
        }
    }

    private static void assertSuccessfulModemStateChangedCallback(
            TestSatelliteStateCallback callback,
            @SatelliteManager.SatelliteModemState int expectedModemState) {
        boolean successful = callback.waitUntilResult();
        assertTrue(successful);
        assertEquals(expectedModemState, callback.getModemState());
    }

    private static void assertModemStateChangedCallbackNotCalled(
            TestSatelliteStateCallback callback) {
        boolean successful = callback.waitUntilResult();
        assertFalse(successful);
    }
}
