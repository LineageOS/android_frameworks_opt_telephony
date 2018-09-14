/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

public class SubscriptionControllerTest extends TelephonyTest {

    private static final int SINGLE_SIM = 1;
    private String mCallingPackage;
    private SubscriptionController mSubscriptionControllerUT;
    private MockContentResolver mMockContentResolver;

    @Before
    public void setUp() throws Exception {
        super.setUp("SubscriptionControllerTest");

        doReturn(SINGLE_SIM).when(mTelephonyManager).getSimCount();
        doReturn(SINGLE_SIM).when(mTelephonyManager).getPhoneCount();

        replaceInstance(SubscriptionController.class, "sInstance", null, null);

        SubscriptionController.init(mContext, null);
        mSubscriptionControllerUT = SubscriptionController.getInstance();
        mCallingPackage = mContext.getOpPackageName();

        doReturn(1).when(mProxyController).getMaxRafSupported();
        mContextFixture.putIntArrayResource(com.android.internal.R.array.sim_colors, new int[]{5});

        mSubscriptionControllerUT.getInstance().updatePhonesAvailability(new Phone[]{mPhone});
        mMockContentResolver = (MockContentResolver) mContext.getContentResolver();
        mMockContentResolver.addProvider(SubscriptionManager.CONTENT_URI.getAuthority(),
                new FakeTelephonyProvider());

    }

    @After
    public void tearDown() throws Exception {
        /* should clear fake content provider and resolver here */
        mContext.getContentResolver().delete(SubscriptionManager.CONTENT_URI, null, null);

        /* Clear sSlotIndexToSubId since they will otherwise be persistent
         * between each test case. */
        mSubscriptionControllerUT.clearSubInfo();

        /* clear settings for default voice/data/sms sub ID */
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        mSubscriptionControllerUT = null;
        super.tearDown();
    }

    @Test @SmallTest
    public void testInsertSim() {
        int slotID = mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage);

        //verify there is no sim inserted in the SubscriptionManager
        assertEquals(0, slotID);

        //insert one Subscription Info
        mSubscriptionControllerUT.addSubInfoRecord("test", slotID);

