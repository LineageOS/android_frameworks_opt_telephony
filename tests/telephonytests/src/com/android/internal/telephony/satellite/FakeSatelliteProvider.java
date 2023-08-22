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

package com.android.internal.telephony.satellite;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.test.mock.MockContentProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

public class FakeSatelliteProvider extends MockContentProvider {
    private static final String TAG = "FakeSatelliteProvider";

    private InMemorySatelliteProviderDbHelper mDbHelper = new InMemorySatelliteProviderDbHelper();

    private class InMemorySatelliteProviderDbHelper extends SQLiteOpenHelper {

        InMemorySatelliteProviderDbHelper() {
            super(InstrumentationRegistry.getTargetContext(),
                    null,    // db file name is null for in-memory db
                    null,    // CursorFactory is null by default
                    1);      // db version is no-op for tests
            Log.d(TAG, "InMemorySatelliteProviderDbHelper creating in-memory database");
        }
        public static String getStringForDatagramTableCreation(String tableName) {
            return "CREATE TABLE " + tableName + "("
                    + Telephony.SatelliteDatagrams.COLUMN_UNIQUE_KEY_DATAGRAM_ID
                    + " INTEGER PRIMARY KEY,"
                    + Telephony.SatelliteDatagrams.COLUMN_DATAGRAM + " BLOB DEFAULT ''" +
                    ");";
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "InMemoryTelephonyProviderDbHelper onCreate:"
                    + " creating satellite incoming datagram table");
            db.execSQL(getStringForDatagramTableCreation(Telephony.SatelliteDatagrams.TABLE_NAME));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // Do nothing.
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "insert. values=" + values);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insert(Telephony.SatelliteDatagrams.TABLE_NAME, null, values);
        return ContentUris.withAppendedId(Telephony.SatelliteDatagrams.CONTENT_URI, id);
    }

    @Override
    public synchronized int delete(Uri url, String where, String[] whereArgs) {
        return mDbHelper.getWritableDatabase()
                .delete(Telephony.SatelliteDatagrams.TABLE_NAME, where, whereArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return mDbHelper.getReadableDatabase().query(Telephony.SatelliteDatagrams.TABLE_NAME,
                projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public Bundle call(String method, String request, Bundle args) {
        return null;
    }

    @Override
    public final int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
        // Do nothing.
        return 0;
    }

    @Override
    public void shutdown() {
        mDbHelper.close();
    }
}
