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

import static android.telephony.AccessNetworkConstants.TRANSPORT_TYPE_WWAN;

import android.annotation.NonNull;
import android.net.LinkProperties;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.os.Looper;
import android.os.Message;
import android.telephony.AccessNetworkConstants.TransportType;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.SparseArray;

import com.android.internal.telephony.Phone;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataNetwork class represents a single PDN (Packet Data Network).
 *
 * The life cycle of a data network starts from {@link ConnectingState}. If setup data request
 * succeeds, then it enters {@link ConnectedState}, otherwise it enters
 * {@link DisconnectedState}.
 *
 * When data network is in {@link ConnectingState}, it can enter {@link HandoverState} if handover
 * between IWLAN and cellular occurs. After handover completes or fails, it return back to
 * {@link ConnectedState}. When the data network is about to be disconnected, it first enters
 * {@link DisconnectingState} when performing graceful tear down or when sending the data
 * deactivation request. At the end, it enters {@link DisconnectedState} when {@link DataService}
 * notifies data disconnected. Note that a unsolicited disconnected event from {@link DataService}
 * can immediately move data network transisted from {@link ConnectedState} to
 * {@link DisconnectedState}. {@link DisconnectedState} is the final state of a data network.
 *
 * State machine diagram:
 *
 *                                  ┌─────────┐
 *                                  │Handover │
 *                                  └─▲────┬──┘
 *                                    │    │
 *             ┌───────────┐        ┌─┴────▼──┐        ┌──────────────┐
 *             │Connecting ├────────►Connected├────────►Disconnecting │
 *             └─────┬─────┘        └────┬────┘        └───────┬──────┘
 *                   │                   │                     │
 *                   │             ┌─────▼──────┐              │
 *                   └─────────────►Disconnected◄──────────────┘
 *                                 └────────────┘
 *
 */
public class DataNetwork extends StateMachine {
    private final @NonNull Phone mPhone;
    private final String mLogTag;
    private final LocalLog mLocalLog = new LocalLog(128);
    private static final AtomicInteger sId = new AtomicInteger(0);

    private final DefaultState mDefaultState = new DefaultState();
    private final ConnectingState mConnectingState = new ConnectingState();
    private final ConnectedState mConnectedState = new ConnectedState();
    private final HandoverState mHandoverState = new HandoverState();
    private final DisconnectingState mDisconnectingState = new DisconnectingState();
    private final DisconnectedState mDisconnectedState = new DisconnectedState();

    /** The current transport of the data network. Could be WWAN or WLAN. */
    private @TransportType int mTransport;

    private final @NonNull SparseArray<DataServiceManager> mDataServiceManagers;

    /** The network agent associated with this data network. */
    private final TelephonyNetworkAgent mNetworkAgent;

    /** The data profile used to establish this data network. */
    private final @NonNull DataProfile mDataProfile;

    /** The network capabilities of this data network. */
    private @NonNull NetworkCapabilities mNetworkCapabilities;

    /** The network score of this data network. */
    private @NonNull NetworkScore mNetworkScore;

    /** The link properties of this data network. */
    private @NonNull LinkProperties mLinkProperties;

    /** The network requests associated with this data network */
    private @NonNull List<TelephonyNetworkRequest> mAttachedNetworkRequestList = new ArrayList<>();

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the state machine. Currently the handler thread is the
     * phone process's main thread.
     * @param dataServiceManagers Data service managers.
     * @param transport The initial transport.
     * @param dataProfile The data profile for establishing the data network.
     */
    public DataNetwork(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull SparseArray<DataServiceManager> dataServiceManagers,
            @TransportType int transport, @NonNull DataProfile dataProfile) {
        super("DataNetwork", looper);
        mPhone = phone;
        mTransport = transport;
        mLogTag = "DN-" + sId.incrementAndGet() + "-"
                + ((transport == TRANSPORT_TYPE_WWAN) ? "C" : "I");

        mDataServiceManagers = dataServiceManagers;
        mDataProfile = dataProfile;
        mNetworkAgent = new TelephonyNetworkAgent(mPhone, looper, this);

        addState(mDefaultState);
        addState(mConnectingState, mDefaultState);
        addState(mConnectedState, mDefaultState);
        addState(mHandoverState, mDefaultState);
        addState(mDisconnectingState, mDefaultState);
        addState(mDisconnectedState, mDefaultState);
        setInitialState(mConnectingState);

        // This must stay at the end of constructor.
        start();
    }

