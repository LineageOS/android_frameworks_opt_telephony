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
package com.android.internal.telephony.euicc;

import android.Manifest;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.ServiceManager;
import android.service.euicc.DownloadResult;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.telephony.TelephonyManager;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Backing implementation of {@link android.telephony.euicc.EuiccManager}. */
public class EuiccController extends IEuiccController.Stub {
    private static final String TAG = "EuiccController";

    private static EuiccController sInstance;

    private final Context mContext;
    private final EuiccConnector mConnector;

    /** Initialize the instance. Should only be called once. */
    public static EuiccController init(Context context) {
        synchronized (EuiccController.class) {
            if (sInstance == null) {
                sInstance = new EuiccController(context);
            } else {
                Log.wtf(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    /** Get an instance. Assumes one has already been initialized with {@link #init}. */
    public static EuiccController get() {
        if (sInstance == null) {
            synchronized (EuiccController.class) {
                if (sInstance == null) {
                    throw new IllegalStateException("get() called before init()");
                }
            }
        }
        return sInstance;
    }

    private EuiccController(Context context) {
        this(context, new EuiccConnector(context));
        ServiceManager.addService("econtroller", this);
    }

    @VisibleForTesting
    public EuiccController(Context context, EuiccConnector connector) {
        mContext = context;
        mConnector = connector;
    }

    /**
     * Return the EID.
     *
     * <p>For API simplicity, this call blocks until completion; while it requires an IPC to load,
     * that IPC should generally be fast, and the EID shouldn't be needed in the normal course of
     * operation.
     */
    @Override
    public String getEid() {
        if (!callerCanReadPhoneStatePrivileged()
                && !callerHasCarrierPrivilegesForActiveSubscription()) {
            throw new SecurityException(
                    "Must have carrier privileges on active subscription to read EID");
        }
        long token = Binder.clearCallingIdentity();
        try {
            return blockingGetEidFromEuiccService();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void getDownloadableSubscriptionMetadata(DownloadableSubscription subscription,
            final PendingIntent callbackIntent) {
        if (!callerCanWriteEmbeddedSubscriptions()) {
            throw new SecurityException("Must have WRITE_EMBEDDED_SUBSCRIPTIONS to get metadata");
        }
        long token = Binder.clearCallingIdentity();
        try {
            final String subscriptionResultKey =
                    EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION;
            mConnector.getDownloadableSubscriptionMetadata(subscription,
                    new EuiccConnector.GetMetadataCommandCallback() {
                        @Override
                        public void onGetMetadataComplete(
                                GetDownloadableSubscriptionMetadataResult result) {
                            Intent extrasIntent = new Intent();
                            final int resultCode;
                            switch (result.result) {
                                case GetDownloadableSubscriptionMetadataResult.RESULT_OK:
                                    resultCode = EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK;
                                    extrasIntent.putExtra(subscriptionResultKey,
                                            result.subscription);
                                    break;
                                case GetDownloadableSubscriptionMetadataResult
                                        .RESULT_MUST_DEACTIVATE_REMOVABLE_SIM:
                                    resultCode = EuiccManager
                                            .EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR;
                                    // TODO(b/33075886): Pass through the PendingIntent for the
                                    // resolution action.
                                    break;
                                case GetDownloadableSubscriptionMetadataResult.RESULT_GENERIC_ERROR:
                                    resultCode =
                                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR;
                                    extrasIntent.putExtra(
                                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                                            result.detailedCode);
                                    break;
                                default:
                                    Log.wtf(TAG, "Unknown result: " + result.result);
                                    resultCode =
                                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR;
                                    break;
                            }

                            sendResult(callbackIntent, resultCode, extrasIntent);
                        }

                        @Override
                        public void onEuiccServiceUnavailable() {
                            sendResult(callbackIntent,
                                    EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                                    null /* extrasIntent */);
                        }
                    });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void downloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, final PendingIntent callbackIntent) {
        if (!callerCanWriteEmbeddedSubscriptions()) {
            // TODO(b/33075886): Allow unprivileged carriers who have carrier privileges on the
            // active mSubscription (if any) and the mSubscription to be downloaded.
            throw new SecurityException("Must have WRITE_EMBEDDED_SUBSCRIPTIONS to download");
        }
        long token = Binder.clearCallingIdentity();
        try {
            mConnector.downloadSubscription(subscription, switchAfterDownload,
                    new EuiccConnector.DownloadCommandCallback() {
                        @Override
                        public void onDownloadComplete(DownloadResult result) {
                            Intent extrasIntent = new Intent();
                            final int resultCode;
                            switch (result.result) {
                                case DownloadResult.RESULT_OK:
                                    resultCode = EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK;
                                    break;
                                case DownloadResult.RESULT_MUST_DEACTIVATE_REMOVABLE_SIM:
                                    resultCode = EuiccManager
                                            .EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR;
                                    // TODO(b/33075886): Pass through the PendingIntent for the
                                    // resolution action.
                                    break;
                                case DownloadResult.RESULT_GENERIC_ERROR:
                                    resultCode =
                                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR;
                                    extrasIntent.putExtra(
                                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                                            result.detailedCode);
                                    break;
                                default:
                                    Log.wtf(TAG, "Unknown result: " + result.result);
                                    resultCode =
                                            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR;
                                    break;
                            }

                            sendResult(callbackIntent, resultCode, extrasIntent);
                        }

                        @Override
                        public void onEuiccServiceUnavailable() {
                            sendResult(callbackIntent,
                                    EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR,
                                    null /* extrasIntent */);
                        }
                    });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void sendResult(PendingIntent callbackIntent, int resultCode, Intent extrasIntent) {
        try {
            callbackIntent.send(mContext, resultCode, extrasIntent);
        } catch (PendingIntent.CanceledException e) {
            // Caller canceled the callback; do nothing.
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, "Requires DUMP");
        final long token = Binder.clearCallingIdentity();
        try {
            mConnector.dump(fd, pw, args);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Nullable
    private String blockingGetEidFromEuiccService() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> eidRef = new AtomicReference<>();
        mConnector.getEid(new EuiccConnector.GetEidCommandCallback() {
            @Override
            public void onGetEidComplete(String eid) {
                eidRef.set(eid);
                latch.countDown();
            }

            @Override
            public void onEuiccServiceUnavailable() {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return eidRef.get();
    }

    private boolean callerCanReadPhoneStatePrivileged() {
        return mContext.checkCallingPermission(Manifest.permission.READ_PRIVILEGED_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean callerCanWriteEmbeddedSubscriptions() {
        return mContext.checkCallingPermission(Manifest.permission.WRITE_EMBEDDED_SUBSCRIPTIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns whether the caller has carrier privileges for the active mSubscription on this eUICC.
     */
    private boolean callerHasCarrierPrivilegesForActiveSubscription() {
        // TODO(b/36260308): We should plumb a slot ID through here for multi-SIM devices.
        TelephonyManager tm =
                (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return tm.hasCarrierPrivileges();
    }
}
