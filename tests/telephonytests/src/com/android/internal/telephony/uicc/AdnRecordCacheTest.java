/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import static com.android.internal.telephony.uicc.IccConstants.EF_ADN;
import static com.android.internal.telephony.uicc.IccConstants.EF_MBDN;
import static com.android.internal.telephony.uicc.IccConstants.EF_PBR;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class AdnRecordCacheTest extends TelephonyTest {

    private AdnRecordCacheUT mAdnRecordCache;
    private TestLooper mTestLooper;
    private Handler mTestHandler;
    private IccFileHandler mFhMock;
    private UsimPhoneBookManager mUsimPhoneBookManager;

    @SuppressWarnings("ClassCanBeStatic")
    private class AdnRecordCacheUT extends AdnRecordCache {
        AdnRecordCacheUT(IccFileHandler fh, UsimPhoneBookManager usimPhoneBookManager) {
            super(fh, usimPhoneBookManager);
        }

        protected void setAdnLikeWriters(int key, ArrayList<Message> waiters) {
            super.setAdnLikeWriters(key, waiters);
        }

        protected void setAdnLikeFiles(int key, ArrayList<AdnRecord> adnRecordList) {
            super.setAdnLikeFiles(key, adnRecordList);
        }

        protected void setUserWriteResponse(int key, Message message) {
            super.setUserWriteResponse(key, message);
        }

        protected UsimPhoneBookManager getUsimPhoneBookManager() {
            return super.getUsimPhoneBookManager();
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        // Mocked classes
        mFhMock = mock(IccFileHandler.class);
        mUsimPhoneBookManager = mock(UsimPhoneBookManager.class);
        mTestLooper = new TestLooper();
        mTestHandler = new Handler(mTestLooper.getLooper());
        mTestHandler.post(
                () -> mAdnRecordCache = new AdnRecordCacheUT(mFhMock, mUsimPhoneBookManager));
        mTestLooper.dispatchAll();
    }

    @After
    public void tearDown() throws Exception {
        if (mTestLooper != null) {
            mTestLooper.dispatchAll();
            mTestLooper = null;
        }
        mTestHandler.removeCallbacksAndMessages(null);
        mTestHandler = null;
        mAdnRecordCache = null;
        super.tearDown();
    }

    @Test
    public void resetTest() {
        Message message1 = Message.obtain(mTestHandler);
        Message message2 = Message.obtain(mTestHandler);

        // test data to create mAdnLikeWaiters
        ArrayList<Message> waiters = new ArrayList<>();
        waiters.add(message1);
        mAdnRecordCache.setAdnLikeWriters(EF_MBDN, waiters);

        // test data to create mAdnLikeFiles
        setAdnLikeFiles(EF_MBDN);

        // test data to create mUserWriteResponse
        mAdnRecordCache.setUserWriteResponse(EF_MBDN, message2);

        mAdnRecordCache.reset();

        mTestLooper.dispatchAll();
        AsyncResult ar1 = (AsyncResult) message1.obj;
        AsyncResult ar2 = (AsyncResult) message2.obj;
        Assert.assertTrue(ar1.exception.toString().contains("AdnCache reset"));
        Assert.assertTrue(ar2.exception.toString().contains("AdnCace reset"));
    }

    @Test
    public void updateAdnByIndexEfException() {
        int efId = 0x6FC5;
        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.updateAdnByIndex(efId, null, 0, null, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains("EF is not known ADN-like EF:0x6FC5")));
    }

    @Test
    public void updateAdnByIndex_WriteResponseException() {
        int efId = EF_MBDN;
        Message message = Message.obtain(mTestHandler);
        Message message2 = Message.obtain(mTestHandler);
        AdnRecord adnRecord = new AdnRecord("AlphaTag", "123456789");
        // test data to create mUserWriteResponse
        mAdnRecordCache.setUserWriteResponse(efId, message2);
        mAdnRecordCache.updateAdnByIndex(efId, adnRecord, 0, null, message);

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains("Have pending update for EF:0x6FC7")));
    }

    @Test
    public void updateAdnByIndex() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, "success2", null);
                    response.sendToTarget();
                    return response;
                })
                .when(mFhMock)
                .getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        Assert.assertNotNull(message);
        AdnRecord adnRecord = new AdnRecord("AlphaTag", "123456789");
        // test data to create mUserWriteResponse
        mAdnRecordCache.updateAdnByIndex(EF_MBDN, adnRecord, 0, null, message);
        mTestLooper.startAutoDispatch();
        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
    }

    @Test
    public void updateAdnBySearch_EfException() {
        int efId = 0x6FC5;
        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.updateAdnBySearch(efId, null, null, null, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains("EF is not known ADN-like EF:0x6FC5")));
    }

    @Test
    public void updateAdnBySearch_Exception() {
        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.updateAdnBySearch(EF_MBDN, null, null, null, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains("Adn list not exist for EF:0x6FC7")));
    }

    @Test
    public void updateAdnBySearch_AdnListError() {
        int efId = EF_MBDN;
        setAdnLikeFiles(efId);
        Message message = Message.obtain(mTestHandler);
        AdnRecord oldAdn = new AdnRecord("oldAlphaTag", "123456789");
        mAdnRecordCache.updateAdnBySearch(efId, oldAdn, null, null, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains(
                "Adn record don't exist for ADN Record 'oldAlphaTag'")));
    }

    @Test
    public void updateAdnBySearch_PendingUpdate() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, "success2", null);
                    response.sendToTarget();
                    return response;
                })
                .when(mFhMock)
                .getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        int efId = EF_MBDN;
        setAdnLikeFiles(efId);
        Message message = Message.obtain(mTestHandler);
        AdnRecord oldAdn = new AdnRecord("AlphaTag", "123456789");
        mAdnRecordCache.updateAdnBySearch(efId, oldAdn, null, null, message);
        mTestLooper.dispatchAll();

        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
    }

    @Test
    public void updateAdnBySearch() {
        doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, "success", null);
                    response.sendToTarget();
                    return response;
                })
                .when(mFhMock)
                .getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));

        int efId = EF_MBDN;
        setAdnLikeFiles(efId);
        Message message = Message.obtain(mTestHandler);
        AdnRecord oldAdn = new AdnRecord("AlphaTag", "123456789");
        mAdnRecordCache.updateAdnBySearch(efId, oldAdn, null, null, message);
        mTestLooper.dispatchAll();

        verify(mFhMock, times(1)).getEFLinearRecordSize(anyInt(), isNull(), any(Message.class));
    }


    @Test
    public void updateAdnBySearch_AdnException() {
        doReturn(null).when(mUsimPhoneBookManager).loadEfFilesFromUsim();
        Message message = Message.obtain(mTestHandler);
        AdnRecord oldAdn = new AdnRecord("oldAlphaTag", "123456789");
        mAdnRecordCache.updateAdnBySearch(EF_PBR, oldAdn, null, null, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNotNull(ar.exception);
        assertTrue((ar.exception.toString().contains("Adn list not exist for EF:0x4F30")));
    }

    @Test
    public void requestLoadAllAdnLike_AlreadyLoadedEf() {
        int efId = EF_MBDN;
        setAdnLikeFiles(efId);
        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.requestLoadAllAdnLike(efId, 0, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNull(ar.exception);
        Assert.assertNotNull(ar.result);
    }

    @Test
    public void requestLoadAllAdnLike_AlreadyLoadingEf() {
        int efId = EF_MBDN;
        // test data to create mAdnLikeWaiters
        Message message = Message.obtain(mTestHandler);
        ArrayList<Message> waiters = new ArrayList<>();
        waiters.add(message);
        mAdnRecordCache.setAdnLikeWriters(efId, waiters);
        mAdnRecordCache.requestLoadAllAdnLike(efId, 0, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertNull(ar);
    }

    @Test
    public void requestLoadAllAdnLike_NotKnownEf() {
        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.requestLoadAllAdnLike(EF_MBDN, -1, message);
        mTestLooper.dispatchAll();

        AsyncResult ar = (AsyncResult) message.obj;
        Assert.assertTrue(ar.exception.toString().contains("EF is not known ADN-like EF:0x"));
    }

    @Test
    public void requestLoadAllAdnLike() {
                doAnswer(
                invocation -> {
                    Message response = invocation.getArgument(2);
                    AsyncResult.forMessage(response, null, new CommandException(
                            CommandException.Error.REQUEST_NOT_SUPPORTED));
                    response.sendToTarget();
                    return response;
                })
                .when(mFhMock)
                .loadEFLinearFixedAll(anyInt(), anyString(), any(Message.class));

        Message message = Message.obtain(mTestHandler);
        mAdnRecordCache.requestLoadAllAdnLike(EF_ADN, 0x6FC8, message);
        mTestLooper.dispatchAll();

        verify(mFhMock, times(1)).loadEFLinearFixedAll(anyInt(), anyString(), any(Message.class));
    }

    private void setAdnLikeFiles(int ef) {
        // test data to create mAdnLikeFiles
        ArrayList<AdnRecord> adnRecordList = new ArrayList<>();
        AdnRecord adnRecord = new AdnRecord("AlphaTag", "123456789");
        adnRecordList.add(adnRecord);
        mAdnRecordCache.setAdnLikeFiles(ef, adnRecordList);
    }
}