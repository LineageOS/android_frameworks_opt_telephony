/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import android.telephony.ims.aidl.ICapabilityExchangeEventListener;
import android.telephony.ims.aidl.IImsRcsFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.CapabilityExchangeEventListener;
import android.telephony.ims.stub.RcsCapabilityExchangeImplBase;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.telephony.ims.ImsTestBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class RcsFeatureTest  extends ImsTestBase {

    private class TestRcsFeature extends RcsFeature {

        public CapabilityExchangeEventListener eventListener;
        public int eventListenerCreateCount = 0;
        public int eventListenerDestroyCount = 0;

        TestRcsFeature() {
            super(Runnable::run);
        }

        @NonNull
        @Override
        public RcsCapabilityExchangeImplBase createCapabilityExchangeImpl(
                @NonNull CapabilityExchangeEventListener listener) {
            eventListener = listener;
            eventListenerCreateCount++;
            return mMockCapExchange;
        }

        @Override
        public void destroyCapabilityExchangeImpl(
                @NonNull RcsCapabilityExchangeImplBase capExchangeImpl) {
            eventListenerDestroyCount++;
            eventListener = null;
        }
    }

    private TestRcsFeature mRcsFeatureUT;
    private IImsRcsFeature mRcsFeatureBinder;

    @Mock
    private ICapabilityExchangeEventListener mEventListener;
    @Mock
    private RcsCapabilityExchangeImplBase mMockCapExchange;

    @Before
    public void setup() throws Exception {
        super.setUp();
        mRcsFeatureUT = new TestRcsFeature();
        mRcsFeatureBinder = mRcsFeatureUT.getBinder();
    }

    @After
    public void tearDown() throws Exception {
        mRcsFeatureUT = null;
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testCreateDestroyCapabilityExchangeImpl() throws Exception {
        // Ensure create is only called once when a listener is set
        mRcsFeatureBinder.setCapabilityExchangeEventListener(mEventListener);
        assertNotNull(mRcsFeatureUT.eventListener);
        assertEquals(1, mRcsFeatureUT.eventListenerCreateCount);
        assertEquals(0, mRcsFeatureUT.eventListenerDestroyCount);

        // Ensure destroying the listener only results in one destroy call.
        mRcsFeatureBinder.setCapabilityExchangeEventListener(null);
        assertNull(mRcsFeatureUT.eventListener);
        assertEquals(1, mRcsFeatureUT.eventListenerCreateCount);
        assertEquals(1, mRcsFeatureUT.eventListenerDestroyCount);

        // Ensure create is only called once more when a listener is set
        mRcsFeatureBinder.setCapabilityExchangeEventListener(mEventListener);
        assertNotNull(mRcsFeatureUT.eventListener);
        assertEquals(2, mRcsFeatureUT.eventListenerCreateCount);
        assertEquals(1, mRcsFeatureUT.eventListenerDestroyCount);
    }
}
