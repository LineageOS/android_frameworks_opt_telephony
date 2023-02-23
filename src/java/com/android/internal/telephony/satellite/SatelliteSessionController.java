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
 * Satellite session controller to manage all the data during a satellite session.
 */
public class SatelliteSessionController {
    private static final String TAG = "SatelliteSessionController";

    @NonNull private static SatelliteSessionController sInstance;
    @NonNull private final Context mContext;

    /**
     * @return The singleton instance of SatelliteSessionController.
     */
    public static SatelliteSessionController getInstance() {
        if (sInstance == null) {
            loge("SatelliteSessionController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the SatelliteSessionController singleton instance.
     * @param context The Context to use to create the SatelliteSessionController.
     * @return The singleton instance of SatelliteSessionController.
     */
    public static SatelliteSessionController make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new SatelliteSessionController(context);
        }
        return sInstance;
    }

    /**
     * Create a SatelliteSessionController to manage satellite session.
     *
     * @param context The Context for the SatelliteSessionController.
     */
    private SatelliteSessionController(@NonNull Context context) {
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
     *- SatelliteStateListenerHandler
     * Things to add in future:
     * - state machine
     * - This class should be aware of who turned on satellite mode etc
     * - voting controller
     */
}
