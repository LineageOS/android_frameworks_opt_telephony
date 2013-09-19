/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.provider.Settings;
import android.provider.Telephony.Sms.Intents;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Class for managing the primary application that we will deliver SMS/MMS messages to
 *
 * {@hide}
 */
public final class SmsApplication {
    public static class SmsApplicationData {
        /**
         * Name of this SMS app for display.
         */
        public String mApplicationName;

        /**
         * Package name for this SMS app.
         */
        public String mPackageName;

        /**
         * The class name of the SMS receiver in this app.
         */
        public String mSmsReceiverClass;

        /**
         * The class name of the MMS receiver in this app.
         */
        public String mMmsReceiverClass;

        /**
         * The user-id for this application
         */
        public int mUid;

        public SmsApplicationData(String applicationName, String packageName,
                String smsReceiverName, String mmsReceiverName, int uid) {
            mApplicationName = applicationName;
            mPackageName = packageName;
            mSmsReceiverClass = smsReceiverName;
            mMmsReceiverClass = mmsReceiverName;
            mUid = uid;
        }
    }

    /**
     * Returns the list of available SMS apps defined as apps that are registered for both the
     * SMS_RECEIVED_ACTION (SMS) and WAP_PUSH_RECEIVED_ACTION (MMS) broadcasts (and their broadcast
     * receivers are enabled)
     */
    public static Collection<SmsApplicationData> getApplicationCollection(Context context) {
        PackageManager packageManager = context.getPackageManager();

        // Get the list of apps registered for SMS
        Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
        int flags = 0;
        List<ResolveInfo> smsReceivers = packageManager.queryBroadcastReceivers(intent, flags);

        intent = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
        intent.setDataAndType(null, "application/vnd.wap.mms-message");
        List<ResolveInfo> mmsReceivers = packageManager.queryBroadcastReceivers(intent, flags);

        HashMap<String, SmsApplicationData> receivers = new HashMap<String, SmsApplicationData>();

        // Add one entry to the map for every sms receiver (ignoring duplicate sms receivers)
        for (ResolveInfo r : smsReceivers) {
            String packageName = r.activityInfo.packageName;
            if (!receivers.containsKey(packageName)) {
                String applicationName = r.loadLabel(packageManager).toString();
                SmsApplicationData smsApplicationData = new SmsApplicationData(applicationName,
                        packageName, r.activityInfo.name, null, r.activityInfo.applicationInfo.uid);
                receivers.put(packageName, smsApplicationData);
            }
        }

        // Update any existing entries with mms receiver class
        for (ResolveInfo r : mmsReceivers) {
            String packageName = r.activityInfo.packageName;
            SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null) {
                smsApplicationData.mMmsReceiverClass = r.activityInfo.name;
            }
        }

