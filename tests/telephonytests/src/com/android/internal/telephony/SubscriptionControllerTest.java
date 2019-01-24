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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SubscriptionControllerTest extends TelephonyTest {
    private static final int SINGLE_SIM = 1;
    private String mCallingPackage;
    private SubscriptionController mSubscriptionControllerUT;
    private MockContentResolver mMockContentResolver;
    private FakeTelephonyProvider mFakeTelephonyProvider;
    @Mock
    private ITelephonyRegistry.Stub mTelephonyRegisteryMock;

    private static final String MAC_ADDRESS_PREFIX = "mac_";
    private static final String DISPLAY_NAME_PREFIX = "my_phone_";

    @Before
    public void setUp() throws Exception {
        super.setUp("SubscriptionControllerTest");

        doReturn(SINGLE_SIM).when(mTelephonyManager).getSimCount();
        doReturn(SINGLE_SIM).when(mTelephonyManager).getPhoneCount();
        mMockContentResolver = (MockContentResolver) mContext.getContentResolver();
        mFakeTelephonyProvider = new FakeTelephonyProvider();
        mMockContentResolver.addProvider(SubscriptionManager.CONTENT_URI.getAuthority(),
                mFakeTelephonyProvider);
        replaceInstance(SubscriptionController.class, "sInstance", null, null);

        SubscriptionController.init(mContext, null);
        mSubscriptionControllerUT = SubscriptionController.getInstance();
        mCallingPackage = mContext.getOpPackageName();

        doReturn(1).when(mProxyController).getMaxRafSupported();
        mContextFixture.putIntArrayResource(com.android.internal.R.array.sim_colors, new int[]{5});

        mSubscriptionControllerUT.getInstance().updatePhonesAvailability(new Phone[]{mPhone});
    }

    @After
    public void tearDown() throws Exception {
        mContextFixture.addCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        /* should clear fake content provider and resolver here */
        mContext.getContentResolver().delete(SubscriptionManager.CONTENT_URI, null, null);

        /*clear sub info in mSubscriptionControllerUT since they will otherwise be persistent
         * between each test case. */
        mSubscriptionControllerUT.clearSubInfo();
        mSubscriptionControllerUT.resetStaticMembers();

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
        mSubscriptionControllerUT.setOpportunistic(isOpportunistic, subID, mCallingPackage);
        mSubscriptionControllerUT.setMetered(isMetered, subID, mCallingPackage);

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
    public void testDefaultSubIdOnSingleSimDevice() {
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        /* insert one sim */
        testInsertSim();
        // if support single sim, sms/data/voice default sub should be the same
        assertEquals(1, mSubscriptionControllerUT.getDefaultSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultVoiceSubId());
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
        mSubscriptionControllerUT.setOpportunistic(true, 2, mCallingPackage);

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

    @Test @SmallTest
    public void testInsertRemoteSim() {
        makeThisDeviceMultiSimCapable();

        // verify there are no sim's in the system.
        assertEquals(0, mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));

        addAndVerifyRemoteSimAddition(1, 0);
    }

    private void addAndVerifyRemoteSimAddition(int num, int numOfCurrentSubs) {
        // Verify the number of current subs in the system
        assertEquals(numOfCurrentSubs,
                mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));

        // if there are current subs in the system, get that info
        List<SubscriptionInfo> mSubList;
        ArrayList<String> macAddresses = new ArrayList<>();
        ArrayList<String> displayNames = new ArrayList<>();
        if (numOfCurrentSubs > 0) {
            mSubList = mSubscriptionControllerUT.getActiveSubscriptionInfoList(mCallingPackage);
            assertNotNull(mSubList);
            assertEquals(numOfCurrentSubs, mSubList.size());
            for (SubscriptionInfo info : mSubList) {
                assertNotNull(info.getIccId());
                assertNotNull(info.getDisplayName());
                macAddresses.add(info.getIccId());
                displayNames.add(info.getDisplayName().toString());
            }
        }

        // To add more subs, we need to create macAddresses + displaynames.
        for (int i = 0; i < num; i++) {
            macAddresses.add(MAC_ADDRESS_PREFIX + (numOfCurrentSubs + i));
            displayNames.add(DISPLAY_NAME_PREFIX + (numOfCurrentSubs + i));
        }

        // Add subs - one at a time and verify the contents in subscription info data structs
        for (int i = 0; i < num; i++) {
            int index = numOfCurrentSubs + i;
            mSubscriptionControllerUT.addSubInfo(macAddresses.get(index), displayNames.get(index),
                    SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                    SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);

            // make sure the subscription is added in SubscriptionController data structs
            Map<Integer, ArrayList<Integer>> slotIndexToSubsMap =
                    mSubscriptionControllerUT.getSlotIndexToSubIdsMap();
            assertNotNull(slotIndexToSubsMap);
            // Since All remote sim's go to the same slot index, there should only be one entry
            assertEquals(1, slotIndexToSubsMap.size());

            // get all the subscriptions available. should be what is just added in this method
            // PLUS the number of subs that already existed before
            int expectedNumOfSubs = numOfCurrentSubs + i + 1;
            ArrayList<Integer> subIdsList =
                    slotIndexToSubsMap.get(SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
            assertNotNull(subIdsList);
            assertEquals(expectedNumOfSubs, subIdsList.size());

            // validate slot index, sub id etc
            mSubList = mSubscriptionControllerUT.getActiveSubscriptionInfoList(mCallingPackage);
            assertNotNull(mSubList);
            assertEquals(expectedNumOfSubs, mSubList.size());

            // sort on subscription-id which will make sure the previously existing subscriptions
            // are in earlier slots in the array
            mSubList.sort(SUBSCRIPTION_INFO_COMPARATOR);

            // Verify the subscription data. Skip the verification for the existing subs.
            for (int j = numOfCurrentSubs; j < mSubList.size(); j++) {
                SubscriptionInfo info = mSubList.get(j);
                assertTrue(SubscriptionManager.isValidSubscriptionId(info.getSubscriptionId()));
                assertEquals(SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                        info.getSimSlotIndex());
                assertEquals(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM,
                        info.getSubscriptionType());
                assertEquals(macAddresses.get(j), info.getIccId());
                assertEquals(displayNames.get(j), info.getDisplayName());
            }
        }
    }

    private static final Comparator<SubscriptionInfo> SUBSCRIPTION_INFO_COMPARATOR =
            Comparator.comparingInt(o -> o.getSubscriptionId());

    @Test @SmallTest
    public void testInsertMultipleRemoteSims() {
        makeThisDeviceMultiSimCapable();

        // verify that there are no subscription info records
        assertEquals(0, mSubscriptionControllerUT.getAllSubInfoCount(mCallingPackage));
        Map<Integer, ArrayList<Integer>> slotIndexToSubsMap =
                mSubscriptionControllerUT.getSlotIndexToSubIdsMap();
        assertNotNull(slotIndexToSubsMap);
        assertTrue(slotIndexToSubsMap.isEmpty());

        // Add a few subscriptions
        addAndVerifyRemoteSimAddition(4, 0);
    }


    @Test @SmallTest
    public void testDefaultSubIdOnMultiSimDevice() {
        makeThisDeviceMultiSimCapable();

        // Initially, defaults should be -1
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                mSubscriptionControllerUT.getDefaultSmsSubId());

        // Insert one Remote-Sim.
        testInsertRemoteSim();

        // defaults should be set to this newly-inserted subscription
        assertEquals(1, mSubscriptionControllerUT.getDefaultSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultVoiceSubId());

        // Add a few subscriptions
        addAndVerifyRemoteSimAddition(4, 1);

        // defaults should be still be set to the first sub - and unchanged by the addition of
        // the above multiple sims.
        assertEquals(1, mSubscriptionControllerUT.getDefaultSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultDataSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultSmsSubId());
        assertEquals(1, mSubscriptionControllerUT.getDefaultVoiceSubId());
    }

    @Test @SmallTest
    public void testRemoveSubscription() {
        makeThisDeviceMultiSimCapable();

        /* insert some sims */
        testInsertMultipleRemoteSims();
        assertEquals(1, mSubscriptionControllerUT.getDefaultSubId());
        int[] subIdsArray = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(subIdsArray.length > 0);
        int len = subIdsArray.length;

        // remove the first sim - which also is the default sim.
        int result = mSubscriptionControllerUT.removeSubInfo(MAC_ADDRESS_PREFIX + 0,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);

        assertTrue(result > 0);
        // now check the number of subs left. should be one less than earlier
        int[] newSubIdsArray = mSubscriptionControllerUT.getActiveSubIdList();
        assertTrue(newSubIdsArray.length > 0);
        assertEquals(len - 1, newSubIdsArray.length);

        // now check that there is a new default
        assertNotSame(1, mSubscriptionControllerUT.getDefaultSubId());
    }

    private void makeThisDeviceMultiSimCapable() {
        doReturn(10).when(mTelephonyManager).getSimCount();
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
        // TODO b/123300875 slot index 1 is not expected to be valid
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
        // TODO b/123300875 slot index 1 is not expected to be valid
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
        // TODO b/123300875 slot index 1 is not expected to be valid
        mSubscriptionControllerUT.addSubInfoRecord("123", 1);   // sub 1
        mSubscriptionControllerUT.addSubInfoRecord("456", 0);   // sub 2

        // Make sure the return sub ids are sorted by slot index
        assertTrue("active sub ids = " + mSubscriptionControllerUT.getActiveSubIdList(),
                Arrays.equals(mSubscriptionControllerUT.getActiveSubIdList(), new int[]{2, 1}));
    }
}
