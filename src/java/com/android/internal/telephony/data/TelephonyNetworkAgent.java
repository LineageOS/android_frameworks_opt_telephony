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
import android.annotation.Nullable;
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.net.QosFilter;
import android.net.Uri;
import android.os.Looper;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;

import com.android.internal.telephony.Phone;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;
/**
 * TelephonyNetworkAgent class represents a single PDN (Packet Data Network). It is an agent
 * for telephony to propagate network related information to the connectivity service. It always
 * has an associated parent {@link DataNetwork}.
 */
public class TelephonyNetworkAgent extends NetworkAgent {
    private final String mLogTag;
    private final Phone mPhone;
    private final LocalLog mLocalLog = new LocalLog(128);

    /** The parent data network. */
    private final @NonNull DataNetwork mDataNetwork;

    /** This is the id from {@link NetworkAgent#register()}. */
    private final int mId;

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the handler. Currently the handler thread is the
     * phone process's main thread.
     * @param dataNetwork The data network which owns this network agent.
     * @param score The initial score of the network.
     * @param config The network agent config.
     * @param provider The network provider.
     */
    public TelephonyNetworkAgent(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull DataNetwork dataNetwork, @NonNull NetworkScore score,
            @NonNull NetworkAgentConfig config, @NonNull NetworkProvider provider) {
        super(phone.getContext(), looper, "TelephonyNetworkAgent",
                dataNetwork.getNetworkCapabilities(), new LinkProperties(), score, config,
                provider);
        register();
        mDataNetwork = dataNetwork;
        mPhone = phone;
        mId = getNetwork().getNetId();
        mLogTag = "TNA-" + mId;
        log("TelephonyNetworkAgent created, nc="
                + dataNetwork.getNetworkCapabilities() + ", score=" + score);
    }

    /**
     * Called when connectivity service has indicated they no longer want this network.
     */
    @Override
    public void onNetworkUnwanted() {
        mDataNetwork.tearDown(DataNetwork.TEAR_DOWN_REASON_CONNECTIVITY_SERVICE_UNWANTED);
    }

    /**
     * @return The unique id of the agent.
     */
    public int getId() {
        return mId;
    }

    /**
     * Called when the system determines the usefulness of this network.
     *
     * @param status one of {@link NetworkAgent#VALIDATION_STATUS_VALID} or
     * {@link NetworkAgent#VALIDATION_STATUS_NOT_VALID}.
     * @param redirectUri If internet connectivity is being redirected (e.g., on a captive portal),
     * this is the destination the probes are being redirected to, otherwise {@code null}.
     *
     * @see NetworkAgent#onValidationStatus(int, Uri)
     */
    @Override
    public void onValidationStatus(@android.telephony.Annotation.ValidationStatus int status,
            @Nullable Uri redirectUri) {
        mDataNetwork.setValidationResult(status, redirectUri);
    }

    /**
     * Called when connectivity service request a bandwidth update.
     */
    @Override
    public void onBandwidthUpdateRequested() {

    }
    /**
     * Called when connectivity service requests that the network hardware send the specified
     * packet at the specified interval.
     *
     * @param slot the hardware slot on which to start the keepalive.
     * @param interval the interval between packets, between 10 and 3600. Note that this API
     *                 does not support sub-second precision and will round off the request.
     * @param packet the packet to send.
     */
    @Override
    public void onStartSocketKeepalive(int slot, @NonNull Duration interval,
            @NonNull KeepalivePacketData packet) {

    }

    /**
     * Called when connectivity service requests that the network hardware stop a previously-started
     * keepalive.
     *
     * @param slot the hardware slot on which to stop the keepalive.
     */
    @Override
    public synchronized void onStopSocketKeepalive(int slot) {

    }

    /**
     * Called when a qos callback is registered with a filter.
     *
     * @param qosCallbackId the id for the callback registered
     * @param filter the filter being registered
     */
    @Override
    public void onQosCallbackRegistered(final int qosCallbackId, final @NonNull QosFilter filter) {

    }

    /**
     * Called when a qos callback is registered with a filter.
     *
     * Any QoS events that are sent with the same callback id after this method is called are a
     * no-op.
     *
     * @param qosCallbackId the id for the callback being unregistered.
     */
    public void onQosCallbackUnregistered(final int qosCallbackId) {
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
