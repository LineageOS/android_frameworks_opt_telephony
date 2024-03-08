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

package com.android.internal.telephony.metrics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VonrHelperTest extends TelephonyTest {
    private static final int SUBID = 1;

    private static class TestableVonrHelper extends VonrHelper {
        TestableVonrHelper(@NonNull FeatureFlags featureFlags) {
            super(featureFlags);
        }

        @Override
        public void updateVonrEnabledState() {
            mVonrRunnable.run();
        }
    }

    private TestableVonrHelper mVonrHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        doReturn(SUBID).when(mPhone).getSubId();
        doReturn(false).when(mTelephonyManager).isVoNrEnabled();
        mVonrHelper = new TestableVonrHelper(mFeatureFlags);
        doReturn(true).when(mFeatureFlags).vonrEnabledMetric();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    @SmallTest
    public void vonr_enabled() {
        doReturn(true).when(mTelephonyManager).isVoNrEnabled();

        mVonrHelper.updateVonrEnabledState();

        assertThat(mVonrHelper.getVonrEnabled(SUBID)).isTrue();
    }

    @Test
    @SmallTest
    public void vonr_disabled() {
        mVonrHelper.updateVonrEnabledState();

        assertThat(mVonrHelper.getVonrEnabled(SUBID)).isFalse();
    }
}
