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

package com.android.internal.telephony.cdma;

import android.content.Intent;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.*;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.util.IState;
import com.android.internal.util.StateMachine;

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CdmaInboundSmsHandlerTest extends TelephonyTest {
    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mCdmaSmsMessage;

    private CdmaInboundSmsHandler mCdmaInboundSmsHandler;
    private TelephonyManager mTelephonyManager;
    private SmsEnvelope mSmsEnvelope = new SmsEnvelope();

    private class CdmaInboundSmsHandlerTestHandler extends HandlerThread {

        private CdmaInboundSmsHandlerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCdmaInboundSmsHandler = CdmaInboundSmsHandler.makeInboundSmsHandler(mContext,
                    mSmsStorageMonitor, mPhone, null);
            setReady(true);
        }
    }

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mCdmaInboundSmsHandler);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("CdmaInboundSmsHandlerTest");

        Field field = SmsMessage.class.getDeclaredField("mEnvelope");
        field.setAccessible(true);
        field.set(mCdmaSmsMessage, mSmsEnvelope);

        mTelephonyManager = TelephonyManager.from(mContext);
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();
        doReturn(mIDeviceIdleController).when(mTelephonyComponentFactory).
                getIDeviceIdleController();

        new CdmaInboundSmsHandlerTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mCdmaInboundSmsHandler = null;
        super.tearDown();
    }

    private void transitionFromStartupToIdle() {
        // verify initially in StartupState
        assertEquals("StartupState", getCurrentState().getName());

        // trigger transition to IdleState
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @SmallTest
    public void testNewSms() {
        transitionFromStartupToIdle();

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        byte[] smsPdu = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF};
        mSmsMessage.mWrappedSmsMessage = mCdmaSmsMessage;
        doReturn(smsPdu).when(mCdmaSmsMessage).getPdu();
        doReturn(SmsEnvelope.TELESERVICE_WMT).when(mCdmaSmsMessage).getTeleService();
        mCdmaInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(50);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getValue().getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        try {
            doReturn(new int[]{UserHandle.USER_SYSTEM}).when(mIActivityManager).getRunningUserIds();
        } catch (RemoteException re) {
            fail("Unexpected RemoteException: " + re.getStackTrace());
        }
        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }
}
