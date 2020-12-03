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

package android.telephony.ims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.telephony.ims.aidl.IImsConfig;
import android.telephony.ims.aidl.IImsConfigCallback;
import android.telephony.ims.stub.ImsConfigImplBase;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.LinkedBlockingQueue;

@RunWith(AndroidJUnit4.class)
public class ImsConfigImplTest {

    private static final int TEST_KEY = 1;
    private static final int TEST_INT_VALUE = 2;
    private static final String TEST_STRING_VALUE = "abc";

    private static class ImsConfigImpl extends ImsConfigImplBase {

        public Pair<Integer, Integer> latestIntConfig;
        public Pair<Integer, String> latestStringConfig;
        // for testing caching
        private boolean mIsGetConfigCalled = false;

        @Override
        public int setConfig(int item, int value) {
            latestIntConfig = new Pair<>(item, value);
            return CONFIG_RESULT_SUCCESS;
        }

        @Override
        public int setConfig(int item, String value) {
            latestStringConfig = new Pair<>(item, value);
            return CONFIG_RESULT_SUCCESS;
        }

        @Override
        public int getConfigInt(int item) {
            mIsGetConfigCalled = true;
            if (latestIntConfig == null) {
                return CONFIG_RESULT_UNKNOWN;
            }
            if (latestIntConfig.first == item) {
                return latestIntConfig.second;
            }
            return CONFIG_RESULT_UNKNOWN;
        }

        @Override
        public String getConfigString(int item) {
            mIsGetConfigCalled = true;
            if (latestStringConfig == null) {
                return null;
            }
            if (latestStringConfig.first == item) {
                return latestStringConfig.second;
            }
            return null;
        }

        public boolean getIsGetConfigCalledAndReset() {
            boolean result = mIsGetConfigCalled;
            mIsGetConfigCalled = false;
            return result;
        }
    }

    private ImsConfigImpl mConfigUT;
    private IImsConfig mConfigBinder;

    @Before
    public void setUp() throws Exception {
        mConfigUT = new ImsConfigImpl();
        mConfigBinder = mConfigUT.getIImsConfig();
    }

    @After
    public void tearDown() {
        mConfigUT = null;
        mConfigBinder = null;
    }

    @Test
    public void testIntCaching() throws Exception {
        final LinkedBlockingQueue<Pair<Integer, Integer>> mConfigChanges =
                new LinkedBlockingQueue<>();
        final IImsConfigCallback mConfigCallback = new IImsConfigCallback.Stub() {

            @Override
            public void onIntConfigChanged(int item, int value) {
                mConfigChanges.offer(new Pair<>(item, value));
            }
            @Override
            public void onStringConfigChanged(int item, String value) {}
        };
        mConfigBinder.addImsConfigCallback(mConfigCallback);
        mConfigBinder.setConfigInt(TEST_KEY, TEST_INT_VALUE);
        // verify callback is called properly
        Pair<Integer, Integer> callbackResult = mConfigChanges.poll();
        assertNotNull(callbackResult);
        assertEquals(TEST_KEY, callbackResult.first.intValue());
        assertEquals(TEST_INT_VALUE, callbackResult.second.intValue());
        // verify set is called on Impl
        assertNotNull(mConfigUT.latestIntConfig);
        assertEquals(TEST_KEY, mConfigUT.latestIntConfig.first.intValue());
        assertEquals(TEST_INT_VALUE, mConfigUT.latestIntConfig.second.intValue());
        // Now get the test key, this impl should not be called, as it is cached internally.
        assertEquals(TEST_INT_VALUE, mConfigBinder.getConfigInt(TEST_KEY));
        assertFalse(mConfigUT.getIsGetConfigCalledAndReset());
    }

    @Test
    public void testStringCaching() throws Exception {
        final LinkedBlockingQueue<Pair<Integer, String>> mConfigChanges =
                new LinkedBlockingQueue<>();
        final IImsConfigCallback mConfigCallback = new IImsConfigCallback.Stub() {

            @Override
            public void onIntConfigChanged(int item, int value) {}
            @Override
            public void onStringConfigChanged(int item, String value) {
                mConfigChanges.offer(new Pair<>(item, value));
            }
        };
        mConfigBinder.addImsConfigCallback(mConfigCallback);
        mConfigBinder.setConfigString(TEST_KEY, TEST_STRING_VALUE);
        // verify callback is called properly
        Pair<Integer, String> callbackResult = mConfigChanges.poll();
        assertNotNull(callbackResult);
        assertEquals(TEST_KEY, callbackResult.first.intValue());
        assertEquals(TEST_STRING_VALUE, callbackResult.second);
        // verify set is called on Impl
        assertNotNull(mConfigUT.latestStringConfig);
        assertEquals(TEST_KEY, mConfigUT.latestStringConfig.first.intValue());
        assertEquals(TEST_STRING_VALUE, mConfigUT.latestStringConfig.second);
        // Now get the test key, this impl should not be called, as it is cached internally.
        assertEquals(TEST_STRING_VALUE, mConfigBinder.getConfigString(TEST_KEY));
        assertFalse(mConfigUT.getIsGetConfigCalledAndReset());
    }
}
