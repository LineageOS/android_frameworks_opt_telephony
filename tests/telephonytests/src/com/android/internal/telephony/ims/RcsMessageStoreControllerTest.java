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

import static android.provider.Telephony.RcsColumns.Rcs1To1ThreadColumns.FALLBACK_THREAD_ID_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsGroupThreadColumns.GROUP_ICON_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsGroupThreadColumns.GROUP_NAME_COLUMN;
import static android.provider.Telephony.RcsColumns.RcsGroupThreadColumns.OWNER_PARTICIPANT_COLUMN;
import static android.telephony.ims.RcsThreadQueryParams.THREAD_TYPE_GROUP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.content.ContentValues;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.ims.RcsParticipant;
import android.telephony.ims.RcsThreadQueryParams;
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
        RcsThreadQueryParams queryParameters =
                new RcsThreadQueryParams.Builder().setParticipant(mMockParticipant)
                        .setThreadType(THREAD_TYPE_GROUP).setResultLimit(30).build();

        // TODO - limit the query as per queryParameters. This will change how the query is executed
        mFakeRcsProvider.setExpectedQueryParameters(Uri.parse("content://rcs/thread"), null, null,
                null, null);

        try {
            mRcsMessageStoreController.getRcsThreads(queryParameters);
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }

    @Test
    public void testCreateRcsParticipant() throws RemoteException {
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

        int participantId = mRcsMessageStoreController.createRcsParticipant("+5551234567", "alias");

        assertThat(participantId).isEqualTo(1001);
    }

    @Test
    public void testUpdateRcsParticipantAlias() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put("rcs_alias", "New Alias");
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/participant/551"),
                contentValues, null, null);

        try {
            mRcsMessageStoreController.setRcsParticipantAlias(551, "New Alias");
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }

    @Test
    public void testSet1To1ThreadFallbackThreadId() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(FALLBACK_THREAD_ID_COLUMN, 456L);
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/p2p_thread/123"),
                contentValues, null, null);
        try {
            mRcsMessageStoreController.set1To1ThreadFallbackThreadId(123, 456L);
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }

    @Test
    public void testSetGroupThreadName() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(GROUP_NAME_COLUMN, "new name");
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/group_thread/345"),
                contentValues, null, null);

        try {
            mRcsMessageStoreController.setGroupThreadName(345, "new name");
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }

    @Test
    public void testSetGroupThreadIcon() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(GROUP_ICON_COLUMN, "newIcon");
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/group_thread/345"),
                contentValues, null, null);

        try {
            mRcsMessageStoreController.setGroupThreadIcon(345, Uri.parse("newIcon"));
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }

    @Test
    public void testSetGroupThreadOwner() {
        ContentValues contentValues = new ContentValues(1);
        contentValues.put(OWNER_PARTICIPANT_COLUMN, 9);
        mFakeRcsProvider.setExpectedUpdateParameters(Uri.parse("content://rcs/group_thread/454"),
                contentValues, null, null);

        RcsParticipant participant = new RcsParticipant(9);

        try {
            mRcsMessageStoreController.setGroupThreadOwner(454, participant.getId());
        } catch (RemoteException e) {
            // eat the exception as there is no provider - we care about the expected update assert
        }
    }
}
