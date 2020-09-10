/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.permission.PermissionManager;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.pm.permission.PermissionManagerService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Map;

@SmallTest
public class TelephonyPermissionsTest {

    private static final int SUB_ID = 55555;
    private static final int SUB_ID_2 = 22222;
    private static final int PID = Binder.getCallingPid();
    private static final int UID = Binder.getCallingUid();
    private static final String PACKAGE = "com.example";
    private static final String FEATURE = "com.example.feature";
    private static final String MSG = "message";

    @Mock
    private Context mMockContext;
    @Mock
    private AppOpsManager mMockAppOps;
    @Mock
    private SubscriptionManager mMockSubscriptionManager;
    @Mock
    private ITelephony mMockTelephony;
    @Mock
    private IBinder mMockTelephonyBinder;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private ApplicationInfo mMockApplicationInfo;
    @Mock
    private TelephonyManager mTelephonyManagerMock;
    @Mock
    private TelephonyManager mTelephonyManagerMockForSub1;
    @Mock
    private TelephonyManager mTelephonyManagerMockForSub2;
    @Mock
    private PermissionManagerService mMockPermissionManagerService;

    private MockContentResolver mMockContentResolver;
    private FakeSettingsConfigProvider mFakeSettingsConfigProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(
                mTelephonyManagerMock);
        when(mTelephonyManagerMock.createForSubscriptionId(anyInt())).thenReturn(
                mTelephonyManagerMock);
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mMockAppOps);
        when(mMockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)).thenReturn(
                mMockSubscriptionManager);
        when(mMockSubscriptionManager.getCompleteActiveSubscriptionIdList()).thenReturn(
                new int[]{SUB_ID});

        PermissionManager permissionManager = new PermissionManager(mMockContext, null,
                mMockPermissionManagerService);
        when(mMockContext.getSystemService(Context.PERMISSION_SERVICE)).thenReturn(
                permissionManager);

        // By default, assume we have no permissions or app-ops bits.
        doThrow(new SecurityException()).when(mMockContext)
                .enforcePermission(anyString(), eq(PID), eq(UID), eq(MSG));
        doThrow(new SecurityException()).when(mMockContext)
                .enforcePermission(anyString(), eq(PID), eq(UID), eq(MSG));
        when(mMockAppOps.noteOp(anyString(), eq(UID), eq(PACKAGE), eq(FEATURE),
                nullable(String.class))).thenReturn(AppOpsManager.MODE_ERRORED);
        when(mMockAppOps.noteOpNoThrow(anyString(), eq(UID), eq(PACKAGE), eq(FEATURE),
                nullable(String.class))).thenReturn(AppOpsManager.MODE_ERRORED);
        when(mMockTelephony.getCarrierPrivilegeStatusForUid(eq(SUB_ID), eq(UID)))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        when(mMockTelephony.getCarrierPrivilegeStatusForUid(eq(SUB_ID_2), eq(UID)))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        when(mMockContext.checkPermission(android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE,
                PID, UID)).thenReturn(PackageManager.PERMISSION_DENIED);
        setTelephonyMockAsService();
    }

    @Test
    public void testCheckReadPhoneState_noPermissions() {
        try {
            TelephonyPermissions.checkReadPhoneState(
                    mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckReadPhoneState_hasPrivilegedPermission() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE, PID, UID, MSG);
        assertTrue(TelephonyPermissions.checkReadPhoneState(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneState_hasPermissionAndAppOp() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_STATE, PID, UID, MSG);
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), eq(UID), eq(PACKAGE),
                eq(FEATURE), nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneState(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneState_hasPermissionWithoutAppOp() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_STATE, PID, UID, MSG);
        assertFalse(TelephonyPermissions.checkReadPhoneState(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneState_hasCarrierPrivileges() throws Exception {
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID))).thenReturn(
                mTelephonyManagerMockForSub1);
        when(mTelephonyManagerMockForSub1.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        assertTrue(TelephonyPermissions.checkReadPhoneState(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneStateOnAnyActiveSub_noPermissions() {
        assertFalse(TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                mMockContext, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneStateOnAnyActiveSub_hasPrivilegedPermission() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE, PID, UID, MSG);
        assertTrue(TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                mMockContext, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneStateOnAnyActiveSub_hasPermissionAndAppOp() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_STATE, PID, UID, MSG);
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_READ_PHONE_STATE), eq(UID), eq(PACKAGE),
                eq(FEATURE), nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                mMockContext, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneStateOnAnyActiveSub_hasPermissionWithoutAppOp() {
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_STATE, PID, UID, MSG);
        assertFalse(TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                mMockContext, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneStateOnAnyActiveSub_hasCarrierPrivileges() throws Exception {
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID))).thenReturn(
                mTelephonyManagerMockForSub1);
        when(mTelephonyManagerMockForSub1.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);

        assertTrue(TelephonyPermissions.checkReadPhoneStateOnAnyActiveSub(
                mMockContext, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_noPermissions() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        try {
            TelephonyPermissions.checkReadPhoneNumber(
                    mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckReadPhoneNumber_defaultSmsApp() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_WRITE_SMS), eq(UID), eq(PACKAGE),
                eq(FEATURE), nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneNumber(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_hasPrivilegedPhoneStatePermission() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE, PID, UID, MSG);
        assertTrue(TelephonyPermissions.checkReadPhoneNumber(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_hasReadSms() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_SMS, PID, UID, MSG);
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_READ_SMS), eq(UID), eq(PACKAGE), eq(FEATURE),
                nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneNumber(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_hasReadPhoneNumbers() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_NUMBERS, PID, UID, MSG);
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_READ_PHONE_NUMBERS), eq(UID), eq(PACKAGE),
                eq(FEATURE), nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneNumber(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_hasReadSmsNoAppop() throws Exception {
        // If an app has been granted the READ_SMS permission, but the OPSTR_READ_SMS appop has been
        // revoked then instead of immediately returning false the phone number access check should
        // check if the caller has the READ_PHONE_NUMBERS permission and appop.
        setupMocksForDeviceIdentifiersErrorPath();
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_SMS, PID, UID, MSG);
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_NUMBERS, PID, UID, MSG);
        when(mMockAppOps.noteOp(eq(AppOpsManager.OPSTR_READ_PHONE_NUMBERS), eq(UID), eq(PACKAGE),
                eq(FEATURE), nullable(String.class))).thenReturn(AppOpsManager.MODE_ALLOWED);
        assertTrue(TelephonyPermissions.checkReadPhoneNumber(
                mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadPhoneNumber_hasReadSmsAndReadPhoneNumbersNoAppops() throws Exception {
        // If an app has both the READ_SMS and READ_PHONE_NUMBERS permissions granted but does not
        // have the corresponding appops instead of returning false for not having the appop granted
        // a SecurityException should be thrown.
        setupMocksForDeviceIdentifiersErrorPath();
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_SMS, PID, UID, MSG);
        doNothing().when(mMockContext).enforcePermission(
                android.Manifest.permission.READ_PHONE_NUMBERS, PID, UID, MSG);
        try {
            TelephonyPermissions.checkReadPhoneNumber(
                    mMockContext, SUB_ID, PID, UID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckReadDeviceIdentifiers_noPermissions() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        try {
            TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                    SUB_ID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasPermissionManagerIdentifierAccess() {
        // The UID, privileged permission, device / profile owner, and appop checks are all now
        // performed by a SystemAPI in PermissionManager; this test verifies when this API returns
        // the calling package meets the requirements for device identifier access the telephony
        // check also returns true.
        when(mMockPermissionManagerService.checkDeviceIdentifierAccess(PACKAGE, MSG, FEATURE, PID,
                UID)).thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(
                TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasCarrierPrivileges() throws Exception {
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID))).thenReturn(
                mTelephonyManagerMockForSub1);
        when(mTelephonyManagerMockForSub1.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        assertTrue(
                TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasReadPhoneStateTargetQ() throws Exception {
        // if an app is targeting Q and does not meet the new requirements for device identifier
        // access then a SecurityException should be thrown even if the app has been granted the
        // READ_PHONE_STATE permission.
        when(mMockContext.checkPermission(android.Manifest.permission.READ_PHONE_STATE, PID,
                UID)).thenReturn(PackageManager.PERMISSION_GRANTED);
        setupMocksForDeviceIdentifiersErrorPath();
        try {
            TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                    SUB_ID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasReadPhoneStateTargetPreQ() throws Exception {
        // To prevent breaking existing apps if an app is targeting pre-Q and has been granted the
        // READ_PHONE_STATE permission then checkReadDeviceIdentifiers should return false to
        // indicate the caller should return null / placeholder data.
        when(mMockContext.checkPermission(android.Manifest.permission.READ_PHONE_STATE, PID,
                UID)).thenReturn(PackageManager.PERMISSION_GRANTED);
        setupMocksForDeviceIdentifiersErrorPath();
        mMockApplicationInfo.targetSdkVersion = Build.VERSION_CODES.P;
        assertFalse(
                TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasCarrierPrivilegesOnOtherSubscription()
            throws Exception {
        when(mMockSubscriptionManager.getCompleteActiveSubscriptionIdList()).thenReturn(
                new int[]{SUB_ID, SUB_ID_2});
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID_2))).thenReturn(
                mTelephonyManagerMockForSub2);
        when(mTelephonyManagerMockForSub2.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        assertTrue(
                TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadDeviceIdentifiers_hasAppOpNullSubscription() {
        // The appop check comes after the carrier privilege check; this test verifies if the
        // SubscriptionManager returns an empty array for the active subscription IDs this check can
        // still proceed to check if the calling package has the appop and any subsequent checks
        // without a NullPointerException.
        when(mMockSubscriptionManager.getCompleteActiveSubscriptionIdList())
                .thenReturn(new int[0]);
        when(mMockAppOps.noteOpNoThrow(eq(AppOpsManager.OPSTR_READ_DEVICE_IDENTIFIERS), eq(UID),
                eq(PACKAGE), eq(FEATURE), nullable(String.class))).thenReturn(
                AppOpsManager.MODE_ALLOWED);
        assertTrue(
                TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckReadDeviceIdentifiers_nullPackageName() throws Exception {
        // If a null package name is passed in then the AppOp and DevicePolicyManager checks cannot
        // be performed, but an app targeting Q should still receive a SecurityException in this
        // case.
        setupMocksForDeviceIdentifiersErrorPath();
        try {
            TelephonyPermissions.checkCallingOrSelfReadDeviceIdentifiers(mMockContext,
                    SUB_ID, null, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCallingOrSelfReadSubscriberIdentifiers_noPermissions() throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        setTelephonyMockAsService();
        try {
            TelephonyPermissions.checkCallingOrSelfReadSubscriberIdentifiers(mMockContext,
                    SUB_ID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    @Test
    public void testCheckCallingOrSelfReadSubscriberIdentifiers_carrierPrivileges()
            throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        setTelephonyMockAsService();
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID))).thenReturn(
                mTelephonyManagerMockForSub1);
        when(mTelephonyManagerMockForSub1.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        assertTrue(
                TelephonyPermissions.checkCallingOrSelfReadSubscriberIdentifiers(mMockContext,
                        SUB_ID, PACKAGE, FEATURE, MSG));
    }

    @Test
    public void testCheckCallingOrSelfReadSubscriberIdentifiers_carrierPrivilegesOnOtherSub()
            throws Exception {
        setupMocksForDeviceIdentifiersErrorPath();
        setTelephonyMockAsService();
        when(mMockSubscriptionManager.getCompleteActiveSubscriptionIdList()).thenReturn(
                new int[]{SUB_ID, SUB_ID_2});
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID_2))).thenReturn(
                mTelephonyManagerMockForSub2);
        when(mTelephonyManagerMockForSub2.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        // Carrier privilege on the other active sub shouldn't allow access to this sub.
        try {
            TelephonyPermissions.checkCallingOrSelfReadSubscriberIdentifiers(mMockContext,
                    SUB_ID, PACKAGE, FEATURE, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Validate the SecurityException will be thrown if call the method without permissions, nor
     * privileges.
     */
    @Test
    public void
    testEnforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege_noPermissions()
            throws Exception {
        // revoke permission READ_PRIVILEGED_PHONE_STATE
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        // revoke permision READ_PRECISE_PHONE_STATE
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        try {
            TelephonyPermissions
                    .enforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                    mMockContext, SUB_ID, MSG);
            fail("Should have thrown SecurityException");
        } catch (SecurityException se) {
            // expected
        }
    }

    /**
     * Validate that no SecurityException thrown when we have either permission
     * READ_PRECISE_PHONE_STATE or READ_PRIVILEGED_PHONE_STATE.
     */
    @Test
    public void
    testEnforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege_withPermissions()
            throws Exception {
        // grant READ_PRIVILEGED_PHONE_STATE permission
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        try {
            TelephonyPermissions
                    .enforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                    mMockContext, SUB_ID, MSG);
        } catch (SecurityException se) {
            fail();
        }

        // revoke permission READ_PRIVILEGED_PHONE_STATE
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        // grant READ_PRECISE_PHONE_STATE permission
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_GRANTED);
        try {
            TelephonyPermissions
                    .enforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                    mMockContext, SUB_ID, MSG);
        } catch (SecurityException se) {
            fail();
        }
    }

    /**
     * Validate that no SecurityException thrown when we have carrier privileges.
     */
    @Test
    public void
    testEnforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege_withPrivileges()
            throws Exception {
        // revoke permission READ_PRIVILEGED_PHONE_STATE
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);
        // revoke permision READ_PRECISE_PHONE_STATE
        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_PRECISE_PHONE_STATE)).thenReturn(
                PackageManager.PERMISSION_DENIED);

        setTelephonyMockAsService();
        when(mTelephonyManagerMock.createForSubscriptionId(eq(SUB_ID))).thenReturn(
                mTelephonyManagerMockForSub1);
        when(mTelephonyManagerMockForSub1.getCarrierPrivilegeStatus(anyInt())).thenReturn(
                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        try {
            TelephonyPermissions
                    .enforeceCallingOrSelfReadPrecisePhoneStatePermissionOrCarrierPrivilege(
                    mMockContext, SUB_ID, MSG);
        } catch (SecurityException se) {
            fail("Should NOT throw SecurityException");
        }
    }

    // Put mMockTelephony into service cache so that TELEPHONY_SUPPLIER will get it.
    private void setTelephonyMockAsService() throws Exception {
        when(mMockTelephonyBinder.queryLocalInterface(anyString())).thenReturn(mMockTelephony);
        Field field = ServiceManager.class.getDeclaredField("sCache");
        field.setAccessible(true);
        ((Map<String, IBinder>) field.get(null)).put(Context.TELEPHONY_SERVICE,
                mMockTelephonyBinder);
    }

    public static class FakeSettingsConfigProvider extends FakeSettingsProvider {
        private static final String PROPERTY_DEVICE_IDENTIFIER_ACCESS_RESTRICTIONS_DISABLED =
                DeviceConfig.NAMESPACE_PRIVACY + "/"
                        + "device_identifier_access_restrictions_disabled";

        @Override
        public Bundle call(String method, String arg, Bundle extras) {
            switch (method) {
                case Settings.CALL_METHOD_GET_CONFIG: {
                    switch (arg) {
                        case PROPERTY_DEVICE_IDENTIFIER_ACCESS_RESTRICTIONS_DISABLED: {
                            Bundle bundle = new Bundle();
                            bundle.putString(
                                    PROPERTY_DEVICE_IDENTIFIER_ACCESS_RESTRICTIONS_DISABLED,
                                    "0");
                            return bundle;
                        }
                        default: {
                            fail("arg not expected: " + arg);
                        }
                    }
                    break;
                }
                // If this is not a get call for Settings.Config then use the FakeSettingsProvider's
                // call method.
                default:
                    return super.call(method, arg, extras);
            }
            return null;
        }
    }

    protected void setupMocksForDeviceIdentifiersErrorPath() throws Exception {
        // If the calling package does not meet the new requirements for device identifier access
        // TelephonyPermissions will query the PackageManager for the ApplicationInfo of the package
        // to determine the target SDK. For apps targeting Q a SecurityException is thrown
        // regardless of if the package satisfies the previous requirements for device ID access.
        mMockApplicationInfo.targetSdkVersion = Build.VERSION_CODES.Q;
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getApplicationInfoAsUser(eq(PACKAGE), anyInt(), any()))
            .thenReturn(mMockApplicationInfo);

        when(mMockContext.checkCallingOrSelfPermission(
                android.Manifest.permission.READ_DEVICE_CONFIG)).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        when(mMockPermissionManagerService.checkDeviceIdentifierAccess(any(), any(), any(),
                anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);

        // TelephonyPermissions queries DeviceConfig to determine if the identifier access
        // restrictions should be enabled; since DeviceConfig uses
        // Activity.currentActivity.getContentResolver as the resolver for Settings.Config.getString
        // the READ_DEVICE_CONFIG permission check cannot be mocked, so replace the IContentProvider
        // in the NameValueCache's provider holder with that from the fake provider.
        mFakeSettingsConfigProvider = new FakeSettingsConfigProvider();
        mMockContentResolver = new MockContentResolver();
        mMockContentResolver.addProvider(Settings.AUTHORITY, mFakeSettingsConfigProvider);
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);

        Class c = Class.forName("android.provider.Settings$Config");
        Field field = c.getDeclaredField("sNameValueCache");
        field.setAccessible(true);
        Object cache = field.get(null);

        c = Class.forName("android.provider.Settings$NameValueCache");
        field = c.getDeclaredField("mProviderHolder");
        field.setAccessible(true);
        Object providerHolder = field.get(cache);

        field = MockContentProvider.class.getDeclaredField("mIContentProvider");
        field.setAccessible(true);
        Object iContentProvider = field.get(mFakeSettingsConfigProvider);

        c = Class.forName("android.provider.Settings$ContentProviderHolder");
        field = c.getDeclaredField("mContentProvider");
        field.setAccessible(true);
        field.set(providerHolder, iContentProvider);
    }

}
