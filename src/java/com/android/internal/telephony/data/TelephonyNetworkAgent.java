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
import android.net.Network;
import android.net.NetworkAgent;
import android.os.Looper;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * TelephonyNetworkAgent class represents a single PDN (Packet Data Network). It is an agent
 * for telephony to propagate network related information to the connectivity service.
 */
public class TelephonyNetworkAgent extends NetworkAgent {
    private final String mLogTag;
    private final Phone mPhone;
    private final LocalLog mLocalLog = new LocalLog(128);

    /** The {@link Network} created by {@link android.net.ConnectivityManager}. */
    private final @NonNull Network mNetwork;

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param dataNetwork The data network which owns this network agent.
     */
    public TelephonyNetworkAgent(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull DataNetwork dataNetwork) {
        super(phone.getContext(), looper, "TelephonyNetworkAgent",
                dataNetwork.getNetworkCapabilities(), dataNetwork.getLinkProperties(),
                dataNetwork.getNetworkScore(), dataNetwork.getNetworkAgentConfig(),
                dataNetwork.getNetworkProvider());
        mNetwork = register();
        mPhone = phone;
        mLogTag = "TNA-" + mNetwork.getNetId();
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    protected void log(@NonNull String s) {
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
     * Dump the state of TelephonyNetworkAgent
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(mLogTag + ":");
        pw.increaseIndent();
        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
