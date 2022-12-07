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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import com.android.internal.telephony.subscription.SubscriptionDatabaseManager.SubscriptionDatabaseManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

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
    static final String FAKE_ICCID1 = "123456";
    static final String FAKE_ICCID2 = "456789";
    static final String FAKE_PHONE_NUMBER1 = "6502530000";
    static final String FAKE_PHONE_NUMBER2 = "4089961010";
    static final String FAKE_CARRIER_NAME1 = "A-Mobile";
    static final String FAKE_CARRIER_NAME2 = "B-Mobile";
    static final int FAKE_COLOR1 = 1;
    static final int FAKE_COLOR2 = 3;
    static final int FAKE_CARRIER_ID1 = 1234;
    static final int FAKE_CARRIER_ID2 = 5678;
    static final String FAKE_COUNTRY_CODE1 = "TW";
    static final String FAKE_COUNTRY_CODE2 = "US";
    static final String FAKE_MCC1 = "466";
    static final String FAKE_MCC2 = "310";
    static final String FAKE_MNC1 = "01";
    static final String FAKE_MNC2 = "410";
    static final String FAKE_EHPLMNS1 = "46602,46603";
    static final String FAKE_EHPLMNS2 = "310411,310412";
    static final String FAKE_HPLMNS1 = "46601,46604";
    static final String FAKE_HPLMNS2 = "310410,310413";
    static final byte[] FAKE_NATIVE_ACCESS_RULES1 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package1", 12345L)});
    static final byte[] FAKE_NATIVE_ACCESS_RULES2 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package2", 45678L)});
    static final byte[] FAKE_CARRIER_CONFIG_ACCESS_RULES1 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package1", 54321L)});
    static final byte[] FAKE_CARRIER_CONFIG_ACCESS_RULES2 = UiccAccessRule.encodeRules(
            new UiccAccessRule[]{new UiccAccessRule(new byte[] {}, "package2", 84954L)});
    static final String FAKE_UUID1 = "a684e31a-5998-4670-abdd-0561252c58a5";
    static final String FAKE_UUID2 = "cf6d7a9d-e712-4b3c-a600-7a2d4961b5b9";
    static final String FAKE_OWNER1 = "owner1";
    static final String FAKE_OWNER2 = "owner2";
    static final String FAKE_MOBILE_DATA_POLICY1 = "1,2";
    static final String FAKE_MOBILE_DATA_POLICY2 = "1";
    static final String FAKE_IMSI1 = "1234";
    static final String FAKE_IMSI2 = "5678";
    static final byte[] FAKE_RCS_CONFIG1 = new byte[]{0x01, 0x02, 0x03};
    static final byte[] FAKE_RCS_CONFIG2 = new byte[]{0x04, 0x05, 0x06};
    static final String FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1 = "carrier=123456, power=3";
    static final String FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2 = "user=1256, enable_2g=3";
    static final String FAKE_CONTACT1 = "John Smith, Tesla Forrest";
    static final String FAKE_CONTACT2 = "Mary Jane, Teresa Mill";
    static final int FAKE_TP_MESSAGE_REFERENCE1 = 123;
    static final int FAKE_TP_MESSAGE_REFERENCE2 = 456;
    static final int FAKE_USER_ID1 = 10;
    static final int FAKE_USER_ID2 = 11;

    static final SubscriptionInfoInternal FAKE_SUBSCRIPTION_INFO1 =
            new SubscriptionInfoInternal.Builder()
                    .setId(1)
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
                    .setWifiCallingEnabledForRoaming(1)
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

    static final SubscriptionInfoInternal FAKE_SUBSCRIPTION_INFO2 =
            new SubscriptionInfoInternal.Builder()
                    .setId(2)
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
                    .setWifiCallingEnabledForRoaming(0)
                    .setOpportunistic(1)
                    .setGroupUuid(FAKE_UUID2)
                    .setCountryIso(FAKE_COUNTRY_CODE2)
                    .setCarrierId(FAKE_CARRIER_ID2)
                    .setProfileClass(SubscriptionManager.PROFILE_CLASS_PROVISIONING)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)
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

    //mock
    private SubscriptionDatabaseManagerCallback mSubscriptionDatabaseManagerCallback;

    static class SubscriptionProvider extends MockContentProvider {
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
        mSubscriptionDatabaseManagerCallback =
                Mockito.mock(SubscriptionDatabaseManagerCallback.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mSubscriptionDatabaseManagerCallback).invokeFromExecutor(any(Runnable.class));

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);
        doReturn(1).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID1));
        doReturn(2).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID2));
        mDatabaseManagerUT = new SubscriptionDatabaseManager(mContext, Looper.myLooper(),
                mSubscriptionDatabaseManagerCallback);
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
        int subId = mDatabaseManagerUT.insertSubscriptionInfo(
                new SubscriptionInfoInternal.Builder(subInfo)
                        .setId(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                        .build());
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
        processAllMessages();
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);

        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO2).getSubscriptionId())
                .isEqualTo(2);
        processAllMessages();
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(2));
    }

    @Test
    public void testUpdateSubscription() throws Exception {
        SubscriptionInfoInternal subInfo = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2)
                .setId(1)
                .build();

        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.updateSubscription(subInfo));

        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1).getSubscriptionId())
                .isEqualTo(1);
        mDatabaseManagerUT.updateSubscription(subInfo);
        processAllMessages();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);

        // Same sub info again. Should not trigger callback
        mDatabaseManagerUT.updateSubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, never()).onSubscriptionChanged(anyInt());
    }

    @Test
    public void testUpdateSubscriptionSync() throws Exception {
        mContextFixture.putBooleanResource(com.android.internal.R.bool
                .config_subscription_database_async_update, false);
        mDatabaseManagerUT = new SubscriptionDatabaseManager(mContext, Looper.myLooper(),
                mSubscriptionDatabaseManagerCallback);

        assertThat(insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1).getSubscriptionId())
                .isEqualTo(1);
        SubscriptionInfoInternal subInfo = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2)
                .setId(1)
                .build();
        mDatabaseManagerUT.updateSubscription(subInfo);

        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);

        // Same sub info again. Should not trigger callback
        mDatabaseManagerUT.updateSubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, never()).onSubscriptionChanged(anyInt());
    }

    @Test
    public void testUpdateIccId() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setIccId(1, FAKE_ICCID2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setIccId(subInfo.getSubscriptionId(), FAKE_ICCID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setIccId(FAKE_ICCID2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateSimSlotIndex() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setSimSlotIndex(1,
                        SubscriptionManager.INVALID_SIM_SLOT_INDEX));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSimSlotIndex(subInfo.getSubscriptionId(),
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setSimSlotIndex(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateDisplayName() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setDisplayName(1, FAKE_CARRIER_NAME2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDisplayName(subInfo.getSubscriptionId(), FAKE_CARRIER_NAME2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setDisplayName(
                FAKE_CARRIER_NAME2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCarrierName() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCarrierName(1, FAKE_CARRIER_NAME2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierName(subInfo.getSubscriptionId(), FAKE_CARRIER_NAME2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setCarrierName(
                FAKE_CARRIER_NAME2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateDisplayNameSource() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setDisplayNameSource(1,
                        SubscriptionManager.NAME_SOURCE_USER_INPUT));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDisplayNameSource(subInfo.getSubscriptionId(),
                SubscriptionManager.NAME_SOURCE_USER_INPUT);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setDisplayNameSource(
                SubscriptionManager.NAME_SOURCE_USER_INPUT).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateIconTint() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setIconTint(1, FAKE_COLOR2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setIconTint(subInfo.getSubscriptionId(), FAKE_COLOR2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setIconTint(FAKE_COLOR2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateNumber() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNumber(1, FAKE_PHONE_NUMBER2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumber(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumber(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateDataRoaming() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setDataRoaming(1,
                        SubscriptionManager.DATA_ROAMING_DISABLE));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDataRoaming(subInfo.getSubscriptionId(),
                SubscriptionManager.DATA_ROAMING_DISABLE);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setDataRoaming(SubscriptionManager.DATA_ROAMING_DISABLE).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateMcc() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setMcc(1, FAKE_MCC2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setMcc(subInfo.getSubscriptionId(), FAKE_MCC2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setMcc(FAKE_MCC2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateMnc() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setMnc(1, FAKE_MNC2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setMnc(subInfo.getSubscriptionId(), FAKE_MNC2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setMnc(FAKE_MNC2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateEhplmns() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEhplmns(1, FAKE_EHPLMNS2.split(",")));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEhplmns(subInfo.getSubscriptionId(), FAKE_EHPLMNS2.split(","));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEhplmns(FAKE_EHPLMNS2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateHplmns() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setHplmns(1, FAKE_HPLMNS2.split(",")));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setHplmns(subInfo.getSubscriptionId(), FAKE_HPLMNS2.split(","));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setHplmns(FAKE_HPLMNS2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateEmbedded() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEmbedded(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEmbedded(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEmbedded(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCardString() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCardString(1, FAKE_ICCID2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCardString(subInfo.getSubscriptionId(), FAKE_ICCID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCardString(FAKE_ICCID2)
                .setCardId(2)
                .build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateNativeAccessRules() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNativeAccessRules(1,
                        UiccAccessRule.decodeRules(FAKE_NATIVE_ACCESS_RULES2)));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNativeAccessRules(subInfo.getSubscriptionId(),
                UiccAccessRule.decodeRules(FAKE_NATIVE_ACCESS_RULES2));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNativeAccessRules(FAKE_NATIVE_ACCESS_RULES2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCarrierConfigAccessRules() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCarrierConfigAccessRules(1,
                        UiccAccessRule.decodeRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2)));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierConfigAccessRules(subInfo.getSubscriptionId(),
                UiccAccessRule.decodeRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2));
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCarrierConfigAccessRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateRemovableEmbedded() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setRemovableEmbedded(1, true));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRemovableEmbedded(subInfo.getSubscriptionId(), true);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setRemovableEmbedded(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateEnhanced4GModeEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEnhanced4GModeEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEnhanced4GModeEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEnhanced4GModeEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateVideoTelephonyEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setVideoTelephonyEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setVideoTelephonyEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setVideoTelephonyEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateWifiCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setWifiCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateWifiCallingMode() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingMode(
                        1, ImsMmTelManager.WIFI_MODE_WIFI_ONLY));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingMode(subInfo.getSubscriptionId(),
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingMode(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateWifiCallingModeForRoaming() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingModeForRoaming(
                        1, ImsMmTelManager.WIFI_MODE_WIFI_ONLY));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingModeForRoaming(subInfo.getSubscriptionId(),
                ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingModeForRoaming(ImsMmTelManager.WIFI_MODE_WIFI_ONLY).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateWifiCallingEnabledForRoaming() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingEnabledForRoaming(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabledForRoaming(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingEnabledForRoaming(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateOpportunistic() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setOpportunistic(1, true));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setOpportunistic(subInfo.getSubscriptionId(), true);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setOpportunistic(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateGroupUuid() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setGroupUuid(1, FAKE_UUID2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setGroupUuid(subInfo.getSubscriptionId(), FAKE_UUID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setGroupUuid(FAKE_UUID2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCountryIso() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCountryIso(1, FAKE_COUNTRY_CODE2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCountryIso(subInfo.getSubscriptionId(), FAKE_COUNTRY_CODE2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCountryIso(FAKE_COUNTRY_CODE2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCarrierId() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCarrierId(1, FAKE_CARRIER_ID2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierId(subInfo.getSubscriptionId(), FAKE_CARRIER_ID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCarrierId(FAKE_CARRIER_ID2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateProfileClass() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setProfileClass(
                        1, SubscriptionManager.PROFILE_CLASS_TESTING));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setProfileClass(subInfo.getSubscriptionId(),
                SubscriptionManager.PROFILE_CLASS_TESTING);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_TESTING).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateSubscriptionType() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setSubscriptionType(
                        1, SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSubscriptionType(subInfo.getSubscriptionId(),
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setType(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateGroupOwner() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setGroupOwner(1, FAKE_OWNER2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setGroupOwner(subInfo.getSubscriptionId(), FAKE_OWNER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setGroupOwner(FAKE_OWNER2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateEnabledMobileDataPolicies() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEnabledMobileDataPolicies(1, FAKE_MOBILE_DATA_POLICY2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEnabledMobileDataPolicies(subInfo.getSubscriptionId(),
                FAKE_MOBILE_DATA_POLICY2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setEnabledMobileDataPolicies(FAKE_MOBILE_DATA_POLICY2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateImsi() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setImsi(1, FAKE_IMSI2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setImsi(subInfo.getSubscriptionId(), FAKE_IMSI2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setImsi(FAKE_IMSI2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateUiccApplicationsEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setUiccApplicationsEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUiccApplicationsEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUiccApplicationsEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateRcsUceEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setRcsUceEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRcsUceEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setRcsUceEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateCrossSimCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCrossSimCallingEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCrossSimCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCrossSimCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateRcsConfig() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setRcsConfig(1, FAKE_RCS_CONFIG2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRcsConfig(subInfo.getSubscriptionId(), FAKE_RCS_CONFIG2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setRcsConfig(FAKE_RCS_CONFIG2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateAllowedNetworkTypesForReasons() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setAllowedNetworkTypesForReasons(
                        1, FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setAllowedNetworkTypesForReasons(subInfo.getSubscriptionId(),
                FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setAllowedNetworkTypesForReasons(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateDeviceToDeviceStatusSharingPreference() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setDeviceToDeviceStatusSharingPreference(
                        1, SubscriptionManager.D2D_SHARING_DISABLED));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setDeviceToDeviceStatusSharingPreference(subInfo.getSubscriptionId(),
                SubscriptionManager.D2D_SHARING_DISABLED);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setDeviceToDeviceStatusSharingPreference(
                        SubscriptionManager.D2D_SHARING_DISABLED).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateNrAdvancedCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNrAdvancedCallingEnabled(1, false));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNrAdvancedCallingEnabled(subInfo.getSubscriptionId(), false);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNrAdvancedCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateNumberFromCarrier() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNumberFromCarrier(1, FAKE_PHONE_NUMBER2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumberFromCarrier(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumberFromCarrier(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateNumberFromIms() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNumberFromIms(1, FAKE_PHONE_NUMBER2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNumberFromIms(subInfo.getSubscriptionId(), FAKE_PHONE_NUMBER2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNumberFromIms(FAKE_PHONE_NUMBER2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdatePortIndex() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setPortIndex(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setPortIndex(subInfo.getSubscriptionId(), 1);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setPortIndex(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateUsageSetting() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setUsageSetting(
                        1, SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUsageSetting(subInfo.getSubscriptionId(),
                SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUsageSetting(SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateLastUsedTPMessageReference() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setLastUsedTPMessageReference(
                        1, FAKE_TP_MESSAGE_REFERENCE2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setLastUsedTPMessageReference(subInfo.getSubscriptionId(),
                FAKE_TP_MESSAGE_REFERENCE2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setLastUsedTPMessageReference(FAKE_TP_MESSAGE_REFERENCE2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testUpdateUserId() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setUserId(1, FAKE_USER_ID2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUserId(subInfo.getSubscriptionId(), FAKE_USER_ID2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUserId(FAKE_USER_ID2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));
    }
}
