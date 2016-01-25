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

public class GsmCellBroadcastHandlerTest {
    private static final String TAG = "GsmCellBroadcastHandlerTest";

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

    private GsmCellBroadcastHandler mGsmCellBroadcastHandler;
    private ContextFixture mContextFixture;
    private SimulatedCommands mSimulatedCommands;
    private TelephonyManager mTelephonyManager;
    private Object mLock = new Object();
    private boolean mReady;

    private class GsmCellBroadcastHandlerTestHandler extends HandlerThread {

        private GsmCellBroadcastHandlerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            synchronized (mLock) {
                mGsmCellBroadcastHandler = GsmCellBroadcastHandler.makeGsmCellBroadcastHandler(
                        mContextFixture.getTestDouble(), mPhone);
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
        new GsmCellBroadcastHandlerTestHandler(TAG).start();
    }

    @After
    public void tearDown() throws Exception {
        mGsmCellBroadcastHandler = null;
    }

    @Test @SmallTest
    public void testNewSms() {
        waitUntilReady();
        mSimulatedCommands.notifyGsmBroadcastSms(new byte[] {
                (byte)0xc0, //geographical scope
                (byte)0x01, //serial number
                (byte)0x01, //serial number
                (byte)0x01, //message identifier
                (byte)0x01, //message identifier
                (byte)0x01
        });
        TelephonyTestUtils.waitForMs(50);
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextFixture.getTestDouble()).sendBroadcast(intentArgumentCaptor.capture());
        assertTrue(intentArgumentCaptor.getValue().getAction().equals(
                Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION) ||
                intentArgumentCaptor.getValue().getAction().equals(
                        Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION));
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
