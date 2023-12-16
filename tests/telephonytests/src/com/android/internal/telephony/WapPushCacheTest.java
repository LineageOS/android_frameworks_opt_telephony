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

package com.android.internal.telephony;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

public class WapPushCacheTest extends TelephonyTest {

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
    }

    @After
    public void tearDown() throws Exception {
        WapPushCache.clear();
        WapPushCache.sTelephonyFacade = new TelephonyFacade();
        super.tearDown();
    }

    @Test
    public void testGetWapMessageSize() {
        long expectedSize = 100L;
        byte[] location = "content://mms".getBytes();
        byte[] transactionId = "123".getBytes();

        WapPushCache.putWapMessageSize(location, transactionId, expectedSize);
        long size = WapPushCache.getWapMessageSize(location);

        assertEquals(expectedSize, size);
    }

    @Test
    public void testGetWapMessageSize_withTransactionIdAppended() {
        long expectedSize = 100L;
        byte[] location = "content://mms".getBytes();
        byte[] transactionId = "123".getBytes();
        byte[] joinedKey = new byte[location.length + transactionId.length];
        System.arraycopy(location, 0, joinedKey, 0, location.length);
        System.arraycopy(transactionId, 0, joinedKey, location.length, transactionId.length);

        WapPushCache.putWapMessageSize(location, transactionId, expectedSize);
        long size = WapPushCache.getWapMessageSize(joinedKey);

        assertEquals(expectedSize, size);
    }

    @Test
    public void testGetWapMessageSize_nonexistentThrows() {
        assertThrows(NoSuchElementException.class, () ->
                WapPushCache.getWapMessageSize("content://mms".getBytes())
        );
    }
    @Test
    public void testGetWapMessageSize_emptyLocationUrlThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                WapPushCache.getWapMessageSize(new byte[0])
        );
    }

    @Test
    public void testPutWapMessageSize_invalidValuePreventsInsert() {
        long expectedSize = 0L;
        byte[] location = "content://mms".getBytes();
        byte[] transactionId = "123".getBytes();

        WapPushCache.putWapMessageSize(location, transactionId, expectedSize);

        assertEquals(0, WapPushCache.size());
    }

    @Test
    public void testPutWapMessageSize_sizeLimitExceeded_oldestEntryRemoved() {
        long expectedSize = 100L;
        for (int i = 0; i < 251; i++) {
            byte[] location = ("" + i).getBytes();
            byte[] transactionId = "abc".getBytes();
            WapPushCache.putWapMessageSize(location, transactionId, expectedSize);
        }

        // assert one of the entries inserted above has been removed
        assertEquals(500, WapPushCache.size());
        // assert last entry added exists
        assertEquals(expectedSize, WapPushCache.getWapMessageSize("250".getBytes()));
        // assert the first entry added was removed
        assertThrows(NoSuchElementException.class, () ->
                WapPushCache.getWapMessageSize("0".getBytes())
        );
    }

    @Test
    public void testPutWapMessageSize_expiryExceeded_entryRemoved() {
        long currentTime = Clock.systemUTC().millis();
        TelephonyFacade facade = mock(TelephonyFacade.class);
        when(facade.getElapsedSinceBootMillis()).thenReturn(currentTime);
        WapPushCache.sTelephonyFacade = facade;

        long expectedSize = 100L;
        byte[] transactionId = "abc".getBytes();
        byte[] location1 = "old".getBytes();
        byte[] location2 = "new".getBytes();

        WapPushCache.putWapMessageSize(location1, transactionId, expectedSize);
        assertEquals(2, WapPushCache.size());

        // advance time
        when(facade.getElapsedSinceBootMillis())
                .thenReturn(currentTime + TimeUnit.DAYS.toMillis(14) + 1);

        WapPushCache.putWapMessageSize(location2, transactionId, expectedSize);

        assertEquals(2, WapPushCache.size());
        assertEquals(expectedSize, WapPushCache.getWapMessageSize(location2));
        assertThrows(NoSuchElementException.class, () ->
                WapPushCache.getWapMessageSize(location1)
        );
    }
}
