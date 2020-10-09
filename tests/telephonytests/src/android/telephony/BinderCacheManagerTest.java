/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.os.IInterface;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
public class BinderCacheManagerTest {

    @Mock IInterface mInterface;
    @Mock IBinder mIBinder;

    private BinderCacheManager<IInterface> mBinderCache;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mInterface.asBinder()).thenReturn(mIBinder);
        mBinderCache = new BinderCacheManager<>(() -> mInterface);
    }

    @Test
    @SmallTest
    public void testGetConnection() {
        when(mIBinder.isBinderAlive()).thenReturn(true);
        assertEquals(mInterface, mBinderCache.getBinder());
        when(mIBinder.isBinderAlive()).thenReturn(false);
        assertNull(mBinderCache.getBinder());
    }

    @Test
    @SmallTest
    public void testAddListenerAndDie() throws Exception {
        IBinder.DeathRecipient recipient = populateCacheCaptureDeathRecipent();
        CountDownLatch l = new CountDownLatch(1);
        assertEquals(mInterface, mBinderCache.listenOnBinder(l, l::countDown));
        when(mIBinder.isBinderAlive()).thenReturn(false);
        recipient.binderDied();
        assertEquals(0, l.getCount());
        assertNull(mBinderCache.getBinder());
        // Listeners should not be abailable wile remote process is not available.
        assertNull(mBinderCache.listenOnBinder(l, l::countDown));
        assertNull(mBinderCache.removeRunnable(l));
        verify(mIBinder).unlinkToDeath(eq(recipient), anyInt());
    }

    @Test
    @SmallTest
    public void testListenerNotCalledAfterRemoved() throws Exception {
        IBinder.DeathRecipient recipient = populateCacheCaptureDeathRecipent();
        CountDownLatch l = new CountDownLatch(1);
        assertEquals(mInterface, mBinderCache.listenOnBinder(l, l::countDown));
        assertEquals(mInterface, mBinderCache.removeRunnable(l));
        when(mIBinder.isBinderAlive()).thenReturn(false);
        recipient.binderDied();
        // Callback should never have been called because the runnable was removed before it died.
        assertNotEquals(0, l.getCount());
    }

    @Test
    @SmallTest
    public void testAddListenerAlreadyDead() throws Exception {
        IBinder.DeathRecipient recipient = populateCacheCaptureDeathRecipent();
        when(mIBinder.isBinderAlive()).thenReturn(false);
        recipient.binderDied();
        CountDownLatch l = new CountDownLatch(1);
        assertNull(mBinderCache.listenOnBinder(l, l::countDown));
        assertNull(mBinderCache.removeRunnable(l));
        // Callback shouldn't be called if it was never added in the first place.
        assertNotEquals(0, l.getCount());
    }

    /**
     * Populate the cache with mInterface & capture the associated DeathRecipient
     */
    private IBinder.DeathRecipient populateCacheCaptureDeathRecipent() throws Exception {
        when(mIBinder.isBinderAlive()).thenReturn(true);
        // Call getBinder() to populate cache the first time.
        assertEquals(mInterface, mBinderCache.getBinder());
        ArgumentCaptor<IBinder.DeathRecipient> captor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);
        verify(mIBinder).linkToDeath(captor.capture(), anyInt());
        IBinder.DeathRecipient recipient = captor.getValue();
        assertNotNull(recipient);
        return recipient;
    }
}
