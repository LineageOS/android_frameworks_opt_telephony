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

import android.annotation.CallbackExecutor;
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
import java.util.concurrent.Executor;

/**
 * DataSettingsManager maintains the data related settings, for example, data enabled settings,
 * data roaming settings, etc...
 */
public class DataSettingsManager extends Handler {
    /** Event for data config updated. */
    private static final int EVENT_DATA_CONFIG_UPDATED = 1;

    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    /** Data config manager */
    private final @NonNull DataConfigManager mDataConfigManager;

    /** Data settings manager callback. */
    private final @NonNull DataSettingsManagerCallback mDataSettingsManagerCallback;

    /**
     * Data settings manager callback. This should be only used by {@link DataNetworkController}.
     */
    public abstract static class DataSettingsManagerCallback extends DataCallback {
        /**
         * Constructor
         *
         * @param executor The executor of the callback.
         */
        public DataSettingsManagerCallback(@NonNull @CallbackExecutor Executor executor) {
            super(executor);
        }

        /**
         * Called when overall data enabled state changed.
         *
         * @param enabled {@code true} indicates mobile data is enabled.
         */
        public abstract void onDataEnabledChanged(boolean enabled);

        /**
         * Called when overall data enabled state changed.
         *
         * @param enabled {@code true} indicates mobile data is enabled.
         */
        public abstract void onRoamingEnabledChanged(boolean enabled);
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param dataNetworkController Data network controller.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param callback Data settings manager callback.
     */
    public DataSettingsManager(@NonNull Phone phone,
            @NonNull DataNetworkController dataNetworkController, @NonNull Looper looper,
            @NonNull DataSettingsManagerCallback callback) {
        super(looper);
        mPhone = phone;
        mLogTag = "DSMGR-" + mPhone.getPhoneId();

        mDataSettingsManagerCallback = callback;
        mDataConfigManager = dataNetworkController.getDataConfigManager();

        mDataConfigManager.registerForConfigUpdate(this, EVENT_DATA_CONFIG_UPDATED);
    }

    @Override
    public void handleMessage(Message msg) {

    }

    /**
     * @return {@code true} if data is enabled.
     */
    public boolean isDataEnabled() {
        return true;
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
     * Dump the state of DataSettingsManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataSettingsManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
