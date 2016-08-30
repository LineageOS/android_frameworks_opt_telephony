/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.internal.telephony.uicc;

import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.MockitoAnnotations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.android.internal.telephony.TelephonyTest;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import junit.framework.Assert;

import com.android.internal.telephony.CommandsInterface;
import android.content.Context;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Message;

public class IsimUiccRecordsTest extends TelephonyTest {

    @Mock
    private CommandsInterface mMockCI;

    private IsimUiccRecords mIsimUiccRecords;

    private class IsimUiccRecordsTestHandler extends HandlerThread {
        private IsimUiccRecordsTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mIsimUiccRecords = new IsimUiccRecords(mUiccCardApplication3gpp, mContext, mMockCI);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        mMockCI = mock(CommandsInterface.class);
        new IsimUiccRecordsTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mIsimUiccRecords.dispose();

    }

    @Test
    public void testBroadcastRefresh() {
        Message msg = new Message();
        msg.what = (Integer) getStaticField(IsimUiccRecords.class,
            mIsimUiccRecords, "EVENT_ISIM_REFRESH");
        mIsimUiccRecords.handleMessage(msg);
        ArgumentCaptor<Intent> intentCapture = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCapture.capture());

        assertEquals(
            ((Intent) intentCapture.getValue()).getAction(), IsimUiccRecords.INTENT_ISIM_REFRESH);
    }

    private Object invokeNonStaticMethod(Class clazz, Object caller, String method,
                                            Class[] clsParams, Object[] params) {
        try {
            Method methodReflection = clazz.getDeclaredMethod(method, clsParams);
            methodReflection.setAccessible(true);
            return methodReflection.invoke(caller, params);
        } catch (Exception e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private Object getStaticField(Class clazz, Object caller, String field) {
        try {
            Field fieldReflection = clazz.getDeclaredField(field);
            fieldReflection.setAccessible(true);
            Object fieldValue = fieldReflection.get(caller);
            return fieldValue;
        } catch (Exception e) {
            Assert.fail(e.toString());
            return null;
        }
    }

}
