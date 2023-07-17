/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.analytics;

import static android.os.Build.VERSION.INCREMENTAL;

import static com.android.internal.telephony.analytics.TelephonyAnalyticsDatabase.DATE_FORMAT;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.database.Cursor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;

public class SmsMmsAnalyticsProviderTest {
    @Mock TelephonyAnalyticsUtil mTelephonyAnalyticsUtil;

    SmsMmsAnalyticsProvider mSmsMmsAnalyticsProvider;
    private TelephonyAnalyticsUtil mMockTelephonyAnalyticsUtil;
    private static final String[] SMS_MMS_INSERTION_PROJECTION = {
        TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable._ID,
        TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.COUNT
    };

    @Mock Cursor mCursor;
    final String mCreateTableQuery =
            "CREATE TABLE IF NOT EXISTS SmsMmsDataLogs("
                    + "_id INTEGER PRIMARY KEY,"
                    + "LogDate DATE ,"
                    + "SmsMmsStatus TEXT DEFAULT '',"
                    + "SmsMmsType TEXT DEFAULT '',"
                    + "SlotID INTEGER , "
                    + "RAT TEXT DEFAULT '',"
                    + "FailureReason TEXT DEFAULT '',"
                    + "ReleaseVersion TEXT DEFAULT '' , "
                    + "Count INTEGER DEFAULT 1 );";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSmsMmsAnalyticsProvider = new SmsMmsAnalyticsProvider(mTelephonyAnalyticsUtil, 0);
        mMockTelephonyAnalyticsUtil = mock(TelephonyAnalyticsUtil.class);
        verify(mTelephonyAnalyticsUtil).createTable(mCreateTableQuery);
    }

    @Test
    public void testCursor() {
        assert (mCursor != null);
        assert (mTelephonyAnalyticsUtil != null);
    }

    private ContentValues getContentValues(
            String status, String type, String rat, String failureReason) {
        ContentValues values = new ContentValues();
        String dateToday = DATE_FORMAT.format(Calendar.getInstance().toInstant());
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.LOG_DATE, dateToday);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_STATUS, status);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_TYPE, type);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT, rat);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SLOT_ID, 0);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.FAILURE_REASON, failureReason);
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RELEASE_VERSION, INCREMENTAL);

        return values;
    }

    private void mockAndVerifyCall(String selection, String[] selectionArgs) {
        when(mTelephonyAnalyticsUtil.getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mCursor);

        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        eq(SMS_MMS_INSERTION_PROJECTION),
                        eq(selection),
                        eq(selectionArgs),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
    }

    @Test
    public void testFailureSms() {
        String status = "Failure";
        String type = "SMS Outgoing";
        String rat = "LTE";
        String failureReason = "SIM_ABSENT";
        final String smsMmsInsertionFailureSelection =
                TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.LOG_DATE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_STATUS
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_TYPE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SLOT_ID
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.FAILURE_REASON
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RELEASE_VERSION
                        + " = ? ";
        ContentValues values = getContentValues(status, type, rat, failureReason);

        String[] selectionArgs =
                new String[] {
                    values.getAsString(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.LOG_DATE),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_STATUS),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_TYPE),
                    values.getAsString(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT),
                    values.getAsString(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SLOT_ID),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.FAILURE_REASON),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RELEASE_VERSION)
                };
        mSmsMmsAnalyticsProvider.insertDataToDb(status, type, rat, failureReason);
        mockAndVerifyCall(smsMmsInsertionFailureSelection, selectionArgs);
    }

    @Test
    public void testSuccessSms() {
        String status = "Success";
        String type = "SMS Outgoing";
        String rat = "LTE";
        String failureReason = "SIM_ABSENT";

        final String smsMmsInsertionSuccessSelection =
                TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.LOG_DATE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_TYPE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_STATUS
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SLOT_ID
                        + " = ? ";

        ContentValues values = getContentValues(status, type, rat, failureReason);
        String[] selectionArgs =
                new String[] {
                    values.getAsString(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.LOG_DATE),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_TYPE),
                    values.getAsString(
                            TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SMS_MMS_STATUS),
                    values.getAsString(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.SLOT_ID)
                };

        mSmsMmsAnalyticsProvider.insertDataToDb(status, type, rat, failureReason);

        mockAndVerifyCall(smsMmsInsertionSuccessSelection, selectionArgs);
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWhenEntryNotExist() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.COUNT, 5);
        when(mCursor.moveToFirst()).thenReturn(false);
        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(mCursor, values);
        verify(mTelephonyAnalyticsUtil)
                .insert(eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME), eq(values));
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWhenCursorNull() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.COUNT, 5);
        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(null, values);
        verify(mTelephonyAnalyticsUtil)
                .insert(eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME), eq(values));
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWhenEntryExists() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT, "LTE");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex("_id")).thenReturn(0);
        when(mCursor.getColumnIndex("Count")).thenReturn(1);
        when(mCursor.getInt(0)).thenReturn(100);
        when(mCursor.getInt(1)).thenReturn(2);

        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(mCursor, values);

        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.COUNT, 3);

        String updateSelection = TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable._ID + " = ? ";
        String[] updateSelectionArgs = {String.valueOf(100)};

        verify(mTelephonyAnalyticsUtil)
                .update(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        eq(values),
                        eq(updateSelection),
                        eq(updateSelectionArgs));
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWithInvalidIdColumnIndex() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT, "LTE");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex("_id")).thenReturn(-1);
        when(mCursor.getColumnIndex("Count")).thenReturn(1);
        when(mCursor.getInt(0)).thenReturn(100);
        when(mCursor.getInt(1)).thenReturn(2);
        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(mCursor, values);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWithInvalidCountColumnIndex() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT, "LTE");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex("_id")).thenReturn(0);
        when(mCursor.getColumnIndex("Count")).thenReturn(-1);
        when(mCursor.getInt(0)).thenReturn(100);
        when(mCursor.getInt(1)).thenReturn(2);
        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(mCursor, values);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWithInvalidColumnIndex() {
        ContentValues values = new ContentValues();
        values.put(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.RAT, "LTE");
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex("_id")).thenReturn(-1);
        when(mCursor.getColumnIndex("Count")).thenReturn(-1);
        when(mCursor.getInt(0)).thenReturn(100);
        when(mCursor.getInt(1)).thenReturn(2);
        mSmsMmsAnalyticsProvider.updateIfEntryExistsOtherwiseInsert(mCursor, values);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testDeleteWhenDateEqualsToday() {
        String status = "Success";
        String type = "SMS Outgoing";
        String rat = "LTE";
        String failureReason = "SIM_ABSENT";

        String dateToday = DATE_FORMAT.format(Calendar.getInstance().toInstant());
        mSmsMmsAnalyticsProvider.setDateOfDeletedRecordsSmsMmsTable(dateToday);
        mSmsMmsAnalyticsProvider.insertDataToDb(status, type, rat, failureReason);
        verify(mTelephonyAnalyticsUtil, times(0))
                .delete(anyString(), anyString(), any(String[].class));
    }

    @Test
    public void testDeleteWhenDateNotNullAndNotEqualsToday() {
        String status = "Success";
        String type = "SMS Outgoing";
        String rat = "LTE";
        String failureReason = "SIM_ABSENT";
        String dateToday = "1965-10-12";
        mSmsMmsAnalyticsProvider.setDateOfDeletedRecordsSmsMmsTable(dateToday);
        mSmsMmsAnalyticsProvider.insertDataToDb(status, type, rat, failureReason);
        verify(mTelephonyAnalyticsUtil, times(1))
                .deleteOverflowAndOldData(anyString(), anyString(), anyString());
    }

    @Test
    public void testAggregate() throws NoSuchFieldException, IllegalAccessException {
        final CountDownLatch latch = new CountDownLatch(9);
        Cursor mockCursor = mock(Cursor.class);
        when(mMockTelephonyAnalyticsUtil.getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mockCursor);
        when(mMockTelephonyAnalyticsUtil.getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        anyString(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mockCursor);
        when(mMockTelephonyAnalyticsUtil.getCountFromCursor(eq(mockCursor)))
                .thenReturn(
                        80L /*totalOutgoingSms*/,
                        70L /*totalIncomingSms*/,
                        60L /*totalOutgoingMms*/,
                        50L /*totalIncomingMms*/,
                        40L /*totalFailedOutgoingSms*/,
                        30L /*totalFailedIncomingSms*/,
                        20L /*totalFailedOutgoingMms*/,
                        10L /*totalFailedIncomingMms*/);

        when(mockCursor.getColumnIndex("RAT")).thenReturn(0 /*ratIndex*/);
        when(mockCursor.getColumnIndex("count")).thenReturn(1 /*countIndex*/);
        when(mockCursor.moveToNext()).thenReturn(true, false);
        when(mockCursor.getString(0 /*ratIndex*/)).thenReturn("LTE" /* RAT*/);
        when(mockCursor.getInt(1 /*countIndex*/)).thenReturn(10 /* Count */);

        SmsMmsAnalyticsProvider smsMmsAnalyticsProvider =
                new SmsMmsAnalyticsProvider(mMockTelephonyAnalyticsUtil, 0 /* slotIndex */);
        ArrayList<String> received = smsMmsAnalyticsProvider.aggregate();

        verify(mMockTelephonyAnalyticsUtil, times(8))
                .getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
        verify(mMockTelephonyAnalyticsUtil, times(1))
                .getCursor(
                        eq(TelephonyAnalyticsDatabase.SmsMmsAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        anyString(),
                        isNull(),
                        isNull(),
                        isNull());
        verify(mMockTelephonyAnalyticsUtil, times(8)).getCountFromCursor(eq(mockCursor));
        verify(mockCursor).getColumnIndex("RAT");
        verify(mockCursor).getColumnIndex("count");

        assertEquals(
                "Total Outgoing Sms Count = 80",
                received.get(0 /* array index for totalOutgoingSms */));
        assertEquals(
                "Total Incoming Sms Count = 70",
                received.get(1 /* array index for totalIncomingSms */));
        assertEquals(
                "Failed Outgoing SMS Count = 40 Percentage failure rate for Outgoing SMS :50.00%",
                received.get(2 /* array index for totalFailedOutgoingSms */));
        assertEquals(
                "Failed Incoming SMS Count = 30 Percentage failure rate for Incoming SMS :42.86%",
                received.get(3 /* array index for totalFailedIncomingSms */));
        assertEquals(
                "Overall Fail Percentage = 46.67%",
                received.get(4 /* array index for overall fail percentage */));
        assertEquals(
                "Failed SMS Count for RAT : LTE = 10, Percentage = 6.67%",
                received.get(5 /* array index for  failedSmsTypeCountByRat */));
    }

    @After
    public void tearDown() {
        mCursor = null;
        mTelephonyAnalyticsUtil = null;
        mMockTelephonyAnalyticsUtil = null;
        mSmsMmsAnalyticsProvider = null;
    }
}
