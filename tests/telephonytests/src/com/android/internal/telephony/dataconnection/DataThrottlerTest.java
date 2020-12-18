/**
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

package com.android.internal.telephony.dataconnection;

import static com.android.internal.telephony.dataconnection.DcTracker.REQUEST_TYPE_HANDOVER;
import static com.android.internal.telephony.dataconnection.DcTracker.REQUEST_TYPE_NORMAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.telephony.AccessNetworkConstants;
import android.telephony.data.ApnSetting;
import android.telephony.data.ApnThrottleStatus;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Data throttler tests
 */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataThrottlerTest extends TelephonyTest {

    private static final boolean DBG = true;
    private DataThrottler mDataThrottler;

    @Mock
    private DataThrottler.Callback mMockChangedCallback1;

    @Mock
    private DataThrottler.Callback mMockChangedCallback2;

    private static final int DEFAULT_APN_TYPE = ApnSetting.TYPE_DEFAULT & ~(ApnSetting.TYPE_HIPRI);

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDataThrottler = new DataThrottler(1, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        mDataThrottler.registerForApnThrottleStatusChanges(mMockChangedCallback1);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test the behavior of a retry manager with no waiting APNs set.
     */
    @Test
    @SmallTest
    public void testSetRetryTime() throws Exception {
        final ArgumentCaptor<List<ApnThrottleStatus>> statusCaptor =
                ArgumentCaptor.forClass((Class) List.class);

        List<List<ApnThrottleStatus>> expectedStatuses = new ArrayList<>();
        processAllMessages();
        expectedStatuses.add(List.of());


        mDataThrottler.setRetryTime(ApnSetting.TYPE_DEFAULT, 1234567890L,
                REQUEST_TYPE_NORMAL);
        assertEquals(1234567890L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DEFAULT));
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY,
                mDataThrottler.getRetryTime(ApnSetting.TYPE_MMS));

        processAllMessages();
        expectedStatuses.add(List.of(
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(ApnSetting.TYPE_HIPRI)
                        .setThrottleExpiryTimeMillis(1234567890L)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_NEW_CONNECTION)
                        .build(),
                new ApnThrottleStatus.Builder()
                    .setSlotIndex(1)
                    .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                    .setApnType(DEFAULT_APN_TYPE)
                    .setThrottleExpiryTimeMillis(1234567890L)
                    .setRetryType(ApnThrottleStatus.RETRY_TYPE_NEW_CONNECTION)
                    .build())
        );


        mDataThrottler.setRetryTime(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_DUN, 13579L,
                REQUEST_TYPE_HANDOVER);
        assertEquals(13579L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DEFAULT));
        assertEquals(13579L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DUN));

        processAllMessages();
        expectedStatuses.add(List.of(
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(ApnSetting.TYPE_HIPRI)
                        .setThrottleExpiryTimeMillis(13579L)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_HANDOVER)
                        .build(),
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(ApnSetting.TYPE_DUN)
                        .setThrottleExpiryTimeMillis(13579L)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_HANDOVER)
                        .build(),
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(DEFAULT_APN_TYPE)
                        .setThrottleExpiryTimeMillis(13579L)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_HANDOVER)
                        .build())
        );


        mDataThrottler.setRetryTime(ApnSetting.TYPE_MMS, -10,
                REQUEST_TYPE_NORMAL);
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY,
                mDataThrottler.getRetryTime(ApnSetting.TYPE_MMS));
        processAllMessages();
        expectedStatuses.add(List.of(
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setNoThrottle()
                        .setApnType(ApnSetting.TYPE_MMS)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_NEW_CONNECTION)
                        .build()
        ));

        mDataThrottler.setRetryTime(ApnSetting.TYPE_FOTA | ApnSetting.TYPE_EMERGENCY,
                RetryManager.NO_RETRY, REQUEST_TYPE_HANDOVER);

        processAllMessages();
        expectedStatuses.add(List.of(
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(ApnSetting.TYPE_EMERGENCY)
                        .setThrottleExpiryTimeMillis(RetryManager.NO_RETRY)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_NONE)
                        .build(),
                new ApnThrottleStatus.Builder()
                        .setSlotIndex(1)
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setApnType(ApnSetting.TYPE_FOTA)
                        .setThrottleExpiryTimeMillis(RetryManager.NO_RETRY)
                        .setRetryType(ApnThrottleStatus.RETRY_TYPE_NONE)
                        .build()
        ));

        assertEquals(RetryManager.NO_RETRY, mDataThrottler.getRetryTime(ApnSetting.TYPE_FOTA));
        assertEquals(RetryManager.NO_RETRY, mDataThrottler.getRetryTime(ApnSetting.TYPE_EMERGENCY));


        // Loop through statuses and test everything
        verify(mMockChangedCallback1, times(expectedStatuses.size()))
                .onApnThrottleStatusChanged(statusCaptor.capture());

        // Check actual statuses
        List<List<ApnThrottleStatus>> actualStatuses =
                (List<List<ApnThrottleStatus>>) statusCaptor.getAllValues();
        assertEquals(expectedStatuses.size(), actualStatuses.size());

        if (DBG) {
            logd("expectedStatuses.size() = " + expectedStatuses.size());
            logd("actualStatuses.size() = " + actualStatuses.size());
        }

        Comparator<ApnThrottleStatus> comparator = (o1, o2) ->
                Integer.compare(o1.getApnType(), o2.getApnType());

        for (int i = 0; i < expectedStatuses.size(); i++) {
            List<ApnThrottleStatus> atsExpected = new ArrayList<>(expectedStatuses.get(i));
            List<ApnThrottleStatus> atsActual = new ArrayList<>(actualStatuses.get(i));

            atsExpected.sort(comparator);
            atsActual.sort(comparator);
            assertEquals("Lists at index " + i + " don't match",
                    atsExpected, atsActual);
        }

        this.mDataThrottler.registerForApnThrottleStatusChanges(mMockChangedCallback2);
    }
}
