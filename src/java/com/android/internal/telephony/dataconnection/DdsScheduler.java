/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.internal.telephony.dataconnection;

import android.os.AsyncResult;
import android.os.Looper;
import android.os.Message;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.net.NetworkRequest;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DdsSchedulerAc;
import com.android.internal.telephony.ModemStackController;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import java.util.HashMap;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/* {@hide} */
public class DdsScheduler extends StateMachine {
    static final String TAG = "DdsScheduler";

    private DefaultState mDefaultState = new DefaultState();
    private DdsIdleState mDdsIdleState = new DdsIdleState();
    private DdsReservedState mDdsReservedState = new DdsReservedState();
    private PsAttachReservedState mPsAttachReservedState = new PsAttachReservedState();
    private DdsSwitchState mDdsSwitchState = new DdsSwitchState();
    private DdsAutoRevertState mDdsAutoRevertState = new DdsAutoRevertState();
    private long mCurrentDds = SubscriptionManager.INVALID_SUB_ID;
    private DctController mDctController;
    private ModemStackController mModemStackController;

    private final int MODEM_DATA_CAPABILITY_UNKNOWN = -1;
    private final int MODEM_SINGLE_DATA_CAPABLE = 1;
    private final int MODEM_DUAL_DATA_CAPABLE = 2;

    private final String OVERRIDE_MODEM_DUAL_DATA_CAP_PROP = "persist.test.msim.config";

    private class NetworkRequestInfo {
        public final NetworkRequest mRequest;
        public boolean mAccepted = false;

        NetworkRequestInfo(NetworkRequest req) {
            mRequest = req;
        }

        public String toString() {
            return mRequest + " accepted = " + mAccepted;
        }
    }
    private List<NetworkRequestInfo> mInbox = Collections.synchronizedList(
            new ArrayList<NetworkRequestInfo>());

    public static DdsScheduler makeDdsScheduler() {
        HandlerThread t = new HandlerThread("DdsSchedulerThread");
        t.start();

        DdsScheduler scheduler = new DdsScheduler(t.getLooper());
        scheduler.start();
        return scheduler;
    }

    private DdsScheduler(Looper looper) {
        super("DdsScheduler", looper);

        addState(mDefaultState);
            addState(mDdsIdleState, mDefaultState);
            addState(mDdsReservedState, mDefaultState);
            addState(mDdsSwitchState, mDefaultState);
            addState(mDdsAutoRevertState, mDefaultState);
            addState(mPsAttachReservedState, mDefaultState);
        setInitialState(mDdsIdleState);
    }

    void addRequest(NetworkRequest req) {
        synchronized(mInbox) {
            mInbox.add(new NetworkRequestInfo(req));
        }
    }

    void removeRequest(NetworkRequest req) {
        synchronized(mInbox) {
            for(int i = 0; i < mInbox.size(); i++) {
                NetworkRequestInfo tempNrInfo = mInbox.get(i);
                if(tempNrInfo.mRequest.equals(req)) {
                    mInbox.remove(i);
                }
            }
        }
    }

    void markAccepted(NetworkRequest req) {
        synchronized(mInbox) {
            for(int i = 0; i < mInbox.size(); i++) {
                NetworkRequestInfo tempNrInfo = mInbox.get(i);
                if(tempNrInfo.mRequest.equals(req)) {
                    tempNrInfo.mAccepted = true;
                }
            }
        }
    }

    boolean isAlreadyAccepted(NetworkRequest nr) {
        synchronized(mInbox) {
            for(int i = 0; i < mInbox.size(); i++) {
                NetworkRequestInfo tempNrInfo = mInbox.get(i);
                if(tempNrInfo.mRequest.equals(nr)) {
                    return (tempNrInfo.mAccepted == true);
                }
            }
        }
        return false;
    }

    NetworkRequest getFirstWaitingRequest() {
        synchronized(mInbox) {
            if(mInbox.isEmpty()) {
                return null;
            } else {
                return mInbox.get(0).mRequest;
            }
        }
    }

    boolean acceptWaitingRequest() {
        boolean anyAccepted = false;
        synchronized(mInbox) {
            if(!mInbox.isEmpty()) {
                for (int i =0; i < mInbox.size(); i++) {
                    NetworkRequest nr = mInbox.get(i).mRequest;
                    if (getSubIdFromNetworkRequest(nr) == getCurrentDds()) {
                        notifyRequestAccepted(nr);
                        anyAccepted = true;
                    }
                }
            } else {
                Rlog.d(TAG, "No request can be accepted for current sub");
                return false;
            }
        }
        return anyAccepted;
    }



    void notifyRequestAccepted(NetworkRequest nr) {
        if (!isAlreadyAccepted(nr)) {
            markAccepted(nr);
            Rlog.d(TAG, "Accepted req = " + nr);

            SubscriptionController subController = SubscriptionController.getInstance();
            subController.notifyOnDemandDataSubIdChanged(nr);
        } else {
            Rlog.d(TAG, "Already accepted/notified req = " + nr);
        }
    }

