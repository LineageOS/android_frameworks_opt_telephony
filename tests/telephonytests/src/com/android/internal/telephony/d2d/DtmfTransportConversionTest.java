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

import static org.junit.Assert.assertArrayEquals;

import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.internal.telephony.TestExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Verifies bidirectional conversion between {@link Communicator.Message}s and DTMF character
 * sequences.
 * This class verifies only the conversion between the two and not the actual sending/receipt.  See
 * {@link DtmfTransportTest} for verification of the send/receive of messages using DTMF.
 */
@RunWith(Parameterized.class)
public class DtmfTransportConversionTest {
    private static class TestParams {
        public Communicator.Message commMessage;
        public Pair<String,String> messageAndValueDigits;
        public String fullMessage;

        TestParams(Communicator.Message theMessage, Pair<String,String> theDigits,
                String theFullMessage) {
            commMessage = theMessage;
            messageAndValueDigits = theDigits;
            fullMessage = theFullMessage;
        }

        public String toString() {
            return "Params{msg = " + commMessage + ", digits = " + messageAndValueDigits
                + ", enc=" + fullMessage + "}";
        }
    }

    @Mock
    private DtmfAdapter mDtmfAdapter;
    @Mock
    private TransportProtocol.Callback mCallback;
    @Mock
    private Timeouts.Adapter mTimeouts;
    private TestExecutorService mScheduledExecutorService = new TestExecutorService();

    private final TestParams mParams;
    private DtmfTransport mDtmfTransport;

    public DtmfTransportConversionTest(DtmfTransportConversionTest.TestParams params) {
        mParams = params;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mDtmfTransport = new DtmfTransport(mDtmfAdapter, mTimeouts, mScheduledExecutorService);
        mDtmfTransport.setCallback(mCallback);
    }

    /**
     * Setup the test cases.
     *
     * @return the test cases
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<DtmfTransportConversionTest.TestParams> generateTestCases() {
        List<DtmfTransportConversionTest.TestParams> result = new ArrayList<>();
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE),
                new Pair<>("A", "A"),
                "AADAD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN),
                new Pair<>("A", "B"),
                "AADBD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR),
                new Pair<>("A", "C"),
                "AADCD"
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_EVS),
                new Pair<>("B", "A"),
                "ABDAD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_WB),
                new Pair<>("B", "B"),
                "ABDBD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB),
                new Pair<>("B", "C"),
                "ABDCD"
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW),
                new Pair<>("C", "A"),
                "ACDAD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD),
                new Pair<>("C", "B"),
                "ACDBD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING),
                new Pair<>("C", "C"),
                "ACDCD"
        ));

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD),
                new Pair<>("AA", "A"),
                "AAADAD"
        ));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR),
                new Pair<>("AA", "B"),
                "AAADBD"
        ));

        return result;
    }

    /**
     * Verify generation of DTMF digits for messages.
     */
    @SmallTest
    @Test
    public void testMessageToDtmf() {
        char[] dtmfSequence = mDtmfTransport.getMessageDigits(mParams.commMessage);
        assertArrayEquals(mParams.fullMessage.toCharArray(), dtmfSequence);
    }

    /**
     * Verify generation of messages from DTMF digits
     */
    @SmallTest
    @Test
    public void testDtmfToMessage() {
        Communicator.Message message = mDtmfTransport.extractMessage(
                mParams.messageAndValueDigits.first, mParams.messageAndValueDigits.second);
        assertEquals(mParams.commMessage, message);
    }
}
