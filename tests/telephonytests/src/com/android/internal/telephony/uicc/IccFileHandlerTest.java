/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class IccFileHandlerTest {
    CommandsInterface mCi;
    IccFileHandler mIccFileHandler;
    private TestLooper mTestLooper;
    private Handler mTestHandler;

    @Before
    public void setUp() throws Exception {
        mCi = mock(CommandsInterface.class);
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        mTestHandler.post(
                () -> mIccFileHandler = new IccFileHandler(mCi) {
                    @Override
                    protected String getEFPath(int efid) {
                        switch (efid) {
                            case 0x4f30:
                            case 0x4f3a:
                                return "3F007F105F3A";
                        }
                        return "";
                    }

                    @Override
                    protected void logd(String s) {
                        Log.d("IccFileHandlerTest", s);
                    }

                    @Override
                    protected void loge(String s) {
                        Log.d("IccFileHandlerTest", s);
                    }
                });


        mTestLooper.dispatchAll();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestLooper != null) {
            mTestLooper.dispatchAll();
            mTestLooper = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
        mTestHandler = null;
        mIccFileHandler = null;
        mCi = null;
    }

    @Test
    public void loadEFLinearFixed_WithNullPath() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixed(0, "", 0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFLinearFixed() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixed(0, 0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFImgLinearFixed() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFImgLinearFixed(0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void getEFLinearRecordSize_WithNullPath() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.getEFLinearRecordSize(0, "", message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void getEFLinearRecordSize() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.getEFLinearRecordSize(0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void getEFTransparentRecordSize() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.getEFTransparentRecordSize(0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFLinearFixedAll_FileNotFoundAtGetRecord() throws InterruptedException {
        int efId = 0x4f30;
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    IccIoResult iir = new IccIoResult(0x94, 0x00,
                            IccUtils.hexStringToBytes(null));
                    AsyncResult.forMessage(response, iir, null);
                    mTestHandler.postDelayed(latch::countDown, 100);
                    response.sendToTarget();
                    return null;
                }).when(mCi).iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(),
                anyInt(), isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(efId, null, message);
        mTestLooper.startAutoDispatch();
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        AsyncResult ar = (AsyncResult) message.obj;
        assertNotNull(ar);
        assertTrue(ar.exception instanceof IccFileNotFound);
    }

    @Test
    public void loadEFLinearFixedAll_FileNotFoundAtReadRecord() throws InterruptedException {
        int efid = 0x4f30;
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    IccIoResult iir = null;
                    if (response.what == 6) {
                        iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(
                                "000000454F30040000FFFF01020145"));
                        latch.countDown();
                    } else if (response.what == 7) {
                        iir = new IccIoResult(0x94, 0x00, IccUtils.hexStringToBytes(null));
                        mTestHandler.postDelayed(latch::countDown, 100);
                    }
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                }).when(mCi).iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(),
                anyInt(), isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(efid, null, message);
        mTestLooper.startAutoDispatch();
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        AsyncResult ar = (AsyncResult) message.obj;
        assertNotNull(ar);
        assertTrue(ar.exception instanceof IccFileNotFound);
    }

    @Test
    public void loadEFLinearFixedAll_ExceptionAtGetRecord() throws InterruptedException {
        int efid = 0x4f30;
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, null, new CommandException(
                            CommandException.Error.OPERATION_NOT_ALLOWED));
                    response.sendToTarget();
                    mTestHandler.postDelayed(latch::countDown, 100);
                    return null;
                }).when(mCi).iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(),
                anyInt(), isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(efid, null, message);
        mTestLooper.startAutoDispatch();
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertSame(CommandException.Error.OPERATION_NOT_ALLOWED,
                ((CommandException) ar.exception).getCommandError());
        assertNull(ar.result);
    }

    @Test
    public void loadEFLinearFixedAll_ExceptionAtReadRecord() throws InterruptedException {
        int efid = 0x4f30;
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    String hexString = null;
                    IccIoResult iir = null;
                    if (response.what == 6) {
                        hexString = "000000454F30040000FFFF01020145";
                        iir = new IccIoResult(0x90, 0x00, IccUtils.hexStringToBytes(hexString));
                        AsyncResult.forMessage(response, iir, null);
                        latch.countDown();
                    } else if (response.what == 7) {
                        AsyncResult.forMessage(response, null, new CommandException(
                                CommandException.Error.OPERATION_NOT_ALLOWED));
                        mTestHandler.postDelayed(latch::countDown, 100);
                    }
                    response.sendToTarget();
                    mTestHandler.postDelayed(latch::countDown, 100);
                    return null;
                }).when(mCi).iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(),
                anyInt(),
                isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(efid, null, message);
        mTestLooper.startAutoDispatch();
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertSame(CommandException.Error.OPERATION_NOT_ALLOWED,
                ((CommandException) ar.exception).getCommandError());
        assertNull(ar.result);
    }

    @Test
    public void loadEFLinearFixedAll_FullLoop() throws InterruptedException {
        int efid = 0x4f30;
        final CountDownLatch latch = new CountDownLatch(2);
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    String hexString = null;
                    if (response.what == 6) {
                        hexString = "000000454F30040000FFFF01020145";
                        latch.countDown();
                    } else if (response.what == 7) {
                        try {
                            IccFileHandler.LoadLinearFixedContext lc =
                                    (IccFileHandler.LoadLinearFixedContext) response.obj;
                            if (mIccFileHandler.getEfid(lc) == efid) {
                                hexString =
                                        "A814C0034F3A01C1034F3202C5034F0904C9034F2109A90FC3034F611"
                                                + "5C4034F1108CA034F5017AA0FC2034F4A03C7034F4B06CB"
                                                + "034F4F16FFFFFFFFFFFFFFFFFFFFFFFFFF";
                            }
                            mTestHandler.postDelayed(latch::countDown, 100);

                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.e("UsimFH", e.getMessage());
                        }

                    }
                    IccIoResult iir = new IccIoResult(0x90, 0x00,
                            IccUtils.hexStringToBytes(hexString));
                    AsyncResult.forMessage(response, iir, null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(efid, null, message);
        mTestLooper.startAutoDispatch();
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        AsyncResult ar = (AsyncResult) message.obj;
        assertNotNull(ar);
        ArrayList<byte[]> results = (ArrayList<byte[]>) ar.result;
        assertEquals(
                "A814C0034F3A01C1034F3202C5034F0904C9034F2109A90FC3034F6115C4034F1108CA03"
                        + "4F5017AA0FC2034F4A03C7034F4B06CB034F4F16FFFFFFFFFFFFFFFFFFFFFFFFFF",
                IccUtils.bytesToHexString(results.get(0)));
        verify(mCi, times(2)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFLinearFixedAll_WithNullPath() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(0, "", message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFLinearFixedAll() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFLinearFixedAll(0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFTransparent() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFTransparent(0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFTransparent_WithZeroSize() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFTransparent(0, 0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void loadEFImgTransparent() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        isNull(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.loadEFImgTransparent(0, 0, 0, 0, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void updateEFLinearFixed_WithNullPath() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        anyString(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.updateEFLinearFixed(0, "", 0, new byte[10], null, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), anyString(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void updateEFLinearFixed() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        anyString(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.updateEFLinearFixed(0, 0, new byte[10], null, message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), anyString(), isNull(), isNull(), any(Message.class));
    }

    @Test
    public void updateEFTransparent() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(9);
                    AsyncResult.forMessage(response, "Success", null);
                    response.sendToTarget();
                    return null;
                })
                .when(mCi)
                .iccIOForApp(anyInt(), anyInt(), anyString(), anyInt(), anyInt(), anyInt(),
                        anyString(), isNull(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mIccFileHandler.updateEFTransparent(0, new byte[10], message);
        verify(mCi, times(1)).iccIOForApp(anyInt(), anyInt(), anyString(),
                anyInt(), anyInt(), anyInt(), anyString(), isNull(), isNull(), any(Message.class));
    }
}
