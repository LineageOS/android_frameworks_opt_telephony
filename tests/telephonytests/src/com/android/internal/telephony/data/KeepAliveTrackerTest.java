/*
 * Copyright 2022 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.InetAddresses;
import android.net.KeepalivePacketData;
import android.net.NattKeepalivePacketData;
import android.net.NetworkAgent;
import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.TelephonyNetworkAgent.TelephonyNetworkAgentCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class KeepAliveTrackerTest extends TelephonyTest {

    private KeepaliveTracker mKeepaliveTrackerUT;

    // Mocked classes
    private DataNetwork mMockedDataNetwork;
    private TelephonyNetworkAgent mMockedTelephonyNetworkAgent;

    private TelephonyNetworkAgentCallback mTelephonyNetworkAgentCallback;

    @Before
    public void setUp() throws Exception {
        logd("KeepAliveTrackerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mMockedDataNetwork = mock(DataNetwork.class);
        mMockedTelephonyNetworkAgent = mock(TelephonyNetworkAgent.class);
        replaceInstance(NetworkAgent.class, "mPreConnectedQueue",
                mMockedTelephonyNetworkAgent, new ArrayList<>());
        doReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                .when(mMockedDataNetwork).getTransport();
        mKeepaliveTrackerUT = new KeepaliveTracker(mPhone, Looper.myLooper(), mMockedDataNetwork,
                mMockedTelephonyNetworkAgent);
        ArgumentCaptor<TelephonyNetworkAgentCallback> callbackCaptor =
                ArgumentCaptor.forClass(TelephonyNetworkAgentCallback.class);
        verify(mMockedTelephonyNetworkAgent).registerCallback(callbackCaptor.capture());
        mTelephonyNetworkAgentCallback = callbackCaptor.getValue();
        logd("KeepAliveTrackerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mKeepaliveTrackerUT = null;
        mTelephonyNetworkAgentCallback = null;
        super.tearDown();
    }

    private void checkStartStopNattKeepalive(boolean useCondensedFlow) throws Exception {
        final int sessionHandle = 0xF00;
        final int slotId = 3;
        final int interval = 10; // seconds
        // Construct a new KeepalivePacketData request as we would receive from a Network Agent,
        // and check that the packet is sent to the RIL.
        KeepalivePacketData kd = NattKeepalivePacketData.nattKeepalivePacket(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                1234,
                InetAddresses.parseNumericAddress("8.8.8.8"),
                4500);
        mTelephonyNetworkAgentCallback.onStartSocketKeepalive(slotId, Duration.ofSeconds(interval),
                kd);
        processAllMessages();
        verify(mSimulatedCommandsVerifier, times(1))
                .startNattKeepalive(anyInt(), eq(kd), eq((int) TimeUnit.SECONDS.toMillis(interval)),
                        any(Message.class));

        Message kaStarted = mKeepaliveTrackerUT.obtainMessage(1 /*EVENT_KEEPALIVE_STARTED*/,
                slotId, 0);
        processAllMessages();
        if (useCondensedFlow) {
            // Send a singled condensed response that a keepalive have been requested and the
            // activation is completed. This flow should be used if the keepalive offload request
            // is handled by a high-priority signalling path.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_ACTIVE), null);
            kaStarted.sendToTarget();
        } else {
            // Send the sequential responses indicating first that the request was received and
            // then that the keepalive is running. This should create an active record of the
            // keepalive in keepalive tracker while permitting the status from a low priority or
            // other high-latency handler to activate the keepalive without blocking a request.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_PENDING), null);
            kaStarted.sendToTarget();
            Message kaRunning = mKeepaliveTrackerUT.obtainMessage(3 /*EVENT_KEEPALIVE_STATUS*/);
            AsyncResult.forMessage(
                    kaRunning, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_ACTIVE), null);
            kaRunning.sendToTarget();
        }
        processAllMessages();

        // Verify that we can stop the connection, which checks that the record in keepalive tracker
        // has a valid mapping between slotId (from network agent) to sessionHandle (from Radio).
        mTelephonyNetworkAgentCallback.onStopSocketKeepalive(slotId);
        processAllMessages();
        verify(mSimulatedCommandsVerifier, times(1))
                .stopNattKeepalive(eq(sessionHandle), any(Message.class));

        Message kaStopped = mKeepaliveTrackerUT.obtainMessage(2 /*EVENT_KEEPALIVE_STOPPED*/,
                sessionHandle, slotId);
        AsyncResult.forMessage(kaStopped);
        kaStopped.sendToTarget();
        processAllMessages();
        // Verify that after the connection is stopped, the mapping for a Keepalive Session is
        // removed. Thus, subsequent calls to stop the same keepalive are ignored.
        mTelephonyNetworkAgentCallback.onStopSocketKeepalive(slotId);
        processAllMessages();
        // Check that the mock has not been called subsequent to the previous invocation
        // while avoiding the use of reset()
        verify(mSimulatedCommandsVerifier, times(1))
                .stopNattKeepalive(anyInt(), any(Message.class));
    }

    @Test
    public void testStartStopNattKeepalive() throws Exception {
        checkStartStopNattKeepalive(false);
    }

    @Test
    public void testStartStopNattKeepaliveCondensed() throws Exception {
        checkStartStopNattKeepalive(true);
    }

    private void checkStartNattKeepaliveFail(boolean useCondensedFlow) throws Exception {
        final int sessionHandle = 0xF00;
        final int slotId = 3;
        final int interval = 10; // seconds
        // Construct a new KeepalivePacketData request as we would receive from a Network Agent,
        // and check that the packet is sent to the RIL.
        KeepalivePacketData kd = NattKeepalivePacketData.nattKeepalivePacket(
                InetAddresses.parseNumericAddress("1.2.3.4"),
                1234,
                InetAddresses.parseNumericAddress("8.8.8.8"),
                4500);
        mTelephonyNetworkAgentCallback.onStartSocketKeepalive(slotId, Duration.ofSeconds(interval),
                kd);
        processAllMessages();
        verify(mSimulatedCommandsVerifier, times(1))
                .startNattKeepalive(anyInt(), eq(kd), eq(interval * 1000), any(Message.class));

        Message kaStarted = mKeepaliveTrackerUT.obtainMessage(1 /*EVENT_KEEPALIVE_STARTED*/,
                slotId, 0);
        processAllMessages();
        if (useCondensedFlow) {
            // Indicate in the response that the keepalive has failed.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(KeepaliveStatus.ERROR_UNSUPPORTED),
                    null);
            kaStarted.sendToTarget();
        } else {
            // Indicate that the keepalive is queued, and then signal a failure from the modem
            // such that a pending keepalive fails to activate.
            AsyncResult.forMessage(
                    kaStarted, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_PENDING), null);
            kaStarted.sendToTarget();
            Message kaRunning = mKeepaliveTrackerUT.obtainMessage(3 /*EVENT_KEEPALIVE_STATUS*/);
            AsyncResult.forMessage(
                    kaRunning, new KeepaliveStatus(
                            sessionHandle, KeepaliveStatus.STATUS_INACTIVE), null);
            kaRunning.sendToTarget();
        }
        processAllMessages();
        // Verify that a failed connection request cannot be stopped due to no record in
        // the keepalive tracker.
        mTelephonyNetworkAgentCallback.onStopSocketKeepalive(slotId);
        processAllMessages();
        verify(mSimulatedCommandsVerifier, never()).stopNattKeepalive(anyInt(), any(Message.class));
    }

    @Test
    public void testStartNattKeepaliveFail() throws Exception {
        checkStartNattKeepaliveFail(false);
    }

    @Test
    public void testStartNattKeepaliveFailCondensed() throws Exception {
        checkStartNattKeepaliveFail(true);
    }
}