    /**
     * The default state. Any events that were not handled by the child states fallback to this
     * state.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DefaultState extends State {
        @Override
        public void enter() {

        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            return HANDLED;
        }
    }

    /**
     * The connecting state. This is the initial state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class ConnectingState extends State {
        @Override
        public void enter() {

        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            return NOT_HANDLED;
        }
    }

    /**
     * The connected state. This is the state when data network becomes usable.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class ConnectedState extends State {
        @Override
        public void enter() {
            mNetworkAgent.markConnected();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            return NOT_HANDLED;
        }
    }

    /**
     * The handover state. This is the state when data network handover between IWLAN and cellular.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class HandoverState extends State {
        @Override
        public void enter() {

        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            return NOT_HANDLED;
        }
    }

    /**
     * The disconnecting state. This is the state when data network is about to be disconnected.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DisconnectingState extends State {
        @Override
        public void enter() {

        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            return NOT_HANDLED;
        }
    }

    /**
     * The disconnected state. This is the final state of a data network.
     *
     * @see DataNetwork for the state machine diagram.
     */
    private final class DisconnectedState extends State {
        @Override
        public void enter() {
            // Immediately discard all the unprocessed events.
            quitNow();
            mNetworkAgent.unregister();
        }
    }

    /**
     * @return The network capabilities of this data network.
     */
    public @NonNull NetworkCapabilities getNetworkCapabilities() {
        return mNetworkCapabilities;
    }

    /**
     * @return The link properties of this data network.
     */
    public @NonNull LinkProperties getLinkProperties() {
        return mLinkProperties;
    }

    /**
     * @return The score of this data network. The higher score makes this data network more chances
     * to be selected as the active network of the device.
     */
    public @NonNull NetworkScore getNetworkScore() {
        return mNetworkScore;
    }

    /**
     * @return The network agent config for building the network agent.
     */
    public @NonNull NetworkAgentConfig getNetworkAgentConfig() {
        // TODO: Should not be null.
        return null;
    }

    /**
     * @return The network provider for building the network agent.
     */
    public @NonNull NetworkProvider getNetworkProvider() {
        // TODO: Should not be null.
        return null;
    }

    /**
     * @return The log tag for debug message use only.
     */
    public @NonNull String getLogTag() {
        return mLogTag;
    }

    /**
     * Log debug messages.
     * @param s debug messages
     */
    @Override
    protected void log(@NonNull String s) {
        Rlog.d(mLogTag, (getCurrentState() != null
                ? (getCurrentState().getName() + ": ") : "") + s);
    }

    /**
     * Log error messages.
     * @param s error messages
     */
    @Override
    protected void loge(@NonNull String s) {
        Rlog.e(mLogTag, (getCurrentState() != null
                ? (getCurrentState().getName() + ": ") : "") + s);
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
     * Dump the state of DataNetwork
     *
     * @param fd File descriptor
     * @param printWriter Print writer
     * @param args Arguments
     */
    public void dump(FileDescriptor fd, PrintWriter printWriter, String[] args) {
        IndentingPrintWriter pw = new IndentingPrintWriter(printWriter, "  ");
        pw.println(DataNetwork.class.getSimpleName() + "-" + mPhone.getPhoneId() + ":");
        super.dump(fd, pw, args);

        pw.increaseIndent();
        mNetworkAgent.dump(fd, pw, args);

        pw.println("Attached network requests:");
        for (TelephonyNetworkRequest request : mAttachedNetworkRequestList) {
            pw.println(mAttachedNetworkRequestList);
        }

        pw.println("Local logs:");
        pw.increaseIndent();
        mLocalLog.dump(fd, pw, args);
        pw.decreaseIndent();
        pw.decreaseIndent();
    }
}
