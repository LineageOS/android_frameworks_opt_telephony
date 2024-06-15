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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.telephony.CellularIdentifierDisclosure;

import com.android.internal.telephony.TestExecutorService;
import com.android.internal.telephony.metrics.CellularSecurityTransparencyStats;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;
import com.android.internal.telephony.subscription.SubscriptionManagerService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.concurrent.TimeUnit;

public class CellularIdentifierDisclosureNotifierTest {

    // 15 minutes and 100 milliseconds. Can be used to advance time in a test executor far enough
    // to (hopefully, if the code is behaving) close a disclosure window.
    private static final long WINDOW_CLOSE_ADVANCE_MILLIS = (15 * 60 * 1000) + 100;
    private static final int SUB_ID_1 = 1;
    private static final int SUB_ID_2 = 2;
    private CellularIdentifierDisclosure mDislosure;
    private CellularNetworkSecuritySafetySource mSafetySource;
    private CellularSecurityTransparencyStats mStats;
    private TestExecutorService mExecutor;
    private SubscriptionManagerService mSubscriptionManagerService;
    private Context mContext;
    private InOrder mInOrder;

    @Before
    public void setUp() {
        mDislosure =
                new CellularIdentifierDisclosure(
                        CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        false);
        mSafetySource = mock(CellularNetworkSecuritySafetySource.class);
        mStats = mock(CellularSecurityTransparencyStats.class);
        mExecutor = new TestExecutorService();
        mSubscriptionManagerService = mock(SubscriptionManagerService.class);
        mContext = mock(Context.class);
        mInOrder = inOrder(mSafetySource);
    }

    @Test
    public void testInitializeDisabled() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        assertFalse(notifier.isEnabled());
        verify(mSafetySource, never()).setIdentifierDisclosureIssueEnabled(any(), anyBoolean());
    }

    @Test
    public void testDisableAddDisclosureNop() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        assertFalse(notifier.isEnabled());
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
        verify(mSafetySource, never())
                .setIdentifierDisclosure(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void testAddDisclosureEmergencyNop() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        CellularIdentifierDisclosure emergencyDisclosure =
                new CellularIdentifierDisclosure(
                        CellularIdentifierDisclosure.NAS_PROTOCOL_MESSAGE_ATTACH_REQUEST,
                        CellularIdentifierDisclosure.CELLULAR_IDENTIFIER_IMSI,
                        "001001",
                        true);

        notifier.enable(mContext);
        notifier.addDisclosure(mContext, SUB_ID_1, emergencyDisclosure);

        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
        verify(mSafetySource, never())
                .setIdentifierDisclosure(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    public void testAddDisclosureCountIncrements() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();
        notifier.enable(mContext);

        for (int i = 0; i < 3; i++) {
            notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        }

        assertEquals(3, notifier.getCurrentDisclosureCount(SUB_ID_1));
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(2), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(3), any(), any());
    }

    @Test
    public void testSingleDisclosureStartAndEndTimesAreEqual() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();
        notifier.enable(mContext);

        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);

        assertEquals(1, notifier.getCurrentDisclosureCount(SUB_ID_1));
        Assert.assertEquals(notifier.getFirstOpen(SUB_ID_1), notifier.getCurrentEnd(SUB_ID_1));
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
    }

    @Test
    public void testMultipleDisclosuresTimeWindows() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();
        notifier.enable(mContext);

        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);

        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));
        assertTrue(notifier.getFirstOpen(SUB_ID_1).isBefore(notifier.getCurrentEnd(SUB_ID_1)));
        verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
    }

    @Test
    public void testAddDisclosureThenWindowClose() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        // One round of disclosures
        notifier.enable(mContext);
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(2), any(), any());

        // Window close should reset the counter
        mExecutor.advanceTime(WINDOW_CLOSE_ADVANCE_MILLIS);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));

        // A new disclosure should increment as normal
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        assertEquals(1, notifier.getCurrentDisclosureCount(SUB_ID_1));
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
    }

    @Test
    public void testDisableClosesWindow() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        // One round of disclosures
        notifier.enable(mContext);
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        assertEquals(2, notifier.getCurrentDisclosureCount(SUB_ID_1));
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(2), any(), any());

        notifier.disable(mContext);
        assertFalse(notifier.isEnabled());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosureIssueEnabled(any(), eq(false));

        // We're disabled now so no disclosures should open the disclosure window
        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        assertEquals(0, notifier.getCurrentDisclosureCount(SUB_ID_1));
        mInOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testMultipleSubIdsTrackedIndependently() {
        CellularIdentifierDisclosureNotifier notifier = getNotifier();

        notifier.enable(mContext);
        for (int i = 0; i < 3; i++) {
            notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);
        }
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(1), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(2), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_1), eq(3), any(), any());

        for (int i = 0; i < 4; i++) {
            notifier.addDisclosure(mContext, SUB_ID_2, mDislosure);
        }
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_2), eq(1), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_2), eq(2), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_2), eq(3), any(), any());
        mInOrder.verify(mSafetySource, times(1))
                .setIdentifierDisclosure(any(), eq(SUB_ID_2), eq(4), any(), any());

        assertEquals(3, notifier.getCurrentDisclosureCount(SUB_ID_1));
        assertEquals(4, notifier.getCurrentDisclosureCount(SUB_ID_2));
    }

    @Test
    public void testLogDisclsoure() {
        String mcc = "100";
        String mnc = "200";

        CellularIdentifierDisclosureNotifier notifier = getNotifier();
        SubscriptionInfoInternal subInfoMock = mock(SubscriptionInfoInternal.class);
        when(mSubscriptionManagerService.getSubscriptionInfoInternal(SUB_ID_1)).thenReturn(
                subInfoMock);
        when(subInfoMock.getMcc()).thenReturn(mcc);
        when(subInfoMock.getMnc()).thenReturn(mnc);

        notifier.addDisclosure(mContext, SUB_ID_1, mDislosure);

        verify(mStats, times(1)).logIdentifierDisclosure(mDislosure, mcc, mnc,
                mDislosure.isEmergency());
    }

    private CellularIdentifierDisclosureNotifier getNotifier() {
        return new CellularIdentifierDisclosureNotifier(mExecutor, 15, TimeUnit.MINUTES,
                mSafetySource, mSubscriptionManagerService, mStats);
    }
}
