/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.fail;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.ims.ImsSsInfo;
import android.telephony.ims.ImsUtListener;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.ims.ImsUt;
import com.android.ims.internal.IImsUt;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class ImsUtTest extends TelephonyTest {

    private static final int MSG_QUERY = 1;
    private static final int TEST_TIMEOUT_MS = 5000;

    private class TestHandler extends Handler {

        TestHandler(Looper looper) {
            super(looper);
        }

        private final LinkedBlockingQueue<ImsSsInfo> mPendingSsInfos = new LinkedBlockingQueue<>(1);
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_QUERY) {
                AsyncResult ar = (AsyncResult) msg.obj;
                mPendingSsInfos.offer((ImsSsInfo) ar.result);
            }
        }
        public ImsSsInfo getPendingImsSsInfo() {
            try {
                return mPendingSsInfos.poll(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                fail("test interrupted!");
            }
            return null;
        }
    }

    @Mock IImsUt mImsUtBinder;

    private TestHandler mHandler;

    @Before
    public void setUp() throws Exception {
        super.setUp("ImsUtTest");
        mHandler = new TestHandler(Looper.myLooper());
        processAllMessages();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testClirConversionCompat() throws Exception {
        ArgumentCaptor<ImsUt.IImsUtListenerProxy> captor =
                ArgumentCaptor.forClass(ImsUt.IImsUtListenerProxy.class);
        ImsUt mImsUt = new ImsUt(mImsUtBinder);
        verify(mImsUtBinder).setListener(captor.capture());
        ImsUt.IImsUtListenerProxy proxy = captor.getValue();
        assertNotNull(proxy);

        doReturn(2).when(mImsUtBinder).queryCLIR();
        mImsUt.queryCLIR(Message.obtain(mHandler, MSG_QUERY));

        Bundle result = new Bundle();
        result.putIntArray(ImsUtListener.BUNDLE_KEY_CLIR, new int[] {
                ImsSsInfo.CLIR_OUTGOING_INVOCATION, ImsSsInfo.CLIR_STATUS_PROVISIONED_PERMANENT});
        // This is deprecated, will be converted from Bundle -> ImsSsInfo
        proxy.utConfigurationQueried(null, 2 /*id*/, result);
        processAllMessages();


        ImsSsInfo info = mHandler.getPendingImsSsInfo();
        assertNotNull(info);
        assertEquals(ImsSsInfo.CLIR_OUTGOING_INVOCATION, info.getClirOutgoingState());
        assertEquals(ImsSsInfo.CLIR_STATUS_PROVISIONED_PERMANENT,
                info.getClirInterrogationStatus());
    }

    @Test
    @SmallTest
    public void testClipConversionCompat() throws Exception {
        ArgumentCaptor<ImsUt.IImsUtListenerProxy> captor =
                ArgumentCaptor.forClass(ImsUt.IImsUtListenerProxy.class);
        ImsUt mImsUt = new ImsUt(mImsUtBinder);
        verify(mImsUtBinder).setListener(captor.capture());
        ImsUt.IImsUtListenerProxy proxy = captor.getValue();
        assertNotNull(proxy);

        doReturn(2).when(mImsUtBinder).queryCLIP();
        mImsUt.queryCLIP(Message.obtain(mHandler, MSG_QUERY));

        ImsSsInfo info = new ImsSsInfo.Builder(ImsSsInfo.ENABLED).setProvisionStatus(
                ImsSsInfo.CLIR_STATUS_PROVISIONED_PERMANENT).build();
        Bundle result = new Bundle();
        result.putParcelable(ImsUtListener.BUNDLE_KEY_SSINFO, info);
        // This is deprecated, will be converted from Bundle -> ImsSsInfo
        proxy.utConfigurationQueried(null, 2 /*id*/, result);
        processAllMessages();

        ImsSsInfo resultInfo = mHandler.getPendingImsSsInfo();
        assertNotNull(resultInfo);
        assertEquals(info.getStatus(), resultInfo.getStatus());
        assertEquals(info.getProvisionStatus(), resultInfo.getProvisionStatus());
    }
}
