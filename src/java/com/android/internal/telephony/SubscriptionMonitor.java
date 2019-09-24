/*
* Copyright (C) 2015 The Android Open Source Project
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
import android.os.Handler;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.util.LocalLog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Utility singleton to monitor subscription changes and help people act on them.
 * Uses Registrant model to post messages to handlers.
 *
 */
public class SubscriptionMonitor {

    private final RegistrantList mSubscriptionsChangedRegistrants[];

    private final SubscriptionController mSubscriptionController;
    private final Context mContext;

    private final int mPhoneSubId[];

    private final Object mLock = new Object();

    private final static boolean VDBG = true;
    private final static String LOG_TAG = "SubscriptionMonitor";

    private final static int MAX_LOGLINES = 100;
    private final LocalLog mLocalLog = new LocalLog(MAX_LOGLINES);

    public SubscriptionMonitor(ITelephonyRegistry tr, Context context,
            SubscriptionController subscriptionController, int numPhones) {
        mSubscriptionController = subscriptionController;
        mContext = context;

        mSubscriptionsChangedRegistrants = new RegistrantList[numPhones];
        mPhoneSubId = new int[numPhones];

        for (int phoneId = 0; phoneId < numPhones; phoneId++) {
            mSubscriptionsChangedRegistrants[phoneId] = new RegistrantList();
            mPhoneSubId[phoneId] = mSubscriptionController.getSubIdUsingPhoneId(phoneId);
        }

        try {
            tr.addOnSubscriptionsChangedListener(context.getOpPackageName(),
                    mSubscriptionsChangedListener);
        } catch (RemoteException e) {
        }
    }

    @VisibleForTesting
    public SubscriptionMonitor() {
        mSubscriptionsChangedRegistrants = null;
        mSubscriptionController = null;
        mContext = null;
        mPhoneSubId = null;
    }

    private final IOnSubscriptionsChangedListener mSubscriptionsChangedListener =
            new IOnSubscriptionsChangedListener.Stub() {
        @Override
        public void onSubscriptionsChanged() {
            synchronized (mLock) {
                for (int phoneId = 0; phoneId < mPhoneSubId.length; phoneId++) {
                    final int newSubId = mSubscriptionController.getSubIdUsingPhoneId(phoneId);
                    final int oldSubId = mPhoneSubId[phoneId];
                    if (oldSubId != newSubId) {
                        log("Phone[" + phoneId + "] subId changed " + oldSubId + "->" + newSubId
                                + ", " + mSubscriptionsChangedRegistrants[phoneId].size()
                                + " registrants");
                        mPhoneSubId[phoneId] = newSubId;
                        mSubscriptionsChangedRegistrants[phoneId].notifyRegistrants();
                    }
                }
            }
        }
    };

    public void registerForSubscriptionChanged(int phoneId, Handler h, int what, Object o) {
        if (invalidPhoneId(phoneId)) {
            throw new IllegalArgumentException("Invalid PhoneId");
        }
        Registrant r = new Registrant(h, what, o);
        mSubscriptionsChangedRegistrants[phoneId].add(r);
        r.notifyRegistrant();
    }

    public void unregisterForSubscriptionChanged(int phoneId, Handler h) {
        if (invalidPhoneId(phoneId)) {
            throw new IllegalArgumentException("Invalid PhoneId");
        }
        mSubscriptionsChangedRegistrants[phoneId].remove(h);
    }

    private boolean invalidPhoneId(int phoneId) {
        if (phoneId >= 0 && phoneId < mPhoneSubId.length) return false;
        return true;
    }

    private void log(String s) {
        Rlog.d(LOG_TAG, s);
        mLocalLog.log(s);
    }

    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        synchronized (mLock) {
            mLocalLog.dump(fd, printWriter, args);
        }
    }
}
