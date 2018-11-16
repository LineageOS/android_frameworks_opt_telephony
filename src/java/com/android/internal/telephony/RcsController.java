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
import android.telephony.rcs.RcsManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.rcs.IRcs;

/** Backing implementation of {@link RcsManager}. */
public class RcsController extends IRcs.Stub {
    private static final String TAG = "RcsController";
    private static final String RCS_SERVICE_NAME = "ircs";

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
        if (ServiceManager.getService(RCS_SERVICE_NAME) == null) {
            ServiceManager.addService(RCS_SERVICE_NAME, this);
        }
    }

    @VisibleForTesting
    public RcsController(Context context, Void unused) {
        mContext = context;
    }

    @Override
    public void deleteThread(int threadId) {
        // TODO - add implementation
    }

    @Override
    public int getMessageCount(int rcsThreadId) {
        // TODO - add implementation. Return a magic number for now to test the RPC calls
        return 1018;
    }
}
