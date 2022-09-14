/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.ims;

import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.telephony.ims.aidl.IImsServiceController;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * Unit tests for ImsEnablementTracker
 */
@RunWith(AndroidJUnit4.class)
public class ImsEnablementTrackerTest extends ImsTestBase {

    private static final int SLOT_1 = 1;
    private static final int SUB_1 = 11;

    private static final long TEST_REQUEST_THROTTLE_TIME_MS = 1000L;
    // Mocked classes
    @Mock
    IImsServiceController mMockServiceControllerBinder;

    private TestableImsEnablementTracker mImsEnablementTracker;
    private Handler mHandler;


    private static class TestableImsEnablementTracker extends ImsEnablementTracker {
        private long mLastImsOperationTimeMs = 0L;
        TestableImsEnablementTracker(Looper looper, IImsServiceController controller) {
            super(looper, controller);
        }

        @Override
        protected long getLastOperationTimeMillis() {
            return mLastImsOperationTimeMs;
        }

        private void setLastOperationTimeMillis(long timeMills) {
            mLastImsOperationTimeMs = timeMills;
        }
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        // Make sure the handler is empty before finishing the test.
        waitForHandlerAction(mHandler, TEST_REQUEST_THROTTLE_TIME_MS);
        mImsEnablementTracker = null;
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testEnableCommandInDefaultState() throws RemoteException {
        // Verify that when the enable command is received in the Default state and enableIms
        // is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DEFAULT);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInDefaultState() throws RemoteException {
        // Verify that when the disable command is received in the Default state and disableIms
        // is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DEFAULT);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDefaultState() throws RemoteException {
        // Verify that when reset command is received in the Default state, it should be ignored.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DEFAULT);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DEFAULT));
    }

    @SmallTest
    @Test
    public void testEnableCommandInEnabledState() throws RemoteException {
        // Verify that received the enable command is not handle in the Enabled state,
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder, never()).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInEnabledState() throws RemoteException {
        // Verify that when the disable command is received in the Enabled state and disableIms
        // is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInEnabledState() throws RemoteException {
        // Verify that when the reset command is received in the Enabled state and disableIms
        // and enableIms are called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        // The disableIms was called. So set the last operation time to current.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        waitForHandlerActionDelayed(mHandler, TEST_REQUEST_THROTTLE_TIME_MS,
                TEST_REQUEST_THROTTLE_TIME_MS + 100);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInDisabledState() throws RemoteException {
        // Verify that when disable command is received in the Disabled state, it should be ignored.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInDisabledState() throws RemoteException {
        // Verify that when the enable command is received in the Disabled state and enableIms
        // is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandWithoutTimeoutInDisableState() throws RemoteException {
        // Verify that when the enable command is received in the Disabled state. After throttle
        // time expired, the enableIms is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDisabledState() throws RemoteException {
        // Verify that the reset command is received in the Disabled state and it`s not handled.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInDisablingState() throws RemoteException {
        // Verify that when enable command is received in the Disabling state, it should be ignored.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current so that the throttle time does not expire.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisablingMessageInDisablingState() throws RemoteException {
        // Verify that when the internal disable message is received in the Disabling state and
        // disableIms is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLING);

        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).disableIms(anyInt(), anyInt());
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDisablingState() throws RemoteException {
        // Verify when the reset command is received in the Disabling state the disableIms and
        // enableIms are called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        // The disableIms was called. So set the last operation time to current.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnablingMessageInEnablingState() throws RemoteException {
        // Verify that when the internal enable message is received in the Enabling state and
        // enableIms is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLING);

        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder).enableIms(anyInt(), anyInt());
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInEnablingState() throws RemoteException {
        // Verify that when the disable command is received in the Enabling state and
        // clear pending message and disableIms is not called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandWithEnablingState() throws RemoteException {
        // Verify that when reset command is received in the Enabling state, it should be ignored.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLING));
    }

    @SmallTest
    @Test
    public void testEnableCommandInResettingState() throws RemoteException {
        // Verify that when the enable command is received in the Resetting state and
        // enableIms is not called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_RESETTING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_RESETTING));
    }

    @SmallTest
    @Test
    public void testDisableCommandInResettingState() throws RemoteException {
        // Verify that when the disable command is received in the Resetting state and
        // disableIms is called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_RESETTING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResettingMessageInResettingState() throws RemoteException {
        // Verify that when the internal reset message is received in the Resetting state and
        // disableIms and enableIms are called.
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_RESETTING);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        verify(mMockServiceControllerBinder).disableIms(anyInt(), anyInt());
        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder).enableIms(anyInt(), anyInt());
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testConsecutiveCommandInEnabledState() throws RemoteException {
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_ENABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));

        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLING));

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLING));
        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder, times(1)).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testConsecutiveCommandInDisabledState() throws RemoteException {
        mImsEnablementTracker = createTracker(mMockServiceControllerBinder);
        mHandler = mImsEnablementTracker.getHandler();
        mImsEnablementTracker.setState(mImsEnablementTracker.STATE_IMS_DISABLED);
        // Wait for a while for the state machine to be ready.
        waitForHandlerActionDelayed(mHandler, 100, 150);

        // Set the last operation time to current to verify the message with delay.
        mImsEnablementTracker.setLastOperationTimeMillis(System.currentTimeMillis());
        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLING));

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));

        mImsEnablementTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));

        mImsEnablementTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, 100, 150);
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_DISABLED));

        mImsEnablementTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mImsEnablementTracker.getRemainThrottleTime(),
                mImsEnablementTracker.getRemainThrottleTime() + 100);
        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mImsEnablementTracker.isState(mImsEnablementTracker.STATE_IMS_ENABLED));
    }

    private TestableImsEnablementTracker createTracker(IImsServiceController binder) {
        TestableImsEnablementTracker tracker = new TestableImsEnablementTracker(
                Looper.getMainLooper(), binder);
        return tracker;
    }
}
