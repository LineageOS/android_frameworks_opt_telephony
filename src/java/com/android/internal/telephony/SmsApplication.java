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

import android.Manifest.permission;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.provider.Telephony.Sms.Intents;
import android.telephony.TelephonyManager;

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
         * The class name of the SMS_DELIVER_ACTION receiver in this app.
         */
        public String mSmsReceiverClass;

        /**
         * The class name of the WAP_PUSH_DELIVER_ACTION receiver in this app.
         */
        public String mMmsReceiverClass;

        /**
         * The class name of the ACTION_RESPOND_VIA_MESSAGE intent in this app.
         */
        public String mRespondViaMessageClass;

        /**
         * The class name of the ACTION_SENDTO intent in this app.
         */
        public String mSendToClass;

        /**
         * The user-id for this application
         */
        public int mUid;

        /**
         * Returns true if this SmsApplicationData is complete (all intents handled).
         * @return
         */
        public boolean isComplete() {
            return (mSmsReceiverClass != null && mMmsReceiverClass != null
                    && mRespondViaMessageClass != null && mSendToClass != null);
        }

        public SmsApplicationData(String applicationName, String packageName, int uid) {
            mApplicationName = applicationName;
            mPackageName = packageName;
            mUid = uid;
        }
    }

    /**
     * Returns the list of available SMS apps defined as apps that are registered for both the
     * SMS_RECEIVED_ACTION (SMS) and WAP_PUSH_RECEIVED_ACTION (MMS) broadcasts (and their broadcast
     * receivers are enabled)
     *
     * Requirements to be an SMS application:
     * Implement SMS_DELIVER_ACTION broadcast receiver.
     * Require BROADCAST_SMS permission.
     *
     * Implement WAP_PUSH_DELIVER_ACTION broadcast receiver.
     * Require BROADCAST_WAP_PUSH permission.
     *
     * Implement RESPOND_VIA_MESSAGE intent.
     * Support smsto Uri scheme.
     * Require SEND_RESPOND_VIA_MESSAGE permission.
     *
     * Implement ACTION_SENDTO intent.
     * Support smsto Uri scheme.
     */
    public static Collection<SmsApplicationData> getApplicationCollection(Context context) {
        PackageManager packageManager = context.getPackageManager();

        // Get the list of apps registered for SMS
        Intent intent = new Intent(Intents.SMS_DELIVER_ACTION);
        List<ResolveInfo> smsReceivers = packageManager.queryBroadcastReceivers(intent, 0);

        HashMap<String, SmsApplicationData> receivers = new HashMap<String, SmsApplicationData>();

        // Add one entry to the map for every sms receiver (ignoring duplicate sms receivers)
        for (ResolveInfo resolveInfo : smsReceivers) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            if (!permission.BROADCAST_SMS.equals(activityInfo.permission)) {
                continue;
            }
            final String packageName = activityInfo.packageName;
            if (!receivers.containsKey(packageName)) {
                final String applicationName = resolveInfo.loadLabel(packageManager).toString();
                final SmsApplicationData smsApplicationData = new SmsApplicationData(
                        applicationName, packageName, activityInfo.applicationInfo.uid);
                smsApplicationData.mSmsReceiverClass = activityInfo.name;
                receivers.put(packageName, smsApplicationData);
            }
        }

        // Update any existing entries with mms receiver class
        intent = new Intent(Intents.WAP_PUSH_DELIVER_ACTION);
        intent.setDataAndType(null, "application/vnd.wap.mms-message");
        List<ResolveInfo> mmsReceivers = packageManager.queryBroadcastReceivers(intent, 0);
        for (ResolveInfo resolveInfo : mmsReceivers) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            if (!permission.BROADCAST_WAP_PUSH.equals(activityInfo.permission)) {
                continue;
            }
            final String packageName = activityInfo.packageName;
            final SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null) {
                smsApplicationData.mMmsReceiverClass = activityInfo.name;
            }
        }

        // Update any existing entries with respond via message intent class.
        intent = new Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE,
                Uri.fromParts("smsto", "", null));
        List<ResolveInfo> respondServices = packageManager.queryIntentServices(intent, 0);
        for (ResolveInfo resolveInfo : respondServices) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null) {
                continue;
            }
            if (!permission.SEND_RESPOND_VIA_MESSAGE.equals(serviceInfo.permission)) {
                continue;
            }
            final String packageName = serviceInfo.packageName;
            final SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null) {
                smsApplicationData.mRespondViaMessageClass = serviceInfo.name;
            }
        }

        // Update any existing entries with supports send to.
        intent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", "", null));
        List<ResolveInfo> sendToActivities = packageManager.queryIntentActivities(intent, 0);
        for (ResolveInfo resolveInfo : sendToActivities) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            final String packageName = activityInfo.packageName;
            final SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null) {
                smsApplicationData.mSendToClass = activityInfo.name;
            }
        }

        // Remove any entries for which we did not find all required intents.
        for (ResolveInfo resolveInfo : smsReceivers) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null) {
                continue;
            }
            final String packageName = activityInfo.packageName;
            final SmsApplicationData smsApplicationData = receivers.get(packageName);
            if (smsApplicationData != null) {
                if (!smsApplicationData.isComplete()) {
                    receivers.remove(packageName);
                }
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
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            // No phone, no SMS
            return null;
        }

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
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            // No phone, no SMS
            return;
        }

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

    /**
     * Gets the default Respond Via Message application
     * @param context context from the calling app
     * @param updateIfNeeded update the default app if there is no valid default app configured.
     * @return component name of the app and class to direct Respond Via Message intent to
     */
    public static ComponentName getDefaultRespondViaMessageApplication(Context context,
            boolean updateIfNeeded) {
        ComponentName component = null;
        SmsApplicationData smsApplicationData = getApplication(context, updateIfNeeded);
        if (smsApplicationData != null) {
            component = new ComponentName(smsApplicationData.mPackageName,
                    smsApplicationData.mRespondViaMessageClass);
        }
        return component;
    }

    /**
     * Gets the default Send To (smsto) application
     * @param context context from the calling app
     * @param updateIfNeeded update the default app if there is no valid default app configured.
     * @return component name of the app and class to direct SEND_TO (smsto) intent to
     */
    public static ComponentName getDefaultSendToApplication(Context context,
            boolean updateIfNeeded) {
        ComponentName component = null;
        SmsApplicationData smsApplicationData = getApplication(context, updateIfNeeded);
        if (smsApplicationData != null) {
            component = new ComponentName(smsApplicationData.mPackageName,
                    smsApplicationData.mSendToClass);
        }
        return component;
    }
}
