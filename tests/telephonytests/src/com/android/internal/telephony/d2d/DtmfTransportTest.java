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
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.TestExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class DtmfTransportTest {
    private static final long DIGIT_INTERVAL_MILLIS = 10L;
    private static final long MSG_TIMEOUT_MILLIS = 1000L;
    private static final long NEGOTIATION_TIMEOUT_MILLIS = 2000L;
    private static final String EXPECTED_PROBE = "AAD";

    @Mock
    private DtmfAdapter mDtmfAdapter;
    @Mock
    private TransportProtocol.Callback mCallback;
    @Mock
    private Timeouts.Adapter mTimeouts;
    @Captor
    private ArgumentCaptor<Set<Communicator.Message>> mMessagesCaptor;
    @Captor
    private ArgumentCaptor<Character> mDigitsCaptor;

    private TestExecutorService mTestExecutorService = new TestExecutorService();
    private DtmfTransport mDtmfTransport;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mTimeouts.getDtmfMinimumIntervalMillis()).thenReturn(DIGIT_INTERVAL_MILLIS);
        when(mTimeouts.getMaxDurationOfDtmfMessageMillis()).thenReturn(MSG_TIMEOUT_MILLIS);
        when(mTimeouts.getDtmfNegotiationTimeoutMillis()).thenReturn(NEGOTIATION_TIMEOUT_MILLIS);
        when(mTimeouts.getDtmfDurationFuzzMillis()).thenReturn(0L);
        mDtmfTransport = new DtmfTransport(mDtmfAdapter, mTimeouts,
                mTestExecutorService /* Executors.newSingleThreadScheduledExecutor() in prod. */);
        mDtmfTransport.setCallback(mCallback);
    }

    /**
     * Verify starting state when newly initialized.
     */
    @SmallTest
    @Test
    public void testIdle() {
        assertEquals(DtmfTransport.STATE_IDLE, mDtmfTransport.getTransportState());
    }

    /**
     * Verify negotiation start
     */
    @SmallTest
    @Test
    public void testStartNegotiation() {
        mDtmfTransport.startNegotiation();
        assertEquals(DtmfTransport.STATE_NEGOTIATING, mDtmfTransport.getTransportState());

        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);

        verify(mDtmfAdapter, times(3)).sendDtmf(mDigitsCaptor.capture());
        String probeString = mDigitsCaptor.getAllValues().stream()
                .map(c -> String.valueOf(c)).collect(Collectors.joining());
        assertEquals(EXPECTED_PROBE, probeString);
    }

    /**
     * Verify negotiation fails due to lack of received digit prior to timeout.
     */
    @SmallTest
    @Test
    public void testNegotiationFailsDueToTimeout() {
        mDtmfTransport.startNegotiation();

        mTestExecutorService.advanceTime(NEGOTIATION_TIMEOUT_MILLIS);

        verify(mCallback).onNegotiationFailed(eq(mDtmfTransport));
        assertEquals(DtmfTransport.STATE_NEGOTIATION_FAILED, mDtmfTransport.getTransportState());
    }

    /**
     * Verify negotiation failed due to invalid response
     */
    @SmallTest
    @Test
    public void testNegotiationFailedInvalidResponse() {
        testStartNegotiation();

        // Received something other than the probe; it should be ignored
        mDtmfTransport.onDtmfReceived('1');
        // Super short message.
        mDtmfTransport.onDtmfReceived('A');
        mDtmfTransport.onDtmfReceived('D');

        verify(mCallback).onNegotiationFailed(eq(mDtmfTransport));
    }

    /**
     * Verify negotiation completed
     */
    @SmallTest
    @Test
    public void testNegotiationSuccess() {
        testStartNegotiation();

        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);

        verify(mCallback).onNegotiationSuccess(eq(mDtmfTransport));
        assertEquals(DtmfTransport.STATE_NEGOTIATED, mDtmfTransport.getTransportState());
    }

    /**
     * Verify negotiation completed and that we don't subsequently cancel the negotiation
     */
    @SmallTest
    @Test
    public void testNegotiationSuccessAndDoesNotTimeOut() {
        testNegotiationSuccess();

        mTestExecutorService.advanceTime(NEGOTIATION_TIMEOUT_MILLIS);

        // Even though we timeout period has passed, we should NOT have failed negotiation and the
        // state should remain negotiated.
        verify(mCallback, never()).onNegotiationFailed(eq(mDtmfTransport));
        assertEquals(DtmfTransport.STATE_NEGOTIATED, mDtmfTransport.getTransportState());
    }

    /**
     * Verify receipt of a single message within the overall {@link #MSG_TIMEOUT_MILLIS} message
     * window.
     */
    @SmallTest
    @Test
    public void testReceiveSuccess() {
        testNegotiationSuccess();

        // Receive message with typical digit spacing
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('C');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);

        verify(mCallback).onMessagesReceived(mMessagesCaptor.capture());
        assertEquals(1, mMessagesCaptor.getValue().size());
        assertTrue(mMessagesCaptor.getValue().contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB)));
    }

    /**
     * Verify invalid message start digits are ignored.
     */
    @SmallTest
    @Test
    public void testReceiveInvalidMessageStart() {
        testNegotiationSuccess();

        // Receive message with invalid start digit; it should be ignored.
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        // Receive random 0-9 digits user might have typed; should be ignored.
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('1');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('2');

        // Back to regularly scheduled message.
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('C');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS);

        verify(mCallback, times(1)).onMessagesReceived(mMessagesCaptor.capture());
        assertEquals(1, mMessagesCaptor.getAllValues().get(0).size());
        assertTrue(mMessagesCaptor.getAllValues().get(0).contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB)));
    }

    /**
     * Verify invalid messages
     */
    @SmallTest
    @Test
    public void testReceiveInvalidMessage() {
        testNegotiationSuccess();

        // Garbage message with no actual values.
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');

        // An unknown message!
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');

        verify(mCallback, never()).onMessagesReceived(mMessagesCaptor.capture());
    }

    /**
     * Verify receipt of two messages back to back..
     */
    @SmallTest
    @Test
    public void testReceiveMultipleSuccess() {
        testNegotiationSuccess();

        // Receive message with typical digit spacing
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('C');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS);

        verify(mCallback, times(1)).onMessagesReceived(mMessagesCaptor.capture());
        assertEquals(1, mMessagesCaptor.getAllValues().get(0).size());
        assertTrue(mMessagesCaptor.getAllValues().get(0).contains(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB)));

        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('C');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);

        // Note: Reusing the captor here appends all call arguments on to mMessagesCaptor, so
        // we need to look at index 2 in getAllValues.
        verify(mCallback, times(2)).onMessagesReceived(mMessagesCaptor.capture());
        assertEquals(1, mMessagesCaptor.getAllValues().get(2).size());
        assertTrue(mMessagesCaptor.getAllValues().get(2).contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW)));
    }

    /**
     * Verify operation of timeout when receiving a first message, followed by successful receipt of
     * a second message.
     */
    @SmallTest
    @Test
    public void testReceiveTimeout() {
        testNegotiationSuccess();

        // Receive a partial first message.
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('B');
        // Timeout
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS);

        // Receive second message.
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('C');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('A');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);
        mDtmfTransport.onDtmfReceived('D');
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS);

        // First message should not be present, but the second one should be.
        verify(mCallback, times(1)).onMessagesReceived(mMessagesCaptor.capture());
        assertTrue(mMessagesCaptor.getValue().contains(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW)));
    }

    /**
     * Basic test of sending a message using the DTMF transport.
     * See {@link DtmfTransportConversionTest} for tests that confirm the translation between
     * messages and DTMF sequences.
     * This test verifies that trans of DMTF digits are sent in groups separated by the required
     * inter-message delay and that digits are separated by the inter-digit delay.
     */
    @SmallTest
    @Test
    public void testSendSuccess() {
        testNegotiationSuccess();

        Set<Communicator.Message> messages = new ArraySet<>();
        messages.add(new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                Communicator.AUDIO_CODEC_AMR_NB));
        messages.add(new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                Communicator.COVERAGE_POOR));
        mDtmfTransport.sendMessages(messages);

        // Advance the "clock" by the DTMF interval to cause the scheduler to run the scheduled
        // repeating task which sends the digits.
        // First digit train; sequences are separated by the longer timeout.
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS); // A message start
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // B audio codec
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // D message type terminator
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // C AMR NB value
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // D message value terminator

        // Second digit train; longer due to longer message type.
        mTestExecutorService.advanceTime(MSG_TIMEOUT_MILLIS); // A message start
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // A coverage
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // A coverage
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // D message type terminator
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // B poor value
        mTestExecutorService.advanceTime(DIGIT_INTERVAL_MILLIS); // D message value terminator

        ArgumentCaptor<Character> captor = ArgumentCaptor.forClass(Character.class);
        // Extra invocations are from negotiation
        verify(mDtmfAdapter, times(14)).sendDtmf(captor.capture());

        // Expected digits includes initial probe send.
        String expectedDigits = "AADABDCDAAADBD";
        String actualDigits = captor.getAllValues().stream()
                .map( c-> String.valueOf(c) )
                .collect(Collectors.joining());
        assertEquals(expectedDigits, actualDigits);
    }
}
