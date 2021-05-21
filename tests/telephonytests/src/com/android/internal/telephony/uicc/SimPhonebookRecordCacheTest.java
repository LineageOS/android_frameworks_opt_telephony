/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Message;

import com.android.internal.telephony.TelephonyTest;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimPhonebookRecordCacheTest extends TelephonyTest {
    private static final int EVENT_PHONEBOOK_RECORDS_RECEIVED = 2;

    private SimPhonebookRecordCache mSimPhonebookRecordCacheUt;
    private SimPhonebookRecordHandler mSimPhonebookRecordHandler;

    private class SimPhonebookRecordHandler extends HandlerThread {
        SimPhonebookRecordHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mSimPhonebookRecordCacheUt =
                    new SimPhonebookRecordCache(mContext, 0, mSimulatedCommands);
            setReady(true);
        }

    };

    @Before
    public void setUp() throws Exception {
        super.setUp(this.getClass().getSimpleName());
        mSimPhonebookRecordHandler = new SimPhonebookRecordHandler(this.getClass().getSimpleName());
        mSimPhonebookRecordHandler.start();
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mSimPhonebookRecordHandler.quit();
        mSimPhonebookRecordHandler.join();
        super.tearDown();
    }

    @Test
    public void testSimPhonebookChangedOnBootup() {
        mSimulatedCommands.notifySimPhonebookChanged();
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        AdnCapacity capacity = mSimPhonebookRecordCacheUt.getAdnCapacity();
        AdnCapacity capVerifer = new AdnCapacity(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertNotNull(capacity);
        assertTrue(capVerifer.equals(capacity));
        mSimulatedCommands.notifySimPhonebookChanged();
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        assertTrue(capacity != mSimPhonebookRecordCacheUt.getAdnCapacity());
        assertTrue(capVerifer.equals(capacity));
    }

    @Test
    public void testGetPhonebookRecords() {
        assertFalse(mSimPhonebookRecordCacheUt.isLoading());
        mSimulatedCommands.notifySimPhonebookChanged();
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        assertFalse(mSimPhonebookRecordCacheUt.isLoading());
        mSimPhonebookRecordCacheUt.requestLoadAllPbRecords(null);
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);

        List<SimPhonebookRecord> records = new ArrayList<SimPhonebookRecord>();
        records.add(new SimPhonebookRecord(10, "ABC", "12345", null, null));
        AsyncResult ar = new AsyncResult(null, new ReceivedPhonebookRecords(4, records), null);
        Message msg = Message.obtain(mSimPhonebookRecordCacheUt,
                EVENT_PHONEBOOK_RECORDS_RECEIVED, ar);
        mSimPhonebookRecordCacheUt.handleMessage(msg);

        assertFalse(mSimPhonebookRecordCacheUt.isLoading());
        List<AdnRecord> adnRecords = mSimPhonebookRecordCacheUt.getAdnRecords();
        assertEquals(adnRecords.size(), 1);
        assertEquals(adnRecords.get(0).getRecId(), 10);
    }

    @Test
    public void testGetPhonebookRecordsWithoutInitization() {
        assertFalse(mSimPhonebookRecordCacheUt.isLoading());
        mSimPhonebookRecordCacheUt.requestLoadAllPbRecords(null);
        assertTrue(mSimPhonebookRecordCacheUt.isLoading());
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        assertFalse(mSimPhonebookRecordCacheUt.isLoading());
    }

    @Test
    public void testUpdatePhonebookRecord() {
        List<AdnRecord> adnRecords = mSimPhonebookRecordCacheUt.getAdnRecords();
        assertEquals(adnRecords.size(), 0);

        AdnRecord newAdn = new AdnRecord(0, 20, "AB", "123", null, null);
        // add
        mSimPhonebookRecordCacheUt.updateSimPbAdnBySearch(null, newAdn, null);
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        adnRecords = mSimPhonebookRecordCacheUt.getAdnRecords();
        assertEquals(adnRecords.size(), 1);
        AdnRecord oldAdn = adnRecords.get(0);
        assertEquals(oldAdn.getRecId(), 20);
        assertEquals(oldAdn.getAlphaTag(), "AB");
        assertEquals(oldAdn.getNumber(), "123");
        // update
        newAdn = new AdnRecord(0, 20, "ABCD", "123456789", null, null);
        mSimPhonebookRecordCacheUt.updateSimPbAdnBySearch(oldAdn, newAdn, null);
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        adnRecords = mSimPhonebookRecordCacheUt.getAdnRecords();
        assertEquals(adnRecords.size(), 1);
        oldAdn = adnRecords.get(0);
        assertEquals(oldAdn.getRecId(), 20);
        assertEquals(oldAdn.getAlphaTag(), "ABCD");
        assertEquals(oldAdn.getNumber(), "123456789");
        // Delete
        newAdn = new AdnRecord(0, 20, null, null, null, null);
        mSimPhonebookRecordCacheUt.updateSimPbAdnBySearch(oldAdn, newAdn, null);
        waitForLastHandlerAction(mSimPhonebookRecordCacheUt);
        adnRecords = mSimPhonebookRecordCacheUt.getAdnRecords();
        assertEquals(adnRecords.size(), 0);
    }
}
