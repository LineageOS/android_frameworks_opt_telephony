/*
 * Copyright 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.mock.MockContentProvider;

/**
 * Mocking/spying ContentProviders break in different ways. Use a fake instead. The
 * RcsMessageStoreController doesn't care if RcsProvider works as expected (and doesn't have
 * visibility into it) - so verifying whether we use the correct parameters should suffice.
 */
class FakeProviderWithAsserts extends MockContentProvider {
    private Uri mExpectedUri;
    private String[] mExpectedProjection;
    private String mExpectedWhereClause;
    private String[] mExpectedWhereArgs;
    private String mExpectedSortOrder;
    private ContentValues mExpectedContentValues;

    private Cursor mQueryReturnValue;
    private Uri mInsertReturnValue;

    void setExpectedQueryParameters(Uri uri, String[] projection, String whereClause,
            String[] whereArgs, String sortOrder) {
        mExpectedUri = uri;
        mExpectedProjection = projection;
        mExpectedWhereClause = whereClause;
        mExpectedWhereArgs = whereArgs;
        mExpectedSortOrder = sortOrder;
    }

    void setExpectedInsertParameters(Uri uri, ContentValues contentValues) {
        mExpectedUri = uri;
        mExpectedContentValues = contentValues;
    }

    void setExpectedUpdateParameters(Uri uri, ContentValues contentValues, String whereClause,
            String[] whereArgs) {
        mExpectedUri = uri;
        mExpectedContentValues = contentValues;
        mExpectedWhereClause = whereClause;
        mExpectedWhereArgs = whereArgs;
    }

    void setInsertReturnValue(Uri returnValue) {
        mInsertReturnValue = returnValue;
    }

    void setQueryReturnValue(Cursor cursor) {

    }

    @Override
    public Cursor query(Uri uri, String[] projection, String whereClause, String[] whereArgs,
            String sortOrder) {
        assertThat(uri).isEqualTo(mExpectedUri);
        assertThat(projection).isEqualTo(mExpectedProjection);
        assertThat(whereClause).isEqualTo(mExpectedWhereClause);
        assertThat(whereArgs).isEqualTo(mExpectedWhereArgs);
        assertThat(sortOrder).isEqualTo(mExpectedSortOrder);
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        assertThat(uri).isEqualTo(mExpectedUri);
        assertThat(contentValues).isEqualTo(mExpectedContentValues);
        return mInsertReturnValue;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String whereClause,
            String[] whereArgs) {
        assertThat(uri).isEqualTo(mExpectedUri);
        assertThat(contentValues).isEqualTo(mExpectedContentValues);
        assertThat(whereClause).isEqualTo(mExpectedWhereClause);
        assertThat(whereArgs).isEqualTo(mExpectedWhereArgs);
        return 0;
    }
}
