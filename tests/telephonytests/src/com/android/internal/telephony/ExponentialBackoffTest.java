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

package com.android.internal.telephony;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.os.Handler;
import android.os.Looper;
import android.support.test.runner.AndroidJUnit4;

import com.android.internal.telephony.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExponentialBackoffTest extends ImsTestBase {

    private static final int START_DELAY_MS = 10;
    private static final int MAXIMUM_DELAY_MS = 1000;
    private static final int MULTIPLIER = 2;

    private ExponentialBackoff mBackoffUnderTest;
    private Handler mHandler = spy(new Handler(Looper.getMainLooper()));
    private Runnable mRunnable = spy(new MyRunnable());

    public class MyRunnable implements Runnable {
        @Override
        public void run() {
            // do nothing
        }
    }

    @Before
    public void setUp() {
        mBackoffUnderTest = new ExponentialBackoff(
                START_DELAY_MS, MAXIMUM_DELAY_MS, MULTIPLIER, mHandler, mRunnable);
    }

    @After
    public void tearDown() {
        mBackoffUnderTest.stop();
    }

    @Test
    public void testStartBackoff() {
        mBackoffUnderTest.start();
        long delay = mBackoffUnderTest.getCurrentDelay();
        waitForHandlerActionDelayed(mHandler, delay, 2 * delay);

        // The runnable is executed after timeout event occurred.
        verify(mRunnable).run();
    }

    @Test
    public void testStopBackoff() {
        mBackoffUnderTest.start();
        reset(mHandler);

        mBackoffUnderTest.stop();
        verify(mHandler).removeCallbacks(mRunnable);
    }

    @Test
    public void testDelayIncreasedExponentially() {
        mBackoffUnderTest.start();
        // guarantee START_DELAY_MS * 2 ^ i <= MAXIMUM_DELAY_MS
        for (int i = 1; i < 5; i++) {
            mBackoffUnderTest.notifyFailed();
            long delay = mBackoffUnderTest.getCurrentDelay();
            long minDelay = (long) (START_DELAY_MS * Math.pow(MULTIPLIER, i - 1));
            long maxDelay = (long) (START_DELAY_MS * Math.pow(MULTIPLIER, i));
            assertTrue("delay = " + delay + " minDelay = " + minDelay, delay >= minDelay);
            assertTrue("delay = " + delay + " maxDelay = " + maxDelay, delay <= maxDelay);
        }
    }

    @Test
    public void testDelayShouldNotExceededTheMaximumLimit() {
        mBackoffUnderTest.start();
        // guarantee START_DELAY_MS * 2 ^ 30 > MAXIMUM_DELAY_MS
        for (int i = 1; i < 30; i++) {
            mBackoffUnderTest.notifyFailed();
        }
        long delay = mBackoffUnderTest.getCurrentDelay();
        assertTrue(
                "delay = " + delay + " maximumDelay = " + MAXIMUM_DELAY_MS,
                delay <= MAXIMUM_DELAY_MS);
    }
}
