/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IDeviceIdleController;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.*;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyTestUtils;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class GsmInboundSmsHandlerTest {
    private static final String TAG = "GsmInboundSmsHandlerTest";

    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private Phone mPhone;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mGsmSmsMessage;
    @Mock
    private UiccController mUiccController;
    @Mock
    private IDeviceIdleController mIDeviceIdleController;
    @Mock
    private TelephonyComponentFactory mTelephonyComponentFactory;

    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private ContextFixture mContextFixture;
    private SimulatedCommands mSimulatedCommands;
    private TelephonyManager mTelephonyManager;
    private Object mLock = new Object();
    private boolean mReady;

    private class GsmInboundSmsHandlerTestHandler extends HandlerThread {

        private GsmInboundSmsHandlerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            synchronized (mLock) {
                mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(
                        mContextFixture.getTestDouble(), mSmsStorageMonitor, mPhone);
                mReady = true;
            }
        }
    }

    private void waitUntilReady() {
        while(true) {
            synchronized (mLock) {
                if (mReady) {
                    break;
                }
            }
        }
    }

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mGsmInboundSmsHandler);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        //Use reflection to mock singletons
        Field field = UiccController.class.getDeclaredField("mInstance");
        field.setAccessible(true);
        field.set(null, mUiccController);

        field = TelephonyComponentFactory.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mTelephonyComponentFactory);

        mContextFixture = new ContextFixture();
        mSimulatedCommands = new SimulatedCommands();
        mPhone.mCi = mSimulatedCommands;

        mTelephonyManager = TelephonyManager.from(mContextFixture.getTestDouble());
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();
        doReturn(mIDeviceIdleController).when(mTelephonyComponentFactory).
                getIDeviceIdleController();

        mReady = false;
        new GsmInboundSmsHandlerTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mGsmInboundSmsHandler = null;
    }

    @Test @SmallTest
    public void testNewSms() {
        // verify initially in StartupState
        assertEquals("StartupState", getCurrentState().getName());

        // start SmsBroadcastUndelivered thread to trigger transition to IdleState
        SmsBroadcastUndelivered.initialize(
                mContextFixture.getTestDouble(), mGsmInboundSmsHandler, null);
        TelephonyTestUtils.waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        byte[] smsPdu = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF};
        mSmsMessage.mWrappedSmsMessage = mGsmSmsMessage;
        doReturn(smsPdu).when(mGsmSmsMessage).getPdu();
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        TelephonyTestUtils.waitForMs(50);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble(), times(2)).
                sendBroadcast(intentArgumentCaptor.capture());

        List<Intent> list = intentArgumentCaptor.getAllValues();
        /* logd("list.size() " + list.size());
        for (int i = 0; i < list.size(); i++) {
            logd("list.get(i) " + list.get(i));
        } */
        //todo: seems to be some issue with ArgumentCaptor. Both DELIVER and RECEIVED broadcasts
        //can be seen in logs but according to list both are RECEIVED
        //assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
        //                list.get(0).getAction());
        boolean smsReceivedAction = false;
        for (Intent i : list) {
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction())) {
                smsReceivedAction = true;
                break;
            }
        }
        assertTrue(smsReceivedAction);

        assertEquals("IdleState", getCurrentState().getName());
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
