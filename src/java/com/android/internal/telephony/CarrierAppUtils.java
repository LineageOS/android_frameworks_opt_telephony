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

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Utilities for handling carrier applications.
 * @hide
 */
public final class CarrierAppUtils {
    private static final String TAG = "CarrierAppUtils";

    private static final boolean DEBUG = false; // STOPSHIP if true

    private CarrierAppUtils() {}

    /**
     * Handle preinstalled carrier apps which should be disabled until a matching SIM is inserted.
     *
     * Evaluates the list of applications in config_disabledUntilUsedPreinstalledCarrierApps. We
     * want to disable each such application which is present on the system image until the user
     * inserts a SIM which causes that application to gain carrier privilege (indicating a "match"),
     * without interfering with the user if they opt to enable/disable the app explicitly.
     *
     * So, for each such app, we either disable until used IFF the app is not carrier privileged AND
     * in the default state (e.g. not explicitly DISABLED/DISABLED_BY_USER/ENABLED), or we enable if
     * the app is carrier privileged and in either the default state or DISABLED_UNTIL_USED.
     *
     * This method is idempotent and is safe to be called at any time; it should be called once at
     * system startup prior to any application running, as well as any time the set of carrier
     * privileged apps may have changed.
     */
    public synchronized static void disableCarrierAppsUntilPrivileged(String callingPackage,
            IPackageManager packageManager, TelephonyManager telephonyManager, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "disableCarrierAppsUntilPrivileged");
        }
        String[] systemCarrierAppsDisabledUntilUsed = Resources.getSystem().getStringArray(
                com.android.internal.R.array.config_disabledUntilUsedPreinstalledCarrierApps);
        disableCarrierAppsUntilPrivileged(callingPackage, packageManager, telephonyManager, userId,
                systemCarrierAppsDisabledUntilUsed);
    }

    // Must be public b/c framework unit tests can't access package-private methods.
    @VisibleForTesting
    public static void disableCarrierAppsUntilPrivileged(String callingPackage,
            IPackageManager packageManager, TelephonyManager telephonyManager, int userId,
            String[] systemCarrierAppsDisabledUntilUsed) {
        if (systemCarrierAppsDisabledUntilUsed == null
                || systemCarrierAppsDisabledUntilUsed.length == 0) {
            return;
        }
        try {
            for (String packageName : systemCarrierAppsDisabledUntilUsed) {
                ApplicationInfo ai = packageManager.getApplicationInfo(packageName,
                        PackageManager.GET_DISABLED_UNTIL_USED_COMPONENTS, userId);
                if (ai == null) {
                    // No app found for packageName
                    continue;
                }
                boolean isSystemPackage = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (!isSystemPackage) {
                    continue;
                }
                boolean hasPrivileges =
                        telephonyManager.checkCarrierPrivilegesForPackageAnyPhone(packageName) ==
                                TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
                if (hasPrivileges
                        && (ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        || ai.enabledSetting ==
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)) {
                    Slog.i(TAG, "Update state(" + packageName + "): ENABLED for user " + userId);
                    packageManager.setApplicationEnabledSetting(packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0, userId,
                            callingPackage);
                } else if (!hasPrivileges
                        && ai.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    Slog.i(TAG, "Update state(" + packageName + "): DISABLED_UNTIL_USED for user "
                            + userId);
                    packageManager.setApplicationEnabledSetting(packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED, 0, userId,
                            callingPackage);
                }
            }
        } catch (RemoteException e) {
            Slog.w(TAG, "Could not reach PackageManager", e);
        }
    }
}
