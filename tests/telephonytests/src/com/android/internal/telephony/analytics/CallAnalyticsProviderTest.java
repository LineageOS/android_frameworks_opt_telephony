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

import static com.android.internal.telephony.analytics.TelephonyAnalyticsDatabase.CallAnalyticsTable;
import static com.android.internal.telephony.analytics.TelephonyAnalyticsDatabase.DATE_FORMAT;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;

public class CallAnalyticsProviderTest {

    @Mock TelephonyAnalyticsUtil mTelephonyAnalyticsUtil;
    @Mock Cursor mCursor;
    private CallAnalyticsProvider mCallAnalyticsProvider;
    private ContentValues mContentValues;

    enum CallStatus {
        SUCCESS("Success"),
        FAILURE("Failure");
        public String value;

        CallStatus(String value) {
            this.value = value;
        }
    }

    enum CallType {
        NORMAL("Normal Call"),
        SOS("SOS Call");
        public String value;

        CallType(String value) {
            this.value = value;
        }
    }

    final String[] mCallInsertionProjection = {
        TelephonyAnalyticsDatabase.CallAnalyticsTable._ID,
        TelephonyAnalyticsDatabase.CallAnalyticsTable.COUNT
    };

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final String createCallAnalyticsTable =
                "CREATE TABLE IF NOT EXISTS "
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.TABLE_NAME
                        + "("
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable._ID
                        + " INTEGER PRIMARY KEY,"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.LOG_DATE
                        + " DATE ,"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.CALL_STATUS
                        + " TEXT DEFAULT '',"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.CALL_TYPE
                        + " TEXT DEFAULT '',"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.RAT
                        + " TEXT DEFAULT '',"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.SLOT_ID
                        + " INTEGER ,"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.FAILURE_REASON
                        + " TEXT DEFAULT '',"
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.RELEASE_VERSION
                        + " TEXT DEFAULT '' , "
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.COUNT
                        + " INTEGER DEFAULT 1 "
                        + ");";
        mCallAnalyticsProvider = new CallAnalyticsProvider(mTelephonyAnalyticsUtil, 0);
        verify(mTelephonyAnalyticsUtil).createTable(createCallAnalyticsTable);
    }

    @Test
    public void testAggregate() {
        String[] columns = {"sum(" + CallAnalyticsTable.COUNT + ")"};

        when(mTelephonyAnalyticsUtil.getCursor(
                        eq(CallAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mCursor);
        when(mTelephonyAnalyticsUtil.getCursor(
                        anyString(),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        anyString(),
                        isNull(),
                        anyString(),
                        anyString()))
                .thenReturn(mCursor);

        when(mTelephonyAnalyticsUtil.getCountFromCursor(eq(mCursor)))
                .thenReturn(
                        100L /*totalCalls*/,
                        50L /*totalFailedCalls*/,
                        40L /*normalCalls*/,
                        10L /*failedNormalCall*/,
                        60L /*sosCalls*/,
                        40L /*failedSosCall*/);
        ArrayList<String> actual = mCallAnalyticsProvider.aggregate();
        verify(mTelephonyAnalyticsUtil, times(6))
                .getCursor(
                        eq(CallAnalyticsTable.TABLE_NAME),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
        assertEquals("\tTotal Normal Calls = " + 40 /*normalCalls*/, actual.get(1));
        assertEquals("\tPercentage Failure of Normal Calls = 25.00%", actual.get(2));
    }

    @Test
    public void testGetMaxFailureVersion() {
        String[] columns = {CallAnalyticsTable.RELEASE_VERSION};
        String selection =
                CallAnalyticsTable.CALL_STATUS + " = ? AND " + CallAnalyticsTable.SLOT_ID + " = ? ";
        String[] selectionArgs = {"Failure", Integer.toString(0 /* slotIndex */)};
        String groupBy = CallAnalyticsTable.RELEASE_VERSION;
        String orderBy = "SUM(" + CallAnalyticsTable.COUNT + ") DESC ";
        String limit = "1";
        when(mTelephonyAnalyticsUtil.getCursor(
                        anyString(),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        anyString(),
                        isNull(),
                        anyString(),
                        anyString()))
                .thenReturn(mCursor);
        when(mTelephonyAnalyticsUtil.getCountFromCursor(any(Cursor.class)))
                .thenReturn(10L /* count */);
        when(mTelephonyAnalyticsUtil.getCountFromCursor(isNull())).thenReturn(10L /* count */);
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(CallAnalyticsTable.RELEASE_VERSION))
                .thenReturn(0 /* releaseVersionColumnIndex */);
        when(mCursor.getString(0)).thenReturn("1.1.1.1" /* version */);
        ArrayList<String> actual = mCallAnalyticsProvider.aggregate();
        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(CallAnalyticsTable.TABLE_NAME),
                        eq(columns),
                        eq(selection),
                        eq(selectionArgs),
                        eq(groupBy),
                        isNull(),
                        eq(orderBy),
                        eq(limit));
        assertEquals(
                actual.get(actual.size() - 2 /* array index for max failure at version info */),
                "\tMax Call(Normal+SOS) Failures at Version : 1.1.1.1");
    }

    private ContentValues getContentValues(
            String callType, String callStatus, int slotId, String rat, String failureReason) {
        ContentValues values = new ContentValues();
        String dateToday = DATE_FORMAT.format(Calendar.getInstance().toInstant());
        values.put(CallAnalyticsTable.LOG_DATE, dateToday);
        values.put(CallAnalyticsTable.CALL_TYPE, callType);
        values.put(CallAnalyticsTable.CALL_STATUS, callStatus);
        values.put(CallAnalyticsTable.SLOT_ID, slotId);
        values.put(CallAnalyticsTable.RAT, rat);
        values.put(CallAnalyticsTable.FAILURE_REASON, failureReason);
        values.put(CallAnalyticsTable.RELEASE_VERSION, INCREMENTAL);
        return values;
    }

    private void whenConditionForGetCursor() {
        when(mTelephonyAnalyticsUtil.getCursor(
                        anyString(),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mCursor);
    }

    private void verifyForGetCursor(
            String[] callInsertionProjection,
            String callSuccessInsertionSelection,
            String[] selectionArgs) {

        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(TelephonyAnalyticsDatabase.CallAnalyticsTable.TABLE_NAME),
                        eq(callInsertionProjection),
                        eq(callSuccessInsertionSelection),
                        eq(selectionArgs),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
    }

    @Test
    public void testSuccessCall() {
        int slotId = 0;
        String callType = "Normal Call";
        String callStatus = "Success";
        String rat = "LTE";
        String failureReason = "User Disconnects";
        int count = 5;

        final String callSuccessInsertionSelection =
                TelephonyAnalyticsDatabase.CallAnalyticsTable.CALL_TYPE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.LOG_DATE
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.CALL_STATUS
                        + " = ? AND "
                        + TelephonyAnalyticsDatabase.CallAnalyticsTable.SLOT_ID
                        + " = ? ";
        ContentValues values = getContentValues(callType, callStatus, slotId, rat, failureReason);

        String[] selectionArgs =
                new String[] {
                    values.getAsString(TelephonyAnalyticsDatabase.CallAnalyticsTable.CALL_TYPE),
                    values.getAsString(TelephonyAnalyticsDatabase.CallAnalyticsTable.LOG_DATE),
                    callStatus,
                    values.getAsString(TelephonyAnalyticsDatabase.CallAnalyticsTable.SLOT_ID)
                };
        whenConditionForGetCursor();
        mCallAnalyticsProvider.insertDataToDb(callType, callStatus, slotId, rat, failureReason);
        verifyForGetCursor(mCallInsertionProjection, callSuccessInsertionSelection, selectionArgs);
    }

    @Test
    public void testFailureCall() {
        int slotId = 0;
        String callType = "Normal Call";
        String callStatus = "Failure";
        String rat = "LTE";
        String failureReason = "Network Detach";
        int count = 5;
        final String callFailedInsertionSelection =
                CallAnalyticsTable.LOG_DATE
                        + " = ? AND "
                        + CallAnalyticsTable.CALL_STATUS
                        + " = ? AND "
                        + CallAnalyticsTable.CALL_TYPE
                        + " = ? AND "
                        + CallAnalyticsTable.SLOT_ID
                        + " = ? AND "
                        + CallAnalyticsTable.RAT
                        + " = ? AND "
                        + CallAnalyticsTable.FAILURE_REASON
                        + " = ? AND "
                        + CallAnalyticsTable.RELEASE_VERSION
                        + " = ? ";
        ContentValues values = getContentValues(callType, callStatus, slotId, rat, failureReason);
        String[] selectionArgs = {
            values.getAsString(CallAnalyticsTable.LOG_DATE),
            values.getAsString(CallAnalyticsTable.CALL_STATUS),
            values.getAsString(CallAnalyticsTable.CALL_TYPE),
            values.getAsString(CallAnalyticsTable.SLOT_ID),
            values.getAsString(CallAnalyticsTable.RAT),
            values.getAsString(CallAnalyticsTable.FAILURE_REASON),
            values.getAsString(CallAnalyticsTable.RELEASE_VERSION)
        };
        whenConditionForGetCursor();
        mCallAnalyticsProvider.insertDataToDb(callType, callStatus, slotId, rat, failureReason);
        verifyForGetCursor(mCallInsertionProjection, callFailedInsertionSelection, selectionArgs);
    }

    public void setUpTestForUpdateEntryIfExistsOrInsert() throws NoSuchMethodException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);
        mContentValues = new ContentValues();
        mContentValues.put(CallAnalyticsTable.CALL_STATUS, "Success");
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenCursorNull()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);
        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");
        updateEntryIfExistsOrInsert.invoke(mCallAnalyticsProvider, null, values);
        verify(mTelephonyAnalyticsUtil).insert(eq(CallAnalyticsTable.TABLE_NAME), eq(values));
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenCursorInvalid()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);
        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");
        when(mCursor.moveToFirst()).thenReturn(false);
        updateEntryIfExistsOrInsert.invoke(mCallAnalyticsProvider, mCursor, values);
        verify(mTelephonyAnalyticsUtil).insert(eq(CallAnalyticsTable.TABLE_NAME), eq(values));
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenCursorValidUpdateSuccess()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);

        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(CallAnalyticsTable._ID)).thenReturn(0 /* idColumnIndex */);
        when(mCursor.getColumnIndex(CallAnalyticsTable.COUNT)).thenReturn(1 /* countColumnIndex */);
        when(mCursor.getInt(0 /* idColumnIndex */)).thenReturn(100 /* id */);
        when(mCursor.getInt(1 /* countColumnIndex */)).thenReturn(5 /* count*/);

        String updateSelection = CallAnalyticsTable._ID + " = ? ";
        String[] updateSelectionArgs = {String.valueOf(100 /* id */)};

        updateEntryIfExistsOrInsert.invoke(mCallAnalyticsProvider, mCursor, values);

        values.put(CallAnalyticsTable.COUNT, 6 /* newCount */);
        verify(mTelephonyAnalyticsUtil)
                .update(
                        eq(CallAnalyticsTable.TABLE_NAME),
                        eq(values),
                        eq(updateSelection),
                        eq(updateSelectionArgs));
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToInvalidIdIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);

        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(CallAnalyticsTable._ID)).thenReturn(-1 /* idColumnIndex */);
        when(mCursor.getColumnIndex(CallAnalyticsTable.COUNT)).thenReturn(1 /* countColumnIndex */);
        updateEntryIfExistsOrInsert.invoke(mCallAnalyticsProvider, mCursor, values);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToInvalidCountIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);

        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(CallAnalyticsTable._ID)).thenReturn(0 /* idColumnIndex */);
        when(mCursor.getColumnIndex(CallAnalyticsTable.COUNT))
                .thenReturn(-1 /* countColumnIndex */);
        updateEntryIfExistsOrInsert.invoke(mCallAnalyticsProvider, mCursor, values);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToInvalidColumnIndex()
            throws NoSuchMethodException {
        Method updateEntryIfExistsOrInsert =
                CallAnalyticsProvider.class.getDeclaredMethod(
                        "updateEntryIfExistsOrInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);

        ContentValues values = new ContentValues();
        values.put(CallAnalyticsTable.CALL_STATUS, "Success");

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(CallAnalyticsTable._ID)).thenReturn(-1 /* idColumnIndex */);
        when(mCursor.getColumnIndex(CallAnalyticsTable.COUNT))
                .thenReturn(-1 /* countColumnIndex */);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @After
    public void tearDown() {
        mCallAnalyticsProvider = null;
        mContentValues = null;
        mTelephonyAnalyticsUtil = null;
        mCursor = null;
    }
}
