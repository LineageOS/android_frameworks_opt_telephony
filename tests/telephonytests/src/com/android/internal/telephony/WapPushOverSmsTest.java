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

package com.android.internal.telephony;

import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Telephony;
import android.test.suitebuilder.annotation.SmallTest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

public class WapPushOverSmsTest extends TelephonyTest {
    private WapPushOverSms mWapPushOverSmsUT;

    @Before
    public void setUp() throws Exception {
        super.setUp("WapPushOverSmsTest");

        mWapPushOverSmsUT = new WapPushOverSms(mContextFixture.getTestDouble());
    }

    @After
    public void tearDown() throws Exception {
        mWapPushOverSmsUT = null;
    }

    @Test @SmallTest
    public void testDispatchWapPdu() {
        doReturn(true).when(mWspTypeDecoder).decodeUintvarInteger(anyInt());
        doReturn(true).when(mWspTypeDecoder).decodeContentType(anyInt());
        doReturn((long)2).when(mWspTypeDecoder).getValue32();
        doReturn(2).when(mWspTypeDecoder).getDecodedDataLength();
        doReturn(WspTypeDecoder.CONTENT_TYPE_B_PUSH_CO).when(mWspTypeDecoder).getValueString();
        byte[] pdu = new byte[]{
                (byte) 0xFF,
                (byte) 0x06,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF,
                (byte) 0xFF
        };

        mWapPushOverSmsUT.dispatchWapPdu(pdu, null, mInboundSmsHandler);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mInboundSmsHandler).dispatchIntent(intentArgumentCaptor.capture(),
                eq(android.Manifest.permission.RECEIVE_WAP_PUSH),
                eq(AppOpsManager.OP_RECEIVE_WAP_PUSH),
                any(Bundle.class),
                isNull(BroadcastReceiver.class),
                eq(UserHandle.SYSTEM));
        Intent intent = intentArgumentCaptor.getValue();
        assertEquals(Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION, intent.getAction());
        assertEquals(0xFF, intent.getIntExtra("transactionId", 0));
        assertEquals(0x06, intent.getIntExtra("pduType", 0));

        byte[] header = intent.getByteArrayExtra("header");
        assertEquals(2, header.length);
        for (int i = 0; i < header.length; i++) {
            assertEquals((byte)0xFF, header[i]);
        }

        byte[] data = intent.getByteArrayExtra("data");
        assertEquals(pdu.length, data.length);
        for (int i = 0; i < pdu.length; i++) {
            assertEquals(pdu[i], data[i]);
        }

        assertEquals(mWspTypeDecoder.getContentParameters(),
                intent.getSerializableExtra("contentTypeParameters"));
    }
}