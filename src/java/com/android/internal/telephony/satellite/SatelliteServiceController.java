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
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.Rlog;
import android.telephony.satellite.stub.ISatellite;
import android.telephony.satellite.stub.SatelliteService;
import android.text.TextUtils;

import com.android.internal.telephony.ExponentialBackoff;

/**
 * Satellite service controller to manage connections with the satellite service.
 */
public class SatelliteServiceController {
    private static final String TAG = "SatelliteServiceController";
    private static final long REBIND_INITIAL_DELAY = 2 * 1000; // 2 seconds
    private static final long REBIND_MAXIMUM_DELAY = 64 * 1000; // 1 minute
    private static final int REBIND_MULTIPLIER = 2;

    @NonNull private static SatelliteServiceController sInstance;
    @NonNull private final Context mContext;
    @NonNull private final ExponentialBackoff mExponentialBackoff;
    @NonNull private final Object mLock = new Object();
    @Nullable private ISatellite mSatelliteService;
    @Nullable private SatelliteServiceConnection mSatelliteServiceConnection;
    private boolean mIsBound;

    /**
     * @return The singleton instance of SatelliteServiceController.
     */
    public static SatelliteServiceController getInstance() {
        if (sInstance == null) {
            loge("SatelliteServiceController was not yet initialized.");
        }
        return sInstance;
    }

    /**
     * Create the SatelliteServiceController singleton instance.
     * @param context The Context to use to create the SatelliteServiceController.
     */
    public static void make(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new SatelliteServiceController(context, Looper.getMainLooper());
        }
    }

    /**
     * Create a SatelliteServiceController to manage connections to the SatelliteService.
     *
     * @param context The Context for the SatelliteServiceController.
     * @param looper The Looper to run binding retry on.
     */
    private SatelliteServiceController(@NonNull Context context, @NonNull Looper looper) {
        mContext = context;
        mExponentialBackoff = new ExponentialBackoff(REBIND_INITIAL_DELAY, REBIND_MAXIMUM_DELAY,
                REBIND_MULTIPLIER, looper, () -> {
            synchronized (mLock) {
                if (mIsBound) {
                    return;
                }
                bindService();
            }
        });
        mExponentialBackoff.start();
        logd("Created SatelliteServiceController. Attempting to bind to SatelliteService.");
        bindService();
    }

    /**
     * Get the SatelliteService interface, if it exists.
     *
     * @return The bound ISatellite, or {@code null} if it is not yet connected.
     */
    @Nullable public ISatellite getService() {
        return mSatelliteService;
    }

    @NonNull private String getSatellitePackageName() {
        return TextUtils.emptyIfNull(mContext.getResources().getString(
                com.android.internal.R.string.config_satellite_service_package));
    }

    private void bindService() {
        String packageName = getSatellitePackageName();
        if (TextUtils.isEmpty(packageName)) {
            loge("Unable to bind to the satellite service because the package is undefined.");
            // Since the package name comes from static device configs, stop retry because
            // rebind will continue to fail without a valid package name.
            mExponentialBackoff.stop();
            return;
        }
        Intent intent = new Intent(SatelliteService.SERVICE_INTERFACE);
        intent.setPackage(packageName);

        mSatelliteServiceConnection = new SatelliteServiceConnection();
        try {
            boolean success = mContext.bindService(
                    intent, mSatelliteServiceConnection, Context.BIND_AUTO_CREATE);
            if (success) {
                logd("Successfully bound to the satellite service.");
            } else {
                mExponentialBackoff.notifyFailed();
                loge("Error binding to the satellite service. Retrying in "
                        + mExponentialBackoff.getCurrentDelay() + " ms.");
            }
        } catch (Exception e) {
            mExponentialBackoff.notifyFailed();
            loge("Exception binding to the satellite service. Retrying in "
                    + mExponentialBackoff.getCurrentDelay() + " ms. Exception: " + e);
        }
    }

    private void unbindService() {
        resetService();
        mContext.unbindService(mSatelliteServiceConnection);
        mSatelliteServiceConnection = null;
    }

    private void resetService() {
        // TODO: clean up any listeners and return failed for pending callbacks
        mSatelliteService = null;
    }

    private class SatelliteServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            logd("onServiceConnected: ComponentName=" + name);
            synchronized (mLock) {
                mIsBound = true;
            }
            mSatelliteService = ISatellite.Stub.asInterface(service);
            mExponentialBackoff.stop();
            // TODO: register any listeners
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            loge("onServiceDisconnected: Waiting for reconnect.");
            // Since we are still technically bound, clear the service and wait for reconnect.
            resetService();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            loge("onBindingDied: Unbinding and rebinding service.");
            synchronized (mLock) {
                mIsBound = false;
            }
            unbindService();
            mExponentialBackoff.start();
        }
    }

    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    private static void loge(@NonNull String log) {
        Rlog.e(TAG, log);
    }
}