    boolean isDdsSwitchRequired(NetworkRequest n) {
        if(getSubIdFromNetworkRequest(n) != getCurrentDds()) {
            Rlog.d(TAG, "DDS switch required for req = " + n);
            return true;
        } else {
            Rlog.d(TAG, "DDS switch not required for req = " + n);
            return false;
        }
    }

    public long getCurrentDds() {
        SubscriptionController subController = SubscriptionController.getInstance();
        if(mCurrentDds == SubscriptionManager.INVALID_SUB_ID) {
            mCurrentDds = subController.getDefaultDataSubId();
        }

        Rlog.d(TAG, "mCurrentDds = " + mCurrentDds);
        return mCurrentDds;
    }

    public void updateCurrentDds(NetworkRequest n) {
        mCurrentDds = getSubIdFromNetworkRequest(n);
        Rlog.d(TAG, "mCurrentDds = " + mCurrentDds);
    }

    long getSubIdFromNetworkRequest(NetworkRequest n) {
        SubscriptionController subController = SubscriptionController.getInstance();
        return subController.getSubIdFromNetworkRequest(n);
    }

    private void requestDdsSwitch(NetworkRequest n) {
        mDctController.setOnDemandDataSubId(n);
    }

    private void requestPsAttach(NetworkRequest n) {
        mDctController.doPsAttach(n);
    }

    private void requestPsDetach() {
        mDctController.doPsDetach();
    }

    private int getMaxDataAllowed() {
        int maxData = mModemStackController.getMaxDataAllowed();
        Rlog.d(TAG, "modem value of max_data = " + maxData);

        int override = SystemProperties.getInt(OVERRIDE_MODEM_DUAL_DATA_CAP_PROP,
                MODEM_DATA_CAPABILITY_UNKNOWN);
        if(override != MODEM_DATA_CAPABILITY_UNKNOWN) {
            Rlog.d(TAG, "Overriding modem max_data_value with " + override);
            maxData = override;
        }
        return maxData;
    }

    void triggerSwitch(NetworkRequest n) {
        if(mDctController == null) {
            mDctController = DctController.getInstance();
            mDctController.registerForOnDemandDataSwitchInfo(getHandler(),
                    DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE, null);
            mDctController.registerForOnDemandPsAttach(getHandler(),
                    DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE, null);
            mModemStackController = ModemStackController.getInstance();
            Rlog.d(TAG, "ModemStackController = " + mModemStackController);

            mModemStackController.registerForModemDataCapsUpdate(getHandler(),
                    DdsSchedulerAc.EVENT_MODEM_DATA_CAPABILITY_UPDATE, null);
        }

        if ((n != null) && (getMaxDataAllowed() == MODEM_DUAL_DATA_CAPABLE)) {
            requestPsAttach(n);
        } else {
            requestDdsSwitch(n);
        }
    }

    boolean isAnyRequestWaiting() {
        synchronized(mInbox) {
            return !mInbox.isEmpty();
        }
    }

    private class DefaultState extends State {
        static final String TAG = DdsScheduler.TAG + "[DefaultState]";

        @Override
        public void enter() {
        }

