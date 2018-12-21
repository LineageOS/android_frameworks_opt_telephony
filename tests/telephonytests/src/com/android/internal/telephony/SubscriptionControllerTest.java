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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.content.ContentValues;
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
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

public class SubscriptionControllerTest extends TelephonyTest {
    private static final int SINGLE_SIM = 1;
    private String mCallingPackage;
    private SubscriptionController mSubscriptionControllerUT;
    private MockContentResolver mMockContentResolver;
    private FakeTelephonyProvider mFakeTelephonyProvider;
    @Mock
    private ITelephonyRegistry.Stub mTelephonyRegisteryMock;

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
        mFakeTelephonyProvider = new FakeTelephonyProvider();
        mMockContentResolver.addProvider(SubscriptionManager.CONTENT_URI.getAuthority(),
                mFakeTelephonyProvider);

    }

    @After
    public void tearDown() throws Exception {
        mContextFixture.addCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        /* should clear fake content provider and resolver here */
        mContext.getContentResolver().delete(SubscriptionManager.CONTENT_URI, null, null);

        /*clear sub info in mSubscriptionControllerUT since they will otherwise be persistent
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
        //verify there is no sim inserted in the SubscriptionManager
        assertEquals(0, mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));

        int slotID = 0;
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
        boolean isMetered = false;

        testInsertSim();
        /* Get SUB ID */
        int[] subIds = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIds != null && subIds.length != 0);
        int subID = subIds[0];

        /* Getting, there is no direct getter function for each fields of property */
        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(subID, mCallingPackage);
        //isMetered should initialize as true
        assertTrue(subInfo.isMetered());

        /* Setting */
        mSubscriptionControllerUT.setDisplayName(disName, subID);
        mSubscriptionControllerUT.setDataRoaming(dataRoaming, subID);
        mSubscriptionControllerUT.setDisplayNumber(disNum, subID);
        mSubscriptionControllerUT.setIconTint(iconTint, subID);
        mSubscriptionControllerUT.setOpportunistic(isOpportunistic, subID);
        mSubscriptionControllerUT.setMetered(isMetered, subID);

        subInfo = mSubscriptionControllerUT
            .getActiveSubscriptionInfo(subID, mCallingPackage);

        assertNotNull(subInfo);
        assertEquals(dataRoaming, subInfo.getDataRoaming());
        assertEquals(disName, subInfo.getDisplayName());
        assertEquals(iconTint, subInfo.getIconTint());
        assertEquals(disNum, subInfo.getNumber());
        assertEquals(isOpportunistic, subInfo.isOpportunistic());
        assertEquals(isMetered, subInfo.isMetered());

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

    @Test @SmallTest
    public void testSetGetCarrierId() {
        testInsertSim();
        int carrierId = 1234;
        mSubscriptionControllerUT.setCarrierId(carrierId, 1);

        SubscriptionInfo subInfo = mSubscriptionControllerUT
                .getActiveSubscriptionInfo(1, mCallingPackage);
        assertNotNull(subInfo);
        assertEquals(carrierId, subInfo.getCarrierId());

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


    @Test
    @SmallTest
    public void testOpptSubInfoListChanged() throws Exception {
        registerMockTelephonyRegistry();
        verify(mTelephonyRegisteryMock, times(0))
                .notifyOpportunisticSubscriptionInfoChanged();

        testInsertSim();
        mSubscriptionControllerUT.addSubInfoRecord("test2", 0);

        // Neither sub1 or sub2 are opportunistic. So getOpportunisticSubscriptions
        // should return empty list and no callback triggered.
        List<SubscriptionInfo> opptSubList = mSubscriptionControllerUT
                .getOpportunisticSubscriptions(mCallingPackage);

        assertTrue(opptSubList.isEmpty());
        verify(mTelephonyRegisteryMock, times(0))
                .notifyOpportunisticSubscriptionInfoChanged();

        // Setting sub2 as opportunistic should trigger callback.
        mSubscriptionControllerUT.setOpportunistic(true, 2);

        verify(mTelephonyRegisteryMock, times(1))
                .notifyOpportunisticSubscriptionInfoChanged();
        opptSubList = mSubscriptionControllerUT
                .getOpportunisticSubscriptions(mCallingPackage);
        assertEquals(1, opptSubList.size());
        assertEquals("test2", opptSubList.get(0).getIccId());

        // Changing non-opportunistic sub1 shouldn't trigger callback.
        mSubscriptionControllerUT.setDisplayName("DisplayName", 1);
        verify(mTelephonyRegisteryMock, times(1))
                .notifyOpportunisticSubscriptionInfoChanged();

        mSubscriptionControllerUT.setDisplayName("DisplayName", 2);
        verify(mTelephonyRegisteryMock, times(2))
                .notifyOpportunisticSubscriptionInfoChanged();
    }

    @Test
    @SmallTest
    public void testSetSubscriptionGroupWithModifyPermission() throws Exception {
        testInsertSim();
        mSubscriptionControllerUT.addSubInfoRecord("test2", 0);
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);

        int[] subIdList = new int[] {1, 2};
        try {
            mSubscriptionControllerUT.setSubscriptionGroup(
                    subIdList, mContext.getOpPackageName());
            fail("setSubscriptionGroup should fail with no permission.");
        } catch (SecurityException e) {
            // Expected result.
        }

        // With modify permission it should succeed.
        mContextFixture.addCallingOrSelfPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        String groupId = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, groupId);

        // Calling it again should generate a new group ID.
        String newGroupId = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, newGroupId);
        assertNotEquals(groupId, newGroupId);
    }

    @Test
    @SmallTest
    public void testSetSubscriptionGroupWithCarrierPrivilegePermission() throws Exception {
        testInsertSim();
        // Adding a second profile and mark as embedded.
        mSubscriptionControllerUT.addSubInfoRecord("test2", 1);
        ContentValues values = new ContentValues();
        values.put(SubscriptionManager.IS_EMBEDDED, 1);
        mFakeTelephonyProvider.update(SubscriptionManager.CONTENT_URI, values,
                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 2, null);
        mSubscriptionControllerUT.refreshCachedActiveSubscriptionInfoList();

        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);

        int[] subIdList = new int[] {1, 2};
        // It should fail since it has no permission.
        try {
            mSubscriptionControllerUT.setSubscriptionGroup(
                    subIdList, mContext.getOpPackageName());
            fail("setSubscriptionGroup should fail with no permission.");
        } catch (SecurityException e) {
            // Expected result.
        }

        doReturn(true).when(mTelephonyManager).hasCarrierPrivileges(1);
        try {
            mSubscriptionControllerUT.setSubscriptionGroup(
                    subIdList, mContext.getOpPackageName());
            fail("setSubscriptionGroup should fail with no permission on sub 2.");
        } catch (SecurityException e) {
            // Expected result.
        }

        doReturn(true).when(mTelephonyManager).hasCarrierPrivileges(2);
        String groupId = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, groupId);

        List<SubscriptionInfo> subInfoList = mSubscriptionControllerUT
                .getActiveSubscriptionInfoList(mContext.getOpPackageName());

        // Put sub3 into slot 1 to make sub2 inactive.
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionControllerUT.addSubInfoRecord("test3", 1);
        mContextFixture.removeCallingOrSelfPermission(
                android.Manifest.permission.MODIFY_PHONE_STATE);
        // As sub2 is inactive, it will checks carrier privilege against access rules in the db.
        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                eq(subInfoList.get(1)), anyString());

        String newGroupId = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, newGroupId);
        assertNotEquals(groupId, newGroupId);
    }

    @Test
    @SmallTest
    public void testDisabledSubscriptionGroup() throws Exception {
        registerMockTelephonyRegistry();

        testInsertSim();
        // Adding a second profile and mark as embedded.
        mSubscriptionControllerUT.addSubInfoRecord("test2", 0);

        ContentValues values = new ContentValues();
        values.put(SubscriptionManager.IS_EMBEDDED, 1);
        values.put(SubscriptionManager.IS_OPPORTUNISTIC, 1);
        mFakeTelephonyProvider.update(SubscriptionManager.CONTENT_URI, values,
                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 2, null);
        mSubscriptionControllerUT.refreshCachedActiveSubscriptionInfoList();

        verify(mTelephonyRegisteryMock, times(1))
                .notifyOpportunisticSubscriptionInfoChanged();

        // Set sub 1 and 2 into same group.
        int[] subIdList = new int[] {1, 2};
        String groupId = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, groupId);

        verify(mTelephonyRegisteryMock, times(2))
                .notifyOpportunisticSubscriptionInfoChanged();
        List<SubscriptionInfo> opptSubList = mSubscriptionControllerUT
                .getOpportunisticSubscriptions(mCallingPackage);
        assertEquals(1, opptSubList.size());
        assertEquals(2, opptSubList.get(0).getSubscriptionId());
        assertEquals(false, opptSubList.get(0).isGroupDisabled());

        // Unplug SIM 1. This should trigger subscription controller disabling sub 2.
        values = new ContentValues();
        values.put(SubscriptionManager.SIM_SLOT_INDEX, -1);
        mFakeTelephonyProvider.update(SubscriptionManager.CONTENT_URI, values,
                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 1, null);
        mSubscriptionControllerUT.refreshCachedActiveSubscriptionInfoList();

        verify(mTelephonyRegisteryMock, times(3))
                .notifyOpportunisticSubscriptionInfoChanged();
        opptSubList = mSubscriptionControllerUT.getOpportunisticSubscriptions(mCallingPackage);
        assertEquals(1, opptSubList.size());
        assertEquals(2, opptSubList.get(0).getSubscriptionId());
        assertEquals(true, opptSubList.get(0).isGroupDisabled());
    }

    @Test
    @SmallTest
    public void testSetSubscriptionGroup() throws Exception {
        testInsertSim();
        // Adding a second profile and mark as embedded.
        mSubscriptionControllerUT.addSubInfoRecord("test2", 0);
        ContentValues values = new ContentValues();
        values.put(SubscriptionManager.IS_EMBEDDED, 1);
        mFakeTelephonyProvider.update(SubscriptionManager.CONTENT_URI, values,
                SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID + "=" + 2, null);
        mSubscriptionControllerUT.refreshCachedActiveSubscriptionInfoList();

        int[] subIdList = new int[] {1, 2};
        String groupUuid = mSubscriptionControllerUT.setSubscriptionGroup(
                subIdList, mContext.getOpPackageName());
        assertNotEquals(null, groupUuid);

        // Sub 1 and sub 2 should be in same group.
        List<SubscriptionInfo> infoList = mSubscriptionControllerUT
                .getSubscriptionsInGroup(1, mContext.getOpPackageName());
        assertNotEquals(null, infoList);
        assertEquals(2, infoList.size());
        assertEquals(1, infoList.get(0).getSubscriptionId());
        assertEquals(2, infoList.get(1).getSubscriptionId());

        // Remove group of sub 1.
        subIdList = new int[] {1};
        boolean result = mSubscriptionControllerUT.removeSubscriptionsFromGroup(
                subIdList, mContext.getOpPackageName());
        assertEquals(true, result);
        infoList = mSubscriptionControllerUT
                .getSubscriptionsInGroup(2, mContext.getOpPackageName());
        assertEquals(1, infoList.size());
        assertEquals(2, infoList.get(0).getSubscriptionId());
    }

    private void registerMockTelephonyRegistry() {
        mServiceManagerMockedServices.put("telephony.registry", mTelephonyRegisteryMock);
        doReturn(mTelephonyRegisteryMock).when(mTelephonyRegisteryMock)
                .queryLocalInterface(anyString());
    }

    @Test
    @SmallTest
    public void testGetActiveSubIdList() throws Exception {
        mSubscriptionControllerUT.addSubInfoRecord("123", 1);   // sub 1
        mSubscriptionControllerUT.addSubInfoRecord("456", 0);   // sub 2

        // Make sure the return sub ids are sorted by slot index
        assertTrue("active sub ids = " + mSubscriptionControllerUT.getActiveSubIdList(),
                Arrays.equals(mSubscriptionControllerUT.getActiveSubIdList(), new int[]{2, 1}));
    }
}
