/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.internal.telephony.satellite;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Looper;
import android.telephony.Rlog;

/**
 * PointingApp controller to manage interactions with PointingUI app.
 */
public class PointingAppController {
    private static final String TAG = "PointingAppController";

    @NonNull
    private static PointingAppController sInstance;
    @NonNull private final Context mContext;

    /**
     * @return The singleton instance of PointingAppController.
     */
    public static PointingAppController getInstance() {
        if (sInstance == null) {
            loge("PointingAppController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the PointingAppController singleton instance.
     * @param context The Context to use to create the PointingAppController.
     * @return The singleton instance of PointingAppController.
     */
    public static PointingAppController make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new PointingAppController(context);
        }
        return sInstance;
    }

    /**
     * Create a PointingAppController to manage interactions with PointingUI app.
     *
     * @param context The Context for the PointingUIController.
     */
    private PointingAppController(@NonNull Context context) {
        mContext = context;
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }

    /**
     * TODO: The following needs to be added in this class:
     * - SatellitePositionUpdateHandler
     * - startPointingUI
     * - check if pointingUI crashes - then restart it
     */
}