        // Remove any entries (which we added for sms receivers) for which we did not also find
        // valid mms receivers
        for (ResolveInfo r : smsReceivers) {
            String packageName = r.activityInfo.packageName;
            SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null && smsApplicationData.mMmsReceiverClass == null) {
                receivers.remove(packageName);
            }
        }
        return receivers.values();
    }

    /**
     * Checks to see if we have a valid installed SMS application for the specified package name
     * @return Data for the specified package name or null if there isn't one
     */
    private static SmsApplicationData getApplicationForPackage(
            Collection<SmsApplicationData> applications, String packageName) {
        if (packageName == null) {
            return null;
        }
        // Is there an entry in the application list for the specified package?
        for (SmsApplicationData application : applications) {
            if (application.mPackageName.contentEquals(packageName)) {
                return application;
            }
        }
        return null;
    }

    /**
     * Get the application we will use for delivering SMS/MMS messages.
     *
     * We return the preferred sms application with the following order of preference:
     * (1) User selected SMS app (if selected, and if still valid)
     * (2) Android Messaging (if installed)
     * (3) The currently configured highest priority broadcast receiver
     * (4) Null
     */
    private static SmsApplicationData getApplication(Context context, boolean updateIfNeeded) {
        Collection<SmsApplicationData> applications = getApplicationCollection(context);

        // Determine which application receives the broadcast
        String defaultApplication = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.SMS_DEFAULT_APPLICATION);

        SmsApplicationData applicationData = null;
        if (defaultApplication != null) {
            applicationData = getApplicationForPackage(applications, defaultApplication);
        }
        // Picking a new SMS app requires AppOps and Settings.Secure permissions, so we only do
        // this if the caller asked us to.
        if (updateIfNeeded) {
            if (applicationData == null) {
                // Try to find the default SMS package for this device
                Resources r = context.getResources();
                String defaultPackage =
                        r.getString(com.android.internal.R.string.default_sms_application);
                applicationData = getApplicationForPackage(applications, defaultPackage);
            }
            if (applicationData == null) {
                // Are there any applications?
                if (applications.size() != 0) {
                    applicationData = (SmsApplicationData)applications.toArray()[0];
                }
            }

            // If we found a new default app, update the setting
            if (applicationData != null) {
                setDefaultApplication(applicationData.mPackageName, context);
            }
        }
        return applicationData;
    }

    /**
     * Sets the specified package as the default SMS/MMS application. The caller of this method
     * needs to have permission to set AppOps and write to secure settings.
     */
    public static void setDefaultApplication(String packageName, Context context) {
        Collection<SmsApplicationData> applications = getApplicationCollection(context);
        String oldPackageName = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.SMS_DEFAULT_APPLICATION);
        SmsApplicationData oldSmsApplicationData = getApplicationForPackage(applications,
                oldPackageName);
        SmsApplicationData smsApplicationData = getApplicationForPackage(applications,
                packageName);

        if (smsApplicationData != null && smsApplicationData != oldSmsApplicationData) {
            // Ignore OP_WRITE_SMS for the previously configured default SMS app.
            AppOpsManager appOps = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
            if (oldSmsApplicationData != null) {
                appOps.setMode(AppOpsManager.OP_WRITE_SMS, oldSmsApplicationData.mUid,
                        oldSmsApplicationData.mPackageName, AppOpsManager.MODE_IGNORED);
            }

            // Update the secure setting.
            Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.SMS_DEFAULT_APPLICATION, smsApplicationData.mPackageName);

            // Allow OP_WRITE_SMS for the newly configured default SMS app.
            appOps.setMode(AppOpsManager.OP_WRITE_SMS, smsApplicationData.mUid,
                    smsApplicationData.mPackageName, AppOpsManager.MODE_ALLOWED);
        }
    }

    /**
     * Returns SmsApplicationData for this package if this package is capable of being set as the
     * default SMS application.
     */
    public static SmsApplicationData getSmsApplicationData(String packageName, Context context) {
        Collection<SmsApplicationData> applications = getApplicationCollection(context);
        return getApplicationForPackage(applications, packageName);
    }

    /**
     * Gets the default SMS application
     * @param context context from the calling app
     * @param updateIfNeeded update the default app if there is no valid default app configured.
     * @return component name of the app and class to deliver SMS messages to
     */
    public static ComponentName getDefaultSmsApplication(Context context, boolean updateIfNeeded) {
        ComponentName component = null;
        SmsApplicationData smsApplicationData = getApplication(context, updateIfNeeded);
        if (smsApplicationData != null) {
            component = new ComponentName(smsApplicationData.mPackageName,
                    smsApplicationData.mSmsReceiverClass);
        }
        return component;
    }

    /**
     * Gets the default MMS application
     * @param context context from the calling app
     * @param updateIfNeeded update the default app if there is no valid default app configured.
     * @return component name of the app and class to deliver MMS messages to
     */
    public static ComponentName getDefaultMmsApplication(Context context, boolean updateIfNeeded) {
        ComponentName component = null;
        SmsApplicationData smsApplicationData = getApplication(context, updateIfNeeded);
        if (smsApplicationData != null) {
            component = new ComponentName(smsApplicationData.mPackageName,
                    smsApplicationData.mMmsReceiverClass);
        }
        return component;
    }
}
