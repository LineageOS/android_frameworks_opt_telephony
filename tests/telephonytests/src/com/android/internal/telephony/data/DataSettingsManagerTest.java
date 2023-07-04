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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Looper;
import android.os.PersistableBundle;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.data.DataSettingsManager.DataSettingsManagerCallback;
import com.android.internal.telephony.subscription.SubscriptionInfoInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Set;

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

        doReturn(new SubscriptionInfoInternal.Builder().setId(1).build())
                .when(mSubscriptionManagerService).getSubscriptionInfoInternal(anyInt());

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
    public void testMobileDataPolicyParsing() {
        //Valid new data policy
        Set<Integer> policies = mDataSettingsManagerUT.getMobileDataPolicyEnabled("1, 2");
        assertThat(policies.size()).isEqualTo(2);
        Set<Integer> policies2 = mDataSettingsManagerUT.getMobileDataPolicyEnabled(",2");
        assertThat(policies2.size()).isEqualTo(1);
        Set<Integer> policies3 = mDataSettingsManagerUT.getMobileDataPolicyEnabled("");
        assertThat(policies3.size()).isEqualTo(0);

        // Invalid
        Set<Integer> invalid = mDataSettingsManagerUT.getMobileDataPolicyEnabled(
                "nonExistent, 1, 2");
        assertThat(invalid.size()).isEqualTo(2);

        Set<Integer> invalid2 = mDataSettingsManagerUT.getMobileDataPolicyEnabled(
                "nonExistent ,,");
        assertThat(invalid2.size()).isEqualTo(0);
    }

    @Test
    public void testGetPolicies() {
        mDataSettingsManagerUT.setMobileDataPolicy(1, true);
        mDataSettingsManagerUT.setMobileDataPolicy(2, true);
        processAllMessages();

        ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(mSubscriptionManagerService, times(2))
                .setEnabledMobileDataPolicies(anyInt(), stringArgumentCaptor.capture());
        assertEquals("1,2", stringArgumentCaptor.getValue());
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

    @Test
    public void testUpdateDataEnabledAndNotifyOverride() throws Exception {
        // Mock another DDS phone.
        int ddsPhoneId = 1;
        int ddsSubId = 2;
        doReturn(ddsSubId).when(mSubscriptionManagerService).getDefaultDataSubId();
        Phone phone2 = Mockito.mock(Phone.class);
        doReturn(ddsPhoneId).when(phone2).getPhoneId();
        doReturn(ddsSubId).when(phone2).getSubId();
        doReturn(ddsPhoneId).when(mSubscriptionManagerService).getPhoneId(ddsSubId);
        DataSettingsManager dataSettingsManager2 = Mockito.mock(DataSettingsManager.class);
        doReturn(dataSettingsManager2).when(phone2).getDataSettingsManager();
        mPhones = new Phone[] {mPhone, phone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        ArgumentCaptor<DataSettingsManagerCallback> callbackArgumentCaptor = ArgumentCaptor
                .forClass(DataSettingsManagerCallback.class);

        mDataSettingsManagerUT.sendEmptyMessage(11 /* EVENT_INITIALIZE */);
        processAllMessages();

        // Verify listening to user enabled status of other phones.
        verify(dataSettingsManager2).registerCallback(callbackArgumentCaptor.capture());
        DataSettingsManagerCallback callback = callbackArgumentCaptor.getValue();

        // Mock the phone as nonDDS.
        mDataSettingsManagerUT.setDataEnabled(TelephonyManager.DATA_ENABLED_REASON_USER, false, "");
        processAllMessages();
        clearInvocations(mPhone);

        // Verify the override policy doesn't take effect because the DDS is user disabled.
        mDataSettingsManagerUT.setMobileDataPolicy(
                TelephonyManager.MOBILE_DATA_POLICY_AUTO_DATA_SWITCH, true);
        processAllMessages();
        verify(mPhone, never()).notifyDataEnabled(anyBoolean(), anyInt());

        // Verify the override takes effect upon DDS user enabled.
        doReturn(true).when(phone2).isUserDataEnabled();
        callback.onUserDataEnabledChanged(true, "callingPackage");
        verify(mPhone).notifyDataEnabled(true, TelephonyManager.DATA_ENABLED_REASON_OVERRIDE);
    }
}
