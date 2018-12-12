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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

import android.content.ContentValues;
import android.net.Uri;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThreadQueryParameters;
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
    private FakeProviderWithAsserts mFakeRcsProvider;
    private FakeProviderWithAsserts mFakeMmsSmsProvider;

    @Mock
    RcsParticipant mMockParticipant;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsMessageStoreControllerTest");
        MockitoAnnotations.initMocks(this);

        mFakeRcsProvider = new FakeProviderWithAsserts();
        mFakeMmsSmsProvider = new FakeProviderWithAsserts();
        mContentResolver = (MockContentResolver) mContext.getContentResolver();
        mContentResolver.addProvider("rcs", mFakeRcsProvider);
        mContentResolver.addProvider("mms-sms", mFakeMmsSmsProvider);

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

    @Test
    public void testCreateRcsParticipant() {
        // verify the first query to existing canonical addresses
        mFakeMmsSmsProvider.setExpectedQueryParameters(
                Uri.parse("content://mms-sms/canonical-addresses"), new String[]{"_id"},
                "address=?", new String[]{"+5551234567"}, null);

        // verify the insert on canonical addresses
        ContentValues expectedMmsSmsValues = new ContentValues(1);
        expectedMmsSmsValues.put("address", "+5551234567");
        mFakeMmsSmsProvider.setInsertReturnValue(
                Uri.parse("content://mms-sms/canonical-address/456"));
        mFakeMmsSmsProvider.setExpectedInsertParameters(
                Uri.parse("content://mms-sms/canonical-addresses"), expectedMmsSmsValues);

        // verify the final insert on rcs participants
        ContentValues expectedRcsValues = new ContentValues(1);
        expectedRcsValues.put("canonical_address_id", 456);
        mFakeRcsProvider.setInsertReturnValue(Uri.parse("content://rcs/participant/1001"));
        mFakeRcsProvider.setExpectedInsertParameters(Uri.parse("content://rcs/participant"),
                expectedRcsValues);

        RcsParticipant participant = mRcsMessageStoreController.createRcsParticipant("+5551234567");

        assertThat(participant.getId()).isEqualTo(1001);
        assertThat(participant.getCanonicalAddress()).isEqualTo("+5551234567");
    }

    @Test
    public void testUpdateRcsParticipantAlias() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("rcs_alias", "New Alias");
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/participant"),
                contentValues, "_id=?", new String[]{"551"});

        mRcsMessageStoreController.updateRcsParticipantAlias(551, "New Alias");
    }

    /**
     * TODO(sahinc): fix the test once there is an implementation in place
     */
    @Test
    public void testGetMessageCount() {
        assertEquals(1018, mRcsMessageStoreController.getMessageCount(0));
    }
}
