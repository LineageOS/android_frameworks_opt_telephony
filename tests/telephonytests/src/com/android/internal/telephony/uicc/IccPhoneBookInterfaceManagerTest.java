/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.ContentValues;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;

public class IccPhoneBookInterfaceManagerTest extends TelephonyTest {
    // Mocked classes
    private AdnRecordCache mAdnRecordCache;
    private AdnRecord mAdnRecord;
    private SimPhonebookRecordCache mSimPhonebookRecordCache;

    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceMgr;
    private IccPhoneBookInterfaceManagerHandler mIccPhoneBookInterfaceManagerHandler;
    private List<AdnRecord> mAdnList = Arrays.asList(mAdnRecord);

    private class IccPhoneBookInterfaceManagerHandler extends HandlerThread {

        private IccPhoneBookInterfaceManagerHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mIccPhoneBookInterfaceMgr = new IccPhoneBookInterfaceManager(mPhone);
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mAdnRecordCache = mock(AdnRecordCache.class);
        mAdnRecord = mock(AdnRecord.class);
        mSimPhonebookRecordCache = mock(SimPhonebookRecordCache.class);
        //APP_FAM_3GPP default mPhone is GSM
        doReturn(mSimRecords).when(mPhone).getIccRecords();
        doReturn(mAdnRecordCache).when(mSimRecords).getAdnCache();

        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message response = (Message) invocation.getArguments()[2];
                //set result for load ADN EF
                AsyncResult.forMessage(response).result = mAdnList;
                response.sendToTarget();
                return null;
            }
        }).when(mAdnRecordCache).requestLoadAllAdnLike(anyInt(), anyInt(), (Message) anyObject());

        doAnswer(invocation -> {
            Message response = (Message) invocation.getArguments()[0];
            //set result for load ADN EF
            AsyncResult.forMessage(response).result = mAdnList;
            response.sendToTarget();
            return null;
        }).when(mSimPhonebookRecordCache).requestLoadAllPbRecords((Message)anyObject());
        mIccPhoneBookInterfaceManagerHandler = new IccPhoneBookInterfaceManagerHandler(TAG);
        mIccPhoneBookInterfaceManagerHandler.start();

        waitUntilReady();
        replaceInstance(IccPhoneBookInterfaceManager.class,
                "mSimPbRecordCache", mIccPhoneBookInterfaceMgr, mSimPhonebookRecordCache);
    }

    @After
    public void tearDown() throws Exception {
        mIccPhoneBookInterfaceManagerHandler.quit();
        mIccPhoneBookInterfaceManagerHandler.join();
        mIccPhoneBookInterfaceManagerHandler = null;
        mIccPhoneBookInterfaceMgr = null;
        mSimPhonebookRecordCache = null;
        mAdnList = null;
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testAdnEFLoadWithFailure() {
        doReturn(false).when(mSimPhonebookRecordCache).isEnabled();
        List<AdnRecord> adnListResult = mIccPhoneBookInterfaceMgr.getAdnRecordsInEf(
                IccConstants.EF_ADN);
        assertEquals(mAdnList, adnListResult);
        //mock a ADN Ef load failure
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message response = (Message) invocation.getArguments()[2];
                AsyncResult.forMessage(response).exception = new RuntimeException();
                response.sendToTarget();
                return null;
            }
        }).when(mAdnRecordCache).requestLoadAllAdnLike(anyInt(), anyInt(), (Message) anyObject());
        List<AdnRecord> adnListResultNew = mIccPhoneBookInterfaceMgr.getAdnRecordsInEf(
                IccConstants.EF_ADN);
        //the later read return null due to exception
        assertNull(adnListResultNew);
        //verify the previous read is not got affected
        assertEquals(mAdnList, adnListResult);
    }

    @Test
    @SmallTest
    public void testAdnEFLoadByPbCacheWithFailure() {
        doReturn(true).when(mSimPhonebookRecordCache).isEnabled();
        List<AdnRecord> adnListResult = mIccPhoneBookInterfaceMgr.getAdnRecordsInEf(
                IccConstants.EF_ADN);
        assertEquals(mAdnList, adnListResult);
        //mock a ADN Ef load failure
        doAnswer(invocation -> {
            Message response = (Message) invocation.getArguments()[0];
            AsyncResult.forMessage(response).exception = new RuntimeException();
            response.sendToTarget();
            return null;
        }).when(mSimPhonebookRecordCache).requestLoadAllPbRecords((Message) anyObject());
        List<AdnRecord> adnListResultNew = mIccPhoneBookInterfaceMgr.getAdnRecordsInEf(
                IccConstants.EF_ADN);
        //the later read return null due to exception
        assertNull(adnListResultNew);
        //verify the previous read is not got affected
        assertEquals(mAdnList, adnListResult);
    }

    @Test
    @SmallTest
    public void testUpdateAdnRecord() {
        doReturn(false).when(mSimPhonebookRecordCache).isEnabled();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message response = (Message) invocation.getArguments()[4];
                //set result for update ADN EF
                AsyncResult.forMessage(response).exception = null;
                response.sendToTarget();
                return null;
            }
        }).when(mAdnRecordCache).updateAdnBySearch(
            anyInt(), any(), any(),
            any(), (Message) anyObject());

        ContentValues values = new ContentValues();
        values.put(IccProvider.STR_TAG, "");
        values.put(IccProvider.STR_NUMBER, "");
        values.put(IccProvider.STR_EMAILS, "");
        values.put(IccProvider.STR_ANRS, "");
        values.put(IccProvider.STR_NEW_TAG, "test");
        values.put(IccProvider.STR_NEW_NUMBER, "123456");
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");

        boolean result = mIccPhoneBookInterfaceMgr.updateAdnRecordsInEfBySearchForSubscriber(
                IccConstants.EF_ADN, values , null);

        assertTrue(result);
    }

    @Test
    @SmallTest
    public void testUpdateAdnRecordByPbCache() {
        doReturn(true).when(mSimPhonebookRecordCache).isEnabled();
        doAnswer(invocation -> {
            Message response = (Message) invocation.getArguments()[2];
            //set result for update ADN EF
            AsyncResult.forMessage(response).exception = null;
            response.sendToTarget();
            return null;
        }).when(mSimPhonebookRecordCache).updateSimPbAdnBySearch(any(),
            any(), (Message) anyObject());

        ContentValues values = new ContentValues();
        values.put(IccProvider.STR_TAG, "");
        values.put(IccProvider.STR_NUMBER, "");
        values.put(IccProvider.STR_EMAILS, "");
        values.put(IccProvider.STR_ANRS, "");
        values.put(IccProvider.STR_NEW_TAG, "test");
        values.put(IccProvider.STR_NEW_NUMBER, "123456");
        values.put(IccProvider.STR_NEW_EMAILS, "");
        values.put(IccProvider.STR_NEW_ANRS, "");

        boolean result = mIccPhoneBookInterfaceMgr.updateAdnRecordsInEfBySearchForSubscriber(
                IccConstants.EF_ADN, values , "12");

        assertTrue(result);
    }
}
