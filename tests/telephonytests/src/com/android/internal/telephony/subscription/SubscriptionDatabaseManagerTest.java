/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.internal.telephony.subscription;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import android.annotation.NonNull;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
import android.telephony.UiccAccessRule;
import android.telephony.ims.ImsMmTelManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SubscriptionDatabaseManagerTest extends TelephonyTest {
    private static final String FAKE_ICCID1 = "123456";
    private static final String FAKE_ICCID2 = "456789";
    private static final String FAKE_PHONE_NUMBER1 = "6502530000";
    private static final String FAKE_PHONE_NUMBER2 = "4089961010";
    private static final String FAKE_CARRIER_NAME1 = "A-Mobile";
    private static final String FAKE_CARRIER_NAME2 = "B-Mobile";
    private static final int FAKE_COLOR1 = 1;
    private static final int FAKE_COLOR2 = 3;
    private static final int FAKE_CARRIER_ID1 = 1234;
    private static final int FAKE_CARRIER_ID2 = 5678;
    private static final String FAKE_COUNTRY_CODE1 = "TW";
    private static final String FAKE_COUNTRY_CODE2 = "US";
    private static final String FAKE_MCC1 = "466";
    private static final String FAKE_MCC2 = "310";
    private static final String FAKE_MNC1 = "01";
    private static final String FAKE_MNC2 = "410";
    private static final String FAKE_EHPLMNS1 = "46602,46603";
    private static final String FAKE_EHPLMNS2 = "310411,310412";
    private static final String FAKE_HPLMNS1 = "46601,46604";
    private static final String FAKE_HPLMNS2 = "310410,310413";
    private static final byte[] FAKE_NATIVE_ACCESS_RULES1 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package1", 12345L)});
    private static final byte[] FAKE_NATIVE_ACCESS_RULES2 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package2", 45678L)});
    private static final byte[] FAKE_CARRIER_CONFIG_ACCESS_RULES1 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package1", 54321L)});
    private static final byte[] FAKE_CARRIER_CONFIG_ACCESS_RULES2 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package2", 84954L)});
    private static final String FAKE_UUID1 = "a684e31a-5998-4670-abdd-0561252c58a5";
    private static final String FAKE_UUID2 = "cf6d7a9d-e712-4b3c-a600-7a2d4961b5b9";
    private static final String FAKE_OWNER1 = "owner1";
    private static final String FAKE_OWNER2 = "owner2";
    private static final String FAKE_MOBILE_DATA_POLICY1 = "1,2";
    private static final String FAKE_MOBILE_DATA_POLICY2 = "1";
    private static final String FAKE_IMSI1 = "1234";
    private static final String FAKE_IMSI2 = "5678";
    private static final byte[] FAKE_RCS_CONFIG1 = new byte[]{0x01, 0x02, 0x03};
    private static final byte[] FAKE_RCS_CONFIG2 = new byte[]{0x04, 0x05, 0x06};
    private static final String FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1 = "carrier=123456, power=3";
    private static final String FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2 = "user=1256, enable_2g=3";
    private static final String FAKE_CONTACT1 = "John Smith, Tesla Forrest";
    private static final String FAKE_CONTACT2 = "Mary Jane, Teresa Mill";
    private static final int FAKE_TP_MESSAGE_REFERENCE1 = 123;
    private static final int FAKE_TP_MESSAGE_REFERENCE2 = 456;
    private static final int FAKE_USER_ID1 = 10;
    private static final int FAKE_USER_ID2 = 11;

    private static final SubscriptionInfoInternal FAKE_SUBSCRIPTION_INFO1 =
            new SubscriptionInfoInternal.Builder()
                    .setIccId(FAKE_ICCID1)
                    .setSimSlotIndex(0)
                    .setDisplayName(FAKE_CARRIER_NAME1)
                    .setCarrierName(FAKE_CARRIER_NAME1)
                    .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_SIM_SPN)
                    .setIconTint(FAKE_COLOR1)
                    .setNumber(FAKE_PHONE_NUMBER1)
                    .setDataRoaming(SubscriptionManager.DATA_ROAMING_ENABLE)
                    .setMcc(FAKE_MCC1)
                    .setMnc(FAKE_MNC1)
                    .setEhplmns(FAKE_EHPLMNS1)
                    .setHplmns(FAKE_HPLMNS1)
                    .setEmbedded(1)
                    .setCardString(FAKE_ICCID1)
                    .setCardId(1)
                    .setNativeAccessRules(FAKE_NATIVE_ACCESS_RULES1)
                    .setCarrierConfigAccessRules(FAKE_CARRIER_CONFIG_ACCESS_RULES1)
                    .setRemovableEmbedded(0)
                    .setEnhanced4GModeEnabled(1)
                    .setVideoTelephonyEnabled(1)
                    .setWifiCallingEnabled(1)
                    .setWifiCallingMode(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED)
                    .setWifiCallingModeForRoaming(ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED)
                    .setWifiCallingModeForRoaming(1)
                    .setOpportunistic(0)
                    .setGroupUuid(FAKE_UUID1)
                    .setCountryIso(FAKE_COUNTRY_CODE1)
                    .setCarrierId(FAKE_CARRIER_ID1)
                    .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)
                    .setGroupOwner(FAKE_OWNER1)
                    .setEnabledMobileDataPolicies(FAKE_MOBILE_DATA_POLICY1)
                    .setImsi(FAKE_IMSI1)
                    .setUiccApplicationsEnabled(1)
                    .setRcsUceEnabled(1)
                    .setCrossSimCallingEnabled(1)
                    .setRcsConfig(FAKE_RCS_CONFIG1)
                    .setAllowedNetworkTypesForReasons(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1)
                    .setDeviceToDeviceStatusSharingPreference(
                            SubscriptionManager.D2D_SHARING_ALL_CONTACTS)
                    .setVoImsOptInEnabled(1)
                    .setDeviceToDeviceStatusSharingContacts(FAKE_CONTACT1)
                    .setNrAdvancedCallingEnabled(1)
                    .setNumberFromCarrier(FAKE_PHONE_NUMBER1)
                    .setNumberFromIms(FAKE_PHONE_NUMBER1)
                    .setPortIndex(0)
                    .setUsageSetting(SubscriptionManager.USAGE_SETTING_DEFAULT)
                    .setLastUsedTPMessageReference(FAKE_TP_MESSAGE_REFERENCE1)
                    .setUserId(FAKE_USER_ID1)
                    .setGroupDisabled(false)
                    .build();

    private static final SubscriptionInfoInternal FAKE_SUBSCRIPTION_INFO2 =
            new SubscriptionInfoInternal.Builder()
                    .setIccId(FAKE_ICCID2)
                    .setSimSlotIndex(1)
                    .setDisplayName(FAKE_CARRIER_NAME2)
                    .setCarrierName(FAKE_CARRIER_NAME2)
                    .setDisplayNameSource(SubscriptionManager.NAME_SOURCE_CARRIER)
                    .setIconTint(FAKE_COLOR2)
                    .setNumber(FAKE_PHONE_NUMBER2)
                    .setDataRoaming(SubscriptionManager.DATA_ROAMING_DISABLE)
                    .setMcc(FAKE_MCC2)
                    .setMnc(FAKE_MNC2)
                    .setEhplmns(FAKE_EHPLMNS2)
                    .setHplmns(FAKE_HPLMNS2)
                    .setEmbedded(0)
                    .setCardString(FAKE_ICCID2)
                    .setCardId(2)
                    .setNativeAccessRules(FAKE_NATIVE_ACCESS_RULES2)
                    .setCarrierConfigAccessRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2)
                    .setRemovableEmbedded(1)
                    .setEnhanced4GModeEnabled(0)
                    .setVideoTelephonyEnabled(0)
                    .setWifiCallingEnabled(0)
                    .setWifiCallingMode(ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED)
                    .setWifiCallingModeForRoaming(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED)
                    .setWifiCallingModeForRoaming(0)
                    .setOpportunistic(1)
                    .setGroupUuid(FAKE_UUID2)
                    .setCountryIso(FAKE_COUNTRY_CODE2)
                    .setCarrierId(FAKE_CARRIER_ID2)
                    .setProfileClass(SubscriptionManager.PROFILE_CLASS_PROVISIONING)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)
                    .setGroupOwner(FAKE_OWNER2)
                    .setEnabledMobileDataPolicies(FAKE_MOBILE_DATA_POLICY2)
                    .setImsi(FAKE_IMSI2)
                    .setUiccApplicationsEnabled(0)
                    .setRcsUceEnabled(0)
                    .setCrossSimCallingEnabled(0)
                    .setRcsConfig(FAKE_RCS_CONFIG2)
                    .setAllowedNetworkTypesForReasons(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2)
                    .setDeviceToDeviceStatusSharingPreference(
                            SubscriptionManager.D2D_SHARING_SELECTED_CONTACTS)
                    .setVoImsOptInEnabled(0)
                    .setDeviceToDeviceStatusSharingContacts(FAKE_CONTACT2)
                    .setNrAdvancedCallingEnabled(0)
                    .setNumberFromCarrier(FAKE_PHONE_NUMBER2)
                    .setNumberFromIms(FAKE_PHONE_NUMBER2)
                    .setPortIndex(1)
                    .setUsageSetting(SubscriptionManager.USAGE_SETTING_DATA_CENTRIC)
                    .setLastUsedTPMessageReference(FAKE_TP_MESSAGE_REFERENCE2)
                    .setUserId(FAKE_USER_ID2)
                    .setGroupDisabled(false)
                    .build();

    private SubscriptionDatabaseManager mDatabaseManagerUT;

    private final SubscriptionProvider mSubscriptionProvider = new SubscriptionProvider();

    private static class SubscriptionProvider extends MockContentProvider {
        private final List<ContentValues> mDatabase = new ArrayList<>();

        private final List<String> mAllColumns;

        SubscriptionProvider() {
            mAllColumns = Telephony.SimInfo.getAllColumns();
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            logd("SubscriptionProvider: query. uri=" + uri);
            if (!Telephony.SimInfo.CONTENT_URI.equals(uri)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }
            if (projection != null || selection != null || selectionArgs != null) {
                throw new UnsupportedOperationException("Only support full database query. "
                        + "projection=" + Arrays.toString(projection) + ", selection=" + selection
                        + ", selectionArgs=" + Arrays.toString(selectionArgs));
            }

            MatrixCursor mc = new MatrixCursor(mAllColumns.toArray(new String[0]));

            // Only support full database query
            for (int row = 0; row < mDatabase.size(); row++) {
                List<Object> values = new ArrayList<>();
                for (String column : mAllColumns) {
                    values.add(mDatabase.get(row).get(column));
                }
                mc.addRow(values);
            }

            return mc;
        }

        @Override
        public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
            if (!uri.isPathPrefixMatch(Telephony.SimInfo.CONTENT_URI)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }

            int subId = Integer.parseInt(uri.getLastPathSegment());
            assertThat(mDatabase.size()).isAtLeast(subId);

            ContentValues existingValues = mDatabase.get(subId - 1);
            logd("update: subId=" + subId + ", contentValues=" + values);
            for (Map.Entry<String, Object> entry : values.valueSet()) {
                String column = entry.getKey();
                Object value = entry.getValue();
                if (!mAllColumns.contains(column)) {
                    throw new IllegalArgumentException("Update with unknown column " + column);
                }
                existingValues.putObject(column, value);
            }
            return 1;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException("delete is not supported uri=" + uri);
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            logd("SubscriptionProvider: insert. uri=" + uri + ", values=" + values);
            if (!Telephony.SimInfo.CONTENT_URI.equals(uri)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }

            for (String column : values.keySet()) {
                if (!mAllColumns.contains(column)) {
                    throw new IllegalArgumentException("Insert with unknown column " + column);
                }
            }
            int subId = mDatabase.size() + 1;
            values.put(Telephony.SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID, subId);
            mDatabase.add(values);
            return ContentUris.withAppendedId(Telephony.SimInfo.CONTENT_URI, subId);
        }
    }

    private void loadFromDatabase() throws Exception {
        Method method = SubscriptionDatabaseManager.class.getDeclaredMethod("loadFromDatabase");
        method.setAccessible(true);
        method.invoke(mDatabaseManagerUT);
        processAllMessages();
    }

    @Before
    public void setUp() throws Exception {
        logd("SubscriptionDatabaseManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);
        doReturn(1).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID1));
        doReturn(2).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID2));
        mDatabaseManagerUT = new SubscriptionDatabaseManager(mContext, Looper.myLooper());
        logd("SubscriptionDatabaseManagerTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Verify the subscription from the cache and database is same as the provided one.
     *
     * @param subInfo The subscription to compare.
     */
    private void verifySubscription(@NonNull SubscriptionInfoInternal subInfo) throws Exception {
        int subId = subInfo.getSubscriptionId();
        // Verify the cache value is same as the inserted one.
        assertWithMessage("Subscription info cache value is different.")
                .that(mDatabaseManagerUT.getSubscriptionInfoInternal(subId)).isEqualTo(subInfo);

        // Load subscription info from the database.
        loadFromDatabase();
        // Verify the database value is same as the inserted one.
        assertWithMessage("Subscription info database value is different.")
                .that(mDatabaseManagerUT.getSubscriptionInfoInternal(subId)).isEqualTo(subInfo);
    }

    /**
     * Insert a subscription info into the database and verify it's in the cache and database.
     *
     * @param subInfo The subscription info to insert.
     * @return The inserted subscription info.
     */
    private SubscriptionInfoInternal insertSubscriptionAndVerify(
            @NonNull SubscriptionInfoInternal subInfo) throws Exception {
        assertThat(subInfo.getSubscriptionId()).isEqualTo(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        int subId = mDatabaseManagerUT.insertSubscriptionInfo(subInfo);
        assertThat(SubscriptionManager.isValidSubscriptionId(subId)).isTrue();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setId(subId).build();
        verifySubscription(subInfo);
        return subInfo;
    }

    @Test
    public void testGetAllColumns() throws Exception {
        Field[] declaredFields = Telephony.SimInfo.class.getDeclaredFields();
        List<String> columnNames = new ArrayList<>();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("COLUMN_")) {
                columnNames.add((String) field.get(null));
            }
        }
        // When you add a new column in Telephony.SimInfo, did you remember to modify
        // Telephony.SimInfo.getAllColumns() as well?
        assertThat(Telephony.SimInfo.getAllColumns()).containsExactlyElementsIn(columnNames);
    }

    @Test
    public void testInsertSubscription() throws Exception {
        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1).getSubscriptionId())
                .isEqualTo(1);
        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO2).getSubscriptionId())
                .isEqualTo(2);
    }

    @Test
    public void testUpdateSubscription() throws Exception {
        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1).getSubscriptionId())
                .isEqualTo(1);
        SubscriptionInfoInternal subInfo = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2)
                .setId(1)
                .build();
        mDatabaseManagerUT.updateSubscription(subInfo);
        processAllMessages();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateIccId() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setIccId(subInfo.getSubscriptionId(), FAKE_ICCID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setIccId(FAKE_ICCID2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateSimSlotIndex() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSimSlotIndex(subInfo.getSubscriptionId(),
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setSimSlotIndex(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateDisplayName() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDisplayName(subInfo.getSubscriptionId(), FAKE_CARRIER_NAME2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setDisplayName(
                FAKE_CARRIER_NAME2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCarrierName() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierName(subInfo.getSubscriptionId(), FAKE_CARRIER_NAME2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setCarrierName(
                FAKE_CARRIER_NAME2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateDisplayNameSource() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierName(subInfo.getSubscriptionId(), FAKE_CARRIER_NAME2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setCarrierName(
                FAKE_CARRIER_NAME2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateIconTint() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setIconTint(subInfo.getSubscriptionId(), FAKE_COLOR2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setIconTint(FAKE_COLOR2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateNumber() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumber(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumber(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateDataRoaming() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDataRoaming(subInfo.getSubscriptionId(),
                SubscriptionManager.DATA_ROAMING_DISABLE);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setDataRoaming(SubscriptionManager.DATA_ROAMING_DISABLE).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateMcc() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setMcc(subInfo.getSubscriptionId(), FAKE_MCC2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setMcc(FAKE_MCC2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateMnc() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setMnc(subInfo.getSubscriptionId(), FAKE_MNC2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setMnc(FAKE_MNC2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateEhplmns() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEhplmns(subInfo.getSubscriptionId(), FAKE_EHPLMNS2.split(","));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEhplmns(FAKE_EHPLMNS2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateHplmns() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setHplmns(subInfo.getSubscriptionId(), FAKE_HPLMNS2.split(","));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setHplmns(FAKE_HPLMNS2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateEmbedded() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEmbedded(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEmbedded(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCardString() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCardString(subInfo.getSubscriptionId(), FAKE_ICCID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCardString(FAKE_ICCID2)
                .setCardId(2)
                .build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateNativeAccessRules() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNativeAccessRules(subInfo.getSubscriptionId(),
                UiccAccessRule.decodeRules(FAKE_NATIVE_ACCESS_RULES2));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNativeAccessRules(FAKE_NATIVE_ACCESS_RULES2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCarrierConfigAccessRules() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierConfigAccessRules(subInfo.getSubscriptionId(),
                UiccAccessRule.decodeRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCarrierConfigAccessRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateRemovableEmbedded() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRemovableEmbedded(subInfo.getSubscriptionId(), true);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setRemovableEmbedded(1).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateEnhanced4GModeEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEnhanced4GModeEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEnhanced4GModeEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateVideoTelephonyEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setVideoTelephonyEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setVideoTelephonyEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateWifiCallingEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setWifiCallingEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateWifiCallingMode() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingMode(subInfo.getSubscriptionId(),
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingMode(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateWifiCallingModeForRoaming() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingModeForRoaming(subInfo.getSubscriptionId(),
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingModeForRoaming(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateWifiCallingEnabledForRoaming() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabledForRoaming(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingEnabledForRoaming(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateOpportunistic() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setOpportunistic(subInfo.getSubscriptionId(), true);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setOpportunistic(1).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateGroupUuid() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setGroupUuid(subInfo.getSubscriptionId(), FAKE_UUID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setGroupUuid(FAKE_UUID2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCountryIso() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCountryIso(subInfo.getSubscriptionId(), FAKE_COUNTRY_CODE2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCountryIso(FAKE_COUNTRY_CODE2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCarrierId() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierId(subInfo.getSubscriptionId(), FAKE_CARRIER_ID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCarrierId(FAKE_CARRIER_ID2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateProfileClass() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setProfileClass(subInfo.getSubscriptionId(),
                SubscriptionManager.PROFILE_CLASS_TESTING);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_TESTING).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateSubscriptionType() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSubscriptionType(subInfo.getSubscriptionId(),
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setType(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateGroupOwner() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setGroupOwner(subInfo.getSubscriptionId(), FAKE_OWNER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setGroupOwner(FAKE_OWNER2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateEnabledMobileDataPolicies() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEnabledMobileDataPolicies(subInfo.getSubscriptionId(),
                FAKE_MOBILE_DATA_POLICY2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setEnabledMobileDataPolicies(FAKE_MOBILE_DATA_POLICY2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateImsi() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setImsi(subInfo.getSubscriptionId(), FAKE_IMSI2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setImsi(FAKE_IMSI2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateUiccApplicationsEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUiccApplicationsEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUiccApplicationsEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateRcsUceEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRcsUceEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setRcsUceEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateCrossSimCallingEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCrossSimCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCrossSimCallingEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateRcsConfig() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRcsConfig(subInfo.getSubscriptionId(), FAKE_RCS_CONFIG2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setRcsConfig(FAKE_RCS_CONFIG2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateAllowedNetworkTypesForReasons() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setAllowedNetworkTypesForReasons(subInfo.getSubscriptionId(),
                FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setAllowedNetworkTypesForReasons(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateDeviceToDeviceStatusSharingPreference() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDeviceToDeviceStatusSharingPreference(subInfo.getSubscriptionId(),
                SubscriptionManager.D2D_SHARING_DISABLED);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setDeviceToDeviceStatusSharingPreference(
                        SubscriptionManager.D2D_SHARING_DISABLED).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateNrAdvancedCallingEnabled() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNrAdvancedCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNrAdvancedCallingEnabled(0).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateNumberFromCarrier() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumberFromCarrier(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumberFromCarrier(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateNumberFromIms() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumberFromIms(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumberFromIms(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdatePortIndex() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setPortIndex(subInfo.getSubscriptionId(), 1);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setPortIndex(1).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateUsageSetting() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUsageSetting(subInfo.getSubscriptionId(),
                SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUsageSetting(SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateLastUsedTPMessageReference() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setLastUsedTPMessageReference(subInfo.getSubscriptionId(),
                FAKE_TP_MESSAGE_REFERENCE2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setLastUsedTPMessageReference(FAKE_TP_MESSAGE_REFERENCE2).build();
        verifySubscription(subInfo);
    }

    @Test
    public void testUpdateUserId() throws Exception {
        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUserId(subInfo.getSubscriptionId(), FAKE_USER_ID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUserId(FAKE_USER_ID2).build();
        verifySubscription(subInfo);
    }
}
