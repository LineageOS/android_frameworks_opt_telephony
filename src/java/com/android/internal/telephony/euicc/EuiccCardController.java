/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.os.Binder;
import android.os.ServiceManager;
import android.telephony.euicc.EuiccCardManager;
import android.telephony.euicc.EuiccNotification;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Backing implementation of {@link EuiccCardManager}. */
public class EuiccCardController extends IEuiccCardController.Stub {
    private static final String TAG = "EuiccCardController";

    private final Context mContext;
    private AppOpsManager mAppOps;
    private String mCallingPackage;
    private ComponentInfo mBestComponent;
    private static EuiccCardController sInstance;

    /** Initialize the instance. Should only be called once. */
    public static EuiccCardController init(Context context) {
        synchronized (EuiccCardController.class) {
            if (sInstance == null) {
                sInstance = new EuiccCardController(context);
            } else {
                Log.wtf(TAG, "init() called multiple times! sInstance = " + sInstance);
            }
        }
        return sInstance;
    }

    /** Get an instance. Assumes one has already been initialized with {@link #init}. */
    public static EuiccCardController get() {
        if (sInstance == null) {
            synchronized (EuiccCardController.class) {
                if (sInstance == null) {
                    throw new IllegalStateException("get() called before init()");
                }
            }
        }
        return sInstance;
    }

    private EuiccCardController(Context context) {
        mContext = context;
        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        ServiceManager.addService("euicc_card_controller", this);
    }

    private void checkCallingPackage(String callingPackage) {
        // Check the caller is LPA.
        mAppOps.checkPackage(Binder.getCallingUid(), callingPackage);
        mCallingPackage = callingPackage;
        mBestComponent = EuiccConnector.findBestComponent(mContext.getPackageManager());
        if (mBestComponent == null
                || !TextUtils.equals(mCallingPackage, mBestComponent.packageName)) {
            throw new SecurityException("The calling package can only be LPA.");
        }
    }

    @Override
    public void getAllProfiles(String callingPackage, IGetAllProfilesCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void getRulesAuthTable(String callingPackage, IGetRulesAuthTableCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void getEuiccChallenge(String callingPackage, IGetEuiccChallengeCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void getEuiccInfo1(String callingPackage, IGetEuiccInfo1Callback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void getEuiccInfo2(String callingPackage, IGetEuiccInfo2Callback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void authenticateServer(String callingPackage, String matchingId, byte[] serverSigned1,
            byte[] serverSignature1, byte[] euiccCiPkIdToBeUsed, byte[] serverCertificate,
            IAuthenticateServerCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void prepareDownload(String callingPackage, @Nullable byte[] hashCc, byte[] smdpSigned2,
            byte[] smdpSignature2, byte[] smdpCertificate, IPrepareDownloadCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void loadBoundProfilePackage(String callingPackage, byte[] boundProfilePackage,
            ILoadBoundProfilePackageCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void cancelSession(String callingPackage, byte[] transactionId,
            @EuiccCardManager.CancelReason int reason, ICancelSessionCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void listNotifications(String callingPackage, @EuiccNotification.Event int events,
            IListNotificationsCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void retrieveNotificationList(String callingPackage, @EuiccNotification.Event int events,
            IRetrieveNotificationListCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void retrieveNotification(String callingPackage, int seqNumber,
            IRetrieveNotificationCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void removeNotificationFromList(String callingPackage, int seqNumber,
            IRemoveNotificationFromListCallback callback) {
        checkCallingPackage(callingPackage);

        // TODO(b/38206971): Get EuiccCard instance from UiccController and call the API.
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, "Requires DUMP");
        final long token = Binder.clearCallingIdentity();

        super.dump(fd, pw, args);
        // TODO(b/38206971): dump more information.
        pw.println("mCallingPackage=" + mCallingPackage);
        pw.println("mBestComponent=" + mBestComponent);

        Binder.restoreCallingIdentity(token);
    }
}
