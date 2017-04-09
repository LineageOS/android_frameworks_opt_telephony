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
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.ServiceManager;
import android.service.euicc.DownloadResult;
import android.service.euicc.EuiccService;
import android.service.euicc.GetDownloadableSubscriptionMetadataResult;
import android.service.euicc.GetEuiccProfileInfoListResult;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccAccessRule;
import android.telephony.euicc.DownloadableSubscription;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.SubscriptionController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Backing implementation of {@link android.telephony.euicc.EuiccManager}. */
public class EuiccController extends IEuiccController.Stub {
    private static final String TAG = "EuiccController";

    /** Extra set on resolution intents containing the {@link EuiccOperation}. */
    @VisibleForTesting
    static final String EXTRA_OPERATION = "operation";

    // Aliases so line lengths stay short.
    private static final int OK = EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_OK;
    private static final int RESOLVABLE_ERROR =
            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR;
    private static final int GENERIC_ERROR =
            EuiccManager.EMBEDDED_SUBSCRIPTION_RESULT_GENERIC_ERROR;

    private static EuiccController sInstance;

    private final Context mContext;
    private final EuiccConnector mConnector;
    private final SubscriptionManager mSubscriptionManager;
    private final AppOpsManager mAppOpsManager;
    private final PackageManager mPackageManager;

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
        mSubscriptionManager = (SubscriptionManager)
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        mAppOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mPackageManager = context.getPackageManager();
    }

    /**
     * Continue an operation which failed with a user-resolvable error.
     *
     * <p>The implementation here makes a key assumption that the resolutionIntent has not been
     * tampered with. This is guaranteed because:
     * <UL>
     * <LI>The intent is wrapped in a PendingIntent created by the phone process which is created
     * with {@link #EXTRA_OPERATION} already present. This means that the operation cannot be
     * overridden on the PendingIntent - a caller can only add new extras.
     * <LI>The resolution activity is restricted by a privileged permission; unprivileged apps
     * cannot start it directly. So the PendingIntent is the only way to start it.
     * </UL>
     */
    @Override
    public void continueOperation(Intent resolutionIntent, Bundle resolutionExtras) {
        if (!callerCanWriteEmbeddedSubscriptions()) {
            throw new SecurityException(
                    "Must have WRITE_EMBEDDED_SUBSCRIPTIONS to continue operation");
        }
        long token = Binder.clearCallingIdentity();
        try {
            EuiccOperation op = resolutionIntent.getParcelableExtra(EXTRA_OPERATION);
            if (op == null) {
                throw new IllegalArgumentException("Invalid resolution intent");
            }

            PendingIntent callbackIntent =
                    resolutionIntent.getParcelableExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESOLUTION_CALLBACK_INTENT);
            op.continueOperation(resolutionExtras, callbackIntent);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
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
            PendingIntent callbackIntent) {
        getDownloadableSubscriptionMetadata(
                subscription, false /* forceDeactivateSim */, callbackIntent);
    }

    void getDownloadableSubscriptionMetadata(DownloadableSubscription subscription,
            boolean forceDeactivateSim, PendingIntent callbackIntent) {
        if (!callerCanWriteEmbeddedSubscriptions()) {
            throw new SecurityException("Must have WRITE_EMBEDDED_SUBSCRIPTIONS to get metadata");
        }
        long token = Binder.clearCallingIdentity();
        try {
            mConnector.getDownloadableSubscriptionMetadata(
                    subscription, forceDeactivateSim,
                    new GetMetadataCommandCallback(token, subscription, callbackIntent));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    class GetMetadataCommandCallback implements EuiccConnector.GetMetadataCommandCallback {
        protected final long mCallingToken;
        protected final DownloadableSubscription mSubscription;
        protected final PendingIntent mCallbackIntent;

        GetMetadataCommandCallback(
                long callingToken,
                DownloadableSubscription subscription,
                PendingIntent callbackIntent) {
            mCallingToken = callingToken;
            mSubscription = subscription;
            mCallbackIntent = callbackIntent;
        }

        @Override
        public void onGetMetadataComplete(
                GetDownloadableSubscriptionMetadataResult result) {
            Intent extrasIntent = new Intent();
            final int resultCode;
            switch (result.result) {
                case GetDownloadableSubscriptionMetadataResult.RESULT_OK:
                    resultCode = OK;
                    extrasIntent.putExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION,
                            result.subscription);
                    break;
                case GetDownloadableSubscriptionMetadataResult.RESULT_MUST_DEACTIVATE_SIM:
                    resultCode = RESOLVABLE_ERROR;
                    addResolutionIntent(extrasIntent,
                            EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                            getOperationForDeactivateSim());
                    break;
                case GetDownloadableSubscriptionMetadataResult.RESULT_GENERIC_ERROR:
                    resultCode = GENERIC_ERROR;
                    extrasIntent.putExtra(
                            EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                            result.detailedCode);
                    break;
                default:
                    Log.wtf(TAG, "Unknown result: " + result.result);
                    resultCode = GENERIC_ERROR;
                    break;
            }

            sendResult(mCallbackIntent, resultCode, extrasIntent);
        }

        @Override
        public void onEuiccServiceUnavailable() {
            sendResult(mCallbackIntent, GENERIC_ERROR, null /* extrasIntent */);
        }

        protected EuiccOperation getOperationForDeactivateSim() {
            return EuiccOperation.forGetMetadataDeactivateSim(mCallingToken, mSubscription);
        }
    }

    @Override
    public void downloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, String callingPackage, PendingIntent callbackIntent) {
        downloadSubscription(subscription, switchAfterDownload, callingPackage,
                false /* forceDeactivateSim */, callbackIntent);
    }

    void downloadSubscription(DownloadableSubscription subscription,
            boolean switchAfterDownload, String callingPackage, boolean forceDeactivateSim,
            PendingIntent callbackIntent) {
        boolean callerCanWriteEmbeddedSubscriptions = callerCanWriteEmbeddedSubscriptions();
        mAppOpsManager.checkPackage(Binder.getCallingUid(), callingPackage);

        long token = Binder.clearCallingIdentity();
        try {
            if (callerCanWriteEmbeddedSubscriptions) {
                // With WRITE_EMBEDDED_SUBSCRIPTIONS, we can skip profile-specific permission checks
                // and move straight to the profile download.
                downloadSubscriptionPrivileged(token, subscription, switchAfterDownload,
                        forceDeactivateSim, callingPackage, callbackIntent);
                return;
            }
            // Without WRITE_EMBEDDED_SUBSCRIPTIONS, the caller *must* be whitelisted per the
            // metadata of the profile to be downloaded, so check the metadata first.
            mConnector.getDownloadableSubscriptionMetadata(subscription,
                    forceDeactivateSim,
                    new DownloadSubscriptionGetMetadataCommandCallback(token, subscription,
                            switchAfterDownload, callingPackage, forceDeactivateSim,
                            callbackIntent));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    class DownloadSubscriptionGetMetadataCommandCallback extends GetMetadataCommandCallback {
        private final boolean mSwitchAfterDownload;
        private final String mCallingPackage;
        private final boolean mForceDeactivateSim;

        DownloadSubscriptionGetMetadataCommandCallback(long callingToken,
                DownloadableSubscription subscription, boolean switchAfterDownload,
                String callingPackage, boolean forceDeactivateSim,
                PendingIntent callbackIntent) {
            super(callingToken, subscription, callbackIntent);
            mSwitchAfterDownload = switchAfterDownload;
            mCallingPackage = callingPackage;
            mForceDeactivateSim = forceDeactivateSim;
        }

        @Override
        public void onGetMetadataComplete(
                GetDownloadableSubscriptionMetadataResult result) {
            if (result.result != GetDownloadableSubscriptionMetadataResult.RESULT_OK) {
                // Just propagate the error as normal.
                super.onGetMetadataComplete(result);
                return;
            }

            DownloadableSubscription subscription = result.subscription;
            UiccAccessRule[] rules = subscription.getAccessRules();
            if (rules == null) {
                Log.e(TAG, "No access rules but caller is unprivileged");
                sendResult(mCallbackIntent, GENERIC_ERROR, null /* extrasIntent */);
                return;
            }

            final PackageInfo info;
            try {
                info = mPackageManager.getPackageInfo(
                        mCallingPackage, PackageManager.GET_SIGNATURES);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Calling package valid but gone");
                sendResult(mCallbackIntent, GENERIC_ERROR, null /* extrasIntent */);
                return;
            }

            for (int i = 0; i < rules.length; i++) {
                if (rules[i].getCarrierPrivilegeStatus(info)
                        == TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS) {
                    // Caller can download this profile. Now, determine whether the caller can also
                    // manage the current profile; if so, we can perform the download silently; if
                    // not, the user must provide consent.

                    // TODO(b/36260308): We should plumb a slot ID through here for multi-SIM
                    // devices.
                    List<SubscriptionInfo> subInfoList =
                            mSubscriptionManager.getActiveSubscriptionInfoList();
                    int size = subInfoList.size();
                    for (int subIndex = 0; subIndex < size; subIndex++) {
                        SubscriptionInfo subInfo = subInfoList.get(subIndex);
                        if (subInfo.isEmbedded()
                                && subInfo.canManageSubscription(mContext, mCallingPackage)) {
                            // Permission verified - move on to the download.
                            downloadSubscriptionPrivileged(
                                    mCallingToken, subscription, mSwitchAfterDownload,
                                    mForceDeactivateSim, mCallingPackage, mCallbackIntent);
                            return;
                        }
                    }

                    // Switch might still be permitted, but the user must consent first.
                    Intent extrasIntent = new Intent();
                    addResolutionIntent(extrasIntent, EuiccService.ACTION_RESOLVE_NO_PRIVILEGES,
                            EuiccOperation.forDownloadNoPrivileges(
                                    mCallingToken, subscription, mSwitchAfterDownload));
                    sendResult(mCallbackIntent, RESOLVABLE_ERROR, extrasIntent);
                    return;
                }
            }
            Log.e(TAG, "Caller is not permitted to download this profile");
            sendResult(mCallbackIntent, GENERIC_ERROR, null /* extrasIntent */);
        }

        @Override
        protected EuiccOperation getOperationForDeactivateSim() {
            return EuiccOperation.forDownloadDeactivateSim(
                    mCallingToken, mSubscription, mSwitchAfterDownload, mCallingPackage);
        }
    }

    void downloadSubscriptionPrivileged(final long callingToken,
            DownloadableSubscription subscription, boolean switchAfterDownload,
            boolean forceDeactivateSim, final String callingPackage,
            final PendingIntent callbackIntent) {
        mConnector.downloadSubscription(
                subscription,
                switchAfterDownload,
                forceDeactivateSim,
                new EuiccConnector.DownloadCommandCallback() {
                    @Override
                    public void onDownloadComplete(DownloadResult result) {
                        Intent extrasIntent = new Intent();
                        final int resultCode;
                        switch (result.result) {
                            case DownloadResult.RESULT_OK:
                                resultCode = OK;
                                if (!switchAfterDownload) {
                                    // Since we're not switching, nothing will trigger a
                                    // subscription list refresh on its own, so request one here.
                                    SubscriptionController.getInstance()
                                            .requestEmbeddedSubscriptionInfoListRefresh();
                                }
                                break;
                            case DownloadResult.RESULT_MUST_DEACTIVATE_SIM:
                                resultCode = RESOLVABLE_ERROR;
                                addResolutionIntent(extrasIntent,
                                        EuiccService.ACTION_RESOLVE_DEACTIVATE_SIM,
                                        EuiccOperation.forDownloadDeactivateSim(
                                                callingToken, subscription, switchAfterDownload,
                                                callingPackage));
                                break;
                            case DownloadResult.RESULT_GENERIC_ERROR:
                                resultCode = GENERIC_ERROR;
                                extrasIntent.putExtra(
                                        EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE,
                                        result.detailedCode);
                                break;
                            default:
                                Log.wtf(TAG, "Unknown result: " + result.result);
                                resultCode = GENERIC_ERROR;
                                break;
                        }

                        sendResult(callbackIntent, resultCode, extrasIntent);
                    }

                    @Override
                    public void onEuiccServiceUnavailable() {
                        sendResult(callbackIntent, GENERIC_ERROR, null /* extrasIntent */);
                    }
                });
    }

    /**
     * Blocking call to {@link EuiccService#onGetEuiccProfileInfoList}.
     *
     * <p>Does not perform permission checks as this is not an exposed API and is only used within
     * the phone process.
     */
    public GetEuiccProfileInfoListResult blockingGetEuiccProfileInfoList() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<GetEuiccProfileInfoListResult> resultRef = new AtomicReference<>();
        mConnector.getEuiccProfileInfoList(
                new EuiccConnector.GetEuiccProfileInfoListCommandCallback() {
                    @Override
                    public void onListComplete(GetEuiccProfileInfoListResult result) {
                        resultRef.set(result);
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
        return resultRef.get();
    }

    /** Dispatch the given callback intent with the given result code and data. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void sendResult(PendingIntent callbackIntent, int resultCode, Intent extrasIntent) {
        try {
            callbackIntent.send(mContext, resultCode, extrasIntent);
        } catch (PendingIntent.CanceledException e) {
            // Caller canceled the callback; do nothing.
        }
    }

    /** Add a resolution intent to the given extras intent. */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public void addResolutionIntent(Intent extrasIntent, String resolutionAction,
            EuiccOperation op) {
        Intent intent = new Intent(EuiccManager.ACTION_RESOLVE_ERROR);
        intent.putExtra(EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESOLUTION_ACTION,
                resolutionAction);
        intent.putExtra(EXTRA_OPERATION, op);
        PendingIntent resolutionIntent = PendingIntent.getActivity(
                mContext, 0 /* requestCode */, intent, PendingIntent.FLAG_ONE_SHOT);
        extrasIntent.putExtra(
                EuiccManager.EXTRA_EMBEDDED_SUBSCRIPTION_RESOLUTION_INTENT, resolutionIntent);
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
