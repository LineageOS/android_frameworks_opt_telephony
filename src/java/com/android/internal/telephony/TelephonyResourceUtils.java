/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.internal.telephony.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.telephony.SubscriptionManager;

import com.android.telephony.Rlog;

/**
 * This class provides utility functions for Telephony Resources
 */
public final class TelephonyResourceUtils {
    public static String TELEPHONY_RESOURCE_PACKAGE = "com.android.telephony.resources";
    private static final String TAG = "TelephonyResourceUtils";

    /**
     * Retrieve resource for the telephony resource package.
     */
    public static Resources getTelephonyResources(Context context) {
        try {
            return context.getPackageManager()
                    .getResourcesForApplication(TELEPHONY_RESOURCE_PACKAGE);
        }  catch (PackageManager.NameNotFoundException ex) {
            Rlog.e(TAG, "No resource package found");
        }
        return null;
    }

    /**
     * Retrieve resource context for the telephony resource package.
     */
    public static Context getTelephonyResourceContext(Context context) {
        Context resourceContext = null;
        try {
            resourceContext = context.createPackageContext(TELEPHONY_RESOURCE_PACKAGE, 0);
        }  catch (PackageManager.NameNotFoundException ex) {
            Rlog.e(TAG, "No resource package found");
        }
        return resourceContext;
    }

    /**
     *  Returns the resources from the given context for the MCC/MNC
     *  associated with the subscription.
     */
    public static Resources getResourcesForSubId(Context context, int subId) {
        Context resourceContext = getTelephonyResourceContext(context);
        if (resourceContext != null) {
            return SubscriptionManager.getResourcesForSubId(resourceContext, subId);
        }

        return null;
    }

    /**
     * Returns the resources from the given context for the MCC/MNC associated
     * with the subscription
     */
    public static Resources getResourcesForSubId(Context context, int subId,
            boolean useRootLocale) {
        Context resourceContext = getTelephonyResourceContext(context);
        if (resourceContext != null) {
            return SubscriptionManager.getResourcesForSubId(resourceContext, subId, useRootLocale);
        }

        return null;
    }

}
