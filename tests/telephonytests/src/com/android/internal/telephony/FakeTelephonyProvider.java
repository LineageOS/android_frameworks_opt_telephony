/*
 * Copyright (C) 2018 The Android Open Source Project
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


import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.test.mock.MockContentProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

public class FakeTelephonyProvider extends MockContentProvider {
    static final String TAG = "FakeTelephonyProvider";

    private InMemoryTelephonyProviderDbHelper mDbHelper =
            new InMemoryTelephonyProviderDbHelper();

    /**
     * An in memory DB.
     */
    private class InMemoryTelephonyProviderDbHelper extends SQLiteOpenHelper {
        InMemoryTelephonyProviderDbHelper() {
            super(InstrumentationRegistry.getTargetContext(),
                    null,    // db file name is null for in-memory db
                    null,    // CursorFactory is null by default
                    1);      // db version is no-op for tests
            Log.d(TAG, "InMemoryTelephonyProviderDbHelper creating in-memory database");
        }

        // This should always be consistent with TelephonyProvider#getStringForSimInfoTableCreation.
        private String getStringForSimInfoTableCreation(String tableName) {
            return "CREATE TABLE " + tableName + "("
                    + Telephony.SimInfo.UNIQUE_KEY_SUBSCRIPTION_ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Telephony.SimInfo.ICC_ID + " TEXT NOT NULL,"
                    + Telephony.SimInfo.SIM_SLOT_INDEX
                    + " INTEGER DEFAULT " + Telephony.SimInfo.SIM_NOT_INSERTED + ","
                    + Telephony.SimInfo.DISPLAY_NAME + " TEXT,"
                    + Telephony.SimInfo.CARRIER_NAME + " TEXT,"
                    + Telephony.SimInfo.NAME_SOURCE
                    + " INTEGER DEFAULT " + Telephony.SimInfo.NAME_SOURCE_DEFAULT + ","
                    + Telephony.SimInfo.COLOR + " INTEGER DEFAULT "
                    + Telephony.SimInfo.COLOR_DEFAULT + ","
                    + Telephony.SimInfo.NUMBER + " TEXT,"
                    + Telephony.SimInfo.DISPLAY_NUMBER_FORMAT
                    + " INTEGER NOT NULL DEFAULT "
                    + Telephony.SimInfo.DISPLAY_NUMBER_DEFAULT + ","
                    + Telephony.SimInfo.DATA_ROAMING
                    + " INTEGER DEFAULT " + Telephony.SimInfo.DATA_ROAMING_DEFAULT + ","
                    + Telephony.SimInfo.MCC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.MNC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.MCC_STRING + " TEXT,"
                    + Telephony.SimInfo.MNC_STRING + " TEXT,"
                    + Telephony.SimInfo.EHPLMNS + " TEXT,"
                    + Telephony.SimInfo.HPLMNS + " TEXT,"
                    + Telephony.SimInfo.SIM_PROVISIONING_STATUS
                    + " INTEGER DEFAULT " + Telephony.SimInfo.SIM_PROVISIONED + ","
                    + Telephony.SimInfo.IS_EMBEDDED + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.CARD_ID + " TEXT NOT NULL,"
                    + Telephony.SimInfo.ACCESS_RULES + " BLOB,"
                    + Telephony.SimInfo.IS_REMOVABLE + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.CB_EXTREME_THREAT_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_SEVERE_THREAT_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_AMBER_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_EMERGENCY_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_ALERT_SOUND_DURATION + " INTEGER DEFAULT 4,"
                    + Telephony.SimInfo.CB_ALERT_REMINDER_INTERVAL + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.CB_ALERT_VIBRATE + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_ALERT_SPEECH + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_ETWS_TEST_ALERT + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.CB_CHANNEL_50_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.CB_CMAS_TEST_ALERT + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.CB_OPT_OUT_DIALOG + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.ENHANCED_4G_MODE_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.VT_IMS_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.WFC_IMS_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.WFC_IMS_MODE + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.WFC_IMS_ROAMING_MODE + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.WFC_IMS_ROAMING_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.IS_OPPORTUNISTIC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.GROUP_UUID + " TEXT,"
                    + Telephony.SimInfo.IS_METERED + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.ISO_COUNTRY_CODE + " TEXT,"
                    + Telephony.SimInfo.CARRIER_ID + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.PROFILE_CLASS
                    + " INTEGER DEFAULT " + Telephony.SimInfo.PROFILE_CLASS_DEFAULT + ","
                    + Telephony.SimInfo.SUBSCRIPTION_TYPE + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.GROUP_OWNER + " TEXT,"
                    + Telephony.SimInfo.DATA_ENABLED_OVERRIDE_RULES + " TEXT,"
                    + Telephony.SimInfo.IMSI + " TEXT,"
                    + Telephony.SimInfo.ACCESS_RULES_FROM_CARRIER_CONFIGS + " BLOB,"
                    + Telephony.SimInfo.UICC_APPLICATIONS_ENABLED + " INTEGER DEFAULT 1"
                    + ");";
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // TODO: set up other tables when needed.
            // set up the siminfo table
            Log.d(TAG, "InMemoryTelephonyProviderDbHelper onCreate creating the siminfo table");
            db.execSQL(getStringForSimInfoTableCreation("siminfo"));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "InMemoryTelephonyProviderDbHelper onUpgrade doing nothing");
            return;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long id = db.insert("siminfo", null, values);
        return ContentUris.withAppendedId(Telephony.SimInfo.CONTENT_URI, id);
    }

    @Override
    public synchronized int delete(Uri url, String where, String[] whereArgs) {
        return mDbHelper.getWritableDatabase().delete("siminfo", where, whereArgs);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return mDbHelper.getReadableDatabase().query("siminfo", projection, selection,
                selectionArgs, null, null, sortOrder);
    }

    @Override
    public Bundle call(String method, String request, Bundle args) {
        return null;
    }

    @Override
    public final int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
        // handle URI with appended subId
        final int urlSimInfoSubId = 0;
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI("telephony", "siminfo/#", urlSimInfoSubId);
        if (matcher.match(uri) == urlSimInfoSubId) {
            where = BaseColumns._ID + "=" + uri.getLastPathSegment();
        }

        int count = mDbHelper.getWritableDatabase().update("siminfo", values, where,
                selectionArgs);
        return count;
    }
}
