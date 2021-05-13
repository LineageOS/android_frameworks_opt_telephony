/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.d2d;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import android.telecom.Connection;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class CommunicatorTest {
    private List<TransportProtocol> mTransportProtocols = new ArrayList<>();
    private TransportProtocol.Callback mCallback;
    private Communicator mCommunicator;
    @Mock
    private Communicator.Callback mCommunicatorCallback;
    @Captor
    private ArgumentCaptor<Set<Communicator.Message>> mMessagesCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        TransportProtocol protocol1 = getMockTransportProtocol();
        TransportProtocol protocol2 = getMockTransportProtocol();
        mTransportProtocols.add(protocol1);
        mTransportProtocols.add(protocol2);
    }

    /**
     * Verifies that we can setup the communicator and negotiate a transport.
     */
    @SmallTest
    @Test
    public void testNegotiate() {
        mCommunicator = new Communicator(mTransportProtocols, mCommunicatorCallback);
        mCommunicator.onStateChanged(null, Connection.STATE_ACTIVE);
        verify(mTransportProtocols.get(0)).startNegotiation();
        assertEquals(mTransportProtocols.get(0), mCommunicator.getActiveTransport());
        // Assume negotiation on the first one failed.
        mCallback.onNegotiationFailed(mTransportProtocols.get(0));
        verify(mTransportProtocols.get(1)).startNegotiation();
        assertEquals(mTransportProtocols.get(1), mCommunicator.getActiveTransport());
        mCallback.onNegotiationSuccess(mTransportProtocols.get(1));
        verify(mCommunicatorCallback).onD2DAvailabilitychanged(eq(true));
    }

    /**
     * Verifies that D2D negotiation failed callback is invoked when D2D could not be negotiated.
     */
    @SmallTest
    @Test
    public void testNegotiationFailed() {
        mCommunicator = new Communicator(mTransportProtocols, mCommunicatorCallback);
        mCommunicator.onStateChanged(null, Connection.STATE_ACTIVE);
        verify(mTransportProtocols.get(0)).startNegotiation();
        assertEquals(mTransportProtocols.get(0), mCommunicator.getActiveTransport());
        // Assume negotiation on the first one failed.
        mCallback.onNegotiationFailed(mTransportProtocols.get(0));
        verify(mTransportProtocols.get(1)).startNegotiation();
        assertEquals(mTransportProtocols.get(1), mCommunicator.getActiveTransport());
        // Oops, the second one failed too; not negotiated!
        mCallback.onNegotiationFailed(mTransportProtocols.get(1));
        verify(mCommunicatorCallback).onD2DAvailabilitychanged(eq(false));
    }

    /**
     * Verifies that D2D negotiation failed callback is invoked when no transports are available.
     */
    @SmallTest
    @Test
    public void testNegotiationFailedNoProtocols() {
        mCommunicator = new Communicator(Collections.EMPTY_LIST, mCommunicatorCallback);
        mCommunicator.onStateChanged(null, Connection.STATE_ACTIVE);
        verify(mCommunicatorCallback).onD2DAvailabilitychanged(eq(false));
    }

    /**
     * Verifies that we can relay messages to send via the active transport.
     */
    @SmallTest
    @Test
    public void testSendMessage() {
        testNegotiate();
        TransportProtocol protocol = mCommunicator.getActiveTransport();

        // Send a couple test messages.
        ArraySet<Communicator.Message> test = new ArraySet<>();
        test.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                Communicator.BATTERY_STATE_GOOD));
        test.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_EVS));
        mCommunicator.sendMessages(test);

        // Ensure they got relayed to the transport protocol intact.
        verify(protocol).sendMessages(mMessagesCaptor.capture());
        Set<Communicator.Message> send = mMessagesCaptor.getValue();
        assertEquals(test, send);
    }

    /**
     * Verifies that we can relay messages received via the active transport to interested parties.
     */
    @SmallTest
    @Test
    public void testReceiveMessage() {
        testNegotiate();
        TransportProtocol protocol = mCommunicator.getActiveTransport();

        // Receive some messages
        ArraySet<Communicator.Message> test = new ArraySet<>();
        test.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                Communicator.BATTERY_STATE_GOOD));
        test.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_EVS));
        mCallback.onMessagesReceived(test);

        // Ensure they got relayed to the communicator listener.
        verify(mCommunicatorCallback).onMessagesReceived(mMessagesCaptor.capture());
        Set<Communicator.Message> send = mMessagesCaptor.getValue();
        assertEquals(test, send);
    }


    /**
     * @return a mocked instance of {@link TransportProtocol}.
     */
    private TransportProtocol getMockTransportProtocol() {
        TransportProtocol transportProtocol = Mockito.mock(TransportProtocol.class);
        doNothing().when(transportProtocol).startNegotiation();
        doNothing().when(transportProtocol).sendMessages(any());
        doAnswer(invocation -> {
            mCallback = (TransportProtocol.Callback) invocation.getArgument(0);
            return true;
        }).when(transportProtocol).setCallback(any());
        return transportProtocol;
    }
}
