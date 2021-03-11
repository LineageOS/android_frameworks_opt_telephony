/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.telephony.ims.RtpHeaderExtension;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Verifies the ability of the {@link RtpTransport} class to successfully encode and decode the
 * device to device communication messages we expect to handle.
 */
@RunWith(Parameterized.class)
public class RtpTransportConversionTest {
    private static class TestParams {
        public Communicator.Message commMessage;
        public RtpHeaderExtension extension;

        public TestParams(Communicator.Message theMessage, RtpHeaderExtension theExtension) {
            commMessage = theMessage;
            extension = theExtension;
        }

        public String toString() {
            return "Params{msg = " + commMessage + ", ext = "
                    + Integer.toBinaryString(extension.getExtensionData()[0]) + "}";
        }
    }

    private static final int CALL_STATE_LOCAL_IDENTIFIER = 1;
    private static final int DEVICE_STATE_LOCAL_IDENTIFIER = 2;

    private RtpTransport mRtpTransport;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private RtpAdapter mRtpAdapter;
    @Mock private Handler mHandler;
    @Mock private TransportProtocol.Callback mCallback;
    @Captor private ArgumentCaptor<Set<RtpHeaderExtension>> mHeaderExtensionCaptor;
    @Captor private ArgumentCaptor<Set<Communicator.Message>> mMessagesCaptor;
    private final TestParams mParams;

    public RtpTransportConversionTest(TestParams params) {
        mParams = params;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRtpTransport = new RtpTransport(mRtpAdapter, mTimeoutsAdapter, mHandler, true /* sdp */);
        mRtpTransport.setCallback(mCallback);

        when(mRtpAdapter.getAcceptedRtpHeaderExtensions()).thenReturn(
                RtpTransportTest.ALL_HEADER_EXTENSION_TYPES);
        mRtpTransport.startNegotiation();
        verify(mCallback).onNegotiationSuccess(any());
        verify(mCallback, never()).onNegotiationFailed(any());
    }

    /**
     * Setup the test cases.
     * @return the test cases
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<TestParams> generateTestCases() {
        List<TestParams> result = new ArrayList<>();

        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_EVS),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00010010})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_WB),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00100010})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_AUDIO_CODEC,
                        Communicator.AUDIO_CODEC_AMR_NB),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00110010})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_LTE),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00010001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_IWLAN),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00100001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_CALL_RADIO_ACCESS_TYPE,
                        Communicator.RADIO_ACCESS_TYPE_NR),
                new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00110001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_LOW),
                new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00000001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_GOOD),
                new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00010001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_BATTERY_STATE,
                        Communicator.BATTERY_STATE_CHARGING),
                new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00110001})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_POOR),
                new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00000010})));
        result.add(new TestParams(
                new Communicator.Message(Communicator.MESSAGE_DEVICE_NETWORK_COVERAGE,
                        Communicator.COVERAGE_GOOD),
                new RtpHeaderExtension(DEVICE_STATE_LOCAL_IDENTIFIER,
                        new byte[]{0b00010010})));
        return result;
    }

    /**
     * Verify generation of RTP header extension data for messages.
     */
    @SmallTest
    @Test
    public void testSendMessages() {
        ArraySet<Communicator.Message> messages = new ArraySet<>();
        messages.add(mParams.commMessage);

        mRtpTransport.sendMessages(messages);
        verify(mRtpAdapter).sendRtpHeaderExtensions(mHeaderExtensionCaptor.capture());
        Set<RtpHeaderExtension> extensions = mHeaderExtensionCaptor.getValue();
        assertEquals(1, extensions.size());
        assertTrue(extensions.contains(mParams.extension));
    }

    /**
     * Verify translation from raw RTP data into message/value pairs.
     */
    @SmallTest
    @Test
    public void testReceiveMessage() {
        ArraySet<RtpHeaderExtension> extension = new ArraySet<>();
        extension.add(mParams.extension);
        mRtpTransport.onRtpHeaderExtensionsReceived(extension);

        verify(mCallback).onMessagesReceived(mMessagesCaptor.capture());
        Set<Communicator.Message> messages = mMessagesCaptor.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.contains(mParams.commMessage));
    }
}
