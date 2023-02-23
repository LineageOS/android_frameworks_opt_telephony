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
 * Datagram dispatcher used to send satellite datagrams.
 */
public class DatagramDispatcher {
    private static final String TAG = "DatagramDispatcher";

    @NonNull
    private static DatagramDispatcher sInstance;
    @NonNull private final Context mContext;

    /**
     * @return The singleton instance of DatagramDispatcher.
     */
    public static DatagramDispatcher getInstance() {
        if (sInstance == null) {
            loge("DatagramDispatcher was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the DatagramDispatcher singleton instance.
     * @param context The Context to use to create the DatagramDispatcher.
     * @return The singleton instance of DatagramDispatcher.
     */
    public static DatagramDispatcher make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new DatagramDispatcher(context);
        }
        return sInstance;
    }

    /**
     * Create a DatagramDispatcher to send satellite datagrams.
     *
     * @param context The Context for the DatagramDispatcher.
     */
    private DatagramDispatcher(@NonNull Context context) {
        mContext = context;
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
