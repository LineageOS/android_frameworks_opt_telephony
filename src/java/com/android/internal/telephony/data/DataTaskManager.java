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

import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.data.DataProfile;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.data.DataNetwork.DataNetworkCallback;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * DataTaskManager schedules and manages data tasks, specifically for data network setup and tear
 * down. Other data related requests that do not need scheduling are not handled by the scheduler.
 */
public class DataTaskManager extends Handler {
    /** Event for scheduling a data task. */
    private static final int EVENT_SCHEDULE_TASK = 1;

    private final Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);

    /**
     * The interface of a data task.
     */
    public static class DataTask {

        private final @ElapsedRealtimeLong long mCreatedTime;

        private @ElapsedRealtimeLong long mScheduledTime;

        public DataTask() {
            mCreatedTime = SystemClock.elapsedRealtime();
        }

        public final void setScheduledTime(@ElapsedRealtimeLong long time) {
            mScheduledTime = time;
        }

        /**
         * Specify what needs to be done when this task is executed.
         */
        public void onExecute() {
        }
    }

    /**
     * Represent a setup data network task.
     */
    public static class SetupDataNetworkTask extends DataTask {
        /** The data profile that will be used to setup a data network. */
        private final @NonNull DataProfile mDataProfile;

        /** The data network callback for caller to track the data network states. */
        private final @NonNull DataNetworkCallback mCallback;

        public SetupDataNetworkTask(@NonNull DataProfile dataProfile,
                @NonNull DataNetworkCallback callback) {
            mDataProfile = dataProfile;
            mCallback = callback;
        }

        /**
         * @return The data profile that will be used to setup a data network.
         */
        public @NonNull DataProfile getDataProfile() {
            return mDataProfile;
        }

        /**
         * @return The data network callback for caller to track the data network states.
         */
        public @NonNull DataNetworkCallback getCallback() {
            return mCallback;
        }
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     */
    public DataTaskManager(Phone phone, Looper looper) {
        super(looper);
        mPhone = phone;
        mLogTag = "DS-" + mPhone.getPhoneId();
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_SCHEDULE_TASK:
                onScheduleTask((DataTask) msg.obj);
                break;
            default:
                loge("Unexpected event " + msg.what);
                break;
        }
    }

    /**
     * Schedule a data task.
     *
     * @param task The data task.
     */
    public void scheduleTask(DataTask task) {
        sendMessage(obtainMessage(EVENT_SCHEDULE_TASK, task));
    }

    private void onScheduleTask(DataTask task) {

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
     * Dump the state of DataTaskManager
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataTaskManager.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
