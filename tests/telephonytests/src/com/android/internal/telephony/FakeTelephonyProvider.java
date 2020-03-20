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
                    + Telephony.SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Telephony.SimInfo.COLUMN_ICC_ID + " TEXT NOT NULL,"
                    + Telephony.SimInfo.COLUMN_SIM_SLOT_INDEX
                    + " INTEGER DEFAULT " + Telephony.SimInfo.SIM_NOT_INSERTED + ","
                    + Telephony.SimInfo.COLUMN_DISPLAY_NAME + " TEXT,"
                    + Telephony.SimInfo.COLUMN_CARRIER_NAME + " TEXT,"
                    + Telephony.SimInfo.COLUMN_NAME_SOURCE
                    + " INTEGER DEFAULT " + Telephony.SimInfo.NAME_SOURCE_CARRIER_ID + ","
                    + Telephony.SimInfo.COLUMN_COLOR + " INTEGER DEFAULT "
                    + Telephony.SimInfo.COLOR_DEFAULT + ","
                    + Telephony.SimInfo.COLUMN_NUMBER + " TEXT,"
                    + Telephony.SimInfo.COLUMN_DISPLAY_NUMBER_FORMAT
                    + " INTEGER NOT NULL DEFAULT "
                    + Telephony.SimInfo.DISPLAY_NUMBER_DEFAULT + ","
                    + Telephony.SimInfo.COLUMN_DATA_ROAMING
                    + " INTEGER DEFAULT " + Telephony.SimInfo.DATA_ROAMING_DISABLE + ","
                    + Telephony.SimInfo.COLUMN_MCC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_MNC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_MCC_STRING + " TEXT,"
                    + Telephony.SimInfo.COLUMN_MNC_STRING + " TEXT,"
                    + Telephony.SimInfo.COLUMN_EHPLMNS + " TEXT,"
                    + Telephony.SimInfo.COLUMN_HPLMNS + " TEXT,"
                    + Telephony.SimInfo.COLUMN_SIM_PROVISIONING_STATUS
                    + " INTEGER DEFAULT " + Telephony.SimInfo.SIM_PROVISIONED + ","
                    + Telephony.SimInfo.COLUMN_IS_EMBEDDED + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_CARD_ID + " TEXT NOT NULL,"
                    + Telephony.SimInfo.COLUMN_ACCESS_RULES + " BLOB,"
                    + Telephony.SimInfo.COLUMN_IS_REMOVABLE + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_CB_EXTREME_THREAT_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_SEVERE_THREAT_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_AMBER_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_EMERGENCY_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_ALERT_SOUND_DURATION + " INTEGER DEFAULT 4,"
                    + Telephony.SimInfo.COLUMN_CB_ALERT_REMINDER_INTERVAL + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_CB_ALERT_VIBRATE + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_ALERT_SPEECH + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_ETWS_TEST_ALERT + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_CB_CHANNEL_50_ALERT + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_CB_CMAS_TEST_ALERT + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_CB_OPT_OUT_DIALOG + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_VT_IMS_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_WFC_IMS_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_WFC_IMS_MODE + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_WFC_IMS_ROAMING_MODE + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_WFC_IMS_ROAMING_ENABLED + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_IS_OPPORTUNISTIC + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_GROUP_UUID + " TEXT,"
                    + Telephony.SimInfo.COLUMN_IS_METERED + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_ISO_COUNTRY_CODE + " TEXT,"
                    + Telephony.SimInfo.COLUMN_CARRIER_ID + " INTEGER DEFAULT -1,"
                    + Telephony.SimInfo.COLUMN_PROFILE_CLASS
                    + " INTEGER DEFAULT " + Telephony.SimInfo.PROFILE_CLASS_UNSET + ","
                    + Telephony.SimInfo.COLUMN_SUBSCRIPTION_TYPE + " INTEGER DEFAULT 0,"
                    + Telephony.SimInfo.COLUMN_GROUP_OWNER + " TEXT,"
                    + Telephony.SimInfo.COLUMN_DATA_ENABLED_OVERRIDE_RULES + " TEXT,"
                    + Telephony.SimInfo.COLUMN_IMSI + " TEXT,"
                    + Telephony.SimInfo.COLUMN_ACCESS_RULES_FROM_CARRIER_CONFIGS + " BLOB,"
                    + Telephony.SimInfo.COLUMN_UICC_APPLICATIONS_ENABLED + " INTEGER DEFAULT 1,"
                    + Telephony.SimInfo.COLUMN_ALLOWED_NETWORK_TYPES + " BIGINT DEFAULT -1, "
                    + Telephony.SimInfo.COLUMN_IMS_RCS_UCE_ENABLED + " INTEGER DEFAULT 0"
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
