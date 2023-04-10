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

import static com.android.internal.telephony.TelephonyTestUtils.waitForMs;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CONTACT1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CONTACT2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_COUNTRY_CODE2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MCC1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MCC2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MNC1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MNC2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MOBILE_DATA_POLICY1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MOBILE_DATA_POLICY2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_NATIVE_ACCESS_RULES1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_NATIVE_ACCESS_RULES2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_PHONE_NUMBER2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_RCS_CONFIG1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_RCS_CONFIG2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SUBSCRIPTION_INFO1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SUBSCRIPTION_INFO2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_UUID1;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.app.PropertyInvalidatedCache;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.SimInfo;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.service.euicc.EuiccService;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.UiccAccessRule;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.Base64;

import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.SubscriptionProvider;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionManagerServiceCallback;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccSlot;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SubscriptionManagerServiceTest extends TelephonyTest {

    private static final String CALLING_PACKAGE = "calling_package";

    private static final String CALLING_FEATURE = "calling_feature";

    private SubscriptionManagerService mSubscriptionManagerServiceUT;

    private final SubscriptionProvider mSubscriptionProvider = new SubscriptionProvider();

    private static final UserHandle FAKE_USER_HANDLE = new UserHandle(12);

    // mocked
    private SubscriptionManagerServiceCallback mMockedSubscriptionManagerServiceCallback;
    private EuiccController mEuiccController;
    private UiccSlot mUiccSlot;
    private UiccCard mUiccCard;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() throws Exception {
        logd("SubscriptionManagerServiceTest +Setup!");
        super.setUp(getClass().getSimpleName());

        mContextFixture.putIntArrayResource(com.android.internal.R.array.sim_colors, new int[0]);

        mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC);
        setupMocksForTelephonyPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        PropertyInvalidatedCache.disableForCurrentProcess("cache_key.is_compat_change_enabled");

        doReturn(true).when(mTelephonyManager).isVoiceCapable();
        mEuiccController = Mockito.mock(EuiccController.class);
        replaceInstance(EuiccController.class, "sInstance", null, mEuiccController);
        mUiccSlot = Mockito.mock(UiccSlot.class);
        mUiccCard = Mockito.mock(UiccCard.class);
        mMockedSubscriptionManagerServiceCallback = Mockito.mock(
                SubscriptionManagerServiceCallback.class);
        doReturn(mUiccCard).when(mUiccSlot).getUiccCard();
        doReturn(FAKE_ICCID1).when(mUiccCard).getCardId();

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);
        mSubscriptionManagerServiceUT = new SubscriptionManagerService(mContext, Looper.myLooper());

        monitorTestableLooper(new TestableLooper(getBackgroundHandler().getLooper()));

        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedSubscriptionManagerServiceCallback).invokeFromExecutor(any(Runnable.class));

        mSubscriptionManagerServiceUT.registerCallback(mMockedSubscriptionManagerServiceCallback);
        // Database loading is on a different thread. Need to wait a bit.
        waitForMs(100);
        processAllFutureMessages();

        // Revoke all permissions.
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).noteOpNoThrow(anyString(),
                anyInt(), nullable(String.class), nullable(String.class), nullable(String.class));
        setIdentifierAccess(false);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);

        logd("SubscriptionManagerServiceTest -Setup!");
    }

    @After
    public void tearDown() throws Exception {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        super.tearDown();
    }

    private Handler getBackgroundHandler() throws Exception {
        Field field = SubscriptionManagerService.class.getDeclaredField(
                "mBackgroundHandler");
        field.setAccessible(true);
        return (Handler) field.get(mSubscriptionManagerServiceUT);
    }

    /**
     * Insert the subscription info to the database.
     *
     * @param subInfo The subscription to be inserted.
     * @return The new sub id.
     */
    private int insertSubscription(@NonNull SubscriptionInfoInternal subInfo) {
        try {
            mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
            subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                    .setId(SubscriptionManager.INVALID_SUBSCRIPTION_ID).build();

            Field field = SubscriptionManagerService.class.getDeclaredField(
                    "mSubscriptionDatabaseManager");
            field.setAccessible(true);
            SubscriptionDatabaseManager sdbm =
                    (SubscriptionDatabaseManager) field.get(mSubscriptionManagerServiceUT);

            Class[] cArgs = new Class[1];
            cArgs[0] = SubscriptionInfoInternal.class;
            Method method = SubscriptionDatabaseManager.class.getDeclaredMethod(
                    "insertSubscriptionInfo", cArgs);
            method.setAccessible(true);
            int subId = (int) method.invoke(sdbm, subInfo);

            // Insertion is sync, but the onSubscriptionChanged callback is handled by the handler.
            processAllMessages();

            Class<?> WatchedMapClass = Class.forName("com.android.internal.telephony.subscription"
                    + ".SubscriptionManagerService$WatchedMap");
            field = SubscriptionManagerService.class.getDeclaredField("mSlotIndexToSubId");
            field.setAccessible(true);
            Object map = field.get(mSubscriptionManagerServiceUT);
            cArgs = new Class[2];
            cArgs[0] = Object.class;
            cArgs[1] = Object.class;

            method = WatchedMapClass.getDeclaredMethod("put", cArgs);
            method.setAccessible(true);
            method.invoke(map, subInfo.getSimSlotIndex(), subId);
            mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
            processAllMessages();
            verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));
            Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);
            return subId;
        } catch (Exception e) {
            fail("Failed to insert subscription. e=" + e);
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private void enableGetSubscriptionUserHandle() {
        Resources mResources = mock(Resources.class);
        doReturn(true).when(mResources).getBoolean(
                eq(com.android.internal.R.bool.config_enable_get_subscription_user_handle));
        doReturn(mResources).when(mContext).getResources();
    }

    @Test
    public void testAddSubInfo() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
    }

    @Test
    public void testSetMccMnc() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setMccMnc(1, FAKE_MCC2 + FAKE_MNC2);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC2);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC2);
        verify(mMockedSubscriptionManagerServiceCallback, times(2)).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testSetCountryIso() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setCountryIso(1, FAKE_COUNTRY_CODE2);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getCountryIso()).isEqualTo(FAKE_COUNTRY_CODE2);
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testSetCarrierId() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setCarrierId(1, FAKE_CARRIER_ID2);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getCarrierId()).isEqualTo(FAKE_CARRIER_ID2);
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testSetPhoneNumber() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);

        // Caller does not have carrier privilege
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setPhoneNumber(1,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, FAKE_PHONE_NUMBER2,
                        CALLING_PACKAGE, CALLING_FEATURE));

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);

        // Source IMS is not acceptable
        assertThrows(IllegalArgumentException.class,
                () -> mSubscriptionManagerServiceUT.setPhoneNumber(1,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_IMS, FAKE_PHONE_NUMBER2,
                        CALLING_PACKAGE, CALLING_FEATURE));

        mSubscriptionManagerServiceUT.setPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, FAKE_PHONE_NUMBER2,
                CALLING_PACKAGE, CALLING_FEATURE);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getNumberFromCarrier()).isEqualTo(FAKE_PHONE_NUMBER2);
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testGetAllSubInfoList() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(new int[]{1, 2}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should throw security exception if the caller does not have permission.
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getAllSubInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE));

        // Grant carrier privilege for sub 1
        setCarrierPrivilegesForSubId(true, 1);
        // Grant carrier privilege for sub 2
        setCarrierPrivilegesForSubId(true, 2);

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(2);

        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());

        assertThat(subInfos.get(1)).isEqualTo(FAKE_SUBSCRIPTION_INFO2.toSubscriptionInfo());

        // Revoke carrier privilege for sub 2
        setCarrierPrivilegesForSubId(false, 2);

        subInfos = mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE);
        // Should only have access to one sub.
        assertThat(subInfos).hasSize(1);

        assertThat(subInfos.get(0).getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfos.get(0).getCardString()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfos.get(0).getNumber()).isEqualTo(FAKE_PHONE_NUMBER1);

        // Grant READ_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Grant identifier access
        setIdentifierAccess(true);
        // Revoke carrier privileges.
        setCarrierPrivilegesForSubId(false, 1);
        setCarrierPrivilegesForSubId(false, 2);

        subInfos = mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(2);

        assertThat(subInfos.get(0).getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfos.get(0).getCardString()).isEqualTo(FAKE_ICCID1);
        // Phone number should be empty
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(1).getIccId()).isEqualTo(FAKE_ICCID2);
        assertThat(subInfos.get(1).getCardString()).isEqualTo(FAKE_ICCID2);
        // Phone number should be empty
        assertThat(subInfos.get(1).getNumber()).isEmpty();

        // Grant phone number access
        doReturn(PackageManager.PERMISSION_GRANTED).when(mMockLegacyPermissionManager)
                .checkPhoneNumberAccess(anyString(), anyString(), anyString(), anyInt(), anyInt());

        subInfos = mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(2);
        assertThat(subInfos.get(0).getNumber()).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(subInfos.get(1).getNumber()).isEqualTo(FAKE_PHONE_NUMBER2);
    }

    @Test
    @EnableCompatChanges({SubscriptionManagerService.REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID})
    public void testGetSubscriptionsInGroup() {
        doReturn(new int[]{1, 2}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();

        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        SubscriptionInfoInternal anotherSubInfo =
                new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                        .setGroupUuid(FAKE_UUID1)
                        .build();
        insertSubscription(anotherSubInfo);

        // Throw exception is the new behavior.
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                        ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE));

        // Grant carrier privilege on sub 1 and 2
        setCarrierPrivilegesForSubId(true, 1);
        setCarrierPrivilegesForSubId(true, 2);
        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE);

        assertThat(subInfos).hasSize(2);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
        assertThat(subInfos.get(1)).isEqualTo(anotherSubInfo.toSubscriptionInfo());

        // Grant READ_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        setIdentifierAccess(false);
        setCarrierPrivilegesForSubId(false, 1);
        setCarrierPrivilegesForSubId(false, 2);
        doNothing().when(mContext).enforcePermission(
                eq(android.Manifest.permission.READ_PHONE_STATE), anyInt(), anyInt(), anyString());

        // Throw exception is the new behavior. Only has READ_PHONE_STATE is not enough. Need
        // identifier access as well.
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                        ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE));

        // Grant identifier access
        setIdentifierAccess(true);
        // Grant phone number access
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);

        subInfos = mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE);

        assertThat(subInfos).hasSize(2);
        assertThat(subInfos).containsExactlyElementsIn(
                List.of(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo(),
                        anotherSubInfo.toSubscriptionInfo()));
    }

    @Test
    public void testGetAvailableSubscriptionInfoList() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        SubscriptionInfoInternal anotherSubInfo =
                new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                        .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX)
                        .setType(SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)
                        .build();
        insertSubscription(anotherSubInfo);

        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE));
        // Grant carrier privilege for sub 1
        setCarrierPrivilegesForSubId(true, 1);

        // Not yet planned for carrier apps to access this API.
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission, which is not enough for this API.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PRIVILEGED_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getAvailableSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    public void testSetDefaultVoiceSubId() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setDefaultVoiceSubId(1));

        // Grant MODIFY_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultVoiceSubId(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendStickyBroadcastAsUser(
                captorIntent.capture(), eq(UserHandle.ALL));

        Intent intent = captorIntent.getAllValues().get(0);
        assertThat(intent.getAction()).isEqualTo(
                TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED);

        Bundle b = intent.getExtras();

        assertThat(b.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isTrue();
        assertThat(b.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isEqualTo(1);

        intent = captorIntent.getAllValues().get(1);
        assertThat(intent.getAction()).isEqualTo(
                SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);

        b = intent.getExtras();

        assertThat(b.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isTrue();
        assertThat(b.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isEqualTo(1);
    }

    @Test
    public void testSetDefaultDataSubId() throws Exception {
        doReturn(false).when(mTelephonyManager).isVoiceCapable();
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setDefaultDataSubId(1));

        // Grant MODIFY_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultDataSubId(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendStickyBroadcastAsUser(
                captorIntent.capture(), eq(UserHandle.ALL));

        Intent intent = captorIntent.getAllValues().get(0);
        assertThat(intent.getAction()).isEqualTo(
                TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED);

        Bundle b = intent.getExtras();

        assertThat(b.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isTrue();
        assertThat(b.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isEqualTo(1);

        intent = captorIntent.getAllValues().get(1);
        assertThat(intent.getAction()).isEqualTo(
                SubscriptionManager.ACTION_DEFAULT_SUBSCRIPTION_CHANGED);

        b = intent.getExtras();

        assertThat(b.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isTrue();
        assertThat(b.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isEqualTo(1);
    }

    @Test
    public void testSetDefaultSmsSubId() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setDefaultSmsSubId(1));

        // Grant MODIFY_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultSmsSubId(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendStickyBroadcastAsUser(captorIntent.capture(), eq(UserHandle.ALL));

        Intent intent = captorIntent.getValue();
        assertThat(intent.getAction()).isEqualTo(
                SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED);

        Bundle b = intent.getExtras();

        assertThat(b.containsKey(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isTrue();
        assertThat(b.getInt(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX)).isEqualTo(1);
    }

    @Test
    public void testIsActiveSubId() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX).build());

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .isActiveSubId(1, CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PRIVILEGED_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(
                1, CALLING_PACKAGE, CALLING_FEATURE)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(
                2, CALLING_PACKAGE, CALLING_FEATURE)).isFalse();
    }

    @Test
    public void testGetActiveSubscriptionInfoList() {
        doReturn(new int[]{1}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();
        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX).build());
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0).getIccId()).isEmpty();
        assertThat(subInfos.get(0).getCardString()).isEmpty();
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(0).getGroupUuid()).isNull();

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    public void testGetActiveSubscriptionInfoForSimSlotIndex() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForSimSlotIndex(0, CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForSimSlotIndex(0, CALLING_PACKAGE,
                        CALLING_FEATURE);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getIccId()).isEmpty();
        assertThat(subInfo.getNumber()).isEmpty();

        // Grant carrier privilege for sub 1
        setCarrierPrivilegesForSubId(true, 1);
        subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                0, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    public void testUpdateEmbeddedSubscriptions() {
        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC1, FAKE_MNC1, null, null, null,
                        null, FAKE_CARRIER_ID1, FAKE_CARRIER_ID1))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();
        EuiccProfileInfo profileInfo2 = new EuiccProfileInfo.Builder(FAKE_ICCID2)
                .setIccid(FAKE_ICCID2)
                .setNickname(FAKE_CARRIER_NAME2)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC2, FAKE_MNC2, null, null, null,
                        null, FAKE_CARRIER_ID2, FAKE_CARRIER_ID2))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES2)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        result = new GetEuiccProfileInfoListResult(EuiccService.RESULT_OK,
                new EuiccProfileInfo[]{profileInfo2}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(2));
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));
        doReturn(FAKE_ICCID2).when(mUiccController).convertToCardString(eq(2));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1, 2), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_CARRIER);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC1);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC1);
        assertThat(subInfo.getProfileClass()).isEqualTo(
                SubscriptionManager.PROFILE_CLASS_OPERATIONAL);
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.isRemovableEmbedded()).isFalse();
        assertThat(subInfo.getNativeAccessRules()).isEqualTo(FAKE_NATIVE_ACCESS_RULES1);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(2);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID2);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME2);
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_CARRIER);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC2);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC2);
        assertThat(subInfo.getProfileClass()).isEqualTo(
                SubscriptionManager.PROFILE_CLASS_OPERATIONAL);
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.isRemovableEmbedded()).isFalse();
        assertThat(subInfo.getNativeAccessRules()).isEqualTo(FAKE_NATIVE_ACCESS_RULES2);
    }

    @Test
    public void testGetActiveSubscriptionInfo() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfo(1, CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForSimSlotIndex(0, CALLING_PACKAGE,
                        CALLING_FEATURE);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getIccId()).isEmpty();
        assertThat(subInfo.getNumber()).isEmpty();

        // Grant carrier privilege for sub 1
        setCarrierPrivilegesForSubId(true, 1);
        subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                0, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    public void testSetDisplayNameUsingSrc() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setDisplayNameUsingSrc(FAKE_CARRIER_NAME2, 1,
                        SubscriptionManager.NAME_SOURCE_CARRIER_ID));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Carrier ID name source should have lower priority. Should not be able to update the
        // display name.
        assertThat(mSubscriptionManagerServiceUT.setDisplayNameUsingSrc(FAKE_CARRIER_NAME2,
                1, SubscriptionManager.NAME_SOURCE_CARRIER_ID)).isEqualTo(0);

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1).getDisplayName())
                .isEqualTo(FAKE_CARRIER_NAME1);

        // User input display name should have highest priority.
        assertThat(mSubscriptionManagerServiceUT.setDisplayNameUsingSrc(FAKE_CARRIER_NAME2,
                1, SubscriptionManager.NAME_SOURCE_USER_INPUT)).isEqualTo(1);

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1).getDisplayName())
                .isEqualTo(FAKE_CARRIER_NAME2);
    }

    @Test
    public void testGetActiveSubInfoCount() {
        doReturn(new int[]{1, 2}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubInfoCount(CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(2);
    }

    @Test
    public void testSetIconTint() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setIconTint(1, 12345));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.setIconTint(1, 12345);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getIconTint()).isEqualTo(12345);
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
    }

    @Test
    public void testGetActiveSubscriptionInfoForIccId() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForIccId(FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    public void testGetAccessibleSubscriptionInfoList() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        doReturn(false).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isNull();

        doReturn(true).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEmpty();

        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                eq(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo()), eq(CALLING_PACKAGE));

        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEqualTo(List.of(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo()));
    }

    @Test
    public void testIsSubscriptionEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .isSubscriptionEnabled(1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(1)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(2)).isFalse();
    }

    @Test
    public void testGetEnabledSubscriptionId() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getEnabledSubscriptionId(0));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThrows(IllegalArgumentException.class, () -> mSubscriptionManagerServiceUT
                .getEnabledSubscriptionId(SubscriptionManager.INVALID_SIM_SLOT_INDEX));

        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(0)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(1)).isEqualTo(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        assertThrows(IllegalArgumentException.class, () -> mSubscriptionManagerServiceUT
                .getEnabledSubscriptionId(2));
    }

    @Test
    public void testGetActiveDataSubscriptionId() {
        doReturn(12345).when(mPhoneSwitcher).getActiveDataSubId();
        assertThat(mSubscriptionManagerServiceUT.getActiveDataSubscriptionId()).isEqualTo(12345);
    }

    @Test
    public void testSetGetSubscriptionUserHandle() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        enableGetSubscriptionUserHandle();

        // Should fail without MANAGE_SUBSCRIPTION_USER_ASSOCIATION
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionUserHandle(FAKE_USER_HANDLE, 1));

        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(FAKE_USER_HANDLE, 1);

        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getUserId()).isEqualTo(FAKE_USER_HANDLE.getIdentifier());

        mContextFixture.removeCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        // Should fail without MANAGE_SUBSCRIPTION_USER_ASSOCIATION
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getSubscriptionUserHandle(1));

        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionUserHandle(1))
                .isEqualTo(FAKE_USER_HANDLE);
    }

    @Test
    public void testIsSubscriptionAssociatedWithUser() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        enableGetSubscriptionUserHandle();

        // Should fail without MANAGE_SUBSCRIPTION_USER_ASSOCIATION
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .isSubscriptionAssociatedWithUser(1, FAKE_USER_HANDLE));
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getSubscriptionInfoListAssociatedWithUser(FAKE_USER_HANDLE));

        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(FAKE_USER_HANDLE, 1);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        List<SubscriptionInfo> associatedSubInfoList = mSubscriptionManagerServiceUT
                .getSubscriptionInfoListAssociatedWithUser(FAKE_USER_HANDLE);
        assertThat(associatedSubInfoList.size()).isEqualTo(1);
        assertThat(associatedSubInfoList.get(0).getSubscriptionId()).isEqualTo(1);

        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_USER_HANDLE)).isEqualTo(true);
    }

    @Test
    public void testSetUsageSetting() {
        doReturn(new int[]{1}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setUsageSetting(SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC, 1,
                        CALLING_PACKAGE));

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);
        mSubscriptionManagerServiceUT.setUsageSetting(
                SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC, 1, CALLING_PACKAGE);

        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getUsageSetting()).isEqualTo(
                SubscriptionManager.USAGE_SETTING_VOICE_CENTRIC);
    }

    @Test
    public void testSetDisplayNumber() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setDisplayNumber(FAKE_PHONE_NUMBER2, 1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDisplayNumber(FAKE_PHONE_NUMBER2, 1);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_PHONE_NUMBER2);
    }

    @Test
    public void testSetOpportunistic() {
        doReturn(new int[]{1}).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setOpportunistic(true, 1, CALLING_PACKAGE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.setOpportunistic(true, 1, CALLING_PACKAGE);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.isOpportunistic()).isTrue();
    }

    @Test
    public void testGetOpportunisticSubscriptions() {
        testSetOpportunistic();
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getOpportunisticSubscriptions(CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);

        setIdentifierAccess(true);
        setPhoneNumberAccess(PackageManager.PERMISSION_GRANTED);

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getOpportunisticSubscriptions(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfos).hasSize(2);
        assertThat(subInfos.get(0)).isEqualTo(new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO1).setOpportunistic(1).build().toSubscriptionInfo());
        assertThat(subInfos.get(1)).isEqualTo(FAKE_SUBSCRIPTION_INFO2.toSubscriptionInfo());
    }

    @Test
    public void testSetPreferredDataSubscriptionId() {
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setPreferredDataSubscriptionId(1, false, null));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setPreferredDataSubscriptionId(1, false, null);
        verify(mPhoneSwitcher).trySetOpportunisticDataSubscription(eq(1), eq(false), eq(null));
    }

    @Test
    public void testGetPreferredDataSubscriptionId() {
        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getPreferredDataSubscriptionId());

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        doReturn(12345).when(mPhoneSwitcher).getAutoSelectedDataSubId();
        assertThat(mSubscriptionManagerServiceUT.getPreferredDataSubscriptionId()).isEqualTo(12345);
    }

    @Test
    public void testAddSubscriptionsIntoGroup() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        ParcelUuid newUuid = ParcelUuid.fromString("6adbc864-691c-45dc-b698-8fc9a2176fae");
        String newOwner = "new owner";
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .addSubscriptionsIntoGroup(new int[]{1, 2}, newUuid, CALLING_PACKAGE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubscriptionsIntoGroup(
                new int[]{1, 2}, newUuid, newOwner);

        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfo(1);
        assertThat(subInfo.getGroupUuid()).isEqualTo(newUuid);
        assertThat(subInfo.getGroupOwner()).isEqualTo(newOwner);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfo(2);
        assertThat(subInfo.getGroupUuid()).isEqualTo(newUuid);
        assertThat(subInfo.getGroupOwner()).isEqualTo(newOwner);
    }

    @Test
    public void testSetDeviceToDeviceStatusSharing() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setDeviceToDeviceStatusSharing(SubscriptionManager.D2D_SHARING_SELECTED_CONTACTS,
                        1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDeviceToDeviceStatusSharing(
                SubscriptionManager.D2D_SHARING_SELECTED_CONTACTS, 1);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getDeviceToDeviceStatusSharingPreference()).isEqualTo(
                SubscriptionManager.D2D_SHARING_SELECTED_CONTACTS);
    }

    @Test
    public void testSetDeviceToDeviceStatusSharingContacts() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setDeviceToDeviceStatusSharingContacts(FAKE_CONTACT2, 1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDeviceToDeviceStatusSharingContacts(FAKE_CONTACT2, 1);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getDeviceToDeviceStatusSharingContacts()).isEqualTo(FAKE_CONTACT2);
    }

    @Test
    public void testGetPhoneNumberFromFirstAvailableSource() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without phone number access
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getPhoneNumberFromFirstAvailableSource(1, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_NUMBERS);

        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                1, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
    }

    @Test
    public void testSetUiccApplicationsEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setUiccApplicationsEnabled(false, 1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(false, 1);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.areUiccApplicationsEnabled()).isFalse();
    }

    @Test
    public void testCanDisablePhysicalSubscription() {
        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .canDisablePhysicalSubscription());

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        doReturn(false).when(mPhone).canDisablePhysicalSubscription();
        assertThat(mSubscriptionManagerServiceUT.canDisablePhysicalSubscription()).isFalse();

        doReturn(true).when(mPhone).canDisablePhysicalSubscription();
        assertThat(mSubscriptionManagerServiceUT.canDisablePhysicalSubscription()).isTrue();
    }

    @Test
    public void testSetGetEnhanced4GModeEnabled() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_ENHANCED_4G_MODE_ENABLED
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_ENHANCED_4G_MODE_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getEnhanced4GModeEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetVideoTelephonyEnabled() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_VT_IMS_ENABLED, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_VT_IMS_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_VT_IMS_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_VT_IMS_ENABLED
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_VT_IMS_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getVideoTelephonyEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetWifiCallingEnabled() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_WFC_IMS_ENABLED, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_WFC_IMS_ENABLED
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getWifiCallingEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetWifiCallingMode() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_WFC_IMS_MODE, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_MODE, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_MODE, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_WFC_IMS_MODE
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_MODE, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getWifiCallingMode()).isEqualTo(0);
    }

    @Test
    public void testSetGetWifiCallingModeForRoaming() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_WFC_IMS_ROAMING_MODE, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_ROAMING_MODE, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("2");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_WFC_IMS_ROAMING_MODE, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_WFC_IMS_ROAMING_MODE
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_WFC_IMS_ROAMING_MODE, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getWifiCallingModeForRoaming()).isEqualTo(0);
    }

    @Test
    public void testSetGetEnabledMobileDataPolicies() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(FAKE_MOBILE_DATA_POLICY1);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_ENABLED_MOBILE_DATA_POLICIES
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_ENABLED_MOBILE_DATA_POLICIES, FAKE_MOBILE_DATA_POLICY2);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getEnabledMobileDataPolicies()).isEqualTo(FAKE_MOBILE_DATA_POLICY2);
    }

    @Test
    public void testSetGetRcsUceEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_IMS_RCS_UCE_ENABLED
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_IMS_RCS_UCE_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getRcsUceEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetCrossSimCallingEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_CROSS_SIM_CALLING_ENABLED
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_CROSS_SIM_CALLING_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getCrossSimCallingEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetRcsConfig() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_RCS_CONFIG, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_RCS_CONFIG, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(Base64.encodeToString(FAKE_RCS_CONFIG1, Base64.DEFAULT));

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_RCS_CONFIG, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_RCS_CONFIG
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_RCS_CONFIG,
                Base64.encodeToString(FAKE_RCS_CONFIG2, Base64.DEFAULT));
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getRcsConfig()).isEqualTo(FAKE_RCS_CONFIG2);
    }

    @Test
    public void testSetGetDeviceToDeviceStatusSharingPreference() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_D2D_STATUS_SHARING, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_D2D_STATUS_SHARING, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_D2D_STATUS_SHARING, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_D2D_STATUS_SHARING
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_D2D_STATUS_SHARING, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getDeviceToDeviceStatusSharingPreference()).isEqualTo(0);
    }

    @Test
    public void testSetGetVoImsOptInEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_VOIMS_OPT_IN_STATUS, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_VOIMS_OPT_IN_STATUS, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_VOIMS_OPT_IN_STATUS, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_VOIMS_OPT_IN_STATUS
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_VOIMS_OPT_IN_STATUS, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getVoImsOptInEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetGetDeviceToDeviceStatusSharingContacts() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_D2D_STATUS_SHARING_SELECTED_CONTACTS, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_D2D_STATUS_SHARING_SELECTED_CONTACTS, CALLING_PACKAGE,
                CALLING_FEATURE)).isEqualTo(FAKE_CONTACT1);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_D2D_STATUS_SHARING_SELECTED_CONTACTS,
                        FAKE_CONTACT2));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_D2D_STATUS_SHARING_SELECTED_CONTACTS
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_D2D_STATUS_SHARING_SELECTED_CONTACTS, FAKE_CONTACT2);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getDeviceToDeviceStatusSharingContacts()).isEqualTo(FAKE_CONTACT2);
    }

    @Test
    public void testSetGetNrAdvancedCallingEnabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("1");

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED, "0"));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // COLUMN_NR_ADVANCED_CALLING_ENABLED
        mSubscriptionManagerServiceUT.setSubscriptionProperty(1,
                SimInfo.COLUMN_NR_ADVANCED_CALLING_ENABLED, "0");
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                .getNrAdvancedCallingEnabled()).isEqualTo(0);
    }

    @Test
    public void testSetSubscriptionPropertyInvalidField() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        assertThrows(IllegalArgumentException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, "hahahaha", "0"));
    }

    @Test
    public void testGetNonAccessibleFields() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        Field field = SubscriptionManagerService.class.getDeclaredField(
                "DIRECT_ACCESS_SUBSCRIPTION_COLUMNS");
        field.setAccessible(true);
        Set<String> accessibleColumns = (Set<String>) field.get(null);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        for (String column : SimInfo.getAllColumns()) {
            if (accessibleColumns.contains(column)) {
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1, column,
                        CALLING_PACKAGE, CALLING_FEATURE);
            } else {
                assertThrows(SecurityException.class, () ->
                        mSubscriptionManagerServiceUT.getSubscriptionProperty(1, column,
                                CALLING_PACKAGE, CALLING_FEATURE));
            }
        }
    }

    @Test
    public void testSyncToGroup() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.createSubscriptionGroup(new int[]{1, 2}, CALLING_PACKAGE);

        mSubscriptionManagerServiceUT.syncGroupedSetting(1);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2).getIconTint())
                .isEqualTo(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                        .getIconTint());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2).getDataRoaming())
                .isEqualTo(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1)
                        .getDataRoaming());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getEnhanced4GModeEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getEnhanced4GModeEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getVideoTelephonyEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getVideoTelephonyEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getWifiCallingEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getWifiCallingEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getWifiCallingMode()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getWifiCallingMode());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getWifiCallingModeForRoaming()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getWifiCallingModeForRoaming());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getWifiCallingEnabledForRoaming()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getWifiCallingEnabledForRoaming());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getEnabledMobileDataPolicies()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getEnabledMobileDataPolicies());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getUiccApplicationsEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getUiccApplicationsEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getRcsUceEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getRcsUceEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getCrossSimCallingEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getCrossSimCallingEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getRcsConfig()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getRcsConfig());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getDeviceToDeviceStatusSharingPreference()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getDeviceToDeviceStatusSharingPreference());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getVoImsOptInEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getVoImsOptInEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getDeviceToDeviceStatusSharingContacts()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getDeviceToDeviceStatusSharingContacts());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getNrAdvancedCallingEnabled()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getNrAdvancedCallingEnabled());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2)
                .getUserId()).isEqualTo(mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1).getUserId());
    }

    @Test
    public void testRemoveSubInfo() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .removeSubInfo(FAKE_ICCID1, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_ICCID1,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)).isEqualTo(0);
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_ICCID2,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)).isEqualTo(0);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).isEmpty()).isTrue();
    }

    @Test
    public void testUserUnlockUpdateEmbeddedSubscriptions() {
        doReturn(true).when(mUiccSlot).isEuicc();
        doReturn(1).when(mUiccController).convertToPublicCardId(FAKE_ICCID1);
        doReturn(new UiccSlot[]{mUiccSlot}).when(mUiccController).getUiccSlots();

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC1, FAKE_MNC1, null, null, null,
                        null, FAKE_CARRIER_ID1, FAKE_CARRIER_ID1))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));

        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
        processAllMessages();

        verify(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_CARRIER);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC1);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC1);
        assertThat(subInfo.getProfileClass()).isEqualTo(
                SubscriptionManager.PROFILE_CLASS_OPERATIONAL);
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.isRemovableEmbedded()).isFalse();
        assertThat(subInfo.getNativeAccessRules()).isEqualTo(FAKE_NATIVE_ACCESS_RULES1);
    }
}
