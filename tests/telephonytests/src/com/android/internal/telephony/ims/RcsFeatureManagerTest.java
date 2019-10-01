/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.telephony.ims;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.ims.RcsFeatureManager;
import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class RcsFeatureManagerTest extends TelephonyTest {

    @Mock
    RcsFeatureManager.SubscriptionManagerProxy mSubscriptionManagerProxy;

    @Before
    public void setUp() throws Exception {
        super.setUp("RcsFeatureManagerTest");

        when(mSubscriptionManagerProxy.getSubId(0)).thenReturn(1);

        RcsFeatureManager.setSubscriptionManager(mSubscriptionManagerProxy);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test RCS UCE feature is supported by carrier
     */
    @Test
    @SmallTest
    public void testRcsUceFeatureIsSupportedByCarrier() {
        int phoneId = mPhone.getPhoneId();
        PersistableBundle bundle = mContextFixture.getCarrierConfigBundle();

        // RCS UCE is supported by carrier
        setIsSupportedByCarrier(bundle, true);
        assertTrue(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));

        // RCS UCE is not supported by carrier
        setIsSupportedByCarrier(bundle, false);
        assertFalse(RcsFeatureManager.isRcsUceSupportedByCarrier(mContext, phoneId));
    }

    private void setIsSupportedByCarrier(PersistableBundle bundle, boolean isSupported) {
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL, isSupported);
        bundle.putBoolean(CarrierConfigManager.KEY_USE_RCS_SIP_OPTIONS_BOOL, isSupported);
    }
}
