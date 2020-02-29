/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.test.SimulatedCommands;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UiccCardApplicationTest extends TelephonyTest {
    private UiccCardApplication mUiccCardApplication;
    @Mock
    private IccCardApplicationStatus mUiccCardAppStatus;
    @Mock
    private UiccProfile mUiccProfile;
    private Handler mHandler;
    private int mAttemptsRemaining = -1;
    private CommandException mException = null;
    private IccCardApplicationStatus.AppState mAppState;
    private static final int UICCCARDAPP_ENABLE_FDN_EVENT = 1;
    private static final int UICCCARDAPP_ENABLE_LOCK_EVENT = 2;
    private static final int UICCCARDAPP_CHANGE_PSW_EVENT = 3;
    private static final int UICCCARDAPP_SUPPLY_PIN_EVENT = 4;
    private static final int EVENT_APP_STATE_DETECTED     = 5;
    private static final int EVENT_APP_STATE_READY        = 6;

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        //set initial state of app status
        mUiccCardAppStatus.app_type = IccCardApplicationStatus.AppType.APPTYPE_SIM;
        mUiccCardAppStatus.aid = TAG;
        mUiccCardAppStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_PIN;
        mUiccCardAppStatus.pin1 = IccCardStatus.PinState.PINSTATE_ENABLED_NOT_VERIFIED;
        mUiccCardAppStatus.pin2 = IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;

        mUiccCardApplication = new UiccCardApplication(mUiccProfile, mUiccCardAppStatus,
            mContext, mSimulatedCommands);
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case UICCCARDAPP_SUPPLY_PIN_EVENT:
                    case UICCCARDAPP_CHANGE_PSW_EVENT:
                    case UICCCARDAPP_ENABLE_LOCK_EVENT:
                    case UICCCARDAPP_ENABLE_FDN_EVENT:
                        mAttemptsRemaining = msg.arg1;
                        mException = (CommandException) ((AsyncResult) msg.obj).exception;
                        if (mAttemptsRemaining != -1) {
                            logd("remaining Attempt:" + mAttemptsRemaining);
                        }
                        break;
                    case EVENT_APP_STATE_DETECTED:
                        mAppState = IccCardApplicationStatus.AppState.APPSTATE_DETECTED;
                        break;
                    case EVENT_APP_STATE_READY:
                        mAppState = IccCardApplicationStatus.AppState.APPSTATE_READY;
                        break;
                    default:
                        logd("Unknown Event " + msg.what);
                }
            }
        };
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testGetSetAppType() {
        assertEquals(IccCardApplicationStatus.AppType.APPTYPE_SIM, mUiccCardApplication.getType());
        mUiccCardAppStatus.app_type = IccCardApplicationStatus.AppType.APPTYPE_USIM;
        logd("Update UiccCardApplication Status");
        mUiccCardApplication.update(mUiccCardAppStatus, mContext, mSimulatedCommands);
        processAllMessages();
        assertEquals(IccCardApplicationStatus.AppType.APPTYPE_USIM, mUiccCardApplication.getType());
    }

    @Test
    @SmallTest
    public void testGetSetAppState() {
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_PIN,
                mUiccCardApplication.getState());
        mUiccCardAppStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_PUK;
        logd("Update UiccCardApplication Status");
        mUiccCardApplication.update(mUiccCardAppStatus, mContext, mSimulatedCommands);
        processAllMessages();
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_PUK,
                mUiccCardApplication.getState());
    }

    @Test
    @SmallTest
    public void testGetSetIccFdnEnabled() {
        assertFalse(mUiccCardApplication.getIccFdnEnabled());
        //enable FDN
        Message mFDNenabled = mHandler.obtainMessage(UICCCARDAPP_ENABLE_FDN_EVENT);
        //wrong PIN2Code
        mUiccCardApplication.setIccFdnEnabled(true, "XXXX", mFDNenabled);
        processAllMessages();
        assertFalse(mUiccCardApplication.getIccFdnEnabled());

        mFDNenabled = mHandler.obtainMessage(UICCCARDAPP_ENABLE_FDN_EVENT);
        mUiccCardApplication.setIccFdnEnabled(true, mSimulatedCommands.DEFAULT_SIM_PIN2_CODE,
                mFDNenabled);
        processAllMessages();
        assertTrue(mUiccCardApplication.getIccFdnEnabled());
    }

    @Test
    @SmallTest
    public void testGetSetIccLockedEnabled() {
        assertFalse(mUiccCardApplication.getIccLockEnabled());
        Message mLockEnabled = mHandler.obtainMessage(UICCCARDAPP_ENABLE_LOCK_EVENT);
        mUiccCardApplication.setIccLockEnabled(true, "XXXX", mLockEnabled);
        processAllMessages();
        assertFalse(mUiccCardApplication.getIccLockEnabled());

        mLockEnabled = mHandler.obtainMessage(UICCCARDAPP_ENABLE_LOCK_EVENT);
        mUiccCardApplication.setIccLockEnabled(true, mSimulatedCommands.DEFAULT_SIM_PIN_CODE,
                mLockEnabled);
        processAllMessages();
        assertTrue(mUiccCardApplication.getIccLockEnabled());
    }

    @Test
    @SmallTest
    public void testChangeIccLockPassword() {
        Message mChangePsw = mHandler.obtainMessage(UICCCARDAPP_CHANGE_PSW_EVENT);
        mUiccCardApplication.changeIccLockPassword(mSimulatedCommands.DEFAULT_SIM_PIN_CODE,
                "1111", mChangePsw);
        processAllMessages();
        verify(mSimulatedCommandsVerifier).changeIccPinForApp(
                eq(mSimulatedCommands.DEFAULT_SIM_PIN_CODE), eq("1111"), eq(TAG), (Message) any());
        assertNull(mException);
    }

    @Test
    @SmallTest
    public void testSupplyPin() {
        //Supply with default PIN1
        Message mSupplyPin = mHandler.obtainMessage(UICCCARDAPP_SUPPLY_PIN_EVENT);
        mUiccCardApplication.supplyPin(mSimulatedCommands.DEFAULT_SIM_PIN_CODE, mSupplyPin);
        processAllMessages();
        assertEquals(-1, mAttemptsRemaining);
        verify(mSimulatedCommandsVerifier).supplyIccPinForApp(
                eq(SimulatedCommands.DEFAULT_SIM_PIN_CODE), eq(TAG), (Message) any());

        //Supply with wrong PIN1
        mSupplyPin = mHandler.obtainMessage(UICCCARDAPP_SUPPLY_PIN_EVENT);
        mUiccCardApplication.supplyPin("1111", mSupplyPin);
        processAllMessages();
        assertEquals(mSimulatedCommands.DEFAULT_PIN1_ATTEMPT - 1, mAttemptsRemaining);
        assertNotNull(mException);
        assertEquals(CommandException.Error.PASSWORD_INCORRECT, mException.getCommandError());

        testChangeIccLockPassword();
        //Supply with the updated PIN1
        mSupplyPin = mHandler.obtainMessage(UICCCARDAPP_SUPPLY_PIN_EVENT);
        mUiccCardApplication.supplyPin("1111", mSupplyPin);
        processAllMessages();
        assertEquals(-1, mAttemptsRemaining);
    }

    @Test
    @SmallTest
    public void testAppStateChangeNotification() {
        mUiccCardApplication.registerForDetected(mHandler, EVENT_APP_STATE_DETECTED, null);
        mUiccCardApplication.registerForReady(mHandler, EVENT_APP_STATE_READY, null);
        processAllMessages();
        assertEquals(null, mAppState);

        // Change to DETECTED state.
        mUiccCardAppStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_DETECTED;
        mUiccCardApplication.update(mUiccCardAppStatus, mContext, mSimulatedCommands);
        processAllMessages();
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_DETECTED, mAppState);
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_DETECTED,
                mUiccCardApplication.getState());

        // Change to READY state.
        mUiccCardAppStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_READY;
        mUiccCardAppStatus.pin1 = IccCardStatus.PinState.PINSTATE_ENABLED_VERIFIED;
        mUiccCardApplication.update(mUiccCardAppStatus, mContext, mSimulatedCommands);
        processAllMessages();
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_READY, mAppState);
        assertEquals(IccCardApplicationStatus.AppState.APPSTATE_READY,
                mUiccCardApplication.getState());
    }
}
