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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.ims.RtpHeaderExtensionType;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;


import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class RtpTransportTest {
    private static final int CALL_STATE_LOCAL_IDENTIFIER = 1;
    private static final int DEVICE_STATE_LOCAL_IDENTIFIER = 2;

    public static final ArraySet<RtpHeaderExtensionType> ALL_HEADER_EXTENSION_TYPES =
            new ArraySet<>();
    static {
        ALL_HEADER_EXTENSION_TYPES.add(new RtpHeaderExtensionType(CALL_STATE_LOCAL_IDENTIFIER,
                RtpTransport.CALL_STATE_RTP_HEADER_EXTENSION));
        ALL_HEADER_EXTENSION_TYPES.add(new RtpHeaderExtensionType(DEVICE_STATE_LOCAL_IDENTIFIER,
                RtpTransport.DEVICE_STATE_RTP_HEADER_EXTENSION));
    }
    private static final ArraySet<RtpHeaderExtensionType> NO_SUPPORTED_HEADER_EXTENSION_TYPES =
            new ArraySet<>();

    private RtpTransport mRtpTransport;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private RtpAdapter mRtpAdapter;
    @Mock private Handler mHandler;
    @Mock private TransportProtocol.Callback mCallback;
    @Captor private ArgumentCaptor<Set<RtpHeaderExtension>> mHeaderExtensionCaptor;
    @Captor private ArgumentCaptor<Set<Communicator.Message>> mMessagesCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRtpTransport = new RtpTransport(mRtpAdapter, mTimeoutsAdapter, mHandler, true /* sdp */);
        mRtpTransport.setCallback(mCallback);
    }

    /**
     * Nominal case; assume the remote side accepted all RTP header extension types we need for D2D
     * communications.  The should get an instant negotiation success.
     */
    @SmallTest
    @Test
    public void testAllHeaderExtensionsSupported() {
        when(mRtpAdapter.getAcceptedRtpHeaderExtensions()).thenReturn(ALL_HEADER_EXTENSION_TYPES);
        mRtpTransport.startNegotiation();
        verify(mCallback).onNegotiationSuccess(any());
        verify(mCallback, never()).onNegotiationFailed(any());
    }

    /**
     * Verify the case where the RTP header extensions are not supported.
     */
    @SmallTest
    @Test
    public void testRtpHeaderExtensionsNotSupported() {
        when(mRtpAdapter.getAcceptedRtpHeaderExtensions()).thenReturn(
                NO_SUPPORTED_HEADER_EXTENSION_TYPES);
        mRtpTransport.startNegotiation();
        verify(mCallback, never()).onNegotiationSuccess(any());
        verify(mCallback).onNegotiationFailed(any());
    }

    /**
     * Verifies that unrecognized RTP header extensions are ignored.
     */
    @SmallTest
    @Test
    public void testIgnoreInvalidMessage() {
        testAllHeaderExtensionsSupported();

        ArraySet<RtpHeaderExtension> extensions = new ArraySet<>();
        // Invalid because it has an unrecognized local identifier
        extensions.add(new RtpHeaderExtension(1, new byte[] {0b01010101}));
        // Invalid because it has an unknown message type within a valid identifier.
        extensions.add(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b00101111}));
        // Invalid because it has an unknown message value within a valid type.
        extensions.add(new RtpHeaderExtension(CALL_STATE_LOCAL_IDENTIFIER,
                new byte[] {0b01110010}));
        mRtpTransport.onRtpHeaderExtensionsReceived(extensions);
        verify(mCallback, never()).onMessagesReceived(any());
    }
}
