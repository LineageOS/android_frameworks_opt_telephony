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

package com.android.internal.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SlidingWindowEventCounterTest {
    long mInitialTime;

    @Before
    public void setUp() throws Exception {
        mInitialTime = SystemClock.elapsedRealtime();
    }

    @Test
    public void test_returnsTrue_ifEnoughEntriesInWindow() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 3; i++) {
            counter.addOccurrence(mInitialTime + i);
        }
        assertTrue(counter.isInWindow());
    }

    @Test
    public void test_returnsTrue_ifMoreThanEnoughEntriesInWindow() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 3; i++) {
            counter.addOccurrence(mInitialTime + i);
        }
        for (int i = 0; i < 3; i++) {
            counter.addOccurrence(mInitialTime + i + 50);
        }
        assertTrue(counter.isInWindow());
    }

    @Test
    public void test_returnsFalse_ifNotEnoughEntriesInWindow() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 2; i++) {
            counter.addOccurrence(mInitialTime + i);
        }
        assertFalse(counter.isInWindow());
    }

    @Test
    public void test_returnsFalse_ifEnoughEntriesButTooFarApart() {
        SlidingWindowEventCounter counter = new SlidingWindowEventCounter(100, 3);
        for (int i = 0; i < 2; i++) {
            counter.addOccurrence(mInitialTime + i);
        }
        counter.addOccurrence(mInitialTime + 101);
        counter.addOccurrence(mInitialTime + 102);
        assertFalse(counter.isInWindow());
    }
}
