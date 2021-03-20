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

package com.android.internal.telephony;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class RadioInterfaceCapabilityControllerTest extends TelephonyTest {
    @Mock
    RadioConfig mMockRadioConfig;

    @Mock
    CommandsInterface mMockCommandsInterface;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testRadioInterfaceCapabilities() {
        final RadioInterfaceCapabilityController capabilities =
                new RadioInterfaceCapabilityController(mMockRadioConfig, mMockCommandsInterface,
                        mTestableLooper.getLooper());

        // The capabilities to test for
        final Set<String> capabilitySet = new HashSet<>();
        capabilitySet.add(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE);

        registerForRadioAvailable();
        getHalDeviceCapabilities(capabilitySet);

        // Test for the capabilities
        assertEquals(1, capabilities.getCapabilities().size());
        assertTrue(capabilities.getCapabilities()
                .contains(TelephonyManager.CAPABILITY_SECONDARY_LINK_BANDWIDTH_VISIBLE));
    }

    private void registerForRadioAvailable() {
        // Capture radio avaialble
        final ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);
        final ArgumentCaptor<Integer> whatCaptor = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<Object> objCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mMockCommandsInterface, times(1)).registerForAvailable(
                handlerCaptor.capture(), whatCaptor.capture(), objCaptor.capture());

        // Send Message back through handler
        final Message m = Message.obtain(
                handlerCaptor.getValue(), whatCaptor.getValue(), objCaptor.getValue());
        m.sendToTarget();
        processAllMessages();
    }

    private void getHalDeviceCapabilities(final Set<String> capabilitySet) {
        // Capture Message when the capabilities are requested
        final ArgumentCaptor<Message> deviceCapsMessage = ArgumentCaptor.forClass(Message.class);
        verify(mMockRadioConfig, times(1))
                .getHalDeviceCapabilities(deviceCapsMessage.capture());

        // Send Message back through handler
        final Message m = deviceCapsMessage.getValue();
        AsyncResult.forMessage(m, capabilitySet, null);
        m.sendToTarget();
        processAllMessages();
    }

    @Test
    public void testEmptyRadioInterfaceCapabilities() {
        final RadioInterfaceCapabilityController capabilities =
                new RadioInterfaceCapabilityController(mMockRadioConfig, null,
                        mTestableLooper.getLooper());

        // Test for the capabilities
        assertEquals(0, capabilities.getCapabilities().size());
    }
}
