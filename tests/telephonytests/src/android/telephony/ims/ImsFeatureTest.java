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

package android.telephony.ims;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import android.os.IInterface;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.ims.feature.ImsFeature;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.internal.IImsFeatureStatusCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ImsFeatureTest {

    private TestImsFeature mTestImsService;

    @Mock
    private IImsFeatureStatusCallback mTestStatusCallback;
    @Mock
    private IImsFeatureStatusCallback mTestStatusCallback2;

    private class TestImsFeature extends ImsFeature {

        public void testSetFeatureState(int featureState) {
            setFeatureState(featureState);
        }

        @Override
        public void onFeatureReady() {

        }

        @Override
        public void onFeatureRemoved() {

        }

        @Override
        public IInterface getBinder() {
            return null;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestImsService = new TestImsFeature();
    }

    @After
    public void tearDown() {
        mTestImsService = null;
    }

    @Test
    @SmallTest
    public void testSetCallbackAndNotify() throws Exception {
        mTestImsService.addImsFeatureStatusCallback(mTestStatusCallback);
        mTestImsService.addImsFeatureStatusCallback(mTestStatusCallback2);

        verify(mTestStatusCallback).notifyImsFeatureStatus(eq(ImsFeature.STATE_NOT_AVAILABLE));
        verify(mTestStatusCallback2).notifyImsFeatureStatus(eq(ImsFeature.STATE_NOT_AVAILABLE));
    }

    @Test
    @SmallTest
    public void testSetFeatureAndCheckCallback() throws Exception {
        mTestImsService.addImsFeatureStatusCallback(mTestStatusCallback);
        mTestImsService.addImsFeatureStatusCallback(mTestStatusCallback2);

        mTestImsService.testSetFeatureState(ImsFeature.STATE_READY);

        verify(mTestStatusCallback).notifyImsFeatureStatus(eq(ImsFeature.STATE_READY));
        verify(mTestStatusCallback2).notifyImsFeatureStatus(eq(ImsFeature.STATE_READY));
        assertEquals(ImsFeature.STATE_READY, mTestImsService.getFeatureState());
    }
}
