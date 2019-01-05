/*
 * Copyright 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import android.database.Cursor;
import android.net.Uri;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThreadQueryParameters;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RcsMessageStoreControllerTest extends TelephonyTest {

    private RcsMessageStoreController mRcsMessageStoreController;
    private MockContentResolver mContentResolver;
    private FakeRcsProvider mFakeRcsProvider;

    @Mock
    RcsParticipant mMockParticipant;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsMessageStoreControllerTest");
        MockitoAnnotations.initMocks(this);

        mFakeRcsProvider = new FakeRcsProvider();
        mContentResolver = (MockContentResolver) mContext.getContentResolver();
        mContentResolver.addProvider("rcs", mFakeRcsProvider);

        mRcsMessageStoreController = new RcsMessageStoreController(mContentResolver, null);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetRcsThreads() {
        doReturn(123).when(mMockParticipant).getId();
        RcsThreadQueryParameters queryParameters =
                RcsThreadQueryParameters.builder().withParticipant(mMockParticipant).isGroupThread(
                        true).limitResultsTo(30).sort(true).build();

        mFakeRcsProvider.setExpectedQueryParameters(Uri.parse("content://rcs/thread"),
                new String[]{"_id"}, null, null, "ASCENDING");
        mRcsMessageStoreController.getRcsThreads(queryParameters);
    }

    /**
     * TODO(sahinc): fix the test once there is an implementation in place
     */
    @Test
    public void testGetMessageCount() {
        assertEquals(1018, mRcsMessageStoreController.getMessageCount(0));
    }

    /**
     * Mocking/spying ContentProviders break in different ways. Use a fake instead. The
     * RcsMessageStoreController doesn't care if RcsProvider works as expected (and doesn't have
     * visibility into it) - so verifying whether we use the correct parameters should suffice.
     */
    class FakeRcsProvider extends MockContentProvider {
        private Uri mExpectedUri;
        private String[] mExpectedProjection;
        private String mExpectedWhereClause;
        private String[] mExpectedWhereArgs;
        private String mExpectedSortOrder;

        void setExpectedQueryParameters(Uri uri, String[] projection, String whereClause,
                String[] whereArgs, String sortOrder) {
            mExpectedUri = uri;
            mExpectedProjection = projection;
            mExpectedWhereClause = whereClause;
            mExpectedWhereArgs = whereArgs;
            mExpectedSortOrder = sortOrder;
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

    }
}
