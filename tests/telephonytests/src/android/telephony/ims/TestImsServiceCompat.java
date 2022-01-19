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

import static org.mockito.Mockito.spy;

import android.content.Context;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.feature.RcsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;

import org.mockito.MockitoAnnotations;

/**
 * Test ImsService compatibility used by mockito to verify functionality.
 */

public class TestImsServiceCompat extends android.telephony.ims.ImsService {

    public TestMmTelFeature mSpyMmTelFeature;
    public TestMmTelFeature mTestMmTelFeature;

    public ImsFeatureConfiguration testFeatureConfig;

    public long testCaps;
    public int createMmtelfeatureCount = 0;

    public TestImsServiceCompat(Context context) {
        attachBaseContext(context);
        MockitoAnnotations.initMocks(this);
        // Must create real MMTelFeature to initialize ImsFeature objects.
        mTestMmTelFeature = new TestMmTelFeature();
        mSpyMmTelFeature = spy(mTestMmTelFeature);
    }

    @Override
    public MmTelFeature createMmTelFeature(int slotId) {
        createMmtelfeatureCount++;
        return mSpyMmTelFeature;
    }

    @Override
    public RcsFeature createRcsFeature(int slotId) {
        return null;
    }

    @Override
    public ImsFeatureConfiguration querySupportedImsFeatures() {
        return testFeatureConfig;
    }

    @Override
    public long getImsServiceCapabilities() {
        return testCaps;
    }
}
