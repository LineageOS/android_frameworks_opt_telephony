/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.os.ServiceManager;
import android.telephony.Rlog;

/** Backing implementation of {@link android.telephony.RcsManager}. */
public class RcsController extends IRcs.Stub {
    private static final String TAG = "RcsController";

    private static RcsController sInstance;

    private final Context mContext;

    /** Initialize the instance. Should only be called once. */
    public static RcsController init(Context context) {
        synchronized (RcsController.class) {
            if (sInstance == null) {
                sInstance = new RcsController(context);
            } else {
                Rlog.e(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    private RcsController(Context context) {
        mContext = context;
        ServiceManager.addService("ircs", this);
    }

    @Override
    public void deleteThread(int threadId) {
        // TODO - add implementation
    }
}
