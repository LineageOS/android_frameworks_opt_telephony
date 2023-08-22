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

import static com.android.internal.telephony.ims.ImsEnablementTracker.COMMAND_DISABLE_MSG;
import static com.android.internal.telephony.ims.ImsEnablementTracker.COMMAND_ENABLE_MSG;
import static com.android.internal.telephony.ims.ImsEnablementTracker.COMMAND_RESETTING_DONE;

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

    // default timeout values(millisecond) for handler working
    private static final int DEFAULT_TIMEOUT = 100;

    // default delay values(millisecond) for handler working
    private static final int DEFAULT_DELAY = 150;

    private static final int SLOT_1 = 0;
    private static final int SUB_1 = 11;

    private static final int SLOT_2 = 1;
    private static final int SUB_2 = 22;
    private static final long TEST_REQUEST_THROTTLE_TIME_MS = 3000L;

    // Mocked classes
    @Mock
    IImsServiceController mMockServiceControllerBinder;

    private TestableImsEnablementTracker mTracker;
    private Handler mHandler;

    private static long sLastImsOperationTimeMs = 0L;

    private static class TestableImsEnablementTracker extends ImsEnablementTracker {
        private ImsEnablementTrackerTest mOwner;

        TestableImsEnablementTracker(Looper looper, IImsServiceController controller, int state,
                int numSlots, ImsEnablementTrackerTest owner) {
            super(looper, controller, state, numSlots);
            mOwner = owner;
        }

        @Override
        protected long getLastOperationTimeMillis() {
            return ImsEnablementTrackerTest.sLastImsOperationTimeMs;
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

        mTracker = null;
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testEnableCommandInDefaultState() throws RemoteException {
        // Verify that when the enable command is received in the Default state and enableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DEFAULT, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
         // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInDefaultState() throws RemoteException {
        // Verify that when the disable command is received in the Default state and disableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DEFAULT, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDefaultState() throws RemoteException {
        // Verify that when reset command is received in the Default state, it should be ignored.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DEFAULT, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).resetIms(eq(SLOT_1), eq(SUB_1));
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInEnabledState() throws RemoteException {
        // Verify that received the enable command is not handle in the Enabled state,
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLED, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder, never()).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInEnabledState() throws RemoteException {
        // Verify that when the disable command is received in the Enabled state and disableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLED, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInEnabledState() throws RemoteException {
        // Verify that when the reset command is received in the Enabled state and disableIms
        // and enableIms are called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLED, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        verify(mMockServiceControllerBinder).resetIms(eq(SLOT_1), eq(SUB_1));

        waitForHandlerActionDelayed(mHandler, TEST_REQUEST_THROTTLE_TIME_MS,
                TEST_REQUEST_THROTTLE_TIME_MS + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInDisabledState() throws RemoteException {
        // Verify that when disable command is received in the Disabled state, it should be ignored.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLED, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInDisabledState() throws RemoteException {
        // Verify that when the enable command is received in the Disabled state and enableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLED, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandWithoutTimeoutInDisableState() throws RemoteException {
        // Verify that when the enable command is received in the Disabled state. After throttle
        // time expired, the enableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLED, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDisabledState() throws RemoteException {
        // Verify that the reset command is received in the Disabled state and it`s not handled.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLED, 1, 0);
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        verify(mMockServiceControllerBinder).resetIms(eq(SLOT_1), eq(SUB_1));

        // The disableIms was called. So set the last operation time to current.
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInDisablingState() throws RemoteException {
        // Verify that when enable command is received in the Disabling state, it should be ignored.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisablingMessageInDisablingState() throws RemoteException {
        // Verify that when the internal disable message is received in the Disabling state and
        // disableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);

        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).disableIms(anyInt(), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInDisablingState() throws RemoteException {
        // Verify when the reset command is received in the Disabling state the disableIms and
        // enableIms are called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).resetIms(eq(SLOT_1), eq(SUB_1));

        // The disableIms was called. So set the last operation time to current.
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnablingMessageInEnablingState() throws RemoteException {
        // Verify that when the internal enable message is received in the Enabling state and
        // enableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).enableIms(anyInt(), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInEnablingState() throws RemoteException {
        // Verify that when the disable command is received in the Enabling state and
        // clear pending message and disableIms is not called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandWithEnablingState() throws RemoteException {
        // Verify that when reset command is received in the Enabling state, it should be ignored.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).resetIms(eq(SLOT_1), eq(SUB_1));

        // The disableIms was called. So set the last operation time to current.
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInResettingState() throws RemoteException {
        // Verify that when the enable command is received in the Resetting state and
        // enableIms is not called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_RESETTING));
    }

    @SmallTest
    @Test
    public void testDisableCommandInResettingState() throws RemoteException {
        // Verify that when the disable command is received in the Resetting state and
        // disableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verifyZeroInteractions(mMockServiceControllerBinder);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_RESETTING));
    }

    @SmallTest
    @Test
    public void testResettingMessageInResettingState() throws RemoteException {
        // Verify that when the internal reset message is received in the Resetting state and
        // disableIms and enableIms are called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);

        // Wait for the throttle time.
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).resetIms(anyInt(), anyInt());

        // Set the last operation time to current to verify the message with delay.
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), anyInt());
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableEnableMessageInResettingState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // Simulation.
        // In Resetting state, disableIms() called before doing resetIms().
        // After doing resetIms(), during the throttle time(before doing disableIms()),
        // enableIms() called. Finally skip disableIms() and do enableIms().
        mHandler.removeMessages(COMMAND_RESETTING_DONE);
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_RESETTING_DONE, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));

        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).resetIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableDisableMessageInResettingState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // Simulation.
        // In Resetting state, enableIms() called before doing resetIms().
        // After doing resetIms(), during the throttle time(before doing enableIms()),
        // disableIms() called. Finally skip enableIms() and do disableIms().
        mHandler.removeMessages(COMMAND_RESETTING_DONE);
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_RESETTING_DONE, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));

        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).resetIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, times(1)).disableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testRepeatEnableMessageInResettingState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // Simulation.
        // In Resetting state, enableIms(), disableIms() are called repeatedly.
        // After doing resetIms(), latest enableIms() should perform.
        mHandler.removeMessages(COMMAND_RESETTING_DONE);
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_RESETTING_DONE, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));

        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).resetIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testRepeatDisableMessageInResettingState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_RESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // Simulation.
        // In Resetting state, enableIms(), disableIms() are called repeatedly.
        // After doing resetIms(), latest disableIms() should perform.
        mHandler.removeMessages(COMMAND_RESETTING_DONE);
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_RESETTING_DONE, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_ENABLE_MSG, SLOT_1, SUB_1));
        mHandler.sendMessage(mHandler.obtainMessage(COMMAND_DISABLE_MSG, SLOT_1, SUB_1));

        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).resetIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, times(1)).disableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testConsecutiveCommandInEnabledState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLED, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));

        // Set the last operation time to current to verify the message with delay.
        sLastImsOperationTimeMs = System.currentTimeMillis();
        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLING));

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLING));

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).disableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).resetIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testConsecutiveCommandInDisabledState() throws RemoteException {
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DISABLED, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // Set the last operation time to current to verify the message with delay.
        sLastImsOperationTimeMs = System.currentTimeMillis();
        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLING));

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));

        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_RESETTING));

        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testSubIdChangeToInvalidAndEnableCommand() throws RemoteException {
        // Verify that when the enable command is received in the Default state and enableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_ENABLED, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        mTracker.subIdChangedToInvalid(SLOT_1);
        waitForHandler(mHandler);
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DEFAULT));

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);
        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandWithDifferentSlotId() throws RemoteException {
        // Verify that when the enable command is received in the Default state and enableIms
        // is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_DEFAULT, 2,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        Handler handlerForSlot2 = mTracker.getHandler(SLOT_2);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);
        waitForHandler(handlerForSlot2);

        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandler(mHandler);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
        assertTrue(mTracker.isState(SLOT_2, mTracker.STATE_IMS_DEFAULT));

        mTracker.enableIms(SLOT_2, SUB_2);
        waitForHandler(handlerForSlot2);

        verify(mMockServiceControllerBinder).enableIms(eq(SLOT_2), eq(SUB_2));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
        assertTrue(mTracker.isState(SLOT_2, mTracker.STATE_IMS_ENABLED));

        mTracker.setNumOfSlots(1);
        sLastImsOperationTimeMs = System.currentTimeMillis();
        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_1), eq(SUB_1));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));

        mTracker.setNumOfSlots(2);
        sLastImsOperationTimeMs = System.currentTimeMillis();
        mTracker.disableIms(SLOT_2, SUB_2);
        waitForHandler(handlerForSlot2);

        verify(mMockServiceControllerBinder).disableIms(eq(SLOT_2), eq(SUB_2));
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
        assertTrue(mTracker.isState(SLOT_2, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testEnableCommandInPostResettingState() throws RemoteException {
        // Verify that when the enable/disable commands are received in the PostResetting state
        // and final enableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_POSTRESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // to confirm the slotId, subId for COMMAND_POST_RESETTING_DONE
        mHandler.removeMessages(mTracker.COMMAND_POST_RESETTING_DONE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(mTracker.COMMAND_POST_RESETTING_DONE,
                SLOT_1, SUB_1), mTracker.getRemainThrottleTime());

        mTracker.enableIms(SLOT_1, SUB_1);
        mTracker.disableIms(SLOT_1, SUB_1);
        mTracker.enableIms(SLOT_1, SUB_1);
        mTracker.disableIms(SLOT_1, SUB_1);
        mTracker.enableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    @SmallTest
    @Test
    public void testDisableCommandInPostResettingState() throws RemoteException {
        // Verify that when the enable/disable commands are received in the PostResetting state
        // and final disableIms is called.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_POSTRESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // to confirm the slotId, subId for COMMAND_POST_RESETTING_DONE
        mHandler.removeMessages(mTracker.COMMAND_POST_RESETTING_DONE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(mTracker.COMMAND_POST_RESETTING_DONE,
                SLOT_1, SUB_1), mTracker.getRemainThrottleTime());

        mTracker.disableIms(SLOT_1, SUB_1);
        mTracker.enableIms(SLOT_1, SUB_1);
        mTracker.disableIms(SLOT_1, SUB_1);
        mTracker.enableIms(SLOT_1, SUB_1);
        mTracker.disableIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).disableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).enableIms(eq(SLOT_1), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_DISABLED));
    }

    @SmallTest
    @Test
    public void testResetCommandInPostResettingState() throws RemoteException {
        // Verify that when the enable/disable/reset commands are received in the PostResetting
        // state and final enableIms is called without calling resetIms again.
        mTracker = createTracker(mMockServiceControllerBinder, mTracker.STATE_IMS_POSTRESETTING, 1,
                System.currentTimeMillis());
        mHandler = mTracker.getHandler(SLOT_1);
        // Wait for a while for the state machine to be ready.
        waitForHandler(mHandler);

        // to confirm the slotId, subId for COMMAND_POST_RESETTING_DONE
        mHandler.removeMessages(mTracker.COMMAND_POST_RESETTING_DONE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(mTracker.COMMAND_POST_RESETTING_DONE,
                SLOT_1, SUB_1), mTracker.getRemainThrottleTime());

        mTracker.disableIms(SLOT_1, SUB_1);
        mTracker.enableIms(SLOT_1, SUB_1);
        mTracker.resetIms(SLOT_1, SUB_1);
        waitForHandlerActionDelayed(mHandler, mTracker.getRemainThrottleTime(),
                mTracker.getRemainThrottleTime() + DEFAULT_DELAY);

        verify(mMockServiceControllerBinder, times(1)).enableIms(eq(SLOT_1), eq(SUB_1));
        verify(mMockServiceControllerBinder, never()).disableIms(eq(SLOT_1), anyInt());
        verify(mMockServiceControllerBinder, never()).resetIms(eq(SLOT_1), anyInt());
        assertTrue(mTracker.isState(SLOT_1, mTracker.STATE_IMS_ENABLED));
    }

    private TestableImsEnablementTracker createTracker(IImsServiceController binder, int state,
            int numSlots, long initLastOperationTime) {
        sLastImsOperationTimeMs = initLastOperationTime;
        TestableImsEnablementTracker tracker = new TestableImsEnablementTracker(
                Looper.getMainLooper(), binder, state, numSlots, this);
        return tracker;
    }

    private void waitForHandler(Handler h) {
        waitForHandlerActionDelayed(h, DEFAULT_TIMEOUT, DEFAULT_DELAY);
    }
}
