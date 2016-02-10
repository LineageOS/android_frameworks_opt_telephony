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

package com.android.internal.telephony.gsm;

import android.os.HandlerThread;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.TelephonyTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class GsmSmsDispatcherTest extends TelephonyTest {
    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private SmsUsageMonitor mSmsUsageMonitor;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mGsmSmsMessage;
    @Mock
    private ImsSMSDispatcher mImsSmsDispatcher;
    @Mock
    private GsmInboundSmsHandler mGsmInboundSmsHandler;

    private GsmSMSDispatcher mGsmSmsDispatcher;
    private TelephonyManager mTelephonyManager;

    private class GsmSmsDispatcherTestHandler extends HandlerThread {

        private GsmSmsDispatcherTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mGsmSmsDispatcher = new GsmSMSDispatcher(mPhone, mSmsUsageMonitor,
                    mImsSmsDispatcher, mGsmInboundSmsHandler);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {

        super.setUp(getClass().getSimpleName());

        mTelephonyManager = TelephonyManager.from(mContextFixture.getTestDouble());
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();

        new GsmSmsDispatcherTestHandler(getClass().getSimpleName()).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mGsmSmsDispatcher = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testSmsStatus() {
        mSimulatedCommands.notifySmsStatus("0123056789ABCDEF");
        TelephonyTestUtils.waitForMs(50);
        verify(mSimulatedCommandsVerifier).acknowledgeLastIncomingGsmSms(true,
                Telephony.Sms.Intents.RESULT_SMS_HANDLED, null);
    }
}
