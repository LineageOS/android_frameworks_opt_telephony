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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.database.Cursor;

import com.android.internal.telephony.analytics.TelephonyAnalyticsDatabase.ServiceStateAnalyticsTable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class ServiceStateAnalyticsProviderTest {
    @Mock TelephonyAnalyticsUtil mTelephonyAnalyticsUtil;
    @Mock Cursor mCursor;
    @Mock TelephonyAnalytics.ServiceStateAnalytics.TimeStampedServiceState mState;
    private ContentValues mContentValues;

    final int mSlotIndex = 0;
    ServiceStateAnalyticsProvider mServiceStateAnalyticsProvider;
    final String mCreateServiceStateTableQuery =
            "CREATE TABLE IF NOT EXISTS "
                    + ServiceStateAnalyticsTable.TABLE_NAME
                    + " ( "
                    + ServiceStateAnalyticsTable._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + ServiceStateAnalyticsTable.LOG_DATE
                    + " DATE ,"
                    + ServiceStateAnalyticsTable.SLOT_ID
                    + " INTEGER , "
                    + ServiceStateAnalyticsTable.TIME_DURATION
                    + " INTEGER ,"
                    + ServiceStateAnalyticsTable.RAT
                    + " TEXT ,"
                    + ServiceStateAnalyticsTable.DEVICE_STATUS
                    + " TEXT ,"
                    + ServiceStateAnalyticsTable.RELEASE_VERSION
                    + " TEXT "
                    + ");";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        assert (mTelephonyAnalyticsUtil != null);
        mServiceStateAnalyticsProvider =
                new ServiceStateAnalyticsProvider(mTelephonyAnalyticsUtil, mSlotIndex);
        verify(mTelephonyAnalyticsUtil).createTable(mCreateServiceStateTableQuery);
        mContentValues = getDummyContentValue();
    }

    @Test
    public void valid() {
        assert (mServiceStateAnalyticsProvider != null);
        assert (mTelephonyAnalyticsUtil != null);
        assert (mCursor != null);
        assert (mState != null);
    }

    @Test
    public void testInsertDataToDb() {
        TelephonyAnalytics.ServiceStateAnalytics.TimeStampedServiceState lastState =
                new TelephonyAnalytics.ServiceStateAnalytics.TimeStampedServiceState(
                        0 /*slotIndex*/,
                        "LTE" /*rat*/,
                        "IN_SERVICE" /*deviceStatus*/,
                        233423424 /*timestampStart*/);
        ContentValues values = new ContentValues();
        long timeInterval = 343443434 /*endTimeStamp*/ - lastState.getTimestampStart();
        String dateToday = DATE_FORMAT.format(Calendar.getInstance().toInstant());
        values.put(ServiceStateAnalyticsTable.LOG_DATE, dateToday);
        values.put(ServiceStateAnalyticsTable.TIME_DURATION, timeInterval);
        values.put(ServiceStateAnalyticsTable.SLOT_ID, lastState.getSlotIndex());
        values.put(ServiceStateAnalyticsTable.RAT, lastState.getRAT());
        values.put(ServiceStateAnalyticsTable.DEVICE_STATUS, lastState.getDeviceStatus());
        values.put(ServiceStateAnalyticsTable.RELEASE_VERSION, INCREMENTAL);

        final String[] serviceStateInsertionColumns = {
            ServiceStateAnalyticsTable._ID, ServiceStateAnalyticsTable.TIME_DURATION
        };
        final String serviceStateInsertionSelection =
                ServiceStateAnalyticsTable.LOG_DATE
                        + " = ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? AND "
                        + ServiceStateAnalyticsTable.RAT
                        + " = ? AND "
                        + ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " = ? AND "
                        + ServiceStateAnalyticsTable.RELEASE_VERSION
                        + " = ? ";
        final String[] selectionArgs = {
            values.getAsString(ServiceStateAnalyticsTable.LOG_DATE),
            values.getAsString(ServiceStateAnalyticsTable.SLOT_ID),
            values.getAsString(ServiceStateAnalyticsTable.RAT),
            values.getAsString(ServiceStateAnalyticsTable.DEVICE_STATUS),
            values.getAsString(ServiceStateAnalyticsTable.RELEASE_VERSION)
        };
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
        mServiceStateAnalyticsProvider.insertDataToDb(lastState, 343443434 /*endTimeStamp*/);

        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(serviceStateInsertionColumns),
                        eq(serviceStateInsertionSelection),
                        eq(selectionArgs),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
    }

    private Method setReflectionForTestingUpdateEntryIfExistsOrInsert()
            throws NoSuchMethodException {
        Method updateEntryIfExistsOrInsert =
                ServiceStateAnalyticsProvider.class.getDeclaredMethod(
                        "updateIfEntryExistsOtherwiseInsert", Cursor.class, ContentValues.class);
        updateEntryIfExistsOrInsert.setAccessible(true);
        return updateEntryIfExistsOrInsert;
    }

    private ContentValues getDummyContentValue() {
        ContentValues values = new ContentValues();
        values.put(ServiceStateAnalyticsTable.DEVICE_STATUS, "IN_SERVICE" /*DeviceStatus*/);
        values.put(ServiceStateAnalyticsTable.TIME_DURATION, 423234 /*Time Duration*/);
        return values;
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenCursorNull()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();
        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, null, mContentValues);
        verify(mTelephonyAnalyticsUtil)
                .insert(eq(ServiceStateAnalyticsTable.TABLE_NAME), eq(mContentValues));
    }

    @Test
    public void testUpdateIfEntryExistsOtherwiseInsertWhenEntryNotExist()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();
        when(mCursor.moveToFirst()).thenReturn(false);
        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, null, mContentValues);
        verify(mTelephonyAnalyticsUtil)
                .insert(eq(ServiceStateAnalyticsTable.TABLE_NAME), eq(mContentValues));
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenCursorValidAndUpdateSuccess()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();

        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable._ID)).thenReturn(0 /* idIndex */);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.TIME_DURATION))
                .thenReturn(1 /* timeDurationIndex */);
        when(mCursor.getInt(0 /* idIndex */)).thenReturn(100 /* ID */);
        when(mCursor.getInt(1 /* timeDurationIndex */)).thenReturn(523535 /* oldTimeDuration */);

        String updateSelection = ServiceStateAnalyticsTable._ID + " = ? ";
        String[] updateSelectionArgs = {Integer.toString(100 /* ID */)};

        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, mCursor, mContentValues);
        mContentValues.put(
                ServiceStateAnalyticsTable.TIME_DURATION, 946769 /* updatedTimeDuration */);
        verify(mTelephonyAnalyticsUtil)
                .update(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(mContentValues),
                        eq(updateSelection),
                        eq(updateSelectionArgs));
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToInvalidIdIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable._ID)).thenReturn(-1 /* idIndex */);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.TIME_DURATION))
                .thenReturn(1 /* timeDurationIndex */);
        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, mCursor, mContentValues);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToInvalidDurationIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable._ID)).thenReturn(0 /* idIndex */);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.TIME_DURATION))
                .thenReturn(-1 /* timeDurationIndex */);
        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, mCursor, mContentValues);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    @Test
    public void testUpdateEntryIfExistsOrInsertWhenUpdateFailedDueToAllInvalidIndex()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method updateEntryIfExistsOrInsert = setReflectionForTestingUpdateEntryIfExistsOrInsert();
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable._ID)).thenReturn(-1 /* idIndex */);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.TIME_DURATION))
                .thenReturn(-1 /* timeDurationIndex */);
        updateEntryIfExistsOrInsert.invoke(mServiceStateAnalyticsProvider, mCursor, mContentValues);
        verifyNoMoreInteractions(mTelephonyAnalyticsUtil);
    }

    private void setWhenClauseForGetCursor(
            String[] columns, String selection, String[] selectionArgs) {
        when(mTelephonyAnalyticsUtil.getCursor(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(columns),
                        eq(selection),
                        eq(selectionArgs),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mCursor);
    }

    private void verifyClause(String[] columns, String selection, String[] selectionArgs) {
        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(columns),
                        eq(selection),
                        eq(selectionArgs),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull());
    }

    private void setUpNullCursorReturn() {
        when(mTelephonyAnalyticsUtil.getCursor(
                        anyString(),
                        any(String[].class),
                        anyString(),
                        any(String[].class),
                        isNull(),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(null);
    }

    @Test
    public void testTotalUpTimeThroughAggregate() {
        HashMap<String, Long> empty = new HashMap<>();
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection = ServiceStateAnalyticsTable.SLOT_ID + " = ? ";
        String[] selectionArgs = {Integer.toString(mSlotIndex)};
        setWhenClauseForGetCursor(columns, selection, selectionArgs);
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getLong(0 /* columnIndex */)).thenReturn(1000000L /* upTime */);

        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        assertEquals(
                actual.get(0 /* Array Index for Total Uptime*/),
                "Total UpTime = " + 1000000 /* upTime */ + " millis");
    }

    @Test
    public void testTotalUpTimeWhenCursorNullThroughAggregate() {
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection = ServiceStateAnalyticsTable.SLOT_ID + " = ? ";
        String[] selectionArgs = {Integer.toString(mSlotIndex)};
        setUpNullCursorReturn();
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        assertEquals(
                actual.get(0 /* Array Index for Total Uptime*/),
                "Total UpTime = " + 0 /*upTime */ + " millis");
    }

    @Test
    public void testTotalUpTimeWhenCursorInvalidThroughAggregate() {
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection = ServiceStateAnalyticsTable.SLOT_ID + " = ? ";
        String[] selectionArgs = {Integer.toString(mSlotIndex)};
        setWhenClauseForGetCursor(columns, selection, selectionArgs);
        when(mCursor.moveToFirst()).thenReturn(false);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        assertEquals(
                actual.get(0 /* Array Index for Total Uptime*/),
                "Total UpTime = " + 0 /*upTime */ + " millis");
    }

    @Test
    public void testOutOfServiceDurationThroughAggregate() {
        setUpTotalTime();
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? "
                        + " AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};

        when(mCursor.moveToFirst()).thenReturn(true, true);
        when(mCursor.getLong(0 /*columnIndex*/))
                .thenReturn(1000000L /*totalUpTime*/, 100000L /*outOfServiceTime*/);
        setWhenClauseForGetCursor(columns, selection, selectionArgs);

        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        assertEquals(
                actual.get(1),
                "Out of Service Time = "
                        + 100000 /*outOfServiceTime*/
                        + " millis, Percentage "
                        + "10.00"
                        + "%");
    }

    @Test
    public void testOutOfServiceDurationWhenCursorNullThroughAggregate() {
        HashMap<String, Long> empty = new HashMap<>();
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? "
                        + " AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        setUpNullCursorReturn();
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        boolean check =
                actual.get(1 /* ArrayIndex for Out Of Service Time Info */)
                        .contains("Out of Service Time = 0");
        assertTrue(check);
    }

    @Test
    public void testOutOfServiceDurationWhenCursorInvalidThroughAggregate() {
        HashMap<String, Long> empty = new HashMap<>();
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? "
                        + " AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        setWhenClauseForGetCursor(columns, selection, selectionArgs);
        when(mCursor.moveToFirst()).thenReturn(false);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyClause(columns, selection, selectionArgs);
        boolean check = actual.get(1).contains("Out of Service Time = 0");
        assertTrue(check);
    }

    private void whenClauseForGroupByPresent(
            String[] columns, String selection, String[] selectionArgs, String groupBy) {
        when(mTelephonyAnalyticsUtil.getCursor(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(columns),
                        eq(selection),
                        eq(selectionArgs),
                        eq(groupBy),
                        isNull(),
                        isNull(),
                        isNull()))
                .thenReturn(mCursor);
    }

    private void verifyGroupBy(
            String[] columns, String selection, String[] selectionArgs, String groupBy) {
        verify(mTelephonyAnalyticsUtil)
                .getCursor(
                        eq(ServiceStateAnalyticsTable.TABLE_NAME),
                        eq(columns),
                        eq(selection),
                        eq(selectionArgs),
                        eq(groupBy),
                        isNull(),
                        isNull(),
                        isNull());
    }

    private void setUpTotalTime() {
        String[] columns = {"SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ")"};
        String selection = ServiceStateAnalyticsTable.SLOT_ID + " = ? ";
        String[] selectionArgs = {Integer.toString(mSlotIndex)};
        setWhenClauseForGetCursor(columns, selection, selectionArgs);
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getLong(0 /*columnIndex*/)).thenReturn(1000000L /*upTime*/);
    }

    @Test
    public void testOutOfServiceDurationByReasonWhenValid() {
        setUpTotalTime();
        String[] columns = {
            ServiceStateAnalyticsTable.DEVICE_STATUS,
            "SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ") AS totalTime"
        };
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        String groupBy = ServiceStateAnalyticsTable.DEVICE_STATUS;
        whenClauseForGroupByPresent(columns, selection, selectionArgs, groupBy);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.DEVICE_STATUS))
                .thenReturn(0 /*deviceStatusIndex*/);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /*totalTimeIndex*/);
        when(mCursor.moveToNext()).thenReturn(true, false);
        when(mCursor.getString(0 /*deviceStatusIndex*/)).thenReturn("NO_NETWORK" /*oosReason*/);
        when(mCursor.getLong(1 /*totalTimeIndex*/)).thenReturn(100000L /*oosTime*/);

        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyGroupBy(columns, selection, selectionArgs, groupBy);
        assertEquals(
                actual.get(2 /*oosReasonDumpArrayIndex*/),
                "Out of service Reason = " + "NO_NETWORK" + ", Percentage = " + "10.00" + "%");
    }

    @Test
    public void testOutOfServiceDurationByReasonWhenNoDataInCursor() {
        String[] columns = {
            ServiceStateAnalyticsTable.DEVICE_STATUS,
            "SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ") AS totalTime"
        };
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        String groupBy = ServiceStateAnalyticsTable.DEVICE_STATUS;
        HashMap<String, Long> outOfServiceDurationByReason = new HashMap<>();
        outOfServiceDurationByReason.put("NO_NETWORK", 10000L);

        whenClauseForGroupByPresent(columns, selection, selectionArgs, groupBy);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.DEVICE_STATUS))
                .thenReturn(0 /*deviceStatusIndex*/);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /*totalTimeIndex*/);
        when(mCursor.moveToNext()).thenReturn(false);

        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyGroupBy(columns, selection, selectionArgs, groupBy);
        assertEquals(actual.size(), 2 /*expectedArraySize*/);
    }

    @Test
    public void testOutOfServiceDurationByReasonWhenReasonIndexInvalid() {
        String[] columns = {
            ServiceStateAnalyticsTable.DEVICE_STATUS,
            "SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ") AS totalTime"
        };
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        String groupBy = ServiceStateAnalyticsTable.DEVICE_STATUS;
        HashMap<String, Long> outOfServiceDurationByReason = new HashMap<>();
        outOfServiceDurationByReason.put("NO_NETWORK", 10000L);

        whenClauseForGroupByPresent(columns, selection, selectionArgs, groupBy);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.DEVICE_STATUS))
                .thenReturn(-1 /*deviceStatusIndex*/);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /*totalTimeIndex*/);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyGroupBy(columns, selection, selectionArgs, groupBy);
        assertEquals(actual.size(), 2 /*expectedArraySize*/);
    }

    @Test
    public void testOutOfServiceDurationByReasonWhenTimeIndexInvalid() {
        String[] columns = {
            ServiceStateAnalyticsTable.DEVICE_STATUS,
            "SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ") AS totalTime"
        };
        String selection =
                ServiceStateAnalyticsTable.DEVICE_STATUS
                        + " != ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"IN_SERVICE", Integer.toString(mSlotIndex)};
        String groupBy = ServiceStateAnalyticsTable.DEVICE_STATUS;
        HashMap<String, Long> outOfServiceDurationByReason = new HashMap<>();
        outOfServiceDurationByReason.put("NO_NETWORK", 10000L);

        whenClauseForGroupByPresent(columns, selection, selectionArgs, groupBy);
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.DEVICE_STATUS))
                .thenReturn(0 /*deviceStatusIndex*/);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(-1 /*totalTimeIndex*/);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        verifyGroupBy(columns, selection, selectionArgs, groupBy);
        assertEquals(actual.size(), 2 /*expectedArraySize*/);
    }

    private void setUpForTestingGetInServiceDurationByRat() {
        String[] columns = {
            ServiceStateAnalyticsTable.RAT,
            "SUM(" + ServiceStateAnalyticsTable.TIME_DURATION + ") AS totalTime"
        };
        String selection =
                ServiceStateAnalyticsTable.RAT
                        + " != ? AND "
                        + ServiceStateAnalyticsTable.SLOT_ID
                        + " = ? ";
        String[] selectionArgs = {"NA", Integer.toString(mSlotIndex)};
        String groupBy = ServiceStateAnalyticsTable.RAT;
        whenClauseForGroupByPresent(columns, selection, selectionArgs, groupBy);
    }

    @Test
    public void testInServiceDurationByRatWhenDataPresent() {
        setUpForTestingGetInServiceDurationByRat();
        setUpTotalTime();
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.RAT)).thenReturn(0 /* ratIndex */);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /* totalTimeIndex */);
        when(mCursor.moveToNext()).thenReturn(true, false);
        when(mCursor.getString(0 /* ratIndex */)).thenReturn("LTE" /* inServiceRat */);
        when(mCursor.getLong(1 /* totalTimeIndex */)).thenReturn(200000L /* inServiceTime */);

        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        assertEquals(
                actual.get(2 /*arrayIndex For In Service RAT Information */),
                "IN_SERVICE RAT : " + "LTE" + ", Percentage = 20.00" + "%");
    }

    @Test
    public void testInServiceDurationByRatWhenDataNotPresent() {
        setUpForTestingGetInServiceDurationByRat();
        setUpTotalTime();
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.RAT)).thenReturn(0 /* ratIndex */);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /* totalTimeIndex */);
        when(mCursor.moveToNext()).thenReturn(false);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        assertEquals(actual.size(), 2 /* expectedArraySize */);
    }

    @Test
    public void testInServiceDurationByRatWhenInvalidRatIndex() {
        setUpForTestingGetInServiceDurationByRat();
        setUpTotalTime();
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.RAT)).thenReturn(-1 /* ratIndex */);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(1 /* totalTimeIndex */);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        assertEquals(actual.size(), 2 /* expectedArraySize */);
    }

    @Test
    public void testInServiceDurationByRatWhenInvalidTimeIndex() {
        setUpForTestingGetInServiceDurationByRat();
        setUpTotalTime();
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.RAT)).thenReturn(0 /* ratIndex */);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(-1 /* totalTimeIndex */);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        assertEquals(actual.size(), 2 /* expectedArraySize */);
    }

    @Test
    public void testInServiceDurationByRatWhenBothIndexInvalid() {
        setUpForTestingGetInServiceDurationByRat();
        setUpTotalTime();
        when(mCursor.getColumnIndex(ServiceStateAnalyticsTable.RAT)).thenReturn(-1 /* ratIndex */);
        when(mCursor.getColumnIndex("totalTime")).thenReturn(-1 /* totalTimeIndex */);
        ArrayList<String> actual = mServiceStateAnalyticsProvider.aggregate();
        assertEquals(actual.size(), 2 /* expectedArraySize */);
    }

    @Test
    public void testDeleteOldAndOverflowDataWhenLastDeletedDateEqualsToday() {
        String dateToday = DATE_FORMAT.format(Calendar.getInstance().toInstant());
        mServiceStateAnalyticsProvider.setDateOfDeletedRecordsServiceStateTable(dateToday);
        TelephonyAnalytics.ServiceStateAnalytics.TimeStampedServiceState
                mockTimeStampedServiceState =
                        mock(
                                TelephonyAnalytics.ServiceStateAnalytics.TimeStampedServiceState
                                        .class);
        mServiceStateAnalyticsProvider.insertDataToDb(
                mockTimeStampedServiceState, 100L /* endTimeStamp */);
    }

    @After
    public void tearDown() {
        mServiceStateAnalyticsProvider = null;
        mCursor = null;
        mTelephonyAnalyticsUtil = null;
        mContentValues = null;
        mState = null;
    }
}
