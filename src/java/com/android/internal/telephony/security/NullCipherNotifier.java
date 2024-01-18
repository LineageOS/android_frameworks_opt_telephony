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

package com.android.internal.telephony.security;

import android.telephony.SecurityAlgorithmUpdate;

import com.android.telephony.Rlog;

/**
 * Encapsulates logic to emit notifications to the user that a null cipher is in use. A null cipher
 * is one that does not attempt to implement any encryption.
 *
 * <p>This class will either emit notifications through SafetyCenterManager if SafetyCenter exists
 * on a device, or it will emit system notifications otherwise.
 *
 * @hide
 */
public class NullCipherNotifier {

    private static final String TAG = "NullCipherNotifier";
    private static NullCipherNotifier sInstance;

    private boolean mEnabled = false;

    /**
     * Gets a singleton NullCipherNotifier.
     */
    public static synchronized NullCipherNotifier getInstance() {
        if (sInstance == null) {
            sInstance = new NullCipherNotifier();
        }
        return sInstance;
    }

    private NullCipherNotifier() {}

    /**
     * Adds a security algorithm update. If appropriate, this will trigger a user notification.
     */
    public void onSecurityAlgorithmUpdate(int phoneId, SecurityAlgorithmUpdate update) {
        // TODO (b/315005938) this is a stub method for now. Logic
        // for tracking disclosures and emitting notifications will flow
        // from here.
        Rlog.d(TAG, "Security algorithm update: phoneId = " + phoneId + " " + update);
    }

    /**
     * Enables null cipher notification; {@code onSecurityAlgorithmUpdate} will start handling
     * security algorithm updates and send notifications to the user when required.
     */
    public void enable() {
        Rlog.d(TAG, "enabled");
        mEnabled = true;
    }

    /**
     * Clear all internal state and prevent further notifications until re-enabled. This can be
     * used in response to a user disabling the feature for null cipher notifications. If
     * {@code onSecurityAlgorithmUpdate} is called while in a disabled state, security algorithm
     * updates will be dropped.
     */
    public void disable() {
        Rlog.d(TAG, "disabled");
        mEnabled = false;
    }

    public boolean isEnabled() {
        return mEnabled;
    }
}
