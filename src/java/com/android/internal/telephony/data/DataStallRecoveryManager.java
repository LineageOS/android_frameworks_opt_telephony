/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import android.annotation.NonNull;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RegistrantList;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * DataStallRecoveryManager monitors the network validation result from connectivity service and
 * takes actions to recovery data network.
 */
public class DataStallRecoveryManager extends Handler {
    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    /** Event for internet validation status changed. */
    private static final int EVENT_INTERNET_VALIDATION_STATUS_CHANGED = 2;

    private final @NonNull Phone mPhone;
    private final @NonNull String mLogTag;
    private final @NonNull LocalLog mLocalLog = new LocalLog(128);

    /** Data network controller */
    private final @NonNull DataNetworkController mDataNetworkController;

    /** Data config manager */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Cellular data service */
    private final @NonNull DataServiceManager mWwanDataServiceManager;

    /** The RegistrantList for recovery action reestablish */
    private final RegistrantList mDataStallReestablishRegistrants = new RegistrantList();

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataServiceManager The WWAN data service manager.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataStallRecoveryManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController,
            @NonNull DataServiceManager dataServiceManager, @NonNull Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DSTMTR-" + mPhone.getPhoneId();
        mDataNetworkController = dataNetworkController;
        mWwanDataServiceManager = dataServiceManager;
        mDataConfigManager = mDataNetworkController.getDataConfigManager();

        registerAllEvents();
    }

    /**
     * Register for all events that data stall monitor is interested.
     */
    private void registerAllEvents() {
        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
        mDataNetworkController.registerForInternetValidationStatusChanged(this,
                EVENT_INTERNET_VALIDATION_STATUS_CHANGED);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_DATA_CONFIG_UPDATED:
                onDataConfigUpdated();
                break;
            case EVENT_INTERNET_VALIDATION_STATUS_CHANGED:
                AsyncResult ar = (AsyncResult) msg.obj;
                Boolean isValid = (Boolean) ar.result;
                onInternetValidationStatusChanged(isValid);
                break;
        }
    }

    /**
     * Called when data config was updated.
     */
    private void onDataConfigUpdated() {
    }

    /**
     * Called when internet validation status changed.
     *
     * @param isValid {@code true} if internet validation succeeded.
     */
    private void onInternetValidationStatusChanged(boolean isValid) {

    }

    /**
     * Register for data stall reestablish event.
     *
     * @param handler The handler to handle the event.
     * @param what The event.
     */
    public void registerForDataStallReestablishEvent(@NonNull Handler handler, int what) {
        mDataStallReestablishRegistrants.addUnique(handler, what, null);
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    private void log(@NonNull String s) {
        Rlog.d(mLogTag, s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    private void loge(@NonNull String s) {
        Rlog.e(mLogTag, s);
    }

    /**
     * Log debug messages and also log into the local log.
     * @param s debug messages
     */
    private void logl(@NonNull String s) {
        log(s);
        mLocalLog.log(s);
    }

    /**
     * Dump the state of DataStallRecoveryManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataStallRecoveryManager.class.getSimpleName() + "-" + mPhone.getPhoneId()
                + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