        //verify there is one sim
        assertEquals(1, mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));

        //sanity for slot id and sub id
        List<SubscriptionInfo> mSubList = mSubscriptionControllerUT
                .getActiveSubscriptionInfoList(mCallingPackage);
        assertTrue(mSubList != null && mSubList.size() > 0);
        for (int i = 0; i < mSubList.size(); i++) {
            assertTrue(SubscriptionManager.isValidSubscriptionId(
                    mSubList.get(i).getSubscriptionId()));
            assertTrue(SubscriptionManager.isValidSlotIndex(mSubList.get(i).getSimSlotIndex()));
        }
    }

    @Test @SmallTest
    public void testChangeSIMProperty() {
        int dataRoaming = 1;
        int iconTint = 1;
        String disName = "TESTING";
        String disNum = "12345";
        boolean isOpportunistic = true;

        testInsertSim();
        /* Get SUB ID */
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        /* Setting */
        mSubscriptionControllerUT.setDisplayName(disName, subID);
        mSubscriptionControllerUT.setDataRoaming(dataRoaming, subID);
        mSubscriptionControllerUT.setDisplayNumber(disNum, subID);
        mSubscriptionControllerUT.setIconTint(iconTint, subID);
        mSubscriptionControllerUT.setOpportunistic(isOpportunistic, subID);

        /* Getting, there is no direct getter function for each fields of property */
        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(subID, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(dataRoaming, subInfo.getDataRoaming());
        assertEquals(disName, subInfo.getDisplayName());
        assertEquals(iconTint, subInfo.getIconTint());
        assertEquals(disNum, subInfo.getNumber());
        assertEquals(isOpportunistic, subInfo.isOpportunistic());

        /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());
    }

    @Test @SmallTest
    public void testSetGetDisplayNameSrc() {
        testInsertSim();

        /* Get SUB ID */
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        /* Setting */
        String disName = "TESTING";
        long nameSource = 1;
        mSubscriptionControllerUT.setDisplayNameUsingSrc(disName, subID, nameSource);
        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(subID, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(disName, subInfo.getDisplayName());
        assertEquals(nameSource, subInfo.getNameSource());

        /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());

    }

    @Test @SmallTest
    public void testCleanUpSIM() {
        testInsertSim();
        assertFalse(mSubscriptionControllerUT.isActiveSubId(2));
        mSubscriptionControllerUT.clearSubInfo();
        assertFalse(mSubscriptionControllerUT.isActiveSubId(1));
        assertEquals(SubscriptionManager.SIM_NOT_INSERTED,
                mSubscriptionControllerUT.getSlotIndex(1));
    }

    @Test @SmallTest
    public void testDefaultSubID() {
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        /* insert one sim */
        testInsertSim();
        // if support single sim, sms/data/voice default sub should be the same
        assertNotSame(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSubId());
        assertEquals(mSubscriptionControllerUT.getDefaultDataSubId(),
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(mSubscriptionControllerUT.getDefaultDataSubId(),
                mSubscriptionControllerUT.getDefaultVoiceSubId());
    }

    @Test @SmallTest
    public void testSetGetMCCMNC() {
        testInsertSim();
        String mCcMncVERIZON = "310004";
        mSubscriptionControllerUT.setMccMnc(mCcMncVERIZON, 1);

        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(1, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(Integer.parseInt(mCcMncVERIZON.substring(0, 3)), subInfo.getMcc());
        assertEquals(Integer.parseInt(mCcMncVERIZON.substring(3)), subInfo.getMnc());

         /* verify broadcast intent */
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, atLeast(1)).sendBroadcast(captorIntent.capture());
        assertEquals(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED,
                captorIntent.getValue().getAction());
    }

    @Test
    @SmallTest
    public void testSetDefaultDataSubId() throws Exception {
        doReturn(1).when(mPhone).getSubId();

        mSubscriptionControllerUT.setDefaultDataSubId(1);

        verify(mPhone, times(1)).updateDataConnectionTracker();
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendStickyBroadcastAsUser(
                captorIntent.capture(), eq(UserHandle.ALL));

        Intent intent = captorIntent.getValue();
        assertEquals(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED, intent.getAction());

        Bundle b = intent.getExtras();

        assertTrue(b.containsKey(PhoneConstants.SUBSCRIPTION_KEY));
        assertEquals(1, b.getInt(PhoneConstants.SUBSCRIPTION_KEY));
    }

    @Test
    @SmallTest
    public void testMigrateImsSettings() throws Exception {
        testInsertSim();
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        // Set default void subId.
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                subID);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.ENHANCED_4G_MODE_ENABLED,
                1);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.VT_IMS_ENABLED,
                0);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_ENABLED,
                1);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_MODE,
                2);

        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_ROAMING_MODE,
                3);

        mSubscriptionControllerUT.migrateImsSettings();

        // Global settings should be all set.
        assertEquals(-1,  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.ENHANCED_4G_MODE_ENABLED));

        assertEquals(-1,  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.VT_IMS_ENABLED));

        assertEquals(-1,  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_ENABLED));

        assertEquals(-1,  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_MODE));

        assertEquals(-1,  Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WFC_IMS_ROAMING_MODE));

        // The values should be migrated to its DB.
        assertEquals("1", mSubscriptionControllerUT.getSubscriptionProperty(
                subID,
                SubscriptionManager.ENHANCED_4G_MODE_ENABLED,
                mCallingPackage));

        assertEquals("0", mSubscriptionControllerUT.getSubscriptionProperty(
                subID,
                SubscriptionManager.VT_IMS_ENABLED,
                mCallingPackage));

        assertEquals("1", mSubscriptionControllerUT.getSubscriptionProperty(
                subID,
                SubscriptionManager.WFC_IMS_ENABLED,
                mCallingPackage));

        assertEquals("2", mSubscriptionControllerUT.getSubscriptionProperty(
                subID,
                SubscriptionManager.WFC_IMS_MODE,
                mCallingPackage));

        assertEquals("3", mSubscriptionControllerUT.getSubscriptionProperty(
                subID,
                SubscriptionManager.WFC_IMS_ROAMING_MODE,
                mCallingPackage));
    }
}
