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

package com.android.internal.telephony;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;

import android.os.AsyncResult;
import android.os.Message;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class IccSmsInterfaceManagerTest extends TelephonyTest {
    // object under test
    private IccSmsInterfaceManager mIccSmsInterfaceManager;

    // Mocked classes
    private SmsPermissions mMockSmsPermissions;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mMockSmsPermissions = mock(SmsPermissions.class);
        mIccSmsInterfaceManager = new IccSmsInterfaceManager(mPhone, mContext, mAppOpsManager,
                mSmsDispatchersController, mMockSmsPermissions);
    }

    @After
    public void tearDown() throws Exception {
        mIccSmsInterfaceManager = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testSynchronization() throws Exception {
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission
                .RECEIVE_EMERGENCY_BROADCAST);
        when(mMockSmsPermissions.checkCallingOrSelfCanGetSmscAddress(anyString(), anyString()))
                .thenReturn(true);
        mSimulatedCommands.mSendSetGsmBroadcastConfigResponse = false;
        mSimulatedCommands.mSendGetSmscAddressResponse = false;
        CountDownLatch enableRangeLatch = new CountDownLatch(1);
        CountDownLatch getSmscLatch = new CountDownLatch(1);

        // call enableGsmBroadcastRange from first thread
        Thread enableRangeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIccSmsInterfaceManager.enableGsmBroadcastRange(0, 5);
                enableRangeLatch.countDown();
            }
        });
        enableRangeThread.start();

        // call getSmscAddressFromIccEf from second thread
        Thread getSmscThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mIccSmsInterfaceManager.getSmscAddressFromIccEf("calling package");
                getSmscLatch.countDown();
            }
        });
        getSmscThread.start();

        // wait for half a second to let the threads send their messages
        waitForMs(500);
        processAllMessages();

        // latch count should not be reduced until response is sent
        assertEquals("enableRangeLatch.getCount should be 1", 1, enableRangeLatch.getCount());
        assertEquals("getSmscLatch.getCount should be 1", 1, getSmscLatch.getCount());

        // send back response for first request and assert that only the first thread is unblocked
        ArgumentCaptor<Message> enableRangeCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mSimulatedCommandsVerifier).setGsmBroadcastConfig(any(),
                enableRangeCaptor.capture());
        Message enableRangeResponse = enableRangeCaptor.getValue();
        AsyncResult.forMessage(enableRangeResponse);
        enableRangeResponse.sendToTarget();
        processAllMessages();

        // the response triggers setGsmBroadcastActivation and since that's on another thread
        // (enableRangeThread), wait for half a second for the response message of that to reach
        // the handler before calling processAllMessages(). Consider increasing this timeout if the
        // test fails.
        waitForMs(500);
        processAllMessages();

        try {
            assertEquals("enableRangeLatch.await should be true", true,
                    enableRangeLatch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ie) {
            fail("enableRangeLatch.await interrupted");
        }
        assertEquals("getSmscLatch.getCount should still be 1", 1, getSmscLatch.getCount());

        // send back response for second request and assert that the second thread gets unblocked
        ArgumentCaptor<Message> getSmscCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mSimulatedCommandsVerifier).getSmscAddress(getSmscCaptor.capture());
        Message getSmscResponse = getSmscCaptor.getValue();
        AsyncResult.forMessage(getSmscResponse);
        getSmscResponse.sendToTarget();
        processAllMessages();

        try {
            assertEquals("getSmscLatch.await should be true", true,
                    getSmscLatch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ie) {
            fail("getSmscLatch.await interrupted");
        }
    }
}
