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

import android.os.HandlerThread;
import android.os.Message;
import android.telephony.*;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.ImsSMSDispatcher;
import com.android.internal.telephony.SMSDispatcher;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.SmsUsageMonitor;
import com.android.internal.telephony.TelephonyTest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;

public class CdmaSmsDispatcherTest extends TelephonyTest {
    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private SmsUsageMonitor mSmsUsageMonitor;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mCdmaSmsMessage;
    @Mock
    private ImsSMSDispatcher mImsSmsDispatcher;
    @Mock
    private SMSDispatcher.SmsTracker mSmsTracker;

    private CdmaSMSDispatcher mCdmaSmsDispatcher;
    private TelephonyManager mTelephonyManager;

    private class CdmaSmsDispatcherTestHandler extends HandlerThread {

        private CdmaSmsDispatcherTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCdmaSmsDispatcher = new CdmaSMSDispatcher(mPhone, mSmsUsageMonitor,
                    mImsSmsDispatcher);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("CdmaSmsDispatcherTest");

        mTelephonyManager = TelephonyManager.from(mContext);
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();

        new CdmaSmsDispatcherTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mCdmaSmsDispatcher = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testSendSms() {
        doReturn(mServiceState).when(mPhone).getServiceState();
        mCdmaSmsDispatcher.sendSms(mSmsTracker);
        verify(mSimulatedCommandsVerifier).sendCdmaSms(any(byte[].class), any(Message.class));
    }
}
