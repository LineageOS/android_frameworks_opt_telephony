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

package com.android.internal.telephony.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import android.os.Looper;
import android.os.PersistableBundle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataSettingsManager.DataSettingsManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class DataSettingsManagerTest extends TelephonyTest {
    private static final String DATA_ROAMING_IS_USER_SETTING = "data_roaming_is_user_setting_key0";

    // Mocked
    DataSettingsManagerCallback mMockedDataSettingsManagerCallback;

    DataSettingsManager mDataSettingsManagerUT;
    PersistableBundle mBundle;

    @Before
    public void setUp() throws Exception {
        logd("DataSettingsManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mMockedDataSettingsManagerCallback = Mockito.mock(DataSettingsManagerCallback.class);
        mBundle = mContextFixture.getCarrierConfigBundle();
        doReturn(true).when(mDataConfigManager).isConfigCarrierSpecific();

        doReturn("").when(mSubscriptionController).getDataEnabledOverrideRules(anyInt());

        mDataSettingsManagerUT = new DataSettingsManager(mPhone, mDataNetworkController,
                Looper.myLooper(), mMockedDataSettingsManagerCallback);
        logd("DataSettingsManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        logd("tearDown");
        super.tearDown();
    }

    @Test
    public void testDefaultDataRoamingEnabled() {
        doReturn(true).when(mDataConfigManager).isDataRoamingEnabledByDefault();
        mDataSettingsManagerUT.setDefaultDataRoamingEnabled();
        assertTrue(mDataSettingsManagerUT.isDataRoamingEnabled());

        mDataSettingsManagerUT.setDataRoamingEnabled(false);
        processAllMessages();
        assertFalse(mDataSettingsManagerUT.isDataRoamingEnabled());

        mDataSettingsManagerUT.setDefaultDataRoamingEnabled();
        assertFalse(mDataSettingsManagerUT.isDataRoamingEnabled());
    }

    @Test
    public void testDefaultDataRoamingEnabledFromUpgrade() {
        doReturn(true).when(mDataConfigManager).isDataRoamingEnabledByDefault();
        mContext.getSharedPreferences("", 0).edit()
                .putBoolean(DATA_ROAMING_IS_USER_SETTING, true).commit();
        mDataSettingsManagerUT.setDefaultDataRoamingEnabled();
        assertFalse(mDataSettingsManagerUT.isDataRoamingEnabled());
    }
}
