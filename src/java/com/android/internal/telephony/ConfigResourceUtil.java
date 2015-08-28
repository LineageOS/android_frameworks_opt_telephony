/*
 Copyright (c) 2015, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
 * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
 * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import java.util.Arrays;

public class ConfigResourceUtil {
    public static final String TAG = "ConfigResourceUtil";
    public static String packageName = "com.android.frameworks.telresources";

    /**
     * Utility method that gets a boolean resource declared in
     * frameworks/opt/telephony/resources/res/values/config.xml. This
     * resource may be replaced by an overlay of the same name.
     * @param context The context of the application calling the utility
     * @param resourceName The name of the resource that is requested
     * @return value of resource
     */
    public static boolean getBooleanValue(Context context, String resourceName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "bool", packageName);
            boolean resValue = res.getBoolean(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                    + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Utility method that gets an integer resource declared in
     * frameworks/opt/telephony/resources/res/values/config.xml. This
     * resource may be replaced by an overlay of the same name.
     * @param context The context of the application calling the utility
     * @param resourceName The name of the resource that is requested
     * @return value of resource
     */
    public static int getIntValue(Context context, String resourceName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "integer", packageName);
            int resValue = res.getInteger(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                    + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Utility method that gets a string resource declared in
     * frameworks/opt/telephony/resources/res/values/config.xml. This
     * resource may be replaced by an overlay of the same name.
     * @param context The context of the application calling the utility
     * @param resourceName The name of the resource that is requested
     * @return value of resource
     */
    public static String getStringValue(Context context, String resourceName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "string", packageName);
            String resValue = res.getString(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                    + "resourceValue = " + resValue);
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Utility method that gets an integer array resource declared in
     * frameworks/opt/telephony/resources/res/values/config.xml. This
     * resource may be replaced by an overlay of the same name.
     * @param context The context of the application calling the utility
     * @param resourceName The name of the resource that is requested
     * @return value of resource
     */
    public static int[] getIntArray(Context context, String resourceName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "array", packageName);
            int[] resValue = res.getIntArray(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                    + "resourceValue = " + Arrays.toString(resValue));
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Utility method that gets a string array resource declared in
     * frameworks/opt/telephony/resources/res/values/config.xml. This
     * resource may be replaced by an overlay of the same name.
     * @param context The context of the application calling the utility
     * @param resourceName The name of the resource that is requested
     * @return value of resource
     */
    public static String[] getStringArray(Context context, String resourceName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            if (res == null)
                Log.e(TAG, "res is null");
            int resId = res.getIdentifier(resourceName, "array", packageName);
            String[] resValue = res.getStringArray(resId);
            Log.v(TAG, "resourceName = " + resourceName + " resourceId = " + resId
                    + "resourceValue = " + Arrays.toString(resValue));
            return resValue;
        } catch (NameNotFoundException | NotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
