/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.UserManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TelephonyAdminReceiverTest extends TelephonyTest {

    private TelephonyAdminReceiver mTelephonyAdminReceiver;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mTelephonyAdminReceiver = new TelephonyAdminReceiver(mContext, mPhone);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test_nullUserManager() {
        mUserManager = null;
        TelephonyAdminReceiver telephonyAdminReceiver = new TelephonyAdminReceiver(mContext,
                mPhone);
        assertFalse(telephonyAdminReceiver.isCellular2gDisabled());
    }

    @Test
    public void test_nullIntent_noUpdate() {
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());

        mContext.sendBroadcast(new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));

        verify(mPhone, never()).sendSubscriptionSettings(anyBoolean());
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());
    }

    @Test
    public void test_userRestrictionsNotChanged_noUpdate() {
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CELLULAR_2G)).thenReturn(false);

        mContext.sendBroadcast(new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));

        verify(mPhone, never()).sendSubscriptionSettings(anyBoolean());
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());
    }

    @Test
    public void test_userRestrictionToggled_shouldUpdate() {
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_CELLULAR_2G)).thenReturn(
                true).thenReturn(false);

        mContext.sendBroadcast(new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));
        assertTrue(mTelephonyAdminReceiver.isCellular2gDisabled());

        mContext.sendBroadcast(new Intent(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));
        assertFalse(mTelephonyAdminReceiver.isCellular2gDisabled());
        verify(mPhone, times(2)).sendSubscriptionSettings(false);
    }
}
