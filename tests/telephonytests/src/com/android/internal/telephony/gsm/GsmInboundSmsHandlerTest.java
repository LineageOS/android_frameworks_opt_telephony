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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.UserManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Telephony;
import android.telephony.*;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.InboundSmsHandler;
import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.SmsStorageMonitor;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.util.HexDump;
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

import java.lang.reflect.Method;
import java.util.ArrayList;

public class GsmInboundSmsHandlerTest extends TelephonyTest {
    @Mock
    private SmsStorageMonitor mSmsStorageMonitor;
    @Mock
    private android.telephony.SmsMessage mSmsMessage;
    @Mock
    private SmsMessage mGsmSmsMessage;
    @Mock
    private SmsHeader mSmsHeader;
    @Mock
    private InboundSmsTracker mInboundSmsTrackerPart1;
    @Mock
    private InboundSmsTracker mInboundSmsTrackerPart2;

    private GsmInboundSmsHandler mGsmInboundSmsHandler;
    private TelephonyManager mTelephonyManager;

    private ContentValues mInboundSmsTrackerCV = new ContentValues();
    // For multi-part SMS
    private ContentValues mInboundSmsTrackerCVPart1;
    private ContentValues mInboundSmsTrackerCVPart2;

    byte[] mSmsPdu = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF};

    public static class FakeSmsContentProvider extends MockContentProvider {
        private String[] mRawColumns = {"_id",
                "date",
                "reference_number",
                "count",
                "sequence",
                "destination_port",
                "address",
                "sub_id",
                "pdu"};
        private MatrixCursor mRawCursor = new MatrixCursor(mRawColumns);
        private int mNumRows = 0;

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            Uri newUri = null;
            if (uri.compareTo(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw")) == 0) {
                if (values != null) {
                    mRawCursor.addRow(convertRawCVtoArrayList(values));
                    mNumRows++;
                    newUri = Uri.withAppendedPath(uri, "" + mNumRows);
                }
            }
            logd("insert called, new numRows: " + mNumRows);
            return newUri;
        }

        private ArrayList<Object> convertRawCVtoArrayList(ContentValues values) {
            ArrayList<Object> newRow = new ArrayList<>();
            for (String key : mRawColumns) {
                if (values.containsKey(key)) {
                    newRow.add(values.get(key));
                } else if (key.equals("_id")) {
                    newRow.add(mNumRows + 1);
                } else {
                    newRow.add(null);
                }
            }
            return newRow;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                            String sortOrder) {
            logd("query called for: " + selection);
            MatrixCursor cursor = new MatrixCursor(projection);
            if (mNumRows > 0) {
                // parse selection and selectionArgs
                String[] paramName = null;
                String[] paramValue = null;
                if (selection != null) {
                    selection = selection.toLowerCase();
                    String[] selectionParams = selection.toLowerCase().split("and");
                    int i = 0;
                    int j = 0;
                    paramName = new String[selectionParams.length];
                    paramValue = new String[selectionParams.length];
                    for (String param : selectionParams) {
                        String[] paramWithArg = param.split("=");
                        paramName[i] = paramWithArg[0].trim();
                        if (param.contains("?")) {
                            paramValue[i] = selectionArgs[j];
                            j++;
                        } else {
                            paramValue[i] = paramWithArg[1].trim();
                        }
                    }
                }

                mRawCursor.moveToFirst();
                do {
                    ArrayList<Object> row = new ArrayList<>();
                    // filter based on selection parameters if needed
                    if (selection != null) {
                        boolean match = true;
                        for (int i = 0; i < paramName.length; i++) {
                            for (String columnName : mRawColumns) {
                                int columnIndex = mRawCursor.getColumnIndex(columnName);
                                if (columnName.equals(paramName[i]) &&
                                        !mRawCursor.getString(columnIndex).equals(paramValue[i])) {
                                    match = false;
                                    break;
                                }
                            }
                            if (!match) {
                                break;
                            }
                        }
                        // move on to next row if current one does not satisfy selection criteria
                        if (!match) {
                            continue;
                        }
                    }

                    for (String columnName : projection) {
                        int columnIndex = mRawCursor.getColumnIndex(columnName);
                        row.add(mRawCursor.getString(columnIndex));
                    }
                    cursor.addRow(row);
                } while(mRawCursor.moveToNext());
            }
            if (cursor != null) {
                logd("returning rows: " + cursor.getCount());
            }
            return cursor;
        }

        @Override
        public Bundle call(String method, String request, Bundle args) {
            return null;
        }
    }

    private class GsmInboundSmsHandlerTestHandler extends HandlerThread {

        private GsmInboundSmsHandlerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mGsmInboundSmsHandler = GsmInboundSmsHandler.makeInboundSmsHandler(mContext,
                    mSmsStorageMonitor, mPhone);
            setReady(true);
        }
    }

    private IState getCurrentState() {
        try {
            Method method = StateMachine.class.getDeclaredMethod("getCurrentState");
            method.setAccessible(true);
            return (IState) method.invoke(mGsmInboundSmsHandler);
        } catch (Exception e) {
            fail(e.toString());
            return null;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp("GsmInboundSmsHandlerTest");

        mTelephonyManager = TelephonyManager.from(mContext);
        doReturn(true).when(mTelephonyManager).getSmsReceiveCapableForPhone(anyInt(), anyBoolean());
        doReturn(true).when(mSmsStorageMonitor).isStorageAvailable();

        UserManager userManager = (UserManager)mContext.getSystemService(Context.USER_SERVICE);
        doReturn(true).when(userManager).isUserUnlocked();

        try {
            doReturn(new int[]{UserHandle.USER_SYSTEM}).when(mIActivityManager).getRunningUserIds();
        } catch (RemoteException re) {
            fail("Unexpected RemoteException: " + re.getStackTrace());
        }

        mSmsMessage.mWrappedSmsMessage = mGsmSmsMessage;
        doReturn(mSmsPdu).when(mGsmSmsMessage).getPdu();
        doReturn(1).when(mInboundSmsTracker).getMessageCount();
        doReturn(-1).when(mInboundSmsTracker).getDestPort();
        doReturn(mSmsPdu).when(mInboundSmsTracker).getPdu();
        doReturn(mInboundSmsTrackerCV).when(mInboundSmsTracker).getContentValues();

        FakeSmsContentProvider contentProvider = new FakeSmsContentProvider();
        ((MockContentResolver)mContext.getContentResolver()).addProvider(
                Telephony.Sms.CONTENT_URI.getAuthority(), contentProvider);

        new GsmInboundSmsHandlerTestHandler(TAG).start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        if (mGsmInboundSmsHandler.getWakeLock().isHeld()) {
            waitForMs(mGsmInboundSmsHandler.getWakeLockTimeout() + 200);
        }
        assertFalse(mGsmInboundSmsHandler.getWakeLock().isHeld());
        mGsmInboundSmsHandler = null;
        super.tearDown();
    }

    private void transitionFromStartupToIdle() {
        // verify initially in StartupState
        assertEquals("StartupState", getCurrentState().getName());

        // trigger transition to IdleState
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_START_ACCEPTING_SMS);
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testNewSms() {
        transitionFromStartupToIdle();

        // send new SMS to state machine and verify that triggers SMS_DELIVER_ACTION
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getValue().getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testNewSmsFromBlockedNumber_noBroadcastsSent() {
        String blockedNumber = "123456789";
        doReturn(blockedNumber).when(mInboundSmsTracker).getAddress();
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add(blockedNumber);

        transitionFromStartupToIdle();

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS,
                new AsyncResult(null, mSmsMessage, null));
        waitForMs(100);

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testBroadcastSms() {
        transitionFromStartupToIdle();

        doReturn(0).when(mInboundSmsTracker).getDestPort();
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_BROADCAST_SMS,
                mInboundSmsTracker);
        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getValue().getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testInjectSms() {
        transitionFromStartupToIdle();

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_INJECT_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getValue().getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    private void prepareMultiPartSms() {
        // Part 1
        mInboundSmsTrackerCVPart1 = new ContentValues();
        mInboundSmsTrackerCVPart1.put("destination_port", 1 << 16);
        mInboundSmsTrackerCVPart1.put("pdu", HexDump.toHexString(mSmsPdu));
        mInboundSmsTrackerCVPart1.put("address", "1234567890");
        mInboundSmsTrackerCVPart1.put("reference_number", 1);
        mInboundSmsTrackerCVPart1.put("sequence", 1);
        mInboundSmsTrackerCVPart1.put("count", 2);

        doReturn(2).when(mInboundSmsTrackerPart1).getMessageCount();
        doReturn(1).when(mInboundSmsTrackerPart1).getReferenceNumber();
        doReturn("1234567890").when(mInboundSmsTrackerPart1).getAddress();
        doReturn(1).when(mInboundSmsTrackerPart1).getSequenceNumber();
        doReturn(1).when(mInboundSmsTrackerPart1).getIndexOffset();
        doReturn(-1).when(mInboundSmsTrackerPart1).getDestPort();
        doReturn(mSmsPdu).when(mInboundSmsTrackerPart1).getPdu();
        doReturn(mInboundSmsTrackerCVPart1).when(mInboundSmsTrackerPart1).getContentValues();

        // Part 2
        mInboundSmsTrackerCVPart2 = new ContentValues();
        mInboundSmsTrackerCVPart2.put("destination_port", 1 << 16);
        mInboundSmsTrackerCVPart2.put("pdu", HexDump.toHexString(mSmsPdu));
        mInboundSmsTrackerCVPart2.put("address", "1234567890");
        mInboundSmsTrackerCVPart2.put("reference_number", 1);
        mInboundSmsTrackerCVPart2.put("sequence", 2);
        mInboundSmsTrackerCVPart2.put("count", 2);

        doReturn(2).when(mInboundSmsTrackerPart2).getMessageCount();
        doReturn(1).when(mInboundSmsTrackerPart2).getReferenceNumber();
        doReturn("1234567890").when(mInboundSmsTrackerPart2).getAddress();
        doReturn(2).when(mInboundSmsTrackerPart2).getSequenceNumber();
        doReturn(1).when(mInboundSmsTrackerPart2).getIndexOffset();
        doReturn(-1).when(mInboundSmsTrackerPart2).getDestPort();
        doReturn(mSmsPdu).when(mInboundSmsTrackerPart2).getPdu();
        doReturn(mInboundSmsTrackerCVPart2).when(mInboundSmsTrackerPart2).getContentValues();
    }

    @Test
    @MediumTest
    public void testMultiPartSms() {
        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms();

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_DELIVER_ACTION,
                intentArgumentCaptor.getValue().getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();

        intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcast(intentArgumentCaptor.capture());
        assertEquals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION,
                intentArgumentCaptor.getAllValues().get(1).getAction());
        assertEquals("WaitingState", getCurrentState().getName());

        mContextFixture.sendBroadcastToOrderedBroadcastReceivers();
        waitForMs(50);

        assertEquals("IdleState", getCurrentState().getName());
    }

    @Test
    @MediumTest
    public void testMultipartSmsFromBlockedNumber_noBroadcastsSent() {
        mFakeBlockedNumberContentProvider.mBlockedNumbers.add("1234567890");

        transitionFromStartupToIdle();

        // prepare SMS part 1 and part 2
        prepareMultiPartSms();

        mSmsHeader.concatRef = new SmsHeader.ConcatRef();
        doReturn(mSmsHeader).when(mGsmSmsMessage).getUserDataHeader();
        doReturn(mInboundSmsTrackerPart1).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());

        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        // State machine should go back to idle and wait for second part
        assertEquals("IdleState", getCurrentState().getName());

        doReturn(mInboundSmsTrackerPart2).when(mTelephonyComponentFactory)
                .makeInboundSmsTracker(any(byte[].class), anyLong(), anyInt(), anyBoolean(),
                        anyString(), anyInt(), anyInt(), anyInt(), anyBoolean());
        mGsmInboundSmsHandler.sendMessage(InboundSmsHandler.EVENT_NEW_SMS, new AsyncResult(null,
                mSmsMessage, null));
        waitForMs(100);

        verify(mContext, never()).sendBroadcast(any(Intent.class));
        assertEquals("IdleState", getCurrentState().getName());
    }
}
