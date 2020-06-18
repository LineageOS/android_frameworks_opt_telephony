/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.internal.telephony;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CarrierAssociatedAppEntry;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarrierAppUtilsTest {
    private static final String CARRIER_APP = "com.example.carrier";
    private static final ArraySet<String> CARRIER_APPS = new ArraySet<>();
    static {
        CARRIER_APPS.add(CARRIER_APP);
    }

    private static final String ASSOCIATED_APP = "com.example.associated";
    private static final ArrayMap<String, List<CarrierAssociatedAppEntry>> ASSOCIATED_APPS =
            makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP,
                    CarrierAssociatedAppEntry.SDK_UNSPECIFIED);
    private static final int USER_ID = 12345;
    private static final String CALLING_PACKAGE = "phone";

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private TelephonyManager mTelephonyManager;
    private SettingsMockContentProvider mContentProvider;
    private MockContentResolver mContentResolver;

    private static ArrayMap<String, List<CarrierAssociatedAppEntry>> makeAssociatedApp(
            String carrierAppPackage, String associatedAppPackage, int associatedAppAddedInSdk) {
        ArrayMap<String, List<CarrierAssociatedAppEntry>> result = new ArrayMap<>();
        List<CarrierAssociatedAppEntry> associatedAppList = new ArrayList<>();
        associatedAppList.add(
                new CarrierAssociatedAppEntry(associatedAppPackage, associatedAppAddedInSdk));
        result.put(carrierAppPackage, associatedAppList);
        return result;
    }

    @Before
    public void setUp() throws Exception {
        System.setProperty("dexmaker.dexcache",
                InstrumentationRegistry.getTargetContext().getCacheDir().getPath());
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        MockitoAnnotations.initMocks(this);

        Mockito.when(mContext.createContextAsUser(Mockito.any(UserHandle.class), Mockito.eq(0)))
                .thenReturn(mContext);
        Mockito.when(mContext.createPackageContextAsUser(Mockito.anyString(), Mockito.eq(0),
                Mockito.any(UserHandle.class))).thenReturn(mContext);
        Mockito.when(mContext.getPackageManager()).thenReturn(mPackageManager);
        Mockito.when(mContext.getPackageName()).thenReturn(CALLING_PACKAGE);
        // Placeholder, cannot mock final PermissionManager

        mContentResolver = new MockContentResolver();
        mContentProvider = new SettingsMockContentProvider();
        mContentResolver.addProvider(Settings.AUTHORITY, mContentProvider);
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED, 0, USER_ID);
        Mockito.when(mContext.getContentResolver()).thenReturn(mContentResolver);
    }

    /** No apps configured - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_EmptyList() {
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, new ArraySet<>(), ASSOCIATED_APPS, mContext);
        Mockito.verifyNoMoreInteractions(mPackageManager, mTelephonyManager);
    }

    /** Configured app is missing - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_MissingApp() throws Exception {
        Mockito.when(mPackageManager.getApplicationInfo("com.example.missing.app",
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(null);
        ArraySet<String> systemCarrierAppsDisabledUntilUsed = new ArraySet<>();
        systemCarrierAppsDisabledUntilUsed.add("com.example.missing.app");
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, systemCarrierAppsDisabledUntilUsed, ASSOCIATED_APPS,
                mContext);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
        Mockito.verifyNoMoreInteractions(mTelephonyManager);
    }

    /** Configured app is not bundled with the system - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NonSystemApp() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
        Mockito.verifyNoMoreInteractions(mTelephonyManager);
    }

    /**
     * Configured app has privileges, but was disabled by the user - should only grant
     * permissions.
     */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_DisabledUser()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);

        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /** Configured app has privileges, but was disabled - should only grant permissions. */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_Disabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Configured app has privileges, and is already installed - should only grant permissions. */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_Enabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Configured /data app has privileges - should only grant permissions. */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_UpdatedApp() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(appInfo);
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /**
     * Configured app has privileges, and is in the default state - should install. Associated app
     * is missing and should not be touched.
     */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_MissingAssociated_Default()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY)).thenReturn(appInfo);
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
    }

    /**
     * Configured app has privileges, and is in the default state along with associated app - should
     * install both.
     */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_Associated_Default()
            throws Exception {
        // Enabling should be done even if this isn't the first run.
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED, Build.VERSION.SDK_INT,
                USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
    }

    /**
     * Configured app has privileges, and is uninstalled - should install. Associated app has
     * been updated and should not be touched.
     */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_UpdatedAssociated_DisabledUntilUsed()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(CARRIER_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |=
                ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(ASSOCIATED_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(null);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
    }

    /**
     * Configured app has privileges, and is uninstalled until used along with associated app -
     * should install both.
     */
    @Test @SmallTest @Ignore
    public void testDisableCarrierAppsUntilPrivileged_HasPrivileges_Associated_DisabledUntilUsed()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(CARRIER_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(ASSOCIATED_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
    }

    /** Configured app has no privileges, and was disabled by the user - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_DisabledUser() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Telephony is not initialized, and app was disabled by the user - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_DisabledUser()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Configured app has no privileges, and was uninstalled - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_Disabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Telephony is not initialized, and app was uninstalled - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_Disabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Configured app has no privileges, and is explicitly installed - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_Enabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Telephony is not initialized, and app is explicitly installed - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_Enabled() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Configured /data app has no privileges - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_UpdatedApp() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Telephony is not initialized and app is in /data - should do nothing. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_UpdatedApp() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= (ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP | ApplicationInfo.FLAG_INSTALLED);
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /**
     * Configured app has no privileges, and is in the default state - should uninstalled.
     * Associated app is installed and should not be touched.
     */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_EnabledAssociated_Default()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(CARRIER_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(ASSOCIATED_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app -
     * should uninstall both.
     */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_Associated_Default()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(CARRIER_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager.getApplicationEnabledSetting(ASSOCIATED_APP))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has already occurred - should only uninstall configured app.
     */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_Associated_Default_HandledSdk()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED, Build.VERSION.SDK_INT,
                USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Different associated app SDK than usual.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS,
                makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP, Build.VERSION.SDK_INT), mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has already occurred - should only uninstall configured app.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_HandledSdk_AssociatedSdkUnspecified()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED, Build.VERSION.SDK_INT,
                USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Using SDK_UNSPECIFIED for the associated app's addedInSdk.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred on this SDK level - should uninstall both since the associated
     * app's SDK matches.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_NewSdk_AssociatedSdkCurrent()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED,
                Build.VERSION.SDK_INT - 1, USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Different associated app SDK than usual.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS,
                makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP, Build.VERSION.SDK_INT), mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred on the current SDK - should only uninstall configured app
     * since the associated app's SDK isn't specified but we've already run at least once.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_NewSdk_AssociatedSdkUnspecified()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED,
                Build.VERSION.SDK_INT - 1, USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Using SDK_UNSPECIFIED for the associated app's addedInSdk.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred on the current SDK - should only uninstall configured app
     * since the associated app's SDK doesn't match.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_NewSdk_AssociatedSdkTooLow()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED,
                Build.VERSION.SDK_INT - 1, USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Different associated app SDK than usual.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS,
                makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP, Build.VERSION.SDK_INT - 1),
                mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred on this SDK level - should uninstall both since the associated
     * app's SDK is newer than the last evaluation.
     *
     * While this case is expected to feel somewhat strange, it effectively simulates skipping a
     * whole SDK level in a single OTA. For example, the device is on P. A new associated app is
     * added on Q, but the user doesn't take the OTA. Then, they take the R OTA, at which point the
     * associated app should still be disabled if there's no corresponding SIM, because its SDK
     * level is newer than our last round of evaluation.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_NewSdk_AssociatedSdkInRange()
            throws Exception {
        Settings.Secure.putIntForUser(
                mContentResolver, Settings.Secure.CARRIER_APPS_HANDLED,
                Build.VERSION.SDK_INT - 2, USER_ID);
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Different associated app SDK than usual.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS,
                makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP, Build.VERSION.SDK_INT - 1),
                mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred ever - should uninstall both regardless of associated app's
     * SDK.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_FirstRun_AssociatedSdkCurrent()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Different associated app SDK than usual.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS,
                makeAssociatedApp(CARRIER_APP, ASSOCIATED_APP, Build.VERSION.SDK_INT), mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /**
     * Configured app has no privileges, and is in the default state along with associated app, and
     * disabling has not yet occurred ever - should uninstall both regardless of associated app's
     * SDK.
     */
    @Test @SmallTest
    public void testDCAUP_NoPrivileges_Associated_Default_FirstRun_AssociatedSdkUnspecified()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        ApplicationInfo associatedAppInfo = new ApplicationInfo();
        associatedAppInfo.packageName = ASSOCIATED_APP;
        associatedAppInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        associatedAppInfo.enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        Mockito.when(mPackageManager.getApplicationInfo(ASSOCIATED_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(associatedAppInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        // Using SDK_UNSPECIFIED for the associated app's addedInSdk.
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_INSTALLED);
        Mockito.verify(mPackageManager).setSystemAppState(ASSOCIATED_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /** Telephony is not initialized, and app is in the default state - should uninstall it. */
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_Default() throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_UNINSTALLED);
    }

    /** Configured app has no privileges, and is disabled until used or not installed - should do
     *  nothing.
     **/
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NoPrivileges_DisabledUntilUsed()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        Mockito.when(mTelephonyManager.checkCarrierPrivilegesForPackageAnyPhone(CARRIER_APP))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_NO_ACCESS);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE, mTelephonyManager,
                mContentResolver, USER_ID, CARRIER_APPS, ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    /** Telephony is not initialized, and app is disabled until used or not installed - should do
     *  nothing.
     **/
    @Test @SmallTest
    public void testDisableCarrierAppsUntilPrivileged_NullPrivileges_DisabledUntilUsed()
            throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = CARRIER_APP;
        appInfo.flags |= ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_INSTALLED;
        Mockito.when(mPackageManager
                .getApplicationEnabledSetting(Mockito.anyString()))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
        Mockito.when(mPackageManager.getApplicationInfo(CARRIER_APP,
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
                        | PackageManager.MATCH_HIDDEN_UNTIL_INSTALLED_COMPONENTS
                        | PackageManager.MATCH_SYSTEM_ONLY))
                .thenReturn(appInfo);
        CarrierAppUtils.disableCarrierAppsUntilPrivileged(CALLING_PACKAGE,
                null /* telephonyManager */, mContentResolver, USER_ID, CARRIER_APPS,
                ASSOCIATED_APPS, mContext);
        Mockito.verify(mPackageManager).setSystemAppState(CARRIER_APP,
                PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_INSTALLED));
        Mockito.verify(mPackageManager, Mockito.never()).setSystemAppState(Mockito.anyString(),
                Mockito.eq(PackageManager.SYSTEM_APP_STATE_UNINSTALLED));
    }

    class SettingsMockContentProvider extends MockContentProvider {
        private int mExpectedValue;

        @Override
        public Bundle call(String method, String request, Bundle args) {
            Bundle result = new Bundle();
            if (Settings.CALL_METHOD_GET_SECURE.equals(method)) {
                result.putString(Settings.NameValueTable.VALUE, Integer.toString(mExpectedValue));
            } else {
                mExpectedValue = Integer.parseInt(args.getString(Settings.NameValueTable.VALUE));
            }
            return result;
        }
    }

}

