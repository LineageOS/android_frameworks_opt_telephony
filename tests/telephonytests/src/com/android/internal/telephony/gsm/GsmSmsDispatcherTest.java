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
import android.os.Message;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.*;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.GsmCdmaPhone;
import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SmsBroadcastUndelivered;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.TelephonyComponentFactory;
import com.android.internal.telephony.TelephonyTestUtils;
import com.android.internal.telephony.test.SimulatedCommands;
import com.android.internal.telephony.test.SimulatedCommandsVerifier;
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

public class GsmSmsDispatcherTest {
    private static final String TAG = "GsmSmsDispatcherTest";

    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private SmsUsageMonitor mSmsUsageMonitor;
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
    @Mock
    private ImsSMSDispatcher mImsSmsDispatcher;
    @Mock
    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    @Mock
    private SimulatedCommandsVerifier mSimulatedCommandsVerifier;

    private GsmSMSDispatcher mGsmSmsDispatcher;
    private ContextFixture mContextFixture;
    private SimulatedCommands mSimulatedCommands;
    private TelephonyManager mTelephonyManager;
    private Object mLock = new Object();
    private boolean mReady;

    private class GsmSmsDispatcherTestHandler extends HandlerThread {

        private GsmSmsDispatcherTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            synchronized (mLock) {
                mGsmSmsDispatcher = new GsmSMSDispatcher(mPhone, mSmsUsageMonitor,
                        mImsSmsDispatcher, mGsmInboundSmsHandler);
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

        field = SimulatedCommandsVerifier.class.getDeclaredField("sInstance");
        field.setAccessible(true);
        field.set(null, mSimulatedCommandsVerifier);

        mContextFixture = new ContextFixture();
        mSimulatedCommands = new SimulatedCommands();
        mPhone.mCi = mSimulatedCommands;

        mTelephonyManager = TelephonyManager.from(mContextFixture.getTestDouble());
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();
        doReturn(mIDeviceIdleController).when(mTelephonyComponentFactory).
                getIDeviceIdleController();
        doReturn(mContextFixture.getTestDouble()).when(mPhone).getContext();

        new GsmSmsDispatcherTestHandler(TAG).start();
    }

    @After
    public void tearDown() throws Exception {
        mGsmSmsDispatcher = null;
    }

    @Test @SmallTest
    public void testNewSms() {
        waitUntilReady();
        mSimulatedCommands.notifySmsStatus("0123056789ABCDEF");
        TelephonyTestUtils.waitForMs(50);
        verify(mSimulatedCommandsVerifier).acknowledgeLastIncomingGsmSms(true,
                Telephony.Sms.Intents.RESULT_SMS_HANDLED, null);
    }

    private static void logd(String s) {
        Log.d(TAG, s);
    }
}
