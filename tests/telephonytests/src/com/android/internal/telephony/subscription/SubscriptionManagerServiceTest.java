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

import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_ID2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CARRIER_NAME2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CONTACT1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_CONTACT2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_COUNTRY_CODE2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_DEFAULT_CARD_NAME;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_EHPLMNS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_HPLMNS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_IMSI1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MAC_ADDRESS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_MAC_ADDRESS2;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.annotation.NonNull;
import android.app.AppOpsManager;
import android.app.PropertyInvalidatedCache;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.Base64;

import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.SubscriptionProvider;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionManagerServiceCallback;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionMap;
import com.android.internal.telephony.uicc.IccCardStatus;
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

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SubscriptionManagerServiceTest extends TelephonyTest {

    private static final String CALLING_PACKAGE = "calling_package";

    private static final String CALLING_FEATURE = "calling_feature";

    private static final String GROUP_UUID = "6adbc864-691c-45dc-b698-8fc9a2176fae";

    private SubscriptionManagerService mSubscriptionManagerServiceUT;

    private final SubscriptionProvider mSubscriptionProvider = new SubscriptionProvider();

    private static final UserHandle FAKE_USER_HANDLE = new UserHandle(12);

    private static final UserHandle FAKE_MANAGED_PROFILE_USER_HANDLE = new UserHandle(13);

    // mocked
    private SubscriptionManagerServiceCallback mMockedSubscriptionManagerServiceCallback;
    private EuiccController mEuiccController;

    private Set<Integer> mActiveSubs = new ArraySet<>();

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() throws Exception {
        logd("SubscriptionManagerServiceTest +Setup!");
        super.setUp(getClass().getSimpleName());

        // Dual-SIM configuration
        mPhones = new Phone[] {mPhone, mPhone2};
        replaceInstance(PhoneFactory.class, "sPhones", null, mPhones);
        doReturn(FAKE_PHONE_NUMBER1).when(mPhone).getLine1Number();
        doReturn(2).when(mTelephonyManager).getActiveModemCount();
        doReturn(2).when(mTelephonyManager).getSupportedModemCount();
        doReturn(mUiccProfile).when(mPhone2).getIccCard();
        doReturn(new UiccSlot[]{mUiccSlot}).when(mUiccController).getUiccSlots();

        mContextFixture.putBooleanResource(com.android.internal.R.bool
                .config_subscription_database_async_update, true);
        mContextFixture.putIntArrayResource(com.android.internal.R.array.sim_colors, new int[0]);
        mContextFixture.putResource(com.android.internal.R.string.default_card_name,
                FAKE_DEFAULT_CARD_NAME);

        mContextFixture.addSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC);
        setupMocksForTelephonyPermissions(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        PropertyInvalidatedCache.disableForCurrentProcess("cache_key.is_compat_change_enabled");

        doReturn(true).when(mTelephonyManager).isVoiceCapable();
        mEuiccController = Mockito.mock(EuiccController.class);
        replaceInstance(EuiccController.class, "sInstance", null, mEuiccController);
        mMockedSubscriptionManagerServiceCallback = Mockito.mock(
                SubscriptionManagerServiceCallback.class);
        doReturn(FAKE_ICCID1).when(mUiccCard).getCardId();
        doReturn(FAKE_ICCID1).when(mUiccPort).getIccId();
        doReturn(true).when(mUiccSlot).isActive();
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));
        doReturn(FAKE_ICCID2).when(mUiccController).convertToCardString(eq(2));

        doReturn(new int[0]).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);

        mSubscriptionManagerServiceUT = new SubscriptionManagerService(mContext, Looper.myLooper());

        monitorTestableLooper(new TestableLooper(getBackgroundHandler().getLooper()));
        monitorTestableLooper(new TestableLooper(getSubscriptionDatabaseManager().getLooper()));

        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(mMockedSubscriptionManagerServiceCallback).invokeFromExecutor(any(Runnable.class));

        mSubscriptionManagerServiceUT.registerCallback(mMockedSubscriptionManagerServiceCallback);
        processAllFutureMessages();

        // Revoke all permissions.
        mContextFixture.removeCallingOrSelfPermission(ContextFixture.PERMISSION_ENABLE_ALL);
        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).noteOpNoThrow(anyString(),
                anyInt(), nullable(String.class), nullable(String.class), nullable(String.class));
        setIdentifierAccess(false);
        setPhoneNumberAccess(PackageManager.PERMISSION_DENIED);

        doReturn(true).when(mUserManager)
                .isManagedProfile(eq(FAKE_MANAGED_PROFILE_USER_HANDLE.getIdentifier()));

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

    private SubscriptionDatabaseManager getSubscriptionDatabaseManager() throws Exception {
        Field field = SubscriptionManagerService.class.getDeclaredField(
                "mSubscriptionDatabaseManager");
        field.setAccessible(true);
        return (SubscriptionDatabaseManager) field.get(mSubscriptionManagerServiceUT);
    }

    /**
     * Insert the subscription info to the database. This is an instant insertion method. For real
     * insertion sequence please use {@link #testInsertNewSim()}.
     *
     * @param subInfo The subscription to be inserted.
     * @return The new sub id.
     */
    private int insertSubscription(@NonNull SubscriptionInfoInternal subInfo) {
        try {
            mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
            subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                    .setId(SubscriptionManager.INVALID_SUBSCRIPTION_ID).build();
            int subId = getSubscriptionDatabaseManager().insertSubscriptionInfo(subInfo);

            // Insertion is sync, but the onSubscriptionChanged callback is handled by the handler.
            processAllMessages();

            Field field = SubscriptionManagerService.class.getDeclaredField("mSlotIndexToSubId");
            field.setAccessible(true);
            SubscriptionMap<Integer, Integer> map = (SubscriptionMap<Integer, Integer>)
                    field.get(mSubscriptionManagerServiceUT);
            Class[] cArgs = new Class[2];
            cArgs[0] = Object.class;
            cArgs[1] = Object.class;

            if (subInfo.getSimSlotIndex() >= 0) {
                // Change the slot -> subId mapping
                map.put(subInfo.getSimSlotIndex(), subId);
            }

            mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
            processAllMessages();
            verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));
            Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);

            if (subInfo.getSimSlotIndex() >= 0) {
                mActiveSubs.add(subId);

                // Change the SIM state
                field = SubscriptionManagerService.class.getDeclaredField("mSimState");
                field.setAccessible(true);
                Object array = field.get(mSubscriptionManagerServiceUT);
                Array.set(array, subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_LOADED);
            } else {
                mActiveSubs.remove(subId);
            }

            doReturn(mActiveSubs.stream().mapToInt(i->i).toArray()).when(mSubscriptionManager)
                    .getCompleteActiveSubscriptionIdList();
            return subId;
        } catch (Exception e) {
            fail("Failed to insert subscription. e=" + e);
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    @Test
    public void testBroadcastOnInitialization() {
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(3)).sendBroadcastAsUser(
                captorIntent.capture(), eq(UserHandle.ALL));
        assertThat(captorIntent.getAllValues().stream().map(Intent::getAction).toList())
                .containsExactly(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED,
                        TelephonyIntents.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED,
                        SubscriptionManager.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED);
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
        clearInvocations(mContext);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setDefaultVoiceSubId(1));

        // Grant MODIFY_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultVoiceSubId(1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultVoiceSubId()).isEqualTo(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_CALL_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcastAsUser(
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
        clearInvocations(mContext);
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
        assertThat(mSubscriptionManagerServiceUT.getDefaultDataSubId()).isEqualTo(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(2)).sendBroadcastAsUser(
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
        clearInvocations(mContext);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setDefaultSmsSubId(1));

        // Grant MODIFY_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultSmsSubId(1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultSmsSubId()).isEqualTo(1);

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.MULTI_SIM_SMS_SUBSCRIPTION)).isEqualTo(1);
        ArgumentCaptor<Intent> captorIntent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(captorIntent.capture(), eq(UserHandle.ALL));

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
        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX).build());
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();

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
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));
        doReturn(FAKE_ICCID2).when(mUiccController).convertToCardString(eq(2));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1, 2), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getPortIndex()).isEqualTo(TelephonyManager.INVALID_PORT_INDEX);
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
        // Downloaded esim profile should contain proper cardId
        assertThat(subInfo.getCardId()).isEqualTo(1);
        assertThat(subInfo.getCardString()).isEqualTo(FAKE_ICCID1);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(2);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getPortIndex()).isEqualTo(TelephonyManager.INVALID_PORT_INDEX);
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
        // Downloaded esim profile should contain proper cardId
        assertThat(subInfo.getCardId()).isEqualTo(2);
        assertThat(subInfo.getCardString()).isEqualTo(FAKE_ICCID2);
    }

    @Test
    public void testUpdateEmbeddedSubscriptionsNullResult() {
        // Grant READ_PHONE_STATE permission.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);

        doReturn(null).when(mEuiccController).blockingGetEuiccProfileInfoList(anyInt());

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1, 2), null);
        processAllMessages();

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getAllSubInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfoList).isEmpty();
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
        doReturn(true).when(mEuiccManager).isEnabled();
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        // FAKE_SUBSCRIPTION_INFO2 is a not eSIM. So the list should be empty.
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEmpty();

        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        doReturn(false).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isNull();

        doReturn(false).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));

        doReturn(true).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEmpty();

        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEqualTo(List.of(new SubscriptionInfoInternal.Builder(
                        FAKE_SUBSCRIPTION_INFO1).setId(2).build().toSubscriptionInfo()));
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
    public void testGetSubscriptionUserHandleUnknownSubscription() {
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);

        // getSubscriptionUserHandle() returns null when subscription is not available on the device
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionUserHandle(10))
                .isEqualTo(null);

        mContextFixture.removeCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
    }

    @Test
    public void testIsSubscriptionAssociatedWithUser() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

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

        // Work profile is not associated with any subscription
        associatedSubInfoList = mSubscriptionManagerServiceUT
                .getSubscriptionInfoListAssociatedWithUser(FAKE_MANAGED_PROFILE_USER_HANDLE);
        assertThat(associatedSubInfoList.size()).isEqualTo(0);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(false);
    }

    @Test
    public void testSetUsageSetting() {
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
        assertThat(subInfo.getNumber()).isEqualTo(FAKE_PHONE_NUMBER2);
    }

    @Test
    public void testSetOpportunistic() {
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

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getOpportunisticSubscriptions(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();

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

        ParcelUuid newUuid = ParcelUuid.fromString(GROUP_UUID);
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
        verify(mMockedSubscriptionManagerServiceCallback).onUiccApplicationsEnabledChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.areUiccApplicationsEnabled()).isFalse();

        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(false, 1);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback, never()).onSubscriptionChanged(eq(1));
        verify(mMockedSubscriptionManagerServiceCallback, never())
                .onUiccApplicationsEnabledChanged(eq(1));
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
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_ICCID2,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM)).isEqualTo(true);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).isEmpty()).isTrue();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();
    }

    @Test
    public void testUserUnlockUpdateEmbeddedSubscriptions() {
        doReturn(true).when(mUiccSlot).isEuicc();
        doReturn(1).when(mUiccController).convertToPublicCardId(FAKE_ICCID1);
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());

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

        mContext.sendBroadcast(new Intent(Intent.ACTION_USER_UNLOCKED));
        processAllMessages();

        verify(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getPortIndex()).isEqualTo(TelephonyManager.INVALID_PORT_INDEX);
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

    @Test
    public void testInsertNewSim() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        doReturn(FAKE_IMSI1).when(mTelephonyManager).getSubscriberId();
        doReturn(FAKE_MCC1 + FAKE_MNC1).when(mTelephonyManager).getSimOperatorNumeric(anyInt());
        doReturn(FAKE_EHPLMNS1.split(",")).when(mSimRecords).getEhplmns();
        doReturn(FAKE_HPLMNS1.split(",")).when(mSimRecords).getPlmnsFromHplmnActRecord();
        doReturn(0).when(mUiccSlot).getPortIndexFromIccId(anyString());
        doReturn(false).when(mUiccSlot).isEuicc();
        doReturn(1).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID1));

        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_READY, null, null);
        processAllMessages();

        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_LOADED, null, null);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getSubId(0)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(1)).isEqualTo(0);
        assertThat(mSubscriptionManagerServiceUT.getPhoneId(1)).isEqualTo(0);

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getDisplayName()).isEqualTo("CARD 1");

        mSubscriptionManagerServiceUT.setCarrierId(1, FAKE_CARRIER_ID1);
        mSubscriptionManagerServiceUT.setDisplayNameUsingSrc(FAKE_CARRIER_NAME1, 1,
                SubscriptionManager.NAME_SOURCE_SIM_SPN);
        mSubscriptionManagerServiceUT.setCarrierName(1, FAKE_CARRIER_NAME1);

        subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getPortIndex()).isEqualTo(0);
        assertThat(subInfo.isEmbedded()).isFalse();
        assertThat(subInfo.getCarrierId()).isEqualTo(FAKE_CARRIER_ID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_SIM_SPN);
        assertThat(subInfo.getCarrierName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.isOpportunistic()).isFalse();
        assertThat(subInfo.getNumber()).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC1);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC1);
        assertThat(subInfo.getEhplmns()).isEqualTo(FAKE_EHPLMNS1);
        assertThat(subInfo.getHplmns()).isEqualTo(FAKE_HPLMNS1);
        assertThat(subInfo.getCardString()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getCardId()).isEqualTo(1);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(subInfo.areUiccApplicationsEnabled()).isTrue();
        assertThat(subInfo.getAllowedNetworkTypesForReasons()).isEqualTo("user="
                + RadioAccessFamily.getRafFromNetworkType(RILConstants.PREFERRED_NETWORK_MODE));
    }

    @Test
    public void testGroupDisable() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setGroupUuid(FAKE_UUID1).build());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).isGroupDisabled())
                .isFalse();
    }

    @Test
    public void testGetPhoneNumber() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        testSetPhoneNumber();
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(FAKE_PHONE_NUMBER2);
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, CALLING_PACKAGE, CALLING_FEATURE))
                .isEmpty();
    }

    @Test
    public void testGetPhoneNumberFromInactiveSubscription() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        testInactiveSimRemoval();

        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        assertThat(subId).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).hasLength(1);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(CALLING_PACKAGE,
                CALLING_FEATURE)).hasSize(2);

        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(1,
                CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER2);
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_UICC, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(FAKE_PHONE_NUMBER2);
    }

    @Test
    public void testEsimActivation() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
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

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.isActive()).isFalse();
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);

        Mockito.clearInvocations(mEuiccController);

        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_ABSENT, null, null);
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_UNKNOWN, null, null);
        processAllMessages();

        doReturn(FAKE_IMSI1).when(mTelephonyManager).getSubscriberId();
        doReturn(FAKE_MCC1 + FAKE_MNC1).when(mTelephonyManager).getSimOperatorNumeric(anyInt());
        doReturn(FAKE_PHONE_NUMBER1).when(mPhone2).getLine1Number();
        doReturn(FAKE_EHPLMNS1.split(",")).when(mSimRecords).getEhplmns();
        doReturn(FAKE_HPLMNS1.split(",")).when(mSimRecords).getPlmnsFromHplmnActRecord();
        doReturn(0).when(mUiccSlot).getPortIndexFromIccId(anyString());
        doReturn(true).when(mUiccSlot).isEuicc();
        doReturn(1).when(mUiccController).convertToPublicCardId(eq(FAKE_ICCID1));

        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_READY, null, null);
        processAllMessages();

        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_LOADED, null, null);
        processAllMessages();

        // Verify if SMSVC is refreshing eSIM profiles when moving into READY state.
        verify(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfoList).hasSize(1);
        assertThat(subInfoList.get(0).getSimSlotIndex()).isEqualTo(1);
        assertThat(subInfoList.get(0).getSubscriptionId()).isEqualTo(1);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1);
        assertThat(subInfo.isActive()).isTrue();
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(1);
        assertThat(subInfo.getPortIndex()).isEqualTo(0);
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.getCarrierId()).isEqualTo(TelephonyManager.UNKNOWN_CARRIER_ID);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.isOpportunistic()).isFalse();
        assertThat(subInfo.getNumber()).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC1);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC1);
        assertThat(subInfo.getEhplmns()).isEqualTo(FAKE_EHPLMNS1);
        assertThat(subInfo.getHplmns()).isEqualTo(FAKE_HPLMNS1);
        assertThat(subInfo.getCardString()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getCardId()).isEqualTo(1);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
    }

    @Test
    public void testDeleteEsim() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        // pSIM with ICCID2
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(0).build());

        // eSIM with ICCID1
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

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_READY, null, null);

        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_LOADED, null, null);
        processAllMessages();

        // Now we should have two subscriptions in the database. One for pSIM, one for eSIM.
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1).isEmbedded()).isFalse();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).isEmbedded()).isTrue();

        // Delete the eSIM. blockingGetEuiccProfileInfoList will return an empty list.
        result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[0], false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn("").when(mUiccPort).getIccId();
        doReturn(TelephonyManager.INVALID_PORT_INDEX)
                .when(mUiccSlot).getPortIndexFromIccId(anyString());

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_NOT_READY, null, null);

        processAllMessages();

        // The original pSIM is still pSIM
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1).isEmbedded()).isFalse();
        // The original eSIM becomes removed pSIM ¯\_(ツ)_/¯
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).isEmbedded()).isFalse();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).getPortIndex())
                .isEqualTo(TelephonyManager.INVALID_PORT_INDEX);
    }

    @Test
    public void testEsimSwitch() {
        setIdentifierAccess(true);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID2)
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
        doReturn(FAKE_ICCID2).when(mUiccCard).getCardId();
        doReturn(FAKE_ICCID2).when(mUiccController).convertToCardString(eq(1));
        doReturn(FAKE_ICCID2).when(mUiccPort).getIccId();

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_READY, null, null);
        processAllMessages();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_LOADED, null, null);
        processAllMessages();

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE);

        assertThat(subInfoList).hasSize(1);
        assertThat(subInfoList.get(0).isActive()).isTrue();
        assertThat(subInfoList.get(0).getSubscriptionId()).isEqualTo(2);
        assertThat(subInfoList.get(0).getIccId()).isEqualTo(FAKE_ICCID2);

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        assertThat(subInfo.getPortIndex()).isEqualTo(TelephonyManager.DEFAULT_PORT_INDEX);
    }

    @Test
    public void testDump() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        final StringWriter stringWriter = new StringWriter();
        assertThrows(SecurityException.class, ()
                -> mSubscriptionManagerServiceUT.dump(new FileDescriptor(),
                new PrintWriter(stringWriter), null));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.DUMP);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.dump(new FileDescriptor(), new PrintWriter(stringWriter),
                null);
        assertThat(stringWriter.toString().length()).isGreaterThan(0);
    }

    @Test
    public void testOnSubscriptionChanged() {
        CountDownLatch latch = new CountDownLatch(1);
        SubscriptionManagerServiceCallback callback =
                new SubscriptionManagerServiceCallback(Runnable::run) {
                    @Override
                    public void onSubscriptionChanged(int subId) {
                        latch.countDown();
                        logd("testOnSubscriptionChanged: onSubscriptionChanged");
                    }
                };
        mSubscriptionManagerServiceUT.registerCallback(callback);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        processAllMessages();
        assertThat(latch.getCount()).isEqualTo(0);
    }

    @Test
    public void testOnUiccApplicationsEnabled() {
        CountDownLatch latch = new CountDownLatch(1);
        Executor executor = Runnable::run;
        SubscriptionManagerServiceCallback callback =
                new SubscriptionManagerServiceCallback(executor) {
                    @Override
                    public void onUiccApplicationsEnabledChanged(int subId) {
                        latch.countDown();
                        logd("testOnSubscriptionChanged: onUiccApplicationsEnabledChanged");
                    }
                };
        assertThat(callback.getExecutor()).isEqualTo(executor);
        mSubscriptionManagerServiceUT.registerCallback(callback);
        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(false, subId);
        processAllMessages();
        assertThat(latch.getCount()).isEqualTo(0);

        mSubscriptionManagerServiceUT.unregisterCallback(callback);
        // without override. Nothing should happen.
        callback = new SubscriptionManagerServiceCallback(Runnable::run);
        mSubscriptionManagerServiceUT.registerCallback(callback);
        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(true, subId);
        processAllMessages();
    }

    @Test
    public void testDeactivatePsim() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        testInsertNewSim();

        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(false, 1);
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_NOT_READY, null, null);

        processAllMessages();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.isActive()).isFalse();
        assertThat(subInfo.areUiccApplicationsEnabled()).isFalse();
    }

    @Test
    public void testRemoteSim() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);

        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();

        setIdentifierAccess(true);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS2, FAKE_CARRIER_NAME2,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).get(0).getIccId()).isEqualTo(FAKE_MAC_ADDRESS2);
    }

    @Test
    public void testRemoveSubscriptionsFromGroup() {
        testAddSubscriptionsIntoGroup();

        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThrows(SecurityException.class, ()
                -> mSubscriptionManagerServiceUT.removeSubscriptionsFromGroup(new int[]{2},
                ParcelUuid.fromString(GROUP_UUID), CALLING_PACKAGE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        assertThrows(IllegalArgumentException.class, ()
                -> mSubscriptionManagerServiceUT.removeSubscriptionsFromGroup(new int[]{3},
                ParcelUuid.fromString(GROUP_UUID), CALLING_PACKAGE));

        assertThrows(IllegalArgumentException.class, ()
                -> mSubscriptionManagerServiceUT.removeSubscriptionsFromGroup(new int[]{2},
                ParcelUuid.fromString("55911c5b-83ed-419d-8f9b-4e027cf09305"), CALLING_PACKAGE));

        mSubscriptionManagerServiceUT.removeSubscriptionsFromGroup(new int[]{2},
                ParcelUuid.fromString(GROUP_UUID), CALLING_PACKAGE);

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(2);
        assertThat(subInfo.getGroupUuid()).isEmpty();
        assertThat(subInfo.getGroupOwner()).isEmpty();
    }

    @Test
    public void testUpdateSimStateForInactivePort() {
        testSetUiccApplicationsEnabled();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimStateForInactivePort(0, null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.areUiccApplicationsEnabled()).isTrue();
    }

    @Test
    public void testInactiveSimInserted() {
        doReturn(0).when(mUiccSlot).getPortIndexFromIccId(eq(FAKE_ICCID1));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimStateForInactivePort(-1, FAKE_ICCID1);
        processAllMessages();

        // Make sure the inactive SIM's information was inserted.
        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo("CARD 1");
        assertThat(subInfo.getPortIndex()).isEqualTo(0);
    }

    @Test
    public void testRestoreAllSimSpecificSettingsFromBackup() throws Exception {
        assertThrows(SecurityException.class, ()
                -> mSubscriptionManagerServiceUT.restoreAllSimSpecificSettingsFromBackup(
                        new byte[0]));
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);


        // getSubscriptionDatabaseManager().setWifiCallingEnabled(1, 0);

        // Simulate restoration altered the database directly.
        ContentValues cvs = new ContentValues();
        cvs.put(SimInfo.COLUMN_WFC_IMS_ENABLED, 0);
        mSubscriptionProvider.update(Uri.withAppendedPath(SimInfo.CONTENT_URI, "1"), cvs, null,
                null);

        // Setting this to false to prevent database reload.
        mSubscriptionProvider.setRestoreDatabaseChanged(false);
        mSubscriptionManagerServiceUT.restoreAllSimSpecificSettingsFromBackup(
                new byte[0]);

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        // Since reload didn't happen, WFC should remains enabled.
        assertThat(subInfo.getWifiCallingEnabled()).isEqualTo(1);

        // Now the database reload should happen
        mSubscriptionProvider.setRestoreDatabaseChanged(true);
        mSubscriptionManagerServiceUT.restoreAllSimSpecificSettingsFromBackup(
                new byte[0]);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1);
        // Since reload didn't happen, WFC should remains enabled.
        assertThat(subInfo.getWifiCallingEnabled()).isEqualTo(0);
    }

    @Test
    public void testSubscriptionMap() {
        SubscriptionMap<Integer, Integer> map = new SubscriptionMap<>();
        map.put(1, 1);
        assertThat(map.get(1)).isEqualTo(1);
        map.put(0, 2);
        assertThat(map.get(0)).isEqualTo(2);
        map.remove(1);
        assertThat(map.get(1)).isNull();
        map.clear();
        assertThat(map).hasSize(0);
    }

    @Test
    public void testSimNotReady() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_NOT_READY, null, null);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
    }

    @Test
    public void testSimNotReadyBySimDeactivate() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_NOT_READY, null, null);
        doReturn(true).when(mUiccProfile).isEmptyProfile();
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
    }

    @Test
    public void testInactiveSimRemoval() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        doReturn(FAKE_ICCID2).when(mUiccSlot).getIccId(0);
        doReturn(IccCardStatus.CardState.CARDSTATE_PRESENT).when(mUiccSlot).getCardState();

        mSubscriptionManagerServiceUT.setUiccApplicationsEnabled(false, 1);
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_NOT_READY, null, null);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1)
                .areUiccApplicationsEnabled()).isFalse();
        assertThat(mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).hasSize(1);

        // Now remove the SIM
        doReturn(null).when(mUiccSlot).getIccId(0);
        doReturn(IccCardStatus.CardState.CARDSTATE_ABSENT).when(mUiccSlot).getCardState();
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_ABSENT, null, null);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
        // UICC should be re-enabled again for next re-insertion.
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1)
                .areUiccApplicationsEnabled()).isTrue();
        assertThat(mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();
    }

    @Test
    public void testEmbeddedProfilesUpdateFailed() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_MUST_DEACTIVATE_SIM, null, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        // The existing subscription should not be altered if the previous update failed.
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1))
                .isEqualTo(FAKE_SUBSCRIPTION_INFO1);

        EuiccProfileInfo profileInfo = new EuiccProfileInfo.Builder(FAKE_ICCID2)
                .setIccid(FAKE_ICCID2)
                .setNickname(FAKE_CARRIER_NAME2)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC2, FAKE_MNC2, null, null, null,
                        null, FAKE_CARRIER_ID2, FAKE_CARRIER_ID2))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES2)))
                .build();
        result = new GetEuiccProfileInfoListResult(EuiccService.RESULT_OK,
                new EuiccProfileInfo[]{profileInfo}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));

        // Update for the 2nd time.
        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        // The previous subscription should be marked as non-embedded.
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(1).isEmbedded())
                .isEqualTo(false);

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).getIccId())
                .isEqualTo(FAKE_ICCID2);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).isEmbedded())
                .isEqualTo(true);
    }


    @Test
    public void testNonNullSubInfoBuilderFromEmbeddedProfile() {
        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1) //can't build profile with null iccid.
                .setNickname(null) //nullable
                .setServiceProviderName(null) //nullable
                .setProfileName(null) //nullable
                .setCarrierIdentifier(null) //nullable
                .setUiccAccessRule(null) //nullable
                .build();

        EuiccProfileInfo profileInfo2 = new EuiccProfileInfo.Builder(FAKE_ICCID2)
                .setIccid(FAKE_ICCID2) //impossible to build profile with null iccid.
                .setNickname(null) //nullable
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC2, FAKE_MNC2, null, null, null,
                        null, FAKE_CARRIER_ID2, FAKE_CARRIER_ID2)) //not allow null mcc/mnc.
                .setUiccAccessRule(null) //nullable
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        result = new GetEuiccProfileInfoListResult(EuiccService.RESULT_OK,
                new EuiccProfileInfo[]{profileInfo2}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(2));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1, 2), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(1);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo("CARD 1");
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_UNKNOWN);
        assertThat(subInfo.getMcc()).isEqualTo("");
        assertThat(subInfo.getMnc()).isEqualTo("");
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.isRemovableEmbedded()).isFalse();
        assertThat(subInfo.getNativeAccessRules()).isEqualTo(new byte[]{});

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(2);
        assertThat(subInfo.getSubscriptionId()).isEqualTo(2);
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID2);
        assertThat(subInfo.getDisplayName()).isEqualTo("CARD 2");
        assertThat(subInfo.getDisplayNameSource()).isEqualTo(
                SubscriptionManager.NAME_SOURCE_UNKNOWN);
        assertThat(subInfo.getMcc()).isEqualTo(FAKE_MCC2);
        assertThat(subInfo.getMnc()).isEqualTo(FAKE_MNC2);
        assertThat(subInfo.isEmbedded()).isTrue();
        assertThat(subInfo.isRemovableEmbedded()).isFalse();
        assertThat(subInfo.getNativeAccessRules()).isEqualTo(new byte[]{});
    }
}
