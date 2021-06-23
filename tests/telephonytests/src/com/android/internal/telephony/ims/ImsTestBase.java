/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.testing.TestableLooper;

import androidx.test.InstrumentationRegistry;

import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to load Mockito Resources into a test.
 */
public class ImsTestBase {

    protected Context mContext;
    protected List<TestableLooper> mTestableLoopers = new ArrayList<>();
    protected TestableLooper mTestableLooper;

    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        // Set up the looper if it does not exist on the test thread.
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mTestableLooper = TestableLooper.get(ImsTestBase.this);
        monitorTestableLooper(mTestableLooper);
    }

    public void tearDown() throws Exception {
        unmonitorTestableLooper(mTestableLooper);
        for (TestableLooper looper : mTestableLoopers) {
            looper.destroy();
        }
        TestableLooper.remove(ImsTestBase.this);
    }

    protected final void waitForHandlerAction(Handler h, long timeoutMillis) {
        waitForHandlerActionDelayed(h, timeoutMillis, 0 /*delayMs*/);
    }

    protected final void waitForHandlerActionDelayed(Handler h, long timeoutMillis, long delayMs) {
        final CountDownLatch lock = new CountDownLatch(1);
        h.postDelayed(lock::countDown, delayMs);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    /**
     * Add a TestableLooper to the list of monitored loopers
     * @param looper looper to be added if it doesn't already exist
     */
    public void monitorTestableLooper(TestableLooper looper) {
        if (looper != null && !mTestableLoopers.contains(looper)) {
            mTestableLoopers.add(looper);
        }
    }

    /**
     * Remove a TestableLooper from the list of monitored loopers
     * @param looper looper to be removed if it exists
     */
    public void unmonitorTestableLooper(TestableLooper looper) {
        if (looper != null && mTestableLoopers.contains(looper)) {
            mTestableLoopers.remove(looper);
        }
    }

    /**
     * Process all messages at the current time for all monitored TestableLoopers
     */
    public void processAllMessages() {
        if (mTestableLoopers.isEmpty()) {
            fail("mTestableLoopers is empty. Please make sure to add @RunWithLooper annotation");
        }
        while (!areAllTestableLoopersIdle()) {
            for (TestableLooper looper : mTestableLoopers) looper.processAllMessages();
        }
    }

    /**
     * Check if there are any messages to be processed in any monitored TestableLooper
     * Delayed messages to be handled at a later time will be ignored
     * @return true if there are no messages that can be handled at the current time
     *         across all monitored TestableLoopers
     */
    private boolean areAllTestableLoopersIdle() {
        for (TestableLooper looper : mTestableLoopers) {
            if (!looper.getLooper().getQueue().isIdle()) return false;
        }
        return true;
    }
}
