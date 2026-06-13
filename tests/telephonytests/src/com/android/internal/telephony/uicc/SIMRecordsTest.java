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

package com.android.internal.telephony.uicc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class SIMRecordsTest extends TelephonyTest {
    private static final List<String> SHORT_FPLMNS_LIST = Arrays.asList("12345", "123456", "09876");
    private static final List<String> LONG_FPLMNS_LIST =
            Arrays.asList("12345", "123456", "09876", "123456", "098237");
    private static final List<String> EMPTY_FPLMN_LIST = new ArrayList<>();
    private static final int EF_SIZE = 12;
    private static final int MAX_NUM_FPLMN = 4;

    @Mock private IccFileHandler mFhMock;

    private SIMRecordsUT mSIMRecordsUT;
    private TestLooper mTestLooper;
    private Handler mTestHandler;
    private SIMRecordsReceiver mSIMRecordsReceiver;
    private SIMRecords mSIMRecord;

    private class SIMRecordsUT extends SIMRecords {
        SIMRecordsUT(UiccCardApplication app, Context c,
                CommandsInterface ci, IccFileHandler mFhMock) {
            super(app, c, ci);
            mFh = mFhMock;
        }
    }

    private class SIMRecordsReceiver {
        private SIMRecordsUT mSIMRecordsUT;

        public void set(SIMRecordsUT m) {
            mSIMRecordsUT = m;
        }

        public SIMRecordsUT get() {
            return mSIMRecordsUT;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        mSIMRecordsReceiver = new SIMRecordsReceiver();
        mTestHandler.post(
                () -> {
                    SIMRecordsUT mSIMRecords =
                            new SIMRecordsUT(
                                    mUiccCardApplication3gpp,
                                    mContext,
                                    mSimulatedCommands,
                                    mFhMock);
                    mSIMRecordsReceiver.set(mSIMRecords);
                });
        mTestLooper.dispatchAll();
        mSIMRecordsUT = mSIMRecordsReceiver.get();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetForbiddenPlmnsPad() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, SHORT_FPLMNS_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(SHORT_FPLMNS_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(SHORT_FPLMNS_LIST.size(), (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    @Test
    public void testSetForbiddenPlmnsTruncate() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, LONG_FPLMNS_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(LONG_FPLMNS_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(MAX_NUM_FPLMN, (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    @Test
    public void testSetForbiddenPlmnsClear() {
        setUpSetForbiddenPlmnsTests();
        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.setForbiddenPlmns(message, EMPTY_FPLMN_LIST);
        mTestLooper.dispatchAll();

        byte[] encodedFplmn = IccUtils.encodeFplmns(EMPTY_FPLMN_LIST, EF_SIZE);
        AsyncResult ar = (AsyncResult) message.obj;
        assertEquals(EMPTY_FPLMN_LIST.size(), (int) ar.result);
        verify(mFhMock).getEFTransparentRecordSize(eq(mSIMRecordsUT.EF_FPLMN), any(Message.class));
        verify(mFhMock).updateEFTransparent(
                eq(mSIMRecordsUT.EF_FPLMN), eq(encodedFplmn), any(Message.class));
    }

    private void setUpSetForbiddenPlmnsTests() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                AsyncResult.forMessage(response, EF_SIZE, null);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .getEFTransparentRecordSize(anyInt(), any(Message.class));
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(2);
                AsyncResult.forMessage(response, true, null);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .updateEFTransparent(anyInt(), any(byte[].class), any(Message.class));
    }

    @Test
    public void testGetForbiddenPlmns() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                byte[] encodedFplmn = IccUtils.encodeFplmns(SHORT_FPLMNS_LIST, EF_SIZE);
                AsyncResult.forMessage(response, encodedFplmn, null);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertEquals(SHORT_FPLMNS_LIST.toArray(new String[SHORT_FPLMNS_LIST.size()]),
                (String[]) ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsException() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                AsyncResult.forMessage(response, null, new CommandException(
                        CommandException.Error.OPERATION_NOT_ALLOWED));
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertTrue(ar.exception instanceof CommandException);
        assertTrue(((CommandException) ar.exception).getCommandError() ==
                CommandException.Error.OPERATION_NOT_ALLOWED);
        assertNull(ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsNull() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                AsyncResult.forMessage(response);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertNull(ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsEmptyList() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                byte[] encodedFplmn = IccUtils.encodeFplmns(EMPTY_FPLMN_LIST, EF_SIZE);
                AsyncResult.forMessage(response, encodedFplmn, null);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertEquals(EMPTY_FPLMN_LIST.toArray(new String[EMPTY_FPLMN_LIST.size()]),
                (String[]) ar.result);
    }

    @Test
    public void testGetForbiddenPlmnsInvalidLength() {
        doAnswer(
            invocation -> {
                Message response = invocation.getArgument(1);
                AsyncResult.forMessage(response, new byte[] { (byte) 0xFF, (byte) 0xFF }, null);
                response.sendToTarget();
                return null;
            })
            .when(mFhMock)
            .loadEFTransparent(eq(SIMRecords.EF_FPLMN), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mSIMRecordsUT.getForbiddenPlmns(message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        assertNull(ar.exception);
        assertNull(ar.result);
    }

    @Test
    public void testEfCfisTooShortDoesNotCrash() {
        // Mock EF_CFIS to return a 1-byte array (record size 1), which is invalid
        // per spec (TS 51.011 requires 16 bytes).
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2); // Message is at index 2
                    AsyncResult.forMessage(response, new byte[]{0x01}, null);
                    response.sendToTarget();
                    return null;
                }
        ).when(mFhMock).loadEFLinearFixed(eq(SIMRecords.EF_CFIS), anyInt(), any(Message.class));

        // Mock EF_CFF_CPHS to return valid data (1 byte) to isolate EF_CFIS failure
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, new byte[]{0x00}, null);
                    response.sendToTarget();
                    return null;
                }
        ).when(mFhMock).loadEFTransparent(eq(SIMRecords.EF_CFF_CPHS), any(Message.class));

        // Set state to trigger onAllRecordsLoaded() when the event completes
        setProtectedField(mSIMRecordsUT, "mRecordsRequested", true);
        setProtectedField(mSIMRecordsUT, "mRecordsToLoad", 1);

        // Trigger the record loading by sending the event directly to the handler.
        // This simulates the completion of the EF_CFIS load.
        Message msg = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_CFIS_DONE);
        AsyncResult.forMessage(msg, new byte[]{0x01}, null);
        mSIMRecordsUT.handleMessage(msg);
        mTestLooper.dispatchAll();

        // If the bug is present, the looper dispatch above will throw
        // ArrayIndexOutOfBoundsException when mSIMRecordsUT attempts to access
        // mEfCfis[1] in setVoiceCallForwardingFlagFromSimRecords().
        //
        // If the fix is applied, it will handle it gracefully and not crash.
        // We verify that the forwarding status is set to UNKNOWN (-1) since both
        // EF_CFIS and EF_CFF are invalid/missing.
        assertEquals(IccRecords.CALL_FORWARDING_STATUS_UNKNOWN,
                mSIMRecordsUT.getVoiceCallForwardingFlag());
    }

    @Test
    public void testEfCffEmptyDoesNotCrash() {
        // Mock EF_CFIS to fail/be invalid so the code falls back to EF_CFF
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, null, new RuntimeException("Simulated error"));
                    response.sendToTarget();
                    return null;
                }
        ).when(mFhMock).loadEFLinearFixed(eq(SIMRecords.EF_CFIS), anyInt(), any(Message.class));

        // Mock EF_CFF_CPHS to return 0 bytes (invalid, spec requires >= 1 byte)
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(1);
                    AsyncResult.forMessage(response, new byte[0], null); // 0 bytes!
                    response.sendToTarget();
                    return null;
                }
        ).when(mFhMock).loadEFTransparent(eq(SIMRecords.EF_CFF_CPHS), any(Message.class));

        // Set state to trigger onAllRecordsLoaded() when the event completes
        setProtectedField(mSIMRecordsUT, "mRecordsRequested", true);
        setProtectedField(mSIMRecordsUT, "mRecordsToLoad", 1);

        // Trigger the CFF load completion
        Message msg = mSIMRecordsUT.obtainMessage(SIMRecords.EVENT_GET_CFF_DONE);
        AsyncResult.forMessage(msg, new byte[0], null);
        mSIMRecordsUT.handleMessage(msg);
        mTestLooper.dispatchAll();

        // If the bug is present, this will throw ArrayIndexOutOfBoundsException
        // when attempting to access mEfCff[0] in setVoiceCallForwardingFlagFromSimRecords().
        //
        // If fixed, it will complete without crashing.
    }

    private void setProtectedField(Object obj, String fieldName, Object value) {
        try {
            Class<?> clazz = obj.getClass();
            java.lang.reflect.Field field = null;
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
