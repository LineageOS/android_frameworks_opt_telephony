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

import static org.junit.Assert.assertEquals;

import android.telephony.data.ApnSetting;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.RetryManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Data throttler tests
 */
public class DataThrottlerTest extends TelephonyTest {

    private DataThrottler mDataThrottler;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mDataThrottler = new DataThrottler();
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
        mDataThrottler.setRetryTime(ApnSetting.TYPE_DEFAULT, 1234567890L);
        assertEquals(1234567890L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DEFAULT));
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY,
                mDataThrottler.getRetryTime(ApnSetting.TYPE_MMS));

        mDataThrottler.setRetryTime(ApnSetting.TYPE_DEFAULT | ApnSetting.TYPE_DUN, 13579L);
        assertEquals(13579L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DEFAULT));
        assertEquals(13579L, mDataThrottler.getRetryTime(ApnSetting.TYPE_DUN));

        mDataThrottler.setRetryTime(ApnSetting.TYPE_MMS, -10);
        assertEquals(RetryManager.NO_SUGGESTED_RETRY_DELAY,
                mDataThrottler.getRetryTime(ApnSetting.TYPE_MMS));

        mDataThrottler.setRetryTime(ApnSetting.TYPE_FOTA | ApnSetting.TYPE_EMERGENCY,
                RetryManager.NO_RETRY);
        assertEquals(RetryManager.NO_RETRY, mDataThrottler.getRetryTime(ApnSetting.TYPE_FOTA));
        assertEquals(RetryManager.NO_RETRY, mDataThrottler.getRetryTime(ApnSetting.TYPE_EMERGENCY));
    }
}
