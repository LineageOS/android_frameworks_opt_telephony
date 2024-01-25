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

package com.android.internal.telephony.security;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.telephony.CellularIdentifierDisclosure;

import com.android.internal.telephony.TestExecutorService;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class CellularIdentifierDisclosureNotifierTest {

    // 15 minutes and 100 milliseconds. Can be used to advance time in a test executor far enough
    // to (hopefully, if the code is behaving) close a disclosure window.
    private static final long WINDOW_CLOSE_ADVANCE_MILLIS = (15 * 60 * 1000) + 100;
    private static final int SUB_ID_1 = 1;
    private static final int SUB_ID_2 = 2;
    private CellularIdentifierDisclosure mDislosure;

    @Before
    public void setUp() {
        mDislosure =
                new CellularIdentifierDisclosure(
                        CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);
    }

    @Test
    public void testInitializeDisabled() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        assertFalse(notifier.isEnabled());
    }

    @Test
    public void testDisableAddDisclosureNop() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        assertFalse(notifier.isEnabled());
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
    }

    @Test
    public void testAddDisclosureEmergencyNop() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);
        CellularIdentifierDisclosure emergencyDisclosure =
                new CellularIdentifierDisclosure(
                        CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        true);

        notifier.enable();
        notifier.addDisclosure(SUB_ID_1, emergencyDisclosure);

        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
    }

    @Test
    public void testAddDisclosureCountIncrements() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        notifier.enable();

        for (int i = 0; i < 3; i++) {
            notifier.addDisclosure(SUB_ID_1, mDislosure);
        }

        assertEquals(3, notifier.getCurrentDisclosureCount(SUB_ID_1));
    }

    @Test
    public void testSingleDisclosureStartAndEndTimesAreEqual() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        notifier.enable();

        notifier.addDisclosure(SUB_ID_1, mDislosure);

        assertEquals(1, notifier.getCurrentDisclosureCount(SUB_ID_1));
        assertTrue(notifier.getFirstOpen(SUB_ID_1).equals(notifier.getCurrentEnd(SUB_ID_1)));
    }

    @Test
    public void testMultipleDisclosuresTimeWindows() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        notifier.enable();

        notifier.addDisclosure(SUB_ID_1, mDislosure);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        notifier.addDisclosure(SUB_ID_1, mDislosure);

        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));
        assertTrue(notifier.getFirstOpen(SUB_ID_1).isBefore(notifier.getCurrentEnd(SUB_ID_1)));
    }

    @Test
    public void testAddDisclosureThenWindowClose() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        // One round of disclosures
        notifier.enable();
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));

        // Window close should reset the counter
        executor.advanceTime(WINDOW_CLOSE_ADVANCE_MILLIS);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));

        // A new disclosure should increment as normal
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        assertEquals(1, notifier.getCurrentDisclosureCount(SUB_ID_1));
    }

    @Test
    public void testDisableClosesWindow() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        // One round of disclosures
        notifier.enable();
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));

        notifier.disable();
        assertFalse(notifier.isEnabled());

        // We're disabled now so no disclosures should open the disclosure window
        notifier.addDisclosure(SUB_ID_1, mDislosure);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
    }

    @Test
    public void testMultipleSubIdsTrackedIndependently() {
        TestExecutorService executor = new TestExecutorService();
        CellularIdentifierDisclosureNotifier notifier =
                new CellularIdentifierDisclosureNotifier(executor, 15, TimeUnit.MINUTES);

        notifier.enable();
        for (int i = 0; i < 3; i++) {
            notifier.addDisclosure(SUB_ID_1, mDislosure);
        }

        for (int i = 0; i < 4; i++) {
            notifier.addDisclosure(SUB_ID_2, mDislosure);
        }

        assertEquals(3, notifier.getCurrentDisclosureCount(SUB_ID_1));
        assertEquals(4, notifier.getCurrentDisclosureCount(SUB_ID_2));
    }
}
