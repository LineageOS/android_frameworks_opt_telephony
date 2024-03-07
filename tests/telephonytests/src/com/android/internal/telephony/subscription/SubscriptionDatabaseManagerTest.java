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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Telephony;
import android.provider.Telephony.SimInfo;
import android.telephony.SubscriptionManager;
import android.telephony.UiccAccessRule;
import android.telephony.ims.ImsMmTelManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.flags.FeatureFlags;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManager.SubscriptionDatabaseManagerCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SubscriptionDatabaseManagerTest extends TelephonyTest {

    static final String FAKE_DEFAULT_CARD_NAME = "CARD %d";
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
    static final int FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED = 1;
    static final int FAKE_SATELLITE_ATTACH_FOR_CARRIER_DISABLED = 0;
    static final int FAKE_SATELLITE_IS_NTN_ENABLED = 1;
    static final int FAKE_SATELLITE_IS_NTN_DISABLED = 0;

    static final String FAKE_MAC_ADDRESS1 = "DC:E5:5B:38:7D:40";
    static final String FAKE_MAC_ADDRESS2 = "DC:B5:4F:47:F3:4C";

    private FeatureFlags mFeatureFlags;

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
                    .setCellBroadcastExtremeThreatAlertEnabled(1)
                    .setCellBroadcastSevereThreatAlertEnabled(1)
                    .setCellBroadcastAmberAlertEnabled(1)
                    .setCellBroadcastEmergencyAlertEnabled(1)
                    .setCellBroadcastAlertSoundDuration(4)
                    .setCellBroadcastAlertReminderInterval(1)
                    .setCellBroadcastAlertVibrationEnabled(1)
                    .setCellBroadcastAlertSpeechEnabled(1)
                    .setCellBroadcastEtwsTestAlertEnabled(1)
                    .setCellBroadcastAreaInfoMessageEnabled(1)
                    .setCellBroadcastTestAlertEnabled(1)
                    .setCellBroadcastOptOutDialogEnabled(1)
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
                    .setSatelliteEnabled(0)
                    .setSatelliteAttachEnabledForCarrier(
                            FAKE_SATELLITE_ATTACH_FOR_CARRIER_DISABLED)
                    .setOnlyNonTerrestrialNetwork(FAKE_SATELLITE_IS_NTN_DISABLED)
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
                    .setCellBroadcastExtremeThreatAlertEnabled(0)
                    .setCellBroadcastSevereThreatAlertEnabled(0)
                    .setCellBroadcastAmberAlertEnabled(0)
                    .setCellBroadcastEmergencyAlertEnabled(0)
                    .setCellBroadcastAlertSoundDuration(0)
                    .setCellBroadcastAlertReminderInterval(0)
                    .setCellBroadcastAlertVibrationEnabled(0)
                    .setCellBroadcastAlertSpeechEnabled(0)
                    .setCellBroadcastEtwsTestAlertEnabled(0)
                    .setCellBroadcastAreaInfoMessageEnabled(0)
                    .setCellBroadcastTestAlertEnabled(0)
                    .setCellBroadcastOptOutDialogEnabled(0)
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
                    .setSatelliteEnabled(1)
                    .setSatelliteAttachEnabledForCarrier(
                            FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED)
                    .setOnlyNonTerrestrialNetwork(FAKE_SATELLITE_IS_NTN_ENABLED)
                    .setGroupDisabled(false)
                    .build();

    private SubscriptionDatabaseManager mDatabaseManagerUT;

    private final SubscriptionProvider mSubscriptionProvider = new SubscriptionProvider();

    //mock
    private SubscriptionDatabaseManagerCallback mSubscriptionDatabaseManagerCallback;

    static class SubscriptionProvider extends MockContentProvider {
        private final List<ContentValues> mDatabase = new ArrayList<>();

        private final List<String> mAllColumns;

        private boolean mDatabaseChanged;

        SubscriptionProvider() {
            mAllColumns = SimInfo.getAllColumns();
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            logd("SubscriptionProvider: query. uri=" + uri);
            if (!SimInfo.CONTENT_URI.equals(uri)) {
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
            if (!uri.isPathPrefixMatch(SimInfo.CONTENT_URI)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }

            int subId = Integer.parseInt(uri.getLastPathSegment());
            logd("update: subId=" + subId + ", contentValues=" + values);

            ContentValues existingValues = mDatabase.stream()
                    .filter(contentValues -> contentValues.get(
                            SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID).equals(subId))
                    .findFirst()
                    .orElse(null);
            if (existingValues == null) {
                throw new IllegalArgumentException("Invalid sub id " + subId);
            }

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
            if (!uri.isPathPrefixMatch(SimInfo.CONTENT_URI)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }

            logd("delete: uri=" + uri + ", selection=" + selection + ", selectionArgs="
                    + Arrays.toString(selectionArgs));
            if (!selection.equals(SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID + "=?")) {
                throw new UnsupportedOperationException("Only support delete by sub id.");
            }

            int rowsRemoved = 0;
            for (String selectionArg : selectionArgs) {
                int subId = Integer.parseInt(selectionArg);
                // Clear it to null instead of removing it.
                rowsRemoved += mDatabase.removeIf(contentValues -> contentValues.get(
                        SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID).equals(subId)) ? 1 : 0;
            }
            return rowsRemoved;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            logd("SubscriptionProvider: insert. uri=" + uri + ", values=" + values);
            if (!SimInfo.CONTENT_URI.equals(uri)) {
                throw new UnsupportedOperationException("Unsupported uri=" + uri);
            }

            for (String column : values.keySet()) {
                if (!mAllColumns.contains(column)) {
                    throw new IllegalArgumentException("Insert with unknown column " + column);
                }
            }
            // The last row's subId + 1
            int subId;
            if (mDatabase.isEmpty()) {
                subId = 1;
            } else {
                subId = (int) mDatabase.get(mDatabase.size() - 1)
                        .get(SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID) + 1;
            }
            values.put(SimInfo.COLUMN_UNIQUE_KEY_SUBSCRIPTION_ID, subId);
            mDatabase.add(values);
            return ContentUris.withAppendedId(SimInfo.CONTENT_URI, subId);
        }

        @Override
        public Bundle call(String method, @Nullable String args, @Nullable Bundle bundle) {
            Bundle result = new Bundle();
            if (method.equals(SubscriptionManager.RESTORE_SIM_SPECIFIC_SETTINGS_METHOD_NAME)) {
                result.putBoolean(
                        SubscriptionManager.RESTORE_SIM_SPECIFIC_SETTINGS_DATABASE_UPDATED,
                        mDatabaseChanged);
            }
            return result;
        }

        public void setRestoreDatabaseChanged(boolean changed) {
            mDatabaseChanged = changed;
        }
    }

    @Before
    public void setUp() throws Exception {
        logd("SubscriptionDatabaseManagerTest +Setup!");
        super.setUp(getClass().getSimpleName());
        mContextFixture.putBooleanResource(com.android.internal.R.bool
                .config_subscription_database_async_update, true);
        mSubscriptionDatabaseManagerCallback =
                Mockito.mock(SubscriptionDatabaseManagerCallback.class);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mSubscriptionDatabaseManagerCallback).invokeFromExecutor(any(Runnable.class));
        mFeatureFlags = Mockito.mock(FeatureFlags.class);

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);

        doReturn(1).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID1));
        doReturn(2).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID2));
        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(true);
        mDatabaseManagerUT = new SubscriptionDatabaseManager(mContext, Looper.myLooper(),
                mFeatureFlags, mSubscriptionDatabaseManagerCallback);
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
        mDatabaseManagerUT.reloadDatabaseSync();
        processAllMessages();

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
        Field[] declaredFields = SimInfo.class.getDeclaredFields();
        List<String> columnNames = new ArrayList<>();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("COLUMN_")) {
                columnNames.add((String) field.get(null));
            }
        }
        // When you add a new column in Telephony.SimInfo, did you remember to modify
        // Telephony.SimInfo.getAllColumns() as well?
        assertThat(SimInfo.getAllColumns()).containsExactlyElementsIn(columnNames);
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
                mFeatureFlags, mSubscriptionDatabaseManagerCallback);

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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_ICC_ID)).isEqualTo(FAKE_ICCID2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_ICC_ID, FAKE_ICCID1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getIccId())
                .isEqualTo(FAKE_ICCID1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_SIM_SLOT_INDEX))
                .isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_SIM_SLOT_INDEX, 123);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getSimSlotIndex())
                .isEqualTo(123);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_DISPLAY_NAME))
                .isEqualTo(FAKE_CARRIER_NAME2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_DISPLAY_NAME, FAKE_CARRIER_NAME1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getDisplayName())
                .isEqualTo(FAKE_CARRIER_NAME1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_CARRIER_NAME))
                .isEqualTo(FAKE_CARRIER_NAME2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_CARRIER_NAME, FAKE_CARRIER_NAME1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getCarrierName())
                .isEqualTo(FAKE_CARRIER_NAME1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_NAME_SOURCE))
                .isEqualTo(SubscriptionManager.NAME_SOURCE_USER_INPUT);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_NAME_SOURCE, SubscriptionManager.NAME_SOURCE_SIM_PNN);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getDisplayNameSource())
                .isEqualTo(SubscriptionManager.NAME_SOURCE_SIM_PNN);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_COLOR))
                .isEqualTo(FAKE_COLOR2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_COLOR, FAKE_COLOR1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getIconTint())
                .isEqualTo(FAKE_COLOR1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_NUMBER))
                .isEqualTo(FAKE_PHONE_NUMBER2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_NUMBER, FAKE_PHONE_NUMBER1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getNumber())
                .isEqualTo(FAKE_PHONE_NUMBER1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_DATA_ROAMING))
                .isEqualTo(SubscriptionManager.DATA_ROAMING_DISABLE);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_DATA_ROAMING, SubscriptionManager.DATA_ROAMING_ENABLE);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getDataRoaming())
                .isEqualTo(SubscriptionManager.DATA_ROAMING_ENABLE);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_MCC_STRING))
                .isEqualTo(FAKE_MCC2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_MCC_STRING, FAKE_MCC1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getMcc())
                .isEqualTo(FAKE_MCC1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_MNC_STRING))
                .isEqualTo(FAKE_MNC2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_MNC_STRING, FAKE_MNC1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getMnc())
                .isEqualTo(FAKE_MNC1);
    }

    @Test
    public void testUpdateEhplmns() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEhplmns(1, FAKE_EHPLMNS2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEhplmns(subInfo.getSubscriptionId(), FAKE_EHPLMNS2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEhplmns(FAKE_EHPLMNS2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_EHPLMNS))
                .isEqualTo(FAKE_EHPLMNS2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_EHPLMNS, FAKE_EHPLMNS1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getEhplmns())
                .isEqualTo(FAKE_EHPLMNS1);
    }

    @Test
    public void testUpdateHplmns() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setHplmns(1, FAKE_HPLMNS2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setHplmns(subInfo.getSubscriptionId(), FAKE_HPLMNS2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setHplmns(FAKE_HPLMNS2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_HPLMNS))
                .isEqualTo(FAKE_HPLMNS2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_HPLMNS, FAKE_HPLMNS1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getHplmns())
                .isEqualTo(FAKE_HPLMNS1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_IS_EMBEDDED))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_IS_EMBEDDED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getEmbedded())
                .isEqualTo(1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_CARD_ID))
                .isEqualTo(FAKE_ICCID2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_CARD_ID, FAKE_ICCID1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getCardString())
                .isEqualTo(FAKE_ICCID1);
    }

    @Test
    public void testUpdateNativeAccessRules() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNativeAccessRules(1, FAKE_NATIVE_ACCESS_RULES2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNativeAccessRules(subInfo.getSubscriptionId(),
                FAKE_NATIVE_ACCESS_RULES2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNativeAccessRules(FAKE_NATIVE_ACCESS_RULES2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_ACCESS_RULES))
                .isEqualTo(FAKE_NATIVE_ACCESS_RULES2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_ACCESS_RULES, FAKE_NATIVE_ACCESS_RULES1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getNativeAccessRules())
                .isEqualTo(FAKE_NATIVE_ACCESS_RULES1);
    }

    @Test
    public void testUpdateCarrierConfigAccessRules() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCarrierConfigAccessRules(1,
                        FAKE_CARRIER_CONFIG_ACCESS_RULES2));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCarrierConfigAccessRules(subInfo.getSubscriptionId(),
                FAKE_CARRIER_CONFIG_ACCESS_RULES2);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCarrierConfigAccessRules(FAKE_CARRIER_CONFIG_ACCESS_RULES2).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_ACCESS_RULES_FROM_CARRIER_CONFIGS))
                .isEqualTo(FAKE_CARRIER_CONFIG_ACCESS_RULES2);
        mDatabaseManagerUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_ACCESS_RULES_FROM_CARRIER_CONFIGS,
                FAKE_CARRIER_CONFIG_ACCESS_RULES2);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getCarrierConfigAccessRules())
                .isEqualTo(FAKE_CARRIER_CONFIG_ACCESS_RULES2);
    }

    @Test
    public void testUpdateRemovableEmbedded() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setRemovableEmbedded(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRemovableEmbedded(subInfo.getSubscriptionId(), 1);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setRemovableEmbedded(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_IS_REMOVABLE))
                .isEqualTo(1);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_IS_REMOVABLE, 0);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getRemovableEmbedded())
                .isEqualTo(0);
    }

    @Test
    public void testUpdateCellBroadcastExtremeThreatAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastExtremeThreatAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastExtremeThreatAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastExtremeThreatAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_EXTREME_THREAT_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_EXTREME_THREAT_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastExtremeThreatAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastSevereThreatAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastSevereThreatAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastSevereThreatAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastSevereThreatAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_SEVERE_THREAT_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_SEVERE_THREAT_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastSevereThreatAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAmberAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAmberAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAmberAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAmberAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_AMBER_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_AMBER_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAmberAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastEmergencyAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastEmergencyAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastEmergencyAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastEmergencyAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_EMERGENCY_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_EMERGENCY_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastEmergencyAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAlertSoundDuration() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAlertSoundDuration(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAlertSoundDuration(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAlertSoundDuration(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_ALERT_SOUND_DURATION))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_ALERT_SOUND_DURATION, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAlertSoundDuration()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAlertReminderInterval() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAlertReminderInterval(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAlertReminderInterval(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAlertReminderInterval(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_ALERT_REMINDER_INTERVAL))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_ALERT_REMINDER_INTERVAL, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAlertReminderInterval()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAlertVibrationEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAlertVibrationEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAlertVibrationEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAlertVibrationEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_ALERT_VIBRATE))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_ALERT_VIBRATE, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAlertVibrationEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAlertSpeechEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAlertSpeechEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAlertSpeechEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAlertSpeechEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_ALERT_SPEECH))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_ALERT_SPEECH, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAlertSpeechEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastEtwsTestAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastEtwsTestAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastEtwsTestAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastEtwsTestAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_ETWS_TEST_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_ETWS_TEST_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastEtwsTestAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastAreaInfoMessageEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastAreaInfoMessageEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastAreaInfoMessageEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastAreaInfoMessageEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_CHANNEL_50_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_CHANNEL_50_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastAreaInfoMessageEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastTestAlertEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastTestAlertEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastTestAlertEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastTestAlertEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_CMAS_TEST_ALERT))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_CMAS_TEST_ALERT, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastTestAlertEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateCellBroadcastOptOutDialogEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCellBroadcastOptOutDialogEnabled(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCellBroadcastOptOutDialogEnabled(
                subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCellBroadcastOptOutDialogEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CB_OPT_OUT_DIALOG))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CB_OPT_OUT_DIALOG, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCellBroadcastOptOutDialogEnabled()).isEqualTo(1);
    }

    @Test
    public void testUpdateEnhanced4GModeEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setEnhanced4GModeEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setEnhanced4GModeEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setEnhanced4GModeEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getEnhanced4GModeEnabled())
                .isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).isEnhanced4GModeEnabled())
                .isTrue();
    }

    @Test
    public void testUpdateVideoTelephonyEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setVideoTelephonyEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setVideoTelephonyEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setVideoTelephonyEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_VT_IMS_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_VT_IMS_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getVideoTelephonyEnabled())
                .isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).isVideoTelephonyEnabled())
                .isTrue();
    }

    @Test
    public void testUpdateWifiCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setWifiCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ENABLED))
                .isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getWifiCallingEnabled())
                .isEqualTo(1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_MODE))
                .isEqualTo(ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_MODE,
                ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getWifiCallingMode())
                .isEqualTo(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_WFC_IMS_ROAMING_MODE))
                .isEqualTo(ImsMmTelManager.WIFI_MODE_WIFI_ONLY);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ROAMING_MODE,
                ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getWifiCallingModeForRoaming())
                .isEqualTo(ImsMmTelManager.WIFI_MODE_CELLULAR_PREFERRED);
    }

    @Test
    public void testUpdateWifiCallingEnabledForRoaming() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setWifiCallingEnabledForRoaming(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setWifiCallingEnabledForRoaming(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setWifiCallingEnabledForRoaming(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_WFC_IMS_ROAMING_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ROAMING_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getWifiCallingEnabledForRoaming()).isEqualTo(1);
    }

    @Test
    public void testUpdateVoImsOptInEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setVoImsOptInEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setVoImsOptInEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setVoImsOptInEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_VOIMS_OPT_IN_STATUS)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_VOIMS_OPT_IN_STATUS, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getVoImsOptInEnabled()).isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).isVoImsOptInEnabled())
                .isTrue();
    }


    @Test
    public void testUpdateOpportunistic() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setOpportunistic(1, 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setOpportunistic(subInfo.getSubscriptionId(), 1);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo).setOpportunistic(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_IS_OPPORTUNISTIC)).isEqualTo(1);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_IS_OPPORTUNISTIC, 0);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getOpportunistic()).isEqualTo(0);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_GROUP_UUID)).isEqualTo(FAKE_UUID2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_GROUP_UUID, FAKE_UUID1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getGroupUuid()).isEqualTo(FAKE_UUID1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_ISO_COUNTRY_CODE)).isEqualTo(FAKE_COUNTRY_CODE2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_ISO_COUNTRY_CODE, FAKE_COUNTRY_CODE1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getCountryIso()).isEqualTo(FAKE_COUNTRY_CODE1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_CARRIER_ID))
                .isEqualTo(FAKE_CARRIER_ID2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CARRIER_ID, FAKE_CARRIER_ID1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getCarrierId())
                .isEqualTo(FAKE_CARRIER_ID1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_PROFILE_CLASS))
                .isEqualTo(SubscriptionManager.PROFILE_CLASS_TESTING);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_PROFILE_CLASS,
                SubscriptionManager.PROFILE_CLASS_PROVISIONING);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getProfileClass())
                .isEqualTo(SubscriptionManager.PROFILE_CLASS_PROVISIONING);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_SUBSCRIPTION_TYPE))
                .isEqualTo(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_SUBSCRIPTION_TYPE,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getSubscriptionType())
                .isEqualTo(SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_GROUP_OWNER))
                .isEqualTo(FAKE_OWNER2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_GROUP_OWNER, FAKE_OWNER1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getGroupOwner())
                .isEqualTo(FAKE_OWNER1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES)).isEqualTo(FAKE_MOBILE_DATA_POLICY2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES, FAKE_MOBILE_DATA_POLICY1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getEnabledMobileDataPolicies())
                .isEqualTo(FAKE_MOBILE_DATA_POLICY1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_IMSI))
                .isEqualTo(FAKE_IMSI2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_IMSI, FAKE_IMSI1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getImsi())
                .isEqualTo(FAKE_IMSI1);
    }

    @Test
    public void testUpdateUiccApplicationsEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setUiccApplicationsEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setUiccApplicationsEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setUiccApplicationsEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_UICC_APPLICATIONS_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_UICC_APPLICATIONS_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getUiccApplicationsEnabled())
                .isEqualTo(1);
    }

    @Test
    public void testUpdateRcsUceEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setRcsUceEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setRcsUceEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setRcsUceEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_IMS_RCS_UCE_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getRcsUceEnabled())
                .isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).isRcsUceEnabled())
                .isTrue();
    }

    @Test
    public void testUpdateCrossSimCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setCrossSimCallingEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setCrossSimCallingEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setCrossSimCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getCrossSimCallingEnabled())
                .isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).isCrossSimCallingEnabled())
                .isTrue();
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_RCS_CONFIG)).isEqualTo(FAKE_RCS_CONFIG2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_RCS_CONFIG, FAKE_RCS_CONFIG1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getRcsConfig())
                .isEqualTo(FAKE_RCS_CONFIG1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_ALLOWED_NETWORK_TYPES_FOR_REASONS))
                .isEqualTo(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS2);
        mDatabaseManagerUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_ALLOWED_NETWORK_TYPES_FOR_REASONS,
                FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getAllowedNetworkTypesForReasons())
                .isEqualTo(FAKE_ALLOWED_NETWORK_TYPES_FOR_REASONS1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_D2D_STATUS_SHARING))
                .isEqualTo(SubscriptionManager.D2D_SHARING_DISABLED);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_D2D_STATUS_SHARING,
                SubscriptionManager.D2D_SHARING_ALL);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getDeviceToDeviceStatusSharingPreference())
                .isEqualTo(SubscriptionManager.D2D_SHARING_ALL);
    }

    @Test
    public void testUpdateNrAdvancedCallingEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNrAdvancedCallingEnabled(1, 0));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNrAdvancedCallingEnabled(subInfo.getSubscriptionId(), 0);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setNrAdvancedCallingEnabled(0).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED)).isEqualTo(0);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getNrAdvancedCallingEnabled()).isEqualTo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .isNrAdvancedCallingEnabled()).isTrue();
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_PHONE_NUMBER_SOURCE_CARRIER)).isEqualTo(FAKE_PHONE_NUMBER2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_PHONE_NUMBER_SOURCE_CARRIER, FAKE_PHONE_NUMBER1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getNumberFromCarrier()).isEqualTo(FAKE_PHONE_NUMBER1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                1, SimInfo.COLUMN_PHONE_NUMBER_SOURCE_IMS)).isEqualTo(FAKE_PHONE_NUMBER2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_PHONE_NUMBER_SOURCE_IMS, FAKE_PHONE_NUMBER1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getNumberFromIms()).isEqualTo(FAKE_PHONE_NUMBER1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_PORT_INDEX))
                .isEqualTo(1);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_PORT_INDEX, 2);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getPortIndex())
                .isEqualTo(2);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_USAGE_SETTING))
                .isEqualTo(SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_USAGE_SETTING, SubscriptionManager.USAGE_SETTING_DATA_CENTRIC);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1).getUsageSetting())
                .isEqualTo(SubscriptionManager.USAGE_SETTING_DATA_CENTRIC);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_TP_MESSAGE_REF))
                .isEqualTo(FAKE_TP_MESSAGE_REFERENCE2);
        mDatabaseManagerUT.setSubscriptionProperty(
                1, SimInfo.COLUMN_TP_MESSAGE_REF, FAKE_TP_MESSAGE_REFERENCE1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getLastUsedTPMessageReference()).isEqualTo(FAKE_TP_MESSAGE_REFERENCE1);
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

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(1, SimInfo.COLUMN_USER_HANDLE))
                .isEqualTo(FAKE_USER_ID2);
        mDatabaseManagerUT.setSubscriptionProperty(1, SimInfo.COLUMN_USER_HANDLE, FAKE_USER_ID1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getUserId()).isEqualTo(FAKE_USER_ID1);
    }

    @Test
    public void testUpdateSatelliteEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class, () -> mDatabaseManagerUT.setSatelliteEnabled(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(), 1));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSatelliteEnabled(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                1);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setSatelliteEnabled(1).build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(), SimInfo.COLUMN_SATELLITE_ENABLED))
                .isEqualTo(1);

        mDatabaseManagerUT.setSubscriptionProperty(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_SATELLITE_ENABLED, 0);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId()).getSatelliteEnabled())
                .isEqualTo(0);
    }

    @Test
    public void testUpdateCarrierHandoverToSatelliteEnabled() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setSatelliteAttachEnabledForCarrier(
                        FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                        FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSatelliteAttachEnabledForCarrier(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setSatelliteAttachEnabledForCarrier(
                        FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED)
                .build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_SATELLITE_ATTACH_ENABLED_FOR_CARRIER))
                .isEqualTo(FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED);

        mDatabaseManagerUT.setSubscriptionProperty(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_SATELLITE_ATTACH_ENABLED_FOR_CARRIER,
                FAKE_SATELLITE_ATTACH_FOR_CARRIER_DISABLED);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId())
                .getSatelliteAttachEnabledForCarrier())
                .isEqualTo(FAKE_SATELLITE_ATTACH_FOR_CARRIER_DISABLED);
    }

    @Test
    public void testUpdateSatelliteNtn() throws Exception {
        // exception is expected if there is nothing in the database.
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setNtn(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                        FAKE_SATELLITE_IS_NTN_ENABLED));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setNtn(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                FAKE_SATELLITE_IS_NTN_ENABLED);
        processAllMessages();

        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setOnlyNonTerrestrialNetwork(FAKE_SATELLITE_IS_NTN_ENABLED)
                .build();
        verifySubscription(subInfo);
        verify(mSubscriptionDatabaseManagerCallback, times(2)).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_IS_NTN)).isEqualTo(FAKE_SATELLITE_IS_NTN_ENABLED);

        mDatabaseManagerUT.setSubscriptionProperty(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_IS_NTN, FAKE_SATELLITE_IS_NTN_DISABLED);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(
                        FAKE_SUBSCRIPTION_INFO1.getSubscriptionId()).getOnlyNonTerrestrialNetwork())
                .isEqualTo(FAKE_SATELLITE_IS_NTN_DISABLED);
    }

    @Test
    public void testUpdateSatelliteNtnWithFeatureDisabled() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mDatabaseManagerUT.setSatelliteAttachEnabledForCarrier(
                        FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                        FAKE_SATELLITE_ATTACH_FOR_CARRIER_ENABLED));

        SubscriptionInfoInternal subInfo = insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        mDatabaseManagerUT.setSatelliteAttachEnabledForCarrier(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                FAKE_SATELLITE_IS_NTN_DISABLED);
        processAllMessages();

        when(mFeatureFlags.oemEnabledSatelliteFlag()).thenReturn(false);
        reset(mSubscriptionDatabaseManagerCallback);
        subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                .setOnlyNonTerrestrialNetwork(FAKE_SATELLITE_IS_NTN_ENABLED)
                .build();

        int subId = subInfo.getSubscriptionId();
        // Verify the cache value is not same as the inserted one.
        assertWithMessage("Subscription info cache value is not different.")
                .that(mDatabaseManagerUT.getSubscriptionInfoInternal(subId)).isNotEqualTo(subInfo);

        // Load subscription info from the database.
        mDatabaseManagerUT.reloadDatabaseSync();
        processAllMessages();

        // Verify the database value is not same as the inserted one.
        assertWithMessage("Subscription info database value is not different.")
                .that(mDatabaseManagerUT.getSubscriptionInfoInternal(subId)).isNotEqualTo(subInfo);

        verify(mSubscriptionDatabaseManagerCallback, never()).onSubscriptionChanged(eq(1));

        assertThat(mDatabaseManagerUT.getSubscriptionProperty(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_IS_NTN)).isNotEqualTo(FAKE_SATELLITE_IS_NTN_ENABLED);

        mDatabaseManagerUT.setSubscriptionProperty(FAKE_SUBSCRIPTION_INFO1.getSubscriptionId(),
                SimInfo.COLUMN_IS_NTN, FAKE_SATELLITE_IS_NTN_ENABLED);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(
                FAKE_SUBSCRIPTION_INFO1.getSubscriptionId()).getOnlyNonTerrestrialNetwork())
                .isNotEqualTo(FAKE_SATELLITE_IS_NTN_ENABLED);
    }

    @Test
    public void testUpdateSubscriptionsInGroup() throws Exception {
        insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO2);
        // Two subs are now in the same group
        mDatabaseManagerUT.setGroupUuid(2, FAKE_UUID1);

        mDatabaseManagerUT.setWifiCallingEnabled(1, 1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .isWifiCallingEnabled()).isTrue();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .isWifiCallingEnabled()).isTrue();

        mDatabaseManagerUT.setWifiCallingEnabled(1, 0);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .isWifiCallingEnabled()).isFalse();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .isWifiCallingEnabled()).isFalse();

        mDatabaseManagerUT.setUserId(1, 5678);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getUserId()).isEqualTo(5678);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .getUserId()).isEqualTo(5678);

        mDatabaseManagerUT.setWifiCallingEnabledForRoaming(1, 0);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .isWifiCallingEnabledForRoaming()).isFalse();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .isWifiCallingEnabledForRoaming()).isFalse();

        mDatabaseManagerUT.setDisplayName(1, "Pokemon");
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getDisplayName()).isEqualTo("Pokemon");
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .getDisplayName()).isEqualTo("Pokemon");

        // ICCID is not the field that will be synced to all subs in the group.
        mDatabaseManagerUT.setIccId(1, "0987");
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)
                .getIccId()).isEqualTo("0987");
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)
                .getIccId()).isEqualTo(FAKE_ICCID2);
    }

    @Test
    public void testRemoveSubscriptionInfo() throws Exception {
        insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO2);
        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);

        mDatabaseManagerUT.removeSubscriptionInfo(1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2))
                .isEqualTo(FAKE_SUBSCRIPTION_INFO2);
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(1));

        // Insert a new one. Should become sub 3.
        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);
        insertSubscriptionAndVerify(FAKE_SUBSCRIPTION_INFO1);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2))
                .isEqualTo(FAKE_SUBSCRIPTION_INFO2);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(3))
                .isEqualTo(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                        .setId(3).build());
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(3));

        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);
        mDatabaseManagerUT.removeSubscriptionInfo(2);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(3))
                .isEqualTo(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                        .setId(3).build());
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(2));

        Mockito.clearInvocations(mSubscriptionDatabaseManagerCallback);
        mDatabaseManagerUT.removeSubscriptionInfo(3);
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(1)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(2)).isNull();
        assertThat(mDatabaseManagerUT.getSubscriptionInfoInternal(3)).isNull();
        verify(mSubscriptionDatabaseManagerCallback).onSubscriptionChanged(eq(3));
    }

    @Test
    public void testCallback() {
        CountDownLatch latch = new CountDownLatch(2);
        Executor executor = Runnable::run;
        SubscriptionDatabaseManagerCallback callback =
                new SubscriptionDatabaseManagerCallback(executor) {
                    @Override
                    public void onInitialized() {
                        latch.countDown();
                        logd("onInitialized");
                    }

                    @Override
                    public void onSubscriptionChanged(int subId) {
                        latch.countDown();
                        logd("onSubscriptionChanged");
                    }
                };
        assertThat(callback.getExecutor()).isEqualTo(executor);
        mDatabaseManagerUT = new SubscriptionDatabaseManager(mContext, Looper.myLooper(),
                mFeatureFlags, callback);
        processAllMessages();

        assertThat(latch.getCount()).isEqualTo(1);

        mDatabaseManagerUT.insertSubscriptionInfo(
                new SubscriptionInfoInternal.Builder()
                        .setId(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                        .setIccId(FAKE_ICCID1)
                        .setSimSlotIndex(0)
                        .build());
        processAllMessages();
        assertThat(latch.getCount()).isEqualTo(0);
    }
}
