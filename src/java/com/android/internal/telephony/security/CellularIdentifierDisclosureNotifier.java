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

import android.telephony.CellularIdentifierDisclosure;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.Rlog;

/**
 * Encapsulates logic to emit notifications to the user that their cellular identifiers were
 * disclosed in the clear.
 *
 * <p>This class will either emit notifications through SafetyCenterManager if SafetyCenter exists
 * on a device, or it will emit system notifications otherwise.
 *
 * @hide
 */
public class CellularIdentifierDisclosureNotifier {

    private static final String TAG = "CellularIdentifierDisclosureNotifier";
    private static CellularIdentifierDisclosureNotifier sInstance = null;
    private boolean mEnabled = false;

    @VisibleForTesting
    public CellularIdentifierDisclosureNotifier() {}

    /**
     * Add a CellularIdentifierDisclosure to be tracked by this instance.
     * If appropriate, this will trigger a user notification.
     */
    public void addDisclosure(CellularIdentifierDisclosure disclosure) {
        // TODO (b/308985417) this is a stub method for now. Logic
        // for tracking disclosures and emitting notifications will flow
        // from here.
        Rlog.d(TAG, "Identifier disclosure reported: " + disclosure);
    }

    /**
     * Get a singleton CellularIdentifierDisclosureNotifier.
     */
    public static synchronized CellularIdentifierDisclosureNotifier getInstance() {
        if (sInstance == null) {
            sInstance = new CellularIdentifierDisclosureNotifier();
        }

        return sInstance;
    }

    /**
     * Re-enable if previously disabled. This means that {@code addDisclsoure} will start tracking
     * disclosures again and potentially emitting notifications.
     */
    public void enable() {
        Rlog.d(TAG, "enabled");
        mEnabled = true;
    }

    /**
     * Clear all internal state and prevent further notifications until optionally re-enabled.
     * This can be used to in response to a user disabling the feature to emit notifications.
     * If {@code addDisclosure} is called while in a disabled state, disclosures will be dropped.
     */
    public void disable() {
        Rlog.d(TAG, "disabled");
        mEnabled = false;
    }

    public boolean isEnabled() {
        return mEnabled;
    }
}
