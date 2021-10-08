/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.NetworkCapabilities;
import android.os.HandlerThread;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataRetryManager.DataRetryRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataRetryManagerTest extends TelephonyTest {
    private DataRetryManager mDataRetryManager;
    private DataRetryManagerTestHandler mDataRetryManagerTestHandler;

    private class DataRetryManagerTestHandler extends HandlerThread {

        private DataRetryManagerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mDataRetryManager = new DataRetryManager(mPhone, getLooper());
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("DataRetryManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mDataRetryManagerTestHandler = new DataRetryManagerTestHandler(
                getClass().getSimpleName());
        mDataRetryManagerTestHandler.start();
        waitUntilReady();
        waitForLastHandlerAction(mDataRetryManagerTestHandler.getThreadHandler());

        logd("DataRetryManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        mDataRetryManagerTestHandler.quit();
        super.tearDown();
    }

    // The purpose of this test is to ensure retry rule in string format can be correctly parsed.
    @Test
    public void testRetryRulesParsingFromString() {
        String ruleString = "  capabilities   =    eims,     retry_interval = 1000   ";
        DataRetryRule rule = new DataRetryRule(ruleString);
        assertEquals(1, rule.getNetworkCapabilities().length);
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS,
                rule.getNetworkCapabilities()[0]);
        assertEquals(10, rule.getMaxRetries());
        assertNull(rule.getFailCauses());
        assertEquals(1000, rule.getRetryInterval().toMillis());
        assertFalse(rule.isBackedOffDuration());

        ruleString = "fail_causes=8|27|28|29|30| 32| 33|35 |50|51|111|-5 |-6|65537|65538|-3|2253|"
                + "2254, maximum_retries=0  ";
        rule = new DataRetryRule(ruleString);
        assertNull(rule.getNetworkCapabilities());
        assertEquals(0, rule.getMaxRetries());
        assertArrayEquals(new int[]{8, 27, 28, 29, 30, 32, 33, 35, 50, 51, 111, -5, -6, 65537,
                65538, -3, 2253, 2254}, rule.getFailCauses());
        assertNull(rule.getRetryInterval());
        assertFalse(rule.isBackedOffDuration());

        ruleString = "capabilities = internet | enterprise | dun | ims | fota, retry_interval=2000,"
                + "backoff=true,maximum_retries=                13";
        rule = new DataRetryRule(ruleString);
        assertArrayEquals(
                new int[] {NetworkCapabilities.NET_CAPABILITY_INTERNET,
                        NetworkCapabilities.NET_CAPABILITY_ENTERPRISE,
                        NetworkCapabilities.NET_CAPABILITY_DUN,
                        NetworkCapabilities.NET_CAPABILITY_IMS,
                        NetworkCapabilities.NET_CAPABILITY_FOTA},
                rule.getNetworkCapabilities());
        assertEquals(13, rule.getMaxRetries());
        assertNull(rule.getFailCauses());
        assertEquals(2000, rule.getRetryInterval().toMillis());
        assertTrue(rule.isBackedOffDuration());

        ruleString = " capabilities = mms   |supl |  cbs, retry_interval =  2000  ";
        rule = new DataRetryRule(ruleString);
        assertArrayEquals(new int[] {
                NetworkCapabilities.NET_CAPABILITY_MMS,
                NetworkCapabilities.NET_CAPABILITY_SUPL,
                NetworkCapabilities.NET_CAPABILITY_CBS},
                rule.getNetworkCapabilities());
        assertEquals(10, rule.getMaxRetries());
        assertNull(rule.getFailCauses());
        assertEquals(2000, rule.getRetryInterval().toMillis());
        assertFalse(rule.isBackedOffDuration());
    }
}
