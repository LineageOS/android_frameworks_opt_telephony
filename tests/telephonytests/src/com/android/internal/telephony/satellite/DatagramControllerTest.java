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

package com.android.internal.telephony.satellite;

import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_KEEP_ALIVE;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_LOCATION_SHARING;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE;
import static android.telephony.satellite.SatelliteManager.DATAGRAM_TYPE_UNKNOWN;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING;
import static android.telephony.satellite.SatelliteManager.SATELLITE_RESULT_SUCCESS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Looper;
import android.telephony.satellite.SatelliteDatagram;
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

import java.util.function.Consumer;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DatagramControllerTest extends TelephonyTest {
    private static final String TAG = "DatagramControllerTest";

    private DatagramController mDatagramControllerUT;

    @Mock private DatagramReceiver mMockDatagramReceiver;
    @Mock private DatagramDispatcher mMockDatagramDispatcher;
    @Mock private PointingAppController mMockPointingAppController;
    @Mock private SatelliteSessionController mMockSatelliteSessionController;
    @Mock private SatelliteController mMockSatelliteController;

    private static final int SUB_ID = 0;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        MockitoAnnotations.initMocks(this);
        logd(TAG + " Setup!");

        replaceInstance(DatagramDispatcher.class, "sInstance", null,
                mMockDatagramDispatcher);
        replaceInstance(DatagramReceiver.class, "sInstance", null,
                mMockDatagramReceiver);
        replaceInstance(SatelliteController.class, "sInstance", null,
                mMockSatelliteController);
        replaceInstance(SatelliteSessionController.class, "sInstance", null,
                mMockSatelliteSessionController);
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);
        mDatagramControllerUT = new DatagramController(
                mContext, Looper.myLooper(), mMockPointingAppController);

        // Move both send and receive to IDLE state
        mDatagramControllerUT.updateSendStatus(SUB_ID, DATAGRAM_TYPE_UNKNOWN,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, 0, SATELLITE_RESULT_SUCCESS);
        mDatagramControllerUT.updateReceiveStatus(SUB_ID, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE, 0,
                SATELLITE_RESULT_SUCCESS);
        pushDemoModeSosDatagram(DATAGRAM_TYPE_SOS_MESSAGE);
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG + " tearDown");
        super.tearDown();
    }

    @Test
    public void testUpdateSendStatus() throws Exception {
        testUpdateSendStatus(true, DATAGRAM_TYPE_SOS_MESSAGE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
        testUpdateSendStatus(true, DATAGRAM_TYPE_LOCATION_SHARING,
                SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        testUpdateSendStatus(false, DATAGRAM_TYPE_KEEP_ALIVE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
        pushDemoModeSosDatagram(DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP);
        testUpdateSendStatus(true, DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP,
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
        pushDemoModeSosDatagram(DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED);
        testUpdateSendStatus(true, DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED,
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING);
    }

    @Test
    public void testUpdateReceiveStatus() throws Exception {
        testUpdateReceiveStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
        testUpdateReceiveStatus(true, SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE);
        testUpdateReceiveStatus(false, SATELLITE_DATAGRAM_TRANSFER_STATE_RECEIVING);
    }

    @Test
    public void testSetDeviceAlignedWithSatellite() throws Exception {
        testSetDeviceAlignedWithSatellite(true);
        testSetDeviceAlignedWithSatellite(false);
    }

    @Test
    public void testSuppressSendStatusUpdate() throws Exception {
        // Move to NOT_CONNECTED state
        mDatagramControllerUT.onSatelliteModemStateChanged(
                SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);

        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockPointingAppController);
        clearInvocations(mMockDatagramReceiver);

        int sendPendingCount = 1;
        int errorCode = SATELLITE_RESULT_SUCCESS;
        mDatagramControllerUT.updateSendStatus(SUB_ID, DATAGRAM_TYPE_KEEP_ALIVE,
                SATELLITE_DATAGRAM_TRANSFER_STATE_SENDING, sendPendingCount, errorCode);
        verifyZeroInteractions(mMockSatelliteSessionController);
        verifyZeroInteractions(mMockPointingAppController);
        verifyZeroInteractions(mMockDatagramReceiver);
    }

    @Test
    public void testNeedsWaitingForSatelliteConnected() throws Exception {
        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(false);
        assertFalse(mDatagramControllerUT
                .needsWaitingForSatelliteConnected(DATAGRAM_TYPE_KEEP_ALIVE));

        when(mMockSatelliteController.isSatelliteAttachRequired()).thenReturn(true);

        int[] sosDatagramTypes = {DATAGRAM_TYPE_SOS_MESSAGE,
                DATAGRAM_TYPE_LAST_SOS_MESSAGE_STILL_NEED_HELP,
                DATAGRAM_TYPE_LAST_SOS_MESSAGE_NO_HELP_NEEDED};
        for (int datagramType : sosDatagramTypes) {
            mDatagramControllerUT.onSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_NOT_CONNECTED);
            assertFalse(mDatagramControllerUT
                    .needsWaitingForSatelliteConnected(DATAGRAM_TYPE_KEEP_ALIVE));
            assertTrue(mDatagramControllerUT
                    .needsWaitingForSatelliteConnected(datagramType));

            mDatagramControllerUT.onSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_CONNECTED);
            assertFalse(mDatagramControllerUT
                    .needsWaitingForSatelliteConnected(datagramType));

            mDatagramControllerUT.onSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING);
            assertFalse(mDatagramControllerUT
                    .needsWaitingForSatelliteConnected(datagramType));

            mDatagramControllerUT.onSatelliteModemStateChanged(
                    SatelliteManager.SATELLITE_MODEM_STATE_IDLE);
            assertTrue(mDatagramControllerUT
                    .needsWaitingForSatelliteConnected(datagramType));
        }
    }

    private void testUpdateSendStatus(boolean isDemoMode, int datagramType, int sendState) {
        mDatagramControllerUT.setDemoMode(isDemoMode);
        clearAllInvocations();

        int sendPendingCount = 1;
        int errorCode = SATELLITE_RESULT_SUCCESS;
        mDatagramControllerUT.updateSendStatus(SUB_ID, datagramType, sendState, sendPendingCount,
                errorCode);

        verify(mMockSatelliteSessionController)
                .onDatagramTransferStateChanged(eq(sendState), anyInt());
        verify(mMockPointingAppController).updateSendDatagramTransferState(
                eq(SUB_ID), eq(datagramType), eq(sendState), eq(sendPendingCount), eq(errorCode));

        if (isDemoMode) {
            if (sendState == SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE) {
                verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                        anyInt(), any(Consumer.class));
            }
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void testUpdateReceiveStatus(boolean isDemoMode, int receiveState) {
        mDatagramControllerUT.setDemoMode(isDemoMode);
        clearAllInvocations();

        int receivePendingCount = 1;
        int errorCode = SATELLITE_RESULT_SUCCESS;
        mDatagramControllerUT.updateReceiveStatus(
                SUB_ID, receiveState, receivePendingCount, errorCode);

        verify(mMockSatelliteSessionController)
                .onDatagramTransferStateChanged(anyInt(), eq(receiveState));
        verify(mMockPointingAppController).updateReceiveDatagramTransferState(
                eq(SUB_ID), eq(receiveState), eq(receivePendingCount), eq(errorCode));

        if (isDemoMode && receiveState == SATELLITE_DATAGRAM_TRANSFER_STATE_IDLE) {
            verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void testSetDeviceAlignedWithSatellite(boolean isAligned) {
        mDatagramControllerUT.setDemoMode(true);
        clearAllInvocations();

        mDatagramControllerUT.setDeviceAlignedWithSatellite(isAligned);
        verify(mMockDatagramDispatcher).setDeviceAlignedWithSatellite(eq(isAligned));
        verify(mMockDatagramReceiver).setDeviceAlignedWithSatellite(eq(isAligned));
        if (isAligned) {
            verify(mMockDatagramReceiver).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        } else {
            verify(mMockDatagramReceiver, never()).pollPendingSatelliteDatagrams(
                    anyInt(), any(Consumer.class));
        }
    }

    private void clearAllInvocations() {
        clearInvocations(mMockSatelliteSessionController);
        clearInvocations(mMockPointingAppController);
        clearInvocations(mMockDatagramReceiver);
        clearInvocations(mMockDatagramDispatcher);
    }

    private void pushDemoModeSosDatagram(int datagramType) {
        String testMessage = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(testMessage.getBytes());
        mDatagramControllerUT.setDemoMode(true);
        mDatagramControllerUT.pushDemoModeDatagram(datagramType, datagram);
    }
}
