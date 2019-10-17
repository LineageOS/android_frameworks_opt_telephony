/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.ComponentInfo;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.os.SystemProperties;

/**
 * This class provides various util functions
 */
public final class TelephonyUtils {
    /** {@hide} */
    public static String emptyIfNull(@Nullable String str) {
        return str == null ? "" : str;
    }

    /** {@hide} */
    public static RuntimeException rethrowAsRuntimeException(RemoteException remoteException) {
        throw new RuntimeException(remoteException);
    }

    public static ComponentInfo getComponentInfo(@NonNull ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) return resolveInfo.activityInfo;
        if (resolveInfo.serviceInfo != null) return resolveInfo.serviceInfo;
        if (resolveInfo.providerInfo != null) return resolveInfo.providerInfo;
        throw new IllegalStateException("Missing ComponentInfo!");
    }

    public static boolean IS_DEBUGGABLE =
            SystemProperties.getInt("ro.debuggable", 0) == 1;
  }
