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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * DataNetworkController in the central module of the telephony data stack. It is responsible to
 * create and manage all the mobile data networks.
 */
public class DataNetworkController extends Handler {
    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    private final @NonNull DataConfigManager mDataConfigManager;
    private final @NonNull DataSettingsManager mDataSettingsManager;
    private final @NonNull DataProfileManager mDataProfileManager;
    private final @NonNull DataStallMonitor mDataStallMonitor;
    private final @NonNull DataScheduler mDataScheduler;

    /**
     * The current data network list, including the ones that are connected, connecting, or
     * disconnecting.
     */
    private final List<DataNetwork> mDataNetworkList = new ArrayList<>();

    /**
     * Contain the last 10 data networks that were connected. This is for debugging purposes only.
     */
    private final List<DataNetwork> mHistoricalDataNetworkList = new ArrayList<>();

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataNetworkController(Phone phone, Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DNC-" + mPhone.getPhoneId();

        mDataConfigManager = new DataConfigManager(mPhone, looper);
        mDataSettingsManager = new DataSettingsManager(mPhone, looper);
        mDataProfileManager = new DataProfileManager(mPhone, looper);
        mDataStallMonitor = new DataStallMonitor(mPhone, looper);
        mDataScheduler = new DataScheduler(mPhone, looper);
    }

    @Override
    public void handleMessage(Message msg) {

    }

    /**
     * @return Data config manager instance.
     */
    public @NonNull DataConfigManager getDataConfigManager() {
        return mDataConfigManager;
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
     * Dump the state of DataNetworkController
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataNetworkController.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Current data networks:");
        pw.increaseIndent();
        for (DataNetwork dn : mDataNetworkList) {
            dn.dump(fd, printWriter, args);
        }
        pw.decreaseIndent();
        pw.println("Historical connected data networks:");
        pw.increaseIndent();
        for (DataNetwork dn: mHistoricalDataNetworkList) {
            // Do not print networks which is already in current network list.
            if (!mDataNetworkList.contains(dn)) {
                dn.dump(fd, printWriter, args);
            }
        }
        pw.decreaseIndent();

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println("-------------------------------------");
        mDataProfileManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataScheduler.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataSettingsManager.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataStallMonitor.dump(fd, pw, args);
        pw.println("-------------------------------------");
        mDataConfigManager.dump(fd, pw, args);

        pw.decreaseIndent();
    }
}
