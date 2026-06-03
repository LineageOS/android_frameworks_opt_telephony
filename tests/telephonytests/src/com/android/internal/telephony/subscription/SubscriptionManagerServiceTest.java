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
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID3;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_ICCID4;
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
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_BARRED_PLMNS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_DATA_PLAN_PLMNS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_DATA_SERVICE_POLICY1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_PLMNS1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_SERVICE_TYPE_MAP1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_ENTITLEMENT_VOICE_SERVICE_POLICY1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SATELLITE_IS_ONLY_NTN_DISABLED;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SUBSCRIPTION_INFO1;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_SUBSCRIPTION_INFO2;
import static com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.FAKE_UUID1;
import static com.android.internal.telephony.util.TelephonyUtils.TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.PropertyInvalidatedCache;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.SimInfo;
import android.service.carrier.CarrierIdentifier;
import android.service.euicc.EuiccProfileInfo;
import android.service.euicc.EuiccService;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telephony.CarrierConfigManager;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;

import com.android.internal.R;
import com.android.internal.telephony.ContextFixture;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyTest;
import com.android.internal.telephony.euicc.EuiccController;
import com.android.internal.telephony.subscription.SubscriptionDatabaseManagerTest.SubscriptionProvider;
import com.android.internal.telephony.subscription.SubscriptionManagerService.BinderWrapper;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionManagerServiceCallback;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionMap;
import com.android.internal.telephony.subscription.SubscriptionManagerService.SubscriptionSet;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.UiccSlot;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    private static final SubscriptionInfoInternal FAKE_REMOTE_SIM1 =
            new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                    .setIccId(FAKE_MAC_ADDRESS1)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)
                    .setSimSlotIndex(SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)
                    .build();

    private static final SubscriptionInfoInternal FAKE_REMOTE_SIM2 =
            new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                    .setIccId(FAKE_MAC_ADDRESS2)
                    .setType(SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)
                    .setSimSlotIndex(SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)
                    .build();

    // mocked
    private SubscriptionManagerServiceCallback mMockedSubscriptionManagerServiceCallback;
    private EuiccController mEuiccController;
    private BinderWrapper mBinder;
    private Set<Integer> mActiveSubs = new ArraySet<>();

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

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
        doReturn(true).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        mBinder = Mockito.mock(BinderWrapper.class);
        doReturn(FAKE_USER_HANDLE).when(mBinder).getCallingUserHandle();
        replaceInstance(SubscriptionManagerService.class, "BINDER_WRAPPER", null, mBinder);

        doReturn(new int[0]).when(mSubscriptionManager).getCompleteActiveSubscriptionIdList();

        ((MockContentResolver) mContext.getContentResolver()).addProvider(
                Telephony.Carriers.CONTENT_URI.getAuthority(), mSubscriptionProvider);

        doReturn(mContext).when(mContext).createDeviceProtectedStorageContext();
        File tempDir = mTemporaryFolder.newFolder("telephony_tests");
        doReturn(tempDir).when(mContext).getFilesDir();

        mSubscriptionManagerServiceUT = new SubscriptionManagerService(mContext, Looper.myLooper(),
                mFeatureFlags);

        // Monitor both the service's background handler and the database manager's handler
        // to ensure all asynchronous operations are completed during tests.
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
            subInfo = new SubscriptionInfoInternal.Builder(subInfo)
                    .setId(SubscriptionManager.INVALID_SUBSCRIPTION_ID).build();
            int subId = getSubscriptionDatabaseManager().insertSubscriptionInfo(subInfo);

            // Insertion is sync, but the onSubscriptionChanged callback is handled by the handler.
            processAllMessages();

            Field field = SubscriptionManagerService.class.getDeclaredField("mSlotIndexToSubId");
            field.setAccessible(true);
            SubscriptionMap<Integer, Integer> map = (SubscriptionMap<Integer, Integer>)
                    field.get(mSubscriptionManagerServiceUT);

            field = SubscriptionManagerService.class.getDeclaredField("mRemoteSubIds");
            field.setAccessible(true);
            SubscriptionSet<Integer> set = (SubscriptionSet<Integer>)
                    field.get(mSubscriptionManagerServiceUT);

            if (subInfo.getSimSlotIndex() >= 0) {
                // Change the slot -> subId mapping
                map.put(subInfo.getSimSlotIndex(), subId);
            } else if (mFeatureFlags.remoteSimSubIdSet() && subInfo.getSimSlotIndex()
                    == SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB) {
                // Change the remote SIM subId set
                set.add(subId);
            }

            verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));
            Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);

            if (subInfo.getSimSlotIndex() >= 0) {
                mActiveSubs.add(subId);

                // Change the SIM state
                field = SubscriptionManagerService.class.getDeclaredField("mSimState");
                field.setAccessible(true);
                Object array = field.get(mSubscriptionManagerServiceUT);
                Array.set(array, subInfo.getSimSlotIndex(), TelephonyManager.SIM_STATE_LOADED);
            } else if (mFeatureFlags.remoteSimSubIdSet() && subInfo.getSimSlotIndex()
                    == SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB) {
                mActiveSubs.add(subId);
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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

        // Invalid slot index should trigger IllegalArgumentException
        assertThrows(IllegalArgumentException.class,
                () -> mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                        2, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetAdminOwned() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1, 0,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();
        String groupOwner = "test";

        mSubscriptionManagerServiceUT.setGroupOwner(1, groupOwner);

        SubscriptionInfoInternal subInfo =
                mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getGroupOwner()).isEqualTo(groupOwner);
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetGroupOwner_callerMissingpPermission_throws() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1, 0,
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();
        String groupOwner = "test";
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.setGroupOwner(1, groupOwner));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetPhoneNumber() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);

        // Replace field to set vendor API level to the one where the exceptions are enabled.
        replaceInstance(SubscriptionManagerService.class, "mVendorApiLevel",
                mSubscriptionManagerServiceUT, TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL);

        // Enabled ENABLE_FEATURE_MAPPING, telephony features are defined
        doReturn(true).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        try {
            mSubscriptionManagerServiceUT.setPhoneNumber(1,
                    SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, FAKE_PHONE_NUMBER2,
                    CALLING_PACKAGE, CALLING_FEATURE);
        } catch (UnsupportedOperationException e) {
            fail("Not expect exception " + e.getMessage());
        }

        // Telephony features is not defined, expect UnsupportedOperationException.
        doReturn(false).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.setPhoneNumber(1,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, FAKE_PHONE_NUMBER2,
                        CALLING_PACKAGE, CALLING_FEATURE));

        // Resume Telephony feature for the next test
        doReturn(true).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));

        // Test for PHONE_NUMBER_SOURCE_TS43
        String phoneNumberFromTs43 = "1234567890";

        mSubscriptionManagerServiceUT.setPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_TS43, phoneNumberFromTs43,
                CALLING_PACKAGE, CALLING_FEATURE);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getNumberFromTs43()).isEqualTo(phoneNumberFromTs43);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));
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
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
        verify(mProxyController).setRadioCapability(any());

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

        verify(mMockedSubscriptionManagerServiceCallback).onDefaultDataSubscriptionChanged(eq(1));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSingleSimSetDefaultDataSubId() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(1).when(mProxyController).getMinRafSupported();
        doReturn(2).when(mProxyController).getMaxRafSupported();
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setDefaultDataSubId(1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultDataSubId()).isEqualTo(1);
        ArgumentCaptor<RadioAccessFamily[]> rafsCaptor = ArgumentCaptor.forClass(
                RadioAccessFamily[].class);
        verify(mProxyController).setRadioCapability(rafsCaptor.capture());
        RadioAccessFamily[] rafs = (RadioAccessFamily[]) rafsCaptor.getValue();
        assertThat(rafs[0].getRadioAccessFamily()).isEqualTo(1);
        assertThat(rafs[1].getRadioAccessFamily()).isEqualTo(2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0).getIccId()).isEmpty();
        assertThat(subInfos.get(0).getCardString()).isEmpty();
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(0).getGroupUuid()).isNull();

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoForSimSlotIndex() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForSimSlotIndex(0, CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testUpdateEmbeddedSubscriptionsNullResult() {
        // Grant READ_PHONE_STATE permission.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        doReturn(null).when(mEuiccController).blockingGetEuiccProfileInfoList(anyInt());

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1, 2), null);
        processAllMessages();

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getAllSubInfoList(CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfoList).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfo() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfo(1, CALLING_PACKAGE, CALLING_FEATURE));

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoCount() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should fail without READ_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubInfoCount(CALLING_PACKAGE, CALLING_FEATURE, true));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEqualTo(2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetIconTint() {
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetAccessibleSubscriptionInfoList() {
        doReturn(true).when(mEuiccManager).isEnabled();
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);
        UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());

        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        doReturn(true).when(mSubscriptionManager).canManageSubscriptionAsUser(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE), any(UserHandle.class));
        // FAKE_SUBSCRIPTION_INFO2 is a not eSIM. So the list should be empty.
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEmpty();

        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        doReturn(false).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isNull();

        doReturn(false).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        doReturn(false).when(mSubscriptionManager).canManageSubscriptionAsUser(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE), eq(user));

        doReturn(true).when(mEuiccManager).isEnabled();
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEmpty();

        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        doReturn(true).when(mSubscriptionManager).canManageSubscriptionAsUser(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE), eq(user));
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEqualTo(List.of(new SubscriptionInfoInternal.Builder(
                        FAKE_SUBSCRIPTION_INFO1).setId(2).build().toSubscriptionInfo()));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveDataSubscriptionId() {
        doReturn(12345).when(mPhoneSwitcher).getActiveDataSubId();
        assertThat(mSubscriptionManagerServiceUT.getActiveDataSubscriptionId()).isEqualTo(12345);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIsSubscriptionAssociatedWithUser() {
        // Should fail without MANAGE_SUBSCRIPTION_USER_ASSOCIATION
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .isSubscriptionAssociatedWithUser(1, FAKE_USER_HANDLE));
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getSubscriptionInfoListAssociatedWithUser(FAKE_USER_HANDLE));

        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        // Should fail for non-existent sub Id
        assertThrows(IllegalArgumentException.class, () -> mSubscriptionManagerServiceUT
                .isSubscriptionAssociatedWithUser(1, FAKE_USER_HANDLE));

        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

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
    @EnableCompatChanges({SubscriptionManagerService.REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID,
            SubscriptionManagerService.FILTER_ACCESSIBLE_SUBS_BY_USER})
    public void testIsSubscriptionAssociatedWithUserMultiSubs() {
        doReturn(true).when(mFeatureFlags).workProfileApiSplit();
        doReturn(true).when(mFeatureFlags).enforceSubscriptionUserFilter();
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                UserHandle.of(UserHandle.USER_NULL), 1);

        // Verify sub 1 unassociated is visible to all profiles
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(true);

        // Assign sub 2 to work profile
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                FAKE_MANAGED_PROFILE_USER_HANDLE, 2);
        processAllMessages();
        // Verify work profile can only see its dedicated sub 2
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(2,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(false);
        // Verify personal profile can only see the unassigned sub 1
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(2,
                FAKE_USER_HANDLE)).isEqualTo(false);

        // Sub 2 is deactivated, but still on device, verify visibility doesn't change.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_NOT_READY, null, null);
        processAllMessages();
        // Verify work profile can only see its dedicated sub 2
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(2,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(false);
        // Verify personal profile can only see the unassigned sub 1
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(2,
                FAKE_USER_HANDLE)).isEqualTo(false);

        // Sub 2 is removed from the device.
        mSubscriptionManagerServiceUT.updateSimState(
                1, TelephonyManager.SIM_STATE_ABSENT, null, null);
        processAllMessages();
        // Verify the visibility of the unassigned sub 1 is restored to both profiles.
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_USER_HANDLE)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionAssociatedWithUser(1,
                FAKE_MANAGED_PROFILE_USER_HANDLE)).isEqualTo(true);
    }

    @Test
    @EnableCompatChanges({SubscriptionManagerService.REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID,
            SubscriptionManagerService.FILTER_ACCESSIBLE_SUBS_BY_USER})
    public void testSubscriptionAssociationWorkProfileCallerVisibility() {
        // Split mode is defined as when a profile owns a dedicated sub, it loses the visibility to
        // the unassociated sub.
        doReturn(true).when(mFeatureFlags).enforceSubscriptionUserFilter();
        doReturn(true).when(mFeatureFlags).workProfileApiSplit();
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        // Sub 1 is associated with work profile; Sub 2 is unassociated.
        int subId1 = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                FAKE_MANAGED_PROFILE_USER_HANDLE, subId1);
        int subId2 = insertSubscription(FAKE_SUBSCRIPTION_INFO2);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                UserHandle.of(UserHandle.USER_NULL), subId2);
        // Set Sub 1 default data sub
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.setDefaultDataSubId(subId1);
        processAllMessages();

        // Calling from work profile
        doReturn(FAKE_MANAGED_PROFILE_USER_HANDLE).when(mBinder).getCallingUserHandle();

        // Test getAccessibleSubscriptionInfoList
        doReturn(true).when(mEuiccManager).isEnabled();
        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());
        doReturn(true).when(mSubscriptionManager).canManageSubscriptionAsUser(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE), eq(user));
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEqualTo(List.of(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo()));
        // Test getActiveSubIdList, System
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false/*visible only*/))
                .isEqualTo(new int[]{subId1, subId2});
        // Test get getActiveSubInfoCount - forAllProfiles: false
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE, false)).isEqualTo(1);
        // Test get getActiveSubInfoCount - forAllProfiles: true
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                        CALLING_PACKAGE, CALLING_FEATURE, true));
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEqualTo(2);
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        // Test getActiveSubscriptionInfo
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfo(
                subId1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId()).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfo(
                subId2, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId()).isEqualTo(subId2);
        // Test getActiveSubscriptionInfoForIccId
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID2, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getActiveSubscriptionInfoForSimSlotIndex
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                0, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getActiveSubscriptionInfoList - forAllProfiles: false
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, false)
                .stream().map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1));
        // Test getActiveSubscriptionInfoList - forAllProfiles: true
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, true));
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, true)
                .stream().map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1, subId2));
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        // Test getAllSubInfoList
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1));
        // Test getAvailableSubscriptionInfoList
        assertThat(mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1, subId2));
        // Test getDefaultDataSubId
        assertThat(mSubscriptionManagerServiceUT.getDefaultDataSubId()).isEqualTo(subId1);
        // Test getDefault<Sms/Voice>SubIdAsUser
        assertThat(mSubscriptionManagerServiceUT.getDefaultSmsSubIdAsUser(
                FAKE_MANAGED_PROFILE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultSubIdAsUser(
                FAKE_MANAGED_PROFILE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultVoiceSubIdAsUser(
                FAKE_MANAGED_PROFILE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        // Test getEnabledSubscriptionId
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(0)).isEqualTo(
                subId1);
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(1)).isEqualTo(
                subId2);
        // Test getOpportunisticSubscriptions
        mSubscriptionManagerServiceUT.setOpportunistic(true, subId1, CALLING_PACKAGE);
        mSubscriptionManagerServiceUT.setOpportunistic(true, subId2, CALLING_PACKAGE);
        processAllMessages();
        assertThat(mSubscriptionManagerServiceUT.getOpportunisticSubscriptions(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1, subId2));
        // Test getSubscriptionInfo - can get both as it's an internal getter
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(subId1).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(subId2).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getSubscriptionInfoListAssociatedWithUser
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoListAssociatedWithUser(
                FAKE_MANAGED_PROFILE_USER_HANDLE).stream().map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1));
        // Test getSubscriptionsInGroup
        setCarrierPrivilegesForSubId(true, subId1);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE)
                .stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1));
        // Test isActiveSubId
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(subId1, CALLING_PACKAGE,
                CALLING_FEATURE)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(subId2, CALLING_PACKAGE,
                CALLING_FEATURE)).isTrue();
        // Test isSubscriptionEnabled
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(subId1)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(subId2)).isTrue();
    }

    @Test
    @EnableCompatChanges({SubscriptionManagerService.REQUIRE_DEVICE_IDENTIFIERS_FOR_GROUP_UUID,
            SubscriptionManagerService.FILTER_ACCESSIBLE_SUBS_BY_USER})
    public void testSubscriptionAssociationPersonalCallerVisibility() {
        // Split mode is defined as when a profile owns a dedicated sub, it loses the visibility to
        // the unassociated sub.
        doReturn(true).when(mFeatureFlags).enforceSubscriptionUserFilter();
        doReturn(true).when(mFeatureFlags).workProfileApiSplit();
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        // Sub 1 is unassociated; Sub 2 is associated with work profile.
        int subId1 = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                UserHandle.of(UserHandle.USER_NULL), subId1);
        int subId2 = insertSubscription(FAKE_SUBSCRIPTION_INFO2);
        mSubscriptionManagerServiceUT.setSubscriptionUserHandle(
                FAKE_MANAGED_PROFILE_USER_HANDLE, subId2);
        // Set Sub 1 default data sub
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.setDefaultDataSubId(subId1);
        processAllMessages();

        // Calling from a profile that owns no dedicated subs.
        doReturn(FAKE_USER_HANDLE).when(mBinder).getCallingUserHandle();

        // Test getAccessibleSubscriptionInfoList
        doReturn(true).when(mEuiccManager).isEnabled();
        doReturn(true).when(mSubscriptionManager).canManageSubscription(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE));
        UserHandle user = UserHandle.of(ActivityManager.getCurrentUser());
        doReturn(true).when(mSubscriptionManager).canManageSubscriptionAsUser(
                any(SubscriptionInfo.class), eq(CALLING_PACKAGE), eq(user));
        assertThat(mSubscriptionManagerServiceUT.getAccessibleSubscriptionInfoList(
                CALLING_PACKAGE)).isEqualTo(List.of(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo()));
        // Test getActiveSubIdList, System
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false/*visible only*/))
                .isEqualTo(new int[]{subId1, subId2});
        // Test get getActiveSubInfoCount- forAllProfiles: false
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE, false)).isEqualTo(1);
        // Test get getActiveSubInfoCount - forAllProfiles: true
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                        CALLING_PACKAGE, CALLING_FEATURE, true));
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubInfoCount(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEqualTo(2);
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        // Test getActiveSubscriptionInfo
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfo(
                subId1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId()).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfo(
                subId2, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId()).isEqualTo(subId2);
        // Test getActiveSubscriptionInfoForIccId
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID2, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getActiveSubscriptionInfoForSimSlotIndex
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                0, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForSimSlotIndex(
                1, CALLING_PACKAGE, CALLING_FEATURE).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getActiveSubscriptionInfoList - forAllProfiles: false
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, false).stream()
                .map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1));
        // Test getActiveSubscriptionInfoList - forAllProfiles: true
        assertThrows(SecurityException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, true));
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, true)
                .stream().map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1, subId2));
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.INTERACT_ACROSS_PROFILES);
        // Test getAllSubInfoList
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1));
        // Test getAvailableSubscriptionInfoList
        assertThat(mSubscriptionManagerServiceUT.getAvailableSubscriptionInfoList(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1, subId2));
        // Test getDefaultDataSubId
        assertThat(mSubscriptionManagerServiceUT.getDefaultDataSubId()).isEqualTo(subId1);
        // Test getDefault<Sms/Voice>SubIdAsUser
        assertThat(mSubscriptionManagerServiceUT.getDefaultSmsSubIdAsUser(
                FAKE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultSubIdAsUser(
                FAKE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getDefaultVoiceSubIdAsUser(
                FAKE_USER_HANDLE.getIdentifier())).isEqualTo(subId1);
        // Test getEnabledSubscriptionId
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(0)).isEqualTo(
                subId1);
        assertThat(mSubscriptionManagerServiceUT.getEnabledSubscriptionId(1)).isEqualTo(
                subId2);
        // Test getOpportunisticSubscriptions
        mSubscriptionManagerServiceUT.setOpportunistic(true, subId1, CALLING_PACKAGE);
        mSubscriptionManagerServiceUT.setOpportunistic(true, subId2, CALLING_PACKAGE);
        processAllMessages();
        assertThat(mSubscriptionManagerServiceUT.getOpportunisticSubscriptions(CALLING_PACKAGE,
                CALLING_FEATURE).stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1, subId2));
        // Test getSubscriptionInfo - can get both as it's an internal getter
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(subId1).getSubscriptionId())
                .isEqualTo(subId1);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(subId2).getSubscriptionId())
                .isEqualTo(subId2);
        // Test getSubscriptionInfoListAssociatedWithUser
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.MANAGE_SUBSCRIPTION_USER_ASSOCIATION);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoListAssociatedWithUser(
                        FAKE_USER_HANDLE).stream().map(SubscriptionInfo::getSubscriptionId)
                .toList()).isEqualTo(List.of(subId1));
        // Test getSubscriptionsInGroup
        setCarrierPrivilegesForSubId(true, subId1);
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionsInGroup(
                        ParcelUuid.fromString(FAKE_UUID1), CALLING_PACKAGE, CALLING_FEATURE)
                .stream().map(SubscriptionInfo::getSubscriptionId).toList())
                .isEqualTo(List.of(subId1));
        // Test isActiveSubId
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(subId1, CALLING_PACKAGE,
                CALLING_FEATURE)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isActiveSubId(subId2, CALLING_PACKAGE,
                CALLING_FEATURE)).isTrue();
        // Test isSubscriptionEnabled
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(subId1)).isTrue();
        assertThat(mSubscriptionManagerServiceUT.isSubscriptionEnabled(subId2)).isTrue();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetDisplayNumber() {
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                .setNumberFromCarrier("")
                .build());

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetOpportunisticSubscriptions() {
        testSetOpportunistic();
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getOpportunisticSubscriptions(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();

        // Grant READ_PHONE_STATE permission
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetPreferredDataSubscriptionId() {
        // Should fail without MODIFY_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setPreferredDataSubscriptionId(1, false, null));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        mSubscriptionManagerServiceUT.setPreferredDataSubscriptionId(1, false, null);
        verify(mPhoneSwitcher).trySetOpportunisticDataSubscription(eq(1), eq(false), eq(null));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPreferredDataSubscriptionId() {
        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getPreferredDataSubscriptionId());

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        doReturn(12345).when(mPhoneSwitcher).getAutoSelectedDataSubId();
        assertThat(mSubscriptionManagerServiceUT.getPreferredDataSubscriptionId()).isEqualTo(12345);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumberSourcePriority() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_NUMBERS);
        doReturn(Process.SYSTEM_UID).when(mBinder).getCallingUid();

        String phoneNumberFromCarrier = "8675309";
        String phoneNumberFromUicc = "1112223333";
        String phoneNumberFromTs43 = "5551234";
        String phoneNumberFromIms = "5553466";
        String phoneNumberFromPhoneObject = "8001234567";

        doReturn(phoneNumberFromPhoneObject).when(mPhone).getLine1Number();

        SubscriptionInfoInternal multiNumberSubInfo =
                new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                        .setNumberFromCarrier(phoneNumberFromCarrier)
                        .setNumber(phoneNumberFromUicc)
                        .setNumberFromIms(phoneNumberFromIms)
                        .setNumberFromTs43(phoneNumberFromTs43)
                        .build();
        int subId = insertSubscription(multiNumberSubInfo);

        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromCarrier);

        multiNumberSubInfo =
                new SubscriptionInfoInternal.Builder(multiNumberSubInfo)
                        .setNumberFromCarrier("")
                        .setNumber("")
                        .setNumberFromTs43(phoneNumberFromTs43)
                        .setNumberFromIms(phoneNumberFromIms)
                        .build();
        subId = insertSubscription(multiNumberSubInfo);

        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromPhoneObject);

        doReturn("").when(mPhone).getLine1Number();

        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromTs43);

        doReturn(10001).when(mBinder).getCallingUid();
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo("");

        setCarrierPrivilegesForSubId(true, subId);
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromTs43);

        multiNumberSubInfo =
                new SubscriptionInfoInternal.Builder(multiNumberSubInfo)
                        .setNumberFromCarrier("")
                        .setNumber("")
                        .setNumberFromTs43("")
                        .setNumberFromIms(phoneNumberFromIms)
                        .build();
        subId = insertSubscription(multiNumberSubInfo);

        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(true).when(mTelephonyManager).isImsRegistered();
        mSubscriptionManagerServiceUT.setImsNumberUpdateStatus(subId, true);
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromIms);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumber_ImsNotRegistered() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_NUMBERS);
        doReturn(Process.SYSTEM_UID).when(mBinder).getCallingUid();

        String phoneNumberFromCarrier = "";
        String phoneNumberFromUicc = "";
        String phoneNumberFromPhoneObject = "";
        String phoneNumberFromTs43 = "";
        String phoneNumberFromIms = "5553466";

        // Set up a scenario where the number is unavailable from the phone, UICC or TS43
        // but is available from IMS.
        doReturn(phoneNumberFromPhoneObject).when(mPhone).getLine1Number();
        SubscriptionInfoInternal multiNumberSubInfo =
                new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO1)
                        .setNumberFromCarrier(phoneNumberFromCarrier)
                        .setNumber(phoneNumberFromUicc)
                        .setNumberFromTs43(phoneNumberFromTs43)
                        .setNumberFromIms(phoneNumberFromIms)
                        .build();
        int subId = insertSubscription(multiNumberSubInfo);

        // Mock the IMS registration state of TelephonyManager to be unregistered (false).
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(false).when(mTelephonyManager).isImsRegistered();

        // Verify the legacy API: It should return an empty string when IMS is not registered.
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();

        // Verify the new API: It should return the cached IMS number even when IMS is not
        // registered.
        assertThat(mSubscriptionManagerServiceUT.getLastKnownPhoneNumberFromFirstAvailableSource(
                subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(phoneNumberFromIms);

        // Additional verification: The IMS number should remain stored in the SubscriptionInfo.
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId)
                .getNumberFromIms()).isEqualTo(phoneNumberFromIms);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetGetRcsConfig() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // COLUMN_RCS_CONFIG is no longer in the DIRECT_ACCESS_SUBSCRIPTION_COLUMNS whitelist.
        // It should always throw SecurityException when accessed through getSubscriptionProperty
        // or setSubscriptionProperty.
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_RCS_CONFIG, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getSubscriptionProperty(1,
                        SimInfo.COLUMN_RCS_CONFIG, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, SimInfo.COLUMN_RCS_CONFIG, "0"));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetSubscriptionPropertyInvalidField() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        assertThrows(IllegalArgumentException.class, () -> mSubscriptionManagerServiceUT
                .setSubscriptionProperty(1, "hahahaha", "0"));
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetNumberWithCarrierNumber() {
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
        assertThat(subInfo.getNumber()).isEqualTo(FAKE_PHONE_NUMBER1);
        Mockito.clearInvocations(mMockedSubscriptionManagerServiceCallback);

        setCarrierPrivilegesForSubId(true, 1);
        mSubscriptionManagerServiceUT.setPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, "",
                CALLING_PACKAGE, CALLING_FEATURE);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(1));
        setCarrierPrivilegesForSubId(false, 1);

        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getNumber()).isEqualTo(FAKE_PHONE_NUMBER2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGroupDisable() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setGroupUuid(FAKE_UUID1).build());

        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfo(2).isGroupDisabled())
                .isFalse();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumber() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        testSetPhoneNumber();
        doReturn(true).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(FAKE_PHONE_NUMBER2);
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER, CALLING_PACKAGE, CALLING_FEATURE))
                .isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumberFromUicc() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        testSetPhoneNumber();
        doReturn(true).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        // Number from line1Number should be FAKE_PHONE_NUMBER1 instead of FAKE_PHONE_NUMBER2
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_UICC, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo(FAKE_PHONE_NUMBER1);

        doReturn("").when(mPhone).getLine1Number();

        // If getLine1Number is empty, then the number should be from the sub info.
        assertThat(mSubscriptionManagerServiceUT.getPhoneNumber(1,
                SubscriptionManager.PHONE_NUMBER_SOURCE_UICC, CALLING_PACKAGE, CALLING_FEATURE))
                .isEqualTo("");
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumberFromDefaultSubscription() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        doReturn(Process.SYSTEM_UID).when(mBinder).getCallingUid();
        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        mSubscriptionManagerServiceUT.setDefaultVoiceSubId(subId);
        doReturn(mTelephonyManager).when(mTelephonyManager).createForSubscriptionId(anyInt());
        doReturn(true).when(mTelephonyManager).isImsRegistered();
        mSubscriptionManagerServiceUT.setImsNumberUpdateStatus(subId, true);

        assertThat(
                mSubscriptionManagerServiceUT.getPhoneNumberFromFirstAvailableSource(
                        subId, CALLING_PACKAGE, CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(
                mSubscriptionManagerServiceUT.getPhoneNumber(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_UICC,
                        CALLING_PACKAGE,
                        CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(
                mSubscriptionManagerServiceUT.getPhoneNumber(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_CARRIER,
                        CALLING_PACKAGE,
                        CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(
                mSubscriptionManagerServiceUT.getPhoneNumber(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_IMS,
                        CALLING_PACKAGE,
                        CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
        assertThat(
                mSubscriptionManagerServiceUT.getPhoneNumber(
                        SubscriptionManager.DEFAULT_SUBSCRIPTION_ID,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_TS43,
                        CALLING_PACKAGE,
                        CALLING_FEATURE)).isEqualTo(FAKE_PHONE_NUMBER1);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetPhoneNumber_ts43() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        String ts43Number = "1234567890";

        mSubscriptionManagerServiceUT.setPhoneNumber(subId,
                SubscriptionManager.PHONE_NUMBER_SOURCE_TS43, ts43Number,
                CALLING_PACKAGE, CALLING_FEATURE);

        doReturn(10001).when(mBinder).getCallingUid();
        setCarrierPrivilegesForSubId(false, subId);
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getPhoneNumber(
                        subId,
                        SubscriptionManager.PHONE_NUMBER_SOURCE_TS43,
                        CALLING_PACKAGE, CALLING_FEATURE));

        setCarrierPrivilegesForSubId(true, subId);
        String result = mSubscriptionManagerServiceUT.getPhoneNumber(
                subId,
                SubscriptionManager.PHONE_NUMBER_SOURCE_TS43,
                CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(result).isEqualTo(ts43Number);

        setCarrierPrivilegesForSubId(false, subId);
        doReturn(Process.SYSTEM_UID).when(mBinder).getCallingUid();
        result = mSubscriptionManagerServiceUT.getPhoneNumber(
                subId,
                SubscriptionManager.PHONE_NUMBER_SOURCE_TS43,
                CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(result).isEqualTo(ts43Number);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);

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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testDump() throws Exception {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);

        final StringWriter stringWriter = new StringWriter();
        assertThrows(SecurityException.class, ()
                -> mSubscriptionManagerServiceUT.dump(new FileDescriptor(),
                new PrintWriter(stringWriter), null));

        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.DUMP);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        SubscriptionPlan plan = createTestSubscriptionPlan("dump plan");
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                1, new SubscriptionPlan[]{plan}, 0, CALLING_PACKAGE);
        processAllMessages();
        mSubscriptionManagerServiceUT.dump(new FileDescriptor(), new PrintWriter(stringWriter),
                null);

        String dumpOutput = stringWriter.toString();
        assertThat(dumpOutput.length()).isGreaterThan(0);

        // Test SubscriptionPlan dump
        assertThat(dumpOutput).contains("Enrollable Subscription Plans:");
        assertThat(dumpOutput).contains(plan.getTitle());
        assertThat(dumpOutput).contains(CALLING_PACKAGE);
        assertThat(stringWriter.toString().length()).isGreaterThan(0);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);

        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();

        setIdentifierAccess(true);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS2, FAKE_CARRIER_NAME2,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true).get(0).getIccId())
                .isEqualTo(FAKE_MAC_ADDRESS2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSubscriptionSet() {
        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        SubscriptionSet<Integer> set = new SubscriptionSet<>();
        assertThat(set.getLargest()).isNull();

        set.add(0);
        assertThat(set.contains(0)).isTrue();
        assertThat(set.getLargest()).isEqualTo(0);

        set.add(5);
        assertThat(set.contains(5)).isTrue();
        assertThat(set.getLargest()).isEqualTo(5);

        set.add(2);
        assertThat(set.contains(2)).isTrue();
        assertThat(set.getLargest()).isEqualTo(5);

        set.remove(5);
        assertThat(set.contains(5)).isFalse();
        assertThat(set.getLargest()).isEqualTo(2);

        set.clear();
        assertThat(set).hasSize(0);
        assertThat(set.getLargest()).isNull();

        set.addAll(Arrays.asList(2, 0, 1));
        assertThat(set.toArray()).isEqualTo(new Object[]{0, 1, 2});
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSimNotReady() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_NOT_READY, null, null);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(false)).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
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

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListNoSecurityException() {
        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX).build());
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Disallow the application to perform.
        doReturn(AppOpsManager.MODE_ERRORED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        // Should get an empty list if the application is not allowed to perform it.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testUpdateGroupDisabled() {
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2).setGroupUuid(FAKE_UUID1).build());

        mSubscriptionManagerServiceUT.updateGroupDisabled();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(2);
        assertThat(subInfo.isGroupDisabled()).isFalse();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testUpdateGroupDisabledUngroupedOpportunistic() {
        doReturn(true).when(mFeatureFlags).enableIsPrivateNetworkApi();
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(
                Manifest.permission.USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER);

        // Sub 1: Opportunistic, Ungrouped. Active (Slot 0).
        SubscriptionInfoInternal sub1 = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO1)
                .setOpportunistic(1)
                .setGroupUuid("")
                .setSimSlotIndex(0)
                .build();
        int subId1 = insertSubscription(sub1);

        mSubscriptionManagerServiceUT.updateGroupDisabled();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId1)
                .isGroupDisabled()).isFalse();

        // Sub 2: Opportunistic, Grouped (UUID1). Active (Slot 1).
        SubscriptionInfoInternal sub2 = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2)
                .setOpportunistic(1)
                .setGroupUuid(FAKE_UUID1)
                .setSimSlotIndex(1)
                .build();
        int subId2 = insertSubscription(sub2);

        // Verify that sub 2 IS group disabled (Grouped, but no active primary in UUID1).
        // Active subs: 1 (opp, null group), 2 (opp, UUID1).
        mSubscriptionManagerServiceUT.updateGroupDisabled();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId2)
                .isGroupDisabled()).isTrue();

        // Sub 3: Primary, Grouped (UUID1). Active (Slot 0) - replaces Sub 1 in Slot 0 conceptually
        mSubscriptionManagerServiceUT.updateSimState(
                0, TelephonyManager.SIM_STATE_ABSENT, null, null);
        processAllMessages();
        SubscriptionInfoInternal sub3 = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO1)
                .setId(3)
                .setIccId(FAKE_ICCID3)
                .setOpportunistic(0)
                .setGroupUuid(FAKE_UUID1)
                .setSimSlotIndex(0)
                .build();
        insertSubscription(sub3);

        // Verify that sub 2 is enabled (Primary is active).
        mSubscriptionManagerServiceUT.updateGroupDisabled();
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId2)
                .isGroupDisabled()).isFalse();

        // Verify that sub 1 (inactive, ungrouped) is still enabled (default).
        assertThat(mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId1)
                .isGroupDisabled()).isFalse();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIsSatelliteSpn() {
        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "true");

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setServiceProviderName(FAKE_CARRIER_NAME1)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC1, FAKE_MNC1,
                        FAKE_CARRIER_NAME1, null, null, null, FAKE_CARRIER_ID1, FAKE_CARRIER_ID1))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getOnlyNonTerrestrialNetwork()).isEqualTo(1);

        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "false");
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIsSatelliteSpnWithEmptySpn() {
        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier, ""); // Empty
        System.setProperty("persist.radio.allow_mock_modem", "true");

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setServiceProviderName(FAKE_CARRIER_NAME1)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC1, FAKE_MNC1,
                        FAKE_CARRIER_NAME1, null, null, null, FAKE_CARRIER_ID1, FAKE_CARRIER_ID1))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getOnlyNonTerrestrialNetwork())
                .isEqualTo(FAKE_SATELLITE_IS_ONLY_NTN_DISABLED);

        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        EuiccProfileInfo profileInfo2 = new EuiccProfileInfo.Builder(FAKE_ICCID2)
                .setIccid(FAKE_ICCID2)
                .setNickname(FAKE_CARRIER_NAME2)
                .setServiceProviderName("")
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC2, FAKE_MNC2,
                        FAKE_CARRIER_NAME2, null, null, null, FAKE_CARRIER_ID2, FAKE_CARRIER_ID2))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES2)))
                .build();
        result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo2}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(2));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID2).when(mUiccController).convertToCardString(eq(2));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(2), null);
        processAllMessages();

        subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(2);
        assertThat(subInfo.getOnlyNonTerrestrialNetwork())
                .isEqualTo(FAKE_SATELLITE_IS_ONLY_NTN_DISABLED);

        System.setProperty("persist.radio.allow_mock_modem", "false");
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIsSatelliteSpnWithNullCarrierIdentifier() {
        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "true");

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setServiceProviderName(FAKE_CARRIER_NAME1)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(null)
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getOnlyNonTerrestrialNetwork()).isEqualTo(1);

        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "false");
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIsSatelliteSpnWithWrongSpn() {
        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "true");

        EuiccProfileInfo profileInfo1 = new EuiccProfileInfo.Builder(FAKE_ICCID1)
                .setIccid(FAKE_ICCID1)
                .setNickname(FAKE_CARRIER_NAME1)
                .setServiceProviderName(FAKE_CARRIER_NAME2)
                .setProfileClass(SubscriptionManager.PROFILE_CLASS_OPERATIONAL)
                .setCarrierIdentifier(new CarrierIdentifier(FAKE_MCC1, FAKE_MNC1,
                        FAKE_CARRIER_NAME1, null, null, null, FAKE_CARRIER_ID1, FAKE_CARRIER_ID1))
                .setUiccAccessRule(Arrays.asList(UiccAccessRule.decodeRules(
                        FAKE_NATIVE_ACCESS_RULES1)))
                .build();

        GetEuiccProfileInfoListResult result = new GetEuiccProfileInfoListResult(
                EuiccService.RESULT_OK, new EuiccProfileInfo[]{profileInfo1}, false);
        doReturn(result).when(mEuiccController).blockingGetEuiccProfileInfoList(eq(1));
        doReturn(TelephonyManager.INVALID_PORT_INDEX).when(mUiccSlot)
                .getPortIndexFromIccId(anyString());
        doReturn(FAKE_ICCID1).when(mUiccController).convertToCardString(eq(1));

        mSubscriptionManagerServiceUT.updateEmbeddedSubscriptions(List.of(1), null);
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(1);
        assertThat(subInfo.getOnlyNonTerrestrialNetwork()).isEqualTo(0);

        mContextFixture.putResource(R.string.config_satellite_sim_spn_identifier,
                FAKE_CARRIER_NAME1);
        System.setProperty("persist.radio.allow_mock_modem", "false");
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetSatelliteEntitlementPlmnList() throws Exception {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // When the empty list is stored, verify whether SubscriptionInfoInternal returns an
        // empty string and SubscriptionManagerService returns an empty List.
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        List<String> expectedPlmnList = new ArrayList<>();
        int subId = 1;

        SubscriptionInfoInternal subInfo =
                mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId);
        assertTrue(subInfo.getSatelliteEntitlementPlmns().isEmpty());
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnList(subId));

        assertTrue(subInfo.getSatelliteEntitlementBarredPlmnsList().isEmpty());
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementBarredPlmnList(subId));

        assertTrue(subInfo.getSatelliteEntitlementDataPlanForPlmns().isEmpty());
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementDataPlanForPlmns(subId));

        assertTrue(subInfo.getSatelliteEntitlementPlmnsServiceTypes().isEmpty());
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnServiceTypeMap(subId));

        assertTrue(subInfo.getSatellitePlmnsDataServicePolicy().isEmpty());
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnDataServicePolicy(subId));

        assertTrue(subInfo.getSatellitePlmnsVoiceServicePolicy().isEmpty());
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnVoiceServicePolicy(subId));

        // When the list is stored as [123123,12310], verify whether SubscriptionInfoInternal
        // returns the string as "123123,12310" and SubscriptionManagerService returns the List as
        // [123123,12310].
        insertSubscription(FAKE_SUBSCRIPTION_INFO2);
        String expectedPlmn = FAKE_SATELLITE_ENTITLEMENT_PLMNS1;
        expectedPlmnList = Arrays.stream(expectedPlmn.split(",")).collect(Collectors.toList());
        subId = 2;
        subInfo = mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(subId);
        assertEquals(expectedPlmn, subInfo.getSatelliteEntitlementPlmns());
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnList(subId));

        // When the list is stored as [123123,12310], verify whether SubscriptionInfoInternal
        // returns the string as "123123,12310" and SubscriptionManagerService returns the List as
        // [123123,12310].
        expectedPlmn = FAKE_SATELLITE_ENTITLEMENT_BARRED_PLMNS1;
        expectedPlmnList = Arrays.stream(expectedPlmn.split(",")).collect(Collectors.toList());
        assertEquals(expectedPlmn, subInfo.getSatelliteEntitlementBarredPlmnsList());
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementBarredPlmnList(subId));

        // When the Map is stored as {"302820":0,"31026":1,"40445":0}, verify whether
        // SubscriptionInfoInternal returns the Map as {"302820":0,"31026":1,"40445":0} and
        // SubscriptionManagerService returns the Map as {"302820":0,"31026":1,"40445":0}.
        String entitlementInfo = FAKE_SATELLITE_ENTITLEMENT_DATA_PLAN_PLMNS1;
        Map<String, Integer> entitlementInfoMap = mSubscriptionManagerServiceUT.deSerializeCVToMap(
                entitlementInfo);
        assertEquals(entitlementInfo, subInfo.getSatelliteEntitlementDataPlanForPlmns());
        assertEquals(entitlementInfoMap,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementDataPlanForPlmns(subId));

        // When the Map is stored as {"302820":[1,3],"31026":[2,3],"40445":[1,3]}, verify whether
        // SubscriptionInfoInternal returns the Map as {"302820":[1,3],"31026":[2,3],"40445":[1,
        // 3]} and SubscriptionManagerService returns the Map as {"302820":[1,3],"31026":[2,3],
        // "40445":[1,3]}.
        entitlementInfo = FAKE_SATELLITE_ENTITLEMENT_SERVICE_TYPE_MAP1;
        Map<String, List<Integer>> entitlementInfoMapList =
                mSubscriptionManagerServiceUT.deSerializeCVToMapList(entitlementInfo);
        assertEquals(entitlementInfo, subInfo.getSatelliteEntitlementPlmnsServiceTypes());
        assertEquals(entitlementInfoMapList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnServiceTypeMap(subId));

        // When the Map is stored as {"31026":1}, verify whether SubscriptionInfoInternal returns
        // the Map as {"31026":1} and SubscriptionManagerService returns the Map as {"31026":1}.
        entitlementInfo = FAKE_SATELLITE_ENTITLEMENT_DATA_SERVICE_POLICY1;
        entitlementInfoMap = mSubscriptionManagerServiceUT.deSerializeCVToMap(entitlementInfo);
        assertEquals(entitlementInfo, subInfo.getSatellitePlmnsDataServicePolicy());
        assertEquals(entitlementInfoMap,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnDataServicePolicy(subId));

        // When the Map is stored as {"31234":2}, verify whether SubscriptionInfoInternal returns
        // the Map as {"31234":2} and SubscriptionManagerService returns the Map as {"31234":2}.
        entitlementInfo = FAKE_SATELLITE_ENTITLEMENT_VOICE_SERVICE_POLICY1;
        entitlementInfoMap = mSubscriptionManagerServiceUT.deSerializeCVToMap(entitlementInfo);
        assertEquals(entitlementInfo, subInfo.getSatellitePlmnsVoiceServicePolicy());
        assertEquals(entitlementInfoMap,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnVoiceServicePolicy(subId));

        // When calling SubscriptionDatabaseManager#getSubscriptionInfoInternal returns a null, then
        // verify the SubscriptionManagerService returns an empty List.
        SubscriptionDatabaseManager mockSubscriptionDatabaseManager = Mockito.mock(
                SubscriptionDatabaseManager.class);
        Field field = SubscriptionManagerService.class.getDeclaredField(
                "mSubscriptionDatabaseManager");
        field.setAccessible(true);
        field.set(mSubscriptionManagerServiceUT, mockSubscriptionDatabaseManager);

        doReturn(null).when(mockSubscriptionDatabaseManager).getSubscriptionInfoInternal(anyInt());
        expectedPlmnList = new ArrayList<>();
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnList(subId));

        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementBarredPlmnList(subId));

        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementDataPlanForPlmns(subId));

        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnServiceTypeMap(subId));

        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnDataServicePolicy(subId));

        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnVoiceServicePolicy(subId));

        // When calling SubscriptionDatabaseManager#getSubscriptionInfoInternal returns a non null.
        // And when calling SubscriptionInfoInternal#getSatelliteEntitlementPlmns returns a null,
        // then verify the SubscriptionManagerService returns an empty List.
        SubscriptionInfoInternal mockSubscriptionInfoInternal = Mockito.mock(
                SubscriptionInfoInternal.class);
        doReturn(mockSubscriptionInfoInternal).when(
                mockSubscriptionDatabaseManager).getSubscriptionInfoInternal(anyInt());

        doReturn(null).when(mockSubscriptionInfoInternal).getSatelliteEntitlementPlmns();
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnList(subId));

        doReturn(null).when(mockSubscriptionInfoInternal).getSatelliteEntitlementBarredPlmnsList();
        assertEquals(expectedPlmnList,
                mSubscriptionManagerServiceUT.getSatelliteEntitlementBarredPlmnList(subId));

        doReturn(null).when(mockSubscriptionInfoInternal).getSatelliteEntitlementDataPlanForPlmns();
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementDataPlanForPlmns(subId));

        doReturn(null).when(
                mockSubscriptionInfoInternal).getSatelliteEntitlementPlmnsServiceTypes();
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnServiceTypeMap(subId));

        doReturn(null).when(mockSubscriptionInfoInternal).getSatellitePlmnsDataServicePolicy();
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnDataServicePolicy(subId));

        doReturn(null).when(mockSubscriptionInfoInternal).getSatellitePlmnsVoiceServicePolicy();
        assertEquals(new HashMap<>(),
                mSubscriptionManagerServiceUT.getSatelliteEntitlementPlmnVoiceServicePolicy(subId));
    }

    public void testIsSatelliteProvisionedForNonIpDatagram() {
        assertFalse(mSubscriptionManagerServiceUT.isSatelliteProvisionedForNonIpDatagram(-1));
    }

    /**
     * Configures the test environment's telephony subscription behavior.
     *
     * This method allows simulating different states of telephony subscription availability and
     * the associated configuration overlay. It is used to test scenarios specific to automotive
     * devices, where telephony subscription may or may not be available.
     *
     * @param enableFeature {@code true} to simulate that the telephony subscription feature is
     *                                  available, {@code false} otherwise.
     * @param enableOverlay {@code true} to enable the {@code config_force_phone_globals_creation}
     *                                  configuration overlay, {@code false} otherwise.
     */
    private void setTelephonySubscriptionSimulation(boolean enableFeature, boolean enableOverlay)
            throws Exception {
        doReturn(enableFeature).when(mPackageManager).hasSystemFeature(
                eq(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION));
        // Replace field to set vendor API level to the one where the exceptions are enabled.
        replaceInstance(SubscriptionManagerService.class, "mVendorApiLevel",
                mSubscriptionManagerServiceUT, TELEPHONY_FEATURE_ENFORCEMENT_VENDOR_API_LEVEL);
        doReturn(new String[]{CALLING_PACKAGE}).when(mPackageManager).getPackagesForUid(anyInt());
        mContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_force_phone_globals_creation, enableOverlay);
    }

    /**
     * Verifies the lifecycle of adding, removing, and re-adding a remote SIM.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimAddRemoveCycle() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS1, FAKE_CARRIER_NAME1,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(/* subId= */ eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);

        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(/* visibleOnly= */
                false)).isEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();

        setIdentifierAccess(true);
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS2, FAKE_CARRIER_NAME2,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(/* visibleOnly= */
                false)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isNotEmpty();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */
                true).get(0).getIccId())
                .isEqualTo(FAKE_MAC_ADDRESS2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteSimNoSubscriptionWithOverlay_addRemoveCycleRemoteSim() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimAddRemoveCycle();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteSimWithSubscriptionNoOverlay_addRemoveCycleRemoteSim() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimAddRemoveCycle();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteSimWithSubscriptionAndOverlay_addRemoveCycleRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimAddRemoveCycle();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteSimNoSubscriptionNoOverlay_throwsException() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ false);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS1,
                        FAKE_CARRIER_NAME1,
                        SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback, never()).onSubscriptionChanged(eq(1));
        assertThat(
                mSubscriptionManagerServiceUT.getSubscriptionInfoInternal(/* subId= */ 1)).isNull();
    }

    /**
     * Verifies that you can insert two remote SIMs together.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimAddTwo() {
        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS1, FAKE_CARRIER_NAME1,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(/* subId= */ eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        setIdentifierAccess(true);

        // Test with second remote SIM
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS2, FAKE_CARRIER_NAME2,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(/* visibleOnly= */
                false)).isEqualTo(new int[]{1, 2});

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE,
                        /* isForAllProfiles= */ true);
        assertThat(subInfoList).hasSize(2);
        assertThat(subInfoList.get(0).getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfoList.get(0).getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfoList.get(1).getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfoList.get(1).getIccId()).isEqualTo(FAKE_MAC_ADDRESS2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testTwoRemoteSimsNoSubscriptionWithOverlay_addsAndVerifiesRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimAddTwo();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testTwoRemoteSimsWithSubscriptionNoOverlay_addsAndVerifiesRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimAddTwo();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testTwoRemoteSimsWithSubscriptionAndOverlay_addsAndVerifiesRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimAddTwo();
    }

    /**
     * Verifies that you can insert a local SIM followed by a remote SIM together.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimAddLocalAndRemote() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        // Insert local SIM first
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID1, FAKE_CARRIER_NAME1,
                0, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(/* subId= */ eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(0)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(0);

        setIdentifierAccess(true);

        // Insert remote SIM second
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS2, FAKE_CARRIER_NAME2,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(/* visibleOnly= */
                false)).isEqualTo(new int[]{1, 2});

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE,
                        /* isForAllProfiles= */ true);
        assertThat(subInfoList).hasSize(2);
        // Not in insertion order since list is sorted by slot index
        assertThat(subInfoList.get(1).getSimSlotIndex()).isEqualTo(0);
        assertThat(subInfoList.get(1).getIccId()).isEqualTo(FAKE_ICCID1);
        assertThat(subInfoList.get(0).getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfoList.get(0).getIccId()).isEqualTo(FAKE_MAC_ADDRESS2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testLocalAndRemoteSimsNoSubscriptionWithOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimAddLocalAndRemote();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testLocalAndRemoteSimsWithSubscriptionNoOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimAddLocalAndRemote();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testLocalAndRemoteSimsWithSubscriptionAndOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimAddLocalAndRemote();
    }

    /**
     * Verifies that you can insert a remote SIM followed by a local SIM together.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimAddRemoteAndLocal() {
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        // Insert remote SIM first
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_MAC_ADDRESS1, FAKE_CARRIER_NAME1,
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        processAllMessages();

        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(/* subId= */ eq(1));

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo.getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfo.getDisplayName()).isEqualTo(FAKE_CARRIER_NAME1);
        assertThat(subInfo.getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfo.getSubscriptionType()).isEqualTo(
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        setIdentifierAccess(true);

        // Insert local SIM second
        mSubscriptionManagerServiceUT.addSubInfo(FAKE_ICCID2, FAKE_CARRIER_NAME2,
                1, SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(1)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getActiveSubIdList(/* visibleOnly= */
                false)).isEqualTo(new int[]{1, 2});

        List<SubscriptionInfo> subInfoList = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE,
                        /* isForAllProfiles= */ true);
        assertThat(subInfoList).hasSize(2);
        assertThat(subInfoList.get(0).getSimSlotIndex()).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(subInfoList.get(0).getIccId()).isEqualTo(FAKE_MAC_ADDRESS1);
        assertThat(subInfoList.get(1).getSimSlotIndex()).isEqualTo(1);
        assertThat(subInfoList.get(1).getIccId()).isEqualTo(FAKE_ICCID2);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteAndLocalSimsNoSubscriptionWithOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimAddRemoteAndLocal();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteAndLocalSimsWithSubscriptionNoOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimAddRemoteAndLocal();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoteAndLocalSimsWithSubscriptionAndOverlay_addsAndVerifiesBothSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimAddRemoteAndLocal();
    }

    /**
     * Verifies that getSubId correctly returns the most recently inserted remote SIM after removing
     * the second remote SIM.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetSubIdNoSubscriptionWithOverlay_afterRemovingSecondRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        insertSubscription(FAKE_REMOTE_SIM1);

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 1);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfo(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        // Test with second remote SIM
        insertSubscription(FAKE_REMOTE_SIM2);
        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 2);
        subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfo(/* subId= */ 2);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());
        // Correctly gives most recently inserted remote SIM
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        // Remove second remote SIM
        mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS2,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        // Correctly gives most recently inserted remote SIM (the remaining one)
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);

        // Remove first remote SIM
        mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
    }

    /**
     * Verifies that getSubId correctly returns the most recently inserted remote SIM after removing
     * the first remote SIM.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetSubIdNoSubscriptionWithOverlay_afterRemovingFirstRemoteSim()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        insertSubscription(FAKE_REMOTE_SIM1);

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 1);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfo(/* subId= */ 1);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(1);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        // Test with second remote SIM
        insertSubscription(FAKE_REMOTE_SIM2);
        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 2);
        subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfo(/* subId= */ 2);
        assertThat(subInfo).isNotNull();
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());
        // Correctly gives most recently inserted remote SIM
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        // Remove first remote SIM
        mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        // Correctly gives most recently inserted remote SIM (the remaining one)
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(2);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB);

        // Remove second remote SIM
        mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS2,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM);
        assertThat(mSubscriptionManagerServiceUT.getSubId(
                SubscriptionManager.SLOT_INDEX_FOR_REMOTE_SIM_SUB)).isEqualTo(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 1)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
        assertThat(mSubscriptionManagerServiceUT.getSlotIndex(/* subId= */ 2)).isEqualTo(
                SubscriptionManager.INVALID_SIM_SLOT_INDEX);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListNoSubscriptionWithOverlay_localSimListed()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        insertSubscription(new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                .setSimSlotIndex(SubscriptionManager.INVALID_SIM_SLOT_INDEX).build());
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);
        // Identifying information removed
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0).getIccId()).isEmpty();
        assertThat(subInfos.get(0).getCardString()).isEmpty();
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(0).getGroupUuid()).isNull();

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(true, 1);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE, CALLING_FEATURE, true);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    /**
     * Verifies that getActiveSubscriptionInfoList correctly lists a remote SIM, and its
     * interactions with permissions work as intended.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimListedGetActiveSubscriptionInfoList() {
        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_REMOTE_SIM1);
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();

        // Grant READ_PHONE_STATE permission for retrieval.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                        CALLING_FEATURE, /* isForAllProfiles= */ true);
        // Identifying information removed
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0).getIccId()).isEmpty();
        assertThat(subInfos.get(0).getCardString()).isEmpty();
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(0).getGroupUuid()).isNull();

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 1);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                        CALLING_FEATURE, /* isForAllProfiles= */ true);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListNoSubscriptionWithOverlay_remoteSimListed()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimListedGetActiveSubscriptionInfoList();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListWithSubscriptionNoOverlay_remoteSimListed()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimListedGetActiveSubscriptionInfoList();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListWithSubscriptionAndOverlay_remoteSimListed()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimListedGetActiveSubscriptionInfoList();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListNoSubscriptionNoOverlay_throwsException()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ false);

        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_REMOTE_SIM1);
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT
                        .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                                CALLING_FEATURE, /* isForAllProfiles= */ true));

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 1);

        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT
                        .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                                CALLING_FEATURE, /* isForAllProfiles= */ true));
    }

    /**
     * Verifies that getActiveSubscriptionInfoList correctly lists two remote SIMs, and its
     * interactions with permissions work as intended.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimListedGetActiveSubscriptionInfoList_twoRemoteSims() {
        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_REMOTE_SIM1);
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Should get an empty list without READ_PHONE_STATE.
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();

        // Grant READ_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PHONE_STATE);
        // Allow the application to perform.
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), anyInt(),
                        nullable(String.class), nullable(String.class), nullable(String.class));

        List<SubscriptionInfo> subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                        CALLING_FEATURE, /* isForAllProfiles= */ true);
        // Identifying information removed
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0).getIccId()).isEmpty();
        assertThat(subInfos.get(0).getCardString()).isEmpty();
        assertThat(subInfos.get(0).getNumber()).isEmpty();
        assertThat(subInfos.get(0).getGroupUuid()).isNull();

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 1);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                        CALLING_FEATURE, /* isForAllProfiles= */ true);
        assertThat(subInfos).hasSize(1);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());

        // Test with second remote SIM
        // Grant MODIFY_PHONE_STATE permission for insertion.
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        insertSubscription(FAKE_REMOTE_SIM2);
        // Remove MODIFY_PHONE_STATE
        mContextFixture.removeCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);

        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 2);

        subInfos = mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoList(CALLING_PACKAGE,
                        CALLING_FEATURE, /* isForAllProfiles= */ true);
        assertThat(subInfos).hasSize(2);
        assertThat(subInfos.get(0)).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
        assertThat(subInfos.get(1)).isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListNoSubscriptionWithOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimListedGetActiveSubscriptionInfoList_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListWithSubscriptionNoOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimListedGetActiveSubscriptionInfoList_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubscriptionInfoListWithSubscriptionAndOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimListedGetActiveSubscriptionInfoList_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdNoSubscriptionWithOverlay_returnsLocalSimForIccid()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForIccId(FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_ICCID1, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo());
    }

    /**
     * Verifies that getActiveSubscriptionInfoForIccId can retrieve a remote SIM.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId() {
        insertSubscription(FAKE_REMOTE_SIM1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForIccId(FAKE_MAC_ADDRESS1, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_MAC_ADDRESS1, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdNoSubscriptionWithOverlay_returnsRemoteSimForIccId()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdWithSubscriptionNoOverlay_returnsRemoteSimForIccId()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdWithSubscriptionAndOverlay_returnsRemoteSimForIccId()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdNoSubscriptionNoOverlay_throwsException()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ false);

        insertSubscription(FAKE_REMOTE_SIM1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForIccId(FAKE_MAC_ADDRESS1, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                        FAKE_MAC_ADDRESS1, CALLING_PACKAGE, CALLING_FEATURE));
    }

    /**
     * Verifies that getActiveSubscriptionInfoForIccId can retrieve the correct remote SIM, even
     * when two are inserted.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId_twoRemoteSims() {
        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        insertSubscription(FAKE_REMOTE_SIM1);

        // Should fail without READ_PRIVILEGED_PHONE_STATE
        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .getActiveSubscriptionInfoForIccId(FAKE_MAC_ADDRESS1, CALLING_PACKAGE,
                        CALLING_FEATURE));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        SubscriptionInfo subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_MAC_ADDRESS1, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());

        // Test with second remote SIM
        insertSubscription(FAKE_REMOTE_SIM2);

        subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_MAC_ADDRESS1, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM1.toSubscriptionInfo());
        subInfo = mSubscriptionManagerServiceUT.getActiveSubscriptionInfoForIccId(
                FAKE_MAC_ADDRESS2, CALLING_PACKAGE, CALLING_FEATURE);
        assertThat(subInfo).isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdNoSubscriptionWithOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdWithSubscriptionNoOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testGetActiveSubInfoForIccIdWithSubscriptionAndOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimReturnedGetActiveSubscriptionInfoForIccId_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoNoSubscriptionWithOverlay_localSimRemoved() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

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
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, true)).isEmpty();
    }

    /**
     * Verifies that removeSubInfo can remove a remote SIM.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimRemovedRemoveSubInfo() {
        insertSubscription(FAKE_REMOTE_SIM1);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .removeSubInfo(FAKE_MAC_ADDRESS1,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).isEmpty()).isTrue();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoNoSubscriptionWithOverlay_remoteSimRemoved() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimRemovedRemoveSubInfo();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoWithSubscriptionNoOverlay_remoteSimRemoved() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimRemovedRemoveSubInfo();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoWithSubscriptionAndOverlay_remoteSimRemoved() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimRemovedRemoveSubInfo();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoNoSubscriptionNoOverlay_throwsException() throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ false);

        insertSubscription(FAKE_REMOTE_SIM1);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .removeSubInfo(FAKE_MAC_ADDRESS1,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true));
        assertThrows(UnsupportedOperationException.class,
                () -> mSubscriptionManagerServiceUT.getAllSubInfoList(
                        CALLING_PACKAGE, CALLING_FEATURE));
    }

    /**
     * Verifies that removeSubInfo can remove the correct remote SIM, even when two are inserted.
     *
     * <p>This method expects the caller to have already configured the desired simulation state
     * using {@link #setTelephonySubscriptionSimulation}.
     */
    private void verifyRemoteSimRemovedRemoveSubInfo_twoRemoteSims() {
        doReturn(true).when(mFeatureFlags).remoteSimSubIdSet();

        insertSubscription(FAKE_REMOTE_SIM1);
        insertSubscription(FAKE_REMOTE_SIM2);

        assertThrows(SecurityException.class, () -> mSubscriptionManagerServiceUT
                .removeSubInfo(FAKE_MAC_ADDRESS1,
                        SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM));

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.MODIFY_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS1,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).isEmpty()).isFalse();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isNotEmpty();
        // Grant carrier privilege
        setCarrierPrivilegesForSubId(/* hasCarrierPrivileges= */ true, /* subId= */ 2);
        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).get(0))
                .isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true).get(0))
                .isEqualTo(FAKE_REMOTE_SIM2.toSubscriptionInfo());

        // Remove second remote SIM
        assertThat(mSubscriptionManagerServiceUT.removeSubInfo(FAKE_MAC_ADDRESS2,
                SubscriptionManager.SUBSCRIPTION_TYPE_REMOTE_SIM)).isEqualTo(true);

        assertThat(mSubscriptionManagerServiceUT.getAllSubInfoList(
                CALLING_PACKAGE, CALLING_FEATURE).isEmpty()).isTrue();
        assertThat(mSubscriptionManagerServiceUT.getActiveSubscriptionInfoList(
                CALLING_PACKAGE, CALLING_FEATURE, /* isForAllProfiles= */ true)).isEmpty();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoNoSubscriptionWithOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ false, /* enableOverlay= */ true);

        verifyRemoteSimRemovedRemoveSubInfo_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoWithSubscriptionNoOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ false);

        verifyRemoteSimRemovedRemoveSubInfo_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testRemoveSubInfoWithSubscriptionAndOverlay_twoRemoteSims()
            throws Exception {
        setTelephonySubscriptionSimulation(/* enableFeature= */ true, /* enableOverlay= */ true);

        verifyRemoteSimRemovedRemoveSubInfo_twoRemoteSims();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testUpdateSubByCarrierConfig_withPrivateNetworkCarrierConfig_setsIsPrivateNetwork()
            throws Exception {
        doReturn(true).when(mFeatureFlags).enableIsPrivateNetworkApi();
        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        int phoneId = FAKE_SUBSCRIPTION_INFO1.getSimSlotIndex();
        getSubscriptionDatabaseManager().setIsPrivateNetwork(subId, 0);
        processAllMessages();

        PersistableBundle config = new PersistableBundle();
        config.putBoolean(CarrierConfigManager.KEY_IS_PRIVATE_NETWORK_BOOL, true);
        mSubscriptionManagerServiceUT.updateSubscriptionByCarrierConfig(phoneId, CALLING_PACKAGE,
                config, () -> {});
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(subId);
        assertThat(subInfo.getIsPrivateNetwork()).isEqualTo(1);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testUpdateSubByCarrierConfig_withPrivateNetworkMcc_setsIsPrivateNetwork()
            throws Exception {
        doReturn(true).when(mFeatureFlags).enableIsPrivateNetworkApi();
        SubscriptionInfoInternal privateNetworkMccSubInfo =
                new SubscriptionInfoInternal.Builder(FAKE_SUBSCRIPTION_INFO2)
                        .setMcc("999")
                        .build();
        int subId = insertSubscription(privateNetworkMccSubInfo);
        int phoneId = privateNetworkMccSubInfo.getSimSlotIndex();
        getSubscriptionDatabaseManager().setIsPrivateNetwork(subId, 0);
        processAllMessages();

        PersistableBundle config = new PersistableBundle();
        mSubscriptionManagerServiceUT.updateSubscriptionByCarrierConfig(phoneId, CALLING_PACKAGE,
                config, () -> {});
        processAllMessages();

        SubscriptionInfoInternal subInfo = mSubscriptionManagerServiceUT
                .getSubscriptionInfoInternal(subId);
        assertThat(subInfo.getIsPrivateNetwork()).isEqualTo(1);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testIccIdStripping() {
        assertThat(SubscriptionManagerService.getStrippedIccid(FAKE_ICCID1)).isEqualTo(FAKE_ICCID1);
        assertThat(SubscriptionManagerService.getStrippedIccid(FAKE_ICCID2)).isEqualTo(FAKE_ICCID2);
        assertThat(SubscriptionManagerService.getStrippedIccid(FAKE_ICCID3)).isEqualTo("12345");
        assertThat(SubscriptionManagerService.getStrippedIccid(FAKE_ICCID4)).isEqualTo(FAKE_ICCID4);
    }

    /**
     * Helper to setup PackageManager mocks for checking uid and package name.
     */
    private void setupPackageManagerMocks(String packageName, int uid) throws Exception {
        doReturn(uid).when(mPackageManager).getPackageUid(anyString(), anyInt());
        doReturn(new String[]{packageName}).when(mPackageManager).getPackagesForUid(anyInt());
    }

    /**
     * Helper to grant or remove MANAGE_SUBSCRIPTION_PLANS permission
     */
    private void setManageSubscriptionPlansPermission(boolean granted) {
        if (granted) {
            mContextFixture.addCallingOrSelfPermission(
                    Manifest.permission.MANAGE_SUBSCRIPTION_PLANS);
        } else {
            mContextFixture.removeCallingOrSelfPermission(
                    Manifest.permission.MANAGE_SUBSCRIPTION_PLANS);
        }
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testCanManageSubscriptionAsUser() {
        SubscriptionInfo subInfo = FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo();
        UserHandle userHandle = FAKE_USER_HANDLE;
        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);

        // Mock TelephonyManager for the subId
        TelephonyManager mockTm = Mockito.mock(TelephonyManager.class);
        doReturn(mockTm).when(mTelephonyManager).createForSubscriptionId(eq(subId));
        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(CALLING_PACKAGE));
        doReturn(FAKE_CARRIER_ID1).when(mockTm).getSimCarrierId();

        // Caller does NOT have READ_PRIVILEGED_PHONE_STATE, packageName is "self"
        String selfPackageName = mContext.getPackageName();
        doNothing().when(mAppOpsManager).checkPackage(anyInt(), eq(selfPackageName));
        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(selfPackageName));
        assertThat(mSubscriptionManagerServiceUT.canManageSubscriptionAsUser(
                subInfo, selfPackageName, userHandle)).isTrue();
        verify(mAppOpsManager).checkPackage(Binder.getCallingUid(), selfPackageName);

        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(selfPackageName));
        assertThat(mSubscriptionManagerServiceUT.canManageSubscriptionAsUser(
                subInfo, selfPackageName, userHandle)).isFalse();

        // Caller does NOT have READ_PRIVILEGED_PHONE_STATE, packageName is NOT "self"
        // And AppOpsManager check fails
        Mockito.clearInvocations(mAppOpsManager);
        doThrow(new SecurityException())
                .when(mAppOpsManager).checkPackage(anyInt(), eq(CALLING_PACKAGE));
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.canManageSubscriptionAsUser(
                        subInfo, CALLING_PACKAGE, userHandle));
        verify(mAppOpsManager).checkPackage(Binder.getCallingUid(), CALLING_PACKAGE);

        // Caller has READ_PRIVILEGED_PHONE_STATE
        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        Mockito.clearInvocations(mAppOpsManager);

        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(selfPackageName));
        assertThat(mSubscriptionManagerServiceUT.canManageSubscriptionAsUser(
                subInfo, selfPackageName, userHandle)).isTrue();

        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(selfPackageName));

        assertThat(mSubscriptionManagerServiceUT.canManageSubscriptionAsUser(
                subInfo, selfPackageName, userHandle)).isFalse();
        verify(mAppOpsManager, never()).checkPackage(Binder.getCallingUid(), selfPackageName);

        mContextFixture.removeCallingOrSelfPermission(
                Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testCanManageSubscription_CarrierIdMatch() {
        // This test requires the CarrierId matching to be supported
        Mockito.reset(mFeatureFlags);
        doReturn(true).when(mFeatureFlags)
                .downloadableSubscriptionIncludeCarrierIdentifierInternal();

        mContextFixture.addCallingOrSelfPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = insertSubscription(FAKE_SUBSCRIPTION_INFO1);
        SubscriptionInfo subInfo = FAKE_SUBSCRIPTION_INFO1.toSubscriptionInfo();

        // The subscriptions should be mostly different, but subInfo2 should
        // share a CarrierId with subInfo
        SubscriptionInfoInternal targetSubscriptionInfoInternal = new SubscriptionInfoInternal
                .Builder(FAKE_SUBSCRIPTION_INFO2)
                .setCarrierId(subInfo.getCarrierId()).build();
        int targetSubId = insertSubscription(targetSubscriptionInfoInternal);
        SubscriptionInfo targetSubInfo = targetSubscriptionInfoInternal.toSubscriptionInfo();

        TelephonyManager mockTm = Mockito.mock(TelephonyManager.class);
        doReturn(mockTm).when(mTelephonyManager).createForSubscriptionId(eq(subId));
        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(CALLING_PACKAGE));
        doReturn(FAKE_CARRIER_ID1).when(mockTm).getSimCarrierId();

        TelephonyManager mockTargetTm = Mockito.mock(TelephonyManager.class);
        doReturn(mockTargetTm).when(mTelephonyManager).createForSubscriptionId(eq(targetSubId));
        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS)
                .when(mockTargetTm).checkCarrierPrivilegesForPackage(eq(CALLING_PACKAGE));
        doReturn(FAKE_CARRIER_ID1).when(mockTargetTm).getSimCarrierId();

        assertThat(
                mSubscriptionManagerServiceUT
                        .canManageSubscriptionAsUser(
                                targetSubInfo, CALLING_PACKAGE, FAKE_USER_HANDLE))
                                        .isTrue();

        // Now remove Carrier Privileges from the source subscription and confirm that the
        // target subscription can no longer be managed.
        doReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS)
                .when(mockTm).checkCarrierPrivilegesForPackage(eq(CALLING_PACKAGE));
        assertThat(
                mSubscriptionManagerServiceUT
                        .canManageSubscriptionAsUser(
                                targetSubInfo, CALLING_PACKAGE, FAKE_USER_HANDLE))
                                        .isFalse();
    }

    /**
     * Helper to mock Carrier Privileges for the specific test package
     */
    private void setCarrierPrivilegesCheckForPackage(boolean hasPrivileges, int subId) {
        setCarrierPrivilegesForSubId(hasPrivileges, subId);
        TelephonyManager mockTelephonyManager = Mockito.mock(TelephonyManager.class);
        doReturn(mockTelephonyManager).when(mTelephonyManager).createForSubscriptionId(eq(subId));
        int privilegeStatus = hasPrivileges
                ? TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS
                : TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS;
        doReturn(privilegeStatus).when(mockTelephonyManager)
                .checkCarrierPrivilegesForPackage(eq(CALLING_PACKAGE));
    }

    /**
     * Create and return a test Subscription Plan.
     */
    private SubscriptionPlan createTestSubscriptionPlan(String title) {
        return SubscriptionPlan.Builder
                .createRecurring(
                        ZonedDateTime.parse("2025-01-01T00:00:00.000Z"), Period.ofMonths(1))
                .setTitle(title)
                .setDataLimit(SubscriptionPlan.BYTES_UNLIMITED,
                        SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                .setId(1001)
                .setTypes(new int[] {
                        SubscriptionPlan.PLAN_TYPE_CELLULAR,
                        SubscriptionPlan.PLAN_TYPE_PREPAID
                })
                .setDataUsageResetTime(ZonedDateTime.parse("2025-01-15T00:00:00.000Z"))
                .setStreamingAppMaxDownlinkKbps(5000)
                .setStreamingAppMaxUplinkKbps(1000)
                .build();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testSetGetEnrollableSubscriptionPlans() throws Exception {
        // SetUp: the mock PackageManager to associate CALLING_PACKAGE with CALLING_UID
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // SetUp: add MANAGE_SUBSCRIPTION_PLANS permission
        setManageSubscriptionPlansPermission(true);
        // SetUp: make a sample plan
        SubscriptionPlan plan = createTestSubscriptionPlan("Test Enrollable Plan");
        // SetUp: subId to test
        int subId = 1;

        // Act: set plan
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, new SubscriptionPlan[]{plan}, 0, CALLING_PACKAGE);
        processAllMessages();

        // Verify that plan was set properly
        SubscriptionPlan[] storedPlans = mSubscriptionManagerServiceUT
                .getEnrollableSubscriptionPlans(subId, CALLING_PACKAGE);
        assertThat(storedPlans).isNotNull();
        assertThat(storedPlans).hasLength(1);
        assertThat(storedPlans[0]).isEqualTo(plan);
        assertThat(storedPlans[0].getId()).isEqualTo(1001);
        assertThat(storedPlans[0].getTypes()).containsExactly(
                SubscriptionPlan.PLAN_TYPE_CELLULAR,
                SubscriptionPlan.PLAN_TYPE_PREPAID);
        assertThat(storedPlans[0].getDataUsageResetTime())
                .isEqualTo(ZonedDateTime.parse("2025-01-15T00:00:00.000Z"));
        assertThat(storedPlans[0].getStreamingAppMaxDownlinkKbps()).isEqualTo(5000);
        assertThat(storedPlans[0].getStreamingAppMaxUplinkKbps()).isEqualTo(1000);

        try {
            // Verify that owner was set properly
            String owner = mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlansOwner(subId);
            if (owner != null) {
                assertThat(owner).isEqualTo(CALLING_PACKAGE);
            }
        } catch (SecurityException e) {
            // Expected if not system uid
        }
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlansSecurity() throws Exception {
        // Set up the mock PackageManager to associate CALLING_PACKAGE with CALLING_UID
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // SetUp: make a sample plan
        SubscriptionPlan plan = createTestSubscriptionPlan("Security Test Plan");
        // SetUp: subId to test
        int subId = 1;

        // Test #1. no permission, no carrier privilege.
        // SetUp: no permission, no carrier privilege.
        setManageSubscriptionPlansPermission(false);
        setCarrierPrivilegesCheckForPackage(false, subId);

        // Act and Verify SecurityException is thrown.
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(subId,
                        new SubscriptionPlan[]{plan}, 0, CALLING_PACKAGE));

        // Act and Verify SecurityException is thrown.
        assertThrows(SecurityException.class, () ->
                mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(subId,
                        CALLING_PACKAGE));

        // Test #2. call with Carrier Privilege
        // SetUp: set Carrier Privilege
        setCarrierPrivilegesCheckForPackage(true, subId);

        // Act: call set plan API.
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(subId,
                new SubscriptionPlan[]{plan}, 0, CALLING_PACKAGE);
        processAllMessages();

        // Verify that Carrier Privilege allows get/set enrollable subscription plan.
        SubscriptionPlan[] plans = mSubscriptionManagerServiceUT
                .getEnrollableSubscriptionPlans(subId, CALLING_PACKAGE);
        assertThat(plans).hasLength(1);

        // Test #3. call with owner.
        // SetUp: remove all permission.
        setCarrierPrivilegesCheckForPackage(false, subId);

        // Act: call get plan API.
        plans = mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(subId,
                CALLING_PACKAGE);

        // Verify that call get plan API with owner.
        assertThat(plans).hasLength(1);

        // Act: call set plan API as well.
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(subId,
                new SubscriptionPlan[]{}, 0, CALLING_PACKAGE);
        processAllMessages();

        // Verify that call set plan API with owner.
        plans = mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(subId,
                CALLING_PACKAGE);
        assertThat(plans).isEmpty();

        try {
            mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlansOwner(subId);
            fail("SecurityException expected when caller is not SYSTEM_UID");
        } catch (SecurityException e) {
            // Success
        }
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlansExpiration() throws Exception {
        // SetUp: the mock PackageManager to associate CALLING_PACKAGE with CALLING_UID
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // SetUp: add MANAGE_SUBSCRIPTION_PLANS permission
        setManageSubscriptionPlansPermission(true);
        // SetUp: make a sample plan
        SubscriptionPlan plan = createTestSubscriptionPlan("Expiring Plan");
        // SetUp: subId to test
        int subId = 1;
        // SetUp: set expiration time.
        long expirationDuration = 1000; // 1 sec.

        // Act: Sets a plan with an expiration time
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(subId,
                new SubscriptionPlan[]{plan}, expirationDuration, CALLING_PACKAGE);
        processAllMessages();

        // Verify that plan must exist immediately after setup
        assertThat(mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(subId,
                CALLING_PACKAGE)).hasLength(1);

        // Act: Time-lapse simulation (1 second + slack time)
        // Using TelephonyTest's moveTimeForward (controlling TestableLooper)
        moveTimeForward(expirationDuration + 1000);
        processAllMessages();

        // Verify that plan has expired and is gone (expected to return null)
        assertThat(mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(subId,
                CALLING_PACKAGE)).isNull();
    }

    /**
     * Tests that enrollable subscription plans are persisted to disk and restored after a reboot.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlans_Persistence() throws Exception {
        // 1. Setup: Define plans and calling package
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // Grant Permission
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = 1;
        SubscriptionPlan plan1 = createTestSubscriptionPlan("Persisted Plan 1");
        SubscriptionPlan plan2 = createTestSubscriptionPlan("Persisted Plan 2");
        SubscriptionPlan[] plans = new SubscriptionPlan[] {plan1, plan2};

        // 2. Action: Set plans (This should trigger XML write)
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, plans, 10000, CALLING_PACKAGE);
        processAllMessages();

        // 3. Verify: Plans are available in memory
        assertThat(
                        mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                                subId, CALLING_PACKAGE))
                .asList()
                .containsExactly(plan1, plan2);

        // 4. Simulate Reboot: Re-create the service instance
        // This will trigger the constructor, which calls readEnrollableSubscriptionPlans()
        mSubscriptionManagerServiceUT =
                new SubscriptionManagerService(mContext, Looper.myLooper(), mFeatureFlags);
        processAllMessages();

        // 5. Verify: Plans are restored from disk
        // Note: We mock PackageManager again because mContext might be reset or reused depending on
        // test runner,
        // but here we just ensure the service can retrieve the data.
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());

        SubscriptionPlan[] restoredPlans =
                mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                        subId, CALLING_PACKAGE);

        assertThat(restoredPlans).isNotNull();
        assertThat(restoredPlans).asList().containsExactly(plan1, plan2);

        // Verify owner is preserved
        Field ownerMapField = SubscriptionManagerService.class
                .getDeclaredField("mEnrollableSubscriptionPlansOwner");
        ownerMapField.setAccessible(true);
        Map<Integer, String> ownerMap =
                (Map<Integer, String>) ownerMapField.get(mSubscriptionManagerServiceUT);
        assertThat(ownerMap.get(subId)).isEqualTo(CALLING_PACKAGE);

        SubscriptionPlan restoredPlan = restoredPlans[0];
        assertThat(restoredPlan.getId()).isEqualTo(1001);
        assertThat(restoredPlan.getTypes()).containsExactly(
                SubscriptionPlan.PLAN_TYPE_CELLULAR,
                SubscriptionPlan.PLAN_TYPE_PREPAID);
        assertThat(restoredPlan.getDataUsageResetTime())
                .isEqualTo(ZonedDateTime.parse("2025-01-15T00:00:00.000Z"));
        assertThat(restoredPlan.getStreamingAppMaxDownlinkKbps()).isEqualTo(5000);
        assertThat(restoredPlan.getStreamingAppMaxUplinkKbps()).isEqualTo(1000);
    }

    /**
     * Tests that the expiration time of enrollable plans is persisted and respected after a reboot.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlans_Persistence_WithExpiration() throws Exception {
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // Grant Permission
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = 1;
        SubscriptionPlan plan = createTestSubscriptionPlan("Expiring Persisted Plan");
        long expirationDuration = 10000; // 10 seconds

        // 1. Set plan with expiration
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, new SubscriptionPlan[] {plan}, expirationDuration, CALLING_PACKAGE);
        processAllMessages();

        // 2. Simulate Reboot immediately (before expiration)
        mSubscriptionManagerServiceUT =
                new SubscriptionManagerService(mContext, Looper.myLooper(), mFeatureFlags);
        processAllMessages();

        // 3. Verify: Plan is still valid and loaded
        assertThat(
                        mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                                subId, CALLING_PACKAGE))
                .asList()
                .containsExactly(plan);

        // 4. Advance time to make it expire (Simulate time passing after reboot)
        // The service should have rescheduled the expiration timer upon reload.
        moveTimeForward(expirationDuration + 1000);
        processAllMessages();

        // 5. Verify: Plan is expired and removed
        assertThat(
                        mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                                subId, CALLING_PACKAGE))
                .isNull();
    }

    /**
     * Tests that already expired plans are not loaded from disk upon reboot. (Simulates a device
     * that was off for a long time)
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlans_Persistence_AlreadyExpired() throws Exception {
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // Grant Permission
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = 1;
        SubscriptionPlan plan = createTestSubscriptionPlan("Already Expired Plan");
        long expirationDuration = 1000; // 1 second

        // 1. Set plan
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, new SubscriptionPlan[] {plan}, expirationDuration, CALLING_PACKAGE);
        processAllMessages();

        // Since moveTimeForward() only passes the Looper time and System.currentTimeMillis()
        // does not, use latch.await to pass the actual wall clock time.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(1100, TimeUnit.MILLISECONDS); // 1.1 second

        // 3. Simulate Reboot (Create new service)
        // The new service will read the XML. The saved expiration time (T_start + 5s)
        // should be smaller than the current time (T_start + 15s).
        mSubscriptionManagerServiceUT =
                new SubscriptionManagerService(mContext, Looper.myLooper(), mFeatureFlags);
        processAllMessages();

        // 4. Verify: The expired plan should NOT be loaded.
        // (readEnrollablePlansForSubscriptionLocked should skip it)
        assertThat(
                        mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                                subId, CALLING_PACKAGE))
                .isNull();
    }

    /**
     * Tests that plans with 0 expiration (volatile) are NOT persisted to disk.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollableSubscriptionPlans_Volatility() throws Exception {
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = 1;
        SubscriptionPlan plan = createTestSubscriptionPlan("Volatile Plan");

        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, new SubscriptionPlan[]{plan}, 0 /* volatile */, CALLING_PACKAGE);
        processAllMessages();

        assertThat(mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                subId, CALLING_PACKAGE)).isNotEmpty();

        mSubscriptionManagerServiceUT =
                new SubscriptionManagerService(mContext, Looper.myLooper(), mFeatureFlags);
        processAllMessages();

        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        assertThat(mSubscriptionManagerServiceUT.getEnrollableSubscriptionPlans(
                subId, CALLING_PACKAGE)).isNull();
    }

    /**
     * Tests that expiration timers are rescheduled when the system time changes.
     * Simulates a scenario where the system time jumps forward past the expiration time.
     */
    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testEnrollablePlans_RescheduleOnTimeChange() throws Exception {
        setupPackageManagerMocks(CALLING_PACKAGE, Process.myUid());
        // Grant Permission
        setManageSubscriptionPlansPermission(true);
        mContextFixture.addCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);

        int subId = 1;
        SubscriptionPlan plan = createTestSubscriptionPlan("Time Change Test");
        long duration = 3600 * 1000; // an hour

        // Act set plan with an hour expiration.
        mSubscriptionManagerServiceUT.setEnrollableSubscriptionPlans(
                subId, new SubscriptionPlan[]{plan}, duration, CALLING_PACKAGE);
        processAllMessages();

        // Verify that plan was set properly
        assertThat(mSubscriptionManagerServiceUT
                .getEnrollableSubscriptionPlans(subId, CALLING_PACKAGE)).isNotEmpty();

        // Time Change Simulation (Expiration Time Manipulation)
        // Change the saved expiration time to '10 seconds ago'.
        Field expirationMapField = SubscriptionManagerService.class
                .getDeclaredField("mEnrollablePlanExpirationTime");
        expirationMapField.setAccessible(true);
        Map<Integer, Long> expirationMap =
                (Map<Integer, Long>) expirationMapField.get(mSubscriptionManagerServiceUT);
        expirationMap.put(subId, System.currentTimeMillis() - 10000);

        mContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
        processAllMessages();

        processAllMessages();
        assertThat(mSubscriptionManagerServiceUT
                .getEnrollableSubscriptionPlans(subId, CALLING_PACKAGE)).isNull();
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void testNotifyOnImsNumberChange() {
        int subId = 1;
        insertSubscription(new SubscriptionInfoInternal.Builder()
                .setId(subId).setIccId(FAKE_ICCID1).setSimSlotIndex(0).build());
        processAllMessages();

        // 1. Initial success - should notify
        clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setImsNumberUpdateStatus(subId, true);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));

        // 2. Setting same status - should NOT notify
        clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setImsNumberUpdateStatus(subId, true);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback, never()).onSubscriptionChanged(anyInt());

        // 3. Status changed to failed - should notify
        clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.setImsNumberUpdateStatus(subId, false);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));

        // 4. Status cleared - should notify
        clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.clearImsNumberUpdateStatus(subId);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback).onSubscriptionChanged(eq(subId));

        // 5. Status cleared again (was already gone) - should NOT notify
        clearInvocations(mMockedSubscriptionManagerServiceCallback);
        mSubscriptionManagerServiceUT.clearImsNumberUpdateStatus(subId);
        processAllMessages();
        verify(mMockedSubscriptionManagerServiceCallback, never()).onSubscriptionChanged(anyInt());
    }
}

