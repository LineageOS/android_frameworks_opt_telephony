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
import android.net.NetworkRequest;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;

import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DdsSchedulerAc;
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
    private IdleState mIdleState = new IdleState();
    private DdsReservedState mDdsReservedState = new DdsReservedState();
    private DdsSwitchState mDdsSwitchState = new DdsSwitchState();
    private DdsAutoRevertState mDdsAutoRevertState = new DdsAutoRevertState();
    private long mCurrentDds = SubscriptionManager.INVALID_SUB_ID;
    private DctController mDctController;

    private static List<NetworkRequest> sInbox = Collections.synchronizedList(
            new ArrayList<NetworkRequest>());

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
            addState(mIdleState, mDefaultState);
            addState(mDdsReservedState, mDefaultState);
            addState(mDdsSwitchState, mDefaultState);
            addState(mDdsAutoRevertState, mDefaultState);
        setInitialState(mIdleState);

    }

    static void addRequest(NetworkRequest req) {
        synchronized(sInbox) {
            sInbox.add(req);
        }
    }

    static void removeRequest(NetworkRequest req) {
        synchronized(sInbox) {
            for(int i = 0; i < sInbox.size(); i++) {
                NetworkRequest tempNr = sInbox.get(i);
                if(tempNr.equals(req)) {
                    sInbox.remove(i);
                }
            }
        }
    }

    NetworkRequest getFirstWaitingRequest() {
        synchronized(sInbox) {
            if(sInbox.isEmpty()) {
                return null;
            } else {
                return sInbox.get(0);
            }
        }
    }

    boolean acceptWaitingRequest() {
        NetworkRequest nr = getFirstWaitingRequest();
        if ((nr != null) &&
                getSubIdFromNetworkRequest(nr) == getCurrentDds()) {
            notifyRequestAccepted(nr);
            Rlog.d(TAG, "Accepted req = " + nr);
            return true;
        } else {
            if(nr == null) {
                Rlog.d(TAG, "No more requests to accept");
                transitionTo(mDdsAutoRevertState);
            } else {
                Rlog.d(TAG, "DDS switched required to accept next request.");
                transitionTo(mDdsSwitchState);
            }
            return false;
        }

    }

    void notifyRequestAccepted(NetworkRequest n) {
        SubscriptionController subController = SubscriptionController.getInstance();
        subController.notifyOnDemandDataSubIdChanged(n);
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

    void triggerSwitch(NetworkRequest n) {
        if(mDctController == null) {
            mDctController = DctController.getInstance();
            mDctController.registerForOnDemandDataSwitchInfo(getHandler(),
                    DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE, null);
        }
        mDctController.setOnDemandDataSubId(n);
    }

    boolean isAnyRequestWaiting() {
        synchronized(sInbox) {
            return !sInbox.isEmpty();
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
                case DdsSchedulerAc.REQ_DDS_ALLOCATION: {
                    Rlog.d(TAG, "REQ_DDS_ALLOCATION");
                    break;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    break;
                }
            }
            return HANDLED;
        }
    }

    /*
     * IdleState: System is idle, no request to process.
     *
     * If a new request arrives do following actions.
     *  1. If the request is for currentDds, move to DdsReservedState.
     *  2. If the request is for other sub move to DdsSwitchState.
     */
    private class IdleState extends State {
        static final String TAG = DdsScheduler.TAG + "[IdleState]";
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
                    break;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE: NO OP!");
                    break;
                }
            }
            return HANDLED;
        }
    }

    private class DdsReservedState extends State {
        static final String TAG = DdsScheduler.TAG + "[DdsReservedState]";
        @Override
        public void enter() {
            Rlog.d(TAG, "Enter");
            acceptWaitingRequest();
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
                    break;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");

                    if(!acceptWaitingRequest()) {
                        Rlog.d(TAG, "Can't process next in this DDS");
                    } else {
                        Rlog.d(TAG, "Processing next in same DDS");
                    }
                    break;
                }
            }
            return HANDLED;
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
                    break;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    break;
                }

                case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE : {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n = (NetworkRequest)ar.result;
                    updateCurrentDds(n);

                    transitionTo(mDdsReservedState);

                    break;
                }
            }
            return HANDLED;
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
                    break;
                }

                case DdsSchedulerAc.REQ_DDS_FREE: {
                    Rlog.d(TAG, "REQ_DDS_FREE");
                    break;
                }
                 case DdsSchedulerAc.EVENT_ON_DEMAND_DDS_SWITCH_DONE : {
                    AsyncResult ar = (AsyncResult) msg.obj;
                    NetworkRequest n = (NetworkRequest)ar.result;
                    updateCurrentDds(n);

                    transitionTo(mIdleState);

                    break;
                }
            }
            return HANDLED;
        }
    }
}
