/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.internal.telephony;

import android.content.Context;
import android.os.AsyncTask;
import android.provider.BlockedNumberContract;

import com.android.telephony.Rlog;

/**
 * An {@link AsyncTask} that notifies the Blocked number provider that emergency services were
 * contacted.
 * {@hide}
 */
public class AsyncEmergencyContactNotifier extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "AsyncEmergencyContactNotifier";

    private final Context mContext;

    public AsyncEmergencyContactNotifier(Context context) {
        mContext = context;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            notifyEmergencyContact(mContext);
        } catch (Exception e) {
            Rlog.e(TAG, "Exception notifying emergency contact: " + e);
        }
        return null;
    }

    /**
     * Notifies the provider that emergency services were contacted by the user.
     */
    private void notifyEmergencyContact(Context context) {
        try {
            Rlog.i("notifyEmergencyContact; caller=%s", context.getOpPackageName());
            context.getContentResolver().call(
                    BlockedNumberContract.AUTHORITY_URI,
                    BlockedNumberContract.METHOD_NOTIFY_EMERGENCY_CONTACT,
                    null, null);
        } catch (NullPointerException | IllegalArgumentException ex) {
            // The content resolver can throw an NPE or IAE; we don't want to crash Telecom if
            // either of these happen.
            Rlog.w(null, "notifyEmergencyContact: provider not ready.");
        }
    }
}
