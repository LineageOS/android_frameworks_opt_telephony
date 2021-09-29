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
import android.telephony.Annotation.DataFailureCause;
import android.telephony.DataFailCause;
import android.telephony.data.DataProfile;
import android.telephony.data.DataService;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.SparseArray;

import com.android.internal.telephony.Phone;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.telephony.Rlog;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Set;
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
    /** Event for attaching a network request. */
    private static final int EVENT_ATTACH_NETWORK_REQUEST = 1;

    /** Event for detaching a network request. */
    private static final int EVENT_DETACH_NETWORK_REQUEST = 2;

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

    /** The callback to receives data network state update. */
    private final @NonNull DataNetworkCallback mDataNetworkCallback;

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
    private @NonNull Set<TelephonyNetworkRequest> mAttachedNetworkRequestList = new ArraySet<>();

    /** The cause for data disconnected */
    private @DataFailureCause int mFailCause = DataFailCause.NONE;

    /**
     * The interface for data network callback.
     */
    public interface DataNetworkCallback {
        /**
         * Called when data network enters {@link ConnectedState}.
         */
        void onConnected();

        /**
         * Called when data network enters {@link DisconnectedState}.
         *
         * @param cause The disconnected failure cause.
         */
        void onDisconnected(@DataFailureCause int cause);
    }

    /**
     * Constructor
     *
     * @param phone The phone instance.
     * @param looper The looper to be used by the state machine. Currently the handler thread is the
     * phone process's main thread.
     * @param dataServiceManagers Data service managers.
     * @param transport The initial transport.
     * @param dataProfile The data profile for establishing the data network.
     * @param callback The callback to receives data network state update.
     */
    public DataNetwork(@NonNull Phone phone, @NonNull Looper looper,
            @NonNull SparseArray<DataServiceManager> dataServiceManagers,
            @TransportType int transport, @NonNull DataProfile dataProfile,
            @NonNull DataNetworkCallback callback) {
        super("DataNetwork", looper);
        mPhone = phone;
        mDataNetworkCallback = callback;
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
            TelephonyNetworkRequest networkRequest = null;
            switch (msg.what) {
                case EVENT_ATTACH_NETWORK_REQUEST:
                    networkRequest = (TelephonyNetworkRequest) msg.obj;
                    mAttachedNetworkRequestList.add(networkRequest);
                    networkRequest.setAttachedNetwork(DataNetwork.this);
                    break;
                case EVENT_DETACH_NETWORK_REQUEST:
                    networkRequest = (TelephonyNetworkRequest) msg.obj;
                    mAttachedNetworkRequestList.remove(networkRequest);
                    networkRequest.setAttachedNetwork(null);
                    break;
            }
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
            mDataNetworkCallback.onConnected();
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            TelephonyNetworkRequest networkRequest = null;
            switch (msg.what) {
                case EVENT_ATTACH_NETWORK_REQUEST:
                    networkRequest = (TelephonyNetworkRequest) msg.obj;
                    mAttachedNetworkRequestList.add(networkRequest);
                    networkRequest.setAttachedNetwork(DataNetwork.this);
                    return HANDLED;
            }
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
            // Gracefully handle all the un-processed events then quit the state machine.
            // quit() throws a QUIT event to the end of message queue. All the events before quit()
            // will be processed. Events after quit() will not be processed.
            quit();
        }
    }

    @Override
    protected void unhandledMessage(Message msg) {
        IState state = getCurrentState();
        loge("Unhandled message " + msg.what + " in state "
                + (state == null ? "null" : state.getName()));
    }

    // This is the called after all events are handled.
    @Override
    protected void onQuitting() {
            mNetworkAgent.unregister();
    }

    /**
     * Attach the network request to this data network.
     * @param networkRequest Network request to attach.
     *
     * @return {@code false} if the data network cannot be attached (i.e. not in the right state.)
     */
    public boolean attachNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        // Check if the data network is in the right state. Note that because this check is outside
        // of the handler, it's possible that the state at this point is "connecting", but when
        // EVENT_ATTACH_NETWORK_REQUEST is actually processed, the state has entered "disconnected".
        // In that case, the request will just be throw back to the unsatisfied pool.
        if (getCurrentState() == null || getCurrentState() == mDisconnectingState
                || getCurrentState() == mDisconnectedState) {
            return false;
        }
        sendMessage(obtainMessage(EVENT_ATTACH_NETWORK_REQUEST, networkRequest));
        return true;
    }

    /**
     * Detach the network request from this data network. Note that this will not tear down the
     * network.
     * @param networkRequest Network request to detach.
     *
     * @return {@code false} if failed (i.e. The data network has disconnected. It can't process
     * more events.)
     */
    public boolean detachNetworkRequest(@NonNull TelephonyNetworkRequest networkRequest) {
        if (getCurrentState() == null || getCurrentState() == mDisconnectedState) {
            return false;
        }
        sendMessage(obtainMessage(EVENT_DETACH_NETWORK_REQUEST, networkRequest));
        return true;
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
     * @return The data profile of this data network.
     */
    public @NonNull DataProfile getDataProfile() {
        return mDataProfile;
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