        @Override
        public void exit() {
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.EVENT_ADD_REQUEST: {
                    NetworkRequest nr = (NetworkRequest)msg.obj;
                    Rlog.d(TAG, "EVENT_ADD_REQUEST = " + nr);
                    addRequest(nr);
                    sendMessage(obtainMessage(DdsSchedulerAc.REQ_DDS_ALLOCATION, nr));
                    break;
                }

                case DdsSchedulerAc.EVENT_REMOVE_REQUEST: {
                    NetworkRequest nr = (NetworkRequest)msg.obj;
                    Rlog.d(TAG, "EVENT_REMOVE_REQUEST" + nr);
                    removeRequest(nr);
                    sendMessage(obtainMessage(DdsSchedulerAc.REQ_DDS_FREE, nr));
                    break;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    break;
                }
            }
            return HANDLED;
        }
    }

    /*
     * DdsIdleState: System is idle, none of the subscription is reserved.
     *
     * If a new request arrives do following actions.
     *  1. If the request is for currentDds, move to DdsReservedState.
     *  2. If the request is for other sub move to DdsSwitchState.
     */
    private class DdsIdleState extends State {
        static final String TAG = DdsScheduler.TAG + "[DdsIdleState]";
        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest nr = getFirstWaitingRequest();

            if(nr != null) {
                Rlog.d(TAG, "Request pending = " + nr);

                if (!isDdsSwitchRequired(nr)) {
                    transitionTo(mDdsReservedState);
                } else {
                    transitionTo(mDdsSwitchState);
                }
            } else {
                Rlog.d(TAG, "Nothing to process");
            }

        }

        @Override
        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest n = (NetworkRequest)msg.obj;

                    if (!isDdsSwitchRequired(n)) {
                        transitionTo(mDdsReservedState);
                    } else {
                        transitionTo(mDdsSwitchState);
                    }
                    return HANDLED;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE: NO OP!");
                    return HANDLED;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return NOT_HANDLED;
                }
            }
        }
    }

    private class DdsReservedState extends State {
        static final String TAG = DdsScheduler.TAG + "[DdsReservedState]";

        private void handleOtherSubRequests() {
            NetworkRequest nr = getFirstWaitingRequest();
            if (nr == null) {
                Rlog.d(TAG, "No more requests to accept");
                transitionTo(mDdsAutoRevertState);
            } else if (getSubIdFromNetworkRequest(nr) != getCurrentDds()) {
                Rlog.d(TAG, "Switch required for " + nr);
                transitionTo(mDdsSwitchState);
            } else {
                Rlog.e(TAG, "This request failed to get accepted. nr = " + nr);
                //reset state machine to stable state.
                transitionTo(mDdsAutoRevertState);
            }
        }

        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");
            if (!acceptWaitingRequest()) {
                handleOtherSubRequests();
            }
        }

        @Override
        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    NetworkRequest n = (NetworkRequest)msg.obj;

                    if (getSubIdFromNetworkRequest(n) == getCurrentDds()) {
                        Rlog.d(TAG, "Accepting simultaneous request for current sub");

                        notifyRequestAccepted(n);
                    }
                    return HANDLED;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");

                    if(!acceptWaitingRequest()) {
                        Rlog.d(TAG, "Can't process next in this DDS");
                        handleOtherSubRequests();
                    } else {
                        Rlog.d(TAG, "Processing next in same DDS");
                    }
                    return HANDLED;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return NOT_HANDLED;
                }
            }
        }
    }

    private class PsAttachReservedState extends State {
        static final String TAG = DdsScheduler.TAG + "[PSAttachReservedState]";
        private boolean isMultiDataSupported = true;

        private void handleOtherSubRequests() {
            NetworkRequest nr = getFirstWaitingRequest();
            if (nr == null) {
                Rlog.d(TAG, "No more requests to accept");
            } else if (getSubIdFromNetworkRequest(nr) != getCurrentDds()) {
                Rlog.d(TAG, "Next request is not for current on-demand PS sub(DSDA). nr = "
                    + nr);
            }
            transitionTo(mDdsIdleState);
        }


        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");
            if (!acceptWaitingRequest()) {
                handleOtherSubRequests();
            }

        }

        @Override
        public void exit() {
            Rlog.d(TAG, "Exit");

            //Request PS Detach on currentDds.
            requestPsDetach();
            //Update currentDds back to defaultDataSub.
            updateCurrentDds(null);
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    return HANDLED;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    if (isMultiDataSupported) {
                        Rlog.d(TAG, "Simultaneous dual-data supported.");
                        if (!acceptWaitingRequest()) {
                            handleOtherSubRequests();
                        }
                    } else {
                        Rlog.d(TAG, "Simultaneous dual-data no more supported.");
                        transitionTo(mDdsIdleState);
                    }
                    return HANDLED;
                }

                case DdsSchedulerAc.EVENT_MODEM_DATA_CAPABILITY_UPDATE: {
                    Rlog.d(TAG, "EVENT_MODEM_DATA_CAPABILITY_UPDATE");
                    if (getMaxDataAllowed() != MODEM_DUAL_DATA_CAPABLE) {
                        Rlog.d(TAG, "Lost simultaneous dual-data capability.");
                        isMultiDataSupported = false;
                    }
                    return HANDLED;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return NOT_HANDLED;
                }
            }
        }
    }

    private class DdsSwitchState extends State {
        static final String TAG = DdsScheduler.TAG + "[DdsSwitchState]";
        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");
            NetworkRequest nr = getFirstWaitingRequest();
            if (nr != null) {
                triggerSwitch(nr);
            } else {
                Rlog.d(TAG, "Error");
            }
        }

        @Override
        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    return HANDLED;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    return HANDLED;
                }

                case DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE:
                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE : {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n = (NetworkRequest)ar.result;
                    if (ar.exception == null) {
                        updateCurrentDds(n);

                        if (msg.what == DdsSchedulerAc.EVENT_ON_DEMAND_PS_ATTACH_DONE) {
                            transitionTo(mPsAttachReservedState);
                        } else {
                            transitionTo(mDdsReservedState);
                        }
                    } else {
                        Rlog.d(TAG, "Switch failed, move back to idle state");
                        //discard the request so that we can process other pending reqeusts
                        removeRequest(n);
                        transitionTo(mDdsIdleState);
                    }
                    return HANDLED;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return NOT_HANDLED;
                }
            }

        }
    }

    private class DdsAutoRevertState extends State {
        static final String TAG = DdsScheduler.TAG + "[DdsAutoRevertState]";
        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");

            if(!isAnyRequestWaiting()) {
                triggerSwitch(null);
            } else {
                Rlog.d(TAG, "Error");
            }
        }

        @Override
        public void exit() {
            Rlog.d(TAG, "Exit");
        }

        @Override
        public boolean processMessage(Message msg) {
            switch(msg.what) {
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    return HANDLED;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    return HANDLED;
                }
                 case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE : {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n = (NetworkRequest)ar.result;
                    updateCurrentDds(n);

                    transitionTo(mDdsIdleState);
                    return HANDLED;
                }

                default: {
                    Rlog.d(TAG, "unknown msg = " + msg);
                    return NOT_HANDLED;
                }
            }
        }
    }
}
